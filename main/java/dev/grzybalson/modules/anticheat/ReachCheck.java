package dev.grzybalson.modules.anticheat;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.Locale;
import java.util.Map;

/**
 * Reach — dystans do vanilla rdzenia (width+height).
 * Przy wykrytym expandzie hitboxa szybszy cancel.
 */
public final class ReachCheck implements Listener {

    private final AnticheatManager manager;

    public ReachCheck(AnticheatManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!manager.getConfig().isReach()) {
            return;
        }
        if (!(event.getDamager() instanceof Player damager) || !(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (damager.getGameMode() == GameMode.CREATIVE || damager.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        // Usuń blokadę cobweb - gracze mogą się bić normalnie przez pajęczyny

        Vector eyes = damager.getEyeLocation().toVector();
        BoundingBox core = CombatHitboxes.vanillaCore(victim);
        BoundingBox full = victim.getBoundingBox();

        Vector closest = CombatHitboxes.closestPoint(eyes, core);
        double reach = eyes.distance(closest);

        double max = manager.getConfig().getMaxReach();
        int ping = Math.max(0, Math.min(80, damager.getPing()));
        max += ping * 0.0025D;
        max += 0.03D;

        boolean expanded = isExpandedHitbox(full, core);
        // Expand: mierz też do full — jeśli full jest bliżej niż core o dużo, to cheat expandem.
        if (expanded) {
            Vector closestFull = CombatHitboxes.closestPoint(eyes, full);
            double reachFull = eyes.distance(closestFull);
            // Hit „zyskany" expandem: full w zasięgu, core poza
            if (reachFull <= max && reach > max) {
                CheatPlayerData data = manager.data(damager);
                int vl = data.addViolation(CheatType.REACH);
                vl = data.addViolation(CheatType.REACH);
                event.setCancelled(true);
                event.setDamage(0.0D);
                if (vl >= 4) {
                    manager.notifyStaff(damager, CheatType.REACH, vl, manager.getConfig().getReachAlert(), Map.of(
                        "{reach}", String.format(Locale.US, "%.2f", reach),
                        "{max}", String.format(Locale.US, "%.2f", max)
                    ));
                }
                if (vl >= 14) {
                    data.resetViolation(CheatType.REACH);
                }
                return;
            }
        }

        if (reach <= max) {
            return;
        }

        boolean extreme = reach > max + 1.0D || expanded;
        CheatPlayerData data = manager.data(damager);
        int vl = data.addViolation(CheatType.REACH);
        if (extreme) {
            vl = data.addViolation(CheatType.REACH);
        }

        // Przy expandzie / dużym reach — od razu pusty hit
        if (vl >= (expanded ? 3 : 5)) {
            event.setCancelled(true);
            event.setDamage(0.0D);
        } else if (vl >= 5) {
            event.setDamage(0.0D);
        }

        if (vl >= (expanded ? 3 : 6)) {
            manager.notifyStaff(damager, CheatType.REACH, vl, manager.getConfig().getReachAlert(), Map.of(
                "{reach}", String.format(Locale.US, "%.2f", reach),
                "{max}", String.format(Locale.US, "%.2f", max)
            ));
            if (vl >= 14) {
                data.resetViolation(CheatType.REACH);
            }
        }
    }

    private boolean hasCobwebBetween(Player attacker, Player victim) {
        org.bukkit.Location attackerLoc = attacker.getEyeLocation();
        org.bukkit.Location victimLoc = victim.getEyeLocation();

        Vector direction = victimLoc.toVector().subtract(attackerLoc.toVector()).normalize();
        double distance = attackerLoc.distance(victimLoc);

        // Sprawdź co 0.5 bloku między graczami
        for (double d = 0.5; d < distance; d += 0.5) {
            org.bukkit.Location checkLoc = attackerLoc.clone().add(direction.clone().multiply(d));
            if (checkLoc.getBlock().getType() == org.bukkit.Material.COBWEB) {
                return true;
            }
        }
        return false;
    }

    private static boolean isExpandedHitbox(BoundingBox full, BoundingBox core) {
        double fullW = Math.max(full.getWidthX(), full.getWidthZ());
        double coreW = Math.max(core.getWidthX(), core.getWidthZ());
        double fullH = full.getHeight();
        double coreH = core.getHeight();
        return fullW > coreW + 0.12D || fullH > coreH + 0.15D;
    }
}
