package dev.grzybalson.commands;

import com.mongodb.client.model.Filters;
import dev.grzybalson.HollyCore;
import dev.grzybalson.config.configs.PlayerCommandConfig;
import dev.grzybalson.database.StatsDataManager;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.SpooferBridge;
import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
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
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

public class GraczCommand implements CommandExecutor, TabCompleter {

    private final HollyCore plugin;

    public GraczCommand(HollyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("Holly.cmd.gracz.reload") && !sender.hasPermission("core.admin")) {
                sender.sendMessage(ColorUtils.noPermission(label + " reload", "Holly.cmd.gracz.reload"));
                return true;
            }
            plugin.getConfigManager().reloadPlayerCommandConfig();
            sender.sendMessage(ColorUtils.colorizeComponent(config().getReloadMessage()));
            return true;
        }

        if (!sender.hasPermission("Holly.cmd.gracz")) {
            sender.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.gracz"));
            return true;
        }

        if (args.length == 0) {
            if (sender instanceof Player player) {
                sendProfile(sender, player.getUniqueId(), player.getName(), false);
                return true;
            }
            sender.sendMessage(ColorUtils.usage("/" + label + " <nick>"));
            return true;
        }

        String input = args[0];
        ResolvedPlayer local = resolveLocal(input);
        if (local != null) {
            sendProfile(sender, local.uuid(), local.name(), local.fake());
            return true;
        }

        lookupOffline(input).thenAccept(resolved -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (sender instanceof Player player && !player.isOnline()) {
                return;
            }
            if (resolved == null) {
                sender.sendMessage(ColorUtils.colorizeComponent(
                    config().getPlayerNotFound().replace("{player}", input)
                ));
                return;
            }
            sendProfile(sender, resolved.uuid(), resolved.name(), resolved.fake());
        }));
        return true;
    }

    private void sendProfile(CommandSender sender, UUID uuid, String name, boolean fake) {
        String displayName = name != null && !name.isBlank() ? name : uuid.toString();

        if (fake) {
            FakeStats stats = fakeStats(uuid);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (sender instanceof Player player && !player.isOnline()) {
                    return;
                }
                sendLines(sender, displayName, null, stats.deaths(), stats.kills(), stats.points(),
                    stats.playtimeMillis(), stats.killstreak(), stats.highstreak());
            });
            return;
        }

        CompletableFuture<Integer> killsFuture = plugin.getAchievementManager() != null
            ? plugin.getAchievementManager().loadPlayer(uuid).thenApply(data -> data.getKills())
            : CompletableFuture.completedFuture(0);
        CompletableFuture<Integer> deathsFuture = plugin.getAchievementManager() != null
            ? plugin.getAchievementManager().loadPlayer(uuid).thenApply(data -> data.getDeaths())
            : CompletableFuture.completedFuture(0);
        CompletableFuture<Long> playtimeFuture = plugin.getStatsDataManager() != null
            ? plugin.getStatsDataManager().getStats(uuid).thenApply(StatsDataManager.PlayerStats::getTotalPlaytime)
            : CompletableFuture.completedFuture(0L);
        CompletableFuture<Integer> streakFuture = plugin.getKillstreakManager() != null
            ? plugin.getKillstreakManager().getKillstreak(uuid)
            : CompletableFuture.completedFuture(0);
        CompletableFuture<Integer> highStreakFuture = plugin.getKillstreakManager() != null
            ? plugin.getKillstreakManager().getHighStreak(uuid)
            : CompletableFuture.completedFuture(0);
        CompletableFuture<Integer> pointsFuture = plugin.getEloManager() != null
            ? plugin.getEloManager().getEloAsync(uuid)
            : CompletableFuture.completedFuture(0);
        CompletableFuture<String> clanFuture = plugin.getClanManager() != null
            ? plugin.getClanManager().getPlayerClan(uuid)
            : CompletableFuture.completedFuture(null);

        CompletableFuture.allOf(killsFuture, deathsFuture, playtimeFuture, streakFuture, highStreakFuture, pointsFuture, clanFuture)
            .thenAccept(ignored -> {
                int kills = killsFuture.join();
                int deaths = deathsFuture.join();
                long playtime = playtimeFuture.join();
                int streak = streakFuture.join();
                int highStreak = highStreakFuture.join();
                int points = pointsFuture.join();
                String clanTag = clanFuture.join();

                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (sender instanceof Player player && !player.isOnline()) {
                        return;
                    }
                    sendLines(sender, displayName, clanTag, deaths, kills, points, playtime, streak, highStreak);
                });
            }).exceptionally(throwable -> {
                plugin.getLogger().warning("Nie udalo sie wczytac profilu gracza " + displayName + ": " + throwable.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (sender instanceof Player player && !player.isOnline()) {
                        return;
                    }
                    sender.sendMessage(ColorUtils.error("Nie udało się wczytać danych gracza."));
                });
                return null;
            });
    }

    private FakeStats fakeStats(UUID uuid) {
        Random random = new Random(uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits());
        int kills = 40 + random.nextInt(460);
        int deaths = Math.max(1, (int) (kills * (0.55 + random.nextDouble() * 0.7)));
        int points = 800 + random.nextInt(1400);
        long playtime = (2L + random.nextInt(90)) * 3_600_000L + random.nextInt(60) * 60_000L;
        int high = 3 + random.nextInt(18);
        int streak = random.nextInt(Math.max(1, high + 1));
        return new FakeStats(kills, deaths, points, playtime, streak, high);
    }

    private void sendLines(CommandSender sender, String name, String clanTag, int deaths, int kills,
                           int points, long playtimeMillis, int killstreak, int highstreak) {
        PlayerCommandConfig config = config();
        if (config == null || config.getMessage() == null) {
            sender.sendMessage(ColorUtils.error("Brak konfiguracji komendy /gracz."));
            return;
        }
        String clan = (clanTag == null || clanTag.isBlank())
            ? nullToEmpty(config.getNoClan())
            : nullToEmpty(config.getClanFormat()).replace("{clan}", clanTag);

        for (String rawLine : config.getMessage()) {
            if (rawLine == null) {
                continue;
            }
            String line = rawLine
                .replace("{name}", name)
                .replace("{clan}", clan)
                .replace("{deaths}", String.valueOf(deaths))
                .replace("{kills}", String.valueOf(kills))
                .replace("{points}", String.valueOf(points))
                .replace("{playtime}", formatPlaytime(playtimeMillis))
                .replace("{killstreak}", String.valueOf(killstreak))
                .replace("{highstreak}", String.valueOf(highstreak));
            sender.sendMessage(ColorUtils.colorizeComponent(line));
        }
    }

    private PlayerCommandConfig config() {
        return plugin.getConfigManager().getPlayerCommandConfig();
    }

    private @Nullable ResolvedPlayer resolveLocal(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return new ResolvedPlayer(online.getUniqueId(), online.getName(), false);
        }

        String fakeName = SpooferBridge.resolveFakeName(name);
        if (fakeName != null) {
            UUID uuid = SpooferBridge.getFakeBotUuid(fakeName);
            return new ResolvedPlayer(
                uuid != null ? uuid : UUID.nameUUIDFromBytes(("HollySpoofer:" + fakeName.toLowerCase(Locale.ROOT)).getBytes()),
                fakeName,
                true
            );
        }

        for (OfflinePlayer offline : Bukkit.getOfflinePlayers()) {
            if (offline.getName() != null && offline.getName().equalsIgnoreCase(name)) {
                return new ResolvedPlayer(offline.getUniqueId(), offline.getName(), false);
            }
        }

        if (plugin.getJoinInfoManager() != null) {
            UUID uuid = plugin.getJoinInfoManager().getUuidByName(name);
            if (uuid != null) {
                String stored = plugin.getJoinInfoManager().getPlayerName(uuid);
                return new ResolvedPlayer(uuid, stored != null ? stored : name, false);
            }
        }

        if (plugin.getTopManager() != null) {
            UUID uuid = plugin.getTopManager().getUuidByName(name);
            if (uuid != null) {
                return new ResolvedPlayer(uuid, name, false);
            }
        }
        return null;
    }

    private CompletableFuture<ResolvedPlayer> lookupOffline(String name) {
        String fakeName = SpooferBridge.resolveFakeName(name);
        if (fakeName != null) {
            UUID uuid = SpooferBridge.getFakeBotUuid(fakeName);
            return CompletableFuture.completedFuture(new ResolvedPlayer(
                uuid != null ? uuid : UUID.nameUUIDFromBytes(("HollySpoofer:" + fakeName.toLowerCase(Locale.ROOT)).getBytes()),
                fakeName,
                true
            ));
        }

        if (plugin.getMongoManager() == null || !plugin.getMongoManager().isAvailable()) {
            return CompletableFuture.completedFuture(null);
        }

        Pattern exactName = Pattern.compile("^" + Pattern.quote(name) + "$", Pattern.CASE_INSENSITIVE);
        return findByName("join_info", "player_name", exactName, name)
            .thenCompose(found -> found != null ? CompletableFuture.completedFuture(found)
                : findByName(plugin.getConfigManager().getDatabaseConfig().getStatsCollection(), "name", exactName, name))
            .thenCompose(found -> found != null ? CompletableFuture.completedFuture(found)
                : findByName("player_elo", "name", exactName, name));
    }

    private CompletableFuture<ResolvedPlayer> findByName(String collection, String field, Pattern exactName, String fallbackName) {
        return plugin.getMongoManager().toFuture(
            plugin.getMongoManager().getCollection(collection)
                .find(Filters.regex(field, exactName))
                .first()
        ).thenApply(doc -> fromDocument(doc, fallbackName)).exceptionally(throwable -> null);
    }

    private @Nullable ResolvedPlayer fromDocument(Document doc, String fallbackName) {
        if (doc == null) {
            return null;
        }
        String uuidString = doc.getString("uuid");
        if (uuidString == null || uuidString.isBlank()) {
            uuidString = doc.getString("playerUuid");
        }
        if (uuidString == null || uuidString.isBlank()) {
            return null;
        }
        try {
            UUID uuid = UUID.fromString(uuidString);
            String storedName = doc.getString("player_name");
            if (storedName == null || storedName.isBlank()) {
                storedName = doc.getString("name");
            }
            if (storedName == null || storedName.isBlank()) {
                storedName = fallbackName;
            }
            return new ResolvedPlayer(uuid, storedName, false);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String formatPlaytime(long totalMillis) {
        long totalSeconds = Math.max(0L, totalMillis / 1000L);
        long days = totalSeconds / 86400L;
        long hours = (totalSeconds % 86400L) / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        if (days > 0L) {
            return days + "d " + hours + "h " + minutes + "m";
        }
        return hours + "h " + minutes + "m";
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("Holly.cmd.gracz") || args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> completions = new ArrayList<>();
        if (sender.hasPermission("Holly.cmd.gracz.reload") && "reload".startsWith(prefix)) {
            completions.add("reload");
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                completions.add(player.getName());
            }
        }
        for (String fake : SpooferBridge.getFakeNames()) {
            if (fake.toLowerCase(Locale.ROOT).startsWith(prefix) && !completions.contains(fake)) {
                completions.add(fake);
            }
        }
        return completions;
    }

    private record ResolvedPlayer(UUID uuid, String name, boolean fake) {
    }

    private record FakeStats(int kills, int deaths, int points, long playtimeMillis, int killstreak, int highstreak) {
    }
}
