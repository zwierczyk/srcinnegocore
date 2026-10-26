package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
import dev.grzybalson.managers.IncognitoManager;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class IncognitoCommand implements CommandExecutor {

    private final HollyCore plugin;

    public IncognitoCommand(HollyCore plugin) {
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
        if (!player.hasPermission("Holly.cmd.incognito")) {
            player.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.incognito"));
            return true;
        }

        IncognitoManager manager = plugin.getIncognitoManager();
        if (manager == null) {
            player.sendMessage(ColorUtils.error("Incognito jest niedostępne."));
            return true;
        }

        long remaining = manager.remainingCooldownMs(player);
        if (remaining > 0L) {
            player.sendMessage(ColorUtils.error("Odczekaj " + formatSeconds(remaining) + " przed następną zmianą ukrycia."));
            return true;
        }

        boolean enabled = manager.toggle(player);
        if (enabled) {
            manager.refreshVisibility(player);
            if (plugin.getTablistManager() != null) {
                plugin.getTablistManager().refreshIncognito(player);
            }
            player.sendMessage(ColorUtils.success("Incognito włączone. Widoczny nick: &f" + manager.getIncognitoName(player.getUniqueId())));
        } else {
            // toggle już wyłączył + refresh w disable()
            player.sendMessage(ColorUtils.success("Incognito wyłączone."));
        }
        return true;
    }

    private static String formatSeconds(long remainingMs) {
        long seconds = Math.max(1L, (remainingMs + 999L) / 1000L);
        return seconds + "s";
    }
}
