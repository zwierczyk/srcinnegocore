package dev.grzybalson.modules.bossbar;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.modules.bossbar.BossBarConfig;
import dev.grzybalson.modules.generator.Generator;
import org.bukkit.Location;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

public final class ScheduledDropService {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("H:mm");

    private ScheduledDropService() {
    }

    public static double getMultiplier(HollyCore plugin, Location location) {
        BossBarConfig.ScheduledDropSettings settings = settings(plugin);
        if (settings == null || !settings.isEnabled() || !isEventActive(settings)) {
            return 1.0D;
        }
        if (!isEventWorld(location, settings)) {
            return 1.0D;
        }
        return isSafeZone(plugin, location, settings)
            ? Math.max(0.0D, settings.getSafeZoneMultiplier())
            : Math.max(0.0D, settings.getMapMultiplier());
    }

    public static String applyPlaceholders(HollyCore plugin, String text) {
        if (text == null || text.isEmpty() || plugin == null) {
            return text == null ? "" : text;
        }
        if (!text.contains("{DROP_") && !text.contains("{drop_")) {
            return text;
        }
        BossBarConfig.ScheduledDropSettings settings = settings(plugin);
        boolean event = settings != null && settings.isEnabled() && isEventActive(settings);
        String safe = formatMultiplier(event ? settings.getSafeZoneMultiplier() : 1.0D);
        String map = formatMultiplier(event ? settings.getMapMultiplier() : 1.0D);
        String until = formatClock(nextPhaseTime(settings, event));
        String left = formatRemaining(remaining(settings, event));
        String line = event
            ? settings.getBossbarEvent()
            : settings.getBossbarNormal();
        line = replaceDropTokens(line, safe, map, until, left);
        return replaceDropTokens(text, safe, map, until, left).replace("{DROP_LINE}", line).replace("{drop_line}", line);
    }

