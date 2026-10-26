package dev.grzybalson.modules.boss;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.ArrayList;
import java.util.List;

public final class BossEntityListener implements Listener {

    private final BossManager manager;

    public BossEntityListener(BossManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        BossConfig.BossDefinition definition = manager.takeTrackedBoss(entity.getUniqueId());
        if (definition == null) {
            return;
        }

        event.getDrops().clear();
        event.setDroppedExp(0);

        List<Player> ranking = new ArrayList<>();
        Player killer = entity.getKiller();
        if (killer != null && killer.isOnline()) {
            ranking.add(killer);
        }
        BossRewardGiver.grantTop(ranking, entity.getLocation(), definition.rewards(), definition.name());
    }
}
