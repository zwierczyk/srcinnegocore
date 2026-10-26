package dev.grzybalson.modules.generator;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.HollyDataPaths;
import dev.grzybalson.modules.upgrade.UpgradeManager;
import dev.grzybalson.utils.ColorUtils;
import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

public class GeneratorManager implements Listener {

    private final JavaPlugin plugin;
    private final Logger logger;
    private final GeneratorConfig config;
    private final boolean decentHolograms;
    private final BlockRegenerateManager honeyRegenerate;
    private final BlockRegenerateManager cyanConcretePowderRegenerate;
    private final Map<String, Generator> generators = new LinkedHashMap<>();
    private final Map<String, Long> nextRegenerateAt = new ConcurrentHashMap<>();
    private final Set<String> regenerationInProgress = ConcurrentHashMap.newKeySet();
    private final Map<String, Location> locationCache = new ConcurrentHashMap<>();
    private final Map<String, List<IndexedGenerator>> generatorsByChunk = new ConcurrentHashMap<>();
    private final Map<String, String> lastHologramSignature = new ConcurrentHashMap<>();
    private final List<BukkitTask> tasks = new ArrayList<>();

    private static final class IndexedGenerator {
        private final Generator generator;
        private final World world;
        private final int blockX;
        private final int blockY;
        private final int blockZ;
        private final int radius;
        private final int height;

        private IndexedGenerator(Generator generator, Location center) {
            this.generator = generator;
            this.world = center.getWorld();
            this.blockX = center.getBlockX();
            this.blockY = center.getBlockY();
            this.blockZ = center.getBlockZ();
            this.radius = Math.max(0, generator.getRadius());
            this.height = Math.max(0, generator.getHeight());
        }

        private boolean contains(Location location) {
            if (world == null || location.getWorld() == null) {
                return false;
            }
            if (!isSameWorld(world, location.getWorld())) {
                return false;
            }
            int dy = location.getBlockY() - blockY;
            if (dy < 0 || dy >= height) {
                return false;
            }
            int dx = location.getBlockX() - blockX;
            if (dx < -radius || dx > radius) {
                return false;
            }
            int dz = location.getBlockZ() - blockZ;
            return dz >= -radius && dz <= radius;
        }
    }

    public GeneratorManager(HollyCore plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.decentHolograms = Bukkit.getPluginManager().getPlugin("DecentHolograms") != null;
        if (!this.decentHolograms) {
            this.logger.warning("[Generators] DecentHolograms is required for Generator holograms. Some features might not work.");
        }

        HollyDataPaths.ensureResource(plugin, "addons/generator/generator.yml");
        this.config = new GeneratorConfig(new File(plugin.getDataFolder(), "addons/generator/generator.yml"), this.logger);
        this.config.load();
        this.ensureDefaults();
        this.load();

        HollyDataPaths.ensureResource(plugin, "addons/generator/honey-regenerate.yml");
        GeneratorConfig honeyConfig = new GeneratorConfig(new File(plugin.getDataFolder(), "addons/generator/honey-regenerate.yml"), this.logger);
        honeyConfig.load();
        honeyConfig.setDefault("locations", new ArrayList<String>());
        honeyConfig.setDefault("hologram.name", "HONEY_BLOCK");
        honeyConfig.setDefault("hologram.location", "");
        honeyConfig.save();
        this.honeyRegenerate = new BlockRegenerateManager(this, plugin, honeyConfig, Material.HONEY_BLOCK, "honey_block");
        this.honeyRegenerate.start();

        HollyDataPaths.ensureResource(plugin, "addons/generator/cyan-concrete-powder-regenerate.yml");
        GeneratorConfig cyanConfig = new GeneratorConfig(new File(plugin.getDataFolder(), "addons/generator/cyan-concrete-powder-regenerate.yml"), this.logger);
        cyanConfig.load();
        cyanConfig.setDefault("locations", new ArrayList<String>());
        cyanConfig.setDefault("hologram.name", "CYAN_CONCRETE_POWDER");
        cyanConfig.setDefault("hologram.location", "");
        cyanConfig.save();
        this.cyanConcretePowderRegenerate = new BlockRegenerateManager(this, plugin, cyanConfig, Material.CYAN_CONCRETE_POWDER, "cyan_concrete_powder");
        this.cyanConcretePowderRegenerate.start();

        Bukkit.getPluginManager().registerEvents(this, plugin);
        // OPTYMALIZACJA + FIX: Fortune na łopatach teraz działa w generatorach
        Bukkit.getPluginManager().registerEvents(new GeneratorFortuneListener(this), plugin);
        this.start();
    }

    public BlockRegenerateManager getHoneyRegenerate() {
        return this.honeyRegenerate;
    }

    public BlockRegenerateManager getCyanConcretePowderRegenerate() {
        return this.cyanConcretePowderRegenerate;
    }

    public boolean isDecentHologramsAvailable() {
        return this.decentHolograms;
    }

    public GeneratorConfig getGeneratorConfig() {
        return this.config;
    }

    public List<String> getHologramLineTemplates() {
        return this.getHologramLineTemplatesInternal();
    }

    public void load() {
        this.config.load();
        this.ensureDefaults();
        this.generators.clear();
        this.locationCache.clear();
        this.generatorsByChunk.clear();
        this.lastHologramSignature.clear();

        Map<String, Object> section = this.config.getSection("generators");
        for (String id : section.keySet()) {
            Generator generator = this.deserializeGenerator(id);
            if (generator != null) {
                this.generators.put(id.toLowerCase(Locale.ROOT), generator);
            }
        }
        this.logger.info("[Generators] Wczytano " + this.generators.size() + " generatorow.");
        this.rebuildSpatialIndex();
    }

