package dev.grzybalson.anarchiagg.customitems.items;

import dev.grzybalson.anarchiagg.AnarchiaGGSettings;
import dev.grzybalson.anarchiagg.customitems.handlers.RegionHandler;
import dev.grzybalson.anarchiagg.customitems.util.EventItemTools;
import dev.grzybalson.anarchiagg.customitems.util.EventItemUseGuard;
import dev.grzybalson.anarchiagg.customitems.util.EventowkiEffects;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Set;

public class LinaCecco implements Listener {

    public static final String ITEM_KEY = "linacecco";
    private static final String META = "holly-lina-cecco";

    private final JavaPlugin plugin;
    private final RegionHandler regionHandler;
    private final Set<String> blockedRegions;
    private final int cooldownSeconds;
    private final double range;

    public LinaCecco(JavaPlugin plugin, RegionHandler regionHandler) {
        this.plugin = plugin;
        this.regionHandler = regionHandler;
        this.blockedRegions = EventItemTools.loadBlockedRegions(plugin, ITEM_KEY);
        FileConfiguration config = AnarchiaGGSettings.getConfig(plugin);
        this.cooldownSeconds = config.getInt("eventowki." + ITEM_KEY + ".cooldown", 0);
        this.range = config.getDouble("eventowki." + ITEM_KEY + ".range", 15.0D);
    }

    public static ItemStack createDefaultItem(JavaPlugin plugin) {
        return EventItemTools.createFromConfig(plugin, ITEM_KEY, Material.LEAD, "&e&lLina Cecco", 1);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack used = EventItemTools.findUsed(plugin, player, ITEM_KEY, event);
        if (used == null) {
            return;
        }
        event.setCancelled(true);
        if (!EventItemUseGuard.tryUse(plugin, player)) {
            return;
        }
        if (EventItemTools.isLocationBlocked(regionHandler, blockedRegions, player.getLocation())) {
            return;
        }
        if (EventItemUseGuard.hasItemCooldown(plugin, player, ITEM_KEY, used, cooldownSeconds)) {
            return;
        }
        EventItemUseGuard.applyItemCooldown(plugin, player, ITEM_KEY, used, cooldownSeconds);

        ItemStack projectileItem = used.clone();
        projectileItem.setAmount(1);
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        Snowball snowball = player.getWorld().spawn(eye.clone().add(direction.clone().multiply(0.5)), Snowball.class);
        snowball.setShooter(player);
        snowball.setItem(projectileItem);
        snowball.setVelocity(direction.multiply(1.55));
        snowball.setMetadata(META, new FixedMetadataValue(plugin, player.getUniqueId().toString()));
        player.playSound(player.getLocation(), Sound.ENTITY_LEASH_KNOT_PLACE, 1.0f, 1.3f);

        Location origin = player.getLocation().clone();
        double maxRangeSq = range * range;
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!snowball.isValid() || snowball.isDead() || !snowball.hasMetadata(META)) {
                    cancel();
                    return;
                }
                Player hit = EventowkiEffects.findPullTarget(snowball, player, 1.85D);
                if (hit != null) {
                    pull(player, hit, snowball);
                    cancel();
                    return;
                }
                if (snowball.getLocation().distanceSquared(origin) > maxRangeSq) {
                    snowball.remove();
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    @EventHandler
    public void onHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Snowball snowball) || !snowball.hasMetadata(META)) {
            return;
        }
        if (!(snowball.getShooter() instanceof Player shooter)) {
            snowball.remove();
            return;
        }
        Player victim = event.getHitEntity() instanceof Player hit ? hit : EventowkiEffects.findPullTarget(snowball, shooter, 2.35D);
        pull(shooter, victim, snowball);
    }

    private void pull(Player shooter, Player victim, Snowball snowball) {
        if (snowball != null && !EventowkiEffects.claimEntity(snowball)) {
            return;
        }
        if (snowball != null) {
            snowball.removeMetadata(META, plugin);
            snowball.remove();
        }
        if (victim == null || shooter == null || victim.getUniqueId().equals(shooter.getUniqueId())) {
            return;
        }
        if (victim.getWorld() != shooter.getWorld()) {
            return;
        }
        if (shooter.getLocation().distanceSquared(victim.getLocation()) > range * range) {
            return;
        }
        if (EventItemTools.isLocationBlocked(regionHandler, blockedRegions, victim.getLocation())
            || EventItemTools.isLocationBlocked(regionHandler, blockedRegions, shooter.getLocation())) {
            return;
        }
        EventowkiEffects.pullToShooter(plugin, victim, shooter, range);
    }
}
