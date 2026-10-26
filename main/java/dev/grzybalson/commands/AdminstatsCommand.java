package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
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
import java.util.UUID;

public class AdminstatsCommand implements CommandExecutor, TabCompleter {

    private final HollyCore plugin;

    public AdminstatsCommand(HollyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("Holly.cmd.adminstats")) {
            sender.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.adminstats"));
            return true;
        }

        if (args.length != 4) {
            sender.sendMessage(ColorUtils.usage("/adminstats <set|add|remove> <points|deaths|kills> <nick> <ilosc>"));
            return true;
        }

        String actionInput = args[0].toLowerCase(Locale.ROOT);
        if (!actionInput.equals("set") && !actionInput.equals("add") && !actionInput.equals("remove")) {
            sender.sendMessage(ColorUtils.error("Niepoprawna akcja. Dostępne: set, add, remove"));
            return true;
        }

        String statInput = args[1].toLowerCase(Locale.ROOT);
        StatType statType = StatType.fromInput(statInput);
        if (statType == null) {
            sender.sendMessage(ColorUtils.error("Niepoprawny typ statystyki. Dostępne: points, deaths, kills"));
            return true;
        }

        OfflinePlayer target = resolveOffline(args[2]);
        if (target == null) {
            sender.sendMessage(ColorUtils.colorizeComponent(
                plugin.getConfigManager().getMessagesConfig().getPlayerNotFound()
            ));
            return true;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[3]);
            if (amount < 0) {
                sender.sendMessage(ColorUtils.error("Ilość nie może być ujemna."));
                return true;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(ColorUtils.error("Niepoprawna ilość. Podaj liczbę całkowitą."));
            return true;
        }

        UUID uuid = target.getUniqueId();
        String targetName = safeName(target);

