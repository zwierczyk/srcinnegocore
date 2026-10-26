package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
import dev.grzybalson.modules.home.Home;
import dev.grzybalson.modules.home.HomeManager;
import dev.grzybalson.modules.home.PlayerHomesData;
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

public class SetHomeCommand implements CommandExecutor, TabCompleter {

    private final HollyCore plugin;
    private final HomeManager homeManager;

    public SetHomeCommand(HollyCore plugin, HomeManager homeManager) {
        this.plugin = plugin;
        this.homeManager = homeManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.colorizeComponent(plugin.getConfigManager().getMessagesConfig().getPlayerOnly()));
            return true;
        }

        if (!player.hasPermission("holly.home.use") && !player.hasPermission("Holly.cmd.sethome")) {
            player.sendMessage(ColorUtils.noPermission(label, "holly.home.use"));
            return true;
        }

        PlayerHomesData data = homeManager.getPlayerHomes(player);

        int targetSlot = -1;
        String name = null;

        if (args.length > 0) {
            try {
                targetSlot = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                name = args[0];
            }
        }

        if (targetSlot == -1) {
            // Find next available slot player has permission for, or overwrite slot 1
            for (int i = 1; i <= 7; i++) {
                if (homeManager.canAccessSlot(player, i)) {
                    if (data.getHome(i) == null) {
                        targetSlot = i;
                        break;
                    }
                }
            }
            if (targetSlot == -1) {
                targetSlot = 1; // Fallback to slot 1
            }
        }

        homeManager.setHome(player, targetSlot, name);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1 && sender instanceof Player player) {
            List<String> suggestions = new ArrayList<>();
            String input = args[0].toLowerCase();
            for (int i = 1; i <= 7; i++) {
                if (homeManager.canAccessSlot(player, i)) {
                    String val = String.valueOf(i);
                    if (val.startsWith(input)) suggestions.add(val);
                }
            }
            return suggestions;
        }
        return List.of();
    }
}
