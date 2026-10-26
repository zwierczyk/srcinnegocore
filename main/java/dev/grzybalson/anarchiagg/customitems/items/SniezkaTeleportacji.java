package dev.grzybalson.anarchiagg.customitems.items;

import dev.grzybalson.anarchiagg.AnarchiaGGSettings;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import dev.grzybalson.anarchiagg.customitems.handlers.RegionHandler;
import dev.grzybalson.anarchiagg.customitems.util.EventItemRegistry;
import dev.grzybalson.anarchiagg.customitems.util.EventItemUseGuard;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SniezkaTeleportacji implements Listener {
    private final JavaPlugin plugin;
    private final RegionHandler regionHandler;
    private final int sniezkaCooldown;
    private final String sniezkaName;
    private final Material sniezkaMaterial = Material.SNOWBALL;
    private final Set<String> blockedRegions = new HashSet<>();

    public SniezkaTeleportacji(JavaPlugin plugin, RegionHandler regionHandler) {
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

        sniezkaCooldown = eventowkiConfig.getInt("eventowki.sniezkateleportacji.cooldown", 10);
        sniezkaName = eventowkiConfig.getString("eventowki.sniezkateleportacji.name", "&fŚnieżka").replace("&", "§");
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.isCancelled()) return;

        if (!(event.getEntity() instanceof Player victim)) return;
        if (!(event.getDamager() instanceof Snowball snowball)) return;
        if (!(snowball.getShooter() instanceof Player attacker)) return;

        ItemStack snowballItem = snowball.getItem();
        ItemMeta meta = snowballItem.getItemMeta();

        if (!EventItemRegistry.matches(plugin, "sniezkateleportacji", snowballItem)) {
            return;
        }
        if (!EventItemUseGuard.tryUse(plugin, attacker)) {
            return;
        }

        if (isLocationBlocked(attacker.getLocation()) || isLocationBlocked(victim.getLocation())) {
            return;
        }

        if (attacker.getWorld() != victim.getWorld()) return;

        if (EventItemUseGuard.hasItemCooldown(plugin, attacker, "sniezkateleportacji", snowballItem, sniezkaCooldown)) {
            return;
        }

        Location attackerLoc = attacker.getLocation();
        Location victimLoc = victim.getLocation();

        attacker.teleport(victimLoc);
        victim.teleport(attackerLoc);

        EventItemUseGuard.applyItemCooldown(plugin, attacker, "sniezkateleportacji", snowballItem, sniezkaCooldown);
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
