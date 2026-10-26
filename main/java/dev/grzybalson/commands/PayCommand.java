package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.MoneyAmountUtil;
import dev.grzybalson.utils.SpooferBridge;
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

public class PayCommand implements CommandExecutor, TabCompleter {

    private final HollyCore plugin;

    public PayCommand(HollyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.colorizeComponent(plugin.getConfigManager().getMessagesConfig().getPlayerOnly()));
            return true;
        }

        if (!player.hasPermission("Holly.cmd.pay")) {
            player.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.pay"));
            return true;
        }

        if (args.length < 2) {
            ColorUtils.sendUsage(player, "/pay <nick> <ilość>");
            return true;
        }

        Double parsedAmount = MoneyAmountUtil.parse(args[1]);
        if (parsedAmount == null || !MoneyAmountUtil.isPositiveFinite(parsedAmount)) {
            player.sendMessage(ColorUtils.colorizeComponent(plugin.getConfigManager().getMessagesConfig().getPayInvalidAmount()));
            return true;
        }

        double amount = parsedAmount;
        String amountText = MoneyAmountUtil.formatCompact(amount);

        String fakeTarget = SpooferBridge.resolveFakeName(args[0]);
        if (fakeTarget != null) {
            if (fakeTarget.equalsIgnoreCase(player.getName())) {
                player.sendMessage(ColorUtils.colorizeComponent(plugin.getConfigManager().getMessagesConfig().getPaySelf()));
                return true;
            }

            payToFakeBot(player, fakeTarget, amount, amountText);
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            player.sendMessage(ColorUtils.colorizeComponent(plugin.getConfigManager().getMessagesConfig().getPlayerNotFound()));
            return true;
        }

        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(ColorUtils.colorizeComponent(plugin.getConfigManager().getMessagesConfig().getPaySelf()));
            return true;
        }

        plugin.getEconomy().removeBalance(player.getUniqueId(), amount).thenCompose(success -> {
            if (!success) {
                return java.util.concurrent.CompletableFuture.completedFuture(false);
            }
            return plugin.getEconomy().addBalance(target.getUniqueId(), amount);
        }).thenAccept(success -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!success) {
                player.sendMessage(ColorUtils.colorizeComponent(plugin.getConfigManager().getMessagesConfig().getNotEnoughMoney()));
                return;
            }

            String targetName = target.getName() != null ? target.getName() : args[0];

            player.sendMessage(ColorUtils.colorizeComponent(
                plugin.getConfigManager().getMessagesConfig().getPaySent()
                    .replace("{amount}", amountText)
                    .replace("{player}", targetName)
            ));

            if (target.isOnline()) {
                Player onlineTarget = target.getPlayer();
                if (onlineTarget != null) {
                    onlineTarget.sendMessage(ColorUtils.colorizeComponent(
                        plugin.getConfigManager().getMessagesConfig().getPayReceived()
                            .replace("{amount}", amountText)
                            .replace("{player}", player.getName())
                    ));
                }
            }

            plugin.getWebhookLogManager().logPayTransfer(player, target, amount);
        }));

        return true;
    }

    private void payToFakeBot(Player player, String fakeTarget, double amount, String amountText) {
        plugin.getEconomy().removeBalance(player.getUniqueId(), amount).thenAccept(success ->
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!success) {
                    player.sendMessage(ColorUtils.colorizeComponent(plugin.getConfigManager().getMessagesConfig().getNotEnoughMoney()));
                    return;
                }

                player.sendMessage(ColorUtils.colorizeComponent(
                    plugin.getConfigManager().getMessagesConfig().getPaySent()
                        .replace("{amount}", amountText)
                        .replace("{player}", fakeTarget)
                ));

                plugin.getWebhookLogManager().logPayTransferToBot(player, fakeTarget, amount);
            })
        );
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getName().toLowerCase().startsWith(prefix)) {
                    completions.add(online.getName());
                }
            }
            for (String fake : SpooferBridge.getFakeNames()) {
                if (fake.toLowerCase().startsWith(prefix)) {
                    completions.add(fake);
                }
            }
        } else if (args.length == 2) {
            String prefix = args[1].toLowerCase();
            for (String hint : List.of("1k", "10k", "100k", "1m", "1mil", "1b", "1t")) {
                if (hint.startsWith(prefix)) {
                    completions.add(hint);
                }
            }
        }

        return completions;
    }
}
