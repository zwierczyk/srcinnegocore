package dev.grzybalson.anarchiagg.customitems.items;

import dev.grzybalson.anarchiagg.AnarchiaGGSettings;

import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import dev.grzybalson.anarchiagg.customitems.handlers.RegionHandler;
import dev.grzybalson.anarchiagg.customitems.util.EventItemRegistry;
import dev.grzybalson.anarchiagg.customitems.util.EventItemUseGuard;
import dev.grzybalson.anarchiagg.szafa.BoostHandler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Particle;
import org.bukkit.boss.BossBar;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.Bukkit;
import org.bukkit.Sound;

public class HydroKlatka implements Listener {
    private static final int PARTICLE_STRIDE = 4;
    private static final int WAKE_PARTICLE_STRIDE = 3;
    private static final int SOUND_EVERY_LAYERS = 3;

    private final String hydroKlatkaName;
    private final Material hydroKlatkaMaterial;
    private final JavaPlugin plugin;
    private final RegionHandler regionHandler;

    private final Set<String> blockedRegions = new HashSet<>();
    private final Material cageBlockMaterial = Material.BLUE_GLAZED_TERRACOTTA;
    private final Material innerBlockMaterial = Material.LIGHT_BLUE_CONCRETE;
    private final int cooldownSeconds;
    private final int time;
    private final int radius;
    private final boolean elytra;
    private static final Map<UUID, HydroKlatkaInstance> ACTIVE_CAGES = new HashMap<>();
    private static final Map<UUID, Set<UUID>> ACTIVE_CAGES_BY_WORLD = new HashMap<>();
    private static final Map<UUID, Integer> TRAPPED_PLAYERS_COUNT = new HashMap<>();
    public static final Map<UUID, Set<UUID>> TRAPPED_PLAYERS_CAGES = new HashMap<>();
    private static final Map<Location, CageBlockData> ALL_CONTROLLED_BLOCKS = new HashMap<>();

