package dev.grzybalson.database;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.reactivestreams.client.MongoCollection;
import dev.grzybalson.HollyCore;
import org.bson.Document;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class IgnoreDataManager {

    private final HollyCore plugin;
    private final MongoCollection<Document> collection;
    private final Map<UUID, Set<UUID>> ignoreCache;
    private final Set<UUID> ignoreAllCache;
    private final Set<UUID> loadedPlayers;

    public IgnoreDataManager(HollyCore plugin) {
        this.plugin = plugin;
        this.collection = plugin.getMongoManager().getCollection("ignores");
        this.ignoreCache = new ConcurrentHashMap<>();
        this.ignoreAllCache = ConcurrentHashMap.newKeySet();
        this.loadedPlayers = ConcurrentHashMap.newKeySet();
    }

    public CompletableFuture<Void> loadPlayer(UUID uuid) {
        return plugin.getMongoManager().toFuture(
            collection.find(Filters.eq("uuid", uuid.toString())).first()
        ).thenAccept(doc -> {
            if (loadedPlayers.contains(uuid)) {
                return;
            }
            loadedPlayers.add(uuid);
            if (doc != null) {
                boolean ignoreAll = doc.getBoolean("ignoreAll", false);
                if (ignoreAll) {
                    ignoreAllCache.add(uuid);
                }

                List<String> ignoredList = doc.getList("ignored", String.class);
                if (ignoredList != null && !ignoredList.isEmpty()) {
                    Set<UUID> ignored = ConcurrentHashMap.newKeySet();
                    for (String s : ignoredList) {
                        ignored.add(UUID.fromString(s));
                    }
                    ignoreCache.put(uuid, ignored);
                }
            }
        });
    }

    public CompletableFuture<Void> savePlayer(UUID uuid) {
        Set<UUID> ignored = ignoreCache.getOrDefault(uuid, Collections.emptySet());
        boolean ignoreAll = ignoreAllCache.contains(uuid);

        List<String> ignoredStrings = new ArrayList<>();
        for (UUID id : ignored) {
            ignoredStrings.add(id.toString());
        }

        Document doc = new Document()
            .append("uuid", uuid.toString())
            .append("ignoreAll", ignoreAll)
            .append("ignored", ignoredStrings);

        return plugin.getMongoManager().toFuture(
            collection.replaceOne(
                Filters.eq("uuid", uuid.toString()),
                doc,
                new ReplaceOptions().upsert(true)
            )
        ).thenApply(result -> null);
    }

    public void addIgnore(UUID player, UUID target) {
        ignoreCache.computeIfAbsent(player, k -> ConcurrentHashMap.newKeySet()).add(target);
        savePlayer(player);
    }

    public void removeIgnore(UUID player, UUID target) {
        Set<UUID> ignored = ignoreCache.get(player);
        if (ignored != null) {
            ignored.remove(target);
            savePlayer(player);
        }
    }

    public void setIgnoreAll(UUID player, boolean ignoreAll) {
        loadedPlayers.add(player);
        if (ignoreAll) {
            ignoreAllCache.add(player);
        } else {
            ignoreAllCache.remove(player);
        }
        savePlayer(player);
    }

    public boolean isIgnored(UUID player, UUID target) {
        if (ignoreAllCache.contains(player)) {
            return true;
        }
        Set<UUID> ignored = ignoreCache.get(player);
        return ignored != null && ignored.contains(target);
    }

    public boolean isIgnoringAll(UUID player) {
        return ignoreAllCache.contains(player);
    }

    public boolean toggleIgnore(UUID player, UUID target) {
        loadedPlayers.add(player);
        Set<UUID> ignored = ignoreCache.computeIfAbsent(player, k -> ConcurrentHashMap.newKeySet());
        boolean wasIgnored = ignored.contains(target);
        if (wasIgnored) {
            ignored.remove(target);
        } else {
            ignored.add(target);
        }
        savePlayer(player);
        return !wasIgnored;
    }

    public void unloadPlayer(UUID uuid) {
        savePlayer(uuid).whenComplete((res, err) -> {
            ignoreCache.remove(uuid);
            ignoreAllCache.remove(uuid);
            loadedPlayers.remove(uuid);
        });
    }
}
