package dev.grzybalson.utils;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;

/**
 * Klient 1.21+ odrzuca pakiety z poziomem enchantu &gt; 255 (np. sharpness 10000 z anarchi).
 * Używaj przed wysłaniem itemów do GUI lub synchronizacji EQ z graczem.
 */
public final class ItemClientSanitizer {

    /** Górny limit poziomu enchantu w protokole MC 1.21. */
    public static final int MAX_CLIENT_ENCHANT_LEVEL = 255;

    private ItemClientSanitizer() {
    }

    public static ItemStack forClient(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return item;
        }
        ItemStack copy = item.clone();
        sanitizeInPlace(copy);
        return copy;
    }

    public static ItemStack[] forClient(ItemStack[] items) {
        if (items == null) {
            return null;
        }
        ItemStack[] sanitized = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) {
            sanitized[i] = items[i] == null ? null : forClient(items[i]);
        }
        return sanitized;
    }

    public static void sanitizeInPlace(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        boolean changed = false;
        if (meta.hasEnchants()) {
            Map<Enchantment, Integer> enchants = new HashMap<>(meta.getEnchants());
            for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
                int safe = clampLevel(entry.getValue());
                if (safe != entry.getValue()) {
                    if (safe <= 0) {
                        meta.removeEnchant(entry.getKey());
                    } else {
                        meta.addEnchant(entry.getKey(), safe, true);
                    }
                    changed = true;
                }
            }
        }

        if (meta instanceof EnchantmentStorageMeta storageMeta && storageMeta.hasStoredEnchants()) {
            Map<Enchantment, Integer> stored = new HashMap<>(storageMeta.getStoredEnchants());
            for (Map.Entry<Enchantment, Integer> entry : stored.entrySet()) {
                int safe = clampLevel(entry.getValue());
                if (safe != entry.getValue()) {
                    if (safe <= 0) {
                        storageMeta.removeStoredEnchant(entry.getKey());
                    } else {
                        storageMeta.addStoredEnchant(entry.getKey(), safe, true);
                    }
                    changed = true;
                }
            }
        }

        if (changed) {
            item.setItemMeta(meta);
        }
    }

    private static int clampLevel(int level) {
        if (level <= 0) {
            return level;
        }
        return Math.min(level, MAX_CLIENT_ENCHANT_LEVEL);
    }
}
