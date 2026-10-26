package dev.grzybalson.modules.anticheat;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/**
 * Hitboxy do checków PvP — zawsze vanilla width+height,
 * żeby expand (szerokość lub wysokość) nie omijał reach / hit-through.
 */
public final class CombatHitboxes {

    /** Połowa szerokości gracza (vanilla 0.6). */
    public static final double HALF_WIDTH = 0.30D;
    /** Standing. */
    public static final double HEIGHT_STANDING = 1.80D;
    /** Sneak / swim-ish. */
    public static final double HEIGHT_SNEAK = 1.50D;
    public static final double HEIGHT_SWIM_OR_ELYTRA = 0.60D;

    private CombatHitboxes() {
    }

    /**
     * Rdzeń ciała niezależny od expandniętego {@link Player#getBoundingBox()}.
     * Ogranicza zarówno szerokość, jak i wysokość.
     */
    public static BoundingBox vanillaCore(Player player) {
        Location loc = player.getLocation();
        double x = loc.getX();
        double y = loc.getY();
        double z = loc.getZ();
        double h = coreHeight(player);
        return new BoundingBox(
            x - HALF_WIDTH,
            y,
            z - HALF_WIDTH,
            x + HALF_WIDTH,
            y + h,
            z + HALF_WIDTH
        );
    }

    public static double coreHeight(Player player) {
        if (player.isSwimming() || player.isGliding() || player.isRiptiding()) {
            return HEIGHT_SWIM_OR_ELYTRA;
        }
        if (player.isSneaking() || player.isFlying()) {
            return HEIGHT_SNEAK;
        }

        // Check for cobwebs around player
        Location loc = player.getLocation();
        boolean hasWeb = false;
        for (int dy = 0; dy <= 2; dy++) {
            Location check = loc.clone().add(0, dy, 0);
            if (check.getBlock().getType() == Material.COBWEB) {
                hasWeb = true;
                break;
            }
        }

        // If player has cobweb, use their actual bounding box height
        if (hasWeb) {
            BoundingBox playerBox = player.getBoundingBox();
            return playerBox.getHeight();
        }

        return HEIGHT_STANDING;
    }

    public static Vector center(BoundingBox box) {
        return new Vector(
            (box.getMinX() + box.getMaxX()) * 0.5D,
            (box.getMinY() + box.getMaxY()) * 0.5D,
            (box.getMinZ() + box.getMaxZ()) * 0.5D
        );
    }

    public static Vector closestPoint(Vector point, BoundingBox box) {
        return new Vector(
            clamp(point.getX(), box.getMinX(), box.getMaxX()),
            clamp(point.getY(), box.getMinY(), box.getMaxY()),
            clamp(point.getZ(), box.getMinZ(), box.getMaxZ())
        );
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
