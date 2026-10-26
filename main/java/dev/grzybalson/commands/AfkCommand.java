package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.configs.AfkConfig;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AfkCommand implements CommandExecutor, TabCompleter {

    private final HollyCore plugin;
    private final Map<String, Location> pendingFirstLocations;
    private final Map<String, Location> pendingSecondLocations;

    public AfkCommand(HollyCore plugin) {
        this.plugin = plugin;
        this.pendingFirstLocations = new HashMap<>();
        this.pendingSecondLocations = new HashMap<>();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("Holly.cmd.afk.admin")) {
            sender.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.afk.admin"));
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (plugin.getAfkManager() != null) {
                plugin.getAfkManager().reload();
            } else {
                plugin.getConfigManager().reloadAfkConfig();
            }
            sender.sendMessage(ColorUtils.success("Przeładowano konfigurację AFK!"));
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("turboafk")) {
            String[] turboArgs = new String[Math.max(0, args.length - 1)];
            if (args.length > 1) {
                System.arraycopy(args, 1, turboArgs, 0, turboArgs.length);
            }
            return new TurboAfkCommand(plugin).onCommand(sender, command, label, turboArgs);
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.colorizeComponent(
                plugin.getConfigManager().getMessagesConfig().getPlayerOnly()
            ));
            return true;
        }

        if (args.length == 0) {
            sendUsage(player);
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("list")) {
            AfkConfig config = plugin.getConfigManager().getAfkConfig();
            if (config.getZones().isEmpty()) {
                player.sendMessage(ColorUtils.info("Brak stref AFK."));
                return true;
            }

            player.sendMessage(ColorUtils.colorizeComponent("&8&m-----------&r &6&lSTREFY AFK &8&m-----------"));
            for (String name : config.getZones().keySet()) {
                player.sendMessage(ColorUtils.colorizeComponent("&7- &e" + name));
            }
            return true;
        }

        if (args.length < 2) {
            sendUsage(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        String zoneName = args.length > 1 ? args[1] : "";

        switch (subCommand) {
            case "create" -> {
                if (zoneName.isEmpty()) {
                    player.sendMessage(ColorUtils.error("Podaj nazwę strefy!"));
                    return true;
                }

                AfkConfig config = plugin.getConfigManager().getAfkConfig();
                if (config.getZones().containsKey(zoneName)) {
                    player.sendMessage(ColorUtils.error("Strefa o tej nazwie już istnieje!"));
                    return true;
                }

                Location loc = player.getLocation();
                pendingFirstLocations.put(zoneName, loc);
                player.sendMessage(ColorUtils.success("Ustawiono pierwszy punkt strefy &e" + zoneName + "&a!"));
                player.sendMessage(ColorUtils.info("Teraz użyj /afk setlocation second " + zoneName));
            }
            case "setlocation" -> {
                if (args.length < 3) {
                    ColorUtils.sendUsage(player, "/afk setlocation <first|second> <nazwa>");
                    return true;
                }

                String locationType = args[1].toLowerCase();
                zoneName = args[2];
                Location loc = player.getLocation();

                if (locationType.equals("first")) {
                    pendingFirstLocations.put(zoneName, loc);
                    player.sendMessage(ColorUtils.success("Ustawiono pierwszy punkt strefy &e" + zoneName + "&a!"));
                } else if (locationType.equals("second")) {
                    Location first = pendingFirstLocations.get(zoneName);
                    if (first == null) {
                        player.sendMessage(ColorUtils.error("Najpierw ustaw pierwszy punkt! /afk create " + zoneName));
                        return true;
                    }

                    AfkConfig.AfkZone zone = new AfkConfig.AfkZone(
                        first.getWorld().getName(),
                        first.getX(), first.getY(), first.getZ(),
                        loc.getX(), loc.getY(), loc.getZ()
                    );

                    plugin.getConfigManager().getAfkConfig().addZone(zoneName, zone);
                    pendingFirstLocations.remove(zoneName);

                    player.sendMessage(ColorUtils.success("Utworzono strefę AFK &e" + zoneName + "&a!"));
                } else {
                    ColorUtils.sendUsage(player, "/afk setlocation <first|second> <nazwa>");
                }
            }
            case "delete" -> {
                AfkConfig config = plugin.getConfigManager().getAfkConfig();
                if (!config.getZones().containsKey(zoneName)) {
                    player.sendMessage(ColorUtils.error("Strefa nie istnieje!"));
                    return true;
                }

                config.getZones().remove(zoneName);
                config.save();
                player.sendMessage(ColorUtils.success("Usunięto strefę &e" + zoneName + "&a!"));
            }
            default -> sendUsage(player);
        }

        return true;
    }

    private void sendUsage(CommandSender sender) {
        ColorUtils.sendUsage(sender,
            "/afk create <nazwa>",
            "/afk setlocation <first|second> <nazwa>",
            "/afk delete <nazwa>",
            "/afk list",
            "/afk reload",
            "/afk turboafk <mnożnik> <czas>"
        );
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            for (String sub : List.of("create", "setlocation", "delete", "list", "reload", "turboafk")) {
                if (sub.startsWith(partial)) {
                    completions.add(sub);
                }
            }
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("setlocation")) {
                String partial = args[1].toLowerCase();
                for (String loc : List.of("first", "second")) {
                    if (loc.startsWith(partial)) {
                        completions.add(loc);
                    }
                }
            } else if (args[0].equalsIgnoreCase("delete")) {
                completions.addAll(plugin.getConfigManager().getAfkConfig().getZones().keySet());
            } else if (args[0].equalsIgnoreCase("turboafk")) {
                for (String value : List.of("2", "3", "5")) {
                    if (value.startsWith(args[1])) {
                        completions.add(value);
                    }
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("setlocation")) {
            completions.addAll(plugin.getConfigManager().getAfkConfig().getZones().keySet());
            completions.addAll(pendingFirstLocations.keySet());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("turboafk")) {
            for (String value : List.of("30m", "1h", "2h", "1d")) {
                if (value.toLowerCase().startsWith(args[2].toLowerCase())) {
                    completions.add(value);
                }
            }
        }
        return completions;
    }
}
