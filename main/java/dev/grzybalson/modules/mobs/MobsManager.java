package dev.grzybalson.modules.mobs;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import dev.grzybalson.HollyCore;
import dev.grzybalson.config.HollyDataPaths;
import dev.grzybalson.utils.ColorUtils;
import io.papermc.paper.event.entity.EntityMoveEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class MobsManager implements Listener {

    private static final String RESOURCE_PATH = "addons/mobs/mobs.yml";

    private final HollyCore plugin;
    private final File configFile;
    private final NamespacedKey spawnIdKey;
    private final NamespacedKey mobTypeKey;
    private final Map<String, MobDefinition> definitions = new HashMap<>();
    private final List<String> randomMobTypes = new ArrayList<>();
    private final Map<String, MobSpawn> spawns = new HashMap<>();
    private final Map<UUID, String> activeMobToSpawn = new HashMap<>();
    private final Map<UUID, UUID> lastHitKillers = new HashMap<>();
    private final Map<UUID, Location> lastAllowedLocations = new HashMap<>();
    private BukkitTask spawnTask;
    private YamlConfiguration config;
    private boolean enabled;
    private int maxMobsPerSpawn;
    private long checkIntervalTicks;
    private int randomMobTotalWeight;
    private boolean blockPvpDenyRegions;
    private String rewardPackage;
    private int rewardAmount;
    private double deleteSearchRadius;

    public MobsManager(HollyCore plugin) {
        this.plugin = plugin;
        this.configFile = new File(HollyDataPaths.getAddonFolder(plugin, "mobs"), "mobs.yml");
        this.spawnIdKey = new NamespacedKey(plugin, "mobs_spawn_id");
        this.mobTypeKey = new NamespacedKey(plugin, "mobs_type");
        reload();
    }

    public void start() {
        stopTask();
        if (!enabled) {
            return;
        }
        this.spawnTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickSpawns, 20L, checkIntervalTicks);
    }

    public void stop() {
        stopTask();
        removeManagedMobsInLoadedWorlds();
        activeMobToSpawn.clear();
        lastHitKillers.clear();
        lastAllowedLocations.clear();
        spawns.values().forEach(MobSpawn::clearActiveMobs);
    }

    public void reload() {
        stopTask();
        removeManagedMobsInLoadedWorlds();
        activeMobToSpawn.clear();
        lastHitKillers.clear();
        lastAllowedLocations.clear();
        spawns.clear();
        definitions.clear();
        randomMobTypes.clear();
        randomMobTotalWeight = 0;

        HollyDataPaths.ensureResource(plugin, RESOURCE_PATH);
        this.config = YamlConfiguration.loadConfiguration(configFile);
        ensureDefaults();
        loadSettings();
        loadDefinitions();
        loadSpawns();
        start();
    }

    public MobSpawn addSpawn(Location location, long respawnSeconds) {
        if (location.getWorld() == null || randomMobTypes.isEmpty()) {
            return null;
        }

        String id = createSpawnId(location);
        MobSpawn spawn = new MobSpawn(
            id,
            "random",
            location.getWorld().getName(),
            location.getX(),
            location.getY(),
            location.getZ(),
            location.getYaw(),
            location.getPitch(),
            Math.max(1L, respawnSeconds)
        );

        spawns.put(id, spawn);
        saveSpawn(spawn);
        return spawn;
    }

    public MobSpawn addSpawn(String ignoredType, Location location, long respawnSeconds) {
        return addSpawn(location, respawnSeconds);
    }

    public boolean deleteSpawn(String id) {
        MobSpawn spawn = spawns.remove(id);
        if (spawn == null) {
            return false;
        }

        removeSpawnMobs(spawn);
        config.set("spawns." + id, null);
        saveConfig();
        return true;
    }

    public MobSpawn deleteNearest(Location location) {
        MobSpawn nearest = findNearest(location);
        if (nearest == null) {
            return null;
        }
        return deleteSpawn(nearest.id()) ? nearest : null;
    }

    public MobSpawn findNearest(Location location) {
        if (location.getWorld() == null) {
            return null;
        }

        double maxDistanceSquared = deleteSearchRadius * deleteSearchRadius;
        return spawns.values().stream()
            .filter(spawn -> spawn.worldName().equals(location.getWorld().getName()))
            .map(spawn -> new SpawnDistance(spawn, spawn.distanceSquared(location)))
            .filter(distance -> distance.distanceSquared() <= maxDistanceSquared)
            .min(Comparator.comparingDouble(SpawnDistance::distanceSquared))
            .map(SpawnDistance::spawn)
            .orElse(null);
    }

    public List<MobSpawn> getSpawns() {
        return new ArrayList<>(spawns.values());
    }

    public Set<String> getMobTypes() {
        return new HashSet<>(definitions.keySet());
    }

    public String normalizeType(String type) {
        if (type == null) {
            return null;
        }

        String normalized = type.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "zombie", "zombi" -> "zombie";
            case "skeleton", "szkielet" -> "skeleton";
            case "spider", "pajak", "paj\u0105k" -> "spider";
            default -> null;
        };
    }

    public String formatSpawn(MobSpawn spawn) {
        return spawn.id() + " &8- &7random &8(&f"
            + spawn.worldName() + " "
            + Math.round(spawn.x()) + " "
            + Math.round(spawn.y()) + " "
            + Math.round(spawn.z()) + "&8)";
    }

    private void tickSpawns() {
        if (!enabled) {
            return;
        }

        long now = System.currentTimeMillis();
        for (MobSpawn spawn : spawns.values()) {
            Location location = spawn.toLocation();
            if (location == null || location.getWorld() == null) {
                continue;
            }

            World world = location.getWorld();
            int chunkX = location.getBlockX() >> 4;
            int chunkZ = location.getBlockZ() >> 4;
            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                continue;
            }

            pruneSpawn(spawn);
            enforceAllowedRegions(spawn, location);
            if (isPvpDenyRegion(location)) {
                continue;
            }

            spawn.queueInitialRespawns(maxMobsPerSpawn, now);

            while (spawn.activeMobCount() < maxMobsPerSpawn && spawn.hasDueRespawn(now)) {
                spawn.pollDueRespawn();
                spawnMob(spawn, location);
            }
        }
    }

    private void spawnMob(MobSpawn spawn, Location location) {
        if (isPvpDenyRegion(location)) {
            return;
        }

        String mobType = randomMobType();
        if (mobType == null) {
            return;
        }

        MobDefinition definition = definitions.get(mobType);
        if (definition == null) {
            return;
        }

        Entity entity = location.getWorld().spawnEntity(location, definition.entityType());
        if (!(entity instanceof LivingEntity livingEntity)) {
            entity.remove();
            return;
        }

        livingEntity.setCustomName(ColorUtils.colorize(definition.displayName()));
        livingEntity.setCustomNameVisible(true);
        livingEntity.setRemoveWhenFarAway(false);
        livingEntity.setPersistent(true);
        applyHitHealth(livingEntity, definition);
        livingEntity.getPersistentDataContainer().set(spawnIdKey, PersistentDataType.STRING, spawn.id());
        livingEntity.getPersistentDataContainer().set(mobTypeKey, PersistentDataType.STRING, mobType);

        spawn.addActiveMob(livingEntity.getUniqueId());
        activeMobToSpawn.put(livingEntity.getUniqueId(), spawn.id());
        lastAllowedLocations.put(livingEntity.getUniqueId(), location.clone());
    }

    private void applyHitHealth(LivingEntity entity, MobDefinition definition) {
        double health = Math.max(1.0D, definition.hitsToKill());
        AttributeInstance maxHealth = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(health);
            entity.setHealth(Math.min(health, maxHealth.getValue()));
            return;
        }
        entity.setHealth(Math.min(health, entity.getHealth()));
    }

    private void pruneSpawn(MobSpawn spawn) {
        Set<UUID> stale = new HashSet<>();
        for (UUID mobId : spawn.activeMobs()) {
            Entity entity = Bukkit.getEntity(mobId);
            if (!(entity instanceof LivingEntity livingEntity) || livingEntity.isDead() || !livingEntity.isValid()) {
                stale.add(mobId);
            }
        }

        for (UUID mobId : stale) {
            spawn.removeActiveMob(mobId);
            activeMobToSpawn.remove(mobId);
            lastHitKillers.remove(mobId);
            lastAllowedLocations.remove(mobId);
            spawn.scheduleRespawn(System.currentTimeMillis());
        }
    }

    private void enforceAllowedRegions(MobSpawn spawn, Location spawnLocation) {
        if (!blockPvpDenyRegions) {
            return;
        }

        long now = System.currentTimeMillis();
        Set<UUID> removed = new HashSet<>();
        for (UUID mobId : spawn.activeMobs()) {
            Entity entity = Bukkit.getEntity(mobId);
            if (!(entity instanceof LivingEntity livingEntity) || livingEntity.isDead() || !livingEntity.isValid()) {
                continue;
            }

            Location current = livingEntity.getLocation();
            if (!isPvpDenyRegion(current)) {
                lastAllowedLocations.put(mobId, current.clone());
                continue;
            }

            Location fallback = lastAllowedLocations.get(mobId);
            if (fallback == null || fallback.getWorld() == null || isPvpDenyRegion(fallback)) {
                fallback = spawnLocation;
            }

            if (fallback == null || fallback.getWorld() == null || isPvpDenyRegion(fallback)) {
                livingEntity.remove();
                removed.add(mobId);
                continue;
            }

            livingEntity.teleport(fallback);
        }

        for (UUID mobId : removed) {
            spawn.removeActiveMob(mobId);
            activeMobToSpawn.remove(mobId);
            lastHitKillers.remove(mobId);
            lastAllowedLocations.remove(mobId);
            spawn.scheduleRespawn(now);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onManagedMobMove(EntityMoveEvent event) {
        if (!blockPvpDenyRegions || !event.hasChangedBlock() || !isManagedMob(event.getEntity())) {
            return;
        }

        UUID mobId = event.getEntity().getUniqueId();
        Location to = event.getTo();
        if (!isPvpDenyRegion(to)) {
            lastAllowedLocations.put(mobId, to.clone());
            return;
        }

        event.setCancelled(true);
        Location fallback = lastAllowedLocations.get(mobId);
        if (fallback != null && fallback.getWorld() != null && !isPvpDenyRegion(fallback)) {
            event.getEntity().teleport(fallback);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onManagedMobDamage(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent) {
            return;
        }
        if (isManagedMob(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onManagedMobHit(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player && isDamageFromManagedMob(event.getDamager())
            && (isPvpDenyRegion(event.getEntity().getLocation()) || isPvpDenyRegion(event.getDamager().getLocation()))) {
            event.setCancelled(true);
            if (event.getDamager() instanceof Projectile projectile) {
                projectile.remove();
            }
            return;
        }

        if (!(event.getEntity() instanceof LivingEntity entity) || !isManagedMob(entity)) {
            return;
        }

        Player attacker = findAttackingPlayer(event.getDamager());
        if (attacker == null) {
            event.setCancelled(true);
            return;
        }

        lastHitKillers.put(entity.getUniqueId(), attacker.getUniqueId());
        event.setDamage(1.0D);
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        String spawnId = entity.getPersistentDataContainer().get(spawnIdKey, PersistentDataType.STRING);
        String type = entity.getPersistentDataContainer().get(mobTypeKey, PersistentDataType.STRING);
        if (spawnId == null || type == null) {
            return;
        }

        event.getDrops().clear();
        event.setDroppedExp(0);

        MobSpawn spawn = spawns.get(spawnId);
        if (spawn != null) {
            spawn.removeActiveMob(entity.getUniqueId());
            spawn.scheduleRespawn(System.currentTimeMillis());
        }
        activeMobToSpawn.remove(entity.getUniqueId());
        lastAllowedLocations.remove(entity.getUniqueId());

        Player killer = entity.getKiller();
        UUID fallbackKillerId = lastHitKillers.remove(entity.getUniqueId());
        if (killer == null && fallbackKillerId != null) {
            killer = Bukkit.getPlayer(fallbackKillerId);
        }
        if (killer == null) {
            return;
        }

        MobDefinition definition = definitions.get(type);
        if (definition == null || ThreadLocalRandom.current().nextDouble(100.0D) >= definition.dropChancePercent()) {
            return;
        }

        giveReward(killer, entity.getLocation());
    }

    private void giveReward(Player player, Location dropLocation) {
        if (plugin.getPickupManager() == null) {
            plugin.getLogger().warning("Nie mozna zrzucic odlamka z mobow: PickupManager jest wylaczony.");
            return;
        }
        if (!plugin.getPickupManager().packageExists(rewardPackage)) {
            plugin.getLogger().warning("Nie mozna zrzucic odlamka z mobow: przedmiot /odbierz '" + rewardPackage + "' nie istnieje.");
            return;
        }

        ItemStack rewardItem = plugin.getPickupManager().getPackage(rewardPackage);
        if (rewardItem == null || rewardItem.getType().isAir()) {
            plugin.getLogger().warning("Nie mozna zrzucic odlamka z mobow: przedmiot /odbierz '" + rewardPackage + "' jest pusty.");
            return;
        }

        int remaining = Math.max(1, rewardAmount);
        int maxStackSize = Math.max(1, rewardItem.getMaxStackSize());
        while (remaining > 0) {
            ItemStack drop = rewardItem.clone();
            int amount = Math.min(maxStackSize, remaining);
            drop.setAmount(amount);
            if (dropLocation != null && dropLocation.getWorld() != null) {
                dropLocation.getWorld().dropItemNaturally(dropLocation, drop);
            } else {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
            remaining -= amount;
        }
    }

    private void removeManagedMobsInLoadedWorlds() {
        if (spawnIdKey == null) {
            return;
        }

        for (World world : Bukkit.getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                if (entity.getPersistentDataContainer().has(spawnIdKey, PersistentDataType.STRING)) {
                    entity.remove();
                }
            }
        }
    }

    private boolean isManagedMob(Entity entity) {
        return entity instanceof LivingEntity livingEntity
            && livingEntity.getPersistentDataContainer().has(spawnIdKey, PersistentDataType.STRING)
            && livingEntity.getPersistentDataContainer().has(mobTypeKey, PersistentDataType.STRING);
    }

    private Player findAttackingPlayer(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    private boolean isDamageFromManagedMob(Entity damager) {
        if (isManagedMob(damager)) {
            return true;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            return shooter instanceof Entity shooterEntity && isManagedMob(shooterEntity);
        }
        return false;
    }

    private boolean isPvpDenyRegion(Location location) {
        if (!blockPvpDenyRegions || location == null || location.getWorld() == null
            || !Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) {
            return false;
        }

        try {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionQuery query = container.createQuery();
            ApplicableRegionSet regions = query.getApplicableRegions(BukkitAdapter.adapt(location));
            StateFlag.State pvpState = regions.queryState(null, Flags.PVP);
            return pvpState == StateFlag.State.DENY;
        } catch (RuntimeException | LinkageError exception) {
            return false;
        }
    }

    private void removeSpawnMobs(MobSpawn spawn) {
        for (UUID mobId : new HashSet<>(spawn.activeMobs())) {
            Entity entity = Bukkit.getEntity(mobId);
            if (entity != null) {
                entity.remove();
            }
            activeMobToSpawn.remove(mobId);
            lastHitKillers.remove(mobId);
            lastAllowedLocations.remove(mobId);
        }
        spawn.clearActiveMobs();
    }

    private void ensureDefaults() {
        boolean changed = false;
        changed |= setIfMissing("settings.enabled", true);
        changed |= setIfMissing("settings.max-mobs-per-spawn", 2);
        changed |= setIfMissing("settings.check-interval-ticks", 20L);
        changed |= setIfMissing("settings.delete-search-radius", 5.0D);
        changed |= setIfMissing("settings.block-pvp-deny-regions", true);
        changed |= setIfMissing("reward.package", "shard");
        changed |= setIfMissing("reward.amount", 1);
        changed |= setIfMissing("mobs.zombie.entity-type", "ZOMBIE");
        changed |= setIfMissing("mobs.zombie.display-name", "&fPodw\u0142adny &7poziomu &x&F&F&A&8&0&02\u2605");
        changed |= setIfMissing("mobs.zombie.drop-chance-percent", 10.0D);
        changed |= setIfMissing("mobs.zombie.hits-to-kill", 10);
        changed |= setIfMissing("mobs.zombie.spawn-weight", 20);
        changed |= setIfMissing("mobs.skeleton.entity-type", "SKELETON");
        changed |= setIfMissing("mobs.skeleton.display-name", "&fPodw\u0142adny &7poziomu &x&F&F&F&0&0&01&x&F&F&F&0&0&0\u2605");
        changed |= setIfMissing("mobs.skeleton.drop-chance-percent", 5.0D);
        changed |= setIfMissing("mobs.skeleton.hits-to-kill", 6);
        changed |= setIfMissing("mobs.skeleton.spawn-weight", 70);
        changed |= setIfMissing("mobs.spider.entity-type", "SPIDER");
        changed |= setIfMissing("mobs.spider.display-name", "&fPodw\u0142adny &7poziomu &x&F&F&0&0&0&03\u2605");
        changed |= setIfMissing("mobs.spider.drop-chance-percent", 15.0D);
        changed |= setIfMissing("mobs.spider.hits-to-kill", 15);
        changed |= setIfMissing("mobs.spider.spawn-weight", 10);
        if (!config.isConfigurationSection("spawns")) {
            config.createSection("spawns");
            changed = true;
        }

        if (changed) {
            saveConfig();
        }
    }

    private boolean setIfMissing(String path, Object value) {
        if (config.contains(path)) {
            return false;
        }
        config.set(path, value);
        return true;
    }

    private void loadSettings() {
        this.enabled = config.getBoolean("settings.enabled", true);
        this.maxMobsPerSpawn = Math.max(1, config.getInt("settings.max-mobs-per-spawn", 2));
        this.checkIntervalTicks = Math.max(20L, config.getLong("settings.check-interval-ticks", 20L));
        this.deleteSearchRadius = Math.max(1.0D, config.getDouble("settings.delete-search-radius", 5.0D));
        this.blockPvpDenyRegions = config.getBoolean("settings.block-pvp-deny-regions", true);
        this.rewardPackage = config.getString("reward.package", "shard");
        this.rewardAmount = Math.max(1, config.getInt("reward.amount", 1));
    }

    private void loadDefinitions() {
        ConfigurationSection section = config.getConfigurationSection("mobs");
        if (section == null) {
            return;
        }

        for (String key : section.getKeys(false)) {
            EntityType entityType;
            try {
                entityType = EntityType.valueOf(section.getString(key + ".entity-type", "").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Nieprawidlowy typ moba w addons/mobs/mobs.yml: " + key);
                continue;
            }

            if (!entityType.isAlive() || !entityType.isSpawnable()) {
                plugin.getLogger().warning("Typ moba nie jest spawnable w addons/mobs/mobs.yml: " + key);
                continue;
            }

            String normalizedKey = normalizeType(key);
            if (normalizedKey == null) {
                normalizedKey = key.toLowerCase(Locale.ROOT);
            }

            MobDefinition definition = new MobDefinition(
                entityType,
                section.getString(key + ".display-name", key),
                Math.max(0.0D, section.getDouble(key + ".drop-chance-percent", 0.0D)),
                Math.max(1, section.getInt(key + ".hits-to-kill", 1)),
                Math.max(0, section.getInt(key + ".spawn-weight", 1))
            );
            definitions.put(normalizedKey, definition);
            if (definition.spawnWeight() > 0) {
                randomMobTypes.add(normalizedKey);
                randomMobTotalWeight += definition.spawnWeight();
            }
        }
    }

    private void loadSpawns() {
        ConfigurationSection section = config.getConfigurationSection("spawns");
        if (section == null) {
            return;
        }

        for (String id : section.getKeys(false)) {
            MobSpawn spawn = new MobSpawn(
                id,
                "random",
                section.getString(id + ".world", ""),
                section.getDouble(id + ".x"),
                section.getDouble(id + ".y"),
                section.getDouble(id + ".z"),
                (float) section.getDouble(id + ".yaw"),
                (float) section.getDouble(id + ".pitch"),
                Math.max(1L, section.getLong(id + ".respawn-seconds", 60L))
            );
            spawns.put(id, spawn);
        }
    }

    private void saveSpawn(MobSpawn spawn) {
        String path = "spawns." + spawn.id();
        config.set(path + ".type", "random");
        config.set(path + ".world", spawn.worldName());
        config.set(path + ".x", spawn.x());
        config.set(path + ".y", spawn.y());
        config.set(path + ".z", spawn.z());
        config.set(path + ".yaw", spawn.yaw());
        config.set(path + ".pitch", spawn.pitch());
        config.set(path + ".respawn-seconds", spawn.respawnSeconds());
        saveConfig();
    }

    private void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException exception) {
            plugin.getLogger().warning("Nie udalo sie zapisac addons/mobs/mobs.yml: " + exception.getMessage());
        }
    }

    private String createSpawnId(Location location) {
        String base = "random_" + location.getWorld().getName()
            + "_" + location.getBlockX()
            + "_" + location.getBlockY()
            + "_" + location.getBlockZ();
        String id = base.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "_");
        int index = 2;
        while (spawns.containsKey(id)) {
            id = base + "_" + index++;
            id = id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "_");
        }
        return id;
    }

    private String randomMobType() {
        if (randomMobTypes.isEmpty()) {
            return null;
        }

        if (randomMobTotalWeight <= 0) {
            return null;
        }

        int roll = ThreadLocalRandom.current().nextInt(randomMobTotalWeight);
        for (String mobType : randomMobTypes) {
            MobDefinition definition = definitions.get(mobType);
            if (definition == null) {
                continue;
            }
            roll -= definition.spawnWeight();
            if (roll < 0) {
                return mobType;
            }
        }
        return randomMobTypes.get(ThreadLocalRandom.current().nextInt(randomMobTypes.size()));
    }

    private void stopTask() {
        if (spawnTask != null) {
            spawnTask.cancel();
            spawnTask = null;
        }
    }

    public record MobDefinition(EntityType entityType, String displayName, double dropChancePercent, int hitsToKill, int spawnWeight) {}

    public static final class MobSpawn {
        private final String id;
        private final String type;
        private final String worldName;
        private final double x;
        private final double y;
        private final double z;
        private final float yaw;
        private final float pitch;
        private final long respawnSeconds;
        private final Set<UUID> activeMobs = new HashSet<>();
        private final PriorityQueue<Long> pendingRespawns = new PriorityQueue<>();
        private boolean initialRespawnsQueued;

        private MobSpawn(String id, String type, String worldName, double x, double y, double z, float yaw, float pitch, long respawnSeconds) {
            this.id = id;
            this.type = type;
            this.worldName = worldName;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
            this.respawnSeconds = respawnSeconds;
        }

        public String id() { return id; }
        public String type() { return type; }
        public String worldName() { return worldName; }
        public double x() { return x; }
        public double y() { return y; }
        public double z() { return z; }
        public float yaw() { return yaw; }
        public float pitch() { return pitch; }
        public long respawnSeconds() { return respawnSeconds; }
        public int activeMobCount() { return activeMobs.size(); }
        public Set<UUID> activeMobs() { return activeMobs; }
        private void addActiveMob(UUID uuid) { activeMobs.add(uuid); }
        private void removeActiveMob(UUID uuid) { activeMobs.remove(uuid); }
        private void clearActiveMobs() {
            activeMobs.clear();
            pendingRespawns.clear();
            initialRespawnsQueued = false;
        }
        private void queueInitialRespawns(int limit, long nowMillis) {
            if (initialRespawnsQueued) {
                return;
            }
            initialRespawnsQueued = true;
            for (int i = 0; i < limit; i++) {
                pendingRespawns.add(nowMillis);
            }
        }
        private boolean hasDueRespawn(long nowMillis) {
            Long due = pendingRespawns.peek();
            return due != null && due <= nowMillis;
        }
        private void pollDueRespawn() {
            pendingRespawns.poll();
        }
        private void scheduleRespawn(long nowMillis) {
            pendingRespawns.add(nowMillis + respawnSeconds * 1000L);
        }

        public Location toLocation() {
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                return null;
            }
            return new Location(world, x, y, z, yaw, pitch);
        }

        private double distanceSquared(Location location) {
            double dx = x - location.getX();
            double dy = y - location.getY();
            double dz = z - location.getZ();
            return dx * dx + dy * dy + dz * dz;
        }
    }

    private record SpawnDistance(MobSpawn spawn, double distanceSquared) {}
}
