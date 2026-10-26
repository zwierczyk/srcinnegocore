package dev.grzybalson.gui.home;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.modules.home.HomeConfig;
import dev.grzybalson.modules.home.HomeManager;
import dev.grzybalson.utils.ColorUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class HomeDeleteConfirmGUI implements InventoryHolder {

    private final HollyCore plugin;
    private final Player player;
    private final HomeManager homeManager;
    private final int homeNumber;
    private Inventory inventory;

    public HomeDeleteConfirmGUI(HollyCore plugin, Player player, HomeManager homeManager, int homeNumber) {
        this.plugin = plugin;
        this.player = player;
        this.homeManager = homeManager;
        this.homeNumber = homeNumber;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public void open() {
        HomeConfig.DeleteConfirmMenuSection config = homeManager.getConfig().getGui().getDeleteConfirmMenu();
        String title = config.getTitle().replace("{NUMBER}", String.valueOf(homeNumber));
        inventory = Bukkit.createInventory(this, config.getSize(), ColorUtils.colorizeComponent(title));

        if (plugin.getGuiFactory() != null) {
            plugin.getGuiFactory().applyDefaultTemplate(inventory);
        }

        // Confirm button (slot 20)
        HomeConfig.GuiItemConfig confirmBtn = config.getConfirmButton();
        if (confirmBtn.getSlot() >= 0 && confirmBtn.getSlot() < inventory.getSize()) {
            inventory.setItem(confirmBtn.getSlot(), createItem(confirmBtn));
        }

        // Cancel button (slot 24)
        HomeConfig.GuiItemConfig cancelBtn = config.getCancelButton();
        if (cancelBtn.getSlot() >= 0 && cancelBtn.getSlot() < inventory.getSize()) {
            inventory.setItem(cancelBtn.getSlot(), createItem(cancelBtn));
        }

        // Back button (slot 40)
        HomeConfig.GuiItemConfig backBtn = config.getBackButton();
        if (backBtn.getSlot() >= 0 && backBtn.getSlot() < inventory.getSize()) {
            inventory.setItem(backBtn.getSlot(), createItem(backBtn));
        }

        player.openInventory(inventory);
    }

    public void handleClick(int slot) {
        HomeConfig.DeleteConfirmMenuSection config = homeManager.getConfig().getGui().getDeleteConfirmMenu();

        if (slot == config.getConfirmButton().getSlot()) {
            homeManager.deleteHome(player, homeNumber);
            new HomeMainMenuGUI(plugin, player, homeManager).open();
            return;
        }

        if (slot == config.getCancelButton().getSlot() || slot == config.getBackButton().getSlot()) {
            homeManager.playSound(player, homeManager.getConfig().getSounds().getGuiClick());
            new HomeEditMenuGUI(plugin, player, homeManager, homeNumber).open();
        }
    }

    private ItemStack createItem(HomeConfig.GuiItemConfig itemConfig) {
        Material mat;
        try {
            mat = Material.valueOf(itemConfig.getMaterial().toUpperCase());
        } catch (IllegalArgumentException e) {
            mat = Material.BARRIER;
        }

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String name = itemConfig.getName().replace("{NUMBER}", String.valueOf(homeNumber));
        meta.displayName(ColorUtils.colorizeComponent(name).decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        if (itemConfig.getLore() != null) {
            for (String line : itemConfig.getLore()) {
                String processed = line.replace("{NUMBER}", String.valueOf(homeNumber));
                lore.add(ColorUtils.colorizeComponent(processed).decoration(TextDecoration.ITALIC, false));
            }
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
