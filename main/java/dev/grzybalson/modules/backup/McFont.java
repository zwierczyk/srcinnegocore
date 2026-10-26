package dev.grzybalson.modules.backup;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Renderer oryginalnej bitmapowej czcionki Minecrafta (assets/textures/font/ascii.png).
 * Rysuje w skali całkowitej, żeby piksele zostały ostre jak w grze.
 */
public final class McFont {

    private static final String[] ROWS = {
        "\u00C0\u00C1\u00C2\u00C8\u00CA\u00CB\u00CD\u00D3\u00D4\u00D5\u00DA\u00DF\u00E3\u00F5\u011F\u0130",
        "\u0131\u0152\u0153\u015E\u015F\u0174\u0175\u017E\u0207\u0000\u0000\u0000\u0000\u0000\u0000\u0000",
        " !\"#$%&'()*+,-./",
        "0123456789:;<=>?",
        "@ABCDEFGHIJKLMNO",
        "PQRSTUVWXYZ[\\]^_",
        "`abcdefghijklmno",
        "pqrstuvwxyz{|}~\u0000",
        "\u00C7\u00FC\u00E9\u00E2\u00E4\u00E0\u00E5\u00E7\u00EA\u00EB\u00E8\u00EF\u00EE\u00EC\u00C4\u00C5",
        "\u00C9\u00E6\u00C6\u00F4\u00F6\u00F2\u00FB\u00F9\u00FF\u00D6\u00DC\u00F8\u00A3\u00D8\u00D7\u0192",
        "\u00E1\u00ED\u00F3\u00FA\u00F1\u00D1\u00AA\u00BA\u00BF\u00AE\u00AC\u00BD\u00BC\u00A1\u00AB\u00BB",
        "\u2591\u2592\u2593\u2502\u2524\u2561\u2562\u2556\u2555\u2563\u2551\u2557\u255D\u255C\u255B\u2510",
        "\u2514\u2534\u252C\u251C\u2500\u253C\u255E\u255F\u255A\u2554\u2569\u2566\u2560\u2550\u256C\u2567",
        "\u2568\u2564\u2565\u2559\u2558\u2552\u2553\u256B\u256A\u2518\u250C\u2588\u2584\u258C\u2590\u2580",
        "\u03B1\u03B2\u0393\u03C0\u03A3\u03C3\u03BC\u03C4\u03A6\u0398\u03A9\u03B4\u221E\u2205\u2208\u2229",
        "\u2261\u00B1\u2265\u2264\u2320\u2321\u00F7\u2248\u00B0\u2219\u00B7\u221A\u207F\u00B2\u25A0\u0000"
    };

    private static final Map<Character, Glyph> GLYPHS = new HashMap<>();
    private static final Map<Integer, BufferedImage> TINTED = new ConcurrentHashMap<>();
    private static BufferedImage sheet;
    private static int cell = 8;
    private static int spaceAdvance = 4;

    static {
        load();
    }

    private McFont() {
    }

    public static boolean available() {
        return sheet != null;
    }

    /** Wysokość linii tekstu (bez odstępu) dla danej skali. */
    public static int height(int scale) {
        return cell * scale;
    }

    public static int width(String text, int scale) {
        if (text == null || sheet == null) {
            return 0;
        }
        int width = 0;
        String clean = sanitize(text);
        for (int i = 0; i < clean.length(); i++) {
            width += advance(clean.charAt(i)) * scale;
        }
        return width;
    }

    /** Przycina tekst tak, żeby zmieścił się w podanej szerokości (bez wielokropka). */
    public static String fit(String text, int scale, int maxWidth) {
        if (text == null) {
            return "";
        }
        String clean = sanitize(text);
        int width = 0;
        for (int i = 0; i < clean.length(); i++) {
            int next = advance(clean.charAt(i)) * scale;
            if (width + next > maxWidth) {
                return clean.substring(0, i).trim();
            }
            width += next;
        }
        return clean;
    }

    /** Rysuje tekst, gdzie (x, y) to lewy górny róg pierwszego znaku. */
    public static int draw(Graphics2D g, String text, int x, int y, int scale, Color color, boolean shadow) {
        if (text == null || sheet == null) {
            return x;
        }
        String clean = sanitize(text);
        if (shadow) {
            int rgb = color.getRGB();
            Color dark = new Color((rgb & 0xFCFCFC) >> 2, false);
            drawRaw(g, clean, x + scale, y + scale, scale, dark);
        }
        return drawRaw(g, clean, x, y, scale, color);
    }

