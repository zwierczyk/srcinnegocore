package dev.grzybalson.modules.sigil;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.reactivestreams.client.MongoCollection;
import dev.grzybalson.HollyCore;
import org.bson.Document;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class SigilDataManager {

    private final HollyCore plugin;
    private final MongoCollection<Document> collection;
    private final Map<UUID, SigilPlayerData> cache = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> loaded = ConcurrentHashMap.newKeySet();

    public SigilDataManager(HollyCore plugin) {
        this.plugin = plugin;
        String collectionName = plugin.getConfigManager().getDatabaseConfig().getDbPrefix() + "_sigils";
        this.collection = plugin.getMongoManager().getCollection(collectionName);
    }

    public CompletableFuture<SigilPlayerData> load(UUID uuid) {
        if (loaded.contains(uuid)) {
            return CompletableFuture.completedFuture(cache.computeIfAbsent(uuid, SigilPlayerData::new));
        }

        return plugin.getMongoManager().toFuture(collection.find(Filters.eq("uuid", uuid.toString())).first())
            .thenApply(document -> {
                SigilPlayerData fromMongo = documentToData(uuid, document);
                SigilPlayerData existing = cache.get(uuid);
                boolean cacheHadProgress = existing != null && existing.hasProgress();
                SigilPlayerData merged = cache.merge(uuid, fromMongo, (current, incoming) -> {
                    if (current == null || !current.hasProgress()) {
                        return incoming;
                    }
                    if (incoming == null || !incoming.hasProgress()) {
                        return current;
                    }
                    current.mergeFrom(incoming);
                    return current;
                });
                loaded.add(uuid);
                if (cacheHadProgress && merged != null && merged.hasProgress()) {
                    persist(merged);
                }
                return merged;
            });
    }

    public SigilPlayerData getCached(UUID uuid) {
        return cache.computeIfAbsent(uuid, SigilPlayerData::new);
    }

    public SigilPlayerData getCachedIfPresent(UUID uuid) {
        return uuid == null ? null : cache.get(uuid);
    }

    public boolean isLoaded(UUID uuid) {
        return uuid != null && loaded.contains(uuid);
    }

    public CompletableFuture<Void> save(SigilPlayerData data) {
        cache.put(data.getUuid(), data);
        return persist(data);
    }

    public void unload(UUID uuid) {
        loaded.remove(uuid);
        SigilPlayerData data = cache.remove(uuid);
        if (data != null) {
            persist(data);
        }
    }

    public CompletableFuture<Long> resetAll() {
        loaded.clear();
        for (SigilPlayerData data : cache.values()) {
            data.reset();
        }
        return plugin.getMongoManager().toFuture(collection.deleteMany(new Document()))
            .thenApply(result -> result == null ? 0L : result.getDeletedCount());
    }

    private CompletableFuture<Void> persist(SigilPlayerData data) {
        Document document = dataToDocument(data);
        return plugin.getMongoManager().toFuture(
            collection.replaceOne(
                Filters.eq("uuid", data.getUuid().toString()),
                document,
                new ReplaceOptions().upsert(true)
            )
        ).thenApply(result -> null);
    }

    private SigilPlayerData documentToData(UUID uuid, Document document) {
        SigilPlayerData data = new SigilPlayerData(uuid);
        if (document == null) {
            return data;
        }

        Object rawOwned = document.get("owned");
        if (rawOwned instanceof Document ownedDocument) {
            for (String key : ownedDocument.keySet()) {
                SigilType type = SigilType.fromString(key);
                if (type == null) {
                    continue;
                }
                Object rawPower = ownedDocument.get(key);
                if (rawPower instanceof Number number) {
                    data.setOwned(type, number.doubleValue());
                }
            }
        }

        Object rawEquipped = document.get("equipped");
        if (rawEquipped instanceof List<?> list) {
            for (int i = 0; i < Math.min(list.size(), SigilPlayerData.MAX_SLOTS); i++) {
                Object entry = list.get(i);
                if (entry == null) {
                    continue;
                }
                String raw = String.valueOf(entry).trim();
                if (raw.isEmpty()) {
                    continue;
                }
                SigilType type = SigilType.fromString(raw);
                if (type != null && data.owns(type)) {
                    data.setEquippedAt(i, type);
                }
            }
        }

        Object rawExpires = document.get("extraSlotsExpiresAtMillis");
        if (rawExpires instanceof Number number) {
            data.setExtraSlotsExpiresAtMillis(number.longValue());
        }

        Object rawTempExpires = document.get("temporaryGrantExpiresAtMillis");
        if (rawTempExpires instanceof Number number) {
            data.setTemporaryGrantExpiresAtMillis(number.longValue());
        }

        Object rawBackup = document.get("ownedBackup");
        if (rawBackup instanceof Document backupDocument) {
            EnumMap<SigilType, Double> backup = new EnumMap<>(SigilType.class);
            for (String key : backupDocument.keySet()) {
                SigilType type = SigilType.fromString(key);
                if (type == null) {
                    continue;
                }
                Object rawPower = backupDocument.get(key);
                if (rawPower instanceof Number number) {
                    backup.put(type, number.doubleValue());
                }
            }
            data.setOwnedBackup(backup);
        } else if (data.getTemporaryGrantExpiresAtMillis() > 0L) {
            // Grant aktywny / oczekujący na prune, ale backup był pusty i nie zapisał się wcześniej.
            data.setOwnedBackup(Map.of());
        }

        Object rawOwnedExpires = document.get("ownedExpiresAt");
        if (rawOwnedExpires instanceof Document expiresDocument) {
            for (String key : expiresDocument.keySet()) {
                SigilType type = SigilType.fromString(key);
                if (type == null) {
                    continue;
                }
                Object rawAt = expiresDocument.get(key);
                if (rawAt instanceof Number number) {
                    data.setOwnedExpiresAt(type, number.longValue());
                }
            }
        }

        data.pruneExpiredExtraSlots();
        data.pruneTemporaryGrant();
        data.pruneExpiredOwned();
        return data;
    }

    private Document dataToDocument(SigilPlayerData data) {
        Document owned = new Document();
        for (Map.Entry<SigilType, Double> entry : data.getOwned().entrySet()) {
            owned.append(entry.getKey().name().toLowerCase(Locale.ROOT), entry.getValue());
        }

        List<String> equipped = new java.util.ArrayList<>(SigilPlayerData.MAX_SLOTS);
        for (int i = 0; i < SigilPlayerData.MAX_SLOTS; i++) {
            SigilType type = data.getEquippedAt(i);
            equipped.add(type == null ? "" : type.name().toLowerCase(Locale.ROOT));
        }

        Document ownedExpiresAt = new Document();
        for (Map.Entry<SigilType, Long> entry : data.getOwnedExpiresAt().entrySet()) {
            if (entry.getValue() != null && entry.getValue() > 0L) {
                ownedExpiresAt.append(entry.getKey().name().toLowerCase(Locale.ROOT), entry.getValue());
            }
        }

        Document document = new Document("uuid", data.getUuid().toString())
            .append("owned", owned)
            .append("equipped", equipped)
            .append("ownedExpiresAt", ownedExpiresAt)
            .append("extraSlotsExpiresAtMillis", data.getExtraSlotsExpiresAtMillis())
            .append("temporaryGrantExpiresAtMillis", data.getTemporaryGrantExpiresAtMillis())
            .append("updatedAt", System.currentTimeMillis());

        // Zawsze zapisuj backup przy aktywnym/oczekującym granicie (nawet pusty).
        if (data.getTemporaryGrantExpiresAtMillis() > 0L || data.hasOwnedBackup()) {
            Document backupDoc = new Document();
            for (Map.Entry<SigilType, Double> entry : data.getOwnedBackup().entrySet()) {
                backupDoc.append(entry.getKey().name().toLowerCase(Locale.ROOT), entry.getValue());
            }
            document.append("ownedBackup", backupDoc);
            document.append("temporaryGrant", true);
        }

        return document;
    }
}
