package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.configs.CheckConfig;
import dev.grzybalson.gui.CheckMenuGUI;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class CheckCommand implements CommandExecutor, TabCompleter {

    private final HollyCore plugin;

    public CheckCommand(HollyCore plugin) {
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

        if (!player.hasPermission("Holly.cmd.sprawdz")) {
            player.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.sprawdz"));
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        CheckConfig config = plugin.getConfigManager().getCheckConfig();

        switch (subCommand) {
            case "setcheck", "setloc" -> {
                Location loc = player.getLocation();
                config.setCheckWorld(loc.getWorld().getName());
                config.setCheckX(loc.getX());
                config.setCheckY(loc.getY());
                config.setCheckZ(loc.getZ());
                config.setCheckYaw(loc.getYaw());
                config.setCheckPitch(loc.getPitch());
                config.setCheckLocationSet(true);
                config.save();

                player.sendMessage(ColorUtils.colorizeComponent(config.getMessageCheckLocationSet()));
            }
            case "menu" -> {
                if (!plugin.getCheckManager().isChecking(player)) {
                    player.sendMessage(ColorUtils.colorizeComponent(config.getMessageNotChecking()));
                    return true;
                }

                Player target = plugin.getCheckManager().getCheckedPlayer(player);
                if (target == null) {
                    player.sendMessage(ColorUtils.colorizeComponent(config.getMessagePlayerOffline()));
                    return true;
                }

                new CheckMenuGUI(plugin, player, target).open();
            }
            default -> {
                if (args.length >= 1) {
                    Player target = Bukkit.getPlayer(args[0]);
                    if (target == null) {
                        player.sendMessage(ColorUtils.colorizeComponent(config.getMessagePlayerOffline()));
                        return true;
                    }

                    if (target.equals(player)) {
                        player.sendMessage(ColorUtils.error("Nie możesz sprawdzać siebie!"));
                        return true;
                    }

                    plugin.getCheckManager().startCheck(player, target);
                } else {
                    sendHelp(player);
                }
            }
        }

        return true;
    }

    private void sendHelp(Player player) {
        ColorUtils.sendUsage(player,
            "/sprawdz <nick>",
            "/sprawdz menu",
            "/sprawdz setcheck",
            "/sprawdz setloc"
        );
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();

            if ("setcheck".startsWith(partial)) {
                completions.add("setcheck");
            }
            if ("setloc".startsWith(partial)) {
                completions.add("setloc");
            }
            if ("menu".startsWith(partial)) {
                completions.add("menu");
            }

            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(partial)) {
                    completions.add(player.getName());
                }
            }
        }

        return completions;
    }
}
