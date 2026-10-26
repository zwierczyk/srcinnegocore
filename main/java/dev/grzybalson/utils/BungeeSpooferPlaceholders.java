package dev.grzybalson.utils;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Podmienia %bungee_*% — suma wszystkich trybów z Redis; pojedynczy serwer też z Redis. */
public final class BungeeSpooferPlaceholders {

    private static final Pattern BUNGEE_PATTERN = Pattern.compile("%bungee_([A-Za-z0-9_\\-]+)%");

    private BungeeSpooferPlaceholders() {
    }

    public static String apply(Player player, String text) {
        return apply((OfflinePlayer) player, text);
    }

    public static String apply(OfflinePlayer player, String text) {
        if (text == null || !text.contains("%bungee_")) {
            return text;
        }
        Matcher matcher = BUNGEE_PATTERN.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String replacement = resolve(player, matcher.group(1));
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    public static String resolveCount(OfflinePlayer player, String params) {
        return resolve(player, params);
    }

    private static String resolve(OfflinePlayer player, String params) {
        String key = params.toLowerCase(Locale.ROOT);
        if ("total".equals(key) || "all".equals(key) || "online".equals(key)) {
            int network = SpooferCountHelper.getNetworkOnlineCount();
            if (network >= 0) {
                return String.valueOf(network);
            }
            return String.valueOf(Math.max(0, baseCount(player, "total")));
        }
        int visible = SpooferCountHelper.getServerVisibleCount(key);
        if (visible >= 0) {
            return String.valueOf(visible);
        }
        return String.valueOf(Math.max(0, baseCount(player, key)));
    }

    private static int baseCount(OfflinePlayer player, String params) {
        if (!(player instanceof Player online)) {
            return 0;
        }
        String token = "%bungee_" + params + "%";
        String resolved = PlaceholderAPI.setPlaceholders(online, token);
        if (token.equals(resolved)) {
            return 0;
        }
        return parseCount(resolved);
    }

    private static int parseCount(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(value.trim()));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
