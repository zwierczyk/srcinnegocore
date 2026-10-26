package dev.grzybalson.listeners;

import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Podczas /restartadmin: PvP off + gracze zamrożeni w miejscu (anty-dupe trade/kill/rejoin).
 */
public final class RestartFreezeListener implements Listener {

    private final HollyCore plugin;
    private final Map<UUID, Location> frozenAt = new ConcurrentHashMap<>();

    public RestartFreezeListener(HollyCore plugin) {
        this.plugin = plugin;
    }

    public void freezeAllOnline() {
        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            freezePlayer(player);
        }
    }

    public void unfreezeAll() {
        for (UUID uuid : Map.copyOf(frozenAt).keySet()) {
            Player player = org.bukkit.Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                unfreezePlayer(player);
            }
        }
        frozenAt.clear();
    }

    public void freezePlayer(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        Location at = player.getLocation().clone();
        frozenAt.put(player.getUniqueId(), at);
        player.setWalkSpeed(0.0F);
        player.setFlySpeed(0.0F);

        // Zatrzymaj gracza w powietrzu jeśli leci
        if (player.isFlying()) {
            player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
            player.setGravity(false); // Wyłącz grawitację
        } else {
            player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
        }

        if (player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        try {
            player.closeInventory();
        } catch (Throwable ignored) {
        }
        if (plugin.getShulkerBoxListener() != null
            && plugin.getShulkerBoxListener().isViewing(player.getUniqueId())) {
            plugin.getShulkerBoxListener().flush(player);
        }
    }

    public void unfreezePlayer(Player player) {
        if (player == null) {
            return;
        }
        frozenAt.remove(player.getUniqueId());
        if (!player.isOnline()) {
            return;
        }
        player.setWalkSpeed(0.2F);
        player.setFlySpeed(0.1F);
        player.setGravity(true); // Przywróć grawitację
    }

    private boolean active() {
        return plugin.getRestartManager() != null && plugin.getRestartManager().isRestarting();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        if (!active()) {
            return;
        }
        Player player = event.getPlayer();
        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
            if (active() && player.isOnline()) {
                freezePlayer(player);
                player.sendMessage(ColorUtils.colorizeComponent(
                    "&c✕ &8Ξ &cSerwer restartuje — jesteś zamrożony. Poczekaj na restart."
                ));
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        frozenAt.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!active()) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        if (from.getBlockX() == to.getBlockX()
            && from.getBlockY() == to.getBlockY()
            && from.getBlockZ() == to.getBlockZ()) {
            return; // pozwól na obrót głową
        }
        Location freeze = frozenAt.get(event.getPlayer().getUniqueId());
        if (freeze == null) {
            freezePlayer(event.getPlayer());
            freeze = frozenAt.get(event.getPlayer().getUniqueId());
        }
        if (freeze != null) {
            Location look = freeze.clone();
            look.setYaw(to.getYaw());
            look.setPitch(to.getPitch());
            event.setTo(look);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!active()) {
            return;
        }
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN
            || event.getCause() == PlayerTeleportEvent.TeleportCause.UNKNOWN) {
            // pozwól teleportom systemowym (np. kick prep) — ale odśwież freeze point
            Location to = event.getTo();
            if (to != null) {
                frozenAt.put(event.getPlayer().getUniqueId(), to.clone());
            }
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent event) {
        if (!active()) {
            return;
        }
        Player attacker = resolvePlayer(event.getDamager());
        Player victim = event.getEntity() instanceof Player p ? p : null;
        if (attacker != null || victim != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (!active()) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (!active()) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!active()) {
            return;
        }
        if (event.getEntity() instanceof Player) {
            event.setCancelled(true);
        }
    }

    private static Player resolvePlayer(org.bukkit.entity.Entity entity) {
        if (entity instanceof Player player) {
            return player;
        }
        if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }
}
