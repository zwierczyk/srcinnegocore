package dev.grzybalson.listeners;

import dev.grzybalson.gui.SettingsGUI;
import dev.grzybalson.utils.ClickCooldown;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

public class SettingsGUIListener implements Listener {

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        int topSize = event.getView().getTopInventory().getSize();
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= topSize || !(event.getWhoClicked() instanceof Player)) {
            return;
        }
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof SettingsGUI settingsGUI) {
            event.setCancelled(true);
            Player player = (Player) event.getWhoClicked();
            if (!ClickCooldown.tryUse(player, "settings-gui", 300L)) {
                return;
            }
            settingsGUI.handleClick(rawSlot);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        int topSize = event.getView().getTopInventory().getSize();
        boolean touchesTopInventory = event.getRawSlots().stream().anyMatch(slot -> slot >= 0 && slot < topSize);
        if (!touchesTopInventory) {
            return;
        }
        if (event.getView().getTopInventory().getHolder() instanceof SettingsGUI) {
            event.setCancelled(true);
        }
    }
}
