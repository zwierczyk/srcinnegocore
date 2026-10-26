package dev.grzybalson.modules.mission;

public record MissionRewardTier(
    String id,
    String display,
    String permission,
    int shards,
    boolean defaultTier
) {
}
