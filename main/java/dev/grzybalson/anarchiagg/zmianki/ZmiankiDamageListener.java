package dev.grzybalson.anarchiagg.zmianki;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class ZmiankiDamageListener implements Listener {
    private final ZmiankiManager manager;

    public ZmiankiDamageListener(ZmiankiManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (!(damager instanceof Player attacker)) {
            return;
        }
        if (event.getEntity() instanceof Player && hollyDamageOwnsMelee(attacker)) {
            return;
        }

        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        Integer bonus = manager.getBonus(weapon);
        if (bonus == null) {
            return;
        }

        double multiplier = manager.getDamageMultiplier();
        event.setDamage((event.getDamage() + (bonus * multiplier)) * attacker.getAttackCooldown());
    }

    private boolean hollyDamageOwnsMelee(Player attacker) {
        JavaPlugin plugin = manager.plugin();
        if (!(plugin instanceof dev.grzybalson.HollyCore core)) {
            return false;
        }
        var service = core.getHollyDamageService();
        if (service == null || service.getConfig() == null) {
            return false;
        }
        return service.getConfig().controlsMelee(attacker.getInventory().getItemInMainHand());
    }
}
