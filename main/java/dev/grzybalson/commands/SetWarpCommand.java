package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.modules.warps.WarpsConfig;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SetWarpCommand implements CommandExecutor, TabCompleter {

    private final HollyCore plugin;

    public SetWarpCommand(HollyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.colorizeComponent(
                plugin.getModuleConfigManager().getCommandsMessagesConfig().getPlayerOnlyMessage()
            ));
            return true;
        }

        if (!player.hasPermission("Holly.cmd.setwarp")) {
            sender.sendMessage(ColorUtils.colorizeComponent(
                plugin.getModuleConfigManager().getCommandsMessagesConfig().getNoPermissionMessage()
            ));
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(ColorUtils.usage("/setwarp <nazwa> [permission]"));
            return true;
        }

        String warpName = args[0];
        String permission = args.length > 1 ? args[1] : "";

        WarpsConfig config = plugin.getModuleConfigManager().getWarpsConfig();

        if (plugin.getWarpManager().getWarp(warpName) != null) {
            boolean updated = plugin.getWarpManager().updateWarpLocation(warpName, player.getLocation());
            if (updated) {
                String message = config.getWarpUpdatedMessage().replace("{warp}", warpName);
                player.sendMessage(ColorUtils.colorizeComponent(message));
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
            } else {
                player.sendMessage(ColorUtils.colorizeComponent(config.getWarpExistsMessage()));
            }
            return true;
        }

        boolean success = plugin.getWarpManager().createWarp(warpName, player.getLocation(), permission);

        if (success) {
            String message = config.getWarpCreatedMessage().replace("{warp}", warpName);
            player.sendMessage(ColorUtils.colorizeComponent(message));
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        } else {
            player.sendMessage(ColorUtils.colorizeComponent(config.getWarpExistsMessage()));
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 2) {
            return Arrays.asList("Holly.warp.");
        }
        return new ArrayList<>();
    }
}
