package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
import dev.grzybalson.managers.TurboDropManager;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TurboDropCommand implements CommandExecutor, TabCompleter {

    private final HollyCore plugin;
    private final TurboDropManager turboDropManager;

    public TurboDropCommand(HollyCore plugin, TurboDropManager turboDropManager) {
        this.plugin = plugin;
        this.turboDropManager = turboDropManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("Holly.cmd.turbodrop")) {
            sender.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.turbodrop"));
            return true;
        }

        if (args.length == 0) {
            ColorUtils.sendUsage(sender,
                "/" + label + " <mnoznik> <czas>",
                "/" + label + " cancel",
                "/" + label + " reload"
            );
            return true;
        }

        if (args.length == 1) {
            String arg = args[0].toLowerCase(Locale.ROOT);
            if (arg.equals("reload")) {
                turboDropManager.reload();
                sender.sendMessage(ColorUtils.colorizeComponent(turboDropManager.getMsgReloadSuccess()));
                return true;
            }

            if (arg.equals("cancel") || arg.equals("stop") || arg.equals("off")) {
                if (!turboDropManager.isTurboDropActive()) {
                    sender.sendMessage(ColorUtils.colorizeComponent(turboDropManager.getMsgNoActiveEvent()));
                    return true;
                }
                turboDropManager.stopTurboDrop();
                if (turboDropManager.getMsgStopBroadcast() != null && !turboDropManager.getMsgStopBroadcast().isBlank()) {
                    Bukkit.broadcast(ColorUtils.colorizeComponent(turboDropManager.getMsgStopBroadcast()));
                }
                sender.sendMessage(ColorUtils.colorizeComponent(turboDropManager.getMsgStopSender()));
                return true;
            }

            ColorUtils.sendUsage(sender,
                "/" + label + " <mnoznik> <czas>",
                "/" + label + " cancel",
                "/" + label + " reload"
            );
            return true;
        }

        double multiplier;
        try {
            multiplier = Double.parseDouble(args[0].replace(',', '.'));
        } catch (NumberFormatException e) {
            sender.sendMessage(ColorUtils.error("Nieprawidłowy mnożnik! Podaj liczbę większą od 0 (np. 2 lub 1.5)."));
            return true;
        }

        if (multiplier <= 0) {
            sender.sendMessage(ColorUtils.error("Mnożnik musi być większy od 0!"));
            return true;
        }

        String timeArg = String.join("", Arrays.copyOfRange(args, 1, args.length));
        long durationMillis = parseDurationToMillis(timeArg);

        if (durationMillis <= 0) {
            sender.sendMessage(ColorUtils.error("Nieprawidłowy format czasu! Przykład: 2h32m32s, 30m, 1h"));
            return true;
        }

        turboDropManager.startTurboDrop(multiplier, durationMillis);

        String multStr = TurboDropManager.formatMultiplier(multiplier);
        String durationStr = TurboDropManager.formatDuration(durationMillis / 1000L);

        String senderMsg = turboDropManager.getMsgStartSender()
            .replace("{MULTIPLIER}", multStr)
            .replace("{TIME}", durationStr);
        sender.sendMessage(ColorUtils.colorizeComponent(senderMsg));

        String broadcastMsg = turboDropManager.getMsgStartBroadcast()
            .replace("{MULTIPLIER}", multStr)
            .replace("{TIME}", durationStr);
        if (broadcastMsg != null && !broadcastMsg.isBlank()) {
            Bukkit.broadcast(ColorUtils.colorizeComponent(broadcastMsg));
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("Holly.cmd.turbodrop")) {
            return List.of();
        }

        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            List<String> options = List.of("2", "1.5", "3", "cancel", "reload", "stop");
            String input = args[0].toLowerCase(Locale.ROOT);
            for (String opt : options) {
                if (opt.startsWith(input)) {
                    completions.add(opt);
                }
            }
            return completions;
        }

        if (args.length == 2) {
            String firstArg = args[0].toLowerCase(Locale.ROOT);
            if (firstArg.equals("cancel") || firstArg.equals("stop") || firstArg.equals("off") || firstArg.equals("reload")) {
                return List.of();
            }

            List<String> completions = new ArrayList<>();
            List<String> options = List.of("2h32m32s", "30m", "1h", "2h", "1d");
            String input = args[1].toLowerCase(Locale.ROOT);
            for (String opt : options) {
                if (opt.startsWith(input)) {
                    completions.add(opt);
                }
            }
            return completions;
        }

        return List.of();
    }

    public static long parseDurationToMillis(String input) {
        if (input == null || input.isBlank()) {
            return -1L;
        }
        String text = input.trim().toLowerCase(Locale.ROOT);

        if (text.matches("^\\d+$")) {
            try {
                long seconds = Long.parseLong(text);
                return seconds > 0 ? seconds * 1000L : -1L;
            } catch (NumberFormatException e) {
                return -1L;
            }
        }

        Pattern tokenPattern = Pattern.compile("(\\d+)\\s*([dhms])", Pattern.CASE_INSENSITIVE);
        Matcher tokenMatcher = tokenPattern.matcher(text);

        long totalSeconds = 0L;
        boolean foundAny = false;

        while (tokenMatcher.find()) {
            foundAny = true;
            try {
                long amount = Long.parseLong(tokenMatcher.group(1));
                String unit = tokenMatcher.group(2).toLowerCase(Locale.ROOT);
                switch (unit) {
                    case "d" -> totalSeconds += amount * 86400L;
                    case "h" -> totalSeconds += amount * 3600L;
                    case "m" -> totalSeconds += amount * 60L;
                    case "s" -> totalSeconds += amount;
                }
            } catch (NumberFormatException e) {
                return -1L;
            }
        }

        if (!foundAny || totalSeconds <= 0) {
            return -1L;
        }

        return totalSeconds * 1000L;
    }
}
