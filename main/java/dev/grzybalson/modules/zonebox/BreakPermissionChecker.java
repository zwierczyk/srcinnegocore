package dev.grzybalson.modules.zonebox;

import org.bukkit.Location;
import org.bukkit.entity.Player;

public interface BreakPermissionChecker {
    boolean canBreakHere(Player player, Location location);
    boolean isInAllowedRegion(Location location, java.util.List<String> regionNames);
}
