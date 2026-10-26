package dev.grzybalson.gui;

import dev.grzybalson.HollyCore;
import dev.grzybalson.modules.infogui.InfoGuiDefinition;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.HeadUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class InfoGuiInventory implements InventoryHolder {

    private final HollyCore plugin;
    private final Player player;
    private final InfoGuiDefinition definition;
    private Inventory inventory;

    public InfoGuiInventory(HollyCore plugin, Player player, InfoGuiDefinition definition) {
        this.plugin = plugin;
        this.player = player;
        this.definition = definition;
    }

    public void open() {
        int size = definition.getRows() * 9;
        inventory = Bukkit.createInventory(this, size, ColorUtils.colorizeComponent(definition.getTitle()));
        plugin.getGuiFactory().applyDefaultTemplate(inventory);

        for (InfoGuiDefinition.Entry entry : definition.getEntriesList()) {
            if (entry.slot() < 0 || entry.slot() >= size) {
                continue;
            }
            inventory.setItem(entry.slot(), createItem(entry));
        }

        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
    }

    private ItemStack createItem(InfoGuiDefinition.Entry entry) {
        ItemStack item = resolveIcon(entry.icon());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.displayName(ColorUtils.colorizeComponent(entry.name()).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        for (String line : entry.lore()) {
            if (ColorUtils.isBlankLoreLine(line)) {
                continue;
            }
            lore.addAll(ColorUtils.colorizeLore(line));
        }
        if (!lore.isEmpty()) {
            meta.lore(lore);
        }
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack resolveIcon(String iconValue) {
        if (iconValue == null || iconValue.isBlank()) {
            return new ItemStack(Material.CHEST);
        }

        try {
            Material material = Material.valueOf(iconValue.trim().toUpperCase());
            return new ItemStack(material);
        } catch (IllegalArgumentException ignored) {
        }

        if (iconValue.length() > 30) {
            return HeadUtils.createHead(iconValue);
        }

        return new ItemStack(Material.CHEST);
    }

    public Player getPlayer() {
        return player;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
