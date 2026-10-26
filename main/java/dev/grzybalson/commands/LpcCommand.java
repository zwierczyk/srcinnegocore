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

public class LpcCommand implements CommandExecutor, TabCompleter {

    private final HollyCore plugin;

    public LpcCommand(HollyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!(sender instanceof org.bukkit.command.ConsoleCommandSender) && !sender.hasPermission("Holly.cmd.lpc.reload")) {
                sender.sendMessage(ColorUtils.noPermission(label + " reload", "Holly.cmd.lpc.reload"));
                return true;
            }
            plugin.getConfigManager().reloadLpcChatConfig();
            sender.sendMessage(ColorUtils.colorizeComponent(ColorUtils.SUCCESS_PREFIX + "Przeładowano lpc.yml."));
            return true;
        }

        ColorUtils.sendUsage(sender, "/lpc reload");
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            if (sender.hasPermission("Holly.cmd.lpc.reload") || sender instanceof org.bukkit.command.ConsoleCommandSender) {
                if ("reload".startsWith(args[0].toLowerCase())) {
                    completions.add("reload");
                }
            }
        }
        return completions;
    }
}
