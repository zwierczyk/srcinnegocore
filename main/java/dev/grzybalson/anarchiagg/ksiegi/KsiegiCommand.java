package dev.grzybalson.anarchiagg.ksiegi;

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
import java.util.List;
import java.util.Locale;

public class KsiegiCommand implements CommandExecutor, TabCompleter {
    private final KsiegiManager manager;

    public KsiegiCommand(KsiegiManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.error("Ta komenda jest tylko dla gracza."));
            return true;
        }

        if (args.length == 0) {
            player.openInventory(manager.createGui());
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("reload")) {
            manager.reload();
            player.sendMessage(ColorUtils.success("Przeładowano księgi."));
            return true;
        }

        if (sub.equals("give") && args.length >= 3) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                player.sendMessage(ColorUtils.error("Nie ma takiego gracza online."));
                return true;
            }

            if (args[2].equalsIgnoreCase("all")) {
                for (KsiegiManager.BookType type : KsiegiManager.BookType.values()) {
                    ItemStack book = manager.getBook(type);
                    if (book != null) {
                        target.getInventory().addItem(book);
                    }
                }
                player.sendMessage(ColorUtils.success("Dano wszystkie księgi graczowi &f" + target.getName() + "&a."));
                return true;
            }

            KsiegiManager.BookType type = KsiegiManager.BookType.byId(args[2]);
            if (type == null) {
                player.sendMessage(ColorUtils.error("Nieznana księga."));
                return true;
            }

            ItemStack book = manager.getBook(type);
            if (book == null) {
                player.sendMessage(ColorUtils.error("Ta księga nie została wczytana z guiitems.yml."));
                return true;
            }
            target.getInventory().addItem(book);
            player.sendMessage(ColorUtils.success("Dano księgę &f" + type.id() + " &agraczowi &f" + target.getName() + "&a."));
            return true;
        }

        ColorUtils.sendUsage(player,
            "/ksiegi",
            "/ksiegi reload",
            "/ksiegi give <gracz> <id|all>"
        );
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (args.length == 1) {
            return filter(Arrays.asList("reload", "give"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            List<String> players = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                players.add(online.getName());
            }
            return filter(players, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            List<String> ids = new ArrayList<>();
            ids.add("all");
            for (KsiegiManager.BookType type : KsiegiManager.BookType.values()) {
                ids.add(type.id());
            }
            return filter(ids, args[2]);
        }
        return List.of();
    }

    private List<String> filter(List<String> values, String start) {
        String lowered = start.toLowerCase(Locale.ROOT);
        return values.stream().filter(v -> v.toLowerCase(Locale.ROOT).startsWith(lowered)).toList();
    }
}
