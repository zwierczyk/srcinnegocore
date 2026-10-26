package dev.grzybalson.config.modules.bossbar;

import eu.okaeri.configs.OkaeriConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BossBarConfig extends OkaeriConfig {

    private boolean enabled = true;
    private int updateIntervalTicks = 20;
    private ScheduledDropSettings scheduledDrop = new ScheduledDropSettings();
    private List<BossBarEntry> bars = new ArrayList<>(Arrays.asList(
        new BossBarEntry(
            "&8Ξ &fᴅɪꜱᴄᴏʀᴅ &8• &x&0&0&B&7&F&Fᴅ&x&1&6&B&D&F&Fᴄ&x&2&C&C&3&F&F.&x&4&3&C&A&F&Fᴏ&x&5&9&D&0&F&Fɢ&x&6&F&D&6&F&Fʜ&x&5&9&D&0&F&Fᴜ&x&4&3&C&A&F&Fʙ&x&2&C&C&3&F&F.&x&1&6&B&D&F&Fᴘ&x&0&0&B&7&F&Fʟ",
            "BLUE",
            "SOLID",
            1.0
        ),
        new BossBarEntry(
            "&8Ξ &x&0&0&F&F&3&Bᴡ&x&1&9&F&F&4&Eᴡ&x&3&1&F&E&6&1ᴡ&x&4&A&F&E&7&3.&x&6&2&F&D&8&6ᴏ&x&7&B&F&D&9&9ɢ&x&6&7&F&D&8&9ʜ&x&5&2&F&E&7&Aᴜ&x&3&E&F&E&6&Aʙ&x&2&9&F&E&5&A.&x&1&5&F&F&4&Bᴘ&x&0&0&F&F&3&Bʟ",
            "GREEN",
            "SOLID",
            1.0
        ),
        new BossBarEntry(
            "&8• &fꜱᴛʀᴏɴᴀ",
            "WHITE",
            "SOLID",
            1.0
        ),
        new BossBarEntry(
            "{DROP_LINE}",
            "YELLOW",
            "SEGMENTED_10",
            1.0
        )
    ));

    public boolean isEnabled() { return enabled; }
    public int getUpdateIntervalTicks() { return updateIntervalTicks; }
    public ScheduledDropSettings getScheduledDrop() { return scheduledDrop; }
    public List<BossBarEntry> getBars() { return bars; }

    public boolean isScheduledDropBossbarEnabled() {
        return scheduledDrop != null && scheduledDrop.isBossbarEnabled();
    }

    public static class ScheduledDropSettings extends OkaeriConfig {
        private boolean enabled = true;
        private boolean bossbarEnabled = false;
        private String timezone = "Europe/Warsaw";
        private String eventStart = "12:00";
        private String eventEnd = "23:00";
        private double safeZoneMultiplier = 0.5D;
        private double mapMultiplier = 2.0D;
        private List<String> eventWorlds = new ArrayList<>(List.of("pvp"));
        private List<String> safeGeneratorPrefixes = new ArrayList<>(List.of("bezpieczna"));
        private String bossbarEvent = "&6⚡ &eDrop: &cStrefa {DROP_SAFE} &8| &aMapa {DROP_MAP} &8| &7jeszcze &f{DROP_LEFT}";
        private String bossbarNormal = "&7⚡ &fDrop normalny &8| &eEvent od &f{DROP_UNTIL} &8(&f{DROP_LEFT}&8)";

        public boolean isEnabled() { return enabled; }
        public boolean isBossbarEnabled() { return bossbarEnabled; }
        public String getTimezone() { return timezone; }
        public String getEventStart() { return eventStart; }
        public String getEventEnd() { return eventEnd; }
        public double getSafeZoneMultiplier() { return safeZoneMultiplier; }
        public double getMapMultiplier() { return mapMultiplier; }
        public List<String> getEventWorlds() { return eventWorlds; }
        public List<String> getSafeGeneratorPrefixes() { return safeGeneratorPrefixes; }
        public String getBossbarEvent() { return bossbarEvent; }
        public String getBossbarNormal() { return bossbarNormal; }
    }

    public static class BossBarEntry extends OkaeriConfig {
        private String text = "&7Domyślny tekst";
        private String color = "WHITE";
        private String style = "SOLID";
        private double progress = 1.0;

        public BossBarEntry() {}

        public BossBarEntry(String text, String color, String style, double progress) {
            this.text = text;
            this.color = color;
            this.style = style;
            this.progress = progress;
        }

        public String getText() { return text; }
        public String getColor() { return color; }
        public String getStyle() { return style; }
        public double getProgress() { return progress; }
    }
}
