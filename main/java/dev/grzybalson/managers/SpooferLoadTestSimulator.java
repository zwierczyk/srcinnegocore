package dev.grzybalson.managers;

import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.SpooferBridge;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

/**
 * Realistyczna symulacja kosztu pluginow przy N botach.
 * <p>
 * NIE symuluje chunkow / ruchu / entity — tylko tab + scoreboard + nametagi
 * w tempie zblizonym do prawdziwego odswiezania (nie co tick 32x).
 */
public final class SpooferLoadTestSimulator {

    private final HollyCore plugin;
    private BukkitTask task;
    private int tickCounter;

    public SpooferLoadTestSimulator(HollyCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        // Co 1s — jak typowy refresh tablisty, nie co tick.
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        tickCounter = 0;
    }

    private void tick() {
        if (!SpooferBridge.isLoadTestMode()) {
            return;
        }
        int bots = SpooferBridge.getFakeCount();
        if (bots <= 0) {
            return;
        }

        tickCounter++;
        TablistManager tablist = plugin.getTablistManager();
        SidebarManager sidebar = plugin.getSidebarManager();

        // Co 1s: jedna przebiezka nametagow botow (jak przy odswiezeniu identity).
        if (tablist != null) {
            tablist.runSpooferLoadTestPass(bots);
        }

        // Co 3s: scoreboard jak przy pelnym online (batch).
        if (sidebar != null && tickCounter % 3 == 0) {
            sidebar.runLoadTestPass(bots);
        }
    }
}
