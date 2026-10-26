package dev.grzybalson.modules.itemstorage;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.HollyDataPaths;
import dev.grzybalson.managers.PickupManager;
import dev.grzybalson.modules.szaman.PerksManager;
import dev.grzybalson.utils.ItemSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

public class ItemStorageManager {

    public static final String CONFIG_PATH = "addons/itemstorage/items.yml";
    private static final String NBT_TYPE_KEY = "itemstorage_type";

    private final HollyCore plugin;
    private final NamespacedKey nbtKey;
    private final Map<String, ItemStack> items = new LinkedHashMap<>();
    private final Map<String, String> serializedItems = new LinkedHashMap<>();

    public ItemStorageManager(HollyCore plugin) {
        this.plugin = plugin;
        this.nbtKey = new NamespacedKey(plugin, NBT_TYPE_KEY);
        reload();
    }

    public void reload() {
        items.clear();
        serializedItems.clear();

        HollyDataPaths.ensureResource(plugin, CONFIG_PATH);
        File file = new File(plugin.getDataFolder(), CONFIG_PATH);
        if (!file.exists()) {
            plugin.getLogger().warning("[ItemStorage] Brak pliku " + CONFIG_PATH);
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("serializedItems");
        if (section == null) {
            plugin.getLogger().warning("[ItemStorage] Brak sekcji serializedItems w " + CONFIG_PATH);
            return;
        }

        int loaded = 0;
        int failed = 0;
        for (String key : section.getKeys(false)) {
            String data = section.getString(key);
            if (data == null || data.isBlank()) {
                failed++;
                continue;
            }
            ItemStack stack = ItemSerializer.deserializeItem(data.trim());
            if (stack == null || stack.getType().isAir()) {
                plugin.getLogger().warning("[ItemStorage] Nie udalo sie deserializowac itemu: " + key);
                failed++;
                continue;
            }

            if (!key.contains("KEY_") && !key.contains("EVENT_") && !key.equalsIgnoreCase("CLAN_COIN")) {
                ItemMeta meta = stack.getItemMeta();
                if (meta != null) {
                    meta.getPersistentDataContainer().set(nbtKey, PersistentDataType.STRING, key);
                    stack.setItemMeta(meta);
                }
            }

            serializedItems.put(key, data.trim());
            items.put(key, stack);
            loaded++;
        }

        plugin.getLogger().info("[ItemStorage] Zaladowano " + loaded + " itemow" + (failed > 0 ? " (" + failed + " bledow)" : ""));
        scheduleSyncToPickupPackages();
        applyShardToSzaman();
    }

    public ItemStack getItem(String key) {
        if (key == null) {
            return null;
        }
        ItemStack item = items.get(key);
        if (item == null) {
            for (Map.Entry<String, ItemStack> entry : items.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(key)) {
                    return entry.getValue().clone();
                }
            }
            return null;
        }
        return item.clone();
    }

    public ItemStack createItem(String key, int amount) {
        ItemStack item = getItem(key);
        if (item == null) {
            return null;
        }
        item.setAmount(Math.max(1, Math.min(amount, item.getMaxStackSize())));
        return item;
    }

    public Set<String> getKeys() {
        return Collections.unmodifiableSet(items.keySet());
    }

    public Map<String, ItemStack> getItems() {
        return Collections.unmodifiableMap(items);
    }

    public String getSerialized(String key) {
        return serializedItems.get(key);
    }

    public NamespacedKey getNbtKey() {
        return nbtKey;
    }

    public void syncToPickupPackages() {
        PickupManager pickupManager = plugin.getPickupManager();
        if (pickupManager == null) {
            return;
        }

        int created = 0;
        for (Map.Entry<String, ItemStack> entry : items.entrySet()) {
            String key = entry.getKey();
            if (pickupManager.packageExists(key)) {
                continue;
            }
            pickupManager.createPackage(key, entry.getValue().clone());
            created++;
        }

        if (created > 0) {
            plugin.getLogger().info("[ItemStorage] Zarejestrowano " + created + " pakietow /odbierz");
        }
    }

    private void scheduleSyncToPickupPackages() {
        if (plugin.getPickupManager() == null) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, this::syncToPickupPackages, 40L);
        Bukkit.getScheduler().runTaskLater(plugin, this::syncToPickupPackages, 100L);
    }

    private void applyShardToSzaman() {
        ItemStack shard = getItem("SHARD");
        if (shard == null) {
            return;
        }

        PerksManager perksManager = plugin.getSzamanManager();
        if (perksManager != null) {
            perksManager.applyShardItemIfAbsent(shard);
            return;
        }

        writeShardToSzamanConfig(shard);
    }

    private void writeShardToSzamanConfig(ItemStack shard) {
        try {
            HollyDataPaths.ensureResource(plugin, "addons/szaman/szaman.yml");
            File file = new File(plugin.getDataFolder(), "addons/szaman/szaman.yml");
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            if (config.getString("shard.serializedItem") != null && !config.getString("shard.serializedItem").isBlank()) {
                return;
            }
            String serialized = ItemSerializer.serializeItem(shard);
            if (serialized == null) {
                String raw = serializedItems.get("SHARD");
                if (raw != null) {
                    serialized = raw;
                }
            }
            if (serialized == null) {
                return;
            }
            config.set("shard.serializedItem", serialized);
            config.save(file);
        } catch (Exception exception) {
            plugin.getLogger().log(Level.WARNING, "[ItemStorage] Nie udalo sie zapisac SHARD do szaman.yml", exception);
        }
    }
}
