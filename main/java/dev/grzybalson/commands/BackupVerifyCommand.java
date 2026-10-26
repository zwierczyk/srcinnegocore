package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
import dev.grzybalson.gui.BackupAdminGUI;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class BackupVerifyCommand implements CommandExecutor {

    private final HollyCore plugin;

    public BackupVerifyCommand(HollyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.error("Ta komenda jest tylko dla graczy!"));
            return true;
        }

        if (!player.hasPermission("holly.cmd.backup.verify")) {
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
}
