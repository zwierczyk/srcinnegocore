package dev.grzybalson.anarchiagg.customitems.items;

import dev.grzybalson.anarchiagg.AnarchiaGGSettings;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import dev.grzybalson.anarchiagg.customitems.handlers.RegionHandler;
import dev.grzybalson.anarchiagg.customitems.util.EventItemRegistry;
import dev.grzybalson.anarchiagg.customitems.util.EventItemUseGuard;
import java.util.*;

public class LopataGrincza implements Listener {
    private final JavaPlugin plugin;
    private final RegionHandler regionHandler;
    private final int lopataCooldown;
    private final int time;
    private final String lopataName;
    private final Material lopataMaterial;
    private final Set<String> blockedRegions = new HashSet<>();
    private final Random random = new Random();


    public LopataGrincza(JavaPlugin plugin, RegionHandler regionHandler) {
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
        lopataCooldown = eventowkiConfig.getInt("eventowki.lopatagrincza.cooldown", 10);
        time = eventowkiConfig.getInt("eventowki.lopatagrincza.time", 10);
        lopataName = eventowkiConfig.getString("eventowki.lopatagrincza.name", "§a§lŁopata grincha").replace("&", "§");

        Material configuredMaterial = Material.getMaterial(eventowkiConfig.getString("eventowki.lopatagrincza.material", "DIAMOND_SHOVEL"));
        this.lopataMaterial = (configuredMaterial != null) ? configuredMaterial : Material.DIAMOND_SHOVEL;
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (event.isCancelled()) return;

        if (isLocationBlocked(attacker.getLocation()) || isLocationBlocked(victim.getLocation())) {
            return;
        }

        ItemStack mainHandItem = attacker.getInventory().getItemInMainHand();
        if (EventItemUseGuard.hasItemCooldown(plugin, attacker, "lopatagrincza", mainHandItem, lopataCooldown)) {
            return;
        }

        if (!EventItemRegistry.matches(plugin, "lopatagrincza", mainHandItem)) {
            return;
        }
        if (!EventItemUseGuard.tryUse(plugin, attacker)) {
            return;
        }

        EventItemUseGuard.applyItemCooldown(plugin, attacker, "lopatagrincza", mainHandItem, lopataCooldown);
        mixsee(victim);
    }

    private void mixsee(Player player) {
        float yaw = random.nextFloat() * 360f;
        float pitch = -90f + random.nextFloat() * 180f;
        Location loc = player.getLocation();
        loc.setYaw(yaw);
        loc.setPitch(pitch);
        player.teleport(loc);
        player.setFreezeTicks(time * 20);
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
