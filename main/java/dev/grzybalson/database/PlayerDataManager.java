package dev.grzybalson.database;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.reactivestreams.client.MongoCollection;
import dev.grzybalson.HollyCore;
import org.bson.Document;
import org.bukkit.Bukkit;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerDataManager {

    private final HollyCore plugin;
    private final MongoCollection<Document> collection;
    private final ConcurrentHashMap<UUID, PlayerData> cache;

    public PlayerDataManager(HollyCore plugin) {
        this.plugin = plugin;
        this.collection = plugin.getMongoManager().getCollection(
            plugin.getConfigManager().getDatabaseConfig().getPlayersCollection()
        );
        this.cache = new ConcurrentHashMap<>();
    }

    public CompletableFuture<PlayerData> loadPlayer(UUID uuid) {
        if (cache.containsKey(uuid)) {
            return CompletableFuture.completedFuture(cache.get(uuid));
        }

        return plugin.getMongoManager().toFuture(
            collection.find(Filters.eq("uuid", uuid.toString())).first()
        ).thenApply(doc -> {
            PlayerData data;
            if (doc == null) {
                data = new PlayerData(uuid);
            } else {
                data = PlayerData.fromDocument(doc);
            }
            cache.put(uuid, data);
            return data;
        });
    }

    public CompletableFuture<Void> savePlayer(UUID uuid) {
        PlayerData data = cache.get(uuid);
        if (data == null) {
            return CompletableFuture.completedFuture(null);
        }

        return plugin.getMongoManager().toFuture(
            collection.replaceOne(
                Filters.eq("uuid", uuid.toString()),
                data.toDocument(),
                new ReplaceOptions().upsert(true)
            )
        ).thenApply(result -> null);
    }

    public CompletableFuture<Void> savePlayerAsync(UUID uuid) {
        return CompletableFuture.runAsync(() -> {
            savePlayer(uuid).join();
        });
    }

    public void unloadPlayer(UUID uuid) {
        savePlayer(uuid).whenComplete((res, err) -> cache.remove(uuid));
    }

    public PlayerData getPlayerData(UUID uuid) {
        return cache.get(uuid);
    }

    public void saveAll() {
        for (UUID uuid : cache.keySet()) {
            savePlayer(uuid);
        }
    }

    public static class PlayerData {
        private final UUID uuid;
        private double balance;
        private long playtime;
        private long lastSeen;
        private int kills;
        private int deaths;

        public PlayerData(UUID uuid) {
            this.uuid = uuid;
            this.balance = HollyCore.getInstance().getConfigManager().getEconomyConfig().getStartingBalance();
            this.playtime = 0;
            this.lastSeen = System.currentTimeMillis();
            this.kills = 0;
            this.deaths = 0;
        }

        public PlayerData(UUID uuid, double balance, long playtime, long lastSeen, int kills, int deaths) {
            this.uuid = uuid;
            this.balance = balance;
            this.playtime = playtime;
            this.lastSeen = lastSeen;
            this.kills = kills;
            this.deaths = deaths;
        }

        public static PlayerData fromDocument(Document doc) {
            return new PlayerData(
                UUID.fromString(doc.getString("uuid")),
                doc.getDouble("balance"),
                doc.getLong("playtime"),
                doc.getLong("lastSeen"),
                doc.getInteger("kills", 0),
                doc.getInteger("deaths", 0)
            );
        }

        public Document toDocument() {
            return new Document()
                .append("uuid", uuid.toString())
                .append("balance", balance)
                .append("playtime", playtime)
                .append("lastSeen", lastSeen)
                .append("kills", kills)
                .append("deaths", deaths);
        }

        public UUID getUuid() { return uuid; }
        public double getBalance() { return balance; }
        public void setBalance(double balance) { this.balance = balance; }
        public void addBalance(double amount) { this.balance += amount; }
        public void removeBalance(double amount) { this.balance -= amount; }
        public long getPlaytime() { return playtime; }
        public void setPlaytime(long playtime) { this.playtime = playtime; }
        public void addPlaytime(long time) { this.playtime += time; }
        public long getLastSeen() { return lastSeen; }
        public void setLastSeen(long lastSeen) { this.lastSeen = lastSeen; }
        public int getKills() { return kills; }
        public void setKills(int kills) { this.kills = kills; }
        public void addKill() { this.kills++; }
        public int getDeaths() { return deaths; }
        public void setDeaths(int deaths) { this.deaths = deaths; }
        public void addDeath() { this.deaths++; }
    }
}
