package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class EcseeCommand implements CommandExecutor, TabCompleter {

    private final HollyCore plugin;

    public EcseeCommand(HollyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.colorizeComponent(
                plugin.getConfigManager().getMessagesConfig().getPlayerOnly()
            ));
            return true;
        }

        if (!player.hasPermission("Holly.cmd.ecsee")) {
            player.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.ecsee"));
            return true;
        }

        if (args.length < 1 || args.length > 2) {
            ColorUtils.sendUsage(player, "/" + label + " <nick> [strona]");
            return true;
        }

        int page = 1;
        if (args.length == 2) {
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException exception) {
                ColorUtils.sendUsage(player, "/" + label + " <nick> [strona]");
                return true;
            }
            if (page < 1) {
                ColorUtils.sendUsage(player, "/" + label + " <nick> [strona]");
                return true;
            }
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target != null) {
            plugin.getEnderChestManager().openEnderChestPageFor(player, target, page);
            player.sendMessage(ColorUtils.success("Otwarto ender chest gracza &f" + target.getName() + " &7(strona " + page + ")&a."));
            return true;
        }

        plugin.getEnderChestManager().openOfflineEnderChestFor(player, args[0], page);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (!sender.hasPermission("Holly.cmd.ecsee")) {
            return completions;
        }

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(partial)) {
                    completions.add(player.getName());
                }
            }
            return completions;
        }

        if (args.length == 2) {
            for (String page : List.of("1", "2", "3", "4", "5")) {
                if (page.startsWith(args[1])) {
                    completions.add(page);
                }
            }
        }
        return completions;
    }
}
