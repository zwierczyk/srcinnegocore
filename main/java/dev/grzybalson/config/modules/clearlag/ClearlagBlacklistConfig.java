package dev.grzybalson.config.modules.clearlag;

import eu.okaeri.configs.OkaeriConfig;
import java.util.Arrays;
import java.util.List;

public class ClearlagBlacklistConfig extends OkaeriConfig {
    private List<String> blacklistedItems = Arrays.asList(
        "DIAMOND",
        "DIAMOND_BLOCK",
        "NETHERITE_INGOT",
        "NETHERITE_BLOCK",
        "BEACON",
        "NETHER_STAR",
        "ELYTRA",
        "TOTEM_OF_UNDYING"
    );

    private boolean protectNamedItems = true;

    private boolean protectEnchantedItems = true;

    public List<String> getBlacklistedItems() { return blacklistedItems; }
    public boolean isProtectNamedItems() { return protectNamedItems; }
    public boolean isProtectEnchantedItems() { return protectEnchantedItems; }
}
