package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
import dev.grzybalson.core.HollyPluginReloader;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Sound;
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

public class AdminCommand implements CommandExecutor, TabCompleter {

    private final HollyCore plugin;

    public AdminCommand(HollyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ColorUtils.usage("/" + label + " loadnew"));
            sender.sendMessage(ColorUtils.info("Wgraj nowy JAR do &fplugins/&a, opcjonalnie jako &fHollyCore-1.4.jar.new &7i wpisz loadnew."));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("loadnew") || sub.equals("reloadjar") || sub.equals("reloadplugin")) {
            return handleLoadNew(sender, label);
        }

        sender.sendMessage(ColorUtils.usage("/" + label + " loadnew"));
        return true;
    }

    private boolean handleLoadNew(CommandSender sender, String label) {
        if (!sender.hasPermission("Holly.cmd.admin.loadnew")) {
            sender.sendMessage(ColorUtils.noPermission(label + " loadnew", "Holly.cmd.admin.loadnew"));
            return true;
        }

        sender.sendMessage(ColorUtils.info("Trwa loadnew HollyCore — podmiana JAR i hot-reload..."));
        HollyPluginReloader.Result result = HollyPluginReloader.loadNew(plugin, sender);

        switch (result.mode()) {
            case NATIVE -> {
                sender.sendMessage(ColorUtils.success(result.message()));
                sender.sendMessage(ColorUtils.colorizeComponent(
                    "&7Tip: wrzuc nowy plik jako &fplugins/HollyCore-1.4.jar.new &7albo podmien JAR recznie przed loadnew."));
            }
            case EXTERNAL -> sender.sendMessage(ColorUtils.success(result.message()));
            case FAILED -> sender.sendMessage(ColorUtils.error(result.message()));
            default -> sender.sendMessage(ColorUtils.info(result.message()));
        }

        if (result.detail() != null && result.mode() != HollyPluginReloader.Mode.FAILED) {
            sender.sendMessage(ColorUtils.colorizeComponent("&#4EFA76" + result.detail()));
        }

        if (sender instanceof Player player && result.mode() != HollyPluginReloader.Mode.FAILED) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.5f);
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1 && sender.hasPermission("Holly.cmd.admin.loadnew")) {
            String typed = args[0].toLowerCase(Locale.ROOT);
            if ("loadnew".startsWith(typed)) {
                out.add("loadnew");
            }
        }
        return out;
    }
}
