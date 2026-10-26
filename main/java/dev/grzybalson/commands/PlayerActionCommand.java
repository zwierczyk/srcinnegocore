package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
import dev.grzybalson.gui.PlayerActionGUI;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PlayerActionCommand implements CommandExecutor {

    private final HollyCore plugin;

    public PlayerActionCommand(HollyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (args.length == 0) return true;

        String targetName = args[0];

        // Don't open GUI for yourself
        if (player.getName().equalsIgnoreCase(targetName)) return true;

        // Click z czatu bywa async — GUI tylko na main thread.
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> openGui(player, targetName));
            return true;
        }
        openGui(player, targetName);
        return true;
    }

    private void openGui(Player player, String targetName) {
        if (!player.isOnline()) {
            return;
        }
        new PlayerActionGUI(plugin, player, targetName).open();
    }
}
