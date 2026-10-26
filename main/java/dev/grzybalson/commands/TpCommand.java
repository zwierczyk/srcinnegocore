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

public class TpCommand implements CommandExecutor, TabCompleter {

    private final HollyCore plugin;

    public TpCommand(HollyCore plugin) {
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

        if (!player.hasPermission("Holly.cmd.tp")) {
            player.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.tp"));
            return true;
        }

        if (args.length < 1) {
            ColorUtils.sendUsage(player, "/tp <gracz> [gracz2]");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            player.sendMessage(ColorUtils.colorizeComponent(
                plugin.getConfigManager().getMessagesConfig().getPlayerNotFound()
            ));
            return true;
        }

        if (args.length == 1) {
            player.teleport(target.getLocation());
            player.sendMessage(ColorUtils.colorizeComponent(
                plugin.getConfigManager().getMessagesConfig().getTeleported()
            ));
        } else {
            Player target2 = Bukkit.getPlayer(args[1]);
            if (target2 == null) {
                player.sendMessage(ColorUtils.colorizeComponent(
                    plugin.getConfigManager().getMessagesConfig().getPlayerNotFound()
                ));
                return true;
            }
            target.teleport(target2.getLocation());
            player.sendMessage(ColorUtils.success("Teleportowano " + target.getName() + " do " + target2.getName() + "!"));
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1 || args.length == 2) {
            String partial = args[args.length - 1].toLowerCase();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(partial)) {
                    completions.add(player.getName());
                }
            }
        }
        return completions;
    }
}
