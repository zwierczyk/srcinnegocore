package dev.grzybalson.anarchiagg.coach;

import dev.grzybalson.anarchiagg.coach.gui.CoachMenuGUI;
import dev.grzybalson.anarchiagg.util.notice.Notice;
import dev.grzybalson.anarchiagg.util.notice.NoticeType;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class CoachCommand implements CommandExecutor, TabCompleter {

    private final CoachManager manager;
    private final CoachConfig config;

    public CoachCommand(CoachManager manager, CoachConfig config) {
        this.manager = manager;
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cTa komenda jest dostepna tylko dla graczy!");
                return true;
            }
            if (!player.hasPermission("Holly.cmd.trener") && !player.isOp()) {
                player.sendMessage("§cBrak uprawnien!");
                return true;
            }
            new CoachMenuGUI(manager, config, player).open();
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "odlamki", "odlami", "shards", "fragmenty" -> {
                if (!sender.hasPermission("Holly.cmd.trener.admin") && !sender.isOp()) {
                    sender.sendMessage("§cBrak uprawnien!");
                    return true;
                }
                if (args.length < 3) {
                    ColorUtils.sendUsage(sender, "/trener odlamki <nick> <ilosc>");
                    return true;
                }
                String targetName = args[1];
                int amount;
                try {
                    amount = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cNiepoprawna ilosc!");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(targetName);
                if (target == null) {
                    Notice.sendC(sender, NoticeType.CHAT, "&cPodany gracz nie jest online!");
                    return true;
                }
                target.getInventory().addItem(manager.createShardItem(amount));
                if (sender instanceof Player p) {
                    Notice.sendP(p, NoticeType.CHAT, "&#00FF3B✔ &8Ξ &#4EFA76Pomyślnie przyznano odłamki!");
                } else {
                    Notice.sendC(sender, NoticeType.CHAT, "&aPomyslnie przyznano odlamki!");
                }
                return true;
            }
            case "potka" -> {
                if (!sender.hasPermission("Holly.cmd.trener.admin") && !sender.isOp()) {
                    sender.sendMessage("§cBrak uprawnien!");
                    return true;
                }
                if (args.length < 3) {
                    ColorUtils.sendUsage(sender, "/trener potka <nick> <ilosc>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage("§cGracz jest offline!");
                    return true;
                }
                int amount;
                try {
                    amount = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cNiepoprawna ilosc!");
                    return true;
                }
                target.getInventory().addItem(manager.createAllSkillsPotionItem(amount));
                if (sender instanceof Player p) {
                    Notice.sendP(p, NoticeType.CHAT, "&#00FF3B✔ &8Ξ &#4EFA76Pomyślnie przyznano potkę!");
                } else {
                    Notice.sendC(sender, NoticeType.CHAT, "&aPomyslnie przyznano potke!");
                }
                return true;
            }
            case "set" -> {
                if (!sender.hasPermission("Holly.cmd.trener.admin") && !sender.isOp()) {
                    sender.sendMessage("§cBrak uprawnien!");
                    return true;
                }
                // /trener set <nick> <type> <%>   lub   /trener set <type> <%>  (dla siebie)
                String targetName;
                String typeRaw;
                String percentRaw;
                if (args.length >= 4) {
                    targetName = args[1];
                    typeRaw = args[2];
                    percentRaw = args[3];
                } else if (args.length == 3 && sender instanceof Player) {
                    targetName = sender.getName();
                    typeRaw = args[1];
                    percentRaw = args[2];
                } else {
                    ColorUtils.sendUsage(sender, "/trener set <nick> <type> <procent 0-120>");
                    sender.sendMessage("§7Typy: sila, zrecznosc, obrona, szczescie, inteligencja");
                    return true;
                }

                CoachSkill skill = manager.parseSkill(typeRaw);
                if (skill == null) {
                    sender.sendMessage("§cNieznany typ skilla! Uzyj: sila, zrecznosc, obrona, szczescie, inteligencja");
                    return true;
                }

                int percent;
                try {
                    percentRaw = percentRaw.replace("%", "").trim();
                    percent = Integer.parseInt(percentRaw);
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cNiepoprawny procent! Podaj liczbe 0-120.");
                    return true;
                }
                if (percent < 0 || percent > 120) {
                    sender.sendMessage("§cProcent musi byc w zakresie 0-120!");
                    return true;
                }

                OfflinePlayer offline = Bukkit.getOfflinePlayer(targetName);
                UUID uuid = offline.getUniqueId();
                if (!offline.hasPlayedBefore() && !offline.isOnline() && Bukkit.getPlayerExact(targetName) == null) {
                    // nadal pozwalamy po UUID offline nicka z cache Bukkita
                }

                int level = manager.setSkillByProgressPercent(uuid, skill, percent);
                int actualPercent = manager.getProgress(skill, level);
                String name = offline.getName() != null ? offline.getName() : targetName;
                sender.sendMessage(ColorUtils.colorize(
                    "&#00FF3B✔ &8Ξ &#4EFA76Ustawiono &f" + skill.getId()
                        + " &#4EFA76gracza &f" + name
                        + " &#4EFA76na &f" + actualPercent + "% &#4EFA76(poziom &f" + level + "&#4EFA76)."
                ));
                return true;
            }
            case "reset" -> {
                if (!sender.hasPermission("Holly.cmd.trener.admin") && !sender.isOp()) {
                    sender.sendMessage("§cBrak uprawnien!");
                    return true;
                }
                if (args.length < 2) {
                    ColorUtils.sendUsage(sender, "/trener reset <nick>");
                    return true;
                }
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                manager.reset(target.getUniqueId());
                String name = target.getName() != null ? target.getName() : args[1];
                sender.sendMessage(ColorUtils.colorize(config.getMessages().getReset().replace("%player%", name)));
                return true;
            }
            case "reload" -> {
                if (!sender.hasPermission("Holly.cmd.trener.admin") && !sender.isOp()) {
                    sender.sendMessage("§cBrak uprawnien!");
                    return true;
                }
                config.load(true);
                manager.reloadRuntime();
                sender.sendMessage(ColorUtils.colorize(config.getMessages().getReloaded()));
                return true;
            }
            default -> {
                ColorUtils.sendUsage(sender,
                    "/trener",
                    "/trener odlamki <nick> <ilosc>",
                    "/trener potka <nick> <ilosc>",
                    "/trener set <nick> <type> <procent 0-120>",
                    "/trener reset <nick>",
                    "/trener reload"
                );
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(List.of("odlamki", "potka", "set", "reset", "reload"), args[0]);
        }
        if (!sender.hasPermission("Holly.cmd.trener.admin") && !sender.isOp()) {
            return List.of();
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("set") || sub.equals("reset") || sub.equals("odlamki") || sub.equals("potka")) {
                List<String> names = new ArrayList<>();
                for (Player online : Bukkit.getOnlinePlayers()) {
                    names.add(online.getName());
                }
                if (sub.equals("set")) {
                    names.addAll(List.of("sila", "zrecznosc", "obrona", "szczescie", "inteligencja"));
                }
                return filter(names, args[1]);
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            // albo type (gdy args[1]=nick), albo procent (gdy args[1]=type)
            if (manager.parseSkill(args[1]) != null) {
                return filter(List.of("0", "10", "20", "50", "100", "120"), args[2]);
            }
            return filter(List.of("sila", "zrecznosc", "obrona", "szczescie", "inteligencja"), args[2]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("set")) {
            return filter(List.of("0", "10", "20", "50", "100", "120"), args[3]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String input) {
        String needle = input == null ? "" : input.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(needle)) {
                out.add(option);
            }
        }
        return out;
    }
}
