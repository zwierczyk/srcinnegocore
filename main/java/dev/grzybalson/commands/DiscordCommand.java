package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.configs.DiscordConfig;
import dev.grzybalson.utils.ColorUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class DiscordCommand implements CommandExecutor, TabCompleter {

    private final HollyCore plugin;

    public DiscordCommand(HollyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("Holly.cmd.discord.reload") && !sender.hasPermission("core.admin")) {
                sender.sendMessage(ColorUtils.noPermission(label + " reload", "Holly.cmd.discord.reload"));
                return true;
            }

            plugin.getConfigManager().reloadDiscordConfig();
            sender.sendMessage(ColorUtils.colorizeComponent(plugin.getConfigManager().getDiscordConfig().getReloadMessage()));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.colorizeComponent(plugin.getConfigManager().getMessagesConfig().getPlayerOnly()));
            return true;
        }

        DiscordConfig discordConfig = plugin.getConfigManager().getDiscordConfig();
        String link = discordConfig.getDiscordLink();

        for (String rawLine : discordConfig.getMessageLines()) {
            String line = rawLine.replace("{link}", link);
            Component component = ColorUtils.colorizeComponent(line);

            if (rawLine.contains("{link}")) {
                component = component.clickEvent(ClickEvent.openUrl(link));
            }

            player.sendMessage(component);
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1 && (sender.hasPermission("Holly.cmd.discord.reload") || sender.hasPermission("core.admin")) && "reload".startsWith(args[0].toLowerCase())) {
            return List.of("reload");
        }
        return new ArrayList<>();
    }
}
