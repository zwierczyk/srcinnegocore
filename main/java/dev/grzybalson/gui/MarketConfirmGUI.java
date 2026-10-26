package dev.grzybalson.gui;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.configs.MarketConfig;
import dev.grzybalson.modules.market.MarketItem;
import dev.grzybalson.modules.market.MarketManager;
import dev.grzybalson.modules.market.MarketViewState;
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

import java.util.ArrayList;
import java.util.List;

public class MarketConfirmGUI implements InventoryHolder {

    private static final int[] CONFIRM_SLOTS = {0, 1, 2, 9, 10, 11, 18, 19, 20};
    private static final int[] CANCEL_SLOTS = {6, 7, 8, 15, 16, 17, 24, 25, 26};

    private final HollyCore plugin;
    private final Player player;
    private final MarketViewState state;
    private final MarketItem marketItem;
    private Inventory inventory;

    public MarketConfirmGUI(HollyCore plugin, Player player, MarketViewState state, MarketItem marketItem) {
        this.plugin = plugin;
        this.player = player;
        this.state = state == null ? new MarketViewState() : state;
        this.marketItem = marketItem;
    }

    public void open() {
        inventory = Bukkit.createInventory(this, 27, ColorUtils.colorizeComponent("&5&lRynek"));
        fillConfirmSide();
        fillCancelSide();
        inventory.setItem(13, marketItem.getItem().clone());
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
    }

    private void fillConfirmSide() {
        ItemStack confirm = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta meta = confirm.getItemMeta();
        if (meta != null) {
            meta.displayName(ColorUtils.colorizeComponent("&a&lPOTWIERDŹ ZAKUP!").decoration(TextDecoration.ITALIC, false));
            confirm.setItemMeta(meta);
        }
        NBTUtils.setString(confirm, "market_confirm_action", "confirm");
        for (int slot : CONFIRM_SLOTS) {
            inventory.setItem(slot, confirm.clone());
        }
    }

    private void fillCancelSide() {
        ItemStack cancel = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta meta = cancel.getItemMeta();
        if (meta != null) {
            meta.displayName(ColorUtils.colorizeComponent("&c&lANULUJ ZAKUP!").decoration(TextDecoration.ITALIC, false));
            cancel.setItemMeta(meta);
        }
        NBTUtils.setString(cancel, "market_confirm_action", "cancel");
        for (int slot : CANCEL_SLOTS) {
            inventory.setItem(slot, cancel.clone());
        }
    }

    public void handleClick(int slot) {
        if (inventory == null || slot < 0 || slot >= inventory.getSize()) {
            return;
        }

        ItemStack clicked = inventory.getItem(slot);
        if (clicked == null) {
            return;
        }

        String action = NBTUtils.getString(clicked, "market_confirm_action");
        if (action == null) {
            return;
        }

        MarketManager manager = plugin.getMarketManager();
        MarketConfig config = plugin.getConfigManager().getMarketConfig();

        if ("cancel".equals(action)) {
            player.closeInventory();
            new MarketGUI(plugin, player, state).open();
            return;
        }

        if (!"confirm".equals(action)) {
            return;
        }

        player.closeInventory();
        if (!manager.hasInventorySpace(player)) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            player.sendMessage(ColorUtils.colorizeComponent("&cZwolnij miejsce w eq, aby zakupić przedmiot!"));
            new MarketGUI(plugin, player, state).open();
            return;
        }

        if (manager.getPlayerBalance(player) < marketItem.getPrice()) {
            player.sendMessage(ColorUtils.colorizeComponent("&cBy kupić ten przedmiot potrzebujesz jeszcze: &4$"
                + manager.formatPrice(marketItem.getPrice() - manager.getPlayerBalance(player))));
            new MarketGUI(plugin, player, state).open();
            return;
        }

        manager.buyItem(player, marketItem.getId()).thenAccept(success -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (success) {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                player.sendMessage(ColorUtils.colorizeComponent("&aZakupiono!"));
            } else {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                player.sendMessage(ColorUtils.colorizeComponent(config.getItemNotFoundMessage()));
            }
            new MarketGUI(plugin, player, state).open();
        }));
    }

    public Player getPlayer() {
        return player;
    }

    public MarketItem getMarketItem() {
        return marketItem;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
