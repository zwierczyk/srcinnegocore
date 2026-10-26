package dev.grzybalson.anarchiagg.customitems.items;

import dev.grzybalson.anarchiagg.AnarchiaGGSettings;
import dev.grzybalson.anarchiagg.customitems.handlers.RegionHandler;
import dev.grzybalson.anarchiagg.customitems.util.EventItemRegistry;
import dev.grzybalson.anarchiagg.customitems.util.EventItemUseGuard;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class UlepszonaElytra implements Listener {

    private static final double MIN_CHARGE_ADD = 1.0D;
    private static final double MAX_CHARGE_ADD = 3.0D;
    private static final double MAX_CHARGE = 100.0D;
    private static final int CHARGE_RATE_TICKS = 10;
    private static final double EFFECT_RADIUS = 3.0D;
    private static final double EFFECT_RADIUS_SQUARED = EFFECT_RADIUS * EFFECT_RADIUS;
    private static final double KNOCKBACK_Y = 0.3D;
    private static final double KNOCKBACK_XZ = 2.9D;

    private static final Map<UUID, Double> ELYTRA_CHARGE = new HashMap<>();
    private static final Map<UUID, Integer> LAST_DISPLAYED_PERCENT = new HashMap<>();
    private static final Set<UUID> ACTIVE_CHARGING = new HashSet<>();
    private static final Set<UUID> AWAITING_LANDING = new HashSet<>();
    private static BukkitTask sharedChargeTask;

    private final Material itemMaterial;
    private final JavaPlugin plugin;
    private final RegionHandler regionHandler;
    private final Set<String> blockedRegions = new HashSet<>();
    private final int damage;
    private final int cooldownSeconds;

    public UlepszonaElytra(JavaPlugin plugin, RegionHandler regionHandler) {
        this.plugin = plugin;
        this.regionHandler = regionHandler;

        FileConfiguration config = AnarchiaGGSettings.getConfig(plugin);
        Collection<String> regionList = config.getStringList("blocked-regions");
        if (regionList.isEmpty()) {
            blockedRegions.add("spawn");
        } else {
            blockedRegions.addAll(regionList);
        }

        String materialName = config.getString("eventowki.ulepszonaelytra.material", "ELYTRA");
        Material configuredMaterial = Material.matchMaterial(materialName == null ? "ELYTRA" : materialName);
        itemMaterial = configuredMaterial == null ? Material.ELYTRA : configuredMaterial;
        damage = config.getInt("eventowki.ulepszonaelytra.damage", 30);
        cooldownSeconds = config.getInt("eventowki.ulepszonaelytra.cooldown", 0);

        startSharedChargeTask();
    }

    private void startSharedChargeTask() {
        if (sharedChargeTask != null) {
            sharedChargeTask.cancel();
        }
        sharedChargeTask = Bukkit.getScheduler().runTaskTimer(
            plugin,
            this::tickChargingPlayers,
            CHARGE_RATE_TICKS,
            CHARGE_RATE_TICKS
        );
    }

    public static void onDisable() {
        if (sharedChargeTask != null) {
            sharedChargeTask.cancel();
            sharedChargeTask = null;
        }
        ACTIVE_CHARGING.clear();
        AWAITING_LANDING.clear();
        ELYTRA_CHARGE.clear();
        LAST_DISPLAYED_PERCENT.clear();
    }

    public static double getCharge(Player player) {
        return player == null ? 0.0D : ELYTRA_CHARGE.getOrDefault(player.getUniqueId(), 0.0D);
    }

    private void tickChargingPlayers() {
        if (ACTIVE_CHARGING.isEmpty()) {
            return;
        }

        for (UUID uuid : new ArrayList<>(ACTIVE_CHARGING)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                clearState(uuid);
                continue;
            }

            if (!player.isGliding()) {
                ACTIVE_CHARGING.remove(uuid);
                AWAITING_LANDING.add(uuid);
                continue;
            }

            ItemStack chestplate = player.getInventory().getChestplate();
            if (isLocationBlocked(player.getLocation()) || !isSpecialElytra(chestplate)) {
                resetCharge(player);
                continue;
            }

            double charge = ELYTRA_CHARGE.getOrDefault(uuid, 0.0D);
            if (charge < MAX_CHARGE) {
                double speed = Math.sqrt(player.getVelocity().lengthSquared());
                double speedFactor = Math.min(1.0D, speed / 1.5D);
                double add = MIN_CHARGE_ADD + (MAX_CHARGE_ADD - MIN_CHARGE_ADD) * speedFactor;
                charge = Math.min(MAX_CHARGE, charge + add * (CHARGE_RATE_TICKS / 20.0D));
                ELYTRA_CHARGE.put(uuid, charge);
            }

            int displayedPercent = (int) Math.floor(charge);
            Integer previousPercent = LAST_DISPLAYED_PERCENT.put(uuid, displayedPercent);
            if (previousPercent == null || previousPercent != displayedPercent) {
                sendChargeBar(player, displayedPercent);
            }
        }
    }

    private boolean isLocationBlocked(Location location) {
        if (location == null || location.getWorld() == null || regionHandler == null) {
            return false;
        }
        for (String regionName : blockedRegions) {
            if (regionHandler.isLocationInRegion(location, regionName)) {
                return true;
            }
        }
        return false;
    }

    private boolean isSpecialElytra(ItemStack item) {
        if (item == null || item.getType() != itemMaterial) {
            return false;
        }
        if (EventItemRegistry.isTaggedAs(plugin, "ulepszonaelytra", item)) {
            return true;
        }
        return EventItemRegistry.matches(plugin, "ulepszonaelytra", item);
    }

    private void beginCharging(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        UUID uuid = player.getUniqueId();
        ACTIVE_CHARGING.add(uuid);
        AWAITING_LANDING.remove(uuid);
        ELYTRA_CHARGE.putIfAbsent(uuid, 0.0D);
    }

    private void finishLanding(Player player) {
        UUID uuid = player.getUniqueId();
        AWAITING_LANDING.remove(uuid);

        if (ELYTRA_CHARGE.getOrDefault(uuid, 0.0D) >= MAX_CHARGE) {
            ItemStack chestplate = player.getInventory().getChestplate();
            if (isSpecialElytra(chestplate)
                && !EventItemUseGuard.hasItemCooldown(plugin, player, "ulepszonaelytra", chestplate, cooldownSeconds)) {
                EventItemUseGuard.applyItemCooldown(plugin, player, "ulepszonaelytra", chestplate, cooldownSeconds);
                triggerLightning(player, player.getLocation());
            }
        }
        resetCharge(player);
    }

    private void resetCharge(Player player) {
        UUID uuid = player.getUniqueId();
        clearState(uuid);
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(""));
    }

    private static void clearState(UUID uuid) {
        ACTIVE_CHARGING.remove(uuid);
        AWAITING_LANDING.remove(uuid);
        ELYTRA_CHARGE.remove(uuid);
        LAST_DISPLAYED_PERCENT.remove(uuid);
    }

    private void sendChargeBar(Player player, int percent) {
        String message = "§bWzmocniona Elytra §fzaładowana w §3" + percent + "%";
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
    }

    private void triggerLightning(Player attacker, Location impactLocation) {
        World world = impactLocation.getWorld();
        if (world == null) {
            return;
        }

        Player target = null;
        double closestDistance = Double.MAX_VALUE;
        for (Player nearby : impactLocation.getNearbyPlayers(EFFECT_RADIUS)) {
            if (nearby.equals(attacker) || isLocationBlocked(nearby.getLocation())) {
                continue;
            }
            double distance = nearby.getLocation().distanceSquared(impactLocation);
            if (distance < closestDistance) {
                closestDistance = distance;
                target = nearby;
            }
        }
        if (target == null) {
            return;
        }

        Location targetLocation = target.getLocation();
        world.strikeLightningEffect(targetLocation);
        world.playSound(targetLocation, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.0F, 1.0F);
        target.damage(damage);

        dev.grzybalson.utils.ColorUtils.sendTitle(target, "§5Wzmocniona elytra", "§7Zostałeś zaatakowany przez §f" + attacker.getName(), 10, 50, 20);
        dev.grzybalson.utils.ColorUtils.sendTitle(attacker, "§5Wzmocniona elytra", "§7Zaatakowałeś gracza §f" + target.getName(), 10, 50, 20);

        for (Player nearby : targetLocation.getNearbyPlayers(EFFECT_RADIUS)) {
            if (nearby.equals(attacker)) {
                continue;
            }
            Location nearbyLocation = nearby.getLocation();
            if (nearbyLocation.distanceSquared(targetLocation) > EFFECT_RADIUS_SQUARED) {
                continue;
            }

            Vector velocity = nearbyLocation.toVector().subtract(targetLocation.toVector());
            if (velocity.lengthSquared() < 0.01D) {
                velocity.setX(0.1D).setZ(0.1D);
            } else {
                velocity.normalize();
            }
            velocity.multiply(KNOCKBACK_XZ).setY(KNOCKBACK_Y);
            nearby.setVelocity(velocity);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onToggleGlide(EntityToggleGlideEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Player player)) {
            return;
        }

        if (event.isGliding()) {
            beginCharging(player);
        } else {
            UUID uuid = player.getUniqueId();
            if (ACTIVE_CHARGING.remove(uuid) || ELYTRA_CHARGE.containsKey(uuid)) {
                AWAITING_LANDING.add(uuid);
                if (player.isOnGround()) {
                    finishLanding(player);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (!AWAITING_LANDING.contains(uuid) || event.getTo() == null) {
            return;
        }

        if (!player.isOnGround() && !hasChangedBlock(event.getFrom(), event.getTo())) {
            return;
        }
        if (player.isOnGround()) {
            finishLanding(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clearState(event.getPlayer().getUniqueId());
    }

    private boolean hasChangedBlock(Location from, Location to) {
        return from.getWorld() != to.getWorld()
            || from.getBlockX() != to.getBlockX()
            || from.getBlockY() != to.getBlockY()
            || from.getBlockZ() != to.getBlockZ();
    }
}
