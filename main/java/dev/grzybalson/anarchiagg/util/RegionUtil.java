package dev.grzybalson.anarchiagg.util;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import dev.grzybalson.HollyCore;

public class RegionUtil {
    private static final long CONFIG_CACHE_TTL_MILLIS = 1_000L;

    private static volatile RegionContainer cachedContainer;
    private static volatile Set<String> cachedProtectedRegions = Collections.emptySet();
    private static volatile int cachedProtectedRegionsSize = -1;
    private static volatile int cachedProtectedRegionsHash;
    private static volatile long nextProtectedRegionsCheckMillis;

    public static boolean isInProtectedRegion(Player player, HollyCore plugin) {
        return player != null && isInProtectedRegion(player.getLocation(), plugin);
    }

    public static boolean isInProtectedRegion(Location location, HollyCore plugin) {
        return isInsideAnyConfiguredRegion(location, getProtectedRegions(plugin));
    }

    public static boolean isInSpawn(Player player, HollyCore plugin) {
        return player != null && isInSpawn(player.getLocation(), plugin);
    }

    public static boolean isInSpawn(Location location, HollyCore plugin) {
        if (plugin == null) {
            return false;
        }

        String spawnRegion = plugin.getConfig().getString("spawn-region", "spawn");
        return isInsideAnyConfiguredRegion(location, Collections.singleton(spawnRegion));
    }

    private static Set<String> getProtectedRegions(HollyCore plugin) {
        if (plugin == null) {
            return Collections.emptySet();
        }

        long now = System.currentTimeMillis();
        if (now < nextProtectedRegionsCheckMillis) {
            return cachedProtectedRegions;
        }

        List<String> protectedRegions = plugin.getConfig().getStringList("protected-regions");
        int size = protectedRegions.size();
        int hash = protectedRegions.hashCode();
        if (size == cachedProtectedRegionsSize && hash == cachedProtectedRegionsHash) {
            nextProtectedRegionsCheckMillis = now + CONFIG_CACHE_TTL_MILLIS;
            return cachedProtectedRegions;
        }

        Set<String> regionIds = normalizeRegionIds(protectedRegions);
        cachedProtectedRegions = regionIds;
        cachedProtectedRegionsSize = size;
        cachedProtectedRegionsHash = hash;
        nextProtectedRegionsCheckMillis = now + CONFIG_CACHE_TTL_MILLIS;
        return regionIds;
    }

    private static boolean isInsideAnyConfiguredRegion(Location location, Collection<String> regionIds) {
        if (location == null || location.getWorld() == null || regionIds == null || regionIds.isEmpty()) {
            return false;
        }

        RegionManager manager = getRegionManager(location.getWorld());
        if (manager == null) {
            return false;
        }

        BlockVector3 point = BlockVector3.at(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        for (String regionId : regionIds) {
            ProtectedRegion region = getRegion(manager, regionId);
            if (region != null && region.contains(point)) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> normalizeRegionIds(Collection<String> regionIds) {
        if (regionIds == null || regionIds.isEmpty()) {
            return Collections.emptySet();
        }

        Set<String> normalized = new HashSet<>(regionIds.size());
        for (String regionId : regionIds) {
            if (regionId != null && !regionId.isBlank()) {
                normalized.add(regionId.toLowerCase(Locale.ROOT));
            }
        }
        return normalized.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(normalized);
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
