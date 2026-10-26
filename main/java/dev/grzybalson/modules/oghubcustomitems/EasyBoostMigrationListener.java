package dev.grzybalson.modules.oghubcustomitems;

import dev.grzybalson.HollyCore;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsGUI;
import dev.grzybalson.modules.market.MarketItem;
import dev.grzybalson.modules.market.MarketManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Podmienia gwiezdny napierśnik / gwiezdną klatę na Easy Boost przy wejściu na serwer
 * (EQ, EC, shulkery oraz oferty gracza na rynku).
 */
public final class EasyBoostMigrationListener implements Listener {

    private static final int MAX_CONTAINER_DEPTH = 4;

    private final HollyCore plugin;
    private final OgHubCustomItemsManager items;
    private final NamespacedKey customItemKey;
    private final Set<UUID> migrated = ConcurrentHashMap.newKeySet();

    public EasyBoostMigrationListener(HollyCore plugin, OgHubCustomItemsManager items) {
        this.plugin = plugin;
        this.items = items;
        this.customItemKey = new NamespacedKey(plugin, "oghub-custom-item-id");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        if (!migrated.add(id)) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            migratePlayerInventories(player);
            migrateMarketListings(player);
        }, 40L);
    }

    private void migratePlayerInventories(Player player) {
        ItemStack replacement = items.createEasyBoostItem();
        if (replacement == null) {
            return;
        }
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            ItemStack swapped = maybeReplace(stack, replacement);
            if (swapped != null) {
                inv.setItem(i, swapped);
            } else {
                migrateNested(stack, replacement);
            }
        }
        ItemStack[] armor = inv.getArmorContents();
        boolean armorChanged = false;
        for (int i = 0; i < armor.length; i++) {
            ItemStack swapped = maybeReplace(armor[i], replacement);
            if (swapped != null) {
                armor[i] = swapped;
                armorChanged = true;
            }
        }
        if (armorChanged) {
            inv.setArmorContents(armor);
        }
        ItemStack off = maybeReplace(inv.getItemInOffHand(), replacement);
        if (off != null) {
            inv.setItemInOffHand(off);
        }

        Inventory ender = player.getEnderChest();
        for (int i = 0; i < ender.getSize(); i++) {
            ItemStack stack = ender.getItem(i);
            ItemStack swapped = maybeReplace(stack, replacement);
            if (swapped != null) {
                ender.setItem(i, swapped);
            } else {
                migrateNested(stack, replacement);
            }
        }
    }

    private void migrateMarketListings(Player player) {
        MarketManager market = plugin.getMarketManager();
        if (market == null) {
            return;
        }
        ItemStack replacement = items.createEasyBoostItem();
        if (replacement == null) {
            return;
        }
        List<MarketItem> listings = new ArrayList<>(market.getPlayerItems(player));
        for (MarketItem listing : listings) {
            if (listing == null || listing.getItem() == null || !isLegacyStarItem(listing.getItem())) {
                continue;
            }
            ItemStack boosted = replacement.clone();
            boosted.setAmount(Math.max(1, listing.getItem().getAmount()));
            MarketItem updated = new MarketItem(
                listing.getId(),
                listing.getSellerUuid(),
                listing.getSellerName(),
                boosted,
                listing.getPrice(),
                listing.getListedAt(),
                listing.getCategory(),
                listing.getShowTime()
            );
            market.replaceListingItem(listing, updated);
        }
    }

    private void migrateNested(ItemStack stack, ItemStack replacement) {
        if (stack == null || !(stack.getItemMeta() instanceof BlockStateMeta meta)) {
            return;
        }
        if (!(meta.getBlockState() instanceof Container container)) {
            return;
        }
        migrateInventory(container.getInventory(), replacement, 0);
        meta.setBlockState(container);
        stack.setItemMeta(meta);
    }

    private void migrateInventory(Inventory inventory, ItemStack replacement, int depth) {
        if (inventory == null || depth > MAX_CONTAINER_DEPTH) {
            return;
        }
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            ItemStack swapped = maybeReplace(stack, replacement);
            if (swapped != null) {
                inventory.setItem(i, swapped);
            } else if (stack != null && stack.getItemMeta() instanceof BlockStateMeta meta
                && meta.getBlockState() instanceof Container container) {
                migrateInventory(container.getInventory(), replacement, depth + 1);
                meta.setBlockState(container);
                stack.setItemMeta(meta);
            }
        }
    }

    private ItemStack maybeReplace(ItemStack stack, ItemStack replacement) {
        if (!isLegacyStarItem(stack)) {
            return null;
        }
        ItemStack out = replacement.clone();
        out.setAmount(Math.max(1, stack.getAmount()));
        return out;
    }

    private boolean isLegacyStarItem(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        if (AdminItemsGUI.KEY_GWIEZDNA_KLATA != null
            && meta.getPersistentDataContainer().has(AdminItemsGUI.KEY_GWIEZDNA_KLATA, PersistentDataType.BYTE)) {
            return true;
        }
        String id = meta.getPersistentDataContainer().get(customItemKey, PersistentDataType.STRING);
        if (id != null) {
            String norm = id.toLowerCase(Locale.ROOT).replace('_', '-');
            if (norm.equals("star-chestplate") || norm.equals("starchestplate") || norm.equals("gwiezdny-napiersnik")) {
                return true;
            }
        }
        if (meta.hasDisplayName()) {
            String plain = ChatColor.stripColor(meta.getDisplayName()).toLowerCase(Locale.ROOT);
            if (plain.contains("gwiezdny") && (plain.contains("napiersnik") || plain.contains("napierśnik") || plain.contains("klata"))) {
                return true;
            }
            if (plain.contains("gwiezdna") && plain.contains("klata")) {
                return true;
            }
        }
        return false;
    }
}
