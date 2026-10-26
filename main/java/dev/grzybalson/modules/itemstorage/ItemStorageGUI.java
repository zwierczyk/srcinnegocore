package dev.grzybalson.modules.itemstorage;

import dev.grzybalson.HollyCore;
import dev.grzybalson.anarchiagg.util.SoundsUtil;
import dev.grzybalson.utils.ColorUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ItemStorageGUI implements InventoryHolder {

    private final Inventory inventory;

    public ItemStorageGUI(HollyCore plugin) {
        ItemStorageManager manager = plugin.getItemStorageManager();
        int size = 54;
        this.inventory = Bukkit.createInventory(
            this,
            size,
            Component.text(ColorUtils.colorize("&8Item Storage"))
        );

        if (manager == null) {
            return;
        }

        int slot = 0;
        for (Map.Entry<String, ItemStack> entry : manager.getItems().entrySet()) {
            if (slot >= size) {
                break;
            }
            ItemStack display = entry.getValue().clone();
            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
                lore.add(Component.empty());
                lore.add(ColorUtils.createLoreComponent("&8Klucz: &f" + entry.getKey()));
                lore.add(ColorUtils.createLoreComponent("&7Kliknij, aby wziac item"));
                meta.lore(lore);
                display.setItemMeta(meta);
            }
            inventory.setItem(slot++, display);
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void open(Player player) {
        player.openInventory(inventory);
        SoundsUtil.gui(player);
    }
}
