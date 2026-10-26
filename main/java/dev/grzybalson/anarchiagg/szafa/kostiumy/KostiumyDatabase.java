package dev.grzybalson.anarchiagg.szafa.kostiumy;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.reactivestreams.client.MongoCollection;
import dev.grzybalson.HollyCore;
import org.bson.Document;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class KostiumyDatabase {
    private static final KostiumRecord EMPTY_RECORD = new KostiumRecord(null, null, null);

    private final JavaPlugin plugin;
    private final String collectionName;
    private final Map<UUID, KostiumRecord> activeCache = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<KostiumRecord>> pendingLoads = new ConcurrentHashMap<>();
    private MongoCollection<Document> collection;

    public KostiumyDatabase(JavaPlugin plugin) {
        this(plugin, "szafka_kostiumy");
    }

    public KostiumyDatabase(JavaPlugin plugin, String collectionName) {
        this.plugin = plugin;
        this.collectionName = (collectionName == null || collectionName.isBlank()) ? "szafka_kostiumy" : collectionName;
        this.initializeDatabase();
    }

    private void initializeDatabase() {
        if (plugin instanceof HollyCore core && core.getMongoManager() != null) {
            String prefix = core.getConfigManager().getDatabaseConfig().getDbPrefix();
            String name = (prefix != null && !prefix.isBlank()) ? prefix + "_" + this.collectionName : this.collectionName;
            this.collection = core.getMongoManager().getCollection(name);
        } else {
            this.plugin.getLogger().warning("[KostiumyDatabase] HollyCore MongoManager nie jest zainicjalizowany!");
        }
    }

    public void setActiveKostium(UUID playerUUID, String kostiumName, String displayName, String expirationDate) {
        this.activeCache.put(playerUUID, new KostiumRecord(kostiumName, displayName, expirationDate));
        if (collection == null || !(plugin instanceof HollyCore core) || core.getMongoManager() == null) {
            return;
        }

        Document doc = new Document("uuid", playerUUID.toString())
                .append("active_kostium", kostiumName)
                .append("kostium_display_name", displayName)
                .append("kostium_expiration", expirationDate);

        core.getMongoManager().toFuture(
                collection.replaceOne(Filters.eq("uuid", playerUUID.toString()), doc, new ReplaceOptions().upsert(true))
        );
    }

    public String getActiveKostiumDisplayName(UUID playerUUID) {
        return this.getCachedRecord(playerUUID).displayName();
    }

    public String getKostiumExpiration(UUID playerUUID) {
        return this.getCachedRecord(playerUUID).expiration();
    }

    public String getActiveKostium(UUID playerUUID) {
        return this.getCachedRecord(playerUUID).activeKostium();
    }

    public void removeActiveKostium(UUID playerUUID) {
        this.activeCache.put(playerUUID, EMPTY_RECORD);
        if (collection == null || !(plugin instanceof HollyCore core) || core.getMongoManager() == null) {
            return;
        }

        core.getMongoManager().toFuture(
                collection.deleteOne(Filters.eq("uuid", playerUUID.toString()))
        );
    }

    public void invalidateCache(UUID playerUUID) {
        this.activeCache.remove(playerUUID);
        this.pendingLoads.remove(playerUUID);
    }

    /** Po cofce kolekcji Mongo — wyczyść cache i dociągnij na nowo. */
    public void invalidateAllCaches() {
        this.activeCache.clear();
        this.pendingLoads.clear();
    }

    public KostiumRecord getCachedRecord(UUID playerUUID) {
        KostiumRecord cached = this.activeCache.get(playerUUID);
        if (cached != null) {
            return cached;
        }
        loadRecordAsync(playerUUID);
        return EMPTY_RECORD;
    }

    public CompletableFuture<KostiumRecord> loadRecordAsync(UUID playerUUID) {
        KostiumRecord cached = this.activeCache.get(playerUUID);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        CompletableFuture<KostiumRecord> existing = pendingLoads.get(playerUUID);
        if (existing != null) {
            return existing;
        }

        if (collection == null || !(plugin instanceof HollyCore core) || core.getMongoManager() == null || !core.getMongoManager().isAvailable()) {
            return CompletableFuture.completedFuture(EMPTY_RECORD);
        }

        CompletableFuture<KostiumRecord> future = core.getMongoManager().toFuture(
                collection.find(Filters.eq("uuid", playerUUID.toString())).first()
        ).thenApply(doc -> {
            KostiumRecord record;
            if (doc == null) {
                record = EMPTY_RECORD;
            } else {
                record = new KostiumRecord(
                        doc.getString("active_kostium"),
                        doc.getString("kostium_display_name"),
                        doc.getString("kostium_expiration")
                );
            }
            this.activeCache.put(playerUUID, record);
            return record;
        }).exceptionally(e -> {
            this.plugin.getLogger().severe("Błąd podczas pobierania aktywnego kostiumu z MongoDB: " + e.getMessage());
            return EMPTY_RECORD;
        });

        pendingLoads.put(playerUUID, future);
        future.whenComplete((res, ex) -> pendingLoads.remove(playerUUID));
        return future;
    }

    public void close() {
        this.activeCache.clear();
        this.pendingLoads.clear();
    }

    public record KostiumRecord(String activeKostium, String displayName, String expiration) {
    }
}
