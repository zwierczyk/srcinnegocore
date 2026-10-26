package dev.grzybalson.modules.market;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.configs.MarketConfig;
import dev.grzybalson.database.MarketDataManager;
import dev.grzybalson.modules.market.filter.MarketFilterResolver;
import dev.grzybalson.modules.market.filter.MarketFilters;
import dev.grzybalson.network.NatsService;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.TimeUtil;
import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class MarketManager {

    private final HollyCore plugin;
    private final MarketDataManager dataManager;
    private final MarketFilters filters;
    private final Map<UUID, Long> actionCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> guiCooldowns = new ConcurrentHashMap<>();

    public MarketManager(HollyCore plugin) {
        this.plugin = plugin;
        dev.grzybalson.internal.HollyIntegrityGuard.requireBound(plugin, "data:load");
        this.dataManager = new MarketDataManager(plugin);
        // Ładuj filtry z konfiguracji
        this.filters = new MarketFilters(plugin.getConfigManager().getMarketFiltersConfig().buildDefinitions());
    }

    public MarketFilters getFilters() {
        return filters;
    }

    public void reloadFilters() {
        filters.reload(plugin.getConfigManager().getMarketFiltersConfig().buildDefinitions());
    }

    public boolean isOnCooldown(UUID uuid, long now) {
        Long lastAction = actionCooldowns.get(uuid);
        if (lastAction == null) {
            return false;
        }
        return (now - lastAction) < plugin.getConfigManager().getMarketConfig().getActionCooldownMs();
    }

    public void setCooldown(UUID uuid, long now) {
        // wpisy starsze niż cooldown i tak nic nie blokują — czyścimy, żeby mapa nie rosła w nieskończoność
        long cooldownMs = plugin.getConfigManager().getMarketConfig().getActionCooldownMs();
        actionCooldowns.values().removeIf(last -> (now - last) >= cooldownMs);
        actionCooldowns.put(uuid, now);
    }

    public boolean isGuiOnCooldown(UUID uuid) {
        Long last = guiCooldowns.get(uuid);
        return last != null && System.currentTimeMillis() - last < 600L;
    }

    public void setGuiCooldown(UUID uuid) {
        long now = System.currentTimeMillis();
        // wpisy starsze niż cooldown i tak nic nie blokują — czyścimy, żeby mapa nie rosła w nieskończoność
        guiCooldowns.values().removeIf(last -> (now - last) >= 600L);
        guiCooldowns.put(uuid, now);
    }

    public void load() {
        dataManager.loadAllItems();
        NatsService nats = plugin.getNatsService();
        if (nats != null && nats.isConnected()) {
            nats.subscribe(NatsService.MARKET_BUY, this::handleRemotePayout);
        }
    }

    public CompletableFuture<Boolean> listItem(Player seller, ItemStack item, double price) {
        MarketConfig config = plugin.getConfigManager().getMarketConfig();

        if (!Bukkit.isPrimaryThread()) {
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            Bukkit.getScheduler().runTask(plugin, () ->
                listItem(seller, item, price).whenComplete((success, throwable) -> {
                    if (throwable != null) {
                        future.completeExceptionally(throwable);
                    } else {
                        future.complete(success);
                    }
                })
            );
            return future;
        }

        if (price <= 0 || Double.isNaN(price) || Double.isInfinite(price)) {
            seller.sendMessage(ColorUtils.colorizeComponent(formatInvalidPrice(config)));
            return CompletableFuture.completedFuture(false);
        }

        if (price < config.getMinPrice() || price > config.getMaxPrice()) {
            seller.sendMessage(ColorUtils.colorizeComponent(formatInvalidPrice(config)));
            return CompletableFuture.completedFuture(false);
        }

        ItemStack currentMainHandItem = seller.getInventory().getItemInMainHand();
        if (currentMainHandItem == null || currentMainHandItem.getType().isAir()) {
            seller.sendMessage(ColorUtils.colorizeComponent(config.getNoItemInHandMessage()));
            return CompletableFuture.completedFuture(false);
        }

        if (config.isBlockedItem(currentMainHandItem)) {
            seller.sendMessage(ColorUtils.colorizeComponent(config.getBlockedItemMessage()));
            return CompletableFuture.completedFuture(false);
        }

        // Create snapshot BEFORE removing from inventory (race condition fix)
        ItemStack listedStack = currentMainHandItem.clone();

        // Atomic removal with verification (duplication prevention fix)
        ItemStack afterRemoval = seller.getInventory().getItemInMainHand();
        if (afterRemoval == null || afterRemoval.getType().isAir() || !afterRemoval.isSimilar(currentMainHandItem)) {
            // Item changed between check and removal - potential race condition
            seller.sendMessage(ColorUtils.colorizeComponent(config.getNoItemInHandMessage()));
            return CompletableFuture.completedFuture(false);
        }

        seller.getInventory().setItemInMainHand(null);
        seller.updateInventory();

        int currentCount = dataManager.getItemCountBySeller(seller.getUniqueId());
        int maxItems = getPlayerMaxItems(seller);
        if (currentCount >= maxItems) {
            returnItemToPlayer(seller, listedStack);
            seller.sendMessage(ColorUtils.colorizeComponent(config.getMaxItemsReachedMessage()
                .replace("{max}", String.valueOf(maxItems))));
            return CompletableFuture.completedFuture(false);
        }

        String id = dataManager.generateItemId();
        MarketCategory category = MarketCategory.fromItem(listedStack);
        long now = System.currentTimeMillis();
        MarketItem marketItem = new MarketItem(
            id, seller.getUniqueId(), seller.getName(), listedStack.clone(), price,
            now, category, now + config.getListingVisibilityMs()
        );

        return dataManager.saveItem(marketItem).thenApply(v -> {
            notifyItemListed(seller, marketItem, price);
            return true;
        }).exceptionally(throwable -> {
            dataManager.removeItem(id);
            returnItemToPlayer(seller, listedStack);
            seller.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cNie udało się wystawić przedmiotu. Item został zwrócony."));
            return false;
        });
    }

    public CompletableFuture<Boolean> buyItem(Player buyer, String itemId) {
        MarketConfig config = plugin.getConfigManager().getMarketConfig();

        MarketItem preview = dataManager.getItem(itemId);
        if (preview == null || !preview.isVisible()) {
            buyer.sendMessage(ColorUtils.colorizeComponent(config.getItemNotFoundMessage()));
            return CompletableFuture.completedFuture(false);
        }

        if (preview.getSellerUuid().equals(buyer.getUniqueId())) {
            buyer.sendMessage(ColorUtils.colorizeComponent(config.getCannotBuyOwnItemMessage()));
            return CompletableFuture.completedFuture(false);
        }

        double price = preview.getPrice();
        if (plugin.getEconomy().getBalance(buyer) < price) {
            buyer.sendMessage(ColorUtils.colorizeComponent(config.getNotEnoughMoneyMessage()));
            return CompletableFuture.completedFuture(false);
        }

        return dataManager.claimItem(itemId).thenCompose(claimed -> finalizePurchase(buyer, claimed, config));
    }

    public CompletableFuture<Boolean> withdrawItem(Player player, String itemId) {
        MarketConfig config = plugin.getConfigManager().getMarketConfig();

        MarketItem marketItem = dataManager.getItem(itemId);
        if (marketItem == null) {
            player.sendMessage(ColorUtils.colorizeComponent(config.getItemNotFoundMessage()));
            return CompletableFuture.completedFuture(false);
        }

        if (!marketItem.getSellerUuid().equals(player.getUniqueId())) {
            player.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cTo nie jest Twój przedmiot!"));
            return CompletableFuture.completedFuture(false);
        }

        return dataManager.claimItem(itemId).thenApply(claimed -> {
            if (claimed == null) {
                player.sendMessage(ColorUtils.colorizeComponent(config.getItemNotFoundMessage()));
                return false;
            }
            giveItemToPlayer(player, claimed.getItem());
            player.sendMessage(ColorUtils.colorizeComponent(config.getItemWithdrawnMessage()));
            return true;
        });
    }

    public CompletableFuture<Boolean> adminRemoveItem(String itemId) {
        if (dataManager.getItem(itemId) == null) {
            return CompletableFuture.completedFuture(false);
        }
        return dataManager.removeItem(itemId).thenApply(ignored -> true);
    }

    private CompletableFuture<Boolean> finalizePurchase(Player buyer, MarketItem claimed, MarketConfig config) {
        if (claimed == null) {
            Bukkit.getScheduler().runTask(plugin, () ->
                buyer.sendMessage(ColorUtils.colorizeComponent(config.getItemNotFoundMessage())));
            return CompletableFuture.completedFuture(false);
        }

        double price = claimed.getPrice();
        return plugin.getEconomy().removeBalance(buyer.getUniqueId(), price).thenCompose(removed -> {
            if (!Boolean.TRUE.equals(removed)) {
                return dataManager.saveItem(claimed).thenApply(v -> {
                    buyer.sendMessage(ColorUtils.colorizeComponent(config.getNotEnoughMoneyMessage()));
                    return false;
                });
            }
            double commission = Math.max(0.0D, price * config.getCommission());
            double sellerAmount = Math.max(0.0D, price - commission);
            Bukkit.getScheduler().runTask(plugin, () -> {
                giveItemDirect(buyer, claimed.getItem());
                buyer.sendMessage(ColorUtils.colorizeComponent(config.getItemBoughtMessage()
                    .replace("{price}", formatPrice(price))));
            });
            paySeller(claimed.getSellerUuid(), price, commission, sellerAmount);
            return CompletableFuture.completedFuture(true);
        });
    }

    private void paySeller(UUID sellerUuid, double price, double commission, double sellerAmount) {
        if (sellerUuid == null || sellerAmount <= 0.0D) {
            return;
        }
        dataManager.addOfflinePayment(sellerUuid, sellerAmount).whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                plugin.getLogger().log(Level.WARNING, "[Rynek] Nie zapisano wypłaty " + sellerUuid, throwable);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player seller = Bukkit.getPlayer(sellerUuid);
                    if (seller != null && seller.isOnline()) {
                        creditBalance(seller, sellerAmount, price, commission, true);
                    }
                });
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player seller = Bukkit.getPlayer(sellerUuid);
                if (seller != null && seller.isOnline()) {
                    creditPendingPayments(seller, price, commission);
                }
                publishPayout(sellerUuid, price, commission, sellerAmount);
            });
        });
    }

    private void publishPayout(UUID sellerUuid, double price, double commission, double sellerAmount) {
        NatsService nats = plugin.getNatsService();
        if (nats == null || !nats.isConnected()) {
            return;
        }
        nats.publish(NatsService.MARKET_BUY, new Document()
            .append("sellerUuid", sellerUuid.toString())
            .append("amount", sellerAmount)
            .append("price", price)
            .append("commission", commission)
            .append("port", plugin.getServer().getPort()));
    }

    private void handleRemotePayout(Document document) {
        if (document == null) {
            return;
        }
        try {
            int sourcePort = document.getInteger("port", -1);
            if (sourcePort == plugin.getServer().getPort()) {
                return;
            }
            UUID sellerUuid = UUID.fromString(document.getString("sellerUuid"));
            Player seller = Bukkit.getPlayer(sellerUuid);
            if (seller == null || !seller.isOnline()) {
                return;
            }
            Double price = asDouble(document.get("price"));
            Double commission = asDouble(document.get("commission"));
            creditPendingPayments(seller, price, commission);
        } catch (Exception exception) {
            plugin.getLogger().warning("[Rynek] Nieobsłużona wypłata NATS: " + exception.getMessage());
        }
    }

    private void creditPendingPayments(Player seller, Double salePrice, Double commission) {
        if (seller == null || !seller.isOnline()) {
            return;
        }
        UUID sellerUuid = seller.getUniqueId();
        dataManager.getAndClearOfflinePayment(sellerUuid).whenComplete((amount, throwable) -> {
            if (throwable != null) {
                plugin.getLogger().log(Level.WARNING, "[Rynek] Nie odczytano wypłaty " + sellerUuid, throwable);
                return;
            }
            if (amount == null || amount <= 0.0D) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!seller.isOnline()) {
                    dataManager.addOfflinePayment(sellerUuid, amount);
                    return;
                }
                creditBalance(seller, amount, salePrice, commission, salePrice != null);
            });
        });
    }

    private void creditBalance(Player seller, double amount, Double salePrice, Double commission, boolean soldNow) {
        UUID sellerUuid = seller.getUniqueId();
        plugin.getEconomy().addBalance(sellerUuid, amount).whenComplete((ok, throwable) -> {
            boolean credited = throwable == null && Boolean.TRUE.equals(ok);
            if (!credited) {
                dataManager.addOfflinePayment(sellerUuid, amount);
                if (throwable != null) {
                    plugin.getLogger().log(Level.WARNING, "[Rynek] Nie dodano kasy sprzedawcy " + sellerUuid, throwable);
                }
                return;
            }
            plugin.getEconomy().getDataManager().flushPlayer(sellerUuid);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!seller.isOnline()) {
                    return;
                }
                MarketConfig config = plugin.getConfigManager().getMarketConfig();
                if (soldNow && salePrice != null) {
                    seller.sendMessage(ColorUtils.colorizeComponent(config.getItemSoldMessage()
                        .replace("{price}", formatPrice(salePrice))
                        .replace("{commission}", formatPrice(commission == null ? 0.0D : commission))));
                } else {
                    seller.sendMessage(ColorUtils.colorizeComponent(
                        config.getOfflineSaleMessage().replace("{total}", formatPrice(amount))));
                }
            });
        });
    }

    private static Double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return null;
    }

    private void notifyItemListed(Player seller, MarketItem marketItem, double price) {
        MarketConfig config = plugin.getConfigManager().getMarketConfig();
        Bukkit.getScheduler().runTask(plugin, () -> {
            seller.sendMessage(ColorUtils.colorizeComponent(config.getItemListedMessage().replace("{price}", formatPrice(price))));
            seller.sendMessage(ColorUtils.colorizeComponent(
                config.getItemListedVisibilityMessage().replace("{time}", TimeUtil.convert(config.getListingVisibilityMs()))
            ));
            plugin.getWebhookLogManager().logMarketListed(seller, marketItem.getItem(), marketItem.getPrice(), marketItem.getListedAt());
        });
    }

    public List<MarketItem> buildVisibleItems(Player viewer, MarketViewState state) {
        List<MarketItem> result = new ArrayList<>();
        double balance = plugin.getEconomy().getBalance(viewer);
        String categoryFilter = state.getCategoryFilter();

        for (MarketItem item : dataManager.getAllItems()) {
            if (!item.isVisible()) {
                continue;
            }
            if (state.isOnlyAffordable() && item.getPrice() > balance) {
                continue;
            }
            if (categoryFilter != null && !MarketFilterResolver.matches(item.getItem(), categoryFilter, filters.getFilters())) {
                continue;
            }
            result.add(item);
        }

        switch (state.getSortType()) {
            case NEWEST -> result.sort(Comparator.comparingLong(MarketItem::getListedAt).reversed());
            case ASCENDING -> result.sort(Comparator.comparingDouble(MarketItem::getPrice));
            case DESCENDING -> result.sort(Comparator.comparingDouble(MarketItem::getPrice).reversed());
            case ENCHANT -> {
            }
        }
        return result;
    }

    public long getExpirationTimestamp(MarketItem item) {
        MarketConfig config = plugin.getConfigManager().getMarketConfig();
        return item.getListedAt() + config.getExpirationDays() * 24L * 60L * 60L * 1000L;
    }

    public double getPlayerBalance(Player player) {
        return plugin.getEconomy().getBalance(player);
    }

    public boolean hasInventorySpace(Player player) {
        return Arrays.stream(player.getInventory().getStorageContents()).filter(Objects::nonNull).count() <= 35L;
    }

    private void giveItemToPlayer(Player player, ItemStack item) {
        Bukkit.getScheduler().runTask(plugin, () -> giveItemDirect(player, item));
    }

    private void giveItemDirect(Player player, ItemStack item) {
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item.clone());
        for (ItemStack left : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), left);
        }
    }

    private void returnItemToPlayer(Player player, ItemStack item) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item.clone());
            for (ItemStack left : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), left);
            }
        });
    }

    public void checkOfflinePayments(Player player) {
        if (player == null || !player.isOnline() || plugin.getEconomy() == null) {
            return;
        }
        plugin.getEconomy().getBalanceAsync(player.getUniqueId()).whenComplete((ignored, throwable) ->
            Bukkit.getScheduler().runTask(plugin, () -> creditPendingPayments(player, null, null))
        );
    }

    public List<MarketItem> getPlayerItems(Player player) {
        return dataManager.getItemsBySeller(player.getUniqueId());
    }

    /** Podmiana itemu na istniejącej ofercie (np. migracja legacy). */
    public void replaceListingItem(MarketItem oldItem, MarketItem newItem) {
        if (oldItem == null || newItem == null) {
            return;
        }
        dataManager.removeItem(oldItem.getId()).thenCompose(v -> dataManager.saveItem(newItem));
    }

    public MarketDataManager getDataManager() {
        return dataManager;
    }

    private String formatInvalidPrice(MarketConfig config) {
        return config.getInvalidPriceMessage()
            .replace("{min}", String.valueOf(config.getMinPrice()))
            .replace("{max}", String.valueOf(config.getMaxPrice()));
    }

    public String formatPrice(double price) {
        if (price == (long) price) {
            return String.format("%,d", (long) price);
        }
        return String.format("%,.2f", price);
    }

    public int getPlayerMaxItems(Player player) {
        MarketConfig config = plugin.getConfigManager().getMarketConfig();

        int[] limits = {100, 50, 30, 20, 15, 10, 5, 3};
        for (int limit : limits) {
            if (player.hasPermission("Holly.market.limit." + limit)) {
                return limit;
            }
        }

        return config.getDefaultMaxItems();
    }

    public void checkExpiredItems() {
        MarketConfig config = plugin.getConfigManager().getMarketConfig();
        long expirationMs = config.getExpirationDays() * 24L * 60L * 60L * 1000L;
        long now = System.currentTimeMillis();

        List<MarketItem> allItems = new ArrayList<>(dataManager.getAllItems());
        for (MarketItem item : allItems) {
            if (now - item.getListedAt() >= expirationMs) {
                expireItem(item);
            }
        }
    }

    private void expireItem(MarketItem marketItem) {
        // atomowe zdjęcie oferty — brak dokumentu oznacza, że została już sprzedana/wycofana (np. na innym sektorze),
        // więc nie wolno tworzyć dla niej kopii w wygasłych
        dataManager.claimItemExisted(marketItem.getId()).thenCompose(claimed -> {
            if (!Boolean.TRUE.equals(claimed)) {
                return CompletableFuture.<Void>completedFuture(null);
            }
            return dataManager.addExpiredItem(marketItem).thenAccept(v -> notifyItemExpired(marketItem));
        }).exceptionally(throwable -> {
            plugin.getLogger().warning("[Market] Nie udalo sie przeniesc oferty " + marketItem.getId()
                + " do wygaslych: " + throwable.getMessage());
            return null;
        });
    }

    private void notifyItemExpired(MarketItem marketItem) {
        Player seller = Bukkit.getPlayer(marketItem.getSellerUuid());
        if (seller != null && seller.isOnline()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                MarketConfig config = plugin.getConfigManager().getMarketConfig();
                String itemName = marketItem.getItem().getType().name();
                if (marketItem.getItem().hasItemMeta() && marketItem.getItem().getItemMeta().hasDisplayName()) {
                    itemName = marketItem.getItem().getItemMeta().getDisplayName();
                }
                String message = config.getItemExpiredMessage().replace("{item}", itemName);
                seller.sendMessage(ColorUtils.colorizeComponent(message));
            });
        }
    }

    public CompletableFuture<Boolean> claimExpiredItem(Player player, String itemId) {
        MarketConfig config = plugin.getConfigManager().getMarketConfig();

        List<MarketItem> expired = dataManager.getExpiredItemsBySeller(player.getUniqueId());
        MarketItem marketItem = null;
        for (MarketItem item : expired) {
            if (item.getId().equals(itemId)) {
                marketItem = item;
                break;
            }
        }

        if (marketItem == null) {
            player.sendMessage(ColorUtils.colorizeComponent(config.getItemNotFoundMessage()));
            return CompletableFuture.completedFuture(false);
        }

        final MarketItem finalItem = marketItem;
        return dataManager.claimExpiredItem(itemId, player.getUniqueId()).thenCompose(claimed -> {
            CompletableFuture<Boolean> done = new CompletableFuture<>();
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!claimed) {
                    player.sendMessage(ColorUtils.colorizeComponent(config.getItemNotFoundMessage()));
                    done.complete(false);
                    return;
                }
                if (!player.isOnline()) {
                    // Dokument już zdjęty — wróć do expired, żeby nie zgubić itemu.
                    dataManager.addExpiredItem(finalItem);
                    done.complete(false);
                    return;
                }
                giveItemToPlayer(player, finalItem.getItem());
                player.sendMessage(ColorUtils.colorizeComponent(config.getItemWithdrawnMessage()));
                done.complete(true);
            });
            return done;
        });
    }

    public List<MarketItem> getExpiredItems(UUID playerUuid) {
        return dataManager.getExpiredItemsBySeller(playerUuid);
    }

    public void startExpirationTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::checkExpiredItems, 20L * 60L, 20L * 60L * 5L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::creditOnlinePendingPayments, 20L * 20L, 20L * 20L);
    }

    private void creditOnlinePendingPayments() {
        dataManager.listPendingPaymentUuids().thenAccept(uuids -> {
            if (uuids == null || uuids.isEmpty()) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (UUID uuid : uuids) {
                    Player seller = Bukkit.getPlayer(uuid);
                    if (seller != null && seller.isOnline()) {
                        creditPendingPayments(seller, null, null);
                    }
                }
            });
        });
    }
}
