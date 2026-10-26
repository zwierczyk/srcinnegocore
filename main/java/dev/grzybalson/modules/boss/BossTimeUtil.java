package dev.grzybalson.modules.boss;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

public final class BossTimeUtil {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private BossTimeUtil() {
    }

    public static boolean isTime(String time) {
        return LocalTime.now().format(FORMATTER).equals(time);
    }

    public static LocalDateTime nextOccurrence(String time) {
        LocalTime target = LocalTime.parse(time, FORMATTER);
        LocalDateTime candidate = LocalDateTime.of(LocalDate.now(), target);
        if (!candidate.isAfter(LocalDateTime.now())) {
            candidate = candidate.plusDays(1);
        }
        return candidate;
    }

    public static String getNextClosestTime(List<String> times) {
        if (times == null || times.isEmpty()) {
            return null;
        }
        return times.stream()
            .filter(time -> time != null && !time.isBlank())
            .map(BossTimeUtil::nextOccurrence)
            .min(Comparator.naturalOrder())
            .map(value -> value.toLocalTime().format(FORMATTER))
            .orElse(null);
    }

    public static long getMillisUntil(String timeString) {
        if (timeString == null || timeString.isBlank()) {
            return Long.MAX_VALUE;
        }
        return Duration.between(LocalDateTime.now(), nextOccurrence(timeString)).toMillis();
    }

    public static LocalDateTime nextOccurrence(List<String> times) {
        if (times == null || times.isEmpty()) {
            return null;
        }
        return times.stream()
            .filter(time -> time != null && !time.isBlank())
            .map(BossTimeUtil::nextOccurrence)
            .min(Comparator.naturalOrder())
            .orElse(null);
    }
}
