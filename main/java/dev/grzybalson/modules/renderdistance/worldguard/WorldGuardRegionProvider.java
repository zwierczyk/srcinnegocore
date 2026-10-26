package dev.grzybalson.modules.renderdistance.worldguard;

import dev.grzybalson.modules.renderdistance.model.WorldGuardRegionSnapshot;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.List;

public interface WorldGuardRegionProvider {
    String findRegionId(World world, String configuredRegionId);

    List<WorldGuardRegionSnapshot> getApplicableRegions(Location location);
}
