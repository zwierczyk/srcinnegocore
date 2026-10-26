package dev.grzybalson.modules.customhit;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Wspólna klasyfikacja części zbroi dla customhit (cap dmg / toughness).
 * PLAYER_HEAD w slocie hełmu liczy się jak zwykły hełm.
 */
public final class CustomHitArmorUtil {

    private CustomHitArmorUtil() {
    }

    public static boolean isArmorPiece(ItemStack item) {
        if (item == null) {
            return false;
        }
        Material type = item.getType();
        if (type.isAir() || type == Material.ELYTRA) {
            return false;
        }
        return isArmorMaterial(type);
    }

    public static boolean isArmorMaterial(Material material) {
        if (material == null) {
            return false;
        }
        String name = material.name();
        return name.endsWith("_HELMET")
            || name.endsWith("_CHESTPLATE")
            || name.endsWith("_LEGGINGS")
            || name.endsWith("_BOOTS")
            || name.equals("TURTLE_HELMET")
            || name.equals("PLAYER_HEAD")
            || name.equals("SKELETON_SKULL")
            || name.equals("WITHER_SKELETON_SKULL")
            || name.equals("ZOMBIE_HEAD")
            || name.equals("CREEPER_HEAD")
            || name.equals("DRAGON_HEAD")
            || name.equals("PIGLIN_HEAD");
    }

    public static boolean isElytra(ItemStack item) {
        return item != null && item.getType() == Material.ELYTRA;
    }
}
