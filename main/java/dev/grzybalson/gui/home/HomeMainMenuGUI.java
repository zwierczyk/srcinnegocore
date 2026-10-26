package dev.grzybalson.gui.home;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.modules.home.HomeConfig;
import dev.grzybalson.modules.home.Home;
import dev.grzybalson.modules.home.HomeManager;
import dev.grzybalson.modules.home.PlayerHomesData;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HomeMainMenuGUI implements InventoryHolder {

    private final HollyCore plugin;
    private final Player player;
    private final HomeManager homeManager;
    private final Map<Integer, Integer> slotToHomeNumber = new HashMap<>();
    private Inventory inventory;

    public HomeMainMenuGUI(HollyCore plugin, Player player, HomeManager homeManager) {
        this.plugin = plugin;
        this.player = player;
        this.homeManager = homeManager;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public void open() {
        HomeConfig.MainMenuSection config = homeManager.getConfig().getGui().getMainMenu();
        inventory = Bukkit.createInventory(this, config.getSize(), ColorUtils.colorizeComponent(config.getTitle()));

        if (plugin.getGuiFactory() != null) {
            plugin.getGuiFactory().applyDefaultTemplate(inventory);
        }

        // Close button
        HomeConfig.GuiItemConfig closeBtn = config.getCloseButton();
        if (closeBtn.getSlot() >= 0 && closeBtn.getSlot() < inventory.getSize()) {
            inventory.setItem(closeBtn.getSlot(), createGuiItem(closeBtn, null, -1));
        }

        PlayerHomesData data = homeManager.getPlayerHomes(player);
        Map<String, HomeConfig.HomeSlotSection> slotConfigs = homeManager.getConfig().getHomeSlots();

        for (int i = 1; i <= 7; i++) {
            HomeConfig.HomeSlotSection slotConfig = slotConfigs.get("slot" + i);
            if (slotConfig == null) continue;

            int guiSlot = slotConfig.getGuiSlot();
            int homeNum = slotConfig.getHomeNumber();
            slotToHomeNumber.put(guiSlot, homeNum);

            boolean hasPerm = homeManager.canAccessSlot(player, homeNum);
            Home home = data.getHome(homeNum);

            ItemStack item;
            if (!hasPerm) {
                item = createGuiItem(config.getNoPermissionItem(), null, homeNum);
            } else if (home == null) {
                item = createGuiItem(config.getUnsetItem(), null, homeNum);
            } else {
                HomeConfig.GuiItemConfig setItemConfig = home.isShowCoordinates()
                    ? config.getSetItemWithCoords()
                    : config.getSetItemWithoutCoords();
                item = createGuiItem(setItemConfig, home, homeNum);
            }

            if (guiSlot >= 0 && guiSlot < inventory.getSize()) {
                inventory.setItem(guiSlot, item);
            }
        }

        player.openInventory(inventory);
        homeManager.playSound(player, homeManager.getConfig().getSounds().getOpenGui());
    }

    public void handleClick(int slot, boolean isRightClick, boolean isShiftClick) {
        HomeConfig.MainMenuSection config = homeManager.getConfig().getGui().getMainMenu();
        if (slot == config.getCloseButton().getSlot()) {
            player.closeInventory();
            homeManager.playSound(player, homeManager.getConfig().getSounds().getGuiClick());
            return;
        }

        Integer homeNum = slotToHomeNumber.get(slot);
        if (homeNum == null) return;

        boolean hasPerm = homeManager.canAccessSlot(player, homeNum);
        if (!hasPerm) {
            homeManager.playSound(player, homeManager.getConfig().getSounds().getNoPermission());
            return;
        }

        PlayerHomesData data = homeManager.getPlayerHomes(player);
        Home home = data.getHome(homeNum);

        if (home == null) {
            // Left or right click on unset slot -> set home at current location
            if (homeManager.setHome(player, homeNum, null)) {
                open(); // refresh GUI
            }
        } else {
            if (isRightClick) {
                // Open edit menu
                homeManager.playSound(player, homeManager.getConfig().getSounds().getGuiClick());
                new HomeEditMenuGUI(plugin, player, homeManager, homeNum).open();
            } else {
                // Teleport
                player.closeInventory();
                homeManager.teleportToHome(player, homeNum);
            }
        }
    }

    private ItemStack createGuiItem(HomeConfig.GuiItemConfig itemConfig, Home home, int homeNumber) {
        String matStr = itemConfig.getMaterial();
        if (matStr.contains("{CURRENT_ICON}")) {
            matStr = home != null ? home.getIconMaterial() : "WHITE_BED";
        }
        Material mat;
        try {
            mat = Material.valueOf(matStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            mat = Material.WHITE_BED;
        }

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String name = itemConfig.getName()
            .replace("{HOME_NUMBER}", String.valueOf(homeNumber))
            .replace("{NUMBER}", String.valueOf(homeNumber));
        if (home != null && home.getName() != null) {
            name = name.replace("{NAME}", home.getName());
        }
        meta.displayName(ColorUtils.colorizeComponent(name).decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        if (itemConfig.getLore() != null) {
            for (String line : itemConfig.getLore()) {
                String processed = line
                    .replace("{HOME_NUMBER}", String.valueOf(homeNumber))
                    .replace("{NUMBER}", String.valueOf(homeNumber));
                if (home != null) {
                    processed = processed
                        .replace("{X}", String.format(java.util.Locale.US, "%.1f", home.getX()))
                        .replace("{Y}", String.format(java.util.Locale.US, "%.1f", home.getY()))
                        .replace("{Z}", String.format(java.util.Locale.US, "%.1f", home.getZ()))
                        .replace("{WORLD}", home.getWorld());
                }
                lore.add(ColorUtils.colorizeComponent(processed).decoration(TextDecoration.ITALIC, false));
            }
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
