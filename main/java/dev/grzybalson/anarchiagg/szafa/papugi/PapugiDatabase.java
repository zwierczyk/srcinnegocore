package dev.grzybalson.anarchiagg.szafa.papugi;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.reactivestreams.client.MongoCollection;
import dev.grzybalson.HollyCore;
import org.bson.Document;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class PapugiDatabase {
    private static final PapugaRecord EMPTY_RECORD = new PapugaRecord(null, null, null, null, 0);

    private final JavaPlugin plugin;
    private final Map<UUID, PapugaRecord> cache = new ConcurrentHashMap<>();
    private MongoCollection<Document> collection;

    public PapugiDatabase(JavaPlugin plugin) {
        this.plugin = plugin;
        this.initializeDatabase();
    }

    private void initializeDatabase() {
        if (plugin instanceof HollyCore core && core.getMongoManager() != null) {
            String prefix = core.getConfigManager().getDatabaseConfig().getDbPrefix();
            String name = (prefix != null && !prefix.isBlank()) ? prefix + "_szafka_papugi" : "szafka_papugi";
            this.collection = core.getMongoManager().getCollection(name);
        } else {
            this.plugin.getLogger().warning("[PapugiDatabase] HollyCore MongoManager nie jest zainicjalizowany!");
        }
    }

    public void setActivePapuga(UUID playerUUID, String papugaKey, String displayName, String expirationDate, String bonusKey, int rerolls) {
        this.cache.put(playerUUID, new PapugaRecord(papugaKey, displayName, expirationDate, bonusKey, rerolls));
        if (collection == null || !(plugin instanceof HollyCore core) || core.getMongoManager() == null) {
            return;
        }

        Document doc = new Document("uuid", playerUUID.toString())
                .append("active_papuga", papugaKey)
                .append("papuga_display_name", displayName)
                .append("papuga_expiration", expirationDate)
                .append("papuga_bonus", bonusKey)
                .append("papuga_rolls", rerolls);

        core.getMongoManager().toFuture(
                collection.replaceOne(Filters.eq("uuid", playerUUID.toString()), doc, new ReplaceOptions().upsert(true))
        );
    }

    public void updateActivePapugaBonus(UUID playerUUID, String bonusKey, int rerolls) {
        PapugaRecord cached = this.getActivePapugaData(playerUUID);
        if (cached.hasActivePapuga()) {
            this.cache.put(playerUUID, new PapugaRecord(cached.activePapuga(), cached.displayName(), cached.expirationDate(), bonusKey, rerolls));
        }
        if (collection == null || !(plugin instanceof HollyCore core) || core.getMongoManager() == null) {
            return;
        }

        core.getMongoManager().toFuture(
                collection.updateOne(Filters.eq("uuid", playerUUID.toString()), Updates.combine(
                        Updates.set("papuga_bonus", bonusKey),
                        Updates.set("papuga_rolls", rerolls)
                ))
        );
    }

    public String getActivePapuga(UUID playerUUID) {
        return this.getActivePapugaData(playerUUID).activePapuga();
    }

    public String getActivePapugaDisplayName(UUID playerUUID) {
        return this.getActivePapugaData(playerUUID).displayName();
    }

    public String getPapugaExpiration(UUID playerUUID) {
        return this.getActivePapugaData(playerUUID).expirationDate();
    }

    public String getActivePapugaBonus(UUID playerUUID) {
        return this.getActivePapugaData(playerUUID).bonusKey();
    }

    public int getActivePapugaRerolls(UUID playerUUID) {
        return this.getActivePapugaData(playerUUID).rerolls();
    }

    public PapugaRecord getActivePapugaData(UUID playerUUID) {
        PapugaRecord cached = this.cache.get(playerUUID);
        if (cached != null) {
            return cached;
        }
        this.cache.put(playerUUID, EMPTY_RECORD);
        loadPapugaRecordAsync(playerUUID);
        return EMPTY_RECORD;
    }

    public CompletableFuture<PapugaRecord> loadPapugaRecordAsync(UUID playerUUID) {
        if (collection == null || !(plugin instanceof HollyCore core) || core.getMongoManager() == null || !core.getMongoManager().isAvailable()) {
            return CompletableFuture.completedFuture(EMPTY_RECORD);
        }

        return core.getMongoManager().toFuture(
                collection.find(Filters.eq("uuid", playerUUID.toString())).first()
        ).thenApply(doc -> {
            if (doc == null) {
                this.cache.put(playerUUID, EMPTY_RECORD);
                return EMPTY_RECORD;
            }

            Integer rollsObj = doc.getInteger("papuga_rolls");
            int rolls = (rollsObj != null) ? rollsObj : 0;

            PapugaRecord record = new PapugaRecord(
                    doc.getString("active_papuga"),
                    doc.getString("papuga_display_name"),
                    doc.getString("papuga_expiration"),
                    doc.getString("papuga_bonus"),
                    rolls
            );
            this.cache.put(playerUUID, record);
            return record;
        }).exceptionally(e -> {
            this.plugin.getLogger().severe("Błąd podczas pobierania danych papugi z MongoDB: " + e.getMessage());
            this.cache.put(playerUUID, EMPTY_RECORD);
            return EMPTY_RECORD;
        });
    }

    public void removeActivePapuga(UUID playerUUID) {
        this.cache.put(playerUUID, EMPTY_RECORD);
        if (collection == null || !(plugin instanceof HollyCore core) || core.getMongoManager() == null) {
            return;
        }

        core.getMongoManager().toFuture(
                collection.deleteOne(Filters.eq("uuid", playerUUID.toString()))
        );
    }

    public void invalidate(UUID playerUUID) {
        this.cache.remove(playerUUID);
    }

    public void close() {
        this.cache.clear();
    }

    public record PapugaRecord(String activePapuga, String displayName, String expirationDate, String bonusKey, int rerolls) {
        public boolean hasActivePapuga() {
            return this.activePapuga != null && !this.activePapuga.isBlank();
        }
    }
}
