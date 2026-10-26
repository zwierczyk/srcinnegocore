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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import dev.grzybalson.anarchiagg.customitems.handlers.RegionHandler;
import dev.grzybalson.anarchiagg.customitems.util.EventItemRegistry;
import dev.grzybalson.anarchiagg.customitems.util.EventItemUseGuard;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Kosa implements Listener {
    private final JavaPlugin plugin;
    private final RegionHandler regionHandler;
    private final int kosaCooldown;
    private final int time;
    private final String kosaName;
    private final Material kosaMaterial;
    private final Set<String> blockedRegions = new HashSet<>();

    public Kosa(JavaPlugin plugin, RegionHandler regionHandler) {
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

        kosaCooldown = eventowkiConfig.getInt("eventowki.kosa.cooldown", 10);
        time = eventowkiConfig.getInt("eventowki.kosa.time", 10);
        kosaName = eventowkiConfig.getString("eventowki.kosa.name", "§8§lKosa").replace("&", "§");

        Material configuredMaterial = Material.getMaterial(eventowkiConfig.getString("eventowki.kosa.material", "NETHERITE_HOE"));
        this.kosaMaterial = (configuredMaterial != null) ? configuredMaterial : Material.NETHERITE_HOE;
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
        if (EventItemUseGuard.hasItemCooldown(plugin, attacker, "kosa", mainHandItem, kosaCooldown)) {
            return;
        }

        if (!EventItemRegistry.matches(plugin, "kosa", mainHandItem)) {
            return;
        }
        if (!EventItemUseGuard.tryUse(plugin, attacker)) {
            return;
        }
        if (canApplyEffect(victim)) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20 * time, 1));
        }
        EventItemUseGuard.applyItemCooldown(plugin, attacker, "kosa", mainHandItem, kosaCooldown);
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
    private boolean canApplyEffect(Player target) {
        if (!target.hasPotionEffect(PotionEffectType.SLOW)) {
            return true;
        }
        PotionEffect existingEffect = target.getPotionEffect(PotionEffectType.SLOW);
        int reapplyThresholdTicks = 3 * 20;
        if (existingEffect == null) return false;
        return existingEffect.getDuration() < reapplyThresholdTicks;
    }
}
