package dev.grzybalson.gui;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.configs.ShopConfig;
import dev.grzybalson.modules.upgrade.UpgradeManager;
import dev.grzybalson.modules.upgrade.UpgradeType;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.ItemSerializer;
import dev.grzybalson.utils.MoneyAmountUtil;
import dev.grzybalson.utils.NBTUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ShopGUI implements InventoryHolder {

    public enum ShopType {
        MAIN,
        MONEY,
        TIME,
        SELL,
        KILLS,
        WALLET,
        LICENSE
    }

    private final HollyCore plugin;
    private final Player player;
    private final ShopType type;
    private final boolean standaloneSkup;
    private final Map<Integer, PurchaseEntry> purchaseEntries = new HashMap<>();
    private Inventory inventory;

    public ShopGUI(HollyCore plugin, Player player, ShopType type) {
        this(plugin, player, type, false);
    }

    public ShopGUI(HollyCore plugin, Player player, ShopType type, boolean standaloneSkup) {
        this.plugin = plugin;
        this.player = player;
        this.type = type;
        this.standaloneSkup = standaloneSkup && type == ShopType.SELL;
    }

    public void open() {
        ShopConfig config = plugin.getConfigManager().getShopConfig();
        int rows = plugin.getGuiFactory().normalizeRows(config.getShopRows());
        if (type == ShopType.SELL) {
            int needed = Math.max(1, (int) Math.ceil(resolveSellItemCount() / 9.0D));
            if (!standaloneSkup) {
                needed = Math.max(needed, rows);
            }
            rows = plugin.getGuiFactory().normalizeRows(Math.min(6, Math.max(rows, needed)));
        }
        String title;

        if (!isTypeEnabled(type)) {
            if (type == ShopType.MAIN) {
                player.sendMessage(ColorUtils.colorizeComponent(config.getAllCategoriesDisabledMessage()));
            } else {
                player.sendMessage(ColorUtils.colorizeComponent(config.getDisabledCategoryMessage()));
            }
            return;
        }

        switch (type) {
            case MONEY -> title = config.getMoneyShopTitle();
            case TIME -> title = config.getTimeShopTitle();
            case SELL -> title = config.getSellShopTitle();
            case KILLS -> title = config.getKillsShopTitle();
            case WALLET -> title = config.getWalletShopTitle();
            case LICENSE -> title = config.getLicenseShopTitle();
            default -> title = config.getShopTitle();
        }

        inventory = Bukkit.createInventory(this, rows * 9, ColorUtils.colorizeComponent(title));
        purchaseEntries.clear();

        plugin.getGuiFactory().applyDefaultTemplate(inventory);

        if (type == ShopType.MAIN) {
            setupMainShop();
        } else if (type == ShopType.MONEY) {
            setupMoneyShop();
        } else if (type == ShopType.TIME) {
            setupTimeShop();
        } else if (type == ShopType.SELL) {
            setupSellShop();
        } else if (type == ShopType.KILLS) {
            setupKillsShop();
        } else if (type == ShopType.LICENSE) {
            setupLicenseShop();
        }

        player.openInventory(inventory);
    }

    private void setupMainShop() {
        ShopConfig config = plugin.getConfigManager().getShopConfig();

        if (config.isMoneyShopEnabled()) {
            ItemStack moneyShopItem = createMenuItem(config.getMoneyShopItemMaterial(), config.getMoneyShopItemHeadValue(), config.getMoneyShopItemName(), config.getMoneyShopItemLore());
            NBTUtils.setString(moneyShopItem, "shop_action", "open_money");
            plugin.getGuiFactory().setItemIfInBounds(inventory, config.getMoneyShopItemSlot(), moneyShopItem);
        }

        if (config.isLicenseShopEnabled() && hasLicenseShopAccess(plugin, player)) {
            ItemStack licenseShopItem = createMenuItem(config.getLicenseShopItemMaterial(), config.getLicenseShopItemHeadValue(), config.getLicenseShopItemName(), config.getLicenseShopItemLore());
            NBTUtils.setString(licenseShopItem, "shop_action", "open_license");
            plugin.getGuiFactory().setItemIfInBounds(inventory, config.getLicenseShopItemSlot(), licenseShopItem);
        }

        if (config.isTimeShopEnabled()) {
            ItemStack timeShopItem = createMenuItem(config.getTimeShopItemMaterial(), config.getTimeShopItemHeadValue(), config.getTimeShopItemName(), config.getTimeShopItemLore());
            NBTUtils.setString(timeShopItem, "shop_action", "open_time");
            plugin.getGuiFactory().setItemIfInBounds(inventory, config.getTimeShopItemSlot(), timeShopItem);
        }

        if (config.isSellShopEnabled()) {
            ItemStack sellShopItem = createMenuItem(config.getSellShopItemMaterial(), config.getSellShopItemHeadValue(), config.getSellShopItemName(), config.getSellShopItemLore());
            NBTUtils.setString(sellShopItem, "shop_action", "open_sell");
            plugin.getGuiFactory().setItemIfInBounds(inventory, config.getSellShopItemSlot(), sellShopItem);
        }

        if (config.isKillsShopEnabled()) {
            ItemStack killsShopItem = createMenuItem(config.getKillsShopItemMaterial(), config.getKillsShopItemHeadValue(), config.getKillsShopItemName(), config.getKillsShopItemLore());
            NBTUtils.setString(killsShopItem, "shop_action", "open_kills");
            plugin.getGuiFactory().setItemIfInBounds(inventory, config.getKillsShopItemSlot(), killsShopItem);
        }
    }

    private void setupMoneyShop() {
        Map<Integer, ShopConfig.ShopItem> items = plugin.getConfigManager().getShopConfig().getMoneyItems();
        double balance = plugin.getEconomy().getBalance(player);

        for (Map.Entry<Integer, ShopConfig.ShopItem> entry : items.entrySet()) {
            int slot = entry.getKey();
            ShopConfig.ShopItem shopItem = entry.getValue();

            if (slot < 0 || slot >= inventory.getSize()) {
                continue;
            }

            ItemStack rawItem = ItemSerializer.deserializeItem(shopItem.getItemData());
            if (rawItem != null) {
                registerPurchase(slot, "money", rawItem.clone(), shopItem.getPrice(), 0L, 0, shopItem.isEnabled());
                ItemStack displayItem = rawItem.clone();
                if (displayItem.getMaxStackSize() == 1) {
                    displayItem.setAmount(1);
                }
                applyDisplayOverrides(displayItem, shopItem.getDisplayName(), shopItem.getLore());
                addShopLore(displayItem, shopItem.getPrice(), balance, false, shopItem.isEnabled());
                inventory.setItem(slot, displayItem);
            }
        }

        ShopConfig cfg = plugin.getConfigManager().getShopConfig();
        ItemStack backItem = createMenuItem(Material.ARROW, cfg.getBackButtonName(), cfg.getBackButtonLore());
        NBTUtils.setString(backItem, "shop_action", "back");
        inventory.setItem(inventory.getSize() - 5, backItem);
    }

    private void setupLicenseShop() {
        Map<Integer, ShopConfig.ShopItem> items = plugin.getConfigManager().getShopConfig().getLicenseItems();
        double balance = plugin.getEconomy().getBalance(player);

        for (Map.Entry<Integer, ShopConfig.ShopItem> entry : items.entrySet()) {
            int slot = entry.getKey();
            ShopConfig.ShopItem shopItem = entry.getValue();

            if (slot < 0 || slot >= inventory.getSize()) {
                continue;
            }

            ItemStack rawItem = ItemSerializer.deserializeItem(shopItem.getItemData());
            if (rawItem != null) {
                registerPurchase(slot, "license", rawItem.clone(), shopItem.getPrice(), 0L, 0, shopItem.isEnabled());
                ItemStack displayItem = rawItem.clone();
                applyDisplayOverrides(displayItem, shopItem.getDisplayName(), shopItem.getLore());
                addShopLore(displayItem, shopItem.getPrice(), balance, false, shopItem.isEnabled());
                inventory.setItem(slot, displayItem);
            }
        }

        ShopConfig cfg = plugin.getConfigManager().getShopConfig();
        ItemStack backItem = createMenuItem(Material.ARROW, cfg.getBackButtonName(), cfg.getBackButtonLore());
        NBTUtils.setString(backItem, "shop_action", "back");
        inventory.setItem(inventory.getSize() - 5, backItem);
    }

    private void setupTimeShop() {
        Map<Integer, ShopConfig.ShopItem> items = plugin.getConfigManager().getShopConfig().getTimeItems();
        long playtime = getPlayerPlaytime();

        for (Map.Entry<Integer, ShopConfig.ShopItem> entry : items.entrySet()) {
            int slot = entry.getKey();
            ShopConfig.ShopItem shopItem = entry.getValue();

            if (slot < 0 || slot >= inventory.getSize()) {
                continue;
            }

            ItemStack rawItem = ItemSerializer.deserializeItem(shopItem.getItemData());
            if (rawItem != null) {
                registerPurchase(slot, "time", rawItem.clone(), 0.0D, shopItem.getTimePrice(), 0, shopItem.isEnabled());
                ItemStack displayItem = rawItem.clone();
                applyDisplayOverrides(displayItem, shopItem.getDisplayName(), shopItem.getLore());
                addTimeLore(displayItem, shopItem.getTimePrice(), playtime, shopItem.isEnabled());
                inventory.setItem(slot, displayItem);
            }
        }

        ShopConfig cfg = plugin.getConfigManager().getShopConfig();
        ItemStack backItem = createMenuItem(Material.ARROW, cfg.getBackButtonName(), cfg.getBackButtonLore());
        NBTUtils.setString(backItem, "shop_action", "back");
        inventory.setItem(inventory.getSize() - 5, backItem);
    }

    private void setupSellShop() {
        Map<Integer, ShopConfig.SellItem> configuredItems = plugin.getConfigManager().getShopConfig().getSellItems();
        if (configuredItems == null || configuredItems.isEmpty()) {
            setupUlepszSellShop();
            return;
        }

        for (Map.Entry<Integer, ShopConfig.SellItem> entry : configuredItems.entrySet()) {
            int slot = entry.getKey();
            ShopConfig.SellItem sellItem = entry.getValue();
            if (slot < 0 || slot >= inventory.getSize()) {
                continue;
            }

            Material material = Material.getMaterial(sellItem.getMaterial());
            if (material == null) continue;

            ItemStack item = null;
            if (sellItem.getItemData() != null && !sellItem.getItemData().isEmpty()) {
                item = ItemSerializer.deserializeItem(sellItem.getItemData());
            }
            if (item == null) {
                item = new ItemStack(material);
            }

            item.setType(material);
            item.setAmount(Math.max(1, sellItem.getAmount()));
            applyDisplayOverrides(item, sellItem.getDisplayName(), sellItem.getLore());
            placeSellItem(slot, item, sellItem.getSellPrice(), sellItem.getAmount(), sellItem.getMaterial(), sellItem.isEnabled());
        }

        if (!standaloneSkup) {
            addSellBackButton();
        }
    }

    private void setupUlepszSellShop() {
        UpgradeManager upgradeManager = plugin.getUpgradeManager();
        if (upgradeManager == null) {
            if (!standaloneSkup) {
                addSellBackButton();
            }
            return;
        }

        int slot = 0;
        for (UpgradeType type : UpgradeType.values()) {
            if (!upgradeManager.isCategoryEnabled(type)) {
                continue;
            }
            for (int level : upgradeManager.getConfiguredLevels(type)) {
                if (slot >= inventory.getSize()) {
                    return;
                }
                double price = upgradeManager.getPrice(type, level);
                if (price <= 0.0D) {
                    continue;
                }
                ItemStack item = upgradeManager.getConfiguredItem(type, level);
                if (item == null || item.getType().isAir()) {
                    continue;
                }
                item.setAmount(1);
                placeSellItem(slot, item, price, 1, item.getType().name());
                slot++;
            }
        }
    }

    private int resolveSellItemCount() {
        Map<Integer, ShopConfig.SellItem> configuredItems = plugin.getConfigManager().getShopConfig().getSellItems();
        if (configuredItems != null && !configuredItems.isEmpty()) {
            return configuredItems.size();
        }
        UpgradeManager upgradeManager = plugin.getUpgradeManager();
        if (upgradeManager == null) {
            return 0;
        }
        int count = 0;
        for (UpgradeType type : UpgradeType.values()) {
            if (!upgradeManager.isCategoryEnabled(type)) {
                continue;
            }
            for (int level : upgradeManager.getConfiguredLevels(type)) {
                if (upgradeManager.getPrice(type, level) > 0.0D) {
                    count++;
                }
            }
        }
        return count;
    }

    private void placeSellItem(int slot, ItemStack item, double configuredPrice, int amount, String materialName) {
        placeSellItem(slot, item, configuredPrice, amount, materialName, true);
    }

    private void placeSellItem(int slot, ItemStack item, double configuredPrice, int amount, String materialName, boolean enabled) {
        UpgradeManager upgradeManager = plugin.getUpgradeManager();
        UpgradeType upgradeType = upgradeManager == null ? null : upgradeManager.detectType(item);
        int upgradeLevel = upgradeManager == null || upgradeType == null ? 0 : upgradeManager.getUpgradeLevel(item);
        double price = configuredPrice;
        if (upgradeManager != null && upgradeType != null && upgradeLevel > 0) {
            double ulepszPrice = upgradeManager.getPrice(upgradeType, upgradeLevel);
            if (ulepszPrice > 0.0D) {
                price = ulepszPrice;
            }
        }

        int playerHas = countMatchingSellItems(player, materialName, upgradeType, upgradeLevel);
        addSellLore(item, price, Math.max(1, amount), playerHas, enabled);
        NBTUtils.setString(item, "shop_type", "sell");
        NBTUtils.setString(item, "sell_material", materialName);
        NBTUtils.setDouble(item, "sell_price", price);
        NBTUtils.setInt(item, "sell_amount", Math.max(1, amount));
        NBTUtils.setString(item, "sell_enabled", String.valueOf(enabled));
        if (upgradeType != null && upgradeLevel > 0) {
            NBTUtils.setString(item, "sell_upgrade_type", upgradeType.name());
            NBTUtils.setInt(item, "sell_upgrade_level", upgradeLevel);
        }
        inventory.setItem(slot, item);
    }

    private void addSellBackButton() {
        ShopConfig cfg = plugin.getConfigManager().getShopConfig();
        ItemStack backItem = createMenuItem(Material.ARROW, cfg.getBackButtonName(), cfg.getBackButtonLore());
        NBTUtils.setString(backItem, "shop_action", "back");
        inventory.setItem(inventory.getSize() - 5, backItem);
    }

    public int countMatchingSellItems(Player target, String materialName, UpgradeType upgradeType, int upgradeLevel) {
        Material material = Material.getMaterial(materialName);
        int count = 0;
        for (ItemStack content : target.getInventory().getStorageContents()) {
            if (matchesSellCandidate(content, material, upgradeType, upgradeLevel)) {
                count += content.getAmount();
            }
        }
        return count;
    }

    public static boolean matchesSellCandidate(ItemStack candidate, Material material, UpgradeType upgradeType, int upgradeLevel) {
        if (candidate == null || candidate.getType().isAir()) {
            return false;
        }
        if (material != null && candidate.getType() != material) {
            return false;
        }
        if (upgradeType == null || upgradeLevel <= 0) {
            return material != null && candidate.getType() == material;
        }
        HollyCore plugin = HollyCore.getPlugin(HollyCore.class);
        UpgradeManager upgradeManager = plugin.getUpgradeManager();
        if (upgradeManager == null) {
            return false;
        }
        return upgradeManager.detectType(candidate) == upgradeType
            && upgradeManager.getUpgradeLevel(candidate) == upgradeLevel;
    }

    private void setupKillsShop() {
        Map<Integer, ShopConfig.ShopItem> items = plugin.getConfigManager().getShopConfig().getKillsItems();
        int playerKills = getPlayerKills();

        for (Map.Entry<Integer, ShopConfig.ShopItem> entry : items.entrySet()) {
            int slot = entry.getKey();
            ShopConfig.ShopItem shopItem = entry.getValue();

            if (slot < 0 || slot >= inventory.getSize()) {
                continue;
            }

            ItemStack rawItem = ItemSerializer.deserializeItem(shopItem.getItemData());
            if (rawItem != null) {
                registerPurchase(slot, "kills", rawItem.clone(), 0.0D, 0L, shopItem.getKillsPrice(), shopItem.isEnabled());
                ItemStack displayItem = rawItem.clone();
                applyDisplayOverrides(displayItem, shopItem.getDisplayName(), shopItem.getLore());
                addKillsLore(displayItem, shopItem.getKillsPrice(), playerKills, shopItem.isEnabled());
                inventory.setItem(slot, displayItem);
            }
        }

        ShopConfig cfg = plugin.getConfigManager().getShopConfig();
        ItemStack backItem = createMenuItem(Material.ARROW, cfg.getBackButtonName(), cfg.getBackButtonLore());
        NBTUtils.setString(backItem, "shop_action", "back");
        inventory.setItem(inventory.getSize() - 5, backItem);
    }


    private void addSellLore(ItemStack item, double price, int amount, int playerHas) {
        addSellLore(item, price, amount, playerHas, true);
    }

    private void addSellLore(ItemStack item, double price, int amount, int playerHas, boolean enabled) {
        ShopConfig config = plugin.getConfigManager().getShopConfig();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        List<Component> lore = meta.lore();
        if (lore == null) {
            lore = new ArrayList<>();
        } else {
            lore = new ArrayList<>(lore);
        }

        if (!lore.isEmpty()) {
            lore.add(Component.empty());
        }
        appendConfiguredLore(lore, config.getSellPriceLore(), Map.of("{price}", MoneyAmountUtil.formatCompact(price)));
        appendConfiguredLore(lore, config.getRequiredAmountLore(), Map.of("{amount}", String.valueOf(amount)));
        appendConfiguredLore(lore, config.getPlayerHasLore(), Map.of("{amount}", String.valueOf(playerHas)));
        lore.add(Component.empty());

        if (!enabled) {
            appendConfiguredLore(lore, config.getDisabledSellItemLore(), Map.of());
        } else if (playerHas >= amount) {
            appendConfiguredLore(lore, config.getCanSellLore(), Map.of());
        } else {
            appendConfiguredLore(lore, config.getCannotSellLore(), Map.of());
        }

        meta.lore(lore);
        item.setItemMeta(meta);
    }

    private void applyDisplayOverrides(ItemStack item, String displayName, List<String> loreOverride) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        if (displayName != null && !displayName.isEmpty()) {
            meta.displayName(ColorUtils.colorizeComponent(displayName).decoration(TextDecoration.ITALIC, false));
        }

        if (loreOverride != null) {
            List<Component> lore = new ArrayList<>();
            for (String line : loreOverride) {
                if (line == null || line.isEmpty()) {
                    lore.add(Component.empty());
                } else {
                    lore.addAll(ColorUtils.colorizeLore(line));
                }
            }
            meta.lore(lore);
        }

        item.setItemMeta(meta);
    }

    private void appendConfiguredLore(List<Component> target, List<String> configuredLines, Map<String, String> placeholders) {
        if (configuredLines == null) {
            return;
        }
        for (String configuredLine : configuredLines) {
            String line = configuredLine == null ? "" : configuredLine;
            for (Map.Entry<String, String> placeholder : placeholders.entrySet()) {
                line = line.replace(placeholder.getKey(), placeholder.getValue());
            }
            if (line.isEmpty()) {
                target.add(Component.empty());
            } else {
                ColorUtils.appendLore(target, line);
            }
        }
    }

    private int countItems(Player player, Material material) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private void addShopLore(ItemStack item, double price, double balance, boolean isTime) {
        addShopLore(item, price, balance, isTime, true);
    }

    private void addShopLore(ItemStack item, double price, double balance, boolean isTime, boolean enabled) {
        ShopConfig config = plugin.getConfigManager().getShopConfig();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        List<Component> lore = meta.lore();
        if (lore == null) {
            lore = new ArrayList<>();
        } else {
            lore = new ArrayList<>(lore);
        }

        lore.add(Component.empty());
        appendConfiguredLore(lore, config.getPriceLore(), Map.of("{price}", MoneyAmountUtil.formatCompact(price)));
        appendConfiguredLore(lore, config.getBalanceLore(), Map.of("{balance}", MoneyAmountUtil.formatCompact(balance)));

        if (!enabled) {
            appendConfiguredLore(lore, config.getDisabledItemLore(), Map.of());
        } else if (balance >= price) {
            appendConfiguredLore(lore, config.getCanAffordLore(), Map.of());
        } else {
            appendConfiguredLore(lore, config.getCannotAffordLore(), Map.of());
        }

        meta.lore(lore);
        item.setItemMeta(meta);
    }

    private void addTimeLore(ItemStack item, long timePrice, long playtime) {
        addTimeLore(item, timePrice, playtime, true);
    }

    private void addTimeLore(ItemStack item, long timePrice, long playtime, boolean enabled) {
        ShopConfig config = plugin.getConfigManager().getShopConfig();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        List<Component> lore = meta.lore();
        if (lore == null) {
            lore = new ArrayList<>();
        } else {
            lore = new ArrayList<>(lore);
        }

        lore.add(Component.empty());
        appendConfiguredLore(lore, config.getTimePriceLore(), Map.of("{time}", formatTime(timePrice)));
        appendConfiguredLore(lore, config.getPlaytimeLore(), Map.of("{time}", formatTime(playtime)));

        if (!enabled) {
            appendConfiguredLore(lore, config.getDisabledItemLore(), Map.of());
        } else if (playtime >= timePrice) {
            appendConfiguredLore(lore, config.getCanAffordLore(), Map.of());
        } else {
            appendConfiguredLore(lore, config.getNotEnoughTimeLore(), Map.of());
        }

        meta.lore(lore);
        item.setItemMeta(meta);
    }

    private String formatTime(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return days + "d " + (hours % 24) + "h";
        } else if (hours > 0) {
            return hours + "h " + (minutes % 60) + "m";
        } else {
            return minutes + "m";
        }
    }

    private long getPlayerPlaytime() {
        var statsData = plugin.getStatsDataManager().getCachedStats(player.getUniqueId());
        if (statsData != null) {
            return statsData.getShopPlaytime();
        }
        // Fallback to Minecraft statistic if not cached yet
        return player.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE) * 50L;
    }

    private int getPlayerKills() {
        return plugin.getKillstreakManager().getCachedKillstreak(player.getUniqueId());
    }

    private void addKillsLore(ItemStack item, int killsPrice, int playerKills) {
        addKillsLore(item, killsPrice, playerKills, true);
    }

    private void addKillsLore(ItemStack item, int killsPrice, int playerKills, boolean enabled) {
        ShopConfig config = plugin.getConfigManager().getShopConfig();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        List<Component> lore = meta.lore();
        if (lore == null) {
            lore = new ArrayList<>();
        } else {
            lore = new ArrayList<>(lore);
        }

        lore.add(Component.empty());
        appendConfiguredLore(lore, config.getKillsPriceLore(), Map.of("{kills}", String.valueOf(killsPrice)));
        appendConfiguredLore(lore, config.getPlayerKillsLore(), Map.of("{kills}", String.valueOf(playerKills)));

        if (!enabled) {
            appendConfiguredLore(lore, config.getDisabledItemLore(), Map.of());
        } else if (playerKills >= killsPrice) {
            appendConfiguredLore(lore, config.getCanAffordLore(), Map.of());
        } else {
            appendConfiguredLore(lore, config.getNotEnoughKillsLore(), Map.of());
        }

        meta.lore(lore);
        item.setItemMeta(meta);
    }


    private ItemStack createMenuItem(Material material, String name, List<String> lore) {
        return createMenuItem(material.name(), "", name, lore);
    }

    private ItemStack createMenuItem(String materialName, String headValue, String name, List<String> lore) {
        Material material = Material.getMaterial(materialName);
        if (material == null) material = Material.STONE;

        ItemStack item = new ItemStack(material);

        if (material == Material.PLAYER_HEAD && headValue != null && !headValue.isEmpty()) {
            applyHeadTexture(item, headValue);
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(ColorUtils.colorizeComponent(name).decoration(TextDecoration.ITALIC, false));

            List<Component> loreComponents = new ArrayList<>();
            for (String line : lore) {
                loreComponents.addAll(ColorUtils.colorizeLore(line));
            }
            meta.lore(loreComponents);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void applyHeadTexture(ItemStack item, String textureValue) {
        dev.grzybalson.utils.HeadUtils.setHeadTextureLikeShop(item, textureValue);
    }

    public static boolean hasLicenseShopAccess(HollyCore plugin, Player player) {
        if (player == null || !isLicenseShopLicenseActive(plugin)) {
            return false;
        }

        String permission = plugin.getConfigManager().getShopConfig().getLicenseShopPermission();
        return permission == null || permission.isBlank() || player.hasPermission(permission);
    }

    public static boolean isLicenseShopLicenseActive(HollyCore plugin) {
        String requiredLicense = plugin.getConfigManager().getShopConfig().getLicenseShopRequiredLicense();
        String licenseKey = plugin.getConfigManager().getLicenseConfig().getLicenseKey();
        if (requiredLicense == null || requiredLicense.isBlank()) {
            return true;
        }
        return requiredLicense.trim().equalsIgnoreCase(licenseKey == null ? "" : licenseKey.trim());
    }

    private boolean isTypeEnabled(ShopType shopType) {
        ShopConfig config = plugin.getConfigManager().getShopConfig();
        return switch (shopType) {
            case MAIN -> config.isMoneyShopEnabled() || config.isTimeShopEnabled() || config.isSellShopEnabled() || config.isKillsShopEnabled()
                || (config.isLicenseShopEnabled() && hasLicenseShopAccess(plugin, player));
            case MONEY -> config.isMoneyShopEnabled();
            case TIME -> config.isTimeShopEnabled();
            case SELL -> config.isSellShopEnabled();
            case KILLS -> config.isKillsShopEnabled();
            case WALLET -> config.isWalletShopEnabled();
            case LICENSE -> config.isLicenseShopEnabled() && hasLicenseShopAccess(plugin, player);
        };
    }

    public void refresh() {
        purchaseEntries.clear();
        inventory.clear();
        plugin.getGuiFactory().applyDefaultTemplate(inventory);

        if (type == ShopType.MAIN) {
            setupMainShop();
        } else if (type == ShopType.MONEY) {
            setupMoneyShop();
        } else if (type == ShopType.TIME) {
            setupTimeShop();
        } else if (type == ShopType.SELL) {
            setupSellShop();
        } else if (type == ShopType.KILLS) {
            setupKillsShop();
        } else if (type == ShopType.LICENSE) {
            setupLicenseShop();
        }
    }

    public ShopType getType() {
        return type;
    }

    public PurchaseEntry getPurchaseEntry(int slot) {
        PurchaseEntry entry = purchaseEntries.get(slot);
        if (entry == null) {
            return null;
        }
        return new PurchaseEntry(
            entry.shopType(),
            entry.originalItem().clone(),
            entry.moneyPrice(),
            entry.timePrice(),
            entry.killsPrice(),
            entry.enabled()
        );
    }

    private void registerPurchase(int slot, String shopType, ItemStack item, double moneyPrice, long timePrice, int killsPrice, boolean enabled) {
        purchaseEntries.put(slot, new PurchaseEntry(shopType, item.clone(), moneyPrice, timePrice, killsPrice, enabled));
    }

    private void registerPurchase(int slot, String shopType, ItemStack item, double moneyPrice, long timePrice, int killsPrice) {
        registerPurchase(slot, shopType, item, moneyPrice, timePrice, killsPrice, true);
    }

    public Player getPlayer() {
        return player;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public record PurchaseEntry(
        String shopType,
        ItemStack originalItem,
        double moneyPrice,
        long timePrice,
        int killsPrice,
        boolean enabled
    ) {
        public PurchaseEntry(String shopType, ItemStack originalItem, double moneyPrice, long timePrice, int killsPrice) {
            this(shopType, originalItem, moneyPrice, timePrice, killsPrice, true);
        }
    }
}
