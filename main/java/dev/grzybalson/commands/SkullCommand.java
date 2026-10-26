package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.HeadUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SkullCommand implements CommandExecutor, TabCompleter {

    private final HollyCore plugin;

    public SkullCommand(HollyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("Holly.cmd.skull")) {
            sender.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.skull"));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(ColorUtils.usage("/" + label + " <nick> [ilość]"));
            return true;
        }

        String target = args[0];
        Player receiver;
        int amount = 1;

        if (args.length == 1) {
            if (!(sender instanceof Player playerSender)) {
                sender.sendMessage(ColorUtils.error("Konsola musi podać gracza docelowego: /" + label + " <nick> <gracz> [ilość]"));
                return true;
            }
            receiver = playerSender;
        } else if (args.length == 2) {
            // Drugi argument to może być ilość LUB gracz docelowy
            Integer parsedAmount = tryParseInt(args[1]);
            if (parsedAmount != null) {
                if (!(sender instanceof Player playerSender)) {
                    sender.sendMessage(ColorUtils.error("Konsola musi podać gracza docelowego: /" + label + " <nick> <gracz> [ilość]"));
                    return true;
                }
                receiver = playerSender;
                amount = parsedAmount;
            } else {
                Player targetPlayer = Bukkit.getPlayerExact(args[1]);
                if (targetPlayer == null) {
                    sender.sendMessage(ColorUtils.error("Gracz &e" + args[1] + " &cjest offline lub podano nieprawidłową liczbę."));
                    return true;
                }
                receiver = targetPlayer;
            }
        } else {
            // args.length >= 3: /skull <target> <receiver> <amount>
            Player targetPlayer = Bukkit.getPlayerExact(args[1]);
            if (targetPlayer == null) {
                sender.sendMessage(ColorUtils.error("Gracz &e" + args[1] + " &cjest offline."));
                return true;
            }
            receiver = targetPlayer;
            Integer parsedAmount = tryParseInt(args[2]);
            if (parsedAmount == null) {
                sender.sendMessage(ColorUtils.error("Ilość musi być poprawną liczbą całkowitą."));
                return true;
            }
            amount = parsedAmount;
        }

        if (amount <= 0) {
            sender.sendMessage(ColorUtils.error("Ilość musi być większa od zera."));
            return true;
        }
        if (amount > 64) {
            sender.sendMessage(ColorUtils.error("Maksymalna jednorazowa ilość to 64."));
            return true;
        }

        ItemStack head;
        if (looksLikeTexture(target)) {
            head = HeadUtils.createHead(target, "&eGłowa niestandardowa");
        } else {
            head = createPlayerHead(target);
        }
        head.setAmount(amount);

        Map<Integer, ItemStack> leftover = receiver.getInventory().addItem(head);
        if (!leftover.isEmpty()) {
            for (ItemStack drop : leftover.values()) {
                receiver.getWorld().dropItemNaturally(receiver.getLocation(), drop);
            }
            receiver.sendMessage(ColorUtils.warning("Część przedmiotów nie zmieściła się w ekwipunku i wypadła na ziemię!"));
        }

        String amountSuffix = (amount > 1 ? " &8(&7x" + amount + "&8)" : "");
        if (sender.equals(receiver)) {
            sender.sendMessage(ColorUtils.success("Otrzymano główkę gracza &e" + target + amountSuffix + "&a."));
        } else {
            sender.sendMessage(ColorUtils.success("Nadano główkę gracza &e" + target + amountSuffix + " &adla &e" + receiver.getName() + "&a."));
            receiver.sendMessage(ColorUtils.success("Otrzymano główkę gracza &e" + target + amountSuffix + "&a."));
        }

        return true;
    }

    private Integer tryParseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private boolean looksLikeTexture(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String trimmed = value.trim();
        return trimmed.startsWith("eyJ")
            || trimmed.startsWith("http://")
            || trimmed.startsWith("https://")
            || (trimmed.length() >= 32 && trimmed.length() <= 64 && trimmed.matches("^[a-fA-F0-9]+$"));
    }

    private ItemStack createPlayerHead(String playerName) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            Player online = Bukkit.getPlayerExact(playerName);
            if (online != null) {
                meta.setPlayerProfile(online.getPlayerProfile());
            } else {
                meta.setOwningPlayer(Bukkit.getOfflinePlayer(playerName));
            }
            meta.displayName(ColorUtils.colorizeComponent("&eGłowa gracza &6" + playerName)
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            head.setItemMeta(meta);
        }
        return head;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("Holly.cmd.skull")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return filterPlayers(args[0]);
        }

        if (args.length == 2) {
            List<String> suggestions = new ArrayList<>(List.of("1", "16", "64"));
            String partial = args[1].toLowerCase(Locale.ROOT);
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase(Locale.ROOT).startsWith(partial)) {
                    suggestions.add(player.getName());
                }
            }
            return filter(suggestions, args[1]);
        }

        if (args.length == 3) {
            return filter(List.of("1", "16", "64"), args[2]);
        }

        return Collections.emptyList();
    }

    private List<String> filterPlayers(String partial) {
        String normalized = partial == null ? "" : partial.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().toLowerCase(Locale.ROOT).startsWith(normalized)) {
                matches.add(player.getName());
            }
        }
        return matches;
    }

    private List<String> filter(List<String> values, String partial) {
        String normalized = partial == null ? "" : partial.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(normalized)) {
                out.add(value);
            }
        }
        return out;
    }
}
