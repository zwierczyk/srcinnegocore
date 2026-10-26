package dev.grzybalson.anarchiagg.coach.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.Event;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import dev.grzybalson.anarchiagg.coach.CoachManager;

public class CoachPlayerListener implements Listener {

    private final CoachManager manager;

    public CoachPlayerListener(CoachManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (manager.getCachedData(player.getUniqueId()) != null) {
            manager.applyDefenseHealth(player);
        } else {
            manager.preload(player.getUniqueId());
        }
        manager.onPlayerJoin(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        manager.onPlayerQuit(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPotionUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
        if (!manager.tryUseAllSkillsPotion(event.getPlayer(), item)) return;
        event.setCancelled(true);
        event.setUseItemInHand(Event.Result.DENY);
        event.setUseInteractedBlock(Event.Result.DENY);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        if (false) return;
        Player killer = event.getEntity().getKiller();
        if (killer == null || killer.equals(event.getEntity())) return;
        manager.addKills(killer);
    }
}
