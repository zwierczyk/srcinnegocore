package dev.grzybalson.config.configs;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.NameModifier;
import eu.okaeri.configs.annotation.NameStrategy;
import eu.okaeri.configs.annotation.Names;

import java.util.Arrays;
import java.util.List;

@Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
public class ClearLagConfig extends OkaeriConfig {

    private boolean enabled = true;
    private int interval = 300;
    private int countdownStart = 5;

    private String countdownMessage = "";

    private String clearMessage = "";
    private List<String> worlds = Arrays.asList();
    private List<String> excludedItems = Arrays.asList("GLASS_BOTTLE");
    private double playerProtectionRadius = 5.0D;
    private double creativeProtectionRadius = 16.0D;
    private int minItemAgeTicks = 100;

    public boolean isEnabled() { return enabled; }
    public int getInterval() { return interval; }
    public int getCountdownStart() { return countdownStart; }
    public String getCountdownMessage() { return countdownMessage; }
    public String getClearMessage() { return clearMessage; }
    public List<String> getWorlds() { return worlds; }
    public List<String> getExcludedItems() { return excludedItems; }
    public double getPlayerProtectionRadius() { return playerProtectionRadius; }
    public double getCreativeProtectionRadius() { return creativeProtectionRadius; }
    public int getMinItemAgeTicks() { return minItemAgeTicks; }
}
