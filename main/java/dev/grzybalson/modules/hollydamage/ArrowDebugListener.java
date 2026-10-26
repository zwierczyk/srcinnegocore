package dev.grzybalson.modules.hollydamage;

import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.block.Block;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;

import java.util.Locale;

/**
 * Diagnostyka strzal (tylko przy debug.enabled w damage.yml):
 * mowi w co strzala faktycznie trafila i czy obrazenia zostaly anulowane.
 */
public final class ArrowDebugListener implements Listener {

    private final HollyCore plugin;
    private final HollyDamageConfig config;

    public ArrowDebugListener(HollyCore plugin, HollyDamageConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onArrowHit(ProjectileHitEvent event) {
        if (!config.isDebugEnabled() || !(event.getEntity() instanceof AbstractArrow arrow)) {
            return;
        }
        if (!(arrow.getShooter() instanceof Player shooter)) {
            return;
        }

        String target;
        Entity hitEntity = event.getHitEntity();
        Block hitBlock = event.getHitBlock();
        if (hitEntity != null) {
            target = "encje " + hitEntity.getType().name()
                + (hitEntity instanceof Player hitPlayer ? " (" + hitPlayer.getName() + ")" : "")
                + (hitEntity.isInvulnerable() ? " &c[nietykalna]&7" : "");
        } else if (hitBlock != null) {
            target = "blok " + hitBlock.getType().name()
                + " @ " + hitBlock.getX() + "," + hitBlock.getY() + "," + hitBlock.getZ();
        } else {
            target = "nic";
        }
        report(shooter, "strzala trafila w " + target);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onArrowDamage(EntityDamageByEntityEvent event) {
        if (!config.isDebugEnabled() || !(event.getDamager() instanceof AbstractArrow arrow)) {
            return;
        }
        if (!(arrow.getShooter() instanceof Player shooter) || !(event.getEntity() instanceof Player victim)) {
            return;
        }
        report(shooter, "dmg " + victim.getName()
            + ": anulowany=" + (event.isCancelled() ? "&cTAK&7" : "nie")
            + " base=" + format(event.getDamage())
            + " final=" + format(event.getFinalDamage())
            + " i-frame=" + victim.getNoDamageTicks() + "/" + victim.getMaximumNoDamageTicks());
    }

    private void report(Player shooter, String message) {
        plugin.getLogger().info("[ArrowDebug] " + shooter.getName() + ": " + message.replace("&c", "").replace("&7", ""));
        if (config.isDebugNotifyAttacker() && shooter.hasPermission(config.getDebugPermission())) {
            shooter.sendMessage(ColorUtils.colorizeComponent("&8» &#89E2FF&lArrow &8— &7" + message));
        }
    }

    private static String format(double value) {
        return String.format(Locale.US, "%.2f", value);
    }
}
