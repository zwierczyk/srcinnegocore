package dev.grzybalson.listeners;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.configs.SpawnConfig;
import dev.grzybalson.config.modules.messages.JoinQuitConfig;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.SpooferBridge;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class JoinQuitListener implements Listener {

    private final HollyCore plugin;

    public JoinQuitListener(HollyCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        JoinQuitConfig config = plugin.getModuleConfigManager().getJoinQuitConfig();
        Player player = event.getPlayer();

        // Przywróć fly status z permisji
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (player.hasPermission("Holly.fly.keep") || player.hasPermission("Holly.cmd.fly")) {
                boolean hadFly = dev.grzybalson.commands.FlyCommand.getFlyState(player.getUniqueId());
                if (hadFly) {
                    player.setAllowFlight(true);
                    if (player.getFlySpeed() <= 0.001F) {
                        player.setFlySpeed(0.1F);
                    }
                }
            }
        }, 5L);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (plugin.getBossBarManager() != null) {
                plugin.getBossBarManager().addPlayer(player);
            }
        }, 10L);

        if (plugin.getCodeManager() != null) {
            plugin.getCodeManager().handlePlayerJoin(player);
        }

        if (plugin.getStatsDataManager() != null) {
            plugin.getStatsDataManager().startSession(player.getUniqueId());
        }

        if (plugin.getTopManager() != null) {
            plugin.getTopManager().rememberPlayerName(player.getUniqueId(), player.getName());
        }

        // Delay non-critical async-safe operations to reduce join tick spike
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            if (plugin.isWalletAddonEnabled()) {
                var walletManager = plugin.getWalletManager();
                if (walletManager != null) {
                    walletManager.loadPlayer(player.getUniqueId());
                    walletManager.touchPlayer(player.getUniqueId(), player.getName());
                }
            }
            if (plugin.getTopManager() != null) {
                plugin.getTopManager().invalidatePlayer(player.getUniqueId());
            }
        }, 20L);

        SpawnConfig spawnConfig = plugin.getConfigManager().getSpawnConfig();
        boolean isFirstJoin = !player.hasPlayedBefore();
        boolean forceSpawnWorld = spawnConfig.isForceSpawnWorld(player.getWorld() != null ? player.getWorld().getName() : null);
        boolean shouldTeleport = spawnConfig.isSpawnSet() && (
            (isFirstJoin && spawnConfig.isSpawnOnFirstJoin())
                || (spawnConfig.isSpawnOnJoin() && forceSpawnWorld)
        );

        if (shouldTeleport) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                Location spawnLocation = getSpawnLocation(spawnConfig);
                if (spawnLocation != null && spawnLocation.getWorld() != null) {
                    player.teleport(spawnLocation);
                    player.setFallDistance(0.0f);
                }
            });
        }

        if (plugin.getKitManager() != null) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                if (player.getGameMode() == org.bukkit.GameMode.CREATIVE
                        || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                    return;
                }
                plugin.getKitManager().tryGiveFirstJoinKit(player);
            }, 25L);
        }

        if (config.isDisableAllMessages()) {
            event.joinMessage(null);
            return;
        }

        if (!config.isEnabled()) {
            return;
        }

        if (SpooferBridge.isFakeBot(player.getName())) {
            event.joinMessage(null);
            return;
        }

        if (config.isHideDefault()) {
            event.joinMessage(null);
        }

        if (config.isBroadcastJoinQuit()) {
            if (config.isFirstJoinEnabled() && !player.hasPlayedBefore()) {
                String firstJoinMessage = config.getFirstJoinMessage();
                if (firstJoinMessage != null && !firstJoinMessage.isEmpty()) {
                    String message = firstJoinMessage.replace("{player}", player.getName());
                    Bukkit.broadcast(ColorUtils.colorizeComponent(message));
                }
            } else {
                String joinMessage = config.getJoinMessage();
                if (joinMessage != null && !joinMessage.isEmpty()) {
                    String message = joinMessage.replace("{player}", player.getName());
                    Bukkit.broadcast(ColorUtils.colorizeComponent(message));
                }
            }
        }

        String joinTitle = config.getJoinTitle();
        String joinSubtitle = config.getJoinSubtitle();
        if (joinTitle != null && !joinTitle.isEmpty()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                ColorUtils.sendTitle(player,
                    ColorUtils.colorize(joinTitle.replace("{player}", player.getName())),
                    ColorUtils.colorize(joinSubtitle != null ? joinSubtitle.replace("{player}", player.getName()) : ""),
                    config.getTitleFadeIn(),
                    config.getTitleStay(),
                    config.getTitleFadeOut()
                );
            }, 15L);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        JoinQuitConfig config = plugin.getModuleConfigManager().getJoinQuitConfig();
        Player player = event.getPlayer();

        // Zapisz stan fly przed wyjściem
        dev.grzybalson.commands.FlyCommand.setFlyState(player.getUniqueId(), player.getAllowFlight());

        if (plugin.getBossBarManager() != null) {
            plugin.getBossBarManager().removePlayer(player);
        }

        if (plugin.getCodeManager() != null) {
            plugin.getCodeManager().handlePlayerQuit(player);
        }

        if (plugin.getStatsDataManager() != null) {
            plugin.getStatsDataManager().endSession(player.getUniqueId());
        }
        if (plugin.isWalletAddonEnabled()) {
            var walletManager = plugin.getWalletManager();
            if (walletManager != null) {
                walletManager.invalidateCache(player.getUniqueId());
            }
        }
        if (plugin.getTopManager() != null) {
            plugin.getTopManager().invalidatePlayer(player.getUniqueId());
        }

        if (config.isDisableAllMessages()) {
            event.quitMessage(null);
            return;
        }

        if (!config.isEnabled()) {
            return;
        }

        if (SpooferBridge.isFakeBot(player.getName())) {
            event.quitMessage(null);
            return;
        }

        if (config.isHideDefault()) {
            event.quitMessage(null);
        }

        if (config.isBroadcastJoinQuit()) {
            String quitMessage = config.getQuitMessage();
            if (quitMessage != null && !quitMessage.isEmpty()) {
                String message = quitMessage.replace("{player}", player.getName());
                Bukkit.broadcast(ColorUtils.colorizeComponent(message));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        SpawnConfig spawnConfig = plugin.getConfigManager().getSpawnConfig();
        if (spawnConfig.isSpawnOnRespawn() && spawnConfig.isSpawnSet()) {
            Location spawnLocation = getSpawnLocation(spawnConfig);
            if (spawnLocation != null) {
                event.setRespawnLocation(spawnLocation);
            }
        }
    }

    private Location getSpawnLocation(SpawnConfig config) {
        World world = Bukkit.getWorld(config.getWorld());
        if (world == null) return null;
        return new Location(world, config.getX(), config.getY(), config.getZ(), config.getYaw(), config.getPitch());
    }
}
