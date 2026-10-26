package dev.grzybalson.managers;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.configs.LogiConfig;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class WebhookLogManager {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
        .withZone(ZoneId.systemDefault());

    private static final DecimalFormat MONEY_FORMAT = new DecimalFormat("#,##0.00");

    private final HollyCore plugin;

    public WebhookLogManager(HollyCore plugin) {
        this.plugin = plugin;
    }

    public void logMarketListed(Player seller, ItemStack item, double price, long listedAt) {
        LogiConfig.Webhook webhook = plugin.getConfigManager().getLogiConfig().getMarketListed();
        sendEmbed(
            webhook,
            "Rynek • Wystawienie przedmiotu",
            0x2ECC71,
            "**Gracz:** " + escapeMarkdown(seller.getName()) + "\n" +
                "**Przedmiot:** " + escapeMarkdown(resolveItemName(item)) + "\n" +
                "**Ilość:** " + item.getAmount() + "\n" +
                "**Cena:** " + formatMoney(price) + "\n" +
                "**Kiedy:** " + DATE_FORMATTER.format(Instant.ofEpochMilli(listedAt))
        );
    }

    public void logGamemodeChange(CommandSender sender, Player target, GameMode gameMode) {
        LogiConfig.Webhook webhook = plugin.getConfigManager().getLogiConfig().getGamemodeChanged();
        String actor = sender.getName();
        String actorUuid = sender instanceof Player player ? player.getUniqueId().toString() : "CONSOLE";

        sendEmbed(
            webhook,
            "Administracja • Zmiana gamemode",
            0x3498DB,
            "**Wykonał:** " + escapeMarkdown(actor) + "\n" +
                "**UUID wykonującego:** " + actorUuid + "\n" +
                "**Cel:** " + escapeMarkdown(target.getName()) + "\n" +
                "**Nowy tryb:** " + escapeMarkdown(prettyGameMode(gameMode)) + "\n" +
                "**Kiedy:** " + DATE_FORMATTER.format(Instant.now())
        );
    }

    public void logBackupRequested(String targetName, String requesterName, UUID requestedBy, String deathId) {
        LogiConfig.Webhook webhook = plugin.getConfigManager().getLogiConfig().getBackupRequested();

        sendEmbed(
            webhook,
            "Backup • Zgłoszenie",
            0xF1C40F,
            "**Gracz:** " + escapeMarkdown(targetName) + "\n" +
                "**Zgłaszający:** " + escapeMarkdown(requesterName) + "\n" +
                "**UUID zgłaszającego:** " + requestedBy + "\n" +
                "**ID śmierci:** `" + escapeMarkdown(deathId) + "`\n" +
                "**Kiedy:** " + DATE_FORMATTER.format(Instant.now())
        );
    }

    public void logBackupApproved(String playerName, String approverName, UUID approvedBy) {
        LogiConfig.Webhook webhook = plugin.getConfigManager().getLogiConfig().getBackupApproved();

        sendEmbed(
            webhook,
            "Backup • Nadanie / akceptacja",
            0x57F287,
            "**Gracz:** " + escapeMarkdown(playerName) + "\n" +
                "**Nadał:** " + escapeMarkdown(approverName) + "\n" +
                "**UUID nadającego:** " + approvedBy + "\n" +
                "**Kiedy:** " + DATE_FORMATTER.format(Instant.now())
        );
    }


    public void logBackupEqGranted(String playerName, String approverName, UUID approvedBy, String deathId) {
        LogiConfig.Webhook webhook = plugin.getConfigManager().getLogiConfig().getBackupApproved();

        sendEmbed(
            webhook,
            "Backup • Nadanie EQ",
            0x57F287,
            "**Gracz:** " + escapeMarkdown(playerName) + "\n" +
                "**Nadał:** " + escapeMarkdown(approverName) + "\n" +
                "**UUID nadającego:** " + approvedBy + "\n" +
                "**ID śmierci:** `" + escapeMarkdown(deathId) + "`\n" +
                "**Kiedy:** " + DATE_FORMATTER.format(Instant.now())
        );
    }

    public void logBackupStatsRestored(String playerName, String restoredByName, UUID restoredBy, String deathId) {
        LogiConfig.Webhook webhook = plugin.getConfigManager().getLogiConfig().getBackupApproved();

        sendEmbed(
            webhook,
            "Backup • Przywrócenie statystyk",
            0x3498DB,
            "**Gracz:** " + escapeMarkdown(playerName) + "\n" +
                "**Przywrócił:** " + escapeMarkdown(restoredByName) + "\n" +
                "**UUID przywracającego:** " + restoredBy + "\n" +
                "**ID śmierci:** `" + escapeMarkdown(deathId) + "`\n" +
                "**Kiedy:** " + DATE_FORMATTER.format(Instant.now())
        );
    }

    public void logBackupClaimed(String playerName, UUID playerUuid) {
        LogiConfig.Webhook webhook = plugin.getConfigManager().getLogiConfig().getBackupClaimed();
        sendEmbed(
            webhook,
            "Backup • Odebranie",
            0x5865F2,
            "**Gracz:** " + escapeMarkdown(playerName) + "\n" +
                "**UUID:** " + playerUuid + "\n" +
                "**Kiedy:** " + DATE_FORMATTER.format(Instant.now())
        );
    }

    public void logPayTransfer(Player sender, OfflinePlayer target, double amount) {
        LogiConfig.Webhook webhook = plugin.getConfigManager().getLogiConfig().getPayTransfer();
        sendEmbed(
            webhook,
            "Ekonomia • /pay",
            0xED4245,
            "**Od:** " + escapeMarkdown(sender.getName()) + "\n" +
                "**Do:** " + escapeMarkdown(safeName(target)) + "\n" +
                "**Kwota:** " + formatMoney(amount) + "\n" +
                "**UUID nadawcy:** " + sender.getUniqueId() + "\n" +
                "**UUID odbiorcy:** " + target.getUniqueId() + "\n" +
                "**Kiedy:** " + DATE_FORMATTER.format(Instant.now())
        );
    }

    public void logPayTransferToBot(Player sender, String botName, double amount) {
        LogiConfig.Webhook webhook = plugin.getConfigManager().getLogiConfig().getPayTransfer();
        sendEmbed(
            webhook,
            "Ekonomia • /pay (bot)",
            0xED4245,
            "**Od:** " + escapeMarkdown(sender.getName()) + "\n" +
                "**Do:** " + escapeMarkdown(botName) + " *(bot)*\n" +
                "**Kwota:** " + formatMoney(amount) + "\n" +
                "**UUID nadawcy:** " + sender.getUniqueId() + "\n" +
                "**Kiedy:** " + DATE_FORMATTER.format(Instant.now())
        );
    }

    private void sendEmbed(LogiConfig.Webhook webhook, String title, int color, String description) {
        if (!plugin.isAddonEnabled("logi")) {
            return;
        }

        LogiConfig config = plugin.getConfigManager().getLogiConfig();
        if (config == null || !config.isEnabled()) {
            return;
        }

        String webhookUrl = resolveWebhookUrl(webhook, config);
        if (webhookUrl == null || webhookUrl.isBlank()) {
            return;
        }

        // W starszych wygenerowanych configach każdy typ logu miał enabled: false.
        // Jeśli administrator wpisał URL webhooka, traktujemy to jako włączenie tego loga.
        if (webhook != null && !webhook.isEnabled() && isBlank(webhook.getWebhookUrl()) && isBlank(config.getWebhookUrl())) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(webhookUrl);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                connection.setRequestProperty("User-Agent", "HollyCore-Discord-Logs");
                connection.setDoOutput(true);
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                String payload = "{\"embeds\":[{" +
                    "\"title\":\"" + jsonEscape(title) + "\"," +
                    "\"description\":\"" + jsonEscape(description) + "\"," +
                    "\"color\":" + color + "," +
                    "\"footer\":{\"text\":\"HollyCore • " + jsonEscape(plugin.getName()) + "\"}," +
                    "\"timestamp\":\"" + Instant.now() + "\"" +
                    "}]}";

                byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(bytes.length);

                try (OutputStream outputStream = connection.getOutputStream()) {
                    outputStream.write(bytes);
                }

                int responseCode = connection.getResponseCode();
                if (responseCode < 200 || responseCode >= 300) {
                    plugin.getLogger().warning("Discord odrzucił webhook logów HTTP " + responseCode + ": " + readResponse(connection));
                }
            } catch (Exception exception) {
                plugin.getLogger().warning("Nie udało się wysłać webhooka logów: " + exception.getMessage());
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private String resolveWebhookUrl(LogiConfig.Webhook webhook, LogiConfig config) {
        if (webhook != null && !isBlank(webhook.getWebhookUrl())) {
            return webhook.getWebhookUrl();
        }
        return config.getWebhookUrl();
    }

    private boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    private String readResponse(HttpURLConnection connection) {
        try {
            InputStream stream = connection.getErrorStream();
            if (stream == null) {
                stream = connection.getInputStream();
            }
            if (stream == null) {
                return "brak treści odpowiedzi";
            }
            try (InputStream inputStream = stream) {
                return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
            return "nie udało się odczytać odpowiedzi";
        }
    }

    private String resolveItemName(ItemStack item) {
        if (item == null) {
            return "BRAK";
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return stripColors(meta.getDisplayName());
        }

        return ArraysHelper.prettyMaterialName(item.getType().name());
    }

    private String formatMoney(double amount) {
        return MONEY_FORMAT.format(amount) + plugin.getConfigManager().getEconomyConfig().getCurrencySymbol();
    }

    private String prettyGameMode(GameMode gameMode) {
        return switch (gameMode) {
            case SURVIVAL -> "Survival";
            case CREATIVE -> "Creative";
            case ADVENTURE -> "Adventure";
            case SPECTATOR -> "Spectator";
        };
    }

    private String safeName(OfflinePlayer player) {
        return player.getName() != null ? player.getName() : player.getUniqueId().toString();
    }

    private String stripColors(String text) {
        return text == null ? "" : ChatColor.stripColor(text);
    }

    private String jsonEscape(String input) {
        if (input == null) {
            return "";
        }
        return input
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "")
            .replace("\n", "\\n");
    }

    private String escapeMarkdown(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("*", "\\*").replace("`", "\\`").replace("_", "\\_");
    }

    private static final class ArraysHelper {
        private static String prettyMaterialName(String materialName) {
            String normalized = materialName.toLowerCase(Locale.ROOT).replace('_', ' ');
            String[] split = normalized.split(" ");
            StringBuilder builder = new StringBuilder();
            for (String part : split) {
                if (part.isEmpty()) {
                    continue;
                }
                builder.append(Character.toUpperCase(part.charAt(0)))
                    .append(part.substring(1))
                    .append(' ');
            }
            return builder.toString().trim();
        }
    }
}
