package dev.grzybalson.anarchiagg.adminitems.listener;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import dev.grzybalson.HollyCore;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsConfig;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsGUI;

public class AdminItemsCraftListener implements Listener {

    private final AdminItemsConfig config;

    public AdminItemsCraftListener(HollyCore plugin) {
        this.config = plugin.getAdminItemsConfig();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        CraftingInventory inventory = event.getInventory();
        if (!isBoneMealResult(inventory.getResult())) {
            return;
        }

        if (containsIceWand(inventory.getMatrix())) {
            inventory.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getInventory() instanceof CraftingInventory)) { return; } CraftingInventory inventory = (CraftingInventory) event.getInventory(); if (false) {
            return;
        }

        if (!isBoneMealResult(inventory.getResult())) {
            return;
        }

        if (containsIceWand(inventory.getMatrix())) {
            event.setCancelled(true);
            inventory.setResult(null);
        }
    }

    private boolean isBoneMealResult(ItemStack item) {
        return item != null && item.getType() == Material.BONE_MEAL;
    }

    private boolean containsIceWand(ItemStack[] matrix) {
        for (ItemStack item : matrix) {
            if (isIceWand(item)) {
                return true;
            }
        }

        return false;
    }

    private boolean isIceWand(ItemStack item) {
        if (item == null || item.getType() != Material.BONE || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        if (AdminItemsGUI.KEY_LODOWA_ROZDZKA != null
                && meta.getPersistentDataContainer().has(AdminItemsGUI.KEY_LODOWA_ROZDZKA, PersistentDataType.BYTE)) {
            return true;
        }

        return meta.hasCustomModelData()
                && meta.getCustomModelData() == config.getIceWand().getCustomModelData();
    }
}
