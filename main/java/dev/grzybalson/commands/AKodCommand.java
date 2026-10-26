package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.configs.CodeConfig;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.ItemSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class AKodCommand implements CommandExecutor, TabCompleter {

    private final HollyCore plugin;

    public AKodCommand(HollyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        CodeConfig config = plugin.getConfigManager().getCodeConfig();

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        if (subCommand.equals("rozdanie") && args.length >= 2 && args[1].equalsIgnoreCase("rang")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ColorUtils.error("Ta komenda jest tylko dla graczy!"));
                return true;
            }
            if (!player.isOp()) {
                sender.sendMessage(ColorUtils.error("Tylko OP moze testowac rozdanie rang."));
                return true;
            }
            if (plugin.getRankGiveawayManager().tryTestDraw(player)) {
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
            } else {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }
            return true;
        }

        switch (subCommand) {
            case "reload" -> {
                plugin.getConfigManager().reloadCodeConfig();
                plugin.getCodeManager().reload();
                if (plugin.getRankGiveawayManager() != null) {
                    plugin.getRankGiveawayManager().reload();
                }
                sender.sendMessage(ColorUtils.colorizeComponent(config.getCodeReloaded()));
            }
            case "create" -> {
                if (args.length < 2) {
                sender.sendMessage(ColorUtils.usage("/akod create <nazwa> [czas_w_minutach]"));
                    return true;
                }
                String name = args[1];
                int time = 0;
                if (args.length >= 3) {
                    try {
                        time = Integer.parseInt(args[2]);
                    } catch (NumberFormatException e) {
                        sender.sendMessage(ColorUtils.error("Nieprawidłowy czas!"));
                        return true;
                    }
                }

                CodeConfig.CodeEntry entry = new CodeConfig.CodeEntry(name, time);
                config.addCode(name, entry);
                sender.sendMessage(ColorUtils.colorizeComponent(
                    config.getCodeCreated().replace("{code}", name)
                ));
            }
            case "delete" -> {
                if (args.length < 2) {
                sender.sendMessage(ColorUtils.usage("/akod delete <nazwa>"));
                    return true;
                }
                String name = args[1];
                if (config.getCode(name) == null) {
                    sender.sendMessage(ColorUtils.colorizeComponent(config.getCodeNotFound()));
                    return true;
                }
                plugin.getCodeManager().stopCode(name);
                config.removeCode(name);
                sender.sendMessage(ColorUtils.colorizeComponent(
                    config.getCodeDeleted().replace("{code}", name)
                ));
            }
            case "start" -> {
                if (args.length < 2) {
                sender.sendMessage(ColorUtils.usage("/akod start <nazwa>"));
                    return true;
                }
                String name = args[1];
                if (config.getCode(name) == null) {
                    sender.sendMessage(ColorUtils.colorizeComponent(config.getCodeNotFound()));
                    return true;
                }
                plugin.getCodeManager().startCode(name);
                sender.sendMessage(ColorUtils.colorizeComponent(
                    config.getCodeStarted().replace("{code}", name)
                ));
            }
            case "stop" -> {
                if (args.length < 2) {
                sender.sendMessage(ColorUtils.usage("/akod stop <nazwa>"));
                    return true;
                }
                String name = args[1];
                if (config.getCode(name) == null) {
                    sender.sendMessage(ColorUtils.colorizeComponent(config.getCodeNotFound()));
                    return true;
                }
                plugin.getCodeManager().stopCode(name);
                sender.sendMessage(ColorUtils.colorizeComponent(
                    config.getCodeStopped().replace("{code}", name)
                ));
            }
            case "additem" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ColorUtils.error("Ta komenda jest tylko dla graczy!"));
                    return true;
                }
                if (args.length < 2) {
                sender.sendMessage(ColorUtils.usage("/akod additem <nazwa>"));
                    return true;
                }
                String name = args[1];
                CodeConfig.CodeEntry code = config.getCode(name);
                if (code == null) {
                    sender.sendMessage(ColorUtils.colorizeComponent(config.getCodeNotFound()));
                    return true;
                }

                ItemStack item = player.getInventory().getItemInMainHand();
                if (item.getType().isAir()) {
                    sender.sendMessage(ColorUtils.error("Trzymaj przedmiot w ręce!"));
                    return true;
                }

                String itemData = ItemSerializer.serializeItem(item);
                code.addItem(itemData);
                config.save();

                sender.sendMessage(ColorUtils.colorizeComponent(
                    config.getCodeItemAdded().replace("{code}", name)
                ));
            }
            case "addcmd" -> {
                if (args.length < 3) {
                sender.sendMessage(ColorUtils.usage("/akod addcmd <nazwa> <komenda>"));
                    return true;
                }
                String name = args[1];
                CodeConfig.CodeEntry code = config.getCode(name);
                if (code == null) {
                    sender.sendMessage(ColorUtils.colorizeComponent(config.getCodeNotFound()));
                    return true;
                }

                StringBuilder cmdBuilder = new StringBuilder();
                for (int i = 2; i < args.length; i++) {
                    cmdBuilder.append(args[i]).append(" ");
                }
                String cmd = cmdBuilder.toString().trim();

                code.addCommand(cmd);
                config.save();

                sender.sendMessage(ColorUtils.colorizeComponent("&#00FF3B✔ &8Ξ &#4EFA76Dodano komendę do kodu &e" + name + "&a!"));
            }
            case "list" -> {
                StringBuilder codes = new StringBuilder();
                for (String codeName : config.getCodes().keySet()) {
                    CodeConfig.CodeEntry entry = config.getCode(codeName);
                    String status = entry.isActive() ? "&a●" : "&c●";
                    codes.append(status).append(" &e").append(codeName).append("&7, ");
                }
                if (codes.length() > 0) {
                    codes.setLength(codes.length() - 2);
                }
                sender.sendMessage(ColorUtils.colorizeComponent(
                    config.getCodeList().replace("{codes}", codes.toString())
                ));
            }
            case "reset" -> {
                if (args.length < 2) {
                sender.sendMessage(ColorUtils.usage("/akod reset <nazwa> [nick]"));
                    return true;
                }
                String name = args[1];
                if (config.getCode(name) == null) {
                    sender.sendMessage(ColorUtils.colorizeComponent(config.getCodeNotFound()));
                    return true;
                }

                if (args.length >= 3) {
                    org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
                    if (!target.hasPlayedBefore() && !target.isOnline()) {
                        sender.sendMessage(ColorUtils.colorizeComponent(
                            plugin.getConfigManager().getMessagesConfig().getPlayerNotFound()
                        ));
                        return true;
                    }
                    plugin.getCodeManager().resetCodeUsageForPlayer(name, target.getUniqueId());
                    sender.sendMessage(ColorUtils.colorizeComponent(
                        config.getCodeResetPlayer()
                            .replace("{code}", name)
                            .replace("{player}", target.getName())
                    ));
                } else {
                    plugin.getCodeManager().resetCodeUsageForAll(name);
                    sender.sendMessage(ColorUtils.colorizeComponent(
                        config.getCodeResetAll().replace("{code}", name)
                    ));
                }
            }
            default -> sendHelp(sender);
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        ColorUtils.sendUsage(sender,
            "/akod create <nazwa> [czas]",
            "/akod delete <nazwa>",
            "/akod start <nazwa>",
            "/akod stop <nazwa>",
            "/akod additem <nazwa>",
            "/akod addcmd <nazwa> <komenda>",
            "/akod list",
            "/akod reset <nazwa> [nick]",
            "/akod rozdanie rang",
            "/akod reload"
        );
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = List.of("reload", "create", "delete", "start", "stop", "additem", "addcmd", "list", "reset", "rozdanie");
            for (String sub : subCommands) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    completions.add(sub);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("rozdanie")) {
            if ("rang".startsWith(args[1].toLowerCase())) {
                completions.add("rang");
            }
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("delete") || sub.equals("start") || sub.equals("stop") || sub.equals("additem") || sub.equals("addcmd") || sub.equals("reset")) {
                CodeConfig config = plugin.getConfigManager().getCodeConfig();
                for (String code : config.getCodes().keySet()) {
                    if (code.toLowerCase().startsWith(args[1].toLowerCase())) {
                        completions.add(code);
                    }
                }
            }
        } else if (args.length == 3) {
            String sub = args[0].toLowerCase();
            if (sub.equals("reset")) {
                String partial = args[2].toLowerCase();
                for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getName().toLowerCase().startsWith(partial)) {
                        completions.add(player.getName());
                    }
                }
            }
        }

        return completions;
    }
}
