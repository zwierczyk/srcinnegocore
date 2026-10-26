package dev.grzybalson.modules.generator;

import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

/**
 * Persists block locations, shows one countdown hologram and restores all blocks every 5 minutes.
 */
public class BlockRegenerateManager {

    private static final long PERIOD_MS = 5L * 60L * 1000L;
    private static final long PERIOD_TICKS = 5L * 60L * 20L;
    private static final long HOLOGRAM_UPDATE_TICKS = 20L;
    private static final int SCAN_HALF = 25;

    private final GeneratorManager generatorManager;
    private final JavaPlugin plugin;
    private final Logger logger;
    private final GeneratorConfig config;
    private final Material material;
    private final String blockLabel;
    private final String hologramId;
    private final List<String> locations = new CopyOnWriteArrayList<>();

    private long lastRegenerateMs;
    private String hologramLocationSerialized;
    private String hologramName;
    private BukkitTask regenTask;
    private BukkitTask hologramTask;
    private String lastHologramSignature;

    public BlockRegenerateManager(
        GeneratorManager generatorManager,
        JavaPlugin plugin,
        GeneratorConfig config,
        Material material,
        String configKey
    ) {
        this.generatorManager = generatorManager;
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.config = config;
        this.material = material;
        this.blockLabel = material.name().toLowerCase(Locale.ROOT);
        this.hologramId = "blockregen_" + configKey.toLowerCase(Locale.ROOT);
    }

    public Material getMaterial() {
        return material;
    }

    public String getBlockLabel() {
        return blockLabel;
    }

    public void start() {
        this.load();
        this.lastRegenerateMs = System.currentTimeMillis();
        this.cancelTasks();
        this.regenTask = Bukkit.getScheduler().runTaskTimer(this.plugin, this::regenerateAll, PERIOD_TICKS, PERIOD_TICKS);
        this.hologramTask = Bukkit.getScheduler().runTaskTimer(this.plugin, this::updateHologram, HOLOGRAM_UPDATE_TICKS, HOLOGRAM_UPDATE_TICKS);
        this.updateHologram();
    }

    public void shutdown() {
        this.cancelTasks();
        if (this.generatorManager.isDecentHologramsAvailable()) {
            DHAPI.removeHologram(this.hologramId);
        }
    }

    public void reload() {
        this.config.load();
        this.load();
        this.updateHologram();
    }

    private void cancelTasks() {
        if (this.regenTask != null) {
            this.regenTask.cancel();
            this.regenTask = null;
        }
        if (this.hologramTask != null) {
            this.hologramTask.cancel();
            this.hologramTask = null;
        }
    }

    private void load() {
        this.locations.clear();
        List<String> list = this.config.getStringList("locations");
        if (list != null) {
            for (String s : list) {
                if (s != null && !s.isBlank()) {
                    this.locations.add(s.trim());
                }
            }
        }
        this.hologramLocationSerialized = this.config.getString("hologram.location", "");
        this.hologramName = this.config.getString("hologram.name", this.material.name());
        if (this.hologramName == null || this.hologramName.isBlank()) {
            this.hologramName = this.material.name();
        }
    }

    private void saveLocations() {
        this.config.set("locations", new ArrayList<>(this.locations));
        this.config.save();
    }

