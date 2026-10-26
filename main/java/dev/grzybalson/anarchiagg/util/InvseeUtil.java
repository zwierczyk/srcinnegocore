package dev.grzybalson.anarchiagg.util;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public class InvseeUtil {

    public static void copyToGUI(Player target, Inventory gui) {
        PlayerInventory inv = target.getInventory();

        for (int i = 0; i < 36; i++) {
            gui.setItem(i, inv.getItem(i));
        }

        gui.setItem(36, inv.getHelmet());
        gui.setItem(37, inv.getChestplate());
        gui.setItem(38, inv.getLeggings());
        gui.setItem(39, inv.getBoots());
        gui.setItem(40, inv.getItemInOffHand());
    }

    public static void copyToPlayer(Inventory gui, Player target) {
        PlayerInventory inv = target.getInventory();

        for (int i = 0; i < 36; i++) {
            inv.setItem(i, gui.getItem(i));
        }

        inv.setHelmet(gui.getItem(36));
        inv.setChestplate(gui.getItem(37));
        inv.setLeggings(gui.getItem(38));
        inv.setBoots(gui.getItem(39));
        inv.setItemInOffHand(gui.getItem(40));
    }

    public static ItemStack[] copyToArray(Player target) {
        ItemStack[] items = new ItemStack[41];
        PlayerInventory inv = target.getInventory();

        for (int i = 0; i < 36; i++) {
            items[i] = clone(inv.getItem(i));
        }

        items[36] = clone(inv.getHelmet());
        items[37] = clone(inv.getChestplate());
        items[38] = clone(inv.getLeggings());
        items[39] = clone(inv.getBoots());
        items[40] = clone(inv.getItemInOffHand());

        return items;
    }

    public static ItemStack[] copyFromGUI(Inventory gui) {
        ItemStack[] items = new ItemStack[41];
        int limit = Math.min(items.length, gui.getSize());

        for (int i = 0; i < limit; i++) {
            items[i] = clone(gui.getItem(i));
        }

        return items;
    }

    public static void copyToGUI(ItemStack[] items, Inventory gui) {
        for (int i = 0; i < gui.getSize(); i++) {
            gui.setItem(i, null);
        }

        if (items == null) {
            return;
        }

        int limit = Math.min(41, Math.min(items.length, gui.getSize()));
        for (int i = 0; i < limit; i++) {
            gui.setItem(i, clone(items[i]));
        }
    }

    public static void copyToPlayer(ItemStack[] items, Player target) {
        if (items == null) {
            return;
        }

        PlayerInventory inv = target.getInventory();

        for (int i = 0; i < 36; i++) {
            inv.setItem(i, item(items, i));
        }

        inv.setHelmet(item(items, 36));
        inv.setChestplate(item(items, 37));
        inv.setLeggings(item(items, 38));
        inv.setBoots(item(items, 39));
        inv.setItemInOffHand(item(items, 40));
    }

    private static ItemStack item(ItemStack[] items, int slot) {
        if (slot < 0 || slot >= items.length) {
            return null;
        }
        return clone(items[slot]);
    }

    private static ItemStack clone(ItemStack item) {
        return item == null ? null : item.clone();
    }
}
