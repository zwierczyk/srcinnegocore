package dev.grzybalson.anarchiagg.customitems;

import dev.grzybalson.anarchiagg.customitems.util.EventItemRegistry;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class EventItemContainerListener implements Listener {

    private final JavaPlugin plugin;
    private final Map<UUID, PendingSlots> pendingByPlayer = new HashMap<>();
    private final Set<UUID> scheduledPlayers = new HashSet<>();

    public EventItemContainerListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        InventoryAction action = event.getAction();
        if (action == InventoryAction.COLLECT_TO_CURSOR || action == InventoryAction.UNKNOWN) {
            queueFullScan(player);
            return;
        }

        Inventory clicked = event.getClickedInventory();
        if (clicked == null) {
            return;
        }

        if (clicked.equals(player.getInventory())) {
            queueSlot(player, event.getSlot());
        } else if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            queueFullScan(player);
        }

        int hotbarButton = event.getHotbarButton();
        if (hotbarButton >= 0) {
            queueSlot(player, hotbarButton);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int topSize = event.getView().getTopInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= topSize) {
                queueSlot(player, event.getView().convertSlot(rawSlot));
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        pendingByPlayer.remove(uuid);
        scheduledPlayers.remove(uuid);
    }

    private void queueFullScan(Player player) {
        if (!canProcess(player)) {
            return;
        }
        PendingSlots pending = pendingByPlayer.computeIfAbsent(player.getUniqueId(), ignored -> new PendingSlots());
        pending.fullScan = true;
        pending.slots.clear();
        schedule(player);
    }

    private void queueSlot(Player player, int slot) {
        if (!canProcess(player) || slot < 0 || slot > 40) {
            return;
        }
        PendingSlots pending = pendingByPlayer.computeIfAbsent(player.getUniqueId(), ignored -> new PendingSlots());
        if (!pending.fullScan) {
            pending.slots.add(slot);
        }
        schedule(player);
    }

    private boolean canProcess(Player player) {
        return player != null && player.isOnline() && plugin.isEnabled();
    }

    private void schedule(Player player) {
        UUID uuid = player.getUniqueId();
        if (!scheduledPlayers.add(uuid)) {
            return;
        }

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            scheduledPlayers.remove(uuid);
            PendingSlots pending = pendingByPlayer.remove(uuid);
            if (pending == null || !player.isOnline()) {
                return;
            }

            PlayerInventory inventory = player.getInventory();
            if (pending.fullScan) {
                for (int slot = 0; slot <= 40; slot++) {
                    normalizeSlot(inventory, slot);
                }
            } else {
                for (int slot : pending.slots) {
                    normalizeSlot(inventory, slot);
                }
            }
        }, 2L);
    }

    private void normalizeSlot(PlayerInventory inventory, int slot) {
        ItemStack item = inventory.getItem(slot);
        if (item == null || item.getType() == Material.AIR) {
            return;
        }
        if (EventItemRegistry.normalizeKnownEventItem(plugin, item)) {
            inventory.setItem(slot, item);
        }
    }

    private static final class PendingSlots {
        private boolean fullScan;
        private final Set<Integer> slots = new HashSet<>();
    }
}
