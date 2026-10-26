package dev.grzybalson.managers;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.configs.WalletConfig;
import dev.grzybalson.database.WalletDataManager;
import dev.grzybalson.modules.sigil.SigilManager;
import dev.grzybalson.modules.sigil.SigilType;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.CommandPlaceholderUtil;
import dev.grzybalson.utils.WalletItemFactory;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WalletGetbackService {

    private static final Pattern ODBIERZ_GIVE = Pattern.compile("(?i)^/?odbierz\\s+give\\s+\\S+\\s+(\\S+)\\s+(\\d+)\\s*$");
    private static final Pattern SIGIL_GIVE = Pattern.compile("(?i)^/?sigil\\s+give\\s+\\S+\\s+(\\S+)(?:\\s+([\\d.,]+))?\\s*$");
    private static final Pattern SZAMAN_GIVESHARD = Pattern.compile("(?i)^/?szaman\\s+giveshard\\s+\\S+\\s+(\\d+)\\s*$");
    private static final Pattern ITEMSHOP_GIVE = Pattern.compile("(?i)^/?(?:itemshop|nadajitemshop)\\s+give\\s+\\S+\\s+(\\S+)\\s+(\\d+)\\s*$");

    private final HollyCore plugin;

    public WalletGetbackService(HollyCore plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<GetbackResult> restoreForPlayer(UUID uuid, ProductSelection selection) {
        WalletDataManager dataManager = requireWalletDataManager();
        if (dataManager == null) {
            return CompletableFuture.completedFuture(GetbackResult.failed("Modul portfela jest wylaczony."));
        }
        return dataManager.getPurchaseLogs(uuid).thenCompose(logs -> processLogs(logs, selection));
    }

    public CompletableFuture<GetbackResult> restoreForAll(ProductSelection selection) {
        WalletDataManager dataManager = requireWalletDataManager();
        if (dataManager == null) {
            return CompletableFuture.completedFuture(GetbackResult.failed("Modul portfela jest wylaczony."));
        }
        return dataManager.getAllPurchaseLogs().thenCompose(logs -> processLogs(logs, selection));
    }

    public WalletConfig.WalletProduct getProduct(WalletProductRef ref) {
        return resolveProductRef(ref);
    }

    public ProductSelection selectionForRef(WalletProductRef ref) {
        WalletConfig.WalletProduct product = resolveProductRef(ref);
        String label = product == null ? ref.encode() : ColorUtils.stripColors(buildServiceName(product));
        return new ProductSelection(ref, product, label);
    }

    public Optional<ProductSelection> resolveSelection(String query) {
        if (query == null || query.isBlank()) {
            return Optional.empty();
        }
        String key = normalizeKey(query);
        if (key.equals("wszystko") || key.equals("all") || key.equals("*")) {
            return Optional.of(ProductSelection.all());
        }
        Optional<WalletProductRef> ref = findProductRef(query);
        if (ref.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(selectionForRef(ref.get()));
    }

    public List<String> listProductSuggestions(String partial) {
        String key = normalizeKey(partial);
        List<String> suggestions = new ArrayList<>();
        suggestions.add("wszystko");
        for (CatalogEntry entry : getCatalog()) {
            String display = ColorUtils.stripColors(entry.displayName());
            if (key.isBlank() || normalizeKey(display).contains(key)) {
                suggestions.add(display);
            }
        }
        return suggestions;
    }

    public Optional<WalletProductRef> findProductRef(String query) {
        String key = normalizeKey(query);
        WalletProductRef exact = null;
        WalletProductRef partial = null;

        for (CatalogEntry entry : getCatalog()) {
            String displayKey = normalizeKey(entry.displayName());
            if (displayKey.equals(key)) {
                exact = entry.ref();
                break;
            }
            if (partial == null && (displayKey.contains(key) || key.contains(displayKey))) {
                partial = entry.ref();
            }
        }
        return Optional.ofNullable(exact != null ? exact : partial);
    }

    public List<CatalogEntry> getCatalog() {
        List<CatalogEntry> entries = new ArrayList<>();
        WalletConfig config = plugin.getConfigManager().getWalletConfig();
        for (Map.Entry<Integer, WalletConfig.ServiceCategory> serviceEntry : config.getServices().entrySet()) {
            int serviceSlot = serviceEntry.getKey();
            WalletConfig.ServiceCategory service = serviceEntry.getValue();
            int productIndex = 0;
            for (WalletConfig.WalletProduct product : service.getCategories()) {
                entries.add(new CatalogEntry(
                    new WalletProductRef(serviceSlot, productIndex),
                    buildServiceName(product),
                    ColorUtils.stripColors(service.getGuiTitle()),
                    product.getPrice()
                ));
                productIndex++;
            }
        }
        return entries;
    }

    private CompletableFuture<GetbackResult> processLogs(List<WalletDataManager.WalletPurchaseLog> logs, ProductSelection selection) {
        PickupManager pickupManager = plugin.getPickupManager();
        if (pickupManager == null) {
            return CompletableFuture.completedFuture(GetbackResult.failed("Modul /odbierz jest wylaczony."));
        }
        if (logs.isEmpty()) {
            return CompletableFuture.completedFuture(GetbackResult.empty(selection.label()));
        }

        WalletConfig.WalletProduct selectedProduct = selection.product();
        List<WalletDataManager.WalletPurchaseLog> filtered = new ArrayList<>();
        for (WalletDataManager.WalletPurchaseLog log : logs) {
            if (selection.isAll()) {
                filtered.add(log);
                continue;
            }
            if (selectedProduct != null && logMatchesProduct(log, selectedProduct)) {
                filtered.add(log);
            }
        }

        if (filtered.isEmpty()) {
            return CompletableFuture.completedFuture(GetbackResult.emptyFiltered(selection.label()));
        }

        int purchases = 0;
        int queuedRewards = 0;
        int sigilsRestored = 0;
        int unmatched = 0;
        int missingPackages = 0;
        List<String> details = new ArrayList<>();
        List<CompletableFuture<Void>> pending = new ArrayList<>();

        for (WalletDataManager.WalletPurchaseLog log : filtered) {
            purchases++;
            Optional<WalletConfig.WalletProduct> product = selection.isAll()
                ? resolveProduct(log)
                : Optional.ofNullable(selectedProduct);

            if (product.isEmpty()) {
                unmatched++;
                appendDetail(details, "&cNie dopasowano produktu portfela: &f" + safeName(log.actorName()) + " &7-> &f" + safe(log.source()));
                continue;
            }

            OfflinePlayer target = Bukkit.getOfflinePlayer(log.uuid());
            String playerName = resolvePlayerName(target, log.actorName());
            String serviceName = buildServiceName(product.get());
            GrantSummary summary = grantFromProduct(target, playerName, product.get(), serviceName, pickupManager);
            queuedRewards += summary.queued();
            missingPackages += summary.missingPackages();
            sigilsRestored += summary.sigilsRestored();
            if (summary.sigilUnlockFuture() != null) {
                pending.add(summary.sigilUnlockFuture());
            }

            if (summary.granted() == 0) {
                unmatched++;
                appendDetail(details, "&eBrak nagrody: &f" + playerName + " &7-> &f" + safe(log.source()));
            } else {
                appendDetail(details, "&aPrzywrocono: &f" + playerName + " &7-> &f" + safe(log.source())
                    + " &8(&f" + summary.granted() + "&8)");
            }
        }

        CompletableFuture<Void> all = pending.isEmpty()
            ? CompletableFuture.completedFuture(null)
            : CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new));

        int finalPurchases = purchases;
        int finalQueuedRewards = queuedRewards;
        int finalSigilsRestored = sigilsRestored;
        int finalUnmatched = unmatched;
        int finalMissingPackages = missingPackages;
        List<String> finalDetails = List.copyOf(details);
        String productLabel = selection.label();

        return all.thenApply(ignored -> new GetbackResult(
            true,
            finalPurchases,
            finalQueuedRewards,
            finalSigilsRestored,
            finalUnmatched,
            finalMissingPackages,
            finalDetails,
            null,
            productLabel
        ));
    }

    private boolean logMatchesProduct(WalletDataManager.WalletPurchaseLog log, WalletConfig.WalletProduct product) {
        if (Math.abs(product.getPrice() - log.amount()) >= 0.001D) {
            return false;
        }
        String productName = normalizeKey(buildServiceName(product));
        String logSource = normalizeKey(log.source());
        if (productName.isBlank() || logSource.isBlank()) {
            return false;
        }
        return logSource.equals(productName) || logSource.contains(productName) || productName.contains(logSource);
    }

    private WalletConfig.WalletProduct resolveProductRef(WalletProductRef ref) {
        WalletConfig config = plugin.getConfigManager().getWalletConfig();
        WalletConfig.ServiceCategory service = config.getServices().get(ref.serviceSlot());
        if (service == null) {
            return null;
        }
        List<WalletConfig.WalletProduct> products = service.getCategories();
        if (ref.productIndex() < 0 || ref.productIndex() >= products.size()) {
            return null;
        }
        return products.get(ref.productIndex());
    }

    private GrantSummary grantFromProduct(OfflinePlayer target, String playerName, WalletConfig.WalletProduct product,
                                          String serviceName, PickupManager pickupManager) {
        int queued = 0;
        int missingPackages = 0;
        int sigilsRestored = 0;
        CompletableFuture<Void> sigilUnlockFuture = null;
        UUID uuid = target.getUniqueId();

        for (String rawCommand : allCommands(product)) {
            if (rawCommand == null || rawCommand.isBlank()) {
                continue;
            }
            String normalized = normalizeCommand(rawCommand, target, playerName, product, serviceName);

            Matcher odbierzMatcher = ODBIERZ_GIVE.matcher(normalized);
            if (odbierzMatcher.matches()) {
                String packageName = odbierzMatcher.group(1);
                int amount = parseAmount(odbierzMatcher.group(2));
                if (amount <= 0) {
                    continue;
                }
                if (!pickupManager.packageExists(packageName)) {
                    missingPackages++;
                    continue;
                }
                pickupManager.givePackage(uuid, packageName, amount);
                queued++;
                continue;
            }

            Matcher sigilMatcher = SIGIL_GIVE.matcher(normalized);
            if (sigilMatcher.matches()) {
                SigilType type = SigilType.fromString(sigilMatcher.group(1));
                double power = parsePower(sigilMatcher.group(2), 10.0D);
                if (type == null) {
                    continue;
                }
                SigilManager sigilManager = plugin.getSigilManager();
                if (sigilManager != null) {
                    sigilUnlockFuture = combine(sigilUnlockFuture, sigilManager.grantSigilUnlock(uuid, type, power));
                    sigilsRestored++;
                    ItemStack sigilItem = pickupManager.prepareGrantedItem(sigilManager.createSigilItem(type, power));
                    if (sigilItem != null) {
                        pickupManager.giveItemDirect(uuid, "portfel:" + type.name().toLowerCase(Locale.ROOT), sigilItem);
                        queued++;
                    }
                }
                continue;
            }

            Matcher shardMatcher = SZAMAN_GIVESHARD.matcher(normalized);
            if (shardMatcher.matches()) {
                int amount = parseAmount(shardMatcher.group(1));
                if (amount <= 0) {
                    continue;
                }
                SigilManager sigilManager = plugin.getSigilManager();
                if (sigilManager == null) {
                    continue;
                }
                int remaining = amount;
                while (remaining > 0) {
                    ItemStack shard = pickupManager.prepareGrantedItem(sigilManager.createShardGrantItem(remaining));
                    if (shard == null) {
                        break;
                    }
                    pickupManager.giveItemDirect(uuid, "portfel:odlamki", shard);
                    queued++;
                    remaining -= shard.getAmount();
                }
                continue;
            }

            Matcher itemshopMatcher = ITEMSHOP_GIVE.matcher(normalized);
            if (itemshopMatcher.matches()) {
                String packageName = itemshopMatcher.group(1);
                int amount = parseAmount(itemshopMatcher.group(2));
                if (amount <= 0) {
                    continue;
                }
                if (!pickupManager.packageExists(packageName)) {
                    missingPackages++;
                    continue;
                }
                pickupManager.givePackage(uuid, packageName, amount);
                queued++;
            }
        }

        return new GrantSummary(queued, missingPackages, sigilsRestored, sigilUnlockFuture);
    }

    private static CompletableFuture<Void> combine(CompletableFuture<Void> left, CompletableFuture<Void> right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return CompletableFuture.allOf(left, right).thenApply(ignored -> null);
    }

    private Optional<WalletConfig.WalletProduct> resolveProduct(WalletDataManager.WalletPurchaseLog log) {
        WalletConfig config = plugin.getConfigManager().getWalletConfig();
        String logSource = normalizeKey(log.source());
        double logAmount = log.amount();

        WalletConfig.WalletProduct best = null;
        int bestScore = -1;

        for (WalletConfig.ServiceCategory service : config.getServices().values()) {
            String serviceTitle = normalizeKey(service.getGuiTitle());
            for (WalletConfig.WalletProduct product : service.getCategories()) {
                String productName = normalizeKey(buildServiceName(product));
                int score = 0;
                if (Math.abs(product.getPrice() - logAmount) < 0.001D) {
                    score += 2;
                }
                if (!logSource.isBlank() && logSource.equals(productName)) {
                    score += 4;
                } else if (!logSource.isBlank() && (logSource.contains(productName) || productName.contains(logSource))) {
                    score += 2;
                }
                String logDetail = normalizeKey(log.detail());
                if (!logDetail.isBlank() && logDetail.equals(serviceTitle)) {
                    score += 1;
                }
                if (score > bestScore) {
                    bestScore = score;
                    best = product;
                }
            }
        }

        if (bestScore < 3) {
            return Optional.empty();
        }
        return Optional.of(best);
    }

    private static List<String> allCommands(WalletConfig.WalletProduct product) {
        List<String> commands = new ArrayList<>();
        if (product.getCommands() != null) {
            commands.addAll(product.getCommands());
        }
        if (product.getPlayerCommands() != null) {
            commands.addAll(product.getPlayerCommands());
        }
        return commands;
    }

    private String buildServiceName(WalletConfig.WalletProduct product) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("{PRICE}", format(product.getPrice()));
        placeholders.put("{price}", format(product.getPrice()));
        placeholders.put("{CURRENCY}", plugin.getConfigManager().getWalletConfig().getCurrencyName());
        return ColorUtils.colorize(WalletItemFactory.apply(product.getItem().getMeta().getDisplay(), placeholders));
    }

    private String normalizeCommand(String command, OfflinePlayer target, String playerName,
                                    WalletConfig.WalletProduct product, String serviceName) {
        String resolved = command;
        if (target instanceof org.bukkit.entity.Player player) {
            resolved = CommandPlaceholderUtil.applyPlayerPlaceholders(resolved, player);
        } else {
            resolved = applyOfflinePlaceholders(resolved, playerName, target.getUniqueId());
        }
        return CommandPlaceholderUtil.stripLeadingSlash(
            resolved
                .replace("{SERVICE}", ColorUtils.stripColors(serviceName))
                .replace("{service}", ColorUtils.stripColors(serviceName))
                .replace("{PRICE}", format(product.getPrice()))
                .replace("{price}", format(product.getPrice()))
        ).trim();
    }

    private static String applyOfflinePlaceholders(String text, String playerName, UUID uuid) {
        if (text == null) {
            return "";
        }
        String name = playerName == null ? "" : playerName;
        String uuidText = uuid.toString();
        return text
            .replace("{player}", name)
            .replace("{PLAYER}", name)
            .replace("%player%", name)
            .replace("%PLAYER%", name)
            .replace("{nick}", name)
            .replace("{NICK}", name)
            .replace("{uuid}", uuidText)
            .replace("{UUID}", uuidText)
            .replace("%uuid%", uuidText)
            .replace("%UUID%", uuidText);
    }

    private WalletDataManager requireWalletDataManager() {
        if (plugin.getWalletManager() == null) {
            return null;
        }
        return plugin.getWalletManager().getDataManager();
    }

    private static String resolvePlayerName(OfflinePlayer target, String actorName) {
        if (target.getName() != null && !target.getName().isBlank()) {
            return target.getName();
        }
        if (actorName != null && !actorName.isBlank()) {
            return actorName;
        }
        return target.getUniqueId().toString();
    }

    private static String normalizeKey(String value) {
        return ColorUtils.stripColors(value == null ? "" : value).trim().toLowerCase(Locale.ROOT);
    }

    private static String format(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private static int parseAmount(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static double parsePower(String raw, double fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Double.parseDouble(raw.replace(',', '.'));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "?" : ColorUtils.stripColors(value);
    }

    private static String safeName(String value) {
        return value == null || value.isBlank() ? "?" : value;
    }

    private static void appendDetail(List<String> details, String line) {
        if (details.size() >= 25) {
            return;
        }
        details.add(line);
    }

    public record WalletProductRef(int serviceSlot, int productIndex) {
        public String encode() {
            return serviceSlot + ":" + productIndex;
        }

        public static Optional<WalletProductRef> decode(String raw) {
            if (raw == null || raw.isBlank() || !raw.contains(":")) {
                return Optional.empty();
            }
            String[] parts = raw.split(":", 2);
            try {
                return Optional.of(new WalletProductRef(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])));
            } catch (NumberFormatException ignored) {
                return Optional.empty();
            }
        }
    }

    public record CatalogEntry(WalletProductRef ref, String displayName, String serviceTitle, double price) {
    }

    public static final class ProductSelection {
        private final WalletProductRef ref;
        private final WalletConfig.WalletProduct product;
        private final String label;

        private ProductSelection(WalletProductRef ref, WalletConfig.WalletProduct product, String label) {
            this.ref = ref;
            this.product = product;
            this.label = label;
        }

        public static ProductSelection all() {
            return new ProductSelection(null, null, "wszystkie produkty");
        }

        public static ProductSelection one(WalletProductRef ref, WalletConfig.WalletProduct product, String label) {
            return new ProductSelection(ref, product, label);
        }

        public boolean isAll() {
            return ref == null;
        }

        public WalletProductRef ref() {
            return ref;
        }

        public WalletConfig.WalletProduct product() {
            return product;
        }

        public String label() {
            return label;
        }
    }

    private record GrantSummary(int queued, int missingPackages, int sigilsRestored, CompletableFuture<Void> sigilUnlockFuture) {
        int granted() {
            return queued + sigilsRestored;
        }
    }

    public record GetbackResult(
        boolean success,
        int purchases,
        int queuedRewards,
        int sigilsRestored,
        int unmatched,
        int missingPackages,
        List<String> details,
        String error,
        String productLabel
    ) {
        public static GetbackResult failed(String error) {
            return new GetbackResult(false, 0, 0, 0, 0, 0, List.of(), error, null);
        }

        public static GetbackResult empty(String productLabel) {
            return new GetbackResult(true, 0, 0, 0, 0, 0, List.of(), null, productLabel);
        }

        public static GetbackResult emptyFiltered(String productLabel) {
            String message = productLabel == null || productLabel.isBlank()
                ? "&eBrak pasujacych zakupow w logach portfela."
                : "&eBrak zakupow produktu &f" + productLabel + " &ew logach portfela.";
            return new GetbackResult(true, 0, 0, 0, 0, 0, List.of(message), null, productLabel);
        }
    }
}
