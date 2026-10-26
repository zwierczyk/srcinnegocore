package dev.grzybalson.modules.customhit;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Skaluje zużycie dury zbroi / elytry:
 *  - wartość > 1.0: wolniej traci durę
 *  - wartość < 1.0: szybciej traci durę
 *  - 1.0: vanilla
 *  - 0.0: brak zużycia
 */
public final class ArmorToughnessListener implements Listener {

    private final CustomHitsService service;
    private final Method getSlotMethod;

    public ArmorToughnessListener(CustomHitsService service) {
        this.service = service;
        Method m;
        try {
            m = PlayerItemDamageEvent.class.getMethod("getSlot");
        } catch (Throwable ignored) {
            m = null;
        }
        this.getSlotMethod = m;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent e) {
        ItemStack item = e.getItem();
        if (item == null) {
            return;
        }

        EquipmentSlot slot = resolveSlot(e);
        boolean elytra = CustomHitArmorUtil.isElytra(item)
            || (slot == EquipmentSlot.CHEST && item.getType() == Material.ELYTRA);

        double tough;
        if (elytra) {
            tough = service.getElytraToughness();
        } else {
            if (slot != null) {
                if (slot != EquipmentSlot.HEAD && slot != EquipmentSlot.CHEST
                    && slot != EquipmentSlot.LEGS && slot != EquipmentSlot.FEET) {
                    return;
                }
            } else if (!CustomHitArmorUtil.isArmorMaterial(item.getType())) {
                return;
            }
            tough = service.getArmorToughness();
        }

        if (tough == 1.0) {
            return;
        }

        if (tough <= 0.0) {
            e.setDamage(0);
            e.setCancelled(true);
            return;
        }

        int dmg = e.getDamage();
        if (dmg <= 0) {
            return;
        }

        double scaled = dmg / tough;
        int floor = (int) Math.floor(scaled);
        double frac = scaled - floor;

        int newDmg = floor;
        if (frac > 0 && ThreadLocalRandom.current().nextDouble() < frac) {
            newDmg++;
        }

        if (newDmg < 0) {
            newDmg = 0;
        }
        e.setDamage(newDmg);
    }

    private EquipmentSlot resolveSlot(PlayerItemDamageEvent e) {
        if (getSlotMethod == null) {
            return null;
        }
        try {
            Object o = getSlotMethod.invoke(e);
            if (o instanceof EquipmentSlot equipmentSlot) {
                return equipmentSlot;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
