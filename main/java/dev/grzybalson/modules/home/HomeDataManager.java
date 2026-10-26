package dev.grzybalson.modules.home;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.reactivestreams.client.MongoCollection;
import dev.grzybalson.HollyCore;
import org.bson.Document;
import org.bukkit.Bukkit;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class HomeDataManager {

    private static final String COLLECTION_NAME = "player_homes";

    private final HollyCore plugin;
    private final Map<UUID, PlayerHomesData> cache = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> pendingUnload = ConcurrentHashMap.newKeySet();
    private MongoCollection<Document> collection;

    public HomeDataManager(HollyCore plugin) {
        this.plugin = plugin;
        initMongo();
        if (plugin.getMongoManager() != null) {
            plugin.getMongoManager().onAvailable(this::retryPendingSaves);
        }
    }

    private void initMongo() {
        try {
            if (plugin.getMongoManager() != null && plugin.getMongoManager().isAvailable()) {
                this.collection = plugin.getMongoManager().getCollection(COLLECTION_NAME);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not initialize MongoDB collection '" + COLLECTION_NAME + "': " + e.getMessage());
        }
    }

    public PlayerHomesData getCachedHomes(UUID uuid) {
        return cache.computeIfAbsent(uuid, PlayerHomesData::new);
    }

    public CompletableFuture<PlayerHomesData> loadPlayerHomes(UUID uuid) {
        if (collection == null && plugin.getMongoManager() != null && plugin.getMongoManager().isAvailable()) {
            initMongo();
        }

        if (collection == null) {
            PlayerHomesData data = getCachedHomes(uuid);
            return CompletableFuture.completedFuture(data);
        }

        return plugin.getMongoManager().toFuture(collection.find(Filters.eq("uuid", uuid.toString())).first())
            .thenApply(doc -> {
                PlayerHomesData data;
                if (doc != null) {
                    data = PlayerHomesData.fromDocument(doc);
                    if (data == null) {
                        data = new PlayerHomesData(uuid);
                    }
                } else {
                    data = new PlayerHomesData(uuid);
                }
                cache.put(uuid, data);
                return data;
            }).exceptionally(throwable -> {
                if (!dev.grzybalson.database.MongoManager.isSkippedBecauseUnavailable(throwable)) {
                    plugin.getLogger().warning("Failed to load homes from MongoDB for " + uuid + ": " + throwable.getMessage());
                }
                return getCachedHomes(uuid);
            });
    }

    public CompletableFuture<?> savePlayerHomes(UUID uuid) {
        PlayerHomesData data = cache.get(uuid);
        if (data == null) {
            return CompletableFuture.completedFuture(null);
        }

        if (collection == null && plugin.getMongoManager() != null && plugin.getMongoManager().isAvailable()) {
            initMongo();
        }

        if (collection == null) {
            return CompletableFuture.completedFuture(null);
        }

        Document doc = data.toDocument();
        CompletableFuture<?> save = plugin.getMongoManager().toFuture(
            collection.replaceOne(Filters.eq("uuid", uuid.toString()), doc, new ReplaceOptions().upsert(true))
        );
        save.whenComplete((ignored, throwable) -> {
            if (throwable != null && !dev.grzybalson.database.MongoManager.isSkippedBecauseUnavailable(throwable)) {
                plugin.getLogger().warning("Failed to save homes to MongoDB for " + uuid + ": " + throwable.getMessage());
            }
        });
        return save;
    }

    public void unloadPlayer(UUID uuid) {
        pendingUnload.add(uuid);
        savePlayerHomes(uuid).whenComplete((ignored, throwable) -> {
            if (throwable == null && pendingUnload.remove(uuid)) {
                cache.remove(uuid);
            }
        });
    }

    private void retryPendingSaves() {
        initMongo();
        for (UUID uuid : cache.keySet()) {
            savePlayerHomes(uuid).whenComplete((ignored, throwable) -> {
                if (throwable == null && pendingUnload.remove(uuid)) {
                    cache.remove(uuid);
                }
            });
        }
    }

    public void saveAll() {
        for (UUID uuid : cache.keySet()) {
            try {
                savePlayerHomes(uuid).get();
            } catch (Exception ignored) {}
        }
    }
}
