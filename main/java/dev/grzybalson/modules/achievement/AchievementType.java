package dev.grzybalson.modules.achievement;

public enum AchievementType {
    KILLS("Zabójstwa"),
    DEATHS("Śmierci"),
    BLOCKS_MINED("Wykopane bloki"),
    PLAYTIME("Spędzony czas"),
    CRATES("Skrzynki"),
    POINTS("Ranking"),
    EARNED_POINTS("Zdobyte punkty");

    private final String displayName;

    AchievementType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String configKey() {
        return name().toLowerCase().replace("_", "-");
    }
}
