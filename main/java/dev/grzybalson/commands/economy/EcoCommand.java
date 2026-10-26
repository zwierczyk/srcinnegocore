package dev.grzybalson.commands.economy;

import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.MoneyAmountUtil;
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

public class EcoCommand implements CommandExecutor, TabCompleter {

    private final HollyCore plugin;

    public EcoCommand(HollyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("Holly.cmd.eco")) {
            sender.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.eco"));
            return true;
        }

        if (args.length < 2) {
            sendUsage(sender);
            return true;
        }

        String action = args[0].toLowerCase();
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);

        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage(ColorUtils.colorizeComponent(
                plugin.getConfigManager().getMessagesConfig().getPlayerNotFound()
            ));
            return true;
        }

        switch (action) {
            case "add" -> {
                if (args.length < 3) {
                    ColorUtils.sendUsage(sender, "/eco add <nick> <ilość>");
                    return true;
                }
                Double parsedAmount = MoneyAmountUtil.parse(args[2]);
                if (parsedAmount == null || !MoneyAmountUtil.isPositiveFinite(parsedAmount)) {
                    sender.sendMessage(ColorUtils.error("Ilość musi być większa od 0!"));
                    return true;
                }
                double amount = parsedAmount;
                plugin.getEconomy().addBalance(target.getUniqueId(), amount).thenRun(() -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        sender.sendMessage(ColorUtils.colorizeComponent(
                            plugin.getConfigManager().getMessagesConfig().getMoneyAdded()
                                .replace("{amount}", String.format("%.2f", amount))
                                .replace("{player}", target.getName())
                        ));
                    });
                });
            }
            case "remove" -> {
                if (args.length < 3) {
                    ColorUtils.sendUsage(sender, "/eco remove <nick> <ilość>");
                    return true;
                }
                Double parsedAmount = MoneyAmountUtil.parse(args[2]);
                if (parsedAmount == null || !MoneyAmountUtil.isPositiveFinite(parsedAmount)) {
                    sender.sendMessage(ColorUtils.error("Ilość musi być większa od 0!"));
                    return true;
                }
                double amount = parsedAmount;
                plugin.getEconomy().removeBalance(target.getUniqueId(), amount).thenRun(() -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        sender.sendMessage(ColorUtils.colorizeComponent(
                            plugin.getConfigManager().getMessagesConfig().getMoneyRemoved()
                                .replace("{amount}", String.format("%.2f", amount))
                                .replace("{player}", target.getName())
                        ));
                    });
                });
            }
            case "set" -> {
                if (args.length < 3) {
                    ColorUtils.sendUsage(sender, "/eco set <nick> <ilość>");
                    return true;
                }
                Double parsedAmount = MoneyAmountUtil.parse(args[2]);
                if (parsedAmount == null || !MoneyAmountUtil.isNonNegativeFinite(parsedAmount)) {
                    sender.sendMessage(ColorUtils.error("Ilość nie może być ujemna!"));
                    return true;
                }
                double amount = parsedAmount;
                plugin.getEconomy().setBalance(target.getUniqueId(), amount).thenRun(() -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        sender.sendMessage(ColorUtils.colorizeComponent(
                            plugin.getConfigManager().getMessagesConfig().getMoneySet()
                                .replace("{amount}", String.format("%.2f", amount))
                                .replace("{player}", target.getName())
                        ));
                    });
                });
            }
            case "reset" -> {
                double startingBalance = plugin.getConfigManager().getEconomyConfig().getStartingBalance();
                plugin.getEconomy().setBalance(target.getUniqueId(), startingBalance).thenRun(() -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        sender.sendMessage(ColorUtils.colorizeComponent(
                            plugin.getConfigManager().getMessagesConfig().getMoneyReset()
                                .replace("{player}", target.getName())
                        ));
                    });
                });
            }
            default -> sendUsage(sender);
        }

        return true;
    }

    private void sendUsage(CommandSender sender) {
        ColorUtils.sendUsage(sender,
            "/eco add <nick> <ilość>",
            "/eco remove <nick> <ilość>",
            "/eco set <nick> <ilość>",
            "/eco reset <nick>"
        );
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            for (String action : List.of("add", "set", "reset", "remove")) {
                if (action.startsWith(partial)) {
                    completions.add(action);
                }
            }
        } else if (args.length == 2) {
            String partial = args[1].toLowerCase();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(partial)) {
                    completions.add(player.getName());
                }
            }
        } else if (args.length == 3 && !args[0].equalsIgnoreCase("reset")) {
            completions.add("<ilość>");
        }
        return completions;
    }
}
