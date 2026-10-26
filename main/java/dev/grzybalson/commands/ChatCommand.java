package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.configs.ChatConfig;
import dev.grzybalson.utils.ColorUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class ChatCommand implements CommandExecutor, TabCompleter {

    private final HollyCore plugin;
    private static volatile boolean chatEnabled = true;

    public ChatCommand(HollyCore plugin) {
        this.plugin = plugin;
    }

    public static boolean isChatEnabled() {
        return chatEnabled;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        ChatConfig config = plugin.getConfigManager().getChatConfig();

        if (args.length == 0) {
            ColorUtils.sendUsage(sender, "/chat <clear|off|on>");
            return true;
        }

        String senderName = sender instanceof Player ? sender.getName() : "Konsola";

        switch (args[0].toLowerCase()) {
            case "clear" -> {
                if (!sender.hasPermission(config.getChatClearPermission())) {
                    sender.sendMessage(ColorUtils.noPermission(label + " clear", config.getChatClearPermission()));
                    return true;
                }
                Component empty = Component.empty();
                for (int i = 0; i < config.getClearLines(); i++) {
                    Bukkit.broadcast(empty);
                }
                Bukkit.broadcast(ColorUtils.colorizeComponent(
                    config.getChatClearMessage().replace("{player}", senderName)
                ));
            }
            case "off" -> {
                if (!sender.hasPermission(config.getChatTogglePermission())) {
                    sender.sendMessage(ColorUtils.noPermission(label + " off", config.getChatTogglePermission()));
                    return true;
                }
                chatEnabled = false;
                Bukkit.broadcast(ColorUtils.colorizeComponent(
                    config.getChatDisabledMessage().replace("{player}", senderName)
                ));
            }
            case "on" -> {
                if (!sender.hasPermission(config.getChatTogglePermission())) {
                    sender.sendMessage(ColorUtils.noPermission(label + " on", config.getChatTogglePermission()));
                    return true;
                }
                chatEnabled = true;
                Bukkit.broadcast(ColorUtils.colorizeComponent(
                    config.getChatEnabledMessage().replace("{player}", senderName)
                ));
            }
            case "reload" -> {
                if (!sender.hasPermission("Holly.cmd.chat.reload")) {
                    sender.sendMessage(ColorUtils.noPermission(label + " reload", "Holly.cmd.chat.reload"));
                    return true;
                }
                plugin.getConfigManager().reloadChatConfig();
                sender.sendMessage(ColorUtils.colorizeComponent(ColorUtils.SUCCESS_PREFIX + "Przeładowano chat.yml."));
            }
            default -> ColorUtils.sendUsage(sender, "/chat <clear|off|on|reload>");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            for (String sub : List.of("clear", "off", "on", "reload")) {
                if (sub.startsWith(input)) {
                    completions.add(sub);
                }
            }
        }
        return completions;
    }
}
