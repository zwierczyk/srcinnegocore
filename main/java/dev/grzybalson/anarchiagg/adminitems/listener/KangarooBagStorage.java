package dev.grzybalson.anarchiagg.adminitems.listener;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.reactivestreams.client.MongoCollection;
import dev.grzybalson.HollyCore;
import org.bson.Document;
import org.bson.types.Binary;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

final class KangarooBagStorage {

    /** 5 rzędów treści (strona). GUI = 54 (5 treści + 1 nawigacja). */
    static final int PAGE_CONTENT = 45;
    static final int GUI_SIZE = 54;
    /** @deprecated używaj PAGE_CONTENT — zostawione dla kompatybilności odczytu. */
    static final int BAG_SIZE = PAGE_CONTENT;

    private static final String COLLECTION_NAME = "kangaroo_bags";

    private final HollyCore plugin;
    private MongoCollection<Document> collection;
    private final ExecutorService saveExecutor = Executors.newSingleThreadExecutor(new StorageThreadFactory());
    private final Map<UUID, ItemStack[]> contentsById = new HashMap<>();
    private final Set<UUID> dirtyIds = new LinkedHashSet<>();
    private final Set<UUID> deletedIds = new LinkedHashSet<>();

    private CompletableFuture<Void> lastSave = CompletableFuture.completedFuture(null);
    private boolean saveScheduled;
    private boolean closed;

    KangarooBagStorage(HollyCore plugin) {
        this.plugin = plugin;
        if (plugin != null && plugin.getMongoManager() != null) {
            String prefix = plugin.getConfigManager().getDatabaseConfig().getDbPrefix();
            String name = (prefix != null && !prefix.isBlank()) ? prefix + "_" + COLLECTION_NAME : COLLECTION_NAME;
            this.collection = plugin.getMongoManager().getCollection(name);
        }
        load();
    }

    synchronized ItemStack[] get(UUID id) {
        ItemStack[] contents = this.contentsById.get(id);
        return contents == null ? new ItemStack[PAGE_CONTENT] : normalize(contents);
    }

    synchronized int pageCount(UUID id) {
        ItemStack[] contents = get(id);
        int used = highestUsedIndex(contents) + 1;
        return Math.max(1, (int) Math.ceil(used / (double) PAGE_CONTENT));
    }

    synchronized ItemStack[] getPage(UUID id, int page) {
        ItemStack[] all = get(id);
        int safePage = Math.max(0, page);
        ItemStack[] pageItems = new ItemStack[PAGE_CONTENT];
        int start = safePage * PAGE_CONTENT;
        for (int i = 0; i < PAGE_CONTENT; i++) {
            int idx = start + i;
            if (idx < all.length) {
                ItemStack item = all[idx];
                pageItems[i] = (item == null || item.getType().isAir()) ? null : item.clone();
            }
        }
        return pageItems;
    }

    synchronized void savePage(UUID id, int page, ItemStack[] pageContents) {
        if (id == null || this.closed) {
            return;
        }
        int safePage = Math.max(0, page);
        ItemStack[] all = get(id);
        int needed = (safePage + 1) * PAGE_CONTENT;
        if (all.length < needed) {
            ItemStack[] grown = new ItemStack[needed];
            System.arraycopy(all, 0, grown, 0, all.length);
            all = grown;
        }
        int start = safePage * PAGE_CONTENT;
        for (int i = 0; i < PAGE_CONTENT; i++) {
            ItemStack item = pageContents != null && i < pageContents.length ? pageContents[i] : null;
            all[start + i] = (item == null || item.getType().isAir()) ? null : item.clone();
        }
        save(id, all);
    }

