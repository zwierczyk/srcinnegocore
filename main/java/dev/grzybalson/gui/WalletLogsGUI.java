package dev.grzybalson.gui;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.configs.WalletConfig;
import dev.grzybalson.database.WalletDataManager;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class WalletLogsGUI implements InventoryHolder {

    private final HollyCore plugin;
    private final org.bukkit.entity.Player viewer;
    private final OfflinePlayer target;
    private final List<WalletDataManager.WalletLog> logs;
    private final Map<Integer, Runnable> clickActions = new HashMap<>();
    private Inventory inventory;
    private int page = 0;

    public WalletLogsGUI(HollyCore plugin, org.bukkit.entity.Player viewer, OfflinePlayer target, List<WalletDataManager.WalletLog> logs) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.target = target;
        this.logs = logs;
    }

    public void open() {
        build();
        viewer.openInventory(inventory);
    }

    private void build() {
        WalletConfig.LogsGui config = plugin.getConfigManager().getWalletConfig().getLogsGui();
        clickActions.clear();
        inventory = Bukkit.createInventory(this, plugin.getGuiFactory().getInventorySize(config.getRows()),
            ColorUtils.colorizeComponent(config.getTitle().replace("{PLAYER}", target.getName() != null ? target.getName() : target.getUniqueId().toString())));
        plugin.getGuiFactory().applyDefaultTemplate(inventory);

        int[] contentSlots = plugin.getGuiFactory().getContentSlots(inventory);
        int startIndex = page * contentSlots.length;

        SimpleDateFormat format = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.US);
        for (int i = 0; i < contentSlots.length; i++) {
            int dataIndex = startIndex + i;
            if (dataIndex >= logs.size()) {
                break;
            }
            WalletDataManager.WalletLog log = logs.get(dataIndex);
            inventory.setItem(contentSlots[i], createLogItem(log, format));
        }

        ItemStack previous = navItem(Material.ARROW, "&cPoprzednia strona");
        ItemStack next = navItem(Material.ARROW, "&aNastępna strona");
        ItemStack back = navItem(Material.BARRIER, "&cZamknij");

        int previousSlot = resolveBottomSlot(config.getPreviousSlot(), 3);
        int nextSlot = resolveBottomSlot(config.getNextSlot(), 5);
        int backSlot = resolveBottomSlot(config.getBackSlot(), 4);

        plugin.getGuiFactory().setItemIfInBounds(inventory, previousSlot, previous);
        plugin.getGuiFactory().setItemIfInBounds(inventory, nextSlot, next);
        plugin.getGuiFactory().setItemIfInBounds(inventory, backSlot, back);

        clickActions.put(previousSlot, () -> {
            if (page > 0) {
                page--;
                build();
                viewer.openInventory(inventory);
            }
        });
        clickActions.put(nextSlot, () -> {
            if ((page + 1) * contentSlots.length < logs.size()) {
                page++;
                build();
                viewer.openInventory(inventory);
            }
        });
        clickActions.put(backSlot, viewer::closeInventory);
    }

    private int resolveBottomSlot(int configuredSlot, int fallbackColumn) {
        return plugin.getGuiFactory().resolveSlot(
            inventory,
            configuredSlot,
            plugin.getGuiFactory().bottomRowSlot(inventory, fallbackColumn)
        );
    }

    private ItemStack createLogItem(WalletDataManager.WalletLog log, SimpleDateFormat format) {
        String action = log.getAction() == null ? "" : log.getAction();
        Material material = switch (action) {
            case "PURCHASE" -> Material.LIME_DYE;
            case "RECHARGE" -> Material.EMERALD;
            case "ADMIN_REMOVE" -> Material.REDSTONE;
            case "ADMIN_SET" -> Material.COMPARATOR;
            default -> Material.PAPER;
        };

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String actor = log.getActorName() == null || log.getActorName().isBlank() ? "brak" : log.getActorName();
            meta.displayName(ColorUtils.colorizeComponent("&e" + prettyAction(action) + " &8• &f" + actor));
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            ColorUtils.appendLore(lore, "&8» &7Źródło: &f" + safe(log.getSource()));
            ColorUtils.appendLore(lore, "&8» &7Kwota: &a" + String.format(Locale.US, "%.2f", log.getAmount()) + " " + plugin.getConfigManager().getWalletConfig().getCurrencyName());
            ColorUtils.appendLore(lore, "&8» &7Po operacji: &f" + String.format(Locale.US, "%.2f", log.getBalanceAfter()) + " " + plugin.getConfigManager().getWalletConfig().getCurrencyName());
            if (log.getDetail() != null && !log.getDetail().isBlank()) {
                ColorUtils.appendLore(lore, "&8» &7Szczegóły: &f" + log.getDetail());
            }
            ColorUtils.appendLore(lore, "&8» &7Data: &f" + format.format(new Date(log.getTimestamp())));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack navItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(ColorUtils.colorizeComponent(name));
            item.setItemMeta(meta);
        }
        return item;
    }

    private String prettyAction(String action) {
        return switch (action) {
            case "PURCHASE" -> "Zakup";
            case "RECHARGE" -> "Doładowanie";
            case "ADMIN_ADD" -> "Dodanie";
            case "ADMIN_REMOVE" -> "Usunięcie";
            case "ADMIN_SET" -> "Ustawienie";
            default -> action;
        };
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "brak" : value;
    }

    public void handleClick(int rawSlot) {
        Runnable action = clickActions.get(rawSlot);
        if (action != null) {
            action.run();
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
