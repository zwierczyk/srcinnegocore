package dev.grzybalson.utils;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class NBTUtils {

    private static Plugin plugin;
    private static final Map<String, NamespacedKey> KEY_CACHE = new ConcurrentHashMap<>();

    public static void init(Plugin pluginInstance) {
        plugin = pluginInstance;
        KEY_CACHE.clear();
    }

    public static NamespacedKey createKey(String key) {
        Plugin currentPlugin = plugin;
        if (currentPlugin == null) {
            throw new IllegalStateException("NBTUtils has not been initialized");
        }
        return KEY_CACHE.computeIfAbsent(key, ignored -> new NamespacedKey(currentPlugin, key));
    }

    public static ItemStack setString(ItemStack item, String key, String value) {
        if (item == null) return null;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(createKey(key), PersistentDataType.STRING, value);
        item.setItemMeta(meta);

        return item;
    }

    public static String getString(ItemStack item, String key) {
        if (item == null || !item.hasItemMeta()) return null;

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        return container.get(createKey(key), PersistentDataType.STRING);
    }

    public static ItemStack setInt(ItemStack item, String key, int value) {
        if (item == null) return null;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(createKey(key), PersistentDataType.INTEGER, value);
        item.setItemMeta(meta);

        return item;
    }

    public static Integer getInt(ItemStack item, String key) {
        if (item == null || !item.hasItemMeta()) return null;

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        return container.get(createKey(key), PersistentDataType.INTEGER);
    }

    public static ItemStack setDouble(ItemStack item, String key, double value) {
        if (item == null) return null;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(createKey(key), PersistentDataType.DOUBLE, value);
        item.setItemMeta(meta);

        return item;
    }

    public static Double getDouble(ItemStack item, String key) {
        if (item == null || !item.hasItemMeta()) return null;

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        return container.get(createKey(key), PersistentDataType.DOUBLE);
    }

    public static ItemStack setLong(ItemStack item, String key, long value) {
        if (item == null) return null;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(createKey(key), PersistentDataType.LONG, value);
        item.setItemMeta(meta);

        return item;
    }

    public static Long getLong(ItemStack item, String key) {
        if (item == null || !item.hasItemMeta()) return null;

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        return container.get(createKey(key), PersistentDataType.LONG);
    }

    public static boolean hasKey(ItemStack item, String key) {
        if (item == null || !item.hasItemMeta()) return false;

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        return container.has(createKey(key), PersistentDataType.STRING) ||
               container.has(createKey(key), PersistentDataType.INTEGER) ||
               container.has(createKey(key), PersistentDataType.DOUBLE) ||
               container.has(createKey(key), PersistentDataType.LONG);
    }

    public static ItemStack removeKey(ItemStack item, String key) {
        if (item == null || !item.hasItemMeta()) return item;

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.remove(createKey(key));
        item.setItemMeta(meta);

        return item;
    }

    public static boolean matchesNBT(ItemStack item, String key, String expectedValue) {
        String value = getString(item, key);
        return value != null && value.equals(expectedValue);
    }

    public static boolean matchesNBT(ItemStack item, String key, int expectedValue) {
        Integer value = getInt(item, key);
        return value != null && value == expectedValue;
    }
}
