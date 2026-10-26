package dev.grzybalson.anarchiagg.util;

import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;

public class LocationUtil {
    public static boolean isInRadius(Location location, int radius) {
        return Math.abs(location.getBlockX()) <= radius && Math.abs(location.getBlockZ()) <= radius;
    }

    public static boolean isInRadius(String location, Location to, int radius) {
        if (location == null || to == null || to.getWorld() == null) {
            return false;
        }
        String[] split = location.split(";");
        if (split.length < 3) {
            return false;
        }
        World world = resolveWorld(split[0], worldUuid(split));
        if (world == null || !world.equals(to.getWorld())) {
            return false;
        }
        int distanceX = Math.abs(to.getBlockX() - LocationUtil.cordsToBlock(Double.parseDouble(split[1])));
        int zIndex = split.length >= 4 ? 3 : 2;
        int distanceZ = Math.abs(to.getBlockZ() - LocationUtil.cordsToBlock(Double.parseDouble(split[zIndex])));
        return distanceX <= radius && distanceZ <= radius;
    }

    public static boolean isInRadius(Location location, Location to, int radius) {
        if (location == null || to == null || location.getWorld() == null || to.getWorld() == null
                || !location.getWorld().getUID().equals(to.getWorld().getUID())) {
            return false;
        }
        int distanceX = Math.abs(to.getBlockX() - location.getBlockX());
        int distanceZ = Math.abs(to.getBlockZ() - location.getBlockZ());
        return distanceX <= radius && distanceZ <= radius;
    }

    public static boolean isInRadius(Location location, Location to, int radius, int radiusY) {
        if (location == null || to == null || location.getWorld() == null || to.getWorld() == null
                || !location.getWorld().getUID().equals(to.getWorld().getUID())) {
            return false;
        }
        int distanceY = Math.abs(to.getBlockY() - location.getBlockY());
        int distanceX = Math.abs(to.getBlockX() - location.getBlockX());
        int distanceZ = Math.abs(to.getBlockZ() - location.getBlockZ());
        return distanceX <= radius && distanceZ <= radius && distanceY <= radiusY;
    }

    public static boolean isSame(Location location, Location to) {
        return location.getBlockX() == to.getBlockX() && location.getBlockY() == to.getBlockY() && location.getBlockZ() == to.getBlockZ();
    }

    public static Location deserialize(String customLocation) {
        if (customLocation == null || customLocation.isBlank()) {
            throw new IllegalArgumentException("Location is empty");
        }
        String[] split = customLocation.split(";");
        if (split.length < 6) {
            throw new IllegalArgumentException("Invalid location: " + customLocation);
        }
        World world = resolveWorld(split[0], split.length > 6 ? split[6] : null);
        if (world == null) {
            throw new IllegalArgumentException("World not found: " + split[0]);
        }
        return new Location(world, Double.parseDouble(split[1]), Double.parseDouble(split[2]), Double.parseDouble(split[3]), Float.parseFloat(split[4]), Float.parseFloat(split[5]));
    }

    public static String serialize(Location location) {
        World world = requireWorld(location);
        return world.getName() + ";" + location.getX() + ";" + location.getY() + ";" + location.getZ() + ";" + location.getYaw() + ";" + location.getPitch() + ";" + world.getUID();
    }

    public static String serializeCenter(Location location) {
        World world = requireWorld(location);
        return world.getName() + ";" + ((double)location.getBlockX() + 0.5) + ";" + location.getY() + ";" + ((double)location.getBlockZ() + 0.5) + ";" + location.getYaw() + ";" + location.getPitch() + ";" + world.getUID();
    }

    public static String locationToString(Location location) {
        World world = requireWorld(location);
        return world.getName() + ";" + location.getBlockX() + ";" + location.getBlockY() + ";" + location.getBlockZ() + ";" + world.getUID();
    }

    public static Location locationFromString(String location) {
        if (location == null || location.isBlank()) {
            throw new IllegalArgumentException("Location is empty");
        }
        String[] split = location.split(";");
        if (split.length < 4) {
            throw new IllegalArgumentException("Invalid location: " + location);
        }
        World world = resolveWorld(split[0], split.length > 4 ? split[4] : null);
        if (world == null) {
            throw new IllegalArgumentException("World not found: " + split[0]);
        }
        return new Location(world, Double.parseDouble(split[1]), Double.parseDouble(split[2]), Double.parseDouble(split[3]));
    }

    public static Location withRotation(Location destination, Location rotationSource) {
        Location location = destination.clone();
        location.setYaw(rotationSource.getYaw());
        location.setPitch(rotationSource.getPitch());
        return location;
    }

