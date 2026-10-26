package dev.grzybalson.modules.moderation;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public class ModerationListener implements Listener {

    private final ModerationManager manager;

    public ModerationListener(ModerationManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        UUID uuid = event.getUniqueId();
        ModerationPunishment ban = manager.getActiveBan(uuid);
        if (ban == null && event.getAddress() != null) {
            String ip = ModerationStorage.normalizeIp(event.getAddress().getHostAddress());
            ModerationPunishment ipBan = manager.getActiveIpBan(ip);
            if (ipBan != null) {
                ban = manager.inheritIpBan(uuid, event.getName(), ip, ipBan);
            }
        }
        if (ban == null) {
            return;
        }
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, manager.buildBanKickMessage(ban));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        manager.recordJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        manager.handleQuit(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer != null) {
            manager.recordKill(killer, victim);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChatLegacy(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        ModerationPunishment mute = manager.getActiveMute(player.getUniqueId());
        if (mute == null) {
            return;
        }
        event.setCancelled(true);
        player.sendMessage(manager.buildMuteChatMessage(mute));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        ModerationPunishment mute = manager.getActiveMute(player.getUniqueId());
        if (mute == null) {
            return;
        }
        event.setCancelled(true);
        player.sendMessage(manager.buildMuteChatMessage(mute));
    }
}
