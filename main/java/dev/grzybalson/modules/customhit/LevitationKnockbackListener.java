package dev.grzybalson.modules.customhit;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

public final class LevitationKnockbackListener implements Listener {

    private final CustomHitsService service;

    public LevitationKnockbackListener(CustomHitsService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLevitationKnockback(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!victim.hasPotionEffect(PotionEffectType.LEVITATION)) return;

        double multiplier = service.getLevitationKnockbackMultiplier();
        if (multiplier == 1.0) return;

        Vector before = victim.getVelocity().clone();

        service.getPlugin().getServer().getScheduler().runTaskLater(service.getPlugin(), () -> {
            if (!victim.isOnline() || victim.isDead()) return;

            Vector after = victim.getVelocity().clone();
            Vector knockbackPart = after.clone().subtract(before);
            Vector adjusted = before.clone().add(knockbackPart.multiply(multiplier));
            victim.setVelocity(adjusted);
        }, 1L);
    }
}
