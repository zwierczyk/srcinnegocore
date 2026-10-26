package dev.grzybalson.modules.flamehubcustomitems.commands;

import dev.grzybalson.modules.flamehubcustomitems.config.FlameHubConfigManager;
import dev.grzybalson.modules.flamehubcustomitems.manager.ItemManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FlameItemsCommand implements CommandExecutor, TabCompleter {

    private final FlameHubConfigManager configManager;
    private final ItemManager itemManager;

    public FlameItemsCommand(FlameHubConfigManager configManager, ItemManager itemManager) {
        this.configManager = configManager;
        this.itemManager = itemManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("Holly.flamehub.reload") && !sender.hasPermission("Holly.flamehub.items")
                    && !sender.hasPermission("flame.reload") && !sender.hasPermission("flame.items")) {
                sender.sendMessage(configManager.getMessage("no-permission"));
                return true;
            }
            configManager.loadConfig();
            sender.sendMessage(configManager.getMessage("config-reloaded"));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(configManager.getMessage("only-player"));
            return true;
        }

        if (!player.hasPermission("Holly.flamehub.items") && !player.hasPermission("flame.items")) {
            player.sendMessage(configManager.getMessage("no-permission"));
            return true;
        }

        itemManager.openGui(player);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> list = new ArrayList<>();
            if ("reload".startsWith(args[0].toLowerCase())) {
                list.add("reload");
            }
            return list;
        }
        return Collections.emptyList();
    }
}
