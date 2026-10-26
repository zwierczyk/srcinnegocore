package dev.grzybalson.database;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import com.mongodb.reactivestreams.client.MongoCollection;
import dev.grzybalson.HollyCore;
import dev.grzybalson.modules.market.MarketCategory;
import dev.grzybalson.modules.market.MarketItem;
import dev.grzybalson.modules.market.MarketSortType;
import dev.grzybalson.utils.ItemSerializer;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class MarketDataManager {

    private final HollyCore plugin;
    private final Map<String, MarketItem> marketCache;
    private final Map<UUID, Double> offlinePayments;
    private final Map<UUID, List<MarketItem>> expiredCache;

    public MarketDataManager(HollyCore plugin) {
        this.plugin = plugin;
        this.marketCache = new ConcurrentHashMap<>();
        this.offlinePayments = new ConcurrentHashMap<>();
        this.expiredCache = new ConcurrentHashMap<>();
    }

    public void loadAllItems() {
        long loadStart = System.currentTimeMillis();
        plugin.getMongoManager().toListFuture(getCollection().find())
            .thenAccept(docs -> applyLoadedItems(docs, loadStart))
            .thenCompose(v -> loadExpiredItems())
            .exceptionally(e -> {
                plugin.getLogger().severe("Failed to load market items: " + e.getMessage());
                return null;
            });
    }

    public void loadAllItemsSync() {
        try {
            long loadStart = System.currentTimeMillis();
            List<Document> docs = plugin.getMongoManager().toListFuture(getCollection().find()).get();
            applyLoadedItems(docs, loadStart);
            loadExpiredItems().get();
        } catch (Exception exception) {
            plugin.getLogger().severe("Failed to sync-load market items: " + exception.getMessage());
        }
    }

    private void applyLoadedItems(List<Document> docs, long loadStart) {
        Map<String, MarketItem> fresh = new HashMap<>();
        for (Document doc : docs) {
            MarketItem item = documentToMarketItem(doc);
            if (item != null) {
                fresh.put(item.getId(), item);
            }
        }
        // podmiana bez pustego okna: najpierw dokładamy świeże wpisy, potem usuwamy stare,
        // pomijając oferty wystawione już w trakcie ładowania (nie ma ich w odczytanych dokumentach)
        marketCache.putAll(fresh);
        marketCache.entrySet().removeIf(entry ->
            !fresh.containsKey(entry.getKey()) && entry.getValue().getListedAt() < loadStart);
        plugin.getLogger().info("Loaded " + fresh.size() + " market items.");
    }

    public void replaceCache(List<MarketItem> items) {
        Map<String, MarketItem> fresh = new HashMap<>();
        for (MarketItem item : items) {
            fresh.put(item.getId(), item);
        }
        marketCache.putAll(fresh);
        marketCache.keySet().retainAll(fresh.keySet());
    }

    public void putItem(MarketItem item) {
        marketCache.put(item.getId(), item);
    }

    private CompletableFuture<Void> loadExpiredItems() {
        return plugin.getMongoManager().toListFuture(getExpiredCollection().find())
            .thenAccept(docs -> {
                expiredCache.clear();
                for (Document doc : docs) {
                    MarketItem item = documentToMarketItem(doc);
                    if (item != null) {
                        expiredCache.computeIfAbsent(item.getSellerUuid(), k -> new CopyOnWriteArrayList<>()).add(item);
                    }
                }
                int total = expiredCache.values().stream().mapToInt(List::size).sum();
                plugin.getLogger().info("Loaded " + total + " expired market items.");
            });
    }

    public CompletableFuture<Void> saveItem(MarketItem item) {
        Document doc = marketItemToDocument(item);

        return plugin.getMongoManager().toFuture(
            getCollection().replaceOne(
                Filters.eq("_id", item.getId()),
                doc,
                new ReplaceOptions().upsert(true)
            )
        ).thenAccept(result -> marketCache.put(item.getId(), item));
    }

    public CompletableFuture<Void> removeItem(String itemId) {
        marketCache.remove(itemId);
        return deleteItemFromMongo(itemId);
    }

    public CompletableFuture<Void> deleteItemFromMongo(String itemId) {
        return plugin.getMongoManager().toFuture(
            getCollection().deleteOne(Filters.eq("_id", itemId))
        ).thenAccept(result -> {});
    }

    public CompletableFuture<MarketItem> claimItem(String itemId) {
        marketCache.remove(itemId);
        return plugin.getMongoManager().toFuture(
            getCollection().findOneAndDelete(Filters.eq("_id", itemId))
        ).thenApply(doc -> doc == null ? null : documentToMarketItem(doc));
    }

    /**
     * Atomowe zdjęcie oferty, gdy liczy się tylko to, czy dokument istniał.
     * W odróżnieniu od {@link #claimItem(String)} nie myli braku dokumentu z błędem deserializacji.
     */
    public CompletableFuture<Boolean> claimItemExisted(String itemId) {
        marketCache.remove(itemId);
        return plugin.getMongoManager().toFuture(
            getCollection().findOneAndDelete(Filters.eq("_id", itemId))
        ).thenApply(doc -> doc != null);
    }

    public MarketItem getItem(String itemId) {
        return marketCache.get(itemId);
    }

    public List<MarketItem> getAllItems() {
        return new ArrayList<>(marketCache.values());
    }

    public List<MarketItem> getItemsBySeller(UUID sellerUuid) {
        List<MarketItem> items = new ArrayList<>();
        for (MarketItem item : marketCache.values()) {
            if (item.getSellerUuid().equals(sellerUuid)) {
                items.add(item);
            }
        }
        return items;
    }

    public List<MarketItem> getFilteredAndSortedItems(Set<MarketCategory> filters, MarketSortType sortType) {
        List<MarketItem> items = new ArrayList<>();

        for (MarketItem item : marketCache.values()) {
            if (!item.isVisible()) {
                continue;
            }
            MarketCategory liveCategory = MarketCategory.fromItem(item.getItem());
            if (filters.isEmpty() || filters.contains(liveCategory) || filters.contains(item.getCategory())) {
                items.add(item);
            }
        }

        switch (sortType) {
            case NEWEST -> items.sort((a, b) -> Long.compare(b.getListedAt(), a.getListedAt()));
            case ASCENDING -> items.sort((a, b) -> Double.compare(a.getPrice(), b.getPrice()));
            case DESCENDING -> items.sort((a, b) -> Double.compare(b.getPrice(), a.getPrice()));
            case ENCHANT -> {
            }
        }

        return items;
    }

    public int getItemCountBySeller(UUID sellerUuid) {
        int count = 0;
        for (MarketItem item : marketCache.values()) {
            if (item.getSellerUuid().equals(sellerUuid)) {
                count++;
            }
        }
        return count;
    }

    public CompletableFuture<Void> addOfflinePayment(UUID uuid, double amount) {
        if (uuid == null || amount <= 0.0D) {
            return CompletableFuture.completedFuture(null);
        }
        offlinePayments.merge(uuid, amount, Double::sum);
        return plugin.getMongoManager().toFuture(
            getOfflinePaymentsCollection().findOneAndUpdate(
                Filters.eq("uuid", uuid.toString()),
                Updates.combine(
                    Updates.inc("amount", amount),
                    Updates.setOnInsert("uuid", uuid.toString())
                ),
                new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER)
            )
        ).thenAccept(doc -> {
            if (doc != null) {
                offlinePayments.put(uuid, readAmount(doc));
            }
        });
    }

    public CompletableFuture<Double> getAndClearOfflinePayment(UUID uuid) {
        return plugin.getMongoManager().toFuture(
            getOfflinePaymentsCollection().findOneAndDelete(Filters.eq("uuid", uuid.toString()))
        ).thenApply(doc -> {
            offlinePayments.remove(uuid);
            return doc == null ? 0.0D : readAmount(doc);
        });
    }

    public CompletableFuture<List<UUID>> listPendingPaymentUuids() {
        return plugin.getMongoManager().toListFuture(getOfflinePaymentsCollection().find())
            .thenApply(docs -> {
                List<UUID> out = new ArrayList<>();
                for (Document doc : docs) {
                    try {
                        String raw = doc.getString("uuid");
                        if (raw != null && !raw.isBlank()) {
                            out.add(UUID.fromString(raw));
                        }
                    } catch (Exception ignored) {
                    }
                }
                return out;
            });
    }

    private MongoCollection<Document> getOfflinePaymentsCollection() {
        return plugin.getMongoManager().getCollection("offline_payments");
    }

    private static double readAmount(Document doc) {
        Object raw = doc.get("amount");
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        return 0.0D;
    }

    private MongoCollection<Document> getCollection() {
        return plugin.getMongoManager().getCollection("market_items");
    }

    private MongoCollection<Document> getExpiredCollection() {
        return plugin.getMongoManager().getCollection("market_expired_items");
    }

    public CompletableFuture<Void> addExpiredItem(MarketItem item) {
        Document doc = marketItemToDocument(item);
        expiredCache.computeIfAbsent(item.getSellerUuid(), k -> new CopyOnWriteArrayList<>()).add(item);

        return plugin.getMongoManager().toFuture(
            getExpiredCollection().replaceOne(
                Filters.eq("_id", item.getId()),
                doc,
                new ReplaceOptions().upsert(true)
            )
        ).thenAccept(result -> {});
    }

    /**
     * Atomowy odbiór wygasłej oferty — tylko jeden caller dostaje dokument.
     * @return true gdy dokument usunięty (można dać item)
     */
    public CompletableFuture<Boolean> claimExpiredItem(String itemId, UUID sellerUuid) {
        if (itemId == null || sellerUuid == null) {
            return CompletableFuture.completedFuture(false);
        }
        List<MarketItem> items = expiredCache.get(sellerUuid);
        if (items != null) {
            items.removeIf(i -> i.getId().equals(itemId));
            if (items.isEmpty()) {
                expiredCache.remove(sellerUuid);
            }
        }

        return plugin.getMongoManager().toFuture(
            getExpiredCollection().findOneAndDelete(
                Filters.and(
                    Filters.eq("_id", itemId),
                    Filters.eq("sellerUuid", sellerUuid.toString())
                )
            )
        ).thenApply(doc -> doc != null);
    }

    /** @deprecated użyj {@link #claimExpiredItem(String, UUID)} — deleteOne bez checks = dupe. */
    @Deprecated
    public CompletableFuture<Void> removeExpiredItem(String itemId, UUID sellerUuid) {
        return claimExpiredItem(itemId, sellerUuid).thenAccept(ignored -> {});
    }

    public Document marketItemToDocument(MarketItem item) {
        return new Document()
            .append("_id", item.getId())
            .append("sellerUuid", item.getSellerUuid().toString())
            .append("sellerName", item.getSellerName())
            .append("itemData", ItemSerializer.serializeItem(item.getItem()))
            .append("price", item.getPrice())
            .append("listedAt", item.getListedAt())
            .append("showTime", item.getShowTime())
            .append("category", item.getCategory().name());
    }

    public MarketItem documentToMarketItem(Document doc) {
        try {
            String id = doc.getString("_id");
            UUID sellerUuid = UUID.fromString(doc.getString("sellerUuid"));
            String sellerName = doc.getString("sellerName");
            ItemStack item = ItemSerializer.deserializeItem(doc.getString("itemData"));
            double price = doc.getDouble("price");
            long listedAt = doc.getLong("listedAt");
            long showTime = doc.containsKey("showTime") ? doc.getLong("showTime") : listedAt;
            MarketCategory category = MarketCategory.valueOf(doc.getString("category"));

            if (item == null) return null;

            return new MarketItem(id, sellerUuid, sellerName, item, price, listedAt, category, showTime);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to parse market item: " + e.getMessage());
            return null;
        }
    }
    public String generateItemId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    public List<MarketItem> getExpiredItemsBySeller(UUID sellerUuid) {
        List<MarketItem> items = expiredCache.get(sellerUuid);
        return items != null ? new ArrayList<>(items) : new ArrayList<>();
    }
}
