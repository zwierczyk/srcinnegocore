package dev.grzybalson.anarchiagg.adminitems;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.reactivestreams.client.MongoCollection;
import dev.grzybalson.HollyCore;
import org.bson.Document;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AdminItemsInfoDatabase {

    private final HollyCore plugin;
    private MongoCollection<Document> collection;

    public AdminItemsInfoDatabase(HollyCore plugin) {
        this.plugin = plugin;
        if (plugin != null && plugin.getMongoManager() != null) {
            String prefix = plugin.getConfigManager().getDatabaseConfig().getDbPrefix();
            String name = (prefix != null && !prefix.isBlank()) ? prefix + "_adminitems_counts" : "adminitems_counts";
            this.collection = plugin.getMongoManager().getCollection(name);
            try {
                plugin.getMongoManager().toFuture(collection.createIndex(Indexes.descending("updatedAt")));
                plugin.getMongoManager().toFuture(collection.createIndex(Indexes.ascending("total")));
            } catch (Throwable ignored) {
            }
        }
    }

    public Document getSnapshot(String itemKey) {
        if (itemKey == null || itemKey.isBlank() || collection == null || plugin.getMongoManager() == null) return null;
        try {
            return plugin.getMongoManager().toFuture(collection.find(Filters.eq("_id", itemKey)).first()).join();
        } catch (Throwable ignored) {
            return null;
        }
    }

    public List<Document> saveSnapshots(List<AdminItemsInfoGUI.SnapshotWrite> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) return List.of();

        List<Document> saved = new ArrayList<>(snapshots.size());
        for (AdminItemsInfoGUI.SnapshotWrite snapshot : snapshots) {
            Document document = saveSnapshot(snapshot);
            if (document != null) {
                saved.add(document);
            }
        }
        return saved;
    }

    public Document saveSnapshot(AdminItemsInfoGUI.SnapshotWrite snapshot) {
        if (snapshot == null || snapshot.itemKey() == null || snapshot.itemKey().isBlank() || collection == null || plugin.getMongoManager() == null) return null;

        Document existing = getSnapshot(snapshot.itemKey());
        Map<String, Document> mergedPlayers = existingPlayers(existing);
        Set<String> onlinePlayerIds = new HashSet<>(snapshot.onlinePlayerIds() == null ? List.of() : snapshot.onlinePlayerIds());
        boolean refreshMarket = snapshot.refreshMarket();
        Map<String, Integer> preservedMarket = new HashMap<>();

        if (refreshMarket) {
            for (Document player : mergedPlayers.values()) {
                player.put("market", 0);
            }
        } else {
            for (String onlinePlayerId : onlinePlayerIds) {
                Document previous = mergedPlayers.get(onlinePlayerId);
                if (previous != null) {
                    preservedMarket.put(onlinePlayerId, intValue(previous, "market"));
                }
            }
        }

        for (String onlinePlayerId : onlinePlayerIds) {
            mergedPlayers.remove(onlinePlayerId);
        }

        List<Document> snapshotPlayers = snapshot.players() == null ? List.of() : snapshot.players();
        for (Document currentPlayer : snapshotPlayers) {
            String uuid = currentPlayer.getString("uuid");
            if (uuid == null || uuid.isBlank()) continue;

            Document mergedCurrentPlayer = new Document(currentPlayer);
            if (!refreshMarket && intValue(mergedCurrentPlayer, "market") <= 0) {
                int market = preservedMarket.getOrDefault(uuid, 0);
                if (market > 0) {
                    mergedCurrentPlayer.put("market", market);
                    mergedCurrentPlayer.put("total", intValue(mergedCurrentPlayer, "inventory")
                            + intValue(mergedCurrentPlayer, "enderChest")
                            + intValue(mergedCurrentPlayer, "inventoryShulkers")
                            + intValue(mergedCurrentPlayer, "enderChestShulkers")
                            + market);
                }
            }

            Document previous = mergedPlayers.get(uuid);
            if (previous != null && !onlinePlayerIds.contains(uuid)) {
                previous.put("name", mergedCurrentPlayer.getString("name"));
                previous.put("market", intValue(mergedCurrentPlayer, "market"));
                continue;
            }

            mergedPlayers.put(uuid, mergedCurrentPlayer);
        }

        List<Document> players = new ArrayList<>();
        int inventoryTotal = 0;
        int enderChestTotal = 0;
        int shulkerTotal = 0;
        int marketTotal = 0;

        for (Document player : mergedPlayers.values()) {
            int inventory = intValue(player, "inventory");
            int enderChest = intValue(player, "enderChest");
            int inventoryShulkers = intValue(player, "inventoryShulkers");
            int enderChestShulkers = intValue(player, "enderChestShulkers");
            int market = intValue(player, "market");
            int total = inventory + enderChest + inventoryShulkers + enderChestShulkers + market;

            if (total <= 0) continue;

            player.put("total", total);
            players.add(player);
            inventoryTotal += inventory;
            enderChestTotal += enderChest;
            shulkerTotal += inventoryShulkers + enderChestShulkers;
            marketTotal += market;
        }

        int total = inventoryTotal + enderChestTotal + shulkerTotal + marketTotal;
        Document document = new Document("_id", snapshot.itemKey())
                .append("itemKey", snapshot.itemKey())
                .append("itemName", snapshot.itemName())
                .append("total", total)
                .append("inventoryTotal", inventoryTotal)
                .append("enderChestTotal", enderChestTotal)
                .append("shulkerTotal", shulkerTotal)
                .append("marketTotal", marketTotal)
                .append("players", players)
                .append("updatedAt", snapshot.updatedAt());

        try {
            plugin.getMongoManager().toFuture(
                    collection.replaceOne(
                            Filters.eq("_id", snapshot.itemKey()),
                            document,
                            new ReplaceOptions().upsert(true)
                    )
            );
        } catch (Throwable ignored) {
        }
        return document;
    }

    private Map<String, Document> existingPlayers(Document existing) {
        Map<String, Document> players = new HashMap<>();
        if (existing == null) return players;

        List<Document> existingPlayers = existing.getList("players", Document.class, List.of());
        for (Document player : existingPlayers) {
            String uuid = player.getString("uuid");
            if (uuid == null || uuid.isBlank()) continue;
            players.put(uuid, new Document(player));
        }
        return players;
    }

    private int intValue(Document document, String key) {
        Object value = document.get(key);
        return value instanceof Number number ? number.intValue() : 0;
    }
}
