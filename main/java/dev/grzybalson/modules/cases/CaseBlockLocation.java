package dev.grzybalson.modules.cases;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Objects;

public final class CaseBlockLocation {

    private final String world;
    private final int x;
    private final int y;
    private final int z;

    public CaseBlockLocation(String world, int x, int y, int z) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static CaseBlockLocation fromBlock(Block block) {
        return new CaseBlockLocation(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    public static CaseBlockLocation fromSection(ConfigurationSection section) {
        if (section == null) {
            return null;
        }

        String world = section.getString("world");
        if (world == null || world.isBlank()) {
            return null;
        }

        return new CaseBlockLocation(
            world,
            section.getInt("x"),
            section.getInt("y"),
            section.getInt("z")
        );
    }

    public boolean matches(Block block) {
        if (block == null) {
            return false;
        }
        return block.getX() == x
            && block.getY() == y
            && block.getZ() == z
            && block.getWorld().getName().equals(world);
    }

    public Location toLocation() {
        World bukkitWorld = Bukkit.getWorld(world);
        if (bukkitWorld == null) {
            return null;
        }
        return new Location(bukkitWorld, x, y, z);
    }

    public String world() {
        return world;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int z() {
        return z;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof CaseBlockLocation that)) {
            return false;
        }
        return x == that.x && y == that.y && z == that.z && Objects.equals(world, that.world);
    }

    @Override
    public int hashCode() {
        return Objects.hash(world, x, y, z);
    }
}
