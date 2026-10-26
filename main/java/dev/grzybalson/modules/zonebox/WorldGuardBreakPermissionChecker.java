package dev.grzybalson.modules.zonebox;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class WorldGuardBreakPermissionChecker implements BreakPermissionChecker {
    @Override
    public boolean canBreakHere(Player player, Location location) {
        ApplicableRegionSet regions = WorldGuard.getInstance()
            .getPlatform()
            .getRegionContainer()
            .createQuery()
            .getApplicableRegions(BukkitAdapter.adapt(location));

        if (regions.size() == 0) {
            return true;
        }

        LocalPlayer localPlayer = WorldGuardPlugin.inst().wrapPlayer(player);
        return regions.queryState(localPlayer, Flags.BLOCK_BREAK) == StateFlag.State.ALLOW;
    }

    @Override
    public boolean isInAllowedRegion(Location location, java.util.List<String> regionNames) {
        if (location == null || regionNames == null || regionNames.isEmpty()) {
            return true;
        }

        ApplicableRegionSet regions = WorldGuard.getInstance()
            .getPlatform()
            .getRegionContainer()
            .createQuery()
            .getApplicableRegions(BukkitAdapter.adapt(location));

        if (regions.size() == 0) {
            return true;
        }

        for (com.sk89q.worldguard.protection.regions.ProtectedRegion region : regions) {
            for (String allowedRegion : regionNames) {
                if (region.getId().equalsIgnoreCase(allowedRegion.trim())) {
                    return true;
                }
            }
        }
        return false;
    }
}