    public static Location preservingPlayerRotation(Player player, Location destination) {
        return withRotation(destination, player.getLocation());
    }

    public static String customLocationToString(String location) {
        return location;
    }

    public static Location closestLocation(List<Location> locations, Location location) {
        double distance = -1.0;
        Location finalLoc = null;
        for (Location l : locations) {
            if (finalLoc != null && Math.pow(location.getBlockX() - l.getBlockX(), 2.0) + Math.pow(location.getBlockZ() - l.getBlockZ(), 2.0) > distance) continue;
            finalLoc = l;
            distance = Math.pow(location.getBlockX() - l.getBlockX(), 2.0) + Math.pow(location.getBlockZ() - l.getBlockZ(), 2.0);
        }
        return finalLoc;
    }

    public static Location farthestLocation(List<Location> locations, Location location) {
        double distance = -1.0;
        Location finalLoc = null;
        for (Location l : locations) {
            double pow = Math.pow(location.getBlockZ() - l.getBlockZ(), 2.0);
            double pow2 = Math.pow(location.getBlockX() - l.getBlockX(), 2.0);
            if (finalLoc != null && pow2 + pow < distance) continue;
            finalLoc = l;
            distance = Math.pow(location.getBlockX() - l.getBlockX(), 2.0) + Math.pow(location.getBlockZ() - l.getBlockZ(), 2.0);
        }
        return finalLoc;
    }

    public static Block findHighestBlock(World world, int X, int Z, int minY, int maxY) {
        for (int i = 1; i >= minY; ++i) {
            if (i > maxY) {
                return null;
            }
            Block block = new Location(world, (double)X, (double)i, (double)Z).getBlock();
            if (!block.getType().equals((Object)Material.AIR)) continue;
            return block;
        }
        return null;
    }

    public static int cordsToBlock(double cord) {
        if (cord >= 0.0) {
            return (int)cord;
        }
        return (int)cord - 1;
    }

    public static String toStringClaim(Location location) {
        return requireWorld(location).getName() + ";" + (location.getBlockX() >> 4);
    }

    public static String toStringClaim(String location) {
        String[] split = location.split(";");
        return split[0] + ";" + (LocationUtil.cordsToBlock(Double.parseDouble(split[1])) >> 4);
    }

    public static void clearItemsInWorld(World world) {
        if (world == null) {
            return;
        }
        world.getEntities().stream().filter(entity -> entity instanceof Item).forEach(entity -> entity.remove());
    }

    public static boolean isWithinBounds(Location loc, Location bound1, Location bound2) {
        if (loc == null || bound1 == null || bound2 == null
                || loc.getWorld() == null || bound1.getWorld() == null || bound2.getWorld() == null
                || !loc.getWorld().getUID().equals(bound1.getWorld().getUID())
                || !loc.getWorld().getUID().equals(bound2.getWorld().getUID())) {
            return false;
        }
        double minX = Math.min(bound1.getX(), bound2.getX());
        double maxX = Math.max(bound1.getX(), bound2.getX());
        double minY = Math.min(bound1.getY(), bound2.getY());
        double maxY = Math.max(bound1.getY(), bound2.getY());
        double minZ = Math.min(bound1.getZ(), bound2.getZ());
        double maxZ = Math.max(bound1.getZ(), bound2.getZ());
        return loc.getX() >= minX && loc.getX() <= maxX && loc.getY() >= minY && loc.getY() <= maxY && loc.getZ() >= minZ && loc.getZ() <= maxZ;
    }

    public static World resolveWorld(String nameOrUuid) {
        return resolveWorld(nameOrUuid, null);
    }

    public static World resolveWorld(String name, String uuid) {
        World world = worldByUuid(uuid);
        if (world != null) {
            return world;
        }

        if (name == null || name.isBlank()) {
            return null;
        }

        world = Bukkit.getWorld(name);
        if (world != null) {
            return world;
        }

        world = worldByUuid(name);
        if (world != null) {
            return world;
        }

        for (World loadedWorld : Bukkit.getWorlds()) {
            if (loadedWorld.getName().equalsIgnoreCase(name)) {
                return loadedWorld;
            }
        }
        return null;
    }

    private static World requireWorld(Location location) {
        if (location == null || location.getWorld() == null) {
            throw new IllegalArgumentException("Location world is null");
        }
        return location.getWorld();
    }

    private static World worldByUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Bukkit.getWorld(UUID.fromString(value));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String worldUuid(String[] split) {
        if (split.length > 6) {
            return split[6];
        }
        if (split.length > 4) {
            return split[4];
        }
        return null;
    }
}
