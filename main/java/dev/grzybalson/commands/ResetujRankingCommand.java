package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
import dev.grzybalson.modules.kit.Kit;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class ResetujRankingCommand implements CommandExecutor {

    private final HollyCore plugin;

    public ResetujRankingCommand(HollyCore plugin) {
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

        if (!player.hasPermission("Holly.cmd.resetujranking")) {
            player.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.resetujranking"));
            return true;
        }

        if (plugin.getEloManager() == null) {
            player.sendMessage(ColorUtils.error("System rankingu jest wyłączony."));
            plugin.getSoundManager().playError(player);
            return true;
        }

        boolean bypass = player.hasPermission("Holly.bypass.resetujranking");
        long remainingMs = plugin.getEloManager().getRankingResetRemainingMs(player.getUniqueId());
        if (!bypass && remainingMs > 0L) {
            long remainingSeconds = Math.max(1L, (remainingMs + 999L) / 1000L);
            player.sendMessage(ColorUtils.colorizeComponent(
                "&#FF0000✘ &8Ξ &cMusisz poczekać jeszcze &f" + Kit.formatTime(remainingSeconds) + "&c, zanim zresetujesz ranking."
            ));
            plugin.getSoundManager().playError(player);
            return true;
        }

        int value = plugin.getEloManager().getRankingResetValue();
        plugin.getEloManager().resetRankingWithCooldown(player.getUniqueId());
        player.sendMessage(ColorUtils.colorizeComponent(
            "&#00FF3B✔ &8Ξ &#4EFA76Zresetowano ranking do &f" + value + "&a oraz &fkille &ai &fśmierci&a."
        ));
        plugin.getSoundManager().playSuccess(player);
        return true;
    }
}
