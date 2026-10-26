package dev.grzybalson.anarchiagg.adminitems;

import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;

public class AdminItemsClearCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("Holly.cmd.adminitemsclear") && !sender.isOp()) {
            sender.sendMessage("§cBrak uprawnien!");
            return true;
        }

        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Item item : world.getEntitiesByClass(Item.class)) {
                item.remove();
                removed++;
            }
        }

        sender.sendMessage(ColorUtils.colorize("&8[&2✔&8] &aUsunieto &2" + removed + " &aprzedmiotow lezacych na ziemi."));
        if (sender instanceof Player player) {
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        }
        return true;
    }
}
