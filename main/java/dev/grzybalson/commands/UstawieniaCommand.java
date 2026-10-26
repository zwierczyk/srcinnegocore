package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
import dev.grzybalson.gui.SettingsGUI;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.ClickCooldown;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class UstawieniaCommand implements CommandExecutor {

    private final HollyCore plugin;

    public UstawieniaCommand(HollyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.colorizeComponent(plugin.getConfigManager().getMessagesConfig().getPlayerOnly()));
            return true;
        }
        if (!ClickCooldown.tryUse(player, "open-settings-gui", 500L)) {
            return true;
        }
        new SettingsGUI(plugin, player).open();
        return true;
    }
}
