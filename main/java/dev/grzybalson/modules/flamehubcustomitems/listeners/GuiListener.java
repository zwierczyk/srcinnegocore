package dev.grzybalson.modules.flamehubcustomitems.listeners;

import dev.grzybalson.modules.flamehubcustomitems.config.FlameHubConfigManager;
import dev.grzybalson.modules.flamehubcustomitems.manager.CustomItemType;
import dev.grzybalson.modules.flamehubcustomitems.manager.HoodItemsGuiHolder;
import dev.grzybalson.modules.flamehubcustomitems.manager.ItemManager;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public class GuiListener implements Listener {

    private final FlameHubConfigManager configManager;
    private final ItemManager itemManager;

    public GuiListener(FlameHubConfigManager configManager, ItemManager itemManager) {
        this.configManager = configManager;
        this.itemManager = itemManager;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof HoodItemsGuiHolder)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (event.getClickedInventory() == null || !(event.getClickedInventory().getHolder() instanceof HoodItemsGuiHolder)) {
            return;
        }

        int slot = event.getSlot();
        if (slot == configManager.getGuiYodaSlot()) {
            giveItem(player, CustomItemType.YODA);
        } else if (slot == configManager.getGuiVaderSlot()) {
            giveItem(player, CustomItemType.VADER);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof HoodItemsGuiHolder) {
            event.setCancelled(true);
        }
    }

    private void giveItem(Player player, CustomItemType type) {
        ItemStack item = itemManager.createItem(type);
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);

        if (!leftovers.isEmpty()) {
            for (ItemStack leftover : leftovers.values()) {
                player.getWorld().dropItem(player.getLocation(), leftover);
            }
        }

        String itemName = configManager.getItemName(type);
        player.sendMessage(configManager.getMessage("item-received", "{ITEM}", itemName));
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.2f);
    }
}
