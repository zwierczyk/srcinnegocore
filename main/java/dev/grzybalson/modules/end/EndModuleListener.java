package dev.grzybalson.modules.end;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.configs.EndModuleConfig;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class EndModuleListener implements Listener {

    private static final String MANAGED_CRYSTAL_META = "hollycore_managed_end_crystal_explosion";

    private final HollyCore plugin;
    private final EndModuleConfig config;
    private final Map<String, TrackedBlock> trackedBlocks = new ConcurrentHashMap<>();
    private final Set<Material> allowedBlockTypes;
    private final BukkitTask worldborderPushTask;

    public EndModuleListener(HollyCore plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager().getEndModuleConfig();
        this.allowedBlockTypes = config.getAllowedBlocks().stream()
            .map(this::toMaterial)
            .filter(material -> material != null && material.isBlock())
            .collect(Collectors.toSet());
        this.worldborderPushTask = Bukkit.getScheduler().runTaskTimer(
            plugin,
            this::pushPlayersAwayFromWorldborder,
            Math.max(1L, config.getWorldborderPushPeriodTicks()),
            Math.max(1L, config.getWorldborderPushPeriodTicks())
        );
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!isManaged(event.getPlayer(), event.getBlockPlaced().getWorld())) {
            return;
        }
        if (!config.isRestrictBlockPlaceAndBreak()) {
            return;
        }

        Material type = event.getBlockPlaced().getType();
        if (!allowedBlockTypes.contains(type)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ColorUtils.colorizeComponent(config.getPlaceDeniedMessage()));
            return;
        }

        scheduleRemoval(event.getBlockPlaced());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!isManaged(event.getPlayer(), event.getBlock().getWorld())) {
            cancelTracking(event.getBlock());
            return;
        }
        if (!config.isRestrictBlockPlaceAndBreak()) {
            cancelTracking(event.getBlock());
            return;
        }

        Material type = event.getBlock().getType();
        if (!allowedBlockTypes.contains(type)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ColorUtils.colorizeComponent(config.getBreakDeniedMessage()));
            return;
        }

        cancelTracking(event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCrystalUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        if (!isManaged(player, player.getWorld())) {
            return;
        }

        if (event.getItem() == null || event.getItem().getType() != Material.END_CRYSTAL) {
            return;
        }

        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }

        Material clickedType = clicked.getType();
        if (clickedType != Material.OBSIDIAN && clickedType != Material.BEDROCK) {
            event.setCancelled(true);
            player.sendMessage(ColorUtils.colorizeComponent(config.getPlaceDeniedMessage()));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEnderPearlUse(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        if (!isManaged(player, player.getWorld())) {
            return;
        }
        if (!isEnderPearlUse(event)) {
            return;
        }
        if (!shouldBlockPearl(player)) {
            return;
        }

        event.setCancelled(true);
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        player.sendMessage(ColorUtils.colorizeComponent(config.getWorldborderPearlDeniedMessage()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEnderPearlTeleport(PlayerTeleportEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
            return;
        }

        Player player = event.getPlayer();
        if (!isManaged(player, player.getWorld())) {
            return;
        }

        Location target = event.getTo();
        if (target != null && !isInsideWorldborder(target)) {
            event.setCancelled(true);
            player.sendMessage(ColorUtils.colorizeComponent(config.getWorldborderPearlDeniedMessage()));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onCrystalDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof EnderCrystal crystal)) {
            return;
        }
        if (!isManaged(crystal.getWorld())) {
            return;
        }
        if (crystal.hasMetadata(MANAGED_CRYSTAL_META)) {
            return;
        }

        event.setCancelled(true);
        crystal.setMetadata(MANAGED_CRYSTAL_META, new FixedMetadataValue(plugin, true));
        createManagedCrystalExplosion(crystal);
        applyManagedCrystalDamageAndKnockback(crystal);

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (crystal.isValid() && !crystal.isDead()) {
                crystal.remove();
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplosionPrime(ExplosionPrimeEvent event) {
        if (!(event.getEntity() instanceof EnderCrystal crystal)) {
            return;
        }
        if (!isManaged(crystal.getWorld())) {
            return;
        }

        event.setFire(config.isCrystalFire());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!(event.getEntity() instanceof EnderCrystal crystal)) {
            return;
        }
        if (!isManaged(crystal.getWorld())) {
            return;
        }

        if (!config.isCrystalBlockDamage()) {
            event.blockList().clear();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        if (config.isCrystalFire()) {
            return;
        }
        if (!isManaged(event.getBlock().getWorld())) {
            return;
        }
        if (event.getCause() != BlockIgniteEvent.IgniteCause.EXPLOSION) {
            return;
        }

        Location location = event.getBlock().getLocation();
        boolean crystalNearby = location.getWorld() != null
            && location.getWorld().getNearbyEntities(location, 8.0, 8.0, 8.0).stream().anyMatch(entity -> entity instanceof EnderCrystal);
        if (crystalNearby) {
            event.setCancelled(true);
        }
    }

    public void shutdown() {
        if (worldborderPushTask != null) {
            worldborderPushTask.cancel();
        }
        trackedBlocks.values().forEach(tracked -> {
            tracked.task.cancel();
            if (!config.isRemovePlacedBlocksOnShutdown()) {
                return;
            }

            Block block = tracked.location.getBlock();
            if (block.getType() == tracked.material) {
                block.setType(Material.AIR, false);
            }
        });
        trackedBlocks.clear();
    }

    private void createManagedCrystalExplosion(EnderCrystal crystal) {
        World world = crystal.getWorld();
        Location location = crystal.getLocation();

        try {
            world.createExplosion(location, 6.0F, config.isCrystalFire(), config.isCrystalBlockDamage(), crystal);
        } catch (NoSuchMethodError ignored) {
            world.createExplosion(location, 6.0F, config.isCrystalFire(), config.isCrystalBlockDamage());
        }
    }

    private void applyManagedCrystalDamageAndKnockback(EnderCrystal crystal) {
        Location center = crystal.getLocation();
        World world = center.getWorld();
        if (world == null) {
            return;
        }

        double explosionRadius = 12.0D;
        for (LivingEntity target : world.getNearbyLivingEntities(center, explosionRadius, explosionRadius, explosionRadius)) {
            if (target.isDead() || !target.isValid() || target.equals(crystal)) {
                continue;
            }

            Location targetCenter = target.getLocation().add(0.0D, target.getHeight() * 0.5D, 0.0D);
            Vector direction = targetCenter.toVector().subtract(center.toVector());
            double distance = direction.length();
            if (distance > explosionRadius) {
                continue;
            }

            double exposure = getExplosionExposure(center, target);
            if (exposure <= 0.0D) {
                continue;
            }

            double impact = (1.0D - (distance / explosionRadius)) * exposure;
            if (impact <= 0.0D) {
                continue;
            }

            double damage = (((impact * impact) + impact) / 2.0D) * 7.0D * explosionRadius + 1.0D;
            if (damage > 0.0D) {
                target.damage(damage, crystal);
            }

            if (distance > 1.0E-6D) {
                Vector knockback = direction.normalize().multiply(impact);
                knockback.setY(knockback.getY() + (impact * 0.1D));
                target.setVelocity(target.getVelocity().add(knockback));
            }
        }
    }

    private double getExplosionExposure(Location origin, LivingEntity target) {
        BoundingBox box = target.getBoundingBox();
        if (box == null) {
            return hasLineOfSight(origin, target.getEyeLocation()) ? 1.0D : 0.0D;
        }

        int clear = 0;
        int total = 0;
        double[] xs = {box.getMinX(), (box.getMinX() + box.getMaxX()) * 0.5D, box.getMaxX()};
        double[] ys = {box.getMinY(), (box.getMinY() + box.getMaxY()) * 0.5D, box.getMaxY()};
        double[] zs = {box.getMinZ(), (box.getMinZ() + box.getMaxZ()) * 0.5D, box.getMaxZ()};

        for (double x : xs) {
            for (double y : ys) {
                for (double z : zs) {
                    total++;
                    Location sample = new Location(origin.getWorld(), x, y, z);
                    if (hasLineOfSight(origin, sample)) {
                        clear++;
                    }
                }
            }
        }

        return total <= 0 ? 0.0D : (double) clear / (double) total;
    }

    private boolean hasLineOfSight(Location origin, Location target) {
        World world = origin.getWorld();
        if (world == null) {
            return false;
        }

        Vector direction = target.toVector().subtract(origin.toVector());
        double length = direction.length();
        if (length <= 1.0E-6D) {
            return true;
        }

        RayTraceResult result = world.rayTraceBlocks(origin, direction, length, org.bukkit.FluidCollisionMode.NEVER, true);
        return result == null || result.getHitBlock() == null;
    }

    private void pushPlayersAwayFromWorldborder() {
        double pushDistance = Math.max(0.0D, config.getWorldborderPushDistance());
        double pushStrength = Math.max(0.0D, config.getWorldborderPushStrength());
        if (pushDistance <= 0.0D || pushStrength <= 0.0D) {
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!isManaged(player, player.getWorld())) {
                continue;
            }

            double distance = distanceInsideWorldborder(player.getLocation());
            if (Double.isNaN(distance) || distance > pushDistance) {
                continue;
            }

            Vector push = directionToWorldborderCenter(player.getLocation());
            if (push.lengthSquared() <= 1.0E-6D) {
                continue;
            }

            double multiplier = distance < 0.0D ? pushStrength * 1.4D : pushStrength;
            Vector velocity = push.normalize().multiply(multiplier);
            velocity.setY(Math.max(player.getVelocity().getY(), 0.15D));
            player.setVelocity(velocity);
        }
    }

    private boolean isEnderPearlUse(PlayerInteractEvent event) {
        EquipmentSlot hand = event.getHand();
        if (hand == null) {
            return false;
        }

        ItemStack item = hand == EquipmentSlot.OFF_HAND
            ? event.getPlayer().getInventory().getItemInOffHand()
            : event.getPlayer().getInventory().getItemInMainHand();
        return item.getType() == Material.ENDER_PEARL;
    }

    private boolean shouldBlockPearl(Player player) {
        if (!config.isBlockEnderPearlsOutsideWorldborder()) {
            return false;
        }

        Location eyeLocation = player.getEyeLocation();
        if (!isInsideWorldborder(eyeLocation)) {
            return true;
        }

        Vector direction = eyeLocation.getDirection();
        if (direction.lengthSquared() <= 1.0E-6D) {
            return false;
        }

        Vector step = direction.normalize().multiply(2.0D);
        Location sample = eyeLocation.clone();
        for (int i = 0; i < 32; i++) {
            sample.add(step);
            if (!isInsideWorldborder(sample)) {
                return true;
            }
        }
        return false;
    }

    private boolean isInsideWorldborder(Location location) {
        return distanceInsideWorldborder(location) >= 0.0D;
    }

    private double distanceInsideWorldborder(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return Double.NaN;
        }

        WorldBorder border = world.getWorldBorder();
        Location center = border.getCenter();
        double halfSize = border.getSize() * 0.5D;
        double dx = Math.abs(location.getX() - center.getX());
        double dz = Math.abs(location.getZ() - center.getZ());
        return halfSize - Math.max(dx, dz);
    }

    private Vector directionToWorldborderCenter(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return new Vector();
        }

        Location center = world.getWorldBorder().getCenter();
        return new Vector(center.getX() - location.getX(), 0.0D, center.getZ() - location.getZ());
    }

    private void scheduleRemoval(Block block) {
        cancelTracking(block);

        int delayTicks = Math.max(1, config.getTemporaryBlockLifetimeSeconds()) * 20;
        String key = key(block.getLocation());
        Material material = block.getType();
        Location location = block.getLocation().toBlockLocation();

        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            TrackedBlock tracked = trackedBlocks.remove(key);
            if (tracked == null) {
                return;
            }

            Block current = tracked.location.getBlock();
            if (current.getType() == tracked.material) {
                current.setType(Material.AIR, false);
            }
        }, delayTicks);

        trackedBlocks.put(key, new TrackedBlock(location, material, task));
    }

    private void cancelTracking(Block block) {
        TrackedBlock tracked = trackedBlocks.remove(key(block.getLocation()));
        if (tracked != null) {
            tracked.task.cancel();
        }
    }

    private boolean isManaged(Player player, World world) {
        if (!isManaged(world)) {
            return false;
        }
        return !hasBypass(player);
    }

    private boolean isManaged(World world) {
        if (!config.isEnabled() || world == null) {
            return false;
        }

        for (String worldName : config.getWorlds()) {
            if (world.getName().equalsIgnoreCase(worldName)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasBypass(Player player) {
        return player != null && config.isOpsBypassRestrictions() && player.isOp();
    }

    private Material toMaterial(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        try {
            return Material.valueOf(input.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            plugin.getLogger().warning("Niepoprawny materiał w end/end.yml: " + input);
            return null;
        }
    }

    private static String key(Location location) {
        Location blockLocation = location.toBlockLocation();
        return blockLocation.getWorld().getName() + ':' + blockLocation.getBlockX() + ':' + blockLocation.getBlockY() + ':' + blockLocation.getBlockZ();
    }

    private record TrackedBlock(Location location, Material material, BukkitTask task) {}
}
