package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TitleCommand implements CommandExecutor, TabCompleter {

    private final HollyCore plugin;

    public TitleCommand(HollyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("Holly.cmd.title.reload")) {
                sender.sendMessage(ColorUtils.noPermission(label + " reload", "Holly.cmd.title.reload"));
                return true;
            }
            if (plugin.getTitleAddonManager() == null) {
                sender.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cAddon titles jest wyłączony w addons.yml."));
                return true;
            }
            plugin.getTitleAddonManager().reload();
            sender.sendMessage(ColorUtils.colorizeComponent("&#00FF3B✔ &8Ξ &#4EFA76Przeładowano addons/titles/titles.yml."));
            return true;
        }

            sender.sendMessage(ColorUtils.usage("/" + label + " reload"));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1 && "reload".startsWith(args[0].toLowerCase(Locale.ROOT))) {
            completions.add("reload");
        }
        return completions;
    }
}
