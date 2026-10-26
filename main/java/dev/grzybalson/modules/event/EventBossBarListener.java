package dev.grzybalson.modules.event;

import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EventBossBarListener implements Listener {

    private static final String WORLD_NAME = "strefabossa";
    private static final String TITLE = "&aZnajdujesz się na strefie eventowej w której jest keep inventory!";
    private static final long LICENSE_CACHE_MS = 30_000L;

    private final HollyCore plugin;
    private final Map<UUID, BossBar> bars = new ConcurrentHashMap<>();
    private final String coloredTitle = ColorUtils.colorize(TITLE);
    private long lastLicenseCheck;
    private boolean lastMobsEnabled;

    public EventBossBarListener(HollyCore plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, () -> Bukkit.getOnlinePlayers().forEach(this::update), 40L, 40L);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> update(event.getPlayer()), 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        remove(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        update(event.getPlayer());
    }

    private void update(Player player) {
        if (!player.isOnline()) {
            remove(player);
            return;
        }
        World world = player.getWorld();
        boolean shouldShow = isMobsEnabledCached() && world != null && WORLD_NAME.equalsIgnoreCase(world.getName());
        if (!shouldShow) {
            remove(player);
            return;
        }
        BossBar bar = bars.computeIfAbsent(player.getUniqueId(), uuid -> Bukkit.createBossBar(coloredTitle, BarColor.GREEN, BarStyle.SOLID));
        // Title/kolor/progress sa stale, nie ustawiamy ich co tick dla kazdego gracza.
        if (!bar.getPlayers().contains(player)) {
            bar.addPlayer(player);
        }
    }

    private boolean isMobsEnabledCached() {
        long now = System.currentTimeMillis();
        if (now - lastLicenseCheck >= LICENSE_CACHE_MS) {
            lastLicenseCheck = now;
            lastMobsEnabled = plugin.isMobsFeatureEnabled();
        }
        return lastMobsEnabled;
    }

    private void remove(Player player) {
        BossBar bar = bars.remove(player.getUniqueId());
        if (bar != null) {
            bar.removeAll();
        }
    }
}
