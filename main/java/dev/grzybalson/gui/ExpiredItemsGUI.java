package dev.grzybalson.gui;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.configs.MarketConfig;
import dev.grzybalson.modules.market.MarketItem;
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

import java.text.SimpleDateFormat;
import java.util.*;

public class ExpiredItemsGUI implements InventoryHolder {

    private static final int[] ITEM_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    };

    private final HollyCore plugin;
    private final Player player;
    private Inventory inventory;
    private int currentPage = 0;

    public ExpiredItemsGUI(HollyCore plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public void open() {
        MarketConfig config = plugin.getConfigManager().getMarketConfig();
        inventory = Bukkit.createInventory(this, plugin.getGuiFactory().getInventorySize(config.getMarketGuiRows()), ColorUtils.colorizeComponent(config.getExpiredItemsGuiTitle()));
        refresh();
        player.openInventory(inventory);
    }

    public void refresh() {
        refreshInternal();
    }

    public void refreshWithCursor(int cursorSlot) {
        refreshInternal();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.getOpenInventory().getTopInventory().equals(inventory)) {
                player.getOpenInventory().setCursor(null);
            }
        }, 1L);
    }

    private void refreshInternal() {
        MarketConfig config = plugin.getConfigManager().getMarketConfig();
        plugin.getGuiFactory().applyMarketTemplate(inventory);
        setupNavigation();

        List<MarketItem> items = plugin.getMarketManager().getExpiredItems(player.getUniqueId());
        int[] itemSlots = plugin.getGuiFactory().getContentSlots(inventory);

        int totalPages = (int) Math.ceil((double) items.size() / itemSlots.length);
        if (totalPages == 0) totalPages = 1;

        if (currentPage >= totalPages) {
            currentPage = totalPages - 1;
        }

        int startIndex = currentPage * itemSlots.length;

        for (int i = 0; i < itemSlots.length; i++) {
            int slot = itemSlots[i];
            int itemIndex = startIndex + i;

            if (itemIndex < items.size()) {
                MarketItem marketItem = items.get(itemIndex);
                inventory.setItem(slot, createExpiredItemIcon(marketItem));
            } else {
                inventory.setItem(slot, null);
            }
        }
    }

    private void setupNavigation() {
        MarketConfig config = plugin.getConfigManager().getMarketConfig();

        ItemStack prevPage = new ItemStack(Material.ARROW);
        ItemMeta prevMeta = prevPage.getItemMeta();
        if (prevMeta != null) {
            prevMeta.displayName(ColorUtils.colorizeComponent("&c« Poprzednia strona").decoration(TextDecoration.ITALIC, false));
            prevMeta.lore(List.of(
                ColorUtils.colorizeComponent("&7Strona: &e" + (currentPage + 1)).decoration(TextDecoration.ITALIC, false)
            ));
            prevPage.setItemMeta(prevMeta);
        }
        NBTUtils.setString(prevPage, "expired_action", "prev_page");
        plugin.getGuiFactory().setItemIfInBounds(inventory, resolveBottomSlot(config.getExpiredGuiPrevPageSlot(), 3), prevPage);

        ItemStack back = new ItemStack(Material.valueOf(config.getExpiredGuiBackButtonMaterial()));
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            backMeta.displayName(ColorUtils.colorizeComponent(config.getExpiredGuiBackButtonName()).decoration(TextDecoration.ITALIC, false));
            back.setItemMeta(backMeta);
        }
        NBTUtils.setString(back, "expired_action", "back");
        plugin.getGuiFactory().setItemIfInBounds(inventory, resolveBottomSlot(config.getExpiredGuiBackSlot(), 4), back);

        ItemStack nextPage = new ItemStack(Material.ARROW);
        ItemMeta nextMeta = nextPage.getItemMeta();
        if (nextMeta != null) {
            nextMeta.displayName(ColorUtils.colorizeComponent("&aNastępna strona »").decoration(TextDecoration.ITALIC, false));
            nextMeta.lore(List.of(
                ColorUtils.colorizeComponent("&7Strona: &e" + (currentPage + 1)).decoration(TextDecoration.ITALIC, false)
            ));
            nextPage.setItemMeta(nextMeta);
        }
        NBTUtils.setString(nextPage, "expired_action", "next_page");
        plugin.getGuiFactory().setItemIfInBounds(inventory, resolveBottomSlot(config.getExpiredGuiNextPageSlot(), 5), nextPage);
    }

    private int resolveBottomSlot(int configuredSlot, int fallbackColumn) {
        return plugin.getGuiFactory().resolveSlot(
            inventory,
            configuredSlot,
            plugin.getGuiFactory().bottomRowSlot(inventory, fallbackColumn)
        );
    }

    private ItemStack createExpiredItemIcon(MarketItem marketItem) {
        MarketConfig config = plugin.getConfigManager().getMarketConfig();
        ItemStack display = marketItem.getItem().clone();
        ItemMeta meta = display.getItemMeta();
        if (meta == null) return display;

        List<Component> lore = meta.lore();
        if (lore == null) {
            lore = new ArrayList<>();
        } else {
            lore = new ArrayList<>(lore);
        }

        lore.add(Component.empty());
        lore.add(ColorUtils.colorizeComponent("&8&m                    ").decoration(TextDecoration.ITALIC, false));
        lore.add(ColorUtils.colorizeComponent("&7Cena: &e" + formatPrice(marketItem.getPrice()) + "$").decoration(TextDecoration.ITALIC, false));
        lore.add(ColorUtils.colorizeComponent("&7Data: &f" + formatDate(marketItem.getListedAt())).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        ColorUtils.appendLore(lore, config.getExpiredGuiClaimLore());

        meta.lore(lore);
        display.setItemMeta(meta);

        NBTUtils.setString(display, "expired_item_id", marketItem.getId());

        return display;
    }

    public void handleClick(int slot) {
        if (slot < 0 || slot >= inventory.getSize()) return;
        ItemStack clicked = inventory.getItem(slot);
        if (clicked == null
            || clicked.getType() == Material.GRAY_STAINED_GLASS_PANE
            || plugin.getGuiFactory().isBackgroundItem(clicked)) {
            return;
        }

        MarketConfig config = plugin.getConfigManager().getMarketConfig();

        // Cooldown check
        long now = System.currentTimeMillis();
        if (plugin.getMarketManager().isOnCooldown(player.getUniqueId(), now)) {
            player.sendMessage(ColorUtils.colorizeComponent(config.getCooldownMessage()));
            return;
        }
        plugin.getMarketManager().setCooldown(player.getUniqueId(), now);

        String action = NBTUtils.getString(clicked, "expired_action");
        String itemId = NBTUtils.getString(clicked, "expired_item_id");

        if (action != null) {
            switch (action) {
                case "prev_page" -> {
                    if (currentPage > 0) {
                        currentPage--;
                        refresh();
                        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                    }
                }
                case "next_page" -> {
                    List<MarketItem> items = plugin.getMarketManager().getExpiredItems(player.getUniqueId());
                    int itemSlotsCount = plugin.getGuiFactory().getContentSlots(inventory).length;
                    int totalPages = (int) Math.ceil((double) items.size() / itemSlotsCount);

                    if (currentPage < totalPages - 1) {
                        currentPage++;
                        refresh();
                        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                    }
                }
                case "back" -> {
                    player.closeInventory();
                    new MarketGUI(plugin, player).open();
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                }
            }
            return;
        }

        if (itemId != null) {
            final int clickedSlot = slot;
            plugin.getMarketManager().claimExpiredItem(player, itemId).thenAccept(success -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (success) {
                        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
                    } else {
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    }
                    refreshWithCursor(clickedSlot);
                });
            });
        }
    }

    private String formatPrice(double price) {
        if (price == (long) price) {
            return String.format("%,d", (long) price);
        }
        return String.format("%,.2f", price);
    }

    private String formatDate(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm");
        return sdf.format(new Date(timestamp));
    }

    public Player getPlayer() {
        return player;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
