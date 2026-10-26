package dev.grzybalson.anarchiagg.customitems.items;

import dev.grzybalson.anarchiagg.AnarchiaGGSettings;
import dev.grzybalson.anarchiagg.customitems.handlers.RegionHandler;
import dev.grzybalson.anarchiagg.customitems.util.EventItemRegistry;
import dev.grzybalson.anarchiagg.customitems.util.EventItemUseGuard;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class KrewWampira implements Listener {
    private static final String ITEM_KEY = "krewwampira";
    private static final int DEFAULT_MAX_STACK_SIZE = 1;

    private final JavaPlugin plugin;
    private final RegionHandler regionHandler;
    private final Set<String> blockedRegions = new HashSet<>();
    private final int cooldownSeconds;
    private final int maxStackSize;

    public KrewWampira(JavaPlugin plugin, RegionHandler regionHandler) {
        this.plugin = plugin;
        this.regionHandler = regionHandler;
        FileConfiguration eventowkiConfig = AnarchiaGGSettings.getConfig(plugin);
        List<String> regionList = eventowkiConfig.getStringList("blocked-regions");
        if (regionList.isEmpty() && !eventowkiConfig.isSet("blocked-regions")) {
            eventowkiConfig.set("blocked-regions", List.of("spawn"));
            this.blockedRegions.add("spawn");
        } else {
            this.blockedRegions.addAll(regionList);
        }

        cooldownSeconds = eventowkiConfig.getInt("eventowki.krewwampira.cooldown", 5);
        maxStackSize = 1;
    }

    private boolean isLocationBlocked(Location loc) {
        if (loc == null || loc.getWorld() == null) return true;

        for (String regionName : blockedRegions) {
            if (regionHandler.isLocationInRegion(loc, regionName)) {
                return true;
            }
        }
        return false;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        ItemStack itemInOffHand = player.getInventory().getItemInOffHand();
        boolean isMainHand = false;
        ItemStack usedItem = null;

        if (itemInHand.getType() != Material.AIR && isKrewWampira(itemInHand)) {
            usedItem = itemInHand;
            isMainHand = true;
        } else if (itemInOffHand.getType() != Material.AIR && isKrewWampira(itemInOffHand)) {
            usedItem = itemInOffHand;
            isMainHand = false;
        }

        if (usedItem == null) return;
        if (!EventItemUseGuard.tryUse(plugin, player)) return;

        event.setCancelled(true);

        if (EventItemUseGuard.hasItemCooldown(plugin, player, "krewwampira", usedItem, cooldownSeconds)) {
            return;
        }

        if (isLocationBlocked(player.getLocation())) {
            return;
        }

        consumeOne(player, usedItem, isMainHand);
        player.setHealth(player.getMaxHealth());
        EventItemUseGuard.applyItemCooldown(plugin, player, "krewwampira", usedItem, cooldownSeconds);
        schedulePlayerStacking(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();

        if (event.getClickedInventory() != null
                && event.getClickedInventory().equals(player.getInventory())
                && isKrewWampira(cursor)
                && isKrewWampira(current)
                && current.getAmount() < maxStackSize
                && cursor.isSimilar(current)) {
            int move = Math.min(maxStackSize - current.getAmount(), cursor.getAmount());
            if (move > 0) {
                event.setCancelled(true);
                current.setAmount(current.getAmount() + move);
                cursor.setAmount(cursor.getAmount() - move);
                event.setCurrentItem(applyNativeStackSize(current));
                player.setItemOnCursor(cursor.getAmount() > 0 ? applyNativeStackSize(cursor) : new ItemStack(Material.AIR));
            }
        }

        if (isKrewWampira(cursor) || isKrewWampira(current)) {
            schedulePlayerStacking(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (isKrewWampira(event.getOldCursor())) {
            schedulePlayerStacking(player);
            return;
        }

        for (ItemStack item : event.getNewItems().values()) {
            if (isKrewWampira(item)) {
                schedulePlayerStacking(player);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (isKrewWampira(event.getItem().getItemStack())) {
            schedulePlayerStacking(player);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> normalizePlayerInventory(event.getPlayer()), 20L);
    }

    private void consumeOne(Player player, ItemStack usedItem, boolean isMainHand) {
        if (usedItem.getAmount() > 1) {
            usedItem.setAmount(usedItem.getAmount() - 1);
            return;
        }

        if (isMainHand) {
            player.getInventory().setItemInMainHand(null);
        } else {
            player.getInventory().setItemInOffHand(null);
        }
    }

    private void schedulePlayerStacking(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> normalizePlayerInventory(player));
    }

    private void normalizePlayerInventory(Player player) {
        if (!player.isOnline()) {
            return;
        }

        PlayerInventory inventory = player.getInventory();
        normalizeInventory(player, inventory);
    }

    private void normalizeInventory(Player player, Inventory inventory) {
        ItemStack[] contents = inventory.getStorageContents();
        ItemStack template = null;
        int total = 0;
        List<Integer> targetSlots = new ArrayList<>();
        List<Integer> emptySlots = new ArrayList<>();

        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item == null || item.getType() == Material.AIR) {
                emptySlots.add(slot);
                continue;
            }

            if (!isKrewWampira(item)) {
                continue;
            }

            if (template == null) {
                template = item.clone();
                template.setAmount(1);
                template = applyNativeStackSize(template);
            }
            total += Math.max(0, item.getAmount());
            contents[slot] = null;
            targetSlots.add(slot);
        }

        if (template == null || total <= 0) {
            return;
        }

        targetSlots.addAll(emptySlots);
        int remaining = total;
        for (int slot : targetSlots) {
            if (remaining <= 0) {
                break;
            }
            ItemStack stack = template.clone();
            stack.setAmount(Math.min(maxStackSize, remaining));
            contents[slot] = stack;
            remaining -= stack.getAmount();
        }

        inventory.setStorageContents(contents);

        while (remaining > 0 && player.getWorld() != null) {
            ItemStack overflow = template.clone();
            overflow.setAmount(Math.min(maxStackSize, remaining));
            player.getWorld().dropItemNaturally(player.getLocation(), overflow);
            remaining -= overflow.getAmount();
        }
    }

    private boolean isKrewWampira(ItemStack item) {
        return item != null && item.getType() != Material.AIR && EventItemRegistry.matches(plugin, ITEM_KEY, item);
    }

    private ItemStack applyNativeStackSize(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return item;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        for (Method method : meta.getClass().getMethods()) {
            if (!method.getName().equals("setMaxStackSize") || method.getParameterCount() != 1) {
                continue;
            }
            try {
                method.invoke(meta, Integer.valueOf(maxStackSize));
                item.setItemMeta(meta);
            } catch (Exception ignored) {
            }
            break;
        }
        return item;
    }
}
