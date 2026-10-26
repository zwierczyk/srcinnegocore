package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.HollyDataPaths;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TimerCommand implements CommandExecutor, TabCompleter, Listener {

    private static final String RESOURCE_PATH = "addons/commands/timer.yml";
    private static final Pattern DURATION_PART = Pattern.compile("(\\d+)([smhd]?)");

    private final HollyCore plugin;
    private final File configFile;
    private final Map<String, ActiveTimer> activeTimers = new ConcurrentHashMap<>();
    private YamlConfiguration config;

    public TimerCommand(HollyCore plugin) {
        this.plugin = plugin;
        HollyDataPaths.ensureResource(plugin, RESOURCE_PATH);
        this.configFile = new File(plugin.getDataFolder(), RESOURCE_PATH);
        reload();
    }

    public void reload() {
        this.config = YamlConfiguration.loadConfiguration(configFile);
        appendMissingDefaults();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!sender.hasPermission("Holly.cmd.timer")) {
            sender.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.timer"));
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reload();
            sender.sendMessage(ColorUtils.colorizeComponent(message("messages.reloaded", "&#00FF3B✔ &8Ξ &#4EFA76Przeladowano timer.")));
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("cancel")) {
            cancelTimers(sender);
            return true;
        }

        if (args.length < 2 || !args[0].equalsIgnoreCase("key")) {
            sendUsage(sender);
            return true;
        }

        String key = normalizeKey(args[1]);
        KeyTimer keyTimer = loadKeyTimer(key);
        if (keyTimer == null || !keyTimer.enabled()) {
            sender.sendMessage(ColorUtils.colorizeComponent(
                message("messages.key-not-found", "&#FF0000✘ &8Ξ &cNie znaleziono takiego timera kluczy: &4{KEY}")
                    .replace("{KEY}", args[1])
            ));
            return true;
        }

        int seconds = keyTimer.seconds();
        if (args.length >= 3) {
            seconds = parseDurationSeconds(args[2]);
        }
        if (seconds <= 0) {
            sender.sendMessage(ColorUtils.colorizeComponent(message("messages.invalid-time", "&#FF0000✘ &8Ξ &cPodaj poprawny czas.")));
            return true;
        }

        if (activeTimers.containsKey(key)) {
            sender.sendMessage(ColorUtils.colorizeComponent(
                message("messages.already-running", "&#FF0000✘ &8Ξ &cTimer &4{KEY} &cjest juz uruchomiony.")
                    .replace("{KEY}", keyTimer.displayName())
            ));
            return true;
        }

        startTimer(key, keyTimer, seconds);
        sender.sendMessage(ColorUtils.colorizeComponent(
            message("messages.started", "&#00FF3B✔ &8Ξ &#4EFA76Uruchomiono timer &f{KEY} &ana &f{TIME}&a.")
                .replace("{KEY}", keyTimer.displayName())
                .replace("{TIME}", formatDuration(seconds))
                .replace("{SECONDS}", String.valueOf(seconds))
        ));
        return true;
    }

    private void startTimer(String key, KeyTimer keyTimer, int seconds) {
        BossBar bossBar = Bukkit.createBossBar(
            renderBossBarTitle(keyTimer.displayName(), seconds, seconds),
            parseBarColor(config.getString("bossbar.color", "YELLOW")),
            parseBarStyle(config.getString("bossbar.style", "SOLID"))
        );
        bossBar.setProgress(1.0D);
        for (Player player : Bukkit.getOnlinePlayers()) {
            bossBar.addPlayer(player);
        }

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            private int remaining = seconds;

            @Override
            public void run() {
                if (remaining <= 0) {
                    finishTimer(key, keyTimer, bossBar);
                    return;
                }

                double progress = Math.max(0.0D, Math.min(1.0D, remaining / (double) seconds));
                bossBar.setTitle(renderBossBarTitle(keyTimer.displayName(), remaining, seconds));
                bossBar.setProgress(progress);
                playCountdownSound(remaining);
                remaining--;
            }
        }, 0L, 20L);

        activeTimers.put(key, new ActiveTimer(bossBar, task));
    }

    private void cancelTimers(CommandSender sender) {
        if (activeTimers.isEmpty()) {
            sender.sendMessage(ColorUtils.colorizeComponent(message(
                "messages.nothing-to-cancel",
                "&#FF0000✘ &8Ξ &cNie ma aktywnego timera do anulowania."
            )));
            return;
        }

        int cancelled = 0;
        for (ActiveTimer activeTimer : activeTimers.values()) {
            activeTimer.task().cancel();
            activeTimer.bossBar().removeAll();
            cancelled++;
        }
        activeTimers.clear();

        sender.sendMessage(ColorUtils.colorizeComponent(message(
            "messages.cancelled",
            "&#00FF3B✔ &8Ξ &#4EFA76Anulowano aktywne timery: &f{COUNT}&a."
        ).replace("{COUNT}", String.valueOf(cancelled))));
    }

    private void finishTimer(String key, KeyTimer keyTimer, BossBar bossBar) {
        ActiveTimer activeTimer = activeTimers.remove(key);
        if (activeTimer != null) {
            activeTimer.task().cancel();
        }
        bossBar.removeAll();
        executeCommands(keyTimer);

        String finishedMessage = message("messages.finished", "&#00FF3B✔ &8Ξ &#4EFA76Rozdano klucze &f{KEY}&a!");
        if (!finishedMessage.isBlank()) {
            Bukkit.broadcast(ColorUtils.colorizeComponent(finishedMessage.replace("{KEY}", keyTimer.displayName())));
        }
    }

    private void executeCommands(KeyTimer keyTimer) {
        for (String configuredCommand : keyTimer.commands()) {
            if (configuredCommand == null || configuredCommand.isBlank()) {
                continue;
            }

            if (configuredCommand.contains("{PLAYER}")) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    dispatchConfiguredCommand(configuredCommand, keyTimer, player);
                }
                continue;
            }

            dispatchConfiguredCommand(configuredCommand, keyTimer, null);
        }
    }

    private void dispatchConfiguredCommand(String configuredCommand, KeyTimer keyTimer, @Nullable Player player) {
        String command = configuredCommand
            .replace("{KEY}", keyTimer.configKey())
            .replace("{KEY_DISPLAY}", keyTimer.displayName())
            .replace("{ONLINE}", String.valueOf(Bukkit.getOnlinePlayers().size()));

        if (player != null) {
            command = command
                .replace("{PLAYER}", player.getName())
                .replace("{UUID}", player.getUniqueId().toString());
        }

        while (command.startsWith("/")) {
            command = command.substring(1);
        }
        if (!command.isBlank()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        }
    }

    private KeyTimer loadKeyTimer(String requestedKey) {
        ConfigurationSection keys = config.getConfigurationSection("keys");
        if (keys == null) {
            return null;
        }

        for (String rawKey : keys.getKeys(false)) {
            String normalized = normalizeKey(rawKey);
            if (!normalized.equals(requestedKey)) {
                continue;
            }

            String path = "keys." + rawKey + ".";
            String displayName = config.getString(path + "display-name", rawKey);
            int seconds = Math.max(1, config.getInt(path + "time", 60));
            List<String> commands = config.getStringList(path + "commands");
            boolean enabled = config.getBoolean(path + "enabled", true);
            return new KeyTimer(rawKey, displayName, seconds, commands, enabled);
        }
        return null;
    }

    private String renderBossBarTitle(String keyDisplay, int remaining, int total) {
        return ColorUtils.colorize(message("bossbar.title", "&e&lKLUCZE &8» &7{KEY} &8| &c{TIME}")
            .replace("{KEY}", keyDisplay)
            .replace("{TIME}", formatDuration(remaining))
            .replace("{SECONDS}", String.valueOf(remaining))
            .replace("{TOTAL}", formatDuration(total))
            .replace("{TOTAL_SECONDS}", String.valueOf(total)));
    }

    private void playCountdownSound(int remaining) {
        if (!config.getBoolean("countdown-sound.enabled", true)) {
            return;
        }

        int fromSeconds = Math.max(1, config.getInt("countdown-sound.from-seconds", 10));
        if (remaining > fromSeconds) {
            return;
        }

        Sound sound = parseSound(config.getString("countdown-sound.sound", "BLOCK_NOTE_BLOCK_PLING"));
        float volume = (float) config.getDouble("countdown-sound.volume", 1.0D);
        float pitch = (float) config.getDouble("countdown-sound.pitch", 1.0D);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), sound, volume, pitch);
        }
    }

    private Sound parseSound(String value) {
        try {
            return Sound.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException exception) {
            return Sound.BLOCK_NOTE_BLOCK_PLING;
        }
    }

    private String formatDuration(int totalSeconds) {
        int seconds = Math.max(0, totalSeconds);
        int hours = seconds / 3600;
        seconds %= 3600;
        int minutes = seconds / 60;
        seconds %= 60;

        StringBuilder builder = new StringBuilder();
        if (hours > 0) {
            builder.append(hours).append("h");
        }
        if (minutes > 0) {
            builder.append(minutes).append("m");
        }
        if (seconds > 0 || builder.isEmpty()) {
            builder.append(seconds).append("s");
        }
        return builder.toString();
    }

    private void sendUsage(CommandSender sender) {
        ColorUtils.sendUsage(sender,
            "/timer key <maly|sredni|duzy|ogromny> [czas]",
            "/timer cancel",
            "/timer reload"
        );
    }

    private String message(String path, String fallback) {
        return config.getString(path, fallback);
    }

    private int parseDurationSeconds(String input) {
        if (input == null || input.isBlank()) {
            return -1;
        }

        String normalized = input.trim().toLowerCase(Locale.ROOT);
        if (normalized.matches("\\d+")) {
            return parsePositiveInt(normalized);
        }

        Matcher matcher = DURATION_PART.matcher(normalized);
        int total = 0;
        int matchedUntil = 0;
        while (matcher.find()) {
            if (matcher.start() != matchedUntil) {
                return -1;
            }
            int value = parsePositiveInt(matcher.group(1));
            if (value <= 0) {
                return -1;
            }
            total += switch (matcher.group(2)) {
                case "m" -> value * 60;
                case "h" -> value * 3600;
                case "d" -> value * 86400;
                default -> value;
            };
            matchedUntil = matcher.end();
        }
        return matchedUntil == normalized.length() ? total : -1;
    }

    private int parsePositiveInt(String input) {
        try {
            int value = Integer.parseInt(input);
            return value > 0 ? value : -1;
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private String normalizeKey(String input) {
        String value = input == null ? "" : input.toLowerCase(Locale.ROOT).trim();
        value = value.replace("ł", "l").replace("Ł", "l");
        value = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return value.replaceAll("[^a-z0-9_-]", "");
    }

    private BarColor parseBarColor(String value) {
        try {
            return BarColor.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException exception) {
            return BarColor.YELLOW;
        }
    }

    private BarStyle parseBarStyle(String value) {
        try {
            return BarStyle.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException exception) {
            return BarStyle.SOLID;
        }
    }

    private void appendMissingDefaults() {
        try (InputStream inputStream = plugin.getResource(RESOURCE_PATH)) {
            if (inputStream == null) {
                return;
            }

            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8)
            );
            boolean changed = false;
            for (String path : defaults.getKeys(true)) {
                if (defaults.isConfigurationSection(path) || config.contains(path)) {
                    continue;
                }
                config.set(path, defaults.get(path));
                changed = true;
            }

            if (changed) {
                config.save(configFile);
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("Nie udalo sie uzupelnic timer.yml: " + exception.getMessage());
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        for (ActiveTimer activeTimer : activeTimers.values()) {
            activeTimer.bossBar().addPlayer(event.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        for (ActiveTimer activeTimer : activeTimers.values()) {
            activeTimer.bossBar().removePlayer(event.getPlayer());
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (!sender.hasPermission("Holly.cmd.timer")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return filter(List.of("key", "cancel", "reload"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("key")) {
            return filter(new ArrayList<>(configuredKeys().keySet()), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("key")) {
            return filter(List.of("30s", "1m", "5m", "10m"), args[2]);
        }
        return Collections.emptyList();
    }

    private Map<String, String> configuredKeys() {
        Map<String, String> keys = new LinkedHashMap<>();
        ConfigurationSection section = config.getConfigurationSection("keys");
        if (section == null) {
            return keys;
        }
        for (String rawKey : section.getKeys(false)) {
            keys.put(rawKey, normalizeKey(rawKey));
        }
        return keys;
    }

    private List<String> filter(List<String> suggestions, String current) {
        String lowered = current == null ? "" : current.toLowerCase(Locale.ROOT);
        return suggestions.stream()
            .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lowered))
            .toList();
    }

    private record KeyTimer(String configKey, String displayName, int seconds, List<String> commands, boolean enabled) {}

    private record ActiveTimer(BossBar bossBar, BukkitTask task) {}
}
