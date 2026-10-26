package dev.grzybalson.modules.itemstorage;

import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ItemStorageCommand implements CommandExecutor {

    private final HollyCore plugin;

    public ItemStorageCommand(HollyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cTa komenda jest dostepna tylko dla graczy!");
            return true;
        }

        if (!player.hasPermission("Holly.cmd.itemstorage")
            && !player.hasPermission("Holly.cmd.aitems")
            && !player.hasPermission("hollycore.adminitems")
            && !player.isOp()) {
            player.sendMessage(ColorUtils.colorizeComponent("&#FF5C5C✘ &8• &#FF5C5CBrak uprawnien!"));
            return true;
        }

        if (plugin.getItemStorageManager() == null) {
            player.sendMessage(ColorUtils.colorizeComponent("&#FF5C5C✘ &8• &#FF5C5CItemStorage jest wylaczony."));
            return true;
        }

        new ItemStorageGUI(plugin).open(player);
        return true;
    }
}
