package dev.grzybalson.modules.bossbar;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.modules.bossbar.BossBarConfig;
import dev.grzybalson.utils.ColorUtils;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

public class BossBarManager {

    private final HollyCore plugin;
    private final List<BossBar> activeBars = new ArrayList<>();
    private final List<String> lastRenderedTitles = new ArrayList<>();
    private List<BossBarConfig.BossBarEntry> cachedBarConfigs;
    private BukkitTask updateTask;
    private boolean papiEnabled;

    public BossBarManager(HollyCore plugin) {
        this.plugin = plugin;
        this.papiEnabled = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
    }

    public void start() {
        BossBarConfig config = plugin.getModuleConfigManager().getBossBarConfig();
        if (!config.isEnabled()) return;

        List<BossBarConfig.BossBarEntry> barConfigs = visibleBars(config);
        if (barConfigs.isEmpty()) return;

        cachedBarConfigs = barConfigs;

        for (BossBarConfig.BossBarEntry entry : barConfigs) {
            String title = ScheduledDropService.applyPlaceholders(plugin, entry.getText());
            BossBar bar = Bukkit.createBossBar(
                ColorUtils.colorize(title),
                parseColor(entry.getColor()),
                parseStyle(entry.getStyle())
            );
            double progress = ScheduledDropService.usesDropPlaceholders(entry.getText())
                ? ScheduledDropService.getProgress(plugin)
                : entry.getProgress();
            bar.setProgress(Math.max(0.0, Math.min(1.0, progress)));

            for (Player player : Bukkit.getOnlinePlayers()) {
                bar.addPlayer(player);
            }

            activeBars.add(bar);
            lastRenderedTitles.add(ColorUtils.colorize(title));
        }

        int updateInterval = config.getUpdateIntervalTicks();
        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateAllBars, updateInterval, updateInterval);
    }

    private static List<BossBarConfig.BossBarEntry> visibleBars(BossBarConfig config) {
        List<BossBarConfig.BossBarEntry> source = config.getBars();
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        boolean keepDrop = config.isScheduledDropBossbarEnabled();
        List<BossBarConfig.BossBarEntry> filtered = new ArrayList<>(source.size());
        for (BossBarConfig.BossBarEntry entry : source) {
            if (entry == null) {
                continue;
            }
            if (!keepDrop && ScheduledDropService.usesDropPlaceholders(entry.getText())) {
                continue;
            }
            filtered.add(entry);
        }
        return filtered;
    }

    private void updateAllBars() {
        if (cachedBarConfigs == null) return;
        int size = Math.min(activeBars.size(), cachedBarConfigs.size());

        for (int i = 0; i < size; i++) {
            BossBar bar = activeBars.get(i);
            List<Player> players = bar.getPlayers();
            if (players.isEmpty()) continue;

            String renderedTitle = ColorUtils.colorize(parsePlaceholders(cachedBarConfigs.get(i).getText(), players.get(0)));
            if (!renderedTitle.equals(lastRenderedTitles.get(i))) {
                bar.setTitle(renderedTitle);
                lastRenderedTitles.set(i, renderedTitle);
            }
            if (ScheduledDropService.usesDropPlaceholders(cachedBarConfigs.get(i).getText())) {
                bar.setProgress(ScheduledDropService.getProgress(plugin));
            }
        }
    }

    private String parsePlaceholders(String text, Player player) {
        String parsed = ScheduledDropService.applyPlaceholders(plugin, text);
        if (papiEnabled && player != null) {
            return PlaceholderAPI.setPlaceholders(player, parsed);
        }
        return parsed;
    }

    private BarColor parseColor(String color) {
        if (color == null || color.isBlank()) {
            return BarColor.WHITE;
        }
        try {
            return BarColor.valueOf(color.toUpperCase());
        } catch (IllegalArgumentException e) {
            return BarColor.WHITE;
        }
    }

    private BarStyle parseStyle(String style) {
        if (style == null || style.isBlank()) {
            return BarStyle.SOLID;
        }
        try {
            return BarStyle.valueOf(style.toUpperCase());
        } catch (IllegalArgumentException e) {
            return BarStyle.SOLID;
        }
    }

    public void addPlayer(Player player) {
        for (BossBar bar : activeBars) {
            bar.addPlayer(player);
        }
    }

    public void removePlayer(Player player) {
        for (BossBar bar : activeBars) {
            bar.removePlayer(player);
        }
    }

    public void stop() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
        for (BossBar bar : activeBars) {
            bar.removeAll();
        }
        activeBars.clear();
        lastRenderedTitles.clear();
    }

    public void reload() {
        stop();
        start();
    }
}
