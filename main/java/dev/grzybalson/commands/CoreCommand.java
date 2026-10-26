package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
import dev.grzybalson.core.CoreReloadService;
import dev.grzybalson.core.HardResetService;
import dev.grzybalson.gui.HardResetConfirmGUI;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Sound;
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

public class CoreCommand implements CommandExecutor, TabCompleter {

    private final HollyCore plugin;
    private final CoreReloadService reloadService;
    private final HardResetService hardResetService;

    public CoreCommand(HollyCore plugin) {
        this.plugin = plugin;
        this.reloadService = new CoreReloadService(plugin);
        this.hardResetService = new HardResetService(plugin);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ColorUtils.usage("/" + label + " <reload|hardreset> ..."));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("reload")) {
            return handleReload(sender, label, args);
        }
        if (sub.equals("hardreset")) {
            return handleHardReset(sender, label, args);
        }

        sender.sendMessage(ColorUtils.usage("/" + label + " <reload|hardreset> ..."));
        return true;
    }

    private boolean handleReload(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("Holly.cmd.core.reload")) {
            sender.sendMessage(ColorUtils.noPermission(label + " reload", "Holly.cmd.core.reload"));
            return true;
        }

        String module = args.length >= 2 ? args[1] : "all";
        long start = System.currentTimeMillis();

        try {
            reloadService.reload(module);
            long time = System.currentTimeMillis() - start;
            String target = module == null || module.isBlank() ? "all" : module.trim().toLowerCase(Locale.ROOT);

            if ("all".equalsIgnoreCase(target)) {
                sender.sendMessage(ColorUtils.success("Przeladowano HollyCore (&f" + time + "ms&a). Cięższe moduły idą na kolejnych tickach, żeby nie zamrozić serwera."));
            } else if ("plugin".equalsIgnoreCase(target) || "jar".equalsIgnoreCase(target) || "loadnew".equalsIgnoreCase(target)) {
                sender.sendMessage(ColorUtils.success("Loadnew HollyCore uruchomiony (&f" + time + "ms&a)."));
                sender.sendMessage(ColorUtils.colorizeComponent(
                    "&7Wgraj JAR do &fplugins/&7, opcjonalnie jako &f.jar.new&7, potem &f/admin loadnew &7lub &f/core reload loadnew&7."));
            } else {
                sender.sendMessage(ColorUtils.success("Przeladowano modul &f" + target + " &a(&f" + time + "ms&a)."));
            }

            if (sender instanceof Player player) {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7F, 1.6F);
            }
        } catch (IllegalArgumentException exception) {
            sender.sendMessage(ColorUtils.error(exception.getMessage()));
            sender.sendMessage(ColorUtils.info("Dostepne moduly: &f" + String.join(", ", reloadService.getAvailableModules())));
            playErrorSound(sender);
        } catch (Exception exception) {
            String error = rootMessage(exception);
            sender.sendMessage(ColorUtils.error("Reload nieudany."));
            sender.sendMessage(ColorUtils.info("Blad: &f" + error));
            sender.sendMessage(ColorUtils.info("Co zrobic: &f" + fixHint(error)));
            playErrorSound(sender);
            plugin.getLogger().severe("[Core] Reload failed: " + error);
            exception.printStackTrace();
        }

        return true;
    }

    private boolean handleHardReset(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("Holly.cmd.core.hardreset")) {
            sender.sendMessage(ColorUtils.noPermission(label + " hardreset", "Holly.cmd.core.hardreset"));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.error("Hardreset wymaga GUI — uzyj z gry."));
            return true;
        }
        if (args.length < 2) {
            ColorUtils.sendUsage(sender, "/" + label + " hardreset <db-prefix>");
            String current = plugin.getConfigManager().getDatabaseConfig().getDbPrefix();
            sender.sendMessage(ColorUtils.info("Aktualny prefix tego serwera: &f" + current));
            return true;
        }

        String prefix = hardResetService.normalizePrefix(args[1]);
        if (prefix.isBlank() || !prefix.matches("[A-Za-z0-9_-]{1,64}")) {
            sender.sendMessage(ColorUtils.error("Nieprawidlowy db-prefix. Dozwolone: A-Z a-z 0-9 _ -"));
            return true;
        }
        if (hardResetService.isRunning()) {
            sender.sendMessage(ColorUtils.error("Hardreset juz trwa."));
            return true;
        }

        new HardResetConfirmGUI(plugin, hardResetService, player, prefix, 1).open();
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String typed = args[0].toLowerCase(Locale.ROOT);
            if (sender.hasPermission("Holly.cmd.core.reload") && "reload".startsWith(typed)) {
                completions.add("reload");
            }
            if (sender.hasPermission("Holly.cmd.core.hardreset") && "hardreset".startsWith(typed)) {
                completions.add("hardreset");
            }
            return completions;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("reload") && sender.hasPermission("Holly.cmd.core.reload")) {
            String typed = args[1].toLowerCase(Locale.ROOT);
            completions.add("all");
            for (String module : reloadService.getAvailableModules()) {
                if (module.startsWith(typed)) {
                    completions.add(module);
                }
            }
            return completions;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("hardreset") && sender.hasPermission("Holly.cmd.core.hardreset")) {
            String current = plugin.getConfigManager().getDatabaseConfig().getDbPrefix();
            if (current != null && current.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) {
                completions.add(current);
            }
        }

        return completions;
    }

    private static void playErrorSound(CommandSender sender) {
        if (sender instanceof Player player) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6F, 0.8F);
        }
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        if (message == null || message.isBlank()) {
            return current.getClass().getSimpleName();
        }
        return message;
    }

    private static String fixHint(String error) {
        String lower = error.toLowerCase(Locale.ROOT);
        if (lower.contains(".yml")) {
            return "Popraw wskazany plik YAML i wykonaj /core reload ponownie.";
        }
        if (lower.contains("permission") || lower.contains("upraw")) {
            return "Sprawdz permisje lub plugin zalezny i sprobuj ponownie.";
        }
        return "Sprawdz logi w konsoli, popraw konfiguracje i sprobuj ponownie.";
    }
}
