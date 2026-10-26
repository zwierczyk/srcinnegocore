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

public class AutoCommandsCommand implements CommandExecutor, TabCompleter {

    private final HollyCore plugin;

    public AutoCommandsCommand(HollyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("Holly.cmd.autocommands") && !sender.hasPermission("core.admin")) {
            sender.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.autocommands"));
            return true;
        }

        if (args.length != 1 || !args[0].equalsIgnoreCase("reload")) {
            ColorUtils.sendUsage(sender, "/autocommands reload");
            return true;
        }

        plugin.getAutoCommandsManager().reload();
        sender.sendMessage(ColorUtils.success("Przeładowano AutoCommands i wyczyszczono stan wykonanych minut."));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1 && "reload".startsWith(args[0].toLowerCase())) {
            return List.of("reload");
        }
        return new ArrayList<>();
    }
}
