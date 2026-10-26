package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
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

public class PatronCommand implements CommandExecutor, TabCompleter {
    private final HollyCore plugin;

    public PatronCommand(HollyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (plugin.getPatronManager() == null) {
            sender.sendMessage(configuredMessage("addon-disabled", "&#FF0000✘ &8Ξ &cAddon patroni jest wylaczony."));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("random")) {
            if (!hasAdminPermission(sender)) return true;
            if (args.length > 1 && args[1].equalsIgnoreCase("stop")) {
                plugin.getPatronManager().stopGodHour();
                sender.sendMessage(plugin.getPatronManager().formatConfiguredMessage("god-hour-stopped", null, null));
                return true;
            }
            plugin.getPatronManager().startRandomGodHour();
            sender.sendMessage(plugin.getPatronManager().formatConfiguredMessage("god-hour-random-started", null, null));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!hasAdminPermission(sender)) return true;
            plugin.getPatronManager().reload();
            sender.sendMessage(plugin.getPatronManager().formatConfiguredMessage("reload", null, null));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("setpatron")) {
            if (!hasAdminPermission(sender)) return true;
            if (args.length < 3) {
                ColorUtils.sendUsage(sender, "/patron setpatron <patron> <nick>");
                return true;
            }
            if (plugin.getPatronManager().parse(args[1]) == null) {
                sender.sendMessage(plugin.getPatronManager().formatConfiguredMessage("unknown-patron", null, args[1]));
                return true;
            }

            OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
            if (!target.hasPlayedBefore() && !target.isOnline()) {
                sender.sendMessage(plugin.getPatronManager().formatConfiguredMessage("player-not-found", args[2], args[1]));
                return true;
            }

            String targetName = target.getName() == null ? args[2] : target.getName();
            if (!plugin.getPatronManager().setPlayerPatron(target.getUniqueId(), args[1])) {
                sender.sendMessage(plugin.getPatronManager().formatConfiguredMessage("unknown-patron", targetName, args[1]));
                return true;
            }

            sender.sendMessage(plugin.getPatronManager().formatConfiguredMessage("set-patron-success", targetName, args[1]));
            Player onlineTarget = target.getPlayer();
            if (onlineTarget != null && onlineTarget.isOnline()) {
                onlineTarget.sendMessage(plugin.getPatronManager().formatConfiguredMessage("set-patron-target", targetName, args[1]));
            }
            return true;
        }

        if (args.length > 1 && args[0].equalsIgnoreCase("sethead")) {
            if (!hasAdminPermission(sender)) return true;
            if (!(sender instanceof Player player)) {
                sender.sendMessage(plugin.getPatronManager().formatConfiguredMessage("player-only", null, null));
                return true;
            }
            if (plugin.getPatronManager().setPatronHead(args[1], player.getInventory().getItemInMainHand())) {
                sender.sendMessage(plugin.getPatronManager().formatConfiguredMessage("set-head-success", null, args[1]));
            } else {
                sender.sendMessage(plugin.getPatronManager().formatConfiguredMessage("set-head-fail", null, args[1]));
            }
            return true;
        }

        if (args.length > 1 && args[0].equalsIgnoreCase("set")) {
            if (!hasAdminPermission(sender)) return true;
            if (!plugin.getPatronManager().startGodHour(args[1])) {
                sender.sendMessage(plugin.getPatronManager().formatConfiguredMessage("unknown-patron", null, args[1]));
            } else {
                sender.sendMessage(plugin.getPatronManager().formatConfiguredMessage("god-hour-set", null, args[1]));
            }
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getPatronManager().formatConfiguredMessage("player-only", null, null));
            return true;
        }
        if (!player.hasPermission("Holly.cmd.patron")) {
            player.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.patron"));
            return true;
        }

        plugin.getPatronManager().openGui(player);
        return true;
    }

    private boolean hasAdminPermission(CommandSender sender) {
        if (sender.hasPermission("Holly.cmd.patron.admin")) return true;
        sender.sendMessage(ColorUtils.noPermission("/patron", "Holly.cmd.patron.admin"));
        return false;
    }

    private String configuredMessage(String key, String fallback) {
        if (plugin.getPatronManager() != null) {
            return plugin.getPatronManager().formatConfiguredMessage(key, null, null);
        }

        File configFile = new File(plugin.getDataFolder(), "addons/patroni/patroni.yml");
        if (!configFile.exists()) return ColorUtils.colorize(fallback);
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        return ColorUtils.colorize(config.getString("messages." + key, fallback));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("Holly.cmd.patron.admin")) return out;

        if (args.length == 1) {
            for (String value : List.of("random", "set", "setpatron", "sethead", "reload")) {
                if (value.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(value);
            }
            return out;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("random")) {
            if ("stop".startsWith(args[1].toLowerCase(Locale.ROOT))) out.add("stop");
            return out;
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("setpatron") || args[0].equalsIgnoreCase("sethead"))) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            for (String patron : plugin.getPatronManager().patronKeys()) {
                if (patron.startsWith(prefix)) out.add(patron);
            }
            return out;
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("setpatron")) {
            String prefix = args[2].toLowerCase(Locale.ROOT);
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) out.add(player.getName());
            }
        }

        return out;
    }
}
