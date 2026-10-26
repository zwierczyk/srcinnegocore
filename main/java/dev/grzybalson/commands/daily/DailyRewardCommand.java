package dev.grzybalson.commands.daily;

import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class DailyRewardCommand implements CommandExecutor, TabCompleter {

    private final DailyRewardManager manager;

    public DailyRewardCommand(DailyRewardManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.colorizeComponent(manager.getMessage("only-player")));
            return true;
        }

        if (!hasDailyUsePermission(player)) {
            player.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.daily"));
            return true;
        }

        manager.loadPlayer(player.getUniqueId(), true).thenAccept(ignored ->
            Bukkit.getScheduler().runTask(manager.getPlugin(), () -> {
                if (player.isOnline()) {
                    player.openInventory(manager.createMainGui(player));
                }
            })
        );
        return true;
    }

    static boolean hasDailyUsePermission(org.bukkit.entity.Player player) {
        return player.hasPermission("Holly.cmd.daily") || player.hasPermission("Holly.daily.use");
    }

    static boolean hasDailyAdminPermission(org.bukkit.entity.Player player) {
        return player.hasPermission("Holly.cmd.daily.admin") || player.hasPermission("Holly.daily.admin");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        return Collections.emptyList();
    }
}
