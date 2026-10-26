package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
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

public class GuiCommand implements CommandExecutor, TabCompleter {

    private final HollyCore plugin;

    public GuiCommand(HollyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("Holly.cmd.guis.reload")) {
                sender.sendMessage(ColorUtils.noPermission(label + " reload", "Holly.cmd.guis.reload"));
                return true;
            }

            plugin.getConfigManager().getGuiConfig().load(true);
            plugin.getConfigManager().getGuiTemplateConfig().load(true);
            plugin.getModuleConfigManager().getGuiTemplateConfig().load(true);
            sender.sendMessage(ColorUtils.success("Przeladowano konfiguracje GUI i template."));
            return true;
        }

        sender.sendMessage(ColorUtils.usage("/" + label + " reload"));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1 && "reload".startsWith(args[0].toLowerCase(Locale.ROOT))) {
            completions.add("reload");
        }
        return completions;
    }
}
