package dev.grzybalson.modules.anticheat;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.Map;

public final class AuraCheck implements Listener {

    private final AnticheatManager manager;

    public AuraCheck(AnticheatManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!manager.getConfig().isAura()) {
            return;
        }
        if (!(event.getDamager() instanceof Player damager) || !(event.getEntity() instanceof Player victim)) {
            return;
        }
        Location from = damager.getLocation();
        Location to = victim.getLocation();
        if (from.getBlockX() != to.getBlockX() || from.getBlockZ() != to.getBlockZ()) {
            return;
        }
        if (!damager.isCollidable() || !victim.isCollidable()) {
            return;
        }
        if (!damager.getBoundingBox().overlaps(victim.getBoundingBox())) {
            return;
        }
        Location base = from.getBlock().getLocation();
        // Bez budowania listy Location — pierwszy nie-AIR i tak konczy sprawdzenie.
        World world = base.getWorld();
        int cx = base.getBlockX();
        int cy = base.getBlockY();
        int cz = base.getBlockZ();
        for (int y = 0; y <= 2; y++) {
            for (int x = cx - 2; x <= cx + 2; x++) {
                for (int z = cz - 2; z <= cz + 2; z++) {
                    if (world.getBlockAt(x, cy + y, z).getType() != Material.AIR) {
                        return;
                    }
                }
            }
        }
        CheatPlayerData data = manager.data(damager);
        int vl = data.addViolation(CheatType.AURA_IN_PLAYER);
        if (vl < 25) {
            return;
        }
        data.resetViolation(CheatType.AURA_IN_PLAYER);
        manager.notifyStaff(damager, CheatType.AURA_IN_PLAYER, vl, manager.getConfig().getAuraAlert(), Map.of());
    }
}
