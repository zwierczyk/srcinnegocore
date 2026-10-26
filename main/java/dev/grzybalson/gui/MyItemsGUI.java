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

public class MyItemsGUI implements InventoryHolder {

    private static final int[] CONTENT_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    };

    private final HollyCore plugin;
    private final Player player;
    private final MarketViewState state;
    private Inventory inventory;

    public MyItemsGUI(HollyCore plugin, Player player, MarketViewState state) {
        this.plugin = plugin;
        this.player = player;
        this.state = state == null ? new MarketViewState() : state;
    }

    public void open() {
        inventory = Bukkit.createInventory(this, 54, ColorUtils.colorizeComponent("&5&lTwoje Oferty"));
        refresh();
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
    }

    private void refresh() {
        ItemStack border = createPane(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, border);
        }

        List<MarketItem> items = plugin.getMarketManager().getPlayerItems(player);
        for (int i = 0; i < CONTENT_SLOTS.length; i++) {
            int slot = CONTENT_SLOTS[i];
            if (i >= items.size()) {
                inventory.setItem(slot, null);
                continue;
            }
            inventory.setItem(slot, createOfferItem(items.get(i)));
        }

        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta meta = back.getItemMeta();
        if (meta != null) {
            meta.displayName(ColorUtils.colorizeComponent("&4&lPowrót").decoration(TextDecoration.ITALIC, false));
            back.setItemMeta(meta);
        }
        NBTUtils.setString(back, "myitems_action", "back");
        inventory.setItem(49, back);
    }

    private ItemStack createPane(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(ColorUtils.colorizeComponent(name).decoration(TextDecoration.ITALIC, false));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createOfferItem(MarketItem marketItem) {
        ItemStack display = marketItem.getItem().clone();
        ItemMeta meta = display.getItemMeta();
        if (meta == null) {
            return display;
        }

        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.add(Component.empty());
        lore.addAll(ColorUtils.colorizeLore("&cKliknij, aby wycofać przedmiot"));
        meta.lore(lore);
        display.setItemMeta(meta);
        NBTUtils.setString(display, "myitems_item_id", marketItem.getId());
        return display;
    }

    public void handleClick(int slot) {
        ItemStack clicked = inventory.getItem(slot);
        // ramka rozpoznawana po braku znaczników, nie po materiale — szyby też bywają wystawione
        if (clicked == null || (NBTUtils.getString(clicked, "myitems_action") == null
            && NBTUtils.getString(clicked, "myitems_item_id") == null)) {
            return;
        }

        MarketManager manager = plugin.getMarketManager();
        MarketConfig config = plugin.getConfigManager().getMarketConfig();

        if (manager.isGuiOnCooldown(player.getUniqueId())) {
            player.sendMessage(ColorUtils.colorizeComponent("&cZwolnij..."));
            return;
        }
        manager.setGuiCooldown(player.getUniqueId());

        String action = NBTUtils.getString(clicked, "myitems_action");
        if ("back".equals(action)) {
            player.closeInventory();
            new MarketGUI(plugin, player, state).open();
            return;
        }

        String itemId = NBTUtils.getString(clicked, "myitems_item_id");
        if (itemId == null) {
            return;
        }

        if (!manager.hasInventorySpace(player)) {
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            player.sendMessage(ColorUtils.colorizeComponent("&cZwolnij miejsce w eq, aby wycofać przedmiot!"));
            return;
        }

        manager.withdrawItem(player, itemId).thenAccept(success -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (success) {
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
            } else {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                player.sendMessage(ColorUtils.colorizeComponent(config.getItemNotFoundMessage()));
            }
            open();
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
