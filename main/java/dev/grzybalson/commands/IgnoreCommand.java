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

public class IgnoreCommand implements CommandExecutor, TabCompleter {

    private final HollyCore plugin;

    public IgnoreCommand(HollyCore plugin) {
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

        if (!player.hasPermission("Holly.cmd.ignore")) {
            player.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.ignore"));
            return true;
        }

        if (args.length < 1) {
            ColorUtils.sendUsage(player, "/ignore <nick|*>");
            return true;
        }

        String targetArg = args[0];

        if (targetArg.equals("*")) {
            boolean nowIgnoring = plugin.getIgnoreManager().toggleIgnoreAll(player.getUniqueId());
            if (nowIgnoring) {
                player.sendMessage(ColorUtils.colorizeComponent(
                    plugin.getConfigManager().getIgnoreConfig().getIgnoredAll()
                ));
            } else {
                player.sendMessage(ColorUtils.colorizeComponent(
                    plugin.getConfigManager().getIgnoreConfig().getUnignoredAll()
                ));
            }
            return true;
        }

        Player target = Bukkit.getPlayerExact(targetArg);
        java.util.UUID targetId;
        String targetName;
        if (target != null) {
            targetId = target.getUniqueId();
            targetName = target.getName();
        } else {
            org.bukkit.OfflinePlayer offline = Bukkit.getOfflinePlayer(targetArg);
            if (offline.getUniqueId() == null || (!offline.hasPlayedBefore() && !offline.isOnline())) {
                player.sendMessage(ColorUtils.colorizeComponent(
                    plugin.getConfigManager().getMessagesConfig().getPlayerNotFound()
                ));
                return true;
            }
            targetId = offline.getUniqueId();
            targetName = offline.getName() != null ? offline.getName() : targetArg;
        }

        if (targetId.equals(player.getUniqueId())) {
            player.sendMessage(ColorUtils.error("Nie możesz zignorować siebie!"));
            return true;
        }

        boolean nowIgnored = plugin.getIgnoreManager().toggleIgnore(player.getUniqueId(), targetId);
        if (nowIgnored) {
            player.sendMessage(ColorUtils.colorizeComponent(
                plugin.getConfigManager().getIgnoreConfig().getYouIgnored()
                    .replace("{player}", targetName)
            ));
        } else {
            player.sendMessage(ColorUtils.colorizeComponent(
                plugin.getConfigManager().getIgnoreConfig().getYouUnignored()
                    .replace("{player}", targetName)
            ));
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            if ("*".startsWith(partial)) {
                completions.add("*");
            }
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(partial) && !player.equals(sender)) {
                    completions.add(player.getName());
                }
            }
        }
        return completions;
    }
}
