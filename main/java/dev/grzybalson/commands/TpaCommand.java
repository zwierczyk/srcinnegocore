package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
import dev.grzybalson.gui.TpaConfirmGUI;
import dev.grzybalson.modules.achievement.AchievementManager;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
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
import java.util.concurrent.CompletableFuture;

public class TpaCommand implements CommandExecutor, TabCompleter {

    private final HollyCore plugin;

    public TpaCommand(HollyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.colorizeComponent(
                plugin.getConfigManager().getMessagesConfig().getPlayerOnly()
            ));
            return true;
        }

        if (!player.hasPermission("Holly.cmd.tpa")) {
            player.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.tpa"));
            return true;
        }

        if (args.length < 1) {
            ColorUtils.sendUsage(player, "/tpa <gracz>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            player.sendMessage(ColorUtils.colorizeComponent(
                plugin.getConfigManager().getMessagesConfig().getPlayerNotFound()
            ));
            return true;
        }

        if (target.equals(player)) {
            player.sendMessage(ColorUtils.error("Nie mozesz wyslac prosby do siebie!"));
            return true;
        }

        if (plugin.getTeleportRequestManager().isOnCooldown(player.getUniqueId())) {
            long remaining = plugin.getTeleportRequestManager().getRemainingCooldown(player.getUniqueId());
            player.sendMessage(ColorUtils.error("Poczekaj jeszcze " + (remaining / 1000 + 1) + "s przed wyslaniem kolejnej prosby!"));
            return true;
        }

        openConfirmGui(player, target);
        return true;
    }

    private void openConfirmGui(Player player, Player target) {
        UUID targetUuid = target.getUniqueId();
        CompletableFuture<StatsSnapshot> statsFuture = loadStats(targetUuid);
        CompletableFuture<Double> balanceFuture = loadBalance(targetUuid);

        CompletableFuture.allOf(statsFuture, balanceFuture).whenComplete((ignored, throwable) -> {
            StatsSnapshot stats = statsFuture.getNow(new StatsSnapshot(0, 0));
            double balance = balanceFuture.getNow(0.0D);

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }

                Player onlineTarget = Bukkit.getPlayer(targetUuid);
                if (onlineTarget == null || !onlineTarget.isOnline()) {
                    player.sendMessage(ColorUtils.colorizeComponent(
                        plugin.getConfigManager().getMessagesConfig().getPlayerNotFound()
                    ));
                    return;
                }

                int ranking = plugin.getEloManager() != null ? plugin.getEloManager().getElo(targetUuid) : 0;
                new TpaConfirmGUI(plugin, onlineTarget, stats.kills(), stats.deaths(), ranking, formatGuiMoney(balance)).open(player);
            });
        });
    }

    private CompletableFuture<StatsSnapshot> loadStats(UUID targetUuid) {
        AchievementManager achievementManager = plugin.getAchievementManager();
        if (achievementManager == null) {
            return CompletableFuture.completedFuture(new StatsSnapshot(0, 0));
        }

        return achievementManager.loadPlayer(targetUuid)
            .thenApply(data -> new StatsSnapshot(data.getKills(), data.getDeaths()))
            .exceptionally(throwable -> new StatsSnapshot(0, 0));
    }

    private CompletableFuture<Double> loadBalance(UUID targetUuid) {
        if (!plugin.isAddonEnabled("economy") || plugin.getEconomy() == null) {
            return CompletableFuture.completedFuture(0.0D);
        }
        return plugin.getEconomy().getBalanceAsync(targetUuid).exceptionally(throwable -> 0.0D);
    }

    private String formatGuiMoney(double balance) {
        String symbol = plugin.getConfigManager().getEconomyConfig().getCurrencySymbol();
        if (symbol == null) {
            symbol = "";
        }
        return String.format(Locale.US, "%.2f%s", balance, symbol);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(partial) && !player.equals(sender)) {
                    completions.add(player.getName());
                }
            }
        }
        return completions;
    }

    private record StatsSnapshot(int kills, int deaths) {
    }
}
