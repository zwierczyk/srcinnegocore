package dev.grzybalson.gui;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.configs.MarketConfig;
import dev.grzybalson.modules.market.MarketItem;
import dev.grzybalson.modules.market.MarketManager;
import dev.grzybalson.modules.market.MarketSortType;
import dev.grzybalson.modules.market.MarketViewState;
import dev.grzybalson.modules.market.filter.MarketFilterDefinition;
import dev.grzybalson.modules.market.filter.MarketFilters;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.NBTUtils;
import dev.grzybalson.utils.TimeUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MarketGUI implements InventoryHolder {

    private static final int[] ITEM_SLOTS = {
        0, 1, 2, 3, 4, 5, 6,
        9, 10, 11, 12, 13, 14, 15,
        18, 19, 20, 21, 22, 23, 24,
        27, 28, 29, 30, 31, 32, 33
    };

    /** Własne obramowanie rynku — nie korzysta z guis/template.yml. */
    private static final int[] BORDER_SLOTS = {
        7, 16, 25, 34, 43, 52, 44, 53, 42, 51, 41, 40, 39, 38, 37, 36
    };

    private final HollyCore plugin;
    private final Player player;
    private final MarketViewState state;
    private Inventory inventory;

    public MarketGUI(HollyCore plugin, Player player) {
        this(plugin, player, new MarketViewState());
    }

    public MarketGUI(HollyCore plugin, Player player, MarketViewState state) {
        this.plugin = plugin;
        this.player = player;
        this.state = state == null ? new MarketViewState() : state;
    }

    public MarketViewState getState() {
        return state;
    }

    public void open() {
        List<MarketItem> visibleItems = plugin.getMarketManager().buildVisibleItems(player, state);
        int totalPages = Math.max(1, (int) Math.ceil((double) visibleItems.size() / ITEM_SLOTS.length));
        String title = plugin.getConfigManager().getMarketConfig().getMarketGuiTitle()
            + " &8(&7" + state.getPage() + "&8/&7" + totalPages + "&8)";

        inventory = Bukkit.createInventory(this, 54, ColorUtils.colorizeComponent(title));
        refresh(visibleItems);
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
    }

    public void refresh() {
        refresh(plugin.getMarketManager().buildVisibleItems(player, state));
    }

    /** Jedno przeliczenie widocznych ofert na odświeżenie — tytuł, sloty i strony muszą widzieć tę samą listę. */
    private void refresh(List<MarketItem> items) {
        for (int slot : BORDER_SLOTS) {
            inventory.setItem(slot, createPane(Material.GRAY_STAINED_GLASS_PANE, " "));
        }

        setupControls();
        setupFilterTabs();
        setupItems(items);
        setupPagination(items);
    }

    private void setupItems(List<MarketItem> items) {
        MarketManager manager = plugin.getMarketManager();
        int startIndex = (state.getPage() - 1) * ITEM_SLOTS.length;
        double balance = manager.getPlayerBalance(player);
        boolean admin = player.hasPermission("Holly.market.admin") || player.hasPermission("Holly.admin");

        for (int i = 0; i < ITEM_SLOTS.length; i++) {
            int slot = ITEM_SLOTS[i];
            int itemIndex = startIndex + i;
            if (itemIndex >= items.size()) {
                inventory.setItem(slot, null);
                continue;
            }

            MarketItem marketItem = items.get(itemIndex);
            inventory.setItem(slot, createListingItem(marketItem, balance, admin));
        }
    }

    private ItemStack createListingItem(MarketItem marketItem, double balance, boolean admin) {
        MarketConfig config = plugin.getConfigManager().getMarketConfig();
        ItemStack display = marketItem.getItem().clone();
        ItemMeta meta = display.getItemMeta();
        if (meta == null) {
            return display;
        }

        String price = plugin.getMarketManager().formatPrice(marketItem.getPrice());
        long expiresIn = plugin.getMarketManager().getExpirationTimestamp(marketItem) - System.currentTimeMillis();
        String expires = TimeUtil.convert(Math.max(0L, expiresIn));

        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.add(Component.empty());
        lore.addAll(ColorUtils.colorizeLore(config.getListingSellerLine()
            .replace("{seller}", marketItem.getSellerName())));
        lore.addAll(ColorUtils.colorizeLore(config.getListingPriceLine()
            .replace("{price}", price)
            .replace("{price_int}", String.valueOf((long) marketItem.getPrice()))));
        lore.addAll(ColorUtils.colorizeLore(config.getListingExpiresLine()
            .replace("{expires}", expires)));
        lore.add(Component.empty());

        boolean ownItem = marketItem.getSellerUuid().equals(player.getUniqueId());
        if (ownItem) {
            lore.addAll(ColorUtils.colorizeLore(config.getListingWithdrawAction()));
        } else if (balance >= marketItem.getPrice()) {
            lore.addAll(ColorUtils.colorizeLore(config.getListingBuyAction()));
        } else {
            lore.addAll(ColorUtils.colorizeLore(config.getListingNoMoneyAction()));
            lore.addAll(ColorUtils.colorizeLore(config.getListingMissingMoneyAction()
                .replace("{missing}", plugin.getMarketManager().formatPrice(marketItem.getPrice() - balance))));
        }

        if (admin) {
            lore.addAll(ColorUtils.colorizeLore(config.getListingAdminAction()));
        }

        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        display.setItemMeta(meta);
        NBTUtils.setString(display, "market_item_id", marketItem.getId());
        return display;
    }

    private void setupControls() {
        inventory.setItem(8, createPane(Material.GLOW_ITEM_FRAME, "&f"));

        ItemStack offers = createButton(Material.CHEST_MINECART, "&#00C356&lᴛᴡᴏᴊᴇ ᴏꜰᴇʀᴛʏ", List.of(
            "",
            "&#00C356Kliknij, aby zobaczyć swoje oferty"
        ), "market_action", "offers");
        inventory.setItem(17, offers);

        List<String> sortLore = new ArrayList<>();
        sortLore.add("");
        for (MarketSortType type : MarketSortType.values()) {
            if (type == state.getSortType()) {
                sortLore.add(" &#00B02F▶ &#60FB8A" + type.getDisplayName());
            } else {
                sortLore.add(" &#AC2222▶ &#DD2525" + type.getDisplayName());
            }
        }
        sortLore.add("");
        sortLore.add("&bKliknij, aby zmienić filtr sortujący");
        inventory.setItem(26, createButton(Material.HOPPER, "&#00C2FF&lꜱᴏʀᴛᴏᴡᴀɴɪᴇ", sortLore, "market_action", "sort"));

        boolean onlyAffordable = state.isOnlyAffordable();
        inventory.setItem(35, createButton(Material.STONECUTTER, "&#F4FF7C&lꜰɪʟᴛʀᴏᴡᴀɴɪᴇ", List.of(
            "",
            !onlyAffordable ? "&#00B02F▶ &#60FB8AWyświetlaj wszystkie przedmioty " : "&#AC2222▶ &#DD2525Wyświetlaj wszystkie przedmioty",
            !onlyAffordable ? "&#AC2222▶ &#DD2525Wyświetlaj tylko te, na które Cię stać" : "&#00B02F▶ &#60FB8AWyświetlaj tylko te, na które Cię stać",
            "",
            "&#F4FF7CKliknij, aby zmienić filtrowanie."
        ), "market_action", "affordable"));
    }

    private void setupFilterTabs() {
        MarketFilters filters = plugin.getMarketManager().getFilters();
        int index = 0;
        for (Map.Entry<String, MarketFilterDefinition> entry : filters.getFilters().entrySet()) {
            if (index >= 5) {
                break;
            }
            MarketFilterDefinition definition = entry.getValue();
            List<String> lore = new ArrayList<>();
            lore.add("");
            if (definition.getCategories() != null) {
                for (String categoryName : definition.getCategories().keySet()) {
                    if (categoryName.equals(state.getCategoryFilter())) {
                        lore.add(" &#00B02F▶ &#60FB8A" + categoryName);
                    } else {
                        lore.add(" &#AC2222▶ &#DD2525" + categoryName);
                    }
                }
            }
            lore.add("");
            lore.add("&fKliknij, aby wybrać kategorię");

            ItemStack button = createButton(definition.getIcon(), definition.getName(), lore, "market_filter_type", entry.getKey());
            ItemMeta meta = button.getItemMeta();
            if (meta != null) {
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                button.setItemMeta(meta);
            }
            inventory.setItem(45 + index, button);
            index++;
        }
    }

    private void setupPagination(List<MarketItem> items) {
        int totalPages = Math.max(1, (int) Math.ceil((double) items.size() / ITEM_SLOTS.length));

        if (state.getPage() < totalPages) {
            ItemStack next = createButton(Material.LIME_WOOL, "&aNastępna strona", List.of(), "market_action", "next_page");
            next.setAmount(Math.min(64, state.getPage() + 1));
            inventory.setItem(53, next);
        }

        if (state.getPage() > 1) {
            ItemStack prev = createButton(Material.RED_WOOL, "&cPoprzednia strona", List.of(), "market_action", "prev_page");
            prev.setAmount(Math.min(64, state.getPage() - 1));
            inventory.setItem(52, prev);
        }
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

    private ItemStack createButton(Material material, String name, List<String> loreLines, String nbtKey, String nbtValue) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(ColorUtils.colorizeComponent(name).decoration(TextDecoration.ITALIC, false));
            if (loreLines != null && !loreLines.isEmpty()) {
                List<Component> lore = new ArrayList<>();
                for (String line : loreLines) {
                    lore.addAll(ColorUtils.colorizeLore(line));
                }
                meta.lore(lore);
            }
            item.setItemMeta(meta);
        }
        NBTUtils.setString(item, nbtKey, nbtValue);
        return item;
    }

    public void handleClick(int slot, ClickType clickType) {
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }

        ItemStack clicked = inventory.getItem(slot);
        // ramka rozpoznawana po braku znaczników rynku, nie po materiale — szyby też bywają wystawione
        if (clicked == null || (NBTUtils.getString(clicked, "market_action") == null
            && NBTUtils.getString(clicked, "market_filter_type") == null
            && NBTUtils.getString(clicked, "market_item_id") == null)) {
            return;
        }

        MarketManager manager = plugin.getMarketManager();
        MarketConfig config = plugin.getConfigManager().getMarketConfig();

        if (manager.isGuiOnCooldown(player.getUniqueId())) {
            player.sendMessage(ColorUtils.colorizeComponent("&cZwolnij..."));
            return;
        }
        manager.setGuiCooldown(player.getUniqueId());

        String action = NBTUtils.getString(clicked, "market_action");
        if (action != null) {
            handleAction(action);
            return;
        }

        String filterType = NBTUtils.getString(clicked, "market_filter_type");
        if (filterType != null) {
            handleFilterType(filterType);
            return;
        }

        String itemId = NBTUtils.getString(clicked, "market_item_id");
        if (itemId == null) {
            return;
        }

        MarketItem marketItem = manager.getDataManager().getItem(itemId);
        if (marketItem == null) {
            player.sendMessage(ColorUtils.colorizeComponent(config.getItemNotFoundMessage()));
            refresh();
            return;
        }

        boolean admin = player.hasPermission("Holly.market.admin") || player.hasPermission("Holly.admin");
        if (admin && (clickType == ClickType.SHIFT_RIGHT || clickType == ClickType.MIDDLE)) {
            player.closeInventory();
            new MarketAdminGUI(plugin, player, state.copy(), marketItem).open();
            return;
        }

        if (marketItem.getSellerUuid().equals(player.getUniqueId())) {
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
                }
                open();
            }));
            return;
        }

        if (manager.getPlayerBalance(player) < marketItem.getPrice()) {
            player.closeInventory();
            player.sendMessage(ColorUtils.colorizeComponent("&cNie stać Cię na ten przedmiot!"));
            return;
        }

        player.closeInventory();
        new MarketConfirmGUI(plugin, player, state.copy(), marketItem).open();
    }

    private void handleAction(String action) {
        switch (action) {
            case "prev_page" -> {
                state.setPage(state.getPage() - 1);
                open();
            }
            case "next_page" -> {
                state.setPage(state.getPage() + 1);
                open();
            }
            case "sort" -> {
                state.setSortType(state.getSortType().next());
                state.setPage(1);
                open();
            }
            case "affordable" -> {
                state.setOnlyAffordable(!state.isOnlyAffordable());
                state.setPage(1);
                open();
            }
            case "offers" -> {
                player.closeInventory();
                new MyItemsGUI(plugin, player, state.copy()).open();
            }
            default -> {
            }
        }
    }

    private void handleFilterType(String filterType) {
        MarketFilters filters = plugin.getMarketManager().getFilters();
        MarketFilterDefinition definition = filters.getFilters().get(filterType);
        state.setFilterType(filterType);

        if (definition == null || definition.getCategories() == null || definition.getCategories().isEmpty()) {
            state.setCategoryFilter(null);
            state.setPage(1);
            open();
            return;
        }

        List<String> categories = filters.getIndexSafely(filterType);
        if (categories.isEmpty()) {
            state.setCategoryFilter(null);
            state.setPage(1);
            open();
            return;
        }

        String current = state.getCategoryFilter();
        int nextIndex = 0;
        if (current != null && categories.contains(current)) {
            int currentIndex = filters.indexOf(filterType, current);
            nextIndex = currentIndex + 1 >= categories.size() ? 0 : currentIndex + 1;
        }
        state.setCategoryFilter(categories.get(nextIndex));
        state.setPage(1);
        open();
    }

    public Player getPlayer() {
        return player;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
