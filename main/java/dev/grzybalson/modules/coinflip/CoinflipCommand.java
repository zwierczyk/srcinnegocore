package dev.grzybalson.modules.coinflip;

import dev.grzybalson.anarchiagg.util.notice.Notice;
import dev.grzybalson.anarchiagg.util.notice.NoticeType;
import dev.grzybalson.config.modules.coinflip.CoinflipConfig;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

public class CoinflipCommand implements CommandExecutor, TabCompleter {

    private final CoinflipManager manager;
    private final CoinflipMenuManager menuManager;
    private final CoinflipConfig config;

    public CoinflipCommand(CoinflipManager manager, CoinflipMenuManager menuManager, CoinflipConfig config) {
        this.manager = manager;
        this.menuManager = menuManager;
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cTa komenda jest dostepna tylko dla graczy!");
                return true;
            }
            if (!player.hasPermission("Holly.cmd.cf") && !player.isOp()) {
                player.sendMessage("§cBrak uprawnien!");
                return true;
            }
            menuManager.openMain(player);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "stworz", "create", "dodaj" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§cTa komenda jest dostepna tylko dla graczy!");
                    return true;
                }
                if (!player.hasPermission("Holly.cmd.cf") && !player.isOp()) {
                    player.sendMessage("§cBrak uprawnien!");
                    return true;
                }
                if (args.length < 3) {
                    ColorUtils.sendUsage(player, "/coinflip stworz <kasa|odlamki> <ilosc>");
                    return true;
                }

                String currencyInput = args[1];
                double amount;
                try {
                    amount = Double.parseDouble(args[2]);
                } catch (NumberFormatException e) {
                    Notice.sendP(
                            player,
                            config.getMessages().getType(),
                            config.getMessages().getInvalidAmount()
                    );
                    return true;
                }

                Optional<CoinflipCurrency> currencyOpt = CoinflipCurrency.parse(currencyInput);
                if (currencyOpt.isEmpty()) {
                    Notice.sendP(
                            player,
                            config.getMessages().getType(),
                            config.getMessages().getInvalidCurrency()
                    );
                    return true;
                }

                manager.createGame(player, currencyOpt.get(), amount);
                return true;
            }
            case "odbierz", "claim" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§cTa komenda jest dostepna tylko dla graczy!");
                    return true;
                }
                if (!player.hasPermission("Holly.cmd.cf") && !player.isOp()) {
                    player.sendMessage("§cBrak uprawnien!");
                    return true;
                }
                menuManager.openClaim(player);
                return true;
            }
            case "reload" -> {
                if (!sender.hasPermission("Holly.cmd.cf.admin") && !sender.isOp()) {
                    sender.sendMessage("§cBrak uprawnien!");
                    return true;
                }
                config.load(true);
                if (config.migrateBrokenDefaults()) {
                    config.save();
                }
                sender.sendMessage(ColorUtils.colorize("&aPrzeladowano konfiguracje Coinflip."));
                return true;
            }
            default -> {
                ColorUtils.sendUsage(sender,
                    "/coinflip stworz <kasa|odlamki> <ilosc>",
                    "/coinflip odbierz",
                    "/coinflip reload"
                );
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("stworz", "odbierz", "reload");
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("stworz") || args[0].equalsIgnoreCase("create"))) {
            return List.of("kasa", "odlamki");
        }
        return List.of();
    }
}
