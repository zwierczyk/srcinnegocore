package dev.grzybalson.config;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.regex.Pattern;

/**
 * Czyści banery (#### / # ====) i komentarze dokumentacyjne z istniejących YAML
 * w folderze danych plugina. Nie rusza wyłączonych kluczy w stylu {@code # key: value}.
 */
public final class ConfigYamlCommentStripper {

    private static final Pattern BANNER = Pattern.compile("^\\s*#(#{2,}|\\s*[=\\-]{3,}).*$");
    /** Wyłączony klucz YAML: {@code # foo: bar} / {@code #foo:}. */
    private static final Pattern DISABLED_KEY = Pattern.compile("^\\s*#\\s*[A-Za-z0-9_\\-.]+\\s*:.*$");
    /** Pełna linia-komentarz (nie disabled key). */
    private static final Pattern FULL_LINE_COMMENT = Pattern.compile("^\\s*#.*$");

    private ConfigYamlCommentStripper() {
    }

    public static int stripAll(JavaPlugin plugin) {
        File root = plugin.getDataFolder();
        if (root == null || !root.isDirectory()) {
            return 0;
        }
        int changed = 0;
        List<File> files = new ArrayList<>();
        collectYaml(root, files);
        for (File file : files) {
            try {
                if (stripFile(file)) {
                    changed++;
                }
            } catch (IOException | RuntimeException exception) {
                plugin.getLogger().log(
                    Level.WARNING,
                    "Nie udało się wyczyścić komentarzy w " + file.getPath() + ": " + exception.getMessage()
                );
            }
        }
        return changed;
    }

    private static void collectYaml(File dir, List<File> out) {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                collectYaml(child, out);
            } else if (child.isFile()) {
                String name = child.getName().toLowerCase();
                if (name.endsWith(".yml") || name.endsWith(".yaml")) {
                    out.add(child);
                }
            }
        }
    }

    static boolean stripFile(File file) throws IOException {
        byte[] raw = Files.readAllBytes(file.toPath());
        String text = new String(raw, StandardCharsets.UTF_8);
        String newline = text.contains("\r\n") ? "\r\n" : "\n";
        String[] lines = text.split("\\R", -1);
        List<String> out = new ArrayList<>(lines.length);
        boolean changed = false;

        for (String line : lines) {
            if (shouldDrop(line)) {
                changed = true;
                continue;
            }
            out.add(line);
        }

        if (!changed) {
            return false;
        }

        List<String> cleaned = collapseBlankLines(out);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cleaned.size(); i++) {
            if (i > 0) {
                sb.append(newline);
            }
            sb.append(cleaned.get(i));
        }
        // zachowaj końcowy newline jeśli oryginał go miał
        if (text.endsWith("\n") || text.endsWith("\r\n")) {
            if (sb.length() == 0 || !sb.toString().endsWith(newline)) {
                sb.append(newline);
            }
        }

        String result = sb.toString();
        if (result.equals(text)) {
            return false;
        }
        Files.writeString(file.toPath(), result, StandardCharsets.UTF_8);
        return true;
    }

    private static boolean shouldDrop(String line) {
        if (line == null) {
            return false;
        }
        String trimmed = line.stripTrailing();
        if (trimmed.isEmpty()) {
            return false;
        }
        if (BANNER.matcher(trimmed).matches()) {
            return true;
        }
        if (DISABLED_KEY.matcher(trimmed).matches()) {
            return false;
        }
        return FULL_LINE_COMMENT.matcher(trimmed).matches();
    }

    private static List<String> collapseBlankLines(List<String> lines) {
        List<String> result = new ArrayList<>(lines.size());
        int blanks = 0;
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                blanks++;
                if (blanks <= 2) {
                    result.add("");
                }
            } else {
                blanks = 0;
                result.add(line);
            }
        }
        while (!result.isEmpty() && result.get(0).isBlank()) {
            result.remove(0);
        }
        while (!result.isEmpty() && result.get(result.size() - 1).isBlank()) {
            result.remove(result.size() - 1);
        }
        return result;
    }
}
