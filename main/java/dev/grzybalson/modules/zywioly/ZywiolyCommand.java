package dev.grzybalson.modules.zywioly;

import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.MoneyAmountUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

public final class ZywiolyCommand implements CommandExecutor, TabCompleter {

    private final ZywiolyManager manager;

    public ZywiolyCommand(ZywiolyManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("Holly.cmd.zywioly.admin") && !sender.isOp()) {
                sender.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.zywioly.admin"));
                return true;
            }
            manager.reload();
            sender.sendMessage(ColorUtils.colorizeComponent(manager.config().message("reloaded")));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cTylko gracz."));
            return true;
        }
        if (!player.hasPermission("Holly.cmd.zywioly") && !player.isOp()) {
            player.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.zywioly"));
            return true;
        }

        if (args.length == 0) {
            manager.openMain(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("dodaj") || args[0].equalsIgnoreCase("stworz") || args[0].equalsIgnoreCase("create")) {
            if (args.length < 2) {
                ColorUtils.sendUsage(player, "/" + label + " dodaj <kwota>");
                return true;
            }
            Double amount = MoneyAmountUtil.parse(args[1]);
            if (amount == null) {
                manager.send(player, manager.config().message("invalid-amount"));
                return true;
            }
            manager.startPickForCreate(player, amount);
            return true;
        }

        manager.openMain(player);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("dodaj", "reload").stream()
                .filter(option -> option.startsWith(args[0].toLowerCase(Locale.ROOT)))
                .toList();
        }
        return List.of();
    }
}
