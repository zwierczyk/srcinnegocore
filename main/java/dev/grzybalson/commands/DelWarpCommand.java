package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.modules.warps.WarpsConfig;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class DelWarpCommand implements CommandExecutor, TabCompleter {

    private final HollyCore plugin;

    public DelWarpCommand(HollyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("Holly.cmd.delwarp")) {
            sender.sendMessage(ColorUtils.colorizeComponent(
                plugin.getModuleConfigManager().getCommandsMessagesConfig().getNoPermissionMessage()
            ));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(ColorUtils.usage("/delwarp <nazwa>"));
            return true;
        }

        String warpName = args[0];
        WarpsConfig config = plugin.getModuleConfigManager().getWarpsConfig();

        boolean success = plugin.getWarpManager().deleteWarp(warpName);

        if (success) {
            String message = config.getWarpDeletedMessage().replace("{warp}", warpName);
            sender.sendMessage(ColorUtils.colorizeComponent(message));
            if (sender instanceof Player player) {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            }
        } else {
            sender.sendMessage(ColorUtils.colorizeComponent(config.getWarpNotFoundMessage()));
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String input = args[0].toLowerCase();
            for (String warpName : plugin.getWarpManager().getWarpNames()) {
                if (warpName.toLowerCase().startsWith(input)) {
                    completions.add(warpName);
                }
            }
        }

        return completions;
    }
}
