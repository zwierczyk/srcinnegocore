package dev.grzybalson.anarchiagg.adminitems;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import dev.grzybalson.HollyCore;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AdminItemsCooldowns {

    private static final long REFRESH_TICKS = 10L;
    private static final Map<UUID, Map<Material, Long>> VISUAL_COOLDOWNS = new ConcurrentHashMap<>();
    private static BukkitTask refreshTask;

    private AdminItemsCooldowns() {
    }

    public static void start(HollyCore plugin) {
        if (plugin == null || refreshTask != null && !refreshTask.isCancelled()) {
            return;
        }

        refreshTask = plugin.getServer().getScheduler().runTaskTimer(plugin, AdminItemsCooldowns::refresh, REFRESH_TICKS, REFRESH_TICKS);
    }

    public static void shutdown() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }

        VISUAL_COOLDOWNS.clear();
    }

    public static long secondsToMillis(int seconds) {
        return Math.max(0L, seconds) * 1000L;
    }

    public static long secondsToMillis(double seconds) {
        if (seconds <= 0.0D) {
            return 0L;
        }

        return Math.max(1L, (long) Math.ceil(seconds * 1000.0D));
    }

    public static long secondsToTicks(int seconds) {
        return millisToTicks(secondsToMillis(seconds));
    }

    public static int secondsToTicks(double seconds) {
        if (seconds <= 0.0D) {
            return 0;
        }

        return (int) Math.min(Math.max(1L, (long) Math.ceil(seconds * 20.0D)), Integer.MAX_VALUE);
    }

    public static int millisToBukkitTicks(long millis) {
        long ticks = millisToTicks(millis);
        return (int) Math.min(ticks, Integer.MAX_VALUE);
    }

    public static void apply(Player player, Material material, long cooldownMillis) {
        if (player == null || material == null) {
            return;
        }

        remember(player, material, cooldownMillis);
        player.setCooldown(material, millisToBukkitTicks(cooldownMillis));
    }

    public static void applySeconds(Player player, Material material, int seconds) {
        apply(player, material, secondsToMillis(seconds));
    }

    public static void applySeconds(Player player, Material material, double seconds) {
        apply(player, material, secondsToMillis(seconds));
    }

    public static void refresh(Player player, Material material, long cooldownUntilMillis) {
        if (player == null || material == null) {
            return;
        }

        long millisLeft = cooldownUntilMillis - System.currentTimeMillis();
        if (millisLeft <= 0L) {
            clear(player, material);
            player.setCooldown(material, 0);
            return;
        }

        VISUAL_COOLDOWNS
                .computeIfAbsent(player.getUniqueId(), ignored -> new ConcurrentHashMap<>())
                .put(material, cooldownUntilMillis);
        player.setCooldown(material, millisToBukkitTicks(millisLeft));
    }

    public static void clear(Player player) {
        if (player == null) {
            return;
        }

        VISUAL_COOLDOWNS.remove(player.getUniqueId());
    }

    public static void clear(Player player, Material material) {
        if (player == null || material == null) {
            return;
        }

        Map<Material, Long> cooldowns = VISUAL_COOLDOWNS.get(player.getUniqueId());
        if (cooldowns == null) {
            return;
        }

        cooldowns.remove(material);
        if (cooldowns.isEmpty()) {
            VISUAL_COOLDOWNS.remove(player.getUniqueId());
        }
    }

    private static void remember(Player player, Material material, long cooldownMillis) {
        if (cooldownMillis <= 0L) {
            clear(player, material);
            return;
        }

        VISUAL_COOLDOWNS
                .computeIfAbsent(player.getUniqueId(), ignored -> new ConcurrentHashMap<>())
                .put(material, System.currentTimeMillis() + cooldownMillis);
    }

    private static void refresh() {
        long now = System.currentTimeMillis();

        for (Map.Entry<UUID, Map<Material, Long>> playerEntry : VISUAL_COOLDOWNS.entrySet()) {
            Player player = Bukkit.getPlayer(playerEntry.getKey());
            if (player == null || !player.isOnline()) {
                VISUAL_COOLDOWNS.remove(playerEntry.getKey());
                continue;
            }

            Map<Material, Long> cooldowns = playerEntry.getValue();
            for (Map.Entry<Material, Long> cooldownEntry : cooldowns.entrySet()) {
                long millisLeft = cooldownEntry.getValue() - now;
                if (millisLeft <= 0L) {
                    cooldowns.remove(cooldownEntry.getKey());
                    player.setCooldown(cooldownEntry.getKey(), 0);
                }
            }

            if (cooldowns.isEmpty()) {
                VISUAL_COOLDOWNS.remove(playerEntry.getKey());
            }
        }
    }

    private static long millisToTicks(long millis) {
        if (millis <= 0L) {
            return 0L;
        }

        return Math.max(1L, (millis + 49L) / 50L);
    }
}
