package dev.grzybalson.modules.warp;

import dev.grzybalson.HollyCore;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class WarpListener implements Listener {

    private final HollyCore plugin;

    public WarpListener(HollyCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        plugin.getWarpManager().handleDamage(player);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getWarpManager().cancelPendingTeleport(event.getPlayer());
    }
}
