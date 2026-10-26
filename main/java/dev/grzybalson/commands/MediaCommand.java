package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
import dev.grzybalson.gui.InfoGuiInventory;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class MediaCommand implements CommandExecutor, TabCompleter {

    private final HollyCore plugin;

    public MediaCommand(HollyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("Holly.cmd.media.reload") && !sender.hasPermission("Holly.cmd.media")) {
                sender.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.media"));
                return true;
            }
            plugin.getModuleConfigManager().reloadMediaGui();
            sender.sendMessage(ColorUtils.success("Przeładowano konfigurację media!"));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.colorizeComponent(
                plugin.getModuleConfigManager().getCommandsMessagesConfig().getPlayerOnlyMessage()
            ));
            return true;
        }

        new InfoGuiInventory(plugin, player, plugin.getModuleConfigManager().getMediaGui()).open();
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1 && "reload".startsWith(args[0].toLowerCase())) {
            return List.of("reload");
        }
        return List.of();
    }
}
