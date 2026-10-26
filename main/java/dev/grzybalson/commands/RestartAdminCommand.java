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
import java.util.Arrays;
import java.util.List;

public class RestartAdminCommand implements CommandExecutor, TabCompleter {

    private final HollyCore plugin;

    public RestartAdminCommand(HollyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("Holly.cmd.restartadmin")) {
            sender.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.restartadmin"));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(ColorUtils.usage("/restartadmin <start|cancel> [czas]"));
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "start" -> {
                if (args.length < 2) {
                sender.sendMessage(ColorUtils.usage("/restartadmin start <czas w sekundach>"));
                    return true;
                }

                int time;
                try {
                    time = Integer.parseInt(args[1]);
                    if (time <= 0) {
                        sender.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cCzas musi być większy od 0!"));
                        return true;
                    }
                } catch (NumberFormatException e) {
                    sender.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cNieprawidłowy format czasu!"));
                    return true;
                }

                plugin.getRestartManager().startRestart(time);
                sender.sendMessage(ColorUtils.colorizeComponent("&#00FF3B✔ &8Ξ &#4EFA76Rozpoczęto odliczanie do restartu!"));
            }
            case "cancel" -> {
                if (!plugin.getRestartManager().isRestarting()) {
                    sender.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cŻaden restart nie jest w toku!"));
                    return true;
                }

                plugin.getRestartManager().cancelRestart();
                sender.sendMessage(ColorUtils.colorizeComponent("&#00FF3B✔ &8Ξ &#4EFA76Restart został anulowany!"));
            }
            default -> sender.sendMessage(ColorUtils.usage("/restartadmin <start|cancel> [czas]"));
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("Holly.cmd.restartadmin")) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            return Arrays.asList("start", "cancel");
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("start")) {
            return Arrays.asList("60", "120", "300", "600");
        }

        return new ArrayList<>();
    }
}
