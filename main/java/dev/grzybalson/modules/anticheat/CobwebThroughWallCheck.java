package dev.grzybalson.modules.anticheat;

import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Map;

/**
 * Blokuje stawianie cobwebów „przez ścianę”.
 * Raycast musi trafić w solidny blok zanim dojdzie do miejsca stawiania —
 * samo sąsiedztwo ściany przy legalnym place nie flaguje.
 */
public final class CobwebThroughWallCheck implements Listener {

    private final AnticheatManager manager;

    public CobwebThroughWallCheck(AnticheatManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!manager.getConfig().isCobwebThroughWall()) {
            return;
        }
        if (event.getBlock().getType() != Material.COBWEB) {
            return;
        }

        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        if (manager.data(player).getBlockPlaceUntil() >= System.currentTimeMillis()) {
            event.setCancelled(true);
            event.setBuild(false);
            return;
        }

        Block placed = event.getBlock();
        Block against = event.getBlockAgainst();
        Location eye = player.getEyeLocation();
        Location blockCenter = placed.getLocation().add(0.5, 0.5, 0.5);
        double distance = eye.distance(blockCenter);

        // Blisko — zawsze legalne (place przy sobie / narożniki).
        if (distance < 4.0) {
            return;
        }

        if (!isClearlyThroughWall(eye, blockCenter, placed, against)) {
            return;
        }

        int vl = manager.data(player).addViolation(CheatType.COBWEB_THROUGH_WALL);
        // Jeden strzał nie canceluje — dopiero powtórzenia.
        if (vl < 5) {
            return;
        }

        event.setCancelled(true);
        event.setBuild(false);
        manager.punishCobwebPlace(player, 20 * 2, 1200L);

        if (vl >= 5) {
            manager.notifyStaff(player, CheatType.COBWEB_THROUGH_WALL, vl,
                manager.getConfig().getCobwebThroughWallAlert(),
                Map.of());
            if (vl >= 12) {
                manager.data(player).resetViolation(CheatType.COBWEB_THROUGH_WALL);
            }
        }
    }

    private boolean isClearlyThroughWall(Location from, Location to, Block placed, Block against) {
        Vector direction = to.toVector().subtract(from.toVector());
        double length = direction.length();
        if (length < 0.5) {
            return false;
        }

        // Oficjalny raytrace świata — mniej FP niż ręczne kroki 0.4.
        RayTraceResult hit = from.getWorld().rayTraceBlocks(
            from,
            direction.normalize(),
            length - 0.35,
            FluidCollisionMode.NEVER,
            true
        );
        if (hit == null || hit.getHitBlock() == null) {
            return false;
        }
        Block blocked = hit.getHitBlock();
        if (blocked.equals(placed) || blocked.equals(against)) {
            return false;
        }
        Material type = blocked.getType();
        if (!type.isSolid() || !type.isOccluding()) {
            return false;
        }
        // Trafienie musi być wyraźnie przed celem (nie „obok” przy place na ścianie).
        if (hit.getHitPosition() == null) {
            return false;
        }
        double hitDist = hit.getHitPosition().distance(from.toVector());
        return hitDist + 0.55 < length;
    }
}
