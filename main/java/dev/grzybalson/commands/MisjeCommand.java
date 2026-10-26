package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class MisjeCommand implements CommandExecutor {

    private final HollyCore plugin;

    public MisjeCommand(HollyCore plugin) {
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

        if (!player.hasPermission("Holly.cmd.misje")) {
            player.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.misje"));
            return true;
        }

        if (!plugin.isAddonEnabled("mission") || plugin.getMissionManager() == null) {
            player.sendMessage(ColorUtils.colorizeComponent("&#FFB800⚠ &8Ξ &eSystem misji jest wyłączony."));
            return true;
        }

        plugin.getMissionManager().openGui(player);
        return true;
    }
}
