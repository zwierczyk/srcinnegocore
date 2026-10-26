package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.configs.SpawnConfig;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class SetSpawnCommand implements CommandExecutor {

    private final HollyCore plugin;

    public SetSpawnCommand(HollyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.colorizeComponent(
                plugin.getConfigManager().getMessagesConfig().getPlayerOnly()
            ));
            return true;
        }

        if (!player.hasPermission("Holly.cmd.setspawn")) {
            player.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.setspawn"));
            return true;
        }

        Location loc = player.getLocation();
        SpawnConfig config = plugin.getConfigManager().getSpawnConfig();

        config.setSpawnSet(true);
        config.setWorld(loc.getWorld().getName());
        config.setX(loc.getX());
        config.setY(loc.getY());
        config.setZ(loc.getZ());
        config.setYaw(loc.getYaw());
        config.setPitch(loc.getPitch());
        config.save();

        if (loc.getWorld() != null) {
            loc.getWorld().setSpawnLocation(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        }

        player.sendMessage(ColorUtils.colorizeComponent(
            plugin.getConfigManager().getMessagesConfig().getSpawnSet()
        ));

        return true;
    }
}
