package dev.grzybalson.managers;

import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ColorUtils;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AutoTitleManager {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("H:mm");
    private static final Pattern INTERVAL_PATTERN = Pattern.compile("^(\\d+)(s|m|min|sek|minut)?$", Pattern.CASE_INSENSITIVE);

    private final HollyCore plugin;
    private ScheduledTitle active;
    private BukkitTask ticker;

    public AutoTitleManager(HollyCore plugin) {
        this.plugin = plugin;
    }

    public boolean schedule(String rawText, String startTime, String endTime, String intervalRaw) {
        cancel();
        LocalTime start = parseTime(startTime);
        LocalTime end = parseTime(endTime);
        long intervalTicks = parseIntervalTicks(intervalRaw);
        if (start == null || end == null || intervalTicks <= 0L || rawText == null || rawText.isBlank()) {
            return false;
        }

        String titleText = rawText.trim();
        if (titleText.toLowerCase(Locale.ROOT).startsWith("/bc title ")) {
            titleText = titleText.substring("/bc title ".length()).trim();
        } else if (titleText.toLowerCase(Locale.ROOT).startsWith("bc title ")) {
            titleText = titleText.substring("bc title ".length()).trim();
        }

        this.active = new ScheduledTitle(titleText, start, end, intervalTicks);
        this.ticker = new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(plugin, 20L, 20L);
        return true;
    }

    public void cancel() {
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
        active = null;
    }

    public boolean hasActive() {
        return active != null;
    }

    public void shutdown() {
        cancel();
    }

    private void tick() {
        if (active == null) {
            return;
        }
        ZonedDateTime now = ZonedDateTime.now(WARSAW);
        LocalTime current = now.toLocalTime();
        LocalTime windowStart = active.start();
        LocalTime windowEnd = active.end();
        if (windowEnd.equals(windowStart)) {
            windowEnd = windowStart.plusMinutes(1);
        }
        if (current.isBefore(windowStart) || !current.isBefore(windowEnd)) {
            return;
        }
        long nowSecond = now.toEpochSecond();
        long intervalSeconds = Math.max(1L, active.intervalTicks() / 20L);
        if (active.lastSentSecond() >= 0 && nowSecond - active.lastSentSecond() < intervalSeconds) {
            return;
        }
        broadcastTitle(active.text());
        active.lastSentSecond(nowSecond);
    }

    private void broadcastTitle(String subtitle) {
        Title.Times times = Title.Times.times(
            Duration.ofMillis(500),
            Duration.ofMillis(3000),
            Duration.ofMillis(500)
        );
        for (Player player : Bukkit.getOnlinePlayers()) {
            String resolved = subtitle == null ? "" : subtitle.replace("%player%", player.getName());
            player.showTitle(Title.title(
                ColorUtils.colorizeComponent(" "),
                ColorUtils.colorizeComponent(resolved),
                times
            ));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 1.2f);
        }
    }

    private LocalTime parseTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(raw.trim(), TIME_FORMAT);
        } catch (Exception ignored) {
            return null;
        }
    }

    private long parseIntervalTicks(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0L;
        }
        Matcher matcher = INTERVAL_PATTERN.matcher(raw.trim().toLowerCase(Locale.ROOT));
        if (!matcher.matches()) {
            return 0L;
        }
        long amount = Long.parseLong(matcher.group(1));
        String unit = matcher.group(2);
        if (unit == null || unit.isBlank() || unit.startsWith("s") || unit.startsWith("sek")) {
            return amount * 20L;
        }
        return amount * 60L * 20L;
    }

    private static final class ScheduledTitle {
        private final String text;
        private final LocalTime start;
        private final LocalTime end;
        private final long intervalTicks;
        private long lastSentSecond = -1L;

        private ScheduledTitle(String text, LocalTime start, LocalTime end, long intervalTicks) {
            this.text = text;
            this.start = start;
            this.end = end;
            this.intervalTicks = intervalTicks;
        }

        private String text() {
            return text;
        }

        private LocalTime start() {
            return start;
        }

        private LocalTime end() {
            return end;
        }

        private long intervalTicks() {
            return intervalTicks;
        }

        private long lastSentSecond() {
            return lastSentSecond;
        }

        private void lastSentSecond(long value) {
            this.lastSentSecond = value;
        }
    }
}
