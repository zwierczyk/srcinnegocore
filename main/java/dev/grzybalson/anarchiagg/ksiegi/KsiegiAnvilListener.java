package dev.grzybalson.anarchiagg.ksiegi;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;

public class KsiegiAnvilListener implements Listener {
    private final KsiegiManager manager;

    public KsiegiAnvilListener(KsiegiManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        AnvilInventory inventory = event.getInventory();
        ItemStack base = inventory.getItem(0);
        ItemStack addition = inventory.getItem(1);
        KsiegiManager.BookType type = manager.identifyBook(addition);

        if (type == null) {
            return;
        }
        if (base == null || base.getType() == Material.AIR) {
            event.setResult(null);
            return;
        }
        if (!manager.isTargetAllowed(base, type)
                || manager.hasEnchant(base, type)
                || (type != KsiegiManager.BookType.ULASKAWIENIA && !manager.isDamageable(base))) {
            event.setResult(null);
            return;
        }

        ItemStack result = manager.applyEnchantVisual(base.clone(), type);
        event.setResult(result);
        inventory.setRepairCost(1);
    }

    @EventHandler
    public void onResultClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getView().getTopInventory().getType() != InventoryType.ANVIL) {
            return;
        }
        if (event.getRawSlot() != 2) {
            return;
        }

        AnvilInventory inventory = (AnvilInventory) event.getView().getTopInventory();
        ItemStack result = event.getCurrentItem();
        if (result == null || result.getType() == Material.AIR || !manager.hasAnyEnchant(result)) {
            return;
        }

        event.setCancelled(true);

        if (event.isShiftClick()) {
            java.util.HashMap<Integer, ItemStack> leftovers = player.getInventory().addItem(result.clone());
            if (!leftovers.isEmpty()) {
                return;
            }
        } else {
            ItemStack cursor = event.getCursor();
            if (cursor != null && cursor.getType() != Material.AIR) {
                return;
            }
            event.getView().setCursor(result.clone());
        }

        inventory.setItem(0, null);
        ItemStack second = inventory.getItem(1);
        if (second != null && second.getAmount() > 1) {
            second.setAmount(second.getAmount() - 1);
            inventory.setItem(1, second);
        } else {
            inventory.setItem(1, null);
        }
        inventory.setResult(null);
        inventory.setRepairCost(0);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.2f);
        player.updateInventory();
    }
}
