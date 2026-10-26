package dev.grzybalson.gui;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.configs.ChatConfig;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.NBTUtils;
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

import java.util.List;

public class PlayerActionGUI implements InventoryHolder {

    private final HollyCore plugin;
    private final Player viewer;
    private final String targetName;
    private Inventory inventory;

    public PlayerActionGUI(HollyCore plugin, Player viewer, String targetName) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.targetName = targetName;
    }

    public void open() {
        ChatConfig config = plugin.getConfigManager().getChatConfig();
        String title = config.getPlayerActionGuiTitle().replace("{player}", targetName);

        inventory = Bukkit.createInventory(this, config.getPlayerActionGuiRows() * 9,
            ColorUtils.colorizeComponent(title));

        plugin.getGuiFactory().applyDefaultTemplate(inventory);

        // TPA button
        ItemStack tpaItem = new ItemStack(Material.valueOf(config.getTpaButtonMaterial()));
        ItemMeta tpaMeta = tpaItem.getItemMeta();
        if (tpaMeta != null) {
            tpaMeta.displayName(ColorUtils.colorizeComponent(config.getTpaButtonName()).decoration(TextDecoration.ITALIC, false));
            List<Component> tpaLore = new java.util.ArrayList<>();
            tpaLore.add(Component.empty());
            ColorUtils.appendLore(tpaLore, config.getTpaButtonLore());
            tpaMeta.lore(tpaLore);
            if (config.getTpaButtonCustomModelData() > 0) {
                tpaMeta.setCustomModelData(config.getTpaButtonCustomModelData());
            }
            tpaItem.setItemMeta(tpaMeta);
        }
        NBTUtils.setString(tpaItem, "player_action", "tpa");
        NBTUtils.setString(tpaItem, "player_target", targetName);
        inventory.setItem(config.getTpaButtonSlot(), tpaItem);

        // Cancel button
        ItemStack cancelItem = new ItemStack(Material.valueOf(config.getCancelButtonMaterial()));
        ItemMeta cancelMeta = cancelItem.getItemMeta();
        if (cancelMeta != null) {
            cancelMeta.displayName(ColorUtils.colorizeComponent(config.getCancelButtonName()).decoration(TextDecoration.ITALIC, false));
            List<Component> cancelLore = new java.util.ArrayList<>();
            cancelLore.add(Component.empty());
            ColorUtils.appendLore(cancelLore, config.getCancelButtonLore());
            cancelMeta.lore(cancelLore);
            if (config.getCancelButtonCustomModelData() > 0) {
                cancelMeta.setCustomModelData(config.getCancelButtonCustomModelData());
            }
            cancelItem.setItemMeta(cancelMeta);
        }
        NBTUtils.setString(cancelItem, "player_action", "cancel");
        inventory.setItem(config.getCancelButtonSlot(), cancelItem);

        viewer.openInventory(inventory);
    }

    public void handleClick(int slot) {
        ItemStack clicked = inventory.getItem(slot);
        if (clicked == null || plugin.getGuiFactory().isBackgroundItem(clicked)) return;

        String action = NBTUtils.getString(clicked, "player_action");
        if (action == null) return;

        switch (action) {
            case "tpa" -> {
                viewer.closeInventory();
                Bukkit.getScheduler().runTask(plugin, () ->
                    Bukkit.dispatchCommand(viewer, "tpa " + targetName)
                );
                ChatConfig config = plugin.getConfigManager().getChatConfig();
                viewer.sendMessage(ColorUtils.colorizeComponent(
                    config.getTpaSentFromGui().replace("{player}", targetName)
                ));
                viewer.playSound(viewer.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.0f);
            }
            case "cancel" -> {
                viewer.closeInventory();
                viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            }
        }
    }

    public Player getViewer() {
        return viewer;
    }

    public String getTargetName() {
        return targetName;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
