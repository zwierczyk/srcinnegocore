package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
import dev.grzybalson.modules.kit.Kit;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class LosujZestawCommand implements CommandExecutor {

    private final HollyCore plugin;

    public LosujZestawCommand(HollyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.colorizeComponent(
                plugin.getConfigManager().getMessagesConfig().getPlayerOnly()
            ));
            return true;
        }

        Kit graczKit = plugin.getKitManager().getKit("gracz");

        if (graczKit == null) {
            player.sendMessage(ColorUtils.colorizeComponent(
                plugin.getConfigManager().getMessagesConfig().getLosujZestawNoKits()
            ));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return true;
        }

        if (plugin.getKitManager().giveKit(player, "gracz")) {
            player.sendMessage(ColorUtils.colorizeComponent(
                plugin.getConfigManager().getMessagesConfig().getLosujZestawSuccess()
                    .replace("{kit}", graczKit.getDisplayName())
            ));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        }

        return true;
    }
}
