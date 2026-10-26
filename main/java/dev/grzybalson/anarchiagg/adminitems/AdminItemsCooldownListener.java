package dev.grzybalson.anarchiagg.adminitems;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import dev.grzybalson.HollyCore;

public class AdminItemsCooldownListener implements Listener {

    public AdminItemsCooldownListener(HollyCore plugin) {
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        reset(event.getPlayer());
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        reset(event.getPlayer());
    }

    private void reset(Player player) {
        AdminItemsCooldownResetter.clearVisualCooldowns(player);
    }
}
