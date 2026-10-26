package dev.grzybalson.gui;

import dev.grzybalson.HollyCore;
import dev.grzybalson.modules.upgrade.UpgradeManager;
import dev.grzybalson.modules.upgrade.UpgradeType;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.NBTUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class UpgradeLevelsGUI implements InventoryHolder {

    private final HollyCore plugin;
    private final Player player;
    private final UpgradeType type;
    private Inventory inventory;
    private int page;

    public UpgradeLevelsGUI(HollyCore plugin, Player player, UpgradeType type) {
        this(plugin, player, type, 0);
    }

    public UpgradeLevelsGUI(HollyCore plugin, Player player, UpgradeType type, int page) {
        this.plugin = plugin;
        this.player = player;
        this.type = type;
        this.page = Math.max(0, page);
    }

    public void open() {
        inventory = Bukkit.createInventory(this, plugin.getUpgradeManager().getGuiSize(),
            ColorUtils.colorizeComponent(plugin.getUpgradeManager().getLevelsTitle(type)));
        refresh();
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
    }

    private void refresh() {
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, null);
        }
        plugin.getGuiFactory().applyDefaultTemplate(inventory);

        List<Integer> levelSlots = plugin.getUpgradeManager().getLevelSlots();
        List<ItemStack> displayItems = buildDisplayItems();
        int pageSize = Math.max(1, levelSlots.size());
        int totalPages = Math.max(1, (int) Math.ceil(displayItems.size() / (double) pageSize));
        if (page >= totalPages) {
            page = totalPages - 1;
        }

        int start = page * pageSize;
        int write = 0;
        for (int i = start; i < displayItems.size() && write < pageSize; i++) {
            ItemStack item = displayItems.get(i);
            if (UpgradeManager.isBlankItem(item)) {
                continue;
            }
            int slot = levelSlots.get(write++);
            if (slot >= 0 && slot < inventory.getSize()) {
                inventory.setItem(slot, item);
            }
        }

        java.util.Set<Integer> occupied = new java.util.HashSet<>(levelSlots);
        int backSlot = resolveActionSlot(plugin.getUpgradeManager().getBackSlot(), 4, occupied);
        if (backSlot >= 0) {
            occupied.add(backSlot);
            inventory.setItem(backSlot, plugin.getUpgradeManager().createBackItem());
        }

        int prevSlot = resolveActionSlot(plugin.getUpgradeManager().getPrevPageSlot(), 3, occupied);
        if (prevSlot >= 0) {
            occupied.add(prevSlot);
        }
        int nextSlot = resolveActionSlot(plugin.getUpgradeManager().getNextPageSlot(), 5, occupied);
        if (nextSlot >= 0) {
            occupied.add(nextSlot);
        }

        if (totalPages > 1) {
            if (page > 0 && prevSlot >= 0) {
                inventory.setItem(prevSlot, pageItem(true, page, totalPages));
            }
            if (page < totalPages - 1 && nextSlot >= 0) {
                inventory.setItem(nextSlot, pageItem(false, page, totalPages));
            }
        }
    }

    private int resolveActionSlot(int configuredSlot, int fallbackColumn, java.util.Set<Integer> occupied) {
        if (configuredSlot >= 0 && configuredSlot < inventory.getSize() && !occupied.contains(configuredSlot)) {
            return configuredSlot;
        }

        int[] columns = {fallbackColumn, 3, 5, 2, 6, 1, 7, 0, 8};
        for (int column : columns) {
            int slot = plugin.getGuiFactory().bottomRowSlot(inventory, column);
            if (slot >= 0 && slot < inventory.getSize() && !occupied.contains(slot)) {
                return slot;
            }
        }
        return -1;
    }

    /**
     * Buduje listę itemów do wyświetlenia — pomija puste/AIR, bez dziur w siatce.
     */
    private List<ItemStack> buildDisplayItems() {
        List<Integer> configured = plugin.getUpgradeManager().getConfiguredLevels(type);
        List<ItemStack> items = new ArrayList<>(configured.size());
        for (int level : configured) {
            ItemStack item = plugin.getUpgradeManager().createGuiItem(type, level, false);
            if (!UpgradeManager.isBlankItem(item)) {
                items.add(item);
            }
        }
        return items;
    }

    private ItemStack pageItem(boolean previous, int currentPage, int totalPages) {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (previous) {
                meta.setDisplayName(ColorUtils.colorize(plugin.getUpgradeManager().getPrevPageName()));
            } else {
                meta.setDisplayName(ColorUtils.colorize(plugin.getUpgradeManager().getNextPageName()));
            }
            meta.setLore(List.of(ColorUtils.colorize(
                "&7Strona &f" + (currentPage + 1) + "&7/&f" + totalPages
            )));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        NBTUtils.setString(item, "upgrade_action", previous ? "prev_page" : "next_page");
        return item;
    }

    public void handleClick(int slot) {
        ItemStack clicked = inventory.getItem(slot);
        if (clicked == null || plugin.getGuiFactory().isBackgroundItem(clicked)) {
            return;
        }

        String action = NBTUtils.getString(clicked, "upgrade_action");
        if ("back".equals(action)) {
            new UpgradeGUI(plugin, player).open();
            return;
        }
        if ("prev_page".equals(action)) {
            if (page > 0) {
                page--;
                refresh();
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            }
            return;
        }
        if ("next_page".equals(action)) {
            page++;
            refresh();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
