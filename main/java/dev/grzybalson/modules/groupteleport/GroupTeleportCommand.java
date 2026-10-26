package dev.grzybalson.modules.groupteleport;

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
import java.util.Locale;

public final class GroupTeleportCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of("addlocation", "reload", "list", "help");

    private final GroupTeleportManager manager;

    public GroupTeleportCommand(GroupTeleportManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("Holly.cmd.gtp")) {
            sender.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.gtp"));
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sender.sendMessage(ColorUtils.colorizeComponent("&#89E2FF/gtp addlocation &8— &7dodaj lokalizacje teparki"));
            sender.sendMessage(ColorUtils.colorizeComponent("&#89E2FF/gtp list &8— &7lista lokalizacji"));
            sender.sendMessage(ColorUtils.colorizeComponent("&#89E2FF/gtp reload &8— &7przeladuj config"));
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "addlocation", "add" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ColorUtils.colorizeComponent("&#FF5C5C✖ Tylko dla graczy."));
                    return true;
                }
                manager.getConfig().addLocation(player.getLocation());
                sender.sendMessage(ColorUtils.colorizeComponent(
                    "&#60FF8B✔ Dodano lokalizacje teparki (#" + manager.getConfig().locationCount() + ")."));
            }
            case "list" -> sender.sendMessage(ColorUtils.colorizeComponent(
                "&#89E2FFLokalizacje tepark: &#FFFFFF" + manager.getConfig().locationCount()));
            case "reload" -> {
                manager.reload();
                sender.sendMessage(ColorUtils.colorizeComponent("&#60FF8B✔ Przeladowano groupteleport.yml"));
            }
            default -> sender.sendMessage(ColorUtils.colorizeComponent("&#FF5C5C✖ Uzyj /gtp help"));
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("Holly.cmd.gtp") || args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String sub : SUBS) {
            if (sub.startsWith(prefix)) {
                out.add(sub);
            }
        }
        return out;
    }
}
