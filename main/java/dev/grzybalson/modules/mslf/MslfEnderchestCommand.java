package dev.grzybalson.modules.mslf;

import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * /ec gdy addon enderchest jest wylaczony — otwiera lekki EC z MSLF.
 */
public final class MslfEnderchestCommand implements CommandExecutor {

    private final HollyCore plugin;

    public MslfEnderchestCommand(HollyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cTylko dla graczy."));
            return true;
        }
        if (!player.hasPermission("Holly.cmd.ec")) {
            player.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.ec"));
            return true;
        }

        MslfManager mslf = plugin.getMslfManager();
        MslfEnderChestService service = mslf == null ? null : mslf.enderChestService();
        if (service != null) {
            service.openCustom(player);
            return true;
        }

        // MSLF EC nie wstal (np. addon wylaczony albo blad Mongo) — nie zostawiaj gracza bez /ec.
        if (plugin.getEnderChestManager() != null) {
            plugin.getEnderChestManager().openEnderChestFor(player, player);
            return true;
        }

        player.openInventory(player.getEnderChest());
        return true;
    }
}
