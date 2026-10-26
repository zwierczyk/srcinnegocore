package dev.grzybalson.anarchiagg.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TimeUtil {
    private static final Pattern timePattern = Pattern.compile("(?:([0-9]+)\\s*y[a-z]*[,\\s]*)?(?:([0-9]+)\\s*mo[a-z]*[,\\s]*)?(?:([0-9]+)\\s*w[a-z]*[,\\s]*)?(?:([0-9]+)\\s*d[a-z]*[,\\s]*)?(?:([0-9]+)\\s*h[a-z]*[,\\s]*)?(?:([0-9]+)\\s*m[a-z]*[,\\s]*)?(?:([0-9]+)\\s*(?:s[a-z]*)?)?", 2);
    private static final Pattern TIME_PATTERN = Pattern.compile("(\\d+)([smhdw])");

    public static String convert(long millis) {
        return TimeUtil.convert(millis, true);
    }

    public static String convert(long millis, boolean showSeconds) {
        if (millis < 1000L) {
            return millis + "ms";
        }
        long days = TimeUnit.MILLISECONDS.toDays(millis);
        long hours = TimeUnit.MILLISECONDS.toHours(millis -= TimeUnit.DAYS.toMillis(days));
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis -= TimeUnit.HOURS.toMillis(hours));
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis -= TimeUnit.MINUTES.toMillis(minutes));
        StringBuilder sb = new StringBuilder();
        if (days > 0L) {
            sb.append(days).append("d");
        }
        if (hours > 0L) {
            if (days > 0L) {
                sb.append(" ");
            }
            sb.append(hours).append("h");
        }
        if (minutes > 0L) {
            if (hours > 0L || days > 0L) {
                sb.append(" ");
            }
            sb.append(minutes).append("m");
        }
        if ((showSeconds || sb.length() == 0) && seconds > 0L) {
            if (minutes > 0L || hours > 0L || days > 0L) {
                sb.append(" ");
            }
            sb.append(seconds).append("s");
        }
        return sb.toString();
    }

    public static String convertPolish(long millis) {
        if (millis < 1000L) {
            return "<1s";
        }
        long days = TimeUnit.MILLISECONDS.toDays(millis);
        long hours = TimeUnit.MILLISECONDS.toHours(millis -= TimeUnit.DAYS.toMillis(days));
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis -= TimeUnit.HOURS.toMillis(hours));
        StringBuilder sb = new StringBuilder();
        if (days > 0L) {
            sb.append("&f").append(days).append(" &7dni&f");
        }
        if (hours > 0L) {
            if (days > 0L) {
                sb.append(" ");
            }
            sb.append("&f").append(hours).append(" &7godz.&f");
        }
        if (minutes > 0L) {
            if (hours > 0L || days > 0L) {
                sb.append(" ");
            }
            sb.append("&f").append(minutes).append(" &7min.&7");
        }
        return sb.toString();
    }

    public static String getDate(long data) {
        return new SimpleDateFormat("HH:mm:ss dd/MM/yyyy").format(data);
    }

    public static long parseDateDiff(String time, boolean future) {
        try {
            Matcher m = timePattern.matcher(time);
            int years = 0;
            int months = 0;
            int weeks = 0;
            int days = 0;
            int hours = 0;
            int minutes = 0;
            int seconds = 0;
            boolean found = false;
            while (m.find()) {
                if (m.group() == null || m.group().isEmpty()) continue;
                for (int i = 0; i < m.groupCount(); ++i) {
                    if (m.group(i) == null || m.group(i).isEmpty()) continue;
                    found = true;
                    break;
                }
                if (!found) continue;
                if (m.group(1) != null && !m.group(1).isEmpty()) {
                    years = Integer.parseInt(m.group(1));
                }
                if (m.group(2) != null && !m.group(2).isEmpty()) {
                    months = Integer.parseInt(m.group(2));
                }
                if (m.group(3) != null && !m.group(3).isEmpty()) {
                    weeks = Integer.parseInt(m.group(3));
                }
                if (m.group(4) != null && !m.group(4).isEmpty()) {
                    days = Integer.parseInt(m.group(4));
                }
                if (m.group(5) != null && !m.group(5).isEmpty()) {
                    hours = Integer.parseInt(m.group(5));
                }
                if (m.group(6) != null && !m.group(6).isEmpty()) {
                    minutes = Integer.parseInt(m.group(6));
                }
                if (m.group(7) == null || m.group(7).isEmpty()) break;
                seconds = Integer.parseInt(m.group(7));
                break;
            }
            if (!found) {
                return -1L;
            }
            GregorianCalendar c = new GregorianCalendar();
            if (years > 0) {
                ((Calendar)c).add(1, years);
            }
            if (months > 0) {
                ((Calendar)c).add(2, months);
            }
            if (weeks > 0) {
                ((Calendar)c).add(3, weeks);
            }
            if (days > 0) {
                ((Calendar)c).add(5, days);
            }
            if (hours > 0) {
                ((Calendar)c).add(11, hours);
            }
            if (minutes > 0) {
                ((Calendar)c).add(12, minutes);
            }
            if (seconds > 0) {
                ((Calendar)c).add(13, seconds);
            }
            return c.getTimeInMillis() - (future ? 0L : System.currentTimeMillis());
        } catch (Exception e) {
            return -1L;
        }
    }

    public static long parseDate(String data) {
        try {
            return new SimpleDateFormat("HH:mm:ss dd/MM/yyyy").parse(data).getTime();
        } catch (ParseException e) {
            return 0L;
        }
    }

    public static Instant parseTime(String timeString) {
        if (timeString.equalsIgnoreCase("perm") || timeString.equalsIgnoreCase("permanent")) {
            return null;
        }
        Matcher matcher = TIME_PATTERN.matcher(timeString.toLowerCase());
        long totalSeconds = 0L;
        while (matcher.find()) {
            String unit;
            int value = Integer.parseInt(matcher.group(1));
            switch (unit = matcher.group(2)) {
                case "s": {
                    totalSeconds += (long)value;
                    break;
                }
                case "m": {
                    totalSeconds += (long)value * 60L;
                    break;
                }
                case "h": {
                    totalSeconds += (long)value * 3600L;
                    break;
                }
                case "d": {
                    totalSeconds += (long)value * 86400L;
                    break;
                }
                case "w": {
                    totalSeconds += (long)value * 604800L;
                }
            }
        }
        return Instant.now().plusSeconds(totalSeconds);
    }

    public static String formatTimeLeft(Instant expiresAt) {
        Duration duration = Duration.between(Instant.now(), expiresAt);
        long days = duration.toDays();
        long hours = duration.toHoursPart();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();
        StringBuilder result = new StringBuilder();
        if (days > 0L) {
            result.append(days).append("d ");
        }
        if (hours > 0L) {
            result.append(hours).append("h ");
        }
        if (minutes > 0L) {
            result.append(minutes).append("m ");
        }
        if (seconds > 0L && days == 0L) {
            result.append(seconds).append("s");
        }
        return result.toString().trim();
    }
}
