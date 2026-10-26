package dev.grzybalson.commands.wallet;

import dev.grzybalson.HollyCore;
import dev.grzybalson.database.WalletDataManager;
import dev.grzybalson.config.configs.WalletConfig;
import dev.grzybalson.gui.WalletLogsGUI;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.MoneyAmountUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.Locale;

public class APortfelCommand implements CommandExecutor, TabCompleter {

    private final HollyCore plugin;
    private final Queue<RechargeAnnouncementData> rechargeQueue = new ArrayDeque<>();
    private boolean rechargeAnnouncementRunning = false;

    public APortfelCommand(HollyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("reload")) {
            plugin.getConfigManager().reloadWalletConfig();
            if (plugin.getWalletProductStatsService() != null) {
                plugin.getWalletProductStatsService().invalidateCache();
            }
            sender.sendMessage(ColorUtils.colorizeComponent(plugin.getConfigManager().getWalletConfig().getMessages().getReloaded()));
            return true;
        }

        if (!plugin.getConfigManager().getWalletConfig().isEnabled()) {
            sender.sendMessage(ColorUtils.colorizeComponent(plugin.getConfigManager().getWalletConfig().getMessages().getDisabled()));
            return true;
        }

        switch (sub) {
            case "info" -> {
                if (args.length < 2) {
                    sendUsage(sender);
                    return true;
                }
                resolveTarget(sender, args[1], target -> showBalance(sender, target));
                return true;
            }
            case "add", "remove", "set", "doladowanie" -> {
                if (args.length < 3) {
                    sendUsage(sender);
                    return true;
                }
                Double amount = parseAmount(args[2]);
                if (amount == null || (!sub.equals("set") && !MoneyAmountUtil.isPositiveFinite(amount))) {
                    sender.sendMessage(ColorUtils.colorizeComponent(plugin.getConfigManager().getWalletConfig().getMessages().getInvalidAmount()));
                    return true;
                }
                resolveTarget(sender, args[1], target -> handleMoneyChange(sender, target, sub, amount));
                return true;
            }
            case "logs" -> {
                if (args.length < 2) {
                    sendUsage(sender);
                    return true;
                }
                resolveTarget(sender, args[1], target -> showLogs(sender, target));
                return true;
            }
            case "total" -> {
                plugin.getWalletManager().getTotalRechargeValue().thenAccept(total -> Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(ColorUtils.colorizeComponent(
                        plugin.getConfigManager().getWalletConfig().getMessages().getTotal()
                            .replace("{TOTAL}", format(total))
                            .replace("{CURRENCY}", plugin.getConfigManager().getWalletConfig().getCurrencyName())
                    ));
                }));
                return true;
            }
            default -> {
                sendUsage(sender);
                return true;
            }
        }
    }

    private void resolveTarget(CommandSender sender, String name, java.util.function.Consumer<WalletPlayerLookup.ResolvedTarget> action) {
        WalletPlayerLookup.ResolvedTarget local = WalletPlayerLookup.resolveLocal(plugin, name);
        if (local != null) {
            action.accept(local);
            return;
        }
        sender.sendMessage(ColorUtils.colorizeComponent(
            "&#00E0FFℹ &8Ξ &7Wyszukiwanie gracza &f" + name.trim() + "&7 (także offline)..."));
        WalletPlayerLookup.resolve(plugin, name).thenAccept(resolved -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (resolved == null) {
                sender.sendMessage(ColorUtils.colorizeComponent(
                    plugin.getConfigManager().getWalletConfig().getMessages().getPlayerNotFound().replace("{PLAYER}", name)));
                return;
            }
            action.accept(resolved);
        })).exceptionally(error -> {
            Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(ColorUtils.colorizeComponent(
                plugin.getConfigManager().getWalletConfig().getMessages().getPlayerNotFound().replace("{PLAYER}", name))));
            return null;
        });
    }

    private void showBalance(CommandSender sender, WalletPlayerLookup.ResolvedTarget target) {
        plugin.getWalletManager().getBalance(target.uuid()).thenAccept(balance -> Bukkit.getScheduler().runTask(plugin, () -> {
            sender.sendMessage(ColorUtils.colorizeComponent(
                plugin.getConfigManager().getWalletConfig().getMessages().getBalanceTarget()
                    .replace("{PLAYER}", displayName(target))
                    .replace("{BALANCE}", format(balance))
                    .replace("{CURRENCY}", plugin.getConfigManager().getWalletConfig().getCurrencyName())
            ));
        }));
    }

    private void showLogs(CommandSender sender, WalletPlayerLookup.ResolvedTarget target) {
        plugin.getWalletManager().getLogs(target.uuid()).thenAccept(logs -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (logs.isEmpty()) {
                sender.sendMessage(ColorUtils.colorizeComponent(plugin.getConfigManager().getWalletConfig().getMessages().getNoLogs()));
                return;
            }
            if (sender instanceof Player player) {
                new WalletLogsGUI(plugin, player, target.asOffline(), logs).open();
            } else {
                for (WalletDataManager.WalletLog log : logs) {
                    sender.sendMessage(ColorUtils.info("[PORTFEL] " + pretty(log)));
                }
            }
        }));
    }

    private void sendUsage(CommandSender sender) {
        ColorUtils.sendUsage(sender,
            "/aportfel info <nick>",
            "/aportfel add <nick> <ilość>",
            "/aportfel remove <nick> <ilość>",
            "/aportfel set <nick> <ilość>",
            "/aportfel doladowanie <nick> <ilość>",
            "/aportfel logs <nick>",
            "/aportfel total",
            "/aportfel reload"
        );
    }

    private void handleMoneyChange(CommandSender sender, WalletPlayerLookup.ResolvedTarget target, String sub, double amount) {
        String actorName = sender.getName();
        String actorUuid = sender instanceof Player player ? player.getUniqueId().toString() : "CONSOLE";
        String playerName = displayName(target);
        String offlineHint = Bukkit.getPlayer(target.uuid()) == null ? " &7(offline)" : "";
        switch (sub) {
            case "add" -> plugin.getWalletManager().addBalance(target.uuid(), amount, "Administracyjne dodanie", actorName, "ADMIN_ADD", actorUuid, "add", playerName)
                .thenAccept(success -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!Boolean.TRUE.equals(success)) {
                        sender.sendMessage(ColorUtils.colorizeComponent(
                            plugin.getConfigManager().getWalletConfig().getMessages().getInvalidAmount()));
                        return;
                    }
                    sender.sendMessage(ColorUtils.colorizeComponent(
                        plugin.getConfigManager().getWalletConfig().getMessages().getAdded()
                            .replace("{PLAYER}", playerName)
                            .replace("{AMOUNT}", format(amount))
                            .replace("{CURRENCY}", plugin.getConfigManager().getWalletConfig().getCurrencyName())
                            + offlineHint
                    ));
                }));
            case "remove" -> plugin.getWalletManager().removeBalance(target.uuid(), amount, "Administracyjne usunięcie", actorName, "ADMIN_REMOVE", actorUuid, "remove", playerName)
                .thenAccept(success -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!success) {
                        sender.sendMessage(ColorUtils.colorizeComponent(plugin.getConfigManager().getWalletConfig().getMessages().getInsufficientFunds().replace("{CURRENCY}", plugin.getConfigManager().getWalletConfig().getCurrencyName())));
                        return;
                    }
                    sender.sendMessage(ColorUtils.colorizeComponent(
                        plugin.getConfigManager().getWalletConfig().getMessages().getRemoved()
                            .replace("{PLAYER}", playerName)
                            .replace("{AMOUNT}", format(amount))
                            .replace("{CURRENCY}", plugin.getConfigManager().getWalletConfig().getCurrencyName())
                            + offlineHint
                    ));
                }));
            case "set" -> plugin.getWalletManager().setBalance(target.uuid(), amount, "Administracyjne ustawienie", actorName, "ADMIN_SET", actorUuid, "set")
                .thenAccept(newBalance -> Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(ColorUtils.colorizeComponent(
                    plugin.getConfigManager().getWalletConfig().getMessages().getSet()
                        .replace("{PLAYER}", playerName)
                        .replace("{AMOUNT}", format(newBalance))
                        .replace("{CURRENCY}", plugin.getConfigManager().getWalletConfig().getCurrencyName())
                        + offlineHint
                ))));
            case "doladowanie" -> plugin.getWalletManager().addBalance(target.uuid(), amount, "Doładowanie", actorName, "RECHARGE", actorUuid, "recharge", playerName)
                .thenAccept(success -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!Boolean.TRUE.equals(success)) {
                        sender.sendMessage(ColorUtils.colorizeComponent(
                            plugin.getConfigManager().getWalletConfig().getMessages().getInvalidAmount()));
                        return;
                    }
                    sender.sendMessage(ColorUtils.colorizeComponent(
                        plugin.getConfigManager().getWalletConfig().getMessages().getRechargeAdded()
                            .replace("{PLAYER}", playerName)
                            .replace("{AMOUNT}", format(amount))
                            .replace("{CURRENCY}", plugin.getConfigManager().getWalletConfig().getCurrencyName())
                            + offlineHint
                    ));
                    announceRecharge(target.asOffline(), amount);
                }));
        }
    }

    private void announceRecharge(OfflinePlayer target, double amount) {
        rechargeQueue.add(new RechargeAnnouncementData(target, amount));
        if (!rechargeAnnouncementRunning) {
            playNextRechargeAnnouncement();
        }
    }

    private void playNextRechargeAnnouncement() {
        RechargeAnnouncementData data = rechargeQueue.poll();
        if (data == null) {
            rechargeAnnouncementRunning = false;
            return;
        }

        rechargeAnnouncementRunning = true;
        playRechargeAnnouncementNow(data.target(), data.amount());

        var config = plugin.getConfigManager().getWalletConfig().getRechargeAnnouncement();
        long delay = Math.max(20L, (long) config.getTitleFadeIn() + config.getTitleStay() + config.getTitleFadeOut() + 5L);
        Bukkit.getScheduler().runTaskLater(plugin, this::playNextRechargeAnnouncement, delay);
    }

    private void playRechargeAnnouncementNow(OfflinePlayer target, double amount) {
        var config = plugin.getConfigManager().getWalletConfig().getRechargeAnnouncement();
        String playerName = safeName(target);
        String formatted = format(amount);
        String currency = plugin.getConfigManager().getWalletConfig().getCurrencyName();
        String title = ColorUtils.colorize(config.getTitle()
            .replace("{PLAYER}", playerName)
            .replace("{AMOUNT}", formatted)
            .replace("{CURRENCY}", currency));
        String subTitle = ColorUtils.colorize(config.getSubTitle()
            .replace("{PLAYER}", playerName)
            .replace("{AMOUNT}", formatted)
            .replace("{CURRENCY}", currency));

        for (String line : config.getMessages()) {
            Bukkit.broadcast(ColorUtils.colorizeComponent(
                line.replace("{PLAYER}", playerName)
                    .replace("{AMOUNT}", formatted)
                    .replace("{CURRENCY}", currency)
            ));
        }

        WalletConfig.RechargeSound rechargeSound = resolveRechargeSound(config, amount);
        for (Player online : Bukkit.getOnlinePlayers()) {
            dev.grzybalson.utils.ColorUtils.sendTitle(online, 
                title,
                subTitle,
                config.getTitleFadeIn(),
                config.getTitleStay(),
                config.getTitleFadeOut()
            );
            if (plugin.getUserSettingsManager() == null || plugin.getUserSettingsManager().isRechargeMusicEnabled(online.getUniqueId())) {
                playRechargeSound(online, rechargeSound.getSoundName(), rechargeSound.getVolume(), rechargeSound.getPitch());
            }
        }
    }

    private WalletConfig.RechargeSound resolveRechargeSound(WalletConfig.RechargeAnnouncement config, double amount) {
        WalletConfig.RechargeSound selected = new WalletConfig.RechargeSound(0.0, config.getSoundName(), 1.0f, 1.0f);
        if (config.getAmountSounds() == null || config.getAmountSounds().isEmpty()) {
            return selected;
        }

        for (WalletConfig.RechargeSound sound : config.getAmountSounds()) {
            if (sound == null) {
                continue;
            }
            if (amount >= sound.getAmount() && sound.getAmount() >= selected.getAmount()) {
                selected = sound;
            }
        }
        return selected;
    }

    private void playRechargeSound(Player player, String configuredSound, float volume, float pitch) {
        String soundName = configuredSound == null ? "" : configuredSound.trim();
        if (soundName.isEmpty()) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, volume, pitch);
            return;
        }

        try {
            String normalized = soundName.toUpperCase(Locale.ROOT).replace("MINECRAFT:", "");
            player.playSound(player.getLocation(), Sound.valueOf(normalized), volume, pitch);
            return;
        } catch (IllegalArgumentException ignored) {
        }

        try {
            String namespaced = soundName.contains(":") ? soundName.toLowerCase(Locale.ROOT) : "minecraft:" + soundName.toLowerCase(Locale.ROOT);
            player.playSound(player.getLocation(), namespaced, SoundCategory.MASTER, volume, pitch);
            return;
        } catch (Exception ignored) {
        }

        try {
            player.playSound(player.getLocation(), soundName, SoundCategory.MASTER, volume, pitch);
        } catch (Exception ignored) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, volume, pitch);
        }
    }

    private record RechargeAnnouncementData(OfflinePlayer target, double amount) {}

    private String pretty(WalletDataManager.WalletLog log) {
        return log.getAction() + " | aktor=" + log.getActorName() + " | źródło=" + log.getSource() + " | kwota=" + format(log.getAmount());
    }

    private String safeName(OfflinePlayer target) {
        return target.getName() != null ? target.getName() : target.getUniqueId().toString();
    }

    private String displayName(WalletPlayerLookup.ResolvedTarget target) {
        if (target.name() != null && !target.name().isBlank()) {
            return target.name();
        }
        return safeName(target.asOffline());
    }

    private @Nullable Double parseAmount(String input) {
        Double parsed = MoneyAmountUtil.parse(input);
        if (parsed == null || !MoneyAmountUtil.isNonNegativeFinite(parsed)) {
            return null;
        }
        return parsed;
    }

    private String format(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("info", "add", "remove", "set", "doladowanie", "logs", "total", "reload").stream()
                .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                .toList();
        }
        if (args.length == 2 && List.of("info", "add", "remove", "set", "doladowanie", "logs").contains(args[0].toLowerCase(Locale.ROOT))) {
            return WalletPlayerLookup.suggestNames(plugin, args[1]);
        }
        return List.of();
    }
}