    /**
     * Wrzuć itemy do worka (kolejne wolne sloty / nowe strony).
     * Zwraca listę tego, co się nie zmieściło (na razie nie limitujemy — zawsze się mieści, rosnąc).
     * Jeśli chcesz limit, można dodać MAX_PAGES.
     */
    synchronized List<ItemStack> deposit(UUID id, List<ItemStack> items) {
        List<ItemStack> leftover = new ArrayList<>();
        if (id == null || items == null || items.isEmpty() || this.closed) {
            if (items != null) {
                leftover.addAll(items);
            }
            return leftover;
        }
        ItemStack[] all = get(id);
        for (ItemStack raw : items) {
            if (raw == null || raw.getType().isAir()) {
                continue;
            }
            ItemStack remaining = raw.clone();
            // stack into existing
            for (int i = 0; i < all.length && remaining.getAmount() > 0; i++) {
                ItemStack slot = all[i];
                if (slot == null || slot.getType().isAir()) {
                    continue;
                }
                if (!slot.isSimilar(remaining)) {
                    continue;
                }
                int can = slot.getMaxStackSize() - slot.getAmount();
                if (can <= 0) {
                    continue;
                }
                int move = Math.min(can, remaining.getAmount());
                slot.setAmount(slot.getAmount() + move);
                remaining.setAmount(remaining.getAmount() - move);
            }
            while (remaining.getAmount() > 0) {
                int empty = firstEmpty(all);
                if (empty < 0) {
                    ItemStack[] grown = new ItemStack[all.length + PAGE_CONTENT];
                    System.arraycopy(all, 0, grown, 0, all.length);
                    all = grown;
                    empty = firstEmpty(all);
                }
                if (empty < 0) {
                    leftover.add(remaining.clone());
                    break;
                }
                int move = Math.min(remaining.getMaxStackSize(), remaining.getAmount());
                ItemStack place = remaining.clone();
                place.setAmount(move);
                all[empty] = place;
                remaining.setAmount(remaining.getAmount() - move);
            }
        }
        save(id, all);
        return leftover;
    }

    synchronized void save(UUID id, ItemStack[] contents) {
        if (id == null || this.closed) {
            return;
        }

        if (isEmpty(contents)) {
            this.contentsById.remove(id);
            this.dirtyIds.remove(id);
            this.deletedIds.add(id);
        } else {
            this.contentsById.put(id, normalize(contents));
            this.deletedIds.remove(id);
            this.dirtyIds.add(id);
        }

        scheduleSaveLocked();
    }

    synchronized void delete(UUID id) {
        if (id == null || this.closed) {
            return;
        }

        this.contentsById.remove(id);
        this.dirtyIds.remove(id);
        this.deletedIds.add(id);
        scheduleSaveLocked();
    }

