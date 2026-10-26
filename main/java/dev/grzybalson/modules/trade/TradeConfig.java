package dev.grzybalson.modules.trade;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.HollyDataPaths;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public final class TradeConfig {

    private static final String PATH = "addons/trade/trade.yml";

    private final HollyCore plugin;
    private YamlConfiguration config;

    public TradeConfig(HollyCore plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        HollyDataPaths.ensureResource(plugin, PATH);
        File file = new File(plugin.getDataFolder(), PATH);
        config = YamlConfiguration.loadConfiguration(file);
    }

    public String shiftClickWorld() {
        return config.getString("settings.shift-click-world", "spawn");
    }

    public double maxDistance() {
        return config.getDouble("settings.max-distance", 8.0);
    }

    public int requestTimeoutSeconds() {
        return config.getInt("settings.request-timeout-seconds", 60);
    }

    public long clickCooldownMs() {
        return config.getLong("settings.click-cooldown-ms", 150L);
    }

    public String tradeTitle() {
        return config.getString("gui.trade-title", "&8Wymiana z &f{PARTNER}");
    }

    public String acceptTitle() {
        return config.getString("gui.accept-title", "&8Prośba o wymianę");
    }

    public String withdrawTitle() {
        return config.getString("gui.withdraw-title", "&8Odbierz przedmioty");
    }

    public Sound sound(String key, Sound fallback) {
        String raw = config.getString("sounds." + key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Sound.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    public String message(String key) {
        return config.getString("messages." + key, "");
    }

    public String format(String key, String... replacements) {
        String message = message(key);
        if (message.isBlank()) {
            return "";
        }
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            message = message.replace(replacements[i], replacements[i + 1]);
        }
        return message;
    }
}
