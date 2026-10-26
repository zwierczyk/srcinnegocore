package dev.grzybalson.commands;

import com.mongodb.client.model.Updates;
import com.mongodb.client.result.UpdateResult;
import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ColorUtils;
import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class StatsCommand implements CommandExecutor, TabCompleter {

    private static final int RESET_RANKING_VALUE = 1000;

    private final HollyCore plugin;

    public StatsCommand(HollyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("reset")) {
            if (!sender.hasPermission("Holly.cmd.stats.reset")) {
                sender.sendMessage(ColorUtils.noPermission(label + " reset", "Holly.cmd.stats.reset"));
                return true;
            }
            return handleReset(sender, args);
        }

        sender.sendMessage(ColorUtils.usage("/" + label + " reset <*|nick>"));
        return true;
    }

    private boolean handleReset(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage(ColorUtils.usage("/stats reset <*|nick>"));
            return true;
        }

        if (plugin.getMongoManager() == null || !plugin.getMongoManager().isAvailable()) {
            sender.sendMessage(ColorUtils.error("MongoDB jest niedostępne."));
            return true;
        }

        String targetArg = args[1];
        sender.sendMessage(ColorUtils.colorizeComponent("&7Trwa reset statystyk: &f" + targetArg + "&7…"));

        CompletableFuture<ResetResult> future;
        if (targetArg.equals("*")) {
            future = resetAllFromDatabase();
        } else {
            OfflinePlayer target = resolveOffline(targetArg);
            if (target == null) {
                sender.sendMessage(ColorUtils.colorizeComponent(
                    plugin.getConfigManager().getMessagesConfig().getPlayerNotFound()
                ));
                return true;
            }
            future = resetPlayerFromDatabase(target.getUniqueId()).thenApply(ignored -> new ResetResult(1, 0));
        }

        future.whenComplete((result, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (throwable != null) {
                sender.sendMessage(ColorUtils.error("Nie udało się zresetować statystyk. Sprawdź konsolę."));
                plugin.getLogger().warning("Stats reset failed: " + throwable.getMessage());
                return;
            }
            if (targetArg.equals("*")) {
                sender.sendMessage(ColorUtils.colorizeComponent(
                    "&aZresetowano statystyki (&franking, zabójstwa, śmierci&a) dla &f" + result.players() + " &agraczy"
                        + (result.clans() > 0 ? " &7oraz &f" + result.clans() + " &7klanów" : "")
                        + "&a. &7(Kasa &enie&7 została zmieniona.)"
                ));
            } else {
                sender.sendMessage(ColorUtils.colorizeComponent(
                    "&aZresetowano statystyki gracza &f" + targetArg + "&a: &f" + RESET_RANKING_VALUE + " ranking &7| &f0 kills &7| &f0 śmierci"
                ));
            }
        }));
        return true;
    }

    private CompletableFuture<ResetResult> resetAllFromDatabase() {
        List<CompletableFuture<?>> updates = new ArrayList<>();

        if (plugin.getAchievementManager() != null) {
            updates.add(updateMany(
                plugin.getMongoManager().getCollection("achievements"),
                new Document(),
                Updates.combine(Updates.set("kills", 0), Updates.set("deaths", 0))
            ));
        }

        if (plugin.getEloManager() != null) {
            updates.add(updateMany(
                plugin.getMongoManager().getCollection("player_elo"),
                new Document(),
                Updates.set("elo", RESET_RANKING_VALUE)
            ));
        }

        CompletableFuture<Long> clanFuture = CompletableFuture.completedFuture(0L);
        if (plugin.getClanManager() != null && plugin.getClanManager().getDataManager() != null) {
            clanFuture = plugin.getClanManager().getDataManager().resetAllRankingStats();
            updates.add(clanFuture.thenApply(ignored -> null));
        }

        if (updates.isEmpty()) {
            return CompletableFuture.completedFuture(new ResetResult(0, 0));
        }

        CompletableFuture<Long> finalClanFuture = clanFuture;
        return CompletableFuture.allOf(updates.toArray(new CompletableFuture[0]))
            .thenCompose(ignored -> collectAllPlayerUuids())
            .thenApply(uuids -> {
                if (plugin.getAchievementManager() != null) {
                    plugin.getAchievementManager().resetCachedCombatStats();
                }
                if (plugin.getEloManager() != null) {
                    plugin.getEloManager().clearAllCaches();
                }
                Bukkit.getScheduler().runTask(plugin, () -> {
                    syncOnlinePlayers(null);
                    refreshTopAndPlaceholders(null);
                });
                return new ResetResult(uuids.size(), finalClanFuture.join());
            });
    }

    private CompletableFuture<Set<UUID>> collectAllPlayerUuids() {
        Set<UUID> uuids = new HashSet<>();
        List<CompletableFuture<List<Document>>> loads = new ArrayList<>();

        if (plugin.getAchievementManager() != null) {
            loads.add(plugin.getMongoManager().toListFuture(
                plugin.getMongoManager().getCollection("achievements").find()
            ));
        }
        if (plugin.getEloManager() != null) {
            loads.add(plugin.getMongoManager().toListFuture(
                plugin.getMongoManager().getCollection("player_elo").find()
            ));
        }

        if (loads.isEmpty()) {
            return CompletableFuture.completedFuture(uuids);
        }

        return CompletableFuture.allOf(loads.toArray(new CompletableFuture[0]))
            .thenApply(ignored -> {
                for (CompletableFuture<List<Document>> load : loads) {
                    for (Document doc : load.join()) {
                        UUID uuid = parseUuid(doc);
                        if (uuid != null) {
                            uuids.add(uuid);
                        }
                    }
                }
                return uuids;
            });
    }

    private CompletableFuture<Void> resetPlayerFromDatabase(UUID uuid) {
        List<CompletableFuture<?>> futures = new ArrayList<>();

        if (plugin.getAchievementManager() != null) {
            futures.add(plugin.getAchievementManager().resetStats(uuid, true, true, false, false));
        }
        if (plugin.getEloManager() != null) {
            futures.add(plugin.getEloManager().setEloAsync(uuid, RESET_RANKING_VALUE));
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> {
                syncOnlinePlayers(uuid);
                refreshTopAndPlaceholders(uuid);
            }));
    }

    private CompletableFuture<UpdateResult> updateMany(
        com.mongodb.reactivestreams.client.MongoCollection<Document> collection,
        Document filter,
        org.bson.conversions.Bson update
    ) {
        return plugin.getMongoManager().toFuture(collection.updateMany(filter, update));
    }

    private @Nullable UUID parseUuid(Document doc) {
        if (doc == null) {
            return null;
        }
        String uuidStr = doc.getString("uuid");
        if (uuidStr == null || uuidStr.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(uuidStr);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private void syncOnlinePlayers(@Nullable UUID only) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (only != null && !player.getUniqueId().equals(only)) {
                continue;
            }
            UUID uuid = player.getUniqueId();
            try {
                player.setStatistic(Statistic.PLAYER_KILLS, 0);
                player.setStatistic(Statistic.DEATHS, 0);
            } catch (Throwable ignored) {
            }
            if (plugin.getEloManager() != null) {
                plugin.getEloManager().setElo(uuid, RESET_RANKING_VALUE);
            }
            if (plugin.getAchievementManager() != null) {
                var cached = plugin.getAchievementManager().getCachedData(uuid);
                if (cached != null) {
                    cached.setKills(0);
                    cached.setDeaths(0);
                }
            }
        }
    }

    private void refreshTopAndPlaceholders(@Nullable UUID only) {
        if (plugin.getTopManager() != null) {
            if (only != null) {
                plugin.getTopManager().invalidatePlayer(only);
            }
            plugin.getTopManager().requestRefresh();
        }
        if (plugin.getHollyPlaceholderExpansion() != null) {
            if (only != null) {
                plugin.getHollyPlaceholderExpansion().invalidatePlayer(only);
            } else {
                plugin.getHollyPlaceholderExpansion().invalidateAll();
            }
        }
    }

    private @Nullable OfflinePlayer resolveOffline(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        for (OfflinePlayer offline : Bukkit.getOfflinePlayers()) {
            if (offline.getName() != null && offline.getName().equalsIgnoreCase(name)) {
                return offline;
            }
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        if (offline.hasPlayedBefore() || offline.getName() != null) {
            return offline;
        }
        return null;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("Holly.cmd.stats.reset")) {
            return List.of();
        }

        if (args.length == 1) {
            return filterPrefix(List.of("reset"), args[0]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("reset")) {
            List<String> options = new ArrayList<>();
            options.add("*");
            String prefix = args[1].toLowerCase(Locale.ROOT);
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    options.add(player.getName());
                }
            }
            return filterPrefix(options, args[1]);
        }

        return List.of();
    }

    private List<String> filterPrefix(List<String> options, String prefix) {
        String lowerPrefix = prefix.toLowerCase(Locale.ROOT);
        List<String> filtered = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lowerPrefix)) {
                filtered.add(option);
            }
        }
        return filtered;
    }

    private record ResetResult(int players, long clans) {
    }
}
