package dev.grzybalson.modules.mission;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.reactivestreams.client.MongoCollection;
import dev.grzybalson.HollyCore;
import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class MissionDataManager {

    private static final long FLUSH_INTERVAL_TICKS = 60L; // 3s — nie zapisuj Mongo przy każdym bloku

    private final HollyCore plugin;
    private final MongoCollection<Document> collection;
    private final Map<UUID, MissionPlayerData> cache = new ConcurrentHashMap<>();
    private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();
    private BukkitTask flushTask;

    public MissionDataManager(HollyCore plugin) {
        this.plugin = plugin;
        this.collection = resolveCollection(plugin);
        startFlushTask();
    }

    private void startFlushTask() {
        if (flushTask != null) {
            flushTask.cancel();
        }
        flushTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
            plugin, this::flushDirty, FLUSH_INTERVAL_TICKS, FLUSH_INTERVAL_TICKS
        );
    }

    private static MongoCollection<Document> resolveCollection(HollyCore plugin) {
        try {
            if (plugin.getMongoManager() == null || plugin.getConfigManager() == null
                || plugin.getConfigManager().getDatabaseConfig() == null) {
                return null;
            }
            String collectionName = plugin.getConfigManager().getDatabaseConfig().getDbPrefix() + "_missions";
            return plugin.getMongoManager().getCollection(collectionName);
        } catch (Exception exception) {
            plugin.getLogger().warning("[Misje] Nie udało się pobrać kolekcji MongoDB: " + exception.getMessage());
            return null;
        }
    }

    public boolean isMongoEnabled() {
        return collection != null;
    }

    public CompletableFuture<MissionPlayerData> load(UUID uuid) {
        MissionPlayerData cached = cache.get(uuid);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        if (collection == null) {
            MissionPlayerData data = new MissionPlayerData(uuid);
            cache.put(uuid, data);
            plugin.getLogger().warning("[Misje] MongoDB niedostępne — postęp gracza " + uuid + " nie zostanie zapisany.");
            return CompletableFuture.completedFuture(data);
        }

        return plugin.getMongoManager().toFuture(collection.find(Filters.eq("uuid", uuid.toString())).first())
            .thenApply(document -> {
                MissionPlayerData data = MissionPlayerData.fromDocument(uuid, document);
                cache.put(uuid, data);
                return data;
            })
            .exceptionally(throwable -> {
                plugin.getLogger().warning("[Misje] Błąd wczytywania z MongoDB dla " + uuid + ": " + throwable.getMessage());
                MissionPlayerData data = new MissionPlayerData(uuid);
                cache.put(uuid, data);
                return data;
            });
    }

    public MissionPlayerData getCached(UUID uuid) {
        return cache.computeIfAbsent(uuid, MissionPlayerData::new);
    }

    /** Oznacza do zapisu — flush co ~3s (nie blokuj main thread przy kopaniu). */
    public void markDirty(MissionPlayerData data) {
        if (data == null) {
            return;
        }
        cache.put(data.getUuid(), data);
        dirty.add(data.getUuid());
    }

    public CompletableFuture<Void> save(MissionPlayerData data) {
        cache.put(data.getUuid(), data);
        dirty.remove(data.getUuid());
        return saveNow(data);
    }

    private CompletableFuture<Void> saveNow(MissionPlayerData data) {
        if (collection == null) {
            return CompletableFuture.completedFuture(null);
        }

        Document document = data.toDocument().append("updatedAt", System.currentTimeMillis());
        return plugin.getMongoManager().toFuture(
            collection.replaceOne(
                Filters.eq("uuid", data.getUuid().toString()),
                document,
                new ReplaceOptions().upsert(true)
            )
        ).<Void>thenApply(result -> null).exceptionally(throwable -> {
            plugin.getLogger().warning("[Misje] Błąd zapisu MongoDB dla " + data.getUuid() + ": " + throwable.getMessage());
            return null;
        });
    }

    private void flushDirty() {
        if (dirty.isEmpty() || collection == null) {
            return;
        }
        for (UUID uuid : Set.copyOf(dirty)) {
            MissionPlayerData data = cache.get(uuid);
            if (data == null) {
                dirty.remove(uuid);
                continue;
            }
            dirty.remove(uuid);
            saveNow(data);
        }
    }

    public void unload(UUID uuid) {
        MissionPlayerData data = cache.remove(uuid);
        dirty.remove(uuid);
        if (data != null) {
            saveNow(data);
        }
    }

    public void shutdown() {
        if (flushTask != null) {
            flushTask.cancel();
            flushTask = null;
        }
        flushDirty();
    }
}
