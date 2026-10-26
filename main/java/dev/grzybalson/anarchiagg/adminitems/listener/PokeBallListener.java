package dev.grzybalson.anarchiagg.adminitems.listener;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventPriority;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;
import dev.grzybalson.HollyCore;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsConfig;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsCooldowns;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsGUI;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsRegionUtil;
import dev.grzybalson.anarchiagg.util.LocationUtil;
import dev.grzybalson.utils.ColorUtils;

public class PokeBallListener
implements Listener {
    private static final String PROJECTILE_METADATA = "pokeball_projectile";
    private final HollyCore plugin;
    private final NamespacedKey projectileKey;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<UUID, Long>();
    private final Set<UUID> activeProjectiles = ConcurrentHashMap.newKeySet();
    private final Set<UUID> processedProjectiles = ConcurrentHashMap.newKeySet();

    public PokeBallListener(HollyCore plugin) {
        this.plugin = plugin;
        this.projectileKey = new NamespacedKey((Plugin)plugin, "admin_item_pokeball_projectile");
    }

    @EventHandler
    public void onUsePokeBall(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND && event.getHand() != EquipmentSlot.OFF_HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        AdminItemsConfig.PokeBallSettings pokeSettings = this.plugin.getAdminItemsConfig().getPokeBall();
        if (!pokeSettings.isEnabled()) {
            return;
        }
        if (event.getHand() == EquipmentSlot.OFF_HAND && this.isPokeBall(event.getPlayer().getInventory().getItemInMainHand(), pokeSettings)) {
            return;
        }
        if (!this.isPokeBall(item, pokeSettings)) {
            return;
        }
        Player player = event.getPlayer();
        event.setCancelled(true);
        if (AdminItemsRegionUtil.isInBlockedRegion(player, this.plugin)) {
            player.sendMessage(dev.grzybalson.utils.ColorUtils.colorize("&c&l\u1d18\u1d0f\u1d0b\u1d07&f&l\u0299\u1d00\u029f\u029f &8:: &cNie mozesz uzyc pokeballa w tym miejscu!"));
            return;
        }
        UUID uuid = player.getUniqueId();
        int cooldownSeconds = pokeSettings.getCooldown();
        long cooldownMillis = AdminItemsCooldowns.secondsToMillis(cooldownSeconds);
        long now = System.currentTimeMillis();
        Long cooldownUntil = this.cooldowns.get(uuid);
        if (cooldownUntil != null && cooldownUntil > now) {
            AdminItemsCooldowns.refresh(player, item.getType(), cooldownUntil);
            player.sendMessage(dev.grzybalson.utils.ColorUtils.colorize("&c&l\u1d18\u1d0f\u1d0b\u1d07&f&l\u0299\u1d00\u029f\u029f &8:: &cPrzedmiot posiada cooldown!"));
            return;
        }
        this.cooldowns.remove(uuid);
        Arrow projectile = player.launchProjectile(Arrow.class);
        Vector direction = player.getEyeLocation().getDirection().normalize();
        projectile.setVelocity(direction.multiply(2.4D));
        projectile.setGravity(false);
        projectile.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        projectile.setCritical(false);
        projectile.setDamage(0.0D);
        projectile.setGlowing(true);
        projectile.setMetadata(PROJECTILE_METADATA, (MetadataValue)new FixedMetadataValue((Plugin)this.plugin, (Object)true));
        projectile.getPersistentDataContainer().set(this.projectileKey, PersistentDataType.BYTE, (byte)1);
        this.activeProjectiles.add(projectile.getUniqueId());
        long newCooldownUntil = now + cooldownMillis;
        this.cooldowns.put(uuid, newCooldownUntil);
        AdminItemsCooldowns.apply(player, item.getType(), cooldownMillis);
        this.plugin.getServer().getScheduler().runTaskLater((Plugin)this.plugin, () -> dev.grzybalson.anarchiagg.util.MapCompatibilityUtil.remove(this.cooldowns, uuid, newCooldownUntil), (long)cooldownSeconds * 20L);
        this.plugin.getServer().getScheduler().runTaskLater((Plugin)this.plugin, () -> {
            this.activeProjectiles.remove(projectile.getUniqueId());
            this.processedProjectiles.remove(projectile.getUniqueId());
            if (projectile.isValid()) {
                projectile.remove();
            }
        }, 20L * 15L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onProjectileDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Projectile projectile) || !this.isPokeBallProjectile(projectile)) {
            return;
        }
        event.setDamage(0.0D);
        ProjectileSource projectileSource = projectile.getShooter();
        if (!(projectileSource instanceof Player shooter) || !(event.getEntity() instanceof Player targetPlayer) || targetPlayer.equals(shooter)) {
            return;
        }
        this.handlePokeBallHit(projectile, shooter, targetPlayer);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        if (!this.isPokeBallProjectile(projectile)) {
            return;
        }
        ProjectileSource projectileSource = projectile.getShooter();
        if (!(projectileSource instanceof Player)) {
            projectile.remove();
            this.activeProjectiles.remove(projectile.getUniqueId());
            return;
        }
        Player shooter = (Player)projectileSource;
        Player targetPlayer = this.resolveHitPlayer(event, shooter);
        if (targetPlayer == null) {
            this.plugin.getServer().getScheduler().runTaskLater((Plugin)this.plugin, () -> {
                projectile.remove();
                this.activeProjectiles.remove(projectile.getUniqueId());
            }, 1L);
            return;
        }
        this.handlePokeBallHit(projectile, shooter, targetPlayer);
    }

    private void handlePokeBallHit(Projectile projectile, Player shooter, Player targetPlayer) {
        if (!this.processedProjectiles.add(projectile.getUniqueId())) {
            return;
        }
        try {
            if (AdminItemsRegionUtil.isInBlockedRegion(shooter, this.plugin) || AdminItemsRegionUtil.isInBlockedRegion(targetPlayer, this.plugin)) {
                shooter.sendMessage(dev.grzybalson.utils.ColorUtils.colorize("&c&l\u1d18\u1d0f\u1d0b\u1d07&f&l\u0299\u1d00\u029f\u029f &8:: &cNie mozesz uzyc pokeballa w tym miejscu!"));
                return;
            }
            AdminItemsConfig.PokeBallSettings pokeSettings = this.plugin.getAdminItemsConfig().getPokeBall();
            double chance = Math.max(0.0, Math.min(100.0, pokeSettings.getChance()));
            if (ThreadLocalRandom.current().nextDouble(100.0) < chance) {
                targetPlayer.teleport(LocationUtil.preservingPlayerRotation(targetPlayer, shooter.getLocation()));
                shooter.sendMessage(dev.grzybalson.utils.ColorUtils.colorize("&c&l\u1d18\u1d0f\u1d0b\u1d07&f&l\u0299\u1d00\u029f\u029f &8:: &aPomyslnie przyciagnieto gracza pokeballem!"));
                shooter.playSound(shooter.getLocation(), "minecraft:minestar.pokemon", 1.0f, 1.0f);
                targetPlayer.playSound(targetPlayer.getLocation(), "minecraft:minestar.pokemon", 1.0f, 1.0f);
                targetPlayer.sendMessage(dev.grzybalson.utils.ColorUtils.colorize("&c&l\u1d18\u1d0f\u1d0b\u1d07&f&l\u0299\u1d00\u029f\u029f &8:: &cZostales przyciagniety przez &4" + shooter.getName() + "!"));
            } else {
                shooter.sendMessage(dev.grzybalson.utils.ColorUtils.colorize("&c&l\u1d18\u1d0f\u1d0b\u1d07&f&l\u0299\u1d00\u029f\u029f &8:: &cNie udalo ci sie przyciagnac gracza!"));
            }
        } finally {
            projectile.remove();
            this.activeProjectiles.remove(projectile.getUniqueId());
            this.plugin.getServer().getScheduler().runTaskLater((Plugin)this.plugin, () -> dev.grzybalson.anarchiagg.util.MapCompatibilityUtil.remove(this.processedProjectiles, projectile.getUniqueId()), 1L);
        }
    }

    private boolean isPokeBallProjectile(Projectile projectile) {
        return projectile instanceof Arrow
                && (projectile.hasMetadata(PROJECTILE_METADATA)
                || projectile.getPersistentDataContainer().has(this.projectileKey, PersistentDataType.BYTE)
                || this.activeProjectiles.contains(projectile.getUniqueId()));
    }

    private Player resolveHitPlayer(ProjectileHitEvent event, Player shooter) {
        Entity hitEntity = event.getHitEntity();
        if (hitEntity instanceof Player player && !player.equals(shooter)) {
            return player;
        }
        Location hitLocation = event.getEntity().getLocation();
        Player closest = null;
        double closestDistanceSquared = 1.8D;
        for (Entity nearby : hitLocation.getWorld().getNearbyEntities(hitLocation, 1.35D, 1.35D, 1.35D)) {
            if (!(nearby instanceof Player player) || player.equals(shooter) || player.isDead()) {
                continue;
            }
            double distanceSquared = player.getLocation().distanceSquared(hitLocation);
            if (distanceSquared < closestDistanceSquared) {
                closestDistanceSquared = distanceSquared;
                closest = player;
            }
        }
        return closest;
    }

    private boolean isPokeBall(ItemStack item, AdminItemsConfig.PokeBallSettings settings) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (AdminItemsGUI.KEY_POKEBALL != null
                && meta.getPersistentDataContainer().has(AdminItemsGUI.KEY_POKEBALL, PersistentDataType.BYTE)) {
            return true;
        }
        return item.getType() == Material.HEART_OF_THE_SEA
                && settings.getCustomModelData() != 0
                && meta.hasCustomModelData()
                && meta.getCustomModelData() == settings.getCustomModelData();
    }
}
