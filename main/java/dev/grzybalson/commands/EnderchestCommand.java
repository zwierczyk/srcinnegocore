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

public class EnderchestCommand implements CommandExecutor, TabCompleter {

    private final HollyCore plugin;

    public EnderchestCommand(HollyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("Holly.cmd.ec.reload")) {
                sender.sendMessage(ColorUtils.noPermission(label + " reload", "Holly.cmd.ec.reload"));
                return true;
            }
            plugin.getEnderChestManager().reload();
            sender.sendMessage(ColorUtils.success("Przeładowano konfigurację enderchestów!"));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.colorizeComponent(
                plugin.getConfigManager().getMessagesConfig().getPlayerOnly()
            ));
            return true;
        }

        if (!player.hasPermission("Holly.cmd.ec")) {
            player.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.ec"));
            return true;
        }

        Player target = player;
        if (args.length > 0 && player.hasPermission("Holly.cmd.ec.others")) {
            target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                player.sendMessage(ColorUtils.colorizeComponent(
                    plugin.getConfigManager().getMessagesConfig().getPlayerNotFound()
                ));
                return true;
            }
        }

        plugin.getEnderChestManager().openEnderChestFor(player, target);

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            if (sender.hasPermission("Holly.cmd.ec.reload") && "reload".startsWith(partial)) {
                completions.add("reload");
            }
            if (sender.hasPermission("Holly.cmd.ec.others")) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getName().toLowerCase().startsWith(partial)) {
                        completions.add(player.getName());
                    }
                }
            }
        }
        return completions;
    }
}
