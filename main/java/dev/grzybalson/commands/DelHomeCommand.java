package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
import dev.grzybalson.modules.home.Home;
import dev.grzybalson.modules.home.HomeManager;
import dev.grzybalson.modules.home.PlayerHomesData;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class DelHomeCommand implements CommandExecutor, TabCompleter {

    private final HollyCore plugin;
    private final HomeManager homeManager;

    public DelHomeCommand(HollyCore plugin, HomeManager homeManager) {
        this.plugin = plugin;
        this.homeManager = homeManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.colorizeComponent(plugin.getConfigManager().getMessagesConfig().getPlayerOnly()));
            return true;
        }

        if (!player.hasPermission("holly.home.use") && !player.hasPermission("Holly.cmd.delhome")) {
            player.sendMessage(ColorUtils.noPermission(label, "holly.home.use"));
            return true;
        }

        if (args.length < 1) {
            ColorUtils.sendUsage(player, "/delhome <numer|nazwa>");
            return true;
        }

        String target = args[0];
        PlayerHomesData data = homeManager.getPlayerHomes(player);

        int slotNum = -1;
        try {
            slotNum = Integer.parseInt(target);
        } catch (NumberFormatException ignored) {}

        if (slotNum == -1) {
            Home home = data.getHomeByName(target);
            if (home != null) {
                slotNum = home.getHomeNumber();
            }
        }

        if (slotNum == -1 || data.getHome(slotNum) == null) {
            player.sendMessage(ColorUtils.error("Nie znaleziono domku o podanej nazwie lub numerze: &e" + target));
            homeManager.playSound(player, homeManager.getConfig().getSounds().getError());
            return true;
        }

        homeManager.deleteHome(player, slotNum);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1 && sender instanceof Player player) {
            List<String> suggestions = new ArrayList<>();
            String input = args[0].toLowerCase();
            PlayerHomesData data = homeManager.getPlayerHomes(player);

            for (Home home : data.getHomes().values()) {
                String numStr = String.valueOf(home.getHomeNumber());
                if (numStr.startsWith(input)) suggestions.add(numStr);
                if (home.getName() != null && home.getName().toLowerCase().startsWith(input)) {
                    suggestions.add(home.getName());
                }
            }
            return suggestions;
        }
        return List.of();
    }
}
