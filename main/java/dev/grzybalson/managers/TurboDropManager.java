package dev.grzybalson.managers;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.HollyDataPaths;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.Locale;

public class TurboDropManager implements Listener {

    private static final String CONFIG_RESOURCE = "addons/economy/turbodrop.yml";

    private final HollyCore plugin;
    private final File configFile;

    private YamlConfiguration config;
    private String bossBarTitleTemplate;
    private BarColor bossBarColor;
    private BarStyle bossBarStyle;

    private String msgStartSender;
    private String msgStartBroadcast;
    private String msgStopSender;
    private String msgStopBroadcast;
    private String msgEndBroadcast;
    private String msgNoActiveEvent;
    private String msgReloadSuccess;

    private double activeMultiplier = 1.0D;
    private long endTimeMillis = 0L;
    private long totalDurationMillis = 0L;
    private BossBar bossBar = null;
    private BukkitTask updateTask = null;

    public TurboDropManager(HollyCore plugin) {
        this.plugin = plugin;
        HollyDataPaths.ensureResource(plugin, CONFIG_RESOURCE);
        this.configFile = new File(plugin.getDataFolder(), CONFIG_RESOURCE);
        reload();
    }

    public synchronized void reload() {
        HollyDataPaths.ensureResource(plugin, CONFIG_RESOURCE);
        this.config = YamlConfiguration.loadConfiguration(configFile);

        this.bossBarTitleTemplate = config.getString("bossbar.title", "&7Aktualnie trwa event &bTurboDrop &3x{MULTIPLIER}&7, jeszcze przez &b{TIME} &7⌚");
        this.bossBarColor = parseBarColor(config.getString("bossbar.color", "BLUE"));
        this.bossBarStyle = parseBarStyle(config.getString("bossbar.style", "SOLID"));

        this.msgStartSender = config.getString("messages.start-sender", "&aPomyślnie uruchomiono event TurboDrop x{MULTIPLIER} na czas {TIME}!");
        this.msgStartBroadcast = config.getString("messages.start-broadcast", "&7Uruchomiono event &bTurboDrop &3x{MULTIPLIER} &7na czas &b{TIME}&7!");
        this.msgStopSender = config.getString("messages.stop-sender", "&aZatrzymano event TurboDrop.");
        this.msgStopBroadcast = config.getString("messages.stop-broadcast", "&cEvent &bTurboDrop &czostał zatrzymany przez administratora.");
        this.msgEndBroadcast = config.getString("messages.end-broadcast", "&cEvent &bTurboDrop &czakończył się!");
        this.msgNoActiveEvent = config.getString("messages.no-active-event", "&cAktualnie nie trwa żaden event TurboDrop.");
        this.msgReloadSuccess = config.getString("messages.reload-success", "&aPrzeładowano konfigurację TurboDrop.");

        if (isTurboDropActive() && bossBar != null) {
            bossBar.setColor(bossBarColor);
            bossBar.setStyle(bossBarStyle);
            bossBar.setTitle(ColorUtils.colorize(buildBossBarTitle()));
        }
    }

    public synchronized void startTurboDrop(double multiplier, long durationMillis) {
        stopTurboDrop();

        this.activeMultiplier = multiplier;
        this.totalDurationMillis = durationMillis;
        this.endTimeMillis = System.currentTimeMillis() + durationMillis;

        String formattedTitle = buildBossBarTitle();
        this.bossBar = Bukkit.createBossBar(ColorUtils.colorize(formattedTitle), bossBarColor, bossBarStyle);
        for (Player player : Bukkit.getOnlinePlayers()) {
            this.bossBar.addPlayer(player);
        }
        this.bossBar.setVisible(true);

        this.updateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::update, 20L, 20L);
    }

    public synchronized void stopTurboDrop() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar.setVisible(false);
            bossBar = null;
        }
        this.activeMultiplier = 1.0D;
        this.endTimeMillis = 0L;
        this.totalDurationMillis = 0L;
    }

    public boolean isTurboDropActive() {
        return activeMultiplier > 1.0D && System.currentTimeMillis() < endTimeMillis;
    }

    public double getMultiplier() {
        return isTurboDropActive() ? activeMultiplier : 1.0D;
    }

    public long getRemainingSeconds() {
        if (!isTurboDropActive()) {
            return 0L;
        }
        long diff = endTimeMillis - System.currentTimeMillis();
        return Math.max(0L, (diff + 999L) / 1000L);
    }

    private void update() {
        long remainingMillis = endTimeMillis - System.currentTimeMillis();
        if (remainingMillis <= 0) {
            stopTurboDrop();
            if (msgEndBroadcast != null && !msgEndBroadcast.isBlank()) {
                Bukkit.broadcast(ColorUtils.colorizeComponent(msgEndBroadcast));
            }
            return;
        }

        if (bossBar != null) {
            double progress = Math.max(0.0D, Math.min(1.0D, (double) remainingMillis / (double) totalDurationMillis));
            bossBar.setProgress(progress);
            bossBar.setTitle(ColorUtils.colorize(buildBossBarTitle()));
        }
    }

    public String buildBossBarTitle() {
        String multiplierStr = formatMultiplier(activeMultiplier);
        String timeStr = formatDuration(getRemainingSeconds());
        return bossBarTitleTemplate
            .replace("{MULTIPLIER}", multiplierStr)
            .replace("{TIME}", timeStr);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (isTurboDropActive() && bossBar != null) {
            bossBar.addPlayer(event.getPlayer());
        }
    }

    private BarColor parseBarColor(String colorStr) {
        if (colorStr == null) return BarColor.BLUE;
        try {
            return BarColor.valueOf(colorStr.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return BarColor.BLUE;
        }
    }

    private BarStyle parseBarStyle(String styleStr) {
        if (styleStr == null) return BarStyle.SOLID;
        try {
            return BarStyle.valueOf(styleStr.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return BarStyle.SOLID;
        }
    }

    public static String formatMultiplier(double multiplier) {
        if (multiplier == (long) multiplier) {
            return String.format(Locale.US, "%d", (long) multiplier);
        }
        return String.format(Locale.US, "%.2f", multiplier).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    public static String formatDuration(long seconds) {
        if (seconds <= 0) {
            return "0s";
        }
        long days = seconds / 86400L;
        long hours = (seconds % 86400L) / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long secs = seconds % 60L;

        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append("d");
        }
        if (hours > 0) {
            sb.append(hours).append("h");
        }
        if (minutes > 0) {
            sb.append(minutes).append("m");
        }
        if (secs > 0 || sb.length() == 0) {
            sb.append(secs).append("s");
        }
        return sb.toString();
    }

    public String getMsgStartSender() { return msgStartSender; }
    public String getMsgStartBroadcast() { return msgStartBroadcast; }
    public String getMsgStopSender() { return msgStopSender; }
    public String getMsgStopBroadcast() { return msgStopBroadcast; }
    public String getMsgEndBroadcast() { return msgEndBroadcast; }
    public String getMsgNoActiveEvent() { return msgNoActiveEvent; }
    public String getMsgReloadSuccess() { return msgReloadSuccess; }
}
