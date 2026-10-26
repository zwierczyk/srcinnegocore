package dev.grzybalson.modules.boss;

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

public final class BossCommand implements CommandExecutor, TabCompleter {

    private final BossManager bossManager;

    public BossCommand(BossManager bossManager) {
        this.bossManager = bossManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("Holly.cmd.boss")) {
            sender.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.boss"));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                bossManager.reload();
                sender.sendMessage(ColorUtils.success("Przeładowano konfigurację bossów!"));
            }
            case "spawn" -> {
                if (args.length < 2) {
                    ColorUtils.sendUsage(sender, "/boss spawn <hoglin|typ>");
                    return true;
                }
                if (!bossManager.spawnBoss(args[1])) {
                    sender.sendMessage(ColorUtils.error("Nie udało się zespawnować bossa (sprawdź typ/lokalizację)."));
                    return true;
                }
                sender.sendMessage(ColorUtils.success("Zespawnowano bossa &e" + args[1] + "&a!"));
            }
            case "setlocation" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ColorUtils.error("Tylko gracz może ustawić lokalizację."));
                    return true;
                }
                if (args.length < 2) {
                    ColorUtils.sendUsage(sender, "/boss setlocation <hoglin|typ>");
                    return true;
                }
                String type = args[1];
                if (type.equalsIgnoreCase("hoglin") || type.equalsIgnoreCase("warthog")) {
                    bossManager.getConfig().setHoglinLocation(player.getLocation());
                    bossManager.getConfig().save();
                    bossManager.refreshNextSpawnCache();
                    sender.sendMessage(ColorUtils.success("Ustawiono lokalizację Hoglin!"));
                    return true;
                }
                if (bossManager.getConfig().getBoss(type) == null) {
                    sender.sendMessage(ColorUtils.error("Nieznany typ bossa!"));
                    return true;
                }
                bossManager.getConfig().setBossLocation(type, player.getLocation());
                bossManager.getConfig().save();
                bossManager.refreshNextSpawnCache();
                sender.sendMessage(ColorUtils.success("Ustawiono lokalizację &e" + type.toUpperCase(Locale.ROOT) + "&a!"));
            }
            default -> sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        ColorUtils.sendUsage(sender,
            "/boss reload",
            "/boss spawn <hoglin|typ>",
            "/boss setlocation <hoglin|typ>"
        );
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String partial = args[0].toLowerCase(Locale.ROOT);
            for (String sub : List.of("reload", "spawn", "setlocation")) {
                if (sub.startsWith(partial)) {
                    completions.add(sub);
                }
            }
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("spawn") || args[0].equalsIgnoreCase("setlocation"))) {
            String partial = args[1].toLowerCase(Locale.ROOT);
            if ("hoglin".startsWith(partial)) {
                completions.add("hoglin");
            }
            for (String id : bossManager.getConfig().getBosses().keySet()) {
                if (id.toLowerCase(Locale.ROOT).startsWith(partial)) {
                    completions.add(id.toLowerCase(Locale.ROOT));
                }
            }
        }
        return completions;
    }
}
