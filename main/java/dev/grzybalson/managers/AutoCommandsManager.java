package dev.grzybalson.managers;

import dev.grzybalson.HollyCore;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class AutoCommandsManager {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Europe/Warsaw");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter EXECUTION_KEY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Pattern TIME_FORMATTER_PATTERN = Pattern.compile("\\d{2}:\\d{2}");

    private final HollyCore plugin;
    private final File configFile;
    private YamlConfiguration config;
    private BukkitTask task;
    private final Set<String> executedMinutes = ConcurrentHashMap.newKeySet();
    private ZoneId zoneId = DEFAULT_ZONE;
    private Map<String, List<String>> commandsByTime = Map.of();

    public AutoCommandsManager(HollyCore plugin) {
        this.plugin = plugin;
        this.configFile = new File(dev.grzybalson.config.HollyDataPaths.getAddonFolder(plugin, "autocommands"), "autocommands.yml");
        ensureDefaultConfig();
        load();
    }

    public void start() {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        plugin.getLogger().info("AutoCommands started in timezone: " + zoneId.getId());
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void reload() {
        executedMinutes.clear();
        load();
    }

    private void tick() {
        LocalDateTime now = LocalDateTime.now(zoneId);
        String timeKey = now.format(TIME_FORMATTER);
        String executionKey = now.format(EXECUTION_KEY_FORMATTER);

        List<String> commands = getCommandsForTime(timeKey);
        if (commands.isEmpty()) {
            cleanupExecutedMinutes(executionKey);
            return;
        }

        if (!executedMinutes.add(executionKey)) {
            return;
        }

        for (String command : commands) {
            if (command == null || command.isBlank()) {
                continue;
            }

            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.trim());
            plugin.getLogger().info("[AutoCommands] Executed at " + timeKey + ": " + command.trim());
        }

        cleanupExecutedMinutes(executionKey);
    }

    private void cleanupExecutedMinutes(String currentExecutionKey) {
        executedMinutes.removeIf(entry -> !entry.equals(currentExecutionKey));
    }

    private void load() {
        ensureDefaultConfig();
        this.config = YamlConfiguration.loadConfiguration(configFile);
        this.zoneId = loadZoneId();
        this.commandsByTime = loadCommandsByTime();
    }

    private ZoneId loadZoneId() {
        String zone = config.getString("timezone", DEFAULT_ZONE.getId());
        try {
            return ZoneId.of(zone);
        } catch (Exception ignored) {
            return DEFAULT_ZONE;
        }
    }

    private List<String> getCommandsForTime(String timeKey) {
        return commandsByTime.getOrDefault(timeKey, Collections.emptyList());
    }

    private Map<String, List<String>> loadCommandsByTime() {
        Map<String, List<String>> loaded = new java.util.HashMap<>();

        for (String key : config.getKeys(false)) {
            if (TIME_FORMATTER_PATTERN.matcher(key).matches()) {
                List<String> commands = config.getStringList(key);
                if (!commands.isEmpty()) {
                    loaded.put(key, List.copyOf(commands));
                }
            }
        }
        ConfigurationSection section = config.getConfigurationSection("commands");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                List<String> commands = section.getStringList(key);
                if (commands.isEmpty()) {
                    continue;
                }
                loaded.merge(key, List.copyOf(commands), (oldCommands, newCommands) -> {
                    List<String> merged = new ArrayList<>(oldCommands.size() + newCommands.size());
                    merged.addAll(oldCommands);
                    merged.addAll(newCommands);
                    return List.copyOf(merged);
                });
            }
        }

        return loaded.isEmpty() ? Map.of() : Map.copyOf(loaded);
    }

    private void ensureDefaultConfig() {
        File parent = configFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        if (configFile.exists()) {
            return;
        }

        try {
            plugin.saveResource("addons/autocommands/autocommands.yml", false);
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("Nie udało się skopiować autocommands/autocommands.yml z zasobów.");
        }
    }
}
