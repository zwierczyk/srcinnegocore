package dev.grzybalson.database;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.reactivestreams.client.MongoCollection;
import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.MoneyAmountUtil;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class EconomyDataManager {

    private final HollyCore plugin;
    private final MongoCollection<Document> collection;
    private final ConcurrentHashMap<UUID, Double> balanceCache;
    private final Set<UUID> dirtyBalances;
    private final BukkitTask flushTask;

    public EconomyDataManager(HollyCore plugin) {
        this.plugin = plugin;
        this.collection = plugin.getMongoManager().getCollection(
            plugin.getConfigManager().getDatabaseConfig().getEconomyCollection()
        );
        this.balanceCache = new ConcurrentHashMap<>();
        this.dirtyBalances = ConcurrentHashMap.newKeySet();

        // Flush dirty balances to MongoDB every 3 seconds (60 ticks)
        this.flushTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::flushDirtyBalances, 60L, 60L);
    }

    public CompletableFuture<Double> getBalance(UUID uuid) {
        Double cached = balanceCache.get(uuid);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        return plugin.getMongoManager().toFuture(
            collection.find(Filters.eq("uuid", uuid.toString())).first()
        ).thenApply(doc -> {
            double balance = doc == null
                ? plugin.getConfigManager().getEconomyConfig().getStartingBalance()
                : MoneyAmountUtil.sanitizeStoredBalance(doc.getDouble("balance"), plugin.getConfigManager().getEconomyConfig().getStartingBalance());
            balanceCache.putIfAbsent(uuid, balance);
            double cachedBalance = balanceCache.getOrDefault(uuid, balance);
            notifyBalanceChanged(uuid, cachedBalance);
            return cachedBalance;
        });
    }

    public CompletableFuture<Void> setBalance(UUID uuid, double amount) {
        if (!MoneyAmountUtil.isNonNegativeFinite(amount)) {
            return CompletableFuture.completedFuture(null);
        }

        balanceCache.put(uuid, amount);
        dirtyBalances.add(uuid);
        notifyBalanceChanged(uuid, amount);

        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<Boolean> addBalance(UUID uuid, double amount) {
        if (!MoneyAmountUtil.isPositiveFinite(amount)) {
            return CompletableFuture.completedFuture(false);
        }

        Boolean applied = applyCachedAdd(uuid, amount);
        if (applied != null) {
            return CompletableFuture.completedFuture(applied);
        }

        return getBalance(uuid).thenCompose(balance -> {
            // Retry the atomic path once now that getBalance has warmed the cache
            Boolean retried = applyCachedAdd(uuid, amount);
            if (retried != null) {
                return CompletableFuture.completedFuture(retried);
            }
            double newBalance = balance + amount;
            if (!MoneyAmountUtil.isNonNegativeFinite(newBalance)) {
                return CompletableFuture.completedFuture(false);
            }
            return setBalance(uuid, newBalance).thenApply(v -> true);
        });
    }

    // Returns null when the uuid is not cached, otherwise whether the delta was applied.
    // The read-modify-write happens inside a single map operation so concurrent
    // updates (driver threads vs main thread) cannot lose each other.
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
            return null;
        }
        if (!applied[0]) {
            return Boolean.FALSE;
        }
        dirtyBalances.add(uuid);
        notifyBalanceChanged(uuid, updated);
        return Boolean.TRUE;
    }

    private Boolean applyCachedRemove(UUID uuid, double amount) {
        boolean[] applied = new boolean[1];
        Double updated = balanceCache.computeIfPresent(uuid, (key, current) -> {
            if (current < amount) {
                return current;
            }
            double newBalance = current - amount;
            if (!MoneyAmountUtil.isNonNegativeFinite(newBalance)) {
                return current;
            }
            applied[0] = true;
            return newBalance;
        });

        if (updated == null) {
            return null;
        }
        if (!applied[0]) {
            return Boolean.FALSE;
        }
        dirtyBalances.add(uuid);
        notifyBalanceChanged(uuid, updated);
        return Boolean.TRUE;
    }

    public CompletableFuture<Boolean> removeBalance(UUID uuid, double amount) {
        if (!MoneyAmountUtil.isPositiveFinite(amount)) {
            return CompletableFuture.completedFuture(false);
        }

        Boolean applied = applyCachedRemove(uuid, amount);
        if (applied != null) {
            return CompletableFuture.completedFuture(applied);
        }

        return getBalance(uuid).thenCompose(balance -> {
            // Retry the atomic path once now that getBalance has warmed the cache
            Boolean retried = applyCachedRemove(uuid, amount);
            if (retried != null) {
                return CompletableFuture.completedFuture(retried);
            }
            if (balance < amount) {
                return CompletableFuture.completedFuture(false);
            }
            double newBalance = balance - amount;
            if (!MoneyAmountUtil.isNonNegativeFinite(newBalance)) {
                return CompletableFuture.completedFuture(false);
            }
            return setBalance(uuid, newBalance).thenApply(v -> true);
        });
    }

    public void flushDirtyBalances() {
        flushNow();
    }

    public CompletableFuture<Void> flushNow() {
        if (dirtyBalances.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        List<UUID> toFlush = new ArrayList<>(dirtyBalances);
        dirtyBalances.removeAll(toFlush);

        List<CompletableFuture<?>> writes = new ArrayList<>();
        for (UUID uuid : toFlush) {
            Double balance = balanceCache.get(uuid);
            if (balance != null) {
                writes.add(persistBalanceDocument(uuid, balance).exceptionally(err -> {
                    dirtyBalances.add(uuid);
                    plugin.getLogger().warning("Nie udalo sie zapisac balansu dla " + uuid + ": " + err.getMessage());
                    return null;
                }));
            }
        }

        if (writes.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.allOf(writes.toArray(new CompletableFuture[0])).thenRun(() -> {
            if (plugin.isEnabled() && plugin.getTopManager() != null) {
                plugin.getTopManager().requestRefresh();
            }
        });
    }

    public CompletableFuture<Void> flushPlayer(UUID uuid) {
        Double balance = balanceCache.get(uuid);
        if (balance == null) {
            dirtyBalances.remove(uuid);
            return CompletableFuture.completedFuture(null);
        }
        // Clear the dirty flag only once the write really landed, otherwise a failed
        // write silently rolls the balance back on the next join
        return persistBalanceDocument(uuid, balance).whenComplete((result, error) -> {
            if (error == null) {
                // Only clear the flag if nothing changed the balance while the write was in flight,
                // otherwise that newer value would never be picked up by the periodic flush
                Double current = balanceCache.get(uuid);
                if (current == null || current.doubleValue() == balance.doubleValue()) {
                    dirtyBalances.remove(uuid);
                }
                return;
            }
            dirtyBalances.add(uuid);
            plugin.getLogger().warning("Nie udalo sie zapisac balansu dla " + uuid + ": " + error.getMessage());
        });
    }

    private CompletableFuture<Void> persistBalanceDocument(UUID uuid, double balance) {
        Document doc = new Document()
            .append("uuid", uuid.toString())
            .append("balance", balance);

        return plugin.getMongoManager().toFuture(
            collection.replaceOne(
                Filters.eq("uuid", uuid.toString()),
                doc,
                new ReplaceOptions().upsert(true)
            )
        ).thenApply(result -> null);
    }

    public CompletableFuture<BalanceDump> dumpAllAccounts() {
        double fallback = plugin.getConfigManager().getEconomyConfig().getStartingBalance();
        return plugin.getMongoManager().toListFuture(collection.find()).thenApply(docs -> {
            Map<UUID, Double> balances = new LinkedHashMap<>();
            Map<UUID, String> names = new LinkedHashMap<>();
            for (Document doc : docs) {
                try {
                    String uuidStr = doc.getString("uuid");
                    if (uuidStr == null) {
                        continue;
                    }
                    UUID uuid = UUID.fromString(uuidStr);
                    double balance = MoneyAmountUtil.sanitizeStoredBalance(asDouble(doc.get("balance")), fallback);
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

    public record BalanceDump(Map<UUID, Double> balances, Map<UUID, String> names) {}

    public CompletableFuture<Void> touchPlayer(UUID uuid, String name) {
        return getBalance(uuid).thenCompose(balance -> {
            List<Bson> updates = new ArrayList<>();
            updates.add(Updates.setOnInsert("uuid", uuid.toString()));
            updates.add(Updates.setOnInsert("balance", balance));
            if (name != null && !name.isBlank()) {
                updates.add(Updates.set("name", name));
            }
            return plugin.getMongoManager().toFuture(
                collection.updateOne(
                    Filters.eq("uuid", uuid.toString()),
                    Updates.combine(updates),
                    new UpdateOptions().upsert(true)
                )
            ).thenApply(result -> null);
        });
    }

    public CompletableFuture<Boolean> hasBalance(UUID uuid, double amount) {
        if (!MoneyAmountUtil.isNonNegativeFinite(amount)) {
            return CompletableFuture.completedFuture(false);
        }
        Double cached = balanceCache.get(uuid);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached >= amount);
        }
        return getBalance(uuid).thenApply(balance -> balance >= amount);
    }

    /**
     * Flush async, potem evict — nigdy nie czyść cache przed udanym zapisem dirty.
     * Inaczej concurrent getBalance może wczytać stary Mongo i nadpisać świeższy balance.
     */
    public void invalidateCache(UUID uuid) {
        if (uuid == null) {
            return;
        }
        if (!dirtyBalances.contains(uuid) && !balanceCache.containsKey(uuid)) {
            if (plugin.getHollyPlaceholderExpansion() != null) {
                plugin.getHollyPlaceholderExpansion().invalidateBalance(uuid);
            }
            return;
        }
        flushPlayer(uuid).whenComplete((ignored, error) -> {
            // Evict dopiero po flush; jeśli w trakcie znów zabrudzone — zostaw cache.
            if (error == null && !dirtyBalances.contains(uuid)) {
                balanceCache.remove(uuid);
            }
            if (plugin.getHollyPlaceholderExpansion() != null) {
                plugin.getHollyPlaceholderExpansion().invalidateBalance(uuid);
            }
        });
    }

    public void clearAllCaches() {
        dirtyBalances.clear();
        balanceCache.clear();
    }

    public double getCachedBalance(UUID uuid) {
        return balanceCache.getOrDefault(uuid, 0.0);
    }

    private void notifyBalanceChanged(UUID uuid, double balance) {
        if (plugin.getHollyPlaceholderExpansion() != null) {
            plugin.getHollyPlaceholderExpansion().updateBalance(uuid, balance);
        }
        if (plugin.getSidebarManager() != null) {
            plugin.getSidebarManager().refreshPlayer(uuid);
        }
    }

    public void close() {
        if (flushTask != null && !flushTask.isCancelled()) {
            flushTask.cancel();
        }
        // Bounded wait so the final balances are written before MongoManager closes the client
        try {
            flushNow().get(5L, TimeUnit.SECONDS);
        } catch (Exception exception) {
            plugin.getLogger().warning("Nie udalo sie dokonczyc zapisu balansow przy wylaczaniu: " + exception.getMessage());
        }
    }
}
