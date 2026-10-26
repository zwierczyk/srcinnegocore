package dev.grzybalson.commands;

import dev.grzybalson.managers.TablistManager;
import dev.grzybalson.managers.SidebarManager;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TablistCommand implements CommandExecutor, TabCompleter {

    private final TablistManager tablistManager;
    private final SidebarManager sidebarManager;

    public TablistCommand(TablistManager tablistManager) {
        this(tablistManager, null);
    }

    public TablistCommand(TablistManager tablistManager, SidebarManager sidebarManager) {
        this.tablistManager = tablistManager;
        this.sidebarManager = sidebarManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("Holly.cmd.tablist.reload")) {
                sender.sendMessage(ColorUtils.error("Nie posiadasz permisji."));
                return true;
            }

            tablistManager.reload();
            if (sidebarManager != null) {
                org.bukkit.plugin.Plugin core = org.bukkit.Bukkit.getPluginManager().getPlugin("HollyCore");
                if (core != null) {
                    org.bukkit.Bukkit.getScheduler().runTaskLater(core, sidebarManager::reload, 1L);
                } else {
                    sidebarManager.reload();
                }
            }
            sender.sendMessage(ColorUtils.success("Przeladowano tabliste. Scoreboard dojdzie w nastepnym ticku."));
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("debug")) {
            if (!sender.hasPermission("Holly.cmd.tablist.reload")) {
                sender.sendMessage(ColorUtils.error("Nie posiadasz permisji."));
                return true;
            }

            tablistManager.dumpDebugInfo(sender);
            return true;
        }

        sender.sendMessage(ColorUtils.usage("/" + label + " <reload/debug>"));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            if ("reload".startsWith(args[0].toLowerCase(Locale.ROOT))) completions.add("reload");
            if ("debug".startsWith(args[0].toLowerCase(Locale.ROOT))) completions.add("debug");
        }
        return completions;
    }
}
