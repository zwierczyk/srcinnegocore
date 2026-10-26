package dev.grzybalson.utils;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.configs.LpcChatConfig;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Blokada reklam: Discord, linki, IP oraz typowe frazy (gz, mody na kopanie, YT).
 * Normalizuje leetspeak / spacje w domenach (np. d i s c o r d . g g).
 */
public final class ChatAdFilter {

    private static final Pattern ZERO_WIDTH = Pattern.compile("[\\u200B-\\u200D\\uFEFF\\u00AD]");
    private static final Pattern NON_ALNUM_DOT = Pattern.compile("[^a-z0-9./]+");
    private static final Pattern MULTI_SPACE = Pattern.compile("\\s+");

    private static final Pattern DISCORD = Pattern.compile(
        "(?:discord(?:app)?(?:\\.com)?/?invite|discord(?:app)?\\.(?:gg|com|me)|(?:dc|dsc)\\.gg)"
    );
    private static final Pattern URL_SCHEME = Pattern.compile("(?:https?://|www\\.)");
    private static final Pattern DOMAIN = Pattern.compile(
        "(?:^|[^a-z0-9])[a-z0-9][a-z0-9-]{0,61}[a-z0-9]?\\."
            + "(?:gg|com|pl|net|org|xyz|io|me|tv|ru|info|online|store|shop|eu|uk|cz|sk|de|fr|nl|"
            + "co|us|cc|tk|ml|ga|cf|top|club|site|fun|pro|live|app|dev|biz)"
            + "(?:/|\\b)"
    );
    private static final Pattern IPV4 = Pattern.compile(
        "(?:^|[^0-9])(?:\\d{1,3}\\.){3}\\d{1,3}(?::\\d{2,5})?(?:[^0-9]|$)"
    );
    private static final Pattern YOUTUBE = Pattern.compile(
        "(?:youtube\\.com|youtu\\.be|yt\\.be|(?:^|[^a-z])yt\\.[a-z]{2,})"
    );

    private static volatile Needles NEEDLE_CACHE;

    private ChatAdFilter() {
    }

    public static boolean rejectIfBlocked(HollyCore plugin, Player player, String message) {
        if (plugin == null || player == null || message == null || message.isBlank()) {
            return false;
        }
        LpcChatConfig config = plugin.getConfigManager() != null
            ? plugin.getConfigManager().getLpcChatConfig()
            : null;
        if (config == null || !config.isAntiAdEnabled()) {
            return false;
        }
        String bypass = config.getBypassAntiAdPermission();
        if (bypass != null && !bypass.isBlank() && player.hasPermission(bypass)) {
            return false;
        }
        if (!isBlocked(message, config)) {
            return false;
        }
        player.sendMessage(ColorUtils.colorizeComponent(config.getAntiAdMessage()));
        return true;
    }

    public static boolean isBlocked(String message, LpcChatConfig config) {
        if (message == null || message.isBlank() || config == null) {
            return false;
        }

        String soft = softNormalize(message);
        String compact = compactNormalize(message);
        List<String> allowed = config.getAntiAdAllowedDomains();
        String softScrubbed = scrubAllowed(soft, allowed);
        String compactScrubbed = scrubAllowed(compact, allowed);

        if (config.isAntiAdBlockDiscord()
            && (DISCORD.matcher(compactScrubbed).find()
            || DISCORD.matcher(softScrubbed.replace(" ", "")).find())) {
            return true;
        }

        if (config.isAntiAdBlockLinks()) {
            if (URL_SCHEME.matcher(compactScrubbed).find() || URL_SCHEME.matcher(softScrubbed).find()) {
                return true;
            }
            if (YOUTUBE.matcher(compactScrubbed).find()) {
                return true;
            }
            if (IPV4.matcher(compactScrubbed).find() || IPV4.matcher(softScrubbed).find()) {
                return true;
            }
            if (DOMAIN.matcher(compactScrubbed).find()
                || DOMAIN.matcher(softScrubbed.replace(" ", "")).find()) {
                return true;
            }
        }

        List<String> phrases = config.getAntiAdBlockedPhrases();
        if (phrases != null) {
            Needles needles = resolveNeedles(phrases);
            for (int i = 0; i < needles.soft.length; i++) {
                String needle = needles.soft[i];
                if (needle != null && soft.contains(needle)) {
                    return true;
                }
                String compactNeedle = needles.compact[i];
                if (compactNeedle != null && compact.contains(compactNeedle)) {
                    return true;
                }
            }
        }

        return false;
    }

    /** Znormalizowane frazy z configu — przeliczane dopiero gdy lista faktycznie się zmieni. */
    private static Needles resolveNeedles(List<String> phrases) {
        Needles cached = NEEDLE_CACHE;
        if (cached != null && cached.source.equals(phrases)) {
            return cached;
        }
        List<String> snapshot = new ArrayList<>(phrases);
        String[] soft = new String[snapshot.size()];
        String[] compact = new String[snapshot.size()];
        for (int i = 0; i < snapshot.size(); i++) {
            String phrase = snapshot.get(i);
            if (phrase == null || phrase.isBlank()) {
                continue;
            }
            String needle = softNormalize(phrase);
            if (!needle.isBlank()) {
                soft[i] = needle;
            }
            String compactNeedle = compactNormalize(phrase);
            if (!compactNeedle.isBlank() && compactNeedle.length() >= 4) {
                compact[i] = compactNeedle;
            }
        }
        Needles built = new Needles(Collections.unmodifiableList(snapshot), soft, compact);
        NEEDLE_CACHE = built;
        return built;
    }

    private static final class Needles {
        private final List<String> source;
        private final String[] soft;
        private final String[] compact;

        private Needles(List<String> source, String[] soft, String[] compact) {
            this.source = source;
            this.soft = soft;
            this.compact = compact;
        }
    }

    private static String scrubAllowed(String haystack, List<String> allowed) {
        if (haystack == null || haystack.isBlank() || allowed == null || allowed.isEmpty()) {
            return haystack == null ? "" : haystack;
        }
        String out = haystack;
        for (String domain : allowed) {
            if (domain == null || domain.isBlank()) {
                continue;
            }
            String d = domain.toLowerCase(Locale.ROOT).trim();
            out = out.replace(d, "");
            out = out.replace(d.replace(".", ""), "");
        }
        return out;
    }

    /** Spacje zostają — do fraz typu \"gz najlepszy\". */
    static String softNormalize(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        String text = ZERO_WIDTH.matcher(input).replaceAll("");
        text = text.toLowerCase(Locale.ROOT);
        text = applyLeet(text);
        text = text.replace(',', '.').replace('，', '.').replace('․', '.');
        text = text.replace("[dot]", ".").replace("(dot)", ".").replace("{dot}", ".");
        text = MULTI_SPACE.matcher(text).replaceAll(" ").trim();
        return text;
    }

    /** Bez spacji / znaków specjalnych — do discord.gg / linków. */
    static String compactNormalize(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        String text = softNormalize(input);
        text = text.replace(" ", "");
        text = NON_ALNUM_DOT.matcher(text).replaceAll("");
        return text;
    }

    private static String applyLeet(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            out.append(switch (c) {
                case '0' -> 'o';
                case '1' -> 'i';
                case '3' -> 'e';
                case '4' -> 'a';
                case '5' -> 's';
                case '7' -> 't';
                case '@' -> 'a';
                case '$' -> 's';
                case '€' -> 'e';
                default -> c;
            });
        }
        return out.toString();
    }
}
