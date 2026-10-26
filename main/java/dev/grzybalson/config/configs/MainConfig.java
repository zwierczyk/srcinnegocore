package dev.grzybalson.config.configs;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.NameModifier;
import eu.okaeri.configs.annotation.NameStrategy;
import eu.okaeri.configs.annotation.Names;

import java.util.Arrays;
import java.util.List;

@Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
public class MainConfig extends OkaeriConfig {

    private String prefix = "&8[&6HollyCore&8] ";

    private boolean debug = false;
    private int defaultCooldown = 3;

    private NoDamageSettings noDamage = new NoDamageSettings();

    public String getPrefix() {
        return prefix;
    }

    public boolean isDebug() {
        return debug;
    }

    public int getDefaultCooldown() {
        return defaultCooldown;
    }

    public NoDamageSettings getNoDamage() {
        return noDamage;
    }

    @Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
    public static class NoDamageSettings extends OkaeriConfig {

        private boolean enabled = true;

        private List<String> causes = Arrays.asList(
            "FALL",
            "FLY_INTO_WALL",
            "SUFFOCATION",
            "CRAMMING"
        );

        private boolean noFallingBlocks = true;

        public boolean isEnabled() {
            return enabled;
        }

        public List<String> getCauses() {
            return causes;
        }

        public boolean isNoFallingBlocks() {
            return noFallingBlocks;
        }
    }
}
