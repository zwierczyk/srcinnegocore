package dev.grzybalson.database;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.reactivestreams.client.MongoCollection;
import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.MoneyAmountUtil;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class WalletDataManager {

    private final HollyCore plugin;
    private final MongoCollection<Document> walletCollection;
    private final MongoCollection<Document> logsCollection;
    private final Map<UUID, Double> balanceCache;
    private final Map<String, String> knownNamesByLower = new ConcurrentHashMap<>();

    public WalletDataManager(HollyCore plugin) {
        this.plugin = plugin;
        this.walletCollection = plugin.getMongoManager().getCollection("wallet");
        this.logsCollection = plugin.getMongoManager().getCollection("wallet_logs");
        this.balanceCache = new ConcurrentHashMap<>();
        prefetchKnownNames();
    }

    public CompletableFuture<Double> getBalance(UUID uuid) {
        if (balanceCache.containsKey(uuid)) {
            return CompletableFuture.completedFuture(balanceCache.get(uuid));
        }

        return plugin.getMongoManager().toFuture(walletCollection.find(Filters.eq("uuid", uuid.toString())).first())
            .thenApply(doc -> {
                double balance = doc != null
                    ? MoneyAmountUtil.sanitizeStoredBalance(asDouble(doc.get("balance")), 0.0D)
                    : 0.0D;
                balanceCache.put(uuid, balance);
                return balance;
            });
    }

    public CompletableFuture<Double> setBalance(UUID uuid, double amount, String source, String actorName, String action, String actorUuid, String detail) {
        if (!MoneyAmountUtil.isNonNegativeFinite(amount)) {
            return getBalance(uuid);
        }
        return persistBalance(uuid, amount).thenCompose(v -> addLog(uuid, source, amount, action, actorName, actorUuid, detail, amount).thenApply(ignore -> amount));
    }

    public CompletableFuture<Boolean> addBalance(UUID uuid, double amount, String source, String actorName, String action, String actorUuid, String detail) {
        return addBalance(uuid, amount, source, actorName, action, actorUuid, detail, null);
    }

    public CompletableFuture<Boolean> addBalance(UUID uuid, double amount, String source, String actorName, String action, String actorUuid, String detail, String playerName) {
        if (!MoneyAmountUtil.isPositiveFinite(amount)) {
            return CompletableFuture.completedFuture(false);
        }

        // Try atomic cache update first (race condition fix)
        Boolean applied = applyCachedAdd(uuid, amount);
        if (applied != null) {
            return logAndPersistAsync(uuid, applied ? balanceCache.get(uuid) : null, source, amount, action, actorName, actorUuid, detail, playerName, applied);
        }

        // Cache miss - load and retry atomic operation
        return getBalance(uuid).thenCompose(balance -> {
            Boolean retried = applyCachedAdd(uuid, amount);
            if (retried != null) {
                return logAndPersistAsync(uuid, retried ? balanceCache.get(uuid) : null, source, amount, action, actorName, actorUuid, detail, playerName, retried);
            }

            // Fallback non-atomic path (should rarely happen)
            double newBalance = balance + amount;
            if (!MoneyAmountUtil.isNonNegativeFinite(newBalance)) {
                return CompletableFuture.completedFuture(false);
            }
            return persistBalance(uuid, newBalance, playerName).thenCompose(v -> addLog(uuid, source, amount, action, actorName, actorUuid, detail, newBalance).thenApply(ignore -> true));
        });
    }

    public CompletableFuture<Boolean> removeBalance(UUID uuid, double amount, String source, String actorName, String action, String actorUuid, String detail) {
        return removeBalance(uuid, amount, source, actorName, action, actorUuid, detail, null);
    }

    public CompletableFuture<Boolean> removeBalance(UUID uuid, double amount, String source, String actorName, String action, String actorUuid, String detail, String playerName) {
        if (!MoneyAmountUtil.isPositiveFinite(amount)) {
            return CompletableFuture.completedFuture(false);
        }

        // Try atomic cache update first (race condition fix)
        Boolean applied = applyCachedRemove(uuid, amount);
        if (applied != null) {
            return logAndPersistAsync(uuid, applied ? balanceCache.get(uuid) : null, source, amount, action, actorName, actorUuid, detail, playerName, applied);
        }

        // Cache miss - load and retry atomic operation
        return getBalance(uuid).thenCompose(balance -> {
            Boolean retried = applyCachedRemove(uuid, amount);
            if (retried != null) {
                return logAndPersistAsync(uuid, retried ? balanceCache.get(uuid) : null, source, amount, action, actorName, actorUuid, detail, playerName, retried);
            }

            // Fallback non-atomic path
            if (balance < amount) {
                return CompletableFuture.completedFuture(false);
            }
            double newBalance = balance - amount;
            if (!MoneyAmountUtil.isNonNegativeFinite(newBalance)) {
                return CompletableFuture.completedFuture(false);
            }
            return persistBalance(uuid, newBalance, playerName).thenCompose(v -> addLog(uuid, source, amount, action, actorName, actorUuid, detail, newBalance).thenApply(ignore -> true));
        });
    }

    // Atomic add operation on cache (race condition fix)
    private Boolean applyCachedAdd(UUID uuid, double amount) {
        boolean[] applied = new boolean[1];
        Double updated = balanceCache.computeIfPresent(uuid, (key, current) -> {
            double newBalance = current + amount;
            if (!MoneyAmountUtil.isNonNegativeFinite(newBalance)) {
                return current;
            }
            applied[0] = true;
            return newBalance;
        });

        if (updated == null) {
            return null; // Not in cache
        }
        return applied[0];
    }

    // Atomic remove operation on cache (race condition fix)
    private Boolean applyCachedRemove(UUID uuid, double amount) {
        boolean[] applied = new boolean[1];
        Double updated = balanceCache.computeIfPresent(uuid, (key, current) -> {
            if (current < amount) {
                return current; // Insufficient funds
            }
            double newBalance = current - amount;
            if (!MoneyAmountUtil.isNonNegativeFinite(newBalance)) {
                return current;
            }
            applied[0] = true;
            return newBalance;
        });

        if (updated == null) {
            return null; // Not in cache
        }
        return applied[0];
    }

    // Helper to persist and log after atomic cache update
    private CompletableFuture<Boolean> logAndPersistAsync(UUID uuid, Double newBalance, String source, double amount,
                                                          String action, String actorName, String actorUuid,
                                                          String detail, String playerName, boolean success) {
        if (!success || newBalance == null) {
            return CompletableFuture.completedFuture(false);
        }

        // Persist to database asynchronously
        return persistBalance(uuid, newBalance, playerName)
            .thenCompose(v -> addLog(uuid, source, amount, action, actorName, actorUuid, detail, newBalance))
            .thenApply(ignore -> true);
    }


    private CompletableFuture<Void> persistBalance(UUID uuid, double amount) {
        return persistBalance(uuid, amount, null);
    }

    private CompletableFuture<Void> persistBalance(UUID uuid, double amount, String preferredName) {
        if (!MoneyAmountUtil.isNonNegativeFinite(amount)) {
            return CompletableFuture.completedFuture(null);
        }

        balanceCache.put(uuid, amount);
        if (plugin.getTopManager() != null) {
            plugin.getTopManager().invalidatePlayer(uuid);
            plugin.getTopManager().requestRefresh();
        }

        List<Bson> updates = new ArrayList<>();
        updates.add(Updates.set("balance", amount));
        updates.add(Updates.setOnInsert("uuid", uuid.toString()));
        String storedName = preferredName != null && !preferredName.isBlank()
            ? preferredName
            : resolveStoredName(uuid);
        if (storedName != null && !storedName.isBlank()) {
            updates.add(Updates.set("name", storedName));
            rememberName(storedName);
        }

        return plugin.getMongoManager().toFuture(
            walletCollection.updateOne(
                Filters.eq("uuid", uuid.toString()),
                Updates.combine(updates),
                new UpdateOptions().upsert(true)
            )
        ).thenApply(result -> null);
    }
    public CompletableFuture<Boolean> hasBalance(UUID uuid, double amount) {
        if (!MoneyAmountUtil.isNonNegativeFinite(amount)) {
            return CompletableFuture.completedFuture(false);
        }
        return getBalance(uuid).thenApply(balance -> balance >= amount);
    }

    public CompletableFuture<Void> addLog(UUID uuid, String source, double amount, String action, String actorName, String actorUuid, String detail, double balanceAfter) {
        Document log = new Document()
            .append("uuid", uuid.toString())
            .append("source", source)
            .append("amount", amount)
            .append("action", action)
            .append("actorName", actorName)
            .append("actorUuid", actorUuid)
            .append("detail", detail)
            .append("balanceAfter", balanceAfter)
            .append("timestamp", System.currentTimeMillis());

        return plugin.getMongoManager().toFuture(logsCollection.insertOne(log)).thenApply(ignore -> null);
    }

    public CompletableFuture<List<WalletLog>> getLogs(UUID uuid) {
        return plugin.getMongoManager().toListFuture(
            logsCollection.find(Filters.eq("uuid", uuid.toString())).sort(Sorts.descending("timestamp")).limit(54)
        ).thenApply(this::mapWalletLogs);
    }

    public CompletableFuture<List<WalletPurchaseLog>> getPurchaseLogs(UUID uuid) {
        return plugin.getMongoManager().toListFuture(
            logsCollection.find(Filters.and(
                Filters.eq("uuid", uuid.toString()),
                Filters.eq("action", "PURCHASE")
            )).sort(Sorts.descending("timestamp"))
        ).thenApply(this::mapPurchaseLogs);
    }

    public CompletableFuture<List<WalletPurchaseLog>> getAllPurchaseLogs() {
        return plugin.getMongoManager().toListFuture(
            logsCollection.find(Filters.eq("action", "PURCHASE")).sort(Sorts.descending("timestamp"))
        ).thenApply(this::mapPurchaseLogs);
    }

    public CompletableFuture<Optional<ResolvedWalletPlayer>> resolvePlayerByName(String name) {
        if (name == null || name.isBlank()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        String trimmed = name.trim();
        Pattern exactName = Pattern.compile("^" + Pattern.quote(trimmed) + "$", Pattern.CASE_INSENSITIVE);

        return plugin.getMongoManager().toFuture(
            walletCollection.find(Filters.regex("name", exactName)).first()
        ).thenCompose(walletDoc -> {
            if (walletDoc != null) {
                String uuidRaw = walletDoc.getString("uuid");
                if (uuidRaw != null) {
                    try {
                        String resolvedName = walletDoc.getString("name");
                        return CompletableFuture.completedFuture(Optional.of(
                            new ResolvedWalletPlayer(UUID.fromString(uuidRaw),
                                resolvedName != null && !resolvedName.isBlank() ? resolvedName : trimmed)
                        ));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
            return plugin.getMongoManager().toFuture(
                logsCollection.find(Filters.regex("actorName", exactName))
                    .sort(Sorts.descending("timestamp"))
                    .first()
            ).thenApply(logDoc -> {
                if (logDoc == null) {
                    return Optional.<ResolvedWalletPlayer>empty();
                }
                String uuidRaw = logDoc.getString("uuid");
                if (uuidRaw == null) {
                    return Optional.empty();
                }
                try {
                    String actorName = logDoc.getString("actorName");
                    return Optional.of(new ResolvedWalletPlayer(
                        UUID.fromString(uuidRaw),
                        actorName != null && !actorName.isBlank() ? actorName : trimmed
                    ));
                } catch (IllegalArgumentException ignored) {
                    return Optional.empty();
                }
            });
        });
    }

    public CompletableFuture<List<String>> listKnownPlayerNames() {
        return plugin.getMongoManager().toListFuture(walletCollection.find()).thenApply(walletDocs -> {
            Map<String, String> names = new LinkedHashMap<>();
            for (Document doc : walletDocs) {
                String playerName = doc.getString("name");
                if (playerName != null && !playerName.isBlank()) {
                    names.putIfAbsent(playerName.toLowerCase(Locale.ROOT), playerName);
                    rememberName(playerName);
                }
            }
            return new ArrayList<>(names.values());
        });
    }

    public List<String> listCachedPlayerNames() {
        return new ArrayList<>(knownNamesByLower.values());
    }

    public void rememberName(String name) {
        if (name == null || name.isBlank()) {
            return;
        }
        knownNamesByLower.put(name.toLowerCase(Locale.ROOT), name);
    }

    private void prefetchKnownNames() {
        listKnownPlayerNames();
    }

    private String resolveStoredName(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        Player online = Bukkit.getPlayer(uuid);
        if (online != null && online.getName() != null && !online.getName().isBlank()) {
            return online.getName();
        }
        if (plugin.getJoinInfoManager() != null) {
            String stored = plugin.getJoinInfoManager().getPlayerName(uuid);
            if (stored != null && !stored.isBlank()) {
                return stored;
            }
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        if (offline.getName() != null && !offline.getName().isBlank()) {
            return offline.getName();
        }
        return null;
    }

    private List<WalletLog> mapWalletLogs(List<Document> documents) {
        List<WalletLog> logs = new ArrayList<>();
        for (Document doc : documents) {
            logs.add(mapWalletLog(doc));
        }
        return logs;
    }

    private List<WalletPurchaseLog> mapPurchaseLogs(List<Document> documents) {
        List<WalletPurchaseLog> logs = new ArrayList<>();
        for (Document doc : documents) {
            String uuidRaw = doc.getString("uuid");
            if (uuidRaw == null) {
                continue;
            }
            try {
                logs.add(new WalletPurchaseLog(
                    UUID.fromString(uuidRaw),
                    doc.getString("source"),
                    doc.getDouble("amount") != null ? doc.getDouble("amount") : 0.0D,
                    doc.getString("detail"),
                    doc.getString("actorName"),
                    doc.getLong("timestamp") != null ? doc.getLong("timestamp") : System.currentTimeMillis()
                ));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return logs;
    }

    private WalletLog mapWalletLog(Document doc) {
        return new WalletLog(
            doc.getString("source"),
            doc.getDouble("amount") != null ? doc.getDouble("amount") : 0.0D,
            doc.getString("action"),
            doc.getString("actorName"),
            doc.getString("detail"),
            doc.getDouble("balanceAfter") != null ? doc.getDouble("balanceAfter") : 0.0D,
            doc.getLong("timestamp") != null ? doc.getLong("timestamp") : System.currentTimeMillis()
        );
    }

    public CompletableFuture<Double> getTotalRechargeValue() {
        return plugin.getMongoManager().toListFuture(logsCollection.find(Filters.eq("action", "RECHARGE")))
            .thenApply(documents -> {
                double total = 0.0D;
                for (Document doc : documents) {
                    Double amount = doc.getDouble("amount");
                    if (MoneyAmountUtil.isNonNegativeFinite(amount != null ? amount : Double.NaN)) {
                        total += amount;
                    }
                }
                return total;
            });
    }


    public CompletableFuture<Void> touchPlayer(UUID uuid, String name) {
        return getBalance(uuid).thenCompose(balance -> {
            Document doc = new Document("uuid", uuid.toString())
                .append("balance", balance);
            if (name != null && !name.isBlank()) {
                doc.append("name", name);
                rememberName(name);
            }
            return plugin.getMongoManager().toFuture(
                walletCollection.replaceOne(Filters.eq("uuid", uuid.toString()), doc, new ReplaceOptions().upsert(true))
            ).thenApply(result -> null);
        });
    }

    public CompletableFuture<BalanceDump> dumpAllAccounts() {
        return plugin.getMongoManager().toListFuture(walletCollection.find()).thenApply(docs -> {
            Map<UUID, Double> balances = new LinkedHashMap<>();
            Map<UUID, String> names = new LinkedHashMap<>();
            for (Document doc : docs) {
                try {
                    String uuidStr = doc.getString("uuid");
                    if (uuidStr == null) {
                        continue;
                    }
                    UUID uuid = UUID.fromString(uuidStr);
                    double balance = MoneyAmountUtil.sanitizeStoredBalance(asDouble(doc.get("balance")), 0.0D);
                    balances.put(uuid, balance);
                    String name = doc.getString("name");
                    if (name != null && !name.isBlank()) {
                        names.put(uuid, name);
                    }
                } catch (Throwable ignored) {
                }
            }
            overlayCache(balances);
            return new BalanceDump(balances, names);
        });
    }

    public void overlayCache(Map<UUID, Double> target) {
        target.putAll(balanceCache);
    }

    private static Double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return null;
    }

    public double getCachedBalance(UUID uuid) {
        return balanceCache.getOrDefault(uuid, 0.0D);
    }

    public record BalanceDump(Map<UUID, Double> balances, Map<UUID, String> names) {}

    public void invalidateCache(UUID uuid) {
        balanceCache.remove(uuid);
    }

    public record WalletPurchaseLog(
        UUID uuid,
        String source,
        double amount,
        String detail,
        String actorName,
        long timestamp
    ) {}

    public record ResolvedWalletPlayer(UUID uuid, String name) {}

    public static class WalletLog {
        private final String source;
        private final double amount;
        private final String action;
        private final String actorName;
        private final String detail;
        private final double balanceAfter;
        private final long timestamp;

        public WalletLog(String source, double amount, String action, String actorName, String detail, double balanceAfter, long timestamp) {
            this.source = source;
            this.amount = amount;
            this.action = action;
            this.actorName = actorName;
            this.detail = detail;
            this.balanceAfter = balanceAfter;
            this.timestamp = timestamp;
        }

        public String getSource() { return source; }
        public double getAmount() { return amount; }
        public String getAction() { return action; }
        public String getActorName() { return actorName; }
        public String getDetail() { return detail; }
        public double getBalanceAfter() { return balanceAfter; }
        public long getTimestamp() { return timestamp; }
    }
}