    public HydroKlatka(JavaPlugin plugin, RegionHandler regionHandler) {
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
        String hydroKlatkaItem = eventowkiConfig.getString("eventowki.hydroklatka.item", "BLAZE_ROD");
        hydroKlatkaName = eventowkiConfig.getString("eventowki.hydroklatka.name", "Hydro Klatka").replace("&", "§");
        hydroKlatkaMaterial = Material.valueOf(hydroKlatkaItem.toUpperCase());
        cooldownSeconds = eventowkiConfig.getInt("eventowki.hydroklatka.cooldown", 60);
        time = eventowkiConfig.getInt("eventowki.hydroklatka.time", 10);
        radius = eventowkiConfig.getInt("eventowki.hydroklatka.radius", 8);
        elytra = eventowkiConfig.getBoolean("eventowki.hydroklatka.elytra-disable", true);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (TRAPPED_PLAYERS_CAGES.isEmpty()) {
                    return;
                }
                Iterator<Map.Entry<UUID, Set<UUID>>> playerIterator = TRAPPED_PLAYERS_CAGES.entrySet().iterator();
                while (playerIterator.hasNext()) {
                    Map.Entry<UUID, Set<UUID>> entry = playerIterator.next();
                    UUID playerId = entry.getKey();
                    Player player = org.bukkit.Bukkit.getPlayer(playerId);
                    if (player == null || !player.isOnline()) {
                        playerIterator.remove();
                        TRAPPED_PLAYERS_COUNT.remove(playerId);
                        continue;
                    }
                    Location playerLocation = player.getLocation();
                    Iterator<UUID> cageIterator = entry.getValue().iterator();

                    while (cageIterator.hasNext()) {
                        UUID cageId = cageIterator.next();
                        HydroKlatkaInstance cage = ACTIVE_CAGES.get(cageId);
                        double maxDistance = cage == null ? 0.0D : cage.restrictedRadius + 1.0D;
                        boolean outside = cage == null
                            || playerLocation.getWorld() != cage.cageCenter.getWorld()
                            || playerLocation.distanceSquared(cage.cageCenter) > maxDistance * maxDistance;
                        if (!outside) {
                            continue;
                        }

                        cageIterator.remove();
                        TRAPPED_PLAYERS_COUNT.computeIfPresent(playerId, (ignored, count) -> count > 1 ? count - 1 : null);
                        if (cage != null) {
                            cage.removePlayerVisualOnly(player);
                        }
                    }

                    if (entry.getValue().isEmpty()) {
                        playerIterator.remove();
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);

    }

    private static boolean changedBlock(Location from, Location to) {
        return to != null && (from.getBlockX() != to.getBlockX()
                || from.getBlockY() != to.getBlockY()
                || from.getBlockZ() != to.getBlockZ()
                || !from.getWorld().equals(to.getWorld()));
    }

    private UUID getPlayerCageIDIfInside(Player player) {
        Location playerLocation = player.getLocation();
        World playerWorld = playerLocation.getWorld();

        for (HydroKlatkaInstance cage : activeCagesInWorld(playerWorld)) {
            double maxDistance = cage.restrictedRadius;
            if (playerLocation.distanceSquared(cage.cageCenter) <= maxDistance * maxDistance) {
                return cage.id;
            }
        }
        return null;
    }

    private static void registerActiveCage(HydroKlatkaInstance cage) {
        ACTIVE_CAGES.put(cage.id, cage);
        World world = cage.cageCenter.getWorld();
        if (world != null) {
            ACTIVE_CAGES_BY_WORLD.computeIfAbsent(world.getUID(), ignored -> new HashSet<>()).add(cage.id);
        }
    }

    private static void unregisterActiveCage(HydroKlatkaInstance cage) {
        ACTIVE_CAGES.remove(cage.id);
        World world = cage.cageCenter.getWorld();
        if (world == null) {
            return;
        }
        Set<UUID> cageIds = ACTIVE_CAGES_BY_WORLD.get(world.getUID());
        if (cageIds == null) {
            return;
        }
        cageIds.remove(cage.id);
        if (cageIds.isEmpty()) {
            ACTIVE_CAGES_BY_WORLD.remove(world.getUID());
        }
    }

    private static List<HydroKlatkaInstance> activeCagesInWorld(World world) {
        if (world == null) {
            return List.of();
        }
        Set<UUID> cageIds = ACTIVE_CAGES_BY_WORLD.get(world.getUID());
        if (cageIds == null || cageIds.isEmpty()) {
            return List.of();
        }
        List<HydroKlatkaInstance> cages = new ArrayList<>(cageIds.size());
        for (UUID cageId : new HashSet<>(cageIds)) {
            HydroKlatkaInstance cage = ACTIVE_CAGES.get(cageId);
            if (cage != null) {
                cages.add(cage);
            }
        }
        return cages;
    }

    public static void cleanupCagesOnDisable() {
        for (HydroKlatkaInstance instance : new HashSet<>(ACTIVE_CAGES.values())) {
            instance.removeInstantly();
        }
        ACTIVE_CAGES.clear();
        ACTIVE_CAGES_BY_WORLD.clear();
        TRAPPED_PLAYERS_CAGES.clear();
        TRAPPED_PLAYERS_COUNT.clear();
        ALL_CONTROLLED_BLOCKS.clear();
    }

    private void removeCagesInstantly() {
        for (HydroKlatkaInstance instance : new HashSet<>(ACTIVE_CAGES.values())) {
            instance.removeInstantly();
        }
        ACTIVE_CAGES.clear();
        ACTIVE_CAGES_BY_WORLD.clear();
        TRAPPED_PLAYERS_COUNT.clear();
        TRAPPED_PLAYERS_CAGES.clear();
        ALL_CONTROLLED_BLOCKS.clear();
    }

    public boolean isLocationControlled(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        Location blockLoc = loc.getBlock().getLocation();
        if (ALL_CONTROLLED_BLOCKS.containsKey(blockLoc)) return true;

        for (HydroKlatkaInstance cage : activeCagesInWorld(loc.getWorld())) {
            if (cage.allCageLocationsCache != null && cage.allCageLocationsCache.contains(blockLoc)) {
                return true;
            }
        }

        return false;
    }

    private static org.bukkit.block.data.BlockData captureOriginalBlockData(Location location, Block block) {
        Material type = block.getType();
        if (type == Material.SNOW && BoostHandler.consumeTemporarySnow(location)) {
            return Material.AIR.createBlockData();
        }
        if (type == Material.BEACON && CudownaLatarnia.clearActiveBeacon(location)) {
            return Material.AIR.createBlockData();
        }
        return block.getBlockData().clone();
    }

    public boolean isPlayerTrappedOrInsideCage(Player player) {
        if (player == null || !player.isOnline()) return false;
        Set<UUID> cages = TRAPPED_PLAYERS_CAGES.get(player.getUniqueId());
        if (cages != null && !cages.isEmpty()) {
            return true;
        }
        return getPlayerCageIDIfInside(player) != null;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();

        if (isPlayerTrappedOrInsideCage(player)) {
            if (mainHand.getType() == Material.EGG || offHand.getType() == Material.EGG 
                || mainHand.getType() == Material.CHORUS_FRUIT || offHand.getType() == Material.CHORUS_FRUIT
                || mainHand.getType() == Material.FIREWORK_ROCKET || offHand.getType() == Material.FIREWORK_ROCKET) {
                event.setCancelled(true);
                if (player.isGliding()) {
                    player.setGliding(false);
                }
                dev.grzybalson.utils.ColorUtils.sendTitle(player, "", "§cNie możesz używać tego przedmiotu w Hydro Klatce!");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return;
            }
        }

        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (mainHand.getType() == Material.AIR) return;
        if (!EventItemRegistry.matches(plugin, "hydroklatka", mainHand)) return;
        if (!EventItemUseGuard.tryUse(plugin, player)) return;

        event.setCancelled(true);
        if (isPlayerTrappedOrInsideCage(player)) {
            return;
        }

        if (isLocationBlocked(player.getLocation())) {
            return;
        }

        if (EventItemUseGuard.hasItemCooldown(plugin, player, "hydroklatka", mainHand, cooldownSeconds)) {
            return;
        }
        EventItemUseGuard.applyItemCooldown(plugin, player, "hydroklatka", mainHand, cooldownSeconds);
        Fireball fireball = (Fireball) player.getWorld().spawnEntity(player.getEyeLocation(), EntityType.FIREBALL);
        fireball.setShooter(player);
        fireball.setDirection(player.getLocation().getDirection());
        fireball.setIsIncendiary(false);
        fireball.setYield(0F);
        fireball.setDirection(player.getLocation().getDirection().multiply(100));
        fireball.setMetadata("HydroKlatka", new org.bukkit.metadata.FixedMetadataValue(plugin, UUID.randomUUID().toString()));
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
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlockBreakConcrete(BlockBreakEvent event) {
        Block block = event.getBlock();
        CageBlockData data = ALL_CONTROLLED_BLOCKS.get(block.getLocation());
        if (data != null && isBreakableFluidReplacement(block, data)) {
            event.setCancelled(false);
            event.setDropItems(false);
            event.setExpToDrop(0);
            return;
        }

        if (isPlayerTrappedOrInsideCage(event.getPlayer())) {
            if (block.getType().equals(Material.LIGHT_BLUE_CONCRETE)) {
                event.setDropItems(false);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onToggleGlide(EntityToggleGlideEvent event) {
        if (!elytra) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.isGliding() && isPlayerTrappedOrInsideCage(player)) {
            event.setCancelled(true);
            player.setGliding(false);
            dev.grzybalson.utils.ColorUtils.sendTitle(player, "", "§cNie możesz latać §4elytrą §cw Hydro Klatce");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onMove(PlayerMoveEvent event) {
        if (!elytra) return;
        Player player = event.getPlayer();
        if (!player.isGliding()) return;
        if (ACTIVE_CAGES.isEmpty() && TRAPPED_PLAYERS_CAGES.isEmpty()) return;
        if (isPlayerTrappedOrInsideCage(player)) {
            player.setGliding(false);
            dev.grzybalson.utils.ColorUtils.sendTitle(player, "", "§cNie możesz latać §4elytrą §cw Hydro Klatce");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
        }
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Fireball)) return;
        if (!event.getEntity().hasMetadata("HydroKlatka")) return;

        Location hitLocation = event.getHitBlock() != null ? event.getHitBlock().getLocation() : event.getEntity().getLocation();
        if (isLocationBlocked(hitLocation)) {
            event.getEntity().remove();
            return;
        }

        Location cageCenter = hitLocation.clone().add(0.5, 0.5, 0.5);
        if (collidesWithExistingCage(cageCenter, radius)) {
            event.getEntity().remove();
            Player shooter = (Player) event.getEntity().getShooter();
            if (shooter != null) {
                dev.grzybalson.utils.ColorUtils.sendTitle(shooter, "", "§cW pobliżu znajduje się już inna Hydro Klatka!");
                shooter.playSound(shooter.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            }
            return;
        }

        UUID cageID = UUID.fromString(event.getEntity().getMetadata("HydroKlatka").get(0).asString());
        HydroKlatkaInstance newCage = new HydroKlatkaInstance(cageID, cageCenter, plugin, radius, radius - 1, cageBlockMaterial, innerBlockMaterial);
        registerActiveCage(newCage);
        newCage.start();
    }

    private boolean collidesWithExistingCage(Location center, int radius) {
        for (HydroKlatkaInstance existingCage : activeCagesInWorld(center.getWorld())) {
            double minDistance = radius * 2.0;
            if (existingCage.cageCenter.distanceSquared(center) < minDistance * minDistance) {
                return true;
            }
        }
        return false;
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        if (event.getEntity() instanceof Fireball && event.getEntity().hasMetadata("HydroKlatka")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        CageBlockData data = ALL_CONTROLLED_BLOCKS.get(block.getLocation());
        if (block.getType() == cageBlockMaterial && data != null) {
            if (isBreakableFluidReplacement(block, data)) {
                event.setDropItems(false);
                event.setExpToDrop(0);
                return;
            }
            event.setCancelled(true);
            Player player = event.getPlayer();
            dev.grzybalson.utils.ColorUtils.sendTitle(player, "", "§cNie możesz zniszczyć Hydro Klatki!");
            player.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 1f);
        }
    }

    private boolean isBreakableFluidReplacement(Block block, CageBlockData data) {
        Material type = block.getType();
        return (type == cageBlockMaterial || type == innerBlockMaterial)
            && isFluidOrWaterPlant(data.getOriginalMaterial());
    }

    private boolean isFluidOrWaterPlant(Material material) {
        return material == Material.WATER
            || material == Material.LAVA
            || material == Material.BUBBLE_COLUMN
            || material == Material.KELP
            || material == Material.KELP_PLANT
            || material == Material.SEAGRASS
            || material == Material.TALL_SEAGRASS;
    }

    public boolean isLocationInsideCageVolume(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        if (isLocationControlled(loc)) return true;
        for (HydroKlatkaInstance cage : activeCagesInWorld(loc.getWorld())) {
            if (loc.distanceSquared(cage.cageCenter) <= (cage.restrictedRadius * cage.restrictedRadius)) {
                return true;
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Location placeLoc = event.getBlockPlaced().getLocation();

        if (isPlayerTrappedOrInsideCage(player) || isLocationInsideCageVolume(placeLoc)) {
            if (event.getBlock().getType() == Material.BEACON) return;
            event.setCancelled(true);
            dev.grzybalson.utils.ColorUtils.sendTitle(player, "", "§cNie możesz stawiać bloków w Hydro Klatce!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (isPlayerTrappedOrInsideCage(player) || (event.getClickedBlock() != null && isLocationInsideCageVolume(event.getClickedBlock().getLocation()))) {
            if (player.getInventory().getItemInMainHand().getType() == Material.WATER_BUCKET || player.getInventory().getItemInOffHand().getType() == Material.WATER_BUCKET) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        Location from = event.getFrom();

        if (to == null || (from.getBlockX() == to.getBlockX()
            && from.getBlockY() == to.getBlockY()
            && from.getBlockZ() == to.getBlockZ()
            && from.getWorld() == to.getWorld())) return;
        if (ACTIVE_CAGES.isEmpty()) return;
        List<HydroKlatkaInstance> activeWorldCages = activeCagesInWorld(to.getWorld());
        if (activeWorldCages.isEmpty()) return;

        Player player = event.getPlayer();

        Set<UUID> controlledCages = TRAPPED_PLAYERS_CAGES.get(player.getUniqueId());

        for (HydroKlatkaInstance cage : activeWorldCages) {
            double restrictedRadiusSquared = cage.restrictedRadius * cage.restrictedRadius;
            double distanceSquared = to.distanceSquared(cage.cageCenter);
            boolean isTrappedInThisOne = (controlledCages != null && controlledCages.contains(cage.id));

            if (isTrappedInThisOne) {
                if (isPlayerInsideCageWall(to, cage)) {
                    event.setCancelled(true);
                    teleportToCageCenter(player, cage);
                    return;
                }
                if (distanceSquared > restrictedRadiusSquared) {
                    event.setCancelled(true);
                    teleportBack(player, cage, true);
                    return;
                }
            } else {
                if (distanceSquared <= restrictedRadiusSquared) {
                    event.setCancelled(true);
                    teleportBack(player, cage, false);
                    return;
                }
            }
        }

        if (controlledCages == null || controlledCages.isEmpty()) {
            UUID activeCageID = getPlayerCageIDIfInside(player);
            if (activeCageID != null) {
                HydroKlatkaInstance cage = ACTIVE_CAGES.get(activeCageID);
                if (cage != null) {
                    cage.addPlayerToTrappedSet(player);
                }
            }
        }
    }

    private void teleportBack(Player player, HydroKlatkaInstance cage, boolean wasInside) {
        Vector dir = player.getLocation().toVector().subtract(cage.cageCenter.toVector()).normalize();
        double dist = wasInside ? (cage.restrictedRadius - 0.5) : (cage.restrictedRadius + 0.6);

        Location loc = cage.cageCenter.clone().add(dir.multiply(dist));
        loc.setPitch(player.getLocation().getPitch());
        loc.setYaw(player.getLocation().getYaw());

        dev.grzybalson.utils.ColorUtils.sendTitle(player, "", wasInside ? "§cNie możesz wyjść z Hydro Klatki!" : "§cNie możesz wejść do Hydro Klatki!", 5, 20, 5);
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) player.teleport(loc);
            }
        }.runTask(plugin);
    }

    private void teleportToCageCenter(Player player, HydroKlatkaInstance cage) {
        Location loc = cage.cageCenter.clone().add(0.5D, 0.0D, 0.5D);
        loc.setPitch(player.getLocation().getPitch());
        loc.setYaw(player.getLocation().getYaw());

        dev.grzybalson.utils.ColorUtils.sendTitle(player, "", "§cNie możesz bugować się w ścianie Hydro Klatki!", 5, 20, 5);
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    player.teleport(loc);
                }
            }
        }.runTask(plugin);
    }

    private boolean isPlayerInsideCageWall(Location loc, HydroKlatkaInstance cage) {
        if (loc == null || cage == null || cage.shellLocationsCache == null) {
            return false;
        }

        Location feet = loc.getBlock().getLocation();
        Location head = loc.clone().add(0.0D, 1.0D, 0.0D).getBlock().getLocation();
        return cage.shellLocationsCache.contains(feet) || cage.shellLocationsCache.contains(head);
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
            Set<UUID> cages = TRAPPED_PLAYERS_CAGES.get(player.getUniqueId());
            if (cages != null && !cages.isEmpty()) {
                event.setCancelled(true);
                dev.grzybalson.utils.ColorUtils.sendTitle(player, "", "§cNie możesz użyć tego w Hydro Klatce!");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            }
        }
    }

    private static class CageBlockData {
        private final org.bukkit.block.data.BlockData originalBlockData;
        private final boolean originalWasActiveBeacon;
        private final Map<UUID, Boolean> controllingCages;

        public CageBlockData(org.bukkit.block.data.BlockData originalBlockData, UUID cageID, boolean isShell, boolean originalWasActiveBeacon) {
            this.originalBlockData = originalBlockData != null ? originalBlockData.clone() : Material.AIR.createBlockData();
            this.originalWasActiveBeacon = originalWasActiveBeacon;
            this.controllingCages = new HashMap<>();
            this.controllingCages.put(cageID, isShell);
        }

        public Material getOriginalMaterial() {
            return originalBlockData != null ? originalBlockData.getMaterial() : Material.AIR;
        }

        public org.bukkit.block.data.BlockData getOriginalBlockData() {
            return originalBlockData;
        }

        public void restoreBlock(Location location) {
            Block block = location.getBlock();
            if (originalWasActiveBeacon && !CudownaLatarnia.isActiveBeacon(location)) {
                block.setType(Material.AIR, false);
                return;
            }
            if (originalBlockData != null) {
                block.setBlockData(originalBlockData, false);
            } else {
                block.setType(Material.AIR, false);
            }
        }

        public Map<UUID, Boolean> getControllingCages() {
            return controllingCages;
        }

        public boolean isControlledByAnyShellOtherThan(UUID currentCageId) {
            for (Map.Entry<UUID, Boolean> entry : controllingCages.entrySet()) {
                if (!entry.getKey().equals(currentCageId) && entry.getValue()) {
                    return true;
                }
            }
            return false;
        }
    }

    private class HydroKlatkaInstance {
        private final UUID id;
        private final Location cageCenter;
        private final JavaPlugin plugin;
        private final int cageRadius;
        private final int restrictedRadius;
        private final Material cageBlockMaterial;
        private final Material innerBlockMaterial;
        private final Set<UUID> trappedPlayers = new HashSet<>();
        private Set<Location> shellLocationsCache;
        private Set<Location> innerLocationsCache;
        private Set<Location> allCageLocationsCache;
        private Map<Integer, List<Location>> shellLocationsByY;
        private Map<Integer, List<Location>> innerLocationsByY;
        private Map<Integer, List<Location>> allLocationsByY;
        private final BossBar bossBar;
        private final int BOSSBAR_DURATION_SECONDS = time;
        public HydroKlatkaInstance(UUID id, Location cageCenter, JavaPlugin plugin, int cageRadius, int restrictedRadius, Material cageBlockMaterial, Material innerBlockMaterial) {
            this.id = id;
            this.cageCenter = cageCenter;
            this.plugin = plugin;
            this.cageRadius = cageRadius;
            this.restrictedRadius = restrictedRadius;
            this.cageBlockMaterial = cageBlockMaterial;
            this.innerBlockMaterial = innerBlockMaterial;
            this.bossBar = Bukkit.createBossBar("§bHydro Klatka", BarColor.BLUE, BarStyle.SOLID);
            this.bossBar.setProgress(1.0);
        }

        public void addPlayerToTrappedSet(Player player) {
            UUID uuid = player.getUniqueId();
            if (player.isOnline() && !trappedPlayers.contains(uuid)) {
                TRAPPED_PLAYERS_COUNT.merge(uuid, 1, Integer::sum);
                TRAPPED_PLAYERS_CAGES.computeIfAbsent(uuid, k -> new HashSet<>()).add(id);
                trappedPlayers.add(uuid);
                bossBar.addPlayer(player);
            }
        }

        public void removePlayerFromTrappedSet(UUID uuid) {
            if (removePlayerVisualOnly(uuid)) {
                TRAPPED_PLAYERS_CAGES.computeIfPresent(uuid, (ignored, cages) -> {
                    cages.remove(id);
                    return cages.isEmpty() ? null : cages;
                });
            }
        }

        public void removePlayerFromTrappedSet(Player player) {
            if (player != null) {
                removePlayerFromTrappedSet(player.getUniqueId());
            }
        }

        private boolean removePlayerVisualOnly(UUID uuid) {
            if (!trappedPlayers.remove(uuid)) {
                return false;
            }
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                bossBar.removePlayer(player);
            }
            return true;
        }

        private boolean removePlayerVisualOnly(Player player) {
            return player != null && removePlayerVisualOnly(player.getUniqueId());
        }

        private boolean isLocationBlocked(Location loc) {
            return !HydroKlatka.this.isLocationBlocked(loc);
        }

        private boolean isContainerBlock(Material material) {
            return material == Material.CHEST ||
                    material == Material.TRAPPED_CHEST || material == Material.ENDER_CHEST ||
                    material.toString().endsWith("_SHULKER_BOX") ||
                    material == Material.FURNACE || material == Material.BLAST_FURNACE ||
                    material == Material.SMOKER;
        }

        public void start() {
            shellLocationsCache = getHollowSphere(cageCenter, cageRadius);
            innerLocationsCache = getInnerSphere(cageCenter, cageRadius);
            allCageLocationsCache = new HashSet<>(shellLocationsCache);
            allCageLocationsCache.addAll(innerLocationsCache);
            shellLocationsByY = indexLocationsByY(shellLocationsCache);
            innerLocationsByY = indexLocationsByY(innerLocationsCache);
            allLocationsByY = indexLocationsByY(allCageLocationsCache);
            int highestY = (int) cageCenter.getY() + cageRadius;
            int lowestY = (int) cageCenter.getY() - cageRadius;

            for (Player p : cageCenter.getWorld().getPlayers()) {
                Location playerLocation = p.getLocation();
                double maxDistance = restrictedRadius;
                if (playerLocation.getWorld().equals(cageCenter.getWorld()) && playerLocation.distanceSquared(cageCenter) <= maxDistance * maxDistance) {
                    addPlayerToTrappedSet(p);
                    p.setGliding(false);
                }
            }

            new BukkitRunnable() {
                int durationTicks = BOSSBAR_DURATION_SECONDS * 20;
                @Override
                public void run() {
                    if (!plugin.isEnabled() || !ACTIVE_CAGES.containsKey(id)) {
                        bossBar.removeAll();
                        cancel();
                        return;
                    }

                    if (durationTicks <= 0) {
                        bossBar.removeAll();
                        dissolveCageGradually(lowestY, highestY);
                        cancel();
                        return;
                    }

                    double progress = (double) durationTicks / (BOSSBAR_DURATION_SECONDS * 20.0);
                    bossBar.setProgress(Math.max(0.0, progress));
                    durationTicks -= 10;
                }
            }.runTaskTimer(plugin, 0L, 10L);

            buildCageGradually(highestY, lowestY);
        }

        public void removeInstantly() {
            if (allCageLocationsCache == null) {
                return;
            }
            for (Location loc : allCageLocationsCache) {
                Block block = loc.getBlock();
                CageBlockData data = ALL_CONTROLLED_BLOCKS.get(loc);

                if (data != null) {
                    data.getControllingCages().remove(this.id);
                    if (data.getControllingCages().isEmpty()) {
                        data.restoreBlock(loc);
                        ALL_CONTROLLED_BLOCKS.remove(loc);
                    } else if (data.getControllingCages().values().stream().noneMatch(isShell -> isShell) && shellLocationsCache.contains(loc) && block.getType() == cageBlockMaterial) {
                        block.setType(innerBlockMaterial, false);
                    }
                }
            }

            unregisterActiveCage(this);
            bossBar.removeAll();

            for (UUID uuid : new HashSet<>(trappedPlayers)) {
                removePlayerFromTrappedSet(uuid);
            }
            trappedPlayers.clear();
        }

        private Set<Location> getHollowSphere(Location center, int radius) {
            Set<Location> locations = new HashSet<>();
            World world = center.getWorld();
            int cx = center.getBlockX();
            int cy = center.getBlockY();
            int cz = center.getBlockZ();
            int minimumSquared = (radius - 1) * (radius - 1);
            int maximumSquared = radius * radius;

            for (int x = -radius; x <= radius; x++) {
                for (int y = -radius; y <= radius; y++) {
                    for (int z = -radius; z <= radius; z++) {
                        int distanceSquared = x * x + y * y + z * z;
                        if (distanceSquared < minimumSquared || distanceSquared > maximumSquared) {
                            continue;
                        }
                        Location location = new Location(world, cx + x, cy + y, cz + z);
                        if (isLocationBlocked(location) && !isContainerBlock(location.getBlock().getType())) {
                            locations.add(location);
                        }
                    }
                }
            }
            return locations;
        }

        private Set<Location> getInnerSphere(Location center, int radius) {
            Set<Location> locations = new HashSet<>();
            World world = center.getWorld();
            int cx = center.getBlockX();
            int cy = center.getBlockY();
            int cz = center.getBlockZ();
            int innerRadius = radius - 1;
            int innerRadiusSquared = innerRadius * innerRadius;

            for (int x = -innerRadius; x <= innerRadius; x++) {
                for (int y = -innerRadius; y <= innerRadius; y++) {
                    for (int z = -innerRadius; z <= innerRadius; z++) {
                        int distanceSquared = x * x + y * y + z * z;
                        if (distanceSquared >= innerRadiusSquared) {
                            continue;
                        }
                        Location location = new Location(world, cx + x, cy + y, cz + z);
                        if (isLocationBlocked(location)) {
                            locations.add(location);
                        }
                    }
                }
            }
            return locations;
        }

        private Map<Integer, List<Location>> indexLocationsByY(Set<Location> locations) {
            Map<Integer, List<Location>> indexed = new HashMap<>();
            for (Location location : locations) {
                indexed.computeIfAbsent(location.getBlockY(), ignored -> new java.util.ArrayList<>()).add(location);
            }
            return indexed;
        }

        private void playWaterParticle(Location loc) {
            loc.getWorld().spawnParticle(Particle.WATER_SPLASH, loc.getX() + 0.5, loc.getY() + 0.5, loc.getZ() + 0.5, 4, 0.1, 0.1, 0.1, 0.1);
        }

        private void playWaterWakeParticle(Location loc) {
            loc.getWorld().spawnParticle(Particle.WATER_WAKE, loc.getX() + 0.5, loc.getY() + 0.5, loc.getZ() + 0.5, 1, 0.0, 0.0, 0.0, 0.0);
        }

        private void playWaterParticles(List<Location> locations) {
            int index = 0;
            for (Location location : locations) {
                if (index++ % PARTICLE_STRIDE == 0) {
                    playWaterParticle(location);
                }
            }
        }

        private void playTickSound() {
            for (UUID uuid : trappedPlayers) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    player.playSound(player.getLocation(), Sound.BLOCK_WATER_AMBIENT, 1.5f, 1.0f);
                }
            }
        }


