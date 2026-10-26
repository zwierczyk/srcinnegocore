package dev.grzybalson.modules.anticheat;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Bicie przez cobweb / ścianę.
 * <p>
 * Reguła cobweba (niezależna od expandniętego hitboxa):
 * na dowolnym hicie PvP, jeśli na drodze oka → <b>vanilla rdzeń</b> ofiary
 * jest cobweb, który <b>nie nachodzi</b> na ten rdzeń (czyli stoi między wami,
 * a nie „w ciele” ofiary) → pusty hit.
 * Expand width/height jest ignorowany — zawsze liczymy do rdzenia 0.6×1.8.
 */
public final class HitThroughBlockCheck implements Listener {

    private static final double MIN_RAY = 0.22D;
    private static final double STEP = 0.04D;
    private static final double MAX_DIST = 7.0D;
    private static final long HIT_BLOCK_MS = 900L;

    private final AnticheatManager manager;

    public HitThroughBlockCheck(AnticheatManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!manager.getConfig().isHitThroughBlock()) {
            return;
        }
        if (!(event.getDamager() instanceof Player damager) || !(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (damager.getGameMode() == GameMode.CREATIVE || damager.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        if (manager.data(damager).getBlockHitUntil() >= System.currentTimeMillis()) {
            emptyHit(event, damager);
            return;
        }

        Location eyeLoc = damager.getEyeLocation();
        Vector eye = eyeLoc.toVector();
        World world = damager.getWorld();
        BoundingBox core = CombatHitboxes.vanillaCore(victim);
        // Lekki pad — web w którym stoi ofiara nachodzi na rdzeń.
        BoundingBox corePad = core.clone().expand(0.08D, 0.02D, 0.08D);

        for (Vector target : coreTargets(eyeLoc, eye, core)) {
            if (eye.distanceSquared(target) < MIN_RAY * MIN_RAY) {
                continue;
            }
            if (hasSeparatingCobweb(world, eye, target, corePad, damager)) {
                flagCobweb(event, damager, victim);
                return;
            }
        }

        // Ściany — tylko do najbliższego punktu rdzenia (miękciej)
        Vector wallTarget = CombatHitboxes.closestPoint(eye, core);
        if (hasSeparatingWall(world, eye, wallTarget, damager)) {
            int vl = manager.data(damager).addViolation(CheatType.HIT_THROUGH_BLOCK);
            if (vl >= 2) {
                emptyHit(event, damager);
            }
            alertIfNeeded(damager, victim, vl, false);
        }
    }

    /** Cele na vanilla rdzeniu — look-hit + closest + środek + tors + głowa. */
    private static List<Vector> coreTargets(Location eyeLoc, Vector eye, BoundingBox core) {
        List<Vector> targets = new ArrayList<>(6);
        targets.add(CombatHitboxes.closestPoint(eye, core));
        targets.add(CombatHitboxes.center(core));

        double cx = (core.getMinX() + core.getMaxX()) * 0.5D;
        double cz = (core.getMinZ() + core.getMaxZ()) * 0.5D;
        double h = core.getMaxY() - core.getMinY();
        targets.add(new Vector(cx, core.getMinY() + h * 0.55D, cz));
        targets.add(new Vector(cx, core.getMinY() + h * 0.90D, cz));

        Vector look = eyeLoc.getDirection();
        if (look.lengthSquared() > 1.0e-8) {
            RayTraceResult hit = core.rayTrace(eye, look.normalize(), MAX_DIST);
            if (hit != null && hit.getHitPosition() != null) {
                targets.add(hit.getHitPosition());
            }
        }
        return targets;
    }

    /**
     * Cobweb na odcinku eye→target, który NIE nachodzi na rdzeń ofiary
     * (= web między graczami / przed ciałem, nie „web w którym stoi”).
     */
    private static boolean hasSeparatingCobweb(
        World world,
        Vector eye,
        Vector target,
        BoundingBox victimCorePad,
        Player attacker
    ) {
        double dist = eye.distance(target);
        if (dist < MIN_RAY) {
            return false;
        }
        Vector dir = target.clone().subtract(eye).normalize();
        double travel = Math.max(0.05D, dist - 0.03D);

        int lastX = Integer.MIN_VALUE;
        int lastY = Integer.MIN_VALUE;
        int lastZ = Integer.MIN_VALUE;

        for (double d = 0.04D; d <= travel; d += STEP) {
            int bx = floor(eye.getX() + dir.getX() * d);
            int by = floor(eye.getY() + dir.getY() * d);
            int bz = floor(eye.getZ() + dir.getZ() * d);
            if (bx == lastX && by == lastY && bz == lastZ) {
                continue;
            }
            lastX = bx;
            lastY = by;
            lastZ = bz;

            Block block = world.getBlockAt(bx, by, bz);
            if (block.getType() != Material.COBWEB) {
                continue;
            }
            if (attackerStandsIn(attacker, block)) {
                continue;
            }

            BoundingBox webBox = BoundingBox.of(block);
            // Web „należy” do ofiary (stoi w nim) — nie flaguj.
            if (webBox.overlaps(victimCorePad)) {
                continue;
            }
            // Web musi realnie przecinać odcinek (nie tylko sąsiedni voxel)
            if (webBox.rayTrace(eye, dir, dist) == null) {
                continue;
            }
            // Musi być przed celem
            if (d < dist - 0.06D) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasSeparatingWall(World world, Vector eye, Vector target, Player attacker) {
        double dist = eye.distance(target);
        if (dist < MIN_RAY) {
            return false;
        }
        Vector dir = target.clone().subtract(eye).normalize();
        double travel = Math.max(0.05D, dist - 0.14D);
        int lastX = Integer.MIN_VALUE, lastY = Integer.MIN_VALUE, lastZ = Integer.MIN_VALUE;

        for (double d = 0.06D; d <= travel; d += STEP) {
            int bx = floor(eye.getX() + dir.getX() * d);
            int by = floor(eye.getY() + dir.getY() * d);
            int bz = floor(eye.getZ() + dir.getZ() * d);
            if (bx == lastX && by == lastY && bz == lastZ) {
                continue;
            }
            lastX = bx;
            lastY = by;
            lastZ = bz;

            Block block = world.getBlockAt(bx, by, bz);
            Material type = block.getType();
            if (!type.isSolid() || !type.isOccluding() || isPassableCombatBlock(type)) {
                continue;
            }
            if (attackerStandsIn(attacker, block)) {
                continue;
            }
            if (d < dist - 0.12D) {
                return true;
            }
        }
        return false;
    }

    private void flagCobweb(EntityDamageByEntityEvent event, Player damager, Player victim) {
        manager.data(damager).addViolation(CheatType.HIT_THROUGH_BLOCK);
        int vl = manager.data(damager).addViolation(CheatType.HIT_THROUGH_BLOCK);
        emptyHit(event, damager);
        alertIfNeeded(damager, victim, vl, true);
    }

    private static boolean attackerStandsIn(Player attacker, Block block) {
        return sameBlock(attacker.getLocation(), block)
            || sameBlock(attacker.getLocation().add(0.0D, 1.0D, 0.0D), block)
            || sameBlock(attacker.getEyeLocation(), block);
    }

    private static boolean sameBlock(Location loc, Block block) {
        return loc.getBlockX() == block.getX()
            && loc.getBlockY() == block.getY()
            && loc.getBlockZ() == block.getZ();
    }

    private void alertIfNeeded(Player damager, Player victim, int vl, boolean cobweb) {
        int alertAt = cobweb ? 2 : 3;
        if (vl < alertAt) {
            return;
        }
        manager.notifyStaff(damager, CheatType.HIT_THROUGH_BLOCK, vl,
            manager.getConfig().getHitThroughBlockAlert(),
            Map.of(
                "{victim}", victim.getName(),
                "{kind}", cobweb ? "cobweb" : "wall"
            ));
        if (vl >= 16) {
            manager.data(damager).resetViolation(CheatType.HIT_THROUGH_BLOCK);
        }
    }

    private void emptyHit(EntityDamageByEntityEvent event, Player damager) {
        event.setCancelled(true);
        event.setDamage(0.0D);
        manager.data(damager).setBlockHitUntil(System.currentTimeMillis() + HIT_BLOCK_MS);
    }

    private static int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }

    private static boolean isPassableCombatBlock(Material type) {
        return Tag.FENCES.isTagged(type)
            || Tag.FENCE_GATES.isTagged(type)
            || Tag.WALLS.isTagged(type)
            || Tag.SLABS.isTagged(type)
            || Tag.STAIRS.isTagged(type)
            || type.name().contains("GLASS")
            || type.name().contains("LEAF")
            || type == Material.IRON_BARS
            || type == Material.CHAIN
            || type == Material.SCAFFOLDING;
    }
}
