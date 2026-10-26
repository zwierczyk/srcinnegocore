package dev.grzybalson.gui;

import dev.grzybalson.HollyCore;
import dev.grzybalson.modules.market.MarketItem;
import dev.grzybalson.modules.market.MarketManager;
import dev.grzybalson.modules.market.MarketViewState;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.NBTUtils;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class MarketAdminGUI implements InventoryHolder {

    private final HollyCore plugin;
    private final Player player;
    private final MarketViewState state;
    private final MarketItem marketItem;
    private Inventory inventory;

    public MarketAdminGUI(HollyCore plugin, Player player, MarketViewState state, MarketItem marketItem) {
        this.plugin = plugin;
        this.player = player;
        this.state = state == null ? new MarketViewState() : state;
        this.marketItem = marketItem;
    }

    public void open() {
        inventory = Bukkit.createInventory(this, 45, ColorUtils.colorizeComponent("&c&LADMIN &8| &eZarządzanie Przedmiotem"));
        ItemStack border = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta borderMeta = border.getItemMeta();
        if (borderMeta != null) {
            borderMeta.displayName(ColorUtils.colorizeComponent(" ").decoration(TextDecoration.ITALIC, false));
            border.setItemMeta(borderMeta);
        }
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, border);
        }

        ItemStack delete = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta deleteMeta = delete.getItemMeta();
        if (deleteMeta != null) {
            deleteMeta.displayName(ColorUtils.colorizeComponent("&c&lUSUŃ Z RYNKU").decoration(TextDecoration.ITALIC, false));
            delete.setItemMeta(deleteMeta);
        }
        NBTUtils.setString(delete, "market_admin_action", "delete");
        inventory.setItem(21, delete);
        inventory.setItem(23, marketItem.getItem().clone());

        ItemStack back = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            backMeta.displayName(ColorUtils.colorizeComponent("&4&lPowrót").decoration(TextDecoration.ITALIC, false));
            back.setItemMeta(backMeta);
        }
        NBTUtils.setString(back, "market_admin_action", "back");
        inventory.setItem(40, back);

        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
    }

    public void handleClick(int slot) {
        ItemStack clicked = inventory.getItem(slot);
        if (clicked == null) {
            return;
        }

        String action = NBTUtils.getString(clicked, "market_admin_action");
        if (action == null) {
            return;
        }

        MarketManager manager = plugin.getMarketManager();
        if ("back".equals(action)) {
            player.closeInventory();
            new MarketGUI(plugin, player, state).open();
            return;
        }

        if (!"delete".equals(action)) {
            return;
        }

        manager.adminRemoveItem(marketItem.getId()).thenAccept(success -> Bukkit.getScheduler().runTask(plugin, () -> {
            player.closeInventory();
            if (success) {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                player.sendMessage(ColorUtils.colorizeComponent("&aUsunięto przedmiot!"));
            } else {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }
            new MarketGUI(plugin, player, state).open();
        }));
    }

    public Player getPlayer() {
        return player;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
