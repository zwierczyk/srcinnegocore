package dev.grzybalson.config.configs;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.NameModifier;
import eu.okaeri.configs.annotation.NameStrategy;
import eu.okaeri.configs.annotation.Names;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
public class AfkConfig extends OkaeriConfig {
    private Map<String, AfkZone> zones = new LinkedHashMap<>();

    private List<AfkReward> rewards = new ArrayList<>(List.of(
        new AfkReward("Holly.afk.basic", 10.0, 0, 60, "&eNagroda Podstawowa &7(10$)", "YELLOW", List.of()),
        new AfkReward("Holly.afk.vip", 25.0, 0, 45, "&6Nagroda VIP &7(25$)", "GOLD", List.of()),
        new AfkReward("Holly.afk.svip", 50.0, 0, 30, "&cNagroda SVIP &7(50$)", "RED", List.of())
    ));

    private String bossbarFormat = "&e{reward_name} &7otrzymasz za: &f{time}";
    private String turboBossbarTitle = "&aTurboAFK &2x{MULTIPLIER} &7pozostało: &f{TIME}";
    private String turboBossbarColor = "GREEN";
    private List<String> placeholdersHelp = new ArrayList<>(List.of(
        "{player} / {PLAYER} / %player% - nick gracza odbierającego nagrodę",
        "{uuid} / {UUID} / %uuid% - UUID gracza",
        "Przykład komendy: lp user {player} parent addtemp svip 3d"
    ));

    public Map<String, AfkZone> getZones() { return zones; }
    public List<AfkReward> getRewards() { return rewards; }
    public String getBossbarFormat() { return bossbarFormat; }
    public String getTurboBossbarTitle() { return turboBossbarTitle; }
    public String getTurboBossbarColor() { return turboBossbarColor; }
    public List<String> getPlaceholdersHelp() { return placeholdersHelp; }

    public void addZone(String name, AfkZone zone) {
        zones.put(name, zone);
        this.save();
    }

    @Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
    public static class AfkZone extends OkaeriConfig {
        private String world = "world";
        private double x1 = 0;
        private double y1 = 0;
        private double z1 = 0;
        private double x2 = 0;
        private double y2 = 0;
        private double z2 = 0;
        private String bossbarFormat = "";
        private String bossbarColor = "";
        private String permission = "";
        private String rewardPermission = "";
        private double moneyReward = 0;
        private int itemChance = 0;
        private int intervalSeconds = 0;
        private String displayName = "";
        private List<String> itemRewards = new ArrayList<>();
        private List<String> rewardCommands = new ArrayList<>();

        public AfkZone() {}

        public AfkZone(String world, double x1, double y1, double z1, double x2, double y2, double z2) {
            this.world = world;
            this.x1 = x1;
            this.y1 = y1;
            this.z1 = z1;
            this.x2 = x2;
            this.y2 = y2;
            this.z2 = z2;
        }

        public String getWorld() { return world; }
        public void setWorld(String world) { this.world = world; }
        public double getX1() { return x1; }
        public void setX1(double x1) { this.x1 = x1; }
        public double getY1() { return y1; }
        public void setY1(double y1) { this.y1 = y1; }
        public double getZ1() { return z1; }
        public void setZ1(double z1) { this.z1 = z1; }
        public double getX2() { return x2; }
        public void setX2(double x2) { this.x2 = x2; }
        public double getY2() { return y2; }
        public void setY2(double y2) { this.y2 = y2; }
        public double getZ2() { return z2; }
        public void setZ2(double z2) { this.z2 = z2; }
        public String getBossbarFormat() { return bossbarFormat; }
        public void setBossbarFormat(String bossbarFormat) { this.bossbarFormat = bossbarFormat; }
        public String getBossbarColor() { return bossbarColor; }
        public void setBossbarColor(String bossbarColor) { this.bossbarColor = bossbarColor; }
        public String getPermission() { return permission; }
        public void setPermission(String permission) { this.permission = permission; }
        public String getRewardPermission() { return rewardPermission; }
        public void setRewardPermission(String rewardPermission) { this.rewardPermission = rewardPermission; }
        public double getMoneyReward() { return moneyReward; }
        public void setMoneyReward(double moneyReward) { this.moneyReward = moneyReward; }
        public int getItemChance() { return itemChance; }
        public void setItemChance(int itemChance) { this.itemChance = itemChance; }
        public int getIntervalSeconds() { return intervalSeconds; }
        public void setIntervalSeconds(int intervalSeconds) { this.intervalSeconds = intervalSeconds; }
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public List<String> getItemRewards() { return itemRewards; }
        public void setItemRewards(List<String> itemRewards) { this.itemRewards = itemRewards; }
        public List<String> getRewardCommands() { return rewardCommands; }
        public void setRewardCommands(List<String> rewardCommands) { this.rewardCommands = rewardCommands; }

