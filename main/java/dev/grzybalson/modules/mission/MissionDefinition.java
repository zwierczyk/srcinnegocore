package dev.grzybalson.modules.mission;

import org.bukkit.Material;

import java.util.List;

public record MissionDefinition(
    String id,
    boolean enabled,
    int slot,
    Material material,
    String name,
    String progressLore,
    String rewardsHeader,
    String rewardLine,
    int goal,
    String claimableLore,
    List<MissionRewardTier> rewardTiers
) {
}
