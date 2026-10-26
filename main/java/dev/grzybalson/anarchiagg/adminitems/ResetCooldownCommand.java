package dev.grzybalson.anarchiagg.adminitems;

import dev.grzybalson.HollyCore;
import dev.grzybalson.anarchiagg.util.RegionUtil;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ResetCooldownCommand implements CommandExecutor {

    private final HollyCore plugin;

    public ResetCooldownCommand(HollyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cTa komenda jest dostepna tylko dla graczy!");
            return true;
        }

        if (!RegionUtil.isInSpawn(player, this.plugin)) {
            player.sendMessage(ColorUtils.colorize("&cTej komendy mozesz uzyc tylko na spawnie!"));
            return true;
        }

        AdminItemsCooldownResetter.reset(player, this.plugin);
        player.sendMessage(ColorUtils.colorize("&aZresetowano cooldown wszystkich eventowek!"));
        return true;
    }
}
