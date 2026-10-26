package dev.grzybalson.modules.lockedfeatures;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.HollyDataPaths;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class LockedFeaturesManager implements Listener {

    private static final String CROSS = "\uE0C0";
    private static final String CONFIG_RESOURCE = "addons/lockedfeatures/lockedfeatures.yml";

    private final HollyCore plugin;
    private final File configFile;

    private boolean enabled;
    private String title;
    private String subtitle;
    private String chat;
    private int fadeIn;
    private int stay;
    private int fadeOut;
    private Sound sound;
    private float soundVolume;
    private float soundPitch;
    private final Map<String, LockedCommand> commands = new LinkedHashMap<>();

    public LockedFeaturesManager(HollyCore plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), CONFIG_RESOURCE);
        HollyDataPaths.ensureResource(plugin, CONFIG_RESOURCE);
        reload();
    }

    public void reload() {
        HollyDataPaths.ensureResource(plugin, CONFIG_RESOURCE);
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        enabled = config.getBoolean("enabled", true);
        title = config.getString("title", "&#FF1A1A{CROSS} &c&lZablokowane &#FF1A1A{CROSS}");
        subtitle = config.getString("subtitle", "&7{LABEL} &8— &#FF5C5Cdostępne później");
        chat = config.getString("chat", "&#FF1A1A{CROSS} &8• &#FF5C5C{LABEL} &7zostanie odblokowane w &#FF5C5C&lpóźniejszym etapie &7rozgrywki.");
        fadeIn = Math.max(0, config.getInt("title-fade-in", 8));
        stay = Math.max(1, config.getInt("title-stay", 55));
        fadeOut = Math.max(0, config.getInt("title-fade-out", 12));
        sound = parseSound(config.getString("sound", "BLOCK_NOTE_BLOCK_BASS"));
        soundVolume = (float) config.getDouble("sound-volume", 0.75D);
        soundPitch = (float) config.getDouble("sound-pitch", 0.65D);
        loadCommands(config.getConfigurationSection("commands"));
    }

    public void sendLocked(Player player, String featureLabel) {
        if (player == null) {
            return;
        }

        String label = featureLabel == null || featureLabel.isBlank() ? "Ta funkcja" : featureLabel.trim();
        Map<String, String> placeholders = Map.of(
            "CROSS", CROSS,
            "LABEL", label
        );

        ColorUtils.sendTitle(
            player,
            format(title, placeholders),
            format(subtitle, placeholders),
            fadeIn,
            stay,
            fadeOut
        );
        player.sendMessage(ColorUtils.colorizeComponent(format(chat, placeholders)));
        if (sound != null) {
            player.playSound(player.getLocation(), sound, soundVolume, soundPitch);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!enabled) {
            return;
        }

        Player player = event.getPlayer();
        String message = event.getMessage();
        int end = message.indexOf(' ');
        String command = message.substring(1, end < 0 ? message.length() : end).toLowerCase(Locale.ROOT);
        int colon = command.indexOf(':');
        if (colon >= 0) {
            command = command.substring(colon + 1);
        }

        LockedCommand locked = commands.get(command);
        if (locked == null) {
            return;
        }
        if (locked.permission != null && !locked.permission.isBlank() && player.hasPermission(locked.permission)) {
            return;
        }
        for (String bypass : locked.bypassPermissions == null ? List.<String>of() : locked.bypassPermissions) {
            if (bypass != null && !bypass.isBlank() && player.hasPermission(bypass)) {
                return;
            }
        }

        event.setCancelled(true);
        sendLocked(player, locked.label);
    }

    private void loadCommands(ConfigurationSection section) {
        commands.clear();
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection commandSection = section.getConfigurationSection(key);
            if (commandSection == null) {
                continue;
            }
            String permission = commandSection.getString("permission", "");
            String label = commandSection.getString("label", "/" + key);
            List<String> bypass = commandSection.getStringList("bypass-permissions");
            LockedCommand locked = new LockedCommand(permission, label, bypass);
            registerAlias(key, locked);
            for (String alias : commandSection.getStringList("aliases")) {
                registerAlias(alias, locked);
            }
        }
    }

    private void registerAlias(String alias, LockedCommand locked) {
        if (alias == null || alias.isBlank()) {
            return;
        }
        String normalized = alias.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        int colon = normalized.indexOf(':');
        if (colon >= 0) {
            normalized = normalized.substring(colon + 1);
        }
        if (!normalized.isBlank()) {
            commands.put(normalized, locked);
        }
    }

    private String format(String message, Map<String, String> placeholders) {
        if (message == null) {
            return "";
        }
        String formatted = message;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            formatted = formatted.replace("{" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
        }
        return formatted;
    }

    private Sound parseSound(String value) {
        try {
            return Sound.valueOf(String.valueOf(value).trim().toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return Sound.BLOCK_NOTE_BLOCK_BASS;
        }
    }

    private record LockedCommand(String permission, String label, List<String> bypassPermissions) {
    }
}
