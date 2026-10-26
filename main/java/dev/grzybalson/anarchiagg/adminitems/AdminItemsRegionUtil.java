package dev.grzybalson.anarchiagg.adminitems;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ColorUtils;

import java.util.Collection;
import java.util.Locale;

public final class AdminItemsRegionUtil {

    private static volatile RegionContainer cachedContainer;
    private static volatile boolean missingWorldGuardLogged;

    private AdminItemsRegionUtil() {
    }

    public static boolean isInBlockedRegion(Player player, HollyCore plugin) {
        return player != null && isInBlockedRegion(player.getLocation(), plugin);
    }

    public static boolean isInBlockedRegion(Location location, HollyCore plugin) {
        if (plugin == null || plugin.getAdminItemsConfig() == null || location == null || location.getWorld() == null) {
            return false;
        }

        Collection<String> blockedRegions = plugin.getAdminItemsConfig().getBlockedRegions();
        if (blockedRegions == null || blockedRegions.isEmpty()) {
            return false;
        }

        try {
            RegionManager manager = getRegionManager(location.getWorld());
            if (manager == null) {
                return false;
            }

            BlockVector3 point = BlockVector3.at(location.getBlockX(), location.getBlockY(), location.getBlockZ());
            for (String blockedRegion : blockedRegions) {
                ProtectedRegion region = getRegion(manager, blockedRegion);
                if (region != null && region.contains(point)) {
                    return true;
                }
            }
            return false;
        } catch (NoClassDefFoundError e) {
            if (!missingWorldGuardLogged) {
                missingWorldGuardLogged = true;
                org.bukkit.Bukkit.getConsoleSender().sendMessage(dev.grzybalson.utils.ColorUtils.colorize("&eWorldGuard nie zostal znaleziony - pomijam blokade regionow adminitems."));
            }
            return false;
        } catch (Exception e) {
            org.bukkit.Bukkit.getConsoleSender().sendMessage(dev.grzybalson.utils.ColorUtils.colorize("&eBlad podczas sprawdzania regionow adminitems: " + e.getMessage()));
            return false;
        }
    }

    public static void sendBlockedMessage(Player player, HollyCore plugin) {
        if (player == null || plugin == null || plugin.getAdminItemsConfig() == null) {
            return;
        }

        String message = plugin.getAdminItemsConfig().getBlockedRegionMessage();
        if (message != null && !message.isBlank()) {
            player.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(message));
        }
    }

    private static ProtectedRegion getRegion(RegionManager manager, String regionId) {
        if (regionId == null || regionId.isBlank()) {
            return null;
        }

        ProtectedRegion region = manager.getRegion(regionId);
        if (region != null) {
            return region;
        }
        return manager.getRegion(regionId.toLowerCase(Locale.ROOT));
    }

    private static RegionManager getRegionManager(World world) {
        RegionContainer container = getContainer();
        return container == null ? null : container.get(BukkitAdapter.adapt(world));
    }

    private static RegionContainer getContainer() {
        RegionContainer container = cachedContainer;
        if (container != null) {
            return container;
        }

        container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        cachedContainer = container;
        return container;
    }
}
