package dev.grzybalson.managers;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.reactivestreams.client.MongoCollection;
import dev.grzybalson.HollyCore;
import org.bson.Document;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class KillstreakManager {

    private final HollyCore plugin;
    private final MongoCollection<Document> collection;
    private final ConcurrentHashMap<UUID, StreakData> cache;

    public KillstreakManager(HollyCore plugin) {
        this.plugin = plugin;
        this.collection = plugin.getMongoManager().getCollection("killstreaks");
        this.cache = new ConcurrentHashMap<>();
    }

    public CompletableFuture<Integer> getKillstreak(UUID uuid) {
        return getData(uuid).thenApply(StreakData::current);
    }

    public CompletableFuture<Integer> getHighStreak(UUID uuid) {
        return getData(uuid).thenApply(StreakData::high);
    }

    public CompletableFuture<Void> setKillstreak(UUID uuid, int killstreak) {
        return getData(uuid).thenCompose(data -> {
            int sanitized = Math.max(0, killstreak);
            return save(uuid, new StreakData(sanitized, Math.max(data.high(), sanitized)));
        });
    }

    public CompletableFuture<Void> addKillstreak(UUID uuid, int amount) {
        return getData(uuid).thenCompose(data -> {
            int current = Math.max(0, data.current() + amount);
            return save(uuid, new StreakData(current, Math.max(data.high(), current)));
        });
    }

    public CompletableFuture<Void> removeKillstreak(UUID uuid, int amount) {
        return getData(uuid).thenCompose(data -> {
            int current = Math.max(0, data.current() - amount);
            return save(uuid, new StreakData(current, data.high()));
        });
    }

    public int getCachedKillstreak(UUID uuid) {
        StreakData data = cache.get(uuid);
        return data == null ? 0 : data.current();
    }

    public int getCachedHighStreak(UUID uuid) {
        StreakData data = cache.get(uuid);
        return data == null ? 0 : data.high();
    }

    public void loadPlayer(UUID uuid) {
        getData(uuid);
    }

    public void unloadPlayer(UUID uuid) {
        cache.remove(uuid);
    }

    public void invalidateCache(UUID uuid) {
        cache.remove(uuid);
    }

    public CompletableFuture<Void> resetKillstreak(UUID uuid) {
        return getData(uuid).thenCompose(data -> save(uuid, new StreakData(0, data.high())));
    }

    public CompletableFuture<Void> resetAllStreaks(UUID uuid) {
        return save(uuid, new StreakData(0, 0));
    }

    private CompletableFuture<StreakData> getData(UUID uuid) {
        StreakData cached = cache.get(uuid);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        return plugin.getMongoManager().toFuture(
            collection.find(Filters.eq("uuid", uuid.toString())).first()
        ).thenApply(doc -> {
            int current = 0;
            int high = 0;
            if (doc != null) {
                current = Math.max(0, doc.getInteger("killstreak", 0));
                high = Math.max(current, doc.getInteger("highstreak", current));
            }
            StreakData data = new StreakData(current, high);
            cache.put(uuid, data);
            return data;
        });
    }

    private CompletableFuture<Void> save(UUID uuid, StreakData data) {
        cache.put(uuid, data);

        Document doc = new Document()
            .append("uuid", uuid.toString())
            .append("killstreak", data.current())
            .append("highstreak", data.high());

        return plugin.getMongoManager().toFuture(
            collection.replaceOne(
                Filters.eq("uuid", uuid.toString()),
                doc,
                new ReplaceOptions().upsert(true)
            )
        ).thenApply(result -> null);
    }

    private record StreakData(int current, int high) {
    }
}
