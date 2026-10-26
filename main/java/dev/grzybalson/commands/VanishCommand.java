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
import java.util.Locale;

public class VanishCommand implements CommandExecutor, TabCompleter {

    private final HollyCore plugin;

    public VanishCommand(HollyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("Holly.cmd.vanish")) {
                sender.sendMessage(ColorUtils.noPermission(label + " reload", "Holly.cmd.vanish"));
                return true;
            }
            plugin.getConfigManager().reloadVanishConfig();
            sender.sendMessage(ColorUtils.colorizeComponent("&#00FF3B✔ &8Ξ &#4EFA76Przeładowano konfigurację vanish!"));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.colorizeComponent(
                plugin.getConfigManager().getMessagesConfig().getPlayerOnly()
            ));
            return true;
        }

        if (!player.hasPermission("Holly.cmd.vanish")) {
            player.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.vanish"));
            return true;
        }

        boolean nowVanished = plugin.getVanishManager().toggleVanish(player);

        if (nowVanished) {
            player.sendMessage(ColorUtils.colorizeComponent(
                plugin.getConfigManager().getVanishConfig().getVanishEnabled()
            ));
            plugin.getSoundManager().playVanishEnable(player);
        } else {
            player.sendMessage(ColorUtils.colorizeComponent(
                plugin.getConfigManager().getVanishConfig().getVanishDisabled()
            ));
            plugin.getSoundManager().playVanishDisable(player);
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1 && "reload".startsWith(args[0].toLowerCase(Locale.ROOT)) && sender.hasPermission("Holly.cmd.vanish")) {
            completions.add("reload");
        }
        return completions;
    }
}
