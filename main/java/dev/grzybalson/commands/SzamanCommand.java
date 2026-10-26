package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
import dev.grzybalson.modules.szaman.PerkType;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.LockedFeatureMessages;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SzamanCommand implements CommandExecutor, TabCompleter {

    private final HollyCore plugin;

    public SzamanCommand(HollyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("Holly.cmd.szaman.admin")) {
                sender.sendMessage(ColorUtils.noPermission(label + " reload", "Holly.cmd.szaman.admin"));
                return true;
            }
            plugin.getSzamanManager().reload();
            sender.sendMessage(ColorUtils.success("Przeladowano szamana."));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("setshard")) {
            if (!sender.hasPermission("Holly.cmd.szaman.admin")) {
                sender.sendMessage(ColorUtils.noPermission(label + " setshard", "Holly.cmd.szaman.admin"));
                return true;
            }
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ColorUtils.error("Ta komenda jest tylko dla gracza."));
                return true;
            }
            plugin.getSzamanManager().setShardItem(player);
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("setperk")) {
            if (!sender.hasPermission("Holly.cmd.szaman.admin")) {
                sender.sendMessage(ColorUtils.noPermission(label + " setperk", "Holly.cmd.szaman.admin"));
                return true;
            }
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ColorUtils.error("Ta komenda jest tylko dla gracza."));
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(ColorUtils.usage("/szaman setperk <typ>"));
                return true;
            }

            PerkType type = plugin.getSzamanManager().resolvePerkType(args[1]);
            if (type == null) {
                sender.sendMessage(ColorUtils.error("Nieprawidlowy typ perku."));
                return true;
            }

            plugin.getSzamanManager().setPerkItem(player, type);
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("giveall")) {
            if (!sender.hasPermission("Holly.cmd.szaman.admin")) {
                sender.sendMessage(ColorUtils.noPermission(label + " giveall", "Holly.cmd.szaman.admin"));
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(ColorUtils.usage("/szaman giveall <nick>"));
                return true;
            }

            OfflinePlayer target = resolveOfflinePlayer(args[1]);
            if (target == null) {
                sender.sendMessage(ColorUtils.error("Nie znaleziono gracza."));
                return true;
            }

            plugin.getSzamanManager().giveAllPerks(target.getUniqueId()).thenRun(() ->
                Bukkit.getScheduler().runTask(plugin, () ->
                    sender.sendMessage(ColorUtils.success("Nadano wszystkie perki szamana graczowi " + target.getName() + "."))));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("reset")) {
            if (!sender.hasPermission("Holly.cmd.szaman.admin")) {
                sender.sendMessage(ColorUtils.noPermission(label + " reset", "Holly.cmd.szaman.admin"));
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(ColorUtils.usage("/szaman reset <nick>"));
                return true;
            }

            OfflinePlayer target = resolveOfflinePlayer(args[1]);
            if (target == null) {
                sender.sendMessage(ColorUtils.error("Nie znaleziono gracza."));
                return true;
            }

            plugin.getSzamanManager().resetPerks(target.getUniqueId()).thenRun(() ->
                Bukkit.getScheduler().runTask(plugin, () ->
                    sender.sendMessage(ColorUtils.success("Zresetowano perki szamana gracza " + target.getName() + "."))));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("giveshard")) {
            if (!sender.hasPermission("Holly.cmd.szaman.admin")) {
                sender.sendMessage(ColorUtils.noPermission(label + " giveshard", "Holly.cmd.szaman.admin"));
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage(ColorUtils.usage("/szaman giveshard <nick> <ilosc>"));
                return true;
            }

            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(ColorUtils.error("Ten gracz musi byc online."));
                return true;
            }

            int amount = parsePositiveInt(args[2], 1);
            plugin.getSzamanManager().giveShard(target, amount);
            sender.sendMessage(ColorUtils.success("Nadano " + amount + " odlamkow graczowi " + target.getName() + "."));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.error("Ta komenda jest tylko dla gracza."));
            return true;
        }

        if (!player.hasPermission("Holly.cmd.szaman")) {
            LockedFeatureMessages.send(player, "/szaman");
            return true;
        }

        plugin.getSzamanManager().openGui(player);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("Holly.cmd.szaman.admin")) {
            return List.of();
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("setperk")) {
            return matching(plugin.getSzamanManager().getPerkTypeNames(), args[1]);
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("giveall")
            || args[0].equalsIgnoreCase("reset")
            || args[0].equalsIgnoreCase("giveshard"))) {
            return matching(Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .toList(), args[1]);
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("giveshard")) {
            return matching(List.of("1", "16", "32", "64"), args[2]);
        }

        if (args.length != 1) {
            return List.of();
        }

        return matching(List.of("reload", "setshard", "setperk", "giveall", "reset", "giveshard"), args[0]);
    }

    private List<String> matching(List<String> options, String input) {
        List<String> completions = new ArrayList<>();
        String lowerInput = input.toLowerCase(Locale.ROOT);
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lowerInput)) {
                completions.add(option);
            }
        }
        return completions;
    }

    private OfflinePlayer resolveOfflinePlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }

        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        return offline.hasPlayedBefore() ? offline : null;
    }

    private int parsePositiveInt(String raw, int fallback) {
        try {
            return Math.max(1, Integer.parseInt(raw));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
