package dev.grzybalson.listeners;

import dev.grzybalson.HollyCore;
import dev.grzybalson.commands.admin.ChCommand;
import dev.grzybalson.database.PickupDataManager;
import dev.grzybalson.gui.GUIManager;
import dev.grzybalson.gui.PickupGUI;
import dev.grzybalson.gui.ShopGUI;
import dev.grzybalson.gui.ShopPurchaseConfirmGUI;
import dev.grzybalson.gui.KitGUI;
import dev.grzybalson.gui.KitPreviewGUI;
import dev.grzybalson.gui.KitEditGUI;
import dev.grzybalson.gui.MarketGUI;
import dev.grzybalson.gui.MarketConfirmGUI;
import dev.grzybalson.gui.MarketAdminGUI;
import dev.grzybalson.gui.MyItemsGUI;
import dev.grzybalson.gui.ExpiredItemsGUI;
import dev.grzybalson.gui.BackgroundProvider;
import dev.grzybalson.gui.InfoGuiInventory;
import dev.grzybalson.gui.UpgradeGUI;
import dev.grzybalson.gui.UpgradeLevelsGUI;
import dev.grzybalson.gui.ClanUpgradesGUI;
import dev.grzybalson.gui.ClanUpgradeStagesGUI;
import dev.grzybalson.gui.HardResetConfirmGUI;
import dev.grzybalson.gui.PlayerActionGUI;
import dev.grzybalson.gui.WarpGUI;
import dev.grzybalson.modules.sectors.SectorGUI;
import dev.grzybalson.modules.sectors.SectorStatusGUI;
import dev.grzybalson.modules.upgrade.UpgradeManager;
import dev.grzybalson.modules.upgrade.UpgradeType;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.ClickCooldown;
import dev.grzybalson.utils.MoneyAmountUtil;
import dev.grzybalson.utils.NBTUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class GUIListener implements Listener {

    private final HollyCore plugin;
    private final Set<UUID> activeSellTransactions = new HashSet<>();

    public GUIListener(HollyCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        // Handle clicks outside the inventory window
        if (event.getClickedInventory() == null || event.getRawSlot() < 0 || event.getSlot() < 0) {
            Inventory topInventory = event.getView().getTopInventory();
            if (topInventory != null && isOurGUI(topInventory.getHolder())) {
                event.setCancelled(true);
            }
            return;
        }

        // Only process top inventory clicks for GUI detection
        Inventory topInventory = event.getView().getTopInventory();
        if (event.getClickedInventory() != null && !event.getClickedInventory().equals(topInventory)) {
            // Clicked in player inventory - check if top inventory is our GUI
            // This avoids calling getHolder() on potentially large block inventories
        }

        // Cache holder - single expensive NMS call instead of many
        InventoryHolder holder = topInventory.getHolder();
        if (holder == null || !isOurGUI(holder)) {
            return;
        }

        long clickCooldown = getGuiClickCooldown(holder);
        boolean clickBlocked = clickCooldown > 0L && !ClickCooldown.tryUse(player, holder.getClass().getName(), clickCooldown);

        if (holder instanceof ShopGUI shopGUI) {
            event.setCancelled(true);
            if (clickBlocked) return;
            handleShopClick(event, player, shopGUI);
        } else if (holder instanceof ShopPurchaseConfirmGUI confirmGUI) {
            event.setCancelled(true);
            if (clickBlocked) return;
            confirmGUI.handleClick(event.getSlot());
        } else if (holder instanceof PickupGUI pickupGUI) {
            event.setCancelled(true);
            if (clickBlocked) return;
            handlePickupClick(event, player, pickupGUI);
        } else if (holder instanceof KitGUI kitGUI) {
            event.setCancelled(true);
            if (clickBlocked) return;
            kitGUI.handleClick(event.getSlot(), event.isRightClick());
        } else if (holder instanceof KitPreviewGUI kitPreviewGUI) {
            event.setCancelled(true);
            if (clickBlocked) return;
            kitPreviewGUI.handleClick(event.getSlot());
        } else if (holder instanceof dev.grzybalson.gui.EqPreviewGUI) {
            event.setCancelled(true);
        } else if (holder instanceof KitEditGUI kitEditGUI) {
            handleKitEditClick(event, player, kitEditGUI, topInventory);
        } else if (holder instanceof MarketGUI marketGUI) {
            event.setCancelled(true);
            if (clickBlocked) return;
            marketGUI.handleClick(event.getSlot(), event.getClick());
        } else if (holder instanceof MarketConfirmGUI marketConfirmGUI) {
            event.setCancelled(true);
            if (clickBlocked) return;
            marketConfirmGUI.handleClick(event.getSlot());
        } else if (holder instanceof MarketAdminGUI marketAdminGUI) {
            event.setCancelled(true);
            if (clickBlocked) return;
            marketAdminGUI.handleClick(event.getSlot());
        } else if (holder instanceof MyItemsGUI myItemsGUI) {
            event.setCancelled(true);
            if (clickBlocked) return;
            myItemsGUI.handleClick(event.getSlot());
        } else if (holder instanceof ExpiredItemsGUI expiredItemsGUI) {
            event.setCancelled(true);
            if (clickBlocked) return;
            expiredItemsGUI.handleClick(event.getSlot());
        } else if (holder instanceof InfoGuiInventory) {
            event.setCancelled(true);
        } else if (holder instanceof WarpGUI warpGUI) {
            event.setCancelled(true);
            if (clickBlocked) return;
            warpGUI.handleClick(event.getSlot());
        } else if (holder instanceof SectorGUI sectorGUI) {
            event.setCancelled(true);
            if (clickBlocked) return;
            sectorGUI.handleClick(event.getSlot());
        } else if (holder instanceof SectorStatusGUI sectorStatusGUI) {
            event.setCancelled(true);
            if (clickBlocked) return;
            sectorStatusGUI.handleClick(event.getSlot());
        } else if (holder instanceof UpgradeGUI upgradeGUI) {
            event.setCancelled(true);
            if (clickBlocked) return;
            if (event.getClickedInventory() == null || !event.getClickedInventory().equals(topInventory)) {
                return;
            }
            upgradeGUI.handleClick(event.getSlot(), event.isRightClick());
        } else if (holder instanceof UpgradeLevelsGUI upgradeLevelsGUI) {
            event.setCancelled(true);
            if (clickBlocked) return;
            if (event.getClickedInventory() == null || !event.getClickedInventory().equals(topInventory)) {
                return;
            }
            upgradeLevelsGUI.handleClick(event.getSlot());
        } else if (holder instanceof ClanUpgradesGUI clanUpgradesGUI) {
            event.setCancelled(true);
            if (clickBlocked) return;
            if (event.getClickedInventory() == null || !event.getClickedInventory().equals(topInventory)) {
                return;
            }
            clanUpgradesGUI.handleClick(event.getSlot());
        } else if (holder instanceof ClanUpgradeStagesGUI clanUpgradeStagesGUI) {
            event.setCancelled(true);
            if (clickBlocked) return;
            if (event.getClickedInventory() == null || !event.getClickedInventory().equals(topInventory)) {
                return;
            }
            clanUpgradeStagesGUI.handleClick(event.getSlot());
        } else if (holder instanceof HardResetConfirmGUI hardResetConfirmGUI) {
            event.setCancelled(true);
            if (clickBlocked) return;
            if (event.getClickedInventory() == null || !event.getClickedInventory().equals(topInventory)) {
                return;
            }
            hardResetConfirmGUI.handleClick(event.getRawSlot());
        } else if (holder instanceof PlayerActionGUI playerActionGUI) {
            event.setCancelled(true);
            if (clickBlocked) return;
            if (event.getClickedInventory() == null || !event.getClickedInventory().equals(topInventory)) {
                return;
            }
            playerActionGUI.handleClick(event.getSlot());
        } else if (holder instanceof ChCommand.ChannelGuiHolder) {
            event.setCancelled(true);
        }
    }


    private boolean isOurGUI(InventoryHolder holder) {
        if (holder == null) {
            return false;
        }
        return holder instanceof ShopGUI
            || holder instanceof ShopPurchaseConfirmGUI
            || holder instanceof PickupGUI
            || holder instanceof KitGUI
            || holder instanceof KitPreviewGUI
            || holder instanceof dev.grzybalson.gui.EqPreviewGUI
            || holder instanceof KitEditGUI
            || holder instanceof MarketGUI
            || holder instanceof MarketConfirmGUI
            || holder instanceof MarketAdminGUI
            || holder instanceof MyItemsGUI
            || holder instanceof ExpiredItemsGUI
            || holder instanceof InfoGuiInventory
            || holder instanceof WarpGUI
            || holder instanceof UpgradeGUI
            || holder instanceof UpgradeLevelsGUI
            || holder instanceof ClanUpgradesGUI
            || holder instanceof ClanUpgradeStagesGUI
            || holder instanceof HardResetConfirmGUI
            || holder instanceof PlayerActionGUI
            || holder instanceof SectorGUI
            || holder instanceof SectorStatusGUI
            || holder instanceof ChCommand.ChannelGuiHolder;
    }

    private long getGuiClickCooldown(InventoryHolder holder) {
        if (holder instanceof KitEditGUI || holder instanceof InfoGuiInventory || holder instanceof ChCommand.ChannelGuiHolder) {
            return 0L;
        }
        if (holder instanceof UpgradeGUI || holder instanceof UpgradeLevelsGUI) {
            return plugin.getUpgradeManager() != null ? plugin.getUpgradeManager().getClickCooldownMillis() : 1000L;
        }
        if (holder instanceof ShopGUI || holder instanceof ShopPurchaseConfirmGUI || holder instanceof PickupGUI || holder instanceof KitGUI || holder instanceof KitPreviewGUI
            || holder instanceof MarketGUI || holder instanceof MarketConfirmGUI || holder instanceof MarketAdminGUI || holder instanceof MyItemsGUI || holder instanceof ExpiredItemsGUI
            || holder instanceof WarpGUI
            || holder instanceof ClanUpgradesGUI
            || holder instanceof ClanUpgradeStagesGUI
            || holder instanceof SectorGUI
            || holder instanceof SectorStatusGUI) {
            return 250L;
        }
        return 0L;
    }



    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof KitEditGUI kitEditGUI) {
            for (int rawSlot : event.getRawSlots()) {
                if (rawSlot < event.getView().getTopInventory().getSize() && !kitEditGUI.isEditableSlot(rawSlot)) {
                    event.setCancelled(true);
                    return;
                }
            }
            return;
        }

        if (isOurGUI(holder)) {
            event.setCancelled(true);
        }
    }

    private void handleKitEditClick(InventoryClickEvent event, Player player, KitEditGUI kitEditGUI, Inventory topInventory) {
        Inventory clickedInventory = event.getClickedInventory();
        if (clickedInventory == null) {
            return;
        }

        boolean topInventoryClick = clickedInventory.equals(topInventory);
        if (topInventoryClick) {
            int rawSlot = event.getRawSlot();
            if (!kitEditGUI.isEditableSlot(rawSlot)) {
                event.setCancelled(true);
                kitEditGUI.handleClick(rawSlot, event.getCursor(), event.isShiftClick());
                return;
            }

            if (event.isShiftClick()) {
                event.setCancelled(true);
                moveKitItemToPlayerInventory(player, topInventory, rawSlot);
            }
            return;
        }

        if (event.isShiftClick()) {
            event.setCancelled(true);
            movePlayerItemToKitEditor(event, player, kitEditGUI);
        }
    }

    private void moveKitItemToPlayerInventory(Player player, Inventory kitInventory, int slot) {
        ItemStack item = kitInventory.getItem(slot);
        if (item == null || item.getType().isAir()) {
            return;
        }

        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item.clone());
        if (leftover.isEmpty()) {
            kitInventory.setItem(slot, null);
        } else {
            kitInventory.setItem(slot, leftover.values().iterator().next());
        }
        player.updateInventory();
    }

    private void movePlayerItemToKitEditor(InventoryClickEvent event, Player player, KitEditGUI kitEditGUI) {
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType().isAir()) {
            return;
        }

        int remaining = kitEditGUI.addItemToEditableSlots(item.clone());
        if (remaining <= 0) {
            event.getClickedInventory().setItem(event.getSlot(), null);
        } else {
            item.setAmount(remaining);
            event.getClickedInventory().setItem(event.getSlot(), item);
        }
        player.updateInventory();
    }


    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        ClickCooldown.clear(event.getPlayer());
        activeSellTransactions.remove(event.getPlayer().getUniqueId());
    }


    private void playClickSound(Player player) {
        player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_DISPENSE, 0.5f, 1.2f);
    }

    public static void playErrorSound(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
    }

    private void handleShopClick(InventoryClickEvent event, Player player, ShopGUI shopGUI) {
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || GUIManager.isBackgroundItem(clickedItem)) {
            return;
        }

        String action = NBTUtils.getString(clickedItem, "shop_action");
        if (action != null) {
            var shopConfig = plugin.getConfigManager().getShopConfig();
            switch (action) {
                case "open_money" -> {
                    if (!shopConfig.isMoneyShopEnabled()) {
                        playErrorSound(player);
                        player.sendMessage(ColorUtils.colorizeComponent(shopConfig.getDisabledCategoryMessage()));
                        return;
                    }
                    playClickSound(player);
                    Bukkit.getScheduler().runTask(plugin, () -> new ShopGUI(plugin, player, ShopGUI.ShopType.MONEY).open());
                }
                case "open_license" -> {
                    if (!shopConfig.isLicenseShopEnabled() || !ShopGUI.hasLicenseShopAccess(plugin, player)) {
                        playErrorSound(player);
                        player.sendMessage(ColorUtils.colorizeComponent(shopConfig.getDisabledCategoryMessage()));
                        return;
                    }
                    playClickSound(player);
                    Bukkit.getScheduler().runTask(plugin, () -> new ShopGUI(plugin, player, ShopGUI.ShopType.LICENSE).open());
                }
                case "open_time" -> {
                    if (!shopConfig.isTimeShopEnabled()) {
                        playErrorSound(player);
                        player.sendMessage(ColorUtils.colorizeComponent(shopConfig.getDisabledCategoryMessage()));
                        return;
                    }
                    playClickSound(player);
                    Bukkit.getScheduler().runTask(plugin, () -> new ShopGUI(plugin, player, ShopGUI.ShopType.TIME).open());
                }
                case "open_sell" -> {
                    if (!shopConfig.isSellShopEnabled()) {
                        playErrorSound(player);
                        player.sendMessage(ColorUtils.colorizeComponent(shopConfig.getDisabledCategoryMessage()));
                        return;
                    }
                    playClickSound(player);
                    Bukkit.getScheduler().runTask(plugin, () -> new ShopGUI(plugin, player, ShopGUI.ShopType.SELL).open());
                }
                case "back" -> {
                    if (!(shopConfig.isMoneyShopEnabled() || shopConfig.isTimeShopEnabled() || shopConfig.isSellShopEnabled() || shopConfig.isKillsShopEnabled()
                        || (shopConfig.isLicenseShopEnabled() && ShopGUI.hasLicenseShopAccess(plugin, player)))) {
                        playErrorSound(player);
                        player.sendMessage(ColorUtils.colorizeComponent(shopConfig.getAllCategoriesDisabledMessage()));
                        player.closeInventory();
                        return;
                    }
                    playClickSound(player);
                    Bukkit.getScheduler().runTask(plugin, () -> new ShopGUI(plugin, player, ShopGUI.ShopType.MAIN).open());
                }
                case "open_kills" -> {
                    if (!shopConfig.isKillsShopEnabled()) {
                        playErrorSound(player);
                        player.sendMessage(ColorUtils.colorizeComponent(shopConfig.getDisabledCategoryMessage()));
                        return;
                    }
                    playClickSound(player);
                    Bukkit.getScheduler().runTask(plugin, () -> new ShopGUI(plugin, player, ShopGUI.ShopType.KILLS).open());
                }
            }
            return;
        }

        ShopGUI.PurchaseEntry purchaseEntry = shopGUI.getPurchaseEntry(event.getRawSlot());
        if (purchaseEntry != null) {
            if (!purchaseEntry.enabled()) {
                playErrorSound(player);
                player.sendMessage(ColorUtils.colorizeComponent(
                    plugin.getConfigManager().getShopConfig().getItemDisabledMessage()
                ));
                return;
            }

            String shopType = purchaseEntry.shopType();
            if (shopType.equals("license")
                && (!plugin.getConfigManager().getShopConfig().isLicenseShopEnabled()
                || !ShopGUI.hasLicenseShopAccess(plugin, player))) {
                playErrorSound(player);
                player.sendMessage(ColorUtils.colorizeComponent(
                    plugin.getConfigManager().getShopConfig().getDisabledCategoryMessage()
                ));
                return;
            }

            playClickSound(player);
            Bukkit.getScheduler().runTask(plugin, () -> new ShopPurchaseConfirmGUI(
                plugin,
                player,
                shopGUI.getType(),
                shopType,
                purchaseEntry.originalItem(),
                purchaseEntry.moneyPrice(),
                purchaseEntry.timePrice(),
                purchaseEntry.killsPrice()
            ).open());
            return;
        }

        String shopType = NBTUtils.getString(clickedItem, "shop_type");
        if (!"sell".equals(shopType)) {
            return;
        }

        String sellEnabledStr = NBTUtils.getString(clickedItem, "sell_enabled");
        if ("false".equalsIgnoreCase(sellEnabledStr)) {
            playErrorSound(player);
            player.sendMessage(ColorUtils.colorizeComponent(
                plugin.getConfigManager().getShopConfig().getSellItemDisabledMessage()
            ));
            return;
        }

        boolean sellAll = event.getClick() == ClickType.SHIFT_RIGHT;
        handleSellItem(player, clickedItem, sellAll, shopGUI);
    }

    private void handleSellItem(Player player, ItemStack clickedItem, boolean sellAll, ShopGUI shopGUI) {
        String sellEnabledStr = NBTUtils.getString(clickedItem, "sell_enabled");
        if ("false".equalsIgnoreCase(sellEnabledStr)) {
            playErrorSound(player);
            player.sendMessage(ColorUtils.colorizeComponent(
                plugin.getConfigManager().getShopConfig().getSellItemDisabledMessage()
            ));
            return;
        }

        String materialName = NBTUtils.getString(clickedItem, "sell_material");
        Double configuredPrice = NBTUtils.getDouble(clickedItem, "sell_price");
        Integer sellAmount = NBTUtils.getInt(clickedItem, "sell_amount");

        if (materialName == null || configuredPrice == null || sellAmount == null) return;
        if (configuredPrice <= 0.0D || sellAmount <= 0) return;

        Material material = Material.getMaterial(materialName);
        if (material == null) return;

        UpgradeType upgradeType = UpgradeType.fromArgument(NBTUtils.getString(clickedItem, "sell_upgrade_type"));
        Integer upgradeLevel = NBTUtils.getInt(clickedItem, "sell_upgrade_level");
        int resolvedLevel = upgradeLevel == null ? 0 : upgradeLevel;
        double sellPrice = resolveUlepszSellPrice(clickedItem, configuredPrice, upgradeType, resolvedLevel);

        if (!plugin.isAddonEnabled("economy") || plugin.getEconomy() == null) {
            player.sendMessage(ColorUtils.error("Ekonomia jest wylaczona, nie mozna sprzedawac przedmiotow."));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        UUID playerId = player.getUniqueId();
        if (!activeSellTransactions.add(playerId)) {
            player.sendMessage(ColorUtils.error("Sprzedaż jest już przetwarzana. Poczekaj chwilę."));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        int playerHas = countMatchingSellItems(player, material, upgradeType, resolvedLevel);
        int totalToRemove;
        double totalPrice;
        if (sellAll) {
            if (playerHas <= 0) {
                activeSellTransactions.remove(playerId);
                player.sendMessage(ColorUtils.colorizeComponent(
                    plugin.getConfigManager().getShopConfig().getNotEnoughItemsMessage()
                ));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }
            totalToRemove = playerHas;
            totalPrice = (sellPrice / sellAmount) * totalToRemove;
        } else if (playerHas < sellAmount) {
            activeSellTransactions.remove(playerId);
            player.sendMessage(ColorUtils.colorizeComponent(
                plugin.getConfigManager().getShopConfig().getNotEnoughItemsMessage()
            ));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        } else {
            totalToRemove = sellAmount;
            totalPrice = sellPrice;
        }

        List<ItemStack> removedItems = removeMatchingSellItems(player, material, upgradeType, resolvedLevel, totalToRemove);
        int removedAmount = removedItems.stream().mapToInt(ItemStack::getAmount).sum();

        if (removedAmount != totalToRemove) {
            restoreItems(player, removedItems);
            activeSellTransactions.remove(playerId);
            player.sendMessage(ColorUtils.error("Nie udało się bezpiecznie przetworzyć sprzedaży. Spróbuj ponownie."));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            shopGUI.refresh();
            return;
        }

        CompletableFuture<Boolean> balanceFuture;
        try {
            balanceFuture = plugin.getEconomy().addBalance(playerId, totalPrice);
        } catch (Exception exception) {
            restoreItems(player, removedItems);
            activeSellTransactions.remove(playerId);
            player.sendMessage(ColorUtils.error("Wystapil blad podczas sprzedazy. Przedmioty zostaly zwrocone."));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            shopGUI.refresh();
            return;
        }

        balanceFuture.whenComplete((success, throwable) -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                activeSellTransactions.remove(playerId);

                if (throwable != null || !Boolean.TRUE.equals(success)) {
                    restoreItems(player, removedItems);
                    player.sendMessage(ColorUtils.error("Nie udało się dodać pieniędzy za sprzedaż. Przedmioty zostały zwrócone."));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    shopGUI.refresh();
                    return;
                }

                String formattedPrice = MoneyAmountUtil.formatCompact(totalPrice);
                String message = sellAll
                    ? plugin.getConfigManager().getShopConfig().getItemSoldAllMessage()
                        .replace("{price}", formattedPrice)
                        .replace("{amount}", String.valueOf(totalToRemove))
                    : plugin.getConfigManager().getShopConfig().getItemSoldMessage()
                        .replace("{price}", formattedPrice);

                player.sendMessage(ColorUtils.colorizeComponent(message));
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                shopGUI.refresh();
            });
        });
    }

    private double resolveUlepszSellPrice(ItemStack clickedItem, double fallback, UpgradeType upgradeType, int upgradeLevel) {
        UpgradeManager upgradeManager = plugin.getUpgradeManager();
        if (upgradeManager == null) {
            return fallback;
        }
        UpgradeType type = upgradeType;
        int level = upgradeLevel;
        if (type == null || level <= 0) {
            type = upgradeManager.detectType(clickedItem);
            level = upgradeManager.getUpgradeLevel(clickedItem);
        }
        if (type == null || level <= 0) {
            return fallback;
        }
        double ulepszPrice = upgradeManager.getPrice(type, level);
        return ulepszPrice > 0.0D ? ulepszPrice : fallback;
    }

    private int countMatchingSellItems(Player player, Material material, UpgradeType upgradeType, int upgradeLevel) {
        int count = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (ShopGUI.matchesSellCandidate(item, material, upgradeType, upgradeLevel)) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private List<ItemStack> removeMatchingSellItems(Player player, Material material, UpgradeType upgradeType, int upgradeLevel, int amount) {
        int remaining = amount;
        List<ItemStack> removedItems = new ArrayList<>();
        ItemStack[] contents = player.getInventory().getStorageContents();

        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (!ShopGUI.matchesSellCandidate(item, material, upgradeType, upgradeLevel)) {
                continue;
            }
            int itemAmount = item.getAmount();
            int toTake = Math.min(itemAmount, remaining);

            ItemStack removed = item.clone();
            removed.setAmount(toTake);
            removedItems.add(removed);

            if (itemAmount <= remaining) {
                player.getInventory().setItem(i, null);
            } else {
                item.setAmount(itemAmount - toTake);
            }
            remaining -= toTake;
        }

        player.updateInventory();
        return removedItems;
    }

    private void restoreItems(Player player, List<ItemStack> items) {
        for (ItemStack item : items) {
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item.clone());
            for (ItemStack leftoverItem : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftoverItem);
            }
        }
        player.updateInventory();
    }

    private void handlePickupClick(InventoryClickEvent event, Player player, PickupGUI pickupGUI) {
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || GUIManager.isBackgroundItem(clickedItem)) {
            return;
        }

        String action = NBTUtils.getString(clickedItem, "pickup_action");
        if ("claim_all".equals(action)) {
            playClickSound(player);
            player.closeInventory();
            plugin.getPickupManager().claimItems(player);
            return;
        }

        String isPickupItem = NBTUtils.getString(clickedItem, "pickup_item");
        if (!"true".equals(isPickupItem)) {
            return;
        }

        Integer index = NBTUtils.getInt(clickedItem, "pickup_index");
        if (index == null) {
            return;
        }

        List<PickupDataManager.PickupItem> items = pickupGUI.getItems();
        if (index < 0 || index >= items.size()) {
            return;
        }

        PickupDataManager.PickupItem pickupItem = items.get(index);
        ItemStack item = plugin.getPickupManager().prepareGrantedItem(pickupItem.getItem());
        if (item != null) {
            item.setAmount(pickupItem.getAmount());
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
            for (ItemStack leftoverItem : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftoverItem);
            }
        }

        items.remove((int) index);

        playClickSound(player);
        plugin.getPickupManager().getPickupDataManager()
            .savePickupItems(player.getUniqueId(), items)
            .thenRun(() -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage(ColorUtils.colorizeComponent(
                        plugin.getConfigManager().getMessagesConfig().getPickupReceived()
                    ));

                    if (items.isEmpty()) {
                        plugin.getPickupManager().hideBossBar(player);
                        player.closeInventory();
                    } else {
                        player.closeInventory();
                        new PickupGUI(plugin, player).open();
                    }
                });
            });
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
    }
}
