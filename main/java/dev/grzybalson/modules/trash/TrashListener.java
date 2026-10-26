package dev.grzybalson.modules.trash;

import dev.grzybalson.HollyCore;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.InventoryHolder;

public class TrashListener implements Listener {

    private final HollyCore plugin;

    public TrashListener(HollyCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        // Cache holder - single expensive NMS call
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof TrashGUI) {
            event.getInventory().clear();
        }
    }
}
