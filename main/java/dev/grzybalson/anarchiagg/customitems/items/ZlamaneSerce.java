package dev.grzybalson.anarchiagg.customitems.items;

import dev.grzybalson.anarchiagg.AnarchiaGGSettings;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Location;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import dev.grzybalson.anarchiagg.customitems.handlers.RegionHandler;
import dev.grzybalson.anarchiagg.customitems.util.EventItemRegistry;
import dev.grzybalson.anarchiagg.customitems.util.EventItemUseGuard;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ZlamaneSerce implements Listener {
    private final JavaPlugin plugin;
    private final String zlamaneSerduszkoName;
    private final Material zlamaneSerduszkoMaterial;
    private final RegionHandler regionHandler;
    private final Set<String> blockedRegions = new HashSet<>();
    private final int cooldownSeconds;
    private final int time;

    public ZlamaneSerce(JavaPlugin plugin, RegionHandler regionHandler) {
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

        String zlamaneSerduszkoItem = eventowkiConfig.getString("eventowki.zlamaneserduszko.item", "PURPLE_DYE");
        zlamaneSerduszkoMaterial = Material.valueOf(zlamaneSerduszkoItem.toUpperCase());
        cooldownSeconds = eventowkiConfig.getInt("eventowki.zlamaneserduszko.cooldown", 20);
        zlamaneSerduszkoName = eventowkiConfig.getString("eventowki.zlamaneserduszko.name", "§5§lZłamane serce");
        time = eventowkiConfig.getInt("eventowki.zlamaneserduszko.time", 20);
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
        int reapplyThresholdTicks = 3 * 20;
        if (existingEffect == null) return false;
        return existingEffect.getDuration() < reapplyThresholdTicks;
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player damager)) return;
        if (!(event.getEntity() instanceof Player target)) return;

        ItemStack itemInHand = damager.getInventory().getItemInMainHand();

        if (itemInHand.getType() == Material.AIR) return;
        if (!EventItemRegistry.matches(plugin, "zlamaneserduszko", itemInHand)) return;
        if (!EventItemUseGuard.tryUse(plugin, damager)) return;

        if (EventItemUseGuard.hasItemCooldown(plugin, damager, "zlamaneserduszko", itemInHand, cooldownSeconds)) {
            return;
        }

        if (isLocationBlocked(damager.getLocation())) {
            return;
        }

        if (isLocationBlocked(target.getLocation())) {
            return;
        }

        boolean slownessApplied = false;
        boolean slowFallingApplied = false;

        int effectDurationTicks = time * 20;
        if (canApplyEffect(target, PotionEffectType.SLOW)) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, effectDurationTicks, 0));
            slownessApplied = true;
        }

        if (canApplyEffect(target, PotionEffectType.SLOW_FALLING)) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, effectDurationTicks, 0));
            slowFallingApplied = true;
        }

        if (slownessApplied || slowFallingApplied) {
            if (itemInHand.getAmount() > 1) {
                itemInHand.setAmount(itemInHand.getAmount() - 1);
            } else {
                damager.getInventory().setItemInMainHand(null);
            }
        EventItemUseGuard.applyItemCooldown(plugin, damager, "zlamaneserduszko", itemInHand, cooldownSeconds);
            dev.grzybalson.utils.ColorUtils.sendTitle(damager, "", "§7Złamałeś §dserce §7gracza §f" + target.getName() + "§7!", 0, 50, 10);
            dev.grzybalson.utils.ColorUtils.sendTitle(target, "", "§7Twoje §dserce §7zostało złamane!", 0, 50, 10);
            damager.playSound(damager.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 1f);
            target.playSound(target.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 1f);
        }
    }
}
