package dev.grzybalson.modules.mslf;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class MslfRegionUtil {

    private MslfRegionUtil() {
    }

    static boolean isBlocked(Player player, MslfSettings settings) {
        return isBlocked(player, settings, null);
    }

    static boolean isBlocked(Player player, MslfSettings settings, MslfItemType type) {
        return player != null && isBlocked(player.getLocation(), settings, type);
    }

    static boolean isNearBlocked(Location center, MslfSettings settings, double bufferDistance) {
        return isNearBlocked(center, settings, bufferDistance, null);
    }

    static boolean isNearBlocked(Location center, MslfSettings settings, double bufferDistance, MslfItemType type) {
        if (isBlocked(center, settings, type)) {
            return true;
        }
        if (center == null || bufferDistance <= 0.0D) {
            return false;
        }
        Location[] offsets = new Location[] {
            center.clone().add(bufferDistance, 0, 0),
            center.clone().add(-bufferDistance, 0, 0),
            center.clone().add(0, 0, bufferDistance),
            center.clone().add(0, 0, -bufferDistance),
            center.clone().add(bufferDistance, 0, bufferDistance),
            center.clone().add(-bufferDistance, 0, -bufferDistance)
        };
        for (Location loc : offsets) {
            if (isBlocked(loc, settings, type)) {
                return true;
            }
        }
        return false;
    }

    static boolean isBlocked(Location location, MslfSettings settings) {
        return isBlocked(location, settings, null);
    }

    static boolean isBlocked(Location location, MslfSettings settings, MslfItemType type) {
        if (settings == null) {
            return false;
        }
        return isBlocked(location, blockedNames(settings, type));
    }

    /** Lista zablokowanych nazw (global + per-item) — do wyciagniecia z petli w goracych sciezkach. */
    static Set<String> blockedNames(MslfSettings settings, MslfItemType type) {
        Set<String> blocked = new HashSet<>();
        if (settings == null) {
            return blocked;
        }
        addBlockedNames(blocked, settings.blockedRegions());
        if (type != null) {
            addBlockedNames(blocked, settings.itemBlockedRegions(type));
        }
        return blocked;
    }

    static boolean isBlocked(Location location, Set<String> blocked) {
        if (location == null || location.getWorld() == null || blocked == null || blocked.isEmpty()) {
            return false;
        }
        return containsAny(location, blocked);
    }

    static List<String> regionIdsAt(Location location) {
        List<String> ids = new ArrayList<>();
        if (location == null || location.getWorld() == null) {
            return ids;
        }
        String worldName = location.getWorld().getName();
        ids.add(worldName);
        try {
            ApplicableRegionSet set = queryRegions(location);
            if (set == null) {
                return ids;
            }
            List<ProtectedRegion> regions = new ArrayList<>(set.getRegions());
            regions.sort(Comparator
                .comparingInt(ProtectedRegion::getPriority).reversed()
                .thenComparing(ProtectedRegion::getId, String.CASE_INSENSITIVE_ORDER));
            for (ProtectedRegion region : regions) {
                if (region == null || region.getId() == null || region.getId().equalsIgnoreCase("__global__")) {
                    continue;
                }
                if (ids.stream().noneMatch(existing -> existing.equalsIgnoreCase(region.getId()))) {
                    ids.add(region.getId());
                }
            }
        } catch (Throwable ignored) {
        }
        return ids;
    }

    private static void addBlockedNames(Set<String> out, List<String> names) {
        if (out == null || names == null) {
            return;
        }
        for (String raw : names) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String key = raw.trim().toLowerCase(Locale.ROOT);
            out.add(key);
            int sep = Math.max(key.lastIndexOf(':'), key.lastIndexOf(','));
            if (sep > 0 && sep < key.length() - 1) {
                out.add(key.substring(sep + 1).trim());
            }
        }
    }

    private static boolean containsAny(Location location, Set<String> blocked) {
        String worldName = location.getWorld().getName().toLowerCase(Locale.ROOT);
        if (blocked.contains(worldName)
            || blocked.contains("world:" + worldName)
            || blocked.contains("swiat:" + worldName)) {
            return true;
        }
        try {
            ApplicableRegionSet set = queryRegions(location);
            if (set != null) {
                for (ProtectedRegion region : set.getRegions()) {
                    if (matchesRegion(region, blocked)) {
                        return true;
                    }
                }
            }
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionManager manager = container.get(BukkitAdapter.adapt(location.getWorld()));
            if (manager == null) {
                return false;
            }
            BlockVector3 point = BlockVector3.at(location.getBlockX(), location.getBlockY(), location.getBlockZ());
            for (String id : blocked) {
                ProtectedRegion region = manager.getRegion(id);
                if (region != null && region.contains(point)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean matchesRegion(ProtectedRegion region, Set<String> blocked) {
        ProtectedRegion current = region;
        while (current != null) {
            String id = current.getId();
            if (id != null && blocked.contains(id.toLowerCase(Locale.ROOT))) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    private static ApplicableRegionSet queryRegions(Location location) {
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionQuery query = container.createQuery();
        return query.getApplicableRegions(BukkitAdapter.adapt(location));
    }
}
