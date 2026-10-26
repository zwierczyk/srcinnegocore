package dev.grzybalson.anarchiagg.customitems.items;

import dev.grzybalson.anarchiagg.AnarchiaGGSettings;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import dev.grzybalson.anarchiagg.customitems.handlers.RegionHandler;
import dev.grzybalson.anarchiagg.customitems.util.EventItemRegistry;
import dev.grzybalson.anarchiagg.customitems.util.EventItemUseGuard;
import java.util.*;

public class ZatrutyOlowek implements Listener {
    private final JavaPlugin plugin;
    private final RegionHandler regionHandler;
    private final int zatrutyolowekCooldown;
    private final int time;
    private final String zatrutyolowekName;
    private final Material zatrutyolowekMaterial;
    private final Set<String> blockedRegions = new HashSet<>();

    public ZatrutyOlowek(JavaPlugin plugin, RegionHandler regionHandler) {
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
        zatrutyolowekCooldown = eventowkiConfig.getInt("eventowki.zatrutyolowek.cooldown", 10);
        time = eventowkiConfig.getInt("eventowki.zatrutyolowek.time", 10);
        zatrutyolowekName = eventowkiConfig.getString("eventowki.zatrutyolowek.name", "§a§lZatruty ołówek").replace("&", "§");

        Material configuredMaterial = Material.getMaterial(eventowkiConfig.getString("eventowki.zatrutyolowek.material", "LIME_CANDLE"));
        this.zatrutyolowekMaterial = (configuredMaterial != null) ? configuredMaterial : Material.LIME_CANDLE;
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

    private boolean canApplyEffect(Player target, PotionEffectType type) {
        if (!target.hasPotionEffect(type)) {
            return true;
        }
        PotionEffect existingEffect = target.getPotionEffect(type);
        int reapplyThresholdTicks = 4 * 20;
        if (existingEffect == null) return false;
        return existingEffect.getDuration() < reapplyThresholdTicks;
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player damager)) return;
        if (!(event.getEntity() instanceof Player target)) return;

        ItemStack itemInHand = damager.getInventory().getItemInMainHand();

        if (itemInHand.getType() == Material.AIR) return;
        if (!EventItemRegistry.matches(plugin, "zatrutyolowek", itemInHand)) return;
        if (!EventItemUseGuard.tryUse(plugin, damager)) return;

        if (EventItemUseGuard.hasItemCooldown(plugin, damager, "zatrutyolowek", itemInHand, zatrutyolowekCooldown)) {
            return;
        }

        if (isLocationBlocked(damager.getLocation())) {
            return;
        }

        if (isLocationBlocked(target.getLocation())) {
            return;
        }

        boolean weaknessApplied = false;
        boolean poisonApplied = false;

        int effectDurationTicks = time * 20;
        if (canApplyEffect(target, PotionEffectType.WEAKNESS)) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, effectDurationTicks, 0));
            weaknessApplied = true;
        }

        if (canApplyEffect(target, PotionEffectType.POISON)) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, effectDurationTicks, 0));
            poisonApplied = true;
        }

        if (weaknessApplied || poisonApplied) {
        EventItemUseGuard.applyItemCooldown(plugin, damager, "zatrutyolowek", itemInHand, zatrutyolowekCooldown);
            dev.grzybalson.utils.ColorUtils.sendTitle(damager, "", "§7Zatrułeś §a" + target.getName() + "§7!", 0, 50, 10);
            dev.grzybalson.utils.ColorUtils.sendTitle(target, "", "§aZostałeś zatruty przez ołówek!", 0, 50, 10);
        }
    }
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.getBlock().getType().equals(Material.LIME_CANDLE)) {
            event.setCancelled(true);
        }
    }
}
