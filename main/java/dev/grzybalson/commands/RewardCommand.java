package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
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

public class RewardCommand implements CommandExecutor, TabCompleter {

    private final HollyCore plugin;

    public RewardCommand(HollyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("Holly.cmd.nagroda.reload") && !sender.hasPermission("core.admin")) {
                sender.sendMessage(ColorUtils.noPermission(label + " reload", "Holly.cmd.nagroda.reload"));
                return true;
            }

            if (plugin.getDiscordRewardManager() != null) {
                plugin.getDiscordRewardManager().reload();
            }
            sender.sendMessage(ColorUtils.success("Przeladowano Discord reward."));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.colorizeComponent(plugin.getConfigManager().getMessagesConfig().getPlayerOnly()));
            return true;
        }

        if (plugin.getDiscordRewardManager() == null || !plugin.getDiscordRewardManager().isEnabled()) {
            player.sendMessage(ColorUtils.colorizeComponent("&#FF5C5C✘ &8• &fSystem nagrody Discord jest obecnie wylaczony."));
            return true;
        }

        plugin.getDiscordRewardManager().openRewardMenu(player);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length != 1) {
            return new ArrayList<>();
        }

        String partial = args[0].toLowerCase();
        List<String> completions = new ArrayList<>();

        if ((sender.hasPermission("Holly.cmd.nagroda.reload") || sender.hasPermission("core.admin"))
            && "reload".startsWith(partial)) {
            completions.add("reload");
        }

        return completions;
    }
}
