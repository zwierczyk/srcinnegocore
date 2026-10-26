package dev.grzybalson.managers;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.reactivestreams.client.MongoCollection;
import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.SpooferBridge;
import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Stałe numery dołączenia (#1, #2, …).
 * Nadawane raz przy pierwszym wejściu, zapisywane wyłącznie w MongoDB.
 * Nigdy nie nadpisują się przy restarcie / kolejnych joinach.
 */
public class JoinInfoManager implements Listener {

    private static final String META_COUNTER_ID = "join_counter";

    private final HollyCore plugin;
    private final ConcurrentHashMap<UUID, Long> joinNumbers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> firstJoinedAt = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, String> playerNames = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, UUID> uuidByNameLower = new ConcurrentHashMap<>();
    private final AtomicLong nextJoinNumber = new AtomicLong(1L);
    private final AtomicBoolean mongoReady = new AtomicBoolean(false);
    private final Object assignLock = new Object();

    private MongoCollection<Document> mongoCollection;
    private MongoCollection<Document> mongoMetaCollection;

    public JoinInfoManager(HollyCore plugin) {
        this.plugin = plugin;
        bindMongo();
        if (plugin.getMongoManager() != null) {
            plugin.getMongoManager().onAvailable(() -> Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                bindMongo();
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (!isFakePlayer(online)) {
                        ensureRegistered(online.getUniqueId(), online.getName());
                    }
                }
            }));
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!isFakePlayer(online)) {
                ensureRegistered(online.getUniqueId(), online.getName());
            }
        }
    }

    private void bindMongo() {
        if (plugin.getMongoManager() == null || !plugin.getMongoManager().isAvailable()) {
            mongoReady.set(false);
            plugin.getLogger().warning("[JoinInfo] MongoDB niedostępne — numery dołączenia nie będą zapisywane.");
            return;
        }
        try {
            mongoCollection = plugin.getMongoManager().getCollection("join_info");
            mongoMetaCollection = plugin.getMongoManager().getCollection("join_info_meta");
            ensureIndexes();
            loadMongoIntoCache();
            mongoReady.set(true);
            plugin.getLogger().info("[JoinInfo] MongoDB OK — " + joinNumbers.size()
                + " numerów (następny: #" + nextJoinNumber.get() + ").");
        } catch (Throwable t) {
            mongoReady.set(false);
            plugin.getLogger().warning("[JoinInfo] Mongo niedostępne: " + t.getMessage());
        }
    }

    private void ensureIndexes() {
        if (mongoCollection == null) {
            return;
        }
        plugin.getMongoManager().toFuture(
            mongoCollection.createIndex(Indexes.ascending("uuid"), new IndexOptions().unique(true))
        );
        plugin.getMongoManager().toFuture(
            mongoCollection.createIndex(Indexes.ascending("join_number"), new IndexOptions().unique(true))
        );
        plugin.getMongoManager().toFuture(
            mongoCollection.createIndex(Indexes.ascending("player_name"))
        );
    }

    private void loadMongoIntoCache() {
        if (mongoCollection == null || plugin.getMongoManager() == null || !plugin.getMongoManager().isAvailable()) {
            return;
        }
        try {
            List<Document> docs = plugin.getMongoManager()
                .toListFuture(mongoCollection.find())
                .join();
            if (docs == null || docs.isEmpty()) {
                syncCounterFromCache();
                return;
            }
            for (Document doc : docs) {
                String uuidStr = doc.getString("uuid");
                if (uuidStr == null || uuidStr.isBlank()) {
                    continue;
                }
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    long number = readLong(doc, "join_number");
                    if (number <= 0L) {
                        continue;
                    }
                    long firstJoined = readLong(doc, "first_joined");
                    String name = doc.getString("player_name");
                    cacheRecord(uuid, name, number, firstJoined > 0L ? firstJoined : System.currentTimeMillis());
                } catch (IllegalArgumentException ignored) {
                }
            }
            recomputeNextNumber();
            syncCounterFromCache();
        } catch (Exception e) {
            plugin.getLogger().warning("[JoinInfo] Błąd odczytu Mongo: " + e.getMessage());
        }
    }

    private void syncCounterFromCache() {
        if (mongoMetaCollection == null || plugin.getMongoManager() == null || !plugin.getMongoManager().isAvailable()) {
            return;
        }
        long max = Math.max(0L, nextJoinNumber.get() - 1L);
        try {
            plugin.getMongoManager().toFuture(
                mongoMetaCollection.updateOne(
                    Filters.eq("_id", META_COUNTER_ID),
                    Updates.combine(
                        Updates.setOnInsert("_id", META_COUNTER_ID),
                        Updates.max("seq", max)
                    ),
                    new UpdateOptions().upsert(true)
                )
            ).join();
        } catch (Exception ignored) {
        }
    }

    private void cacheRecord(UUID uuid, String name, long number, long firstJoined) {
        joinNumbers.put(uuid, number);
        firstJoinedAt.put(uuid, firstJoined);
        if (name != null && !name.isBlank()) {
            rememberName(uuid, name);
        }
    }

    private void recomputeNextNumber() {
        long maxNumber = joinNumbers.values().stream().mapToLong(Long::longValue).max().orElse(0L);
        nextJoinNumber.updateAndGet(current -> Math.max(current, maxNumber + 1L));
    }

    private static long readLong(Document doc, String key) {
        Object raw = doc.get(key);
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw instanceof String text) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }

    private boolean isFakePlayer(Player player) {
        if (player == null) {
            return true;
        }
        try {
            if (SpooferBridge.isFakeBot(player.getName())) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private boolean isFakeName(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        try {
            return SpooferBridge.isFakeBot(name);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Gwarantuje stały numer: cache → Mongo → dopiero nowy numer zapisany w Mongo.
     * Bez Mongo nie nadaje trwałego numeru (żeby nie „losować” po restarcie).
     */
    public long ensureRegistered(UUID uuid, String name) {
        if (uuid == null || isFakeName(name)) {
            return 0L;
        }

        Long existing = joinNumbers.get(uuid);
        if (existing != null && existing > 0L) {
            touchNameOnly(uuid, name);
            return existing;
        }

        synchronized (assignLock) {
            existing = joinNumbers.get(uuid);
            if (existing != null && existing > 0L) {
                touchNameOnly(uuid, name);
                return existing;
            }

            Long fromMongo = fetchNumberFromMongo(uuid);
            if (fromMongo != null && fromMongo > 0L) {
                long firstJoined = firstJoinedAt.getOrDefault(uuid, System.currentTimeMillis());
                cacheRecord(uuid, name, fromMongo, firstJoined);
                touchNameOnly(uuid, name);
                bumpNext(fromMongo);
                return fromMongo;
            }

            if (!mongoReady.get() || mongoCollection == null
                || plugin.getMongoManager() == null || !plugin.getMongoManager().isAvailable()) {
                plugin.getLogger().warning("[JoinInfo] Brak Mongo — pomijam nadanie numeru dla " + name);
                return 0L;
            }

            long now = System.currentTimeMillis();
            long assigned = allocateNextNumber();
            Long confirmed = persistMongoFirstJoin(uuid, name, assigned, now);
            if (confirmed == null || confirmed <= 0L) {
                return 0L;
            }
            cacheRecord(uuid, name, confirmed, now);
            bumpNext(confirmed);
            return confirmed;
        }
    }

    private void bumpNext(long number) {
        if (number >= nextJoinNumber.get()) {
            nextJoinNumber.set(number + 1L);
        }
    }

    /** Atomowy licznik w Mongo. */
    private long allocateNextNumber() {
        if (mongoMetaCollection != null && plugin.getMongoManager() != null && plugin.getMongoManager().isAvailable()) {
            try {
                // Nie wolno łączyć $setOnInsert(seq) + $inc(seq) — ConflictingUpdateOperators.
                // $inc na brakującym polu traktuje je jak 0, więc insert dostaje seq=1.
                Document result = plugin.getMongoManager().toFuture(
                    mongoMetaCollection.findOneAndUpdate(
                        Filters.eq("_id", META_COUNTER_ID),
                        Updates.combine(
                            Updates.setOnInsert("_id", META_COUNTER_ID),
                            Updates.inc("seq", 1L)
                        ),
                        new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER)
                    )
                ).join();
                if (result != null) {
                    long seq = readLong(result, "seq");
                    if (seq > 0L) {
                        nextJoinNumber.updateAndGet(current -> Math.max(current, seq + 1L));
                        return seq;
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[JoinInfo] Licznik Mongo: " + e.getMessage());
            }
        }
        return nextJoinNumber.getAndIncrement();
    }

    private Long persistMongoFirstJoin(UUID uuid, String name, long number, long firstJoined) {
        if (mongoCollection == null || plugin.getMongoManager() == null || !plugin.getMongoManager().isAvailable()) {
            return null;
        }
        try {
            // player_name tylko w $set — nie może być jednocześnie w $setOnInsert.
            Document setOnInsert = new Document("uuid", uuid.toString())
                .append("join_number", number)
                .append("first_joined", firstJoined);

            Document saved = plugin.getMongoManager().toFuture(
                mongoCollection.findOneAndUpdate(
                    Filters.eq("uuid", uuid.toString()),
                    Updates.combine(
                        new Document("$setOnInsert", setOnInsert),
                        Updates.set("player_name", name != null ? name : "")
                    ),
                    new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER)
                )
            ).join();

            if (saved != null) {
                long loaded = readLong(saved, "join_number");
                return loaded > 0L ? loaded : number;
            }
            return fetchNumberFromMongo(uuid);
        } catch (Exception e) {
            plugin.getLogger().warning("[JoinInfo] Zapis Mongo: " + e.getMessage());
            Long fromDb = fetchNumberFromMongo(uuid);
            if (fromDb != null) {
                return fromDb;
            }
            for (int attempt = 0; attempt < 12; attempt++) {
                long retry = allocateNextNumber();
                try {
                    Document setOnInsert = new Document("uuid", uuid.toString())
                        .append("join_number", retry)
                        .append("first_joined", firstJoined);
                    Document saved = plugin.getMongoManager().toFuture(
                        mongoCollection.findOneAndUpdate(
                            Filters.eq("uuid", uuid.toString()),
                            Updates.combine(
                                new Document("$setOnInsert", setOnInsert),
                                Updates.set("player_name", name != null ? name : "")
                            ),
                            new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER)
                        )
                    ).join();
                    if (saved != null) {
                        long loaded = readLong(saved, "join_number");
                        if (loaded > 0L) {
                            return loaded;
                        }
                    }
                } catch (Exception ignored) {
                }
            }
            return null;
        }
    }

    private void touchNameOnly(UUID uuid, String name) {
        if (name == null || name.isBlank()) {
            return;
        }
        String current = playerNames.get(uuid);
        if (name.equals(current)) {
            return;
        }
        rememberName(uuid, name);
        updateMongoNameAsync(uuid, name);
    }

    private Long fetchNumberFromMongo(UUID uuid) {
        if (mongoCollection == null || plugin.getMongoManager() == null || !plugin.getMongoManager().isAvailable()) {
            return null;
        }
        try {
            Document doc = plugin.getMongoManager()
                .toFuture(mongoCollection.find(Filters.eq("uuid", uuid.toString())).first())
                .join();
            if (doc == null) {
                return null;
            }
            long loaded = readLong(doc, "join_number");
            if (loaded <= 0L) {
                return null;
            }
            long firstJoined = readLong(doc, "first_joined");
            if (firstJoined > 0L) {
                firstJoinedAt.put(uuid, firstJoined);
            }
            String loadedName = doc.getString("player_name");
            if (loadedName != null && !loadedName.isBlank()) {
                rememberName(uuid, loadedName);
            }
            return loaded;
        } catch (Exception e) {
            plugin.getLogger().warning("[JoinInfo] Odczyt UUID z Mongo: " + e.getMessage());
            return null;
        }
    }

    private void updateMongoNameAsync(UUID uuid, String name) {
        if (mongoCollection == null || plugin.getMongoManager() == null || !plugin.getMongoManager().isAvailable()) {
            return;
        }
        if (name == null || name.isBlank()) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.getMongoManager().toFuture(
                    mongoCollection.updateOne(
                        Filters.eq("uuid", uuid.toString()),
                        Updates.set("player_name", name)
                    )
                ).join();
            } catch (Exception e) {
                plugin.getLogger().warning("[JoinInfo] Update nicku: " + e.getMessage());
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        if (isFakeName(event.getName())) {
            return;
        }
        ensureRegistered(event.getUniqueId(), event.getName());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (isFakePlayer(player)) {
            return;
        }
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        Long existing = joinNumbers.get(uuid);
        if (existing != null && existing > 0L) {
            touchNameOnly(uuid, name);
            return;
        }
        // PreLogin już robi Mongo async — nie blokuj ticka joinu .join()-em.
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> ensureRegistered(uuid, name));
    }

    public long getJoinNumber(UUID uuid, String name) {
        if (uuid == null) {
            return 0L;
        }
        Long number = joinNumbers.get(uuid);
        if (number != null && number > 0L) {
            return number;
        }

        // PlaceholderAPI / sidebar na głównym wątku — nigdy nie czekaj na Mongo.
        if (Bukkit.isPrimaryThread()) {
            final String resolveName = name;
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                if (joinNumbers.get(uuid) != null) {
                    return;
                }
                ensureRegistered(uuid, resolveName);
            });
            return 0L;
        }

        Long fromMongo = fetchNumberFromMongo(uuid);
        if (fromMongo != null && fromMongo > 0L) {
            cacheRecord(uuid, name, fromMongo, firstJoinedAt.getOrDefault(uuid, System.currentTimeMillis()));
            bumpNext(fromMongo);
            return fromMongo;
        }

        Player online = Bukkit.getPlayer(uuid);
        if (online != null && online.isOnline() && !isFakePlayer(online)) {
            return ensureRegistered(uuid, name != null ? name : online.getName());
        }
        return 0L;
    }

    public String getJoinInfo(UUID uuid, String name) {
        long number = getJoinNumber(uuid, name);
        if (number <= 0L) {
            return "#…";
        }
        return "#" + number;
    }

    public long getTotalJoinedUsers() {
        return Math.max(joinNumbers.size(), Math.max(0L, nextJoinNumber.get() - 1L));
    }

    public UUID getUuidByName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return uuidByNameLower.get(name.toLowerCase(java.util.Locale.ROOT));
    }

    private void rememberName(UUID uuid, String name) {
        if (uuid == null || name == null || name.isBlank()) {
            return;
        }
        String previous = playerNames.put(uuid, name);
        if (previous != null && !previous.equalsIgnoreCase(name)) {
            uuidByNameLower.remove(previous.toLowerCase(java.util.Locale.ROOT), uuid);
        }
        uuidByNameLower.put(name.toLowerCase(java.util.Locale.ROOT), uuid);
    }

    public String getPlayerName(UUID uuid) {
        return uuid == null ? null : playerNames.get(uuid);
    }

    public java.util.Collection<String> getKnownPlayerNames() {
        return java.util.Collections.unmodifiableCollection(playerNames.values());
    }

    public void shutdown() {
        // Mongo managed by MongoManager.
    }
}