        private void buildCageGradually(int highestY, int lowestY) {
            new BukkitRunnable() {
                int currentY = highestY;
                int layerStep;

                @Override
                public void run() {
                    if (layerStep++ % SOUND_EVERY_LAYERS == 0) {
                        playTickSound();
                    }
                    if (currentY < lowestY) {
                        cancel();
                        return;
                    }

                    playWaterParticles(allLocationsByY.getOrDefault(currentY - 1, List.of()));

                    int shellParticleIndex = 0;
                    for (Location location : shellLocationsByY.getOrDefault(currentY, List.of())) {
                        Block block = location.getBlock();
                        if (isContainerBlock(block.getType())) {
                            continue;
                        }
                        CageBlockData data = ALL_CONTROLLED_BLOCKS.get(location);
                        if (data == null) {
                            ALL_CONTROLLED_BLOCKS.put(location, new CageBlockData(captureOriginalBlockData(location, block), id, true, false));
                            block.setType(cageBlockMaterial, false);
                        } else {
                            data.getControllingCages().put(id, true);
                            if (block.getType() != cageBlockMaterial) {
                                block.setType(cageBlockMaterial, false);
                            }
                        }
                        if (shellParticleIndex++ % WAKE_PARTICLE_STRIDE == 0) {
                            playWaterWakeParticle(location);
                        }
                    }

                    for (Location location : innerLocationsByY.getOrDefault(currentY, List.of())) {
                        Block block = location.getBlock();
                        Material type = block.getType();
                        if (isContainerBlock(type)) {
                            continue;
                        }
                        CageBlockData data = ALL_CONTROLLED_BLOCKS.get(location);
                        if (data == null) {
                            if (type != Material.BEDROCK && type != Material.AIR) {
                                ALL_CONTROLLED_BLOCKS.put(location, new CageBlockData(captureOriginalBlockData(location, block), id, false, false));
                                block.setType(innerBlockMaterial, false);
                            }
                        } else {
                            data.getControllingCages().put(id, false);
                        }
                    }

                    currentY--;
                }
            }.runTaskTimer(plugin, 0L, 2L);
        }

