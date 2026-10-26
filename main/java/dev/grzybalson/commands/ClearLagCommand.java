package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class ClearLagCommand implements CommandExecutor {

    private final HollyCore plugin;

    public ClearLagCommand(HollyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("Holly.cmd.clearlag")) {
            sender.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.clearlag"));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            plugin.getClearLagManager().reload();
            sender.sendMessage(ColorUtils.success("Przeładowano ClearLag!"));
            return true;
        }

        plugin.getClearLagManager().forceClear();
        return true;
    }
}
