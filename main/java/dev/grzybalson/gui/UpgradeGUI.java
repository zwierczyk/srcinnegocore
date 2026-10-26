package dev.grzybalson.gui;

import dev.grzybalson.HollyCore;
import dev.grzybalson.modules.upgrade.UpgradeType;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.NBTUtils;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class UpgradeGUI implements InventoryHolder {

    private final HollyCore plugin;
    private final Player player;
    private Inventory inventory;

    public UpgradeGUI(HollyCore plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public void open() {
        if (plugin.getUpgradeManager() != null) {
            plugin.getUpgradeManager().updatePlayerShears(player);
        }
        inventory = Bukkit.createInventory(this, plugin.getUpgradeManager().getGuiSize(),
            ColorUtils.colorizeComponent(plugin.getUpgradeManager().getGuiTitle()));
        plugin.getGuiFactory().applyDefaultTemplate(inventory);

        refreshContents();

        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
    }

    public void refreshContents() {
        if (inventory == null) {
            return;
        }

        if (plugin.getUpgradeManager() != null) {
            plugin.getUpgradeManager().updatePlayerShears(player);
        }

        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack current = inventory.getItem(i);
            if (current != null && !plugin.getGuiFactory().isBackgroundItem(current)) {
                inventory.setItem(i, null);
            }
        }
        plugin.getGuiFactory().applyDefaultTemplate(inventory);

        Map<UpgradeType, ItemStack> previews = plugin.getUpgradeManager().getMainGuiPreviews(player);
        Map<UpgradeType, Integer> typeSlots = plugin.getUpgradeManager().getMainGuiTypeSlots();
        Map<Integer, ItemStack> slotItems = new HashMap<>();
        for (Map.Entry<UpgradeType, Integer> entry : typeSlots.entrySet()) {
            UpgradeType type = entry.getKey();
            int slot = entry.getValue();
            ItemStack preview = previews.get(type);
            if (preview != null) {
                slotItems.put(slot, preview);
            }
        }
        for (Map.Entry<Integer, ItemStack> entry : slotItems.entrySet()) {
            inventory.setItem(entry.getKey(), entry.getValue());
        }
    }

    public void handleClick(int slot, boolean rightClick) {
        ItemStack clicked = inventory.getItem(slot);
        if (clicked == null || plugin.getGuiFactory().isBackgroundItem(clicked)) {
            return;
        }

        UpgradeType type = UpgradeType.fromArgument(NBTUtils.getString(clicked, "upgrade_gui_type"));
        if (type == null) {
            return;
        }

        if (rightClick) {
            new UpgradeLevelsGUI(plugin, player, type).open();
            return;
        }

        plugin.getUpgradeManager().upgradeHeldItem(player, type);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
