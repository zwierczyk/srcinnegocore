package dev.grzybalson.modules.backup;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Map;

/**
 * Zamienia ozdobne nazwy przedmiotów (small caps, pogrubiony unicode, fullwidth, emoji)
 * na zwykły tekst, który da się narysować czcionką Minecrafta.
 */
public final class PlainText {

    private static final Map<Integer, Character> SPECIAL = new HashMap<>();

    static {
        String smallCaps = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘqʀꜱᴛᴜᴠᴡxʏᴢ";
        String latin = "abcdefghijklmnopqrstuvwxyz";
        for (int i = 0; i < latin.length(); i++) {
            SPECIAL.put((int) smallCaps.charAt(i), latin.charAt(i));
        }
        SPECIAL.put(0x01EB, 'q');
        SPECIAL.put(0x0262, 'g');
        SPECIAL.put(0x0274, 'n');
        SPECIAL.put(0x0280, 'r');
        SPECIAL.put(0x0299, 'b');
        SPECIAL.put(0x029C, 'h');
        SPECIAL.put(0x029F, 'l');
        SPECIAL.put(0x026A, 'i');
        SPECIAL.put(0x028F, 'y');
        SPECIAL.put(0x0281, 'r');
        SPECIAL.put(0x1D07, 'e');
        SPECIAL.put(0x0142, 'l');
        SPECIAL.put(0x0141, 'L');
        SPECIAL.put(0x2019, '\'');
        SPECIAL.put(0x2018, '\'');
        SPECIAL.put(0x201C, '"');
        SPECIAL.put(0x201D, '"');
        SPECIAL.put(0x2013, '-');
        SPECIAL.put(0x2014, '-');
        SPECIAL.put(0x2212, '-');
        SPECIAL.put(0x00A0, ' ');
    }

    private PlainText() {
    }

    /**
     * @return czytelny tekst ASCII albo pusty string, jeśli z nazwy nic sensownego nie zostało
     */
    public static String toReadable(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }

        String stripped = stripColorCodes(raw);
        StringBuilder mapped = new StringBuilder(stripped.length());
        stripped.codePoints().forEach(codePoint -> {
            Character replacement = SPECIAL.get(codePoint);
            if (replacement != null) {
                mapped.append(replacement.charValue());
            } else {
                mapped.appendCodePoint(codePoint);
            }
        });

        String normalized = Normalizer.normalize(mapped, Normalizer.Form.NFKD);
        StringBuilder out = new StringBuilder(normalized.length());
        normalized.codePoints().forEach(codePoint -> {
            int type = Character.getType(codePoint);
            if (type == Character.NON_SPACING_MARK
                || type == Character.ENCLOSING_MARK
                || type == Character.COMBINING_SPACING_MARK) {
                return;
            }
            if (codePoint == '\n' || codePoint == '\t') {
                out.append(' ');
            } else if (codePoint >= 0x20 && codePoint <= 0x7E) {
                out.appendCodePoint(codePoint);
            } else {
                out.append(' ');
            }
        });

        return stripColorCodes(out.toString()).replaceAll("\\s{2,}", " ").trim();
    }

    /** Liczba liter i cyfr — pozwala odrzucić nazwy złożone tylko z ozdobników. */
    public static int letters(String text) {
        if (text == null) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (Character.isLetterOrDigit(text.charAt(i))) {
                count++;
            }
        }
        return count;
    }

    private static String stripColorCodes(String raw) {
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char character = raw.charAt(i);
            boolean code = (character == '\u00A7' || character == '&')
                && i + 1 < raw.length()
                && "0123456789abcdefklmnorxABCDEFKLMNORX".indexOf(raw.charAt(i + 1)) >= 0;
            if (code) {
                i++;
                continue;
            }
            out.append(character);
        }
        return out.toString();
    }
}
