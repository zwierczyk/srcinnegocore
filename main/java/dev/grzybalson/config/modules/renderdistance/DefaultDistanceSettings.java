package dev.grzybalson.config.modules.renderdistance;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.NameModifier;
import eu.okaeri.configs.annotation.NameStrategy;
import eu.okaeri.configs.annotation.Names;

@Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
public class DefaultDistanceSettings extends OkaeriConfig {
    private int renderDistance = -1;

    public int getRenderDistance() {
        return renderDistance;
    }
}