    void shutdown() {
        CompletableFuture<Void> pendingSave;
        synchronized (this) {
            this.closed = true;
            pendingSave = scheduleSaveLocked();
        }

        if (pendingSave != null) {
            try {
                pendingSave.get(5, TimeUnit.SECONDS);
            } catch (Exception ignored) {
            }
        }

        this.saveExecutor.shutdown();
        try {
            if (!this.saveExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                this.saveExecutor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            this.saveExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void load() {
        if (this.collection == null || this.plugin == null || this.plugin.getMongoManager() == null) {
            return;
        }

        try {
            List<Document> docs = this.plugin.getMongoManager().toListFuture(this.collection.find()).join();
            if (docs == null) return;

            for (Document document : docs) {
                String rawId = document.getString("_id");
                if (rawId == null || rawId.isBlank()) continue;

                try {
                    UUID id = UUID.fromString(rawId);
                    ItemStack[] contents = deserializeContents(document);
                    if (!isEmpty(contents)) {
                        this.contentsById.put(id, contents);
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private synchronized CompletableFuture<Void> scheduleSaveLocked() {
        if (this.saveScheduled || (this.dirtyIds.isEmpty() && this.deletedIds.isEmpty())) {
            return this.lastSave;
        }

        this.saveScheduled = true;
        Map<UUID, ItemStack[]> saveSnapshot = new HashMap<>();
        for (UUID id : this.dirtyIds) {
            ItemStack[] contents = this.contentsById.get(id);
            if (contents != null && !isEmpty(contents)) {
                saveSnapshot.put(id, normalize(contents));
            }
        }

        Set<UUID> deleteSnapshot = new LinkedHashSet<>(this.deletedIds);
        this.dirtyIds.clear();
        this.deletedIds.clear();

        CompletableFuture<Void> currentSave = new CompletableFuture<>();
        this.lastSave = currentSave;

        try {
            this.saveExecutor.execute(() -> {
                try {
                    writeSnapshot(saveSnapshot, deleteSnapshot);
                    currentSave.complete(null);
                } catch (Throwable throwable) {
                    currentSave.completeExceptionally(throwable);
                } finally {
                    synchronized (KangarooBagStorage.this) {
                        KangarooBagStorage.this.saveScheduled = false;
                        if (!KangarooBagStorage.this.dirtyIds.isEmpty() || !KangarooBagStorage.this.deletedIds.isEmpty()) {
                            KangarooBagStorage.this.scheduleSaveLocked();
                        }
                    }
                }
            });
        } catch (RejectedExecutionException exception) {
            this.saveScheduled = false;
            currentSave.completeExceptionally(exception);
        }

        return currentSave;
    }

    private void writeSnapshot(Map<UUID, ItemStack[]> saveSnapshot, Set<UUID> deleteSnapshot) {
        if (this.collection == null || this.plugin == null || this.plugin.getMongoManager() == null) return;
        long now = System.currentTimeMillis();

        for (UUID id : deleteSnapshot) {
            try {
                this.plugin.getMongoManager().toFuture(this.collection.deleteOne(Filters.eq("_id", id.toString())));
            } catch (Throwable ignored) {
            }
        }

        for (Map.Entry<UUID, ItemStack[]> entry : saveSnapshot.entrySet()) {
            String id = entry.getKey().toString();
            try {
                this.plugin.getMongoManager().toFuture(
                        this.collection.replaceOne(
                                Filters.eq("_id", id),
                                new Document("_id", id)
                                        .append("contents", new Binary(serializeItemsAsBytes(normalize(entry.getValue()))))
                                        .append("updatedAt", now),
                                new ReplaceOptions().upsert(true)
                        )
                );
            } catch (Throwable ignored) {
            }
        }
    }

    private ItemStack[] deserializeContents(Document document) {
        byte[] bytes = readBytes(document.get("contents"));
        if (bytes == null || bytes.length == 0) {
            return new ItemStack[PAGE_CONTENT];
        }

        try {
            return normalize(deserializeItemsFromBytes(bytes));
        } catch (RuntimeException exception) {
            return new ItemStack[PAGE_CONTENT];
        }
    }

    private byte[] readBytes(Object value) {
        if (value instanceof Binary binary) {
            return binary.getData();
        }
        if (value instanceof byte[] bytes) {
            return bytes;
        }
        return null;
    }

    private static byte[] serializeItemsAsBytes(ItemStack[] items) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);
            dataOutput.writeInt(items.length);
            for (ItemStack item : items) {
                dataOutput.writeObject(item);
            }
            dataOutput.close();
            return outputStream.toByteArray();
        } catch (Exception e) {
            return new byte[0];
        }
    }

    private static ItemStack[] deserializeItemsFromBytes(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return new ItemStack[PAGE_CONTENT];
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
            int length = dataInput.readInt();
            ItemStack[] items = new ItemStack[length];
            for (int i = 0; i < length; i++) {
                items[i] = (ItemStack) dataInput.readObject();
            }
            dataInput.close();
            return items;
        } catch (Exception e) {
            return new ItemStack[PAGE_CONTENT];
        }
    }

    /** Zachowuje pełną długość (min. 1 strona), bez obcinania starych danych. */
    private static ItemStack[] normalize(ItemStack[] source) {
        int len = PAGE_CONTENT;
        if (source != null && source.length > len) {
            int used = highestUsedIndex(source) + 1;
            len = Math.max(PAGE_CONTENT, ((used + PAGE_CONTENT - 1) / PAGE_CONTENT) * PAGE_CONTENT);
        }
        ItemStack[] result = new ItemStack[len];
        if (source == null) {
            return result;
        }
        int max = Math.min(len, source.length);
        for (int i = 0; i < max; i++) {
            ItemStack item = source[i];
            result[i] = (item == null || item.getType().isAir()) ? null : item.clone();
        }
        return result;
    }

    private static int highestUsedIndex(ItemStack[] contents) {
        if (contents == null) {
            return -1;
        }
        for (int i = contents.length - 1; i >= 0; i--) {
            ItemStack item = contents[i];
            if (item != null && !item.getType().isAir()) {
                return i;
            }
        }
        return -1;
    }

    private static int firstEmpty(ItemStack[] contents) {
        if (contents == null) {
            return -1;
        }
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType().isAir()) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isEmpty(ItemStack[] contents) {
        if (contents == null) {
            return true;
        }
        for (ItemStack item : contents) {
            if (item != null && !item.getType().isAir()) {
                return false;
            }
        }
        return true;
    }

    private static final class StorageThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, "kangaroo-bag-storage");
            thread.setDaemon(true);
            return thread;
        }
    }
}
