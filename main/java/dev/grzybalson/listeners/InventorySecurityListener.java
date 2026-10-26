package dev.grzybalson.listeners;

import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;

public class InventorySecurityListener implements Listener {

    private final HollyCore plugin;

    public InventorySecurityListener(HollyCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!isRestarting()) {
            return;
        }

        if (isRestrictedContainer(event.getView().getTopInventory())) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                player.closeInventory();
                player.sendMessage(ColorUtils.colorizeComponent("&c✕ &8Ξ &cInterakcje z rynkiem, skrzyniami i enderchestami są zablokowane podczas restartu!"));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!isRestarting()) {
            return;
        }

        if (isRestrictedContainer(event.getView().getTopInventory())) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                player.closeInventory();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!isRestarting()) {
            return;
        }

        if (isRestrictedContainer(event.getInventory())) {
            event.setCancelled(true);
            if (event.getPlayer() instanceof Player player) {
                player.sendMessage(ColorUtils.colorizeComponent("&c✕ &8Ξ &cOtwieranie rynku, skrzyń i enderchestów jest zablokowane podczas restartu!"));
            }
        }
    }

    private boolean isRestarting() {
        return plugin.getRestartManager() != null && plugin.getRestartManager().isRestarting();
    }

    private boolean isRestrictedContainer(Inventory inventory) {
        if (inventory == null) {
            return false;
        }

        InventoryType type = inventory.getType();
        // Allow pure player inventory / crafting screen ('E' key)
        if (type == InventoryType.CRAFTING || type == InventoryType.PLAYER) {
            return false;
        }

        // Block EnderChest, Chest, Barrel, Shulker, Market, Custom GUIs, etc.
        return true;
    }
}
