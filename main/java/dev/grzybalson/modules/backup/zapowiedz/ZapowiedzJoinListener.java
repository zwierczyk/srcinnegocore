package dev.grzybalson.modules.backup.zapowiedz;

import dev.grzybalson.HollyCore;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class ZapowiedzJoinListener implements Listener {

    private final HollyCore plugin;
    private final ZapowiedzService service;

    public ZapowiedzJoinListener(HollyCore plugin, ZapowiedzService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!service.isEnabled()) {
            return;
        }
        service.tryRewardPlayer(event.getPlayer());
    }
}
