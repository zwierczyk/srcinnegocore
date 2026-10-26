package dev.grzybalson.anarchiagg.customitems.handlers;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Bukkit;
import org.bukkit.Location;

public class RegionHandler {
    public boolean isLocationInRegion(Location location, String regionName) {
        if (location == null || regionName == null) return false;
        RegionManager regionManager = WorldGuard.getInstance().getPlatform()
                .getRegionContainer()
                .get(BukkitAdapter.adapt(location.getWorld()));

        if (regionManager == null) {
            Bukkit.getLogger().warning("[RegionHandler] RegionManager is null for world: " + location.getWorld().getName());
            return false;
        }

        ProtectedRegion region = regionManager.getRegion(regionName);
        if (region == null) {
            return false;
        }

        BlockVector3 blockVector = BukkitAdapter.asBlockVector(location);
        return region.contains(blockVector);
    }

    public boolean isPvpAllowedOrNoRegion(Location location) {
        try {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionQuery query = container.createQuery();
            ApplicableRegionSet regions = query.getApplicableRegions(BukkitAdapter.adapt(location));

            StateFlag.State pvpState = regions.queryState(null, Flags.PVP);
            return pvpState != StateFlag.State.DENY;
        } catch (Exception e) {
            return true;
        }
    }
}
