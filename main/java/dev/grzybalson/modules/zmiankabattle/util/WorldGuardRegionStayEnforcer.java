package dev.grzybalson.modules.zmiankabattle.util;

import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import dev.grzybalson.modules.zmiankabattle.ZmiankaBattle;
import dev.grzybalson.modules.zmiankabattle.battle.Battle;

import java.util.UUID;

/**
 * Pilnuje aby w trakcie zaproszenia / bitwy gracze nie opuszczali dozwolonego regionu WorldGuard.
 *
 * - Jeśli gracz ma tylko zaproszenie (invite) i wyjdzie poza region -> invite jest anulowane.
 * - Jeśli gracz jest już w aktywnej bitwie -> bitwa jest natychmiast rozstrzygana (instant wyniki).
 */
public final class WorldGuardRegionStayEnforcer implements Listener {

    private final ZmiankaBattle plugin;

    public WorldGuardRegionStayEnforcer(ZmiankaBattle plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null) return;
        if (sameBlock(e.getFrom(), e.getTo())) return;
        checkTransition(e.getPlayer(), e.getFrom(), e.getTo());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        if (e.getTo() == null) return;
        checkTransition(e.getPlayer(), e.getFrom(), e.getTo());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        // nic nie trzymamy w pamięci, ale zostawiamy handler gdyby w przyszłości doszły cache'e
    }

    private void checkTransition(Player player, Location from, Location to) {
        if (player == null || from == null || to == null) return;

        FileConfiguration cfg = plugin.getConfig();
        if (!cfg.getBoolean("settings.worldguard.enabled", true)) return;
        if (!cfg.getBoolean("settings.worldguard.enforce-stay-in-region", true)) return;

        UUID id = player.getUniqueId();
        boolean inBattle = plugin.battles().isInBattle(id);
        boolean inInvite = !inBattle && plugin.invites().isBusy(id);
        boolean inGlobal = !inBattle && !inInvite && plugin.globals().hasOpenChallenge(id);
        if (!inBattle && !inInvite && !inGlobal) return;

        String region = cfg.getString("settings.worldguard.region", "spawn");
        if (region == null || region.isBlank()) region = "spawn";

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
            FileConfiguration cfg = plugin.getConfig();
            boolean hasCancelKey = cfg.contains("settings.worldguard.cancel-battle-on-leave");
            boolean cancelBattle = hasCancelKey
                    ? cfg.getBoolean("settings.worldguard.cancel-battle-on-leave", true)
                    : !cfg.getBoolean("settings.worldguard.instant-finish-battle-on-leave", true);

            Battle b = plugin.battles().getBattle(player.getUniqueId());
            if (b != null && !b.isFinished()) {
                if (cancelBattle) b.forceCancelAndRefund();
                else b.finishInstant();
            }
            return;
        }

        if (inInvite && plugin.getConfig().getBoolean("settings.worldguard.cancel-invite-on-leave", true)) {
            plugin.invites().cancelDueToLeavingRegion(player.getUniqueId(), region);
        }

        // Globalne zmianki (hologram) – gdy challenger wyjdzie z regionu, od razu anuluj.
        if (!inBattle && !inInvite
                && plugin.getConfig().getBoolean("settings.worldguard.cancel-global-on-leave", true)) {
            plugin.globals().cancelDueToLeavingRegion(player.getUniqueId(), region);
        }
    }

    private boolean sameBlock(Location a, Location b) {
        if (a == null || b == null) return true;
        if (a.getWorld() != null && b.getWorld() != null && !a.getWorld().equals(b.getWorld())) return false;
        return a.getBlockX() == b.getBlockX() && a.getBlockY() == b.getBlockY() && a.getBlockZ() == b.getBlockZ();
    }
}
