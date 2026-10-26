package dev.grzybalson.listeners;

import dev.grzybalson.HollyCore;
import dev.grzybalson.gui.StaticMenuGUI;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

public class StaticMenuListener implements Listener {

    private final HollyCore plugin;

    public StaticMenuListener(HollyCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof StaticMenuGUI staticMenuGUI)) {
            return;
        }

        event.setCancelled(true);

        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }

        String action = staticMenuGUI.getAction(event.getRawSlot());
        if (action == null || action.isBlank() || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        handleAction(player, staticMenuGUI, action);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof StaticMenuGUI) {
            event.setCancelled(true);
        }
    }

    private void handleAction(Player player, StaticMenuGUI staticMenuGUI, String action) {
        String normalized = action.trim();

        if (normalized.regionMatches(true, 0, "player_command:", 0, "player_command:".length())) {
            String command = normalized.substring("player_command:".length()).trim();
            if (!command.isEmpty()) {
                player.performCommand(command);
            }
            return;
        }

        if (normalized.equalsIgnoreCase("reward_toggle")) {
            plugin.getRewardNotificationManager().toggle(player.getUniqueId());
            if ("reward".equalsIgnoreCase(staticMenuGUI.getKey())) {
                plugin.getStaticMenuManager().openReward(player);
            }
        }
    }
}
