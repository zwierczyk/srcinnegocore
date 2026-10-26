package dev.grzybalson.anarchiagg.customitems.items;

import dev.grzybalson.anarchiagg.AnarchiaGGSettings;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import dev.grzybalson.anarchiagg.customitems.handlers.RegionHandler;
import dev.grzybalson.anarchiagg.customitems.util.EventItemRegistry;
import dev.grzybalson.anarchiagg.customitems.util.EventItemUseGuard;
import java.util.*;

public class KroliczyMiecz implements Listener {
    private final JavaPlugin plugin;
    private final RegionHandler regionHandler;
    private final int kroliczymieczCooldown;
    private final String kroliczymieczName;
    private final Set<String> blockedRegions = new HashSet<>();
    private final Set<UUID> jumblock = new HashSet<>();

    public KroliczyMiecz(JavaPlugin plugin, RegionHandler regionHandler) {
        this.plugin = plugin;
        this.regionHandler = regionHandler;
        FileConfiguration eventowkiConfig = AnarchiaGGSettings.getConfig(plugin);
        List<String> regionList = eventowkiConfig.getStringList("blocked-regions");
        if (regionList.isEmpty() && !eventowkiConfig.isSet("blocked-regions")) {
            eventowkiConfig.set("blocked-regions", List.of("spawn"));
            this.blockedRegions.add("spawn");
        } else {
            this.blockedRegions.addAll(regionList);
        }

        kroliczymieczCooldown = eventowkiConfig.getInt("eventowki.kroliczymiecz.cooldown", 5);
        kroliczymieczName = eventowkiConfig.getString("eventowki.kroliczymiecz.name", "§3§lKróliczy miecz").replace("&", "§");
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (event.isCancelled()) return;
        if (isLocationBlocked(attacker.getLocation())) {
            return;
        }
        if (isLocationBlocked(victim.getLocation())) {
            return;
        }
        ItemStack mainHand = attacker.getInventory().getItemInMainHand();
        if (EventItemUseGuard.hasItemCooldown(plugin, attacker, "kroliczymiecz", mainHand, kroliczymieczCooldown)) {
            return;
        }
        if (attacker.getInventory().getItemInMainHand().getItemMeta() == null) return;
        if (!EventItemRegistry.matches(plugin, "kroliczymiecz", mainHand)) return;
        if (!EventItemUseGuard.tryUse(plugin, attacker)) return;

        EventItemUseGuard.applyItemCooldown(plugin, attacker, "kroliczymiecz", mainHand, kroliczymieczCooldown);
        UUID victimId = victim.getUniqueId();
        jumblock.add(victimId);
        new BukkitRunnable() {
            @Override
            public void run() {
                jumblock.remove(victimId);
            }
        }.runTaskLater(plugin, 20 * 4L);
    }
    @EventHandler
    public void onJump(PlayerJumpEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        if (jumblock.contains(playerId)) {
            event.setCancelled(true);
        }
    }

    private boolean isLocationBlocked(Location loc) {
        if (loc == null || loc.getWorld() == null) return true;

        for (String regionName : blockedRegions) {
            if (regionHandler.isLocationInRegion(loc, regionName)) {
                return true;
            }
        }
        return false;
    }
}