    public static double getProgress(HollyCore plugin) {
        BossBarConfig.ScheduledDropSettings settings = settings(plugin);
        if (settings == null || !settings.isEnabled()) {
            return 1.0D;
        }
        boolean event = isEventActive(settings);
        Duration total = phaseDuration(settings, event);
        Duration left = remaining(settings, event);
        if (total.isZero() || total.isNegative()) {
            return 1.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, left.toMillis() / (double) total.toMillis()));
    }

    public static boolean usesDropPlaceholders(String text) {
        return text != null && (text.contains("{DROP_") || text.contains("{drop_"));
    }

    private static String replaceDropTokens(String text, String safe, String map, String until, String left) {
        if (text == null) {
            return "";
        }
        return text
            .replace("{DROP_SAFE}", safe)
            .replace("{drop_safe}", safe)
            .replace("{DROP_MAP}", map)
            .replace("{drop_map}", map)
            .replace("{DROP_UNTIL}", until)
            .replace("{drop_until}", until)
            .replace("{DROP_LEFT}", left)
            .replace("{drop_left}", left);
    }

    private static BossBarConfig.ScheduledDropSettings settings(HollyCore plugin) {
        if (plugin == null || plugin.getModuleConfigManager() == null || plugin.getModuleConfigManager().getBossBarConfig() == null) {
            return null;
        }
        return plugin.getModuleConfigManager().getBossBarConfig().getScheduledDrop();
    }

    static boolean isEventActive(BossBarConfig.ScheduledDropSettings settings) {
        if (settings == null) {
            return false;
        }
        LocalTime now = now(settings).toLocalTime();
        LocalTime start = parseTime(settings.getEventStart(), LocalTime.of(12, 0));
        LocalTime lastMinute = parseTime(settings.getEventEnd(), LocalTime.of(23, 0));
        LocalTime endExclusive = lastMinute.plusMinutes(1);
        if (start.equals(endExclusive)) {
            return false;
        }
        if (start.isBefore(endExclusive)) {
            return !now.isBefore(start) && now.isBefore(endExclusive);
        }
        return !now.isBefore(start) || now.isBefore(endExclusive);
    }

    private static boolean isEventWorld(Location location, BossBarConfig.ScheduledDropSettings settings) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        List<String> worlds = settings.getEventWorlds();
        if (worlds == null || worlds.isEmpty()) {
            return false;
        }
        String worldName = location.getWorld().getName();
        for (String configured : worlds) {
            if (configured != null && configured.equalsIgnoreCase(worldName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSafeZone(HollyCore plugin, Location location, BossBarConfig.ScheduledDropSettings settings) {
        if (location == null) {
            return false;
        }
        if (plugin.getGeneratorManager() != null) {
            Generator generator = plugin.getGeneratorManager().findGeneratorAt(location);
            if (generator != null && generator.getHologram() != null && generator.getHologram().getName() != null) {
                String name = generator.getHologram().getName().toLowerCase(Locale.ROOT);
                for (String prefix : prefixes(settings)) {
                    if (name.startsWith(prefix)) {
                        return true;
                    }
                }
                return false;
            }
        }
        return false;
    }

    private static List<String> prefixes(BossBarConfig.ScheduledDropSettings settings) {
        List<String> configured = settings.getSafeGeneratorPrefixes();
        if (configured == null || configured.isEmpty()) {
            return List.of("bezpieczna");
        }
        return configured.stream()
            .filter(value -> value != null && !value.isBlank())
            .map(value -> value.toLowerCase(Locale.ROOT))
            .toList();
    }

    private static ZonedDateTime now(BossBarConfig.ScheduledDropSettings settings) {
        return ZonedDateTime.now(zone(settings));
    }

    private static ZoneId zone(BossBarConfig.ScheduledDropSettings settings) {
        try {
            return ZoneId.of(settings.getTimezone() == null || settings.getTimezone().isBlank()
                ? "Europe/Warsaw"
                : settings.getTimezone().trim());
        } catch (RuntimeException ignored) {
            return ZoneId.of("Europe/Warsaw");
        }
    }

    private static LocalTime parseTime(String raw, LocalTime fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return LocalTime.parse(raw.trim(), TIME_FORMAT);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalTime.parse(raw.trim());
            } catch (DateTimeParseException ignoredAgain) {
                return fallback;
            }
        }
    }

    private static LocalTime nextPhaseTime(BossBarConfig.ScheduledDropSettings settings, boolean event) {
        if (settings == null) {
            return LocalTime.of(12, 0);
        }
        return event
            ? parseTime(settings.getEventEnd(), LocalTime.of(23, 0))
            : parseTime(settings.getEventStart(), LocalTime.of(12, 0));
    }

    private static Duration remaining(BossBarConfig.ScheduledDropSettings settings, boolean event) {
        ZonedDateTime now = now(settings);
        LocalTime target = event
            ? parseTime(settings.getEventEnd(), LocalTime.of(23, 0)).plusMinutes(1)
            : parseTime(settings.getEventStart(), LocalTime.of(12, 0));
        ZonedDateTime until = now.with(target).withSecond(0).withNano(0);
        if (!until.isAfter(now)) {
            until = until.plusDays(1);
        }
        return Duration.between(now, until);
    }

    private static Duration phaseDuration(BossBarConfig.ScheduledDropSettings settings, boolean event) {
        LocalTime start = parseTime(settings.getEventStart(), LocalTime.of(12, 0));
        LocalTime endExclusive = parseTime(settings.getEventEnd(), LocalTime.of(23, 0)).plusMinutes(1);
        Duration eventDuration = durationBetween(start, endExclusive);
        Duration day = Duration.ofDays(1);
        Duration normal = day.minus(eventDuration);
        return event ? eventDuration : (normal.isZero() || normal.isNegative() ? day : normal);
    }

    private static Duration durationBetween(LocalTime from, LocalTime to) {
        Duration duration = Duration.between(from, to);
        if (duration.isNegative() || duration.isZero()) {
            duration = duration.plusDays(1);
        }
        return duration;
    }

    private static String formatMultiplier(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0001D) {
            return "x" + (long) Math.rint(value);
        }
        String formatted = String.format(Locale.US, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
        return "x" + formatted;
    }

    private static String formatClock(LocalTime time) {
        return time == null ? "" : time.format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    private static String formatRemaining(Duration duration) {
        long totalSeconds = Math.max(0L, duration.toSeconds());
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        if (hours > 0L) {
            return hours + "h " + minutes + "m";
        }
        long seconds = totalSeconds % 60L;
        if (minutes > 0L) {
            return minutes + "m " + seconds + "s";
        }
        return seconds + "s";
    }
}
