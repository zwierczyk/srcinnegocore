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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FlyCommand implements CommandExecutor, TabCompleter {

    private final HollyCore plugin;
    private static final Map<UUID, Boolean> flyStates = new ConcurrentHashMap<>();

    public FlyCommand(HollyCore plugin) {
        this.plugin = plugin;
    }

    public static boolean getFlyState(UUID uuid) {
        return flyStates.getOrDefault(uuid, false);
    }

    public static void setFlyState(UUID uuid, boolean state) {
        if (state) {
            flyStates.put(uuid, true);
        } else {
            flyStates.remove(uuid);
        }
    }

    public static void clearFlyState(UUID uuid) {
        flyStates.remove(uuid);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.colorizeComponent(
                plugin.getConfigManager().getMessagesConfig().getPlayerOnly()
            ));
            return true;
        }

        if (!player.hasPermission("Holly.cmd.fly")) {
            player.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.fly"));
            return true;
        }

        Player target = player;
        if (args.length > 0) {
            target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                player.sendMessage(ColorUtils.colorizeComponent(
                    plugin.getConfigManager().getMessagesConfig().getPlayerNotFound()
                ));
                return true;
            }
        }

        boolean newFlight = !target.getAllowFlight();
        target.setAllowFlight(newFlight);

        // Zapisz stan fly
        setFlyState(target.getUniqueId(), newFlight);

        if (newFlight) {
            // Dungeon/event freeze ustawia flySpeed=0 — wtedy WASD nie dziala, tylko gora/dol.
            if (target.getFlySpeed() <= 0.001F) {
                target.setFlySpeed(0.1F);
            }
            target.sendMessage(ColorUtils.colorizeComponent(
                plugin.getConfigManager().getMessagesConfig().getFlyEnabled()
            ));
            if (!target.equals(player)) {
                player.sendMessage(ColorUtils.colorizeComponent(
                    plugin.getConfigManager().getMessagesConfig().getFlyEnabledOther().replace("{player}", target.getName())
                ));
            }
        } else {
            target.setFlying(false);
            target.sendMessage(ColorUtils.colorizeComponent(
                plugin.getConfigManager().getMessagesConfig().getFlyDisabled()
            ));
            if (!target.equals(player)) {
                player.sendMessage(ColorUtils.colorizeComponent(
                    plugin.getConfigManager().getMessagesConfig().getFlyDisabledOther().replace("{player}", target.getName())
                ));
            }
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(partial)) {
                    completions.add(player.getName());
                }
            }
        }
        return completions;
    }
}
