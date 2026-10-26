package dev.grzybalson.config.configs;

import eu.okaeri.configs.OkaeriConfig;
public class RestartConfig extends OkaeriConfig {

    private String restartBossBarTitle = "&c&lRESTART &8» &f{time}";

    private String bossBarColor = "RED";

    private String bossBarStyle = "SEGMENTED_10";

    private String restartStartMessage = "&#FFB800⚠ &8Ξ &eRozpoczęto odliczanie do restartu: &c{time} sekund";
    private String restartEndMessage = "&#FFB800⚠ &8Ξ &eSerwer zostanie zrestartowany!";

    private String antylogoutDisabledMessage = "&#00E0FFℹ &8Ξ &aAntylogout został wyłączony na czas restartu.";

    private String countdownSound = "BLOCK_NOTE_BLOCK_PLING";
    private float countdownSoundVolume = 1.0f;
    private float countdownSoundPitch = 1.0f;
    private java.util.List<Integer> countdownSoundTimes = java.util.List.of(60, 30, 10, 5, 4, 3, 2, 1);

    private String finalSound = "ENTITY_ENDER_DRAGON_GROWL";
    private float finalSoundVolume = 1.0f;
    private float finalSoundPitch = 1.0f;

    public String getRestartBossBarTitle() {
        return restartBossBarTitle;
    }

    public String getBossBarColor() {
        return bossBarColor;
    }

    public String getBossBarStyle() {
        return bossBarStyle;
    }

    public String getRestartStartMessage() {
        return restartStartMessage;
    }

    public String getRestartEndMessage() {
        return restartEndMessage;
    }

    public String getAntylogoutDisabledMessage() {
        return antylogoutDisabledMessage;
    }

    public String getCountdownSound() { return countdownSound; }
    public float getCountdownSoundVolume() { return countdownSoundVolume; }
    public float getCountdownSoundPitch() { return countdownSoundPitch; }
    public java.util.List<Integer> getCountdownSoundTimes() { return countdownSoundTimes; }

    public String getFinalSound() { return finalSound; }
    public float getFinalSoundVolume() { return finalSoundVolume; }
    public float getFinalSoundPitch() { return finalSoundPitch; }
}
