package dev.grzybalson.managers;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.configs.ClearLagConfig;
import dev.grzybalson.utils.ColorUtils;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ClearLagManager {

    private static final long AUTOMESSAGE_PAUSE_AFTER_BROADCAST_MILLIS = 1500L;

    private final HollyCore plugin;
    private final boolean papiEnabled;
    private BukkitTask mainTask;
    private int secondsUntilClear;
    private volatile long autoMessagePausedUntilMillis;
    private ClearLagSettings settings = ClearLagSettings.disabled();

    public ClearLagManager(HollyCore plugin) {
        this.plugin = plugin;
        this.papiEnabled = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
    }

    public void start() {
        stop();
        settings = ClearLagSettings.from(plugin.getConfigManager().getClearLagConfig());

        if (!settings.enabled()) {
            secondsUntilClear = 0;
            return;
        }

        secondsUntilClear = settings.interval();

        mainTask = new BukkitRunnable() {
            @Override
            public void run() {
                secondsUntilClear--;

                int countdownStart = settings.countdownStart();
                if (secondsUntilClear <= countdownStart && secondsUntilClear > 0) {
                    String message = settings.countdownMessage();
                    if (message != null && !message.isBlank()) {
                        message = message
                            .replace("{seconds}", String.valueOf(secondsUntilClear))
                            .replace("{time}", String.valueOf(secondsUntilClear))
                            .replace("{TIME}", String.valueOf(secondsUntilClear));
                        broadcastMessage(message);
                    }
                }

                if (secondsUntilClear <= 0) {
                    clearItems();
                    String clearMsg = settings.clearMessage();
                    if (clearMsg != null && !clearMsg.isBlank()) {
                        broadcastMessage(applyCountPlaceholders(clearMsg, lastClearedCount));
                    }
                    secondsUntilClear = settings.interval();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);

        plugin.getLogger().info("ClearLag started with interval: " + settings.interval() + "s");
    }

    public void stop() {
        if (mainTask != null) {
            mainTask.cancel();
            mainTask = null;
        }
    }

    public void reload() {
        plugin.getConfigManager().reloadClearLagConfig();
        start();
    }

    private int lastClearedCount;

    public int clearItems() {
        ClearLagSettings current = settings;

        int cleared = 0;
        double survivalRadius = current.playerProtectionRadius();
        double creativeRadius = current.creativeProtectionRadius();
        boolean protectPlayers = survivalRadius > 0.0D || creativeRadius > 0.0D;

        // Cache lokalizacji graczy raz na clear — O(items + players) zamiast O(items × players).
        List<PlayerAnchor> anchors = protectPlayers ? buildPlayerAnchors(current) : List.of();

        for (World world : Bukkit.getWorlds()) {
            if (!current.worlds().isEmpty() && !current.worlds().contains(world.getName().toLowerCase(Locale.ROOT))) {
                continue;
            }

            for (Item item : world.getEntitiesByClass(Item.class)) {
                if (shouldClearItem(item, current, anchors, protectPlayers)) {
                    item.remove();
                    cleared++;
                }
            }
        }

        this.lastClearedCount = cleared;
        return cleared;
    }

    private List<PlayerAnchor> buildPlayerAnchors(ClearLagSettings current) {
        List<PlayerAnchor> anchors = new ArrayList<>(Bukkit.getOnlinePlayers().size());
        for (Player player : Bukkit.getOnlinePlayers()) {
            Location loc = player.getLocation();
            if (loc.getWorld() == null) {
                continue;
            }
            GameMode mode = player.getGameMode();
            double radius = (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR)
                ? current.creativeProtectionRadius()
                : current.playerProtectionRadius();
            if (radius <= 0.0D) {
                continue;
            }
            anchors.add(new PlayerAnchor(loc.getWorld().getUID(), loc.getX(), loc.getY(), loc.getZ(), radius * radius));
        }
        return anchors;
    }

    private boolean shouldClearItem(Item item, ClearLagSettings current, List<PlayerAnchor> anchors, boolean protectPlayers) {
        if (item == null || item.isDead() || item.getItemStack().getType().isAir()) {
            return false;
        }

        if (current.excludedMaterials().contains(item.getItemStack().getType())) {
            return false;
        }

        if (item.getPickupDelay() > 0) {
            return false;
        }

        if (item.getTicksLived() < current.minItemAgeTicks()) {
            return false;
        }

        Location location = item.getLocation();
        if (location.getWorld() == null) {
            return false;
        }

        if (!protectPlayers || anchors.isEmpty()) {
            return true;
        }

        java.util.UUID worldId = location.getWorld().getUID();
        double ix = location.getX();
        double iy = location.getY();
        double iz = location.getZ();
        for (PlayerAnchor anchor : anchors) {
            if (!anchor.worldId.equals(worldId)) {
                continue;
            }
            double dx = anchor.x - ix;
            double dy = anchor.y - iy;
            double dz = anchor.z - iz;
            if ((dx * dx + dy * dy + dz * dz) <= anchor.radiusSquared) {
                return false;
            }
        }

        return true;
    }

    private record PlayerAnchor(java.util.UUID worldId, double x, double y, double z, double radiusSquared) {}

    public int forceClear() {
        return clearItems();
    }

    private String applyCountPlaceholders(String text, int count) {
        if (text == null) {
            return "";
        }

        String strCount = String.valueOf(count);
        return text.replace("{count}", strCount)
            .replace("{COUNT}", strCount)
            .replace("{counts}", strCount)
            .replace("{COUNTS}", strCount)
            .replace("{amount}", strCount)
            .replace("{AMOUNT}", strCount)
            .replace("{cleared}", strCount)
            .replace("{CLEARED}", strCount);
    }

    private void broadcastMessage(String message) {
        if (message == null || message.isBlank()) {
            return;
        }

        boolean needsPapi = papiEnabled && message.indexOf('%') >= 0;
        if (!needsPapi) {
            var component = ColorUtils.colorizeComponent(message);
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendMessage(component);
            }
        } else {
            for (Player player : Bukkit.getOnlinePlayers()) {
                String formatted = PlaceholderAPI.setPlaceholders(player, message);
                player.sendMessage(ColorUtils.colorizeComponent(formatted));
            }
        }

        markAutoMessagePaused();
    }

    private void markAutoMessagePaused() {
        autoMessagePausedUntilMillis = Math.max(
            autoMessagePausedUntilMillis,
            System.currentTimeMillis() + AUTOMESSAGE_PAUSE_AFTER_BROADCAST_MILLIS
        );
    }

    public boolean isAutoMessagePaused() {
        return System.currentTimeMillis() < autoMessagePausedUntilMillis;
    }

    public int getSecondsUntilClear() {
        return secondsUntilClear;
    }

    private record ClearLagSettings(
        boolean enabled,
        int interval,
        int countdownStart,
        String countdownMessage,
        String clearMessage,
        Set<String> worlds,
        Set<String> excludedItems,
        Set<org.bukkit.Material> excludedMaterials,
        double playerProtectionRadius,
        double creativeProtectionRadius,
        int minItemAgeTicks
    ) {
        private static ClearLagSettings from(ClearLagConfig config) {
            Set<String> worlds = new HashSet<>();
            for (String world : config.getWorlds()) {
                if (world != null && !world.isBlank()) {
                    worlds.add(world.trim().toLowerCase(Locale.ROOT));
                }
            }

            Set<String> excludedItems = new HashSet<>();
            Set<org.bukkit.Material> excludedMaterials = new HashSet<>();
            for (String item : config.getExcludedItems()) {
                if (item != null && !item.isBlank()) {
                    String upper = item.trim().toUpperCase(Locale.ROOT);
                    excludedItems.add(upper);
                    org.bukkit.Material mat = org.bukkit.Material.getMaterial(upper);
                    if (mat != null) {
                        excludedMaterials.add(mat);
                    }
                }
            }

            return new ClearLagSettings(
                config.isEnabled(),
                Math.max(1, config.getInterval()),
                Math.max(0, config.getCountdownStart()),
                config.getCountdownMessage(),
                config.getClearMessage(),
                Set.copyOf(worlds),
                Set.copyOf(excludedItems),
                Set.copyOf(excludedMaterials),
                Math.max(0.0D, config.getPlayerProtectionRadius()),
                Math.max(0.0D, config.getCreativeProtectionRadius()),
                Math.max(0, config.getMinItemAgeTicks())
            );
        }

        private static ClearLagSettings disabled() {
            return new ClearLagSettings(false, 300, 5, "", "", Set.of(), Set.of(), Set.of(), 5.0D, 16.0D, 100);
        }
    }
}
