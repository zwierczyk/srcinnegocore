package dev.grzybalson.config.modules.renderdistance;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.NameModifier;
import eu.okaeri.configs.annotation.NameStrategy;
import eu.okaeri.configs.annotation.Names;

@Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
public class RegionDistanceSettings extends OkaeriConfig {
    private String world;
    private String region;
    private int priority = 0;
    private Integer renderDistance;

    public RegionDistanceSettings() {
    }

    public RegionDistanceSettings(String world, String region, int priority, int renderDistance) {
        this.world = world;
        this.region = region;
        this.priority = priority;
        this.renderDistance = renderDistance;
    }

    public String getWorld() {
        return world;
    }

    public String getRegion() {
        return region;
    }

    public int getPriority() {
        return priority;
    }

    public Integer getRenderDistance() {
        return renderDistance;
    }
}
