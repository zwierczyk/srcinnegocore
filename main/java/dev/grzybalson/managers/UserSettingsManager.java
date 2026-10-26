package dev.grzybalson.managers;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.reactivestreams.client.MongoCollection;
import dev.grzybalson.HollyCore;
import org.bson.Document;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class UserSettingsManager {

    private final HollyCore plugin;
    private final MongoCollection<Document> collection;
    private final Map<UUID, UserSettings> cache = new ConcurrentHashMap<>();

    public UserSettingsManager(HollyCore plugin) {
        this.plugin = plugin;
        MongoCollection<Document> bound = null;
        if (plugin.getMongoManager() != null) {
            try {
                bound = plugin.getMongoManager().getCollection("player_settings");
            } catch (IllegalStateException ignored) {
            }
        }
        this.collection = bound;
    }

    public CompletableFuture<UserSettings> loadPlayer(UUID uuid) {
        if (plugin.getMongoManager() == null || collection == null) {
            UserSettings settings = UserSettings.defaults();
            cache.put(uuid, settings);
            return CompletableFuture.completedFuture(settings);
        }
        return plugin.getMongoManager().toFuture(collection.find(Filters.eq("uuid", uuid.toString())).first())
            .thenApply(document -> {
                UserSettings settings = fromDocument(document);
                cache.put(uuid, settings);
                return settings;
            })
            .exceptionally(throwable -> {
                plugin.getLogger().warning("Nie udalo sie wczytac ustawien gracza " + uuid + ": " + throwable.getMessage());
                UserSettings settings = UserSettings.defaults();
                cache.put(uuid, settings);
                return settings;
            });
    }

    public UserSettings getSettings(UUID uuid) {
        return cache.computeIfAbsent(uuid, ignored -> UserSettings.defaults());
    }

    public boolean isRechargeMusicEnabled(UUID uuid) {
        return getSettings(uuid).rechargeMusic();
    }

    public boolean isPrivateMessagesEnabled(UUID uuid) {
        return getSettings(uuid).privateMessages();
    }

    public boolean isKillInfoEnabled(UUID uuid) {
        return getSettings(uuid).killInfo();
    }

    public boolean isKillTotemEffectEnabled(UUID uuid) {
        return getSettings(uuid).killTotemEffect();
    }

    public boolean toggleRechargeMusic(UUID uuid) {
        UserSettings current = getSettings(uuid);
        UserSettings updated = current.withRechargeMusic(!current.rechargeMusic());
        cache.put(uuid, updated);
        save(uuid, updated);
        return updated.rechargeMusic();
    }

    public boolean togglePrivateMessages(UUID uuid) {
        UserSettings current = getSettings(uuid);
        UserSettings updated = current.withPrivateMessages(!current.privateMessages());
        cache.put(uuid, updated);
        save(uuid, updated);
        return updated.privateMessages();
    }

    public boolean toggleKillInfo(UUID uuid) {
        UserSettings current = getSettings(uuid);
        UserSettings updated = current.withKillInfo(!current.killInfo());
        cache.put(uuid, updated);
        save(uuid, updated);
        return updated.killInfo();
    }

    public boolean toggleKillTotemEffect(UUID uuid) {
        UserSettings current = getSettings(uuid);
        UserSettings updated = current.withKillTotemEffect(!current.killTotemEffect());
        cache.put(uuid, updated);
        save(uuid, updated);
        return updated.killTotemEffect();
    }

    public void unloadPlayer(UUID uuid) {
        cache.remove(uuid);
    }

    private UserSettings fromDocument(Document document) {
        if (document == null) {
            return UserSettings.defaults();
        }
        return new UserSettings(
            document.getBoolean("rechargeMusic", true),
            document.getBoolean("privateMessages", true),
            document.getBoolean("killInfo", true),
            document.getBoolean("killTotemEffect", true)
        );
    }

    private void save(UUID uuid, UserSettings settings) {
        if (plugin.getMongoManager() == null || collection == null) {
            return;
        }
        Document document = new Document("uuid", uuid.toString())
            .append("rechargeMusic", settings.rechargeMusic())
            .append("privateMessages", settings.privateMessages())
            .append("killInfo", settings.killInfo())
            .append("killTotemEffect", settings.killTotemEffect())
            .append("updatedAt", System.currentTimeMillis());

        plugin.getMongoManager().toFuture(collection.replaceOne(
            Filters.eq("uuid", uuid.toString()),
            document,
            new ReplaceOptions().upsert(true)
        )).exceptionally(throwable -> {
            plugin.getLogger().warning("Nie udalo sie zapisac ustawien gracza " + uuid + ": " + throwable.getMessage());
            return null;
        });
    }

    public record UserSettings(boolean rechargeMusic, boolean privateMessages, boolean killInfo, boolean killTotemEffect) {
        public static UserSettings defaults() {
            return new UserSettings(true, true, true, true);
        }

        public UserSettings withRechargeMusic(boolean value) {
            return new UserSettings(value, privateMessages, killInfo, killTotemEffect);
        }

        public UserSettings withPrivateMessages(boolean value) {
            return new UserSettings(rechargeMusic, value, killInfo, killTotemEffect);
        }

        public UserSettings withKillInfo(boolean value) {
            return new UserSettings(rechargeMusic, privateMessages, value, killTotemEffect);
        }

        public UserSettings withKillTotemEffect(boolean value) {
            return new UserSettings(rechargeMusic, privateMessages, killInfo, value);
        }
    }
}
