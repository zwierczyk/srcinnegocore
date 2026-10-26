package dev.grzybalson.anarchiagg.customitems.items;

import dev.grzybalson.anarchiagg.AnarchiaGGSettings;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SiekieraGrincza implements Listener {
    private final RegionHandler regionHandler;
    private final int marchewkowymieczCooldown;
    private final String marchewkowymieczName;
    private final Material marchewkowymieczMaterial;
    private final Set<String> blockedRegions = new HashSet<>();
    private final JavaPlugin plugin;

    public SiekieraGrincza(JavaPlugin plugin, RegionHandler regionHandler) {
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

        marchewkowymieczCooldown = eventowkiConfig.getInt("eventowki.siekieragrincza.cooldown", 10);
        marchewkowymieczName = eventowkiConfig.getString("eventowki.siekieragrincza.name", "§2Siekiera Grincha").replace("&", "§");

        Material configuredMaterial = Material.getMaterial(eventowkiConfig.getString("eventowki.siekieragrincza.material", "GOLDEN_AXE"));
        this.marchewkowymieczMaterial = (configuredMaterial != null) ? configuredMaterial : Material.GOLDEN_AXE;
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
        if (EventItemUseGuard.hasItemCooldown(plugin, attacker, "siekieragrincza", mainHandItem, marchewkowymieczCooldown)) {
            return;
        }

        if (!EventItemRegistry.matches(plugin, "siekieragrincza", mainHandItem)) {
            return;
        }
        if (!EventItemUseGuard.tryUse(plugin, attacker)) {
            return;
        }

        EventItemUseGuard.applyItemCooldown(plugin, attacker, "siekieragrincza", mainHandItem, marchewkowymieczCooldown);
        dev.grzybalson.utils.ColorUtils.sendTitle(attacker, "", "§7Uderzyłeś §f" + victim.getName() + "§7siekierą §agrincza§7", 0, 50, 10);
        dev.grzybalson.utils.ColorUtils.sendTitle(victim, "", "§7Zostałeś uderzony §asiekiera grincza§7!", 0, 50, 10);
        victim.getWorld().strikeLightningEffect(victim.getLocation());
        victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1f, 1f);
        victim.setHealth(victim.getHealth() * 0.7);
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