        private void dissolveCageGradually(int lowestY, int highestY) {
            new BukkitRunnable() {
                int currentY = lowestY;
                int layerStep;

                @Override
                public void run() {
                    if (layerStep++ % SOUND_EVERY_LAYERS == 0) {
                        playTickSound();
                    }
                    if (currentY > highestY) {
                        cancel();
                        unregisterActiveCage(HydroKlatkaInstance.this);
                        for (UUID uuid : new HashSet<>(trappedPlayers)) {
                            removePlayerFromTrappedSet(uuid);
                        }
                        trappedPlayers.clear();
                        return;
                    }

                    List<Location> locations = allLocationsByY.getOrDefault(currentY, List.of());
                    playWaterParticles(locations);
                    for (Location location : locations) {
                        Block block = location.getBlock();
                        CageBlockData data = ALL_CONTROLLED_BLOCKS.get(location);
                        if (data == null) {
                            continue;
                        }

                        boolean wasShell = data.getControllingCages().getOrDefault(id, false);
                        data.getControllingCages().remove(id);
                        if (isContainerBlock(data.getOriginalMaterial())) {
                            ALL_CONTROLLED_BLOCKS.remove(location);
                            continue;
                        }
                        if (data.getControllingCages().isEmpty()) {
                            data.restoreBlock(location);
                            ALL_CONTROLLED_BLOCKS.remove(location);
                        } else if (wasShell && shellLocationsCache.contains(location)
                            && !data.isControlledByAnyShellOtherThan(id)
                            && block.getType() == cageBlockMaterial) {
                            block.setType(data.getOriginalMaterial() == Material.AIR ? Material.AIR : innerBlockMaterial, false);
                        }
                    }
                    currentY++;
                }
            }.runTaskTimer(plugin, 0L, 2L);
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
    @EventHandler
    public void onDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID uuid = player.getUniqueId();
        Set<UUID> playerCages = TRAPPED_PLAYERS_CAGES.get(uuid);

        if (playerCages != null && !playerCages.isEmpty()) {
            Set<UUID> cagesCopy = new HashSet<>(playerCages);
            for (UUID cageID : cagesCopy) {
                HydroKlatkaInstance cage = ACTIVE_CAGES.get(cageID);
                if (cage != null) {
                    cage.removePlayerFromTrappedSet(player);
                }
            }
            TRAPPED_PLAYERS_COUNT.remove(uuid);
            TRAPPED_PLAYERS_CAGES.remove(uuid);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        TRAPPED_PLAYERS_COUNT.remove(uuid);
        Set<UUID> cages = TRAPPED_PLAYERS_CAGES.remove(uuid);
        if (cages != null) {
            for (UUID cageId : cages) {
                HydroKlatkaInstance cage = ACTIVE_CAGES.get(cageId);
                if (cage != null) {
                    cage.removePlayerVisualOnly(player);
                }
            }
        }
    }
}
