package dev.grzybalson.anarchiagg.zmianki;

import dev.grzybalson.utils.ColorUtils;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import dev.grzybalson.anarchiagg.zmiankibattle.command.ZmiankaBattleCommand;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class ZmiankiCommand implements CommandExecutor, TabCompleter {
    private static final String PERMISSION_USE = "holly.zmianki.use";
    private static final String PERMISSION_ADMIN = "holly.zmianki.admin";
    private static final String PERMISSION_BATTLE = "holly.zmianki.battle";

    private final ZmiankiManager manager;
    private final ZmiankiGUIHandler guiHandler;
    private final ZmiankaBattleCommand battleCommand;

    public ZmiankiCommand(ZmiankiManager manager, ZmiankiGUIHandler guiHandler, ZmiankaBattleCommand battleCommand) {
        this.manager = manager;
        this.guiHandler = guiHandler;
        this.battleCommand = battleCommand;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        boolean adminLabel = label.equalsIgnoreCase("azmianki") || label.equalsIgnoreCase("aszczesciarz");

        if (args.length == 0) {
            if (adminLabel) {
                if (!sender.hasPermission(PERMISSION_ADMIN)) {
                    sender.sendMessage(ColorUtils.noPermission(label, PERMISSION_ADMIN));
                    return true;
                }
                sendAdminUsage(sender, label);
                return true;
            }
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ColorUtils.error("Ta komenda jest tylko dla gracza."));
                return true;
            }
            if (!hasUseAccess(player)) {
                player.sendMessage(ColorUtils.noPermission(label, PERMISSION_USE));
                return true;
            }
            guiHandler.openGUI(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("setitem")) {
            if (!sender.hasPermission(PERMISSION_ADMIN)) {
                sender.sendMessage(ColorUtils.noPermission(label + " setitem", PERMISSION_ADMIN));
                return true;
            }
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ColorUtils.error("Ta komenda jest tylko dla graczy."));
                return true;
            }
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand == null || hand.getType() == Material.AIR) {
                sender.sendMessage(ColorUtils.error("Trzymaj item zmianki w ręce (z CMD / lore / nazwą)."));
                return true;
            }
            if (!manager.setTemplateItem(hand)) {
                sender.sendMessage(ColorUtils.error("Nie udało się zapisać itemu zmianki."));
                return true;
            }
            sender.sendMessage(ColorUtils.success("Ustawiono item zmianki z ręki (typ, CMD, meta, lore)."));
            return true;
        }

        if (sub.equals("set")) {
            if (!sender.hasPermission(PERMISSION_ADMIN)) {
                sender.sendMessage(ColorUtils.noPermission(label + " set", PERMISSION_ADMIN));
                return true;
            }
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ColorUtils.error("Ta komenda jest tylko dla graczy."));
                return true;
            }
            if (args.length < 2) {
                ColorUtils.sendUsage(sender, "/" + label + " set <procent>");
                return true;
            }

            int percent;
            try {
                percent = Integer.parseInt(args[1]);
            } catch (NumberFormatException exception) {
                sender.sendMessage(ColorUtils.error("Procent musi być liczbą całkowitą."));
                return true;
            }

            ItemStack item = player.getInventory().getItemInMainHand();
            if (item == null || item.getType() == Material.AIR) {
                sender.sendMessage(ColorUtils.error("Musisz trzymać miecz/przedmiot w ręce."));
                return true;
            }

            manager.applyBonus(item, percent);
            player.getInventory().setItemInMainHand(item);
            sender.sendMessage(ColorUtils.success("Ustawiono dodatkowe obrażenia na mieczu na &f" + percent + "%&a."));
            return true;
        }

        if (isBattleSubcommand(sub)) {
            if (adminLabel) {
                sender.sendMessage(ColorUtils.error("Bitwy są na &f/zmianki&c, nie na /azmianki."));
                return true;
            }
            if (!sender.hasPermission(PERMISSION_BATTLE)) {
                sender.sendMessage(ColorUtils.noPermission(label + " " + sub, PERMISSION_BATTLE));
                return true;
            }
            if (battleCommand == null) {
                sender.sendMessage(ColorUtils.error("Moduł bitew na zmianki nie jest dostępny."));
                return true;
            }
            return battleCommand.onCommand(sender, command, label, args);
        }

        if (sub.equals("reload")) {
            if (!sender.hasPermission(PERMISSION_ADMIN)) {
                sender.sendMessage(ColorUtils.noPermission(label + " reload", PERMISSION_ADMIN));
                return true;
            }
            manager.reload();
            if (battleCommand != null) {
                battleCommand.getPlugin().loadConfig();
            }
            sender.sendMessage(ColorUtils.success("Przeładowano zmianki."));
            return true;
        }

        if (sub.equals("give")) {
            if (!sender.hasPermission(PERMISSION_ADMIN)) {
                sender.sendMessage(ColorUtils.noPermission(label + " give", PERMISSION_ADMIN));
                return true;
            }
            if (args.length < 2) {
                ColorUtils.sendUsage(sender, "/" + label + " give <nick> [ilość]");
                return true;
            }

            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(ColorUtils.error("Nie ma takiego gracza online."));
                return true;
            }

            int amount = 1;
            if (args.length >= 3) {
                try {
                    amount = Integer.parseInt(args[2]);
                } catch (NumberFormatException exception) {
                    sender.sendMessage(ColorUtils.error("Ilość musi być liczbą."));
                    return true;
                }
            }

            if (amount <= 0) {
                sender.sendMessage(ColorUtils.error("Ilość musi być większa od 0."));
                return true;
            }

            while (amount > 0) {
                int stackSize = Math.min(64, amount);
                ItemStack item = manager.buildZmiankaItem(stackSize);
                target.getInventory().addItem(item);
                amount -= stackSize;
            }

            sender.sendMessage(ColorUtils.success("Dano zmianki graczowi &f" + target.getName() + "&a."));
            return true;
        }

        if (adminLabel) {
            sendAdminUsage(sender, label);
        } else {
            ColorUtils.sendUsage(sender,
                "/" + label,
                "/" + label + " give <nick> [ilość]",
                "/" + label + " set <procent>",
                "/" + label + " setitem",
                "/" + label + " reload",
                "/" + label + " global <kwota>",
                "/" + label + " wyzwij <nick> <kwota>",
                "/" + label + " bitwa zaakceptuj <nick>"
            );
        }
        return true;
    }

    private void sendAdminUsage(CommandSender sender, String label) {
        ColorUtils.sendUsage(sender,
            "/" + label + " give <nick> [ilość]",
            "/" + label + " setitem",
            "/" + label + " set <procent>",
            "/" + label + " reload"
        );
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        boolean adminLabel = alias.equalsIgnoreCase("azmianki") || alias.equalsIgnoreCase("aszczesciarz");
        if (args.length == 1) {
            List<String> values = new ArrayList<>();
            if (!adminLabel && sender.hasPermission(PERMISSION_BATTLE)) {
                values.addAll(Arrays.asList("global", "wyzwij", "bitwa"));
            }
            if (sender.hasPermission(PERMISSION_ADMIN)) {
                values.addAll(Arrays.asList("set", "setitem", "reload", "give"));
            }
            return filter(values, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("set") && sender.hasPermission(PERMISSION_ADMIN)) {
            return filter(Arrays.asList("5", "13", "30", "43"), args[1]);
        }
        if (!adminLabel && battleCommand != null && isBattleSubcommand(args[0])) {
            if (!sender.hasPermission(PERMISSION_BATTLE)) {
                return List.of();
            }
            return battleCommand.onTabComplete(sender, command, alias, args);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            if (!sender.hasPermission(PERMISSION_ADMIN)) {
                return List.of();
            }
            List<String> players = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                players.add(online.getName());
            }
            return filter(players, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            if (!sender.hasPermission(PERMISSION_ADMIN)) {
                return List.of();
            }
            return filter(Arrays.asList("1", "8", "16", "32", "64"), args[2]);
        }
        return List.of();
    }

    private boolean isBattleSubcommand(String sub) {
        return sub.equals("global") || sub.equals("wyzwij") || sub.equals("bitwa");
    }

    private boolean hasUseAccess(CommandSender sender) {
        return sender.hasPermission(PERMISSION_USE)
            || sender.hasPermission(PERMISSION_ADMIN)
            || sender.hasPermission("Holly.kowal.use")
            || sender.hasPermission("holly.kowal.use");
    }

    private List<String> filter(List<String> values, String start) {
        String lowered = start.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lowered)).toList();
    }
}
