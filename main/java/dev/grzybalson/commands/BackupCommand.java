package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
import dev.grzybalson.gui.BackupAdminGUI;
import dev.grzybalson.gui.BackupDeathSelectGUI;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BackupCommand implements CommandExecutor, TabCompleter {

    private final HollyCore plugin;

    public BackupCommand(HollyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!hasVerifyPermission(sender)) {
                sender.sendMessage(ColorUtils.noPermission(label + " reload", "holly.cmd.backup.verify"));
                return true;
            }

            plugin.getConfigManager().reloadBackupConfig();
            if (plugin.getBackupManager() != null) {
                plugin.getBackupManager().reloadZapowiedzReminderTask();
                plugin.getBackupManager().restartDiscordBot();
            }
            sender.sendMessage(ColorUtils.colorizeComponent(
                plugin.getConfigManager().getBackupConfig().getMessages().getBackupReloaded()
            ));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.error("Ta komenda jest tylko dla graczy!"));
            return true;
        }

        if (args.length == 0) {
            if (!hasVerifyPermission(player)) {
                player.sendMessage(ColorUtils.noPermission(label, "holly.cmd.backup.verify"));
                return true;
            }

            plugin.getBackupManager().getAllPendingBackups().thenAccept(backups -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (backups.isEmpty()) {
                    player.sendMessage(ColorUtils.colorizeComponent(
                        plugin.getConfigManager().getBackupConfig().getMessages().getBackupListEmpty()
                    ));
                    return;
                }
                new BackupAdminGUI(plugin, player, backups).open();
            }));
            return true;
        }

        if (!hasGivePermission(player) && !hasVerifyPermission(player)) {
            player.sendMessage(ColorUtils.noPermission(label, "holly.cmd.backup.give"));
            return true;
        }

        String targetName = args[0];
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);

        if (!target.hasPlayedBefore() && !target.isOnline()) {
            player.sendMessage(ColorUtils.colorizeComponent(
                plugin.getConfigManager().getBackupConfig().getMessages().getPlayerNotFound()
            ));
            return true;
        }

        UUID targetUuid = target.getUniqueId();
        plugin.getBackupManager().getPlayerDeaths(targetUuid).thenAccept(deaths -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (deaths.isEmpty()) {
                player.sendMessage(ColorUtils.colorizeComponent(
                    plugin.getConfigManager().getBackupConfig().getMessages().getNoDeathsFound()
                ));
                return;
            }
            new BackupDeathSelectGUI(plugin, player, targetUuid, target.getName(), deaths).open();
        }));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            if ("reload".startsWith(prefix) && hasVerifyPermission(sender)) {
                completions.add("reload");
            }
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(prefix)) {
                    completions.add(player.getName());
                }
            }
        }

        return completions;
    }

    private boolean hasGivePermission(CommandSender sender) {
        return sender.hasPermission("holly.cmd.backup.give");
    }

    private boolean hasVerifyPermission(CommandSender sender) {
        return sender.hasPermission("holly.cmd.backup.verify");
    }
}
