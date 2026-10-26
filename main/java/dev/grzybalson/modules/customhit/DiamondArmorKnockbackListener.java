package dev.grzybalson.modules.customhit;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public final class DiamondArmorKnockbackListener implements Listener {

    private final CustomHitsService service;

    public DiamondArmorKnockbackListener(CustomHitsService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKnockback(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!hasFullDiamondArmor(victim)) return;

        double multiplier = service.getDiamondArmorKnockbackMultiplier();
        if (multiplier == 1.0D) return;

        Vector before = victim.getVelocity().clone();
        service.getPlugin().getServer().getScheduler().runTaskLater(service.getPlugin(), () -> {
            if (!victim.isOnline() || victim.isDead() || !hasFullDiamondArmor(victim)) return;

            Vector after = victim.getVelocity().clone();
            Vector knockbackPart = after.clone().subtract(before);
            if (knockbackPart.lengthSquared() < 0.0001D) return;

            Vector adjusted = before.clone().add(knockbackPart.multiply(multiplier));
            victim.setVelocity(adjusted);
        }, 1L);
    }

    private static boolean hasFullDiamondArmor(Player player) {
        return isDiamond(player.getInventory().getHelmet(), Material.DIAMOND_HELMET)
            && isDiamond(player.getInventory().getChestplate(), Material.DIAMOND_CHESTPLATE)
            && isDiamond(player.getInventory().getLeggings(), Material.DIAMOND_LEGGINGS)
            && isDiamond(player.getInventory().getBoots(), Material.DIAMOND_BOOTS);
    }

    private static boolean isDiamond(ItemStack item, Material material) {
        return item != null && item.getType() == material;
    }
}
