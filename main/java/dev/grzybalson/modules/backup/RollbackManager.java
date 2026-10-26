package dev.grzybalson.modules.backup;

import dev.grzybalson.HollyCore;
import org.bson.Document;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.ItemClientSanitizer;
import dev.grzybalson.utils.PlayerInventories;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class RollbackManager {

    public static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
        .withZone(ZoneId.systemDefault());

    private final HollyCore plugin;
    private final BackupDataManager dataManager;

    public RollbackManager(HollyCore plugin, BackupDataManager dataManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
    }

    public CompletableFuture<RollbackPreview> previewRollback(Date targetDate) {
        return dataManager.getSnapshotPlayerUuidsBefore(targetDate).thenCombine(
            dataManager.getLatestMarketSnapshotBefore(targetDate),
            (playerUuids, marketSnapshot) -> {
                int marketItemsCount = 0;
                if (marketSnapshot != null && marketSnapshot.getList("activeItems", Document.class) != null) {
                    marketItemsCount = marketSnapshot.getList("activeItems", Document.class).size();
                }
                return new RollbackPreview(targetDate, playerUuids, marketSnapshot, marketItemsCount);
            }
        );
    }

    public CompletableFuture<RollbackResult> executeRollback(Date targetDate) {
        return dataManager.getLatestGlobalSnapshotsBefore(targetDate).thenCompose(playerSnapshots ->
            dataManager.getLatestMarketSnapshotBefore(targetDate).thenCompose(marketSnapshot ->
            dataManager.getLatestAuxSnapshotBefore(targetDate).thenCompose(auxSnapshot -> {
            if (playerSnapshots.isEmpty() && marketSnapshot == null && auxSnapshot == null) {
                return CompletableFuture.completedFuture(new RollbackResult(
                    false,
                    "Nie znaleziono żadnych snapshotów sprzed " + DATE_FORMATTER.format(targetDate.toInstant()),
                    0, 0, 0
                ));
            }

            // Najpierw aux (pełny EC MSLF, kostiumy/pety, torby) — potem EQ graczy.
            CompletableFuture<Void> auxFirst = auxSnapshot == null
                ? CompletableFuture.completedFuture(null)
                : dataManager.restoreAuxiliarySnapshot(auxSnapshot);

            return auxFirst.thenCompose(ignored -> {
            List<CompletableFuture<?>> futures = new ArrayList<>();

            int onlineRestored = 0;
            int offlineQueued = 0;

            for (Map.Entry<UUID, BackupDataManager.PlayerSnapshotData> entry : playerSnapshots.entrySet()) {
                UUID uuid = entry.getKey();
                BackupDataManager.PlayerSnapshotData snapshot = entry.getValue();
                Player online = Bukkit.getPlayer(uuid);

                if (online != null && online.isOnline()) {
                    onlineRestored++;
                    Bukkit.getScheduler().runTask(plugin, () -> applyPlayerSnapshot(online, snapshot));
                    restoreBalances(uuid, snapshot, targetDate, futures);
                    if (plugin.getHollyPlaceholderExpansion() != null) {
                        plugin.getHollyPlaceholderExpansion().invalidatePlayer(uuid);
                    }
                } else {
                    offlineQueued++;
                    if (snapshot.hasInventory()) {
                        futures.add(dataManager.queueOfflineRollbackRestore(uuid, snapshot));
                    }
                    restoreBalances(uuid, snapshot, targetDate, futures);
                }
            }

            int marketItemsCount = 0;
            if (marketSnapshot != null) {
                var mongo = plugin.getMongoManager();
                List<Document> activeItems = marketSnapshot.getList("activeItems", Document.class);
                List<Document> expiredItems = marketSnapshot.getList("expiredItems", Document.class);
                List<Document> offlinePayments = marketSnapshot.getList("offlinePayments", Document.class);

                marketItemsCount = activeItems != null ? activeItems.size() : 0;

                futures.add(
                    mongo.toFuture(mongo.getCollection("market_items").deleteMany(new Document()))
                        .thenCompose(r -> activeItems != null && !activeItems.isEmpty()
                            ? mongo.toFuture(mongo.getCollection("market_items").insertMany(activeItems))
                            : CompletableFuture.completedFuture(null))
                        .thenCompose(r -> mongo.toFuture(mongo.getCollection("market_expired_items").deleteMany(new Document())))
                        .thenCompose(r -> expiredItems != null && !expiredItems.isEmpty()
                            ? mongo.toFuture(mongo.getCollection("market_expired_items").insertMany(expiredItems))
                            : CompletableFuture.completedFuture(null))
                        .thenCompose(r -> mongo.toFuture(mongo.getCollection("offline_payments").deleteMany(new Document())))
                        .thenCompose(r -> offlinePayments != null && !offlinePayments.isEmpty()
                            ? mongo.toFuture(mongo.getCollection("offline_payments").insertMany(offlinePayments))
                            : CompletableFuture.completedFuture(null))
                        .thenRun(() -> {
                            if (plugin.getMarketManager() != null && plugin.getMarketManager().getDataManager() != null) {
                                plugin.getMarketManager().getDataManager().loadAllItems();
                            }
                        })
                );
            }

            futures.add(dataManager.rollbackPendingBackupsAfter(targetDate));

            if (auxSnapshot != null) {
                Bukkit.getScheduler().runTask(plugin, this::afterAuxiliaryRestore);
            }

            if (plugin.getEconomy() != null) {
                futures.add(plugin.getEconomy().flushNow());
            }

            final int finalOnline = onlineRestored;
            final int finalOffline = offlineQueued;
            final int finalMarket = marketItemsCount;

            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenApply(v -> {
                if (plugin.getHollyPlaceholderExpansion() != null) {
                    plugin.getHollyPlaceholderExpansion().invalidateAll();
                }
                if (plugin.getTopManager() != null) {
                    plugin.getTopManager().requestRefresh();
                }
                return new RollbackResult(
                    true,
                    "Pomyślnie cofnięto stan serwera do " + DATE_FORMATTER.format(targetDate.toInstant()),
                    finalOnline,
                    finalOffline,
                    finalMarket
                );
            });
            });
            }))
        );
    }

    /** Cofka jednego gracza — bez restartu serwera. */
    public CompletableFuture<PlayerRollbackResult> executePlayerRollback(UUID uuid, BackupDataManager.PlayerSnapshotData snapshot) {
        if (uuid == null || snapshot == null) {
            return CompletableFuture.completedFuture(new PlayerRollbackResult(false, "Brak danych snapshotu.", false));
        }

        List<CompletableFuture<?>> futures = new ArrayList<>();
        Player online = Bukkit.getPlayer(uuid);
        boolean appliedNow = false;

        if (online != null && online.isOnline()) {
            appliedNow = true;
            Bukkit.getScheduler().runTask(plugin, () -> applyPlayerSnapshot(online, snapshot));
        } else if (snapshot.hasInventory()) {
            futures.add(dataManager.queueOfflineRollbackRestore(uuid, snapshot));
        }

        Date targetDate = snapshot.createdAt() != null ? Date.from(snapshot.createdAt()) : new Date();
        restoreBalances(uuid, snapshot, targetDate, futures);

        if (plugin.getEconomy() != null) {
            futures.add(plugin.getEconomy().flushNow());
        }

        final boolean finalAppliedNow = appliedNow;
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenApply(ignored -> {
            if (plugin.getHollyPlaceholderExpansion() != null) {
                plugin.getHollyPlaceholderExpansion().invalidatePlayer(uuid);
            }
            String message = finalAppliedNow
                ? "Przywrócono EQ, enderchest i salda (gracz online)."
                : "Gracz offline — EQ/EC i salda zostaną nadane przy następnym wejściu.";
            return new PlayerRollbackResult(true, message, finalAppliedNow);
        });
    }

    private void applyPlayerSnapshot(Player player, BackupDataManager.PlayerSnapshotData snapshot) {
        if (player == null || !player.isOnline() || snapshot == null) {
            return;
        }
        if (player.getOpenInventory() != null) {
            player.closeInventory();
        }
        if (snapshot.hasInventory()) {
            ItemStack[] inventory = ItemClientSanitizer.forClient(snapshot.inventory());
            ItemStack[] armor = ItemClientSanitizer.forClient(snapshot.armor());
            ItemStack[] enderChest = ItemClientSanitizer.forClient(snapshot.enderChest());
            PlayerInventories.apply(player, inventory, armor);
            var mslf = plugin.getMslfManager();
            var mslfEc = mslf != null ? mslf.enderChestService() : null;
            if (mslfEc != null) {
                mslfEc.restoreFromSnapshot(player, enderChest);
            } else {
                player.getEnderChest().setContents(enderChest != null ? enderChest : new ItemStack[0]);
            }
            player.updateInventory();
            player.saveData();
            ItemStack[] vanillaEc = vanillaEnderSlice(enderChest);
            plugin.getBackupManager().syncRollbackInventoryToItemSync(
                player.getUniqueId(),
                player.getName(),
                inventory,
                armor,
                vanillaEc
            );
            player.sendMessage(ColorUtils.colorizeComponent(
                "&aTwój ekwipunek, enderchest i stan konta zostały przywrócone przez administrację."
            ));
        }
        reloadKostiumState(player.getUniqueId());
    }

    private static ItemStack[] vanillaEnderSlice(ItemStack[] enderChest) {
        ItemStack[] vanilla = new ItemStack[27];
        if (enderChest == null) {
            return vanilla;
        }
        for (int i = 0; i < Math.min(27, enderChest.length); i++) {
            vanilla[i] = enderChest[i] == null ? null : enderChest[i].clone();
        }
        return vanilla;
    }

    private void reloadKostiumState(UUID uuid) {
        var mslfKostiumy = plugin.getMslfKostiumyManager();
        if (mslfKostiumy != null) {
            if (mslfKostiumy.getKostiumyHandler() != null && mslfKostiumy.getKostiumyHandler().getDatabase() != null) {
                mslfKostiumy.getKostiumyHandler().getDatabase().invalidateCache(uuid);
                mslfKostiumy.getKostiumyHandler().getDatabase().loadRecordAsync(uuid);
            }
            if (mslfKostiumy.getPetyHandler() != null && mslfKostiumy.getPetyHandler().getDatabase() != null) {
                mslfKostiumy.getPetyHandler().getDatabase().invalidateCache(uuid);
                mslfKostiumy.getPetyHandler().getDatabase().loadRecordAsync(uuid);
            }
        }
    }

    private void afterAuxiliaryRestore() {
        var mslf = plugin.getMslfManager();
        if (mslf != null && mslf.enderChestService() != null) {
            mslf.enderChestService().reloadAllOnlineFromMongo();
        }
        var mslfKostiumy = plugin.getMslfKostiumyManager();
        if (mslfKostiumy != null) {
            if (mslfKostiumy.getKostiumyHandler() != null && mslfKostiumy.getKostiumyHandler().getDatabase() != null) {
                mslfKostiumy.getKostiumyHandler().getDatabase().invalidateAllCaches();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    mslfKostiumy.getKostiumyHandler().getDatabase().loadRecordAsync(player.getUniqueId());
                }
            }
            if (mslfKostiumy.getPetyHandler() != null && mslfKostiumy.getPetyHandler().getDatabase() != null) {
                mslfKostiumy.getPetyHandler().getDatabase().invalidateAllCaches();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    mslfKostiumy.getPetyHandler().getDatabase().loadRecordAsync(player.getUniqueId());
                }
            }
        }
    }

    private void restoreBalances(UUID uuid, BackupDataManager.PlayerSnapshotData snapshot, Date targetDate,
                                 List<CompletableFuture<?>> futures) {
        if (snapshot.economyBalance() != null && plugin.getEconomy() != null) {
            plugin.getEconomy().setBalance(uuid, snapshot.economyBalance());
        }
        if (snapshot.walletBalance() != null && plugin.getWalletDataManager() != null) {
            futures.add(plugin.getWalletDataManager().setBalance(
                uuid,
                snapshot.walletBalance(),
                "ROLLBACK",
                "System",
                "ROLLBACK",
                "system",
                "Server Rollback to " + DATE_FORMATTER.format(targetDate.toInstant())
            ));
        }
    }

    public CompletableFuture<Integer> recoverWalletBalances() {
        if (plugin.getWalletDataManager() == null) {
            return CompletableFuture.completedFuture(0);
        }
        var mongo = plugin.getMongoManager();
        var logsColl = mongo.getCollection("wallet_logs");

        return mongo.toListFuture(logsColl.find().sort(com.mongodb.client.model.Sorts.descending("timestamp"))).thenCompose(allLogs -> {
            Map<UUID, Double> recoveredBalances = new HashMap<>();
            Set<UUID> rollbackPlayers = new HashSet<>();
            Set<UUID> processedPlayers = new HashSet<>();

            for (Document doc : allLogs) {
                try {
                    String uuidStr = doc.getString("uuid");
                    if (uuidStr == null) continue;
                    UUID uuid = UUID.fromString(uuidStr);

                    String action = doc.getString("action");
                    if ("ROLLBACK".equalsIgnoreCase(action)) {
                        rollbackPlayers.add(uuid);
                        continue;
                    }

                    if (rollbackPlayers.contains(uuid) && !processedPlayers.contains(uuid)) {
                        Double prevBalance = doc.getDouble("balanceAfter");
                        if (prevBalance != null && prevBalance > 0) {
                            recoveredBalances.put(uuid, prevBalance);
                        }
                        processedPlayers.add(uuid);
                    }
                } catch (Throwable ignored) {}
            }

            List<CompletableFuture<?>> updateFutures = new ArrayList<>();
            for (Map.Entry<UUID, Double> entry : recoveredBalances.entrySet()) {
                UUID uuid = entry.getKey();
                double amount = entry.getValue();
                updateFutures.add(plugin.getWalletDataManager().setBalance(
                    uuid,
                    amount,
                    "RECOVERY",
                    "System",
                    "RECOVERY",
                    "system",
                    "Wallet balance auto-recovery"
                ));
                if (plugin.getEconomy() != null) {
                    plugin.getEconomy().setBalance(uuid, amount);
                }
                if (plugin.getHollyPlaceholderExpansion() != null) {
                    plugin.getHollyPlaceholderExpansion().invalidatePlayer(uuid);
                }
            }

            return CompletableFuture.allOf(updateFutures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    if (plugin.getTopManager() != null) {
                        plugin.getTopManager().requestRefresh();
                    }
                    return recoveredBalances.size();
                });
        });
    }

    public record RollbackPreview(Date targetDate,
                                  Set<UUID> playerUuids,
                                  Document marketSnapshot,
                                  int marketItemsCount) {}

    public record RollbackResult(boolean success,
                                 String message,
                                 int onlinePlayersRestored,
                                 int offlinePlayersQueued,
                                 int marketItemsRestored) {}

    public record PlayerRollbackResult(boolean success, String message, boolean appliedImmediately) {}
}
