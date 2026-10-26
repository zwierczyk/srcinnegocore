package dev.grzybalson.listeners;

import dev.grzybalson.HollyCore;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.block.data.Openable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public class TrapdoorInteractListener implements Listener {

    private final HollyCore plugin;

    public TrapdoorInteractListener(HollyCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTrapdoorInteract(PlayerInteractEvent event) {
        if (plugin.getZoneBoxManager() == null) {
            return;
        }

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block clicked = event.getClickedBlock();
        if (clicked == null || !(clicked.getBlockData() instanceof Openable)) {
            return;
        }

        if (event.getPlayer().getGameMode() == GameMode.CREATIVE) {
            return;
        }

        event.setCancelled(true);
        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
    }
}
