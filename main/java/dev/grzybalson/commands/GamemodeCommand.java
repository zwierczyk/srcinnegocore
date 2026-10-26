package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class GamemodeCommand implements CommandExecutor, TabCompleter {

    private final HollyCore plugin;

    public GamemodeCommand(HollyCore plugin) {
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

        if (args.length < 1) {
            ColorUtils.sendUsage(player, "/gm <0|1|2|3> [gracz]");
            return true;
        }

        GameMode gameMode = parseGameMode(args[0]);
        if (gameMode == null) {
            player.sendMessage(ColorUtils.error("Nieprawidłowy tryb gry! Użyj: 0, 1, 2 lub 3"));
            return true;
        }

        if (!hasModePermission(player, gameMode)) {
            player.sendMessage(ColorUtils.noPermission(label + " " + args[0], modePermission(gameMode)));
            return true;
        }

        Player target = player;
        if (args.length > 1) {
            if (!player.hasPermission("Holly.cmd.gm") && !player.hasPermission("Holly.cmd.gm.others")) {
                player.sendMessage(ColorUtils.noPermission(label + " " + args[0] + " " + args[1], "Holly.cmd.gm.others"));
                return true;
            }
            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                player.sendMessage(ColorUtils.colorizeComponent(
                    plugin.getConfigManager().getMessagesConfig().getPlayerNotFound()
                ));
                return true;
            }
        }

        target.setGameMode(gameMode);

        String modeName = getGameModeName(gameMode);
        String message = plugin.getConfigManager().getMessagesConfig().getGamemodeChanged()
            .replace("{mode}", modeName);

        target.sendMessage(ColorUtils.colorizeComponent(message));
        dev.grzybalson.utils.ColorUtils.sendTitle(target, 
            ColorUtils.colorize(plugin.getConfigManager().getMessagesConfig().getGamemodeTitle().replace("{mode}", modeName)),
            ColorUtils.colorize(plugin.getConfigManager().getMessagesConfig().getGamemodeSubtitle().replace("{mode}", modeName)),
            plugin.getConfigManager().getMessagesConfig().getGamemodeTitleFadeIn(),
            plugin.getConfigManager().getMessagesConfig().getGamemodeTitleStay(),
            plugin.getConfigManager().getMessagesConfig().getGamemodeTitleFadeOut()
        );
        plugin.getSoundManager().playGamemodeChange(target);

        if (!target.equals(player)) {
            player.sendMessage(ColorUtils.success("Zmieniono tryb gry gracza " + target.getName() + " na &2" + modeName + "&a!"));
        }

        plugin.getWebhookLogManager().logGamemodeChange(player, target, gameMode);
        return true;
    }

    private boolean hasModePermission(Player player, GameMode gameMode) {
        return player.hasPermission("Holly.cmd.gm") || player.hasPermission(modePermission(gameMode));
    }

    private String modePermission(GameMode gameMode) {
        return "Holly.cmd.gm." + gameModeToNumber(gameMode);
    }

    private int gameModeToNumber(GameMode gameMode) {
        return switch (gameMode) {
            case SURVIVAL -> 0;
            case CREATIVE -> 1;
            case ADVENTURE -> 2;
            case SPECTATOR -> 3;
        };
    }

    private GameMode parseGameMode(String input) {
        return switch (input.toLowerCase()) {
            case "0", "survival", "s" -> GameMode.SURVIVAL;
            case "1", "creative", "c" -> GameMode.CREATIVE;
            case "2", "adventure", "a" -> GameMode.ADVENTURE;
            case "3", "spectator", "sp" -> GameMode.SPECTATOR;
            default -> null;
        };
    }

    private String getGameModeName(GameMode gameMode) {
        return switch (gameMode) {
            case SURVIVAL -> "Survival";
            case CREATIVE -> "Creative";
            case ADVENTURE -> "Adventure";
            case SPECTATOR -> "Spectator";
        };
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            for (String mode : List.of("0", "1", "2", "3", "survival", "creative", "adventure", "spectator")) {
                if (mode.startsWith(partial)) {
                    completions.add(mode);
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
