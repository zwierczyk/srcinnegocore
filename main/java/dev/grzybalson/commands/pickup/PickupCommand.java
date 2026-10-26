package dev.grzybalson.commands.pickup;

import dev.grzybalson.HollyCore;
import dev.grzybalson.gui.PickupGUI;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class PickupCommand implements CommandExecutor, TabCompleter {

    private final HollyCore plugin;

    public PickupCommand(HollyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!canAdmin(sender, "Holly.cmd.odbierz.reload")) {
                sender.sendMessage(ColorUtils.noPermission(label + " reload", "Holly.cmd.odbierz.reload"));
                return true;
            }
            plugin.getPickupManager().reload();
            sender.sendMessage(ColorUtils.colorizeComponent("&#00FF3B✔ &8Ξ &#4EFA76Przeładowano addons/commands/odbierz.yml."));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("give")) {
            if (!canAdmin(sender, "Holly.cmd.odbierz.give")) {
                sender.sendMessage(ColorUtils.noPermission(label + " give", "Holly.cmd.odbierz.give"));
                return true;
            }

            if (args.length < 4) {
                ColorUtils.sendUsage(sender, "/odbierz give <nick> <przedmiot> <ilość>");
                return true;
            }

            Player onlineTarget = Bukkit.getPlayerExact(args[1]);
            OfflinePlayer target = onlineTarget != null ? onlineTarget : Bukkit.getOfflinePlayer(args[1]);
            if (onlineTarget == null && !target.hasPlayedBefore() && target.getName() == null) {
                sender.sendMessage(ColorUtils.colorizeComponent(
                    plugin.getConfigManager().getMessagesConfig().getPlayerNotFound()
                ));
                return true;
            }
            String targetName = onlineTarget != null ? onlineTarget.getName() : (target.getName() != null ? target.getName() : args[1]);

            String packageName = args[2];
            if (!plugin.getPickupManager().packageExists(packageName)) {
                sender.sendMessage(ColorUtils.error("Przedmiot o nazwie '" + packageName + "' nie istnieje!"));
                return true;
            }

            int amount;
            try {
                amount = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                sender.sendMessage(ColorUtils.error("Ilość musi być liczbą!"));
                return true;
            }

            if (amount <= 0) {
                sender.sendMessage(ColorUtils.error("Ilość musi być większa od 0!"));
                return true;
            }

            plugin.getPickupManager().givePackage(target.getUniqueId(), packageName, amount);
            sender.sendMessage(ColorUtils.colorizeComponent(
                plugin.getConfigManager().getMessagesConfig().getPickupGiven()
                    .replace("{player}", targetName)
            ));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("giveall")) {
            if (!canAdmin(sender, "Holly.cmd.odbierz.giveall")) {
                sender.sendMessage(ColorUtils.noPermission(label + " giveall", "Holly.cmd.odbierz.giveall"));
                return true;
            }

            if (args.length < 3) {
                ColorUtils.sendUsage(sender, "/odbierz giveall <przedmiot> <ilość>");
                return true;
            }

            String packageName = args[1];
            if (!plugin.getPickupManager().packageExists(packageName)) {
                sender.sendMessage(ColorUtils.error("Przedmiot o nazwie '" + packageName + "' nie istnieje!"));
                return true;
            }

            int amount;
            try {
                amount = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(ColorUtils.error("Ilość musi być liczbą!"));
                return true;
            }

            if (amount <= 0) {
                sender.sendMessage(ColorUtils.error("Ilość musi być większa od 0!"));
                return true;
            }

            plugin.getPickupManager().givePackageToAll(packageName, amount);
            sender.sendMessage(ColorUtils.colorizeComponent(
                plugin.getConfigManager().getMessagesConfig().getPickupGivenAll()
            ));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.colorizeComponent(
                plugin.getConfigManager().getMessagesConfig().getPlayerOnly()
            ));
            return true;
        }

        if (args.length == 0) {
            if (!player.hasPermission("Holly.cmd.odbierz")) {
                player.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.odbierz"));
                return true;
            }

            PickupGUI pickupGUI = new PickupGUI(plugin, player);
            pickupGUI.open();
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "create" -> {
                if (!player.hasPermission("Holly.cmd.odbierz.create")) {
                    player.sendMessage(ColorUtils.noPermission(label + " create", "Holly.cmd.odbierz.create"));
                    return true;
                }

                if (args.length < 2) {
                    ColorUtils.sendUsage(player, "/odbierz create <nazwa>");
                    return true;
                }

                String name = args[1];
                ItemStack itemInHand = player.getInventory().getItemInMainHand();

                if (itemInHand.getType() == Material.AIR) {
                    player.sendMessage(ColorUtils.error("Musisz trzymać przedmiot w ręce!"));
                    return true;
                }

                plugin.getPickupManager().createPackage(name, itemInHand.clone());
                player.sendMessage(ColorUtils.colorizeComponent(
                    plugin.getConfigManager().getMessagesConfig().getPickupCreated()
                        .replace("{name}", name)
                ));
            }
            default -> sendAdminUsage(player);
        }

        return true;
    }

    private boolean canAdmin(CommandSender sender, String permission) {
        if (!(sender instanceof Player)) {
            return true;
        }
        return sender.hasPermission(permission);
    }

    private void sendAdminUsage(CommandSender sender) {
        ColorUtils.sendUsage(sender,
            "/odbierz create <nazwa>",
            "/odbierz give <nick> <przedmiot> <ilość>",
            "/odbierz giveall <przedmiot> <ilość>",
            "/odbierz reload"
        );
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            for (String sub : List.of("create", "give", "giveall", "reload")) {
                if (sub.startsWith(partial) && canAdmin(sender, "Holly.cmd.odbierz." + sub)) {
                    completions.add(sub);
                }
            }
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("give")) {
                String partial = args[1].toLowerCase();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getName().toLowerCase().startsWith(partial)) {
                        completions.add(player.getName());
                    }
                }
            } else if (subCommand.equals("giveall") || subCommand.equals("create")) {
                completions.addAll(plugin.getPickupManager().getPickupPackages().keySet());
            }
        } else if (args.length == 3) {
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("give")) {
                completions.addAll(plugin.getPickupManager().getPickupPackages().keySet());
            } else if (subCommand.equals("giveall")) {
                completions.add("<ilość>");
            }
        } else if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
            completions.add("<ilość>");
        }

        return completions;
    }
}
