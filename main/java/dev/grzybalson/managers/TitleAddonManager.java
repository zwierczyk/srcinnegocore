package dev.grzybalson.managers;

import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TitleAddonManager implements Listener {

    private final HollyCore plugin;
    private final File configFile;
    private final Map<UUID, Long> rtpWorldChangeSuppressions = new ConcurrentHashMap<>();
    private YamlConfiguration config;

    public TitleAddonManager(HollyCore plugin) {
        this.plugin = plugin;
        this.configFile = new File(dev.grzybalson.config.HollyDataPaths.getAddonFolder(plugin, "titles"), "titles.yml");
        ensureDefaultConfig();
        reload();
    }

    public void reload() {
        ensureDefaultConfig();
        this.config = YamlConfiguration.loadConfiguration(configFile);
        patchTeleportSuccess();
    }

    private void patchTeleportSuccess() {
        if (config.contains("teleport-success.title")) {
            return;
        }
        config.set("teleport-success.enabled", true);
        config.set("teleport-success.title", "&#77F454☺");
        config.set("teleport-success.subtitle", "&aPomyślnie zostałeś przeniesiony");
        config.set("teleport-success.fade-in", 2);
        config.set("teleport-success.stay", 36);
        config.set("teleport-success.fade-out", 6);
        try {
            config.save(configFile);
        } catch (IOException exception) {
            plugin.getLogger().warning("Nie udalo sie dopisac teleport-success do titles.yml: " + exception.getMessage());
        }
    }

    public boolean isEnabled() {
        return plugin.isAddonEnabled("titles") && config.getBoolean("enabled", true);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!isEnabled() || !config.getBoolean("join.enabled", true)) {
            return;
        }

        Player player = event.getPlayer();
        long delay = Math.max(0L, config.getLong("join.delay-ticks", 80L));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            sendConfiguredTitle(player, "join", null);
        }, delay);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        if (!isEnabled() || !config.getBoolean("world-change.enabled", true)) {
            return;
        }

        Player player = event.getPlayer();
        Long until = rtpWorldChangeSuppressions.remove(player.getUniqueId());
        if (until != null && until > System.currentTimeMillis()) {
            return;
        }

        String worldName = player.getWorld().getName();
        String section = "world-change.sectors." + worldName;
        if (config.isConfigurationSection(section)) {
            sendConfiguredTitle(player, section, worldName);
            return;
        }

        if (config.getBoolean("world-change.fallback.enabled", true)) {
            sendConfiguredTitle(player, "world-change.fallback", worldName);
        }
    }

    public void markRtpTeleport(Player player) {
        if (player == null) {
            return;
        }
        rtpWorldChangeSuppressions.put(player.getUniqueId(), System.currentTimeMillis() + 5000L);
    }

    public void sendRtpSectorTitle(Player player) {
        if (!isEnabled() || !config.getBoolean("rtp.enabled", true) || player == null || !player.isOnline()) {
            return;
        }
        sendConfiguredTitle(player, "rtp", "rtp");
    }

    public void sendTeleportCountdown(Player player, int seconds) {
        if (!isEnabled() || !config.getBoolean("teleport-countdown.enabled", true) || player == null || !player.isOnline()) {
            return;
        }
        sendConfiguredTitle(player, "teleport-countdown", null, String.valueOf(seconds));
    }

    public void sendTeleportSuccess(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (isEnabled() && config.getBoolean("teleport-success.enabled", true)) {
            String title = config.getString("teleport-success.title", "");
            String subtitle = config.getString("teleport-success.subtitle", "");
            if ((title != null && !title.isBlank()) || (subtitle != null && !subtitle.isBlank())) {
                sendConfiguredTitle(player, "teleport-success", null);
                return;
            }
        }
        var messages = plugin.getConfigManager().getMessagesConfig();
        ColorUtils.sendTitle(
            player,
            messages.getSpawnTeleportSuccessTitle(),
            messages.getSpawnTeleportSuccessSubtitle(),
            2,
            36,
            6
        );
    }

    private void sendConfiguredTitle(Player player, String path, String sector) {
        sendConfiguredTitle(player, path, sector, null);
    }

    private void sendConfiguredTitle(Player player, String path, String sector, String time) {
        String title = config.getString(path + ".title", "");
        String subtitle = config.getString(path + ".subtitle", "");
        if ((title == null || title.isBlank()) && (subtitle == null || subtitle.isBlank())) {
            return;
        }

        String sectorValue = sector == null ? player.getWorld().getName() : sector;
        title = replacePlaceholders(title, player, sectorValue, time);
        subtitle = replacePlaceholders(subtitle, player, sectorValue, time);

        int fadeIn = config.getInt(path + ".fade-in", config.getInt("defaults.fade-in", 5));
        int stay = config.getInt(path + ".stay", config.getInt("defaults.stay", 45));
        int fadeOut = config.getInt(path + ".fade-out", config.getInt("defaults.fade-out", 10));

        dev.grzybalson.utils.ColorUtils.sendTitle(player, ColorUtils.colorize(title), ColorUtils.colorize(subtitle), fadeIn, stay, fadeOut);
    }

    private String replacePlaceholders(String text, Player player, String sector, String time) {
        if (text == null) {
            return "";
        }
        String result = text
            .replace("{player}", player.getName())
            .replace("{world}", player.getWorld().getName())
            .replace("{sector}", sector == null ? player.getWorld().getName() : sector);
        if (time != null) {
            result = result.replace("{time}", time);
        }
        return result;
    }

    private void ensureDefaultConfig() {
        File parent = configFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        if (configFile.exists()) {
            return;
        }

        try {
            plugin.saveResource("addons/titles/titles.yml", false);
        } catch (IllegalArgumentException exception) {
            try {
                configFile.createNewFile();
                YamlConfiguration defaults = new YamlConfiguration();
                defaults.set("enabled", true);
                defaults.save(configFile);
            } catch (IOException ioException) {
                plugin.getLogger().warning("Nie udało się utworzyć addons/titles/titles.yml: " + ioException.getMessage());
            }
        }
    }
}
