package dev.grzybalson.managers;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.configs.MessagesConfig;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class IdleManager implements Listener {

    private final HollyCore plugin;
    private final Map<UUID, Long> lastMovement = new ConcurrentHashMap<>();
    private BukkitTask checkTask;

    public IdleManager(HollyCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);

        checkTask = new BukkitRunnable() {
            @Override
            public void run() {
                MessagesConfig config = plugin.getConfigManager().getMessagesConfig();
                if (!config.isAfkTeleportEnabled()) return;

                long now = System.currentTimeMillis();
                int afkTimeMs = config.getAfkTimeSeconds() * 1000;

                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.isOp() || player.hasPermission("Holly.afk.bypass")) {
                        continue;
                    }

                    UUID uuid = player.getUniqueId();
                    Long lastMove = lastMovement.get(uuid);

                    if (lastMove == null) {
                        lastMovement.put(uuid, now);
                        continue;
                    }

                    if (now - lastMove >= afkTimeMs) {
                        teleportToAfkWarp(player);
                        lastMovement.put(uuid, now);
                    }
                }
            }
        }.runTaskTimer(plugin, 100L, 100L);
    }

    public void stop() {
        if (checkTask != null) {
            checkTask.cancel();
        }
        lastMovement.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;

        if (event.getFrom().getBlockX() != event.getTo().getBlockX() ||
            event.getFrom().getBlockY() != event.getTo().getBlockY() ||
            event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {

            lastMovement.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastMovement.remove(event.getPlayer().getUniqueId());
    }

    private void teleportToAfkWarp(Player player) {
        MessagesConfig config = plugin.getConfigManager().getMessagesConfig();
        String warpName = config.getAfkWarpName();

        if (plugin.getWarpManager() == null) return;

        plugin.getWarpManager().teleportToWarpSilent(player, warpName);
        player.sendMessage(ColorUtils.colorizeComponent(config.getAfkTeleportMessage()));
    }

    public void resetIdleTime(Player player) {
        lastMovement.put(player.getUniqueId(), System.currentTimeMillis());
    }
}
