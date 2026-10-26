package dev.grzybalson.modules.discordreward;

import dev.grzybalson.HollyCore;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

public class DiscordRewardMenuListener implements Listener {

    private final HollyCore plugin;

    public DiscordRewardMenuListener(HollyCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof DiscordRewardMenuHolder)) {
            return;
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }

        if (plugin.getDiscordRewardManager() != null) {
            plugin.getDiscordRewardManager().handleMenuClick(player, event.getRawSlot());
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof DiscordRewardMenuHolder) {
            event.setCancelled(true);
        }
    }
}
