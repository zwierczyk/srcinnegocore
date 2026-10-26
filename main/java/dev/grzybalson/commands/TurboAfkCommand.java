package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
import dev.grzybalson.managers.AfkManager;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.TimeUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TurboAfkCommand implements CommandExecutor, TabCompleter {

    private static final Pattern DURATION = Pattern.compile("(?i)^(\\d+)\\s*([smhd]?)$");

    private final HollyCore plugin;

    public TurboAfkCommand(HollyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("Holly.cmd.afk.admin")) {
            sender.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.afk.admin"));
            return true;
        }

        if (args.length < 2) {
            ColorUtils.sendUsage(sender, "/" + label + " <mnożnik> <czas>");
            return true;
        }

        AfkManager afkManager = plugin.getAfkManager();
        if (afkManager == null) {
            sender.sendMessage(ColorUtils.error("System AFK jest wyłączony!"));
            return true;
        }

        int multiplier;
        try {
            multiplier = Integer.parseInt(args[0]);
        } catch (NumberFormatException exception) {
            sender.sendMessage(ColorUtils.error("Nieprawidłowy mnożnik!"));
            return true;
        }
        if (multiplier < 1) {
            sender.sendMessage(ColorUtils.error("Mnożnik musi być co najmniej 1!"));
            return true;
        }

        Long duration = parseDuration(args[1]);
        if (duration == null || duration < 1000L) {
            sender.sendMessage(ColorUtils.error("Nieprawidłowy czas! Użyj np. 30m, 1h, 2d"));
            return true;
        }

        afkManager.enableTurbo(multiplier, duration);
        sender.sendMessage(ColorUtils.colorizeComponent(
            "&aWłączono turboAfk &2x" + multiplier + "&a na &2" + TimeUtil.convert(duration)
        ));
        return true;
    }

    public static Long parseDuration(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Matcher matcher = DURATION.matcher(raw.trim());
        if (!matcher.matches()) {
            return null;
        }
        long amount = Long.parseLong(matcher.group(1));
        String unit = matcher.group(2) == null || matcher.group(2).isBlank()
            ? "s"
            : matcher.group(2).toLowerCase(Locale.ROOT);
        return switch (unit) {
            case "d" -> TimeUnit.DAYS.toMillis(amount);
            case "h" -> TimeUnit.HOURS.toMillis(amount);
            case "m" -> TimeUnit.MINUTES.toMillis(amount);
            default -> TimeUnit.SECONDS.toMillis(amount);
        };
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            for (String value : List.of("2", "3", "5")) {
                if (value.startsWith(args[0])) {
                    completions.add(value);
                }
            }
        } else if (args.length == 2) {
            for (String value : List.of("30m", "1h", "2h", "1d")) {
                if (value.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    completions.add(value);
                }
            }
        }
        return completions;
    }
}
