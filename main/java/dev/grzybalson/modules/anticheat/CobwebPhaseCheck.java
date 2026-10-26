package dev.grzybalson.modules.anticheat;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wykrywa nienaturalnie szybki ruch w cobwebbie.
 * Świadomie łagodny: knockback, lag, wejście w pajęczynę i ViaVersion
 * dają krótkie skoki prędkości — nie cancelujemy od razu.
 */
public final class CobwebPhaseCheck implements Listener {

    private static final long KNOCKBACK_IGNORE_MS = 900L;
    private static final double LAG_SPIKE_DELTA = 1.35D;

    private final AnticheatManager manager;
    private final Map<UUID, Long> lastDamagedAt = new ConcurrentHashMap<>();

    public CobwebPhaseCheck(AnticheatManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            lastDamagedAt.put(player.getUniqueId(), System.currentTimeMillis());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastDamagedAt.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!manager.getConfig().isCobwebPhase()) {
            return;
        }

        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        if (player.isFlying() || player.isGliding() || player.isInsideVehicle()) {
            return;
        }
        // Knockback / damage = legalny skok prędkości.
        if (player.getNoDamageTicks() > 8) {
            return;
        }
        Long damagedAt = lastDamagedAt.get(player.getUniqueId());
        if (damagedAt != null && System.currentTimeMillis() - damagedAt < KNOCKBACK_IGNORE_MS) {
            return;
        }

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }

        if (from.getBlockX() == to.getBlockX()
            && from.getBlockY() == to.getBlockY()
            && from.getBlockZ() == to.getBlockZ()
            && from.getX() == to.getX()
            && from.getY() == to.getY()
            && from.getZ() == to.getZ()) {
            return;
        }

        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double horizontalSpeed = Math.sqrt(dx * dx + dz * dz);
        // Bazowy limit to dolna granica — poniżej niej flaga i tak nie padnie,
        // więc nie ruszamy bloków (sprawdzenie na każdym pakiecie ruchu).
        if (horizontalSpeed <= manager.getConfig().getMaxCobwebSpeed()) {
            return;
        }

        // Tylko gdy stopy są w cobwebbie — samo ocieranie głową = FP.
        if (!isFeetInCobweb(from) && !isFeetInCobweb(to)) {
            return;
        }

        double dy = to.getY() - from.getY();
        double totalDelta = Math.sqrt(dx * dx + dy * dy + dz * dz);

        // Lag / batch pakietów — duże delty nie są „phase”.
        if (totalDelta >= LAG_SPIKE_DELTA) {
            return;
        }

        double maxSpeed = manager.getConfig().getMaxCobwebSpeed();
        if (player.hasPotionEffect(PotionEffectType.SPEED)) {
            maxSpeed += 0.06D;
        }
        if (player.isSprinting()) {
            maxSpeed += 0.03D;
        }
        // Wejście w pajęczynę z rozpędu — pierwszy tick zawsze szybszy.
        if (!isFeetInCobweb(from) && isFeetInCobweb(to)) {
            maxSpeed += 0.12D;
        }

        if (horizontalSpeed <= maxSpeed) {
            return;
        }

        int vl = manager.data(player).addViolation(CheatType.COBWEB_PHASE);

        // Nie canceluj od razu — dopiero po serii flag (mniej rubberbanda = mniej FP).
        if (vl >= 8) {
            event.setCancelled(true);
        }
        if (vl >= 14) {
            manager.punishCobwebMove(player, 150L);
        }
        if (vl >= 10) {
            manager.notifyStaff(player, CheatType.COBWEB_PHASE, vl,
                manager.getConfig().getCobwebPhaseAlert(),
                Map.of("{speed}", String.format("%.3f", horizontalSpeed)));
            if (vl >= 18) {
                manager.data(player).resetViolation(CheatType.COBWEB_PHASE);
            }
        }
    }

    private static boolean isFeetInCobweb(Location location) {
        Block feet = location.getBlock();
        return feet.getType() == Material.COBWEB;
    }
}
