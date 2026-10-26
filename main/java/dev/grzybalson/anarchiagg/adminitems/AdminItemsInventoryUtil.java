package dev.grzybalson.anarchiagg.adminitems;

import dev.grzybalson.anarchiagg.util.ItemCompatibilityUtil;

import java.util.HashMap;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public final class AdminItemsInventoryUtil {

    private static final int STORAGE_SIZE = 36;

    private AdminItemsInventoryUtil() {
    }

    public static boolean moveItemToStorageTotemSlot(Player player, ItemStack item) {
        PlayerInventory inventory = player.getInventory();
        int slot = findStorageTotemSlot(inventory);

        if (slot == -1) {
            return false;
        }

        ItemStack slotItem = inventory.getItem(slot);
        if (slotItem == null) {
            return false;
        }

        if (slotItem.getAmount() <= 1) {
            inventory.setItem(slot, item);
            return true;
        }

        slotItem.setAmount(slotItem.getAmount() - 1);
        HashMap<Integer, ItemStack> leftovers = inventory.addItem(item);
        leftovers.values().forEach(leftover -> ItemCompatibilityUtil.dropItemNaturally(player, leftover));
        return true;
    }

    public static int findStorageTotemSlot(PlayerInventory inventory) {
        for (int slot = 0; slot < STORAGE_SIZE; slot++) {
            ItemStack slotItem = inventory.getItem(slot);
            if (slotItem != null && slotItem.getType() == Material.TOTEM_OF_UNDYING) {
                return slot;
            }
        }

        return -1;
    }
}
