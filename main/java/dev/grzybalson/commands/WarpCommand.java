package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
import dev.grzybalson.gui.WarpGUI;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class WarpCommand implements CommandExecutor, TabCompleter {

    private final HollyCore plugin;

    public WarpCommand(HollyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("Holly.cmd.warp.reload")) {
                sender.sendMessage(ColorUtils.noPermission(label + " reload", "Holly.cmd.warp.reload"));
                return true;
            }
            plugin.getModuleConfigManager().reloadAll();
            sender.sendMessage(ColorUtils.colorizeComponent("&#00FF3B✔ &8Ξ &#4EFA76Przeładowano konfigurację warpy."));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.colorizeComponent(
                plugin.getModuleConfigManager().getCommandsMessagesConfig().getPlayerOnlyMessage()
            ));
            return true;
        }

        if (args.length == 0) {
            new WarpGUI(plugin, player).open();
            return true;
        }

        String warpName = args[0];
        plugin.getWarpManager().teleportToWarp(player, warpName);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String input = args[0].toLowerCase();
            if ("reload".startsWith(input) && sender.hasPermission("Holly.cmd.warp.reload")) {
                completions.add("reload");
            }
            for (String warpName : plugin.getWarpManager().getWarpNames()) {
                if (warpName.toLowerCase().startsWith(input)) {
                    completions.add(warpName);
                }
            }
        }

        return completions;
    }
}
