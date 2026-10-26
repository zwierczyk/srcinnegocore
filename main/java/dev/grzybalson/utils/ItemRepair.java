package dev.grzybalson.utils;

import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Naprawia durability bez zjadania nazwy / lore / CMD (Paper + Adventure na setach).
 */
public final class ItemRepair {

    private ItemRepair() {
    }

    public static boolean repair(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable) || damageable.getDamage() <= 0) {
            return false;
        }
        Component name = meta.displayName();
        List<Component> lore = meta.lore();
        boolean hadCmd = meta.hasCustomModelData();
        int cmd = hadCmd ? meta.getCustomModelData() : 0;
        damageable.setDamage(0);
        if (name != null) {
            meta.displayName(name);
        }
        if (lore != null) {
            meta.lore(lore);
        }
        if (hadCmd) {
            meta.setCustomModelData(cmd);
        }
        item.setItemMeta(meta);
        return true;
    }
}
