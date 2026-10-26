package dev.grzybalson.commands;

import dev.grzybalson.listeners.EntityLimitListener;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class EntityLimitCommand implements CommandExecutor, TabCompleter {

    private final EntityLimitListener listener;

    public EntityLimitCommand(EntityLimitListener listener) {
        this.listener = listener;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("Holly.cmd.entitylimit")) {
            sender.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.entitylimit"));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "start" -> {
                if (listener.isActive()) {
                    sender.sendMessage(ColorUtils.colorize("&c[EntityLimit] Automatyczne czyszczenie jest już włączone."));
                    return true;
                }
                listener.setActive(true);
                sender.sendMessage(ColorUtils.colorize("&a[EntityLimit] Uruchomiono skanowanie i czyszczenie nadmiarowych entity."));
            }
            case "stop" -> {
                if (!listener.isActive()) {
                    sender.sendMessage(ColorUtils.colorize("&c[EntityLimit] Automatyczne czyszczenie jest już wyłączone."));
                    return true;
                }
                listener.setActive(false);
                sender.sendMessage(ColorUtils.colorize("&c[EntityLimit] Zatrzymano automatyczne czyszczenie nadmiarowych entity."));
            }
            case "run", "scan" -> {
                sender.sendMessage(ColorUtils.colorize("&e[EntityLimit] Rozpoczynanie jednorazowego czyszczenia entity..."));
                EntityLimitListener.ManualCleanupResult result = listener.runManualCleanup();
                sender.sendMessage(ColorUtils.colorize("&a[EntityLimit] Wykonano czyszczenie! Przeszukano &e" 
                    + result.chunksProcessed() + " &achunków, usunięto &e" + result.entitiesRemoved() + " &anadmiarowych obiektów."));
            }
            case "status" -> sendStatus(sender);
            default -> sendHelp(sender, label);
        }

        return true;
    }

    private void sendStatus(CommandSender sender) {
        boolean active = listener.isActive();
        String state = active ? "&aWŁĄCZONE" : "&cWYŁĄCZONE";
        sender.sendMessage(ColorUtils.colorize("&8&m----------------------------------------"));
        sender.sendMessage(ColorUtils.colorize("&6&lStatus EntityLimit:"));
        sender.sendMessage(ColorUtils.colorize("&7Stan skanowania w tle: " + state));
        sender.sendMessage(ColorUtils.colorize("&7Użyj &e/entitylimit start &7lub &e/entitylimit stop"));
        sender.sendMessage(ColorUtils.colorize("&7Użyj &e/entitylimit run &7dla jednorazowego czyszczenia"));
        sender.sendMessage(ColorUtils.colorize("&8&m----------------------------------------"));
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(ColorUtils.colorize("&8&m----------------------------------------"));
        sender.sendMessage(ColorUtils.colorize("&6&lKomendy /" + label + ":"));
        sender.sendMessage(ColorUtils.colorize("&e/" + label + " start &7- Uruchamia automatyczne czyszczenie w tle"));
        sender.sendMessage(ColorUtils.colorize("&e/" + label + " stop &7- Zatrzymuje automatyczne czyszczenie"));
        sender.sendMessage(ColorUtils.colorize("&e/" + label + " run &7- Wykonuje natychmiastowe jednorazowe czyszczenie"));
        sender.sendMessage(ColorUtils.colorize("&e/" + label + " status &7- Wyświetla aktualny status"));
        sender.sendMessage(ColorUtils.colorize("&8&m----------------------------------------"));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("Holly.cmd.entitylimit")) {
            return List.of();
        }
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            String current = args[0].toLowerCase(Locale.ROOT);
            for (String sub : List.of("start", "stop", "run", "status")) {
                if (sub.startsWith(current)) {
                    completions.add(sub);
                }
            }
            return completions;
        }
        return List.of();
    }
}
