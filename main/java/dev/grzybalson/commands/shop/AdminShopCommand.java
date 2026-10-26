package dev.grzybalson.commands.shop;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.configs.ShopConfig;
import dev.grzybalson.gui.ShopGUI;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.ItemSerializer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AdminShopCommand implements CommandExecutor, TabCompleter {

    private final HollyCore plugin;
    private static final Pattern TIME_PATTERN = Pattern.compile("(\\d+)([mhd])");
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.builder()
        .character('&')
        .hexColors()
        .build();

    public AdminShopCommand(HollyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.colorizeComponent(
                plugin.getConfigManager().getMessagesConfig().getPlayerOnly()
            ));
            return true;
        }

        if (!player.hasPermission("Holly.cmd.asklep")) {
            player.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.asklep"));
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            plugin.getConfigManager().getShopConfig().load(true);
            player.sendMessage(ColorUtils.success("Przeładowano konfigurację sklepu!"));
            return true;
        }

        if (args.length < 4 || !args[0].equalsIgnoreCase("additem")) {
            ColorUtils.sendUsage(player,
                "/asklep additem <money|license|time|kills> <slot> <cena> [true|false]",
                "/asklep additem sell <slot> <cena> <ilość> [true|false]",
                "/asklep reload"
            );
            player.sendMessage(ColorUtils.info("Dla czasu użyj formatu: 1m, 1h, 1d"));
            player.sendMessage(ColorUtils.info("Dla sell: /asklep additem sell <slot> <cena> <ilość> [true|false]"));
            player.sendMessage(ColorUtils.info("Domyślnie przedmiot jest włączony (true). Możesz podać false, aby go wyłączyć z zakupu."));
            return true;
        }

        String type = args[1].toLowerCase();
        if (!type.equals("money") && !type.equals("license") && !type.equals("time") && !type.equals("sell") && !type.equals("kills")) {
            player.sendMessage(ColorUtils.error("Typ musi być 'money', 'license', 'time', 'sell' lub 'kills'!"));
            return true;
        }

        if (type.equals("license") && !ShopGUI.isLicenseShopLicenseActive(plugin)) {
            player.sendMessage(ColorUtils.error("Sklep licencyjny jest dostepny tylko dla wymaganej licencji."));
            return true;
        }

        int slot;
        try {
            slot = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage(ColorUtils.error("Slot musi być liczbą!"));
            return true;
        }

        if (slot < 0 || slot >= 54) {
            player.sendMessage(ColorUtils.error("Slot musi być w zakresie 0-53!"));
            return true;
        }

        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (itemInHand.getType() == Material.AIR) {
            player.sendMessage(ColorUtils.error("Musisz trzymać przedmiot w ręce!"));
            return true;
        }

        String serializedItem = ItemSerializer.serializeItem(itemInHand);
        ShopConfig shopConfig = plugin.getConfigManager().getShopConfig();

        if (type.equals("money") || type.equals("license")) {
            double price;
            try {
                price = Double.parseDouble(args[3]);
            } catch (NumberFormatException e) {
                player.sendMessage(ColorUtils.error("Cena musi być liczbą!"));
                return true;
            }
            if (price <= 0) {
                player.sendMessage(ColorUtils.error("Cena musi być większa od 0!"));
                return true;
            }

            boolean enabled = true;
            if (args.length >= 5) {
                Boolean parsed = parseBooleanArg(args[4]);
                if (parsed == null) {
                    player.sendMessage(ColorUtils.error("Wartość włączenia musi być 'true' lub 'false'!"));
                    return true;
                }
                enabled = parsed;
            }

            ShopConfig.ShopItem shopItem = new ShopConfig.ShopItem(serializedItem, price);
            shopItem.setEnabled(enabled);
            applyEditableMeta(shopItem, itemInHand);
            if (type.equals("money")) {
                shopConfig.addMoneyItem(slot, shopItem);
            } else {
                shopConfig.addLicenseItem(slot, shopItem);
            }

            if (enabled) {
                player.sendMessage(ColorUtils.colorizeComponent(
                    plugin.getConfigManager().getMessagesConfig().getShopItemAdded()
                ));
            } else {
                player.sendMessage(ColorUtils.colorizeComponent(
                    "&#00FF3B✔ &8Ξ &#4EFA76Dodano przedmiot do sklepu &c(wyłączony z zakupu)&a!"
                ));
            }
        } else if (type.equals("time")) {
            long timeMillis = parseTime(args[3]);
            if (timeMillis <= 0) {
                player.sendMessage(ColorUtils.error("Nieprawidłowy format czasu! Użyj: 1m, 1h, 1d"));
                return true;
            }

            boolean enabled = true;
            if (args.length >= 5) {
                Boolean parsed = parseBooleanArg(args[4]);
                if (parsed == null) {
                    player.sendMessage(ColorUtils.error("Wartość włączenia musi być 'true' lub 'false'!"));
                    return true;
                }
                enabled = parsed;
            }

            ShopConfig.ShopItem shopItem = new ShopConfig.ShopItem(serializedItem, timeMillis);
            shopItem.setEnabled(enabled);
            applyEditableMeta(shopItem, itemInHand);
            shopConfig.addTimeItem(slot, shopItem);

            if (enabled) {
                player.sendMessage(ColorUtils.colorizeComponent(
                    plugin.getConfigManager().getMessagesConfig().getShopItemAdded()
                ));
            } else {
                player.sendMessage(ColorUtils.colorizeComponent(
                    "&#00FF3B✔ &8Ξ &#4EFA76Dodano przedmiot do sklepu &c(wyłączony z zakupu)&a!"
                ));
            }
        } else if (type.equals("sell")) {
            if (args.length < 5) {
                ColorUtils.sendUsage(player, "/asklep additem sell <slot> <cena> <ilość> [true|false]");
                return true;
            }

            double sellPrice;
            try {
                sellPrice = Double.parseDouble(args[3]);
            } catch (NumberFormatException e) {
                player.sendMessage(ColorUtils.error("Cena musi być liczbą!"));
                return true;
            }

            int amount;
            try {
                amount = Integer.parseInt(args[4]);
            } catch (NumberFormatException e) {
                player.sendMessage(ColorUtils.error("Ilość musi być liczbą!"));
                return true;
            }

            if (sellPrice <= 0 || amount <= 0) {
                player.sendMessage(ColorUtils.error("Cena i ilość muszą być większe od 0!"));
                return true;
            }

            boolean enabled = true;
            if (args.length >= 6) {
                Boolean parsed = parseBooleanArg(args[5]);
                if (parsed == null) {
                    player.sendMessage(ColorUtils.error("Wartość włączenia musi być 'true' lub 'false'!"));
                    return true;
                }
                enabled = parsed;
            }

            String materialName = itemInHand.getType().name();
            ShopConfig.SellItem sellItem = new ShopConfig.SellItem(materialName, sellPrice, amount, serializedItem);
            sellItem.setEnabled(enabled);
            applyEditableMeta(sellItem, itemInHand);
            shopConfig.addSellItem(slot, sellItem);

            if (enabled) {
                player.sendMessage(ColorUtils.colorizeComponent(
                    "&#00FF3B✔ &8Ξ &#4EFA76Dodano przedmiot do skupu: &e" + materialName + " &ax" + amount + " &7za &e" + sellPrice + "$"
                ));
            } else {
                player.sendMessage(ColorUtils.colorizeComponent(
                    "&#00FF3B✔ &8Ξ &#4EFA76Dodano przedmiot do skupu: &e" + materialName + " &ax" + amount + " &7za &e" + sellPrice + "$ &c(wyłączony ze sprzedaży)"
                ));
            }
        } else if (type.equals("kills")) {
            int killsPrice;
            try {
                killsPrice = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                player.sendMessage(ColorUtils.error("Cena musi być liczbą całkowitą!"));
                return true;
            }

            if (killsPrice <= 0) {
                player.sendMessage(ColorUtils.error("Cena musi być większa od 0!"));
                return true;
            }

            boolean enabled = true;
            if (args.length >= 5) {
                Boolean parsed = parseBooleanArg(args[4]);
                if (parsed == null) {
                    player.sendMessage(ColorUtils.error("Wartość włączenia musi być 'true' lub 'false'!"));
                    return true;
                }
                enabled = parsed;
            }

            ShopConfig.ShopItem shopItem = new ShopConfig.ShopItem(serializedItem, killsPrice, true);
            shopItem.setEnabled(enabled);
            applyEditableMeta(shopItem, itemInHand);
            shopConfig.addKillsItem(slot, shopItem);

            if (enabled) {
                player.sendMessage(ColorUtils.colorizeComponent(
                    plugin.getConfigManager().getMessagesConfig().getShopItemAdded()
                ));
            } else {
                player.sendMessage(ColorUtils.colorizeComponent(
                    "&#00FF3B✔ &8Ξ &#4EFA76Dodano przedmiot do sklepu &c(wyłączony z zakupu)&a!"
                ));
            }
        }

        return true;
    }

    private Boolean parseBooleanArg(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String lower = input.trim().toLowerCase();
        if (lower.equals("false") || lower.equals("nie") || lower.equals("off") || lower.equals("0") || lower.equals("wylaczony") || lower.equals("wyłączony")) {
            return Boolean.FALSE;
        }
        if (lower.equals("true") || lower.equals("tak") || lower.equals("on") || lower.equals("1") || lower.equals("wlaczony") || lower.equals("włączony")) {
            return Boolean.TRUE;
        }
        return null;
    }

    private void applyEditableMeta(ShopConfig.ShopItem shopItem, ItemStack source) {
        shopItem.setDisplayName(extractDisplayName(source));
        shopItem.setLore(extractLore(source));
    }

    private void applyEditableMeta(ShopConfig.SellItem sellItem, ItemStack source) {
        sellItem.setDisplayName(extractDisplayName(source));
        sellItem.setLore(extractLore(source));
    }

    private String extractDisplayName(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return null;
        }

        Component displayName = meta.displayName();
        return displayName != null ? LEGACY_SERIALIZER.serialize(displayName) : null;
    }

    private List<String> extractLore(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null || meta.lore() == null) {
            return new ArrayList<>();
        }

        List<String> lore = new ArrayList<>();
        for (Component line : meta.lore()) {
            lore.add(LEGACY_SERIALIZER.serialize(line));
        }
        return lore;
    }

    private long parseTime(String input) {
        Matcher matcher = TIME_PATTERN.matcher(input.toLowerCase());
        if (!matcher.matches()) {
            return -1;
        }

        long value;
        try {
            value = Long.parseLong(matcher.group(1));
        } catch (NumberFormatException exception) {
            return -1;
        }
        if (value <= 0 || value > 3_650_000L) {
            return -1;
        }
        String unit = matcher.group(2);

        return switch (unit) {
            case "m" -> value * 60L * 1000L;
            case "h" -> value * 60L * 60L * 1000L;
            case "d" -> value * 24L * 60L * 60L * 1000L;
            default -> -1;
        };
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            if ("additem".startsWith(args[0].toLowerCase())) {
                completions.add("additem");
            }
            if ("reload".startsWith(args[0].toLowerCase())) {
                completions.add("reload");
            }
        } else if (args.length == 2) {
            String partial = args[1].toLowerCase();
            for (String type : List.of("money", "license", "time", "sell", "kills")) {
                if (type.equals("license") && !ShopGUI.isLicenseShopLicenseActive(plugin)) {
                    continue;
                }
                if (type.startsWith(partial)) {
                    completions.add(type);
                }
            }
        } else if (args.length == 3) {
            completions.add("<slot>");
        } else if (args.length == 4) {
            if (args[1].equalsIgnoreCase("time")) {
                completions.add("1m");
                completions.add("1h");
                completions.add("1d");
            } else {
                completions.add("<cena>");
            }
        } else if (args.length == 5) {
            if (args[1].equalsIgnoreCase("sell")) {
                completions.add("<ilość>");
            } else {
                String partial = args[4].toLowerCase();
                for (String opt : List.of("true", "false")) {
                    if (opt.startsWith(partial)) {
                        completions.add(opt);
                    }
                }
            }
        } else if (args.length == 6) {
            if (args[1].equalsIgnoreCase("sell")) {
                String partial = args[5].toLowerCase();
                for (String opt : List.of("true", "false")) {
                    if (opt.startsWith(partial)) {
                        completions.add(opt);
                    }
                }
            }
        }
        return completions;
    }
}
