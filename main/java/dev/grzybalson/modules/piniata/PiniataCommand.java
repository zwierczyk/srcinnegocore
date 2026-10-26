package dev.grzybalson.modules.piniata;

import dev.grzybalson.HollyCore;
import dev.grzybalson.anarchiagg.util.TimeUtil;
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

public class PiniataCommand implements CommandExecutor, TabCompleter {

    private final HollyCore plugin;
    private final PiniataManager manager;

    public PiniataCommand(HollyCore plugin, PiniataManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("Holly.cmd.piniata.admin")) {
            sender.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.piniata.admin"));
            return true;
        }

        if (label.equalsIgnoreCase("adminevent")) {
            return handleAdminEvent(sender, args);
        }

        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "start" -> {
                long delay = 0L;
                if (args.length >= 2) {
                    delay = parseDelay(args[1]);
                    if (delay < 0L) {
                        sender.sendMessage(ColorUtils.colorizeComponent(
                            "&#FF0000✘ &8Ξ &cPodaj poprawny czas, np. &f30s&c, &f5m&c, &f1h&c."
                        ));
                        return true;
                    }
                }
                manager.start(sender, delay);
            }
            case "stop" -> manager.stop(sender);
            case "setspawn" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ColorUtils.colorizeComponent(
                        plugin.getConfigManager().getMessagesConfig().getPlayerOnly()
                    ));
                    return true;
                }
                manager.setSpawn(player.getLocation());
                sender.sendMessage(ColorUtils.colorizeComponent("&8[&dPiniata&8] &aUstawiono spawn piniaty."));
            }
            case "reload" -> {
                manager.reload();
                sender.sendMessage(ColorUtils.colorizeComponent("&8[&dPiniata&8] &aPrzeładowano konfigurację piniaty."));
            }
            default -> sendHelp(sender, label);
        }
        return true;
    }

    private boolean handleAdminEvent(CommandSender sender, String[] args) {
        if (args.length < 2) {
            ColorUtils.sendUsage(sender, "/adminevent piniata <czas>");
            return true;
        }
        String eventName = args[0].toLowerCase(Locale.ROOT);
        if (!eventName.equals("piniata") && !eventName.equals("pinata")) {
            sender.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cDostępne eventy: &fpiniata"));
            return true;
        }
        long delay = parseDelay(args[1]);
        if (delay < 0L) {
            sender.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cPodaj poprawny czas, np. &f30s&c, &f5m&c, &f1h&c."));
            return true;
        }
        manager.start(sender, delay);
        return true;
    }

    private long parseDelay(String input) {
        if (input == null || input.isBlank()) {
            return -1L;
        }
        String value = input.trim().toLowerCase(Locale.ROOT);
        if (value.matches("\\d+")) {
            return Long.parseLong(value) * 1000L;
        }
        long parsed = TimeUtil.parseDateDiff(value, false);
        return parsed <= 0L ? -1L : parsed;
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(ColorUtils.colorizeComponent("&8[&dPiniata&8] &fKomendy:"));
        sender.sendMessage(ColorUtils.colorizeComponent("&8 • &d/" + label + " start [czas] &7- start teraz albo za 30s/5m"));
        sender.sendMessage(ColorUtils.colorizeComponent("&8 • &d/" + label + " stop &7- zatrzymaj event"));
        sender.sendMessage(ColorUtils.colorizeComponent("&8 • &d/" + label + " setspawn &7- spawn tam gdzie stoisz"));
        sender.sendMessage(ColorUtils.colorizeComponent("&8 • &d/" + label + " reload"));
        sender.sendMessage(ColorUtils.colorizeComponent("&8 • &d/adminevent piniata <czas>"));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (!sender.hasPermission("Holly.cmd.piniata.admin")) {
            return completions;
        }
        if (alias.equalsIgnoreCase("adminevent")) {
            if (args.length == 1) {
                if ("piniata".startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    completions.add("piniata");
                }
            } else if (args.length == 2) {
                completions.addAll(List.of("30s", "1m", "5m", "10m"));
            }
            return completions;
        }
        if (args.length == 1) {
            for (String option : List.of("start", "stop", "setspawn", "reload")) {
                if (option.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    completions.add(option);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("start")) {
            completions.addAll(List.of("30s", "1m", "5m", "10m"));
        }
        return completions;
    }
}
