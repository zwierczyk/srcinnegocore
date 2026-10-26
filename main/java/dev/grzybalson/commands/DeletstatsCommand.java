package dev.grzybalson.commands;

import com.mongodb.client.model.Filters;
import dev.grzybalson.HollyCore;
import dev.grzybalson.modules.market.MarketItem;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class DeletstatsCommand implements CommandExecutor, TabCompleter {

    private final HollyCore plugin;

    public DeletstatsCommand(HollyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("Holly.cmd.deletstats")) {
            sender.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.deletstats"));
            return true;
        }

        if (args.length != 2) {
            sender.sendMessage(ColorUtils.usage("/" + label + " <nick|all> <kills|deaths|ranking|time|all>"));
            return true;
        }

        ResetType resetType = ResetType.fromInput(args[1]);
        if (resetType == null) {
            sender.sendMessage(ColorUtils.error("Niepoprawny typ. Dostępne: kills, deaths, ranking, time, all"));
            return true;
        }

        String targetArg = args[0];
        sender.sendMessage(ColorUtils.colorizeComponent("&7Trwa reset danych: &f" + targetArg + " &8(&f" + resetType.name().toLowerCase(Locale.ROOT) + "&8)"));

        CompletableFuture<Void> future;
        String successMessage;

        if (targetArg.equalsIgnoreCase("all")) {
            future = resetAllPlayers(resetType);
            successMessage = "&aZresetowano dane dla wszystkich graczy: &f" + resetType.name().toLowerCase(Locale.ROOT);
        } else {
            OfflinePlayer target = resolveOffline(targetArg);
            if (target == null) {
                sender.sendMessage(ColorUtils.colorizeComponent(
                    plugin.getConfigManager().getMessagesConfig().getPlayerNotFound()
                ));
                return true;
            }
            future = resetPlayer(target, resetType);
            successMessage = "&aZresetowano &f" + resetType.name().toLowerCase(Locale.ROOT) + " &adla gracza &f" + safeName(target);
        }

        future.whenComplete((ignored, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (throwable != null) {
                sender.sendMessage(ColorUtils.error("Nie udało się zresetować danych. Sprawdź konsolę."));
                plugin.getLogger().warning("Failed to reset player data: " + throwable.getMessage());
                return;
            }
            sender.sendMessage(ColorUtils.colorizeComponent(successMessage));
        }));
        return true;
    }

    private CompletableFuture<Void> resetAllPlayers(ResetType resetType) {
        return CompletableFuture.supplyAsync(() -> {
            Map<UUID, OfflinePlayer> targets = new LinkedHashMap<>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                targets.put(online.getUniqueId(), online);
            }
            for (OfflinePlayer offline : Bukkit.getOfflinePlayers()) {
                if (offline.hasPlayedBefore() || offline.isOnline()) {
                    targets.putIfAbsent(offline.getUniqueId(), offline);
                }
            }
            return targets;
        }).thenCompose(targets -> {
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (OfflinePlayer target : targets.values()) {
                futures.add(resetPlayer(target, resetType));
            }

            CompletableFuture<Void> combined = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            return combined.thenRun(() -> {
                if (resetType == ResetType.ALL) {
                    deleteAllPlayerFiles();
                }
            });
        }).thenRun(() -> {
            if (plugin.getTopManager() != null) {
                plugin.getTopManager().requestRefresh();
            }
            if (plugin.getMarketManager() != null) {
                plugin.getMarketManager().load();
            }
            if (plugin.getHollyPlaceholderExpansion() != null) {
                plugin.getHollyPlaceholderExpansion().invalidateAll();
            }
        });
    }

    private CompletableFuture<Void> resetPlayer(OfflinePlayer target, ResetType resetType) {
        UUID uuid = target.getUniqueId();
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        switch (resetType) {
            case KILLS -> {
                if (plugin.getAchievementManager() != null) {
                    futures.add(plugin.getAchievementManager().resetStats(uuid, true, false, false, false));
                }
                if (plugin.getKillstreakManager() != null) {
                    futures.add(plugin.getKillstreakManager().resetAllStreaks(uuid));
                }
                updateStatistic(target, Statistic.PLAYER_KILLS, 0);
            }
            case DEATHS -> {
                if (plugin.getAchievementManager() != null) {
                    futures.add(plugin.getAchievementManager().resetStats(uuid, false, true, false, false));
                }
                updateStatistic(target, Statistic.DEATHS, 0);
            }
            case RANKING -> {
                if (plugin.getEloManager() != null) {
                    futures.add(plugin.getEloManager().resetElo(uuid));
                }
            }
            case TIME -> {
                if (plugin.getStatsDataManager() != null) {
                    futures.add(plugin.getStatsDataManager().resetPlaytime(uuid));
                }
                updateStatistic(target, Statistic.PLAY_ONE_MINUTE, 0);
            }
            case ALL -> {
                if (plugin.getAchievementManager() != null) {
                    futures.add(plugin.getAchievementManager().resetStats(uuid, true, true, true, true));
                }
                if (plugin.getKillstreakManager() != null) {
                    futures.add(plugin.getKillstreakManager().resetAllStreaks(uuid));
                }
                if (plugin.getEloManager() != null) {
                    futures.add(plugin.getEloManager().resetElo(uuid));
                }
                if (plugin.getStatsDataManager() != null) {
                    futures.add(plugin.getStatsDataManager().resetPlaytime(uuid));
                }
                if (plugin.getEnderChestManager() != null) {
                    futures.add(plugin.getEnderChestManager().clearPlayerData(uuid));
                }
                if (plugin.getMarketManager() != null) {
                    futures.add(clearMarketData(uuid));
                }

                if (target.isOnline()) {
                    Player online = target.getPlayer();
                    if (online != null) {
                        clearOnlinePlayerData(online);
                    }
                } else {
                    deletePlayerFiles(uuid);
                }

                updateStatistic(target, Statistic.PLAYER_KILLS, 0);
                updateStatistic(target, Statistic.DEATHS, 0);
                updateStatistic(target, Statistic.PLAY_ONE_MINUTE, 0);
            }
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenRun(() -> {
                if (plugin.getTopManager() != null) {
                    plugin.getTopManager().invalidatePlayer(uuid);
                    plugin.getTopManager().requestRefresh();
                }
                if (plugin.getHollyPlaceholderExpansion() != null) {
                    plugin.getHollyPlaceholderExpansion().invalidatePlayer(uuid);
                }
            });
    }

    private CompletableFuture<Void> clearMarketData(UUID uuid) {
        if (plugin.getMarketManager() == null || plugin.getMarketManager().getDataManager() == null) {
            return CompletableFuture.completedFuture(null);
        }

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (MarketItem item : plugin.getMarketManager().getDataManager().getItemsBySeller(uuid)) {
            futures.add(plugin.getMarketManager().getDataManager().removeItem(item.getId()));
        }
        for (MarketItem item : plugin.getMarketManager().getExpiredItems(uuid)) {
            futures.add(plugin.getMarketManager().getDataManager().removeExpiredItem(item.getId(), uuid));
        }

        if (plugin.getMongoManager() != null && plugin.getMongoManager().getCollection("offline_payments") != null) {
            futures.add(
                plugin.getMongoManager().toFuture(
                    plugin.getMongoManager().getCollection("offline_payments")
                        .deleteOne(Filters.eq("uuid", uuid.toString()))
                ).thenApply(result -> null)
            );
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    private void clearOnlinePlayerData(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            InventoryView openInventory = player.getOpenInventory();
            if (openInventory != null) {
                player.closeInventory();
                openInventory.setCursor(null);
            }
            player.getInventory().clear();
            player.getInventory().setArmorContents(null);
            player.getInventory().setExtraContents(null);
            player.getEnderChest().clear();
            player.updateInventory();
            player.saveData();
        });
    }

    private void updateStatistic(OfflinePlayer target, Statistic statistic, int value) {
        if (target.isOnline()) {
            Player player = target.getPlayer();
            if (player != null) {
                if (Bukkit.isPrimaryThread()) {
                    try {
                        player.setStatistic(statistic, value);
                    } catch (Throwable ignored) {}
                } else {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        try {
                            if (player.isOnline()) {
                                player.setStatistic(statistic, value);
                            }
                        } catch (Throwable ignored) {}
                    });
                }
            }
        } else {
            resetOfflineStatisticFiles(target.getUniqueId(), statistic);
        }
    }

    private void resetOfflineStatisticFiles(UUID uuid, Statistic statistic) {
        Set<File> statsFiles = new HashSet<>();
        for (World world : Bukkit.getWorlds()) {
            statsFiles.add(new File(new File(world.getWorldFolder(), "stats"), uuid + ".json"));
        }
        File container = Bukkit.getWorldContainer();
        if (container != null) {
            statsFiles.add(new File(new File(container, "world/stats"), uuid + ".json"));
            statsFiles.add(new File(new File(container, "stats"), uuid + ".json"));
        }

        for (File file : statsFiles) {
            if (!file.exists() || !file.isFile()) continue;
            try (java.io.FileReader reader = new java.io.FileReader(file)) {
                com.google.gson.JsonObject root = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                if (root.has("stats") && root.get("stats").isJsonObject()) {
                    com.google.gson.JsonObject stats = root.getAsJsonObject("stats");
                    if (stats.has("minecraft:custom") && stats.get("minecraft:custom").isJsonObject()) {
                        com.google.gson.JsonObject custom = stats.getAsJsonObject("minecraft:custom");
                        if (statistic == Statistic.DEATHS) {
                            custom.remove("minecraft:deaths");
                        } else if (statistic == Statistic.PLAYER_KILLS) {
                            custom.remove("minecraft:player_kills");
                        } else if (statistic == Statistic.PLAY_ONE_MINUTE) {
                            custom.remove("minecraft:play_time");
                            custom.remove("minecraft:play_one_minute");
                            custom.remove("minecraft:total_world_time");
                        }
                        try (java.io.FileWriter writer = new java.io.FileWriter(file)) {
                            new com.google.gson.Gson().toJson(root, writer);
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private void deleteAllPlayerFiles() {
        for (OfflinePlayer target : Bukkit.getOfflinePlayers()) {
            if (target.hasPlayedBefore() || target.isOnline()) {
                deletePlayerFiles(target.getUniqueId());
            }
        }
    }

    private void deletePlayerFiles(UUID uuid) {
        for (World world : Bukkit.getWorlds()) {
            deleteIfExists(new File(new File(world.getWorldFolder(), "playerdata"), uuid + ".dat"));
            deleteIfExists(new File(new File(world.getWorldFolder(), "playerdata"), uuid + ".dat_old"));
            deleteIfExists(new File(new File(world.getWorldFolder(), "stats"), uuid + ".json"));
        }
    }

    private void deleteIfExists(File file) {
        if (file.exists() && !file.delete()) {
            plugin.getLogger().warning("Nie udalo sie usunac pliku: " + file.getAbsolutePath());
        }
    }

    private @Nullable OfflinePlayer resolveOffline(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        online = Bukkit.getPlayer(name);
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

    private String safeName(OfflinePlayer player) {
        return player.getName() != null ? player.getName() : player.getUniqueId().toString();
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("Holly.cmd.deletstats")) {
            return List.of();
        }

        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            completions.add("all");
            String input = args[0].toLowerCase(Locale.ROOT);
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase(Locale.ROOT).startsWith(input)) {
                    completions.add(player.getName());
                }
            }
            return completions;
        }

        if (args.length == 2) {
            return List.of("kills", "deaths", "ranking", "time", "all");
        }

        return List.of();
    }

    private enum ResetType {
        KILLS,
        DEATHS,
        RANKING,
        TIME,
        ALL;

        private static @Nullable ResetType fromInput(String input) {
            return switch (input.toLowerCase(Locale.ROOT)) {
                case "kills" -> KILLS;
                case "deaths" -> DEATHS;
                case "ranking" -> RANKING;
                case "time" -> TIME;
                case "all" -> ALL;
                default -> null;
            };
        }
    }
}
