package dev.grzybalson.anarchiagg.customitems.items;

import dev.grzybalson.anarchiagg.AnarchiaGGSettings;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import dev.grzybalson.anarchiagg.customitems.handlers.RegionHandler;
import dev.grzybalson.anarchiagg.customitems.util.EventItemRegistry;
import dev.grzybalson.anarchiagg.customitems.util.EventItemUseGuard;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class LukKupidyna implements Listener {
    private final JavaPlugin plugin;
    private final RegionHandler regionHandler;
    private final int lukCooldown;
    private final String lukName;
    private final Material lukMaterial = Material.BOW;
    private final Set<String> blockedRegions = new HashSet<>();
    private final Random random = new Random();

    public LukKupidyna(JavaPlugin plugin, RegionHandler regionHandler) {
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

        lukCooldown = eventowkiConfig.getInt("eventowki.lukkupidyna.cooldown", 30);
        lukName = eventowkiConfig.getString("eventowki.lukkupidyna.name", "§d§lLuk Kupidyna").replace("&", "§");
    }

    @EventHandler
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        if (event.getBow() == null || event.getBow().getType() == Material.AIR) return;
        if (!EventItemRegistry.matches(plugin, "lukkupidyna", event.getBow())) return;
        if (!EventItemUseGuard.tryUse(plugin, player)) return;

        if (EventItemUseGuard.hasItemCooldown(plugin, player, "lukkupidyna", event.getBow(), lukCooldown)) {
            event.setCancelled(true);
            return;
        }

        if (isLocationBlocked(player.getLocation())) {
            event.setCancelled(true);
            return;
        }

        if (event.getProjectile() instanceof Arrow arrow) {
            arrow.setMetadata("kupidyn_arrow", new FixedMetadataValue(plugin, true));
            arrow.setMetadata("kupidyn_bow_type", new FixedMetadataValue(plugin, event.getBow().getType().name()));
        }
    }

    @EventHandler
    public void onHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Arrow arrow)) return;

        if (!arrow.hasMetadata("kupidyn_arrow")) return;
        if (!(arrow.getShooter() instanceof Player shooter)) return;
        if (!(event.getHitEntity() instanceof Player victim)) return;

        if (isLocationBlocked(shooter.getLocation()) || isLocationBlocked(victim.getLocation())) return;

        if (random.nextDouble() <= 0.30) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20 * 5, 1));
            victim.playSound(victim.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 2f);

            Material bowType = Material.BOW;
            if (arrow.hasMetadata("kupidyn_bow_type")) {
                String typeName = arrow.getMetadata("kupidyn_bow_type").get(0).asString();
                Material parsed = Material.matchMaterial(typeName);
                if (parsed != null) {
                    bowType = parsed;
                }
            }
            EventItemUseGuard.applyItemCooldown(plugin, shooter, "lukkupidyna", new ItemStack(bowType), lukCooldown);
        }

        arrow.remove();
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
