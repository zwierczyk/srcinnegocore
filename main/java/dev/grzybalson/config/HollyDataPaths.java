package dev.grzybalson.config;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

public final class HollyDataPaths {
    private HollyDataPaths() {}

    public static File getAddonsFolder(JavaPlugin plugin) {
        File folder = new File(plugin.getDataFolder(), "addons");
        if (!folder.exists()) {
            folder.mkdirs();
        }
        return folder;
    }

    public static File getAddonFolder(JavaPlugin plugin, String addonName) {
        File folder = new File(getAddonsFolder(plugin), addonName);
        if (!folder.exists()) {
            folder.mkdirs();
        }
        return folder;
    }

    public static File getRootFile(JavaPlugin plugin, String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        return file;
    }

    public static void ensureResource(JavaPlugin plugin, String resourcePath) {
        String normalizedPath = resourcePath.replace('\\', '/');
        File file = new File(plugin.getDataFolder(), normalizedPath);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        // Tylko gdy pliku NIE MA — nigdy nie nadpisuj istniejacych configow.
        if (file.exists() || plugin.getResource(normalizedPath) == null) {
            return;
        }

        try {
            plugin.saveResource(normalizedPath, false);
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("Nie udalo sie skopiowac domyslnego configu " + normalizedPath + ": " + exception.getMessage());
        }
    }

    public static void ensureEmptyFile(JavaPlugin plugin, String relativePath) {
        File file = new File(plugin.getDataFolder(), relativePath.replace('\\', '/'));
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        if (file.exists()) {
            return;
        }
        try {
            file.createNewFile();
        } catch (IOException exception) {
            plugin.getLogger().warning("Nie udalo sie utworzyc pliku: " + file.getPath());
        }
    }
}
