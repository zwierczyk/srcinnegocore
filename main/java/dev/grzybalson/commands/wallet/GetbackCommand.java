package dev.grzybalson.commands.wallet;

import dev.grzybalson.HollyCore;
import dev.grzybalson.gui.GetbackProductGUI;
import dev.grzybalson.managers.WalletGetbackService;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class GetbackCommand implements CommandExecutor, TabCompleter {

    private final HollyCore plugin;
    private final WalletGetbackService getbackService;

    public GetbackCommand(HollyCore plugin) {
        this.plugin = plugin;
        this.getbackService = new WalletGetbackService(plugin);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("Holly.cmd.getback")) {
            sender.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.getback"));
            return true;
        }

        if (args.length < 1) {
            ColorUtils.sendUsage(sender,
                "/" + label + " <nick|all>",
                "/" + label + " <nick|all> <produkt|wszystko> &7- z konsoli");
            return true;
        }

        String targetArg = args[0];
        boolean forAll = targetArg.equalsIgnoreCase("all") || targetArg.equals("*");

        if (forAll && !sender.hasPermission("Holly.cmd.getback.all")) {
            sender.sendMessage(ColorUtils.noPermission(label + " all", "Holly.cmd.getback.all"));
            return true;
        }

        if (forAll) {
            if (args.length >= 2) {
                runWithProduct(sender, null, "wszyscy", true, joinProductQuery(args, 1));
                return true;
            }
            if (sender instanceof Player player) {
                new GetbackProductGUI(plugin, sender, "wszyscy", null, true).open();
                return true;
            }
            sender.sendMessage(ColorUtils.error("Z konsoli podaj produkt: /" + label + " all <produkt|wszystko>"));
            listProducts(sender);
            return true;
        }

        ResolvedTarget local = resolveLocalTarget(targetArg);
        if (local != null) {
            continueForTarget(sender, local, args);
            return true;
        }

        sender.sendMessage(ColorUtils.colorizeComponent(
            "&#00E0FFℹ &8Ξ &7Wyszukiwanie gracza &f" + targetArg.trim() + "&7 (takze offline)..."));

        resolveTargetAsync(targetArg).thenAccept(resolved -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (resolved == null) {
                sender.sendMessage(ColorUtils.colorizeComponent(
                    plugin.getConfigManager().getWalletConfig().getMessages().getPlayerNotFound()
                        .replace("{PLAYER}", targetArg)));
                return;
            }
            continueForTarget(sender, resolved, args);
        })).exceptionally(ex -> {
            Bukkit.getScheduler().runTask(plugin, () ->
                sender.sendMessage(ColorUtils.error("Blad wyszukiwania gracza: " + ex.getMessage())));
            return null;
        });
        return true;
    }

    private void continueForTarget(CommandSender sender, ResolvedTarget target, String[] args) {
        if (args.length >= 2) {
            runWithProduct(sender, target.uuid(), target.name(), false, joinProductQuery(args, 1));
            return;
        }

        if (sender instanceof Player) {
            new GetbackProductGUI(plugin, sender, target.name(), target.uuid(), false).open();
            return;
        }

        sender.sendMessage(ColorUtils.error("Z konsoli podaj produkt: /getback " + target.name() + " <produkt|wszystko>"));
        listProducts(sender);
    }

    private void runWithProduct(CommandSender sender, UUID targetUuid, String targetLabel, boolean forAll, String productQuery) {
        Optional<WalletGetbackService.ProductSelection> selection = getbackService.resolveSelection(productQuery);
        if (selection.isEmpty()) {
            sender.sendMessage(ColorUtils.error("Nie znaleziono produktu portfela: " + productQuery));
            listProducts(sender);
            return;
        }

        sender.sendMessage(ColorUtils.colorizeComponent(
            "&#00E0FFℹ &8Ξ &7Przywracanie &f" + selection.get().label() + " &7dla &f" + targetLabel + "&7..."));

        var future = forAll
            ? getbackService.restoreForAll(selection.get())
            : getbackService.restoreForPlayer(targetUuid, selection.get());

        future.thenAccept(result -> Bukkit.getScheduler().runTask(plugin, () ->
            GetbackCommandHelper.sendResult(sender, targetLabel, result)
        )).exceptionally(ex -> {
            Bukkit.getScheduler().runTask(plugin, () ->
                sender.sendMessage(ColorUtils.error("Blad przywracania zakupow: " + ex.getMessage())));
            return null;
        });
    }

    private CompletableFuture<ResolvedTarget> resolveTargetAsync(String name) {
        CompletableFuture<ResolvedTarget> walletLookup = CompletableFuture.completedFuture((ResolvedTarget) null);
        if (plugin.getWalletManager() != null && plugin.getWalletManager().getDataManager() != null) {
            walletLookup = plugin.getWalletManager().getDataManager()
                .resolvePlayerByName(name)
                .thenApply(optional -> optional
                    .map(player -> new ResolvedTarget(player.uuid(), player.name()))
                    .orElse(null));
        }

        CompletableFuture<ResolvedTarget> backupLookup = CompletableFuture.completedFuture(null);
        if (plugin.getBackupManager() != null) {
            backupLookup = plugin.getBackupManager().getDataManager()
                .resolvePlayerByNick(name)
                .thenApply(resolved -> resolved == null ? null : new ResolvedTarget(resolved.uuid(), resolved.name()));
        }

        return walletLookup.thenCombine(backupLookup, (wallet, backup) -> wallet != null ? wallet : backup);
    }

    private ResolvedTarget resolveLocalTarget(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String trimmed = name.trim();

        Player online = Bukkit.getPlayerExact(trimmed);
        if (online != null) {
            return new ResolvedTarget(online.getUniqueId(), online.getName());
        }

        try {
            OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(trimmed);
            if (cached != null && cached.hasPlayedBefore()) {
                return new ResolvedTarget(cached.getUniqueId(),
                    cached.getName() != null ? cached.getName() : trimmed);
            }
        } catch (NoSuchMethodError | NoClassDefFoundError ignored) {
        }

        for (OfflinePlayer offline : Bukkit.getOfflinePlayers()) {
            if (offline.getName() != null && offline.getName().equalsIgnoreCase(trimmed)) {
                return new ResolvedTarget(offline.getUniqueId(), offline.getName());
            }
        }

        if (plugin.getJoinInfoManager() != null) {
            UUID uuid = plugin.getJoinInfoManager().getUuidByName(trimmed);
            if (uuid != null) {
                String stored = plugin.getJoinInfoManager().getPlayerName(uuid);
                return new ResolvedTarget(uuid, stored != null ? stored : trimmed);
            }
        }

        if (plugin.getTopManager() != null) {
            UUID uuid = plugin.getTopManager().getUuidByName(trimmed);
            if (uuid != null) {
                return new ResolvedTarget(uuid, trimmed);
            }
        }

        OfflinePlayer offline = Bukkit.getOfflinePlayer(trimmed);
        if (offline.hasPlayedBefore()) {
            return new ResolvedTarget(offline.getUniqueId(),
                offline.getName() != null ? offline.getName() : trimmed);
        }

        return null;
    }

    private void listProducts(CommandSender sender) {
        sender.sendMessage(ColorUtils.colorizeComponent("&7Dostepne produkty portfela:"));
        for (WalletGetbackService.CatalogEntry entry : getbackService.getCatalog()) {
            sender.sendMessage(ColorUtils.colorizeComponent(
                " &8• &f" + ColorUtils.stripColors(entry.displayName())
                    + " &7(" + entry.serviceTitle() + ", " + String.format(Locale.US, "%.2f", entry.price()) + ")"));
        }
        sender.sendMessage(ColorUtils.colorizeComponent(" &8• &awszystko &7- wszystkie zakupy z logow"));
    }

    private static String joinProductQuery(String[] args, int fromIndex) {
        StringBuilder builder = new StringBuilder();
        for (int i = fromIndex; i < args.length; i++) {
            if (i > fromIndex) {
                builder.append(' ');
            }
            builder.append(args[i]);
        }
        return builder.toString();
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("Holly.cmd.getback")) {
            return List.of();
        }
        if (args.length == 1) {
            Set<String> options = new LinkedHashSet<>();
            if (sender.hasPermission("Holly.cmd.getback.all")) {
                options.add("all");
            }
            String partial = args[0].toLowerCase(Locale.ROOT);
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase(Locale.ROOT).startsWith(partial)) {
                    options.add(player.getName());
                }
            }
            for (OfflinePlayer offline : Bukkit.getOfflinePlayers()) {
                if (offline.getName() != null && offline.getName().toLowerCase(Locale.ROOT).startsWith(partial)) {
                    options.add(offline.getName());
                }
            }
            if (plugin.getWalletManager() != null && plugin.getWalletManager().getDataManager() != null) {
                try {
                    List<String> walletNames = plugin.getWalletManager().getDataManager().listCachedPlayerNames();
                    for (String name : walletNames) {
                        if (name.toLowerCase(Locale.ROOT).startsWith(partial)) {
                            options.add(name);
                        }
                    }
                } catch (Exception ignored) {
                }
            }
            return options.stream()
                .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(partial))
                .toList();
        }
        if (args.length >= 2) {
            String partial = joinProductQuery(args, 1).toLowerCase(Locale.ROOT);
            return getbackService.listProductSuggestions(partial).stream()
                .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(partial)
                    || option.toLowerCase(Locale.ROOT).contains(partial))
                .toList();
        }
        return List.of();
    }

    private record ResolvedTarget(UUID uuid, String name) {
    }
}
