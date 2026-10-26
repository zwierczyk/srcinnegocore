package dev.grzybalson.modules.szaman;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.reactivestreams.client.MongoCollection;
import dev.grzybalson.HollyCore;
import org.bson.Document;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class PerksDataManager {

    private final HollyCore plugin;
    private final MongoCollection<Document> collection;
    private final Map<UUID, PerkPlayerData> cache = new ConcurrentHashMap<>();

    public PerksDataManager(HollyCore plugin) {
        this.plugin = plugin;
        String collectionName = plugin.getConfigManager().getDatabaseConfig().getDbPrefix() + "_szaman";
        this.collection = plugin.getMongoManager().getCollection(collectionName);
    }

    public CompletableFuture<PerkPlayerData> load(UUID uuid) {
        PerkPlayerData cached = cache.get(uuid);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        return plugin.getMongoManager().toFuture(collection.find(Filters.eq("uuid", uuid.toString())).first())
            .thenApply(document -> {
                PerkPlayerData data = documentToData(uuid, document);
                cache.put(uuid, data);
                return data;
            });
    }

    public PerkPlayerData getCached(UUID uuid) {
        return cache.computeIfAbsent(uuid, PerkPlayerData::new);
    }

    public CompletableFuture<Void> save(PerkPlayerData data) {
        cache.put(data.getUuid(), data);
        Document document = dataToDocument(data);
        return plugin.getMongoManager().toFuture(
            collection.replaceOne(
                Filters.eq("uuid", data.getUuid().toString()),
                document,
                new ReplaceOptions().upsert(true)
            )
        ).thenApply(result -> null);
    }

    public void unload(UUID uuid) {
        cache.remove(uuid);
    }

    private PerkPlayerData documentToData(UUID uuid, Document document) {
        PerkPlayerData data = new PerkPlayerData(uuid);
        if (document == null) {
            return data;
        }

        Object rawLevels = document.get("levels");
        if (rawLevels instanceof Document levelsDocument) {
            for (String key : levelsDocument.keySet()) {
                PerkType type = PerkType.fromString(key);
                if (type == null) {
                    continue;
                }
                Object rawLevel = levelsDocument.get(key);
                if (rawLevel instanceof Number number) {
                    data.setLevel(type, Math.max(0, number.intValue()));
                }
            }
        }

        data.setTurboDropBonus(readDouble(document, "turboDropBonus"));
        data.setTurboRankingBonus(readDouble(document, "turboRankingBonus"));
        return data;
    }

    private Document dataToDocument(PerkPlayerData data) {
        Document levels = new Document();
        for (Map.Entry<PerkType, Integer> entry : data.getLevels().entrySet()) {
            levels.append(entry.getKey().name().toLowerCase(Locale.ROOT), entry.getValue());
        }

        return new Document("uuid", data.getUuid().toString())
            .append("levels", levels)
            .append("turboDropBonus", data.getTurboDropBonus())
            .append("turboRankingBonus", data.getTurboRankingBonus())
            .append("updatedAt", System.currentTimeMillis());
    }

    private double readDouble(Document document, String key) {
        Object raw = document.get(key);
        if (raw instanceof Number number) {
            double value = number.doubleValue();
            return Double.isFinite(value) ? Math.max(0.0D, value) : 0.0D;
        }
        return 0.0D;
    }
}
