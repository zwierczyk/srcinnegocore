package dev.grzybalson.anarchiagg.customitems.items;

import dev.grzybalson.anarchiagg.AnarchiaGGSettings;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import dev.grzybalson.anarchiagg.customitems.handlers.RegionHandler;
import dev.grzybalson.anarchiagg.customitems.util.EventItemRegistry;
import dev.grzybalson.anarchiagg.customitems.util.EventItemUseGuard;
import java.util.*;

public class KostkaRubika implements Listener {
    private final JavaPlugin plugin;
    private final RegionHandler regionHandler;
    private final int kostkarubikaCooldown;
    private final String kostkarubikaName;
    private final Material kostkarubikaMaterial;
    private final Set<String> blockedRegions = new HashSet<>();

    public KostkaRubika(JavaPlugin plugin, RegionHandler regionHandler) {
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
        kostkarubikaCooldown = eventowkiConfig.getInt("eventowki.kostkarubika.cooldown", 10);
        kostkarubikaName = eventowkiConfig.getString("eventowki.kostkarubika.name", "§e§lKostka rubika").replace("&", "§");

        Material configuredMaterial = Material.getMaterial(eventowkiConfig.getString("eventowki.kostkarubika.material", "PLAYER_HEAD"));
        this.kostkarubikaMaterial = (configuredMaterial != null) ? configuredMaterial : Material.PLAYER_HEAD;
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (event.isCancelled()) return;

        if (isLocationBlocked(attacker.getLocation()) || isLocationBlocked(victim.getLocation())) {
            return;
        }

        ItemStack mainHandItem = attacker.getInventory().getItemInMainHand();
        ItemStack offHandItem = attacker.getInventory().getItemInOffHand();

        ItemStack usedItem = null;
        boolean isMainHand = false;

        if (mainHandItem.getType() != Material.AIR && EventItemRegistry.matches(plugin, "kostkarubika", mainHandItem)) {
            usedItem = mainHandItem;
            isMainHand = true;
        } else if (offHandItem.getType() != Material.AIR && EventItemRegistry.matches(plugin, "kostkarubika", offHandItem)) {
            usedItem = offHandItem;
            isMainHand = false;
        }

        if (usedItem == null) return;
        if (!EventItemUseGuard.tryUse(plugin, attacker)) return;

        if (EventItemUseGuard.hasItemCooldown(plugin, attacker, "kostkarubika", usedItem, kostkarubikaCooldown)) {
            return;
        }

        if (usedItem.getAmount() > 1) {
            usedItem.setAmount(usedItem.getAmount() - 1);
        } else {
            if (isMainHand) {
                mainHandItem.setAmount(0);
            } else {
                offHandItem.setAmount(0);
            }
        }

        EventItemUseGuard.applyItemCooldown(plugin, attacker, "kostkarubika", usedItem, kostkarubikaCooldown);
        shuffleHotbar(victim);
    }

    private void shuffleHotbar(Player player) {
        PlayerInventory inventory = player.getInventory();

        List<ItemStack> hotbarItems = new ArrayList<>();
        for (int i = 0; i <= 8; i++) {
            hotbarItems.add(inventory.getItem(i));
        }

        Collections.shuffle(hotbarItems);

        for (int i = 0; i <= 8; i++) {
            inventory.setItem(i, hotbarItems.get(i));
        }
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
}
