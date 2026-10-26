package dev.grzybalson.anarchiagg.szafa.pety;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.reactivestreams.client.MongoCollection;
import dev.grzybalson.HollyCore;
import org.bson.Document;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class PetyDatabase {
    private static final PetRecord EMPTY_RECORD = new PetRecord(null, null, null);

    private final JavaPlugin plugin;
    private final String collectionName;
    private final Map<UUID, PetRecord> activeCache = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<PetRecord>> pendingLoads = new ConcurrentHashMap<>();
    // Token biezacego odczytu — po wyjsciu gracza stary callback nie moze juz nic wrzucic do cache.
    private final Map<UUID, Object> loadTokens = new ConcurrentHashMap<>();
    private MongoCollection<Document> collection;

    public PetyDatabase(JavaPlugin plugin) {
        this(plugin, "szafka_pety");
    }

    public PetyDatabase(JavaPlugin plugin, String collectionName) {
        this.plugin = plugin;
        this.collectionName = (collectionName == null || collectionName.isBlank()) ? "szafka_pety" : collectionName;
        this.initializeDatabase();
    }

    private void initializeDatabase() {
        if (plugin instanceof HollyCore core && core.getMongoManager() != null) {
            String prefix = core.getConfigManager().getDatabaseConfig().getDbPrefix();
            String name = (prefix != null && !prefix.isBlank()) ? prefix + "_" + this.collectionName : this.collectionName;
            this.collection = core.getMongoManager().getCollection(name);
        } else {
            this.plugin.getLogger().warning("[PetyDatabase] HollyCore MongoManager nie jest zainicjalizowany!");
        }
    }

    public void setActivePet(UUID playerUUID, String petName, String displayName, String expirationDate) {
        this.activeCache.put(playerUUID, new PetRecord(petName, displayName, expirationDate));
        if (collection == null || !(plugin instanceof HollyCore core) || core.getMongoManager() == null) {
            return;
        }

        Document doc = new Document("uuid", playerUUID.toString())
                .append("active_pet", petName)
                .append("pet_display_name", displayName)
                .append("pet_expiration", expirationDate);

        core.getMongoManager().toFuture(
                collection.replaceOne(Filters.eq("uuid", playerUUID.toString()), doc, new ReplaceOptions().upsert(true))
        ).exceptionally(e -> {
            this.plugin.getLogger().severe("[PetyDatabase] Błąd zapisu peta w Mongo: " + e.getMessage());
            return null;
        });
    }

    public String getActivePetDisplayName(UUID playerUUID) {
        return this.getActivePetData(playerUUID).displayName();
    }

    public String getPetExpiration(UUID playerUUID) {
        return this.getActivePetData(playerUUID).expirationDate();
    }

    public String getActivePet(UUID playerUUID) {
        return this.getActivePetData(playerUUID).activePet();
    }

    /** Gracze z aktywnym petem już w cache — bez skanu całego online / Mongo. */
    public List<UUID> cachedActivePetOwners() {
        if (activeCache.isEmpty()) {
            return List.of();
        }
        List<UUID> owners = new ArrayList<>(Math.min(32, activeCache.size()));
        for (Map.Entry<UUID, PetRecord> entry : activeCache.entrySet()) {
            PetRecord record = entry.getValue();
            if (record != null && record.hasActivePet()) {
                owners.add(entry.getKey());
            }
        }
        return owners;
    }

    public PetRecord getActivePetData(UUID playerUUID) {
        PetRecord cached = this.activeCache.get(playerUUID);
        if (cached != null) {
            return cached;
        }
        loadRecordAsync(playerUUID);
        return EMPTY_RECORD;
    }

    public CompletableFuture<PetRecord> loadRecordAsync(UUID playerUUID) {
        PetRecord cached = this.activeCache.get(playerUUID);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        CompletableFuture<PetRecord> existing = pendingLoads.get(playerUUID);
        if (existing != null) {
            return existing;
        }

        if (collection == null || !(plugin instanceof HollyCore core) || core.getMongoManager() == null || !core.getMongoManager().isAvailable()) {
            return CompletableFuture.completedFuture(EMPTY_RECORD);
        }

        Object token = new Object();
        this.loadTokens.put(playerUUID, token);

        CompletableFuture<PetRecord> future = core.getMongoManager().toFuture(
                collection.find(Filters.eq("uuid", playerUUID.toString())).first()
        ).thenApply(doc -> {
            PetRecord record;
            if (doc == null) {
                record = EMPTY_RECORD;
            } else {
                record = new PetRecord(
                        doc.getString("active_pet"),
                        doc.getString("pet_display_name"),
                        doc.getString("pet_expiration")
                );
            }
            if (this.loadTokens.get(playerUUID) == token) {
                this.activeCache.put(playerUUID, record);
            }
            return record;
        }).exceptionally(e -> {
            this.plugin.getLogger().severe("Błąd podczas pobierania aktywnego peta z MongoDB: " + e.getMessage());
            return EMPTY_RECORD;
        });

        pendingLoads.put(playerUUID, future);
        future.whenComplete((res, ex) -> {
            pendingLoads.remove(playerUUID);
            this.loadTokens.remove(playerUUID, token);
        });
        return future;
    }

    public void removeActivePet(UUID playerUUID) {
        this.activeCache.put(playerUUID, EMPTY_RECORD);
        if (collection == null || !(plugin instanceof HollyCore core) || core.getMongoManager() == null) {
            return;
        }

        core.getMongoManager().toFuture(
                collection.deleteOne(Filters.eq("uuid", playerUUID.toString()))
        ).exceptionally(e -> {
            this.plugin.getLogger().severe("[PetyDatabase] Błąd usuwania peta z Mongo: " + e.getMessage());
            return null;
        });
    }

    public void invalidateCache(UUID playerUUID) {
        this.loadTokens.remove(playerUUID);
        this.activeCache.remove(playerUUID);
        this.pendingLoads.remove(playerUUID);
    }

    /** Po cofce kolekcji Mongo — wyczyść cache i dociągnij na nowo. */
    public void invalidateAllCaches() {
        this.loadTokens.clear();
        this.activeCache.clear();
        this.pendingLoads.clear();
    }

    public void close() {
        this.loadTokens.clear();
        this.activeCache.clear();
        this.pendingLoads.clear();
    }

    public record PetRecord(String activePet, String displayName, String expirationDate) {
        public boolean hasActivePet() {
            return this.activePet != null && !this.activePet.isBlank();
        }
    }
}
