package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.HollyDataPaths;
import dev.grzybalson.utils.ChatInputSanitizer;
import dev.grzybalson.utils.ColorUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HelpopCommand implements CommandExecutor, TabCompleter {

    private static final String RESOURCE_PATH = "addons/commands/helpop.yml";
    private static final String COOLDOWN_KEY = "helpop";

    private final HollyCore plugin;
    private final File configFile;
    private YamlConfiguration config;

    public HelpopCommand(HollyCore plugin) {
        this.plugin = plugin;
        this.configFile = new File(HollyDataPaths.getAddonFolder(plugin, "commands"), "helpop.yml");
        reloadConfig();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload") && sender.hasPermission(getPermission("permissions.reload", "holly.cmd.helpop.reload"))) {
            reloadConfig();
            sender.sendMessage(ColorUtils.colorizeComponent(getString("messages.reload", "&8[&#32D583&lHELPOP&8] &aPrzeladowano helpop.yml.")));
            return true;
        }

        String sendPermission = getPermission("permissions.send", "holly.cmd.helpop");
        if (!sender.hasPermission(sendPermission)) {
            sender.sendMessage(ColorUtils.noPermission(label, sendPermission));
            return true;
        }

        if (args.length == 0) {
            ColorUtils.sendUsage(sender, "/" + label + " <wiadomosc>");
            return true;
        }

        if (sender instanceof Player player && isCoolingDown(player)) {
            int remaining = plugin.getCooldownManager().getRemainingCooldown(player.getUniqueId(), COOLDOWN_KEY);
            player.sendMessage(ColorUtils.colorizeComponent(getString("messages.cooldown", "&8[&#FF3D3D&lHELPOP&8] &cOdczekaj jeszcze &#FFB347{time}s &cprzed kolejnym zgloszeniem.")
                .replace("{time}", String.valueOf(remaining))));
            return true;
        }

        String message = String.join(" ", args);
        Player playerSender = sender instanceof Player player ? player : null;
        if (playerSender != null && ChatInputSanitizer.rejectIfBlocked(playerSender, message)) {
            return true;
        }
        String senderName = playerSender != null ? playerSender.getName() : getString("console-name", "Konsola");
        Map<String, String> placeholders = createPlaceholders(playerSender, senderName);
        List<Component> alert = createAlert(placeholders, message);

        int receivers = 0;
        String viewPermission = getPermission("permissions.view", "holly.cmd.helpop.view");
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (!onlinePlayer.hasPermission(viewPermission)) {
                continue;
            }

            for (Component line : alert) {
                onlinePlayer.sendMessage(line);
            }
            playSound(onlinePlayer);
            receivers++;
        }

        if (playerSender != null && getCooldownSeconds() > 0) {
            plugin.getCooldownManager().setCooldown(playerSender.getUniqueId(), COOLDOWN_KEY, getCooldownSeconds());
        }

        String resultPath = receivers == 0 ? "messages.sent-no-admins" : "messages.sent";
        String resultDefault = receivers == 0
            ? "&8[&#FF3D3D&lHELPOP&8] &cWyslano zgloszenie, ale zaden administrator nie jest online."
            : "&8[&#32D583&lHELPOP&8] &aWyslano zgloszenie do administracji.";
        sender.sendMessage(ColorUtils.colorizeComponent(getString(resultPath, resultDefault)));
        return true;
    }

    private boolean isCoolingDown(Player player) {
        if (getCooldownSeconds() <= 0) {
            return false;
        }
        String bypassPermission = getPermission("permissions.bypass-cooldown", "holly.cmd.helpop.bypass");
        return !player.hasPermission(bypassPermission) && plugin.getCooldownManager().isOnCooldown(player.getUniqueId(), COOLDOWN_KEY);
    }

    private List<Component> createAlert(Map<String, String> placeholders, String message) {
        List<String> rawLines = config.getStringList("message-lines");
        if (rawLines.isEmpty()) {
            rawLines = List.of(
                "&8&m--------------------------------------------------",
                "&#FF3D3D&lHELPOP &8| &7Nowe zgloszenie od &f{player}",
                "&8> &7Lokalizacja: &f{location}",
                "&8> &7Wiadomosc: &f{message}",
                "&8&m--------------------------------------------------"
            );
        }

        List<Component> lines = new ArrayList<>();
        for (String rawLine : rawLines) {
            lines.add(formatComponent(rawLine, placeholders, message));
        }
        return lines;
    }

    private Component formatComponent(String rawLine, Map<String, String> placeholders, String message) {
        String line = rawLine == null ? "" : rawLine;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            line = line.replace("{" + entry.getKey() + "}", entry.getValue());
        }

        if (!line.contains("{message}")) {
            return ColorUtils.colorizeComponent(line);
        }

        String[] parts = line.split("\\{message}", -1);
        Component component = Component.empty();
        for (int i = 0; i < parts.length; i++) {
            component = component.append(ColorUtils.colorizeComponent(parts[i]));
            if (i < parts.length - 1) {
                component = component.append(Component.text(message, NamedTextColor.WHITE));
            }
        }
        return component;
    }

    private Map<String, String> createPlaceholders(Player player, String senderName) {
        if (player == null) {
            String consoleLocation = getString("console-location", "Konsola");
            return Map.of(
                "player", senderName,
                "location", consoleLocation,
                "world", consoleLocation,
                "x", "-",
                "y", "-",
                "z", "-"
            );
        }

        String world = player.getWorld().getName();
        String x = String.valueOf(player.getLocation().getBlockX());
        String y = String.valueOf(player.getLocation().getBlockY());
        String z = String.valueOf(player.getLocation().getBlockZ());
        return Map.of(
            "player", senderName,
            "location", world + " (" + x + ", " + y + ", " + z + ")",
            "world", world,
            "x", x,
            "y", y,
            "z", z
        );
    }

    private void playSound(Player player) {
        if (!config.getBoolean("sound.enabled", true)) {
            return;
        }

        Sound sound;
        try {
            sound = Sound.valueOf(getString("sound.name", "BLOCK_NOTE_BLOCK_PLING").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            sound = Sound.BLOCK_NOTE_BLOCK_PLING;
        }

        float volume = (float) config.getDouble("sound.volume", 0.8D);
        float pitch = (float) config.getDouble("sound.pitch", 1.2D);
        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    private void reloadConfig() {
        HollyDataPaths.ensureResource(plugin, RESOURCE_PATH);
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    private int getCooldownSeconds() {
        return Math.max(0, config.getInt("cooldown-seconds", 10));
    }

    private String getPermission(String path, String defaultValue) {
        return getString(path, defaultValue);
    }

    private String getString(String path, String defaultValue) {
        return config == null ? defaultValue : config.getString(path, defaultValue);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1
            && sender.hasPermission(getPermission("permissions.reload", "holly.cmd.helpop.reload"))
            && "reload".startsWith(args[0].toLowerCase(Locale.ROOT))) {
            return List.of("reload");
        }
        return new ArrayList<>();
    }
}
