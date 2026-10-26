package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.HollyDataPaths;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class NadajMediaCommand implements CommandExecutor, TabCompleter {

    private static final String CONFIG_PATH = "addons/media/media.yml";
    private static final List<String> RANKS = List.of("minimedia", "media", "tworca");

    private final HollyCore plugin;

    public NadajMediaCommand(HollyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("Holly.cmd.nadajmedia") && !sender.isOp()) {
            sender.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.nadajmedia"));
            return true;
        }

        YamlConfiguration config = loadConfig();
        if (args.length < 2) {
            ColorUtils.sendUsage(sender, "/" + label + " <minimedia|media|tworca> <nick>");
            return true;
        }

        String rankKey = args[0].toLowerCase(Locale.ROOT);
        ConfigurationSection rankSection = config.getConfigurationSection("grant.ranks." + rankKey);
        if (rankSection == null) {
            sender.sendMessage(ColorUtils.colorizeComponent(
                config.getString("grant.unknown-rank", "&#FF0000✘ &8Ξ &cNieznana ranga.")
            ));
            return true;
        }

        String playerName = args[1];
        OfflinePlayer target = resolvePlayer(playerName);
        if (target == null || target.getName() == null) {
            sender.sendMessage(ColorUtils.colorizeComponent(
                config.getString("grant.player-not-found", "&#FF0000✘ &8Ξ &cNie znaleziono gracza &4{PLAYER}&c.")
                    .replace("{PLAYER}", playerName)
            ));
            return true;
        }

        String group = rankSection.getString("group", rankKey);
        String prefix = rankSection.getString("prefix", "");
        String duration = config.getString("grant.duration", "7d");

        ConsoleCommandSender console = Bukkit.getConsoleSender();
        Bukkit.dispatchCommand(console, "lp user " + target.getName() + " parent addtemp " + group + " " + duration);

        String displayName = target.getName();
        String subtitleTemplate = config.getString("grant.subtitle", "&f{PLAYER} &7odebrał rangę {PREFIX}");
        if (subtitleTemplate == null || subtitleTemplate.isBlank()) {
            subtitleTemplate = "&f{PLAYER} &7odebrał rangę {PREFIX}";
        } else if (!hasPrefixPlaceholder(subtitleTemplate)) {
            subtitleTemplate = subtitleTemplate + " {PREFIX}";
        }
        String subtitle = applyPlaceholders(subtitleTemplate, displayName, prefix, rankKey);
        int fadeIn = config.getInt("grant.title-fade-in", 5);
        int stay = config.getInt("grant.title-stay", 60);
        int fadeOut = config.getInt("grant.title-fade-out", 10);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            // Sam subtitle — spacja w title, żeby Minecraft pokazał dolną linię.
            viewer.sendTitle(" ", ColorUtils.colorize(subtitle), fadeIn, stay, fadeOut);
        }

        sender.sendMessage(ColorUtils.colorizeComponent(
            config.getString("grant.success", "&#00FF3B✔ &8Ξ &#4EFA76Nadano &f{RANK} &ana 7 dni dla &f{PLAYER}&a.")
                .replace("{RANK}", rankKey)
                .replace("{PLAYER}", displayName)
        ));
        return true;
    }

    private static boolean hasPrefixPlaceholder(String text) {
        if (text == null) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("{prefix}") || lower.contains("%prefix%");
    }

    private static String applyPlaceholders(String text, String player, String prefix, String rank) {
        if (text == null) {
            return "";
        }
        String safePrefix = prefix == null ? "" : prefix;
        return text
            .replace("{PREFIX}", safePrefix)
            .replace("{prefix}", safePrefix)
            .replace("%prefix%", safePrefix)
            .replace("%player%", player)
            .replace("%Player%", player)
            .replace("{PLAYER}", player)
            .replace("{player}", player)
            .replace("{RANK}", rank)
            .replace("{rank}", rank)
            .replace("%rank%", rank);
    }

    @SuppressWarnings("deprecation")
    private OfflinePlayer resolvePlayer(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        return Bukkit.getOfflinePlayer(name);
    }

    private YamlConfiguration loadConfig() {
        HollyDataPaths.ensureResource(plugin, CONFIG_PATH);
        return YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), CONFIG_PATH));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("Holly.cmd.nadajmedia") && !sender.isOp()) {
            return List.of();
        }
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return RANKS.stream().filter(rank -> rank.startsWith(prefix)).collect(Collectors.toList());
        }
        if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    names.add(player.getName());
                }
            }
            return names;
        }
        return List.of();
    }
}
