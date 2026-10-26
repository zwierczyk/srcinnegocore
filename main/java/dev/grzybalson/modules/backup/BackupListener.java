package dev.grzybalson.modules.backup;

import dev.grzybalson.HollyCore;
import dev.grzybalson.modules.deathkeep.DeathKeepListener;
import dev.grzybalson.modules.ranking.EloManager;
import dev.grzybalson.utils.PlayerInventories;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class BackupListener implements Listener {

    private final HollyCore plugin;
    private final BackupManager backupManager;
    private final DeathKeepListener deathKeepListener;
    private final Map<UUID, CompletableFuture<Integer>> pendingPreDeathElo;

    public BackupListener(HollyCore plugin, BackupManager backupManager, DeathKeepListener deathKeepListener) {
        this.plugin = plugin;
        this.backupManager = backupManager;
        this.deathKeepListener = deathKeepListener;
        this.pendingPreDeathElo = new ConcurrentHashMap<>();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDeathCaptureElo(PlayerDeathEvent event) {
        EloManager eloManager = plugin.getEloManager();
        if (eloManager == null) {
            return;
        }

        UUID uuid = event.getEntity().getUniqueId();
        CompletableFuture<Integer> eloFuture = eloManager.hasCachedElo(uuid)
            ? CompletableFuture.completedFuture(eloManager.getElo(uuid))
            : eloManager.getEloAsync(uuid);
        pendingPreDeathElo.put(uuid, eloFuture);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        backupManager.onPlayerJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) {
            backupManager.updatePlayerSession(player);
        }

        pendingPreDeathElo.remove(player.getUniqueId());
        backupManager.onPlayerQuit(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID playerUuid = player.getUniqueId();
        // Wpis zdejmujemy zawsze, takze gdy nic nie przepadlo — inaczej mapa rosla przez caly uptime.
        CompletableFuture<Integer> eloFuture = pendingPreDeathElo.remove(playerUuid);

        // Po DeathKeepListener (HIGH) — zapisujemy TYLKO stracone itemy.
        DeathKeepListener.LostSnapshot lost = deathKeepListener != null
            ? deathKeepListener.consumeLostSnapshot(player.getUniqueId())
            : null;
        boolean fromDeathKeep = lost != null && lost.isFresh();

        // keepInventory bez swiezego snapshotu DeathKeep (dungeon, tryb kreatora, gamerule) oznacza,
        // ze gracz nie stracil nic. Bez tego powstawal backup z pelnym EQ, ktore gracz nadal ma,
        // a jego nadanie duplikowalo przedmioty.
        if (!fromDeathKeep && event.getKeepInventory()) {
            return;
        }

        ItemStack[] inv;
        ItemStack[] armor;
        if (fromDeathKeep) {
            inv = lost.inventory();
            armor = lost.armor();
        } else {
            inv = PlayerInventories.combineStorageAndExtra(
                player.getInventory().getStorageContents(),
                player.getInventory().getExtraContents()
            );
            armor = player.getInventory().getArmorContents().clone();
        }

        if (!BackupDataManager.hasAnyItems(inv) && !BackupDataManager.hasAnyItems(armor)) {
            return;
        }

        String deathCause = "Unknown";
        if (player.getLastDamageCause() != null) {
            deathCause = player.getLastDamageCause().getCause().name();
        }

        String killerName = "Brak";
        if (player.getKiller() != null) {
            killerName = player.getKiller().getName();
        } else if (player.getLastDamageCause() instanceof org.bukkit.event.entity.EntityDamageByEntityEvent edbe) {
            if (edbe.getDamager() instanceof Player damager) {
                killerName = damager.getName();
            } else if (edbe.getDamager() instanceof org.bukkit.entity.Projectile proj && proj.getShooter() instanceof Player shooter) {
                killerName = shooter.getName();
            }
        }

        String location = String.format("%s: %.1f, %.1f, %.1f",
            player.getWorld().getName(),
            player.getLocation().getX(),
            player.getLocation().getY(),
            player.getLocation().getZ()
        );

        final String playerName = player.getName();
        if (eloFuture == null) {
            EloManager eloManager = plugin.getEloManager();
            eloFuture = eloManager != null
                ? eloManager.getEloAsync(playerUuid)
                : CompletableFuture.completedFuture(1000);
        }

        final String finalDeathCause = deathCause;
        final String finalKillerName = killerName;
        final ItemStack[] finalInv = inv;
        final ItemStack[] finalArmor = armor;
        eloFuture.thenAccept(preDeathElo -> Bukkit.getScheduler().runTaskLater(plugin, () ->
            backupManager.onPlayerDeath(
                playerUuid,
                playerName,
                finalDeathCause,
                finalKillerName,
                location,
                preDeathElo,
                finalInv,
                finalArmor
            ), 1L));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        Inventory inventory = event.getInventory();

        if (inventory.getType() == org.bukkit.event.inventory.InventoryType.ENDER_CHEST ||
            inventory.getType() == org.bukkit.event.inventory.InventoryType.PLAYER ||
            inventory.getType() == org.bukkit.event.inventory.InventoryType.CRAFTING) {
            backupManager.captureAndUpdateSessionAsync(player);
        }
    }
}
