package dev.grzybalson.config.configs;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.NameModifier;
import eu.okaeri.configs.annotation.NameStrategy;
import eu.okaeri.configs.annotation.Names;

@Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
public class HeartsConfig extends OkaeriConfig {

    private boolean enabled = true;
    private double maxHealth = 60.0;
    private boolean resetToVanillaWhenDisabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public double getMaxHealth() {
        return maxHealth;
    }

    public boolean isResetToVanillaWhenDisabled() {
        return resetToVanillaWhenDisabled;
    }
}
