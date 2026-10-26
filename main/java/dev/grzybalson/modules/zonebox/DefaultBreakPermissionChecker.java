package dev.grzybalson.modules.zonebox;

import org.bukkit.Location;
import org.bukkit.entity.Player;

public class DefaultBreakPermissionChecker implements BreakPermissionChecker {
    @Override
    public boolean canBreakHere(Player player, Location location) {
        return true;
    }

    @Override
    public boolean isInAllowedRegion(Location location, java.util.List<String> regionNames) {
        return true;
    }
}
