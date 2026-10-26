package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

public class DropKeysCommand implements CommandExecutor, TabCompleter {

    private final HollyCore plugin;

    public DropKeysCommand(HollyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("Holly.cmd.dropkeys")) {
            sender.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.dropkeys"));
            return true;
        }
        if (plugin.getDropKeysManager() == null) {
            sender.sendMessage(ColorUtils.error("Addon dropkeys jest wyłączony."));
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            plugin.getDropKeysManager().reload();
            sender.sendMessage(ColorUtils.success("Przeładowano konfigurację dropkeys."));
            return true;
        }
        sender.sendMessage(ColorUtils.usage("/" + label + " reload"));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("Holly.cmd.dropkeys")) {
            return List.of();
        }
        if (args.length == 1 && "reload".startsWith(args[0].toLowerCase(Locale.ROOT))) {
            return List.of("reload");
        }
        return List.of();
    }
}
