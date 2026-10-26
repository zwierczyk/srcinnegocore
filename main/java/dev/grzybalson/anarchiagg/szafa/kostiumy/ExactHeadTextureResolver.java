package dev.grzybalson.anarchiagg.szafa.kostiumy;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ExactHeadTextureResolver {
    private static final String MINESKIN_GENERATE_URL = "https://api.mineskin.org/generate/url";
    private static final Pattern BASE64_VALUE_PATTERN = Pattern.compile("\\\"value\\\"\\s*:\\s*\\\"([A-Za-z0-9+/=]{100,})\\\"");

    private ExactHeadTextureResolver() {
    }

    static void resolveFromUrlAsync(JavaPlugin plugin, String skinImageUrl, java.util.function.Consumer<String> callback) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> callback.accept(resolveFromUrl(plugin, skinImageUrl)));
    }

    static String resolveFromUrl(JavaPlugin plugin, String skinImageUrl) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(MINESKIN_GENERATE_URL).toURL().openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(30000);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "HollyCore/1.0 (kostium-head-resolver)");

            String body = "url=" + URLEncoder.encode(skinImageUrl, StandardCharsets.UTF_8);
            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(body.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = connection.getResponseCode();
            InputStream stream = responseCode >= 200 && responseCode < 300 ? connection.getInputStream() : connection.getErrorStream();
            String response = readFully(stream);

            if (responseCode < 200 || responseCode >= 300) {
                plugin.getLogger().warning("MineSkin nie zwrocil poprawnej odpowiedzi dla URL glowki (HTTP " + responseCode + "): " + trimForLog(response));
                return null;
            }

            Matcher matcher = BASE64_VALUE_PATTERN.matcher(response);
            if (matcher.find()) {
                return matcher.group(1);
            }

            plugin.getLogger().warning("Nie udalo sie odczytac pola 'value' z odpowiedzi MineSkin dla URL glowki.");
            return null;
        } catch (Exception exception) {
            plugin.getLogger().warning("Nie udalo sie pobrac dokladnej tekstury glowki z MineSkin: " + exception.getMessage());
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String readFully(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return "";
        }

        try (InputStream in = inputStream) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String trimForLog(String value) {
        if (value == null) {
            return "";
        }

        String normalized = value.replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.length() > 220 ? normalized.substring(0, 220) + "..." : normalized;
    }
}
