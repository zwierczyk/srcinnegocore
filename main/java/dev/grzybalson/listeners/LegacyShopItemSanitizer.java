package dev.grzybalson.listeners;

import dev.grzybalson.HollyCore;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Container;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Removes transient metadata used by old shop GUI versions from player-owned items.
 *
 * The old GUI embedded a complete Base64-serialized copy of the purchased item in
 * shop_original_data. If a GUI item escaped into an inventory or a shulker, that
 * payload was written into vanilla playerdata and could make packets/player saves
 * unnecessarily large.
 */
public final class LegacyShopItemSanitizer implements Listener {

    private static final int MAX_CONTAINER_DEPTH = 4;

    private final HollyCore plugin;
    private final NamespacedKey originalDataKey;
    private final List<NamespacedKey> transientKeys;
    private final Set<UUID> sanitizedThisRuntime = ConcurrentHashMap.newKeySet();

    public LegacyShopItemSanitizer(HollyCore plugin) {
        this.plugin = plugin;
        this.originalDataKey = new NamespacedKey(plugin, "shop_original_data");
        this.transientKeys = List.of(
            originalDataKey,
            new NamespacedKey(plugin, "shop_type"),
            new NamespacedKey(plugin, "shop_price"),
            new NamespacedKey(plugin, "shop_time_price"),
            new NamespacedKey(plugin, "shop_kills_price"),
            new NamespacedKey(plugin, "shop_action"),
            new NamespacedKey(plugin, "sell_material"),
            new NamespacedKey(plugin, "sell_price"),
            new NamespacedKey(plugin, "sell_amount"),
            new NamespacedKey(plugin, "sell_enabled")
        );
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        java.util.UUID uuid = event.getPlayer().getUniqueId();
        if (!sanitizedThisRuntime.add(uuid)) {
            return;
        }
        // Skan schodzi rekurencyjnie po shulkerach, wiec przy fali wejsc rozrzucamy go
        // po tickach zamiast uruchamiac wszystkim naraz na tym samym ticku.
        int online = plugin.getServer().getOnlinePlayers().size();
        long delay = (online > 150 ? 25L : 5L)
            + Math.floorMod(uuid.hashCode(), online > 150 ? 40 : 20);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> sanitizePlayer(event.getPlayer()), delay);
    }

    private boolean hasLegacyShopMetadata(org.bukkit.entity.Player player) {
        return hasLegacyShopMetadata(player.getInventory())
            || hasLegacyShopMetadata(player.getEnderChest());
    }

    private boolean hasLegacyShopMetadata(Inventory inventory) {
        if (inventory == null) {
            return false;
        }
        for (ItemStack item : inventory.getContents()) {
            if (containsLegacyShopMetadata(item, 0)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsLegacyShopMetadata(ItemStack item, int depth) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta() || depth > MAX_CONTAINER_DEPTH) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        for (NamespacedKey key : transientKeys) {
            if (meta.getPersistentDataContainer().has(key, PersistentDataType.STRING)) {
                return true;
            }
        }
        if (depth < MAX_CONTAINER_DEPTH && meta instanceof BlockStateMeta blockStateMeta
            && blockStateMeta.hasBlockState()
            && blockStateMeta.getBlockState() instanceof Container container) {
            for (ItemStack nested : container.getInventory().getContents()) {
                if (containsLegacyShopMetadata(nested, depth + 1)) {
                    return true;
                }
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        sanitizePlayer(event.getPlayer());
    }

    private void sanitizePlayer(org.bukkit.entity.Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (!hasLegacyShopMetadata(player)) {
            return;
        }
        CleanupStats stats = new CleanupStats();
        PlayerInventory inventory = player.getInventory();

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (sanitizeItem(item, 0, stats)) {
                inventory.setItem(slot, item);
            }
        }

        sanitizeInventory(player.getEnderChest(), 0, stats);

        ItemStack cursor = player.getItemOnCursor();
        if (sanitizeItem(cursor, 0, stats)) {
            player.setItemOnCursor(cursor);
        }

        if (stats.cleanedItems > 0) {
            long kib = (stats.removedPayloadCharacters + 1023L) / 1024L;
            plugin.getLogger().warning(
                "Usunieto stare dane GUI sklepu z " + stats.cleanedItems
                    + " itemow gracza " + player.getName()
                    + " (payload ok. " + kib + " KiB, najwiekszy: "
                    + stats.largestPayloadMaterial + " / "
                    + ((stats.largestPayloadCharacters + 1023L) / 1024L) + " KiB). "
                    + "Mogly powiekszac playerdata i powodowac dlugi zapis."
            );
        }
    }

    private void sanitizeInventory(Inventory inventory, int depth, CleanupStats stats) {
        if (inventory == null || depth > MAX_CONTAINER_DEPTH) {
            return;
        }
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (sanitizeItem(item, depth, stats)) {
                inventory.setItem(slot, item);
            }
        }
    }

    private boolean sanitizeItem(ItemStack item, int depth, CleanupStats stats) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        boolean changed = false;
        PersistentDataContainer data = meta.getPersistentDataContainer();
        String originalData = data.get(originalDataKey, PersistentDataType.STRING);
        if (originalData != null) {
            int payloadLength = originalData.length();
            stats.removedPayloadCharacters += payloadLength;
            if (payloadLength > stats.largestPayloadCharacters) {
                stats.largestPayloadCharacters = payloadLength;
                stats.largestPayloadMaterial = item.getType().name();
            }
        }

        Set<NamespacedKey> presentKeys = data.getKeys();
        for (NamespacedKey key : transientKeys) {
            if (presentKeys.contains(key)) {
                data.remove(key);
                changed = true;
            }
        }

        if (depth < MAX_CONTAINER_DEPTH && meta instanceof BlockStateMeta blockStateMeta
            && blockStateMeta.hasBlockState()
            && blockStateMeta.getBlockState() instanceof Container container) {
            CleanupStats nestedBefore = stats.copy();
            sanitizeInventory(container.getInventory(), depth + 1, stats);
            if (!stats.sameAs(nestedBefore)) {
                blockStateMeta.setBlockState(container);
                changed = true;
            }
        }

        if (!changed) {
            return false;
        }

        item.setItemMeta(meta);
        stats.cleanedItems++;
        return true;
    }

    private static final class CleanupStats {
        private int cleanedItems;
        private long removedPayloadCharacters;
        private int largestPayloadCharacters;
        private String largestPayloadMaterial = "brak";

        private CleanupStats copy() {
            CleanupStats copy = new CleanupStats();
            copy.cleanedItems = cleanedItems;
            copy.removedPayloadCharacters = removedPayloadCharacters;
            copy.largestPayloadCharacters = largestPayloadCharacters;
            copy.largestPayloadMaterial = largestPayloadMaterial;
            return copy;
        }

        private boolean sameAs(CleanupStats other) {
            return cleanedItems == other.cleanedItems
                && removedPayloadCharacters == other.removedPayloadCharacters
                && largestPayloadCharacters == other.largestPayloadCharacters;
        }
    }
}
