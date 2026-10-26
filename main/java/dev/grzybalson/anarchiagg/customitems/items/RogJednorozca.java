package dev.grzybalson.anarchiagg.customitems.items;

import dev.grzybalson.anarchiagg.AnarchiaGGSettings;
import dev.grzybalson.anarchiagg.customitems.handlers.RegionHandler;
import dev.grzybalson.anarchiagg.customitems.util.EventItemRegistry;
import dev.grzybalson.anarchiagg.customitems.util.EventItemUseGuard;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.spigotmc.event.entity.EntityDismountEvent;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class RogJednorozca implements Listener {

    private static final Set<Material> UNBREAKABLE_BLOCKS = EnumSet.of(
            Material.AIR,
            Material.BEDROCK,
            Material.BARRIER,
            Material.BLUE_GLAZED_TERRACOTTA,
            Material.LIGHT_BLUE_CONCRETE,
            Material.BEACON
    );

    private final JavaPlugin plugin;
    private final RegionHandler regionHandler;
    private final Material rogMaterial;
    private final int rogCooldown;
    private final int rogTime;
    private final double unicornSpeed;
    private final double unicornJumpStrength;
    private final Set<String> blockedRegions = new HashSet<>();

    private static final Map<UUID, Horse> activeUnicorns = new HashMap<>();
    private static final Map<UUID, BukkitTask> activeTasks = new HashMap<>();

    public RogJednorozca(JavaPlugin plugin, RegionHandler regionHandler) {
        this.plugin = plugin;
        this.regionHandler = regionHandler;
        FileConfiguration eventowkiConfig = AnarchiaGGSettings.getConfig(plugin);
        List<String> regionList = eventowkiConfig.getStringList("blocked-regions");
        List<String> regionListItem = eventowkiConfig.getStringList("eventowki.rogjednorozca.blocked-regions");
        if (regionList.isEmpty() && !eventowkiConfig.isSet("blocked-regions")) {
            this.blockedRegions.add("spawn");
        } else {
            this.blockedRegions.addAll(regionList);
        }
        this.blockedRegions.addAll(regionListItem);

        this.rogCooldown = eventowkiConfig.getInt("eventowki.rogjednorozca.cooldown", 30);
        this.rogTime = eventowkiConfig.getInt("eventowki.rogjednorozca.time", 5);
        this.unicornSpeed = Math.max(0.15D, eventowkiConfig.getDouble("eventowki.rogjednorozca.unicorn-speed", 0.38D));
        this.unicornJumpStrength = Math.max(0.7D, eventowkiConfig.getDouble("eventowki.rogjednorozca.unicorn-jump-strength", 1.0D));
        Material configuredMaterial = Material.getMaterial(eventowkiConfig.getString("eventowki.rogjednorozca.material", "GOAT_HORN"));
        this.rogMaterial = configuredMaterial != null ? configuredMaterial : Material.GOAT_HORN;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (item == null || item.getType() == Material.AIR) {
            return;
        }
        if (!EventItemRegistry.matches(plugin, "rogjednorozca", item)) {
            return;
        }
        if (!EventItemUseGuard.tryUse(plugin, player)) {
            return;
        }
        if (EventItemUseGuard.hasItemCooldown(plugin, player, "rogjednorozca", item, rogCooldown)) {
            return;
        }
        if (isLocationBlocked(player.getLocation())) {
            return;
        }
        if (HydroKlatka.TRAPPED_PLAYERS_CAGES.containsKey(player)) {
            return;
        }

        spawnUnicorn(player);
        EventItemUseGuard.applyItemCooldown(plugin, player, "rogjednorozca", item, rogCooldown);
    }

    @EventHandler
    public void onDismount(EntityDismountEvent event) {
        if (event.getEntity() instanceof Player player && activeUnicorns.containsKey(player.getUniqueId())) {
            removeUnicorn(player.getUniqueId());
        }
    }

    private boolean isLocationBlocked(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return true;
        }

        for (String regionName : blockedRegions) {
            if (regionHandler.isLocationInRegion(loc, regionName)) {
                return true;
            }
        }
        return false;
    }

    private void spawnUnicorn(Player player) {
        removeUnicorn(player.getUniqueId());

        World world = player.getWorld();
        Location loc = player.getLocation();
        Horse unicorn = (Horse) world.spawnEntity(loc, EntityType.HORSE);
        unicorn.setTamed(true);
        unicorn.setOwner(player);
        unicorn.getInventory().setSaddle(new ItemStack(Material.SADDLE));
        unicorn.setColor(Horse.Color.WHITE);
        unicorn.setStyle(Horse.Style.NONE);
        unicorn.setGlowing(true);
        unicorn.setAdult();
        unicorn.setJumpStrength(unicornJumpStrength);
        unicorn.setInvulnerable(false);

        AttributeInstance movement = unicorn.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (movement != null) {
            movement.setBaseValue(unicornSpeed);
        }

        clearPath(unicorn, player);
        unicorn.addPassenger(player);

        activeUnicorns.put(player.getUniqueId(), unicorn);

        BukkitTask breakerTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Horse active = activeUnicorns.get(player.getUniqueId());
            if (active == null || active.isDead() || !player.isOnline() || player.getVehicle() != active) {
                stopUnicorn(player.getUniqueId(), active != null && !active.isDead());
                return;
            }
            clearPath(active, player);
        }, 0L, 1L);
        activeTasks.put(player.getUniqueId(), breakerTask);

        Bukkit.getScheduler().runTaskLater(plugin, () -> removeUnicorn(player.getUniqueId()), Math.max(20L, rogTime * 20L));
    }

    @EventHandler
    public void onUnicornDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Horse horse)) {
            return;
        }

        UUID ownerId = getActiveUnicornOwner(horse.getUniqueId());
        if (ownerId == null) {
            return;
        }

        stopUnicorn(ownerId, false);
        event.getDrops().clear();
        event.setDroppedExp(0);
    }

    private UUID getActiveUnicornOwner(UUID horseId) {
        for (Map.Entry<UUID, Horse> entry : activeUnicorns.entrySet()) {
            Horse horse = entry.getValue();
            if (horse != null && horse.getUniqueId().equals(horseId)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private void clearPath(Horse unicorn, Player rider) {
        Location base = unicorn.getLocation();
        Vector direction = unicorn.getVelocity().clone();
        if (direction.lengthSquared() < 0.01D) {
            direction = rider.getLocation().getDirection().clone();
        }
        direction.setY(0);
        if (direction.lengthSquared() < 0.0001D) {
            direction = new Vector(0, 0, 1);
        }
        direction.normalize();

        ensureBreathingRoom(base);
        breakAhead(base, direction, 0.75D);
        breakAhead(base, direction, 1.25D);
        breakAhead(base, direction, 1.75D);
    }

    private void ensureBreathingRoom(Location base) {
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                for (int y = 0; y <= 2; y++) {
                    breakBlock(base.clone().add(x, y, z).getBlock());
                }
            }
        }
    }

    private void breakAhead(Location base, Vector direction, double distance) {
        Location center = base.clone().add(direction.clone().multiply(distance));
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                for (int y = 0; y <= 2; y++) {
                    breakBlock(center.clone().add(x * 0.8D, y, z * 0.8D).getBlock());
                }
            }
        }
    }

    private void breakBlock(Block block) {
        Material type = block.getType();
        if (UNBREAKABLE_BLOCKS.contains(type) || !type.isBlock() || block.isLiquid()) {
            return;
        }
        if (!type.isAir()) {
            block.breakNaturally();
        }
    }

    private void removeUnicorn(UUID playerUUID) {
        stopUnicorn(playerUUID, true);
    }

    private void stopUnicorn(UUID playerUUID, boolean removeHorse) {
        BukkitTask task = activeTasks.remove(playerUUID);
        if (task != null) {
            task.cancel();
        }

        Horse horse = activeUnicorns.remove(playerUUID);
        if (removeHorse && horse != null && !horse.isDead()) {
            horse.remove();
        }
    }

    public static void disable() {
        for (BukkitTask task : activeTasks.values()) {
            if (task != null) {
                task.cancel();
            }
        }
        activeTasks.clear();

        for (Horse horse : activeUnicorns.values()) {
            if (horse != null && !horse.isDead()) {
                horse.remove();
            }
        }
        activeUnicorns.clear();
    }
}
