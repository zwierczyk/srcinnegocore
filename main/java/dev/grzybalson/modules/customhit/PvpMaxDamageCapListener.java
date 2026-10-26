package dev.grzybalson.modules.customhit;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

public final class PvpMaxDamageCapListener implements Listener {

    private static final double EPS = 1.0E-6;

    private final CustomHitsService service;

    public PvpMaxDamageCapListener(CustomHitsService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player victim))
            return;
        double maxDmg = getMaxDamage(victim);
        if (maxDmg < 0.0)
            return;
        if (!isPlayerVsPlayer(e))
            return;
        if (e instanceof EntityDamageByEntityEvent byEntity && byEntity.getDamager() instanceof Player attacker) {
            var hd = service.getPlugin().getHollyDamageService();
            if (hd != null && hd.getConfig() != null && hd.getConfig().controlsMelee(attacker.getInventory().getItemInMainHand())) {
                return;
            }
        }

        double curFinalBeforeAbs = getDamageBeforeAbsorption(e);
        if (curFinalBeforeAbs <= EPS)
            return;
        if (curFinalBeforeAbs <= maxDmg + EPS)
            return;

        setDamageToMatchFinalBeforeAbsorption(e, maxDmg);
    }

    private double getMaxDamage(Player victim) {
        if (usesElytraDamageProfile(victim)) {
            return service.getElytraMaxDamage();
        }
        return service.getMaxDamageForArmorPieces(countArmorPieces(victim));
    }

    private static int countArmorPieces(Player player) {
        EntityEquipment equipment = player.getEquipment();
        if (equipment == null)
            return 0;

        int pieces = 0;
        if (isArmorPiece(equipment.getHelmet()))
            pieces++;
        if (isArmorPiece(equipment.getChestplate()))
            pieces++;
        if (isArmorPiece(equipment.getLeggings()))
            pieces++;
        if (isArmorPiece(equipment.getBoots()))
            pieces++;
        return pieces;
    }

    private static boolean hasElytra(Player player) {
        EntityEquipment equipment = player.getEquipment();
        if (equipment == null)
            return false;
        ItemStack chestplate = equipment.getChestplate();
        return chestplate != null && chestplate.getType() == Material.ELYTRA;
    }

    private static boolean usesElytraDamageProfile(Player player) {
        return player != null && hasElytra(player);
    }

    private static boolean isArmorPiece(ItemStack item) {
        return CustomHitArmorUtil.isArmorPiece(item);
    }

    private static boolean isPlayerVsPlayer(EntityDamageEvent e) {
        return getAttackingPlayer(e) != null;
    }

    private static Player getAttackingPlayer(EntityDamageEvent e) {
        if (!(e instanceof EntityDamageByEntityEvent byEntity))
            return null;
        if (byEntity.getDamager() instanceof Player p)
            return p;
        if (byEntity.getDamager() instanceof Projectile proj) {
            Object shooter = proj.getShooter();
            if (shooter instanceof Player p)
                return p;
        }
        return null;
    }

    private static void setDamageToMatchFinalBeforeAbsorption(EntityDamageEvent e, double targetFinalBeforeAbs) {
        if (targetFinalBeforeAbs <= 0.0) {
            e.setDamage(0.0);
            return;
        }

        double maxBase = 4096.0;
        for (int i = 0; i < 4; i++) {
            double cur = getDamageBeforeAbsorption(e);
            if (cur <= EPS) {
                e.setDamage(0.0);
                return;
            }

            if (Math.abs(cur - targetFinalBeforeAbs) <= 1.0E-3)
                return;

            double ratio = targetFinalBeforeAbs / cur;
            if (Double.isNaN(ratio) || Double.isInfinite(ratio))
                return;

            double newBase = e.getDamage() * ratio;
            if (Double.isNaN(newBase) || Double.isInfinite(newBase) || newBase < 0.0)
                newBase = 0.0;
            if (newBase > maxBase)
                newBase = maxBase;
            e.setDamage(newBase);
        }
    }

    private static double getDamageBeforeAbsorption(EntityDamageEvent e) {
        double fin = e.getFinalDamage();
        try {
            if (e.isApplicable(EntityDamageEvent.DamageModifier.ABSORPTION)) {
                double abs = e.getDamage(EntityDamageEvent.DamageModifier.ABSORPTION);
                if (!Double.isNaN(abs) && !Double.isInfinite(abs)) {
                    fin += Math.abs(abs);
                }
            }
        } catch (Throwable ignored) {
        }
        if (Double.isNaN(fin) || Double.isInfinite(fin))
            return 0.0;
        return Math.max(0.0, fin);
    }
}
