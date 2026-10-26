package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class OdbierzBackupCommand implements CommandExecutor {

    private final HollyCore plugin;

    public OdbierzBackupCommand(HollyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.error("Ta komenda jest tylko dla graczy!"));
            return true;
        }

        plugin.getBackupManager().openClaimGui(player);
        return true;
    }
}
