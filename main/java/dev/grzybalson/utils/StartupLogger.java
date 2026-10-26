package dev.grzybalson.utils;

import dev.grzybalson.HollyCore;
import dev.grzybalson.internal.HollyIntegrityGuard;
import dev.grzybalson.listeners.CoreStartupGate;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Start HollyCore — cały raport w jednym bloku (bez tick-by-tick),
 * żeby flood join/kick z Paper nie mieszał się między liniami modułów.
 */
public class StartupLogger {

    private static final String PREFIX = "§8[§c§lHolly§4§lCore§8]";
    private static final String LINE = "§8§m────────────────────────────────────────────────────────────";
    private static final int MODULES_PER_ROW = 3;

    private final JavaPlugin plugin;
    private final List<ModuleStatus> modules = new ArrayList<>();
    private final List<String> buffer = new ArrayList<>();
    private long startTime;
    private boolean buffering = true;
    private boolean bannerPrinted;
    private boolean flushed;
    private boolean finaleRunning;

    public StartupLogger(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        this.startTime = System.currentTimeMillis();
        this.buffering = true;
        this.flushed = false;
        this.finaleRunning = false;
        this.buffer.clear();
        printBanner();
    }

    public void progress(String phase, int percent) {
        // no-op
    }

    public void animateProgress(String phase, int from, int to, int step) {
        // no-op
    }

    public void addModule(String name, boolean loaded) {
        modules.add(new ModuleStatus(name, loaded));
    }

    public void logAddonInit(String addonName, boolean enabled) {
        // Ciszej przy starcie — szczegóły w finale (moduły).
    }

    public void logAddonReady(String addonName, boolean enabled) {
        // Ciszej przy starcie — szczegóły w finale (moduły).
    }

    public void printStartup() {
        if (plugin instanceof HollyCore hollyCore) {
            HollyIntegrityGuard.requireStrong(hollyCore, "startup:print");
        }
    }

    /** Cały raport od razu w jednym ticku — bez przeplotu z kickami graczy. */
    public void playStartupFinale() {
        if (flushed || finaleRunning) {
            return;
        }
        finaleRunning = true;

        long loadTime = Math.max(0L, System.currentTimeMillis() - startTime);
        String version = plugin.getDescription().getVersion();
        int loaded = countLoaded();
        int total = modules.size();

        List<String> preamble = new ArrayList<>(buffer);
        buffer.clear();
        buffering = false;
        flushed = true;

        List<String> report = new ArrayList<>();
        report.addAll(preamble);
        report.add(LINE);
        report.add(PREFIX + " §7Moduly §8(" + loaded + "/" + total + "§8):");
        report.addAll(formatModuleRows());
        report.add(LINE);
        report.add(PREFIX + " §a✔ §fSystem gotowy §8┃ §7v§f" + version + " §8┃ §c" + loadTime + "ms");
        report.add(PREFIX + " §7Wszystkie pluginy zaladowane — wpuszczam graczy.");
        report.add(LINE);
        report.add("");

        // Jeden tick później — cały blok atomowo, potem od razu otwieramy bramkę.
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (String line : report) {
                sendConsole(line);
            }
            finaleRunning = false;
            CoreStartupGate.markReady();
        });
    }

    public void printShutdown() {
        buffering = false;
        sendConsole("");
        sendConsole(LINE);
        logInfo("Wylaczanie HollyCore...");
        logSuccess("Wylaczono pomyslnie.");
        sendConsole(LINE);
        sendConsole("");
    }

    public void logInfo(String message) {
        queueOrSend(PREFIX + " §7» §f" + message);
    }

    public void logSuccess(String message) {
        queueOrSend(PREFIX + " §a✔ §f" + message);
    }

    public void logWarning(String message) {
        queueOrSend(PREFIX + " §c! §7" + message);
    }

    public void logError(String message) {
        queueOrSend(PREFIX + " §4✘ §c" + message);
    }

    private void printBanner() {
        if (bannerPrinted) {
            return;
        }
        bannerPrinted = true;
        String version = plugin.getDescription().getVersion();
        queue("");
        queue(LINE);
        queue("  §8╔════════════════════════════════════════════════════════╗");
        queue("  §8║                                                      §8║");
        queue("  §8║" + centerPlain("§c§lHolly§4§lCore", 54) + "§8║");
        queue("  §8║" + centerPlain("§7v§f" + version + " §8┃ §7Silnik serwera Minecraft", 54) + "§8║");
        queue("  §8║                                                      §8║");
        queue("  §8╚════════════════════════════════════════════════════════╝");
        queue(LINE);
        queue(PREFIX + " §7Ladowanie… raport na koniec, po wszystkich pluginach");
        queue(PREFIX + " §7Wejscie graczy zablokowane do gotowosci.");
        queue("");
    }

    private List<String> formatModuleRows() {
        List<String> rows = new ArrayList<>();
        if (modules.isEmpty()) {
            return rows;
        }
        StringBuilder row = new StringBuilder();
        int inRow = 0;
        for (ModuleStatus module : modules) {
            if (inRow > 0) {
                row.append(" §8│ ");
            } else {
                row.append(PREFIX).append(' ');
            }
            row.append(module.loaded ? "§a✔" : "§c✘")
                .append(" §f")
                .append(shortName(module.name));
            inRow++;
            if (inRow >= MODULES_PER_ROW) {
                rows.add(row.toString());
                row = new StringBuilder();
                inRow = 0;
            }
        }
        if (inRow > 0) {
            rows.add(row.toString());
        }
        return rows;
    }

    private static String shortName(String name) {
        if (name == null) {
            return "";
        }
        return name.length() > 18 ? name.substring(0, 17) + "…" : name;
    }

    private void queueOrSend(String message) {
        if (buffering && !flushed) {
            queue(message);
            return;
        }
        sendConsole(message);
    }

    private void queue(String message) {
        buffer.add(message);
    }

    private void sendConsole(String message) {
        Bukkit.getConsoleSender().sendMessage(message);
    }

    private int countLoaded() {
        int count = 0;
        for (ModuleStatus module : modules) {
            if (module.loaded) {
                count++;
            }
        }
        return count;
    }

    private static String centerPlain(String text, int width) {
        if (text == null) {
            text = "";
        }
        String visible = text.replaceAll("§.", "");
        int pad = Math.max(0, (width - visible.length()) / 2);
        int right = Math.max(0, width - visible.length() - pad);
        return " ".repeat(pad) + text + " ".repeat(right);
    }

    private static class ModuleStatus {
        final String name;
        final boolean loaded;

        ModuleStatus(String name, boolean loaded) {
            this.name = name;
            this.loaded = loaded;
        }
    }
}
