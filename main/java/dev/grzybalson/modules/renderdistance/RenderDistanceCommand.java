package dev.grzybalson.modules.renderdistance;

import dev.grzybalson.config.modules.renderdistance.RenderDistanceMessages;
import dev.grzybalson.modules.renderdistance.model.RenderDistanceStatus;
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
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class RenderDistanceCommand implements CommandExecutor, TabCompleter {
    private static final String RELOAD_PERMISSION = "Holly.cmd.renderdistance.reload";
    private static final String STATUS_PERMISSION = "Holly.cmd.renderdistance.status";
    private static final String STATUS_OTHERS_PERMISSION = "Holly.cmd.renderdistance.status.others";

    private final RenderDistanceManager manager;

    public RenderDistanceCommand(RenderDistanceManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);
        switch (subCommand) {
            case "reload" -> reload(sender, label);
            case "status" -> status(sender, label, args);
            default -> sendUsage(sender);
        }
        return true;
    }

    private void reload(CommandSender sender, String label) {
        RenderDistanceMessages messages = manager.getMessages();
        if (!sender.hasPermission(RELOAD_PERMISSION)) {
            sender.sendMessage(ColorUtils.noPermission(label + " reload", RELOAD_PERMISSION));
            return;
        }

        manager.reload();
        messages = manager.getMessages();
        if (!manager.isConfigEnabled()) {
            send(sender, messages.getDisabled());
            return;
        }
        if (!manager.isWorldGuardAvailable()) {
            send(sender, messages.getUnavailable());
            return;
        }
        send(sender, messages.getReload());
    }

    private void status(CommandSender sender, String label, String[] args) {
        RenderDistanceMessages messages = manager.getMessages();
        if (args.length == 1) {
            if (!sender.hasPermission(STATUS_PERMISSION)) {
                sender.sendMessage(ColorUtils.noPermission(label + " status", STATUS_PERMISSION));
                return;
            }
            if (!(sender instanceof Player player)) {
                send(sender, messages.getPlayerOnly());
                return;
            }
            sendStatus(sender, player);
            return;
        }

        if (args.length == 2) {
            if (!sender.hasPermission(STATUS_OTHERS_PERMISSION)) {
                sender.sendMessage(ColorUtils.noPermission(label + " status " + args[1], STATUS_OTHERS_PERMISSION));
                return;
            }
            Player target = findPlayer(args[1]);
            if (target == null) {
                send(sender, messages.getInvalidPlayer());
                return;
            }
            sendStatus(sender, target);
            return;
        }

        sendUsage(sender);
    }

    private void sendStatus(CommandSender sender, Player target) {
        RenderDistanceMessages messages = manager.getMessages();
        if (!manager.isConfigEnabled()) {
            send(sender, messages.getDisabled());
            return;
        }
        if (!manager.isWorldGuardAvailable()) {
            send(sender, messages.getUnavailable());
            return;
        }

        RenderDistanceStatus status = manager.getStatus(target);
        if (status == null) {
            send(sender, messages.getInvalidPlayer());
            return;
        }

        send(sender, "&#00E0FFℹ &8Ξ &fWidoczność graczy: &f" + status.playerName());
        send(sender, "&8- &7Świat: &f" + status.worldName());
        send(sender, "&8- &7Aktywny wpis: &f" + status.activeEntryName());
        send(sender, "&8- &7Region WorldGuarda: &f" + status.worldGuardRegionName());
        send(sender, "&8- &7Zasięg widoczności: &f" + formatDistance(status.renderDistance()));
        send(sender, "&8- &7Ukryci gracze: &f" + status.hiddenPlayers());
        send(sender, "&8- &7Wartości domyślne: " + (status.defaults() ? "&aTAK" : "&cNIE"));
    }

    private String formatDistance(int renderDistance) {
        if (renderDistance == -1) {
            return "bez limitu";
        }
        if (renderDistance == 0) {
            return "ukrywa wszystkich graczy";
        }
        return renderDistance + " chunków";
    }

    private Player findPlayer(String name) {
        Player exactPlayer = Bukkit.getPlayerExact(name);
        if (exactPlayer != null) {
            return exactPlayer;
        }
        return Bukkit.getPlayer(name);
    }

    private void sendUsage(CommandSender sender) {
        ColorUtils.sendUsage(sender,
            "/renderdistance reload",
            "/renderdistance status [gracz]"
        );
    }

    private void send(CommandSender sender, String message) {
        sender.sendMessage(ColorUtils.colorizeComponent(message));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> values = new ArrayList<>();
            if (sender.hasPermission(RELOAD_PERMISSION)) {
                values.add("reload");
            }
            if (sender.hasPermission(STATUS_PERMISSION) || sender.hasPermission(STATUS_OTHERS_PERMISSION)) {
                values.add("status");
            }
            return filter(values, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("status") && sender.hasPermission(STATUS_OTHERS_PERMISSION)) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }
        return Collections.emptyList();
    }

    private List<String> filter(List<String> input, String prefix) {
        String normalizedPrefix = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String value : input) {
            if (value.toLowerCase(Locale.ROOT).startsWith(normalizedPrefix)) {
                result.add(value);
            }
        }
        return result;
    }
}
