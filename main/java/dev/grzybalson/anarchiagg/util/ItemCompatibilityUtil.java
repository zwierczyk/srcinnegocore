package dev.grzybalson.anarchiagg.util;

import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class ItemCompatibilityUtil {

    private static final String HIDE_ADDITIONAL_TOOLTIP = "HIDE_ADDITIONAL_TOOLTIP";

    private ItemCompatibilityUtil() {
    }

    public static ItemStack stack(Material material, int amount) {
        Material safeMaterial = material == null || material.isAir() ? Material.STONE : material;
        return new ItemStack(safeMaterial, safeAmount(safeMaterial, amount));
    }

    public static int safeAmount(Material material, int amount) {
        Material safeMaterial = material == null || material.isAir() ? Material.STONE : material;
        return Math.max(1, Math.min(amount, vanillaMaxStack(safeMaterial)));
    }

    public static int vanillaMaxStack(ItemStack item) {
        return item == null ? 64 : vanillaMaxStack(item.getType());
    }

    public static int vanillaMaxStack(Material material) {
        return Math.max(1, material == null || material.isAir() ? 64 : material.getMaxStackSize());
    }

    public static boolean normalizeForClient(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }

        boolean changed = clearCustomMaxStackSize(item);
        int maxStack = vanillaMaxStack(item);
        if (item.getAmount() > maxStack) {
            item.setAmount(maxStack);
            changed = true;
        }
        return changed;
    }

    public static void dropItemNaturally(Player player, ItemStack item) {
        if (player == null || item == null || item.getType().isAir()) {
            return;
        }

        Item droppedItem = player.getWorld().dropItemNaturally(player.getLocation(), item);
        ObfuscatorStackFix.consume(droppedItem);
        if (droppedItem == null) {
            throw new IllegalStateException("dropItemNaturally returned null");
        }
    }

    public static boolean clearCustomMaxStackSize(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        try {
            Object hasCustomMaxStack = meta.getClass().getMethod("hasMaxStackSize").invoke(meta);
            if (Boolean.TRUE.equals(hasCustomMaxStack)) {
                meta.getClass().getMethod("setMaxStackSize", Integer.class).invoke(meta, new Object[]{null});
                item.setItemMeta(meta);
                return true;
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
        return false;
    }

    public static boolean setHideTooltip(ItemStack item, boolean hideTooltip) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        try {
            meta.getClass().getMethod("setHideTooltip", boolean.class).invoke(meta, hideTooltip);
            item.setItemMeta(meta);
            return true;
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
        return false;
    }

    public static boolean hideAttributes(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        boolean changed = !meta.hasItemFlag(ItemFlag.HIDE_ATTRIBUTES);
        addSafeItemFlags(meta, ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return changed;
    }

    public static void addSafeItemFlags(ItemMeta meta, ItemFlag... flags) {
        if (meta == null || flags == null) {
            return;
        }
        for (ItemFlag flag : flags) {
            if (flag != null && !HIDE_ADDITIONAL_TOOLTIP.equals(flag.name())) {
                meta.addItemFlags(flag);
            }
        }
    }

    public static void addAllSafeItemFlags(ItemMeta meta) {
        if (meta == null) {
            return;
        }
        for (ItemFlag flag : ItemFlag.values()) {
            if (!HIDE_ADDITIONAL_TOOLTIP.equals(flag.name())) {
                meta.addItemFlags(flag);
            }
        }
    }
}
