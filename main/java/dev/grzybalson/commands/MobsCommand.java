package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
import dev.grzybalson.modules.mobs.MobsManager;
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

public class MobsCommand implements CommandExecutor, TabCompleter {

    private final HollyCore plugin;

    public MobsCommand(HollyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!plugin.isMobsFeatureEnabled()) {
            return true;
        }

        if (!sender.hasPermission("Holly.cmd.mobs")) {
            sender.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.mobs"));
            return true;
        }

        MobsManager manager = plugin.getMobsManager();
        if (manager == null) {
            sender.sendMessage(ColorUtils.error("Modul mobow nie jest aktywny."));
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("reload")) {
            manager.reload();
            sender.sendMessage(ColorUtils.success("Przeladowano addons/mobs/mobs.yml."));
            return true;
        }

        if (sub.equals("list")) {
            List<MobsManager.MobSpawn> spawns = manager.getSpawns();
            if (spawns.isEmpty()) {
                sender.sendMessage(ColorUtils.info("Brak zapisanych spawnow mobow."));
                return true;
            }
            sender.sendMessage(ColorUtils.info("Spawny mobow:"));
            for (MobsManager.MobSpawn spawn : spawns) {
                sender.sendMessage(ColorUtils.colorizeComponent("&8- &f" + manager.formatSpawn(spawn)));
            }
            return true;
        }

        if (sub.equals("delete")) {
            if (args.length >= 2) {
                if (manager.deleteSpawn(args[1])) {
                    sender.sendMessage(ColorUtils.success("Usunieto spawn moba: " + args[1]));
                } else {
                    sender.sendMessage(ColorUtils.error("Nie znaleziono spawna o id: " + args[1]));
                }
                return true;
            }

            if (!(sender instanceof Player player)) {
                sender.sendMessage(ColorUtils.error("Konsola musi podac id: /" + label + " delete <id>"));
                return true;
            }

            MobsManager.MobSpawn deleted = manager.deleteNearest(player.getLocation());
            if (deleted == null) {
                sender.sendMessage(ColorUtils.error("Nie ma spawna mobow w poblizu."));
                return true;
            }

            sender.sendMessage(ColorUtils.success("Usunieto najblizszy spawn moba: " + deleted.id()));
            return true;
        }

        if (sub.equals("setrespawn") && args.length == 2) {
            handleSetRespawn(sender, label, manager, args[1]);
            return true;
        }

        if (args.length == 3 && args[1].equalsIgnoreCase("setrespawn")) {
            if (manager.normalizeType(args[0]) == null) {
                sender.sendMessage(ColorUtils.error("Nieprawidlowy typ. Dostepne: zombie, skeleton, spider albo /" + label + " setrespawn <czas>."));
                return true;
            }
            handleSetRespawn(sender, label, manager, args[2]);
            return true;
        }

        sendUsage(sender, label);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!plugin.isMobsFeatureEnabled()) {
            return List.of();
        }

        MobsManager manager = plugin.getMobsManager();
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            for (String value : List.of("setrespawn", "zombie", "skeleton", "spider", "delete", "list", "reload")) {
                if (value.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    completions.add(value);
                }
            }
            return completions;
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("setrespawn")) {
                completions.add("30s");
                completions.add("1m");
                completions.add("5m");
            } else if (List.of("zombie", "skeleton", "spider").contains(args[0].toLowerCase(Locale.ROOT))
                && "setrespawn".startsWith(args[1].toLowerCase(Locale.ROOT))) {
                completions.add("setrespawn");
            } else if (args[0].equalsIgnoreCase("delete") && manager != null) {
                for (MobsManager.MobSpawn spawn : manager.getSpawns()) {
                    if (spawn.id().startsWith(args[1].toLowerCase(Locale.ROOT))) {
                        completions.add(spawn.id());
                    }
                }
            }
            return completions;
        }

        if (args.length == 3 && args[1].equalsIgnoreCase("setrespawn")) {
            completions.add("30s");
            completions.add("1m");
            completions.add("5m");
        }

        return completions;
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage(ColorUtils.usage("/" + label + " setrespawn <czas>"));
        sender.sendMessage(ColorUtils.usage("/" + label + " delete [id]"));
        sender.sendMessage(ColorUtils.usage("/" + label + " list"));
        sender.sendMessage(ColorUtils.usage("/" + label + " reload"));
    }

    private void handleSetRespawn(CommandSender sender, String label, MobsManager manager, String rawTime) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.error("Tylko gracz moze ustawic lokalizacje spawna."));
            return;
        }

        long seconds = parseDurationSeconds(rawTime);
        if (seconds <= 0L) {
            sender.sendMessage(ColorUtils.error("Czas musi byc dodatni, np. 30, 30s, 5m."));
            return;
        }

        MobsManager.MobSpawn spawn = manager.addSpawn(player.getLocation(), seconds);
        if (spawn == null) {
            sender.sendMessage(ColorUtils.error("Nie udalo sie dodac losowego spawna moba."));
            return;
        }

        sender.sendMessage(ColorUtils.success("Dodano losowy spawn mobow co " + seconds + "s po zabiciu. ID: " + spawn.id()));
    }

    private long parseDurationSeconds(String raw) {
        String value = raw.toLowerCase(Locale.ROOT).trim();
        long multiplier = 1L;
        if (value.endsWith("s")) {
            value = value.substring(0, value.length() - 1);
        } else if (value.endsWith("m")) {
            value = value.substring(0, value.length() - 1);
            multiplier = 60L;
        } else if (value.endsWith("h")) {
            value = value.substring(0, value.length() - 1);
            multiplier = 3600L;
        }

        try {
            return Long.parseLong(value) * multiplier;
        } catch (NumberFormatException exception) {
            return -1L;
        }
    }
}
