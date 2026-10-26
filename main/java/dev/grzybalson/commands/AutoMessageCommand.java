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

public class AutoMessageCommand implements CommandExecutor, TabCompleter {

    private final HollyCore plugin;

    public AutoMessageCommand(HollyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("Holly.cmd.automessage")) {
            sender.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.automessage"));
            return true;
        }

        if (args.length < 1) {
            sendUsage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "reload" -> {
                plugin.getConfigManager().getAutoMessageConfig().load(true);
                plugin.getAutoMessageManager().reload();
                sender.sendMessage(ColorUtils.success("Przeładowano konfigurację AutoMessage!"));
            }
            case "test" -> {
                if (args.length < 2) {
                    ColorUtils.sendUsage(sender, "/automessage test <numer>");
                    return true;
                }
                try {
                    int index = Integer.parseInt(args[1]);
                    plugin.getAutoMessageManager().sendTestMessage(index);
                    sender.sendMessage(ColorUtils.success("Wysłano testową wiadomość nr " + index + "."));
                } catch (NumberFormatException e) {
                    sender.sendMessage(ColorUtils.error("Nieprawidłowy numer!"));
                }
            }
            default -> sendUsage(sender);
        }

        return true;
    }

    private void sendUsage(CommandSender sender) {
        ColorUtils.sendUsage(sender,
            "/automessage reload",
            "/automessage test <numer>"
        );
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            for (String sub : List.of("reload", "test")) {
                if (sub.startsWith(partial)) {
                    completions.add(sub);
                }
            }
        }
        return completions;
    }
}
