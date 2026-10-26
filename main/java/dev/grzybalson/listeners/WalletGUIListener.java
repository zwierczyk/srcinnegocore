package dev.grzybalson.listeners;

import dev.grzybalson.gui.WalletGUI;
import dev.grzybalson.gui.WalletConfirmGUI;
import dev.grzybalson.gui.WalletLogsGUI;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

public class WalletGUIListener implements Listener {

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        int topSize = event.getView().getTopInventory().getSize();
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= topSize || !(event.getWhoClicked() instanceof Player)) {
            return;
        }

        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof WalletGUI walletGUI) {
            event.setCancelled(true);
            walletGUI.handleClick(rawSlot);
        } else if (holder instanceof WalletConfirmGUI walletConfirmGUI) {
            event.setCancelled(true);
            walletConfirmGUI.handleClick(rawSlot);
        } else if (holder instanceof WalletLogsGUI walletLogsGUI) {
            event.setCancelled(true);
            walletLogsGUI.handleClick(rawSlot);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        int topSize = event.getView().getTopInventory().getSize();
        boolean touchesTopInventory = event.getRawSlots().stream().anyMatch(slot -> slot >= 0 && slot < topSize);
        if (!touchesTopInventory) {
            return;
        }

        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof WalletGUI || holder instanceof WalletConfirmGUI || holder instanceof WalletLogsGUI) {
            event.setCancelled(true);
        }
    }
}
