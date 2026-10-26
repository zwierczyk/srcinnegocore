package dev.grzybalson.anarchiagg.zmiankibattle.util;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class BossBarTracker implements Listener {

    private final JavaPlugin plugin;
    private final Set<BossBar> active = Collections.synchronizedSet(new HashSet<>());

    public BossBarTracker(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public BossBar createGlobalBar(String legacyTitle, BarColor color) {
        BossBar bar = Bukkit.createBossBar(Text.color(legacyTitle), color, BarStyle.SOLID);
        bar.setProgress(1.0D);
        bar.setVisible(true);
        for (Player p : Bukkit.getOnlinePlayers()) {
            bar.addPlayer(p);
        }
        active.add(bar);
        return bar;
    }

    public BarColor readColor(FileConfiguration cfg, String path, BarColor def) {
        String raw = cfg.getString(path);
        if (raw == null) return def;
        try {
            return BarColor.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return def;
        }
    }

    public void remove(BossBar bar) {
        if (bar == null) return;
        bar.removeAll();
        active.remove(bar);
    }

    public void clearAll() {
        for (BossBar bar : active.toArray(new BossBar[0])) {
            remove(bar);
        }
        active.clear();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        for (BossBar bar : active.toArray(new BossBar[0])) {
            bar.addPlayer(p);
        }
    }
}
