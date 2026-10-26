package dev.grzybalson.anarchiagg.ksiegi;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class KsiegiDeathListener implements Listener {
    private final JavaPlugin plugin;
    private final KsiegiManager manager;
    private final Map<UUID, List<ItemStack>> pendingReturns = new HashMap<>();

    public KsiegiDeathListener(JavaPlugin plugin, KsiegiManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        if (event.getKeepInventory()) {
            removePardonFromKeptInventory(player);
            return;
        }

        List<ItemStack> protectedItems = new ArrayList<>();
        Iterator<ItemStack> iterator = event.getDrops().iterator();
        while (iterator.hasNext()) {
            ItemStack drop = iterator.next();
            if (drop == null || drop.getType() == Material.AIR) {
                continue;
            }
            if (!manager.hasEnchant(drop, KsiegiManager.BookType.ULASKAWIENIA)) {
                continue;
            }

            ItemStack returned = manager.removeEnchantVisual(drop.clone(), KsiegiManager.BookType.ULASKAWIENIA);
            protectedItems.add(returned);
            iterator.remove();
        }

        if (!protectedItems.isEmpty()) {
            try {
                event.getItemsToKeep().addAll(protectedItems);
            } catch (Throwable ignored) {
            }
            pendingReturns.computeIfAbsent(player.getUniqueId(), ignored -> new ArrayList<>()).addAll(protectedItems);
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        givePending(event.getPlayer());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        givePending(event.getPlayer());
    }

    private void removePardonFromKeptInventory(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            removePardon(item);
        }
        for (ItemStack item : player.getInventory().getArmorContents()) {
            removePardon(item);
        }
        removePardon(player.getInventory().getItemInOffHand());
    }

    private void removePardon(ItemStack item) {
        if (item != null && item.getType() != Material.AIR && manager.hasEnchant(item, KsiegiManager.BookType.ULASKAWIENIA)) {
            manager.removeEnchantVisual(item, KsiegiManager.BookType.ULASKAWIENIA);
        }
    }

    private void givePending(Player player) {
        List<ItemStack> items = pendingReturns.remove(player.getUniqueId());
        if (items == null || items.isEmpty()) {
            return;
        }

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (ItemStack item : items) {
                if (player.getInventory().containsAtLeast(item, item.getAmount())) {
                    continue;
                }
                Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
                for (ItemStack leftover : leftovers.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                }
            }
        });
    }
}
