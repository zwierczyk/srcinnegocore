package dev.grzybalson.modules.warp;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.modules.warps.WarpDataConfig;
import dev.grzybalson.config.modules.warps.WarpsConfig;
import dev.grzybalson.modules.teleport.TeleportAnimator;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class WarpManager {

    private final HollyCore plugin;

    public WarpManager(HollyCore plugin) {
        this.plugin = plugin;
    }

    public boolean createWarp(String name, Location location, String permission) {
        WarpDataConfig dataConfig = plugin.getModuleConfigManager().getWarpDataConfig();
        WarpsConfig config = plugin.getModuleConfigManager().getWarpsConfig();

        if (dataConfig.hasWarp(name)) {
            return false;
        }

        int slot = findNextAvailableSlot();

        WarpDataConfig.WarpEntry entry = new WarpDataConfig.WarpEntry(
            location.getWorld().getName(),
            location.getX(),
            location.getY(),
            location.getZ(),
            location.getYaw(),
            location.getPitch(),
            permission,
            config.getDefaultItemMaterial(),
            "&e&l" + name.toUpperCase(),
            config.getDefaultLore(),
            slot
        );

        dataConfig.addWarp(name, entry);
        return true;
    }

    public boolean updateWarpLocation(String name, Location location) {
        if (name == null || location == null || location.getWorld() == null) {
            return false;
        }
        WarpDataConfig dataConfig = plugin.getModuleConfigManager().getWarpDataConfig();
        WarpDataConfig.WarpEntry entry = dataConfig.getWarp(name);
        if (entry == null) {
            return false;
        }
        entry.setWorld(location.getWorld().getName());
        entry.setX(location.getX());
        entry.setY(location.getY());
        entry.setZ(location.getZ());
        entry.setYaw(location.getYaw());
        entry.setPitch(location.getPitch());
        dataConfig.save();
        return true;
    }

    public boolean deleteWarp(String name) {
        WarpDataConfig dataConfig = plugin.getModuleConfigManager().getWarpDataConfig();

        if (!dataConfig.hasWarp(name)) {
            return false;
        }

        dataConfig.removeWarp(name);
        return true;
    }

    public void teleportToWarp(Player player, String warpName) {
        WarpDataConfig dataConfig = plugin.getModuleConfigManager().getWarpDataConfig();
        WarpsConfig config = plugin.getModuleConfigManager().getWarpsConfig();
        TeleportAnimator animator = plugin.getTeleportAnimator();

        WarpDataConfig.WarpEntry warp = dataConfig.getWarp(warpName);
        if (warp == null) {
            player.sendMessage(ColorUtils.colorizeComponent(config.getWarpNotFoundMessage()));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        if (warp.getPermission() != null && !warp.getPermission().isEmpty()
            && !player.hasPermission(warp.getPermission())) {
            player.sendMessage(ColorUtils.noPermission("/warp " + warpName, warp.getPermission()));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        Location location = resolveWarpLocation(warp);
        if (location == null) {
            player.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cŚwiat warpa nie istnieje!"));
            return;
        }

        TeleportAnimator.Options options = warpOptions(config, warpName);
        int delay = config.getTeleportDelay();
        options.delaySeconds = delay;
        options.cancelOnMove = config.isCancelOnMove();
        options.cancelOnDamage = config.isCancelOnDamage();

        if (delay <= 0 || player.hasPermission("Holly.warp.bypass.delay")) {
            animator.completeInstant(player, location, options);
            return;
        }

        animator.start(player, location, options);
    }

    public void teleportToWarpSilent(Player player, String warpName) {
        WarpDataConfig dataConfig = plugin.getModuleConfigManager().getWarpDataConfig();

        WarpDataConfig.WarpEntry warp = dataConfig.getWarp(warpName);
        if (warp == null) {
            return;
        }

        Location location = resolveWarpLocation(warp);
        if (location == null) {
            return;
        }

        player.teleport(location);
    }

    public void cancelPendingTeleport(Player player) {
        plugin.getTeleportAnimator().cancel(player.getUniqueId(), false);
    }

    public void handleDamage(Player player) {
    }

    public Collection<String> getWarpNames() {
        return plugin.getModuleConfigManager().getWarpDataConfig().getWarps().keySet();
    }

    public WarpDataConfig.WarpEntry getWarp(String name) {
        return plugin.getModuleConfigManager().getWarpDataConfig().getWarp(name);
    }

    public Map<String, WarpDataConfig.WarpEntry> getAllWarps() {
        return plugin.getModuleConfigManager().getWarpDataConfig().getWarps();
    }

    public void shutdown() {
    }

    private TeleportAnimator.Options warpOptions(WarpsConfig config, String warpName) {
        TeleportAnimator.Options options = new TeleportAnimator.Options();
        options.countdownSubtitle = config.getTeleportingSubtitle();
        options.cancelledMessage = config.getTeleportCancelledMessage();
        options.successTitle = config.getTeleportSuccessTitle();
        options.successSubtitle = config.getTeleportSuccessSubtitle().replace("{warp}", warpName);
        options.successChat = config.getTeleportSuccessMessage().replace("{warp}", warpName);
        return options;
    }

    private Location resolveWarpLocation(WarpDataConfig.WarpEntry warp) {
        World world = Bukkit.getWorld(warp.getWorld());
        if (world == null) {
            return null;
        }
        return new Location(world, warp.getX(), warp.getY(), warp.getZ(), warp.getYaw(), warp.getPitch());
    }

    private int findNextAvailableSlot() {
        Set<Integer> usedSlots = new HashSet<>();
        for (WarpDataConfig.WarpEntry warp : getAllWarps().values()) {
            usedSlots.add(warp.getSlot());
        }

        for (int i = 10; i < 35; i++) {
            if (i % 9 == 0 || i % 9 == 8) continue;
            if (!usedSlots.contains(i)) {
                return i;
            }
        }
        return 10;
    }
}