    public void reload() {
        this.shutdown();
        this.load();
        this.start();
        if (this.honeyRegenerate != null) {
            this.honeyRegenerate.reload();
            this.honeyRegenerate.start();
        }
        if (this.cyanConcretePowderRegenerate != null) {
            this.cyanConcretePowderRegenerate.reload();
            this.cyanConcretePowderRegenerate.start();
        }
        Bukkit.getScheduler().runTaskLater(this.plugin, () -> this.resetAllGenerators(), 20L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent event) {
        String loadedWorld = event.getWorld().getName().toLowerCase(Locale.ROOT);
        boolean hasGeneratorsOnWorld = false;
        for (Generator generator : this.generators.values()) {
            String genWorld = generator.getWorldName();
            if (genWorld == null) {
                String center = generator.getCenter();
                if (center != null && center.toLowerCase(Locale.ROOT).startsWith(loadedWorld + ";")) {
                    hasGeneratorsOnWorld = true;
                    break;
                }
            } else if (genWorld.equalsIgnoreCase(loadedWorld)) {
                hasGeneratorsOnWorld = true;
                break;
            }
        }

        if (!hasGeneratorsOnWorld) {
            int before = this.generators.size();
            this.load();
            int after = this.generators.size();
            if (after > before) {
                hasGeneratorsOnWorld = true;
                this.logger.info("[Generators] Swiat " + event.getWorld().getName() + " zaladowany — wczytano " + (after - before) + " nowych generatorow.");
                this.restartRuntime();
            }
        }

        if (hasGeneratorsOnWorld) {
            this.logger.info("[Generators] Swiat " + event.getWorld().getName() + " zaladowany — resetuje generatory na tym swiecie.");
            Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
                for (Map.Entry<String, Generator> entry : this.generators.entrySet()) {
                    Generator gen = entry.getValue();
                    String gw = gen.getWorldName();
                    boolean match = false;
                    if (gw != null) {
                        match = gw.equalsIgnoreCase(loadedWorld);
                    } else if (gen.getCenter() != null) {
                        match = gen.getCenter().toLowerCase(Locale.ROOT).startsWith(loadedWorld + ";");
                    }
                    if (match) {
                        this.resetGenerator(entry.getKey());
                    }
                }
            }, 40L);
        }
    }

    public void start() {
        this.cancelTasks();
        this.regenerationInProgress.clear();
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Generator> entry : this.generators.entrySet()) {
            this.nextRegenerateAt.put(entry.getKey(), now + entry.getValue().getTime());
        }
        BukkitTask masterTask = Bukkit.getScheduler().runTaskTimer(this.plugin, () -> {
            for (String id : this.generators.keySet()) {
                this.tickGenerator(id);
            }
        }, 20L, 20L);
        this.tasks.add(masterTask);

        if (this.config.getBoolean("continuous-regeneration.enabled", false)) {
            long interval = Math.max(20L, this.config.getInt("continuous-regeneration.check-interval-ticks", 20));
            BukkitTask continuousTask = Bukkit.getScheduler().runTaskTimer(this.plugin, this::continuousRegenerateAll, interval, interval);
            this.tasks.add(continuousTask);
        }

        if (this.config.getBoolean("hologram.enabled", true)) {
            long holoInterval = Math.max(40L, this.config.getInt("hologram.update-interval-ticks", 40));
            BukkitTask hologramTask = Bukkit.getScheduler().runTaskTimer(this.plugin, this::updateHolograms, holoInterval, holoInterval);
            this.tasks.add(hologramTask);
            this.updateHolograms();
        }
        this.syncZoneBoxAllowedBlocks();
    }

    public void shutdown() {
        this.cancelTasks();
        this.regenerationInProgress.clear();
        if (this.decentHolograms) {
            for (String id : new ArrayList<>(this.generators.keySet())) {
                DHAPI.removeHologram(this.runtimeHologramId(id));
            }
        }
        if (this.honeyRegenerate != null) {
            this.honeyRegenerate.shutdown();
        }
        if (this.cyanConcretePowderRegenerate != null) {
            this.cyanConcretePowderRegenerate.shutdown();
        }
    }

    public Collection<Generator> getGenerators() {
        return this.generators.values();
    }

    public List<String> getGeneratorIds() {
        return new ArrayList<>(this.generators.keySet());
    }

    public Generator getGenerator(String id) {
        if (id == null) {
            return null;
        }
        return this.generators.get(id.toLowerCase(Locale.ROOT));
    }

    public boolean exists(String id) {
        return this.getGenerator(id) != null;
    }

    public boolean create(String id, Location center, int radius, int height, Material material, long timeMillis, String permission) {
        String key = id.toLowerCase(Locale.ROOT);
        if (this.generators.containsKey(key)) {
            return false;
        }

        Location middle = getMiddleOfBlock(center);
        World world = middle.getWorld();
        if (world == null) {
            return false;
        }

        double heightOffset = this.config.getDouble("hologram.height-offset", 1.45D);
        Generator generator = new Generator(
            this.serializeLocation(middle),
            new GeneratorHologram(id, this.serializeLocation(middle.clone().add(0.0D, heightOffset, 0.0D))),
            radius,
            height,
            timeMillis,
            material,
            permission == null ? "" : permission,
            List.of(),
            world.getName(),
            true
        );

        this.generators.put(key, generator);
        this.save();
        this.restartRuntime();
        return true;
    }

    public boolean delete(String id) {
        String key = id.toLowerCase(Locale.ROOT);
        Generator removed = this.generators.remove(key);
        if (removed == null) {
            return false;
        }
        this.clearGeneratorBlocks(removed);
        if (this.decentHolograms) {
            DHAPI.removeHologram(this.runtimeHologramId(key));
        }
        this.save();
        this.restartRuntime();
        return true;
    }

    private void clearGeneratorBlocks(Generator generator) {
        Location center = this.deserializeLocation(generator.getCenter());
        if (center == null || center.getWorld() == null) {
            return;
        }
        Set<Material> knownMaterials = new HashSet<>();
        knownMaterials.add(generator.getDefaultBlock());
        for (GeneratorChance chance : generator.getBlocks()) {
            knownMaterials.add(chance.getMaterial());
        }
        for (Location loc : this.getSquare(center, generator.getRadius(), generator.getHeight())) {
            World world = loc.getWorld();
            if (world == null) {
                continue;
            }
            if (!world.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
                world.loadChunk(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
            }
            Block block = loc.getBlock();
            if (knownMaterials.contains(block.getType())) {
                block.setType(Material.AIR, false);
            }
        }
    }

    public boolean cloneGenerator(String sourceId, String newId, Location newCenter) {
        Generator source = this.getGenerator(sourceId);
        if (source == null || this.exists(newId)) {
            return false;
        }

        Generator copy = source.copy();
        Location middle = getMiddleOfBlock(newCenter);
        World world = middle.getWorld();
        if (world == null) {
            return false;
        }
        double heightOffset = this.config.getDouble("hologram.height-offset", 1.45D);
        copy.setCenter(this.serializeLocation(middle));
        copy.setWorldName(world.getName());
        copy.getHologram().setLocation(this.serializeLocation(middle.clone().add(0.0D, heightOffset, 0.0D)));
        this.generators.put(newId.toLowerCase(Locale.ROOT), copy);
        this.save();
        this.restartRuntime();
        return true;
    }

    public boolean clearBlocks(String id) {
        Generator generator = this.getGenerator(id);
        if (generator == null) {
            return false;
        }
        generator.getBlocks().clear();
        this.save();
        this.restartRuntime();
        return true;
    }

    public boolean addBlock(String id, Material material, double chance) {
        Generator generator = this.getGenerator(id);
        if (generator == null) {
            return false;
        }
        generator.getBlocks().add(new GeneratorChance(chance, material));
        this.save();
        this.restartRuntime();
        return true;
    }

    public boolean move(String id, Location center) {
        Generator generator = this.getGenerator(id);
        if (generator == null) {
            return false;
        }
        Location middle = getMiddleOfBlock(center);
        World world = middle.getWorld();
        if (world == null) {
            return false;
        }
        double heightOffset = this.config.getDouble("hologram.height-offset", 1.45D);
        generator.setCenter(this.serializeLocation(middle));
        generator.setWorldName(world.getName());
        generator.getHologram().setLocation(this.serializeLocation(middle.clone().add(0.0D, heightOffset, 0.0D)));
        this.save();
        this.restartRuntime();
        return true;
    }

    public boolean renameHologram(String id, String name) {
        Generator generator = this.getGenerator(id);
        if (generator == null) {
            return false;
        }
        generator.getHologram().setName(name);
        this.save();
        this.updateHolograms();
        return true;
    }

    public boolean setHeight(String id, int height) {
        Generator generator = this.getGenerator(id);
        if (generator == null) {
            return false;
        }
        generator.setHeight(height);
        this.save();
        this.restartRuntime();
        return true;
    }

    public boolean setRadius(String id, int radius) {
        Generator generator = this.getGenerator(id);
        if (generator == null) {
            return false;
        }
        generator.setRadius(radius);
        this.save();
        this.restartRuntime();
        return true;
    }

    public boolean setTime(String id, long time) {
        Generator generator = this.getGenerator(id);
        if (generator == null) {
            return false;
        }
        generator.setTime(time);
        this.save();
        this.restartRuntime();
        return true;
    }

    public boolean setDefaultBlock(String id, Material material) {
        Generator generator = this.getGenerator(id);
        if (generator == null) {
            return false;
        }
        generator.setDefaultBlock(material);
        this.save();
        this.restartRuntime();
        return true;
    }

    public boolean setPermission(String id, String permission) {
        Generator generator = this.getGenerator(id);
        if (generator == null) {
            return false;
        }
        generator.setPermission(permission == null ? "" : permission);
        this.save();
        this.restartRuntime();
        return true;
    }

    public Location getTeleportLocation(String id) {
        Generator generator = this.getGenerator(id);
        return generator == null ? null : this.deserializeLocation(generator.getCenter());
    }

    public long parseDurationMillis(String input) {
        return parseDuration(input.toLowerCase(Locale.ROOT));
    }

    public String formatDuration(long millis) {
        long safe = Math.max(millis, 0L);
        if (safe <= 0L) {
            return "0s";
        }
        long totalSec = (safe + 999L) / 1000L;
        if (totalSec < 60L) {
            return totalSec + "s";
        }
        long minutes = totalSec / 60L;
        long seconds = totalSec % 60L;
        if (minutes < 60L) {
            if (seconds > 0L) {
                return minutes + "m " + seconds + "s";
            }
            return minutes + "m";
        }
        long hours = minutes / 60L;
        minutes = minutes % 60L;
        StringBuilder sb = new StringBuilder();
        if (hours > 0L) {
            sb.append(hours).append("h ");
        }
        if (minutes > 0L) {
            sb.append(minutes).append("m ");
        }
        if (seconds > 0L) {
            sb.append(seconds).append("s");
        }
        String result = sb.toString().trim();
        if (!result.isEmpty()) {
            return result;
        }
        return hours > 0L ? hours + "h" : minutes + "m";
    }

    public String getMessage(String key) {
        return this.config.getString("messages." + key, "");
    }

    public void save() {
        this.createBackup();
        this.config.set("generators", null);
        for (Map.Entry<String, Generator> entry : this.generators.entrySet()) {
            String key = entry.getKey();
            Generator generator = entry.getValue();
            if (generator.isLegacyFormat()) {
                this.saveLegacyGenerator(key, generator);
            } else {
                this.saveModernGenerator(key, generator);
            }
        }
        this.config.save();
        this.syncZoneBoxAllowedBlocks();
    }

    private void createBackup() {
        File configFile = this.config.getFile();
        if (configFile == null || !configFile.exists() || configFile.length() < 50L) {
            return;
        }
        try {
            File backupDir = new File(configFile.getParentFile(), "backups");
            backupDir.mkdirs();

            File[] existing = backupDir.listFiles((dir, name) -> name.startsWith("generator-") && name.endsWith(".yml"));
            if (existing != null && existing.length > 10) {
                java.util.Arrays.sort(existing, java.util.Comparator.comparingLong(File::lastModified));
                for (int i = 0; i < existing.length - 10; i++) {
                    existing[i].delete();
                }
            }

            String timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new java.util.Date());
            File backup = new File(backupDir, "generator-" + timestamp + ".yml");
            java.nio.file.Files.copy(configFile.toPath(), backup.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            this.logger.warning("[Generators] Nie udalo sie utworzyc backupu: " + e.getMessage());
        }
    }

    private void saveLegacyGenerator(String key, Generator generator) {
        Location center = this.deserializeLocation(generator.getCenter());
        if (center == null || center.getWorld() == null) {
            this.logger.warning("[Generators] Zapisuję generator " + key + " jako surowy (świat niedostępny), dane nie zostaną utracone.");
            this.saveRawFallback(key, generator);
            return;
        }

        int centerX = center.getBlockX();
        int centerY = center.getBlockY();
        int centerZ = center.getBlockZ();
        int radius = generator.getRadius();
        int height = generator.getHeight();
        String worldName = generator.getWorldName() != null ? generator.getWorldName() : center.getWorld().getName();
        long renewSeconds = Math.max(1L, generator.getTime() / 1000L);

        String path = "generators." + key;
        this.config.set(path + ".world", worldName);
        this.config.set(path + ".radius", radius);
        this.config.set(path + ".height", height);
        this.config.set(path + ".center.x", centerX);
        this.config.set(path + ".center.y", centerY);
        this.config.set(path + ".center.z", centerZ);
        this.config.set(path + ".renew-seconds", renewSeconds);
        this.config.set(path + ".permission", generator.getPermission());
        this.config.set(path + ".primary-block", generator.getDefaultBlock().name());
        this.config.set(path + ".min.x", centerX - radius);
        this.config.set(path + ".min.y", centerY);
        this.config.set(path + ".min.z", centerZ - radius);
        this.config.set(path + ".max.x", centerX + radius);
        this.config.set(path + ".max.y", centerY + height - 1);
        this.config.set(path + ".max.z", centerZ + radius);

        List<Map<String, Object>> blocks = new ArrayList<>();
        for (GeneratorChance chance : generator.getBlocks()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("chance", chance.getChance());
            entry.put("material", chance.getMaterial().name());
            blocks.add(entry);
        }
        this.config.set(path + ".blocks", blocks);
    }

    private void saveModernGenerator(String key, Generator generator) {
        String path = "generators." + key;
        this.config.set(path + ".center", generator.getCenter());
        this.config.set(path + ".hologram.name", generator.getHologram().getName());
        this.config.set(path + ".hologram.location", generator.getHologram().getLocation());
        this.config.set(path + ".radius", generator.getRadius());
        this.config.set(path + ".height", generator.getHeight());
        this.config.set(path + ".time", generator.getTime());
        this.config.set(path + ".default-block", generator.getDefaultBlock().name());
        this.config.set(path + ".permission", generator.getPermission());

        List<Map<String, Object>> blocks = new ArrayList<>();
        for (GeneratorChance chance : generator.getBlocks()) {
            Map<String, Object> chanceMap = new LinkedHashMap<>();
            chanceMap.put("chance", chance.getChance());
            chanceMap.put("material", chance.getMaterial().name());
            blocks.add(chanceMap);
        }
        this.config.set(path + ".blocks", blocks);
    }

    private void saveRawFallback(String key, Generator generator) {
        String path = "generators." + key;
        this.config.set(path + ".center", generator.getCenter());
        GeneratorHologram hologram = generator.getHologram();
        if (hologram != null) {
            this.config.set(path + ".hologram.name", hologram.getName());
            this.config.set(path + ".hologram.location", hologram.getLocation());
        }
        this.config.set(path + ".radius", generator.getRadius());
        this.config.set(path + ".height", generator.getHeight());
        this.config.set(path + ".time", generator.getTime());
        this.config.set(path + ".default-block", generator.getDefaultBlock().name());
        this.config.set(path + ".permission", generator.getPermission());
        if (generator.getWorldName() != null) {
            this.config.set(path + ".world", generator.getWorldName());
        }

        List<Map<String, Object>> blocks = new ArrayList<>();
        for (GeneratorChance chance : generator.getBlocks()) {
            Map<String, Object> chanceMap = new LinkedHashMap<>();
            chanceMap.put("chance", chance.getChance());
            chanceMap.put("material", chance.getMaterial().name());
            blocks.add(chanceMap);
        }
        this.config.set(path + ".blocks", blocks);
    }

    public static Location getMiddleOfBlock(Location blockLocation) {
        Location location = blockLocation.getBlock().getLocation();
        return location.add(0.5D, 0.0D, 0.5D);
    }

    public boolean resetGenerator(String id) {
        if (id == null) {
            return false;
        }
        String key = id.toLowerCase(Locale.ROOT);
        if (!this.generators.containsKey(key)) {
            return false;
        }
        this.regenerationInProgress.remove(key);
        this.regenerate(key, true);
        return true;
    }

    public int resetAllGenerators() {
        int count = 0;
        for (String id : new ArrayList<>(this.generators.keySet())) {
            if (this.resetGenerator(id)) {
                count++;
            }
        }
        return count;
    }

    private void tickGenerator(String id) {
        Generator generator = this.generators.get(id);
        if (generator == null) {
            return;
        }
        if (this.regenerationInProgress.contains(id)) {
            return;
        }
        if (System.currentTimeMillis() < this.nextRegenerateAt.getOrDefault(id, 0L)) {
            return;
        }
        this.regenerate(id, true);
    }

    private void regenerate(String id, boolean fullCycle) {
        Generator generator = this.generators.get(id);
        if (generator == null) {
            return;
        }
        if (!this.regenerationInProgress.add(id)) {
            return;
        }

        Location center = this.deserializeLocation(generator.getCenter());
        if (center == null || center.getWorld() == null) {
            this.regenerationInProgress.remove(id);
            if (fullCycle) {
                this.nextRegenerateAt.put(id, System.currentTimeMillis() + generator.getTime());
            }
            return;
        }

        boolean replaceAll = fullCycle;
        Set<Material> preservedOres = this.buildPreservedMaterials(generator);
        List<Location> work = this.collectWork(generator, center, replaceAll, preservedOres, false);
        if (work.isEmpty()) {
            work = this.collectWork(generator, center, replaceAll, preservedOres, true);
        }

        if (work.isEmpty()) {
            this.regenerationInProgress.remove(id);
            if (fullCycle) {
                this.nextRegenerateAt.put(id, System.currentTimeMillis() + generator.getTime());
            }
            return;
        }

        Map<Integer, List<Location>> layers = new TreeMap<>();
        for (Location loc : work) {
            layers.computeIfAbsent(loc.getBlockY(), y -> new ArrayList<>()).add(loc);
        }

        boolean effects = this.config.getBoolean("regeneration.effects", true);
        long layerDelayTicks = Math.max(1L, Math.round(this.config.getDouble("regeneration.seconds-per-layer", 1.5D) * 20D));
        boolean playAnimation = fullCycle && this.config.getInt("settings.animation-last-seconds", 0) > 0;

        List<Integer> layerYs = new ArrayList<>(layers.keySet());
        long tickOffset = 0L;
        for (Integer layerY : layerYs) {
            List<Location> layer = layers.get(layerY);
            int layerSize = layer.size();

            for (int tick = 0; tick < layerDelayTicks; tick++) {
                int from = (int) ((layerSize * (long) tick) / layerDelayTicks);
                int to = (int) ((layerSize * (long) (tick + 1)) / layerDelayTicks);
                if (from >= to) {
                    continue;
                }
                List<Location> slice = new ArrayList<>(layer.subList(from, to));
                long delay = tickOffset + tick;
                Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
                    this.regenerateBatch(generator, slice, preservedOres, replaceAll, effects, playAnimation);
                }, delay);
            }
            tickOffset += layerDelayTicks;
        }

        String generatorId = id;
        boolean scheduleNextCycle = fullCycle;
        Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            this.regenerationInProgress.remove(generatorId);
            if (scheduleNextCycle) {
                Generator current = this.generators.get(generatorId);
                if (current != null) {
                    this.nextRegenerateAt.put(generatorId, System.currentTimeMillis() + current.getTime());
                }
            }
        }, tickOffset);
    }

    private List<Location> collectWork(
        Generator generator,
        Location center,
        boolean replaceAll,
        Set<Material> preservedOres,
        boolean forceLoadChunks
    ) {
        List<Location> work = new ArrayList<>();
        for (Location loc : this.getSquare(center, generator.getRadius(), generator.getHeight())) {
            World world = loc.getWorld();
            if (world == null) {
                continue;
            }
            int chunkX = loc.getBlockX() >> 4;
            int chunkZ = loc.getBlockZ() >> 4;
            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                if (!forceLoadChunks) {
                    continue;
                }
                world.loadChunk(chunkX, chunkZ);
            }
            Material current = loc.getBlock().getType();
            if (replaceAll) {
                work.add(loc);
                continue;
            }
            if (current == generator.getDefaultBlock() || preservedOres.contains(current)) {
                continue;
            }
            work.add(loc);
        }
        return work;
    }

    private Set<Material> buildPreservedMaterials(Generator generator) {
        Set<Material> preserved = new HashSet<>();
        for (GeneratorChance chance : generator.getBlocks()) {
            preserved.add(chance.getMaterial());
        }
        return preserved;
    }

    private void regenerateBatch(
        Generator generator,
        List<Location> locations,
        Set<Material> preservedOres,
        boolean replaceAll,
        boolean effects,
        boolean playAnimation
    ) {
        boolean playedSound = false;
        boolean playedParticle = false;
        for (Location location : locations) {
            Block block = location.getBlock();
            Material currentType = block.getType();
            Material target = this.pickBlock(generator);

            if (!replaceAll && (currentType == target || preservedOres.contains(currentType))) {
                continue;
            }

            if (!replaceAll && currentType == target) {
                continue;
            }

            try {
                block.setType(target, false);
                if (playAnimation && !playedParticle && location.getWorld() != null) {
                    location.getWorld().spawnParticle(
                        Particle.VILLAGER_HAPPY,
                        location.getX() + 0.5D,
                        location.getY() + 0.5D,
                        location.getZ() + 0.5D,
                        2, 0.15D, 0.15D, 0.15D, 0.0D
                    );
                    playedParticle = true;
                }
                if (effects && !playedSound && location.getWorld() != null) {
                    location.getWorld().playSound(location, Sound.BLOCK_STONE_PLACE, 0.35F, 1.6F);
                    playedSound = true;
                }
            } catch (Exception ignored) {
            }
        }
    }

    private void continuousRegenerateAll() {
        for (String id : this.getGeneratorIds()) {
            if (this.regenerationInProgress.contains(id)) {
                continue;
            }
            this.regenerate(id, false);
        }
    }

    private void updateHolograms() {
        if (this.generators.isEmpty() || !this.decentHolograms || !this.config.getBoolean("hologram.enabled", true)) {
            return;
        }
        for (String id : this.getGeneratorIds()) {
            this.updateHologram(id);
        }
    }

    private void updateHologram(String id) {
        Generator generator = this.generators.get(id);
        if (generator == null) {
            return;
        }

        List<String> templates = this.getHologramLineTemplatesInternal();
        double heightOffset = this.config.getDouble("hologram.height-offset", 1.45D);
        boolean iconEnabled = this.config.getBoolean("hologram.icon-enabled", true);
        Location hologramLocation = this.resolveHologramLocation(generator, heightOffset);
        if (hologramLocation == null) {
            return;
        }

        long remaining = Math.max(0L, this.nextRegenerateAt.getOrDefault(id, System.currentTimeMillis()) - System.currentTimeMillis());
        String blockName = generator.getDefaultBlock().name();
        String hologramName = generator.getHologram().getName();
        String timeFormatted = this.formatDuration(remaining);

        List<String> lines = new ArrayList<>();
        for (String template : templates) {
            if (template == null || template.isBlank()) {
                continue;
            }
            if (!iconEnabled && template.toUpperCase(Locale.ROOT).contains("#ICON")) {
                continue;
            }
            String line = template
                .replace("{TYPE}", blockName)
                .replace("{type}", blockName)
                .replace("{BLOCK}", blockName)
                .replace("{block}", blockName)
                .replace("{TIME}", timeFormatted)
                .replace("{time}", timeFormatted)
                .replace("{NAME}", hologramName)
                .replace("{name}", hologramName);
            lines.add(line);
        }

        if (lines.isEmpty()) {
            if (iconEnabled) {
                lines.add("#ICON:" + blockName);
            }
            lines.add("&7Regeneracja za &f" + timeFormatted);
        }

        String holoId = this.runtimeHologramId(id);
        String worldName = hologramLocation.getWorld() != null ? hologramLocation.getWorld().getName() : "";
        String signature = timeFormatted + '\n' + blockName + '\n' + hologramName + '\n' + worldName
            + ',' + hologramLocation.getBlockX() + ',' + hologramLocation.getBlockY() + ',' + hologramLocation.getBlockZ();
        Hologram hologram = DHAPI.getHologram(holoId);
        if (hologram == null) {
            DHAPI.createHologram(holoId, hologramLocation, lines);
            this.lastHologramSignature.put(id, signature);
            return;
        }
        if (signature.equals(this.lastHologramSignature.get(id))) {
            return;
        }
        DHAPI.moveHologram(hologram, hologramLocation);
        DHAPI.setHologramLines(hologram, lines);
        this.lastHologramSignature.put(id, signature);
    }

    private List<String> getHologramLineTemplatesInternal() {
        List<String> lines = this.config.getStringList("hologramLines");
        if (!lines.isEmpty()) {
            return lines;
        }
        lines = this.config.getStringList("hologram.lines");
        if (!lines.isEmpty()) {
            return lines;
        }
        return List.of("#ICON:{TYPE}", "&f⛏ &8| &7Regeneracja za &f&n{TIME}&8 | ⛏");
    }

    private Location resolveHologramLocation(Generator generator, double heightOffset) {
        Location stored = this.deserializeLocation(generator.getHologram().getLocation());
        if (stored != null) {
            return stored;
        }
        Location center = this.deserializeLocation(generator.getCenter());
        if (center == null) {
            return null;
        }
        return center.clone().add(0.0D, heightOffset, 0.0D);
    }

    private void restartRuntime() {
        this.cancelTasks();
        if (this.decentHolograms) {
            for (String id : new ArrayList<>(this.generators.keySet())) {
                DHAPI.removeHologram(this.runtimeHologramId(id));
            }
        }
        this.start();
    }

    private void cancelTasks() {
        for (BukkitTask task : this.tasks) {
            task.cancel();
        }
        this.tasks.clear();
    }

    private List<Location> collectCreativeBuilderLocations(World world) {
        if (world == null) {
            return List.of();
        }
        List<Location> builders = new ArrayList<>();
        for (Player player : world.getPlayers()) {
            GameMode mode = player.getGameMode();
            if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) {
                builders.add(player.getLocation());
            }
        }
        return builders;
    }

    private static boolean isNearCachedBuilders(Location location, List<Location> builders, double radius) {
        if (builders.isEmpty() || location == null) {
            return false;
        }
        double radiusSquared = radius * radius;
        for (Location builder : builders) {
            if (builder.getWorld() == location.getWorld() && builder.distanceSquared(location) <= radiusSquared) {
                return true;
            }
        }
        return false;
    }

    private boolean isNearCreativeBuilder(Location location, double radius) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        double radiusSquared = radius * radius;
        for (Player player : location.getWorld().getPlayers()) {
            GameMode mode = player.getGameMode();
            if (mode != GameMode.CREATIVE && mode != GameMode.SPECTATOR) {
                continue;
            }
            if (player.getLocation().distanceSquared(location) <= radiusSquared) {
                return true;
            }
        }
        return false;
    }

    private Material pickBlock(Generator generator) {
        List<GeneratorChance> chances = generator.getBlocks();
        Material defaultBlock = generator.getDefaultBlock();

        if (chances.isEmpty()) {
            return defaultBlock;
        }

        boolean hasDefault = false;
        double total = 0.0D;
        for (GeneratorChance chance : chances) {
            total += chance.getChance();
            if (chance.getMaterial() == defaultBlock) {
                hasDefault = true;
            }
        }

        double defaultWeight = hasDefault ? 0.0D : 100.0D;
        double grandTotal = total + defaultWeight;
        if (grandTotal <= 0.0D) {
            return defaultBlock;
        }

        double roll = ThreadLocalRandom.current().nextDouble(grandTotal);

        if (!hasDefault && roll < defaultWeight) {
            return defaultBlock;
        }

        double cumulative = defaultWeight;
        for (GeneratorChance chance : chances) {
            cumulative += chance.getChance();
            if (roll < cumulative) {
                return chance.getMaterial();
            }
        }
        return chances.get(chances.size() - 1).getMaterial();
    }

    public boolean isInGeneratorVolume(Location location) {
        return this.findGeneratorAt(location) != null;
    }

    public Set<Material> getAllGeneratorMaterials() {
        Set<Material> materials = EnumSet.noneOf(Material.class);
        for (Generator generator : this.generators.values()) {
            if (generator.getDefaultBlock() != null && generator.getDefaultBlock().isBlock()) {
                materials.add(generator.getDefaultBlock());
            }
            for (GeneratorChance chance : generator.getBlocks()) {
                if (chance != null && chance.getMaterial() != null && chance.getMaterial().isBlock()) {
                    materials.add(chance.getMaterial());
                }
            }
        }
        if (this.honeyRegenerate != null && this.honeyRegenerate.getMaterial() != null) {
            materials.add(this.honeyRegenerate.getMaterial());
        }
        if (this.cyanConcretePowderRegenerate != null && this.cyanConcretePowderRegenerate.getMaterial() != null) {
            materials.add(this.cyanConcretePowderRegenerate.getMaterial());
        }
        return materials;
    }

    public void syncZoneBoxAllowedBlocks() {
        if (!(this.plugin instanceof HollyCore hollyCore)) {
            return;
        }
        if (hollyCore.getZoneBoxManager() == null) {
            return;
        }
        hollyCore.getZoneBoxManager().syncGeneratorBlocks(this.getAllGeneratorMaterials());
    }

    public Generator findGeneratorAt(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        List<IndexedGenerator> candidates = this.generatorsByChunk.get(chunkKey(location));
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        for (IndexedGenerator indexed : candidates) {
            if (indexed.contains(location)) {
                return indexed.generator;
            }
        }
        return null;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Generator generator = this.findGeneratorAt(event.getBlock().getLocation());
        if (generator == null) {
            return;
        }
        String required = generator.getPermission();
        if (required != null && !required.isBlank() && !event.getPlayer().hasPermission(required)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ColorUtils.noPermission("/generator", required));
            return;
        }

        if (generator.containsWool() || isWoolMaterial(event.getBlock().getType())) {
            Player player = event.getPlayer();
            ItemStack tool = player.getInventory().getItemInMainHand();
            UpgradeManager upgradeManager = getUpgradeManager();
            boolean isUpgradedShears = upgradeManager != null && upgradeManager.isUpgradedShears(tool);
            if (!isUpgradedShears) {
                event.setCancelled(true);
                String msg = getMessage("wool-shears-only");
                if (msg == null || msg.isBlank()) {
                    msg = "&#FF5C5C✖ Ten generator możesz kopać tylko nożyczkami z ulepsz!";
                }
                player.sendMessage(ColorUtils.colorizeComponent(getMessage("prefix") + msg));
                return;
            } else if (upgradeManager != null) {
                upgradeManager.updateShearsIfOutdated(tool);
            }
        }
    }

    private UpgradeManager getUpgradeManager() {
        if (this.plugin instanceof HollyCore core) {
            return core.getUpgradeManager();
        }
        HollyCore core = HollyCore.getInstance();
        return core != null ? core.getUpgradeManager() : null;
    }

    public static boolean isWoolMaterial(Material material) {
        if (material == null) {
            return false;
        }
        String name = material.name();
        return name.endsWith("_WOOL") || name.equals("WOOL");
    }

    private static boolean isInsideGeneratorBox(Location location, Location center, int radius, int height) {
        int dx = location.getBlockX() - center.getBlockX();
        int dy = location.getBlockY() - center.getBlockY();
        int dz = location.getBlockZ() - center.getBlockZ();
        return dy >= 0 && dy < height && dx >= -radius && dx <= radius && dz >= -radius && dz <= radius;
    }

    private List<Location> getSquare(Location center, int radius, int height) {
        List<Location> locations = new ArrayList<>();
        World world = center.getWorld();
        if (world == null) {
            return locations;
        }

        int baseX = center.getBlockX();
        int baseY = center.getBlockY();
        int baseZ = center.getBlockZ();
        for (int y = 0; y < height; y++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    locations.add(new Location(world, baseX + x, baseY + y, baseZ + z));
                }
            }
        }
        return locations;
    }

    private Generator deserializeGenerator(String id) {
        String path = "generators." + id;
        Object centerRaw = this.config.get(path + ".center");
        if (centerRaw instanceof String centerString && !centerString.isBlank()) {
            return this.deserializeModernGenerator(id, path);
        }
        if (this.config.contains(path + ".center.x") || this.config.contains(path + ".min.x") || this.config.contains(path + ".world")) {
            return this.deserializeLegacyGenerator(id, path);
        }
        return this.deserializeModernGenerator(id, path);
    }

    private Generator deserializeModernGenerator(String id, String path) {
        String center = this.config.getString(path + ".center", null);
        String hologramName = this.config.getString(path + ".hologram.name", id);
        String hologramLocation = this.config.getString(path + ".hologram.location", null);
        Material defaultBlock = this.parseMaterial(this.config.getString(path + ".default-block", this.config.getString(path + ".primary-block", "STONE")));
        if (center == null || defaultBlock == null) {
            this.logger.warning("[Generators] Pominięto generator " + id + " - brak danych hollyservice.");
            return null;
        }

        Location centerLocation = this.deserializeLocation(center);
        if (centerLocation == null) {
            this.logger.warning("[Generators] Pominięto generator " + id + " - nieprawidlowe centrum.");
            return null;
        }
        if (hologramLocation == null) {
            double heightOffset = this.config.getDouble("hologram.height-offset", 1.45D);
            hologramLocation = this.serializeLocation(centerLocation.clone().add(0.0D, heightOffset, 0.0D));
        }

        return new Generator(
            center,
            new GeneratorHologram(hologramName, hologramLocation),
            this.config.getInt(path + ".radius", 2),
            this.config.getInt(path + ".height", 2),
            Math.max(1000L, (long) this.config.getDouble(path + ".time", this.config.getInt(path + ".renew-seconds", 30) * 1000L)),
            defaultBlock,
            this.config.getString(path + ".permission", ""),
            this.loadBlockEntries(path),
            null,
            false
        );
    }

    private Generator deserializeLegacyGenerator(String id, String path) {
        String worldName = this.config.getString(path + ".world", "world");
        World world = resolveWorld(worldName);
        if (world == null) {
            this.logger.warning("[Generators] Pominięto generator " + id + " - brak swiata " + worldName);
            return null;
        }

        int centerX = this.config.getInt(path + ".center.x", this.config.getInt(path + ".min.x", 0));
        int centerY = this.config.getInt(path + ".center.y", this.config.getInt(path + ".min.y", 64));
        int centerZ = this.config.getInt(path + ".center.z", this.config.getInt(path + ".min.z", 0));
        int radius = this.config.getInt(path + ".radius", 2);
        int height = this.config.getInt(path + ".height", 2);
        long renewSeconds = Math.max(1L, this.config.getInt(path + ".renew-seconds", (int) Math.max(1L, (long) (this.config.getDouble(path + ".time", 30000D) / 1000D))));
        Material defaultBlock = this.parseMaterial(this.config.getString(path + ".primary-block", this.config.getString(path + ".default-block", "STONE")));
        if (defaultBlock == null) {
            defaultBlock = Material.STONE;
        }

        Location center = new Location(world, centerX + 0.5D, centerY, centerZ + 0.5D);
        double heightOffset = this.config.getDouble("hologram.height-offset", 1.45D);
        Location hologram = center.clone().add(0.0D, heightOffset, 0.0D);

        return new Generator(
            this.serializeLocation(center),
            new GeneratorHologram(id, this.serializeLocation(hologram)),
            radius,
            height,
            renewSeconds * 1000L,
            defaultBlock,
            this.config.getString(path + ".permission", ""),
            this.loadBlockEntries(path),
            world.getName(),
            true
        );
    }

    private List<GeneratorChance> loadBlockEntries(String path) {
        List<GeneratorChance> blocks = new ArrayList<>();
        Object rawBlocks = this.config.get(path + ".blocks");
        if (rawBlocks instanceof List<?> list) {
            for (Object entry : list) {
                if (!(entry instanceof Map<?, ?> map)) {
                    continue;
                }
                Object chanceValue = map.get("chance");
                Object materialValue = map.get("material");
                if (!(chanceValue instanceof Number number) || materialValue == null) {
                    continue;
                }
                Material material = this.parseMaterial(String.valueOf(materialValue));
                if (material == null) {
                    continue;
                }
                blocks.add(new GeneratorChance(number.doubleValue(), material));
            }
        } else if (rawBlocks instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Material material = this.parseMaterial(String.valueOf(entry.getKey()));
                double chance = this.parseChance(entry.getValue());
                if (material != null && chance > 0.0D) {
                    blocks.add(new GeneratorChance(chance, material));
                }
            }
        } else {
            ConfigurationSection section = this.config.getConfigurationSection(path + ".blocks");
            if (section != null) {
                for (String key : section.getKeys(false)) {
                    Material material = this.parseMaterial(key);
                    double chance = section.getDouble(key);
                    if (material != null && chance > 0.0D) {
                        blocks.add(new GeneratorChance(chance, material));
                    }
                }
            }
        }
        return blocks;
    }

    private double parseChance(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return 0.0D;
        }
        try {
            return Double.parseDouble(String.valueOf(value).trim().replace(',', '.'));
        } catch (NumberFormatException exception) {
            return 0.0D;
        }
    }

    private Material parseMaterial(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Material.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (Exception exception) {
            Material matched = Material.matchMaterial(value.trim().toUpperCase(Locale.ROOT));
            return matched != null && matched.isBlock() ? matched : null;
        }
    }

    private String runtimeHologramId(String id) {
        return "generator_" + id.toLowerCase(Locale.ROOT);
    }

    private String serializeLocation(Location location) {
        return (location.getWorld() != null ? location.getWorld().getName() : "world") + ";"
            + location.getX() + ";"
            + location.getY() + ";"
            + location.getZ() + ";"
            + location.getYaw() + ";"
            + location.getPitch();
    }

    private Location deserializeLocation(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        Location cached = this.locationCache.get(value);
        if (cached != null && cached.getWorld() != null) {
            return cached;
        }
        Location parsed = this.parseLocation(value);
        if (parsed != null) {
            this.locationCache.put(value, parsed);
        }
        return parsed;
    }

    private Location parseLocation(String value) {
        String[] parts = value.split(";");
        if (parts.length != 6) {
            return null;
        }

        World world = resolveWorld(parts[0]);
        if (world == null) {
            return null;
        }
        try {
            return new Location(
                world,
                Double.parseDouble(parts[1]),
                Double.parseDouble(parts[2]),
                Double.parseDouble(parts[3]),
                Float.parseFloat(parts[4]),
                Float.parseFloat(parts[5])
            );
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private void rebuildSpatialIndex() {
        this.generatorsByChunk.clear();
        for (Generator generator : this.generators.values()) {
            Location center = this.deserializeLocation(generator.getCenter());
            if (center == null || center.getWorld() == null) {
                continue;
            }
            IndexedGenerator indexed = new IndexedGenerator(generator, center);
            int radius = indexed.radius;
            int minChunkX = (indexed.blockX - radius) >> 4;
            int maxChunkX = (indexed.blockX + radius) >> 4;
            int minChunkZ = (indexed.blockZ - radius) >> 4;
            int maxChunkZ = (indexed.blockZ + radius) >> 4;
            String worldName = indexed.world.getName();
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    this.generatorsByChunk
                        .computeIfAbsent(worldName + ':' + chunkX + ':' + chunkZ, key -> new ArrayList<>())
                        .add(indexed);
                }
            }
        }
    }

    private static String chunkKey(Location location) {
        return location.getWorld().getName() + ':' + (location.getBlockX() >> 4) + ':' + (location.getBlockZ() >> 4);
    }

    private static World resolveWorld(String worldName) {
        if (worldName == null || worldName.isBlank()) {
            return null;
        }
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            return world;
        }
        for (World loaded : Bukkit.getWorlds()) {
            if (loaded.getName().equalsIgnoreCase(worldName)) {
                return loaded;
            }
        }
        return null;
    }

    private static boolean isSameWorld(World first, World second) {
        if (first == null || second == null) {
            return false;
        }
        if (first.equals(second)) {
            return true;
        }
        return first.getName().equalsIgnoreCase(second.getName());
    }

    private void ensureDefaults() {
        boolean changed = false;
        if (this.config.getBoolean("continuous-regeneration.enabled", false)) {
            this.config.set("continuous-regeneration.enabled", false);
            changed = true;
        }
        changed |= this.setDefaultIfMissing("continuous-regeneration.enabled", false);
        changed |= this.setDefaultIfMissing("continuous-regeneration.check-interval-ticks", 20);
        changed |= this.setDefaultIfMissing("continuous-regeneration.replace-all-blocks", true);
        changed |= this.setDefaultIfMissing("settings.animation-last-seconds", 4);
        changed |= this.setDefaultIfMissing("hologram.enabled", true);
        changed |= this.setDefaultIfMissing("hologram.height-offset", 1.45D);
        changed |= this.setDefaultIfMissing("hologram.update-interval-ticks", 5);
        changed |= this.setDefaultIfMissing("hologram.icon-offset", 0.85D);
        changed |= this.setDefaultIfMissing("hologram.text-offset", -0.35D);
        changed |= this.setDefaultIfMissing("hologram.background", true);
        changed |= this.setDefaultIfMissing("hologram.shadow", true);
        changed |= this.setDefaultIfMissing("hologram.icon-enabled", true);
        changed |= this.setDefaultIfMissing("regeneration.seconds-per-layer", 1.5D);
        changed |= this.setDefaultIfMissing("regeneration.blocks-per-tick", 4);
        changed |= this.setDefaultIfMissing("regeneration.ticks-between-batches", 3);
        changed |= this.setDefaultIfMissing("regeneration.effects", true);
        changed |= this.setDefaultIfMissing("hologramLines", List.of("#ICON:{TYPE}", "&f⛏ &8| &7Regeneracja za &f&n{TIME}&8 | ⛏"));
        changed |= this.setDefaultIfMissing("messages.prefix", "");
        changed |= this.setDefaultIfMissing("messages.created", "&#60FF8B✔ Stworzono generator!");
        changed |= this.setDefaultIfMissing("messages.updated", "&#60FF8B✔ Zaaktualizowano generator!");
        changed |= this.setDefaultIfMissing("messages.deleted", "&#60FF8B✔ Usunięto generator!");
        changed |= this.setDefaultIfMissing("messages.exists", "&#FF5C5C✖ Generator o tej nazwie już istnieje!");
        changed |= this.setDefaultIfMissing("messages.not-found", "&#FF5C5C✖ Nie znaleziono generatora!");
        changed |= this.setDefaultIfMissing("messages.invalid-size", "&#FF5C5C✖ Argument musi być w przedziale 1-30.");
        changed |= this.setDefaultIfMissing("messages.invalid-time", "&#FF5C5C✖ Czas musi być większy niż 1 sekunda.");
        changed |= this.setDefaultIfMissing("messages.invalid-material", "&#FF5C5C✖ Nieprawidłowy materiał.");
        changed |= this.setDefaultIfMissing("messages.teleported", "&#60FF8B✔ Przeteleportowano do generatora!");
        changed |= this.setDefaultIfMissing("messages.reloaded", "&#60FF8B✔ Przeładowano konfigurację generatorów!");
        changed |= this.setDefaultIfMissing("messages.wool-shears-only", "&#FF5C5C✖ Ten generator możesz kopać tylko nożyczkami z ulepsz!");
        if (!this.config.contains("generators")) {
            this.config.set("generators", new LinkedHashMap<String, Object>());
            changed = true;
        }
        if (changed) {
            this.config.save();
        }
    }

    private boolean setDefaultIfMissing(String path, Object value) {
        if (this.config.contains(path)) {
            return false;
        }
        this.config.set(path, value);
        return true;
    }

    private static long parseDuration(String input) {
        if (input == null || input.isBlank()) {
            return 0L;
        }
        if (input.chars().allMatch(Character::isDigit)) {
            return Long.parseLong(input) * 1000L;
        }
        long total = 0;
        StringBuilder num = new StringBuilder();
        for (char c : input.toCharArray()) {
            if (Character.isDigit(c)) {
                num.append(c);
            } else {
                if (num.isEmpty()) {
                    continue;
                }
                long val = Long.parseLong(num.toString());
                switch (c) {
                    case 'd' -> total += val * 86400000L;
                    case 'h' -> total += val * 3600000L;
                    case 'm' -> total += val * 60000L;
                    case 's' -> total += val * 1000L;
                }
                num.setLength(0);
            }
        }
        return total;
    }
}
