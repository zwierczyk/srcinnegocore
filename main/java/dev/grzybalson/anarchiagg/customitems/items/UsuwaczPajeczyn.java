package dev.grzybalson.anarchiagg.customitems.items;

import dev.grzybalson.anarchiagg.AnarchiaGGSettings;
import dev.grzybalson.anarchiagg.customitems.handlers.RegionHandler;
import dev.grzybalson.anarchiagg.customitems.util.EventItemRegistry;
import dev.grzybalson.anarchiagg.customitems.util.EventItemUseGuard;
import dev.grzybalson.utils.ColorUtils;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class UsuwaczPajeczyn implements Listener {

    public static final String ITEM_KEY = "usuwaczpajeczyn";
    public static final String RAW_DISPLAY_NAME = "&x&A&A&A&A&A&A&lᴜ&x&9&E&9&E&9&E&lꜱ&x&9&2&9&2&9&2&lᴜ&x&8&6&8&6&8&6&lᴡ&x&7&9&7&9&7&9&lᴀ&x&6&D&6&D&6&D&lᴄ&x&6&1&6&1&6&1&lᴢ &x&6&0&6&0&6&0&lᴘ&x&6&A&6&A&6&A&lᴀ&x&7&5&7&5&7&5&lᴊ&x&8&0&8&0&8&0&lᴇ&x&8&A&8&A&8&A&lᴄ&x&9&5&9&5&9&5&lᴢ&x&9&F&9&F&9&F&lʏ&x&A&A&A&A&A&A&lɴ";

    private final JavaPlugin plugin;
    private final RegionHandler regionHandler;
    private final Set<String> blockedRegions = new HashSet<>();
    private final int cooldownSeconds;
    private final int radius;

    public UsuwaczPajeczyn(JavaPlugin plugin, RegionHandler regionHandler) {
        this.plugin = plugin;
        this.regionHandler = regionHandler;

        FileConfiguration eventowkiConfig = AnarchiaGGSettings.getConfig(plugin);
        List<String> regionList = eventowkiConfig.getStringList("blocked-regions");
        if (regionList.isEmpty() && !eventowkiConfig.isSet("blocked-regions")) {
            eventowkiConfig.set("blocked-regions", List.of("spawn"));
            this.blockedRegions.add("spawn");
        } else {
            this.blockedRegions.addAll(regionList);
        }

        this.cooldownSeconds = eventowkiConfig.getInt("eventowki.usuwaczpajeczyn.cooldown", 10);
        this.radius = eventowkiConfig.getInt("eventowki.usuwaczpajeczyn.radius", 4);
    }

    public static ItemStack createDefaultItem(JavaPlugin plugin) {
        FileConfiguration config = plugin != null ? AnarchiaGGSettings.getConfig(plugin) : null;
        ItemStack item = new ItemStack(Material.GRAY_DYE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String name = config != null ? config.getString("eventowki.usuwaczpajeczyn.name", RAW_DISPLAY_NAME) : RAW_DISPLAY_NAME;
            meta.setDisplayName(ColorUtils.colorize(name));

            if (config != null && config.isList("eventowki.usuwaczpajeczyn.lore")) {
                List<String> lore = config.getStringList("eventowki.usuwaczpajeczyn.lore");
                if (!lore.isEmpty()) {
                    List<String> coloredLore = new ArrayList<>();
                    for (String line : lore) {
                        coloredLore.add(ColorUtils.colorize(line));
                    }
                    meta.setLore(coloredLore);
                } else {
                    meta.setLore(Collections.emptyList());
                }
            } else {
                meta.setLore(Collections.emptyList());
            }

            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack createDefaultItem() {
        return createDefaultItem(null);
    }

    private boolean isLocationBlocked(Location loc) {
        if (loc == null || loc.getWorld() == null) return true;
        for (String regionName : blockedRegions) {
            if (regionHandler.isLocationInRegion(loc, regionName)) {
                return true;
            }
        }
        return false;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (itemInHand == null || itemInHand.getType() == Material.AIR) {
            return;
        }

        if (!EventItemRegistry.matches(plugin, ITEM_KEY, itemInHand)
            && !EventItemRegistry.matches(plugin, "usuwacz-pajeczyn", itemInHand)
            && !isUsuwaczItem(itemInHand)) {
            return;
        }

        event.setCancelled(true);

        if (isLocationBlocked(player.getLocation())) {
            player.sendMessage(ColorUtils.error("Nie możesz używać tego przedmiotu na tej strefie!"));
            return;
        }

        if (EventItemUseGuard.hasItemCooldown(plugin, player, ITEM_KEY, itemInHand, cooldownSeconds)) {
            return;
        }

        Block center = player.getLocation().getBlock();
        int removedCount = 0;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block block = center.getRelative(x, y, z);
                    if (block.getType() == Material.COBWEB) {
                        block.setType(Material.AIR, false);
                        removedCount++;
                    }
                }
            }
        }

        if (removedCount > 0) {
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_SPIDER_DEATH, 0.8f, 1.4f);
            player.sendMessage(ColorUtils.success("Usunięto &e" + removedCount + " &apajęczyn wokół Ciebie!"));
        } else {
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1.2f);
            player.sendMessage(ColorUtils.warning("Brak pajęczyn w promieniu " + radius + " kratek!"));
        }

        EventItemUseGuard.applyItemCooldown(plugin, player, ITEM_KEY, itemInHand, cooldownSeconds);
    }

    private boolean isUsuwaczItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return false;
        String name = ColorUtils.stripColors(meta.getDisplayName()).toLowerCase(Locale.ROOT);
        return name.contains("usuwacz") && (name.contains("pajecz") || name.contains("pajęcz"));
    }
}
