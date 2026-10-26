package dev.grzybalson.modules.backup;

import dev.grzybalson.HollyCore;
import dev.grzybalson.gui.BackupAdminGUI;
import dev.grzybalson.gui.BackupDeathSelectGUI;
import dev.grzybalson.gui.BackupPreviewGUI;
import dev.grzybalson.gui.ClaimBackupGUI;
import dev.grzybalson.gui.PlayerRollbackPreviewGUI;
import dev.grzybalson.gui.PlayerRollbackSelectGUI;
import dev.grzybalson.gui.ServerRollbackConfirmGUI;
import dev.grzybalson.gui.ServerRollbackSelectGUI;
import dev.grzybalson.modules.backup.BackupDataManager;
import dev.grzybalson.modules.backup.RollbackManager;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.NBTUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class BackupGUIListener implements Listener {

    private final HollyCore plugin;
    private final Set<UUID> backupInventoryViewers = ConcurrentHashMap.newKeySet();
    private final Set<String> grantsInFlight = ConcurrentHashMap.newKeySet();

    public BackupGUIListener(HollyCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!backupInventoryViewers.contains(player.getUniqueId())) {
            return;
        }

        if (event.getInventory().getHolder() instanceof BackupDeathSelectGUI deathSelectGUI) {
            event.setCancelled(true);
            if (event.getClickedInventory() == event.getInventory()) {
                handleDeathSelectClick(event, player, deathSelectGUI);
            }
        } else if (event.getInventory().getHolder() instanceof ClaimBackupGUI claimGUI) {
            event.setCancelled(true);
            if (event.getClickedInventory() == event.getInventory()) {
                handleClaimBackupClick(event, player, claimGUI);
            }
        } else if (event.getInventory().getHolder() instanceof BackupAdminGUI) {
            event.setCancelled(true);
            if (event.getClickedInventory() == event.getInventory()) {
                handleAdminGUIClick(event, player);
            }
        } else if (event.getInventory().getHolder() instanceof BackupPreviewGUI previewGUI) {
            event.setCancelled(true);
            if (event.getClickedInventory() == event.getInventory()) {
                handlePreviewGUIClick(event, player, previewGUI);
            }
        } else if (event.getInventory().getHolder() instanceof PlayerRollbackSelectGUI rollbackSelectGUI) {
            event.setCancelled(true);
            if (event.getClickedInventory() == event.getInventory()) {
                handlePlayerRollbackSelectClick(event, player, rollbackSelectGUI);
            }
        } else if (event.getInventory().getHolder() instanceof PlayerRollbackPreviewGUI rollbackPreviewGUI) {
            event.setCancelled(true);
            if (event.getClickedInventory() == event.getInventory()) {
                handlePlayerRollbackPreviewClick(event, player, rollbackPreviewGUI);
            }
        } else if (event.getInventory().getHolder() instanceof ServerRollbackSelectGUI serverRollbackSelectGUI) {
            event.setCancelled(true);
            if (event.getClickedInventory() == event.getInventory()) {
                handleServerRollbackSelectClick(event, player, serverRollbackSelectGUI);
            }
        } else if (event.getInventory().getHolder() instanceof ServerRollbackConfirmGUI serverRollbackConfirmGUI) {
            event.setCancelled(true);
            if (event.getClickedInventory() == event.getInventory()) {
                handleServerRollbackConfirmClick(event, player, serverRollbackConfirmGUI);
            }
        }
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player && isBackupHolder(event.getInventory().getHolder())) {
            backupInventoryViewers.add(player.getUniqueId());
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            backupInventoryViewers.remove(player.getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        backupInventoryViewers.remove(event.getPlayer().getUniqueId());
        String prefix = event.getPlayer().getUniqueId() + "|";
        grantsInFlight.removeIf(key -> key.startsWith(prefix));
    }

    private void handleClaimBackupClick(InventoryClickEvent event, Player player, ClaimBackupGUI gui) {
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType().isAir()) {
            return;
        }
        String nav = NBTUtils.getString(item, "claim_nav");
        if ("close".equals(nav)) {
            player.closeInventory();
            return;
        }
        if ("prev".equals(nav)) {
            new ClaimBackupGUI(plugin, player, gui.getBackups(), gui.getPage() - 1).open();
            return;
        }
        if ("next".equals(nav)) {
            new ClaimBackupGUI(plugin, player, gui.getBackups(), gui.getPage() + 1).open();
            return;
        }
        String backupId = NBTUtils.getString(item, "claim_backup_id");
        if (backupId == null || backupId.isBlank()) {
            return;
        }
        plugin.getBackupManager().claimBackupById(player, backupId);
    }

    private void handleDeathSelectClick(InventoryClickEvent event, Player player, BackupDeathSelectGUI gui) {
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType().isAir()) {
            return;
        }

        String action = NBTUtils.getString(item, "action");
        if ("page_prev".equals(action)) {
            new BackupDeathSelectGUI(plugin, player, gui.getTargetUuid(), gui.getTargetName(), gui.getDeaths(), gui.getPage() - 1).open();
            return;
        }
        if ("page_next".equals(action)) {
            new BackupDeathSelectGUI(plugin, player, gui.getTargetUuid(), gui.getTargetName(), gui.getDeaths(), gui.getPage() + 1).open();
            return;
        }

        String deathId = NBTUtils.getString(item, "death_id");
        String targetUuid = NBTUtils.getString(item, "target_uuid");
        String targetName = NBTUtils.getString(item, "target_name");

        if (deathId == null || targetUuid == null || targetName == null) {
            return;
        }

        boolean canVerify = hasVerifyPermission(player);
        if (!canVerify) {
            plugin.getBackupManager().requestBackup(
                UUID.fromString(targetUuid),
                targetName,
                deathId,
                player.getUniqueId()
            ).thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> {
                player.closeInventory();
                player.sendMessage(ColorUtils.colorizeComponent(
                    plugin.getConfigManager().getBackupConfig().getMessages().getBackupRequested()
                ));
            }));
            return;
        }

        plugin.getBackupManager().getDeathById(deathId).thenAccept(death -> {
            if (death == null) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () ->
                new BackupPreviewGUI(plugin, player, death, BackupPreviewGUI.PreviewMode.DIRECT, null, gui.getPage()).open()
            );
        });
    }

    private void handleAdminGUIClick(InventoryClickEvent event, Player player) {
        if (!(event.getInventory().getHolder() instanceof BackupAdminGUI gui)) {
            return;
        }

        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType().isAir()) {
            return;
        }

        String action = NBTUtils.getString(item, "action");
        if ("page_prev".equals(action)) {
            new BackupAdminGUI(plugin, player, gui.getPendingBackups(), gui.getPage() - 1).open();
            return;
        }
        if ("page_next".equals(action)) {
            new BackupAdminGUI(plugin, player, gui.getPendingBackups(), gui.getPage() + 1).open();
            return;
        }

        String backupId = NBTUtils.getString(item, "backup_id");
        String deathId = NBTUtils.getString(item, "death_id");
        if (backupId == null || deathId == null) {
            return;
        }

        plugin.getBackupManager().getDeathById(deathId).thenAccept(death -> {
            if (death == null) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () ->
                new BackupPreviewGUI(plugin, player, death, BackupPreviewGUI.PreviewMode.VERIFY, backupId, gui.getPage()).open()
            );
        });
    }

    private void handlePreviewGUIClick(InventoryClickEvent event, Player player, BackupPreviewGUI gui) {
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType().isAir()) {
            return;
        }

        String action = NBTUtils.getString(item, "action");
        if (action == null) {
            return;
        }

        BackupDataManager.DeathRecord death = gui.getDeath();
        String requestId = gui.getRequestId();

        switch (action) {
            case "restore_eq" -> {
                if (gui.getMode() == BackupPreviewGUI.PreviewMode.VERIFY && requestId != null) {
                    plugin.getBackupManager().approveBackup(requestId, player.getUniqueId()).thenRun(() ->
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            player.sendMessage(ColorUtils.colorizeComponent(
                                plugin.getConfigManager().getBackupConfig().getMessages().getBackupEqGranted()
                            ));
                            refreshAdminGUI(player, gui.getReturnPage());
                        })
                    );
                } else {
                    // Blokada podwojnego klikniecia — nadanie jest async, GUI zamyka sie dopiero
                    // po insercie, wiec kazdy kolejny klik dawal drugi approved backup (dupe EQ).
                    String grantKey = player.getUniqueId() + "|" + death.id();
                    if (!grantsInFlight.add(grantKey)) {
                        return;
                    }
                    plugin.getBackupManager().grantBackup(
                        death.playerUuid(),
                        death.playerName(),
                        death.id(),
                        player.getUniqueId(),
                        player.getUniqueId()
                    ).whenComplete((ignored, error) -> grantsInFlight.remove(grantKey))
                    .thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> {
                        player.closeInventory();
                        player.sendMessage(ColorUtils.colorizeComponent(
                            plugin.getConfigManager().getBackupConfig().getMessages().getBackupEqGranted()
                        ));
                    }));
                }
            }
            case "restore_stats" -> plugin.getBackupManager().restoreStats(death, player.getUniqueId()).thenCompose(success -> {
                if (!success) {
                    return java.util.concurrent.CompletableFuture.completedFuture(null);
                }
                if (requestId != null) {
                    return plugin.getBackupManager().markStatsRestored(requestId, player.getUniqueId());
                }
                return java.util.concurrent.CompletableFuture.completedFuture(null);
            }).thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage(ColorUtils.colorizeComponent(
                    plugin.getConfigManager().getBackupConfig().getMessages().getBackupStatsRestored()
                ));
                if (gui.getMode() == BackupPreviewGUI.PreviewMode.VERIFY) {
                    refreshAdminGUI(player, gui.getReturnPage());
                }
            }));
            case "deny" -> {
                if (requestId == null) {
                    return;
                }
                plugin.getBackupManager().deleteBackup(requestId).thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage(ColorUtils.colorizeComponent(
                        plugin.getConfigManager().getBackupConfig().getMessages().getBackupDeleted()
                    ));
                    refreshAdminGUI(player, gui.getReturnPage());
                }));
            }
            case "back" -> {
                if (gui.getMode() == BackupPreviewGUI.PreviewMode.VERIFY) {
                    refreshAdminGUI(player, gui.getReturnPage());
                } else {
                    plugin.getBackupManager().getPlayerDeaths(death.playerUuid()).thenAccept(deaths ->
                        Bukkit.getScheduler().runTask(plugin, () ->
                            new BackupDeathSelectGUI(plugin, player, death.playerUuid(), death.playerName(), deaths, gui.getReturnPage()).open()
                        )
                    );
                }
            }
        }
    }

    private void handlePlayerRollbackSelectClick(InventoryClickEvent event, Player player, PlayerRollbackSelectGUI gui) {
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType().isAir()) {
            return;
        }

        String action = NBTUtils.getString(item, "action");
        if ("page_prev".equals(action)) {
            new PlayerRollbackSelectGUI(plugin, player, gui.getTargetUuid(), gui.getTargetName(), gui.getSnapshots(), gui.getPage() - 1).open();
            return;
        }
        if ("page_next".equals(action)) {
            new PlayerRollbackSelectGUI(plugin, player, gui.getTargetUuid(), gui.getTargetName(), gui.getSnapshots(), gui.getPage() + 1).open();
            return;
        }

        String indexRaw = NBTUtils.getString(item, "snapshot_index");
        if (indexRaw == null) {
            return;
        }

        int index;
        try {
            index = Integer.parseInt(indexRaw);
        } catch (NumberFormatException ignored) {
            return;
        }

        if (index < 0 || index >= gui.getSnapshots().size()) {
            return;
        }

        BackupDataManager.PlayerSnapshotData snapshot = gui.getSnapshots().get(index);
        new PlayerRollbackPreviewGUI(
            plugin,
            player,
            gui.getTargetUuid(),
            gui.getTargetName(),
            snapshot,
            gui.getSnapshots(),
            gui.getPage()
        ).open();
    }

    private void handleServerRollbackSelectClick(InventoryClickEvent event, Player player, ServerRollbackSelectGUI gui) {
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType().isAir()) {
            return;
        }

        String action = NBTUtils.getString(item, "action");
        if ("page_prev".equals(action)) {
            new ServerRollbackSelectGUI(plugin, player, gui.getSnapshots(), gui.getPage() - 1).open();
            return;
        }
        if ("page_next".equals(action)) {
            new ServerRollbackSelectGUI(plugin, player, gui.getSnapshots(), gui.getPage() + 1).open();
            return;
        }

        String indexRaw = NBTUtils.getString(item, "snapshot_index");
        if (indexRaw == null) {
            return;
        }

        int index;
        try {
            index = Integer.parseInt(indexRaw);
        } catch (NumberFormatException ignored) {
            return;
        }

        java.util.Date targetDate = gui.getSnapshotDate(index);
        if (targetDate == null) {
            return;
        }

        player.sendMessage(ColorUtils.colorizeComponent("&#00E0FFℹ &8Ξ &7Przygotowywanie podglądu cofki serwera..."));
        plugin.getRollbackManager().previewRollback(targetDate).thenAccept(preview ->
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (preview.playerUuids().isEmpty() && preview.marketSnapshot() == null) {
                    player.sendMessage(ColorUtils.colorizeComponent(
                        "&c✕ &8Ξ &cBrak snapshotów sprzed wybranej godziny."));
                    return;
                }
                new ServerRollbackConfirmGUI(
                    plugin,
                    player,
                    targetDate,
                    preview,
                    gui.getSnapshots(),
                    gui.getPage()
                ).open();
            })
        ).exceptionally(ex -> {
            Bukkit.getScheduler().runTask(plugin, () ->
                player.sendMessage(ColorUtils.colorizeComponent(
                    "&c✕ &8Ξ &cBłąd podglądu cofki: " + ex.getMessage()))
            );
            return null;
        });
    }

    private void handleServerRollbackConfirmClick(InventoryClickEvent event, Player player, ServerRollbackConfirmGUI gui) {
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType().isAir()) {
            return;
        }

        String action = NBTUtils.getString(item, "action");
        if ("cancel".equals(action)) {
            new ServerRollbackSelectGUI(plugin, player, gui.getAllSnapshots(), gui.getReturnPage()).open();
            return;
        }
        if (!"confirm".equals(action)) {
            return;
        }

        if (!player.hasPermission("holly.cmd.rollback")) {
            player.sendMessage(ColorUtils.colorizeComponent("&c✕ &8Ξ &cBrak uprawnień do cofki serwera."));
            return;
        }

        java.util.Date targetDate = gui.getTargetDate();
        player.closeInventory();
        player.sendMessage(ColorUtils.colorizeComponent(
            "&#00FF3B✔ &8Ξ &#4EFA76Rozpoczynanie cofki serwera do &f"
                + RollbackManager.DATE_FORMATTER.format(targetDate.toInstant()) + "&#4EFA76..."));

        plugin.getRollbackManager().executeRollback(targetDate).thenAccept(result ->
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (result.success()) {
                    player.sendMessage(ColorUtils.colorizeComponent("&8&m--------------------------------------------------"));
                    player.sendMessage(ColorUtils.colorizeComponent("&#00FF3B✔ &8Ξ &#4EFA76&lCOFKA SERWERA ZAKOŃCZONA SUKCESEM!"));
                    player.sendMessage(ColorUtils.colorizeComponent("&7Szczegóły: &f" + result.message()));
                    player.sendMessage(ColorUtils.colorizeComponent("&7Przywróceni gracze online: &a" + result.onlinePlayersRestored()));
                    player.sendMessage(ColorUtils.colorizeComponent("&7Zaplanowani gracze offline: &e" + result.offlinePlayersQueued()));
                    player.sendMessage(ColorUtils.colorizeComponent("&7Przywróconych ofert rynku: &f" + result.marketItemsRestored()));
                    player.sendMessage(ColorUtils.colorizeComponent("&8&m--------------------------------------------------"));

                    if (plugin.getRestartManager() != null) {
                        plugin.getRestartManager().startRollbackRestart(5, targetDate);
                    }
                } else {
                    player.sendMessage(ColorUtils.colorizeComponent("&c✕ &8Ξ &cBłąd cofki: " + result.message()));
                }
            })
        ).exceptionally(ex -> {
            Bukkit.getScheduler().runTask(plugin, () ->
                player.sendMessage(ColorUtils.colorizeComponent(
                    "&c✕ &8Ξ &cWystąpił błąd podczas wykonywania cofki: " + ex.getMessage()))
            );
            return null;
        });
    }

    private void handlePlayerRollbackPreviewClick(InventoryClickEvent event, Player player, PlayerRollbackPreviewGUI gui) {
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType().isAir()) {
            return;
        }

        String action = NBTUtils.getString(item, "action");
        if (action == null) {
            return;
        }

        switch (action) {
            case "restore" -> {
                if (!player.hasPermission("holly.cmd.rollback")) {
                    player.sendMessage(ColorUtils.colorizeComponent("&c✕ &8Ξ &cBrak uprawnień do cofki gracza."));
                    return;
                }
                plugin.getRollbackManager().executePlayerRollback(gui.getTargetUuid(), gui.getSnapshot()).thenAccept(result ->
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (result.success()) {
                            player.sendMessage(ColorUtils.colorizeComponent(
                                "&#00FF3B✔ &8Ξ &#4EFA76Cofnięto gracza &f" + gui.getTargetName()
                                    + " &#4EFA76do &f"
                                    + (gui.getSnapshot().createdAt() != null
                                    ? RollbackManager.DATE_FORMATTER.format(gui.getSnapshot().createdAt())
                                    : "?")
                                    + "&#4EFA76. &7" + result.message()
                            ));
                            player.closeInventory();
                        } else {
                            player.sendMessage(ColorUtils.colorizeComponent("&c✕ &8Ξ &c" + result.message()));
                        }
                    })
                ).exceptionally(ex -> {
                    Bukkit.getScheduler().runTask(plugin, () ->
                        player.sendMessage(ColorUtils.colorizeComponent("&c✕ &8Ξ &cBłąd cofki: " + ex.getMessage()))
                    );
                    return null;
                });
            }
            case "back" -> new PlayerRollbackSelectGUI(
                plugin,
                player,
                gui.getTargetUuid(),
                gui.getTargetName(),
                gui.getAllSnapshots(),
                gui.getReturnPage()
            ).open();
            default -> {
            }
        }
    }

    private void refreshAdminGUI(Player player, int page) {
        plugin.getBackupManager().getAllPendingBackups().thenAccept(backups -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (backups.isEmpty()) {
                player.closeInventory();
                player.sendMessage(ColorUtils.colorizeComponent(
                    plugin.getConfigManager().getBackupConfig().getMessages().getBackupListEmpty()
                ));
            } else {
                new BackupAdminGUI(plugin, player, backups, page).open();
            }
        }));
    }

    private boolean hasVerifyPermission(Player player) {
        return player.hasPermission("holly.cmd.backup.verify");
    }

    private boolean isBackupHolder(InventoryHolder holder) {
        return holder instanceof BackupDeathSelectGUI
            || holder instanceof ClaimBackupGUI
            || holder instanceof BackupAdminGUI
            || holder instanceof BackupPreviewGUI
            || holder instanceof PlayerRollbackSelectGUI
            || holder instanceof PlayerRollbackPreviewGUI
            || holder instanceof ServerRollbackSelectGUI
            || holder instanceof ServerRollbackConfirmGUI;
    }
}
