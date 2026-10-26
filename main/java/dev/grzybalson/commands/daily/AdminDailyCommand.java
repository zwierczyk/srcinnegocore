package dev.grzybalson.commands.daily;

import dev.grzybalson.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class AdminDailyCommand implements CommandExecutor, TabCompleter {

    private final DailyRewardManager manager;

    public AdminDailyCommand(DailyRewardManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.colorizeComponent(manager.getMessage("only-player")));
            return true;
        }

        if (!DailyRewardCommand.hasDailyAdminPermission(player)) {
            player.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.daily.admin"));
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            manager.reload();
            player.sendMessage(ColorUtils.colorizeComponent(manager.getMessage("reloaded")));
            return true;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("edit")) {
            try {
                int day = Integer.parseInt(args[1]);
                if (day <= 0) {
                    throw new NumberFormatException();
                }
                player.openInventory(manager.createEditorGui(day));
                return true;
            } catch (NumberFormatException exception) {
                player.sendMessage(ColorUtils.colorizeComponent(manager.getMessage("invalid-day")));
                return true;
            }
        }

        ColorUtils.sendUsage(player,
            "/adaily edit <dzień>",
            "/adaily reload"
        );
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();
        if (args.length == 1) {
            if ("edit".startsWith(args[0].toLowerCase())) suggestions.add("edit");
            if ("reload".startsWith(args[0].toLowerCase())) suggestions.add("reload");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("edit")) {
            for (int day : manager.getConfiguredDays()) {
                suggestions.add(String.valueOf(day));
            }
        }
        return suggestions;
    }
}