    private static int drawRaw(Graphics2D g, String clean, int x, int y, int scale, Color color) {
        BufferedImage tinted = tint(color);
        Object oldInterpolation = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        int cursor = x;
        for (int i = 0; i < clean.length(); i++) {
            char character = clean.charAt(i);
            Glyph glyph = GLYPHS.get(character);
            if (glyph != null) {
                int sx = glyph.column * cell;
                int sy = glyph.row * cell;
                g.drawImage(
                    tinted,
                    cursor, y, cursor + cell * scale, y + cell * scale,
                    sx, sy, sx + cell, sy + cell,
                    null
                );
            }
            cursor += advance(character) * scale;
        }
        if (oldInterpolation != null) {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldInterpolation);
        }
        return cursor;
    }

    /** Zamienia polskie znaki na odpowiedniki dostępne w atlasie czcionki. */
    public static String sanitize(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            char mapped = switch (character) {
                case 'ą' -> 'a';
                case 'ć' -> 'c';
                case 'ę' -> 'e';
                case 'ł' -> 'l';
                case 'ń' -> 'n';
                case 'ś' -> 's';
                case 'ź', 'ż' -> 'z';
                case 'Ą' -> 'A';
                case 'Ć' -> 'C';
                case 'Ę' -> 'E';
                case 'Ł' -> 'L';
                case 'Ń' -> 'N';
                case 'Ś' -> 'S';
                case 'Ź', 'Ż' -> 'Z';
                case '—', '–', '−' -> '-';
                case '’', '‘' -> '\'';
                case '“', '”' -> '"';
                default -> character;
            };
            if (mapped == ' ' || GLYPHS.containsKey(mapped)) {
                out.append(mapped);
            }
        }
        return out.toString();
    }

    private static int advance(char character) {
        if (character == ' ') {
            return spaceAdvance;
        }
        Glyph glyph = GLYPHS.get(character);
        return glyph == null ? spaceAdvance : glyph.advance;
    }

    private static BufferedImage tint(Color color) {
        return TINTED.computeIfAbsent(color.getRGB(), rgb -> {
            BufferedImage out = new BufferedImage(sheet.getWidth(), sheet.getHeight(), BufferedImage.TYPE_INT_ARGB);
            int tint = rgb & 0xFFFFFF;
            for (int y = 0; y < sheet.getHeight(); y++) {
                for (int x = 0; x < sheet.getWidth(); x++) {
                    int alpha = (sheet.getRGB(x, y) >>> 24) & 0xFF;
                    out.setRGB(x, y, (alpha << 24) | tint);
                }
            }
            return out;
        });
    }

    private static void load() {
        BufferedImage image = read("backup-font/ascii.png");
        if (image == null) {
            return;
        }
        sheet = image;
        cell = Math.max(1, image.getWidth() / 16);
        spaceAdvance = Math.max(2, cell / 2);
        for (int row = 0; row < ROWS.length && row < 16; row++) {
            String chars = ROWS[row];
            for (int column = 0; column < chars.length() && column < 16; column++) {
                char character = chars.charAt(column);
                if (character == '\u0000' || GLYPHS.containsKey(character)) {
                    continue;
                }
                GLYPHS.put(character, new Glyph(row, column, measure(image, row, column)));
            }
        }
    }

    private static int measure(BufferedImage image, int row, int column) {
        int last = -1;
        for (int x = 0; x < cell; x++) {
            for (int y = 0; y < cell; y++) {
                int alpha = (image.getRGB(column * cell + x, row * cell + y) >>> 24) & 0xFF;
                if (alpha > 16) {
                    last = x;
                    break;
                }
            }
        }
        return last < 0 ? spaceAdvance : last + 2;
    }

    private static BufferedImage read(String path) {
        try (InputStream input = McFont.class.getClassLoader().getResourceAsStream(path)) {
            return input == null ? null : ImageIO.read(input);
        } catch (Exception ignored) {
            return null;
        }
    }

    private record Glyph(int row, int column, int advance) {
    }
}
