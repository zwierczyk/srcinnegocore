package dev.grzybalson.gui;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.configs.WalletConfig;
import dev.grzybalson.managers.WalletProductStatsService;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.NBTUtils;
import dev.grzybalson.utils.WalletItemFactory;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class WalletConfirmGUI implements InventoryHolder {

    private static final int SIZE = 27;
    private static final int PREVIEW_SLOT = 13;
    private static final int CONFIRM_SLOT = 11;
    private static final int CANCEL_SLOT = 15;

    private final HollyCore plugin;
    private final Player player;
    private final WalletGUI parent;
    private final int serviceSlot;
    private final String serviceName;
    private final WalletConfig.WalletProduct product;
    private final WalletConfig.ServiceCategory service;
    private final Map<String, WalletProductStatsService.ProductStats> statsMap;
    private Inventory inventory;
    private long lastConfirmAt;

    public WalletConfirmGUI(HollyCore plugin, Player player, WalletGUI parent, int serviceSlot, String serviceName,
                            WalletConfig.WalletProduct product, WalletConfig.ServiceCategory service,
                            Map<String, WalletProductStatsService.ProductStats> statsMap) {
        this.plugin = plugin;
        this.player = player;
        this.parent = parent;
        this.serviceSlot = serviceSlot;
        this.serviceName = serviceName;
        this.product = product;
        this.service = service;
        this.statsMap = statsMap == null ? Map.of() : statsMap;
    }

    public void open() {
        inventory = Bukkit.createInventory(this, SIZE, ColorUtils.colorizeComponent("&8Potwierdź zakup"));
        plugin.getGuiFactory().applyDefaultTemplate(inventory);
        inventory.setItem(PREVIEW_SLOT, createPreviewItem());
        inventory.setItem(CONFIRM_SLOT, createActionItem(Material.LIME_WOOL, "&a&lPotwierdź zakup", List.of(
            "&7Usługa: &f" + ColorUtils.stripColors(serviceName),
            "&7Cena: &a" + format(product.getPrice()) + " " + plugin.getConfigManager().getWalletConfig().getCurrencyName(),
            "",
            "&aKliknij, aby kupić."
        ), "confirm"));
        inventory.setItem(CANCEL_SLOT, createActionItem(Material.RED_WOOL, "&c&lAnuluj", List.of(
            "&7Zakup nie zostanie wykonany.",
            "",
            "&cKliknij, aby wrócić."
        ), "cancel"));
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
    }

    public void handleClick(int slot) {
        ItemStack clicked = inventory.getItem(slot);
        if (clicked == null || clicked.getType().isAir() || plugin.getGuiFactory().isBackgroundItem(clicked)) {
            return;
        }
        String action = NBTUtils.getString(clicked, "wallet_confirm_action");
        if (action == null) {
            return;
        }
        if (action.equals("confirm")) {
            // Podwojne klikniecie wysyla dwa pakiety - blokujemy drugi zakup
            long now = System.currentTimeMillis();
            if (now - lastConfirmAt < 750L) {
                return;
            }
            lastConfirmAt = now;
            parent.purchase(serviceName, product, service);
            return;
        }
        if (action.equals("cancel")) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            parent.openService(serviceSlot, service);
        }
    }

    private ItemStack createPreviewItem() {
        double balance = plugin.getWalletManager().getCachedBalance(player.getUniqueId());
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("{PLAYER}", player.getName());
        placeholders.put("{player}", player.getName());
        placeholders.put("{BALANCE}", format(balance));
        placeholders.put("{MONEY}", format(balance));
        placeholders.put("{PRICE}", format(product.getPrice()));
        placeholders.put("{SERVICE}", serviceName);
        placeholders.put("{CURRENCY}", plugin.getConfigManager().getWalletConfig().getCurrencyName());

        ItemStack item = WalletItemFactory.create(product.getItem(), placeholders);
        WalletProductStatsService statsService = plugin.getWalletProductStatsService();
        if (statsService != null && canViewAdminStats()) {
            statsService.appendAdminLore(item, product, statsMap);
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.add(Component.empty());
        lore.add(ColorUtils.colorizeComponent("&8• &7Cena: &a" + format(product.getPrice())));
        lore.add(ColorUtils.colorizeComponent("&8• &7Saldo: &f" + format(balance)));
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private boolean canViewAdminStats() {
        WalletConfig.AdminProductStats settings = plugin.getConfigManager().getWalletConfig().getAdminProductStats();
        if (!settings.isEnabled()) {
            return false;
        }
        String permission = settings.getPermission();
        return permission == null || permission.isBlank() || player.hasPermission(permission);
    }

    private ItemStack createActionItem(Material material, String name, List<String> loreLines, String action) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(ColorUtils.colorizeComponent(name).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            for (String line : loreLines) {
                lore.add(ColorUtils.colorizeComponent(line).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        NBTUtils.setString(item, "wallet_confirm_action", action);
        return item;
    }

    private String format(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
