package dev.grzybalson.listeners;

import dev.grzybalson.HollyCore;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class CreatorModeListener implements Listener {

    private final HollyCore plugin;
    private final Set<UUID> pendingCreatorModeDeaths = new HashSet<>();

    public CreatorModeListener(HollyCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDeathLowest(PlayerDeathEvent event) {
        if (shouldPreventDrops(event)) {
            pendingCreatorModeDeaths.add(event.getEntity().getUniqueId());
            event.setKeepInventory(true);
            event.getDrops().clear();
            event.setKeepLevel(true);
            event.setDroppedExp(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeathHighest(PlayerDeathEvent event) {
        if (shouldPreventDrops(event)) {
            event.setKeepInventory(true);
            event.getDrops().clear();
            event.setKeepLevel(true);
            event.setDroppedExp(0);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onPlayerDeathMonitor(PlayerDeathEvent event) {
        if (shouldPreventDrops(event)) {
            event.setKeepInventory(true);
            event.getDrops().clear();
            event.setKeepLevel(true);
            event.setDroppedExp(0);
        }
        pendingCreatorModeDeaths.remove(event.getEntity().getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onItemSpawn(ItemSpawnEvent event) {
        Item item = event.getEntity();

        for (Player player : item.getLocation().getNearbyPlayers(2)) {
            if (pendingCreatorModeDeaths.contains(player.getUniqueId())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private boolean shouldPreventDrops(PlayerDeathEvent event) {
        if (plugin.getCreatorModeManager() == null) return false;
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        return pendingCreatorModeDeaths.contains(victim.getUniqueId())
            || plugin.getCreatorModeManager().shouldPreventDrops(victim, killer);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (plugin.getCreatorModeManager() == null) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        Player attacker = null;
        if (event.getDamager() instanceof Player) {
            attacker = (Player) event.getDamager();
        } else if (event.getDamager() instanceof org.bukkit.entity.Projectile projectile) {
            if (projectile.getShooter() instanceof Player) {
                attacker = (Player) projectile.getShooter();
            }
        }

        if (attacker == null || attacker.equals(victim)) return;
        double damage = event.getFinalDamage();
        // A real hit absorbed by golden hearts still counts; blocked/cancelled hits do not.
        if (event.isApplicable(org.bukkit.event.entity.EntityDamageEvent.DamageModifier.ABSORPTION)) {
            damage -= event.getDamage(org.bukkit.event.entity.EntityDamageEvent.DamageModifier.ABSORPTION);
        }
        if (damage <= 0) return;

        boolean attackerInCreatorMode = plugin.getCreatorModeManager().isInCreatorMode(attacker);
        boolean victimInCreatorMode = plugin.getCreatorModeManager().isInCreatorMode(victim);

        if (attackerInCreatorMode) plugin.getCreatorModeManager().protectAfterCreatorHit(victim);

        if (attackerInCreatorMode || victimInCreatorMode) {
            event.getEntity().setMetadata("creator_mode_kill",
                new org.bukkit.metadata.FixedMetadataValue(plugin, true));
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (plugin.getCreatorModeManager() != null) {
            plugin.getCreatorModeManager().handlePlayerJoin(event.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (plugin.getCreatorModeManager() != null) {
            plugin.getCreatorModeManager().handlePlayerQuit(event.getPlayer());
        }
    }
}
