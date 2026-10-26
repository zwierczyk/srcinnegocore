package dev.grzybalson.modules.sectors;

import java.util.ArrayList;
import java.util.List;

public final class SectorInfo {

    private final String name;
    private final double tps;
    private final double load;
    private final int online;
    private final int maxPlayers;
    private final long lastUpdate;
    private final double mspt;
    private final double cpuPercent;
    private final double ramUsedGb;
    private final double avgPingMs;
    private final String sectorType;
    private final String worldName;
    private final String borderText;
    private final int entities;
    private final int chunks;
    private final int items;
    private final int monsters;
    private final List<String> lagSources;
    private final List<String> predictions;

    public SectorInfo(String name, double tps, double load, int online, int maxPlayers, long lastUpdate) {
        this(name, tps, load, online, maxPlayers, lastUpdate, 0.0D, 0.0D, 0.0D, 0.0D,
            "NORMAL", "world", "-", 0, 0, 0, 0, List.of(), List.of());
    }

    public SectorInfo(String name, double tps, double load, int online, int maxPlayers, long lastUpdate,
                      double mspt, double cpuPercent, double ramUsedGb, double avgPingMs,
                      String sectorType, String worldName, String borderText,
                      int entities, int chunks, int items, int monsters,
                      List<String> lagSources, List<String> predictions) {
        this.name = name;
        this.tps = tps;
        this.load = load;
        this.online = online;
        this.maxPlayers = maxPlayers;
        this.lastUpdate = lastUpdate;
        this.mspt = mspt;
        this.cpuPercent = cpuPercent;
        this.ramUsedGb = ramUsedGb;
        this.avgPingMs = avgPingMs;
        this.sectorType = sectorType == null || sectorType.isBlank() ? "NORMAL" : sectorType;
        this.worldName = worldName == null || worldName.isBlank() ? "world" : worldName;
        this.borderText = borderText == null || borderText.isBlank() ? "-" : borderText;
        this.entities = Math.max(0, entities);
        this.chunks = Math.max(0, chunks);
        this.items = Math.max(0, items);
        this.monsters = Math.max(0, monsters);
        this.lagSources = lagSources == null ? List.of() : List.copyOf(lagSources);
        this.predictions = predictions == null ? List.of() : List.copyOf(predictions);
    }

    public String getName() {
        return name;
    }

    public double getTps() {
        return tps;
    }

    public double getLoad() {
        return load;
    }

    public int getOnline() {
        return online;
    }

    public SectorInfo withOnline(int newOnline) {
        if (newOnline == this.online) {
            return this;
        }
        return new SectorInfo(
            name, tps, load, Math.max(0, newOnline), maxPlayers, lastUpdate,
            mspt, cpuPercent, ramUsedGb, avgPingMs,
            sectorType, worldName, borderText,
            entities, chunks, items, monsters,
            lagSources, predictions
        );
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public long getLastUpdate() {
        return lastUpdate;
    }

    public double getMspt() {
        return mspt;
    }

    public double getCpuPercent() {
        return cpuPercent;
    }

    public double getRamUsedGb() {
        return ramUsedGb;
    }

    public double getAvgPingMs() {
        return avgPingMs;
    }

    public String getSectorType() {
        return sectorType;
    }

    public String getWorldName() {
        return worldName;
    }

    public String getBorderText() {
        return borderText;
    }

    public int getEntities() {
        return entities;
    }

    public int getChunks() {
        return chunks;
    }

    public int getItems() {
        return items;
    }

    public int getMonsters() {
        return monsters;
    }

    public List<String> getLagSources() {
        return lagSources;
    }

    public List<String> getPredictions() {
        return predictions;
    }

    public boolean isAlive() {
        return lastUpdate > 0L && System.currentTimeMillis() - lastUpdate <= 3000L;
    }

    public static List<String> safeStringList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object value : list) {
            if (value != null) {
                result.add(String.valueOf(value));
            }
        }
        return result;
    }
}
