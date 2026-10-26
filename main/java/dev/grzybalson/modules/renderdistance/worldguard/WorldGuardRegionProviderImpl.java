package dev.grzybalson.modules.renderdistance.worldguard;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import dev.grzybalson.modules.renderdistance.model.WorldGuardRegionSnapshot;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;

public class WorldGuardRegionProviderImpl implements WorldGuardRegionProvider {
    private final RegionContainer regionContainer;
    private final RegionQuery regionQuery;

    public WorldGuardRegionProviderImpl() {
        this.regionContainer = WorldGuard.getInstance().getPlatform().getRegionContainer();
        this.regionQuery = regionContainer.createQuery();
    }

    @Override
    public String findRegionId(World world, String configuredRegionId) {
        if (world == null || configuredRegionId == null || configuredRegionId.isBlank()) {
            return null;
        }

        RegionManager regionManager = regionContainer.get(BukkitAdapter.adapt(world));
        if (regionManager == null) {
            return null;
        }

        ProtectedRegion directRegion = regionManager.getRegion(configuredRegionId);
        if (directRegion != null) {
            return directRegion.getId();
        }

        for (ProtectedRegion protectedRegion : regionManager.getRegions().values()) {
            if (protectedRegion.getId().equalsIgnoreCase(configuredRegionId)) {
                return protectedRegion.getId();
            }
        }
        return null;
    }

    @Override
    public List<WorldGuardRegionSnapshot> getApplicableRegions(Location location) {
        if (location == null || location.getWorld() == null) {
            return List.of();
        }

        ApplicableRegionSet applicableRegions = regionQuery.getApplicableRegions(BukkitAdapter.adapt(location));

        List<WorldGuardRegionSnapshot> snapshots = new ArrayList<>();
        for (ProtectedRegion protectedRegion : applicableRegions) {
            snapshots.add(new WorldGuardRegionSnapshot(protectedRegion.getId(), protectedRegion.getPriority()));
        }
        return snapshots;
    }
}
