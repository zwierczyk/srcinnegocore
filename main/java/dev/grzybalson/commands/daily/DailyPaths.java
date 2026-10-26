package dev.grzybalson.commands.daily;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

public final class DailyPaths {
    public static final String ROOT_FOLDER = "addons/daily";

    private DailyPaths() {}

    public static File getRootFolder(JavaPlugin plugin) {
        File root = new File(plugin.getDataFolder(), ROOT_FOLDER);
        if (!root.exists()) {
            root.mkdirs();
        }
        return root;
    }

    public static File getFile(JavaPlugin plugin, String relativePath) {
        File file = new File(getRootFolder(plugin), relativePath);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        return file;
    }

    public static void ensureResource(JavaPlugin plugin, String relativePath) {
        File file = getFile(plugin, relativePath);
        if (file.exists()) {
            return;
        }

        try {
            plugin.saveResource(ROOT_FOLDER + "/" + relativePath.replace('\\', '/'), false);
        } catch (IllegalArgumentException exception) {
            try {
                file.createNewFile();
            } catch (IOException ioException) {
                plugin.getLogger().warning("Nie udalo sie utworzyc pliku: " + file.getPath());
            }
        }
    }
}
