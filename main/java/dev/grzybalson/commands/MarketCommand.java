package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.configs.MarketConfig;
import dev.grzybalson.gui.MarketGUI;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Sound;
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
import java.util.List;

public class MarketCommand implements CommandExecutor, TabCompleter {

    private final HollyCore plugin;

    public MarketCommand(HollyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cTa komenda jest tylko dla graczy!"));
            return true;
        }

        String lbl = label.toLowerCase();

        if (lbl.equals("amarket") || lbl.equals("arynek")) {
            if (!player.hasPermission("Holly.cmd.amarket") && !player.hasPermission("Holly.cmd.rynek.reload")) {
                player.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.amarket"));
                return true;
            }
            if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
                plugin.getConfigManager().getMarketConfig().load(true);
                if (plugin.getMarketManager() != null) {
                    plugin.getMarketManager().load();
                    plugin.getMarketManager().reloadFilters();
                }
                player.sendMessage(ColorUtils.success("Przeładowano konfigurację i dane rynku (addons/market/market.yml)!"));
                return true;
            }
            ColorUtils.sendUsage(player, "/amarket reload");
            return true;
        }

        if (lbl.equals("wystaw")) {
            return handleSell(player, args);
        }

        if (args.length == 0) {
            openMarket(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        if (subCommand.equals("reload")) {
            if (!player.hasPermission("Holly.cmd.rynek.reload")) {
                player.sendMessage(ColorUtils.noPermission(label + " reload", "Holly.cmd.rynek.reload"));
                return true;
            }
            plugin.getConfigManager().getMarketConfig().load(true);
            if (plugin.getMarketManager() != null) {
                plugin.getMarketManager().load();
                plugin.getMarketManager().reloadFilters();
            }
            player.sendMessage(ColorUtils.success("Przeładowano konfigurację rynku!"));
            return true;
        }

        if (subCommand.equals("open") || subCommand.equals("gui") || subCommand.equals("otworz")) {
            openMarket(player);
            return true;
        }

        if (subCommand.equals("wystaw") || subCommand.equals("sell") || subCommand.equals("list") || subCommand.equals("sprzedaj")) {
            String[] sellArgs = removeFirstArg(args);
            return handleSell(player, sellArgs);
        }

        if (isNumeric(subCommand)) {
            return handleSell(player, args);
        }

        openMarket(player);
        return true;
    }

    private boolean isNumeric(String str) {
        if (str == null || str.isEmpty()) return false;
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean handleSell(Player player, String[] args) {
        MarketConfig config = plugin.getConfigManager().getMarketConfig();
        if (args.length == 0) {
            ColorUtils.sendUsage(player, "/wystaw [ilość] <cena>");
            return true;
        }

        double price;
        try {
            if (args.length == 1) {
                price = Double.parseDouble(args[0]);
            } else {
                price = Double.parseDouble(args[1]);
            }
        } catch (NumberFormatException e) {
            String message = config.getInvalidPriceMessage()
                .replace("{min}", String.valueOf(config.getMinPrice()))
                .replace("{max}", String.valueOf(config.getMaxPrice()));
            player.sendMessage(ColorUtils.colorizeComponent(message));
            return true;
        }

        long now = System.currentTimeMillis();
        if (plugin.getMarketManager().isOnCooldown(player.getUniqueId(), now)) {
            player.sendMessage(ColorUtils.colorizeComponent(config.getCooldownMessage()));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return true;
        }

        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (itemInHand.getType().isAir()) {
            player.sendMessage(ColorUtils.colorizeComponent(config.getNoItemInHandMessage()));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return true;
        }

        plugin.getMarketManager().setCooldown(player.getUniqueId(), now);
        plugin.getMarketManager().listItem(player, itemInHand, price).thenAccept(success ->
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (success) {
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                } else {
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                }
            })
        );
        return true;
    }

    private String[] removeFirstArg(String[] args) {
        if (args.length <= 1) return new String[0];
        String[] newArgs = new String[args.length - 1];
        System.arraycopy(args, 1, newArgs, 0, newArgs.length);
        return newArgs;
    }

    private void openMarket(Player player) {
        new MarketGUI(plugin, player).open();
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (alias.equalsIgnoreCase("amarket") || alias.equalsIgnoreCase("arynek")) {
            return List.of("reload");
        }
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(Arrays.asList("open", "otworz", "wystaw", "100", "500", "1000"));
            if (sender.hasPermission("Holly.cmd.rynek.reload")) {
                subs.add("reload");
            }
            return subs;
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("wystaw") || args[0].equalsIgnoreCase("sell") || args[0].equalsIgnoreCase("list") || isNumeric(args[0]))) {
            return Arrays.asList("100", "1000", "10000");
        }

        return new ArrayList<>();
    }
}
