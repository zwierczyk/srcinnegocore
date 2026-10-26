package dev.grzybalson.modules.home;

import dev.grzybalson.HollyCore;
import dev.grzybalson.gui.home.HomeDeleteConfirmGUI;
import dev.grzybalson.gui.home.HomeEditMenuGUI;
import dev.grzybalson.gui.home.HomeIconPickerGUI;
import dev.grzybalson.gui.home.HomeMainMenuGUI;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryHolder;

public class HomeListener implements Listener {

    private final HollyCore plugin;
    private final HomeManager homeManager;

    public HomeListener(HollyCore plugin, HomeManager homeManager) {
        this.plugin = plugin;
        this.homeManager = homeManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        homeManager.getDataManager().loadPlayerHomes(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        homeManager.cancelPendingTeleport(player);
        homeManager.getDataManager().unloadPlayer(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            homeManager.handleDamage(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder == null) return;

        if (holder instanceof HomeMainMenuGUI mainMenuGUI) {
            event.setCancelled(true);
            mainMenuGUI.handleClick(event.getSlot(), event.isRightClick(), event.isShiftClick());
        } else if (holder instanceof HomeEditMenuGUI editMenuGUI) {
            event.setCancelled(true);
            editMenuGUI.handleClick(event.getSlot(), event.isRightClick());
        } else if (holder instanceof HomeDeleteConfirmGUI deleteConfirmGUI) {
            event.setCancelled(true);
            deleteConfirmGUI.handleClick(event.getSlot());
        } else if (holder instanceof HomeIconPickerGUI iconPickerGUI) {
            event.setCancelled(true);
            iconPickerGUI.handleClick(event.getSlot());
        }
    }
}
