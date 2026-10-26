package dev.grzybalson.commands.economy;

import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public class MoneyCommand implements CommandExecutor {

    private final HollyCore plugin;

    public MoneyCommand(HollyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.colorizeComponent(plugin.getConfigManager().getMessagesConfig().getPlayerOnly()));
            return true;
        }

        if (!player.hasPermission("Holly.cmd.money")) {
            player.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.money"));
            return true;
        }

        plugin.getEconomy().getBalanceAsync(player.getUniqueId()).thenAccept(balance -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                String amount = String.format(Locale.US, "%.2f", balance);
                String symbol = plugin.getConfigManager().getEconomyConfig().getCurrencySymbol();
                player.sendMessage(ColorUtils.success("Masz &f" + amount + symbol + " &ana koncie."));
            });
        }).exceptionally(throwable -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage(ColorUtils.error("Nie udało się pobrać stanu konta."));
            });
            return null;
        });

        return true;
    }
}
