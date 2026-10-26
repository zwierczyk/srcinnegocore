package dev.grzybalson.placeholders;

import dev.grzybalson.HollyCore;
import dev.grzybalson.database.ClanDataManager;
import dev.grzybalson.config.HollyDataPaths;
import dev.grzybalson.utils.ArmorStarsUtil;
import dev.grzybalson.utils.MoneyAmountUtil;
import dev.grzybalson.utils.SpoofedPlayerProfile;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.clip.placeholderapi.expansion.Relational;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class HollyPlaceholderExpansion extends PlaceholderExpansion implements Relational, Listener {

    private static final int CLAN_TOP_CACHE_LIMIT = 1000;
    private static final long PLAYTIME_CACHE_MILLIS = 60_000L;
    private static final long STATISTIC_CACHE_MILLIS = 1_000L;
    private static final long SECTOR_CACHE_MILLIS = 30_000L;
    private static final long PLAYER_CLAN_REFRESH_RETRY_MILLIS = 10_000L;
    private static final long PLAYER_CLAN_REFRESH_LOG_MILLIS = 60_000L;

    private final HollyCore plugin;
    private final Map<UUID, CachedClanData> playerClanCache = new ConcurrentHashMap<>();
    private final Map<UUID, CachedStatisticData> playerStatisticCache = new ConcurrentHashMap<>();
    private final Map<UUID, String> balanceTextCache = new ConcurrentHashMap<>();
    private final Map<UUID, String> eloTextCache = new ConcurrentHashMap<>();
    private final Map<UUID, String> rankTextCache = new ConcurrentHashMap<>();
    private final Map<String, CachedText> tabTopTextCache = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> playerClanRefreshes = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> playerClanRefreshRetryAt = new ConcurrentHashMap<>();
    private final Map<UUID, Long> playerClanRefreshLogAt = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> onlinePlayerIds = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean topClanRefreshQueued = new AtomicBoolean(false);
    private volatile List<ClanDataManager.Clan> topClansCache = List.of();
    private volatile Map<String, Integer> topClanPositionsCache = Map.of();
    private final File sectorsFile;
    private final File tablistFile;
    private volatile String sectorNameCache;
    private volatile long sectorNameCacheAt;
    private volatile TabTopStyle tabTopStyle = TabTopStyle.defaults();
    private volatile long tabTopStyleLoadedAt;
    private volatile long tabTopStyleLastModified = -1L;
    private static final long TAB_TOP_STYLE_CHECK_MILLIS = 30_000L;

    public HollyPlaceholderExpansion(HollyCore plugin) {
        this.plugin = plugin;
        this.sectorsFile = new File(HollyDataPaths.getAddonFolder(plugin, "commands"), "sectors.yml");
        this.tablistFile = new File(plugin.getDataFolder(), "addons/tablist/tablist.yml");
        refreshTopClans();
        for (Player player : Bukkit.getOnlinePlayers()) {
            onlinePlayerIds.add(player.getUniqueId());
            refreshPlayerCaches(player);
        }
    }

    public void refreshTopClans() {
        if (plugin.getClanManager() == null) {
            topClansCache = List.of();
            topClanPositionsCache = Map.of();
            return;
        }

        if (!topClanRefreshQueued.compareAndSet(false, true)) {
            return;
        }

        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            topClanRefreshQueued.set(false);
            plugin.getClanManager().getTopClans(CLAN_TOP_CACHE_LIMIT)
                .thenAccept(clans -> {
                    List<ClanDataManager.Clan> cachedClans = clans.stream()
                        .filter(this::isDisplayableClan)
                        .toList();
                    topClansCache = cachedClans;
                    topClanPositionsCache = buildClanPositionCache(cachedClans);
                    clearTabTopCache();
                    if (plugin.getTablistManager() != null) {
                        plugin.getTablistManager().refreshLeaderboardRows();
                    }
                })
                .exceptionally(throwable -> {
                    plugin.getLogger().warning("Nie udalo sie odswiezyc cache top klanow: " + throwable.getMessage());
                    return null;
                });
        }, 20L);
    }

    public void refreshPlayerClan(UUID uuid) {
        if (plugin.getClanManager() == null) {
            playerClanCache.put(uuid, new CachedClanData(null, null));
            return;
        }

        long now = System.currentTimeMillis();
        Long retryAt = playerClanRefreshRetryAt.get(uuid);
        if (retryAt != null) {
            if (retryAt > now) {
                return;
            }
            playerClanRefreshRetryAt.remove(uuid, retryAt);
        }

        if (!playerClanRefreshes.add(uuid)) {
            return;
        }

        plugin.getClanManager().getPlayerClan(uuid).thenCompose(clanTag -> {
            if (clanTag == null) {
                playerClanCache.put(uuid, new CachedClanData(null, null));
                return java.util.concurrent.CompletableFuture.completedFuture(null);
            }

            return plugin.getClanManager().getClan(clanTag).thenAccept(clan -> {
                if (clan == null) {
                    playerClanCache.put(uuid, new CachedClanData(null, null));
                    return;
                }
                playerClanCache.put(uuid, new CachedClanData(clanTag, clan));
            });
        }).whenComplete((ignored, throwable) -> {
            playerClanRefreshes.remove(uuid);
            if (throwable == null) {
                playerClanRefreshRetryAt.remove(uuid);
                if (plugin.getTablistManager() != null) {
                    plugin.getTablistManager().refreshPlayerIdentity(uuid);
                }
                return;
            }

            long failedAt = System.currentTimeMillis();
            playerClanRefreshRetryAt.put(uuid, failedAt + PLAYER_CLAN_REFRESH_RETRY_MILLIS);
            if (shouldLogPlayerClanRefreshFailure(uuid, failedAt)) {
                plugin.getLogger().warning("Nie udalo sie odswiezyc cache klanu gracza " + uuid + ": " + throwable.getMessage());
            }
        });
    }

    public void updateBalance(UUID uuid, double balance) {
        balanceTextCache.put(uuid, MoneyAmountUtil.formatCompact(balance));
    }

    public void invalidateBalance(UUID uuid) {
        balanceTextCache.remove(uuid);
    }

    public void updateElo(UUID uuid, int elo) {
        eloTextCache.put(uuid, String.valueOf(elo));
        rankTextCache.remove(uuid);
    }

    public void invalidatePlayer(UUID uuid) {
        playerClanCache.remove(uuid);
        playerClanRefreshes.remove(uuid);
        playerClanRefreshRetryAt.remove(uuid);
        playerClanRefreshLogAt.remove(uuid);
        playerStatisticCache.remove(uuid);
        balanceTextCache.remove(uuid);
        eloTextCache.remove(uuid);
        rankTextCache.remove(uuid);
    }

    public void invalidateAll() {
        playerClanCache.clear();
        playerClanRefreshes.clear();
        playerClanRefreshRetryAt.clear();
        playerClanRefreshLogAt.clear();
        playerStatisticCache.clear();
        balanceTextCache.clear();
        eloTextCache.clear();
        rankTextCache.clear();
        tabTopTextCache.clear();
    }

    private boolean shouldLogPlayerClanRefreshFailure(UUID uuid, long now) {
        Long lastLogAt = playerClanRefreshLogAt.get(uuid);
        if (lastLogAt != null && now - lastLogAt < PLAYER_CLAN_REFRESH_LOG_MILLIS) {
            return false;
        }

        playerClanRefreshLogAt.put(uuid, now);
        return true;
    }

    public void clearRankCache() {
        rankTextCache.clear();
        clearTabTopCache();
    }

    public void clearTabTopCache() {
        tabTopTextCache.clear();
    }

    private void refreshPlayerCaches(Player player) {
        UUID uuid = player.getUniqueId();
        refreshPlayerClan(uuid);
        updateBalance(uuid, plugin.getEconomy().getBalance(uuid));
        if (plugin.getEloManager() != null) {
            updateElo(uuid, plugin.getEloManager().getElo(uuid));
        }
        refreshStatisticCache(player);
    }

    private CachedClanData getCachedClanData(UUID uuid) {
        if (uuid == null) {
            return new CachedClanData(null, null);
        }
        if (plugin.getClanManager() != null) {
            String liveTag = plugin.getClanManager().getCachedPlayerClan(uuid);
            if (liveTag != null) {
                ClanDataManager.Clan liveClan = plugin.getClanManager().getCachedClan(liveTag);
                CachedClanData live = new CachedClanData(liveTag, liveClan);
                playerClanCache.put(uuid, live);
                return live;
            }
        }
        CachedClanData cached = playerClanCache.get(uuid);
        if (cached == null) {
            refreshPlayerClan(uuid);
            return new CachedClanData(null, null);
        }
        return cached;
    }

    private Map<String, Integer> buildClanPositionCache(List<ClanDataManager.Clan> clans) {
        Map<String, Integer> positions = new ConcurrentHashMap<>();
        int rank = 1;

        for (ClanDataManager.Clan clan : clans) {
            if (!isDisplayableClan(clan)) {
                continue;
            }

            positions.put(clan.getTag().toUpperCase(Locale.ROOT), rank);
            rank++;
        }

        return Map.copyOf(positions);
    }

    private String getClanPositionText(CachedClanData cached) {
        String notRankedText = plugin.getConfigManager().getClanConfig().getClanNotRankedText();
        if (cached.clanTag() == null || cached.clan() == null) {
            return notRankedText;
        }

        ClanDataManager.Clan clan = cached.clan();
        if (!isDisplayableClan(clan)) {
            return notRankedText;
        }

        if (topClanPositionsCache.isEmpty()) {
            refreshTopClans();
        }

        Integer position = topClanPositionsCache.get(cached.clanTag().toUpperCase(Locale.ROOT));
        return position != null ? String.valueOf(position) : notRankedText;
    }

    private record CachedClanData(String clanTag, ClanDataManager.Clan clan) {}

    private CachedStatisticData getCachedStatisticData(OfflinePlayer player, boolean allowPlaytimeRefresh) {
        UUID uuid = player.getUniqueId();
        CachedStatisticData cached = playerStatisticCache.get(uuid);
        long now = System.currentTimeMillis();
        long maxAge = allowPlaytimeRefresh ? PLAYTIME_CACHE_MILLIS : STATISTIC_CACHE_MILLIS;

        if (cached == null || (now - cached.cachedAt() >= maxAge)) {
            cached = buildStatisticCache(player, now);
            playerStatisticCache.put(uuid, cached);
        }

        return cached;
    }

    private void refreshStatisticCache(OfflinePlayer player) {
        playerStatisticCache.put(player.getUniqueId(), buildStatisticCache(player, System.currentTimeMillis()));
    }

    private CachedStatisticData buildStatisticCache(OfflinePlayer player, long now) {
        if (player == null) {
            return new CachedStatisticData(0, 0, 0L, now);
        }

        UUID uuid = player.getUniqueId();
        int kills = getPlayerStatistic(player, Statistic.PLAYER_KILLS);
        int deaths = getPlayerStatistic(player, Statistic.DEATHS);
        long playtimeMillis = 0L;

        if (plugin.getAchievementManager() != null) {
            dev.grzybalson.modules.achievement.AchievementManager.PlayerAchievementData achData = plugin.getAchievementManager().getCachedData(uuid);
            if (achData != null) {
                kills = achData.getKills();
                deaths = achData.getDeaths();
            }
        }

        if (plugin.getStatsDataManager() != null) {
            dev.grzybalson.database.StatsDataManager.PlayerStats stats = plugin.getStatsDataManager().getCachedStats(uuid);
            if (stats != null) {
                playtimeMillis = stats.getTotalPlaytime();
            }
        }

        return new CachedStatisticData(kills, deaths, playtimeMillis, now);
    }

    private record CachedStatisticData(int kills, int deaths, long playtimeMillis, long cachedAt) {}
    private record CachedText(String value, long expiresAt) {}

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        onlinePlayerIds.add(player.getUniqueId());
        long delay = 40L + Math.floorMod(player.getUniqueId().hashCode(), 40);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                refreshPlayerCaches(player);
            }
        }, delay);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        onlinePlayerIds.remove(uuid);
        invalidatePlayer(uuid);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        Bukkit.getScheduler().runTask(plugin, () -> {
            refreshStatisticCache(victim);
            if (killer != null && !killer.equals(victim)) {
                refreshStatisticCache(killer);
            }
            if (plugin.getTopManager() != null) {
                plugin.getTopManager().requestRefresh();
            }
        });
    }

    @Override
    public @NotNull String getIdentifier() {
        return "holly";
    }

    @Override
    public @NotNull String getAuthor() {
        return "HollyCore";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        return onRequest(player, params);
    }

    /** Placeholdery Holly dla botów spoofera — ten sam wynik co u gracza z ekwipunkiem bota. */
    public @Nullable String onRequestForSpoofedProfile(@NotNull dev.grzybalson.utils.SpoofedPlayerProfile profile, @NotNull String params) {
        String normalizedParams = params.toLowerCase(Locale.ROOT);

        if (normalizedParams.equals("armorstars") || normalizedParams.equals("armor_stars") || normalizedParams.equals("stars") || normalizedParams.equals("eqstars")
            || normalizedParams.startsWith("armorstars_") || normalizedParams.startsWith("armor_stars_") || normalizedParams.startsWith("stars_")) {
            if (!plugin.isAddonEnabled("armorstars")) {
                return "";
            }
            return ArmorStarsUtil.calculateStarsFromItems(
                plugin,
                profile.helmet(),
                profile.chestplate(),
                profile.leggings(),
                profile.boots(),
                profile.weapon(),
                params
            );
        }

        if (params.equalsIgnoreCase("nick") || params.equalsIgnoreCase("name") || params.equalsIgnoreCase("player_name")) {
            return profile.name();
        }

        if (params.equalsIgnoreCase("name_color") || params.equalsIgnoreCase("rank_name_color")
            || params.equalsIgnoreCase("core_name_color") || params.equalsIgnoreCase("holly_name_color")
            || params.equalsIgnoreCase("namecolor") || params.equalsIgnoreCase("ranknamecolor")
            || params.equalsIgnoreCase("holly_rank_name_color")) {
            return profile.nameColor() != null ? profile.nameColor() : "&7";
        }

        if (params.equalsIgnoreCase("kills") || params.equalsIgnoreCase("deaths")
            || params.equalsIgnoreCase("balance") || params.equalsIgnoreCase("money")) {
            return "0";
        }

        if (params.equalsIgnoreCase("points") || params.equalsIgnoreCase("ranking")
            || params.equalsIgnoreCase("position") || params.equalsIgnoreCase("elo")
            || params.equalsIgnoreCase("player_elo")) {
            if (plugin.getEloManager() != null && profile.uuid() != null) {
                plugin.getEloManager().ensureSpoofedElo(profile.uuid());
                if (params.equalsIgnoreCase("position") || params.equalsIgnoreCase("rank")
                    || params.equalsIgnoreCase("place") || params.equalsIgnoreCase("postion")
                    || params.equalsIgnoreCase("ranking_position") || params.equalsIgnoreCase("ranking_place")) {
                    return String.valueOf(plugin.getEloManager().getRank(profile.uuid()));
                }
                return String.valueOf(plugin.getEloManager().getElo(profile.uuid()));
            }
            return "0";
        }

        if (params.equalsIgnoreCase("vanish") || params.equalsIgnoreCase("is_vanished")) {
            return "";
        }

        return null;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        String normalizedParams = params.toLowerCase(Locale.ROOT);

        if (normalizedParams.equals("armorstars") || normalizedParams.equals("armor_stars") || normalizedParams.equals("stars") || normalizedParams.equals("eqstars")
            || normalizedParams.startsWith("armorstars_") || normalizedParams.startsWith("armor_stars_") || normalizedParams.startsWith("stars_")) {
            if (!plugin.isAddonEnabled("armorstars")) {
                return "";
            }
            if (player == null || !player.isOnline() || player.getPlayer() == null) {
                return "0";
            }
            return ArmorStarsUtil.calculateStarsPlaceholder(plugin, player.getPlayer(), params);
        }

        if (params.equalsIgnoreCase("clearlag_time") || params.equalsIgnoreCase("clearlag_countdown") || params.equalsIgnoreCase("clearlag")) {
            if (plugin.getClearLagManager() == null) return "0s";
            int seconds = plugin.getClearLagManager().getSecondsUntilClear();
            return formatTime(seconds);
        }

        if (params.equalsIgnoreCase("boss_next") || params.equalsIgnoreCase("boss_hour")) {
            if (plugin.getBossManager() == null) return "---";
            return plugin.getBossManager().getNextBossHour();
        }
        if (params.equalsIgnoreCase("boss_time") || params.equalsIgnoreCase("boss_countdown")) {
            if (plugin.getBossManager() == null) return "---";
            return plugin.getBossManager().getNextBossTimeFormatted();
        }
        if (params.equalsIgnoreCase("boss_name") || params.equalsIgnoreCase("boss_next_name")) {
            if (plugin.getBossManager() == null) return "---";
            return plugin.getBossManager().getNextBossName();
        }

        if (params.equalsIgnoreCase("balance") || params.equalsIgnoreCase("money")) {
            if (player == null) return "0";
            UUID uuid = player.getUniqueId();
            return balanceTextCache.computeIfAbsent(uuid, key -> MoneyAmountUtil.formatCompact(plugin.getEconomy().getBalance(key)));
        }

        if (params.equalsIgnoreCase("patron")) {
            if (player == null || plugin.getPatronManager() == null) return "";
            return plugin.getPatronManager().getPlayerPatronDisplayName(player.getUniqueId());
        }

        if (params.equalsIgnoreCase("rotation_customitem") || params.equalsIgnoreCase("customitem_rotation")) {
            if (plugin.getCaseManager() == null) return "0s";
            return plugin.getCaseManager().getDefaultRewardRotationCountdown();
        }

        if (params.equalsIgnoreCase("nick") || params.equalsIgnoreCase("name") || params.equalsIgnoreCase("player_name")) {
            return player != null && player.getName() != null ? player.getName() : "---";
        }

        if (params.equalsIgnoreCase("name_color") || params.equalsIgnoreCase("rank_name_color")
            || params.equalsIgnoreCase("core_name_color") || params.equalsIgnoreCase("holly_name_color")
            || params.equalsIgnoreCase("namecolor") || params.equalsIgnoreCase("ranknamecolor")) {
            if (player != null && player.isOnline() && player.getPlayer() != null && plugin.getTablistManager() != null) {
                return plugin.getTablistManager().getRankNameColor(player.getPlayer());
            }
            return "&7";
        }

        if (params.equalsIgnoreCase("level") || params.equalsIgnoreCase("player_level")) {
            if (player == null || !player.isOnline() || player.getPlayer() == null) return "0";
            return String.valueOf(player.getPlayer().getLevel());
        }

        if (params.equalsIgnoreCase("sector") || params.equalsIgnoreCase("server") || params.equalsIgnoreCase("server_name")) {
            return getSectorName(player);
        }

        if (params.equalsIgnoreCase("hearts")) {
            return getHeartsPlaceholder(player);
        }

        if (normalizedParams.equals("joininfo") || normalizedParams.equals("join_info")
            || normalizedParams.equals("joinnumber") || normalizedParams.equals("join_number")) {
            if (plugin.getJoinInfoManager() == null) return "#…";
            if (player == null) return "#…";
            return plugin.getJoinInfoManager().getJoinInfo(player.getUniqueId(), player.getName());
        }

        if (normalizedParams.equals("joininfo_raw") || normalizedParams.equals("join_info_raw")
            || normalizedParams.equals("joinnumber_raw") || normalizedParams.equals("join_number_raw")) {
            if (plugin.getJoinInfoManager() == null) return "0";
            if (player == null) return "0";
            return String.valueOf(plugin.getJoinInfoManager().getJoinNumber(player.getUniqueId(), player.getName()));
        }

        if (normalizedParams.equals("joininfo_total") || normalizedParams.equals("join_info_total")
            || normalizedParams.equals("jointotal") || normalizedParams.equals("join_total")) {
            if (plugin.getJoinInfoManager() == null) return "1";
            return String.valueOf(plugin.getJoinInfoManager().getTotalJoinedUsers());
        }

        if (normalizedParams.startsWith("top_clan_")) {
            if (topClansCache.isEmpty()) {
                refreshTopClans();
            }
            String[] parts = params.split("_");
            if (parts.length >= 4) {
                String field = parts[2];
                int place;
                try {
                    place = Integer.parseInt(parts[3]);
                } catch (NumberFormatException e) {
                    return "---";
                }

                if (place < 1 || place > topClansCache.size()) return "---";
                var clan = topClansCache.get(place - 1);
                if (!isDisplayableClan(clan)) return "---";

                if (field.equalsIgnoreCase("tag") || field.equalsIgnoreCase("name")) {
                    return clan.getTag();
                } else if (field.equalsIgnoreCase("elo") || field.equalsIgnoreCase("points")) {
                    return String.valueOf(clan.getElo());
                }
            }
            return "---";
        }

        if (normalizedParams.startsWith("tabtopuser_") || normalizedParams.startsWith("tabtopclan_")) {
            return resolveTabTopPlaceholder(params);
        }

        if (normalizedParams.startsWith("totempop_topvalue_") || normalizedParams.startsWith("totempop_top_value_")) {
            String sub = normalizedParams.startsWith("totempop_top_value_") ? normalizedParams.substring("totempop_top_value_".length()) : normalizedParams.substring("totempop_topvalue_".length());
            try {
                int place = Integer.parseInt(sub);
                if (plugin.getTopManager() != null) {
                    return plugin.getTopManager().getTopValue("totem", place);
                }
            } catch (NumberFormatException ignored) {}
            return "0";
        }

        if (normalizedParams.startsWith("totempop_top_")) {
            String sub = normalizedParams.substring("totempop_top_".length());
            try {
                int place = Integer.parseInt(sub);
                if (plugin.getTopManager() != null) {
                    return plugin.getTopManager().getTopNick("totem", place);
                }
            } catch (NumberFormatException ignored) {}
            return "---";
        }

        if (normalizedParams.startsWith("topclanvalue_") || normalizedParams.startsWith("top_clan_value_") || normalizedParams.startsWith("top_clanvalue_")) {
            if (topClansCache.isEmpty()) {
                refreshTopClans();
            }
            String[] parts = params.split("_");
            int place = 1;
            String type = "points";
            if (parts.length >= 3) {
                type = parts[1].toLowerCase(Locale.ROOT);
                try {
                    place = Integer.parseInt(parts[2]);
                } catch (NumberFormatException ignored) {}
            } else if (parts.length == 2) {
                try {
                    place = Integer.parseInt(parts[1]);
                } catch (NumberFormatException ignored) {}
            }

            if (place < 1 || place > topClansCache.size()) return "0";
            var clan = topClansCache.get(place - 1);
            if (!isDisplayableClan(clan)) return "0";

            if (type.equalsIgnoreCase("kills")) {
                return String.valueOf(clan.getKills());
            } else if (type.equalsIgnoreCase("deaths")) {
                return String.valueOf(clan.getDeaths());
            }
            return String.valueOf(clan.getElo());
        }

        if (normalizedParams.startsWith("topclan_") || normalizedParams.startsWith("top_clan_")) {
            if (topClansCache.isEmpty()) {
                refreshTopClans();
            }
            String[] parts = params.split("_");
            int place = 1;
            if (parts.length >= 3) {
                try {
                    place = Integer.parseInt(parts[2]);
                } catch (NumberFormatException ignored) {
                    try { place = Integer.parseInt(parts[1]); } catch (NumberFormatException ignored2) {}
                }
            } else if (parts.length == 2) {
                try {
                    place = Integer.parseInt(parts[1]);
                } catch (NumberFormatException ignored) {}
            }

            if (place < 1 || place > topClansCache.size()) return "---";
            var clan = topClansCache.get(place - 1);
            if (!isDisplayableClan(clan)) return "---";
            return clan.getTag();
        }

        if (normalizedParams.startsWith("topvalue_") || normalizedParams.startsWith("topval_") || normalizedParams.startsWith("top_value_")) {
            String sub = normalizedParams.startsWith("top_value_") ? normalizedParams.substring("top_value_".length()) :
                        (normalizedParams.startsWith("topvalue_") ? normalizedParams.substring("topvalue_".length()) : normalizedParams.substring("topval_".length()));
            String[] parts = sub.split("_");
            if (parts.length >= 2) {
                String type = normalizeTopType(parts[0]);
                try {
                    int place = Integer.parseInt(parts[1]);
                    if (plugin.getTopManager() != null) {
                        return plugin.getTopManager().getTopValue(type, place);
                    }
                } catch (NumberFormatException ignored) {}
            }
            return "0";
        }

        if (normalizedParams.startsWith("top_nick_") || normalizedParams.startsWith("topnick_")) {
            String sub = normalizedParams.startsWith("top_nick_") ? normalizedParams.substring("top_nick_".length()) : normalizedParams.substring("topnick_".length());
            String[] parts = sub.split("_");
            if (parts.length >= 2) {
                String type = normalizeTopType(parts[0]);
                try {
                    int place = Integer.parseInt(parts[1]);
                    if (plugin.getTopManager() != null) {
                        return plugin.getTopManager().getTopNick(type, place);
                    }
                } catch (NumberFormatException ignored) {}
            }
            return "---";
        }

        if (normalizedParams.startsWith("top_")) {
            String[] parts = params.split("_");
            if (parts.length >= 4) {
                String type = normalizeTopType(parts[1]);
                String field = parts[2];
                int place;
                try {
                    place = Integer.parseInt(parts[3]);
                } catch (NumberFormatException e) {
                    return "---";
                }

                if (field.equalsIgnoreCase("nick") || field.equalsIgnoreCase("name")) {
                    if (plugin.getTopManager() != null) return plugin.getTopManager().getTopNick(type, place);
                } else if (field.equalsIgnoreCase("value") || field.equalsIgnoreCase("amount") || field.equalsIgnoreCase("val")) {
                    if (plugin.getTopManager() != null) return plugin.getTopManager().getTopValue(type, place);
                } else if (field.equalsIgnoreCase("rank") || field.equalsIgnoreCase("place") || field.equalsIgnoreCase("position")) {
                    if (plugin.getTopManager() != null) return plugin.getTopManager().getTopRank(type, place);
                }
            } else if (parts.length == 3) {
                String type = normalizeTopType(parts[1]);
                try {
                    int place = Integer.parseInt(parts[2]);
                    if (plugin.getTopManager() != null) {
                        return plugin.getTopManager().getTopNick(type, place);
                    }
                } catch (NumberFormatException ignored) {}
            }
            return "---";
        }

        if (params.equalsIgnoreCase("clan") || params.equalsIgnoreCase("clan_tag")) {
            if (player == null) return "";
            CachedClanData cached = getCachedClanData(player.getUniqueId());
            return cached.clanTag() != null ? "[" + cached.clanTag() + "]" : "";
        }

        if (params.equalsIgnoreCase("clan_has") || params.equalsIgnoreCase("in_clan") || params.equalsIgnoreCase("has_clan")) {
            if (player == null) return "false";
            CachedClanData cached = getCachedClanData(player.getUniqueId());
            return cached.clanTag() != null ? "true" : "false";
        }

        if (params.equalsIgnoreCase("clan_tag_colored") || params.equalsIgnoreCase("clan_colored")) {
            if (player == null) return "";
            CachedClanData cached = getCachedClanData(player.getUniqueId());
            if (cached.clanTag() == null) return "";
            return "&a[" + cached.clanTag() + "]";
        }

        if (params.equalsIgnoreCase("clan_tag_enemy") || params.equalsIgnoreCase("clan_enemy")) {
            if (player == null) return "";
            CachedClanData cached = getCachedClanData(player.getUniqueId());
            if (cached.clanTag() == null) return "";
            return "&c[" + cached.clanTag() + "]";
        }

        if (params.equalsIgnoreCase("kills") || params.equalsIgnoreCase("player_kills")
            || params.equalsIgnoreCase("core_kills") || params.equalsIgnoreCase("holly_kills")
            || params.equalsIgnoreCase("holly_core_kills") || params.equalsIgnoreCase("core_player_kills")) {
            if (player == null) return "0";
            return String.valueOf(getCachedStatisticData(player, false).kills());
        }

        if (params.equalsIgnoreCase("deaths") || params.equalsIgnoreCase("player_deaths")
            || params.equalsIgnoreCase("core_deaths") || params.equalsIgnoreCase("holly_deaths")
            || params.equalsIgnoreCase("holly_core_deaths") || params.equalsIgnoreCase("core_player_deaths")) {
            if (player == null) return "0";
            return String.valueOf(getCachedStatisticData(player, false).deaths());
        }

        if (params.equalsIgnoreCase("killstreak") || params.equalsIgnoreCase("streak") || params.equalsIgnoreCase("seria")) {
            if (player == null || plugin.getKillstreakManager() == null) return "0";
            return String.valueOf(plugin.getKillstreakManager().getCachedKillstreak(player.getUniqueId()));
        }

        if (params.equalsIgnoreCase("playtime") || params.equalsIgnoreCase("time") || params.equalsIgnoreCase("player_time")
            || params.equalsIgnoreCase("player_playtime")) {
            if (player == null) return "0h 0m";
            return formatPlaytime(getCachedStatisticData(player, true).playtimeMillis());
        }

        if (params.equalsIgnoreCase("playtime_seconds") || params.equalsIgnoreCase("time_seconds")
            || params.equalsIgnoreCase("player_playtime_seconds") || params.equalsIgnoreCase("player_time_seconds")) {
            if (player == null) return "0";
            return String.valueOf(getCachedStatisticData(player, true).playtimeMillis() / 1000L);
        }

        if (params.equalsIgnoreCase("playtime_minutes") || params.equalsIgnoreCase("time_minutes")
            || params.equalsIgnoreCase("player_playtime_minutes") || params.equalsIgnoreCase("player_time_minutes")) {
            if (player == null) return "0";
            return String.valueOf(getCachedStatisticData(player, true).playtimeMillis() / 60000L);
        }

        if (params.equalsIgnoreCase("playtime_hours") || params.equalsIgnoreCase("time_hours")
            || params.equalsIgnoreCase("player_playtime_hours") || params.equalsIgnoreCase("player_time_hours")) {
            if (player == null) return "0";
            return String.valueOf(getCachedStatisticData(player, true).playtimeMillis() / 3600000L);
        }

        if (params.startsWith("afk_") || params.equalsIgnoreCase("afk")) {
            if (player == null || !player.isOnline() || plugin.getAfkManager() == null) {
                if (params.equalsIgnoreCase("afk_in_zone")) return "false";
                if (params.contains("chance")) return "0%";
                if (params.contains("seconds")) return "0";
                if (params.contains("time") || params.contains("timer")) return "0s";
                return "";
            }

            Player onlinePlayer = player.getPlayer();
            var afkManager = plugin.getAfkManager();

            if (params.equalsIgnoreCase("afk_in_zone")) {
                return afkManager.isInAfkZone(onlinePlayer) ? "true" : "false";
            }

            if (params.equalsIgnoreCase("afk_zone")) {
                return afkManager.getZoneName(onlinePlayer);
            }

            if (params.equalsIgnoreCase("afk_time") || params.equalsIgnoreCase("afk_timer") || params.equalsIgnoreCase("afk_time_formatted")) {
                int seconds = afkManager.getRemainingSeconds(onlinePlayer);
                return formatTime(seconds);
            }

            if (params.equalsIgnoreCase("afk_time_seconds") || params.equalsIgnoreCase("afk_seconds")) {
                return String.valueOf(afkManager.getRemainingSeconds(onlinePlayer));
            }

            if (params.equalsIgnoreCase("afk_chance")) {
                return afkManager.getItemChance(onlinePlayer) + "%";
            }

            if (params.equalsIgnoreCase("afk_name")) {
                return afkManager.getDisplayName(onlinePlayer);
            }

            if (params.startsWith("afk_time_seconds_")) {
                String zoneName = params.substring("afk_time_seconds_".length());
                return String.valueOf(afkManager.getRemainingSeconds(onlinePlayer, zoneName));
            }
            if (params.startsWith("afk_time_") || params.startsWith("afk_timer_")) {
                String prefix = params.startsWith("afk_time_") ? "afk_time_" : "afk_timer_";
                String zoneName = params.substring(prefix.length());
                int seconds = afkManager.getRemainingSeconds(onlinePlayer, zoneName);
                return formatTime(seconds);
            }
            if (params.startsWith("afk_chance_")) {
                String zoneName = params.substring("afk_chance_".length());
                return afkManager.getItemChance(onlinePlayer, zoneName) + "%";
            }
            if (params.startsWith("afk_name_")) {
                String zoneName = params.substring("afk_name_".length());
                return afkManager.getDisplayName(onlinePlayer, zoneName);
            }
        }

        if (params.equalsIgnoreCase("clan_kills")) {
            if (player == null) return "0";
            CachedClanData cached = getCachedClanData(player.getUniqueId());
            return cached.clan() != null ? String.valueOf(cached.clan().getKills()) : "0";
        }

        if (params.equalsIgnoreCase("clan_deaths")) {
            if (player == null) return "0";
            CachedClanData cached = getCachedClanData(player.getUniqueId());
            return cached.clan() != null ? String.valueOf(cached.clan().getDeaths()) : "0";
        }

        if (params.equalsIgnoreCase("clan_elo") || params.equalsIgnoreCase("clan_points")) {
            if (player == null) return "0";
            CachedClanData cached = getCachedClanData(player.getUniqueId());
            return cached.clan() != null ? String.valueOf(cached.clan().getElo()) : "0";
        }

        if (params.equalsIgnoreCase("clan_members") || params.equalsIgnoreCase("clan_member_count")) {
            if (player == null) return "0";
            CachedClanData cached = getCachedClanData(player.getUniqueId());
            return cached.clan() != null ? String.valueOf(cached.clan().getMembers().size()) : "0";
        }

        if (params.equalsIgnoreCase("clan_online") || params.equalsIgnoreCase("clan_online_members")) {
            if (player == null) return "0";
            CachedClanData cached = getCachedClanData(player.getUniqueId());
            return cached.clan() != null ? String.valueOf(countOnlineMembers(cached.clan())) : "0";
        }

        if (params.equalsIgnoreCase("clan_name") || params.equalsIgnoreCase("clan_tag_raw")) {
            if (player == null) return "";
            CachedClanData cached = getCachedClanData(player.getUniqueId());
            return cached.clanTag() != null ? cached.clanTag() : "";
        }

        if (params.equalsIgnoreCase("clan_position") || params.equalsIgnoreCase("clan_rank") || params.equalsIgnoreCase("clan_place")) {
            if (player == null) return "0";
            CachedClanData cached = getCachedClanData(player.getUniqueId());
            return getClanPositionText(cached);
        }

        if (params.equalsIgnoreCase("elo") || params.equalsIgnoreCase("player_elo") || params.equalsIgnoreCase("ranking")
            || params.equalsIgnoreCase("points") || params.equalsIgnoreCase("player_points")) {
            if (player == null) return "0";
            if (plugin.getEloManager() == null) return "0";
            UUID uuid = player.getUniqueId();
            return eloTextCache.computeIfAbsent(uuid, key -> String.valueOf(plugin.getEloManager().getElo(key)));
        }

        if (params.equalsIgnoreCase("rank") || params.equalsIgnoreCase("position") || params.equalsIgnoreCase("postion")
            || params.equalsIgnoreCase("ranking_position") || params.equalsIgnoreCase("ranking_place")) {
            if (player == null) return "0";
            if (plugin.getEloManager() == null || plugin.getTopManager() == null) return "0";
            UUID uuid = player.getUniqueId();
            return rankTextCache.computeIfAbsent(uuid, key -> {
                int elo = plugin.getEloManager().getElo(key);
                return String.valueOf(plugin.getTopManager().getCachedPlayerRank("elo", key, elo));
            });
        }

        if (params.equalsIgnoreCase("vanish") || params.equalsIgnoreCase("is_vanished")) {
            if (player == null || !player.isOnline()) return "";
            Player onlinePlayer = player.getPlayer();
            if (onlinePlayer == null) return "";
            boolean vanished = plugin.getVanishManager() != null && plugin.getVanishManager().isVanished(onlinePlayer);
            return vanished ? plugin.getConfigManager().getVanishConfig().getVanishPlaceholder() : "";
        }

        if (params.equalsIgnoreCase("creator") || params.equalsIgnoreCase("tryb_tworcy") || params.equalsIgnoreCase("creator_mode")) {
            if (player == null || !player.isOnline()) return "";
            Player onlinePlayer = player.getPlayer();
            if (onlinePlayer == null) return "";
            boolean inCreatorMode = plugin.getCreatorModeManager() != null && plugin.getCreatorModeManager().isInCreatorMode(onlinePlayer);
            return inCreatorMode ? plugin.getConfigManager().getCreatorModeConfig().getCreatorPlaceholder() : "";
        }

        if (normalizedParams.startsWith("top_points_nick_")) {
            if (topClansCache.isEmpty()) {
                refreshTopClans();
            }
            try {
                int place = Integer.parseInt(params.replace("top_points_nick_", ""));
                if (place < 1 || place > topClansCache.size()) return "---";
                return topClansCache.get(place - 1).getTag();
            } catch (NumberFormatException e) {
                return "---";
            }
        }

        return null;
    }

    private String resolveTabTopPlaceholder(String params) {
        long now = System.currentTimeMillis();
        String cacheKey = params.toLowerCase(Locale.ROOT);
        CachedText cachedText = tabTopTextCache.get(cacheKey);
        if (cachedText != null && (cachedText.expiresAt() <= 0L || cachedText.expiresAt() > now)) {
            return cachedText.value();
        }
        if (cachedText != null) {
            tabTopTextCache.remove(cacheKey, cachedText);
        }

        String[] parts = params.split("_");
        if (parts.length < 3) {
            return "---";
        }

        boolean clanTop = parts[0].equalsIgnoreCase("tabtopclan");
        boolean userTop = parts[0].equalsIgnoreCase("tabtopuser");
        if (!clanTop && !userTop) {
            return "---";
        }

        String type = normalizeTopType(parts[1]);
        int place;
        try {
            place = Integer.parseInt(parts[2]);
        } catch (NumberFormatException exception) {
            return "---";
        }

        if (place < 1) {
            return "---";
        }

        String resolved = clanTop ? formatTabTopClan(type, place) : formatTabTopUser(type, place);
        long expiresAt = "---".equals(resolved) ? now + 2_000L : 0L;
        tabTopTextCache.put(cacheKey, new CachedText(resolved, expiresAt));
        return resolved;
    }

    private String formatTabTopUser(String type, int place) {
        if (plugin.getTopManager() == null) {
            return "---";
        }

        String name = plugin.getTopManager().getTopNick(type, place);
        String value = plugin.getTopManager().getTopValue(type, place);
        if (name == null || name.isBlank() || name.equals("---")) {
            return "---";
        }

        TabTopStyle style = getTabTopStyle();
        String active = Bukkit.getPlayerExact(name) != null ? style.onlineSymbol() : style.offlineSymbol();
        String color = getTopColor(style, place);
        return applyTabTopFormat(style.userFormat(), place, active, color, name, value);
    }

    private String formatTabTopClan(String type, int place) {
        if (topClansCache.isEmpty()) {
            refreshTopClans();
        }
        if (place > topClansCache.size()) {
            return "---";
        }

        ClanDataManager.Clan clan = topClansCache.get(place - 1);
        if (!isDisplayableClan(clan)) {
            return "---";
        }

        String value = switch (type) {
            case "kills" -> String.valueOf(clan.getKills());
            case "deaths" -> String.valueOf(clan.getDeaths());
            default -> String.valueOf(clan.getElo());
        };
        TabTopStyle style = getTabTopStyle();
        String active = countOnlineMembers(clan) > 0 ? style.onlineSymbol() : style.offlineSymbol();
        String color = getTopColor(style, place);
        return applyTabTopFormat(style.clanFormat(), place, active, color, clan.getTag(), value);
    }

    private String getTopColor(TabTopStyle style, int place) {
        return style.colors().getOrDefault(place, style.defaultColor());
    }

    private String applyTabTopFormat(String format, int place, String status, String color, String name, String value) {
        TabTopStyle style = getTabTopStyle();
        String placeLabel = style.positionLabels().getOrDefault(place, style.defaultPositionLabel().replace("{PLACE}", String.valueOf(place)));
        boolean explicitColor = format.contains("{COLOR}");
        String coloredName = explicitColor || color == null || color.isBlank() ? name : color + name;
        String coloredPlace = explicitColor || color == null || color.isBlank() ? placeLabel : color + placeLabel;
        return format
            .replace("{PLACE}", coloredPlace)
            .replace("{STATUS}", status)
            .replace("{COLOR}", color)
            .replace("{NAME}", coloredName)
            .replace("{VALUE}", value);
    }

    private TabTopStyle getTabTopStyle() {
        long now = System.currentTimeMillis();
        TabTopStyle cached = tabTopStyle;
        if (now - tabTopStyleLoadedAt < TAB_TOP_STYLE_CHECK_MILLIS) {
            return cached;
        }

        long lastModified = tablistFile.exists() ? tablistFile.lastModified() : -1L;
        if (lastModified == tabTopStyleLastModified) {
            tabTopStyleLoadedAt = now;
            return cached;
        }

        tabTopStyleLoadedAt = now;
        tabTopStyleLastModified = lastModified;
        if (!tablistFile.exists()) {
            tabTopStyle = TabTopStyle.defaults();
            return tabTopStyle;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(tablistFile);
        Map<Integer, String> colors = new ConcurrentHashMap<>();
        ConfigurationSection colorsSection = firstSection(config, "tab.tabtop.colors", "tabtop.colors");
        if (colorsSection != null) {
            for (String key : colorsSection.getKeys(false)) {
                try {
                    colors.put(Integer.parseInt(key), colorsSection.getString(key, ""));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        colors.putIfAbsent(1, getString(config, "&x&F&F&1&8&1&8", "tab.tabtop.colors.1", "tabtop.colors.1"));
        colors.putIfAbsent(2, getString(config, "&x&F&F&B&A&0&0", "tab.tabtop.colors.2", "tabtop.colors.2"));
        colors.putIfAbsent(3, getString(config, "&x&7&2&F&F&4&1", "tab.tabtop.colors.3", "tabtop.colors.3"));

        Map<Integer, String> positionLabels = new ConcurrentHashMap<>();
        ConfigurationSection labelsSection = firstSection(config, "tab.tabtop.position-labels", "tabtop.position-labels");
        if (labelsSection != null) {
            for (String key : labelsSection.getKeys(false)) {
                if (key.equalsIgnoreCase("default")) {
                    continue;
                }
                try {
                    positionLabels.put(Integer.parseInt(key), labelsSection.getString(key, String.valueOf(key)));
                } catch (NumberFormatException ignored) {
                }
            }
        }

        tabTopStyle = new TabTopStyle(
            getString(config, "#{PLACE} {STATUS} {NAME} ({VALUE})", "tab.tabtop.user-format", "tab.tabtop.de-fa-ult_points_format", "tabtop.user-format"),
            getString(config, "#{PLACE} {STATUS} {NAME} ({VALUE})", "tab.tabtop.clan-format", "tabtop.clan-format"),
            getString(config, "*", "tab.tabtop.online-symbol", "tabtop.online-symbol"),
            getString(config, "*", "tab.tabtop.offline-symbol", "tabtop.offline-symbol"),
            getString(config, "&x&A&F&D&C&F&F", "tab.tabtop.colors.default", "tabtop.colors.default"),
            Map.copyOf(colors),
            Map.copyOf(positionLabels),
            getString(config, "{PLACE}", "tab.tabtop.position-labels.default", "tabtop.position-labels.default")
        );
        return tabTopStyle;
    }

    private ConfigurationSection firstSection(YamlConfiguration config, String... paths) {
        for (String path : paths) {
            ConfigurationSection section = config.getConfigurationSection(path);
            if (section != null) {
                return section;
            }
        }
        return null;
    }

    private String getString(YamlConfiguration config, String def, String... paths) {
        for (String path : paths) {
            String value = config.getString(path);
            if (value != null) {
                return value;
            }
        }
        return def;
    }

    private record TabTopStyle(
        String userFormat,
        String clanFormat,
        String onlineSymbol,
        String offlineSymbol,
        String defaultColor,
        Map<Integer, String> colors,
        Map<Integer, String> positionLabels,
        String defaultPositionLabel
    ) {
        private static TabTopStyle defaults() {
            return new TabTopStyle(
                "#{PLACE} {STATUS} {NAME} ({VALUE})",
                "#{PLACE} {STATUS} {NAME} ({VALUE})",
                "*",
                "*",
                "&x&A&F&D&C&F&F",
                Map.of(
                    1, "&x&F&F&1&8&1&8",
                    2, "&x&F&F&B&A&0&0",
                    3, "&x&7&2&F&F&4&1"
                ),
                Map.of(),
                "{PLACE}"
            );
        }
    }
    private int countOnlineMembers(ClanDataManager.Clan clan) {
        int online = 0;
        for (UUID member : clan.getMembers()) {
            if (onlinePlayerIds.contains(member)) {
                online++;
            }
        }
        return online;
    }

    private boolean isDisplayableClan(ClanDataManager.Clan clan) {
        return clan != null
            && clan.getTag() != null
            && !clan.getTag().isBlank()
            && clan.getLeader() != null
            && clan.getMembers() != null
            && !clan.getMembers().isEmpty();
    }

    private String getSectorName(OfflinePlayer player) {
        // Prefer live sector service (np. skypvp_1 / boxpvp-1 z /ch)
        if (plugin.getSectorService() != null) {
            String current = plugin.getSectorService().getCurrentSector();
            if (current != null && !current.isBlank()) {
                sectorNameCache = current;
                sectorNameCacheAt = System.currentTimeMillis();
                return current;
            }
        }

        long now = System.currentTimeMillis();
        String cached = sectorNameCache;
        if (cached != null && !cached.isBlank() && now - sectorNameCacheAt < SECTOR_CACHE_MILLIS) {
            return cached;
        }

        if (now - sectorNameCacheAt >= SECTOR_CACHE_MILLIS) {
            sectorNameCacheAt = now;
            if (sectorsFile.exists()) {
                String sector = YamlConfiguration.loadConfiguration(sectorsFile).getString("sector-name");
                if (sector != null && !sector.isBlank()) {
                    sectorNameCache = sector;
                    return sector;
                }
            }
        }

        if (player != null && player.isOnline() && player.getPlayer() != null) {
            return player.getPlayer().getWorld().getName();
        }
        return Bukkit.getServer().getName();
    }

    private String getHeartsPlaceholder(OfflinePlayer player) {
        if (player == null || !player.isOnline()) {
            return "0";
        }

        Player onlinePlayer = player.getPlayer();
        if (onlinePlayer == null) {
            return "0";
        }

        AttributeInstance maxHealthAttribute = onlinePlayer.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        double maxHealth = maxHealthAttribute != null ? maxHealthAttribute.getValue() : onlinePlayer.getMaxHealth();
        double hearts = Math.max(0.0D, maxHealth / 2.0D);
        return formatHearts(hearts);
    }

    private String formatHearts(double hearts) {
        double rounded = Math.rint(hearts);
        if (Math.abs(hearts - rounded) < 0.001D) {
            return String.valueOf((int) rounded);
        }
        return String.format(Locale.US, "%.1f", hearts);
    }

    private String normalizeTopType(String type) {
        if (type == null) return "kills";
        String lower = type.toLowerCase(Locale.ROOT);
        if (lower.equals("points") || lower.equals("ranking") || lower.equals("elo") || lower.equals("punkty")) {
            return "elo";
        }
        if (lower.equals("kills") || lower.equals("kill") || lower.equals("kille") || lower.equals("zabojstwa") || lower.equals("zabojstw")) {
            return "kills";
        }
        if (lower.equals("playtime") || lower.equals("time") || lower.equals("czas")) {
            return "playtime";
        }
        if (lower.equals("money") || lower.equals("kasa") || lower.equals("balance") || lower.equals("pieniadze")) {
            return "money";
        }
        if (lower.equals("wpln") || lower.equals("wallet") || lower.equals("portfel")) {
            return "wpln";
        }
        return lower;
    }

    @Override
    public String onPlaceholderRequest(Player viewer, Player target, String identifier) {
        if (identifier.equalsIgnoreCase("clan_tag_rel")
            || identifier.equalsIgnoreCase("clan_rel")
            || identifier.equalsIgnoreCase("clan_tag")
            || identifier.equalsIgnoreCase("clan")) {
            if (target == null) return "";

            CachedClanData targetCached = getCachedClanData(target.getUniqueId());
            if (targetCached.clanTag() == null) return "";

            if (viewer == null) return "[" + targetCached.clanTag() + "]";

            CachedClanData viewerCached = getCachedClanData(viewer.getUniqueId());

            if (viewerCached.clanTag() != null && viewerCached.clanTag().equalsIgnoreCase(targetCached.clanTag())) {
                return "&a[" + targetCached.clanTag() + "]";
            } else {
                return "&c[" + targetCached.clanTag() + "]";
            }
        }

        return null;
    }

    /** Rel / clan placeholdery dla botów spoofera — ten sam wynik co dla gracza (zwykle pusto). */
    public String onPlaceholderRequestForSpoofed(
        Player viewer,
        @NotNull SpoofedPlayerProfile target,
        @NotNull String identifier
    ) {
        if (identifier.equalsIgnoreCase("clan_tag_rel")
            || identifier.equalsIgnoreCase("clan_rel")
            || identifier.equalsIgnoreCase("clan_tag")
            || identifier.equalsIgnoreCase("clan")) {
            if (target.uuid() == null) {
                return "";
            }
            CachedClanData targetCached = getCachedClanData(target.uuid());
            if (targetCached.clanTag() == null) {
                return "";
            }
            if (viewer == null) {
                return "[" + targetCached.clanTag() + "]";
            }
            CachedClanData viewerCached = getCachedClanData(viewer.getUniqueId());
            if (viewerCached.clanTag() != null && viewerCached.clanTag().equalsIgnoreCase(targetCached.clanTag())) {
                return "&a[" + targetCached.clanTag() + "]";
            }
            return "&c[" + targetCached.clanTag() + "]";
        }
        return onRequestForSpoofedProfile(target, identifier);
    }

    private int getPlayerStatistic(OfflinePlayer player, Statistic statistic) {
        try {
            if (player.isOnline()) {
                Player online = player.getPlayer();
                if (online != null) {
                    return online.getStatistic(statistic);
                }
            }
            return player.getStatistic(statistic);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private long getPlayerPlaytimeMillis(OfflinePlayer player) {
        return Math.max(0L, (long) getPlayerStatistic(player, Statistic.PLAY_ONE_MINUTE) * 50L);
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

    private String formatTime(int totalSeconds) {
        if (totalSeconds <= 0) return "0s";

        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;

        if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        }
        return seconds + "s";
    }
}
