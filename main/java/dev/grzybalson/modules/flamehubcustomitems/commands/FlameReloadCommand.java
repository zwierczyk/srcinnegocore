package dev.grzybalson.modules.flamehubcustomitems.commands;

import dev.grzybalson.modules.flamehubcustomitems.config.FlameHubConfigManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class FlameReloadCommand implements CommandExecutor, TabCompleter {

    private final FlameHubConfigManager configManager;

    public FlameReloadCommand(FlameHubConfigManager configManager) {
        this.configManager = configManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("Holly.flamehub.reload") && !sender.hasPermission("Holly.flamehub.items")
                && !sender.hasPermission("flame.reload") && !sender.hasPermission("flame.items")) {
            sender.sendMessage(configManager.getMessage("no-permission"));
            return true;
        }

        configManager.loadConfig();
        sender.sendMessage(configManager.getMessage("config-reloaded"));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        return Collections.emptyList();
    }
}
