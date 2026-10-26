package dev.grzybalson.modules.ranking;

import dev.grzybalson.HollyCore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class EloListener implements Listener {

    private final HollyCore plugin;

    public EloListener(HollyCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = resolveKiller(victim);
        if (killer == null || killer.equals(victim)) {
            return;
        }

        EloManager eloManager = plugin.getEloManager();
        UUID killerUuid = killer.getUniqueId();
        UUID victimUuid = victim.getUniqueId();
        Runnable process = () -> {
            boolean onCooldown = eloManager.isOnCooldown(killerUuid, victimUuid);
            eloManager.processKill(killer, victim);
            if (!onCooldown && plugin.getClanManager() != null) {
                plugin.getClanManager().handleKill(killerUuid, victimUuid);
                plugin.getClanManager().tryDropShardOnKill(killer);
            }
        };

        if (eloManager.hasCachedElo(killerUuid) && eloManager.hasCachedElo(victimUuid)) {
            process.run();
            return;
        }

        CompletableFuture.allOf(
            eloManager.getEloAsync(killerUuid),
            eloManager.getEloAsync(victimUuid)
        ).thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (killer.isOnline()) {
                process.run();
            }
        }));
    }

    private Player resolveKiller(Player victim) {
        Player killer = victim.getKiller();
        if (killer != null && !killer.equals(victim)) {
            return killer;
        }
        if (plugin.getCombatManager() == null) {
            return null;
        }
        UUID attackerUuid = plugin.getCombatManager().getLastAttacker(victim);
        if (attackerUuid == null) {
            return null;
        }
        return Bukkit.getPlayer(attackerUuid);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        plugin.getEloManager().loadElo(event.getPlayer().getUniqueId());
    }

    /**
     * MONITOR + opóźnienie — combat logout (też MONITOR) musi najpierw przetworzyć śmierć,
     * zanim cache rankingu zostanie wyczyszczony.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.getEloManager().unloadElo(uuid), 2L);
    }
}
