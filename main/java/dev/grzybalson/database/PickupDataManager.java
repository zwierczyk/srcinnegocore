package dev.grzybalson.database;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.reactivestreams.client.MongoCollection;
import dev.grzybalson.HollyCore;
import dev.grzybalson.config.HollyDataPaths;
import dev.grzybalson.utils.ItemSerializer;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class PickupDataManager {

    private static final String PLAYER_PICKUP_COLLECTION = "pickup_items";

    private final HollyCore plugin;
    private final File dataFile;
    private final MongoCollection<Document> playerCollection;
    private YamlConfiguration data;

    public PickupDataManager(HollyCore plugin) {
        this.plugin = plugin;
        this.dataFile = new File(HollyDataPaths.getAddonFolder(plugin, "commands"), "odbierz.yml");
        this.playerCollection = plugin.getMongoManager().getCollection(PLAYER_PICKUP_COLLECTION);
        reload();
    }

    public synchronized void reload() {
        ensureDataFile();
        this.data = YamlConfiguration.loadConfiguration(dataFile);
        appendMissingDefaults();
        if (!data.isConfigurationSection("packages")) {
            data.createSection("packages");
        }

        Map<UUID, List<PickupItem>> legacyPlayerItems = readLegacyPlayerItems();
        if (legacyPlayerItems.isEmpty()) {
            data.set("players", null);
            saveNow();
            return;
        }

        saveNow();
        migrateLegacyPlayerItems(legacyPlayerItems);
    }

    private void appendMissingDefaults() {
        try (var input = plugin.getResource("addons/commands/odbierz.yml")) {
            if (input == null) {
                return;
            }
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                new InputStreamReader(input, StandardCharsets.UTF_8)
            );
            boolean changed = false;
            for (Map.Entry<String, Object> entry : defaults.getValues(true).entrySet()) {
                if (entry.getValue() instanceof ConfigurationSection || data.contains(entry.getKey())) {
                    continue;
                }
                data.set(entry.getKey(), entry.getValue());
                changed = true;
            }
            if (changed) {
                saveNow();
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("Nie udało się uzupełnić domyślnych ustawień /odbierz: " + exception.getMessage());
        }
    }

    public CompletableFuture<Map<String, String>> loadPackages() {
        synchronized (this) {
            Map<String, String> packages = new HashMap<>();
            ConfigurationSection section = data.getConfigurationSection("packages");
            if (section != null) {
                for (String name : section.getKeys(false)) {
                    String itemData = section.getString(name + ".item-data", section.getString(name));
                    if (itemData != null && !itemData.isBlank()) {
                        packages.put(name, itemData);
                    }
                }
            }
            return CompletableFuture.completedFuture(packages);
        }
    }

    public CompletableFuture<Void> savePackage(String name, String itemData) {
        synchronized (this) {
            data.set("packages." + safeKey(name) + ".item-data", itemData);
            saveNow();
            return CompletableFuture.completedFuture(null);
        }
    }

    public CompletableFuture<List<PickupItem>> getPickupItems(UUID uuid) {
        return plugin.getMongoManager().toFuture(
            playerCollection.find(Filters.eq("_id", uuid.toString())).first()
        ).thenApply(this::readMongoItems);
    }

    public CompletableFuture<Void> addPickupItem(UUID uuid, PickupItem item) {
        Document itemDocument = pickupItemToDocument(item);
        Bson update = Updates.combine(
            Updates.setOnInsert("_id", uuid.toString()),
            Updates.setOnInsert("uuid", uuid.toString()),
            Updates.push("items", itemDocument)
        );

        return plugin.getMongoManager().toFuture(
            playerCollection.updateOne(
                Filters.eq("_id", uuid.toString()),
                update,
                new UpdateOptions().upsert(true)
            )
        ).thenApply(result -> null);
    }

    public CompletableFuture<Void> savePickupItems(UUID uuid, List<PickupItem> items) {
        if (items == null || items.isEmpty()) {
            return clearPickupItems(uuid);
        }

        List<Document> serializedItems = new ArrayList<>();
        for (PickupItem item : items) {
            serializedItems.add(pickupItemToDocument(item));
        }

        Document doc = new Document("_id", uuid.toString())
            .append("uuid", uuid.toString())
            .append("items", serializedItems);

        return plugin.getMongoManager().toFuture(
            playerCollection.replaceOne(
                Filters.eq("_id", uuid.toString()),
                doc,
                new ReplaceOptions().upsert(true)
            )
        ).thenApply(result -> null);
    }

    public CompletableFuture<Void> clearPickupItems(UUID uuid) {
        return plugin.getMongoManager().toFuture(
            playerCollection.deleteOne(Filters.eq("_id", uuid.toString()))
        ).thenApply(result -> null);
    }

    public CompletableFuture<Boolean> hasPickupItems(UUID uuid) {
        return plugin.getMongoManager().toFuture(
            playerCollection.find(Filters.and(
                Filters.eq("_id", uuid.toString()),
                Filters.exists("items.0", true)
            )).projection(Projections.include("_id")).first()
        ).thenApply(doc -> doc != null);
    }

    private Map<UUID, List<PickupItem>> readLegacyPlayerItems() {
        Map<UUID, List<PickupItem>> result = new LinkedHashMap<>();
        ConfigurationSection playersSection = data.getConfigurationSection("players");
        if (playersSection == null) {
            return result;
        }

        for (String rawUuid : playersSection.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(rawUuid);
                List<PickupItem> items = readYamlItems(uuid);
                if (!items.isEmpty()) {
                    result.put(uuid, items);
                }
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Nieprawidlowy UUID w commands/odbierz.yml -> players: " + rawUuid);
            }
        }
        return result;
    }

    private void migrateLegacyPlayerItems(Map<UUID, List<PickupItem>> legacyPlayerItems) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (Map.Entry<UUID, List<PickupItem>> entry : legacyPlayerItems.entrySet()) {
            UUID uuid = entry.getKey();
            List<PickupItem> legacyItems = entry.getValue();
            futures.add(getPickupItems(uuid).thenCompose(existingItems -> {
                List<PickupItem> mergedItems = new ArrayList<>(existingItems);
                mergedItems.addAll(legacyItems);
                return savePickupItems(uuid, mergedItems);
            }));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenRun(() -> {
                synchronized (this) {
                    data.set("players", null);
                    saveNow();
                }
                plugin.getLogger().info("Przeniesiono wpisy /odbierz graczy z YAML do MongoDB: " + legacyPlayerItems.size());
            })
            .exceptionally(exception -> {
                plugin.getLogger().warning("Nie udalo sie przeniesc wpisow /odbierz z YAML do MongoDB: " + exception.getMessage());
                return null;
            });
    }

    private List<PickupItem> readYamlItems(UUID uuid) {
        List<PickupItem> result = new ArrayList<>();
        List<Map<?, ?>> rawItems = data.getMapList(playerPath(uuid) + ".items");
        for (Map<?, ?> raw : rawItems) {
            PickupItem item = mapToPickupItem(raw);
            if (item != null) {
                result.add(item);
            }
        }
        return result;
    }

    private List<PickupItem> readMongoItems(Document doc) {
        List<PickupItem> result = new ArrayList<>();
        if (doc == null) {
            return result;
        }

        Object rawItems = doc.get("items");
        if (!(rawItems instanceof List<?> items)) {
            return result;
        }

        for (Object rawItem : items) {
            PickupItem item = null;
            if (rawItem instanceof Document document) {
                item = documentToPickupItem(document);
            } else if (rawItem instanceof Map<?, ?> map) {
                item = mapToPickupItem(map);
            }

            if (item != null) {
                result.add(item);
            }
        }
        return result;
    }

    private PickupItem documentToPickupItem(Document document) {
        String name = stringValue(document.get("name"));
        String itemData = stringValue(document.get("item-data"));
        if (itemData == null || itemData.isBlank()) {
            itemData = stringValue(document.get("itemData"));
        }
        int amount = intValue(document.get("amount"), 1);
        long timestamp = longValue(document.get("timestamp"), System.currentTimeMillis());

        if (name == null || itemData == null || itemData.isBlank()) {
            return null;
        }
        return new PickupItem(name, itemData, amount, timestamp);
    }

    private PickupItem mapToPickupItem(Map<?, ?> raw) {
        String name = stringValue(raw.get("name"));
        String itemData = stringValue(raw.get("item-data"));
        if (itemData == null || itemData.isBlank()) {
            itemData = stringValue(raw.get("itemData"));
        }
        int amount = intValue(raw.get("amount"), 1);
        long timestamp = longValue(raw.get("timestamp"), System.currentTimeMillis());

        if (name == null || itemData == null || itemData.isBlank()) {
            return null;
        }
        return new PickupItem(name, itemData, amount, timestamp);
    }

    private Document pickupItemToDocument(PickupItem item) {
        return new Document("name", item.getName())
            .append("item-data", item.getItemData())
            .append("amount", item.getAmount())
            .append("timestamp", item.getTimestamp());
    }

    private String playerPath(UUID uuid) {
        return "players." + uuid;
    }

    private String safeKey(String key) {
        return key.replace('.', '_');
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private long longValue(Object value, long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return value == null ? fallback : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private void ensureDataFile() {
        File parent = dataFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException exception) {
                plugin.getLogger().warning("Nie udalo sie utworzyc commands/odbierz.yml: " + exception.getMessage());
            }
        }
    }

    private void saveNow() {
        try {
            data.save(dataFile);
        } catch (IOException exception) {
            plugin.getLogger().warning("Nie udalo sie zapisac commands/odbierz.yml: " + exception.getMessage());
        }
    }

    public static class PickupItem {
        private final String name;
        private final String itemData;
        private final int amount;
        private final long timestamp;

        public PickupItem(String name, ItemStack item, int amount) {
            this.name = name;
            this.itemData = ItemSerializer.serializeItem(item);
            this.amount = amount;
            this.timestamp = System.currentTimeMillis();
        }

        public PickupItem(String name, String itemData, int amount, long timestamp) {
            this.name = name;
            this.itemData = itemData;
            this.amount = amount;
            this.timestamp = timestamp;
        }

        public String getName() { return name; }
        public String getItemData() { return itemData; }
        public ItemStack getItem() { return ItemSerializer.deserializeItem(itemData); }
        public int getAmount() { return amount; }
        public long getTimestamp() { return timestamp; }
    }
}
