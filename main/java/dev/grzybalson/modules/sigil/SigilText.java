package dev.grzybalson.modules.sigil;

import java.util.Locale;

public final class SigilText {

    private static final char[] SMALL_CAPS = {
        'ᴀ', 'ʙ', 'ᴄ', 'ᴅ', 'ᴇ', 'ꜰ', 'ɢ', 'ʜ', 'ɪ', 'ᴊ', 'ᴋ', 'ʟ', 'ᴍ',
        'ɴ', 'ᴏ', 'ᴘ', 'ǫ', 'ʀ', 'ꜱ', 'ᴛ', 'ᴜ', 'ᴠ', 'ᴡ', 'x', 'ʏ', 'ᴢ'
    };

    private SigilText() {
    }

    public static String toSmallCaps(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            builder.append(mapChar(input.charAt(i)));
        }
        return builder.toString();
    }

    /** Pogrubiony gradient 2 hexów przez small-caps (legacy &x&R&G...&l). */
    public static String gradientBoldTitle(String text, String startHex, String endHex) {
        String smallCaps = toSmallCaps(text);
        if (smallCaps.isEmpty()) {
            return "";
        }
        int[] start = parseHex(startHex);
        int[] end = parseHex(endHex);
        int steps = Math.max(1, smallCaps.length() - 1);
        StringBuilder builder = new StringBuilder(smallCaps.length() * 16);
        for (int i = 0; i < smallCaps.length(); i++) {
            double ratio = (double) i / steps;
            int red = (int) Math.round(start[0] + (end[0] - start[0]) * ratio);
            int green = (int) Math.round(start[1] + (end[1] - start[1]) * ratio);
            int blue = (int) Math.round(start[2] + (end[2] - start[2]) * ratio);
            builder.append(toLegacyHex(red, green, blue)).append("&l").append(smallCaps.charAt(i));
        }
        return builder.toString();
    }

    private static char mapChar(char c) {
        return switch (c) {
            case 'ą', 'Ą' -> 'ᴀ';
            case 'ć', 'Ć' -> 'ᴄ';
            case 'ę', 'Ę' -> 'ᴇ';
            case 'ł', 'Ł' -> 'ʟ';
            case 'ń', 'Ń' -> 'ɴ';
            case 'ó', 'Ó' -> 'ᴏ';
            case 'ś', 'Ś' -> 'ꜱ';
            case 'ź', 'Ź', 'ż', 'Ż' -> 'ᴢ';
            default -> {
                if (c >= 'A' && c <= 'Z') {
                    yield SMALL_CAPS[c - 'A'];
                }
                if (c >= 'a' && c <= 'z') {
                    yield SMALL_CAPS[c - 'a'];
                }
                yield c;
            }
        };
    }

    private static int[] parseHex(String value) {
        String normalized = value == null ? "" : value.replace("#", "").replace("&", "");
        if (normalized.length() != 6) {
            return new int[]{255, 255, 255};
        }
        try {
            return new int[]{
                Integer.parseInt(normalized.substring(0, 2), 16),
                Integer.parseInt(normalized.substring(2, 4), 16),
                Integer.parseInt(normalized.substring(4, 6), 16)
            };
        } catch (NumberFormatException ignored) {
            return new int[]{255, 255, 255};
        }
    }

    private static String toLegacyHex(int red, int green, int blue) {
        String hex = String.format(Locale.ROOT, "%02X%02X%02X", red, green, blue);
        StringBuilder builder = new StringBuilder("&x");
        for (char character : hex.toCharArray()) {
            builder.append('&').append(character);
        }
        return builder.toString();
    }

    public static String normalizeKey(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }
}
