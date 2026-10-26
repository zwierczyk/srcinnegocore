package dev.grzybalson.modules.diagnostics;

import com.sun.management.OperatingSystemMXBean;
import dev.grzybalson.HollyCore;
import dev.grzybalson.config.HollyDataPaths;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Animals;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public final class ServerDiagnosticsService {

    private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter LOG_DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    /** Minimalna liczba graczy, przy ktorej stosunek chunki/gracz ma sens. */
    private static final int MIN_PLAYERS_FOR_RATIO = 5;
    /** Bazowy koszt MSPT jednego nowego gracza (tick, siec, tab). */
    private static final double BASE_MSPT_PER_JOIN = 0.35D;
    /** Szacowany koszt MSPT na chunk zaladowany przez gracza. */
    private static final double MSPT_PER_PLAYER_CHUNK = 0.0035D;
    /** Overhead chunkow spawn/force-load niezalezny od graczy. */
    private static final int STATIC_CHUNK_OVERHEAD = 128;

    private final HollyCore plugin;
    private final File logFolder;
    private final AtomicReference<DiagnosticsSnapshot> latest = new AtomicReference<>(DiagnosticsSnapshot.empty());
    private BukkitTask collectTask;
    private BukkitTask logTask;
    private int collectTick;

    public ServerDiagnosticsService(HollyCore plugin) {
        this.plugin = plugin;
        this.logFolder = HollyDataPaths.getRootFile(plugin, "info/txt-rzeczy");
        if (!logFolder.exists()) {
            logFolder.mkdirs();
        }
    }

    public void start() {
        stop();
        collect();
        collectTask = Bukkit.getScheduler().runTaskTimer(plugin, this::collect, 200L, 200L);
        logTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::writeLog, 1200L, 1200L);
        plugin.getLogger().info("[Diagnostyka] Logi wydajnosci: " + logFolder.getPath());
    }

    public void shutdown() {
        stop();
    }

    public DiagnosticsSnapshot getLatest() {
        DiagnosticsSnapshot snapshot = latest.get();
        return snapshot == null ? DiagnosticsSnapshot.empty() : snapshot;
    }

    private void stop() {
        if (collectTask != null) {
            collectTask.cancel();
            collectTask = null;
        }
        if (logTask != null) {
            logTask.cancel();
            logTask = null;
        }
    }

    private void collect() {
        latest.set(captureSnapshot());
    }

    public DiagnosticsSnapshot captureSnapshot() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long maxMemory = runtime.maxMemory();

        double[] tpsArray = safeTps();
        double tps = tpsArray.length > 0 ? Math.min(20.0, tpsArray[0]) : 20.0;
        double mspt = safeMspt();

        double processCpu = processCpuLoad();
        double systemCpu = systemCpuLoad();
        double ramGb = usedMemory / (1024.0D * 1024.0D * 1024.0D);

        int online = Bukkit.getOnlinePlayers().size();
        int maxPlayers = Bukkit.getMaxPlayers();
        double avgPing = averagePing();

        int loadedChunks = 0;
        for (World world : Bukkit.getWorlds()) {
            loadedChunks += world.getLoadedChunks().length;
        }

        World primaryWorld = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        String worldName = primaryWorld != null ? primaryWorld.getName() : "world";
        String borderText = formatBorder(primaryWorld);

        // Pelny skan encji tylko co 6. probe (~30s) — nie co 5s.
        boolean deepScan = (collectTick++ % 12) == 0;
        DiagnosticsSnapshot previous = latest.get();
        int entities;
        int items;
        int monsters;
        int animals;
        int armorStands;
        Map<String, Integer> entityTypes;
        String heaviestWorld;
        int heaviestEntities;

        if (deepScan || previous == null || previous.capturedAt() <= 0L) {
            entities = 0;
            items = 0;
            monsters = 0;
            animals = 0;
            armorStands = 0;
            entityTypes = new HashMap<>();
            heaviestWorld = "world";
            heaviestEntities = 0;

            for (World world : Bukkit.getWorlds()) {
                int worldEntities = 0;
                for (Entity entity : world.getEntities()) {
                    entities++;
                    worldEntities++;
                    entityTypes.merge(entity.getType().name(), 1, Integer::sum);
                    if (entity instanceof Item) {
                        items++;
                    } else if (entity instanceof Monster) {
                        monsters++;
                    } else if (entity instanceof Animals) {
                        animals++;
                    } else if (entity instanceof ArmorStand) {
                        armorStands++;
                    }
                }
                if (worldEntities > heaviestEntities) {
                    heaviestEntities = worldEntities;
                    heaviestWorld = world.getName();
                }
            }
        } else {
            entities = previous.entities();
            items = previous.items();
            monsters = previous.monsters();
            animals = previous.animals();
            armorStands = previous.armorStands();
            entityTypes = Map.of();
            heaviestWorld = previous.heaviestWorld();
            heaviestEntities = previous.heaviestEntities();
        }

        List<String> lagSources = analyzeLagSources(tps, mspt, online, entities, items, monsters,
            animals, armorStands, loadedChunks, entityTypes, heaviestWorld, heaviestEntities, processCpu, ramGb, maxMemory);
        List<String> predictions = predictLag(mspt, online, maxPlayers, entities, loadedChunks, tps);

        return new DiagnosticsSnapshot(
            tps,
            mspt,
            processCpu,
            systemCpu,
            ramGb,
            avgPing,
            online,
            maxPlayers,
            worldName,
            borderText,
            "NORMAL",
            loadedChunks,
            entities,
            items,
            monsters,
            animals,
            armorStands,
            heaviestWorld,
            heaviestEntities,
            lagSources,
            predictions,
            usedMemory,
            maxMemory,
            System.currentTimeMillis()
        );
    }

    private List<String> analyzeLagSources(double tps, double mspt, int online, int entities, int items,
                                           int monsters, int animals, int armorStands, int loadedChunks,
                                           Map<String, Integer> entityTypes, String heaviestWorld,
                                           int heaviestEntities, double cpu, double ramGb, long maxMemory) {
        List<String> sources = new ArrayList<>();
        int ratioPlayers = Math.max(MIN_PLAYERS_FOR_RATIO, Math.max(1, online));
        double entitiesPerPlayer = entities / (double) ratioPlayers;
        double chunksPerPlayer = loadedChunks / (double) ratioPlayers;
        double ramRatio = maxMemory <= 0L ? 0.0D : (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (double) maxMemory;
        int expectedChunksForPlayers = online * estimateChunksPerPlayer() + STATIC_CHUNK_OVERHEAD;

        if (mspt >= 50.0D) {
            sources.add("Krytyczny MSPT (&c" + format(mspt) + " ms&7) — tick nie nadaza");
        } else if (mspt >= 40.0D) {
            sources.add("Podwyzszony MSPT (&e" + format(mspt) + " ms&7) — ryzyko spadku TPS");
        }

        if (tps < 19.0D) {
            sources.add("Spadek TPS (&c" + format(tps) + "&7) — serwer traci plynnosc");
        }

        if (items >= 800 || (online >= MIN_PLAYERS_FOR_RATIO && items / (double) online >= 35.0D)) {
            sources.add("Duzo przedmiotow na ziemi (&e" + items + "&7" + perPlayerSuffix(online, items) + ")");
        }

        if (online >= MIN_PLAYERS_FOR_RATIO && monsters / (double) online >= 25.0D) {
            sources.add("Nadmier spawn mobow (&e" + monsters + "&7, ~" + format(monsters / (double) online) + "/gracz)");
        }

        if (armorStands >= 120 || (online >= MIN_PLAYERS_FOR_RATIO && armorStands / (double) online >= 8.0D)) {
            sources.add("ArmorStandy obciazaja tick (&e" + armorStands + "&7)");
        }

        if (online >= MIN_PLAYERS_FOR_RATIO && entitiesPerPlayer >= 160.0D) {
            sources.add("Za duzo encji wzgledem graczy (&e" + format(entities / (double) online) + "&7/gracz)");
        }

        if (online >= MIN_PLAYERS_FOR_RATIO && chunksPerPlayer >= 180.0D) {
            sources.add("Za duzo chunkow na gracza (&e" + format(loadedChunks / (double) online) + "&7/gracz)");
        } else if (loadedChunks > expectedChunksForPlayers + 200) {
            sources.add("Chunki force-load/spawn (&e" + loadedChunks + "&7, oczekiwane ~" + expectedChunksForPlayers + ")");
        }

        if (cpu >= 0.85D) {
            sources.add("Wysokie CPU procesu (&e" + formatPercent(cpu) + "&7)");
        }

        if (ramRatio >= 0.82D) {
            sources.add("Presja RAM (&e" + format(ramGb) + " GB&7, " + formatPercent(ramRatio) + " limitu)");
        }

        Map.Entry<String, Integer> topEntity = entityTypes.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .orElse(null);
        if (topEntity != null && topEntity.getValue() >= 400) {
            sources.add("Dominuje typ &f" + topEntity.getKey() + " &7(&e" + topEntity.getValue() + "&7 encji)");
        }

        if (heaviestEntities >= 2000) {
            sources.add("Najciezszy swiat &f" + heaviestWorld + " &7(&e" + heaviestEntities + " encji&7)");
        }

        if (sources.isEmpty()) {
            sources.add("&aBrak oczywistych zrodel lagu — serwer stabilny");
        }

        return sources.stream().limit(5).toList();
    }

    private List<String> predictLag(double mspt, int online, int maxPlayers, int entities, int chunks, double tps) {
        List<String> predictions = new ArrayList<>();
        int chunksPerJoin = estimateChunksPerPlayer();
        double msptPerJoin = estimateMsptPerJoin(mspt, online, entities, chunks, chunksPerJoin);
        double fixedChunkMspt = estimateFixedChunkMspt(online, chunks, chunksPerJoin);

        int[] scenarios = new int[]{10, 25, 50};
        for (int extra : scenarios) {
            int projectedOnline = online + extra;
            if (projectedOnline > maxPlayers) {
                continue;
            }
            // Liniowo per gracz + lekka nadwyzka przy duzej liczbie (kolizje chunkow, entity activation)
            double loadFactor = 1.0D + (extra >= 25 ? 0.08D : 0.0D) + (extra >= 50 ? 0.07D : 0.0D);
            double addedMspt = extra * msptPerJoin * loadFactor;
            double projectedMspt = mspt + fixedChunkMspt + addedMspt;
            double projectedTps = Math.max(0.0D, 20.0D - Math.max(0.0D, projectedMspt - 50.0D) / 10.0D);
            String status = projectedMspt >= 50.0D ? "&cLAGI" : projectedMspt >= 40.0D ? "&eRYZYKO" : "&aOK";
            predictions.add("+" + extra + " graczy -> MSPT &f" + format(projectedMspt)
                + " &7(+&f" + format(addedMspt) + "&7) | TPS &f" + format(projectedTps) + " &7| " + status);
        }

        if (online < MIN_PLAYERS_FOR_RATIO) {
            predictions.add("&7Prognoza modelowa (~" + format(msptPerJoin) + " ms/gracz, "
                + chunksPerJoin + " chunkow/gracz) — malo graczy online");
        }

        if (online >= maxPlayers * 0.85D) {
            predictions.add("&cLimit graczy bliski &7(" + online + "/" + maxPlayers + ")");
        }

        if (predictions.isEmpty()) {
            predictions.add("&aPrognoza stabilna przy obecnym obciazeniu");
        }

        return predictions.stream().limit(4).toList();
    }

    /**
     * Szacuje ile chunkow laduje jeden gracz (view/simulation distance).
     */
    private static int estimateChunksPerPlayer() {
        int view = 10;
        int simulation = 10;
        try {
            view = Math.max(2, Bukkit.getViewDistance());
        } catch (Throwable ignored) {
        }
        try {
            simulation = Math.max(2, Bukkit.getSimulationDistance());
        } catch (Throwable ignored) {
        }
        int radius = Math.max(view, simulation);
        // Realnie laduje sie ~65% teoretycznego kwadratu (chunki wspoldzielone, elipsa)
        return (int) Math.round((2 * radius + 1) * (2 * radius + 1) * 0.65D);
    }

    /**
     * Koszt MSPT jednego dodatkowego gracza.
     * Przy malym online nie dzieli calego MSPT przez 1, tylko uzywa modelu.
     */
    private static double estimateMsptPerJoin(double mspt, int online, int entities, int chunks, int chunksPerJoin) {
        if (online >= MIN_PLAYERS_FOR_RATIO) {
            double measured = mspt / online;
            double entityCost = (entities / (double) online) * 0.002D;
            return Math.max(BASE_MSPT_PER_JOIN, measured * 0.55D + entityCost);
        }
        return BASE_MSPT_PER_JOIN + (chunksPerJoin * MSPT_PER_PLAYER_CHUNK);
    }

    /**
     * MSPT od chunkow force-load/spawn juz obecnych na serwerze (nie rosnie liniowo z graczami).
     */
    private static double estimateFixedChunkMspt(int online, int chunks, int chunksPerJoin) {
        int expected = online * chunksPerJoin + STATIC_CHUNK_OVERHEAD;
        if (chunks <= expected) {
            return 0.0D;
        }
        return (chunks - expected) * 0.001D;
    }

    private static String perPlayerSuffix(int online, int value) {
        if (online < MIN_PLAYERS_FOR_RATIO) {
            return "";
        }
        return ", ~" + format(value / (double) online) + "/gracz";
    }

    private void writeLog() {
        DiagnosticsSnapshot snapshot = latest.get();
        if (snapshot == null || snapshot.capturedAt() <= 0L) {
            return;
        }

        File logFile = new File(logFolder, "diag-" + LocalDate.now().format(LOG_DAY) + ".log");
        String sector = plugin.getSectorService() != null
            ? plugin.getSectorService().getCurrentSector()
            : Bukkit.getServer().getName();

        StringBuilder builder = new StringBuilder();
        builder.append('[').append(LocalDateTime.now().format(LOG_TIME)).append("] ")
            .append("SECTOR=").append(sector)
            .append(" TPS=").append(format(snapshot.tps()))
            .append(" MSPT=").append(format(snapshot.mspt()))
            .append(" CPU=").append(formatPercent(snapshot.processCpu()))
            .append(" RAM=").append(format(snapshot.ramUsedGb())).append("GB")
            .append(" PLAYERS=").append(snapshot.online()).append('/').append(snapshot.maxPlayers())
            .append(" PING=").append(format(snapshot.avgPingMs())).append("ms")
            .append(System.lineSeparator());

        builder.append("  LAG_SOURCES:");
        for (String source : snapshot.lagSources()) {
            builder.append(System.lineSeparator()).append("    - ").append(stripColors(source));
        }

        builder.append(System.lineSeparator()).append("  PREDICTIONS:");
        for (String prediction : snapshot.predictions()) {
            builder.append(System.lineSeparator()).append("    - ").append(stripColors(prediction));
        }

        builder.append(System.lineSeparator())
            .append("  WORLD=").append(snapshot.worldName())
            .append(" CHUNKS=").append(snapshot.loadedChunks())
            .append(" ENTITIES=").append(snapshot.entities())
            .append(" ITEMS=").append(snapshot.items())
            .append(" MOBS=").append(snapshot.monsters())
            .append(System.lineSeparator());

        appendLine(logFile, builder.toString());
    }

    private static void appendLine(File file, String content) {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, true))) {
            writer.write(content);
            writer.write(System.lineSeparator());
        } catch (IOException exception) {
            // ignore — logging must never crash the server
        }
    }

    private static String formatBorder(World world) {
        if (world == null) {
            return "-";
        }
        WorldBorder border = world.getWorldBorder();
        double half = border.getSize() / 2.0D;
        double minX = border.getCenter().getX() - half;
        double maxX = border.getCenter().getX() + half;
        double minZ = border.getCenter().getZ() - half;
        double maxZ = border.getCenter().getZ() + half;
        return format(minX) + ", " + format(maxX) + ", Z: " + format(minZ) + ", " + format(maxZ);
    }

    private static double averagePing() {
        int total = 0;
        int count = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            total += player.getPing();
            count++;
        }
        return count == 0 ? 0.0D : total / (double) count;
    }

    private static double[] safeTps() {
        try {
            return Bukkit.getTPS();
        } catch (Throwable ignored) {
            return new double[]{20.0D};
        }
    }

    private static double safeMspt() {
        try {
            return Bukkit.getAverageTickTime();
        } catch (Throwable ignored) {
            return 0.0D;
        }
    }

    private static double processCpuLoad() {
        try {
            java.lang.management.OperatingSystemMXBean bean = ManagementFactory.getOperatingSystemMXBean();
            if (bean instanceof OperatingSystemMXBean extended) {
                double load = extended.getProcessCpuLoad();
                return load >= 0.0D ? load : 0.0D;
            }
        } catch (Throwable ignored) {
        }
        return 0.0D;
    }

    private static double systemCpuLoad() {
        try {
            java.lang.management.OperatingSystemMXBean bean = ManagementFactory.getOperatingSystemMXBean();
            if (bean instanceof OperatingSystemMXBean extended) {
                double load = extended.getCpuLoad();
                return load >= 0.0D ? load : 0.0D;
            }
        } catch (Throwable ignored) {
        }
        return 0.0D;
    }

    private static String format(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private static String formatPercent(double ratio) {
        return String.format(Locale.US, "%.2f%%", ratio * 100.0D);
    }

    private static String stripColors(String input) {
        if (input == null) {
            return "";
        }
        return input.replaceAll("(?i)&#[0-9A-F]{6}", "")
            .replaceAll("&[0-9A-FK-OR]", "")
            .replaceAll("§#[0-9A-F]{6}", "")
            .replaceAll("§[0-9A-FK-OR]", "");
    }

    public record DiagnosticsSnapshot(
        double tps,
        double mspt,
        double processCpu,
        double systemCpu,
        double ramUsedGb,
        double avgPingMs,
        int online,
        int maxPlayers,
        String worldName,
        String borderText,
        String sectorType,
        int loadedChunks,
        int entities,
        int items,
        int monsters,
        int animals,
        int armorStands,
        String heaviestWorld,
        int heaviestEntities,
        List<String> lagSources,
        List<String> predictions,
        long usedMemory,
        long maxMemory,
        long capturedAt
    ) {
        public static DiagnosticsSnapshot empty() {
            return new DiagnosticsSnapshot(
                20.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D,
                0, 0, "world", "-", "NORMAL",
                0, 0, 0, 0, 0, 0,
                "world", 0,
                List.of("&aBrak danych"), List.of("&7Oczekiwanie na probe..."),
                0L, 0L, 0L
            );
        }

        public Map<String, String> placeholders(String sectorName, long lastUpdateMillis) {
            Map<String, String> map = new LinkedHashMap<>();
            map.put("{SECTORNAME}", sectorName);
            map.put("{sector}", sectorName);
            map.put("{TPS}", format(Math.min(20.0, tps)));
            map.put("{tps}", format(Math.min(20.0, tps)));
            map.put("{MSPT}", format(mspt));
            map.put("{mspt}", format(mspt));
            map.put("{CPU}", formatPercent(processCpu));
            map.put("{cpu}", formatPercent(processCpu));
            map.put("{RAM}", format(ramUsedGb) + "GB");
            map.put("{ram}", format(ramUsedGb) + "GB");
            map.put("{PING}", format(avgPingMs) + "ms");
            map.put("{ping}", format(avgPingMs) + "ms");
            map.put("{ONLINE}", online + "/" + maxPlayers);
            map.put("{online}", online + "/" + maxPlayers);
            map.put("{LAST_PING}", formatLastPing(lastUpdateMillis));
            map.put("{last_ping}", formatLastPing(lastUpdateMillis));
            map.put("{TYPE}", sectorType);
            map.put("{type}", sectorType);
            map.put("{WORLD}", worldName);
            map.put("{world}", worldName);
            map.put("{BORDER}", borderText);
            map.put("{border}", borderText);
            map.put("{ENTITIES}", String.valueOf(entities));
            map.put("{entities}", String.valueOf(entities));
            map.put("{CHUNKS}", String.valueOf(loadedChunks));
            map.put("{chunks}", String.valueOf(loadedChunks));
            map.put("{ITEMS}", String.valueOf(items));
            map.put("{items}", String.valueOf(items));
            map.put("{MOBS}", String.valueOf(monsters));
            map.put("{mobs}", String.valueOf(monsters));
            map.put("{HEAVY_WORLD}", heaviestWorld);
            map.put("{heavy_world}", heaviestWorld);
            map.put("{LAG_1}", lagSources.isEmpty() ? "&aBrak" : lagSources.get(0));
            map.put("{LAG_2}", lagSources.size() > 1 ? lagSources.get(1) : "");
            map.put("{LAG_3}", lagSources.size() > 2 ? lagSources.get(2) : "");
            map.put("{PRED_1}", predictions.isEmpty() ? "&aStabilnie" : predictions.get(0));
            map.put("{PRED_2}", predictions.size() > 1 ? predictions.get(1) : "");
            map.put("{PRED_3}", predictions.size() > 2 ? predictions.get(2) : "");
            return map;
        }

        private static String formatLastPing(long lastUpdateMillis) {
            if (lastUpdateMillis <= 0L) {
                return "brak danych";
            }
            long delta = Math.max(0L, System.currentTimeMillis() - lastUpdateMillis);
            if (delta < 1000L) {
                return "<1s temu";
            }
            if (delta < 60_000L) {
                return (delta / 1000L) + "s temu";
            }
            return (delta / 60_000L) + "m temu";
        }
    }
}
