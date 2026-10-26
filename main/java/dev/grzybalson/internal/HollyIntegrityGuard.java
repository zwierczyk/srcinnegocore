package dev.grzybalson.internal;

import dev.grzybalson.HollyCore;
import dev.grzybalson.license.LicenseManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginDescriptionFile;

import java.io.InputStream;
import java.util.logging.Level;

public final class HollyIntegrityGuard {

    private static final long WATCHDOG_DELAY_TICKS = 20L * 30L;
    private static final long WATCHDOG_PERIOD_TICKS = 20L * 90L;
    private static final Object PREFLIGHT_LOCK = new Object();

    private static volatile boolean preflightVerified;
    private static volatile int watchdogTaskId = -1;

    private HollyIntegrityGuard() {
    }

    public static void preflight(HollyCore plugin, String area) {
        if (plugin == null) {
            throw new IllegalStateException("HollyCore integrity preflight without plugin: " + area);
        }

        if (preflightVerified) {
            return;
        }

        synchronized (PREFLIGHT_LOCK) {
            if (preflightVerified) {
                return;
            }

            try {
                verifyPluginDescription(plugin);
                verifyRuntimeClasses();
                verifyBundledResources(plugin);
                dev.grzybalson.protect.N.attach();
                preflightVerified = true;
            } catch (Throwable throwable) {
                fail(plugin, "integrity:" + cleanArea(area), throwable);
            }
        }
    }

    public static void requireBound(HollyCore plugin, String area) {
        preflight(plugin, area);
        HollyRuntime.require(plugin, area);
    }

    public static void requireStrong(HollyCore plugin, String area) {
        preflight(plugin, area);
        HollyRuntime.requireStrong(plugin, area);
    }

    public static void startWatchdog(HollyCore plugin) {
        requireStrong(plugin, "integrity-watchdog:start");

        if (watchdogTaskId != -1) {
            Bukkit.getScheduler().cancelTask(watchdogTaskId);
            watchdogTaskId = -1;
        }

        watchdogTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            try {
                requireStrong(plugin, "integrity-watchdog:tick");
            } catch (Throwable ignored) {
                // requireStrong already disables the plugin when it fails.
            }
        }, WATCHDOG_DELAY_TICKS, WATCHDOG_PERIOD_TICKS);
    }

    public static void stopWatchdog() {
        if (watchdogTaskId != -1) {
            Bukkit.getScheduler().cancelTask(watchdogTaskId);
            watchdogTaskId = -1;
        }
    }

    private static void verifyPluginDescription(HollyCore plugin) {
        PluginDescriptionFile description = plugin.getDescription();
        if (!"HollyCore".equals(description.getName())) {
            throw new IllegalStateException("invalid plugin name");
        }
        if (!HollyCore.class.getName().equals(description.getMain())) {
            throw new IllegalStateException("invalid plugin main");
        }
    }

    private static void verifyRuntimeClasses() throws ReflectiveOperationException {
        LicenseManager.class.getDeclaredConstructor(HollyCore.class);
        if (HollyRuntime.class == null || HollyIntegrityGuard.class == null) {
            throw new IllegalStateException("runtime class unavailable");
        }
    }

    private static void verifyBundledResources(HollyCore plugin) throws Exception {
        requireResource(plugin, "plugin.yml");
        requireResource(plugin, "license.yml");
    }

    private static void requireResource(HollyCore plugin, String resource) throws Exception {
        try (InputStream inputStream = plugin.getResource(resource)) {
            if (inputStream == null) {
                throw new IllegalStateException("missing resource: " + resource);
            }
        }
    }

    private static void fail(HollyCore plugin, String area, Throwable throwable) {
        String where = cleanArea(area);
        try {
            plugin.getLogger().log(Level.SEVERE, "[HollyCore] Naruszono modul weryfikacji: " + where, throwable);
            if (plugin.isEnabled()) {
                Bukkit.getPluginManager().disablePlugin(plugin);
            }
        } catch (Throwable ignored) {
        }
        throw new IllegalStateException("HollyCore module state check failed: " + where, throwable);
    }

    private static String cleanArea(String area) {
        return area == null || area.isBlank() ? "unknown" : area;
    }
}
