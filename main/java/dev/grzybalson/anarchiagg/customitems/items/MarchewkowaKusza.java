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
import dev.grzybalson.anarchiagg.customitems.handlers.RegionHandler;
import dev.grzybalson.anarchiagg.customitems.util.EventItemRegistry;
import dev.grzybalson.anarchiagg.customitems.util.EventItemUseGuard;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MarchewkowaKusza implements Listener {
    private final JavaPlugin plugin;
    private final RegionHandler regionHandler;
    private final int kuszaCooldown;
    private final String kuszaName;
    private final Material kuszaMaterial = Material.CROSSBOW;
    private final Set<String> blockedRegions = new HashSet<>();

    public MarchewkowaKusza(JavaPlugin plugin, RegionHandler regionHandler) {
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

        kuszaCooldown = eventowkiConfig.getInt("eventowki.marchewkowakusza.cooldown", 30);
        kuszaName = eventowkiConfig.getString("eventowki.marchewkowakusza.name", "§6§lMarchewkowa Kusza").replace("&", "§");
    }

    @EventHandler
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack item = event.getBow();

        if (item == null || item.getType() == Material.AIR) return;
        if (!EventItemRegistry.matches(plugin, "marchewkowakusza", item)) return;
        if (!EventItemUseGuard.tryUse(plugin, player)) return;

        if (EventItemUseGuard.hasItemCooldown(plugin, player, "marchewkowakusza", item, kuszaCooldown)) {
            event.setCancelled(true);
            return;
        }

        if (isLocationBlocked(player.getLocation())) {
            event.setCancelled(true);
            return;
        }

        EventItemUseGuard.applyItemCooldown(plugin, player, "marchewkowakusza", item, kuszaCooldown);

        if (event.getProjectile() instanceof Arrow) {
            event.getProjectile().setMetadata("marchewkowa_arrow", new FixedMetadataValue(plugin, true));
        }
    }

    @EventHandler
    public void onHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Arrow arrow)) return;

        if (!arrow.hasMetadata("marchewkowa_arrow")) return;
        if (!(arrow.getShooter() instanceof Player shooter)) return;
        if (!(event.getHitEntity() instanceof Player victim)) return;

        if (isLocationBlocked(shooter.getLocation()) || isLocationBlocked(victim.getLocation())) return;

        victim.teleport(shooter.getLocation());
        victim.playSound(victim, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
        shooter.playSound(shooter, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);

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
