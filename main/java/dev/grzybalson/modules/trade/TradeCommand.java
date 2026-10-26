package dev.grzybalson.modules.trade;

import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TradeCommand implements CommandExecutor, TabCompleter {

    private final HollyCore plugin;

    public TradeCommand(HollyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cTylko gracz może użyć tej komendy."));
            return true;
        }
        if (!sender.hasPermission("Holly.cmd.trade")) {
            sender.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.trade"));
            return true;
        }
        if (args.length < 1) {
            ColorUtils.sendUsage(sender, "/" + label + " <gracz>");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(ColorUtils.colorizeComponent(
                plugin.getTradeManager().getConfig().format("offline", "{PLAYER}", args[0])
            ));
            return true;
        }

        plugin.getTradeManager().tryTrade(player, target);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> names = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                names.add(online.getName());
            }
        }
        return names;
    }
}