        public boolean isInside(String worldName, double x, double y, double z) {
            if (worldName == null || world == null || !world.equals(worldName)) {
                return false;
            }

            int blockX = floorBlock(x);
            int blockY = floorBlock(y);
            int blockZ = floorBlock(z);
            int minX = Math.min(floorBlock(x1), floorBlock(x2));
            int maxX = Math.max(floorBlock(x1), floorBlock(x2));
            int minY = Math.min(floorBlock(y1), floorBlock(y2));
            int maxY = Math.max(floorBlock(y1), floorBlock(y2));
            int minZ = Math.min(floorBlock(z1), floorBlock(z2));
            int maxZ = Math.max(floorBlock(z1), floorBlock(z2));
            return blockX >= minX && blockX <= maxX
                && blockY >= minY && blockY <= maxY
                && blockZ >= minZ && blockZ <= maxZ;
        }

        private static int floorBlock(double value) {
            return (int) Math.floor(value);
        }
    }

    @Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
    public static class AfkReward extends OkaeriConfig {
        private String permission = "";
        private double moneyReward = 0;
        private int itemChance = 0;
        private int intervalSeconds = 60;
        private String displayName = "";
        private String bossbarColor = "YELLOW";
        private String bossbarFormat = "";
        private List<String> itemRewards = new ArrayList<>();
        private List<String> rewardCommands = new ArrayList<>(List.of(
            "lp user {player} parent addtemp svip 3d"
        ));

        public AfkReward() {}

        public AfkReward(String permission, double moneyReward, int itemChance, int intervalSeconds, String displayName, String bossbarColor, List<String> itemRewards) {
            this(permission, moneyReward, itemChance, intervalSeconds, displayName, bossbarColor, "", itemRewards, new ArrayList<>());
        }

        public AfkReward(String permission, double moneyReward, int itemChance, int intervalSeconds, String displayName, String bossbarColor, String bossbarFormat, List<String> itemRewards) {
            this(permission, moneyReward, itemChance, intervalSeconds, displayName, bossbarColor, bossbarFormat, itemRewards, new ArrayList<>());
        }

        public AfkReward(String permission, double moneyReward, int itemChance, int intervalSeconds, String displayName, String bossbarColor, String bossbarFormat, List<String> itemRewards, List<String> rewardCommands) {
            this.permission = permission;
            this.moneyReward = moneyReward;
            this.itemChance = itemChance;
            this.intervalSeconds = intervalSeconds;
            this.displayName = displayName;
            this.bossbarColor = bossbarColor;
            this.bossbarFormat = bossbarFormat;
            this.itemRewards = itemRewards != null ? itemRewards : new ArrayList<>();
            this.rewardCommands = rewardCommands != null ? rewardCommands : new ArrayList<>();
        }

        public String getPermission() { return permission; }
        public double getMoneyReward() { return moneyReward; }
        public int getItemChance() { return itemChance; }
        public int getIntervalSeconds() { return intervalSeconds; }
        public String getDisplayName() { return displayName; }
        public String getBossbarColor() { return bossbarColor; }
        public String getBossbarFormat() { return bossbarFormat; }
        public void setBossbarFormat(String bossbarFormat) { this.bossbarFormat = bossbarFormat; }
        public List<String> getItemRewards() { return itemRewards; }
        public List<String> getRewardCommands() { return rewardCommands; }
    }
}
