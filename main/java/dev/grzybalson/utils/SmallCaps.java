package dev.grzybalson.utils;

public final class SmallCaps {

    private SmallCaps() {
    }

    public static String convert(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        StringBuilder result = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            result.append(map(codePoint));
            i += Character.charCount(codePoint);
        }
        return result.toString();
    }

    private static String map(int codePoint) {
        char ch = Character.toUpperCase((char) codePoint);
        return switch (ch) {
            case 'A', 'Ą', 'Á', 'À', 'Ä' -> "ᴀ";
            case 'B' -> "ʙ";
            case 'C', 'Ć', 'Ç' -> "ᴄ";
            case 'D' -> "ᴅ";
            case 'E', 'Ę', 'É', 'È', 'Ë' -> "ᴇ";
            case 'F' -> "ꜰ";
            case 'G' -> "ɢ";
            case 'H' -> "ʜ";
            case 'I', 'Í', 'Ì', 'Ï' -> "ɪ";
            case 'J' -> "ᴊ";
            case 'K' -> "ᴋ";
            case 'L', 'Ł' -> "ʟ";
            case 'M' -> "ᴍ";
            case 'N', 'Ń' -> "ɴ";
            case 'O', 'Ó', 'Ò', 'Ö' -> "ᴏ";
            case 'P' -> "ᴘ";
            case 'Q' -> "ǫ";
            case 'R' -> "ʀ";
            case 'S', 'Ś', 'Ş' -> "ꜱ";
            case 'T' -> "ᴛ";
            case 'U', 'Ú', 'Ù', 'Ü' -> "ᴜ";
            case 'V' -> "ᴠ";
            case 'W' -> "ᴡ";
            case 'X' -> "x";
            case 'Y', 'Ý' -> "ʏ";
            case 'Z', 'Ź', 'Ż' -> "ᴢ";
            default -> Character.toString(codePoint);
        };
    }
}
