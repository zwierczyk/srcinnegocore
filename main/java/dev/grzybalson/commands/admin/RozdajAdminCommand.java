package dev.grzybalson.commands.admin;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.configs.AdminConfig;
import dev.grzybalson.utils.ColorUtils;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RozdajAdminCommand implements CommandExecutor, TabCompleter {

    private final HollyCore plugin;

    public RozdajAdminCommand(HollyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        AdminConfig config = plugin.getConfigManager().getAdminConfig();
        String requiredPermission = (config != null && config.getPermission() != null && !config.getPermission().isEmpty())
                ? config.getPermission()
                : "Holly.cmd.rozdaj";

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            String reloadPerm = requiredPermission + ".reload";
            if (!(sender instanceof ConsoleCommandSender) && !sender.hasPermission(reloadPerm) && !sender.hasPermission("Holly.cmd.rozdajadmin.reload")) {
                sender.sendMessage(ColorUtils.noPermission(label + " reload", reloadPerm));
                return true;
            }
            plugin.getConfigManager().reloadAdminConfig();
            sender.sendMessage(ColorUtils.colorizeComponent(ColorUtils.SUCCESS_PREFIX + "Przeładowano distributeadmin.yml."));
            return true;
        }

        if (!(sender instanceof ConsoleCommandSender) && !sender.hasPermission(requiredPermission) && !sender.hasPermission("Holly.cmd.rozdajadmin")) {
            sender.sendMessage(ColorUtils.noPermission(label, requiredPermission));
            return true;
        }

        String packageName = null;
        int amount = 0;
        int seconds = (config != null && config.getCountdownSeconds() > 0) ? config.getCountdownSeconds() : 5;

        if (args.length >= 3) {
            packageName = args[0];
            boolean isPickupEnabled = plugin.isAddonEnabled("odbierz") && plugin.getPickupManager() != null;
            boolean existsInPickup = isPickupEnabled && plugin.getPickupManager().packageExists(packageName);
            boolean isCaseEnabled = plugin.isAddonEnabled("cases") && plugin.getCaseManager() != null;
            boolean existsInCases = isCaseEnabled && plugin.getCaseManager().getCase(packageName) != null;

            if (!existsInPickup && !existsInCases) {
                sender.sendMessage(ColorUtils.error("Przedmiot/skrzynka o nazwie '" + packageName + "' nie istnieje w /odbierz ani w /case!"));
                sender.sendMessage(ColorUtils.info("Stwórz przedmiot w /odbierz lub skrzynkę w /case!"));
                return true;
            }

            try {
                amount = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                sender.sendMessage(ColorUtils.error("Ilość musi być liczbą!"));
                return true;
            }

            try {
                seconds = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(ColorUtils.error("Czas musi być liczbą!"));
                return true;
            }

            if (amount <= 0 || seconds <= 0) {
                sender.sendMessage(ColorUtils.error("Ilość i czas muszą być większe od 0!"));
                return true;
            }
        } else if (args.length > 0 && args.length < 3) {
            ColorUtils.sendUsage(sender, "/" + label + " [nazwa_przedmiotu] [ilość] [czas_sekund]");
            return true;
        }

        String senderName = (sender instanceof Player player) ? player.getName() : "Konsola";
        final String finalPackageName = packageName;
        final int finalAmount = amount;
        final int totalSeconds = seconds;

        sender.sendMessage(ColorUtils.success("Rozpoczęto odliczanie rozdawania za " + totalSeconds + " sekund!"));

        new BukkitRunnable() {
            int remaining = totalSeconds;

            @Override
            public void run() {
                if (remaining <= 0) {
                    if (finalPackageName != null) {
                        boolean isPickupEnabled = plugin.isAddonEnabled("odbierz") && plugin.getPickupManager() != null;
                        boolean existsInPickup = isPickupEnabled && plugin.getPickupManager().packageExists(finalPackageName);

                        if (existsInPickup) {
                            plugin.getPickupManager().givePackageToAll(finalPackageName, finalAmount);
                        } else if (plugin.getCaseManager() != null) {
                            dev.grzybalson.modules.cases.CaseDefinition caseDef = plugin.getCaseManager().getCase(finalPackageName);
                            if (caseDef != null) {
                                for (Player p : Bukkit.getOnlinePlayers()) {
                                    plugin.getCaseManager().giveKey(p, caseDef, finalAmount);
                                }
                            }
                        }
                    }

                    if (config != null && config.getCommands() != null) {
                        for (String cmd : config.getCommands()) {
                            if (cmd == null || cmd.trim().isEmpty()) continue;
                            if (cmd.contains("{target}")) {
                                for (Player player : Bukkit.getOnlinePlayers()) {
                                    String formattedCmd = cmd.replace("{target}", player.getName())
                                            .replace("{player}", senderName)
                                            .replace("{nick}", senderName);
                                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), formattedCmd);
                                }
                            } else {
                                String formattedCmd = cmd.replace("{player}", senderName)
                                        .replace("{nick}", senderName);
                                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), formattedCmd);
                            }
                        }
                    }

                    String finishedTitleStr = (config != null ? config.getRozdajFinishedTitle() : "&a&lodbierz")
                            .replace("{player}", senderName)
                            .replace("{nick}", senderName)
                            .replace("{name}", finalPackageName != null ? finalPackageName : "")
                            .replace("{amount}", String.valueOf(finalAmount));

                    String finishedSubtitleStr = (config != null ? config.getRozdajFinishedSubtitle() : "&fdostałeś eventowy kluczyk do &a/odbierz")
                            .replace("{player}", senderName)
                            .replace("{nick}", senderName)
                            .replace("{name}", finalPackageName != null ? finalPackageName : "")
                            .replace("{amount}", String.valueOf(finalAmount));

                    Title.Times finishedTimes = Title.Times.times(
                            Duration.ofMillis(300),
                            Duration.ofMillis(3500),
                            Duration.ofMillis(800)
                    );
                    Title finishedTitle = Title.title(
                            ColorUtils.colorizeComponent(finishedTitleStr),
                            ColorUtils.colorizeComponent(finishedSubtitleStr),
                            finishedTimes
                    );

                    String soundName = config != null ? config.getRozdajFinishedSound() : "UI_TOAST_CHALLENGE_COMPLETE";
                    Sound finishSound = parseSound(soundName, Sound.UI_TOAST_CHALLENGE_COMPLETE);

                    String finalMsg = config != null ? config.getRozdajFinalMessage() : null;
                    if (finalMsg != null) {
                        finalMsg = finalMsg.replace("{player}", senderName)
                                .replace("{nick}", senderName)
                                .replace("{name}", finalPackageName != null ? finalPackageName : "")
                                .replace("{amount}", String.valueOf(finalAmount));
                    }

                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (finalMsg != null && !finalMsg.isEmpty()) {
                            player.sendMessage(ColorUtils.colorizeComponent(finalMsg));
                        }
                        player.showTitle(finishedTitle);
                        if (finishSound != null) {
                            player.playSound(player.getLocation(), finishSound, 1.0f, 1.0f);
                        }
                    }

                    cancel();
                    return;
                }

                Title.Times times = Title.Times.times(
                        Duration.ZERO,
                        Duration.ofMillis(1500),
                        Duration.ofMillis(200)
                );

                String countdownTitleStr = (config != null ? config.getRozdajCountdownTitle() : "&aGracz &e{player}")
                        .replace("{player}", senderName)
                        .replace("{nick}", senderName)
                        .replace("{seconds}", String.valueOf(remaining));

                String countdownSubtitleStr = (config != null ? config.getRozdajCountdownSubtitle() : "&frozdaje eventowy klucz dla kazdego")
                        .replace("{player}", senderName)
                        .replace("{nick}", senderName)
                        .replace("{seconds}", String.valueOf(remaining));

                Title title = Title.title(
                        ColorUtils.colorizeComponent(countdownTitleStr),
                        ColorUtils.colorizeComponent(countdownSubtitleStr),
                        times
                );

                String soundName = config != null ? config.getRozdajCountdownSound() : "BLOCK_NOTE_BLOCK_PLING";
                Sound tickSound = parseSound(soundName, Sound.BLOCK_NOTE_BLOCK_PLING);
                float pitch = 1.0f + (totalSeconds - remaining) * 0.15f;

                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.showTitle(title);
                    if (tickSound != null) {
                        player.playSound(player.getLocation(), tickSound, 0.8f, pitch);
                    }
                }

                remaining--;
            }
        }.runTaskTimer(plugin, 0L, 20L);

        return true;
    }

    private Sound parseSound(String soundName, Sound fallback) {
        if (soundName == null || soundName.isEmpty()) return fallback;
        try {
            return Sound.valueOf(soundName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        AdminConfig config = plugin.getConfigManager().getAdminConfig();
        String requiredPermission = (config != null && config.getPermission() != null && !config.getPermission().isEmpty())
                ? config.getPermission()
                : "Holly.cmd.rozdaj";

        if (args.length == 1) {
            String input = args[0].toLowerCase();
            if (sender.hasPermission(requiredPermission + ".reload") || sender.hasPermission("Holly.cmd.rozdajadmin.reload") || sender instanceof ConsoleCommandSender) {
                if ("reload".startsWith(input)) {
                    completions.add("reload");
                }
            }
            if (plugin.isAddonEnabled("odbierz") && plugin.getPickupManager() != null && plugin.getPickupManager().getPickupPackages() != null) {
                for (String key : plugin.getPickupManager().getPickupPackages().keySet()) {
                    if (key.toLowerCase().startsWith(input)) {
                        completions.add(key);
                    }
                }
            }
            if (plugin.isAddonEnabled("cases") && plugin.getCaseManager() != null && plugin.getCaseManager().getCaseNames() != null) {
                for (String key : plugin.getCaseManager().getCaseNames()) {
                    if (key.toLowerCase().startsWith(input)) {
                        completions.add(key);
                    }
                }
            }
        } else if (args.length == 2) {
            completions.addAll(List.of("1", "5", "10"));
        } else if (args.length == 3) {
            completions.addAll(List.of("5", "10", "15", "30", "60"));
        }
        return completions;
    }
}

