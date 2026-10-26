package dev.grzybalson.gui;

import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class BackgroundProvider {

    private static final Set<Integer> CORNERS_27 = new HashSet<>(Arrays.asList(0, 8, 18, 26));
    private static final Set<Integer> CORNERS_45 = new HashSet<>(Arrays.asList(0, 8, 36, 44));
    private static final Set<Integer> CORNERS_54 = new HashSet<>(Arrays.asList(0, 8, 45, 53));

    private static final int BOOK_SLOT = 4;

    public static void fillBackground(Inventory inventory) {
        int size = inventory.getSize();
        Set<Integer> corners = getCorners(size);

        for (int i = 0; i < size; i++) {
            if (corners.contains(i)) {
                inventory.setItem(i, createBackgroundItem(Material.FLINT));
            } else if (i == BOOK_SLOT) {
                inventory.setItem(i, createBackgroundItem(Material.WRITABLE_BOOK));
            } else if (size == 54 && i >= 45) {
                inventory.setItem(i, createBackgroundItem(Material.CRIMSON_BUTTON));
            } else if (i % 9 == 0 || i % 9 == 8) {
                inventory.setItem(i, createBackgroundItem(Material.RED_STAINED_GLASS_PANE));
            } else {
                inventory.setItem(i, createBackgroundItem(Material.WHITE_STAINED_GLASS_PANE));
            }
        }
    }

    public static void fillBackgroundExcept(Inventory inventory, Set<Integer> excludedSlots) {
        int size = inventory.getSize();
        Set<Integer> corners = getCorners(size);

        for (int i = 0; i < size; i++) {
            if (excludedSlots.contains(i)) continue;

            if (corners.contains(i)) {
                inventory.setItem(i, createBackgroundItem(Material.FLINT));
            } else if (i == BOOK_SLOT) {
                inventory.setItem(i, createBackgroundItem(Material.WRITABLE_BOOK));
            } else if (size == 54 && i >= 45) {
                inventory.setItem(i, createBackgroundItem(Material.CRIMSON_BUTTON));
            } else if (i % 9 == 0 || i % 9 == 8) {
                inventory.setItem(i, createBackgroundItem(Material.RED_STAINED_GLASS_PANE));
            } else {
                inventory.setItem(i, createBackgroundItem(Material.WHITE_STAINED_GLASS_PANE));
            }
        }
    }

    private static Set<Integer> getCorners(int size) {
        return switch (size) {
            case 27 -> CORNERS_27;
            case 45 -> CORNERS_45;
            case 54 -> CORNERS_54;
            default -> new HashSet<>();
        };
    }

    private static ItemStack createBackgroundItem(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtils.colorize("&r&c"));
            meta.setLore(null);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static boolean isBackgroundItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return false;
        String name = meta.getDisplayName();
        return name.equals(ColorUtils.colorize("&r&c"));
    }
}
