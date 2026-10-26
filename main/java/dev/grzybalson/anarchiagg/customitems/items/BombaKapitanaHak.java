package dev.grzybalson.anarchiagg.customitems.items;

import dev.grzybalson.anarchiagg.AnarchiaGGSettings;
import dev.grzybalson.anarchiagg.customitems.handlers.RegionHandler;
import dev.grzybalson.anarchiagg.customitems.util.EventItemRegistry;
import dev.grzybalson.anarchiagg.customitems.util.EventItemTools;
import dev.grzybalson.anarchiagg.customitems.util.EventItemUseGuard;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Set;

public class BombaKapitanaHak implements Listener {

    public static final String ITEM_KEY = "bombakapitana";
    private static final String META = "holly-bomba-kapitana";

    private final JavaPlugin plugin;
    private final RegionHandler regionHandler;
    private final Set<String> blockedRegions;
    private final int cooldownSeconds;
    private final double radius;
    private final int blindnessTicks;
    private final long elytraLockMillis;

    public BombaKapitanaHak(JavaPlugin plugin, RegionHandler regionHandler) {
        this.plugin = plugin;
        this.regionHandler = regionHandler;
        this.blockedRegions = EventItemTools.loadBlockedRegions(plugin, ITEM_KEY);
        FileConfiguration config = AnarchiaGGSettings.getConfig(plugin);
        this.cooldownSeconds = config.getInt("eventowki." + ITEM_KEY + ".cooldown", 0);
        this.radius = config.getDouble("eventowki." + ITEM_KEY + ".radius", 5.0D);
        this.blindnessTicks = Math.max(20, config.getInt("eventowki." + ITEM_KEY + ".blindness-ticks", 100));
        this.elytraLockMillis = Math.max(1L, config.getLong("eventowki." + ITEM_KEY + ".elytra-lock-millis", 2000L));
    }

    public static ItemStack createDefaultItem(JavaPlugin plugin) {
        return EventItemTools.createFromConfig(plugin, ITEM_KEY, Material.FIREWORK_STAR, "&c&lBomba Kapitana Hak", 1);
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

        ItemStack projectileItem = used.clone();
        projectileItem.setAmount(1);
        boolean mainHand = EventItemTools.isMainHand(player, used);
        EventItemTools.consumeOne(player, used, mainHand);
        EventItemUseGuard.applyItemCooldown(plugin, player, ITEM_KEY, used, cooldownSeconds);

        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        Snowball snowball = player.getWorld().spawn(eye.clone().add(direction.clone().multiply(0.6)), Snowball.class);
        snowball.setShooter(player);
        snowball.setItem(projectileItem);
        snowball.setVelocity(direction.multiply(1.35));
        snowball.setMetadata(META, new FixedMetadataValue(plugin, player.getUniqueId().toString()));
        player.playSound(player.getLocation(), Sound.ENTITY_SNOWBALL_THROW, 1.0f, 0.6f);
    }

    @EventHandler
    public void onHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Snowball snowball) || !snowball.hasMetadata(META)) {
            return;
        }
        Location hit = snowball.getLocation();
        if (hit.getWorld() == null) {
            return;
        }
        snowball.remove();
        hit.getWorld().playSound(hit, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.8f);
        hit.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, hit, 2, 0.2, 0.2, 0.2, 0);
        hit.getWorld().spawnParticle(Particle.SMOKE_LARGE, hit, 18, 0.6, 0.4, 0.6, 0.02);

        Player shooter = snowball.getShooter() instanceof Player player ? player : null;
        for (Player nearby : hit.getWorld().getPlayers()) {
            if (nearby.getLocation().distanceSquared(hit) > radius * radius) {
                continue;
            }
            if (shooter != null && nearby.getUniqueId().equals(shooter.getUniqueId())) {
                continue;
            }
            if (EventItemTools.isLocationBlocked(regionHandler, blockedRegions, nearby.getLocation())) {
                continue;
            }
            nearby.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, blindnessTicks, 0, true, false, true));
            EventItemTools.lockElytra(nearby, elytraLockMillis);
            nearby.playSound(nearby.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.5f, 1.4f);
        }
    }

    @EventHandler
    public void onGlide(EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (EventItemTools.isElytraLocked(player) && event.isGliding()) {
            event.setCancelled(true);
            player.setGliding(false);
        }
    }
}
