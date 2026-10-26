package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class FlySpeedCommand implements CommandExecutor, TabCompleter {

    private final HollyCore plugin;

    public FlySpeedCommand(HollyCore plugin) {
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

        if (!player.hasPermission("Holly.cmd.flyspeed")) {
            player.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.flyspeed"));
            return true;
        }

        if (args.length < 1) {
            ColorUtils.sendUsage(player, "/flyspeed <1-10> [gracz]");
            return true;
        }

        int speed;
        try {
            speed = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            player.sendMessage(ColorUtils.error("Prędkość musi być liczbą od 1 do 10!"));
            return true;
        }

        if (speed < 1 || speed > 10) {
            player.sendMessage(ColorUtils.error("Prędkość musi być w zakresie 1-10!"));
            return true;
        }

        Player target = player;
        if (args.length > 1) {
            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                player.sendMessage(ColorUtils.colorizeComponent(
                    plugin.getConfigManager().getMessagesConfig().getPlayerNotFound()
                ));
                return true;
            }
        }

        float flySpeed = speed / 10.0f;
        target.setFlySpeed(flySpeed);

        String message = plugin.getConfigManager().getMessagesConfig().getFlyspeedChanged()
            .replace("{speed}", String.valueOf(speed));
        target.sendMessage(ColorUtils.colorizeComponent(message));

        if (!target.equals(player)) {
            player.sendMessage(ColorUtils.success("Zmieniono prędkość latania gracza " + target.getName() + " na " + speed + "!"));
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String partial = args[0];
            for (int i = 1; i <= 10; i++) {
                String num = String.valueOf(i);
                if (num.startsWith(partial)) {
                    completions.add(num);
                }
            }
        } else if (args.length == 2) {
            String partial = args[1].toLowerCase();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(partial)) {
                    completions.add(player.getName());
                }
            }
        }
        return completions;
    }
}
