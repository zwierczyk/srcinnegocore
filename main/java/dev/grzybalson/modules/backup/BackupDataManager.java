package dev.grzybalson.modules.backup;

import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import com.mongodb.reactivestreams.client.MongoCollection;
import org.bson.conversions.Bson;
import com.mongodb.reactivestreams.client.MongoDatabase;
import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ItemSerializer;
import dev.grzybalson.utils.PlayerInventories;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class BackupDataManager {

    private final HollyCore plugin;
    private final MongoCollection<Document> snapshotsCollection;
    private final MongoCollection<Document> deathsCollection;
    private final MongoCollection<Document> pendingBackupsCollection;
    private final MongoCollection<Document> pendingStatRestoresCollection;
    private final MongoCollection<Document> marketSnapshotsCollection;
    private final MongoCollection<Document> queuedRollbackRestoresCollection;
    private final MongoCollection<Document> auxSnapshotsCollection;

    public BackupDataManager(HollyCore plugin) {
        this.plugin = plugin;
        this.snapshotsCollection = plugin.getMongoManager().getCollection("player_snapshots");
        this.deathsCollection = plugin.getMongoManager().getCollection("player_deaths");
        this.pendingBackupsCollection = plugin.getMongoManager().getCollection("pending_backups");
        this.pendingStatRestoresCollection = plugin.getMongoManager().getCollection("pending_backup_stat_restores");
        this.marketSnapshotsCollection = plugin.getMongoManager().getCollection("market_snapshots");
        this.queuedRollbackRestoresCollection = plugin.getMongoManager().getCollection("queued_rollback_restores");
        this.auxSnapshotsCollection = plugin.getMongoManager().getCollection("aux_snapshots");

        createIndexes();
    }

    private void createIndexes() {
        int ttlHours = plugin.getConfigManager().getBackupConfig().getSnapshotTtlHours();

        plugin.getMongoManager().toFuture(
            snapshotsCollection.createIndex(
                Indexes.ascending("createdAt"),
                new IndexOptions().expireAfter((long) ttlHours, TimeUnit.HOURS)
            )
        );

        plugin.getMongoManager().toFuture(
            marketSnapshotsCollection.createIndex(
                Indexes.ascending("createdAt"),
                new IndexOptions().expireAfter((long) ttlHours, TimeUnit.HOURS)
            )
        );

        plugin.getMongoManager().toFuture(
            auxSnapshotsCollection.createIndex(
                Indexes.ascending("createdAt"),
                new IndexOptions().expireAfter((long) ttlHours, TimeUnit.HOURS)
            )
        );

        plugin.getMongoManager().toFuture(snapshotsCollection.createIndex(Indexes.ascending("playerUuid")));
        plugin.getMongoManager().toFuture(snapshotsCollection.createIndex(
            Indexes.compoundIndex(Indexes.ascending("type"), Indexes.descending("createdAt"))));
        plugin.getMongoManager().toFuture(snapshotsCollection.createIndex(
            Indexes.compoundIndex(Indexes.ascending("type"), Indexes.ascending("playerUuid"), Indexes.descending("createdAt"))));
        plugin.getMongoManager().toFuture(snapshotsCollection.createIndex(Indexes.ascending("playerName")));
        plugin.getMongoManager().toFuture(deathsCollection.createIndex(Indexes.ascending("playerUuid")));
        plugin.getMongoManager().toFuture(deathsCollection.createIndex(Indexes.compoundIndex(Indexes.ascending("playerUuid"), Indexes.descending("createdAt"))));
        plugin.getMongoManager().toFuture(deathsCollection.createIndex(Indexes.ascending("playerName")));
        plugin.getMongoManager().toFuture(deathsCollection.createIndex(Indexes.ascending("publicId")));
        plugin.getMongoManager().toFuture(pendingBackupsCollection.createIndex(Indexes.ascending("playerUuid")));
        plugin.getMongoManager().toFuture(pendingBackupsCollection.createIndex(Indexes.ascending("status")));
        plugin.getMongoManager().toFuture(pendingBackupsCollection.createIndex(Indexes.compoundIndex(Indexes.ascending("playerUuid"), Indexes.ascending("status"))));
        plugin.getMongoManager().toFuture(pendingStatRestoresCollection.createIndex(Indexes.ascending("playerUuid"), new IndexOptions().unique(true)));
        plugin.getMongoManager().toFuture(queuedRollbackRestoresCollection.createIndex(Indexes.ascending("playerUuid"), new IndexOptions().unique(true)));
        configureDeathsExpiry();
    }

    /**
     * Backupy śmierci NIE dzielą TTL ze snapshotami (48h). Stary indeks Mongo
     * zostaje na kolekcji nawet po zmianie kodu — trzeba go zrzucić, inaczej
     * TTL dalej kasuje wszystkie backupy gracza.
     */
    private void configureDeathsExpiry() {
        int deathsTtlHours = Math.max(0, plugin.getConfigManager().getBackupConfig().getDeathsTtlHours());
        plugin.getMongoManager().toListFuture(deathsCollection.listIndexes()).thenCompose(indexes -> {
            List<CompletableFuture<?>> drops = new ArrayList<>();
            if (indexes != null) {
                for (Document index : indexes) {
                    if (index.get("expireAfterSeconds") == null) {
                        continue;
                    }
                    String name = index.getString("name");
                    if (name == null || "_id_".equals(name)) {
                        continue;
                    }
                    plugin.getLogger().warning("[Backup] player_deaths ma TTL index '" + name
                        + "' — to kasowało backupy graczy. Usuwam.");
                    drops.add(plugin.getMongoManager().toFuture(deathsCollection.dropIndex(name)));
                }
            }
            return CompletableFuture.allOf(drops.toArray(new CompletableFuture[0]));
        }).thenCompose(v -> {
            if (deathsTtlHours <= 0) {
                return CompletableFuture.completedFuture(null);
            }
            return plugin.getMongoManager().toFuture(
                deathsCollection.createIndex(
                    Indexes.ascending("createdAt"),
                    new IndexOptions().expireAfter((long) deathsTtlHours, TimeUnit.HOURS)
                )
            ).thenApply(result -> null);
        }).exceptionally(ex -> {
            plugin.getLogger().warning("[Backup] Nie udało się poprawić TTL player_deaths: " + ex.getMessage());
            return null;
        });
    }

    public CompletableFuture<Void> saveMarketSnapshot() {
        if (plugin.getMarketManager() == null || plugin.getMarketManager().getDataManager() == null) {
            return CompletableFuture.completedFuture(null);
        }
        var mongo = plugin.getMongoManager();
        var itemsFut = mongo.toListFuture(mongo.getCollection("market_items").find());
        var expiredFut = mongo.toListFuture(mongo.getCollection("market_expired_items").find());
        var paymentsFut = mongo.toListFuture(mongo.getCollection("offline_payments").find());

        return CompletableFuture.allOf(itemsFut, expiredFut, paymentsFut).thenCompose(v -> {
            List<Document> activeItems = itemsFut.join();
            List<Document> expiredItems = expiredFut.join();
            List<Document> offlinePayments = paymentsFut.join();

            Document snapshot = new Document()
                .append("createdAt", Date.from(Instant.now()))
                .append("activeItems", activeItems)
                .append("expiredItems", expiredItems)
                .append("offlinePayments", offlinePayments);

            return mongo.toFuture(marketSnapshotsCollection.insertOne(snapshot)).thenApply(res -> (Void) null);
        }).exceptionally(ex -> {
            plugin.getLogger().warning("[Backup] Nie udało się zapisać snapshotu rynku: " + ex.getMessage());
            return null;
        });
    }

    public CompletableFuture<Void> saveGlobalSnapshot(List<PlayerSnapshotData> snapshots) {
        CompletableFuture<Void> marketFuture = saveMarketSnapshot();
        CompletableFuture<Void> auxFuture = saveAuxiliarySnapshots();
        if (snapshots.isEmpty()) {
            return CompletableFuture.allOf(marketFuture, auxFuture);
        }

        List<Document> documents = new ArrayList<>();
        Date now = Date.from(Instant.now());

        for (PlayerSnapshotData snapshot : snapshots) {
            boolean hasInventory = snapshot.hasInventory();
            Document doc = new Document()
                .append("playerUuid", snapshot.playerUuid().toString())
                .append("playerName", snapshot.playerName())
                .append("hasInventory", hasInventory)
                .append("economyBalance", snapshot.economyBalance())
                .append("walletBalance", snapshot.walletBalance())
                .append("createdAt", now)
                .append("type", "global_snapshot");
            if (hasInventory) {
                doc.append("inventory", ItemSerializer.serializeItems(snapshot.inventory()))
                    .append("armor", ItemSerializer.serializeItems(snapshot.armor()))
                    .append("enderChest", ItemSerializer.serializeItems(snapshot.enderChest()));
            }
            documents.add(doc);
        }

        return plugin.getMongoManager().toFuture(
            snapshotsCollection.insertMany(documents)
        ).thenCompose(result -> CompletableFuture.allOf(marketFuture, auxFuture)).thenApply(result -> null);
    }

    /**
     * Zapisuje stan kolekcji poza EQ gracza: pełny MSLF EC, kostiumy/pety, torby kangura.
     */
    public CompletableFuture<Void> saveAuxiliarySnapshots() {
        if (plugin.getMongoManager() == null || !plugin.getMongoManager().isAvailable()) {
            return CompletableFuture.completedFuture(null);
        }
        var mongo = plugin.getMongoManager();
        List<String> names = auxiliaryCollectionNames();
        List<CompletableFuture<Document>> dumps = new ArrayList<>(names.size());
        for (String name : names) {
            dumps.add(mongo.toListFuture(mongo.getCollection(name).find()).thenApply(docs ->
                new Document("name", name).append("documents", docs != null ? docs : List.of())
            ).exceptionally(ex -> {
                plugin.getLogger().warning("[Backup] Nie udało się zrzucić kolekcji " + name + ": " + ex.getMessage());
                return new Document("name", name).append("documents", List.of());
            }));
        }
        return CompletableFuture.allOf(dumps.toArray(new CompletableFuture[0])).thenCompose(v -> {
            List<Document> collections = new ArrayList<>(dumps.size());
            for (CompletableFuture<Document> dump : dumps) {
                collections.add(dump.join());
            }
            Document snapshot = new Document()
                .append("createdAt", Date.from(Instant.now()))
                .append("collections", collections);
            return mongo.toFuture(auxSnapshotsCollection.insertOne(snapshot)).thenApply(res -> (Void) null);
        }).exceptionally(ex -> {
            plugin.getLogger().warning("[Backup] aux snapshot fail: " + ex.getMessage());
            return null;
        });
    }

    public CompletableFuture<Document> getLatestAuxSnapshotBefore(Date targetDate) {
        return plugin.getMongoManager().toFuture(
            auxSnapshotsCollection.find(Filters.lte("createdAt", targetDate))
                .sort(Sorts.descending("createdAt"))
                .first()
        );
    }

    public CompletableFuture<Void> restoreAuxiliarySnapshot(Document snapshot) {
        if (snapshot == null || plugin.getMongoManager() == null || !plugin.getMongoManager().isAvailable()) {
            return CompletableFuture.completedFuture(null);
        }
        List<Document> collections = snapshot.getList("collections", Document.class);
        if (collections == null || collections.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        var mongo = plugin.getMongoManager();
        List<CompletableFuture<?>> futures = new ArrayList<>();
        for (Document entry : collections) {
            String name = entry.getString("name");
            if (name == null || name.isBlank()) {
                continue;
            }
            List<Document> documents = entry.getList("documents", Document.class);
            List<Document> safeDocs = documents != null ? documents : List.of();
            futures.add(
                mongo.toFuture(mongo.getCollection(name).deleteMany(new Document()))
                    .thenCompose(ignored -> {
                        if (safeDocs.isEmpty()) {
                            return CompletableFuture.completedFuture(null);
                        }
                        return mongo.toFuture(mongo.getCollection(name).insertMany(safeDocs));
                    })
            );
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenApply(v -> null);
    }

    public List<String> auxiliaryCollectionNames() {
        List<String> names = new ArrayList<>();
        names.add("mslf_enderchest");
        names.add(prefixedCollection("mslf_kostiumy"));
        names.add(prefixedCollection("mslf_pety"));
        names.add(prefixedCollection("szafka_kostiumy"));
        names.add(prefixedCollection("szafka_pety"));
        names.add(prefixedCollection("kangaroo_bags"));
        return names;
    }

    private String prefixedCollection(String base) {
        String prefix = plugin.getConfigManager().getDatabaseConfig().getDbPrefix();
        if (prefix != null && !prefix.isBlank()) {
            return prefix + "_" + base;
        }
        return base;
    }

    public static boolean hasAnyItems(ItemStack[] items) {
        if (items == null || items.length == 0) {
            return false;
        }
        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir() && item.getAmount() > 0) {
                return true;
            }
        }
        return false;
    }

    public CompletableFuture<Void> saveDeathSnapshot(UUID playerUuid, String playerName,
                                                     ItemStack[] inventory, ItemStack[] armor,
                                                     String deathCause, String killerName, String location,
                                                     int preDeathElo) {
        inventory = PlayerInventories.withoutEquippedArmorCopies(inventory, armor);
        if (!hasAnyItems(inventory) && !hasAnyItems(armor)) {
            return CompletableFuture.completedFuture(null);
        }

        Document doc = new Document()
            .append("publicId", BackupIds.newRandom())
            .append("playerUuid", playerUuid.toString())
            .append("playerName", playerName)
            .append("inventory", ItemSerializer.serializeItems(inventory))
            .append("armor", ItemSerializer.serializeItems(armor))
            .append("deathCause", deathCause)
            .append("killerName", killerName != null ? killerName : "Brak")
            .append("location", location)
            .append("preDeathElo", preDeathElo)
            .append("createdAt", Date.from(Instant.now()))
            .append("type", "death");

        return plugin.getMongoManager().toFuture(
            deathsCollection.insertOne(doc)
        ).thenCompose(result -> trimOldDeaths(playerUuid));
    }

    private CompletableFuture<Void> trimOldDeaths(UUID playerUuid) {
        int maxDeathsPerPlayer = plugin.getConfigManager().getBackupConfig().getMaxDeathsPerPlayer();
        if (maxDeathsPerPlayer <= 0) {
            return CompletableFuture.completedFuture(null);
        }

        return plugin.getMongoManager().toListFuture(
            deathsCollection.find(Filters.and(
                    Filters.eq("playerUuid", playerUuid.toString()),
                    Filters.exists("createdAt", true)
                ))
                .sort(Sorts.descending("createdAt"))
                .projection(Projections.include("_id"))
                .skip(maxDeathsPerPlayer)
        ).thenCompose(documents -> {
            if (documents.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }

            List<ObjectId> idsToDelete = new ArrayList<>();
            for (Document document : documents) {
                ObjectId id = document.getObjectId("_id");
                if (id != null) {
                    idsToDelete.add(id);
                }
            }
            if (idsToDelete.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }

            return plugin.getMongoManager().toFuture(
                deathsCollection.deleteMany(Filters.in("_id", idsToDelete))
            ).thenApply(result -> null);
        });
    }

    public CompletableFuture<List<DeathRecord>> getPlayerDeaths(UUID playerUuid) {
        int maxDeaths = plugin.getConfigManager().getBackupConfig().getMaxDeathsPerPlayer();
        var find = deathsCollection.find(Filters.eq("playerUuid", playerUuid.toString()))
            .sort(Sorts.descending("createdAt"));
        if (maxDeaths > 0) {
            find = find.limit(maxDeaths);
        }
        return plugin.getMongoManager().toListFuture(find).thenApply(docs -> mapDeathDocs(docs, deathsCollection));
    }

    public CompletableFuture<DeathRecord> getDeathById(String deathId) {
        return getDeathById(deathId, null);
    }

    public CompletableFuture<DeathRecord> getDeathById(String deathId, String databaseName) {
        if (deathId == null || deathId.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }
        MongoCollection<Document> deaths = deathsOf(databaseName);
        String trimmed = deathId.trim();
        if (BackupIds.isPublicId(trimmed)) {
            String publicId = BackupIds.normalize(trimmed);
            return plugin.getMongoManager().toFuture(
                deaths.find(Filters.eq("publicId", publicId)).first()
            ).thenCompose(doc -> {
                if (doc != null) {
                    return CompletableFuture.completedFuture(mapDeathRecord(doc, deaths));
                }
                return plugin.getMongoManager().toFuture(
                    deaths.find(Filters.eq("publicId", trimmed)).first()
                ).thenApply(found -> mapDeathRecord(found, deaths));
            });
        }
        if (!ObjectId.isValid(trimmed)) {
            return CompletableFuture.completedFuture(null);
        }
        return plugin.getMongoManager().toFuture(
            deaths.find(Filters.eq("_id", new ObjectId(trimmed))).first()
        ).thenApply(doc -> mapDeathRecord(doc, deaths));
    }

    public CompletableFuture<List<DeathRecord>> findDeathsByNick(String nick) {
        return findDeathsByNick(nick, null);
    }

    public CompletableFuture<List<DeathRecord>> findDeathsByNick(String nick, String databaseName) {
        if (nick == null || nick.isBlank()) {
            return CompletableFuture.completedFuture(List.of());
        }
        String trimmed = nick.trim();
        MongoCollection<Document> deaths = deathsOf(databaseName);
        boolean local = isLocalDatabase(databaseName);
        if (local) {
            org.bukkit.entity.Player online = org.bukkit.Bukkit.getPlayerExact(trimmed);
            if (online != null) {
                return getPlayerDeaths(online.getUniqueId());
            }
        }

        java.util.regex.Pattern namePattern = java.util.regex.Pattern.compile(
            "^" + java.util.regex.Pattern.quote(trimmed) + "$",
            java.util.regex.Pattern.CASE_INSENSITIVE
        );
        int maxDeaths = plugin.getConfigManager().getBackupConfig().getMaxDeathsPerPlayer();
        var find = deaths.find(Filters.regex("playerName", namePattern))
            .sort(Sorts.descending("createdAt"));
        if (maxDeaths > 0) {
            find = find.limit(maxDeaths);
        }
        return plugin.getMongoManager().toListFuture(find).thenCompose(docs -> {
            List<DeathRecord> mapped = mapDeathDocs(docs, deaths);
            if (!mapped.isEmpty() || !local) {
                return CompletableFuture.completedFuture(mapped);
            }
            // getOfflinePlayer(String) potrafi blokować (profile cache / Mojang),
            // a ten kod leci na wątku callbacka sterownika Mongo — przenosimy go na pulę async.
            CompletableFuture<org.bukkit.OfflinePlayer> lookup = new CompletableFuture<>();
            org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
                lookup.complete(org.bukkit.Bukkit.getOfflinePlayer(trimmed))
            );
            return lookup.thenCompose(offline -> {
                if (offline.hasPlayedBefore() || offline.isOnline()) {
                    return getPlayerDeaths(offline.getUniqueId());
                }
                return CompletableFuture.completedFuture(List.<DeathRecord>of());
            });
        });
    }

    private int safeStartingElo() {
        try {
            return plugin.getModuleConfigManager().getEloSettingsConfig().getStartingElo();
        } catch (Throwable ignored) {
            return 1000;
        }
    }

    private List<DeathRecord> mapDeathDocs(List<Document> docs, MongoCollection<Document> deaths) {
        List<DeathRecord> deathsMapped = new ArrayList<>();
        for (Document doc : docs) {
            DeathRecord record = mapDeathRecord(doc, deaths);
            if (record == null) {
                continue;
            }
            if (record.hasItems() || hasStoredItemPayload(doc)) {
                deathsMapped.add(record);
            }
        }
        return deathsMapped;
    }

    private static boolean hasStoredItemPayload(Document doc) {
        return hasPayload(doc.getString("inventory")) || hasPayload(doc.getString("armor"));
    }

    private static boolean hasPayload(String data) {
        return data != null && !data.isBlank();
    }

    private DeathRecord mapDeathRecord(Document doc, MongoCollection<Document> deaths) {
        if (doc == null) {
            return null;
        }
        try {
            Object idValue = doc.get("_id");
            if (!(idValue instanceof ObjectId objectId)) {
                return null;
            }
            String publicId = doc.getString("publicId");
            if (publicId == null || publicId.isBlank()) {
                publicId = BackupIds.fromObjectId(objectId);
                plugin.getMongoManager().toFuture(
                    deaths.updateOne(Filters.eq("_id", objectId), Updates.set("publicId", publicId))
                );
            }

            String uuidRaw = doc.getString("playerUuid");
            if (uuidRaw == null || uuidRaw.isBlank()) {
                return null;
            }
            ItemStack[] inventory = ItemSerializer.deserializeItems(doc.getString("inventory"));
            ItemStack[] armor = ItemSerializer.deserializeItems(doc.getString("armor"));
            return new DeathRecord(
                objectId.toString(),
                BackupIds.normalize(publicId),
                UUID.fromString(uuidRaw),
                doc.getString("playerName"),
                PlayerInventories.withoutEquippedArmorCopies(inventory, armor),
                armor,
                doc.getString("deathCause"),
                doc.getString("killerName") != null ? doc.getString("killerName") : "Brak",
                doc.getString("location"),
                doc.getInteger("preDeathElo", safeStartingElo()),
                doc.getDate("createdAt") != null ? doc.getDate("createdAt").toInstant() : Instant.now()
            );
        } catch (Exception ex) {
            plugin.getLogger().warning("[Backup] Nie odczytano backupu śmierci: " + ex.getMessage());
            return null;
        }
    }

    private MongoCollection<Document> deathsOf(String databaseName) {
        if (isLocalDatabase(databaseName)) {
            return deathsCollection;
        }
        MongoDatabase database = plugin.getMongoManager().getDatabase(databaseName);
        if (database == null) {
            return deathsCollection;
        }
        return database.getCollection("player_deaths");
    }

    private MongoCollection<Document> pendingOf(String databaseName) {
        if (isLocalDatabase(databaseName)) {
            return pendingBackupsCollection;
        }
        MongoDatabase database = plugin.getMongoManager().getDatabase(databaseName);
        if (database == null) {
            return pendingBackupsCollection;
        }
        return database.getCollection("pending_backups");
    }

    private boolean isLocalDatabase(String databaseName) {
        if (databaseName == null || databaseName.isBlank()) {
            return true;
        }
        String local = plugin.getConfigManager().getDatabaseConfig().getDbPrefix();
        return databaseName.equalsIgnoreCase(local);
    }

    public CompletableFuture<Void> createPendingBackup(UUID playerUuid, String playerName,
                                                       String deathId, UUID requestedBy) {
        Document doc = new Document()
            .append("playerUuid", playerUuid.toString())
            .append("playerName", playerName)
            .append("deathId", deathId)
            .append("requestedBy", requestedBy.toString())
            .append("status", "pending")
            .append("statsRestored", false)
            .append("createdAt", Date.from(Instant.now()));

        return plugin.getMongoManager().toFuture(
            pendingBackupsCollection.insertOne(doc)
        ).thenApply(result -> null);
    }

    public CompletableFuture<Void> createApprovedBackup(UUID playerUuid, String playerName,
                                                        String deathId, UUID requestedBy, UUID approvedBy) {
        Document doc = new Document()
            .append("playerUuid", playerUuid.toString())
            .append("playerName", playerName)
            .append("deathId", deathId)
            .append("requestedBy", requestedBy.toString())
            .append("status", "approved")
            .append("statsRestored", false)
            .append("approvedBy", approvedBy.toString())
            .append("approvedAt", Date.from(Instant.now()))
            .append("createdAt", Date.from(Instant.now()));

        return plugin.getMongoManager().toFuture(
            pendingBackupsCollection.insertOne(doc)
        ).thenApply(result -> null);
    }

    /**
     * Sprawdza czy gracz już otrzymał backup dla danego deathId.
     */
    public CompletableFuture<Boolean> hasPlayerReceivedBackup(UUID playerUuid, String deathId) {
        return plugin.getMongoManager().toFuture(
            pendingBackupsCollection.find(Filters.and(
                Filters.eq("playerUuid", playerUuid.toString()),
                Filters.eq("deathId", deathId),
                Filters.eq("status", "approved")
            )).first()
        ).thenApply(doc -> doc != null);
    }

    public CompletableFuture<Void> approveBackup(String backupId, UUID approvedBy) {
        return plugin.getMongoManager().toFuture(
            pendingBackupsCollection.updateOne(
                Filters.eq("_id", new ObjectId(backupId)),
                Updates.combine(
                    Updates.set("status", "approved"),
                    Updates.set("approvedBy", approvedBy.toString()),
                    Updates.set("approvedAt", Date.from(Instant.now()))
                )
            )
        ).thenApply(result -> null);
    }

    public CompletableFuture<Void> deleteApprovedBackupsForPlayer(UUID playerUuid) {
        return plugin.getMongoManager().toFuture(
            pendingBackupsCollection.deleteMany(
                Filters.and(
                    Filters.eq("playerUuid", playerUuid.toString()),
                    Filters.eq("status", "approved")
                )
            )
        ).thenApply(result -> null);
    }

    public CompletableFuture<Void> markStatsRestored(String backupId, UUID restoredBy) {
        return plugin.getMongoManager().toFuture(
            pendingBackupsCollection.updateOne(
                Filters.eq("_id", new ObjectId(backupId)),
                Updates.combine(
                    Updates.set("statsRestored", true),
                    Updates.set("statsRestoredBy", restoredBy.toString()),
                    Updates.set("statsRestoredAt", Date.from(Instant.now()))
                )
            )
        ).thenApply(result -> null);
    }

    public CompletableFuture<Void> deletePendingBackup(String backupId) {
        return deletePendingBackup(backupId, null);
    }

    public CompletableFuture<Void> deletePendingBackup(String backupId, String databaseName) {
        if (backupId == null || !ObjectId.isValid(backupId)) {
            return CompletableFuture.completedFuture(null);
        }
        return plugin.getMongoManager().toFuture(
            pendingOf(databaseName).deleteOne(Filters.eq("_id", new ObjectId(backupId)))
        ).thenApply(result -> null);
    }

    public CompletableFuture<List<PendingBackup>> getAllPendingBackups() {
        return getAllPendingBackups(null);
    }

    public CompletableFuture<List<PendingBackup>> getAllPendingBackups(String databaseName) {
        MongoCollection<Document> pending = pendingOf(databaseName);
        return plugin.getMongoManager().toListFuture(
            pending.find(Filters.eq("status", "pending"))
                .sort(Sorts.ascending("createdAt"))
        ).thenApply(docs -> {
            List<PendingBackup> backups = new ArrayList<>();
            for (Document doc : docs) {
                backups.add(mapPendingBackup(doc));
            }
            return backups;
        });
    }

    public CompletableFuture<PendingBackup> getPendingBackupById(String backupId) {
        return getPendingBackupById(backupId, null);
    }

    public CompletableFuture<PendingBackup> getPendingBackupById(String backupId, String databaseName) {
        if (backupId == null || !ObjectId.isValid(backupId)) {
            return CompletableFuture.completedFuture(null);
        }
        return plugin.getMongoManager().toFuture(
            pendingOf(databaseName).find(Filters.eq("_id", new ObjectId(backupId))).first()
        ).thenApply(this::mapPendingBackup);
    }

    public CompletableFuture<PendingBackup> getApprovedBackupForPlayer(UUID playerUuid) {
        return getApprovedBackupsForPlayer(playerUuid).thenApply(list ->
            list == null || list.isEmpty() ? null : list.get(0)
        );
    }

    public CompletableFuture<List<PendingBackup>> getApprovedBackupsForPlayer(UUID playerUuid) {
        return plugin.getMongoManager().toListFuture(
            pendingBackupsCollection.find(
                Filters.and(
                    Filters.eq("playerUuid", playerUuid.toString()),
                    Filters.eq("status", "approved")
                )
            ).sort(Sorts.descending("approvedAt"))
        ).thenApply(docs -> {
            List<PendingBackup> backups = new ArrayList<>();
            for (Document doc : docs) {
                PendingBackup mapped = mapPendingBackup(doc);
                if (mapped != null) {
                    backups.add(mapped);
                }
            }
            return backups;
        });
    }

    private PendingBackup mapPendingBackup(Document doc) {
        if (doc == null) {
            return null;
        }

        return new PendingBackup(
            doc.getObjectId("_id").toString(),
            UUID.fromString(doc.getString("playerUuid")),
            doc.getString("playerName"),
            doc.getString("deathId"),
            UUID.fromString(doc.getString("requestedBy")),
            doc.getString("status"),
            doc.getBoolean("statsRestored", false),
            doc.getString("approvedBy") != null ? UUID.fromString(doc.getString("approvedBy")) : null,
            doc.getDate("createdAt").toInstant(),
            doc.getDate("approvedAt") != null ? doc.getDate("approvedAt").toInstant() : null
        );
    }

    public CompletableFuture<Void> queueDeathStatRestore(UUID playerUuid, int amount) {
        return plugin.getMongoManager().toFuture(
            pendingStatRestoresCollection.updateOne(
                Filters.eq("playerUuid", playerUuid.toString()),
                Updates.combine(
                    Updates.setOnInsert("playerUuid", playerUuid.toString()),
                    Updates.inc("deathsToRemove", amount),
                    Updates.set("updatedAt", Date.from(Instant.now()))
                ),
                new com.mongodb.client.model.UpdateOptions().upsert(true)
            )
        ).thenApply(result -> null);
    }

    public CompletableFuture<Integer> getQueuedDeathStatRestore(UUID playerUuid) {
        return plugin.getMongoManager().toFuture(
            pendingStatRestoresCollection.find(Filters.eq("playerUuid", playerUuid.toString())).first()
        ).thenApply(doc -> doc == null ? 0 : doc.getInteger("deathsToRemove", 0));
    }

    public CompletableFuture<Void> clearQueuedDeathStatRestore(UUID playerUuid) {
        return plugin.getMongoManager().toFuture(
            pendingStatRestoresCollection.deleteOne(Filters.eq("playerUuid", playerUuid.toString()))
        ).thenApply(result -> null);
    }

    public CompletableFuture<Map<UUID, PlayerSnapshotData>> getLatestGlobalSnapshotsBefore(Date targetDate) {
        List<Bson> pipeline = List.of(
            Aggregates.match(Filters.and(
                Filters.eq("type", "global_snapshot"),
                Filters.lte("createdAt", targetDate)
            )),
            Aggregates.sort(Sorts.descending("createdAt")),
            Aggregates.group("$playerUuid", Accumulators.first("doc", "$$ROOT")),
            Aggregates.replaceRoot("$doc")
        );
        return plugin.getMongoManager().toListFuture(
            snapshotsCollection.aggregate(pipeline).allowDiskUse(true)
        ).thenApply(this::mapLatestSnapshots)
            .exceptionally(ignored -> null)
            .thenCompose(mapped -> mapped != null
                ? CompletableFuture.completedFuture(mapped)
                : getLatestGlobalSnapshotsBeforeFallback(targetDate));
    }

    private CompletableFuture<Map<UUID, PlayerSnapshotData>> getLatestGlobalSnapshotsBeforeFallback(Date targetDate) {
        return plugin.getMongoManager().toListFuture(
            snapshotsCollection.find(Filters.and(
                Filters.eq("type", "global_snapshot"),
                Filters.lte("createdAt", targetDate)
            )).sort(Sorts.descending("createdAt"))
                .projection(Projections.exclude("inventory", "armor", "enderChest"))
        ).thenCompose(lightDocs -> {
            Map<UUID, ObjectId> latestIds = new LinkedHashMap<>();
            for (Document doc : lightDocs) {
                try {
                    UUID uuid = UUID.fromString(doc.getString("playerUuid"));
                    if (!latestIds.containsKey(uuid) && doc.getObjectId("_id") != null) {
                        latestIds.put(uuid, doc.getObjectId("_id"));
                    }
                } catch (Throwable ignored) {
                }
            }
            if (latestIds.isEmpty()) {
                return CompletableFuture.completedFuture(Map.of());
            }
            return plugin.getMongoManager().toListFuture(
                snapshotsCollection.find(Filters.in("_id", latestIds.values()))
            ).thenApply(this::mapLatestSnapshots);
        });
    }

    private Map<UUID, PlayerSnapshotData> mapLatestSnapshots(List<Document> docs) {
        Map<UUID, PlayerSnapshotData> result = new LinkedHashMap<>();
        if (docs == null) {
            return result;
        }
        for (Document doc : docs) {
            Document snapshot = doc.get("doc") instanceof Document nested ? nested : doc;
            try {
                UUID uuid = UUID.fromString(snapshot.getString("playerUuid"));
                if (!result.containsKey(uuid)) {
                    result.put(uuid, parsePlayerSnapshot(uuid, snapshot));
                }
            } catch (Throwable ignored) {
            }
        }
        return result;
    }

    public CompletableFuture<Set<UUID>> getSnapshotPlayerUuidsBefore(Date targetDate) {
        List<Bson> pipeline = List.of(
            Aggregates.match(Filters.and(
                Filters.eq("type", "global_snapshot"),
                Filters.lte("createdAt", targetDate)
            )),
            Aggregates.group("$playerUuid")
        );
        return plugin.getMongoManager().toListFuture(
            snapshotsCollection.aggregate(pipeline).allowDiskUse(true)
        ).thenApply(docs -> {
            Set<UUID> uuids = new HashSet<>();
            for (Document doc : docs) {
                Object raw = doc.get("_id");
                if (raw == null) {
                    continue;
                }
                try {
                    uuids.add(UUID.fromString(String.valueOf(raw)));
                } catch (IllegalArgumentException ignored) {
                }
            }
            return uuids;
        }).exceptionally(ignored -> null)
            .thenCompose(uuids -> uuids != null
                ? CompletableFuture.completedFuture(uuids)
                : getSnapshotPlayerUuidsBeforeFallback(targetDate));
    }

    private CompletableFuture<Set<UUID>> getSnapshotPlayerUuidsBeforeFallback(Date targetDate) {
        return plugin.getMongoManager().toListFuture(
            snapshotsCollection.find(Filters.and(
                Filters.eq("type", "global_snapshot"),
                Filters.lte("createdAt", targetDate)
            )).projection(Projections.include("playerUuid"))
        ).thenApply(docs -> {
            Set<UUID> uuids = new HashSet<>();
            for (Document doc : docs) {
                try {
                    uuids.add(UUID.fromString(doc.getString("playerUuid")));
                } catch (Throwable ignored) {
                }
            }
            return uuids;
        });
    }

    public CompletableFuture<Document> getLatestMarketSnapshotBefore(Date targetDate) {
        return plugin.getMongoManager().toFuture(
            marketSnapshotsCollection.find(Filters.lte("createdAt", targetDate))
                .sort(Sorts.descending("createdAt"))
                .first()
        );
    }

    public CompletableFuture<List<Date>> getAvailableSnapshotDates(int limit) {
        int capped = Math.max(1, Math.min(limit, 200));
        List<Bson> pipeline = List.of(
            Aggregates.match(Filters.eq("type", "global_snapshot")),
            Aggregates.group("$createdAt"),
            Aggregates.sort(Sorts.descending("_id")),
            Aggregates.limit(capped)
        );
        return plugin.getMongoManager().toListFuture(
            snapshotsCollection.aggregate(pipeline).allowDiskUse(true)
        ).thenApply(this::datesFromGroupDocs)
            .exceptionally(ignored -> null)
            .thenCompose(dates -> dates != null
                ? CompletableFuture.completedFuture(dates)
                : getAvailableSnapshotDatesFallback(capped));
    }

    private CompletableFuture<List<Date>> getAvailableSnapshotDatesFallback(int limit) {
        return plugin.getMongoManager().toListFuture(
            snapshotsCollection.find(Filters.eq("type", "global_snapshot"))
                .projection(Projections.include("createdAt"))
                .sort(Sorts.descending("createdAt"))
                .limit(Math.max(limit * 400, limit))
        ).thenApply(docs -> {
            List<Date> dates = new ArrayList<>();
            for (Document doc : docs) {
                Date date = doc.getDate("createdAt");
                if (date != null && !dates.contains(date)) {
                    dates.add(date);
                    if (dates.size() >= limit) {
                        break;
                    }
                }
            }
            return dates;
        });
    }

    private List<Date> datesFromGroupDocs(List<Document> docs) {
        List<Date> dates = new ArrayList<>();
        if (docs == null) {
            return dates;
        }
        for (Document doc : docs) {
            Object raw = doc.get("_id");
            Date date = raw instanceof Date value ? value : doc.getDate("_id");
            if (date != null && !dates.contains(date)) {
                dates.add(date);
            }
        }
        return dates;
    }

    public CompletableFuture<List<PlayerSnapshotData>> getPlayerGlobalSnapshots(UUID playerUuid, int limit) {
        if (playerUuid == null) {
            return CompletableFuture.completedFuture(List.of());
        }
        int capped = Math.max(1, Math.min(limit, 200));
        return plugin.getMongoManager().toListFuture(
            snapshotsCollection.find(Filters.and(
                Filters.eq("type", "global_snapshot"),
                Filters.eq("playerUuid", playerUuid.toString())
            )).sort(Sorts.descending("createdAt")).limit(capped)
        ).thenApply(docs -> {
            List<PlayerSnapshotData> snapshots = new ArrayList<>(docs.size());
            for (Document doc : docs) {
                try {
                    snapshots.add(parsePlayerSnapshot(playerUuid, doc));
                } catch (Throwable ignored) {
                }
            }
            return snapshots;
        });
    }

    public CompletableFuture<Void> queueOfflineRollbackRestore(UUID playerUuid, PlayerSnapshotData data) {
        boolean hasInventory = data.hasInventory();
        Document doc = new Document()
            .append("playerUuid", playerUuid.toString())
            .append("playerName", data.playerName())
            .append("hasInventory", hasInventory)
            .append("createdAt", Date.from(Instant.now()));

        if (hasInventory) {
            doc.append("inventory", ItemSerializer.serializeItems(data.inventory()))
                .append("armor", ItemSerializer.serializeItems(data.armor()))
                .append("enderChest", ItemSerializer.serializeItems(data.enderChest()));
        }
        if (data.economyBalance() != null) {
            doc.append("economyBalance", data.economyBalance());
        }
        if (data.walletBalance() != null) {
            doc.append("walletBalance", data.walletBalance());
        }

        return plugin.getMongoManager().toFuture(
            queuedRollbackRestoresCollection.replaceOne(
                Filters.eq("playerUuid", playerUuid.toString()),
                doc,
                new com.mongodb.client.model.ReplaceOptions().upsert(true)
            )
        ).thenApply(res -> null);
    }

    public CompletableFuture<PlayerSnapshotData> getQueuedRollbackRestore(UUID playerUuid) {
        return plugin.getMongoManager().toFuture(
            queuedRollbackRestoresCollection.find(Filters.eq("playerUuid", playerUuid.toString())).first()
        ).thenApply(doc -> {
            if (doc == null) return null;
            try {
                return parsePlayerSnapshot(playerUuid, doc);
            } catch (Throwable ignored) {
                return null;
            }
        });
    }

    public CompletableFuture<Void> clearQueuedRollbackRestore(UUID playerUuid) {
        return plugin.getMongoManager().toFuture(
            queuedRollbackRestoresCollection.deleteOne(Filters.eq("playerUuid", playerUuid.toString()))
        ).thenApply(res -> null);
    }

    public CompletableFuture<Long> rollbackPendingBackupsAfter(Date targetDate) {
        return plugin.getMongoManager().toFuture(
            pendingBackupsCollection.deleteMany(Filters.gt("createdAt", targetDate))
        ).thenApply(res -> res != null ? res.getDeletedCount() : 0L);
    }

    private PlayerSnapshotData parsePlayerSnapshot(UUID uuid, Document doc) {
        boolean hasInventory = doc.containsKey("hasInventory")
            ? Boolean.TRUE.equals(doc.getBoolean("hasInventory"))
            : doc.getString("inventory") != null;
        ItemStack[] inv = hasInventory ? ItemSerializer.deserializeItems(doc.getString("inventory")) : null;
        ItemStack[] armor = hasInventory ? ItemSerializer.deserializeItems(doc.getString("armor")) : null;
        ItemStack[] ec = hasInventory ? ItemSerializer.deserializeItems(doc.getString("enderChest")) : null;
        Double ecoBalance = readSnapshotDouble(doc, "economyBalance");
        Double walletBalance = readSnapshotDouble(doc, "walletBalance");
        Date createdAt = doc.getDate("createdAt");
        return new PlayerSnapshotData(
            uuid,
            doc.getString("playerName"),
            inv,
            armor,
            ec,
            ecoBalance,
            walletBalance,
            createdAt != null ? createdAt.toInstant() : null
        );
    }

    private static Double readSnapshotDouble(Document doc, String key) {
        if (!doc.containsKey(key) || doc.get(key) == null) {
            return null;
        }
        Object value = doc.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return null;
    }

    /** Szuka gracza po nicku — online, cache, join_info, Mongo (bez skanowania wszystkich OfflinePlayer). */
    public CompletableFuture<ResolvedPlayer> resolvePlayerByNick(String nick) {
        if (nick == null || nick.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }
        String trimmed = nick.trim();
        ResolvedPlayer local = resolveLocalPlayer(trimmed);
        if (local != null) {
            return CompletableFuture.completedFuture(local);
        }
        if (plugin.getMongoManager() == null || !plugin.getMongoManager().isAvailable()) {
            return CompletableFuture.completedFuture(null);
        }
        Pattern exactName = Pattern.compile("^" + Pattern.quote(trimmed) + "$", Pattern.CASE_INSENSITIVE);
        List<CompletableFuture<ResolvedPlayer>> lookups = List.of(
            findPlayerInCollection(snapshotsCollection, "playerName", exactName, trimmed),
            findPlayerInCollection(deathsCollection, "playerName", exactName, trimmed),
            findPlayerInCollection("join_info", "player_name", exactName, trimmed),
            findPlayerInCollection(plugin.getConfigManager().getDatabaseConfig().getStatsCollection(), "name", exactName, trimmed),
            findPlayerInCollection("player_elo", "name", exactName, trimmed)
        );
        return firstResolved(lookups);
    }

    private CompletableFuture<ResolvedPlayer> firstResolved(List<CompletableFuture<ResolvedPlayer>> lookups) {
        CompletableFuture<ResolvedPlayer> done = new CompletableFuture<>();
        java.util.concurrent.atomic.AtomicInteger remaining = new java.util.concurrent.atomic.AtomicInteger(lookups.size());
        for (CompletableFuture<ResolvedPlayer> lookup : lookups) {
            lookup.whenComplete((found, error) -> {
                if (found != null) {
                    done.complete(found);
                    return;
                }
                if (remaining.decrementAndGet() <= 0) {
                    done.complete(null);
                }
            });
        }
        return done;
    }

    private ResolvedPlayer resolveLocalPlayer(String nick) {
        Player online = Bukkit.getPlayerExact(nick);
        if (online != null) {
            return new ResolvedPlayer(online.getUniqueId(), online.getName());
        }
        try {
            OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(nick);
            if (cached != null && cached.hasPlayedBefore()) {
                String name = cached.getName() != null ? cached.getName() : nick;
                return new ResolvedPlayer(cached.getUniqueId(), name);
            }
        } catch (NoSuchMethodError | NoClassDefFoundError ignored) {
        }
        if (plugin.getJoinInfoManager() != null) {
            UUID uuid = plugin.getJoinInfoManager().getUuidByName(nick);
            if (uuid != null) {
                String stored = plugin.getJoinInfoManager().getPlayerName(uuid);
                return new ResolvedPlayer(uuid, stored != null ? stored : nick);
            }
        }
        if (plugin.getTopManager() != null) {
            UUID uuid = plugin.getTopManager().getUuidByName(nick);
            if (uuid != null) {
                return new ResolvedPlayer(uuid, nick);
            }
        }
        return null;
    }

    private static final Bson PLAYER_LOOKUP_PROJECTION = Projections.include(
        "playerUuid", "uuid", "playerName", "player_name", "name"
    );

    private CompletableFuture<ResolvedPlayer> findPlayerInCollection(
        MongoCollection<Document> collection,
        String nameField,
        Pattern exactName,
        String fallbackName
    ) {
        return plugin.getMongoManager().toFuture(
            collection.find(Filters.eq(nameField, fallbackName))
                .projection(PLAYER_LOOKUP_PROJECTION)
                .first()
        ).thenCompose(doc -> {
            if (doc != null) {
                return CompletableFuture.completedFuture(playerFromDocument(doc, fallbackName));
            }
            return plugin.getMongoManager().toFuture(
                collection.find(Filters.regex(nameField, exactName))
                    .projection(PLAYER_LOOKUP_PROJECTION)
                    .first()
            ).thenApply(found -> playerFromDocument(found, fallbackName));
        }).exceptionally(ex -> null);
    }

    private CompletableFuture<ResolvedPlayer> findPlayerInCollection(
        String collectionName,
        String nameField,
        Pattern exactName,
        String fallbackName
    ) {
        return findPlayerInCollection(
            plugin.getMongoManager().getCollection(collectionName),
            nameField,
            exactName,
            fallbackName
        );
    }

    private static ResolvedPlayer playerFromDocument(Document doc, String fallbackName) {
        if (doc == null) {
            return null;
        }
        String uuidString = doc.getString("playerUuid");
        if (uuidString == null || uuidString.isBlank()) {
            uuidString = doc.getString("uuid");
        }
        if (uuidString == null || uuidString.isBlank()) {
            return null;
        }
        try {
            UUID uuid = UUID.fromString(uuidString);
            String name = doc.getString("playerName");
            if (name == null || name.isBlank()) {
                name = doc.getString("player_name");
            }
            if (name == null || name.isBlank()) {
                name = doc.getString("name");
            }
            if (name == null || name.isBlank()) {
                name = fallbackName;
            }
            return new ResolvedPlayer(uuid, name);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public record ResolvedPlayer(UUID uuid, String name) {}

    public record PlayerSnapshotData(UUID playerUuid, String playerName,
                                     ItemStack[] inventory, ItemStack[] armor, ItemStack[] enderChest,
                                     Double economyBalance, Double walletBalance, Instant createdAt) {
        public PlayerSnapshotData(UUID playerUuid, String playerName,
                                  ItemStack[] inventory, ItemStack[] armor, ItemStack[] enderChest,
                                  Double economyBalance, Double walletBalance) {
            this(playerUuid, playerName, inventory, armor, enderChest, economyBalance, walletBalance, null);
        }

        public PlayerSnapshotData(UUID playerUuid, String playerName,
                                  ItemStack[] inventory, ItemStack[] armor, ItemStack[] enderChest) {
            this(playerUuid, playerName, inventory, armor, enderChest, null, null, null);
        }

        public boolean hasInventory() {
            return inventory != null;
        }
    }

    public record DeathRecord(String id, String publicId, UUID playerUuid, String playerName,
                              ItemStack[] inventory, ItemStack[] armor,
                              String deathCause, String killerName, String location,
                              int preDeathElo,
                              Instant createdAt) {
        public boolean hasItems() {
            return hasAnyItems(inventory) || hasAnyItems(armor);
        }
    }

    public record PendingBackup(String id, UUID playerUuid, String playerName,
                                String deathId, UUID requestedBy, String status,
                                boolean statsRestored, UUID approvedBy,
                                Instant createdAt, Instant approvedAt) {}
}
