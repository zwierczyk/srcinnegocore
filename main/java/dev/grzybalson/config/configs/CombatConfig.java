package dev.grzybalson.config.configs;

import eu.okaeri.configs.OkaeriConfig;
import java.util.Arrays;
import java.util.List;

public class CombatConfig extends OkaeriConfig {

    private int combatTime = 30;

    private String notificationType = "BOSSBAR";

    private String combatMessage = "&cNie logaj! &7Jesteś podzcas walki &8- &f{time}s";

    private String combatLogoutMessage = "&#FF0000✘ &8Ξ &cGracz &4{player} &cwylogował się podczas walki!";

    private String combatEndMessage = "&#00FF3B✔ &8Ξ &#4EFA76Nie jesteś już w walce!";

    private String regionBlockedMessage = "&#FF0000✘ &8Ξ &cNie możesz wejść na ten region podczas walki!";
    private List<String> safeRegions = Arrays.asList("spawn", "safe_zone", "pvp_off");

    private double knockbackStrength = 5.0;

    private String bossBarColor = "RED";

    private String bossBarStyle = "SOLID";

    private boolean blockCommandsInCombat = true;
    private List<String> allowedCommandsInCombat = Arrays.asList(
        "msg", "r", "reply", "klan", "clan", "helpop", "szybkiezgloszenie", "fastreport"
    );
    private String commandBlockedMessage = "&#FF0000✘ &8Ξ &cNie możesz używać tej komendy podczas walki!";

    private boolean blockEnderchestInCombat = true;
    private String enderchestBlockedMessage = "&#FF0000✘ &8Ξ &cNie możesz otworzyć enderchesta podczas walki!";

    private boolean combatActionBarEnabled = true;
    private String combatActionBarChannel = "combat";
    private long combatActionBarDurationTicks = 60L;
    private boolean combatActionBarShowToVictim = false;
    private String combatActionBarTemplate = "&f{nick} &#FD7A7A❤{hearts}{golden}";

    private boolean killSoundEnabled = false;
    private String killSound = "none";
    private float killSoundVolume = 0.85F;
    private float killSoundPitch = 1.35F;

    public int getCombatTime() {
        return combatTime;
    }

    public String getNotificationType() {
        return notificationType;
    }

    public String getCombatMessage() {
        return combatMessage;
    }

    public String getCombatLogoutMessage() {
        return combatLogoutMessage;
    }

    public String getCombatEndMessage() {
        return combatEndMessage;
    }

    public String getRegionBlockedMessage() {
        return regionBlockedMessage;
    }

    public List<String> getSafeRegions() {
        return safeRegions;
    }

    public double getKnockbackStrength() {
        return knockbackStrength;
    }

    public String getBossBarColor() {
        return bossBarColor;
    }

    public String getBossBarStyle() {
        return bossBarStyle;
    }

    public boolean isBlockCommandsInCombat() {
        return blockCommandsInCombat;
    }

    public List<String> getAllowedCommandsInCombat() {
        return allowedCommandsInCombat;
    }

    public boolean isBlockEnderchestInCombat() {
        return blockEnderchestInCombat;
    }

    public String getEnderchestBlockedMessage() {
        return enderchestBlockedMessage;
    }

    public String getCommandBlockedMessage() {
        return commandBlockedMessage;
    }

    public boolean isCombatActionBarEnabled() {
        return combatActionBarEnabled;
    }

    public String getCombatActionBarChannel() {
        return combatActionBarChannel == null || combatActionBarChannel.isBlank() ? "combat" : combatActionBarChannel;
    }

    public long getCombatActionBarDurationTicks() {
        return Math.max(20L, combatActionBarDurationTicks);
    }

    public boolean isCombatActionBarShowToVictim() {
        return combatActionBarShowToVictim;
    }

    public String getCombatActionBarTemplate() {
        return combatActionBarTemplate == null || combatActionBarTemplate.isBlank()
            ? "&f{nick} &#FD7A7A❤{hearts}{golden}"
            : combatActionBarTemplate;
    }

    public boolean isKillSoundEnabled() {
        return killSoundEnabled;
    }

    public org.bukkit.Sound getKillSound() {
        if (!killSoundEnabled || isBlockedKillSound(killSound)) {
            return null;
        }
        if (killSound == null || killSound.isBlank() || killSound.equalsIgnoreCase("none")) {
            return null;
        }
        try {
            return org.bukkit.Sound.valueOf(killSound.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public void sanitizeKillSoundSettings() {
        if (isBlockedKillSound(killSound)) {
            killSoundEnabled = false;
            killSound = "none";
        }
    }

    private static boolean isBlockedKillSound(String sound) {
        if (sound == null || sound.isBlank() || "none".equalsIgnoreCase(sound)) {
            return false;
        }
        String normalized = sound.trim().toUpperCase(java.util.Locale.ROOT).replace('.', '_');
        return normalized.contains("UI_TOAST")
            || normalized.contains("TOAST_CHALLENGE")
            || normalized.contains("LEVEL_UP")
            || normalized.contains("LEVELUP");
    }

    public float getKillSoundVolume() {
        return killSoundVolume;
    }

    public float getKillSoundPitch() {
        return killSoundPitch;
    }
}
