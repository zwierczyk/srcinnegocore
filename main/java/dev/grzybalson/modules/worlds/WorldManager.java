package dev.grzybalson.modules.worlds;

import dev.grzybalson.HollyCore;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * Zamiennik MultiverseCore — auto-load światów z level.dat + zarządzanie przez /world.
 * 1:1 logika z hollyservices-engine WorldModule.
 */
public final class WorldManager {

    private final HollyCore plugin;
    private final Logger logger;
    private boolean autoLoad;

    public WorldManager(HollyCore plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    public void start() {
        File configFile = new File(plugin.getDataFolder(), "addons/worlds/worlds.yml");
        if (!configFile.exists()) {
            plugin.saveResource("addons/worlds/worlds.yml", false);
        }
        autoLoad = org.bukkit.configuration.file.YamlConfiguration
            .loadConfiguration(configFile)
            .getBoolean("auto-load", true);

        if (!autoLoad) {
            return;
        }

        File container = Bukkit.getWorldContainer();
        File[] folders = container.listFiles(File::isDirectory);
        if (folders == null) {
            return;
        }

        for (File folder : folders) {
            File levelDat = new File(folder, "level.dat");
            if (!levelDat.exists()) {
                continue;
            }
            String worldName = folder.getName();
            if (Bukkit.getWorld(worldName) != null) {
                continue;
            }
            if (shouldSkipFolder(worldName)) {
                continue;
            }
            logger.info("[Worlds] Auto-loading world: " + worldName);
            try {
                Bukkit.createWorld(new WorldCreator(worldName));
            } catch (RuntimeException exception) {
                logger.warning("[Worlds] Nie udalo sie zaladowac " + worldName + ": " + exception.getMessage());
            }
        }
    }

    public void shutdown() {
        // światy zostają — Paper sam je zapisuje przy stopie
    }

    public List<String> getUnloadedWorldNames() {
        List<String> result = new ArrayList<>();
        File serverDir = Bukkit.getWorldContainer();
        File[] dirs = serverDir.listFiles(File::isDirectory);
        if (dirs == null) {
            return result;
        }
        for (File dir : dirs) {
            if (shouldSkipFolder(dir.getName())) {
                continue;
            }
            if (new File(dir, "level.dat").exists() && Bukkit.getWorld(dir.getName()) == null) {
                result.add(dir.getName());
            }
        }
        return result;
    }

    public List<String> getLoadedWorldNames() {
        return Bukkit.getWorlds().stream()
            .map(World::getName)
            .filter(name -> !dev.grzybalson.modules.event.dungeon.DungeonWorldService.isDungeonWorld(name))
            .toList();
    }

    private static boolean shouldSkipFolder(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.equals("plugins")
            || lower.equals("logs")
            || lower.equals("cache")
            || lower.equals("libraries")
            || lower.equals("versions")
            || lower.equals("config")
            || lower.startsWith(".")
            || lower.startsWith("ninja_dungeon_");
    }
}
