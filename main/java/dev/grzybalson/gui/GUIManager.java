package dev.grzybalson.gui;

import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ColorUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class GUIManager {

    private final HollyCore plugin;

    public GUIManager(HollyCore plugin) {
        this.plugin = plugin;
    }

    public Inventory createGUI(String title, int rows, InventoryHolder holder) {
        int normalizedRows = plugin.getConfigManager().getGuiConfig().normalizeRows(rows);
        int size = normalizedRows * 9;
        Inventory inventory = Bukkit.createInventory(holder, size, ColorUtils.colorizeComponent(title));
        fillBackground(inventory, normalizedRows);
        return inventory;
    }

    public void fillBackground(Inventory inventory, int rows) {
        List<String> pattern = plugin.getConfigManager().getGuiConfig().getBackgroundForRows(rows);
        String backgroundName = plugin.getConfigManager().getGuiConfig().getBackgroundItemName();

        int slot = 0;
        for (String row : pattern) {
            for (char c : row.toCharArray()) {
                if (slot >= inventory.getSize()) break;

                ItemStack item = getBackgroundItem(c, backgroundName);
                if (item != null) {
                    inventory.setItem(slot, item);
                }
                slot++;
            }
        }
    }

    private ItemStack getBackgroundItem(char type, String name) {
        Material material = switch (type) {
            case 'F' -> Material.FLINT;
            case 'G' -> Material.GRAY_STAINED_GLASS_PANE;
            case 'W' -> Material.WRITABLE_BOOK;
            case 'X' -> null;
            default -> null;
        };

        if (material == null) {
            return null;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(ColorUtils.colorizeComponent(name).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            item.setItemMeta(meta);
        }
        return item;
    }

    public static boolean isBackgroundItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;

        Material type = item.getType();
        return type == Material.FLINT ||
               type == Material.GRAY_STAINED_GLASS_PANE ||
               type == Material.WRITABLE_BOOK;
    }
}
