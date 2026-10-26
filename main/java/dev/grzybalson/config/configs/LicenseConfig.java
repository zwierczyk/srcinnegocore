package dev.grzybalson.config.configs;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.NameModifier;
import eu.okaeri.configs.annotation.NameStrategy;
import eu.okaeri.configs.annotation.Names;

@Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
public class LicenseConfig extends OkaeriConfig {

    private String licenseKey = "XXXXX-XXXXX-XXXXX-XXXXX-XXXXX";

    public String getLicenseKey() {
        return licenseKey;
    }
}
