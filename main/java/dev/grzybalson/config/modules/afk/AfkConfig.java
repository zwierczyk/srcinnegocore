package dev.grzybalson.config.modules.afk;

import eu.okaeri.configs.OkaeriConfig;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AfkConfig extends OkaeriConfig {

    private Map<String, AfkArea> areas = new HashMap<>() {{
        put("spawn", new AfkArea());
        put("vip", new AfkArea("world", -100, 50, -100, 100, 100, 100, true, 300, 150.0, 2.0, "&8[&a$&8] &aOtrzymałeś &e{amount}$ &aza AFK w strefie VIP!", Arrays.asList()));
    }};

    public Map<String, AfkArea> getAreas() { return areas; }

    public static class AfkArea extends OkaeriConfig {
        private String world = "world";
        private int minX = 0;
        private int minY = 0;
        private int minZ = 0;
        private int maxX = 100;
        private int maxY = 256;
        private int maxZ = 100;
        private boolean enabled = true;
        private int rewardIntervalSeconds = 300;
        private double rewardAmount = 100.0;
        private double multiplier = 1.0;
        private String rewardMessage = "&8[&a$&8] &aOtrzymałeś &e{amount}$ &aza AFK!";
        private List<String> rewardCommands = Arrays.asList();

        public AfkArea() {}

        public AfkArea(String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                       boolean enabled, int rewardIntervalSeconds, double rewardAmount, double multiplier,
                       String rewardMessage, List<String> rewardCommands) {
            this.world = world;
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
            this.enabled = enabled;
            this.rewardIntervalSeconds = rewardIntervalSeconds;
            this.rewardAmount = rewardAmount;
            this.multiplier = multiplier;
            this.rewardMessage = rewardMessage;
            this.rewardCommands = rewardCommands;
        }

        public String getWorld() { return world; }
        public int getMinX() { return minX; }
        public int getMinY() { return minY; }
        public int getMinZ() { return minZ; }
        public int getMaxX() { return maxX; }
        public int getMaxY() { return maxY; }
        public int getMaxZ() { return maxZ; }
        public boolean isEnabled() { return enabled; }
        public int getRewardIntervalSeconds() { return rewardIntervalSeconds; }
        public double getRewardAmount() { return rewardAmount; }
        public double getMultiplier() { return multiplier; }
        public String getRewardMessage() { return rewardMessage; }
        public List<String> getRewardCommands() { return rewardCommands; }
    }
}
