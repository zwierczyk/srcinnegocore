package dev.grzybalson.anarchiagg.zmiankibattle.util;

import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import dev.grzybalson.anarchiagg.zmiankibattle.ZmiankaBattle;
import dev.grzybalson.anarchiagg.zmiankibattle.battle.Battle;

import java.util.UUID;

/**
 * Pilnuje aby w trakcie zaproszenia / bitwy gracze nie opuszczali dozwolonego regionu WorldGuard.
 *
 * - Jeśli gracz ma tylko zaproszenie (invite) i wyjdzie poza region -> invite jest anulowane.
 * - Jeśli gracz jest już w aktywnej bitwie -> bitwa jest natychmiast rozstrzygana (instant wyniki).
 */
public final class WorldGuardRegionStayEnforcer implements Listener {

    private final ZmiankaBattle plugin;
    private final boolean worldGuardEnabled;
    private final boolean enforceStayInRegion;
    private final String region;
    private final boolean cancelInviteOnLeave;
    private final boolean cancelGlobalOnLeave;
    private final boolean cancelBattleOnLeave;

    public WorldGuardRegionStayEnforcer(ZmiankaBattle plugin) {
        this.plugin = plugin;
        FileConfiguration cfg = plugin.getConfig();
        this.worldGuardEnabled = cfg.getBoolean("settings.worldguard.enabled", true);
        this.enforceStayInRegion = cfg.getBoolean("settings.worldguard.enforce-stay-in-region", true);
        String configuredRegion = cfg.getString("settings.worldguard.region", "spawn");
        this.region = configuredRegion == null || configuredRegion.isBlank() ? "spawn" : configuredRegion;
        this.cancelInviteOnLeave = cfg.getBoolean("settings.worldguard.cancel-invite-on-leave", true);
        this.cancelGlobalOnLeave = cfg.getBoolean("settings.worldguard.cancel-global-on-leave", true);
        boolean hasCancelKey = cfg.contains("settings.worldguard.cancel-battle-on-leave");
        this.cancelBattleOnLeave = hasCancelKey
                ? cfg.getBoolean("settings.worldguard.cancel-battle-on-leave", true)
                : !cfg.getBoolean("settings.worldguard.instant-finish-battle-on-leave", true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (!worldGuardEnabled || !enforceStayInRegion) return;
        if (e.getTo() == null) return;
        if (sameBlock(e.getFrom(), e.getTo())) return;
        checkTransition(e.getPlayer(), e.getFrom(), e.getTo());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        if (!worldGuardEnabled || !enforceStayInRegion) return;
        if (e.getTo() == null) return;
        checkTransition(e.getPlayer(), e.getFrom(), e.getTo());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        // nic nie trzymamy w pamięci, ale zostawiamy handler gdyby w przyszłości doszły cache'e
    }

    private void checkTransition(Player player, Location from, Location to) {
        if (player == null || from == null || to == null) return;

        UUID id = player.getUniqueId();
        boolean inBattle = plugin.battles().isInBattle(id);
        boolean inInvite = !inBattle && plugin.invites().isBusy(id);
        boolean inGlobal = !inBattle && !inInvite && plugin.globals().hasOpenChallenge(id);
        if (!inBattle && !inInvite && !inGlobal) return;

        // zmiana świata = na pewno opuszczenie regionu
        if (from.getWorld() != null && to.getWorld() != null && !from.getWorld().equals(to.getWorld())) {
            handleLeave(player, region, inBattle, inInvite);
            return;
        }

        // Sprawdzamy przejście: był w regionie -> wyszedł poza.
        boolean fromInside = plugin.regions().isLocationInsideRegionOrSkip(from, region);
        boolean toInside = plugin.regions().isLocationInsideRegionOrSkip(to, region);

        if (fromInside && !toInside) {
            handleLeave(player, region, inBattle, inInvite);
        }
    }

    private void handleLeave(Player player, String region, boolean inBattle, boolean inInvite) {
        if (inBattle) {
            Battle b = plugin.battles().getBattle(player.getUniqueId());
            if (b != null && !b.isFinished()) {
                if (cancelBattleOnLeave) b.forceCancelAndRefund();
                else b.finishInstant();
            }
            return;
        }

        if (inInvite && cancelInviteOnLeave) {
            plugin.invites().cancelDueToLeavingRegion(player.getUniqueId(), region);
        }

        // Globalne zmianki (hologram) – gdy challenger wyjdzie z regionu, od razu anuluj.
        if (!inBattle && !inInvite
                && cancelGlobalOnLeave) {
            plugin.globals().cancelDueToLeavingRegion(player.getUniqueId(), region);
        }
    }

    private boolean sameBlock(Location a, Location b) {
        if (a == null || b == null) return true;
        if (a.getWorld() != null && b.getWorld() != null && !a.getWorld().equals(b.getWorld())) return false;
        return a.getBlockX() == b.getBlockX() && a.getBlockY() == b.getBlockY() && a.getBlockZ() == b.getBlockZ();
    }
}
