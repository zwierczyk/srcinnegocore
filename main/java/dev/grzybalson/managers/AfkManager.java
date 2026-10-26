package dev.grzybalson.managers;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.configs.AfkConfig;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.CommandPlaceholderUtil;
import dev.grzybalson.utils.TimeUtil;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class AfkManager {

    private final HollyCore plugin;
    private final Map<UUID, Map<String, AfkSession>> activeSessions;
    private final Map<UUID, Map<String, BossBar>> playerBossBars;
    private final Map<UUID, BossBar> turboBossBars;
    private BukkitTask checkTask;
    private BukkitTask turboTask;

    private int turboMultiplier = 1;
    private long turboStartTime;
    private long turboEndTime;

    public AfkManager(HollyCore plugin) {
        this.plugin = plugin;
        this.activeSessions = new ConcurrentHashMap<>();
        this.playerBossBars = new ConcurrentHashMap<>();
        this.turboBossBars = new ConcurrentHashMap<>();
    }

    public void start() {
        checkTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    checkPlayerAfk(player);
                }
            }
        }.runTaskTimer(plugin, 60L, 60L);

        turboTask = new BukkitRunnable() {
            @Override
            public void run() {
                tickTurboBossBars();
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    public void stop() {
        if (checkTask != null) {
            checkTask.cancel();
        }
        if (turboTask != null) {
            turboTask.cancel();
        }
        for (UUID uuid : playerBossBars.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                removeAllBossBars(player);
            }
        }
        clearTurboBossBars();
        activeSessions.clear();
        turboMultiplier = 1;
        turboStartTime = 0L;
        turboEndTime = 0L;
    }

    public void reload() {
        plugin.getConfigManager().reloadAfkConfig();
        for (Player player : Bukkit.getOnlinePlayers()) {
            removeAllBossBars(player);
        }
        activeSessions.clear();
        for (Player player : Bukkit.getOnlinePlayers()) {
            checkPlayerAfk(player);
        }
    }

    private void checkPlayerAfk(Player player) {
        Location loc = player.getLocation();
        AfkConfig config = plugin.getConfigManager().getAfkConfig();

        String currentZoneName = null;
        AfkConfig.AfkZone currentZone = null;

        for (Map.Entry<String, AfkConfig.AfkZone> entry : config.getZones().entrySet()) {
            AfkConfig.AfkZone zone = entry.getValue();
            if (zone.isInside(loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ())) {
                currentZoneName = entry.getKey();
                currentZone = zone;
                break;
            }
        }

        UUID uuid = player.getUniqueId();

        if (currentZone != null) {
            Map<String, AfkSession> playerSessions = activeSessions.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
            Set<String> activeKeysThisCheck = new HashSet<>();

            List<AfkConfig.AfkReward> qualifiedRewards = new ArrayList<>();
            boolean isSelfContainedZone = (currentZone.getRewardCommands() != null && !currentZone.getRewardCommands().isEmpty())
                || currentZone.getMoneyReward() > 0
                || currentZone.getItemChance() > 0
                || currentZone.getIntervalSeconds() > 0
                || (currentZone.getDisplayName() != null && !currentZone.getDisplayName().isBlank());

            if (isSelfContainedZone) {
                String reqPerm = (currentZone.getPermission() != null && !currentZone.getPermission().isBlank())
                    ? currentZone.getPermission()
                    : (currentZone.getRewardPermission() != null ? currentZone.getRewardPermission() : "");
                if (reqPerm.isBlank() || player.hasPermission(reqPerm)) {
                    qualifiedRewards.add(createRewardFromZone(currentZone));
                }
            } else {
                for (AfkConfig.AfkReward reward : config.getRewards()) {
                    if (currentZone.getRewardPermission() != null && !currentZone.getRewardPermission().isBlank()) {
                        if (reward.getPermission().equalsIgnoreCase(currentZone.getRewardPermission())) {
                            if (reward.getPermission().isBlank() || player.hasPermission(reward.getPermission())) {
                                qualifiedRewards.add(reward);
                            }
                        }
                    } else {
                        if (reward.getPermission().isBlank() || player.hasPermission(reward.getPermission())) {
                            qualifiedRewards.add(reward);
                        }
                    }
                }
            }

            for (AfkConfig.AfkReward reward : qualifiedRewards) {
                String sessionKey = currentZoneName + ":" + (reward.getPermission().isBlank() ? "zone" : reward.getPermission());
                activeKeysThisCheck.add(sessionKey);

                AfkSession session = playerSessions.get(sessionKey);
                if (session == null) {
                    startAfkSession(player, sessionKey, currentZoneName, currentZone, reward);
                } else {
                    updateAfkSession(player, session, currentZone);
                }
            }

            Iterator<Map.Entry<String, AfkSession>> iterator = playerSessions.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, AfkSession> entry = iterator.next();
                if (!activeKeysThisCheck.contains(entry.getKey())) {
                    AfkSession oldSession = entry.getValue();
                    if (oldSession.getBossBar() != null) {
                        player.hideBossBar(oldSession.getBossBar());
                    }
                    iterator.remove();
                }
            }
        } else {
            if (activeSessions.containsKey(uuid)) {
                endAfkSession(player);
            }
        }
    }

    private AfkConfig.AfkReward createRewardFromZone(AfkConfig.AfkZone zone) {
        String perm = (zone.getPermission() != null && !zone.getPermission().isBlank())
            ? zone.getPermission()
            : (zone.getRewardPermission() != null ? zone.getRewardPermission() : "");
        int interval = zone.getIntervalSeconds() > 0 ? zone.getIntervalSeconds() : 60;
        String displayName = (zone.getDisplayName() != null && !zone.getDisplayName().isBlank())
            ? zone.getDisplayName()
            : "Nagroda AFK";
        String color = (zone.getBossbarColor() != null && !zone.getBossbarColor().isBlank())
            ? zone.getBossbarColor()
            : "YELLOW";
        String format = zone.getBossbarFormat();

        return new AfkConfig.AfkReward(
            perm,
            zone.getMoneyReward(),
            zone.getItemChance(),
            interval,
            displayName,
            color,
            format,
            zone.getItemRewards(),
            zone.getRewardCommands()
        );
    }

    private void startAfkSession(Player player, String sessionKey, String zoneName, AfkConfig.AfkZone zone, AfkConfig.AfkReward reward) {
        UUID uuid = player.getUniqueId();
        AfkSession session = new AfkSession(uuid, sessionKey, zoneName, reward);
        activeSessions.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>()).put(sessionKey, session);

        createBossBar(player, session, zone);
    }

    private void updateAfkSession(Player player, AfkSession session, AfkConfig.AfkZone zone) {
        AfkConfig.AfkReward reward = session.getReward();
        int intervalSeconds = reward.getIntervalSeconds();
        long elapsed = (System.currentTimeMillis() - session.getLastRewardTime()) / 1000;
        double effectiveIntervalSeconds = getEffectiveInterval(player, intervalSeconds);

        float progress = Math.min(1.0f, (float) (elapsed / effectiveIntervalSeconds));
        int remaining = Math.max(0, (int) Math.ceil(effectiveIntervalSeconds - elapsed));

        updateBossBar(player, session, zone, progress, remaining);

        if (elapsed >= effectiveIntervalSeconds) {
            giveReward(player, reward);
            session.setLastRewardTime(System.currentTimeMillis());
        }
    }

    public boolean isTurboActive() {
        return turboMultiplier > 1 && System.currentTimeMillis() < turboEndTime;
    }

    public double getEffectiveInterval(int seconds) {
        return getEffectiveInterval(null, seconds);
    }

    public double getEffectiveInterval(Player player, int seconds) {
        double multiplier = isTurboActive() ? Math.max(1, turboMultiplier) : 1.0D;
        if (player != null) {
            var mslf = plugin.getMslfKostiumyManager();
            if (mslf != null && mslf.getPetsEffectsListener() != null
                && mslf.getPetsEffectsListener().hasSzybciorek(player.getUniqueId())) {
                multiplier *= 2.0D;
            }
        }
        return Math.max(1.0D, (double) seconds / multiplier);
    }

    public void enableTurbo(int multiplier, long durationMillis) {
        this.turboMultiplier = Math.max(1, multiplier);
        this.turboStartTime = System.currentTimeMillis();
        this.turboEndTime = turboStartTime + Math.max(1000L, durationMillis);
        tickTurboBossBars();
    }

    public int getTurboMultiplier() {
        return isTurboActive() ? turboMultiplier : 1;
    }

    public long getTurboRemainingMillis() {
        if (!isTurboActive()) {
            return 0L;
        }
        return Math.max(0L, turboEndTime - System.currentTimeMillis());
    }

    private void tickTurboBossBars() {
        if (!isTurboActive()) {
            clearTurboBossBars();
            turboMultiplier = 1;
            return;
        }

        AfkConfig config = plugin.getConfigManager().getAfkConfig();
        long remaining = turboEndTime - System.currentTimeMillis();
        long total = Math.max(1L, turboEndTime - turboStartTime);
        float progress = Math.max(0.0f, Math.min(1.0f, (float) remaining / (float) total));

        BossBar.Color parsedColor;
        try {
            parsedColor = BossBar.Color.valueOf(config.getTurboBossbarColor().toUpperCase());
        } catch (IllegalArgumentException e) {
            parsedColor = BossBar.Color.GREEN;
        }
        final BossBar.Color barColor = parsedColor;

        final String title = config.getTurboBossbarTitle()
            .replace("{TIME}", TimeUtil.convert(remaining))
            .replace("{MULTIPLIER}", String.valueOf(turboMultiplier));
        final float barProgress = progress;

        for (Player player : Bukkit.getOnlinePlayers()) {
            final Player onlinePlayer = player;
            BossBar bar = turboBossBars.computeIfAbsent(onlinePlayer.getUniqueId(), uuid -> {
                BossBar created = BossBar.bossBar(
                    ColorUtils.colorizeComponent(title),
                    barProgress,
                    barColor,
                    BossBar.Overlay.NOTCHED_20
                );
                onlinePlayer.showBossBar(created);
                return created;
            });
            bar.name(ColorUtils.colorizeComponent(title));
            bar.progress(barProgress);
            bar.color(barColor);
            if (plugin.getPickupManager() != null) {
                plugin.getPickupManager().schedulePinToBottom(onlinePlayer);
            }
        }

        turboBossBars.keySet().removeIf(uuid -> {
            Player online = Bukkit.getPlayer(uuid);
            if (online == null || !online.isOnline()) {
                BossBar bar = turboBossBars.get(uuid);
                if (bar != null && online != null) {
                    online.hideBossBar(bar);
                }
                return true;
            }
            return false;
        });
    }

    private void clearTurboBossBars() {
        for (Map.Entry<UUID, BossBar> entry : turboBossBars.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                player.hideBossBar(entry.getValue());
            }
        }
        turboBossBars.clear();
    }

    private void giveReward(Player player, AfkConfig.AfkReward reward) {
        if (reward.getMoneyReward() > 0) {
            plugin.getEconomy().addBalance(player.getUniqueId(), reward.getMoneyReward());
            player.sendMessage(ColorUtils.success("Otrzymałeś " + String.format("%.2f", reward.getMoneyReward()) + "$ za AFK!"));
        }

        if (reward.getItemChance() > 0 && !reward.getItemRewards().isEmpty()) {
            if (ThreadLocalRandom.current().nextInt(100) < reward.getItemChance()) {
                String itemData = reward.getItemRewards().get(ThreadLocalRandom.current().nextInt(reward.getItemRewards().size()));
                player.sendMessage(ColorUtils.success("Otrzymałeś przedmiot za AFK!"));
            }
        }

        for (String cmd : reward.getRewardCommands()) {
            String parsedCmd = CommandPlaceholderUtil.normalizeConsoleCommand(cmd, player);
            if (parsedCmd.isBlank()) {
                continue;
            }
            plugin.getLogger().info("[AFK] Executing command: " + parsedCmd);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsedCmd);
        }
    }

    private void endAfkSession(Player player) {
        UUID uuid = player.getUniqueId();
        Map<String, AfkSession> sessions = activeSessions.remove(uuid);
        if (sessions != null) {
            for (AfkSession session : sessions.values()) {
                if (session.getBossBar() != null) {
                    player.hideBossBar(session.getBossBar());
                }
            }
        }
        removeAllBossBars(player);
    }

    private void createBossBar(Player player, AfkSession session, AfkConfig.AfkZone zone) {
        AfkConfig.AfkReward reward = session.getReward();
        AfkConfig config = plugin.getConfigManager().getAfkConfig();

        String colorStr = (zone != null && zone.getBossbarColor() != null && !zone.getBossbarColor().isBlank())
            ? zone.getBossbarColor()
            : (reward != null && reward.getBossbarColor() != null && !reward.getBossbarColor().isBlank())
                ? reward.getBossbarColor()
                : "YELLOW";

        BossBar.Color color;
        try {
            color = BossBar.Color.valueOf(colorStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            color = BossBar.Color.YELLOW;
        }

        String rawFormat = (zone != null && zone.getBossbarFormat() != null && !zone.getBossbarFormat().isBlank())
            ? zone.getBossbarFormat()
            : (reward != null && reward.getBossbarFormat() != null && !reward.getBossbarFormat().isBlank())
                ? reward.getBossbarFormat()
                : config.getBossbarFormat();

        String format = rawFormat
            .replace("{reward_name}", reward != null ? reward.getDisplayName() : "")
            .replace("{zone_name}", session.getZoneName() != null ? session.getZoneName() : "")
            .replace("{time}", formatTime(reward != null ? reward.getIntervalSeconds() : 60));

        BossBar bossBar = BossBar.bossBar(
            ColorUtils.colorizeComponent(format),
            0.0f,
            color,
            BossBar.Overlay.PROGRESS
        );

        session.setBossBar(bossBar);
        player.showBossBar(bossBar);
        playerBossBars.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>())
            .put(session.getSessionKey(), bossBar);
        if (plugin.getPickupManager() != null) {
            plugin.getPickupManager().schedulePinToBottom(player);
        }
    }

    private void updateBossBar(Player player, AfkSession session, AfkConfig.AfkZone zone, float progress, int remainingSeconds) {
        BossBar bossBar = session.getBossBar();
        if (bossBar == null) {
            createBossBar(player, session, zone);
            bossBar = session.getBossBar();
            if (bossBar == null) return;
        }

        AfkConfig.AfkReward reward = session.getReward();
        AfkConfig config = plugin.getConfigManager().getAfkConfig();

        String rawFormat = (zone != null && zone.getBossbarFormat() != null && !zone.getBossbarFormat().isBlank())
            ? zone.getBossbarFormat()
            : (reward != null && reward.getBossbarFormat() != null && !reward.getBossbarFormat().isBlank())
                ? reward.getBossbarFormat()
                : config.getBossbarFormat();

        String format = rawFormat
            .replace("{reward_name}", reward != null ? reward.getDisplayName() : "")
            .replace("{zone_name}", session.getZoneName() != null ? session.getZoneName() : "")
            .replace("{time}", formatTime(remainingSeconds));

        bossBar.name(ColorUtils.colorizeComponent(format));
        bossBar.progress(progress);
    }

    private void removeAllBossBars(Player player) {
        Map<String, BossBar> bars = playerBossBars.remove(player.getUniqueId());
        if (bars != null) {
            for (BossBar bar : bars.values()) {
                player.hideBossBar(bar);
            }
        }
    }

    private String formatTime(int seconds) {
        if (seconds >= 3600) {
            return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m";
        } else if (seconds >= 60) {
            return (seconds / 60) + "m " + (seconds % 60) + "s";
        } else {
            return seconds + "s";
        }
    }

    public void handlePlayerQuit(Player player) {
        endAfkSession(player);
        BossBar turboBar = turboBossBars.remove(player.getUniqueId());
        if (turboBar != null) {
            player.hideBossBar(turboBar);
        }
    }

    public boolean isInAfkZone(Player player) {
        Map<String, AfkSession> sessions = activeSessions.get(player.getUniqueId());
        return sessions != null && !sessions.isEmpty();
    }

    public int getRemainingSeconds(Player player) {
        if (player == null) return 0;
        Map<String, AfkSession> sessions = activeSessions.get(player.getUniqueId());
        if (sessions == null || sessions.isEmpty()) return 0;

        int minRemaining = Integer.MAX_VALUE;
        for (AfkSession session : sessions.values()) {
            AfkConfig.AfkReward reward = session.getReward();
            if (reward != null) {
                double interval = getEffectiveInterval(player, Math.max(1, reward.getIntervalSeconds()));
                long elapsed = (System.currentTimeMillis() - session.getLastRewardTime()) / 1000;
                int remaining = Math.max(0, (int) Math.ceil(interval - elapsed));
                minRemaining = Math.min(minRemaining, remaining);
            }
        }
        return minRemaining == Integer.MAX_VALUE ? 0 : minRemaining;
    }

    public int getRemainingSeconds(Player player, String zoneName) {
        if (player == null || zoneName == null) return 0;
        Map<String, AfkSession> sessions = activeSessions.get(player.getUniqueId());
        if (sessions == null || sessions.isEmpty()) return 0;

        for (AfkSession session : sessions.values()) {
            if (zoneName.equalsIgnoreCase(session.getZoneName())) {
                AfkConfig.AfkReward reward = session.getReward();
                if (reward != null) {
                    double interval = getEffectiveInterval(player, Math.max(1, reward.getIntervalSeconds()));
                    long elapsed = (System.currentTimeMillis() - session.getLastRewardTime()) / 1000;
                    return Math.max(0, (int) Math.ceil(interval - elapsed));
                }
            }
        }
        return 0;
    }

    public int getItemChance(Player player) {
        if (player == null) return 0;
        Map<String, AfkSession> sessions = activeSessions.get(player.getUniqueId());
        if (sessions == null || sessions.isEmpty()) return 0;

        for (AfkSession session : sessions.values()) {
            if (session.getReward() != null) {
                return session.getReward().getItemChance();
            }
        }
        return 0;
    }

    public int getItemChance(Player player, String zoneName) {
        if (player == null || zoneName == null) return 0;
        Map<String, AfkSession> sessions = activeSessions.get(player.getUniqueId());
        if (sessions != null && !sessions.isEmpty()) {
            for (AfkSession session : sessions.values()) {
                if (zoneName.equalsIgnoreCase(session.getZoneName()) && session.getReward() != null) {
                    return session.getReward().getItemChance();
                }
            }
        }

        AfkConfig config = plugin.getConfigManager().getAfkConfig();
        AfkConfig.AfkZone zone = config.getZones().get(zoneName);
        if (zone != null) {
            return zone.getItemChance();
        }
        return 0;
    }

    public String getDisplayName(Player player) {
        if (player == null) return "";
        Map<String, AfkSession> sessions = activeSessions.get(player.getUniqueId());
        if (sessions == null || sessions.isEmpty()) return "";

        for (AfkSession session : sessions.values()) {
            if (session.getReward() != null && session.getReward().getDisplayName() != null) {
                return session.getReward().getDisplayName();
            }
        }
        return "";
    }

    public String getDisplayName(Player player, String zoneName) {
        if (player == null || zoneName == null) return "";
        Map<String, AfkSession> sessions = activeSessions.get(player.getUniqueId());
        if (sessions != null && !sessions.isEmpty()) {
            for (AfkSession session : sessions.values()) {
                if (zoneName.equalsIgnoreCase(session.getZoneName()) && session.getReward() != null) {
                    return session.getReward().getDisplayName();
                }
            }
        }

        AfkConfig config = plugin.getConfigManager().getAfkConfig();
        AfkConfig.AfkZone zone = config.getZones().get(zoneName);
        if (zone != null && zone.getDisplayName() != null) {
            return zone.getDisplayName();
        }
        return "";
    }

    public String getZoneName(Player player) {
        if (player == null) return "";
        Map<String, AfkSession> sessions = activeSessions.get(player.getUniqueId());
        if (sessions == null || sessions.isEmpty()) return "";

        for (AfkSession session : sessions.values()) {
            if (session.getZoneName() != null) {
                return session.getZoneName();
            }
        }
        return "";
    }

    private static class AfkSession {
        private final UUID playerUuid;
        private final String sessionKey;
        private final String zoneName;
        private final AfkConfig.AfkReward reward;
        private long lastRewardTime;
        private BossBar bossBar;

        public AfkSession(UUID playerUuid, String sessionKey, String zoneName, AfkConfig.AfkReward reward) {
            this.playerUuid = playerUuid;
            this.sessionKey = sessionKey;
            this.zoneName = zoneName;
            this.reward = reward;
            this.lastRewardTime = System.currentTimeMillis();
        }

        public UUID getPlayerUuid() { return playerUuid; }
        public String getSessionKey() { return sessionKey; }
        public String getZoneName() { return zoneName; }
        public AfkConfig.AfkReward getReward() { return reward; }
        public long getLastRewardTime() { return lastRewardTime; }
        public void setLastRewardTime(long time) { this.lastRewardTime = time; }
        public BossBar getBossBar() { return bossBar; }
        public void setBossBar(BossBar bossBar) { this.bossBar = bossBar; }
    }
}
