package dev.grzybalson.modules.szaman;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

public class PerkPlayerData {

    private final UUID uuid;
    private final EnumMap<PerkType, Integer> levels;
    private double turboDropBonus;
    private double turboRankingBonus;

    public PerkPlayerData(UUID uuid) {
        this.uuid = uuid;
        this.levels = new EnumMap<>(PerkType.class);
    }

    public UUID getUuid() {
        return uuid;
    }

    public int getLevel(PerkType type) {
        return levels.getOrDefault(type, 0);
    }

    public void setLevel(PerkType type, int level) {
        if (type == null) {
            return;
        }

        if (level <= 0) {
            levels.remove(type);
            return;
        }

        levels.put(type, level);
    }

    public Map<PerkType, Integer> getLevels() {
        return Map.copyOf(levels);
    }

    public double getTurboDropBonus() {
        return turboDropBonus;
    }

    public void setTurboDropBonus(double turboDropBonus) {
        this.turboDropBonus = Math.max(0.0D, turboDropBonus);
    }

    public double getTurboRankingBonus() {
        return turboRankingBonus;
    }

    public void setTurboRankingBonus(double turboRankingBonus) {
        this.turboRankingBonus = Math.max(0.0D, turboRankingBonus);
    }
}
