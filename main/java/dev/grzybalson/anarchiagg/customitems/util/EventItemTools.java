package dev.grzybalson.anarchiagg.customitems.util;

import dev.grzybalson.anarchiagg.AnarchiaGGSettings;
import dev.grzybalson.anarchiagg.customitems.handlers.RegionHandler;
import dev.grzybalson.managers.MultiActionBarService;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.ConfigItemStackReader;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class EventItemTools {

    private static final Map<UUID, Long> ELYTRA_LOCK_UNTIL = new ConcurrentHashMap<>();

    private EventItemTools() {
    }

    public static ItemStack createFromConfig(JavaPlugin plugin, String itemKey, Material fallback, String fallbackName, int customModelData) {
        FileConfiguration config = AnarchiaGGSettings.getConfig(plugin);
        ItemStack fromConfig = ConfigItemStackReader.getItem(config, "eventowki." + itemKey + ".full-item");
        if (fromConfig != null && fromConfig.getType() != Material.AIR) {
            tag(plugin, fromConfig, itemKey);
            return fromConfig;
        }

        ItemStack item = new ItemStack(fallback);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String name = config.getString("eventowki." + itemKey + ".name", fallbackName);
            meta.setDisplayName(ColorUtils.colorize(name));
            List<String> lore = config.getStringList("eventowki." + itemKey + ".full-item.lore");
            if (lore.isEmpty()) {
                lore = config.getStringList("eventowki." + itemKey + ".lore");
            }
            if (!lore.isEmpty()) {
                List<String> colored = new ArrayList<>();
                for (String line : lore) {
                    colored.add(ColorUtils.colorize(line));
                }
                meta.setLore(colored);
            }
            if (customModelData > 0) {
                meta.setCustomModelData(customModelData);
            }
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        tag(plugin, item, itemKey);
        return item;
    }

    public static void tag(JavaPlugin plugin, ItemStack item, String itemKey) {
        if (plugin == null || item == null || item.getType().isAir() || itemKey == null) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "event-item-id"), PersistentDataType.STRING, itemKey);
        item.setItemMeta(meta);
    }

    public static Set<String> loadBlockedRegions(JavaPlugin plugin, String itemKey) {
        FileConfiguration config = AnarchiaGGSettings.getConfig(plugin);
        Set<String> blocked = new HashSet<>();
        List<String> global = config.getStringList("blocked-regions");
        if (global.isEmpty()) {
            blocked.add("spawn");
        } else {
            blocked.addAll(global);
        }
        blocked.addAll(config.getStringList("eventowki." + itemKey + ".blocked-regions"));
        return blocked;
    }

    public static boolean isLocationBlocked(RegionHandler regionHandler, Set<String> blockedRegions, Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return true;
        }
        if (regionHandler == null) {
            return false;
        }
        for (String regionName : blockedRegions) {
            if (regionHandler.isLocationInRegion(loc, regionName)) {
                return true;
            }
        }
        return false;
    }

    public static void consumeOne(Player player, ItemStack usedItem, boolean mainHand) {
        if (usedItem == null) {
            return;
        }
        if (usedItem.getAmount() > 1) {
            usedItem.setAmount(usedItem.getAmount() - 1);
            return;
        }
        if (mainHand) {
            player.getInventory().setItemInMainHand(null);
        } else {
            player.getInventory().setItemInOffHand(null);
        }
    }

    public static ItemStack findUsed(JavaPlugin plugin, Player player, String itemKey, org.bukkit.event.player.PlayerInteractEvent event) {
        ItemStack main = player.getInventory().getItemInMainHand();
        if (main.getType() != Material.AIR && EventItemRegistry.matches(plugin, itemKey, main)) {
            if (event != null && event.getHand() == EquipmentSlot.OFF_HAND) {
                ItemStack off = player.getInventory().getItemInOffHand();
                if (off.getType() != Material.AIR && EventItemRegistry.matches(plugin, itemKey, off)) {
                    return off;
                }
            }
            return main;
        }
        ItemStack off = player.getInventory().getItemInOffHand();
        if (off.getType() != Material.AIR && EventItemRegistry.matches(plugin, itemKey, off)) {
            return off;
        }
        return null;
    }

    public static boolean isMainHand(Player player, ItemStack used) {
        return used != null && used == player.getInventory().getItemInMainHand();
    }

    public static void sendCastleActionBar(Player player, String channel, String message) {
        if (player == null || message == null) {
            return;
        }
        MultiActionBarService service = MultiActionBarService.get();
        if (service != null) {
            service.send(player, channel, message, 30L);
            return;
        }
        player.sendActionBar(ColorUtils.colorizeComponent(message));
    }

    public static void lockElytra(Player player, long millis) {
        if (player == null || millis <= 0L) {
            return;
        }
        long until = System.currentTimeMillis() + millis;
        ELYTRA_LOCK_UNTIL.merge(player.getUniqueId(), until, Math::max);
        player.setGliding(false);
    }

    public static boolean isElytraLocked(Player player) {
        if (player == null) {
            return false;
        }
        Long until = ELYTRA_LOCK_UNTIL.get(player.getUniqueId());
        if (until == null) {
            return false;
        }
        if (until <= System.currentTimeMillis()) {
            ELYTRA_LOCK_UNTIL.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    public static void clearElytraLock(Player player) {
        if (player != null) {
            ELYTRA_LOCK_UNTIL.remove(player.getUniqueId());
        }
    }
}
