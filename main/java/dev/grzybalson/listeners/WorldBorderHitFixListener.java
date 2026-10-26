package dev.grzybalson.listeners;

import dev.grzybalson.HollyCore;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitTask;

/**
 * Minecraft nie wysyła pakietów melee na entity poza world borderem —
 * gracz „w borderze” jest praktisch nie do zbicia. Trzymamy graczy w środku
 * (global + per-player border) i zdejmujemy i-frame z dmg bordera.
 */
public final class WorldBorderHitFixListener implements Listener {

    private static final double INSIDE_MARGIN = 0.75D;

    private final HollyCore plugin;
    private BukkitTask task;

    public WorldBorderHitFixListener(HollyCore plugin) {
        this.plugin = plugin;
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 5L, 5L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            pullInsideIfNeeded(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        Location from = event.getFrom();
        if (from.getBlockX() == to.getBlockX()
            && from.getBlockZ() == to.getBlockZ()
            && from.getBlockY() == to.getBlockY()) {
            return;
        }
        pullInsideIfNeeded(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBorderDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (event.getCause() != EntityDamageEvent.DamageCause.WORLD_BORDER) {
            return;
        }
        // Border tick daje i-frame — bez tego nie da sie bic nawet jak juz wroci do srodka.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                player.setNoDamageTicks(0);
                pullInsideIfNeeded(player);
            }
        });
    }

    private void pullInsideIfNeeded(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        GameMode mode = player.getGameMode();
        if (mode == GameMode.SPECTATOR || mode == GameMode.CREATIVE) {
            return;
        }

        WorldBorder border = effectiveBorder(player);
        if (border == null) {
            return;
        }

        Location loc = player.getLocation();
        if (isSafelyInside(loc, border)) {
            return;
        }

        Location safe = clampInside(loc, border, INSIDE_MARGIN);
        if (safe == null || safe.getWorld() == null) {
            return;
        }
        player.teleport(safe);
        player.setNoDamageTicks(0);
        player.setVelocity(player.getVelocity().multiply(0));
    }

    private static WorldBorder effectiveBorder(Player player) {
        try {
            WorldBorder personal = player.getWorldBorder();
            if (personal != null) {
                return personal;
            }
        } catch (Throwable ignored) {
        }
        World world = player.getWorld();
        return world != null ? world.getWorldBorder() : null;
    }

    private static boolean isSafelyInside(Location location, WorldBorder border) {
        if (location == null || border == null) {
            return true;
        }
        try {
            if (!border.isInside(location)) {
                return false;
            }
        } catch (Throwable ignored) {
            // starsze API — fallback na dystans
        }
        return distanceInside(location, border) >= INSIDE_MARGIN;
    }

    private static double distanceInside(Location location, WorldBorder border) {
        Location center = border.getCenter();
        double half = border.getSize() * 0.5D;
        double dx = Math.abs(location.getX() - center.getX());
        double dz = Math.abs(location.getZ() - center.getZ());
        return half - Math.max(dx, dz);
    }

    private static Location clampInside(Location location, WorldBorder border, double margin) {
        World world = location.getWorld();
        if (world == null) {
            return null;
        }
        Location center = border.getCenter();
        double maxOffset = Math.max(0.0D, border.getSize() * 0.5D - Math.max(0.1D, margin));
        double x = clamp(location.getX(), center.getX() - maxOffset, center.getX() + maxOffset);
        double z = clamp(location.getZ(), center.getZ() - maxOffset, center.getZ() + maxOffset);
        return new Location(world, x, location.getY(), z, location.getYaw(), location.getPitch());
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
