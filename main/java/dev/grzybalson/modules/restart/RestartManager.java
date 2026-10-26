package dev.grzybalson.modules.restart;

import dev.grzybalson.HollyCore;
import dev.grzybalson.listeners.RestartFreezeListener;
import dev.grzybalson.config.configs.RestartConfig;
import dev.grzybalson.utils.ColorUtils;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public class RestartManager {

    private final HollyCore plugin;
    private BossBar restartBossBar;
    private BukkitTask restartTask;
    private boolean isRestarting = false;
    private int totalTime;
    private int timeLeft;
    private RestartFreezeListener freezeListener;

    public RestartManager(HollyCore plugin) {
        this.plugin = plugin;
    }

    public void setFreezeListener(RestartFreezeListener freezeListener) {
        this.freezeListener = freezeListener;
    }

    public void startRestart(int seconds) {
        if (isRestarting) {
            cancelRestart();
        }

        this.totalTime = seconds;
        this.isRestarting = true;

        RestartConfig config = plugin.getConfigManager().getRestartConfig();

        if (plugin.getCombatManager() != null) {
            plugin.getCombatManager().setEnabled(false);
        }
        Bukkit.broadcast(ColorUtils.colorizeComponent(config.getAntylogoutDisabledMessage()));

        // Anty-dupe: zamknij trade, shulkery, zamróź, PvP off.
        if (plugin.getTradeManager() != null) {
            plugin.getTradeManager().cancelAllTrades("restart");
        }
        if (plugin.getShulkerBoxListener() != null) {
            plugin.getShulkerBoxListener().flushAll();
        }
        if (freezeListener != null) {
            freezeListener.freezeAllOnline();
        }
        Bukkit.broadcast(ColorUtils.colorizeComponent(
            "&c✕ &8Ξ &cPvP wyłączone — gracze zamrożeni do restartu (anty-duplikacja)."
        ));

        String startMessage = config.getRestartStartMessage().replace("{time}", String.valueOf(seconds));
        Bukkit.broadcast(ColorUtils.colorizeComponent(startMessage));

        restartBossBar = createBossBar(config);

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showBossBar(restartBossBar);
        }

        timeLeft = seconds;
        restartTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (timeLeft <= 0) {
                    executeRestart();
                    cancel();
                    return;
                }

                String title = config.getRestartBossBarTitle().replace("{time}", formatTime(timeLeft));
                restartBossBar.name(ColorUtils.colorizeComponent(title));
                restartBossBar.progress(Math.max(0, Math.min(1, (float) timeLeft / totalTime)));

                net.kyori.adventure.title.Title.Times times = net.kyori.adventure.title.Title.Times.times(
                    java.time.Duration.ofMillis(0),
                    java.time.Duration.ofMillis(1200),
                    java.time.Duration.ofMillis(300)
                );
                net.kyori.adventure.title.Title screenTitle = net.kyori.adventure.title.Title.title(
                    ColorUtils.colorizeComponent("&#FF0000&lRESTART SERWERA"),
                    ColorUtils.colorizeComponent("&7Restart za: &e" + formatTime(timeLeft)),
                    times
                );
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.showTitle(screenTitle);
                }

                if (config.getCountdownSoundTimes().contains(timeLeft)) {
                    playCountdownSound(config);
                }

                timeLeft--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    public void startRollbackRestart(int seconds, java.util.Date targetDate) {
        if (isRestarting) {
            cancelRestart();
        }

        this.totalTime = seconds;
        this.isRestarting = true;

        if (plugin.getCombatManager() != null) {
            plugin.getCombatManager().setEnabled(false);
        }
        if (plugin.getTradeManager() != null) {
            plugin.getTradeManager().cancelAllTrades("restart");
        }
        if (plugin.getShulkerBoxListener() != null) {
            plugin.getShulkerBoxListener().flushAll();
        }
        if (freezeListener != null) {
            freezeListener.freezeAllOnline();
        }

        String formattedDate = dev.grzybalson.modules.backup.RollbackManager.DATE_FORMATTER.format(targetDate.toInstant());
        Bukkit.broadcast(ColorUtils.colorizeComponent("&8&m--------------------------------------------------"));
        Bukkit.broadcast(ColorUtils.colorizeComponent("&#FF0000&lRESTART SERWERA PO COFCE"));
        Bukkit.broadcast(ColorUtils.colorizeComponent("&7Stan serwera został cofnięty do: &f" + formattedDate));
        Bukkit.broadcast(ColorUtils.colorizeComponent("&7Restart za: &e" + seconds + "s"));
        Bukkit.broadcast(ColorUtils.colorizeComponent("&cPvP off — gracze zamrożeni (anty-dupe)."));
        Bukkit.broadcast(ColorUtils.colorizeComponent("&8&m--------------------------------------------------"));

        restartBossBar = BossBar.bossBar(
            ColorUtils.colorizeComponent("&#FF0000⚠ &8Ξ &#FF5555&lRESTART PO COFCE: &f" + formatTime(seconds)),
            1.0f,
            BossBar.Color.RED,
            BossBar.Overlay.PROGRESS
        );

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showBossBar(restartBossBar);
        }

        timeLeft = seconds;
        restartTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (timeLeft <= 0) {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        player.kick(ColorUtils.colorizeComponent("&c&lRESTART SERWERA PO COFCE\n&7Stan serwera cofnięto do: &f" + formattedDate + "\n&eDołącz ponownie za chwilę."));
                    }
                    cleanup();
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        Bukkit.getServer().shutdown();
                    }, 10L);
                    cancel();
                    return;
                }

                restartBossBar.name(ColorUtils.colorizeComponent("&#FF0000⚠ &8Ξ &#FF5555&lRESTART PO COFCE: &f" + formatTime(timeLeft)));
                restartBossBar.progress(Math.max(0, Math.min(1, (float) timeLeft / totalTime)));

                net.kyori.adventure.title.Title.Times times = net.kyori.adventure.title.Title.Times.times(
                    java.time.Duration.ofMillis(0),
                    java.time.Duration.ofMillis(1200),
                    java.time.Duration.ofMillis(300)
                );
                net.kyori.adventure.title.Title screenTitle = net.kyori.adventure.title.Title.title(
                    ColorUtils.colorizeComponent("&#FF0000&lRESTART SERWERA"),
                    ColorUtils.colorizeComponent("&eCofka do &f" + formattedDate + " &7- restart za: &c" + formatTime(timeLeft)),
                    times
                );
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.showTitle(screenTitle);
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);
                }

                timeLeft--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private BossBar createBossBar(RestartConfig config) {
        BossBar.Color color;
        try {
            color = BossBar.Color.valueOf(config.getBossBarColor().toUpperCase());
        } catch (IllegalArgumentException e) {
            color = BossBar.Color.RED;
        }

        BossBar.Overlay overlay;
        try {
            overlay = BossBar.Overlay.valueOf(config.getBossBarStyle().toUpperCase());
        } catch (IllegalArgumentException e) {
            overlay = BossBar.Overlay.PROGRESS;
        }

        return BossBar.bossBar(
            Component.empty(),
            1.0f,
            color,
            overlay
        );
    }

    private String formatTime(int seconds) {
        if (seconds >= 60) {
            int minutes = seconds / 60;
            int secs = seconds % 60;
            return minutes + "m " + secs + "s";
        }
        return seconds + "s";
    }

    private void playCountdownSound(RestartConfig config) {
        Sound sound;
        try {
            sound = Sound.valueOf(config.getCountdownSound());
        } catch (IllegalArgumentException e) {
            sound = Sound.BLOCK_NOTE_BLOCK_PLING;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), sound, config.getCountdownSoundVolume(), config.getCountdownSoundPitch());
        }
    }

    private void playFinalSound(RestartConfig config) {
        Sound sound;
        try {
            sound = Sound.valueOf(config.getFinalSound());
        } catch (IllegalArgumentException e) {
            sound = Sound.ENTITY_ENDER_DRAGON_GROWL;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), sound, config.getFinalSoundVolume(), config.getFinalSoundPitch());
        }
    }

    private void executeRestart() {
        RestartConfig config = plugin.getConfigManager().getRestartConfig();
        Bukkit.broadcast(ColorUtils.colorizeComponent(config.getRestartEndMessage()));
        playFinalSound(config);

        cleanup();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Bukkit.getServer().shutdown();
        }, 20L);
    }

    public void cancelRestart() {
        if (!isRestarting) return;

        cleanup();

        if (plugin.getCombatManager() != null) {
            plugin.getCombatManager().setEnabled(true);
        }
        if (freezeListener != null) {
            freezeListener.unfreezeAll();
        }

        Bukkit.broadcast(ColorUtils.colorizeComponent("&#00E0FFℹ &8Ξ &eRestart został anulowany!"));
    }

    private void cleanup() {
        isRestarting = false;

        if (restartTask != null) {
            restartTask.cancel();
            restartTask = null;
        }

        if (restartBossBar != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.hideBossBar(restartBossBar);
            }
            restartBossBar = null;
        }
    }

    public boolean isRestarting() {
        return isRestarting;
    }

    public int getTimeLeft() {
        return timeLeft;
    }

    public void handlePlayerJoin(Player player) {
        if (isRestarting && restartBossBar != null) {
            player.showBossBar(restartBossBar);
        }
        if (isRestarting && freezeListener != null) {
            freezeListener.freezePlayer(player);
        }
    }

    public void shutdown() {
        if (freezeListener != null) {
            freezeListener.unfreezeAll();
        }
        cleanup();
    }
}