        switch (statType) {
            case POINTS -> {
                if (plugin.getEloManager() == null) {
                    sender.sendMessage(ColorUtils.error("System punktów/rankingu jest wyłączony."));
                    return true;
                }
                int current = plugin.getEloManager().getElo(uuid);
                int newPoints = switch (actionInput) {
                    case "set" -> amount;
                    case "add" -> current + amount;
                    case "remove" -> Math.max(0, current - amount);
                    default -> current;
                };

                plugin.getEloManager().setElo(uuid, newPoints);
                if (plugin.getHollyPlaceholderExpansion() != null) {
                    plugin.getHollyPlaceholderExpansion().invalidatePlayer(uuid);
                }

                String msg = switch (actionInput) {
                    case "set" -> "&aUstawiono punkty dla gracza &f" + targetName + " &ana &f" + newPoints + "&a.";
                    case "add" -> "&aDodano &f" + amount + " &apunktów dla gracza &f" + targetName + " &a(razem: &f" + newPoints + "&a).";
                    case "remove" -> "&aUsunięto &f" + amount + " &apunktów graczowi &f" + targetName + " &a(razem: &f" + newPoints + "&a).";
                    default -> "";
                };
                sender.sendMessage(ColorUtils.colorizeComponent(msg));
            }
            case KILLS -> {
                if (plugin.getAchievementManager() == null) {
                    sender.sendMessage(ColorUtils.error("System statystyk jest wyłączony."));
                    return true;
                }
                plugin.getAchievementManager().loadPlayer(uuid).thenAccept(data -> {
                    int current = data.getKills();
                    int newKills = switch (actionInput) {
                        case "set" -> amount;
                        case "add" -> current + amount;
                        case "remove" -> Math.max(0, current - amount);
                        default -> current;
                    };

                    data.setKills(newKills);
                    plugin.getAchievementManager().savePlayer(uuid);

                    if (target.isOnline() && target.getPlayer() != null) {
                        Player onlinePlayer = target.getPlayer();
                        Bukkit.getScheduler().runTask(plugin, () -> onlinePlayer.setStatistic(Statistic.PLAYER_KILLS, newKills));
                    }

                    if (plugin.getTopManager() != null) {
                        plugin.getTopManager().invalidatePlayer(uuid);
                        plugin.getTopManager().requestRefresh();
                    }
                    if (plugin.getHollyPlaceholderExpansion() != null) {
                        plugin.getHollyPlaceholderExpansion().invalidatePlayer(uuid);
                    }

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        String msg = switch (actionInput) {
                            case "set" -> "&aUstawiono zabójstwa dla gracza &f" + targetName + " &ana &f" + newKills + "&a.";
                            case "add" -> "&aDodano &f" + amount + " &azabójstw dla gracza &f" + targetName + " &a(razem: &f" + newKills + "&a).";
                            case "remove" -> "&aUsunięto &f" + amount + " &azabójstw graczowi &f" + targetName + " &a(razem: &f" + newKills + "&a).";
                            default -> "";
                        };
                        sender.sendMessage(ColorUtils.colorizeComponent(msg));
                    });
                });
            }
            case DEATHS -> {
                if (plugin.getAchievementManager() == null) {
                    sender.sendMessage(ColorUtils.error("System statystyk jest wyłączony."));
                    return true;
                }
                plugin.getAchievementManager().loadPlayer(uuid).thenAccept(data -> {
                    int current = data.getDeaths();
                    int newDeaths = switch (actionInput) {
                        case "set" -> amount;
                        case "add" -> current + amount;
                        case "remove" -> Math.max(0, current - amount);
                        default -> current;
                    };

                    data.setDeaths(newDeaths);
                    plugin.getAchievementManager().savePlayer(uuid);

                    if (target.isOnline() && target.getPlayer() != null) {
                        Player onlinePlayer = target.getPlayer();
                        Bukkit.getScheduler().runTask(plugin, () -> onlinePlayer.setStatistic(Statistic.DEATHS, newDeaths));
                    }

                    if (plugin.getTopManager() != null) {
                        plugin.getTopManager().invalidatePlayer(uuid);
                        plugin.getTopManager().requestRefresh();
                    }
                    if (plugin.getHollyPlaceholderExpansion() != null) {
                        plugin.getHollyPlaceholderExpansion().invalidatePlayer(uuid);
                    }

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        String msg = switch (actionInput) {
                            case "set" -> "&aUstawiono zgony dla gracza &f" + targetName + " &ana &f" + newDeaths + "&a.";
                            case "add" -> "&aDodano &f" + amount + " &azgonów dla gracza &f" + targetName + " &a(razem: &f" + newDeaths + "&a).";
                            case "remove" -> "&aUsunięto &f" + amount + " &azgonów graczowi &f" + targetName + " &a(razem: &f" + newDeaths + "&a).";
                            default -> "";
                        };
                        sender.sendMessage(ColorUtils.colorizeComponent(msg));
                    });
                });
            }
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("Holly.cmd.adminstats")) {
            return List.of();
        }

        if (args.length == 1) {
            return filterPrefix(List.of("set", "add", "remove"), args[0]);
        }

        if (args.length == 2) {
            return filterPrefix(List.of("points", "deaths", "kills"), args[1]);
        }

        if (args.length == 3) {
            List<String> playerNames = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                playerNames.add(player.getName());
            }
            return filterPrefix(playerNames, args[2]);
        }

        if (args.length == 4) {
            return filterPrefix(List.of("10", "50", "100", "500", "1000"), args[3]);
        }

        return List.of();
    }

    private List<String> filterPrefix(List<String> options, String prefix) {
        String lowerPrefix = prefix.toLowerCase(Locale.ROOT);
        List<String> filtered = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lowerPrefix)) {
                filtered.add(option);
            }
        }
        return filtered;
    }

    private @Nullable OfflinePlayer resolveOffline(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        if (offline.hasPlayedBefore() || offline.isOnline()) {
            return offline;
        }
        return null;
    }

    private String safeName(OfflinePlayer player) {
        return player.getName() != null ? player.getName() : player.getUniqueId().toString();
    }

    private enum StatType {
        POINTS,
        DEATHS,
        KILLS;

        private static @Nullable StatType fromInput(String input) {
            return switch (input.toLowerCase(Locale.ROOT)) {
                case "points", "elo", "ranking" -> POINTS;
                case "deaths", "zgony" -> DEATHS;
                case "kills", "zabojstwa", "zabójstwa" -> KILLS;
                default -> null;
            };
        }
    }
}
