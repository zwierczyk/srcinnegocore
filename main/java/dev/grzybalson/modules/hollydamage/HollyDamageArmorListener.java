package dev.grzybalson.modules.hollydamage;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;

public final class HollyDamageArmorListener implements Listener {

    private final HollyDamageConfig config;

    public HollyDamageArmorListener(HollyDamageConfig config) {
        this.config = config;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        if (!config.isArmorDurabilityEnabled()) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null) {
            return;
        }
        String name = item.getType().name();
        if (!(name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") || name.endsWith("_LEGGINGS")
            || name.endsWith("_BOOTS") || name.equals("ELYTRA") || name.equals("PLAYER_HEAD"))) {
            return;
        }
        double divisor = config.getArmorDurabilityDivisor();
        if (divisor <= 1.0) {
            return;
        }
        int reduced = (int) Math.floor(event.getDamage() / divisor);
        event.setDamage(Math.max(0, reduced));
    }
}
