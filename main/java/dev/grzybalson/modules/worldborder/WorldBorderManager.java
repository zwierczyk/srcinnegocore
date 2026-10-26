package dev.grzybalson.modules.worldborder;

import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ColorUtils;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WorldBorderManager implements Listener {

    private static final String CONFIG_RESOURCE = "addons/worldborder/worldborder.yml";
    private static final long MESSAGE_THROTTLE_MILLIS = 750L;

    private final HollyCore plugin;
    private final File configFile;
    private final Map<UUID, BossBar> warningBars = new ConcurrentHashMap<>();
    private final Map<UUID, Long> messageThrottle = new ConcurrentHashMap<>();
    private final Set<UUID> internalTeleports = ConcurrentHashMap.newKeySet();
    private final Set<UUID> warningTitleShown = ConcurrentHashMap.newKeySet();

    private BukkitTask task;
    private boolean enabled;
    private long checkIntervalTicks;
    private double warningDistance;
    private double protectionDistance;
    private double pushDistance;
    private double pushStrength;
    private double pushY;
    private boolean bossBarEnabled;
    private String bossBarTitle;
    private BossBar.Color bossBarColor;
    private BossBar.Overlay bossBarOverlay;
    private String blockedActionMessage;
    private String blockedTeleportMessage;
    private boolean warningTitleEnabled;
    private String warningTitle;
    private String warningSubtitle;
    private int warningFadeIn;
    private int warningStay;
    private int warningFadeOut;

    public WorldBorderManager(HollyCore plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), CONFIG_RESOURCE);
    }

    public void start() {
        loadConfig();
        if (!enabled) {
            hideAllBossBars();
            return;
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tickPlayers, checkIntervalTicks, checkIntervalTicks);
    }

    public void reload() {
        shutdown();
        start();
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        hideAllBossBars();
        messageThrottle.clear();
        internalTeleports.clear();
        warningTitleShown.clear();
    }

    private void loadConfig() {
        ensureConfigFile();
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        enabled = config.getBoolean("enabled", true);
        checkIntervalTicks = Math.max(1L, config.getLong("check-interval-ticks", 5L));
        warningDistance = Math.max(0.0D, config.getDouble("warning-distance", 10.0D));
        protectionDistance = Math.max(0.0D, config.getDouble("protection-distance", 10.0D));
        pushDistance = Math.max(0.0D, config.getDouble("push-distance", 1.5D));
        pushStrength = Math.max(0.0D, config.getDouble("push-strength", 0.85D));
        pushY = Math.max(0.0D, config.getDouble("push-y", 0.18D));
        bossBarEnabled = config.getBoolean("bossbar.enabled", true);
        bossBarTitle = config.getString("bossbar.title", "&#FF5C5C⚠ &fGranica mapy &8• &7pozostało &#FFDD57{distance}m");
        bossBarColor = parseColor(config.getString("bossbar.color", "RED"));
        bossBarOverlay = parseOverlay(config.getString("bossbar.overlay", "PROGRESS"));
        blockedActionMessage = config.getString("messages.blocked-action", "&#FF0000✘ &8Ξ &cNie możesz tego robić przy granicy mapy.");
        blockedTeleportMessage = config.getString("messages.blocked-teleport", "&#FF0000✘ &8Ξ &cNie możesz teleportować się w pobliże granicy mapy.");
        warningTitleEnabled = config.getBoolean("title.enabled", true);
        warningTitle = config.getString("title.title", "&#FF5C5C&lUWAGA!");
        warningSubtitle = config.getString("title.subtitle", "&fZbliżasz się do granicy mapy &8• &#FFDD57{distance}m");
        warningFadeIn = Math.max(0, config.getInt("title.fade-in-ticks", 5));
        warningStay = Math.max(1, config.getInt("title.stay-ticks", 35));
        warningFadeOut = Math.max(0, config.getInt("title.fade-out-ticks", 10));
    }

    private void ensureConfigFile() {
        if (configFile.exists()) {
            return;
        }
        plugin.saveResource(CONFIG_RESOURCE, false);
    }

    private BossBar.Color parseColor(String raw) {
        try {
            return BossBar.Color.valueOf((raw == null ? "RED" : raw).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return BossBar.Color.RED;
        }
    }

    private BossBar.Overlay parseOverlay(String raw) {
        try {
            return BossBar.Overlay.valueOf((raw == null ? "PROGRESS" : raw).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return BossBar.Overlay.PROGRESS;
        }
    }

    private void tickPlayers() {
        if (!enabled) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            Location location = player.getLocation();
            double distance = distanceToBorder(location);
            if (Double.isNaN(distance)) {
                hideBossBar(player);
                continue;
            }

            if (distance <= warningDistance) {
                if (bossBarEnabled) {
                    showOrUpdateBossBar(player, distance);
                }
                showWarningTitle(player, distance);
            } else {
                hideBossBar(player);
                if (distance > warningDistance + 2.0D) {
                    warningTitleShown.remove(player.getUniqueId());
                }
            }

            if (distance <= pushDistance) {
                pushFromBorder(player, distance);
            }
        }
    }

    private void showWarningTitle(Player player, double distance) {
        if (!warningTitleEnabled || !warningTitleShown.add(player.getUniqueId())) {
            return;
        }
        String formattedDistance = formatDistance(Math.max(0.0D, distance));
        player.sendTitle(
            ColorUtils.colorize(warningTitle.replace("{distance}", formattedDistance)),
            ColorUtils.colorize(warningSubtitle.replace("{distance}", formattedDistance)),
            warningFadeIn,
            warningStay,
            warningFadeOut
        );
    }

    private void showOrUpdateBossBar(Player player, double distance) {
        BossBar bar = warningBars.computeIfAbsent(player.getUniqueId(), uuid -> {
            BossBar created = BossBar.bossBar(ColorUtils.colorizeComponent(""), 1.0f, bossBarColor, bossBarOverlay);
            player.showBossBar(created);
            return created;
        });

        String title = bossBarTitle.replace("{distance}", formatDistance(Math.max(0.0D, distance)));
        bar.name(ColorUtils.colorizeComponent(title));
        if (warningDistance <= 0.0D) {
            bar.progress(1.0f);
        } else {
            bar.progress((float) Math.max(0.0D, Math.min(1.0D, 1.0D - (Math.max(0.0D, distance) / warningDistance))));
        }
    }

    private void hideBossBar(Player player) {
        BossBar bar = warningBars.remove(player.getUniqueId());
        if (bar != null) {
            player.hideBossBar(bar);
        }
    }

    private void hideAllBossBars() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            hideBossBar(player);
        }
        warningBars.clear();
    }

    private String formatDistance(double distance) {
        if (distance >= 10.0D) {
            return String.valueOf((int) Math.floor(distance));
        }
        return String.format(Locale.US, "%.1f", distance);
    }

    private void pushFromBorder(Player player, double distance) {
        Location location = player.getLocation();
        if (distance <= 0.05D) {
            teleportInside(player, clampInsideBorder(location, Math.max(pushDistance + 1.0D, 2.0D)));
        }

        Vector direction = directionToCenter(location);
        if (direction.lengthSquared() <= 0.0001D) {
            return;
        }
        Vector velocity = direction.normalize().multiply(pushStrength);
        velocity.setY(Math.max(pushY, velocity.getY() + pushY));
        player.setVelocity(velocity);
    }

    private void teleportInside(Player player, Location safeLocation) {
        internalTeleports.add(player.getUniqueId());
        try {
            player.teleport(safeLocation);
        } finally {
            internalTeleports.remove(player.getUniqueId());
        }
    }

    private Location clampInsideBorder(Location location, double minDistanceInside) {
        World world = location.getWorld();
        if (world == null) {
            return location;
        }

        WorldBorder border = world.getWorldBorder();
        Location center = border.getCenter();
        double maxOffset = Math.max(0.0D, border.getSize() * 0.5D - minDistanceInside);
        double x = clamp(location.getX(), center.getX() - maxOffset, center.getX() + maxOffset);
        double z = clamp(location.getZ(), center.getZ() - maxOffset, center.getZ() + maxOffset);
        return new Location(world, x, location.getY(), z, location.getYaw(), location.getPitch());
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private Vector directionToCenter(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return new Vector();
        }
        Location center = world.getWorldBorder().getCenter();
        return new Vector(center.getX() - location.getX(), 0.0D, center.getZ() - location.getZ());
    }

    private boolean isProtected(Location location) {
        double distance = distanceToBorder(location);
        return !Double.isNaN(distance) && distance <= protectionDistance;
    }

    private double distanceToBorder(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return Double.NaN;
        }

        WorldBorder border = world.getWorldBorder();
        Location center = border.getCenter();
        double halfSize = border.getSize() * 0.5D;
        double dx = Math.abs(location.getX() - center.getX());
        double dz = Math.abs(location.getZ() - center.getZ());
        return halfSize - Math.max(dx, dz);
    }

    private void sendThrottled(Player player, String message) {
        long now = System.currentTimeMillis();
        Long previous = messageThrottle.put(player.getUniqueId(), now);
        if (previous == null || now - previous >= MESSAGE_THROTTLE_MILLIS) {
            player.sendMessage(ColorUtils.colorizeComponent(message));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!enabled || !isProtected(event.getBlockPlaced().getLocation())) {
            return;
        }
        event.setCancelled(true);
        sendThrottled(event.getPlayer(), blockedActionMessage);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!enabled || !isProtected(event.getBlock().getLocation())) {
            return;
        }
        event.setCancelled(true);
        sendThrottled(event.getPlayer(), blockedActionMessage);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPearlUse(PlayerInteractEvent event) {
        if (!enabled) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.ENDER_PEARL || !isProtected(event.getPlayer().getLocation())) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer().updateInventory();
        sendThrottled(event.getPlayer(), blockedActionMessage);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!enabled || event.getTo() == null || internalTeleports.contains(event.getPlayer().getUniqueId())) {
            return;
        }
        if (!isProtected(event.getTo())) {
            return;
        }
        event.setCancelled(true);
        sendThrottled(event.getPlayer(), blockedTeleportMessage);
        Bukkit.getScheduler().runTask(plugin, () -> pushFromBorder(event.getPlayer(), distanceToBorder(event.getPlayer().getLocation())));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        hideBossBar(event.getPlayer());
        messageThrottle.remove(event.getPlayer().getUniqueId());
        internalTeleports.remove(event.getPlayer().getUniqueId());
        warningTitleShown.remove(event.getPlayer().getUniqueId());
    }
}
