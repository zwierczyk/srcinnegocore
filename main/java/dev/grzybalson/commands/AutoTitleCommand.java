package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class AutoTitleCommand implements CommandExecutor, TabCompleter {

    private static final Pattern TIME_ARG = Pattern.compile("^\\d{1,2}:\\d{2}$");
    private static final Pattern INTERVAL_ARG = Pattern.compile("^(\\d+)(s|m|min|sek|minut)?$", Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("H:mm");

    private final HollyCore plugin;

    public AutoTitleCommand(HollyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("Holly.cmd.autotitle")) {
            sender.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.autotitle"));
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("cancel")) {
            plugin.getAutoTitleManager().cancel();
            sender.sendMessage(ColorUtils.colorizeComponent("&#00FF3B✔ &8Ξ &#4EFA76Anulowano zaplanowany autotitle."));
            return true;
        }

        if (args.length < 3) {
            sendUsage(sender);
            return true;
        }

        String interval = args[args.length - 1];
        if (!INTERVAL_ARG.matcher(interval).matches()) {
            sender.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cNieprawidlowy interwal (np. &f1m&c, &f30s&c)."));
            return true;
        }

        List<String> times = new ArrayList<>(2);
        int textEnd = args.length - 1;
        for (int i = args.length - 2; i >= 0 && times.size() < 2; i--) {
            if (TIME_ARG.matcher(args[i]).matches()) {
                times.add(0, args[i]);
                textEnd = i;
            } else {
                break;
            }
        }

        if (times.isEmpty()) {
            sender.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cBrak godziny w formacie &fHH:mm&c na koncu komendy."));
            sendUsage(sender);
            return true;
        }

        String startTime = times.get(0);
        String endTime = times.size() >= 2 ? times.get(1) : defaultEndTime(startTime);
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < textEnd; i++) {
            if (i > 0) {
                text.append(' ');
            }
            text.append(args[i]);
        }
        if (text.isEmpty()) {
            sender.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cPodaj tekst title przed godzinami."));
            return true;
        }

        if (!plugin.getAutoTitleManager().schedule(text.toString(), startTime, endTime, interval)) {
            sender.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cNieprawidlowe argumenty autotitle."));
            return true;
        }

        sender.sendMessage(ColorUtils.colorizeComponent(
            "&#00FF3B✔ &8Ξ &#4EFA76Autotitle aktywny &8(&f" + startTime + " &8- &f" + endTime
                + "&8, co &f" + interval + "&8, czas &fWarszawa&8)."
        ));
        return true;
    }

    private static String defaultEndTime(String startTime) {
        try {
            return LocalTime.parse(startTime, TIME_FORMAT).plusHours(1).format(TIME_FORMAT);
        } catch (Exception ignored) {
            return "23:59";
        }
    }

    private void sendUsage(CommandSender sender) {
        ColorUtils.sendUsage(sender,
            "/autotitle <tekst> <od HH:mm> [do HH:mm] <co ile>",
            "/autotitle cancel"
        );
        sender.sendMessage(ColorUtils.colorizeComponent(
            "&7Przyklad: &f/autotitle &7NAJWIEKSZE ROZDANIE O 20:00 | MK! &f20:00 22:00 1m"
        ));
        sender.sendMessage(ColorUtils.colorizeComponent(
            "&7Bez godziny &cdo&7: domyslnie +1h od startu. Godziny liczone od &fkonca &7komendy."
        ));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("Holly.cmd.autotitle")) {
            return out;
        }
        if (args.length == 1) {
            if ("cancel".startsWith(args[0].toLowerCase(Locale.ROOT))) {
                out.add("cancel");
            }
        }
        return out;
    }
}
