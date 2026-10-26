package dev.grzybalson.gui.home;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.modules.home.HomeConfig;
import dev.grzybalson.modules.home.Home;
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

public class HomeEditMenuGUI implements InventoryHolder {

    private final HollyCore plugin;
    private final Player player;
    private final HomeManager homeManager;
    private final int homeNumber;
    private Inventory inventory;

    public HomeEditMenuGUI(HollyCore plugin, Player player, HomeManager homeManager, int homeNumber) {
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
        HomeConfig.EditMenuSection config = homeManager.getConfig().getGui().getEditMenu();
        String title = config.getTitle().replace("{NUMBER}", String.valueOf(homeNumber));
        inventory = Bukkit.createInventory(this, config.getSize(), ColorUtils.colorizeComponent(title));

        if (plugin.getGuiFactory() != null) {
            plugin.getGuiFactory().applyDefaultTemplate(inventory);
        }

        Home home = homeManager.getPlayerHomes(player).getHome(homeNumber);

        // Icon button (slot 21)
        HomeConfig.GuiItemConfig iconBtn = config.getIconButton();
        if (iconBtn.getSlot() >= 0 && iconBtn.getSlot() < inventory.getSize()) {
            inventory.setItem(iconBtn.getSlot(), createItem(iconBtn, home));
        }

        // Coordinates button (slot 22)
        HomeConfig.CoordinatesButtonConfig coordsBtn = config.getCoordinatesButton();
        if (coordsBtn.getSlot() >= 0 && coordsBtn.getSlot() < inventory.getSize()) {
            inventory.setItem(coordsBtn.getSlot(), createCoordsItem(coordsBtn, home));
        }

        // Delete button (slot 23)
        HomeConfig.GuiItemConfig deleteBtn = config.getDeleteButton();
        if (deleteBtn.getSlot() >= 0 && deleteBtn.getSlot() < inventory.getSize()) {
            inventory.setItem(deleteBtn.getSlot(), createItem(deleteBtn, home));
        }

        // Back button (slot 40)
        HomeConfig.GuiItemConfig backBtn = config.getBackButton();
        if (backBtn.getSlot() >= 0 && backBtn.getSlot() < inventory.getSize()) {
            inventory.setItem(backBtn.getSlot(), createItem(backBtn, home));
        }

        player.openInventory(inventory);
    }

    public void handleClick(int slot, boolean isRightClick) {
        HomeConfig.EditMenuSection config = homeManager.getConfig().getGui().getEditMenu();

        if (slot == config.getBackButton().getSlot()) {
            homeManager.playSound(player, homeManager.getConfig().getSounds().getGuiClick());
            new HomeMainMenuGUI(plugin, player, homeManager).open();
            return;
        }

        if (slot == config.getIconButton().getSlot()) {
            homeManager.playSound(player, homeManager.getConfig().getSounds().getGuiClick());
            new HomeIconPickerGUI(plugin, player, homeManager, homeNumber).open();
            return;
        }

        if (slot == config.getCoordinatesButton().getSlot()) {
            homeManager.toggleCoordinates(player, homeNumber);
            open(); // refresh GUI
            return;
        }

        if (slot == config.getDeleteButton().getSlot()) {
            homeManager.playSound(player, homeManager.getConfig().getSounds().getGuiClick());
            new HomeDeleteConfirmGUI(plugin, player, homeManager, homeNumber).open();
        }
    }

    private ItemStack createItem(HomeConfig.GuiItemConfig itemConfig, Home home) {
        Material mat;
        try {
            mat = Material.valueOf(itemConfig.getMaterial().toUpperCase());
        } catch (IllegalArgumentException e) {
            mat = Material.STONE;
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

    private ItemStack createCoordsItem(HomeConfig.CoordinatesButtonConfig itemConfig, Home home) {
        Material mat;
        try {
            mat = Material.valueOf(itemConfig.getMaterial().toUpperCase());
        } catch (IllegalArgumentException e) {
            mat = Material.IRON_NUGGET;
        }

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        boolean showCoords = home != null && home.isShowCoordinates();
        String status = showCoords ? itemConfig.getStatusEnabled() : itemConfig.getStatusDisabled();

        String name = itemConfig.getName().replace("{NUMBER}", String.valueOf(homeNumber));
        meta.displayName(ColorUtils.colorizeComponent(name).decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        if (itemConfig.getLore() != null) {
            for (String line : itemConfig.getLore()) {
                String processed = line
                    .replace("{NUMBER}", String.valueOf(homeNumber))
                    .replace("{STATUS}", status);
                lore.add(ColorUtils.colorizeComponent(processed).decoration(TextDecoration.ITALIC, false));
            }
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
