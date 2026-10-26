package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
import dev.grzybalson.gui.BlockEconomyGUI;
import dev.grzybalson.modules.zonebox.ZoneBoxManager;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.MoneyAmountUtil;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class BlockEconomyCommand implements CommandExecutor, TabCompleter {

    private final HollyCore plugin;
    private final ZoneBoxManager zoneBoxManager;

    public BlockEconomyCommand(HollyCore plugin, ZoneBoxManager zoneBoxManager) {
        this.plugin = plugin;
        this.zoneBoxManager = zoneBoxManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("Holly.cmd.blockseconomy")) {
            sender.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.blockseconomy"));
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("menu")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ColorUtils.error("GUI jest tylko dla graczy. Użyj /" + label + " set/remove/reload."));
                return true;
            }
            new BlockEconomyGUI(plugin, zoneBoxManager, player).open();
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            zoneBoxManager.reloadBlockEconomy();
            sender.sendMessage(ColorUtils.success("Przeładowano blockseconomy."));
            return true;
        }

        if (args[0].equalsIgnoreCase("set")) {
            if (args.length < 3) {
                sender.sendMessage(ColorUtils.info("/" + label + " set <blok> <cena>"));
                return true;
            }
            Material material = parseBlock(args[1]);
            if (material == null) {
                sender.sendMessage(ColorUtils.error("Nieprawidłowy blok."));
                return true;
            }
            Double price = MoneyAmountUtil.parse(args[2]);
            if (price == null || price < 0.0D) {
                sender.sendMessage(ColorUtils.error("Nieprawidłowa cena."));
                return true;
            }
            zoneBoxManager.setBlockPrice(material, price);
            sender.sendMessage(ColorUtils.success("Cena &f" + material.name() + " &7→ &a" + MoneyAmountUtil.formatCompact(price) + "$"));
            if (sender instanceof Player player) {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6F, 1.6F);
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("remove")) {
            if (args.length < 2) {
                sender.sendMessage(ColorUtils.info("/" + label + " remove <blok>"));
                return true;
            }
            Material material = parseBlock(args[1]);
            if (material == null || !zoneBoxManager.removeBlockPrice(material)) {
                sender.sendMessage(ColorUtils.error("Ten blok nie ma ceny w blockseconomy."));
                return true;
            }
            sender.sendMessage(ColorUtils.success("Usunięto cenę &f" + material.name() + "&7."));
            if (sender instanceof Player player) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5F, 1.2F);
            }
            return true;
        }

        sender.sendMessage(ColorUtils.info("/" + label + " [menu|reload|set <blok> <cena>|remove <blok>]"));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("Holly.cmd.blockseconomy")) {
            return List.of();
        }
        String typed = args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            return filter(List.of("menu", "reload", "set", "remove"), typed);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            return filter(Arrays.stream(Material.values()).filter(Material::isBlock).map(Enum::name).toList(), typed);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("remove")) {
            return filter(zoneBoxManager.getConfiguredBlockEconomyMaterials().stream().map(Enum::name).toList(), typed);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            return filter(List.of("0.01", "0.1", "1", "10"), typed);
        }
        return List.of();
    }

    private static Material parseBlock(String input) {
        Material material = Material.matchMaterial(input.trim().toUpperCase(Locale.ROOT));
        return material != null && material.isBlock() ? material : null;
    }

    private static List<String> filter(List<String> options, String typed) {
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(typed) || option.toLowerCase(Locale.ROOT).contains(typed)) {
                out.add(option);
                if (out.size() >= 40) {
                    break;
                }
            }
        }
        return out;
    }
}
