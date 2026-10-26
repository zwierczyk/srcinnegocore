package dev.grzybalson.utils;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerTextures;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collection;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class HeadUtils {

    private static final Pattern HASH_PATTERN = Pattern.compile("^[a-fA-F0-9]{32,64}$");
    private static final Pattern URL_HASH_PATTERN = Pattern.compile("(?i)textures\\.minecraft\\.net/texture/([a-f0-9]{32,64})");
    private static final Pattern TEXTURE_URL_PATTERN = Pattern.compile("(?i)\"url\"\\s*:\\s*\"(http[s]?://[^\"]+)\"");
    private static final Pattern EMBEDDED_HASH_PATTERN = Pattern.compile("(?i)\\b([a-f0-9]{56,64})\\b");
    private static final Map<String, PlayerProfile> PROFILE_CACHE = new ConcurrentHashMap<>();

    private HeadUtils() {
    }

    public static ItemStack createHead(String value) {
        return setHeadTexture(new ItemStack(Material.PLAYER_HEAD), value);
    }

    public static ItemStack createHead(String value, String displayName) {
        ItemStack head = createHead(value);
        SkullMeta meta = (SkullMeta) head.getItemMeta();

        if (meta != null) {
            meta.displayName(ColorUtils.colorizeComponent(displayName)
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            head.setItemMeta(meta);
        }

        return head;
    }

    public static ItemStack createPlayerHead(String playerName) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();

        if (meta != null) {
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(playerName));
            head.setItemMeta(meta);
        }

        return head;
    }

    public static String getBase64FromHead(ItemStack head) {
        String url = getTextureUrlFromHead(head);
        if (url != null && !url.isBlank()) {
            return encodeUrl(url);
        }

        if (head == null || head.getType() != Material.PLAYER_HEAD) {
            return null;
        }

        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta == null) {
            return null;
        }

        PlayerProfile paperProfile = meta.getPlayerProfile();
        if (paperProfile != null) {
            for (ProfileProperty property : paperProfile.getProperties()) {
                if (property.getName().equalsIgnoreCase("textures") && property.getValue() != null && !property.getValue().isBlank()) {
                    return normalizeToBase64(property.getValue());
                }
            }
        }

        String storedHead = NBTUtils.getString(head, "holly_head_value");
        if (storedHead != null && !storedHead.isBlank()) {
            return normalizeToBase64(storedHead);
        }

        return null;
    }

    public static String getTextureUrlFromHead(ItemStack head) {
        if (head == null || head.getType() != Material.PLAYER_HEAD) {
            return null;
        }

        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta == null) {
            return null;
        }

        org.bukkit.profile.PlayerProfile ownerProfile = meta.getOwnerProfile();
        String ownerUrl = extractUrl(ownerProfile != null ? ownerProfile.getTextures() : null);
        if (ownerUrl != null) {
            return ownerUrl;
        }

        PlayerProfile paperProfile = meta.getPlayerProfile();
        String paperUrl = extractUrlFromPaperProfile(paperProfile);
        if (paperUrl != null) {
            return paperUrl;
        }

        String storedHead = NBTUtils.getString(head, "holly_head_value");
        if (storedHead != null && !storedHead.isBlank()) {
            return normalizeToTextureUrl(storedHead);
        }

        return null;
    }

    public static boolean isCustomHead(ItemStack item) {
        if (item == null || item.getType() != Material.PLAYER_HEAD) {
            return false;
        }

        return getTextureUrlFromHead(item) != null || getBase64FromHead(item) != null;
    }

    public static ItemStack setHeadTextureLikeShop(ItemStack head, String rawValue) {
        return setHeadTexture(head, rawValue);
    }

    public static ItemStack setHeadTexture(ItemStack head, String rawValue) {
        if (head == null || head.getType() != Material.PLAYER_HEAD) {
            return head;
        }
        if (rawValue == null || rawValue.isBlank()) {
            return head;
        }

        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta == null) {
            return head;
        }

        String base64Value = normalizeToBase64(rawValue);
        if (base64Value != null && !base64Value.isBlank()) {
            try {
                PlayerProfile profile = PROFILE_CACHE.computeIfAbsent(base64Value, key -> {
                    UUID uuid = UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
                    PlayerProfile p = Bukkit.createProfile(uuid);
                    p.setProperty(new ProfileProperty("textures", key));
                    return p;
                });
                meta.setPlayerProfile(profile);
                head.setItemMeta(meta);
                return head;
            } catch (Exception exception) {
                Bukkit.getLogger().warning("Nie udalo sie ustawic tekstury glowki: " + exception.getMessage());
            }
        }

        String cleanName = normalizeValue(rawValue);
        if (cleanName != null && cleanName.matches("^[a-zA-Z0-9_]{1,16}$")) {
            try {
                meta.setOwningPlayer(Bukkit.getOfflinePlayer(cleanName));
                head.setItemMeta(meta);
                return head;
            } catch (Exception exception) {
                Bukkit.getLogger().warning("Nie udalo sie ustawic gracza glowki: " + exception.getMessage());
            }
        }

        return head;
    }

    public static String normalizeToTextureUrl(String rawValue) {
        String value = normalizeValue(rawValue);
        if (value == null || value.isBlank()) {
            return null;
        }

        if (HASH_PATTERN.matcher(value).matches()) {
            return "https://textures.minecraft.net/texture/" + value.toLowerCase(java.util.Locale.ROOT);
        }

        Matcher urlMatcher = URL_HASH_PATTERN.matcher(value);
        if (urlMatcher.find()) {
            return "https://textures.minecraft.net/texture/" + urlMatcher.group(1).toLowerCase(java.util.Locale.ROOT);
        }

        String decoded = tryDecodeBase64(value);
        if (decoded != null && !decoded.isBlank()) {
            Matcher decodedUrlMatcher = URL_HASH_PATTERN.matcher(decoded);
            if (decodedUrlMatcher.find()) {
                return "https://textures.minecraft.net/texture/" + decodedUrlMatcher.group(1).toLowerCase(java.util.Locale.ROOT);
            }
            Matcher decodedJsonUrlMatcher = TEXTURE_URL_PATTERN.matcher(decoded);
            if (decodedJsonUrlMatcher.find()) {
                return decodedJsonUrlMatcher.group(1).replace("http://", "https://");
            }
            Matcher decodedHashMatcher = EMBEDDED_HASH_PATTERN.matcher(decoded);
            if (decodedHashMatcher.find()) {
                return "https://textures.minecraft.net/texture/" + decodedHashMatcher.group(1).toLowerCase(java.util.Locale.ROOT);
            }
        }

        Matcher embeddedMatcher = EMBEDDED_HASH_PATTERN.matcher(value);
        if (embeddedMatcher.find()) {
            return "https://textures.minecraft.net/texture/" + embeddedMatcher.group(1).toLowerCase(java.util.Locale.ROOT);
        }

        if (value.regionMatches(true, 0, "http://", 0, 7) || value.regionMatches(true, 0, "https://", 0, 8)) {
            return value.replace("http://", "https://");
        }

        if (value.toLowerCase(java.util.Locale.ROOT).startsWith("textures.minecraft.net/")) {
            return "https://" + value;
        }

        return null;
    }

    public static String normalizeToBase64(String rawValue) {
        String url = normalizeToTextureUrl(rawValue);
        if (url != null && !url.isBlank()) {
            return encodeUrl(url);
        }

        String value = normalizeValue(rawValue);
        if (value == null || value.isBlank()) {
            return null;
        }

        String decodedJson = tryDecodeBase64(value);
        if (decodedJson != null && decodedJson.toLowerCase(java.util.Locale.ROOT).contains("textures")) {
            return encodeJson(decodedJson);
        }

        return null;
    }

    private static String normalizeValue(String rawValue) {
        if (rawValue == null) {
            return null;
        }

        String value = rawValue.trim();
        if (value.isEmpty()) {
            return value;
        }

        if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
            value = value.substring(1, value.length() - 1).trim();
        }

        if (value.regionMatches(true, 0, "value:", 0, 6)) {
            value = value.substring(6).trim();
        }

        if (value.regionMatches(true, 0, "url:", 0, 4)) {
            value = value.substring(4).trim();
        }

        if (value.regionMatches(true, 0, "head:", 0, 5)) {
            value = value.substring(5).trim();
        }

        if (value.regionMatches(true, 0, "texture:", 0, 8)) {
            value = value.substring(8).trim();
        }

        return value;
    }

    private static String extractUrlFromPaperProfile(PlayerProfile profile) {
        if (profile == null) {
            return null;
        }

        Collection<ProfileProperty> properties = profile.getProperties();
        if (properties == null) {
            return null;
        }

        for (ProfileProperty property : properties) {
            if (!property.getName().equalsIgnoreCase("textures") || property.getValue() == null || property.getValue().isBlank()) {
                continue;
            }

            String decodedJson = tryDecodeBase64(property.getValue());
            String url = decodedJson != null ? extractTextureUrl(decodedJson) : null;
            if (url != null) {
                return url.replace("http://", "https://");
            }
        }

        return null;
    }

    private static String extractUrl(PlayerTextures textures) {
        if (textures == null || textures.getSkin() == null) {
            return null;
        }

        return textures.getSkin().toString().replace("http://", "https://");
    }

    private static String encodeUrl(String url) {
        return encodeJson("{\"textures\":{\"SKIN\":{\"url\":\"" + url.replace("https://", "http://") + "\"}}}");
    }

    private static String encodeJson(String json) {
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private static String tryDecodeBase64(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String toDecode = value.trim();
        int rem = toDecode.length() % 4;
        if (rem > 0) {
            toDecode = toDecode + "=".repeat(4 - rem);
        }
        try {
            return new String(Base64.getDecoder().decode(toDecode), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
        }
        try {
            return new String(Base64.getUrlDecoder().decode(toDecode), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
        }
        try {
            return new String(Base64.getMimeDecoder().decode(toDecode), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String extractTextureUrl(String json) {
        Matcher matcher = TEXTURE_URL_PATTERN.matcher(json);
        return matcher.find() ? matcher.group(1) : null;
    }
}
