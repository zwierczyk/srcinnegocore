package dev.grzybalson.managers;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.configs.WalletConfig;
import dev.grzybalson.database.WalletDataManager;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.WalletItemFactory;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class WalletProductStatsService {

    private static final long CACHE_TTL_MS = 30_000L;

    private final HollyCore plugin;
    private volatile Map<String, ProductStats> cache = Map.of();
    private volatile long cacheExpiresAt;

    public WalletProductStatsService(HollyCore plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<Map<String, ProductStats>> getStatsMap() {
        long now = System.currentTimeMillis();
        if (now < cacheExpiresAt && !cache.isEmpty()) {
            return CompletableFuture.completedFuture(cache);
        }

        WalletDataManager dataManager = plugin.getWalletManager() != null
            ? plugin.getWalletManager().getDataManager()
            : null;
        if (dataManager == null) {
            return CompletableFuture.completedFuture(Map.of());
        }

        return dataManager.getAllPurchaseLogs().thenApply(logs -> {
            Map<String, ProductStats> built = buildStats(logs);
            cache = built;
            cacheExpiresAt = System.currentTimeMillis() + CACHE_TTL_MS;
            return built;
        }).exceptionally(ex -> {
            plugin.getLogger().warning("Nie udalo sie policzyc statystyk produktow portfela: " + ex.getMessage());
            return Map.of();
        });
    }

    public ProductStats getStats(WalletConfig.WalletProduct product, Map<String, ProductStats> statsMap) {
        if (product == null || statsMap == null) {
            return ProductStats.EMPTY;
        }
        return statsMap.getOrDefault(productKey(product), ProductStats.EMPTY);
    }

    public void appendAdminLore(ItemStack item, WalletConfig.WalletProduct product, Map<String, ProductStats> statsMap) {
        WalletConfig.AdminProductStats settings = plugin.getConfigManager().getWalletConfig().getAdminProductStats();
        if (!settings.isEnabled() || item == null || product == null) {
            return;
        }

        ProductStats stats = getStats(product, statsMap);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        String currency = plugin.getConfigManager().getWalletConfig().getCurrencyName();
        for (String line : settings.getLore()) {
            if (ColorUtils.isBlankLoreLine(line)) {
                lore.add(Component.empty());
                continue;
            }
            String formatted = line
                .replace("{BUYERS}", String.valueOf(stats.buyers()))
                .replace("{buyers}", String.valueOf(stats.buyers()))
                .replace("{PURCHASES}", String.valueOf(stats.purchases()))
                .replace("{purchases}", String.valueOf(stats.purchases()))
                .replace("{REVENUE}", format(stats.revenue()))
                .replace("{revenue}", format(stats.revenue()))
                .replace("{CURRENCY}", currency)
                .replace("{currency}", currency);
            lore.add(ColorUtils.colorizeComponent(formatted).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
    }

    public void invalidateCache() {
        cache = Map.of();
        cacheExpiresAt = 0L;
    }

    public String productKey(WalletConfig.WalletProduct product) {
        return normalizeKey(buildServiceName(product)) + "|" + formatPrice(product.getPrice());
    }

    private Map<String, ProductStats> buildStats(List<WalletDataManager.WalletPurchaseLog> logs) {
        WalletConfig config = plugin.getConfigManager().getWalletConfig();
        Map<String, Set<UUID>> buyersByKey = new HashMap<>();
        Map<String, Integer> purchasesByKey = new HashMap<>();
        Map<String, Double> revenueByKey = new HashMap<>();

        // Normalizujemy zrodlo kazdego logu raz, zamiast dla kazdego produktu osobno
        String[] normalizedSources = new String[logs.size()];
        for (int i = 0; i < logs.size(); i++) {
            normalizedSources[i] = normalizeKey(logs.get(i).source());
        }

        for (Map.Entry<Integer, WalletConfig.ServiceCategory> serviceEntry : config.getServices().entrySet()) {
            for (WalletConfig.WalletProduct product : serviceEntry.getValue().getCategories()) {
                String key = productKey(product);
                buyersByKey.putIfAbsent(key, new HashSet<>());
                purchasesByKey.putIfAbsent(key, 0);
                revenueByKey.putIfAbsent(key, 0.0D);

                String productName = normalizeKey(buildServiceName(product));
                double price = product.getPrice();

                for (int i = 0; i < logs.size(); i++) {
                    WalletDataManager.WalletPurchaseLog log = logs.get(i);
                    if (!matches(log, normalizedSources[i], productName, price)) {
                        continue;
                    }
                    buyersByKey.get(key).add(log.uuid());
                    purchasesByKey.put(key, purchasesByKey.get(key) + 1);
                    revenueByKey.put(key, revenueByKey.get(key) + log.amount());
                }
            }
        }

        Map<String, ProductStats> result = new HashMap<>();
        for (String key : buyersByKey.keySet()) {
            result.put(key, new ProductStats(
                buyersByKey.get(key).size(),
                purchasesByKey.getOrDefault(key, 0),
                revenueByKey.getOrDefault(key, 0.0D)
            ));
        }
        return result;
    }

    private boolean matches(WalletDataManager.WalletPurchaseLog log, String logSource, String productName, double price) {
        if (Math.abs(price - log.amount()) >= 0.001D) {
            return false;
        }
        if (productName.isBlank() || logSource.isBlank()) {
            return false;
        }
        return logSource.equals(productName) || logSource.contains(productName) || productName.contains(logSource);
    }

    private String buildServiceName(WalletConfig.WalletProduct product) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("{PRICE}", format(product.getPrice()));
        placeholders.put("{price}", format(product.getPrice()));
        placeholders.put("{CURRENCY}", plugin.getConfigManager().getWalletConfig().getCurrencyName());
        return ColorUtils.colorize(WalletItemFactory.apply(product.getItem().getMeta().getDisplay(), placeholders));
    }

    private static String normalizeKey(String value) {
        return ColorUtils.stripColors(value == null ? "" : value).trim().toLowerCase(Locale.ROOT);
    }

    private static String format(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private static String formatPrice(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    public record ProductStats(int buyers, int purchases, double revenue) {
        public static final ProductStats EMPTY = new ProductStats(0, 0, 0.0D);
    }
}
