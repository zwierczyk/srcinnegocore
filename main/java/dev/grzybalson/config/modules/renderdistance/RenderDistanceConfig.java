package dev.grzybalson.config.modules.renderdistance;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.NameModifier;
import eu.okaeri.configs.annotation.NameStrategy;
import eu.okaeri.configs.annotation.Names;

import java.util.LinkedHashMap;
import java.util.Map;

@Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
public class RenderDistanceConfig extends OkaeriConfig {
    private boolean enabled = true;
    private int checkIntervalTicks = 10;
    private boolean checkOnlyOnBlockChange = true;
    private boolean debug = false;
    private DefaultDistanceSettings defaults = new DefaultDistanceSettings();
    private Map<String, RegionDistanceSettings> regions = new LinkedHashMap<>();
    private RenderDistanceMessages messages = new RenderDistanceMessages();

    public boolean isEnabled() {
        return enabled;
    }

    public int getCheckIntervalTicks() {
        return checkIntervalTicks;
    }

    public boolean isCheckOnlyOnBlockChange() {
        return checkOnlyOnBlockChange;
    }

    public boolean isDebug() {
        return debug;
    }

    public DefaultDistanceSettings getDefaults() {
        return defaults;
    }

    public Map<String, RegionDistanceSettings> getRegions() {
        return regions;
    }

    public RenderDistanceMessages getMessages() {
        return messages;
    }
}
