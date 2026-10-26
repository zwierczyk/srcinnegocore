package dev.grzybalson.modules.masakracustomitems;

import org.bukkit.inventory.ItemStack;

final class MisakoBagItems {
    static final int SIZE = 54;
    private MisakoBagItems() { }

    static boolean empty(ItemStack stack) {
        return stack == null || stack.getType().isAir() || stack.getAmount() <= 0;
    }

    /** Mutates only destination slots; returns the amount accepted from the untouched source. */
    static int insert(ItemStack[] destination, ItemStack source) {
        if (empty(source)) return 0;
        int remaining = source.getAmount();
        for (ItemStack slot : destination) {
            if (empty(slot) || !slot.isSimilar(source)) continue;
            int count = Math.min(remaining, Math.max(0, slot.getMaxStackSize() - slot.getAmount()));
            slot.setAmount(slot.getAmount() + count);
            remaining -= count;
            if (remaining == 0) return source.getAmount();
        }
        for (int i = 0; i < destination.length && remaining > 0; i++) {
            if (!empty(destination[i])) continue;
            ItemStack placed = source.clone();
            placed.setAmount(Math.min(remaining, source.getMaxStackSize()));
            destination[i] = placed;
            remaining -= placed.getAmount();
        }
        return source.getAmount() - remaining;
    }
}
