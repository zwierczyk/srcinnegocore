package dev.grzybalson.modules.boss;

import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.TimeUtil;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hoglin;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class BossManager {

    private final HollyCore plugin;
    private final BossConfig config;
    private final HoglinHandler hoglinHandler;
    private final Map<UUID, BossBar> countdownBars = new HashMap<>();
    private final Map<UUID, String> trackedBosses = new HashMap<>();

    private BukkitTask spawnTask;
    private BukkitTask countdownTask;

    private volatile long cachedNextSpawnMillis = -1L;
    private volatile String cachedNextBossName = "";
    private volatile String cachedNextBossId = "";

    public BossManager(HollyCore plugin) {
        this.plugin = plugin;
        this.config = new BossConfig(plugin);
        this.hoglinHandler = new HoglinHandler(this);
    }

    public void start() {
        refreshNextSpawnCache();
        spawnTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickSpawns, 20L, 20L * 60L);
        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickCountdownBars, 20L, 20L);
    }

    public void shutdown() {
        if (spawnTask != null) {
            spawnTask.cancel();
        }
        if (countdownTask != null) {
            countdownTask.cancel();
        }
        clearCountdownBars();
    }

    public void reload() {
        config.reload();
        refreshNextSpawnCache();
    }

    public HollyCore getPlugin() {
        return plugin;
    }

    public BossConfig getConfig() {
        return config;
    }

    public HoglinHandler getHoglinHandler() {
        return hoglinHandler;
    }

    public BossConfig.BossDefinition takeTrackedBoss(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        String id = trackedBosses.remove(uuid);
        return id == null ? null : config.getBoss(id);
    }

    public String getNextBossName() {
        if (cachedNextSpawnMillis <= 0L) {
            return config.getNoBossPlaceholder();
        }
        return cachedNextBossName == null || cachedNextBossName.isBlank()
            ? config.getNoBossPlaceholder()
            : cachedNextBossName;
    }

    public String getNextBossTimeFormatted() {
        long remaining = getMillisUntilNextBoss();
        if (remaining == Long.MAX_VALUE || remaining < 0L) {
            return config.getNoBossPlaceholder();
        }
        return TimeUtil.convert(remaining);
    }

    public String getNextBossHour() {
        if (cachedNextSpawnMillis <= 0L) {
            return config.getNoBossPlaceholder();
        }
        LocalDateTime time = LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(cachedNextSpawnMillis),
            ZoneId.systemDefault()
        );
        return String.format("%02d:%02d", time.getHour(), time.getMinute());
    }

    public long getMillisUntilNextBoss() {
        if (cachedNextSpawnMillis <= 0L) {
            return Long.MAX_VALUE;
        }
        return Math.max(0L, cachedNextSpawnMillis - System.currentTimeMillis());
    }

    public void refreshNextSpawnCache() {
        List<ScheduledBoss> schedule = buildSchedule();
        if (schedule.isEmpty()) {
            cachedNextSpawnMillis = -1L;
            cachedNextBossName = "";
            cachedNextBossId = "";
            return;
        }

        ScheduledBoss next = null;
        for (ScheduledBoss entry : schedule) {
            if (next == null || entry.nextAt().isBefore(next.nextAt())) {
                next = entry;
            }
        }
        if (next == null) {
            cachedNextSpawnMillis = -1L;
            cachedNextBossName = "";
            cachedNextBossId = "";
            return;
        }

        cachedNextSpawnMillis = next.nextAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        cachedNextBossName = next.displayName();
        cachedNextBossId = next.id();
    }

    public void spawnHoglin() {
        Location location = config.getHoglinLocation();
        if (location == null || location.getWorld() == null) {
            plugin.getLogger().warning("[Boss] Brak lokalizacji Hoglin — użyj /boss setlocation hoglin");
            return;
        }

        hoglinHandler.prepareSpawn(config.getHoglinMaxHealth(), config.getHoglinName());
        Hoglin hoglin = location.getWorld().spawn(location, Hoglin.class);
        hoglin.setGlowing(true);
        if (hoglin.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
            hoglin.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(1000.0D);
        }
        hoglin.setHealth(1000.0D);
        hoglin.setCustomNameVisible(true);
        hoglin.setImmuneToZombification(true);
        hoglinHandler.trackSpawned(hoglin);
        broadcastSpawn(config.getHoglinName(), config.getHoglinSpawnTitle(), config.getHoglinSpawnSubtitle());
        refreshNextSpawnCache();
    }

    public boolean spawnBoss(String typeId) {
        if (typeId == null) {
            return false;
        }
        if (typeId.equalsIgnoreCase("hoglin") || typeId.equalsIgnoreCase("HOGLIN") || typeId.equalsIgnoreCase("warthog")) {
            spawnHoglin();
            return true;
        }

        BossConfig.BossDefinition definition = config.getBoss(typeId);
        if (definition == null) {
            return false;
        }
        Location location = definition.location();
        if (location == null || location.getWorld() == null) {
            plugin.getLogger().warning("[Boss] Brak lokalizacji dla " + definition.id());
            return false;
        }

        Entity entity = location.getWorld().spawnEntity(location, definition.entityType());
        if (!(entity instanceof Mob mob)) {
            entity.remove();
            plugin.getLogger().warning("[Boss] Typ " + definition.entityType() + " nie jest mobem");
            return false;
        }

        mob.setGlowing(true);
        double health = Math.max(1.0D, definition.maxHealth());
        if (mob.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
            mob.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(health);
        }
        mob.setHealth(Math.min(health, mob.getMaxHealth()));
        mob.customName(ColorUtils.colorizeComponent(definition.name()));
        mob.setCustomNameVisible(true);
        trackedBosses.put(mob.getUniqueId(), definition.id());
        broadcastSpawn(definition.name(), definition.spawnTitle(), definition.spawnSubtitle());
        refreshNextSpawnCache();
        return true;
    }

    private void tickSpawns() {
        for (String hour : config.getHoglinHours()) {
            if (BossTimeUtil.isTime(hour)) {
                spawnHoglin();
            }
        }
        for (BossConfig.BossDefinition boss : config.getBosses().values()) {
            for (String hour : boss.hours()) {
                if (BossTimeUtil.isTime(hour)) {
                    spawnBoss(boss.id());
                }
            }
        }
        refreshNextSpawnCache();
    }

    private void tickCountdownBars() {
        long remaining = getMillisUntilNextBoss();
        if (remaining > TimeUnit.MINUTES.toMillis(30) || remaining == Long.MAX_VALUE) {
            clearCountdownBars();
            return;
        }

        String titleTemplate = resolveCountdownTitle();
        BossBar.Color color = resolveCountdownColor();
        float progress = Math.max(0.0f, Math.min(1.0f, (float) remaining / (float) TimeUnit.MINUTES.toMillis(30)));
        String title = titleTemplate.replace("{TIME}", TimeUtil.convert(remaining));

        for (Player player : Bukkit.getOnlinePlayers()) {
            BossBar bar = countdownBars.computeIfAbsent(player.getUniqueId(), uuid -> {
                BossBar created = BossBar.bossBar(
                    ColorUtils.colorizeComponent(title),
                    progress,
                    color,
                    BossBar.Overlay.NOTCHED_10
                );
                player.showBossBar(created);
                return created;
            });
            bar.name(ColorUtils.colorizeComponent(title));
            bar.progress(progress);
            bar.color(color);
            if (plugin.getPickupManager() != null) {
                plugin.getPickupManager().schedulePinToBottom(player);
            }
        }

        countdownBars.entrySet().removeIf(entry -> {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                return true;
            }
            return false;
        });
    }

    private String resolveCountdownTitle() {
        if ("HOGLIN".equalsIgnoreCase(cachedNextBossId)) {
            return config.getHoglinCountdownTitle();
        }
        BossConfig.BossDefinition definition = config.getBoss(cachedNextBossId);
        if (definition != null) {
            return definition.countdownTitle();
        }
        return "&fBoss pojawi się za &e{TIME}";
    }

    private BossBar.Color resolveCountdownColor() {
        String raw = "GREEN";
        if ("HOGLIN".equalsIgnoreCase(cachedNextBossId)) {
            raw = config.getHoglinCountdownColor();
        } else {
            BossConfig.BossDefinition definition = config.getBoss(cachedNextBossId);
            if (definition != null) {
                raw = definition.countdownColor();
            }
        }
        try {
            return BossBar.Color.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return BossBar.Color.GREEN;
        }
    }

    private void clearCountdownBars() {
        for (Map.Entry<UUID, BossBar> entry : countdownBars.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                player.hideBossBar(entry.getValue());
            }
        }
        countdownBars.clear();
    }

    private void broadcastSpawn(String name, String titleOverride, String subtitleOverride) {
        String safeName = name == null ? "" : name;
        String titleRaw = firstNonBlank(titleOverride, config.getSpawnTitle()).replace("{NAME}", safeName);
        String subtitleRaw = firstNonBlank(subtitleOverride, config.getSpawnSubtitle()).replace("{NAME}", safeName);
        Title title = Title.title(
            ColorUtils.colorizeComponent(titleRaw),
            ColorUtils.colorizeComponent(subtitleRaw),
            Title.Times.times(
                Duration.ofMillis(config.getSpawnTitleFadeIn()),
                Duration.ofMillis(config.getSpawnTitleStay()),
                Duration.ofMillis(config.getSpawnTitleFadeOut())
            )
        );
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showTitle(title);
        }
    }

    private static String firstNonBlank(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        return fallback == null ? "" : fallback;
    }

    private List<ScheduledBoss> buildSchedule() {
        List<ScheduledBoss> schedule = new ArrayList<>();
        LocalDateTime hoglinNext = BossTimeUtil.nextOccurrence(config.getHoglinHours());
        if (hoglinNext != null) {
            schedule.add(new ScheduledBoss("HOGLIN", config.getHoglinName(), hoglinNext));
        }
        for (BossConfig.BossDefinition boss : config.getBosses().values()) {
            LocalDateTime next = BossTimeUtil.nextOccurrence(boss.hours());
            if (next != null) {
                schedule.add(new ScheduledBoss(boss.id(), boss.name(), next));
            }
        }
        return schedule;
    }

    private record ScheduledBoss(String id, String displayName, LocalDateTime nextAt) {
    }
}
