package dev.grzybalson.anarchiagg;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

public final class AnarchiaGGPaths {
    public static final String ROOT_FOLDER = "addons/anarchiagg";

    private AnarchiaGGPaths() {}

    public static File getRootFolder(JavaPlugin plugin) {
        File root = new File(plugin.getDataFolder(), ROOT_FOLDER);
        if (!root.exists()) {
            root.mkdirs();
        }
        return root;
    }

    public static File getFolder(JavaPlugin plugin, String relativePath) {
        File folder = new File(getRootFolder(plugin), relativePath);
        if (!folder.exists()) {
            folder.mkdirs();
        }
        return folder;
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
