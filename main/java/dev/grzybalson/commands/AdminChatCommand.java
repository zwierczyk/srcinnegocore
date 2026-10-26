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

public class AdminChatCommand implements CommandExecutor, TabCompleter {

    private static final String RESOURCE_PATH = "addons/commands/adminchat.yml";

    private final HollyCore plugin;
    private final File configFile;
    private YamlConfiguration config;

    public AdminChatCommand(HollyCore plugin) {
        this.plugin = plugin;
        this.configFile = new File(HollyDataPaths.getAddonFolder(plugin, "commands"), "adminchat.yml");
        reloadConfig();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload") && sender.hasPermission(getPermission("permissions.reload", "holly.cmd.adminchat.reload"))) {
            reloadConfig();
            sender.sendMessage(ColorUtils.colorizeComponent(getString("messages.reload", "&8[&#FF7A18&lADMINCHAT&8] &aPrzeladowano adminchat.yml.")));
            return true;
        }

        String usePermission = getPermission("permissions.use", "holly.cmd.adminchat");
        if (!sender.hasPermission(usePermission)) {
            sender.sendMessage(ColorUtils.noPermission(label, usePermission));
            return true;
        }

        if (args.length == 0) {
            ColorUtils.sendUsage(sender, "/" + label + " <wiadomosc>");
            return true;
        }

        String senderName = sender instanceof Player player ? player.getName() : getString("console-name", "Konsola");
        String message = String.join(" ", args);
        if (sender instanceof Player player && ChatInputSanitizer.rejectIfBlocked(player, message)) {
            return true;
        }
        Component formattedMessage = formatComponent(getString("format", "&8[&#FF7A18&lADMINCHAT&8] &#FFD166{player} &8> &f{message}"),
            Map.of("player", senderName),
            message
        );

        int receivers = 0;
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (!onlinePlayer.hasPermission(usePermission)) {
                continue;
            }

            onlinePlayer.sendMessage(formattedMessage);
            playSound(onlinePlayer);
            receivers++;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(formattedMessage);
        } else if (receivers == 0) {
            sender.sendMessage(ColorUtils.colorizeComponent(getString("messages.no-admins-online", "&8[&#FF7A18&lADMINCHAT&8] &cNie ma nikogo online na admin chacie.")));
        }

        return true;
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

    private void playSound(Player player) {
        if (!config.getBoolean("sound.enabled", false)) {
            return;
        }

        Sound sound;
        try {
            sound = Sound.valueOf(getString("sound.name", "UI_BUTTON_CLICK").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            sound = Sound.UI_BUTTON_CLICK;
        }

        float volume = (float) config.getDouble("sound.volume", 0.4D);
        float pitch = (float) config.getDouble("sound.pitch", 1.4D);
        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    private void reloadConfig() {
        HollyDataPaths.ensureResource(plugin, RESOURCE_PATH);
        config = YamlConfiguration.loadConfiguration(configFile);
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
            && sender.hasPermission(getPermission("permissions.reload", "holly.cmd.adminchat.reload"))
            && "reload".startsWith(args[0].toLowerCase(Locale.ROOT))) {
            return List.of("reload");
        }
        return new ArrayList<>();
    }
}
