package dev.grzybalson.managers;

import com.mongodb.client.model.Sorts;
import com.mongodb.reactivestreams.client.MongoCollection;
import dev.grzybalson.HollyCore;
import dev.grzybalson.config.configs.DatabaseConfig;
import dev.grzybalson.database.StatsDataManager;
import dev.grzybalson.utils.MoneyAmountUtil;
import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class TopManager {

    private static final long REFRESH_INTERVAL_TICKS = 20L * 30L;
    private static final int TOP_CACHE_LIMIT = 100;

    private final HollyCore plugin;
    private final ConcurrentHashMap<String, List<TopEntry>> topCache;
    private final ConcurrentHashMap<String, Map<String, Integer>> rankCache;
    private final ConcurrentHashMap<UUID, String> nameCache;
    private final ConcurrentHashMap<UUID, TopEntry> liveKillSnapshot;
    private final AtomicBoolean refreshInProgress;
    private final AtomicBoolean refreshQueued;
    private final AtomicBoolean leaderboardChanged;
    private BukkitTask updateTask;

    public TopManager(HollyCore plugin) {
        this.plugin = plugin;
        this.topCache = new ConcurrentHashMap<>();
        this.rankCache = new ConcurrentHashMap<>();
        this.nameCache = new ConcurrentHashMap<>();
        this.liveKillSnapshot = new ConcurrentHashMap<>();
        this.refreshInProgress = new AtomicBoolean(false);
        this.refreshQueued = new AtomicBoolean(false);
        this.leaderboardChanged = new AtomicBoolean(false);
    }

    public void start() {
        refreshAllTops();
        updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                refreshAllTops();
            }
        }.runTaskTimerAsynchronously(plugin, REFRESH_INTERVAL_TICKS, REFRESH_INTERVAL_TICKS);

        plugin.getLogger().info("TopManager started - refreshing every " + (REFRESH_INTERVAL_TICKS / 20L) + " seconds");
    }

    public void stop() {
        if (updateTask != null) {
            updateTask.cancel();
        }
    }

    public void requestRefresh() {
        if (!refreshQueued.compareAndSet(false, true)) {
            return;
        }

        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            refreshQueued.set(false);
            if (refreshInProgress.get()) {
                requestRefresh();
                return;
            }
            refreshAllTops();
        }, 40L);
    }

    private void refreshAllTops() {
        if (!refreshInProgress.compareAndSet(false, true)) {
            return;
        }
        leaderboardChanged.set(false);

        if (plugin.getMongoManager() == null || !plugin.getMongoManager().isAvailable()) {
            refreshInProgress.set(false);
            return;
        }

        DatabaseConfig databaseConfig = plugin.getConfigManager().getDatabaseConfig();
        CompletableFuture<Void> moneyFuture = refreshTop("money", databaseConfig.getEconomyCollection(), "balance");
        CompletableFuture<Void> walletFuture = refreshTop("wpln", "wallet", "balance");
        CompletableFuture<Void> playtimeFuture = refreshTop("playtime", databaseConfig.getStatsCollection(), "playtime");
        CompletableFuture<Void> eloFuture = refreshTop("elo", "player_elo", "elo");
        CompletableFuture<Void> killsFuture = refreshTop("kills", "achievements", "kills");
        CompletableFuture<Void> deathsFuture = refreshTop("deaths", "achievements", "deaths");
        CompletableFuture<Void> blocksFuture = refreshTop("blocks", "achievements", "blocksMined");
        CompletableFuture<Void> casesFuture = refreshTop("cases", "achievements", "casesOpened");
        CompletableFuture<Void> totemFuture = refreshTop("totem", "achievements", "totemPops");

        CompletableFuture.allOf(moneyFuture, walletFuture, playtimeFuture, eloFuture, killsFuture, deathsFuture, blocksFuture, casesFuture, totemFuture)
            .whenComplete((ignored, throwable) -> {
                if (throwable != null && !dev.grzybalson.database.MongoManager.isSkippedBecauseUnavailable(throwable)) {
                    plugin.getLogger().warning("Error refreshing tops: " + throwable.getMessage());
                }
                refreshInProgress.set(false);
                if (leaderboardChanged.get() && plugin.getHollyPlaceholderExpansion() != null) {
                    plugin.getHollyPlaceholderExpansion().clearRankCache();
                }
                if (leaderboardChanged.get() && plugin.getTablistManager() != null) {
                    plugin.getTablistManager().refreshLeaderboardRows();
                }
            });
    }

    private CompletableFuture<Void> refreshTop(String topName, String collectionName, String field) {
        MongoCollection<Document> collection = plugin.getMongoManager().getCollection(collectionName);
        return plugin.getMongoManager().toListFuture(
            collection.find()
            .sort(Sorts.descending(field))
            .limit(TOP_CACHE_LIMIT)
        ).thenCompose(documents -> {
            List<CompletableFuture<TopEntry>> entryFutures = new ArrayList<>();
            for (Document doc : documents) {
                String uuidString = doc.getString("uuid");
                String inlineName = sanitizeInlineName(doc.getString("name"));
                double numericValue = extractNumericValue(doc.get(field));
                if (topName.equals("elo")) {
                    numericValue = normalizeLegacyEloValue(doc, numericValue);
                }
                final double topValue = numericValue;
                entryFutures.add(resolveDisplayName(uuidString, inlineName)
                    .thenApply(name -> new TopEntry(name, topValue, buildIdentityKey(uuidString, name))));
            }

            if (entryFutures.isEmpty()) {
                if (topName.equals("playtime")) {
                    storeTop(topName, mergeLivePlaytime(List.of()));
                } else if (topName.equals("kills")) {
                    storeTop(topName, mergeLiveKills(List.of()));
                } else if (topName.equals("blocks")) {
                    storeTop(topName, mergeLiveBlocks(List.of()));
                } else if (topName.equals("cases")) {
                    storeTop(topName, mergeLiveCases(List.of()));
                } else if (topName.equals("totem")) {
                    storeTop(topName, mergeLiveTotems(List.of()));
                } else {
                    storeTop(topName, List.of());
                }
                return CompletableFuture.completedFuture(null);
            }

            return CompletableFuture.allOf(entryFutures.toArray(new CompletableFuture[0]))
                .thenAccept(ignored -> {
                    List<TopEntry> entries = entryFutures.stream()
                        .map(CompletableFuture::join)
                        .filter(entry -> entry != null && entry.hasIdentity())
                        .collect(Collectors.toList());

                    if (topName.equals("playtime")) {
                        entries = mergeLivePlaytime(entries);
                    } else if (topName.equals("kills")) {
                        entries = mergeLiveKills(entries);
                    } else if (topName.equals("blocks")) {
                        entries = mergeLiveBlocks(entries);
                    } else if (topName.equals("cases")) {
                        entries = mergeLiveCases(entries);
                    } else if (topName.equals("totem")) {
                        entries = mergeLiveTotems(entries);
                    }
                    storeTop(topName, entries);
                });
        }).exceptionally(throwable -> {
            if (!dev.grzybalson.database.MongoManager.isSkippedBecauseUnavailable(throwable)) {
                plugin.getLogger().warning("Error refreshing top " + topName + ": " + throwable.getMessage());
            }
            return null;
        });
    }

    private void storeTop(String topName, List<TopEntry> entries) {
        List<TopEntry> limitedEntries = entries.size() > TOP_CACHE_LIMIT
            ? List.copyOf(entries.subList(0, TOP_CACHE_LIMIT))
            : List.copyOf(entries);

        List<TopEntry> previousEntries = topCache.get(topName);
        boolean changed = !sameTopEntries(previousEntries, limitedEntries);

        Map<String, Integer> ranks = new ConcurrentHashMap<>();
        for (int i = 0; i < limitedEntries.size(); i++) {
            TopEntry entry = limitedEntries.get(i);
            ranks.put(entry.getIdentityKey(), i + 1);
        }

        topCache.put(topName, limitedEntries);
        rankCache.put(topName, ranks);
        if (changed) {
            leaderboardChanged.set(true);
        }
    }

    private boolean sameTopEntries(List<TopEntry> previous, List<TopEntry> current) {
        if (previous == current) {
            return true;
        }
        if (previous == null || current == null || previous.size() != current.size()) {
            return false;
        }
        for (int i = 0; i < previous.size(); i++) {
            TopEntry left = previous.get(i);
            TopEntry right = current.get(i);
            if (left == right) {
                continue;
            }
            if (left == null || right == null) {
                return false;
            }
            if (Double.compare(left.getValue(), right.getValue()) != 0) {
                return false;
            }
            if (!java.util.Objects.equals(left.getName(), right.getName())) {
                return false;
            }
            if (!java.util.Objects.equals(left.getIdentityKey(), right.getIdentityKey())) {
                return false;
            }
        }
        return true;
    }

    private List<TopEntry> mergeLivePlaytime(List<TopEntry> databaseEntries) {
        if (plugin.getStatsDataManager() == null) {
            return databaseEntries;
        }

        Map<String, TopEntry> merged = new LinkedHashMap<>();
        for (TopEntry entry : databaseEntries) {
            if (entry != null && entry.hasIdentity()) {
                merged.put(entry.getIdentityKey(), entry);
            }
        }

        for (Map.Entry<UUID, StatsDataManager.PlaytimeSnapshot> liveEntry : plugin.getStatsDataManager().getActivePlaytimeSnapshot().entrySet()) {
            UUID uuid = liveEntry.getKey();
            StatsDataManager.PlaytimeSnapshot snapshot = liveEntry.getValue();
            String name = sanitizeInlineName(snapshot.name());
            if (name == null || name.isBlank()) {
                name = nameCache.get(uuid);
            }
            if (name == null || name.isBlank()) {
                name = "---";
            }

            TopEntry liveTopEntry = new TopEntry(name, snapshot.playtime(), uuid.toString());
            TopEntry existing = merged.get(liveTopEntry.getIdentityKey());
            if (existing == null || liveTopEntry.getValue() >= existing.getValue()) {
                merged.put(liveTopEntry.getIdentityKey(), liveTopEntry);
            }
        }

        return merged.values().stream()
            .filter(TopEntry::hasIdentity)
            .sorted(Comparator.comparingDouble(TopEntry::getValue).reversed())
            .limit(TOP_CACHE_LIMIT)
            .collect(Collectors.toList());
    }

    private List<TopEntry> mergeLiveKills(List<TopEntry> databaseEntries) {
        Map<String, TopEntry> merged = new LinkedHashMap<>();
        for (TopEntry entry : databaseEntries) {
            if (entry != null && entry.hasIdentity()) {
                merged.put(entry.getIdentityKey(), entry);
            }
        }

        for (TopEntry liveTopEntry : liveKillSnapshot.values()) {
            if (liveTopEntry == null || !liveTopEntry.hasIdentity()) {
                continue;
            }
            TopEntry existing = merged.get(liveTopEntry.getIdentityKey());
            if (existing == null || liveTopEntry.getValue() >= existing.getValue()) {
                merged.put(liveTopEntry.getIdentityKey(), liveTopEntry);
            }
        }

        return merged.values().stream()
            .filter(TopEntry::hasIdentity)
            .sorted(Comparator.comparingDouble(TopEntry::getValue).reversed())
            .limit(TOP_CACHE_LIMIT)
            .collect(Collectors.toList());
    }

    private List<TopEntry> mergeLiveBlocks(List<TopEntry> databaseEntries) {
        return mergeLiveAchievementStat(databaseEntries, dev.grzybalson.modules.achievement.AchievementManager.PlayerAchievementData::getBlocksMined);
    }

    private List<TopEntry> mergeLiveCases(List<TopEntry> databaseEntries) {
        return mergeLiveAchievementStat(databaseEntries, dev.grzybalson.modules.achievement.AchievementManager.PlayerAchievementData::getCasesOpened);
    }

    private List<TopEntry> mergeLiveTotems(List<TopEntry> databaseEntries) {
        return mergeLiveAchievementStat(databaseEntries, data -> {
            int totems = data.getTotemPops();
            Player online = Bukkit.getPlayer(data.getUuid());
            if (online != null) {
                try {
                    totems = Math.max(totems, online.getStatistic(org.bukkit.Statistic.USE_ITEM, org.bukkit.Material.TOTEM_OF_UNDYING));
                } catch (Exception ignored) {}
            }
            return totems;
        });
    }

    private List<TopEntry> mergeLiveAchievementStat(List<TopEntry> databaseEntries, java.util.function.Function<dev.grzybalson.modules.achievement.AchievementManager.PlayerAchievementData, Integer> statExtractor) {
        if (plugin.getAchievementManager() == null) {
            return databaseEntries;
        }
        Map<String, TopEntry> merged = new LinkedHashMap<>();
        for (TopEntry entry : databaseEntries) {
            if (entry != null && entry.hasIdentity()) {
                merged.put(entry.getIdentityKey(), entry);
            }
        }

        for (Player online : Bukkit.getOnlinePlayers()) {
            var data = plugin.getAchievementManager().getCachedData(online.getUniqueId());
            if (data != null) {
                int value = statExtractor.apply(data);
                TopEntry liveEntry = new TopEntry(online.getName(), value, online.getUniqueId().toString());
                TopEntry existing = merged.get(liveEntry.getIdentityKey());
                if (existing == null || liveEntry.getValue() >= existing.getValue()) {
                    merged.put(liveEntry.getIdentityKey(), liveEntry);
                }
            }
        }

        return merged.values().stream()
            .filter(TopEntry::hasIdentity)
            .sorted(Comparator.comparingDouble(TopEntry::getValue).reversed())
            .limit(TOP_CACHE_LIMIT)
            .collect(Collectors.toList());
    }

    private CompletableFuture<String> resolveDisplayName(String uuidString, String inlineName) {
        if (inlineName != null && !inlineName.isBlank()) {
            return CompletableFuture.completedFuture(inlineName);
        }

        if (uuidString == null || uuidString.isBlank()) {
            return CompletableFuture.completedFuture("---");
        }

        UUID uuid;
        try {
            uuid = UUID.fromString(uuidString);
        } catch (IllegalArgumentException exception) {
            return CompletableFuture.completedFuture("---");
        }

        String cachedName = nameCache.get(uuid);
        if (cachedName != null && !cachedName.isBlank()) {
            return CompletableFuture.completedFuture(cachedName);
        }

        String statsCollection = plugin.getConfigManager().getDatabaseConfig().getStatsCollection();
        return plugin.getMongoManager().toFuture(
            plugin.getMongoManager().getCollection(statsCollection)
                .find(new Document("uuid", uuidString))
                .first()
        ).thenApply(document -> {
            String statsName = document != null ? sanitizeInlineName(document.getString("name")) : null;
            if (statsName != null && !statsName.isBlank()) {
                nameCache.put(uuid, statsName);
                return statsName;
            }

            return shortIdentity(uuidString);
        }).exceptionally(throwable -> {
            plugin.getLogger().warning("Error resolving player name for uuid " + uuidString + ": " + throwable.getMessage());
            return shortIdentity(uuidString);
        });
    }

    public void rememberPlayerName(UUID uuid, String name) {
        String sanitized = sanitizeInlineName(name);
        if (sanitized != null) {
            nameCache.put(uuid, sanitized);
        }
    }

    public UUID getUuidByName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        for (Map.Entry<UUID, String> entry : nameCache.entrySet()) {
            if (name.equalsIgnoreCase(entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }

    public void rememberPlayerKillStatistic(Player player) {
        if (player == null || !Bukkit.isPrimaryThread()) {
            return;
        }

        UUID uuid = player.getUniqueId();
        String name = player.getName();
        rememberPlayerName(uuid, name);

        int kills = Math.max(0, player.getStatistic(Statistic.PLAYER_KILLS));
        liveKillSnapshot.put(uuid, new TopEntry(name, kills, uuid.toString()));
    }

    private String sanitizeInlineName(String name) {
        if (name == null) {
            return null;
        }

        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        if (trimmed.matches("[0-9a-fA-F\\-]{8,}")) {
            return null;
        }

        return trimmed;
    }

    private double extractNumericValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return 0;
    }

    private double normalizeLegacyEloValue(Document doc, double value) {
        if (doc == null || Boolean.TRUE.equals(doc.getBoolean("initialized"))) {
            return value;
        }

        int startingElo = plugin.getModuleConfigManager().getEloSettingsConfig().getStartingElo();
        int minElo = plugin.getModuleConfigManager().getEloSettingsConfig().getMinElo();
        if (startingElo > minElo && value <= minElo) {
            return startingElo;
        }

        return value;
    }

    public List<TopEntry> getCachedTop(String type) {
        List<TopEntry> entries = topCache.get(type);
        return entries == null ? List.of() : entries;
    }

    public String getTopNick(String type, int place) {
        List<TopEntry> entries = topCache.get(type);
        if (entries == null || place < 1 || place > entries.size()) {
            return "---";
        }
        return entries.get(place - 1).getName();
    }

    public String getTopValue(String type, int place) {
        List<TopEntry> entries = topCache.get(type);
        if (entries == null || place < 1 || place > entries.size()) {
            return "0";
        }
        double value = entries.get(place - 1).getValue();

        if (type.equals("playtime")) {
            return formatPlaytime(value);
        }

        if (type.equals("elo") || type.equals("kills") || type.equals("deaths") || type.equals("blocks") || type.equals("cases") || type.equals("totem") || type.equals("totempop")) {
            return String.valueOf((int) Math.round(value));
        }

        return MoneyAmountUtil.formatCompact(value);
    }

    public String getTopRank(String type, int place) {
        List<TopEntry> entries = topCache.get(type);
        if (entries == null || place < 1 || place > entries.size()) {
            return String.valueOf(place);
        }
        return String.valueOf(place);
    }

    public int getCachedPlayerRank(String type, UUID uuid, double fallbackValue) {
        String key = uuid.toString();
        Map<String, Integer> ranks = rankCache.get(type);
        if (ranks != null) {
            Integer cachedRank = ranks.get(key);
            if (cachedRank != null) {
                return cachedRank;
            }
        }

        return estimatePlayerRank(type, uuid, fallbackValue);
    }

    public int estimatePlayerRank(String type, UUID uuid, double value) {
        List<TopEntry> entries = topCache.get(type);
        if (entries == null || entries.isEmpty()) {
            return value > 0 ? 1 : 0;
        }

        String key = uuid != null ? uuid.toString() : "";
        int rank = 1;
        for (TopEntry entry : entries) {
            if (entry == null || key.equals(entry.getIdentityKey())) {
                continue;
            }
            if (value < entry.getValue()) {
                rank++;
                continue;
            }
            break;
        }
        return rank;
    }

    public void invalidatePlayer(UUID uuid) {
        String key = uuid.toString();
        for (Map<String, Integer> ranks : rankCache.values()) {
            ranks.remove(key);
        }
    }

    private String buildIdentityKey(String uuidString, String name) {
        if (uuidString != null && !uuidString.isBlank()) {
            return uuidString;
        }
        if (name != null && !name.isBlank()) {
            return "name:" + name.toLowerCase(Locale.ROOT);
        }
        return "unknown";
    }

    private String shortIdentity(String uuidString) {
        if (uuidString == null || uuidString.isBlank()) {
            return "---";
        }
        return uuidString.length() > 8 ? uuidString.substring(0, 8) : uuidString;
    }

    private String formatPlaytime(double value) {
        long totalMillis = Math.max(0L, Math.round(value));
        long totalSeconds = totalMillis / 1000L;
        long days = totalSeconds / 86400L;
        long hours = (totalSeconds % 86400L) / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;

        if (days > 0) {
            return days + "d " + hours + "h " + minutes + "m";
        }
        return hours + "h " + minutes + "m";
    }

    public static class TopEntry {
        private final String name;
        private final double value;
        private final String identityKey;

        public TopEntry(String name, double value, String identityKey) {
            this.name = name;
            this.value = value;
            this.identityKey = identityKey;
        }

        public String getName() {
            return name;
        }

        public double getValue() {
            return value;
        }

        public String getIdentityKey() {
            return identityKey;
        }

        public boolean hasIdentity() {
            return identityKey != null && !identityKey.isBlank() && !"unknown".equals(identityKey);
        }
    }
}
