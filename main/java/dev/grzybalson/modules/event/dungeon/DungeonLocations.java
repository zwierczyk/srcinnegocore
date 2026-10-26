package dev.grzybalson.modules.event.dungeon;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DungeonLocations {

    private final File file;
    private YamlConfiguration yaml;

    public DungeonLocations(File file) {
        this.file = file;
        reload();
    }

    public void reload() {
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        yaml = YamlConfiguration.loadConfiguration(file);
    }

    public void save() {
        try {
            yaml.save(file);
        } catch (IOException ignored) {
        }
    }

    public void setPlayerSpawn(Location loc) {
        writeLoc("player-spawn", loc);
        save();
    }

    public Location getPlayerSpawn(World world) {
        return readLoc("player-spawn", world);
    }

    public void setBoss(int level, Location loc) {
        writeLoc("levels." + level + ".boss", loc);
        save();
    }

    public Location getBoss(int level, World world) {
        return readLoc("levels." + level + ".boss", world);
    }

    public void setBossCamera(int level, Location loc) {
        writeLoc("levels." + level + ".camera", loc);
        save();
    }

    public Location getBossCamera(int level, World world) {
        return readLoc("levels." + level + ".camera", world);
    }

    public void setWave(int level, int wave, Location loc) {
        writeLoc("levels." + level + ".waves." + wave, loc);
        save();
    }

    public Location getWave(int level, int wave, World world) {
        return readLoc("levels." + level + ".waves." + wave, world);
    }

    public int maxWaves(int level) {
        return level == 3 ? 1 : 6;
    }

    public void addTutorialCamera(Location loc) {
        List<Map<String, Object>> list = new ArrayList<>();
        ConfigurationSection section = yaml.getConfigurationSection("tutorial-cameras");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("x", section.getDouble(key + ".x"));
                map.put("y", section.getDouble(key + ".y"));
                map.put("z", section.getDouble(key + ".z"));
                map.put("yaw", (float) section.getDouble(key + ".yaw"));
                map.put("pitch", (float) section.getDouble(key + ".pitch"));
                list.add(map);
            }
        }
        Map<String, Object> next = new LinkedHashMap<>();
        next.put("x", loc.getX());
        next.put("y", loc.getY());
        next.put("z", loc.getZ());
        next.put("yaw", loc.getYaw());
        next.put("pitch", loc.getPitch());
        list.add(next);
        yaml.set("tutorial-cameras", null);
        int i = 1;
        for (Map<String, Object> map : list) {
            String path = "tutorial-cameras." + i++;
            yaml.set(path + ".x", map.get("x"));
            yaml.set(path + ".y", map.get("y"));
            yaml.set(path + ".z", map.get("z"));
            yaml.set(path + ".yaw", map.get("yaw"));
            yaml.set(path + ".pitch", map.get("pitch"));
        }
        save();
    }

    public List<Location> getTutorialCameras(World world) {
        List<Location> out = new ArrayList<>();
        ConfigurationSection section = yaml.getConfigurationSection("tutorial-cameras");
        if (section == null) {
            return out;
        }
        for (String key : section.getKeys(false)) {
            Location loc = readLoc("tutorial-cameras." + key, world);
            if (loc != null) {
                out.add(loc);
            }
        }
        return out;
    }

    public boolean isReadyForStart() {
        if (!yaml.isSet("player-spawn.x")) {
            return false;
        }
        for (int level = 1; level <= 3; level++) {
            if (!yaml.isSet("levels." + level + ".boss.x")) {
                return false;
            }
            if (!yaml.isSet("levels." + level + ".camera.x")) {
                return false;
            }
            int waves = maxWaves(level);
            for (int wave = 1; wave <= waves; wave++) {
                if (!yaml.isSet("levels." + level + ".waves." + wave + ".x")) {
                    return false;
                }
            }
        }
        return true;
    }

    private void writeLoc(String path, Location loc) {
        yaml.set(path + ".world", loc.getWorld() == null ? "ninjaevent" : loc.getWorld().getName());
        yaml.set(path + ".x", loc.getX());
        yaml.set(path + ".y", loc.getY());
        yaml.set(path + ".z", loc.getZ());
        yaml.set(path + ".yaw", loc.getYaw());
        yaml.set(path + ".pitch", loc.getPitch());
    }

    private Location readLoc(String path, World world) {
        if (!yaml.isSet(path + ".x")) {
            return null;
        }
        World w = world;
        if (w == null) {
            String worldName = yaml.getString(path + ".world", "ninjaevent");
            w = Bukkit.getWorld(worldName);
        }
        if (w == null) {
            return null;
        }
        return new Location(
            w,
            yaml.getDouble(path + ".x"),
            yaml.getDouble(path + ".y"),
            yaml.getDouble(path + ".z"),
            (float) yaml.getDouble(path + ".yaw"),
            (float) yaml.getDouble(path + ".pitch")
        );
    }
}
