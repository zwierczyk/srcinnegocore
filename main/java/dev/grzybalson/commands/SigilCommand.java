package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
import dev.grzybalson.modules.sigil.SigilType;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
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
import java.util.concurrent.TimeUnit;

public class SigilCommand implements CommandExecutor, TabCompleter {

    private final HollyCore plugin;

    public SigilCommand(HollyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (plugin.getSigilManager() == null) {
            sender.sendMessage(ColorUtils.error("Modul sigil jest wylaczony."));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("Holly.cmd.sigil.admin")) {
                sender.sendMessage(ColorUtils.noPermission(label + " reload", "Holly.cmd.sigil.admin"));
                return true;
            }
            plugin.getSigilManager().reload();
            sender.sendMessage(ColorUtils.success("Przeladowano sigile."));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("giveall")) {
            if (!sender.hasPermission("Holly.cmd.sigil.admin")) {
                sender.sendMessage(ColorUtils.noPermission(label + " giveall", "Holly.cmd.sigil.admin"));
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(ColorUtils.usage("/sigil giveall <gracz> [moc%] [czas]"));
                sender.sendMessage(ColorUtils.error("Przyklad: /sigil giveall Steve 30 1d"));
                return true;
            }

            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(ColorUtils.error("Ten gracz musi byc online."));
                return true;
            }

            double power = args.length >= 3 ? parsePower(args[2], 30.0D) : 30.0D;
            long durationMs = args.length >= 4
                ? parseDurationMillis(args[3], TimeUnit.DAYS.toMillis(1L))
                : TimeUnit.DAYS.toMillis(1L);

            plugin.getSigilManager().giveAllTemporarySigils(target, power, durationMs).whenComplete((ignored, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (error != null) {
                        sender.sendMessage(ColorUtils.error("Nie udalo sie nadac sigili."));
                        return;
                    }
                    sender.sendMessage(ColorUtils.success(
                        "Nadano wszystkie sigile (" + SigilType.formatValue(power) + "%, "
                            + formatDurationShort(durationMs) + ") graczowi " + target.getName() + "."));
                }));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("give")) {
            if (!sender.hasPermission("Holly.cmd.sigil.admin")) {
                sender.sendMessage(ColorUtils.noPermission(label + " give", "Holly.cmd.sigil.admin"));
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage(ColorUtils.usage("/sigil give <gracz> <typ> [moc]"));
                return true;
            }

            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(ColorUtils.error("Ten gracz musi byc online."));
                return true;
            }

            SigilType type = SigilType.fromString(args[2]);
            if (type == null) {
                sender.sendMessage(ColorUtils.error("Nieprawidlowy typ sigila."));
                return true;
            }

            double power = args.length >= 4 ? parsePower(args[3], 10.0D) : 10.0D;
            plugin.getSigilManager().giveSigil(target, type, power);
            sender.sendMessage(ColorUtils.success("Nadano sigil " + type.getDisplayName()
                + " (" + SigilType.formatValue(power) + "%) graczowi " + target.getName() + "."));
            return true;
        }

        if (args.length > 0 && (args[0].equalsIgnoreCase("reset") || args[0].equalsIgnoreCase("zdejmij"))) {
            if (!sender.hasPermission("Holly.cmd.sigil.admin")) {
                sender.sendMessage(ColorUtils.noPermission(label + " " + args[0].toLowerCase(Locale.ROOT), "Holly.cmd.sigil.admin"));
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(ColorUtils.usage("/sigil zdejmij <gracz|*>"));
                return true;
            }

            if (args[1].equals("*") || args[1].equalsIgnoreCase("all") || args[1].equalsIgnoreCase("wszyscy")) {
                plugin.getSigilManager().resetAllPlayers().whenComplete((count, error) ->
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (error != null) {
                            sender.sendMessage(ColorUtils.error("Nie udalo sie wyczyscic sigili w MongoDB."));
                            return;
                        }
                        sender.sendMessage(ColorUtils.success(
                            "Zdjeto sigile wszystkim graczom. MongoDB: " + count + " rekordow."));
                    }));
                return true;
            }

            OfflinePlayer target = resolveOfflinePlayer(args[1]);
            if (target == null) {
                sender.sendMessage(ColorUtils.error("Nie znaleziono gracza."));
                return true;
            }

            plugin.getSigilManager().resetPlayer(target.getUniqueId()).thenRun(() ->
                Bukkit.getScheduler().runTask(plugin, () ->
                    sender.sendMessage(ColorUtils.success("Zdjeto sigile gracza " + target.getName() + "."))));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.error("Ta komenda jest tylko dla gracza."));
            return true;
        }

        if (!player.hasPermission("Holly.cmd.sigil")) {
            sender.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.sigil"));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("debug")) {
            plugin.getSigilManager().sendDebug(player);
            return true;
        }

        plugin.getSigilManager().openGui(player);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            if (sender.hasPermission("Holly.cmd.sigil")) {
                options.add("debug");
            }
            if (sender.hasPermission("Holly.cmd.sigil.admin")) {
                options.add("give");
                options.add("giveall");
                options.add("reload");
                options.add("reset");
                options.add("zdejmij");
            }
            return matching(options, args[0]);
        }

        if (!sender.hasPermission("Holly.cmd.sigil.admin")) {
            return List.of();
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("give")
            || args[0].equalsIgnoreCase("giveall")
            || args[0].equalsIgnoreCase("reset")
            || args[0].equalsIgnoreCase("zdejmij"))) {
            List<String> names = new ArrayList<>();
            if (args[0].equalsIgnoreCase("reset") || args[0].equalsIgnoreCase("zdejmij")) {
                names.add("*");
            }
            names.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
            return matching(names, args[1]);
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("giveall")) {
            return matching(List.of("10", "15", "25", "30"), args[2]);
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("giveall")) {
            return matching(List.of("1d", "12h", "6h", "1h"), args[3]);
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("give") && plugin.getSigilManager() != null) {
            return matching(plugin.getSigilManager().getTypeNames(), args[2]);
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
            return matching(List.of("1", "5", "10", "15", "25", "30"), args[3]);
        }

        return List.of();
    }

    private List<String> matching(List<String> options, String input) {
        List<String> completions = new ArrayList<>();
        String lowerInput = input.toLowerCase(Locale.ROOT);
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lowerInput)) {
                completions.add(option);
            }
        }
        return completions;
    }

    private OfflinePlayer resolveOfflinePlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        return offline.hasPlayedBefore() ? offline : null;
    }

    private double parsePower(String raw, double fallback) {
        try {
            return Double.parseDouble(raw.replace(',', '.'));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private long parseDurationMillis(String raw, long fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        try {
            if (value.endsWith("d")) {
                return (long) (Double.parseDouble(value.substring(0, value.length() - 1)) * TimeUnit.DAYS.toMillis(1L));
            }
            if (value.endsWith("h")) {
                return (long) (Double.parseDouble(value.substring(0, value.length() - 1)) * TimeUnit.HOURS.toMillis(1L));
            }
            if (value.endsWith("m")) {
                return (long) (Double.parseDouble(value.substring(0, value.length() - 1)) * TimeUnit.MINUTES.toMillis(1L));
            }
            return (long) (Double.parseDouble(value) * TimeUnit.DAYS.toMillis(1L));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String formatDurationShort(long millis) {
        long days = TimeUnit.MILLISECONDS.toDays(millis);
        if (days > 0) {
            return days + "d";
        }
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        if (hours > 0) {
            return hours + "h";
        }
        long minutes = Math.max(1L, TimeUnit.MILLISECONDS.toMinutes(millis));
        return minutes + "m";
    }
}
