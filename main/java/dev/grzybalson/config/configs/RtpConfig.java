package dev.grzybalson.config.configs;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.NameModifier;
import eu.okaeri.configs.annotation.NameStrategy;
import eu.okaeri.configs.annotation.Names;

@Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
public class RtpConfig extends OkaeriConfig {

    private boolean enabled = true;
    private String worldName = "world";
    private int minX = -5000;
    private int maxX = 5000;
    private int minZ = -5000;
    private int maxZ = 5000;
    private int cooldownSeconds = 60;
    private int maxAttempts = 10;

    private String teleportingMessage = "&#00E0FFℹ &8Ξ &fSzukanie bezpiecznej lokalizacji...";
    private String teleportedMessage = "&#00FF3B✔ &8Ξ &#4EFA76Przeteleportowano na losowe koordynaty!";
    private String cooldownMessage = "&#FF0000✘ &8Ξ &cMusisz odczekać &4{time} &czanim użyjesz ponownie!";
    private String noSafeLocationMessage = "&#FF0000✘ &8Ξ &cNie znaleziono bezpiecznej lokalizacji, spróbuj ponownie!";
    private String worldNotFoundMessage = "&#FF0000✘ &8Ξ &cŚwiat nie został znaleziony!";

    public boolean isEnabled() { return enabled; }
    public String getWorldName() { return worldName; }
    public int getMinX() { return minX; }
    public int getMaxX() { return maxX; }
    public int getMinZ() { return minZ; }
    public int getMaxZ() { return maxZ; }
    public int getCooldownSeconds() { return cooldownSeconds; }
    public int getMaxAttempts() { return maxAttempts; }
    public String getTeleportingMessage() { return teleportingMessage; }
    public String getTeleportedMessage() { return teleportedMessage; }
    public String getCooldownMessage() { return cooldownMessage; }
    public String getNoSafeLocationMessage() { return noSafeLocationMessage; }
    public String getWorldNotFoundMessage() { return worldNotFoundMessage; }
}
