package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class ArmorStarsCommand implements CommandExecutor, TabCompleter {

    private static final String RELOAD_PERMISSION = "Holly.cmd.armorstars.reload";

    private final HollyCore plugin;

    public ArmorStarsCommand(HollyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0 || !args[0].equalsIgnoreCase("reload")) {
            sender.sendMessage(ColorUtils.usage("/" + label + " reload"));
            return true;
        }
        if (!sender.hasPermission(RELOAD_PERMISSION)) {
            sender.sendMessage(ColorUtils.noPermission(label + " reload", RELOAD_PERMISSION));
            return true;
        }
        plugin.getConfigManager().reloadArmorStarsConfig();
        if (plugin.getTablistManager() != null) {
            plugin.getTablistManager().refreshSpooferNameTags();
            plugin.getTablistManager().refreshAllIdentities();
        }
        sender.sendMessage(ColorUtils.success("Przeladowano addons/armorstars/armorstars.yml"));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String alias,
        @NotNull String[] args
    ) {
        if (args.length == 1 && sender.hasPermission(RELOAD_PERMISSION) && "reload".startsWith(args[0].toLowerCase(Locale.ROOT))) {
            return List.of("reload");
        }
        return Collections.emptyList();
    }
}
