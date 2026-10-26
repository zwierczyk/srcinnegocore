package dev.grzybalson.modules.itemsync;

import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
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

public class InventorySyncCommand implements CommandExecutor, TabCompleter {

    private final HollyCore plugin;
    private final InventorySyncManager manager;

    public InventorySyncCommand(HollyCore plugin, InventorySyncManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("Holly.cmd.itemsync.reload") && !sender.hasPermission("core.admin")) {
                sender.sendMessage(ColorUtils.noPermission(label + " reload", "Holly.cmd.itemsync.reload"));
                return true;
            }
            manager.reload();
            sender.sendMessage(ColorUtils.colorizeComponent(manager.getReloadMessage()));
            return true;
        }

        if (!sender.hasPermission("Holly.cmd.itemsync.saveall") && !sender.hasPermission("core.admin")) {
            sender.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.itemsync.saveall"));
            return true;
        }

        // Capture (trade cancel / shulker flush) rusza itemami — musi isc na main thread.
        // Serializacja i zapis Mongo i tak schodza z glownego watku w saveSnapshot().
        Bukkit.getScheduler().runTask(plugin, () -> {
            int count = manager.saveAllOnline();
            sender.sendMessage(ColorUtils.colorizeComponent(manager.getSaveAllMessage(count)));
        });
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String partial = args[0].toLowerCase(Locale.ROOT);
        List<String> completions = new ArrayList<>();
        if ("reload".startsWith(partial)
            && (sender.hasPermission("Holly.cmd.itemsync.reload") || sender.hasPermission("core.admin"))) {
            completions.add("reload");
        }
        return completions;
    }
}
