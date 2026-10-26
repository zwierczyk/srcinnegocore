package dev.grzybalson.anarchiagg.customitems.items;

import dev.grzybalson.anarchiagg.AnarchiaGGSettings;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import dev.grzybalson.anarchiagg.customitems.handlers.RegionHandler;
import dev.grzybalson.anarchiagg.customitems.util.EventItemRegistry;
import dev.grzybalson.anarchiagg.customitems.util.EventItemUseGuard;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BombardaMaxima implements Listener {
    private final String bombardaName;
    private final Material bombardaMaterial;
    private final JavaPlugin plugin;
    private final int cooldownSeconds;
    private final RegionHandler regionHandler;
    private final Set<String> blockedRegions = new HashSet<>();

    public BombardaMaxima(JavaPlugin plugin, RegionHandler regionHandler) {
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
        String bombardaItem = eventowkiConfig.getString("eventowki.bombarda.item", "FIRE_CHARGE");
        bombardaName = eventowkiConfig.getString("eventowki.bombarda.name", "§5§lBombarda maxima");
        bombardaMaterial = Material.valueOf(bombardaItem.toUpperCase());

        List<String> itemregionList = eventowkiConfig.getStringList("eventowki.bombarda.blocked-regions");
        if (itemregionList.isEmpty() && !eventowkiConfig.isSet("eventowki.bombarda.blocked-regions")) {
            eventowkiConfig.set("eventowki.bombarda.blocked-regions", List.of("pvp"));
            this.blockedRegions.add("pvp");
        } else {
            this.blockedRegions.addAll(itemregionList);
        }

        cooldownSeconds = eventowkiConfig.getInt("eventowki.bombarda.cooldown", 5);
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

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (itemInHand.getType() == Material.AIR) return;
        if (!EventItemRegistry.matches(plugin, "bombarda", itemInHand)) return;
        if (!EventItemUseGuard.tryUse(plugin, player)) return;
        event.setCancelled(true);

        if (EventItemUseGuard.hasItemCooldown(plugin, player, "bombarda", itemInHand, cooldownSeconds)) return;
        if (isLocationBlocked(player.getLocation())) return;

        Location eyeLocation = player.getEyeLocation();
        Vector direction = eyeLocation.getDirection().multiply(1.5);

        Fireball fireball = player.getWorld().spawn(eyeLocation.add(direction), Fireball.class);
        fireball.setShooter(player);
        fireball.setYield(0F);
        fireball.setIsIncendiary(false);
        fireball.setMetadata("bombarda", new FixedMetadataValue(plugin, true));
        fireball.setDirection(direction);

        if (itemInHand.getAmount() > 1) {
            itemInHand.setAmount(itemInHand.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }
        EventItemUseGuard.applyItemCooldown(plugin, player, "bombarda", itemInHand, cooldownSeconds);
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (event.getEntity() instanceof Fireball fireball && fireball.hasMetadata("bombarda")) {
            Location hitLocation = fireball.getLocation();
            if (hitLocation.getWorld() == null) return;

            if (!isLocationBlocked(event.getEntity().getLocation())) {
                int radius = 4;
                for (int x = -radius; x <= radius; x++) {
                    for (int y = -radius; y <= radius; y++) {
                        for (int z = -radius; z <= radius; z++) {
                            Block block = hitLocation.getBlock().getRelative(x, y, z);
                            if (block.getLocation().distance(hitLocation) <= radius) {
                                if (!block.getType().equals(Material.BEDROCK) && !block.getType().equals(Material.LIGHT_BLUE_CONCRETE) && !block.getType().equals(Material.BLUE_GLAZED_TERRACOTTA)) {
                                    if (isLocationBlocked(block.getLocation())) return;
                                    block.setType(Material.AIR);
                                }
                            }
                        }
                    }
                }
                hitLocation.getWorld().playSound(hitLocation, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 1f);
                hitLocation.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, hitLocation, 1);
            }

            fireball.remove();
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Fireball) {
            if (event.getDamager() instanceof Player) {
                event.setCancelled(true);
            }
        }
    }
}
