package dev.grzybalson.listeners;

import dev.grzybalson.HollyCore;
import dev.grzybalson.database.ClanDataManager.Clan;
import dev.grzybalson.managers.ClanManager;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;

public class ClanPvpListener implements Listener {

    private final HollyCore plugin;

    public ClanPvpListener(HollyCore plugin) {
        this.plugin = plugin;
        if (plugin.getClanManager() != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                plugin.getClanManager().preloadPlayerClan(player.getUniqueId());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClanDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        Player attacker = resolveAttacker(event);
        if (attacker == null || attacker.equals(victim)) {
            return;
        }

        ClanManager clanManager = plugin.getClanManager();
        if (clanManager == null) {
            return;
        }

        boolean attackerLookupKnown = clanManager.hasCachedPlayerClanLookup(attacker.getUniqueId());
        boolean victimLookupKnown = clanManager.hasCachedPlayerClanLookup(victim.getUniqueId());
        if (!attackerLookupKnown || !victimLookupKnown) {
            event.setCancelled(true);
            clanManager.preloadPlayerClan(attacker.getUniqueId());
            clanManager.preloadPlayerClan(victim.getUniqueId());
            return;
        }

        String attackerClan = clanManager.getCachedPlayerClan(attacker.getUniqueId());
        String victimClan = clanManager.getCachedPlayerClan(victim.getUniqueId());
        if (attackerClan == null || victimClan == null) {
            return;
        }

        if (!attackerClan.equalsIgnoreCase(victimClan)) {
            return;
        }

        Clan clan = clanManager.getCachedClan(attackerClan);
        if (clan == null) {
            event.setCancelled(true);
            clanManager.getClan(attackerClan);
            return;
        }

        if (clan.isPvpEnabled()) {
            return;
        }

        event.setCancelled(true);
        attacker.sendMessage(ColorUtils.colorizeComponent(
            plugin.getConfigManager().getClanConfig().getClanFriendlyFireBlockedMessage()
                .replace("{clan}", clan.getTag())
                .replace("{player}", victim.getName())
        ));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (plugin.getClanManager() != null) {
            plugin.getClanManager().preloadPlayerClan(event.getPlayer().getUniqueId());
        }
    }

    private Player resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }

        if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }

        return null;
    }
}
