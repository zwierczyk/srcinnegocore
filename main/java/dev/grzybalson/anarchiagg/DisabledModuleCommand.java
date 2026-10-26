package dev.grzybalson.anarchiagg;

import dev.grzybalson.utils.ColorUtils;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class DisabledModuleCommand implements CommandExecutor {
    private final String moduleName;

    public DisabledModuleCommand(String moduleName) {
        this.moduleName = moduleName;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        sender.sendMessage(ColorUtils.colorizeComponent(ChatColor.translateAlternateColorCodes('&', "&#FFB800⚠ &8Ξ &7Moduł &f" + moduleName + " &7jest obecnie wyłączony.")));
        return true;
    }
}
