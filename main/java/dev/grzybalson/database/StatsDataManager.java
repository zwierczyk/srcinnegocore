package dev.grzybalson.database;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.reactivestreams.client.MongoCollection;
import dev.grzybalson.HollyCore;
import org.bson.Document;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class StatsDataManager {

    private static final long PLAYTIME_SAVE_INTERVAL_TICKS = 20L * 30L;
    private static final long SAVE_FAILURE_LOG_INTERVAL_MILLIS = 60_000L;

    private final HollyCore plugin;
    private final MongoCollection<Document> collection;
    private final ConcurrentHashMap<UUID, PlayerStats> cache;
    private final ConcurrentHashMap<UUID, CompletableFuture<Void>> activeSaves;
    private final Set<UUID> queuedSaves;
    private final Set<UUID> unloadAfterSave;
    private final ConcurrentHashMap<UUID, Long> saveFailureLogAt;
    private BukkitTask playtimeSaveTask;

    public StatsDataManager(HollyCore plugin) {
        this.plugin = plugin;
        this.collection = plugin.getMongoManager().getCollection(
            plugin.getConfigManager().getDatabaseConfig().getStatsCollection()
        );
        this.cache = new ConcurrentHashMap<>();
        this.activeSaves = new ConcurrentHashMap<>();
        this.queuedSaves = ConcurrentHashMap.newKeySet();
        this.unloadAfterSave = ConcurrentHashMap.newKeySet();
        this.saveFailureLogAt = new ConcurrentHashMap<>();
        plugin.getMongoManager().onAvailable(this::retryPendingSaves);
    }

    public void startAutoSave() {
        stopAutoSave();
        playtimeSaveTask = plugin.getServer().getScheduler().runTaskTimer(
            plugin,
            (Runnable) this::saveActiveSessions,
            PLAYTIME_SAVE_INTERVAL_TICKS,
            PLAYTIME_SAVE_INTERVAL_TICKS
        );
    }

    public void stopAutoSave() {
        if (playtimeSaveTask != null) {
            playtimeSaveTask.cancel();
            playtimeSaveTask = null;
        }
    }

    public void saveActiveSessions() {
        saveActiveSessions(true);
    }

    public void saveActiveSessions(boolean requestTopRefresh) {
        if (cache.isEmpty()) {
            return;
        }

        for (UUID uuid : cache.keySet()) {
            updatePlaytime(uuid);
            saveStats(uuid);
        }

        if (requestTopRefresh && plugin.getTopManager() != null && plugin.getMongoManager() != null
            && plugin.getMongoManager().isAvailable()) {
            plugin.getTopManager().requestRefresh();
        }
    }

    public CompletableFuture<PlayerStats> getStats(UUID uuid) {
        if (cache.containsKey(uuid)) {
            return CompletableFuture.completedFuture(cache.get(uuid));
        }

        return plugin.getMongoManager().toFuture(
            collection.find(Filters.eq("uuid", uuid.toString())).first()
        ).thenApply(doc -> {
            PlayerStats stats;
            if (doc == null) {
                stats = new PlayerStats(uuid);
            } else {
                stats = PlayerStats.fromDocument(doc);
            }
            cache.put(uuid, stats);
            return stats;
        });
    }

    public CompletableFuture<Void> saveStats(UUID uuid) {
        return queueStatsSave(uuid, false);
    }

    private CompletableFuture<Void> queueStatsSave(UUID uuid, boolean unloadWhenComplete) {
        PlayerStats stats = cache.get(uuid);
        if (stats == null) {
            return CompletableFuture.completedFuture(null);
        }

        synchronized (activeSaves) {
            if (unloadWhenComplete) {
                unloadAfterSave.add(uuid);
            }

            CompletableFuture<Void> activeSave = activeSaves.get(uuid);
            if (activeSave != null) {
                queuedSaves.add(uuid);
                return activeSave;
            }

            return startStatsSave(uuid);
        }
    }

    private CompletableFuture<Void> startStatsSave(UUID uuid) {
        CompletableFuture<Void> save = writeStats(uuid);
        activeSaves.put(uuid, save);
        save.whenComplete((ignored, throwable) -> handleStatsSaveComplete(uuid, save, throwable));
        return save;
    }

    private CompletableFuture<Void> writeStats(UUID uuid) {
        PlayerStats stats = cache.get(uuid);
        if (stats == null) {
            return CompletableFuture.completedFuture(null);
        }

        Document document;
        synchronized (stats) {
            document = stats.toDocument();
        }

        return plugin.getMongoManager().toFuture(
            collection.replaceOne(
                Filters.eq("uuid", uuid.toString()),
                document,
                new ReplaceOptions().upsert(true)
            )
        ).thenApply(result -> null);
    }

    private void handleStatsSaveComplete(UUID uuid, CompletableFuture<Void> completedSave, Throwable throwable) {
        if (throwable != null) {
            logSaveFailure(uuid, throwable);
            synchronized (activeSaves) {
                activeSaves.remove(uuid, completedSave);
            }
            return;
        }

        boolean unloadPlayer;
        synchronized (activeSaves) {
            activeSaves.remove(uuid, completedSave);

            boolean shouldSaveQueued = queuedSaves.remove(uuid) && cache.containsKey(uuid);
            if (shouldSaveQueued) {
                startStatsSave(uuid);
                return;
            }

            unloadPlayer = unloadAfterSave.remove(uuid);
        }

        if (unloadPlayer) {
            cache.remove(uuid);
            saveFailureLogAt.remove(uuid);
            if (plugin.getTopManager() != null) {
                plugin.getTopManager().invalidatePlayer(uuid);
                if (plugin.getMongoManager() != null && plugin.getMongoManager().isAvailable()) {
                    plugin.getTopManager().requestRefresh();
                }
            }
        }
    }

    private void logSaveFailure(UUID uuid, Throwable throwable) {
        long now = System.currentTimeMillis();
        Long lastLogAt = saveFailureLogAt.get(uuid);
        if (lastLogAt != null && now - lastLogAt < SAVE_FAILURE_LOG_INTERVAL_MILLIS) {
            return;
        }
        if (MongoManager.isSkippedBecauseUnavailable(throwable)) {
            return;
        }

        saveFailureLogAt.put(uuid, now);
        plugin.getLogger().warning("Nie udalo sie zapisac statystyk gracza " + uuid + ": " + throwable.getMessage());
    }

    private void retryPendingSaves() {
        for (UUID uuid : cache.keySet()) {
            queueStatsSave(uuid, unloadAfterSave.contains(uuid));
        }
    }

    public void updatePlaytime(UUID uuid) {
        PlayerStats stats = cache.get(uuid);
        if (stats != null) {
            synchronized (stats) {
                long now = System.currentTimeMillis();
                long sessionTime = Math.max(0L, now - stats.getSessionStart());
                stats.addPlaytime(sessionTime);
                stats.setSessionStart(now);
            }
        }
    }

    public void startSession(UUID uuid) {
        unloadAfterSave.remove(uuid);
        queuedSaves.remove(uuid);
        saveFailureLogAt.remove(uuid);

        org.bukkit.entity.Player player = org.bukkit.Bukkit.getPlayer(uuid);
        String playerName = player != null ? player.getName() : null;
        if (playerName != null && plugin.getTopManager() != null) {
            plugin.getTopManager().rememberPlayerName(uuid, playerName);
        }
        getStats(uuid).thenCompose(stats -> {
            synchronized (stats) {
                long now = System.currentTimeMillis();
                stats.setSessionStart(now);
                stats.setLastJoin(now);
                if (playerName != null && !playerName.isBlank()) {
                    stats.setName(playerName);
                }
            }
            return saveStats(uuid).thenRun(() -> {
                if (plugin.getTopManager() != null) {
                    plugin.getTopManager().requestRefresh();
                }
            });
        }).exceptionally(throwable -> null);
    }

    public void endSession(UUID uuid) {
        updatePlaytime(uuid);
        queueStatsSave(uuid, true);
    }

    public PlayerStats getCachedStats(UUID uuid) {
        return cache.get(uuid);
    }

    public Map<UUID, PlaytimeSnapshot> getActivePlaytimeSnapshot() {
        Map<UUID, PlaytimeSnapshot> snapshot = new HashMap<>();
        for (Map.Entry<UUID, PlayerStats> entry : cache.entrySet()) {
            PlayerStats stats = entry.getValue();
            if (stats == null) {
                continue;
            }
            synchronized (stats) {
                snapshot.put(entry.getKey(), new PlaytimeSnapshot(stats.getName(), stats.getTotalPlaytime()));
            }
        }
        return snapshot;
    }

    public CompletableFuture<Boolean> removePlaytime(UUID uuid, long amountMillis) {
        return spendShopPlaytime(uuid, amountMillis);
    }

    public CompletableFuture<Boolean> spendShopPlaytime(UUID uuid, long amountMillis) {
        if (amountMillis <= 0) {
            return CompletableFuture.completedFuture(true);
        }

        return getStats(uuid).thenCompose(stats -> {
            synchronized (stats) {
                if (stats.getShopPlaytime() < amountMillis) {
                    return CompletableFuture.completedFuture(false);
                }
                stats.addShopPlaytimeSpent(amountMillis);
            }

            return saveStats(uuid)
                .thenApply(ignored -> true)
                .exceptionally(ex -> false);
        });
    }

    public record PlaytimeSnapshot(String name, long playtime) {
    }

    public CompletableFuture<Void> resetPlaytime(UUID uuid) {
        return getStats(uuid).thenCompose(stats -> {
            synchronized (stats) {
                stats.setPlaytime(0L);
                stats.setShopPlaytimeSpent(0L);
                stats.setSessionStart(System.currentTimeMillis());
            }
            return saveStats(uuid);
        });
    }

    public static class PlayerStats {
        private final UUID uuid;
        private String name;
        private long playtime;
        private long shopPlaytimeSpent;
        private long sessionStart;
        private long firstJoin;
        private long lastJoin;

        public PlayerStats(UUID uuid) {
            this.uuid = uuid;
            this.name = "";
            this.playtime = 0;
            this.shopPlaytimeSpent = 0;
            this.sessionStart = System.currentTimeMillis();
            this.firstJoin = System.currentTimeMillis();
            this.lastJoin = System.currentTimeMillis();
        }

        public PlayerStats(UUID uuid, String name, long playtime, long firstJoin, long lastJoin) {
            this(uuid, name, playtime, 0L, firstJoin, lastJoin);
        }

        public PlayerStats(UUID uuid, String name, long playtime, long shopPlaytimeSpent, long firstJoin, long lastJoin) {
            this.uuid = uuid;
            this.name = name != null ? name : "";
            this.playtime = playtime;
            this.shopPlaytimeSpent = Math.max(0L, shopPlaytimeSpent);
            this.sessionStart = System.currentTimeMillis();
            this.firstJoin = firstJoin;
            this.lastJoin = lastJoin;
        }

        public static PlayerStats fromDocument(Document doc) {
            String nameVal = doc.getString("name");
            return new PlayerStats(
                UUID.fromString(doc.getString("uuid")),
                nameVal != null ? nameVal : "",
                readLong(doc, "playtime"),
                readLong(doc, "shopPlaytimeSpent"),
                readLong(doc, "firstJoin", System.currentTimeMillis()),
                readLong(doc, "lastJoin", System.currentTimeMillis())
            );
        }

        public Document toDocument() {
            return new Document()
                .append("uuid", uuid.toString())
                .append("name", name)
                .append("playtime", playtime)
                .append("shopPlaytimeSpent", shopPlaytimeSpent)
                .append("firstJoin", firstJoin)
                .append("lastJoin", lastJoin);
        }

        public UUID getUuid() { return uuid; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public long getPlaytime() { return playtime; }
        public void setPlaytime(long playtime) { this.playtime = playtime; }
        public void addPlaytime(long time) { this.playtime += time; }
        public long getShopPlaytimeSpent() { return shopPlaytimeSpent; }
        public void setShopPlaytimeSpent(long shopPlaytimeSpent) { this.shopPlaytimeSpent = Math.max(0L, shopPlaytimeSpent); }
        public void addShopPlaytimeSpent(long amount) { this.shopPlaytimeSpent = Math.max(0L, this.shopPlaytimeSpent + amount); }
        public long getSessionStart() { return sessionStart; }
        public void setSessionStart(long sessionStart) { this.sessionStart = sessionStart; }
        public long getFirstJoin() { return firstJoin; }
        public long getLastJoin() { return lastJoin; }
        public void setLastJoin(long lastJoin) { this.lastJoin = lastJoin; }

        public long getTotalPlaytime() {
            return playtime + (System.currentTimeMillis() - sessionStart);
        }

        public long getShopPlaytime() {
            return Math.max(0L, getTotalPlaytime() - shopPlaytimeSpent);
        }

        private static long readLong(Document doc, String key) {
            return readLong(doc, key, 0L);
        }

        private static long readLong(Document doc, String key, long fallback) {
            Object value = doc.get(key);
            if (value instanceof Number number) {
                return number.longValue();
            }
            return fallback;
        }
    }
}
