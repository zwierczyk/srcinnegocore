package dev.grzybalson.commands;

import dev.grzybalson.modules.zonebox.ZoneBoxManager;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class ZoneBoxCommand implements CommandExecutor, TabCompleter {

    private final ZoneBoxManager zoneBoxManager;

    public ZoneBoxCommand(ZoneBoxManager zoneBoxManager) {
        this.zoneBoxManager = zoneBoxManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("Holly.cmd.zonebox")) {
            sender.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.zonebox"));
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            zoneBoxManager.reload();
            sender.sendMessage(ColorUtils.success("Przeladowano zonebox i blockseconomy."));
            return true;
        }

        if (args[0].equalsIgnoreCase("add")) {
            if (args.length < 2) {
                sender.sendMessage(ColorUtils.usage("/zonebox add <blok>"));
                return true;
            }

            Material material = Material.matchMaterial(args[1].toUpperCase(Locale.ROOT));
            if (material == null || !material.isBlock()) {
                sender.sendMessage(ColorUtils.error("Nie znaleziono takiego bloku."));
                return true;
            }

            if (!zoneBoxManager.addAllowedBlock(material)) {
                sender.sendMessage(ColorUtils.info("Ten blok jest juz dodany albo nie udalo sie zapisac configu."));
                return true;
            }

            sender.sendMessage(ColorUtils.success("Dodano blok " + material.name() + " do zonebox."));
            return true;
        }

        sendUsage(sender);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("Holly.cmd.zonebox")) {
            return List.of();
        }

        if (args.length == 1) {
            return filter(List.of("add", "reload"), args[0]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("add")) {
            String prefix = args[1].toUpperCase(Locale.ROOT);
            List<String> blocks = new ArrayList<>();
            Arrays.stream(Material.values())
                .filter(Material::isBlock)
                .map(Material::name)
                .filter(name -> name.startsWith(prefix))
                .forEach(blocks::add);
            return blocks;
        }

        return List.of();
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ColorUtils.info("/zonebox add <blok>"));
        sender.sendMessage(ColorUtils.info("/zonebox reload"));
    }

    private List<String> filter(List<String> values, String prefix) {
        String normalizedPrefix = prefix.toLowerCase(Locale.ROOT);
        return values.stream()
            .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalizedPrefix))
            .toList();
    }
}
