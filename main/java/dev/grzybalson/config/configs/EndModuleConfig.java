package dev.grzybalson.config.configs;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.NameModifier;
import eu.okaeri.configs.annotation.NameStrategy;
import eu.okaeri.configs.annotation.Names;

import java.util.Arrays;
import java.util.List;

@Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
public class EndModuleConfig extends OkaeriConfig {

    private boolean enabled = true;
    private List<String> worlds = Arrays.asList("world_the_end");
    private List<String> allowedBlocks = Arrays.asList("OBSIDIAN", "COBWEB");
    private boolean restrictBlockPlaceAndBreak = true;
    private int temporaryBlockLifetimeSeconds = 20;
    private boolean removePlacedBlocksOnShutdown = true;
    private boolean crystalBlockDamage = false;
    private boolean crystalFire = false;
    private boolean opsBypassRestrictions = true;
    private boolean blockEnderPearlsOutsideWorldborder = true;
    private double worldborderPushDistance = 3.0D;
    private double worldborderPushStrength = 0.75D;
    private int worldborderPushPeriodTicks = 5;
    private String worldborderPearlDeniedMessage = "&#FF0000✘ &8Ξ &cNie mozesz rzucac perel za worldborder.";
    private String placeDeniedMessage = "&#FF0000✘ &8Ξ &cW tym świecie możesz stawiać tylko obsydian, pajęczyny i end crystale!";
    private String breakDeniedMessage = "&#FF0000✘ &8Ξ &cW tym świecie możesz rozwalać tylko obsydian, pajęczyny i end crystale!";

    public boolean isEnabled() {
        return enabled;
    }

    public List<String> getWorlds() {
        return worlds;
    }

    public List<String> getAllowedBlocks() {
        return allowedBlocks;
    }

    public boolean isRestrictBlockPlaceAndBreak() {
        return restrictBlockPlaceAndBreak;
    }

    public int getTemporaryBlockLifetimeSeconds() {
        return temporaryBlockLifetimeSeconds;
    }

    public boolean isRemovePlacedBlocksOnShutdown() {
        return removePlacedBlocksOnShutdown;
    }

    public boolean isCrystalBlockDamage() {
        return crystalBlockDamage;
    }

    public boolean isCrystalFire() {
        return crystalFire;
    }

    public boolean isOpsBypassRestrictions() {
        return opsBypassRestrictions;
    }

    public boolean isBlockEnderPearlsOutsideWorldborder() {
        return blockEnderPearlsOutsideWorldborder;
    }

    public double getWorldborderPushDistance() {
        return worldborderPushDistance;
    }

    public double getWorldborderPushStrength() {
        return worldborderPushStrength;
    }

    public int getWorldborderPushPeriodTicks() {
        return worldborderPushPeriodTicks;
    }

    public String getWorldborderPearlDeniedMessage() {
        return worldborderPearlDeniedMessage;
    }

    public String getPlaceDeniedMessage() {
        return placeDeniedMessage;
    }

    public String getBreakDeniedMessage() {
        return breakDeniedMessage;
    }
}
