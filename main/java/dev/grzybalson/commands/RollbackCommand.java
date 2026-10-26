package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
import dev.grzybalson.gui.PlayerRollbackSelectGUI;
import dev.grzybalson.gui.ServerRollbackSelectGUI;
import dev.grzybalson.modules.backup.BackupManager;
import dev.grzybalson.modules.backup.RollbackManager;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class RollbackCommand implements CommandExecutor, TabCompleter {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final HollyCore plugin;
    private final Map<String, PendingRollback> pendingConfirmations = new ConcurrentHashMap<>();

    public RollbackCommand(HollyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {
        if (!sender.hasPermission("holly.cmd.rollback")) {
            sender.sendMessage(ColorUtils
                    .colorizeComponent("&c✕ &8Ξ &cNie posiadasz uprawnień do komendy cofki (&4holly.cmd.rollback&c)."));
            return true;
        }

        if (args.length == 0) {
            if (sender instanceof Player player) {
                openServerRollbackGui(player);
            } else {
                sendHelp(sender, label);
            }
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        BackupManager backupManager = requireBackupManager(sender);
        if (backupManager == null) {
            return true;
        }

        if (sub.equals("snapshot") || sub.equals("save")) {
            backupManager.createGlobalSnapshot();
            sender.sendMessage(ColorUtils.colorizeComponent(
                    "&#00FF3B✔ &8Ξ &#4EFA76Wymuszono snapshot: EQ+EC wszystkich (ItemSync+MSLF), kostiumy/pety, torby, rynek, salda."));
            return true;
        }

        if (sub.equals("list") || sub.equals("historia")) {
            sender.sendMessage(ColorUtils.colorizeComponent("&#00E0FFℹ &8Ξ &7Ładowanie listy snapshotów..."));
            backupManager.getDataManager().getAvailableSnapshotDates(10).thenAccept(dates ->
                runSync(() -> {
                    if (dates.isEmpty()) {
                        sender.sendMessage(
                            ColorUtils.colorizeComponent("&c✕ &8Ξ &cBrak zarejestrowanych snapshotów w bazie."));
                        return;
                    }
                    sender.sendMessage(
                        ColorUtils.colorizeComponent("&8&m--------------------------------------------------"));
                    sender.sendMessage(ColorUtils
                        .colorizeComponent("&#00E0FFℹ &8Ξ &#00E0FF&lDOSTĘPNE PUNKTY PRZYWRACANIA (SNAPSHOTY):"));
                    for (Date date : dates) {
                        sender.sendMessage(ColorUtils
                            .colorizeComponent("&8▪ &f" + RollbackManager.DATE_FORMATTER.format(date.toInstant())));
                    }
                    sender.sendMessage(ColorUtils.colorizeComponent(
                        "&7Użyj: &f/" + label + " <HH:mm> &7lub &f/" + label + " <YYYY-MM-DD> <HH:mm>"));
                    sender.sendMessage(
                        ColorUtils.colorizeComponent("&8&m--------------------------------------------------"));
                })
            ).exceptionally(ex -> {
                runSync(() -> sender.sendMessage(ColorUtils.colorizeComponent(
                    "&c✕ &8Ξ &cBłąd ładowania snapshotów: " + ex.getMessage())));
                return null;
            });
            return true;
        }

        if (sub.equals("confirm") || sub.equals("potwierdz")) {
            if (args.length < 2) {
                sender.sendMessage(ColorUtils
                        .colorizeComponent("&c✕ &8Ξ &cPodaj kod potwierdzenia: &f/" + label + " confirm <token>"));
                return true;
            }
            String token = args[1].trim().toUpperCase(Locale.ROOT);
            PendingRollback pending = pendingConfirmations.get(token);

            if (pending == null || pending.isExpired()) {
                pendingConfirmations.remove(token);
                sender.sendMessage(
                        ColorUtils.colorizeComponent("&c✕ &8Ξ &cKod potwierdzenia wygasł lub jest nieprawidłowy."));
                return true;
            }

            pendingConfirmations.remove(token);
            sender.sendMessage(ColorUtils.colorizeComponent("&#00FF3B✔ &8Ξ &#4EFA76Rozpoczynanie procedury cofki do &f"
                    + RollbackManager.DATE_FORMATTER.format(pending.targetDate().toInstant()) + "&#4EFA76..."));

            RollbackManager rollbackManager = plugin.getRollbackManager();
            if (rollbackManager == null) {
                sender.sendMessage(ColorUtils.error("Modul cofki nie jest aktywny."));
                return true;
            }

            rollbackManager.executeRollback(pending.targetDate()).thenAccept(result ->
                runSync(() -> {
                    if (result.success()) {
                        sender.sendMessage(
                            ColorUtils.colorizeComponent("&8&m--------------------------------------------------"));
                        sender.sendMessage(
                            ColorUtils.colorizeComponent("&#00FF3B✔ &8Ξ &#4EFA76&lCOFKA SERWERA ZAKOŃCZONA SUKCESEM!"));
                        sender.sendMessage(ColorUtils.colorizeComponent("&7Szczegóły: &f" + result.message()));
                        sender.sendMessage(ColorUtils
                            .colorizeComponent("&7Przywróceni gracze online: &a" + result.onlinePlayersRestored()));
                        sender.sendMessage(ColorUtils
                            .colorizeComponent("&7Zaplanowani gracze offline: &e" + result.offlinePlayersQueued()));
                        sender.sendMessage(ColorUtils
                            .colorizeComponent("&7Przywróconych ofert rynku: &f" + result.marketItemsRestored()));
                        sender.sendMessage(
                            ColorUtils.colorizeComponent("&8&m--------------------------------------------------"));

                        if (plugin.getRestartManager() != null) {
                            plugin.getRestartManager().startRollbackRestart(5, pending.targetDate());
                        }
                    } else {
                        sender.sendMessage(ColorUtils.colorizeComponent("&c✕ &8Ξ &cBłąd cofki: " + result.message()));
                    }
                })
            ).exceptionally(ex -> {
                runSync(() -> sender.sendMessage(ColorUtils
                    .colorizeComponent("&c✕ &8Ξ &cWystąpił błąd podczas wykonywania cofki: " + ex.getMessage())));
                return null;
            });
            return true;
        }

        if (args.length == 1 && !isReservedSubcommand(sub) && !isTimeArg(args[0])) {
            if (!(sender instanceof Player admin)) {
                sender.sendMessage(ColorUtils.colorizeComponent("&c✕ &8Ξ &cGUI cofki gracza wymaga gracza (nie konsoli)."));
                return true;
            }
            openPlayerRollbackGui(admin, args[0]);
            return true;
        }

        // Parse Target Date & Time
        Date targetDate = parseTargetDate(args);
        if (targetDate == null) {
            sender.sendMessage(ColorUtils.colorizeComponent(
                    "&c✕ &8Ξ &cNieprawidłowy format daty/godziny. Poprawny format: &fHH:mm &club &fYYYY-MM-DD HH:mm"));
            return true;
        }

        sender.sendMessage(ColorUtils.colorizeComponent("&#00E0FFℹ &8Ξ &7Wyszukiwanie punktów przywracania sprzed &f"
                + RollbackManager.DATE_FORMATTER.format(targetDate.toInstant()) + "&7..."));

        RollbackManager rollbackManager = plugin.getRollbackManager();
        if (rollbackManager == null) {
            sender.sendMessage(ColorUtils.error("Modul cofki nie jest aktywny."));
            return true;
        }

        rollbackManager.previewRollback(targetDate).thenAccept(preview ->
            runSync(() -> {
                if (preview.playerUuids().isEmpty() && preview.marketSnapshot() == null) {
                    sender.sendMessage(ColorUtils.colorizeComponent("&c✕ &8Ξ &cNie znaleziono żadnych snapshotów sprzed "
                        + RollbackManager.DATE_FORMATTER.format(targetDate.toInstant())));
                    return;
                }

                int onlineCount = 0;
                int offlineCount = 0;
                for (UUID uuid : preview.playerUuids()) {
                    if (org.bukkit.Bukkit.getPlayer(uuid) != null && org.bukkit.Bukkit.getPlayer(uuid).isOnline()) {
                        onlineCount++;
                    } else {
                        offlineCount++;
                    }
                }

                String token = UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
                pendingConfirmations.put(token,
                    new PendingRollback(targetDate, System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(60)));

                sender.sendMessage(ColorUtils.colorizeComponent("&8&m--------------------------------------------------"));
                sender.sendMessage(
                    ColorUtils.colorizeComponent("&#FFB800⚠ &8Ξ &#FFD700&lPODSUMOWANIE COFKI SERWERA (ROLLBACK)"));
                sender.sendMessage(ColorUtils.colorizeComponent(
                    "&7Docelowy punkt czasu: &f" + RollbackManager.DATE_FORMATTER.format(targetDate.toInstant())));
                sender.sendMessage(
                    ColorUtils.colorizeComponent("&7Znaleziono snapshotów graczy: &f" + preview.playerUuids().size()
                        + " &7(Online: &a" + onlineCount + "&7, Offline: &c" + offlineCount + "&7)"));
                sender.sendMessage(ColorUtils
                    .colorizeComponent("&7Przedmiotów rynku do przywrócenia: &f" + preview.marketItemsCount()));
                sender.sendMessage(ColorUtils
                    .colorizeComponent("&7Zakres cofki: &eEkwipunek, Zbroja, Enderchesty, Kasa, Portfel, Rynek, Backupy"));
                sender.sendMessage(ColorUtils.colorizeComponent("&8&m--------------------------------------------------"));
                sender.sendMessage(ColorUtils.colorizeComponent("&c&lUWAGA: &cAby zatwierdzić cofkę, wpisz w ciągu 60s:"));
                sender.sendMessage(ColorUtils.colorizeComponent("&#00FF3B&l/" + label + " confirm " + token));
                sender.sendMessage(ColorUtils.colorizeComponent("&8&m--------------------------------------------------"));
            })
        ).exceptionally(ex -> {
            runSync(() -> sender.sendMessage(ColorUtils
                .colorizeComponent("&c✕ &8Ξ &cBłąd podczas przygotowywania podglądu cofki: " + ex.getMessage())));
            return null;
        });

        return true;
    }

    private Date parseTargetDate(String[] args) {
        try {
            if (args.length == 1) {
                // Format: HH:mm
                LocalTime time = LocalTime.parse(args[0], TIME_FORMATTER);
                LocalDate today = LocalDate.now();
                LocalDateTime ldt = LocalDateTime.of(today, time);
                if (ldt.isAfter(LocalDateTime.now())) {
                    ldt = ldt.minusDays(1);
                }
                return Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
            } else if (args.length >= 2) {
                // Format: YYYY-MM-DD HH:mm
                String dateTimeStr = args[0] + " " + args[1];
                LocalDateTime ldt = LocalDateTime.parse(dateTimeStr, DATE_TIME_FORMATTER);
                return Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(ColorUtils.colorizeComponent("&8&m--------------------------------------------------"));
        sender.sendMessage(ColorUtils.colorizeComponent("&#FFD700&lKOMENDY SYSTEMU COFKI (ROLLBACK):"));
        sender.sendMessage(
                ColorUtils.colorizeComponent("&8▪ &f/" + label + " &7- GUI cofki całego serwera"));
        sender.sendMessage(
                ColorUtils.colorizeComponent("&8▪ &f/" + label + " <HH:mm> &7- Cofka do danej godziny (np. 18:00)"));
        sender.sendMessage(
                ColorUtils.colorizeComponent("&8▪ &f/" + label + " <YYYY-MM-DD> <HH:mm> &7- Cofka do konkretnej daty"));
        sender.sendMessage(ColorUtils
                .colorizeComponent("&8▪ &f/" + label + " list &7- Lista ostatnich zarejestrowanych snapshotów"));
        sender.sendMessage(
                ColorUtils.colorizeComponent("&8▪ &f/" + label + " snapshot &7- Wymuszenie zapisu snapshotu"));
        sender.sendMessage(
                ColorUtils.colorizeComponent("&8▪ &f/" + label + " <nick> &7- GUI cofki jednego gracza (bez restartu)"));
        sender.sendMessage(ColorUtils.colorizeComponent("&8&m--------------------------------------------------"));
    }

    private BackupManager requireBackupManager(CommandSender sender) {
        BackupManager backupManager = plugin.getBackupManager();
        if (backupManager == null) {
            sender.sendMessage(ColorUtils.error("Modul backup/cofki jest wylaczony (addons.yml: backup)."));
        }
        return backupManager;
    }

    private void runSync(Runnable action) {
        Bukkit.getScheduler().runTask(plugin, action);
    }

    private void openServerRollbackGui(Player admin) {
        BackupManager backupManager = requireBackupManager(admin);
        if (backupManager == null) {
            return;
        }

        admin.sendMessage(ColorUtils.colorizeComponent("&#00E0FFℹ &8Ξ &7Ładowanie snapshotów serwera..."));

        backupManager.getDataManager().getAvailableSnapshotDates(100).thenAccept(dates ->
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (dates.isEmpty()) {
                    admin.sendMessage(ColorUtils.colorizeComponent(
                        "&c✕ &8Ξ &cBrak zarejestrowanych snapshotów serwera."));
                    return;
                }
                new ServerRollbackSelectGUI(plugin, admin, dates).open();
            })
        ).exceptionally(ex -> {
            Bukkit.getScheduler().runTask(plugin, () ->
                admin.sendMessage(ColorUtils.colorizeComponent(
                    "&c✕ &8Ξ &cBłąd ładowania snapshotów: " + ex.getMessage()))
            );
            return null;
        });
    }

    private void openPlayerRollbackGui(Player admin, String playerName) {
        BackupManager backupManager = requireBackupManager(admin);
        if (backupManager == null) {
            return;
        }

        if (playerName == null || playerName.isBlank()) {
            admin.sendMessage(ColorUtils.colorizeComponent("&c✕ &8Ξ &cPodaj nick gracza."));
            return;
        }

        admin.sendMessage(ColorUtils.colorizeComponent(
            "&#00E0FFℹ &8Ξ &7Wyszukiwanie gracza &f" + playerName.trim() + "&7..."));

        backupManager.getDataManager().resolvePlayerByNick(playerName.trim()).thenAccept(resolved ->
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!admin.isOnline()) {
                    return;
                }
                if (resolved == null) {
                    admin.sendMessage(ColorUtils.colorizeComponent(
                        plugin.getConfigManager().getBackupConfig().getMessages().getPlayerNotFound()));
                    return;
                }
                openPlayerRollbackGui(admin, resolved.uuid(), resolved.name());
            })
        ).exceptionally(ex -> {
            Bukkit.getScheduler().runTask(plugin, () ->
                admin.sendMessage(ColorUtils.colorizeComponent(
                    "&c✕ &8Ξ &cBłąd wyszukiwania gracza: " + ex.getMessage()))
            );
            return null;
        });
    }

    private void openPlayerRollbackGui(Player admin, UUID targetUuid, String targetName) {
        BackupManager backupManager = requireBackupManager(admin);
        if (backupManager == null) {
            return;
        }

        admin.sendMessage(ColorUtils.colorizeComponent(
            "&#00E0FFℹ &8Ξ &7Ładowanie snapshotów gracza &f" + targetName + "&7..."));

        backupManager.getDataManager().getPlayerGlobalSnapshots(targetUuid, 100).thenAccept(snapshots ->
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!admin.isOnline()) {
                    return;
                }
                if (snapshots.isEmpty()) {
                    admin.sendMessage(ColorUtils.colorizeComponent(
                        "&c✕ &8Ξ &cBrak snapshotów dla gracza &f" + targetName + "&c."));
                    return;
                }
                new PlayerRollbackSelectGUI(plugin, admin, targetUuid, targetName, snapshots).open();
            })
        ).exceptionally(ex -> {
            Bukkit.getScheduler().runTask(plugin, () ->
                admin.sendMessage(ColorUtils.colorizeComponent(
                    "&c✕ &8Ξ &cBłąd ładowania snapshotów: " + ex.getMessage()))
            );
            return null;
        });
    }

    private boolean isReservedSubcommand(String arg) {
        if (arg == null) {
            return false;
        }
        return switch (arg.toLowerCase(Locale.ROOT)) {
            case "list", "historia", "snapshot", "save", "confirm", "potwierdz" -> true;
            default -> false;
        };
    }

    private boolean isTimeArg(String arg) {
        return arg != null && arg.matches("\\d{1,2}:\\d{2}");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
            @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("holly.cmd.rollback")) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            List<String> list = new ArrayList<>(List.of("list", "snapshot", "confirm", "12:00", "18:00", "20:00",
                    LocalDate.now().toString()));
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (Player player : Bukkit.getOnlinePlayers()) {
                list.add(player.getName());
            }
            if (plugin.getJoinInfoManager() != null) {
                for (String name : plugin.getJoinInfoManager().getKnownPlayerNames()) {
                    if (name != null && !name.isBlank()) {
                        list.add(name);
                    }
                }
            }
            return list.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix)).distinct().toList();
        }
        return Collections.emptyList();
    }

    private record PendingRollback(Date targetDate, long expiresAt) {
        public boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }
}
