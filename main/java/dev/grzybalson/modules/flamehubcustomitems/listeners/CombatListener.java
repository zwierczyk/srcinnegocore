package dev.grzybalson.modules.flamehubcustomitems.listeners;

import dev.grzybalson.HollyCore;
import dev.grzybalson.modules.flamehubcustomitems.config.FlameHubConfigManager;
import dev.grzybalson.modules.flamehubcustomitems.manager.EffectManager;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Fallback obrażeń gdy customhit jest wyłączony.
 * Przy włączonym customhit mnożniki Yoda/Vader są aplikowane w CustomHitDamageListener.
 */
public class CombatListener implements Listener {

    private final HollyCore plugin;
    private final FlameHubConfigManager configManager;
    private final EffectManager effectManager;

    public CombatListener(HollyCore plugin, FlameHubConfigManager configManager, EffectManager effectManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.effectManager = effectManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onYodaDamageReduction(EntityDamageByEntityEvent event) {
        if (plugin.getCustomHitsService() != null) {
            return;
        }
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (!effectManager.isYodaActive(victim)) {
            return;
        }

        double percent = Math.max(0.0, Math.min(100.0, configManager.getYodaDamageReductionPercent()));
        double multiplier = 1.0 - (percent / 100.0);
        event.setDamage(Math.max(0.0, event.getDamage() * multiplier));

        victim.getWorld().spawnParticle(
                Particle.ENCHANTMENT_TABLE,
                victim.getLocation().add(0, 1.0, 0),
                15, 0.3, 0.5, 0.3, 0.1
        );
        victim.playSound(victim.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.45f, 1.85f);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVaderDamageIncrease(EntityDamageByEntityEvent event) {
        if (plugin.getCustomHitsService() != null) {
            return;
        }
        Player attacker = resolveAttacker(event);
        if (attacker == null || !effectManager.isVaderActive(attacker)) {
            return;
        }

        double bonusPercent = Math.max(0.0, configManager.getVaderDamageIncreasePercent());
        event.setDamage(event.getDamage() * (1.0 + (bonusPercent / 100.0)));

        event.getEntity().getWorld().spawnParticle(
                Particle.CRIT,
                event.getEntity().getLocation().add(0, 1.0, 0),
                15, 0.3, 0.4, 0.3, 0.15
        );
        attacker.playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.8f, 1.2f);
    }

    private Player resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }
}
