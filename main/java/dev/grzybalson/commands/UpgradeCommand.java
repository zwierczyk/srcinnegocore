package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
import dev.grzybalson.gui.UpgradeGUI;
import dev.grzybalson.modules.upgrade.UpgradeType;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.ClickCooldown;
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
import java.util.Locale;

public class UpgradeCommand implements CommandExecutor, TabCompleter {

    private final HollyCore plugin;

    public UpgradeCommand(HollyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (plugin.getUpgradeManager() == null) {
            sender.sendMessage(ColorUtils.error("Addon ulepsz jest wyłączony."));
            return true;
        }

        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ColorUtils.colorizeComponent(plugin.getConfigManager().getMessagesConfig().getPlayerOnly()));
                return true;
            }
            if (!player.hasPermission("Holly.cmd.ulepsz")) {
                player.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.ulepsz"));
                return true;
            }
            if (!ClickCooldown.tryUse(player, "open-upgrade-gui", plugin.getUpgradeManager().getClickCooldownMillis())) {
                return true;
            }
            new UpgradeGUI(plugin, player).open();
            return true;
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);
        if (isAdminSubCommand(subCommand) && !hasAdminPermission(sender)) {
            sender.sendMessage(ColorUtils.noPermission(label + " " + subCommand, "Holly.cmd.ulepsz.admin"));
            return true;
        }
        switch (subCommand) {
            case "create" -> handleCreate(sender, label, args);
            case "get" -> handleGet(sender, label, args);
            case "remove" -> handleRemove(sender, label, args);
            case "debug" -> handleDebug(sender, label, args);
            case "reload" -> handleReload(sender, label);
            default -> sendHelp(sender);
        }
        return true;
    }

    private static boolean isAdminSubCommand(String subCommand) {
        return switch (subCommand) {
            case "create", "get", "remove", "debug", "reload" -> true;
            default -> false;
        };
    }

    private static boolean hasAdminPermission(CommandSender sender) {
        return sender != null && sender.hasPermission("Holly.cmd.ulepsz.admin");
    }

    private void handleCreate(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.colorizeComponent(plugin.getConfigManager().getMessagesConfig().getPlayerOnly()));
            return;
        }
        if (!player.hasPermission("Holly.cmd.ulepsz.admin")) {
            player.sendMessage(ColorUtils.noPermission(label + " create", "Holly.cmd.ulepsz.admin"));
            return;
        }
        if (args.length < 3) {
            sendUsage(player, "/ulepsz create <poziom> [set|typ] [cena]");
            return;
        }

        Integer level = parseInt(args[1]);
        if (level == null || level < 1) {
            player.sendMessage(ColorUtils.error("Poziom musi być poprawną liczbą dodatnią."));
            return;
        }

        UpgradeType forcedType = null;
        Double basePrice = null;

        if (args.length >= 4) {
            if (!args[2].equalsIgnoreCase("set") && !args[2].equalsIgnoreCase("auto")) {
                forcedType = UpgradeType.fromArgument(args[2]);
                if (forcedType == null) {
                    player.sendMessage(ColorUtils.error("Nieznany typ itemu. Użyj: set, auto lub nazwę typu."));
                    return;
                }
            }
            if (args[3].equalsIgnoreCase("auto")) {
                basePrice = 0.0;
            } else {
                basePrice = parseDouble(args[3]);
            }
        } else {
            Double maybePrice = parseDouble(args[2]);
            if (maybePrice != null) {
                basePrice = maybePrice;
            } else {
                forcedType = UpgradeType.fromArgument(args[2]);
                if (forcedType == null) {
                    player.sendMessage(ColorUtils.error("Nieznany typ itemu lub niepoprawna cena."));
                    return;
                }
                basePrice = 0.0;
            }
        }

        if (basePrice == null || basePrice < 0.0) {
            player.sendMessage(ColorUtils.error("Cena musi być poprawną liczbą."));
            return;
        }

        plugin.getUpgradeManager().createFromHand(player, level, basePrice, forcedType);
    }

    private void handleGet(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.colorizeComponent(plugin.getConfigManager().getMessagesConfig().getPlayerOnly()));
            return;
        }
        if (!player.hasPermission("Holly.cmd.ulepsz.admin")) {
            player.sendMessage(ColorUtils.noPermission(label + " get", "Holly.cmd.ulepsz.admin"));
            return;
        }
        if (args.length < 2) {
            sendUsage(player, "/ulepsz get <poziom> [typ]");
            return;
        }

        Integer level = parseInt(args[1]);
        if (level == null || level < 1) {
            player.sendMessage(ColorUtils.error("Poziom musi być poprawną liczbą."));
            return;
        }

        if (args.length >= 3 && (args[2].equalsIgnoreCase("excalibur") || args[2].equalsIgnoreCase("nowyexcalibur"))) {
            ItemStack excalibur = plugin.getUpgradeManager().createExcaliburItem(level);
            HashMap<Integer, ItemStack> leftovers = player.getInventory().addItem(excalibur);
            leftovers.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
            player.sendMessage(ColorUtils.success("Nadano &eExcalibur &az poziomu &e" + level + "&a."));
            return;
        }

        UpgradeType type = null;
        if (args.length >= 3 && !args[2].equalsIgnoreCase("all")) {
            type = UpgradeType.fromArgument(args[2]);
            if (type == null) {
                player.sendMessage(ColorUtils.error("Nieznany typ itemu."));
                return;
            }
        }

        List<ItemStack> items = plugin.getUpgradeManager().getItemsForLevel(level, type);
        if (items.isEmpty()) {
            player.sendMessage(ColorUtils.error("Brak itemów dla tego poziomu."));
            return;
        }

        for (ItemStack item : items) {
            HashMap<Integer, ItemStack> leftovers = player.getInventory().addItem(withoutUid(item.clone()));
            leftovers.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        }
        player.sendMessage(ColorUtils.success("Nadano itemy z poziomu &e" + level + "&a."));
    }

    private ItemStack withoutUid(ItemStack item) {
        if (plugin.getUniqueItemService() != null && item != null) {
            plugin.getUniqueItemService().clearUid(item);
        }
        return item;
    }

    private void handleRemove(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            sendUsage(sender, "/ulepsz remove <poziom>");
            return;
        }

        Integer level = parseInt(args[1]);
        if (level == null || level < 1) {
            sender.sendMessage(ColorUtils.error("Poziom musi być poprawną liczbą dodatnią."));
            return;
        }

        int removed = plugin.getUpgradeManager().removeArmorLevel(level);
        if (removed == 0) {
            sender.sendMessage(ColorUtils.error("Nie znaleziono poziomu zbroi &e" + level + "&c w aktywnym stylu."));
            return;
        }
        sender.sendMessage(ColorUtils.success("Usunięto poziom zbroi &e" + level + "&a z &e" + removed + " &atypów."));
    }

    private void handleDebug(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("Holly.cmd.ulepsz.admin")) {
            sender.sendMessage(ColorUtils.noPermission(label + " debug", "Holly.cmd.ulepsz.admin"));
            return;
        }

        UpgradeType type = null;
        Integer level = null;
        if (args.length >= 2 && !args[1].equalsIgnoreCase("all")) {
            type = UpgradeType.fromArgument(args[1]);
            if (type == null) {
                level = parseInt(args[1]);
                if (level == null) {
                    sender.sendMessage(ColorUtils.usage("/ulepsz debug [typ|poziom] [poziom]"));
                    return;
                }
            }
        }
        if (args.length >= 3) {
            level = parseInt(args[2]);
            if (level == null) {
                sender.sendMessage(ColorUtils.error("Poziom musi byc poprawna liczba."));
                return;
            }
        }

        for (String line : plugin.getUpgradeManager().getDebugReport(type, level)) {
            sender.sendMessage(ColorUtils.colorizeComponent(line));
        }
    }

    private void handleReload(CommandSender sender, String label) {
        if (!sender.hasPermission("Holly.cmd.ulepsz.admin")) {
            sender.sendMessage(ColorUtils.noPermission(label + " reload", "Holly.cmd.ulepsz.admin"));
            return;
        }
        plugin.getUpgradeManager().reload();
        sender.sendMessage(ColorUtils.success("Przeładowano addon ulepsz."));
    }

    private void sendHelp(CommandSender sender) {
        if (!hasAdminPermission(sender)) {
            sendUsage(sender, "/ulepsz");
            return;
        }
        sendUsage(
            sender,
            "/ulepsz",
            "/ulepsz create <poziom> [set|typ] <cena>",
            "/ulepsz get <poziom> [typ]",
            "/ulepsz remove <poziom>",
            "/ulepsz debug [typ|poziom] [poziom]",
            "/ulepsz reload"
        );
    }

    private void sendUsage(CommandSender sender, String... commands) {
        ColorUtils.sendUsage(sender, commands);
    }

    private Integer parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Double parseDouble(String value) {
        try {
            return Double.parseDouble(value.replace(',', '.'));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!hasAdminPermission(sender)) {
            return List.of();
        }

        if (args.length == 1) {
            return filterCompletions(Arrays.asList("create", "get", "remove", "debug", "reload"), args[0]);
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && (subCommand.equals("create") || subCommand.equals("get") || subCommand.equals("remove"))) {
            return filterCompletions(Arrays.asList("1", "2", "3", "4", "5", "8", "10", "20"), args[1]);
        }

        if (args.length == 2 && subCommand.equals("debug")) {
            List<String> completions = new ArrayList<>();
            completions.add("all");
            completions.addAll(upgradeTypeCompletions());
            completions.addAll(Arrays.asList("1", "2", "3", "4", "5", "8", "10", "20"));
            return filterCompletions(completions, args[1]);
        }

        if (args.length == 3 && subCommand.equals("create")) {
            List<String> completions = new ArrayList<>();
            completions.add("set");
            completions.add("auto");
            completions.addAll(upgradeTypeCompletions());
            completions.addAll(Arrays.asList("1000", "2500", "5000"));
            return filterCompletions(completions, args[2]);
        }

        if (args.length == 4 && subCommand.equals("create")) {
            return filterCompletions(Arrays.asList("1000", "2500", "5000", "10000", "25000", "50000"), args[3]);
        }

        if (args.length == 3 && subCommand.equals("get")) {
            List<String> types = new ArrayList<>();
            types.add("all");
            types.addAll(upgradeTypeCompletions());
            return filterCompletions(types, args[2]);
        }

        if (args.length == 3 && subCommand.equals("debug")) {
            return filterCompletions(Arrays.asList("1", "2", "3", "4", "5", "8", "10", "20"), args[2]);
        }

        return List.of();
    }

    private List<String> upgradeTypeCompletions() {
        List<String> types = new ArrayList<>();
        for (UpgradeType type : UpgradeType.values()) {
            types.add(type.name().toLowerCase(Locale.ROOT));
        }
        types.addAll(Arrays.asList(
            "miecz", "bron", "excalibur", "helm", "klata", "zbroja", "napiersnik",
            "spodnie", "buty", "kilof", "hoe", "motyka", "nozyce", "siekiera", "siekiery", "topor", "axe"
        ));
        return types;
    }

    private List<String> filterCompletions(List<String> values, String partial) {
        String normalized = partial == null ? "" : partial.toLowerCase();
        List<String> completions = new ArrayList<>();
        for (String value : values) {
            if (value.toLowerCase().startsWith(normalized)) {
                completions.add(value);
            }
        }
        return completions;
    }
}
