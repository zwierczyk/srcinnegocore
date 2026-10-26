package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
import dev.grzybalson.gui.KitGUI;
import dev.grzybalson.gui.KitPreviewGUI;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class KitCommand implements CommandExecutor, TabCompleter {

    private final HollyCore plugin;

    public KitCommand(HollyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cTa komenda jest tylko dla graczy!"));
            return true;
        }

        if (args.length == 0) {
            new KitGUI(plugin, player).open();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            return true;
        }

        // Jak w Holly Service Engine: nie dawaj kita z komendy — otwórz podgląd z przyciskiem odbioru.
        String kitName = args[0];
        if (plugin.getKitManager().getKit(kitName) == null) {
            player.sendMessage(ColorUtils.colorizeComponent(
                plugin.getConfigManager().getKitConfig().getNotFound().replace("{NAME}", kitName)
            ));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return true;
        }

        new KitPreviewGUI(plugin, player, kitName).open();
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            String input = args[0].toLowerCase();

            for (var kit : plugin.getKitManager().getAllKits()) {
                if (kit.getName().toLowerCase().startsWith(input) && player.hasPermission(kit.getPermission())) {
                    completions.add(kit.getName());
                }
            }

            return completions;
        }

        return new ArrayList<>();
    }
}
