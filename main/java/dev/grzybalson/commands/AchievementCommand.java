package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
import dev.grzybalson.gui.AchievementGUI;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.LockedFeatureMessages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class AchievementCommand implements CommandExecutor {

    private final HollyCore plugin;

    public AchievementCommand(HollyCore plugin) {
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

        if (!player.hasPermission("Holly.cmd.osiagniecia")) {
            LockedFeatureMessages.send(player, "/osiagniecia");
            return true;
        }

        if (args.length > 0) {
            ColorUtils.sendUsage(player, "/osiagniecia");
            return true;
        }

        new AchievementGUI(plugin, player).open();
        return true;
    }
}