    public int scanAndSaveCuboid(Player player) {
        Location loc = player.getLocation();
        World world = loc.getWorld();
        if (world == null) {
            return 0;
        }
        int cx = loc.getBlockX();
        int cy = loc.getBlockY();
        int cz = loc.getBlockZ();
        String wname = world.getName();
        int added = 0;
        for (int dx = -SCAN_HALF; dx < SCAN_HALF; dx++) {
            for (int dy = -SCAN_HALF; dy < SCAN_HALF; dy++) {
                for (int dz = -SCAN_HALF; dz < SCAN_HALF; dz++) {
                    int x = cx + dx;
                    int y = cy + dy;
                    int z = cz + dz;
                    if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                        continue;
                    }
                    if (world.getBlockAt(x, y, z).getType() != this.material) {
                        continue;
                    }
                    String key = wname + "," + x + "," + y + "," + z;
                    if (!this.locations.contains(key)) {
                        this.locations.add(key);
                        added++;
                    }
                }
            }
        }
        if (added > 0) {
            this.saveLocations();
        }
        return added;
    }

    public boolean setHologramLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        this.hologramLocationSerialized = serializeLocation(location);
        this.config.set("hologram.location", this.hologramLocationSerialized);
        this.config.setDefault("hologram.name", this.material.name());
        this.config.save();
        this.load();
        this.updateHologram();
        return true;
    }

    public int getCount() {
        return this.locations.size();
    }

    private void regenerateAll() {
        this.lastRegenerateMs = System.currentTimeMillis();
        if (this.locations.isEmpty()) {
            return;
        }

        int restored = 0;
        for (String entry : this.locations) {
            String[] parts = entry.split(",", 4);
            if (parts.length != 4) {
                continue;
            }
            World world = resolveWorld(parts[0]);
            if (world == null) {
                continue;
            }
            try {
                int x = Integer.parseInt(parts[1].trim());
                int y = Integer.parseInt(parts[2].trim());
                int z = Integer.parseInt(parts[3].trim());
                Location location = new Location(world, x + 0.5D, y + 0.5D, z + 0.5D);
                if (isNearCreativeBuilder(location, 16.0D)) {
                    continue;
                }
                Chunk chunk = world.getChunkAt(x >> 4, z >> 4);
                if (!chunk.isLoaded()) {
                    chunk.load();
                }
                world.getBlockAt(x, y, z).setType(this.material, false);
                restored++;
            } catch (NumberFormatException ignored) {
            }
        }

        if (restored > 0) {
            this.logger.info("[BlockRegen:" + this.blockLabel + "] Odnowiono " + restored + " blokow naraz.");
        }
        this.updateHologram();
    }

    private void updateHologram() {
        if (!this.generatorManager.isDecentHologramsAvailable()) {
            return;
        }
        if (!this.generatorManager.getGeneratorConfig().getBoolean("hologram.enabled", true)) {
            return;
        }
        if (this.hologramLocationSerialized == null || this.hologramLocationSerialized.isBlank()) {
            return;
        }

        Location hologramLocation = deserializeLocation(this.hologramLocationSerialized);
        if (hologramLocation == null) {
            return;
        }

        long regenAt = this.lastRegenerateMs + PERIOD_MS;
        long remaining = Math.max(0L, regenAt - System.currentTimeMillis());
        String blockName = this.material.name();
        String timeFormatted = this.generatorManager.formatDuration(remaining);
        List<String> templates = this.generatorManager.getHologramLineTemplates();
        boolean iconEnabled = this.generatorManager.getGeneratorConfig().getBoolean("hologram.icon-enabled", true);

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
                .replace("{NAME}", this.hologramName)
                .replace("{name}", this.hologramName);
            lines.add(line);
        }

        if (lines.isEmpty()) {
            if (iconEnabled) {
                lines.add("#ICON:" + blockName);
            }
            lines.add("&7Regeneracja za &f" + timeFormatted);
        }

        Hologram hologram = DHAPI.getHologram(this.hologramId);
        String signature = timeFormatted + '\n' + this.hologramLocationSerialized;
        if (hologram == null) {
            DHAPI.createHologram(this.hologramId, hologramLocation, lines);
            this.lastHologramSignature = signature;
            return;
        }
        if (signature.equals(this.lastHologramSignature)) {
            return;
        }
        DHAPI.setHologramLines(hologram, lines);
        this.lastHologramSignature = signature;
    }

    private static String serializeLocation(Location location) {
        return (location.getWorld() != null ? location.getWorld().getName() : "world") + ";"
            + location.getX() + ";"
            + location.getY() + ";"
            + location.getZ() + ";"
            + location.getYaw() + ";"
            + location.getPitch();
    }

    private static Location deserializeLocation(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
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

    private static boolean isNearCreativeBuilder(Location location, double radius) {
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
}
