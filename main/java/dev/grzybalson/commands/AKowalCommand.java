package dev.grzybalson.commands;

import dev.grzybalson.pvp.kowal.KowalUpgradeHandler;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class AKowalCommand implements CommandExecutor, TabCompleter {

    private final KowalUpgradeHandler upgradeHandler;

    public AKowalCommand(KowalUpgradeHandler upgradeHandler) {
        this.upgradeHandler = upgradeHandler;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("Holly.kowal.admin")) {
            sender.sendMessage(ColorUtils.noPermission(label, "Holly.kowal.admin"));
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase();
        if (sub.equals("reload")) {
            upgradeHandler.reloadConfig();
            sender.sendMessage(ColorUtils.success("Pomyślnie przeładowano konfigurację kowala (kowal.yml)!"));
            return true;
        }

        if (sub.equals("giveshard") || sub.equals("give") || sub.equals("giveshards") || sub.equals("kamien")) {
            if (args.length < 2) {
                sender.sendMessage(ColorUtils.usage("/" + label + " giveshard <nick> <ilość>"));
                return true;
            }

            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(ColorUtils.error("Gracz &f" + args[1] + " &cjest offline."));
                return true;
            }

            int amount = 1;
            if (args.length >= 3) {
                try {
                    amount = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(ColorUtils.error("Podaj poprawną ilość."));
                    return true;
                }
            }

            if (amount < 1) {
                sender.sendMessage(ColorUtils.error("Ilość musi być większa od 0."));
                return true;
            }

            ItemStack stone = upgradeHandler.createKowalStone(amount);
            HashMap<Integer, ItemStack> leftovers = target.getInventory().addItem(stone);
            leftovers.values().forEach(leftover -> target.getWorld().dropItemNaturally(target.getLocation(), leftover));

            sender.sendMessage(ColorUtils.success("Nadano &fx" + amount + " &aKamienia Kowalskiego dla gracza &f" + target.getName() + "&a."));
            target.sendMessage(ColorUtils.info("Otrzymałeś &fx" + amount + " &7Kamienia Kowalskiego."));
            return true;
        }

        sendUsage(sender, label);
        return true;
    }

    private void sendUsage(CommandSender sender, String label) {
        ColorUtils.sendUsage(sender, "/" + label + " <giveshard|reload>");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("Holly.kowal.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            List<String> list = new ArrayList<>();
            String input = args[0].toLowerCase();
            if ("giveshard".startsWith(input)) list.add("giveshard");
            if ("reload".startsWith(input)) list.add("reload");
            return list;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("giveshard")) {
            return null;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("giveshard")) {
            return Arrays.asList("1", "4", "16", "32", "64");
        }
        return List.of();
    }
}
