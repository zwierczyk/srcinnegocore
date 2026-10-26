package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
import dev.grzybalson.gui.home.HomeMainMenuGUI;
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

public class HomeCommand implements CommandExecutor, TabCompleter {

    private final HollyCore plugin;
    private final HomeManager homeManager;

    public HomeCommand(HollyCore plugin, HomeManager homeManager) {
        this.plugin = plugin;
        this.homeManager = homeManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("holly.home.reload") && !sender.hasPermission("Holly.cmd.home.reload") && !sender.isOp()) {
                sender.sendMessage(ColorUtils.noPermission(label, "holly.home.reload"));
                return true;
            }
            plugin.getModuleConfigManager().getHomeConfig().load(true);
            sender.sendMessage(ColorUtils.success("Pomyślnie przeładowano konfigurację domków!"));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.colorizeComponent(plugin.getConfigManager().getMessagesConfig().getPlayerOnly()));
            return true;
        }

        if (!player.hasPermission("holly.home.use") && !player.hasPermission("Holly.cmd.home")) {
            player.sendMessage(ColorUtils.noPermission(label, "holly.home.use"));
            return true;
        }

        if (args.length == 0) {
            new HomeMainMenuGUI(plugin, player, homeManager).open();
            return true;
        }

        String target = args[0];
        PlayerHomesData data = homeManager.getPlayerHomes(player);

        // Check numeric slot argument (e.g. /home 1)
        try {
            int slotNum = Integer.parseInt(target);
            if (slotNum >= 1 && slotNum <= 7) {
                homeManager.teleportToHome(player, slotNum);
                return true;
            }
        } catch (NumberFormatException ignored) {}

        // Check home by name
        Home home = data.getHomeByName(target);
        if (home != null) {
            homeManager.teleportToHome(player, home.getHomeNumber());
            return true;
        }

        player.sendMessage(ColorUtils.error("Nie znaleziono domku o nazwie lub numerze: &e" + target));
        homeManager.playSound(player, homeManager.getConfig().getSounds().getError());
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            String input = args[0].toLowerCase();
            if ("reload".startsWith(input) && (sender.hasPermission("holly.home.reload") || sender.hasPermission("Holly.cmd.home.reload") || sender.isOp())) {
                suggestions.add("reload");
            }
            if (sender instanceof Player player) {
                PlayerHomesData data = homeManager.getPlayerHomes(player);
                for (Home home : data.getHomes().values()) {
                    String numStr = String.valueOf(home.getHomeNumber());
                    if (numStr.startsWith(input)) suggestions.add(numStr);
                    if (home.getName() != null && home.getName().toLowerCase().startsWith(input)) {
                        suggestions.add(home.getName());
                    }
                }
            }
            return suggestions;
        }
        return List.of();
    }
}
