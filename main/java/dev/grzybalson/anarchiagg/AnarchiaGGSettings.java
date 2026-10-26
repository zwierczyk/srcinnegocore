package dev.grzybalson.anarchiagg;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AnarchiaGGSettings {
    private static final Map<String, CachedConfig> CACHE = new ConcurrentHashMap<>();

    private AnarchiaGGSettings() {}

    public static YamlConfiguration getConfig(JavaPlugin plugin) {
        File file = getFile(plugin);
        AnarchiaGGPaths.ensureResource(plugin, "anarchiagg.yml");
        String key = file.getAbsolutePath();
        long lastModified = file.lastModified();
        CachedConfig cached = CACHE.get(key);
        if (cached != null && cached.lastModified == lastModified) {
            return cached.configuration;
        }
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        CACHE.put(key, new CachedConfig(configuration, lastModified));
        return configuration;
    }

    public static void save(JavaPlugin plugin, YamlConfiguration configuration) {
        File file = getFile(plugin);
        try {
            configuration.save(file);
            CACHE.put(file.getAbsolutePath(), new CachedConfig(configuration, file.lastModified()));
        } catch (IOException exception) {
            plugin.getLogger().warning("Nie udalo sie zapisac anarchiagg/anarchiagg.yml: " + exception.getMessage());
        }
    }

    public static void reload(JavaPlugin plugin) {
        File file = getFile(plugin);
        CACHE.remove(file.getAbsolutePath());
    }

    public static File getFile(JavaPlugin plugin) {
        return AnarchiaGGPaths.getFile(plugin, "anarchiagg.yml");
    }

    private record CachedConfig(YamlConfiguration configuration, long lastModified) {}
}
