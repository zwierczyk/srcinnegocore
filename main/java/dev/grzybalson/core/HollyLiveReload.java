package dev.grzybalson.core;

import dev.grzybalson.HollyCore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Koordynuje hot-reload JAR — rozklada ciezki init na wiele tickow,
 * zeby gracze nie czuli minutowego freeza serwera.
 */
public final class HollyLiveReload {

    private static final AtomicBoolean ACTIVE = new AtomicBoolean(false);
    private static final int PLAYERS_PER_TICK = 4;

    private HollyLiveReload() {
    }

    public static boolean isActive() {
        return ACTIVE.get();
    }

    public static void begin() {
        ACTIVE.set(true);
    }

    public static void finish() {
        ACTIVE.set(false);
    }

    public static void schedulePostEnableWarmup(HollyCore plugin) {
        if (!ACTIVE.get()) {
            return;
        }

        List<UUID> online = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            online.add(player.getUniqueId());
        }

        if (plugin.getTablistManager() != null) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (plugin.getTablistManager() != null) {
                    plugin.getTablistManager().warmupAfterLiveReload();
                }
            }, 2L);
        }

        if (online.isEmpty()) {
            finish();
            plugin.getLogger().info("[LoadNew] Warmup zakonczony (brak graczy online).");
            return;
        }

        plugin.getLogger().info("[LoadNew] Rozkladam warmup danych graczy na ticki (" + online.size() + " online)...");

        Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            private int index;

            @Override
            public void run() {
                if (!plugin.isEnabled()) {
                    finish();
                    return;
                }

                int processed = 0;
                while (index < online.size() && processed < PLAYERS_PER_TICK) {
                    UUID uuid = online.get(index++);
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null && player.isOnline()) {
                        if (plugin.getAchievementManager() != null) {
                            plugin.getAchievementManager().loadPlayer(uuid);
                        }
                        if (plugin.getMissionManager() != null) {
                            plugin.getMissionManager().loadPlayer(uuid);
                        }
                        if (plugin.getEloManager() != null) {
                            plugin.getEloManager().loadElo(uuid);
                        }
                        if (plugin.getClanManager() != null) {
                            plugin.getClanManager().preloadPlayerClan(uuid);
                        }
                    }
                    processed++;
                }

                if (index >= online.size()) {
                    finish();
                    if (plugin.getTopManager() != null) {
                        plugin.getTopManager().requestRefresh();
                    }
                    plugin.getLogger().info("[LoadNew] Warmup zakonczony — gracze online odswiezeni.");
                }
            }
        }, 5L, 1L);
    }
}
