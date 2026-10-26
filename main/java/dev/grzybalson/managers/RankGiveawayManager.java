package dev.grzybalson.managers;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.configs.CodeConfig;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class RankGiveawayManager {

    public static final String CODE_KEY = "rozdanie rang";
    private static final String GRANT_DURATION = "1h";

    private enum Phase { IDLE, DRAWING }

    private final HollyCore plugin;
    private final Set<UUID> winners = ConcurrentHashMap.newKeySet();
    private final Map<String, Integer> rankAwardedCounts = new ConcurrentHashMap<>();

    private Phase phase = Phase.IDLE;
    private BukkitTask animationTask;

    public RankGiveawayManager(HollyCore plugin) {
        this.plugin = plugin;
        loadState();
    }

    public void reload() {
        cancelTasks();
        phase = Phase.IDLE;
        loadState();
    }

    public void shutdown() {
        cancelTasks();
        phase = Phase.IDLE;
    }

    public boolean tryJoin(Player player) {
        return tryJoin(player, false);
    }

    public boolean tryTestDraw(Player player) {
        if (player == null || !player.isOp()) {
            return false;
        }
        if (phase != Phase.IDLE) {
            player.sendMessage(ColorUtils.colorizeComponent(config().getWaitForDraw()));
            return false;
        }
        phase = Phase.DRAWING;
        startDraw(player.getUniqueId(), true);
        return true;
    }

    public boolean tryJoin(Player player, boolean ignoreSchedule) {
        CodeConfig.RankGiveaway config = config();
        if (!config.isActive()) {
            player.sendMessage(ColorUtils.colorizeComponent(config.getNotActive()));
            return false;
        }
        if (!ignoreSchedule && !isAvailableNow(config)) {
            player.sendMessage(ColorUtils.colorizeComponent(
                config.getNotAvailableYet().replace("{time}", config.getAvailableFrom())
            ));
            return false;
        }
        if (winners.contains(player.getUniqueId())) {
            player.sendMessage(ColorUtils.colorizeComponent(config.getAlreadyWon()));
            return false;
        }
        if (winners.size() >= config.getMaxWinners()) {
            player.sendMessage(ColorUtils.colorizeComponent(
                config.getLimitReached().replace("{limit}", String.valueOf(config.getMaxWinners()))
            ));
            return false;
        }
        if (phase == Phase.DRAWING) {
            player.sendMessage(ColorUtils.colorizeComponent(config.getWaitForDraw()));
            return false;
        }

        phase = Phase.DRAWING;
        player.sendMessage(ColorUtils.colorizeComponent(config.getDrawStarted()));
        startDraw(player.getUniqueId(), false);
        return true;
    }

    private void startDraw(UUID playerId, boolean testMode) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            phase = Phase.IDLE;
            return;
        }

        String rankId = drawRankId();
        CodeConfig.RankPoolEntry rank = config().getRanks().get(rankId);
        if (rank == null) {
            phase = Phase.IDLE;
            return;
        }

        List<String> rankIds = new ArrayList<>(config().getRanks().keySet());
        List<String> fakeDurations = new ArrayList<>(config().getFakeDurations());
        if (fakeDurations.isEmpty()) {
            fakeDurations.addAll(List.of("1h", "10h", "1d", "7d", "30d"));
        }
        int rankSteps = 7;
        int timeSteps = 5;
        int[] step = {0};
        int[] phaseIndex = {0};

        animationTask = new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                if (phaseIndex[0] == 0) {
                    if (step[0] >= rankSteps) {
                        phaseIndex[0] = 1;
                        step[0] = 0;
                        return;
                    }
                    String shownRank = rankIds.get(ThreadLocalRandom.current().nextInt(rankIds.size()));
                    CodeConfig.RankPoolEntry shown = config().getRanks().get(shownRank);
                    String title = shown == null ? rank.getTitle() : shown.getTitle();
                    if (step[0] >= rankSteps - 2) {
                        title = rank.getTitle();
                    }
                    broadcastTitle(testMode, title, config().getRankSubtitle(), step[0], rankSteps);
                    playTick(testMode);
                    step[0]++;
                    return;
                }

                if (step[0] >= timeSteps) {
                    cancel();
                    animationTask = null;
                    finishDraw(player, rank, testMode);
                    return;
                }

                String fakeDuration = pickFakeDuration(fakeDurations, step[0], timeSteps);
                broadcastTitle(
                    testMode,
                    rank.getTitle(),
                    config().getTimeSubtitle().replace("{time}", fakeDuration),
                    step[0],
                    timeSteps
                );
                playTick(testMode);
                step[0]++;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void finishDraw(Player player, CodeConfig.RankPoolEntry rank, boolean testMode) {
        String announcementTitle = config().getAnnouncementTitle();
        String announcementSubtitle = config().getAnnouncementSubtitle().replace("{player}", player.getName());
        broadcastTitle(testMode, announcementTitle, announcementSubtitle, 0, 1);
        playFinish(testMode);

        if (!testMode) {
            winners.add(player.getUniqueId());
            rankAwardedCounts.merge(rank.getGroup(), 1, Integer::sum);
            saveState();
            grantRank(player, rank.getGroup());
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> phase = Phase.IDLE, 20L);
    }

    private void grantRank(Player player, String group) {
        String command = config().getCommandTemplate()
            .replace("{player}", player.getName())
            .replace("{PLAYER}", player.getName())
            .replace("{group}", group)
            .replace("{GROUP}", group)
            .replace("{duration}", config().getGrantDuration())
            .replace("{DURATION}", config().getGrantDuration());
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    }

    private String drawRankId() {
        CodeConfig.RankGiveaway config = config();
        List<String> ids = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        int total = 0;
        for (Map.Entry<String, CodeConfig.RankPoolEntry> entry : config.getRanks().entrySet()) {
            CodeConfig.RankPoolEntry rank = entry.getValue();
            if (rank == null) {
                continue;
            }
            int limit = rank.getLimit();
            int awarded = rankAwardedCounts.getOrDefault(rank.getGroup(), 0);
            if (limit >= 0 && awarded >= limit) {
                continue;
            }
            int weight = Math.max(1, rank.getWeight());
            ids.add(entry.getKey());
            weights.add(weight);
            total += weight;
        }
        if (ids.isEmpty()) {
            return config.getRanks().keySet().iterator().next();
        }
        int roll = ThreadLocalRandom.current().nextInt(total);
        int current = 0;
        for (int i = 0; i < ids.size(); i++) {
            current += weights.get(i);
            if (roll < current) {
                return ids.get(i);
            }
        }
        return ids.get(ids.size() - 1);
    }

    private String pickFakeDuration(List<String> durations, int step, int maxSteps) {
        if (step >= maxSteps - 1) {
            return GRANT_DURATION;
        }
        if (ThreadLocalRandom.current().nextDouble() < 0.9D) {
            return GRANT_DURATION;
        }
        List<String> flashy = durations.stream()
            .filter(duration -> !GRANT_DURATION.equalsIgnoreCase(duration))
            .toList();
        if (flashy.isEmpty()) {
            return durations.get(ThreadLocalRandom.current().nextInt(durations.size()));
        }
        return flashy.get(ThreadLocalRandom.current().nextInt(flashy.size()));
    }

    private void broadcastTitle(boolean testMode, String title, String subtitle, int step, int maxSteps) {
        int fadeIn = step == 0 ? 3 : 1;
        int stay = Math.max(5, 12 - step);
        int fadeOut = 1;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (testMode && !online.isOp()) {
                continue;
            }
            ColorUtils.sendTitle(online, title, subtitle, fadeIn, stay, fadeOut);
        }
    }

    private void playTick(boolean testMode) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (testMode && !online.isOp()) {
                continue;
            }
            float pitch = 0.8f + (ThreadLocalRandom.current().nextFloat() * 0.4f);
            online.playSound(online.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.35f, pitch);
        }
    }

    private void playFinish(boolean testMode) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (testMode && !online.isOp()) {
                continue;
            }
            online.playSound(online.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.2f);
        }
    }

    private boolean isAvailableNow(CodeConfig.RankGiveaway config) {
        try {
            LocalTime from = LocalTime.parse(config.getAvailableFrom(), DateTimeFormatter.ofPattern("H:mm"));
            ZoneId zone = ZoneId.of(config.getTimezone());
            LocalTime now = LocalTime.now(zone);
            return !now.isBefore(from);
        } catch (Exception ignored) {
            return true;
        }
    }

    private CodeConfig.RankGiveaway config() {
        return plugin.getConfigManager().getCodeConfig().getRankGiveaway();
    }

    private void cancelTasks() {
        if (animationTask != null) {
            animationTask.cancel();
            animationTask = null;
        }
    }

    private void loadState() {
        plugin.getMongoManager().toFuture(
            plugin.getMongoManager().getCollection("rank_giveaway_state")
                .find(new org.bson.Document("_id", CODE_KEY))
                .first()
        ).thenAccept(doc -> {
            if (doc == null) {
                return;
            }
            Set<UUID> loadedWinners = ConcurrentHashMap.newKeySet();
            List<String> winnerIds = doc.getList("winners", String.class);
            if (winnerIds != null) {
                for (String uuid : winnerIds) {
                    try {
                        loadedWinners.add(UUID.fromString(uuid));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
            winners.clear();
            winners.addAll(loadedWinners);

            rankAwardedCounts.clear();
            org.bson.Document counts = doc.get("rank-counts", org.bson.Document.class);
            if (counts != null) {
                for (Map.Entry<String, Object> entry : counts.entrySet()) {
                    if (entry.getValue() instanceof Number number) {
                        rankAwardedCounts.put(entry.getKey().toLowerCase(Locale.ROOT), number.intValue());
                    }
                }
            }
        });
    }

    private void saveState() {
        List<String> winnerIds = new ArrayList<>();
        for (UUID winner : winners) {
            winnerIds.add(winner.toString());
        }
        org.bson.Document counts = new org.bson.Document();
        for (Map.Entry<String, Integer> entry : rankAwardedCounts.entrySet()) {
            counts.append(entry.getKey(), entry.getValue());
        }
        org.bson.Document doc = new org.bson.Document("_id", CODE_KEY)
            .append("winners", winnerIds)
            .append("rank-counts", counts);
        plugin.getMongoManager().toFuture(
            plugin.getMongoManager().getCollection("rank_giveaway_state")
                .replaceOne(new org.bson.Document("_id", CODE_KEY), doc, new com.mongodb.client.model.ReplaceOptions().upsert(true))
        );
    }
}
