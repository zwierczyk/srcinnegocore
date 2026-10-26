package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.configs.CreatorModeConfig;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CreatorModeCommand implements CommandExecutor, TabCompleter {

    private final HollyCore plugin;

    public CreatorModeCommand(HollyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("hollycore.creatormode")) {
                sender.sendMessage(ColorUtils.noPermission(label + " reload", "hollycore.creatormode"));
                return true;
            }
            plugin.getConfigManager().reloadCreatorModeConfig();
            sender.sendMessage(ColorUtils.colorizeComponent("&#00FF3B✔ &8Ξ &#4EFA76Przeładowano konfigurację trybu twórcy!"));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.colorizeComponent(
                plugin.getConfigManager().getMessagesConfig().getPlayerOnly()
            ));
            return true;
        }

        CreatorModeConfig config = plugin.getConfigManager().getCreatorModeConfig();

        if (!player.hasPermission("hollycore.creatormode")) {
            player.sendMessage(ColorUtils.noPermission(label, "hollycore.creatormode"));
            return true;
        }

        if (plugin.getCreatorModeManager() == null) {
            player.sendMessage(ColorUtils.error("Tryb twórcy jest wyłączony."));
            return true;
        }

        if (plugin.getCreatorModeManager().hasActiveGrant(player.getUniqueId())) {
            String remaining = dev.grzybalson.modules.moderation.ModerationManager.formatDuration(
                plugin.getCreatorModeManager().remainingMillis(player.getUniqueId())
            );
            player.sendMessage(ColorUtils.colorizeComponent(
                config.getGrantLockedMessage().replace("{time}", remaining)
            ));
            return true;
        }

        boolean enabled = plugin.getCreatorModeManager().toggleCreatorMode(player);

        if (enabled) {
            player.sendMessage(ColorUtils.colorizeComponent(config.getEnabledMessage()));
        } else {
            player.sendMessage(ColorUtils.colorizeComponent(config.getDisabledMessage()));
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1 && "reload".startsWith(args[0].toLowerCase(Locale.ROOT)) && sender.hasPermission("hollycore.creatormode")) {
            completions.add("reload");
        }
        return completions;
    }
}
