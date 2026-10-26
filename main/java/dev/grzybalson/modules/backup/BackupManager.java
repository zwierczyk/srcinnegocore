package dev.grzybalson.modules.backup;

import dev.grzybalson.HollyCore;
import dev.grzybalson.database.EconomyDataManager;
import dev.grzybalson.database.WalletDataManager;
import dev.grzybalson.modules.backup.zapowiedz.ZapowiedzJoinListener;
import dev.grzybalson.modules.backup.zapowiedz.ZapowiedzMongoManager;
import dev.grzybalson.modules.backup.zapowiedz.ZapowiedzReminderService;
import dev.grzybalson.modules.backup.zapowiedz.ZapowiedzRepository;
import dev.grzybalson.modules.backup.zapowiedz.ZapowiedzService;
import net.dv8tion.jda.api.JDA;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.PlayerInventories;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import org.bukkit.scheduler.BukkitTask;
import dev.grzybalson.utils.ItemSerializer;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class BackupManager {

    private final HollyCore plugin;
    private final PlayerSessionCache sessionCache;
    private final BackupDataManager dataManager;
    private final Map<UUID, BossBar> pendingBackupBars;
    private BukkitTask snapshotTask;
    private BukkitTask bossBarTask;
    private BukkitTask discordHubTask;
    private BackupDiscordBot discordBot;
    private BackupModeHub modeHub;
    private ZapowiedzMongoManager zapowiedzMongo;
    private ZapowiedzService zapowiedzService;
    private ZapowiedzReminderService zapowiedzReminderService;
    private ZapowiedzJoinListener zapowiedzJoinListener;
    private BukkitTask zapowiedzReminderTask;

    public BackupManager(HollyCore plugin) {
        this.plugin = plugin;
        this.sessionCache = new PlayerSessionCache(plugin);
        this.dataManager = new BackupDataManager(plugin);
        this.pendingBackupBars = new ConcurrentHashMap<>();
        this.modeHub = new BackupModeHub(plugin, this);
    }

    public void start() {
        int intervalMinutes = plugin.getConfigManager().getBackupConfig().getSnapshotIntervalMinutes();
        long intervalTicks = intervalMinutes * 60L * 20L;

        snapshotTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::createGlobalSnapshot,
            intervalTicks, intervalTicks);

        bossBarTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateBossBars, 20L, 20L);

        discordHubTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::tickDiscordHub, 40L, 40L);

        startZapowiedz();

        for (Player player : Bukkit.getOnlinePlayers()) {
            sessionCache.loadSession(player);
            applyQueuedStatRestore(player);
            checkPendingBackup(player);
        }
    }

    public void shutdown() {
        if (snapshotTask != null) {
            snapshotTask.cancel();
        }
        if (bossBarTask != null) {
            bossBarTask.cancel();
        }
        if (discordHubTask != null) {
            discordHubTask.cancel();
            discordHubTask = null;
        }
        stopDiscordBot();
        stopZapowiedz();

        for (BossBar bar : pendingBackupBars.values()) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.hideBossBar(bar);
            }
        }
        pendingBackupBars.clear();
        sessionCache.invalidateAll();
    }

    public BackupModeHub modeHub() {
        return modeHub;
    }

    public ZapowiedzService getZapowiedzService() {
        return zapowiedzService;
    }

    public ZapowiedzReminderService getZapowiedzReminderService() {
        return zapowiedzReminderService;
    }

    public JDA getDiscordJda() {
        return discordBot != null ? discordBot.getJda() : null;
    }

    private void startZapowiedz() {
        if (!plugin.getConfigManager().getBackupConfig().getZapowiedz().isEnabled()) {
            return;
        }
        zapowiedzMongo = new ZapowiedzMongoManager(plugin);
        ZapowiedzRepository repository = new ZapowiedzRepository(plugin, zapowiedzMongo);
        zapowiedzService = new ZapowiedzService(plugin, zapowiedzMongo, repository);
        zapowiedzReminderService = new ZapowiedzReminderService(plugin, zapowiedzService, repository);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            if (zapowiedzService.connectMongo()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (zapowiedzJoinListener == null) {
                        zapowiedzJoinListener = new ZapowiedzJoinListener(plugin, zapowiedzService);
                        Bukkit.getPluginManager().registerEvents(zapowiedzJoinListener, plugin);
                    }
                    startZapowiedzReminderTask();
                });
            }
        });
    }

    private void startZapowiedzReminderTask() {
        if (zapowiedzReminderTask != null) {
            zapowiedzReminderTask.cancel();
        }
        if (zapowiedzReminderService == null) {
            return;
        }
        long intervalTicks = ZapowiedzReminderService.DISPATCH_INTERVAL_SECONDS * 20L;
        zapowiedzReminderTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
            plugin,
            this::tickZapowiedzReminders,
            20L,
            intervalTicks
        );
    }

    public void reloadZapowiedzReminderTask() {
        startZapowiedzReminderTask();
    }

    private void tickZapowiedzReminders() {
        if (zapowiedzReminderService == null || discordBot == null) {
            return;
        }
        JDA jda = discordBot.getJda();
        if (jda != null) {
            zapowiedzReminderService.tick(jda);
        }
    }

    private void stopZapowiedz() {
        if (zapowiedzReminderTask != null) {
            zapowiedzReminderTask.cancel();
            zapowiedzReminderTask = null;
        }
        if (zapowiedzService != null) {
            zapowiedzService.disconnectMongo();
            zapowiedzService = null;
        }
        zapowiedzReminderService = null;
        zapowiedzMongo = null;
        zapowiedzJoinListener = null;
    }

    public CompletableFuture<List<BackupDataManager.DeathRecord>> findDeathsByNick(String nick, String databaseName) {
        return dataManager.findDeathsByNick(nick, databaseName);
    }

    public CompletableFuture<BackupDataManager.DeathRecord> getDeathById(String deathId, String databaseName) {
        return dataManager.getDeathById(deathId, databaseName);
    }

    public CompletableFuture<List<BackupDataManager.PendingBackup>> getAllPendingBackups(String databaseName) {
        return dataManager.getAllPendingBackups(databaseName);
    }

    public CompletableFuture<String> grantOnMode(String mode, String deathId, boolean eq, boolean stats, String staff) {
        if (mode == null || mode.isBlank() || mode.equalsIgnoreCase(modeHub.localPrefix())) {
            return getDeathById(deathId).thenCompose(death -> applyGrant(death, eq, stats, staff));
        }
        String action = eq && stats ? "all" : stats ? "stats" : "eq";
        return modeHub.submitJob(mode, action, deathId, staff);
    }

    private CompletableFuture<String> applyGrant(BackupDataManager.DeathRecord death, boolean eq, boolean stats, String staff) {
        if (death == null) {
            return CompletableFuture.completedFuture("Nie znaleziono backupu.");
        }
        UUID staffUuid = UUID.nameUUIDFromBytes(("discord:" + staff).getBytes(StandardCharsets.UTF_8));
        CompletableFuture<Void> work = CompletableFuture.completedFuture(null);
        if (eq) {
            work = work.thenCompose(v -> grantBackup(death.playerUuid(), death.playerName(), death.id(), staffUuid, staffUuid));
        }
        if (stats) {
            work = work.thenCompose(v -> restoreStats(death, staffUuid).thenApply(ok -> null));
        }
        return work.thenApply(v -> "ok");
    }

    private void tickDiscordHub() {
        try {
            modeHub.heartbeat().join();
            boolean leader = modeHub.claimLeadership().join();
            String token = plugin.getConfigManager().getBackupConfig().getDiscordBotToken();
            boolean wantBot = token != null && !token.isBlank() && leader;
            if (wantBot && discordBot == null) {
                plugin.getLogger().info("[Backup DC] Ten tryb (" + modeHub.localName() + ") prowadzi bota Discord.");
                Bukkit.getScheduler().runTask(plugin, this::startDiscordBot);
            } else if (!wantBot && discordBot != null) {
                plugin.getLogger().info("[Backup DC] Oddaję bota Discord innemu trybowi.");
                Bukkit.getScheduler().runTask(plugin, this::stopDiscordBot);
            }
            modeHub.processLocalJobs().join();
        } catch (Exception exception) {
            plugin.getLogger().warning("[Backup DC] Hub: " + exception.getMessage());
        }
    }

    public void restartDiscordBot() {
        stopDiscordBot();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::tickDiscordHub);
    }

    private void startDiscordBot() {
        if (discordBot != null) {
            return;
        }
        discordBot = new BackupDiscordBot(plugin, this);
        discordBot.start();
    }

    private void stopDiscordBot() {
        if (discordBot != null) {
            discordBot.shutdown();
            discordBot = null;
        }
    }

    public void createGlobalSnapshot() {
        // EQ/EC muszą być zczytane na main thread (live, nie stary session cache).
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, this::createGlobalSnapshot);
            return;
        }

        List<BackupDataManager.PlayerSnapshotData> onlineSnapshots = new ArrayList<>();
        var mslf = plugin.getMslfManager();
        var mslfEc = mslf != null ? mslf.enderChestService() : null;

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            ItemStack[] inv = deepCopy(player.getInventory().getContents());
            ItemStack[] armor = deepCopy(player.getInventory().getArmorContents());
            ItemStack[] ec = deepCopy(mslfEc != null
                ? mslfEc.captureContents(player)
                : player.getEnderChest().getContents());
            sessionCache.updateSession(uuid, inv, armor, ec);
            onlineSnapshots.add(new BackupDataManager.PlayerSnapshotData(
                uuid,
                player.getName(),
                inv,
                armor,
                ec,
                null,
                null
            ));
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> finishGlobalSnapshot(onlineSnapshots));
    }

    /**
     * getContents()/getArmorContents() zwracają lustra żywych NMS-owych stacków, a clone() tablicy
     * jest płytki — bez kopii per-item async serializacja czytałaby EQ zmieniane przez main thread.
     */
    private static ItemStack[] deepCopy(ItemStack[] source) {
        if (source == null) {
            return null;
        }
        ItemStack[] copy = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) {
            copy[i] = source[i] == null ? null : source[i].clone();
        }
        return copy;
    }

    private void finishGlobalSnapshot(List<BackupDataManager.PlayerSnapshotData> onlineSnapshots) {
        // Zbior online wyliczony na main threadzie — Bukkit.getPlayer() z wątku Mongo bywa niespójny.
        Set<UUID> onlineUuids = new HashSet<>();
        for (BackupDataManager.PlayerSnapshotData online : onlineSnapshots) {
            onlineUuids.add(online.playerUuid());
        }
        dumpEconomyForSnapshot().thenCombine(dumpWalletForSnapshot(), (economyDump, walletDump) -> {
            List<BackupDataManager.PlayerSnapshotData> snapshots = new ArrayList<>();
            Set<UUID> seen = new HashSet<>();

            for (BackupDataManager.PlayerSnapshotData online : onlineSnapshots) {
                UUID uuid = online.playerUuid();
                seen.add(uuid);
                snapshots.add(new BackupDataManager.PlayerSnapshotData(
                    uuid,
                    online.playerName(),
                    online.inventory(),
                    online.armor(),
                    online.enderChest(),
                    snapshotEconomyBalance(uuid, economyDump),
                    snapshotWalletBalance(uuid, walletDump)
                ));
            }

            Set<UUID> offlineUuids = new HashSet<>();
            offlineUuids.addAll(economyDump.balances().keySet());
            offlineUuids.addAll(walletDump.balances().keySet());
            offlineUuids.removeAll(seen);

            for (UUID uuid : offlineUuids) {
                snapshots.add(new BackupDataManager.PlayerSnapshotData(
                    uuid,
                    snapshotPlayerName(uuid, null, economyDump, walletDump),
                    null,
                    null,
                    null,
                    snapshotEconomyBalance(uuid, economyDump),
                    snapshotWalletBalance(uuid, walletDump)
                ));
            }

            return snapshots;
        }).thenCompose(list -> enrichOfflineSnapshotsFully(list, onlineUuids)
        ).thenCompose(enriched -> dataManager.saveGlobalSnapshot(enriched).thenApply(v -> enriched.size())
        ).thenAccept(count ->
            plugin.getLogger().info("Global snapshot saved for " + count
                + " players (EQ+EC online, ItemSync offline, MSLF EC, kostiumy/pety, rynek).")
        ).exceptionally(ex -> {
            plugin.getLogger().severe("Failed to save global snapshot: " + ex.getMessage());
            return null;
        });
    }

    /**
     * Offline: ItemSync (wszyscy z playerdata) + pełny MSLF EC z mslf_enderchest.
     */
    private CompletableFuture<List<BackupDataManager.PlayerSnapshotData>> enrichOfflineSnapshotsFully(
        List<BackupDataManager.PlayerSnapshotData> snapshots,
        Set<UUID> onlineUuids
    ) {
        return collectOfflineItemSyncUuids(snapshots).thenCompose(extraUuids -> {
            List<BackupDataManager.PlayerSnapshotData> expanded = new ArrayList<>(snapshots);
            Set<UUID> known = new HashSet<>();
            for (BackupDataManager.PlayerSnapshotData snapshot : snapshots) {
                known.add(snapshot.playerUuid());
            }
            for (UUID uuid : extraUuids) {
                if (known.add(uuid)) {
                    expanded.add(new BackupDataManager.PlayerSnapshotData(
                        uuid,
                        snapshotPlayerName(uuid, null,
                            new EconomyDataManager.BalanceDump(Map.of(), Map.of()),
                            new WalletDataManager.BalanceDump(Map.of(), Map.of())),
                        null, null, null, null, null
                    ));
                }
            }
            return enrichOfflineSnapshotsFromItemSync(expanded)
                .thenCompose(list -> enrichEnderChestsFromMslf(list, onlineUuids));
        });
    }

    private CompletableFuture<Set<UUID>> collectOfflineItemSyncUuids(
        List<BackupDataManager.PlayerSnapshotData> snapshots
    ) {
        Set<UUID> online = new HashSet<>();
        for (BackupDataManager.PlayerSnapshotData snapshot : snapshots) {
            if (snapshot.hasInventory()) {
                online.add(snapshot.playerUuid());
            }
        }
        dev.grzybalson.modules.itemsync.InventorySyncManager itemSync = plugin.getInventorySyncManager();
        if (itemSync == null || !itemSync.isEnabled()) {
            return CompletableFuture.completedFuture(Set.of());
        }
        return itemSync.listAllPlayerUuids().thenApply(all -> {
            Set<UUID> offline = new HashSet<>(all);
            offline.removeAll(online);
            return offline;
        });
    }

    private CompletableFuture<List<BackupDataManager.PlayerSnapshotData>> enrichEnderChestsFromMslf(
        List<BackupDataManager.PlayerSnapshotData> snapshots,
        Set<UUID> onlineUuids
    ) {
        if (plugin.getMongoManager() == null || !plugin.getMongoManager().isAvailable() || snapshots.isEmpty()) {
            return CompletableFuture.completedFuture(snapshots);
        }
        var mongo = plugin.getMongoManager();
        List<CompletableFuture<BackupDataManager.PlayerSnapshotData>> futures = new ArrayList<>(snapshots.size());
        for (BackupDataManager.PlayerSnapshotData snapshot : snapshots) {
            // Online już ma pełny EC z captureContents.
            if (onlineUuids.contains(snapshot.playerUuid())) {
                futures.add(CompletableFuture.completedFuture(snapshot));
                continue;
            }
            futures.add(mongo.toFuture(
                mongo.getCollection("mslf_enderchest")
                    .find(com.mongodb.client.model.Filters.eq("_id", snapshot.playerUuid().toString()))
                    .first()
            ).thenApply(doc -> {
                if (doc == null) {
                    return snapshot;
                }
                ItemStack[] mslfEc = ItemSerializer.deserializeItems(doc.getString("contents"));
                if (!BackupDataManager.hasAnyItems(mslfEc)) {
                    return snapshot;
                }
                ItemStack[] inv = snapshot.inventory();
                ItemStack[] armor = snapshot.armor();
                // Zachowaj EQ z ItemSync jeśli było; zawsze nadpisz EC pełnym MSLF.
                return new BackupDataManager.PlayerSnapshotData(
                    snapshot.playerUuid(),
                    snapshot.playerName(),
                    inv != null ? inv : new ItemStack[0],
                    armor != null ? armor : new ItemStack[0],
                    mslfEc,
                    snapshot.economyBalance(),
                    snapshot.walletBalance()
                );
            }).exceptionally(ex -> snapshot));
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(ignored -> {
                List<BackupDataManager.PlayerSnapshotData> out = new ArrayList<>(futures.size());
                for (CompletableFuture<BackupDataManager.PlayerSnapshotData> future : futures) {
                    out.add(future.join());
                }
                return out;
            });
    }

    private CompletableFuture<List<BackupDataManager.PlayerSnapshotData>> enrichOfflineSnapshotsFromItemSync(
        List<BackupDataManager.PlayerSnapshotData> snapshots
    ) {
        dev.grzybalson.modules.itemsync.InventorySyncManager itemSync = plugin.getInventorySyncManager();
        if (itemSync == null || !itemSync.isEnabled() || snapshots.isEmpty()) {
            return CompletableFuture.completedFuture(snapshots);
        }

        List<CompletableFuture<BackupDataManager.PlayerSnapshotData>> futures = new ArrayList<>(snapshots.size());
        for (BackupDataManager.PlayerSnapshotData snapshot : snapshots) {
            if (snapshot.hasInventory()) {
                futures.add(CompletableFuture.completedFuture(snapshot));
                continue;
            }
            futures.add(itemSync.loadSnapshot(snapshot.playerUuid()).thenApply(stored -> {
                if (stored == null) {
                    return snapshot;
                }
                boolean hasItems = BackupDataManager.hasAnyItems(stored.inventory())
                    || BackupDataManager.hasAnyItems(stored.armor())
                    || BackupDataManager.hasAnyItems(stored.enderChest());
                if (!hasItems) {
                    return snapshot;
                }
                return new BackupDataManager.PlayerSnapshotData(
                    snapshot.playerUuid(),
                    snapshot.playerName() != null ? snapshot.playerName() : stored.playerName(),
                    stored.inventory(),
                    stored.armor(),
                    stored.enderChest(),
                    snapshot.economyBalance(),
                    snapshot.walletBalance()
                );
            }));
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(ignored -> {
                List<BackupDataManager.PlayerSnapshotData> enriched = new ArrayList<>(snapshots.size());
                for (CompletableFuture<BackupDataManager.PlayerSnapshotData> future : futures) {
                    enriched.add(future.join());
                }
                return enriched;
            });
    }

    public void syncRollbackInventoryToItemSync(
        UUID uuid,
        String playerName,
        ItemStack[] inventory,
        ItemStack[] armor,
        ItemStack[] enderChest
    ) {
        dev.grzybalson.modules.itemsync.InventorySyncManager itemSync = plugin.getInventorySyncManager();
        if (itemSync == null || !itemSync.isEnabled() || uuid == null) {
            return;
        }
        itemSync.saveRollbackInventory(uuid, playerName, inventory, armor, enderChest);
    }

    private CompletableFuture<EconomyDataManager.BalanceDump> dumpEconomyForSnapshot() {
        if (plugin.getEconomy() == null) {
            return CompletableFuture.completedFuture(new EconomyDataManager.BalanceDump(Map.of(), Map.of()));
        }
        return plugin.getEconomy().getDataManager().dumpAllAccounts().exceptionally(ex -> {
            plugin.getLogger().warning("Nie udalo sie zrzucic kas z Mongo do snapshotu: " + ex.getMessage());
            Map<UUID, Double> cached = new HashMap<>();
            plugin.getEconomy().getDataManager().overlayCache(cached);
            return new EconomyDataManager.BalanceDump(cached, Map.of());
        });
    }

    private CompletableFuture<WalletDataManager.BalanceDump> dumpWalletForSnapshot() {
        if (plugin.getWalletDataManager() == null) {
            return CompletableFuture.completedFuture(new WalletDataManager.BalanceDump(Map.of(), Map.of()));
        }
        return plugin.getWalletDataManager().dumpAllAccounts().exceptionally(ex -> {
            plugin.getLogger().warning("Nie udalo sie zrzucic portfeli z Mongo do snapshotu: " + ex.getMessage());
            Map<UUID, Double> cached = new HashMap<>();
            plugin.getWalletDataManager().overlayCache(cached);
            return new WalletDataManager.BalanceDump(cached, Map.of());
        });
    }

    private Double snapshotEconomyBalance(UUID uuid, EconomyDataManager.BalanceDump dump) {
        if (plugin.getEconomy() == null) {
            return null;
        }
        return dump.balances().getOrDefault(uuid, 0.0D);
    }

    private Double snapshotWalletBalance(UUID uuid, WalletDataManager.BalanceDump dump) {
        if (plugin.getWalletDataManager() == null) {
            return null;
        }
        return dump.balances().getOrDefault(uuid, 0.0D);
    }

    private String snapshotPlayerName(UUID uuid, String fallback,
                                      EconomyDataManager.BalanceDump economyDump,
                                      WalletDataManager.BalanceDump walletDump) {
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }
        String economyName = economyDump.names().get(uuid);
        if (economyName != null && !economyName.isBlank()) {
            return economyName;
        }
        String walletName = walletDump.names().get(uuid);
        if (walletName != null && !walletName.isBlank()) {
            return walletName;
        }
        return uuid.toString();
    }

    public void onPlayerJoin(Player player) {
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        ItemStack[] inv = deepCopy(player.getInventory().getContents());
        ItemStack[] armor = deepCopy(player.getInventory().getArmorContents());
        var mslfJoin = plugin.getMslfManager();
        var mslfEcJoin = mslfJoin != null ? mslfJoin.enderChestService() : null;
        ItemStack[] ec = deepCopy(mslfEcJoin != null
            ? mslfEcJoin.captureContents(player)
            : player.getEnderChest().getContents());
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
            sessionCache.loadSession(uuid, name, inv, armor, ec)
        );
        applyQueuedRollbackRestore(player);
        applyQueuedStatRestore(player);
        checkPendingBackup(player);
    }

    private void applyQueuedRollbackRestore(Player player) {
        UUID uuid = player.getUniqueId();
        dataManager.getQueuedRollbackRestore(uuid).thenAccept(queuedRestore -> {
            if (queuedRestore != null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        if (player.getOpenInventory() != null) {
                            player.closeInventory();
                        }
                        if (queuedRestore.hasInventory()) {
                            PlayerInventories.apply(player, queuedRestore.inventory(), queuedRestore.armor());
                            if (queuedRestore.enderChest() != null) {
                                var mslf = plugin.getMslfManager();
                                var mslfEc = mslf != null ? mslf.enderChestService() : null;
                                if (mslfEc != null) {
                                    mslfEc.restoreFromSnapshot(player, queuedRestore.enderChest());
                                } else {
                                    player.getEnderChest().setContents(queuedRestore.enderChest());
                                }
                            }
                            player.updateInventory();
                            player.saveData();
                            ItemStack[] vanillaEc = queuedRestore.enderChest();
                            if (vanillaEc != null && vanillaEc.length > 27) {
                                ItemStack[] slice = new ItemStack[27];
                                System.arraycopy(vanillaEc, 0, slice, 0, 27);
                                vanillaEc = slice;
                            }
                            syncRollbackInventoryToItemSync(
                                uuid,
                                player.getName(),
                                queuedRestore.inventory(),
                                queuedRestore.armor(),
                                vanillaEc
                            );
                        }

                        if (queuedRestore.economyBalance() != null) {
                            if (plugin.getEconomy() != null) {
                                plugin.getEconomy().setBalance(uuid, queuedRestore.economyBalance())
                                    .thenCompose(v -> plugin.getEconomy().flushNow());
                            }
                        }
                        if (queuedRestore.walletBalance() != null) {
                            if (plugin.getWalletDataManager() != null) {
                                plugin.getWalletDataManager().setBalance(uuid, queuedRestore.walletBalance(), "ROLLBACK_JOIN", "System", "ROLLBACK", "system", "Rollback applied on join");
                            }
                        }
                        if (plugin.getHollyPlaceholderExpansion() != null) {
                            plugin.getHollyPlaceholderExpansion().invalidatePlayer(uuid);
                        }

                        dataManager.clearQueuedRollbackRestore(uuid);
                        player.sendMessage(ColorUtils.colorizeComponent("&aTwój ekwipunek, enderchest i stan konta zostały przywrócone po cofce serwera."));
                    }
                });
            }
        });
    }

    public void onPlayerQuit(Player player) {
        PlayerSessionCache.PlayerSession session = sessionCache.getSession(player.getUniqueId());
        if (session != null) {
            UUID uuid = player.getUniqueId();
            ItemStack[] inv = deepCopy(player.getInventory().getContents());
            ItemStack[] armor = deepCopy(player.getInventory().getArmorContents());
            var mslfQuit = plugin.getMslfManager();
            var mslfEcQuit = mslfQuit != null ? mslfQuit.enderChestService() : null;
            ItemStack[] ec = deepCopy(mslfEcQuit != null
                ? mslfEcQuit.captureContents(player)
                : player.getEnderChest().getContents());
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
                sessionCache.updateSession(uuid, inv, armor, ec)
            );
        }

        BossBar bar = pendingBackupBars.remove(player.getUniqueId());
        if (bar != null) {
            player.hideBossBar(bar);
        }

        sessionCache.removeSession(player.getUniqueId());
    }

    public void onPlayerDeath(Player player, String deathCause, String killerName, String location, int preDeathElo,
                              ItemStack[] deathInv, ItemStack[] deathArmor) {
        onPlayerDeath(player.getUniqueId(), player.getName(), deathCause, killerName, location, preDeathElo, deathInv, deathArmor);
    }

    public void onPlayerDeath(UUID playerUuid, String playerName, String deathCause, String killerName, String location,
                              int preDeathElo, ItemStack[] deathInv, ItemStack[] deathArmor) {
        ItemStack[] inv = deathInv;
        ItemStack[] armor = deathArmor;

        if (!BackupDataManager.hasAnyItems(inv) && !BackupDataManager.hasAnyItems(armor)) {
            PlayerSessionCache.PlayerSession session = sessionCache.getSession(playerUuid);
            if (session != null) {
                inv = session.getInventory();
                armor = session.getArmor();
            }
        }

        if (!BackupDataManager.hasAnyItems(inv) && !BackupDataManager.hasAnyItems(armor)) {
            return;
        }

        dataManager.saveDeathSnapshot(
            playerUuid,
            playerName,
            inv,
            armor,
            deathCause,
            killerName,
            location,
            preDeathElo
        ).thenRun(() -> plugin.getLogger().info("Death snapshot saved for " + playerName));
    }

    public void onPlayerDeath(Player player, String deathCause, String killerName, String location, int preDeathElo) {
        onPlayerDeath(player, deathCause, killerName, location, preDeathElo, null, null);
    }

    public void updatePlayerSession(Player player) {
        sessionCache.updateSession(player);
    }

    public void captureAndUpdateSessionAsync(Player player) {
        PlayerSessionCache.PlayerSession session = sessionCache.getSession(player.getUniqueId());
        if (session == null) return;
        UUID uuid = player.getUniqueId();
        ItemStack[] inv = deepCopy(player.getInventory().getContents());
        ItemStack[] armor = deepCopy(player.getInventory().getArmorContents());
        var mslf = plugin.getMslfManager();
        var mslfEc = mslf != null ? mslf.enderChestService() : null;
        ItemStack[] ec = deepCopy(mslfEc != null
            ? mslfEc.captureContents(player)
            : player.getEnderChest().getContents());
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
            sessionCache.updateSession(uuid, inv, armor, ec)
        );
    }

    private void applyQueuedStatRestore(Player player) {
        dataManager.getQueuedDeathStatRestore(player.getUniqueId()).thenAccept(toRemove -> {
            if (toRemove <= 0) {
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    // Licznik zostaje w kolejce — zostanie zastosowany przy kolejnym wejsciu.
                    return;
                }
                int currentDeaths = player.getStatistic(Statistic.DEATHS);
                player.setStatistic(Statistic.DEATHS, Math.max(0, currentDeaths - toRemove));
                dataManager.clearQueuedDeathStatRestore(player.getUniqueId());
            });
        });
    }

    private void checkPendingBackup(Player player) {
        dataManager.getApprovedBackupForPlayer(player.getUniqueId()).thenAccept(backup -> {
            if (backup != null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    showPendingBackupBar(player);
                    player.sendMessage(ColorUtils.colorizeComponent(
                        plugin.getConfigManager().getBackupConfig().getMessages().getBackupPending()
                    ));
                });
            }
        });
    }

    private void showPendingBackupBar(Player player) {
        if (pendingBackupBars.containsKey(player.getUniqueId())) {
            return;
        }

        String title = plugin.getConfigManager().getBackupConfig().getPendingBossBarText();
        BossBar bar = BossBar.bossBar(
            ColorUtils.colorizeComponent(title),
            1.0f,
            BossBar.Color.YELLOW,
            BossBar.Overlay.PROGRESS
        );
        pendingBackupBars.put(player.getUniqueId(), bar);
        player.showBossBar(bar);
    }

    public void hidePendingBackupBar(Player player) {
        BossBar bar = pendingBackupBars.remove(player.getUniqueId());
        if (bar != null) {
            player.hideBossBar(bar);
        }
    }

    private void updateBossBars() {
        pendingBackupBars.entrySet().removeIf(entry -> {
            Player player = Bukkit.getPlayer(entry.getKey());
            return player == null || !player.isOnline();
        });
    }

    public CompletableFuture<List<BackupDataManager.DeathRecord>> getPlayerDeaths(UUID playerUuid) {
        return dataManager.getPlayerDeaths(playerUuid);
    }

    public CompletableFuture<List<BackupDataManager.DeathRecord>> findDeathsByNick(String nick) {
        return dataManager.findDeathsByNick(nick);
    }

    public CompletableFuture<Void> requestBackup(UUID playerUuid, String playerName,
                                                 String deathId, UUID requestedBy) {
        String requesterName = Optional.ofNullable(Bukkit.getOfflinePlayer(requestedBy).getName()).orElse(requestedBy.toString());
        return dataManager.createPendingBackup(playerUuid, playerName, deathId, requestedBy).thenRun(() ->
            plugin.getWebhookLogManager().logBackupRequested(playerName, requesterName, requestedBy, deathId)
        );
    }

    public CompletableFuture<Void> grantBackup(UUID playerUuid, String playerName, String deathId,
                                               UUID requestedBy, UUID approvedBy) {
        // Sprawdź czy gracz już otrzymał ten backup
        return dataManager.hasPlayerReceivedBackup(playerUuid, deathId).thenCompose(alreadyGranted -> {
            if (alreadyGranted) {
                // Sprawdź czy osoba nadająca ma OP
                Player approver = Bukkit.getPlayer(approvedBy);
                boolean hasOp = approver != null && approver.isOp();

                if (!hasOp) {
                    // Nie ma OP i backup już został nadany - odmów
                    return CompletableFuture.runAsync(() -> {
                        if (approver != null) {
                            Bukkit.getScheduler().runTask(plugin, () ->
                                approver.sendMessage(ColorUtils.colorizeComponent(
                                    "&c✕ &8Ξ &cTen backup został już nadany graczowi &e" + playerName + "&c!"
                                ))
                            );
                        }
                    });
                }
                // Ma OP - pozwól nadać ponownie
            }

            return dataManager.createApprovedBackup(playerUuid, playerName, deathId, requestedBy, approvedBy).thenRun(() -> {
                Player player = Bukkit.getPlayer(playerUuid);
                if (player != null && player.isOnline()) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        showPendingBackupBar(player);
                        player.sendMessage(ColorUtils.colorizeComponent(
                            plugin.getConfigManager().getBackupConfig().getMessages().getBackupAccepted()
                        ));
                    });
                }

                String approverName = Optional.ofNullable(Bukkit.getOfflinePlayer(approvedBy).getName()).orElse(approvedBy.toString());
                plugin.getWebhookLogManager().logBackupEqGranted(playerName, approverName, approvedBy, deathId);
            });
        });
    }

    public CompletableFuture<List<BackupDataManager.PendingBackup>> getAllPendingBackups() {
        return dataManager.getAllPendingBackups();
    }

    public CompletableFuture<BackupDataManager.PendingBackup> getPendingBackupById(String backupId) {
        return dataManager.getPendingBackupById(backupId);
    }

    public CompletableFuture<BackupDataManager.PendingBackup> getPendingBackupById(String backupId, String databaseName) {
        return dataManager.getPendingBackupById(backupId, databaseName);
    }

    public CompletableFuture<Void> approveBackup(String backupId, UUID approvedBy) {
        return dataManager.getPendingBackupById(backupId).thenCompose(backup -> {
            if (backup == null) {
                return CompletableFuture.completedFuture(null);
            }

            String approverName = Optional.ofNullable(Bukkit.getOfflinePlayer(approvedBy).getName()).orElse(approvedBy.toString());
            return dataManager.approveBackup(backupId, approvedBy).thenRun(() -> {
                Player player = Bukkit.getPlayer(backup.playerUuid());
                if (player != null && player.isOnline()) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        showPendingBackupBar(player);
                        player.sendMessage(ColorUtils.colorizeComponent(
                            plugin.getConfigManager().getBackupConfig().getMessages().getBackupAccepted()
                        ));
                    });
                }

                if (plugin.getConfigManager().getBackupConfig().isDiscordNotificationsEnabled()) {
                    sendDiscordNotification(backup.playerName(), approvedBy);
                }
                plugin.getWebhookLogManager().logBackupEqGranted(backup.playerName(), approverName, approvedBy, backup.deathId());
            });
        });
    }

    public CompletableFuture<Boolean> restoreStats(BackupDataManager.DeathRecord death, UUID restoredBy) {
        // Modul clans (a z nim EloManager) bywa wylaczony — wtedy pomijamy sam ELO,
        // ale statystyka smierci i log nadal musza sie wykonac.
        dev.grzybalson.modules.ranking.EloManager elo = plugin.getEloManager();
        CompletableFuture<Void> future;
        if (elo == null) {
            future = CompletableFuture.completedFuture(null);
        } else {
            int startingElo = plugin.getModuleConfigManager().getEloSettingsConfig().getStartingElo();
            future = elo.getEloAsync(death.playerUuid()).thenCompose(currentElo -> {
                int eloToRestore = death.preDeathElo();
                if (eloToRestore <= startingElo && currentElo > startingElo) {
                    eloToRestore = currentElo;
                }
                return elo.setEloAsync(death.playerUuid(), eloToRestore);
            });
        }

        Player target = Bukkit.getPlayer(death.playerUuid());
        if (target != null && target.isOnline()) {
            future = future.thenCompose(v -> {
                CompletableFuture<Void> deathFuture = new CompletableFuture<>();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    int currentDeaths = target.getStatistic(Statistic.DEATHS);
                    target.setStatistic(Statistic.DEATHS, Math.max(0, currentDeaths - 1));
                    target.sendMessage(ColorUtils.colorizeComponent(
                        plugin.getConfigManager().getBackupConfig().getMessages().getBackupStatsRestoredTarget()
                    ));
                    deathFuture.complete(null);
                });
                return deathFuture;
            });
        } else {
            future = future.thenCompose(v -> dataManager.queueDeathStatRestore(death.playerUuid(), 1));
        }

        return future.thenApply(v -> {
            plugin.getWebhookLogManager().logBackupStatsRestored(
                death.playerName(),
                Optional.ofNullable(Bukkit.getOfflinePlayer(restoredBy).getName()).orElse(restoredBy.toString()),
                restoredBy,
                death.id()
            );
            return true;
        });
    }

    public CompletableFuture<Void> markStatsRestored(String backupId, UUID restoredBy) {
        return dataManager.markStatsRestored(backupId, restoredBy);
    }

    public CompletableFuture<Void> deleteBackup(String backupId) {
        return dataManager.deletePendingBackup(backupId);
    }

    public CompletableFuture<Void> deleteBackup(String backupId, String databaseName) {
        return dataManager.deletePendingBackup(backupId, databaseName);
    }

    public CompletableFuture<BackupDataManager.DeathRecord> getDeathById(String deathId) {
        return dataManager.getDeathById(deathId);
    }

    public CompletableFuture<Boolean> claimBackup(Player player) {
        return openClaimGui(player).thenApply(opened -> opened);
    }

    public CompletableFuture<Boolean> openClaimGui(Player player) {
        return dataManager.getApprovedBackupsForPlayer(player.getUniqueId()).thenCompose(backups -> {
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    future.complete(false);
                    return;
                }
                if (backups == null || backups.isEmpty()) {
                    player.sendMessage(ColorUtils.colorizeComponent(
                        plugin.getConfigManager().getBackupConfig().getMessages().getNoBackupPending()
                    ));
                    future.complete(false);
                    return;
                }
                new dev.grzybalson.gui.ClaimBackupGUI(plugin, player, backups).open();
                future.complete(true);
            });
            return future;
        });
    }

    public CompletableFuture<Boolean> claimBackupById(Player player, String backupId) {
        String playerName = player.getName();
        UUID playerUuid = player.getUniqueId();
        if (!isInventoryEmpty(player)) {
            player.sendMessage(ColorUtils.colorizeComponent(
                plugin.getConfigManager().getBackupConfig().getMessages().getInventoryNotEmpty()
            ));
            return CompletableFuture.completedFuture(false);
        }

        return dataManager.getPendingBackupById(backupId).thenCompose(backup -> {
            if (backup == null
                || !"approved".equalsIgnoreCase(backup.status())
                || !backup.playerUuid().equals(playerUuid)) {
                Bukkit.getScheduler().runTask(plugin, () ->
                    player.sendMessage(ColorUtils.colorizeComponent(
                        plugin.getConfigManager().getBackupConfig().getMessages().getNoBackupPending()
                    )));
                return CompletableFuture.completedFuture(false);
            }

            return dataManager.getDeathById(backup.deathId()).thenCompose(death -> {
                if (death == null) {
                    return CompletableFuture.completedFuture(false);
                }

                CompletableFuture<Boolean> applied = new CompletableFuture<>();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline() || !isInventoryEmpty(player)) {
                        player.sendMessage(ColorUtils.colorizeComponent(
                            plugin.getConfigManager().getBackupConfig().getMessages().getInventoryNotEmpty()
                        ));
                        applied.complete(false);
                        return;
                    }
                    ItemStack[] inventory = death.inventory();
                    ItemStack[] armor = death.armor();
                    PlayerInventories.apply(player, inventory, armor);

                    if (plugin.getUniqueItemService() != null) {
                        java.util.Set<String> uids = new java.util.HashSet<>();
                        collectUids(inventory, uids);
                        collectUids(armor, uids);
                        if (!uids.isEmpty()) {
                            plugin.getUniqueItemService().removeDuplicatesForOwner(playerUuid, uids);
                        }
                    }

                    player.sendMessage(ColorUtils.colorizeComponent(
                        plugin.getConfigManager().getBackupConfig().getMessages().getBackupReceived()
                    ));
                    applied.complete(true);
                });

                return applied.thenCompose(ok -> {
                    if (!ok) {
                        return CompletableFuture.completedFuture(false);
                    }
                    return dataManager.deletePendingBackup(backup.id()).thenCompose(v ->
                        dataManager.getApprovedBackupsForPlayer(playerUuid).thenApply(remaining -> {
                            plugin.getWebhookLogManager().logBackupClaimed(playerName, playerUuid);
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                if (!player.isOnline()) {
                                    return;
                                }
                                java.util.List<BackupDataManager.PendingBackup> left = remaining == null
                                    ? java.util.List.of()
                                    : remaining.stream()
                                        .filter(b -> b != null && !backup.id().equals(b.id()))
                                        .toList();
                                if (left.isEmpty()) {
                                    hidePendingBackupBar(player);
                                    player.closeInventory();
                                } else {
                                    // Odebrany znika z listy — GUI zostaje z pozostałymi.
                                    new dev.grzybalson.gui.ClaimBackupGUI(plugin, player, left).open();
                                }
                            });
                            return true;
                        })
                    );
                });
            });
        });
    }

    private void collectUids(ItemStack[] items, java.util.Set<String> out) {
        if (items == null || plugin.getUniqueItemService() == null) {
            return;
        }
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            String uid = plugin.getUniqueItemService().getUid(item);
            if (uid != null) {
                out.add(uid);
            }
        }
    }

    private boolean isInventoryEmpty(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && !item.getType().isAir()) {
                return false;
            }
        }
        for (ItemStack item : player.getInventory().getArmorContents()) {
            if (item != null && !item.getType().isAir()) {
                return false;
            }
        }
        ItemStack offhand = player.getInventory().getItemInOffHand();
        return offhand == null || offhand.getType().isAir();
    }

    private void sendDiscordNotification(String playerName, UUID approvedBy) {
        String webhookUrl = plugin.getConfigManager().getBackupConfig().getDiscordWebhookUrl();
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            return;
        }

        String approverName = Bukkit.getOfflinePlayer(approvedBy).getName();

        CompletableFuture.runAsync(() -> {
            try {
                URL url = new URL(webhookUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                String json = String.format(
                    "{\"embeds\":[{\"title\":\"Backup Zaakceptowany\",\"description\":\"Gracz **%s** otrzymał backup od **%s**\",\"color\":65280}]}",
                    playerName, approverName
                );

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(json.getBytes(StandardCharsets.UTF_8));
                }

                conn.getResponseCode();
                conn.disconnect();
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to send Discord notification: " + e.getMessage());
            }
        });
    }

    public PlayerSessionCache getSessionCache() {
        return sessionCache;
    }

    public BackupDataManager getDataManager() {
        return dataManager;
    }
}
