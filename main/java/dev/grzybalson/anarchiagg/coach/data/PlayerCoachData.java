package dev.grzybalson.anarchiagg.coach.data;

import dev.grzybalson.anarchiagg.coach.CoachSkill;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

public class PlayerCoachData {

    private static final CoachSkill[] SKILLS = CoachSkill.values();

    private final UUID uuid;
    private final Map<CoachSkill, Integer> levels = new EnumMap<>(CoachSkill.class);
    private final Map<CoachSkill, Integer> kills = new EnumMap<>(CoachSkill.class);
    private final int[] levelValues = new int[SKILLS.length];
    private final int[] killValues = new int[SKILLS.length];
    private boolean loadedFromDatabase;
    private boolean loadFailed;

    public PlayerCoachData(UUID uuid) {
        this.uuid = uuid;
    }

    public PlayerCoachData(PlayerCoachData source) {
        this.uuid = source.uuid;
        this.loadedFromDatabase = source.loadedFromDatabase;
        this.loadFailed = source.loadFailed;
        for (CoachSkill skill : SKILLS) {
            this.levels.put(skill, source.getLevel(skill));
            this.kills.put(skill, source.getKills(skill));
            this.levelValues[skill.ordinal()] = source.getLevel(skill);
            this.killValues[skill.ordinal()] = source.getKills(skill);
        }
    }

    public PlayerCoachData copy() {
        return new PlayerCoachData(this);
    }

    public UUID getUuid() {
        return uuid;
    }

    public int getLevel(CoachSkill skill) {
        return skill == null ? 0 : levelValues[skill.ordinal()];
    }

    public void setLevel(CoachSkill skill, int level) {
        if (skill == null) return;
        int sanitized = Math.max(0, level);
        levelValues[skill.ordinal()] = sanitized;
        levels.put(skill, sanitized);
    }

    public Map<CoachSkill, Integer> getLevels() {
        return levels;
    }

    public int getKills(CoachSkill skill) {
        return skill == null ? 0 : killValues[skill.ordinal()];
    }

    public void setKills(CoachSkill skill, int amount) {
        if (skill == null) return;
        int sanitized = Math.max(0, amount);
        killValues[skill.ordinal()] = sanitized;
        kills.put(skill, sanitized);
    }

    public void addKill(CoachSkill skill) {
        setKills(skill, getKills(skill) + 1);
    }

    public Map<CoachSkill, Integer> getKills() {
        return kills;
    }

    public int getStrengthKills() {
        return getKills(CoachSkill.STRENGTH);
    }

    public void setStrengthKills(int strengthKills) {
        setKills(CoachSkill.STRENGTH, strengthKills);
    }

    public void addStrengthKill() {
        addKill(CoachSkill.STRENGTH);
    }

    public void markLoadedFromDatabase() {
        this.loadedFromDatabase = true;
        this.loadFailed = false;
    }

    public void markLoadFailed() {
        this.loadFailed = true;
        this.loadedFromDatabase = false;
    }

    public boolean isLoadedFromDatabase() {
        return loadedFromDatabase;
    }

    public boolean isLoadFailed() {
        return loadFailed;
    }

    public boolean canPersist() {
        return loadedFromDatabase && !loadFailed;
    }
}
