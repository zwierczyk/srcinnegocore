package dev.grzybalson.anarchiagg.util;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import java.util.Collection;
import java.util.Locale;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class MineRegionUtil {

    private static volatile RegionQuery cachedQuery;
    private static volatile RegionContainer cachedContainer;
    private static volatile boolean missingWorldGuardLogged;

    private MineRegionUtil() {}

    public static boolean isInAnyRegion(Plugin plugin, Location loc, Collection<String> regionIds) {
        return findFirstRegion(plugin, loc, regionIds) != null;
    }

    public static String findFirstRegion(Plugin plugin, Location loc, Collection<String> regionIds) {
        if (regionIds == null || regionIds.isEmpty() || loc == null || loc.getWorld() == null) {
            return null;
        }
        try {
            RegionManager manager = getContainer().get(BukkitAdapter.adapt(loc.getWorld()));
            if (manager == null) {
                return null;
            }

            BlockVector3 point = BlockVector3.at(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
            for (String regionId : regionIds) {
                if (regionId == null || regionId.isBlank()) {
                    continue;
                }

                ProtectedRegion region = getRegion(manager, regionId);
                if (region != null && region.contains(point)) {
                    return regionId;
                }
            }
            return null;
        } catch (NoClassDefFoundError e) {
            if (plugin != null && !missingWorldGuardLogged) {
                missingWorldGuardLogged = true;
                dev.grzybalson.anarchiagg.util.TextUtil.sendConsole("&e" + "WorldGuard nie zostal znaleziony - pomijam blockedRegions.");
            }
            return null;
        } catch (Exception e) {
            if (plugin != null) {
                dev.grzybalson.anarchiagg.util.TextUtil.sendConsole("&e" + "Blad podczas sprawdzania regionow WorldGuard: " + e.getMessage());
            }
            return null;
        }
    }

    public static boolean isBlockBreakDenied(Plugin plugin, Player player, Location loc) {
        return isBuildDenied(plugin, player, loc, Flags.BLOCK_BREAK);
    }

    public static boolean isBlockPlaceDenied(Plugin plugin, Player player, Location loc) {
        return isBuildDenied(plugin, player, loc, Flags.BLOCK_PLACE);
    }

    private static boolean isBuildDenied(Plugin plugin, Player player, Location loc, StateFlag flag) {
        if (player == null || loc == null || loc.getWorld() == null) {
            return false;
        }
        try {
            WorldGuardPlugin worldGuard = WorldGuardPlugin.inst();
            if (worldGuard == null) {
                return false;
            }

            RegionQuery query = getQuery();
            com.sk89q.worldedit.util.Location wgLoc = BukkitAdapter.adapt(loc);
            LocalPlayer localPlayer = worldGuard.wrapPlayer(player);
            if (WorldGuard.getInstance().getPlatform().getSessionManager().hasBypass(localPlayer, BukkitAdapter.adapt(loc.getWorld()))) {
                return false;
            }

            return !query.testBuild(wgLoc, localPlayer, flag);
        } catch (NoClassDefFoundError e) {
            if (plugin != null && !missingWorldGuardLogged) {
                missingWorldGuardLogged = true;
                dev.grzybalson.anarchiagg.util.TextUtil.sendConsole("&e" + "WorldGuard nie zostal znaleziony - pomijam integracje regionow.");
            }
            return false;
        } catch (Exception e) {
            if (plugin != null) {
                dev.grzybalson.anarchiagg.util.TextUtil.sendConsole("&e" + "Blad podczas sprawdzania permisji WorldGuard: " + e.getMessage());
            }
            return false;
        }
    }

    private static RegionQuery getQuery() {
        RegionQuery query = cachedQuery;
        if (query != null) {
            return query;
        }

        query = getContainer().createQuery();
        cachedQuery = query;
        return query;
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
}
