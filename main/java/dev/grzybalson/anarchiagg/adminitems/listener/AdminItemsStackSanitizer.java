package dev.grzybalson.anarchiagg.adminitems.listener;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import dev.grzybalson.HollyCore;
import dev.grzybalson.anarchiagg.util.ItemCompatibilityUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AdminItemsStackSanitizer implements Listener {

    private final HollyCore plugin;

    public AdminItemsStackSanitizer(HollyCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void afterInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            this.sanitizeSoon(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player) {
            this.sanitize(event.getInventory(), player);
            this.sanitizeSoon(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            this.sanitizeSoon(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemHeld(PlayerItemHeldEvent event) {
        this.sanitizeSoon(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSwapHandItems(PlayerSwapHandItemsEvent event) {
        this.sanitizeSoon(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater((Plugin)this.plugin, () -> this.sanitize(event.getPlayer()), 20L);
    }

    private void sanitizeSoon(Player player) {
        Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> this.sanitize(player));
    }

    private void sanitize(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        this.sanitize(player.getInventory(), player);
    }

    private void sanitize(Inventory inventory, Player dropTarget) {
        if (inventory == null) {
            return;
        }

        List<ItemStack> overflow = new ArrayList<>();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }

            ItemCompatibilityUtil.clearCustomMaxStackSize(item);
            ItemCompatibilityUtil.hideAttributes(item);
            if (!this.shouldSplit(item)) {
                continue;
            }

            int maxStack = ItemCompatibilityUtil.vanillaMaxStack(item);
            int amount = item.getAmount();
            ItemStack kept = item.clone();
            kept.setAmount(maxStack);
            inventory.setItem(slot, kept);

            int left = amount - maxStack;
            while (left > 0) {
                int stackAmount = Math.min(maxStack, left);
                ItemStack stack = item.clone();
                stack.setAmount(stackAmount);
                overflow.add(stack);
                left -= stackAmount;
            }
        }

        for (ItemStack item : overflow) {
            Map<Integer, ItemStack> leftovers = inventory.addItem(item);
            if (dropTarget != null) {
                for (ItemStack leftover : leftovers.values()) {
                    ItemCompatibilityUtil.dropItemNaturally(dropTarget, leftover);
                }
            }
        }
    }

    private boolean shouldSplit(ItemStack item) {
        return item != null
                && item.getType() != Material.AIR
                && item.getAmount() > ItemCompatibilityUtil.vanillaMaxStack(item);
    }
}
