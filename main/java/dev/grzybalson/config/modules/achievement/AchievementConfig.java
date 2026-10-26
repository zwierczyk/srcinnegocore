package dev.grzybalson.config.modules.achievement;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.NameModifier;
import eu.okaeri.configs.annotation.NameStrategy;
import eu.okaeri.configs.annotation.Names;

import java.util.*;

@Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
public class AchievementConfig extends OkaeriConfig {

    private String guiTitle = "&5&lOsiągnięcia";
    private int guiRows = 5;
    private String subGuiTitle = "&8{category}";
    private int subGuiRows = 5;

    /** Hypestar: osiągnięcia aktywne dopiero po tej dacie (dd.MM.yyyy HH:mm:ss). Puste = od razu. */
    private String enableDate = "";

    private String categoryClaimedLine = " &7Odebrane osiągnięcia&8: &f{claimed}&8/&7{total}";

    // Category items in main GUI
    private String killsName = "&c&lZabójstwa";
    private List<String> killsLore = List.of("", "&a» &7Kliknij LPM aby otworzyć sekcję");
    private String killsMaterial = "DIAMOND_SWORD";
    private int killsSlot = 20;

    private String deathsName = "&4&lŚmierci";
    private List<String> deathsLore = List.of("", "&a» &7Kliknij LPM aby otworzyć sekcję");
    private String deathsMaterial = "SKELETON_SKULL";
    private int deathsSlot = 21;

    private String blocksMinedName = "&3&lZniszczone bloki";
    private List<String> blocksMinedLore = List.of("", "&eKliknij, aby &6przeglądać &eosiągnięcia w tej kategorii");
    private String blocksMinedMaterial = "DIAMOND_PICKAXE";
    private int blocksMinedSlot = 23;

    private String playtimeName = "&e&lSpędzony czas";
    private List<String> playtimeLore = List.of("", "&a» &7Kliknij LPM aby otworzyć sekcję");
    private String playtimeMaterial = "CLOCK";
    private int playtimeSlot = 24;

    private String cratesName = "&6&lSkrzynki";
    private List<String> cratesLore = List.of("", "&a» &7Kliknij LPM aby otworzyć sekcję");
    private String cratesMaterial = "CHEST";
    private int cratesSlot = 22;

    private String pointsName = "&d&lRanking";
    private List<String> pointsLore = List.of("", "&a» &7Kliknij LPM aby otworzyć sekcję");
    private String pointsMaterial = "NETHER_STAR";
    private int pointsSlot = 29;

    private String earnedPointsName = "&5&lZdobyte punkty";
    private List<String> earnedPointsLore = List.of("", "&a» &7Kliknij LPM aby otworzyć sekcję");
    private String earnedPointsMaterial = "EXPERIENCE_BOTTLE";
    private int earnedPointsSlot = 30;

    // Achievement tier requirements (tier 1-5)
    private Map<Integer, Integer> killsTiers = new LinkedHashMap<>() {{
        put(1, 10); put(2, 50); put(3, 100); put(4, 500); put(5, 1000);
    }};

    private Map<Integer, Integer> deathsTiers = new LinkedHashMap<>() {{
        put(1, 10); put(2, 50); put(3, 100); put(4, 500); put(5, 1000);
    }};

    private Map<Integer, Integer> blocksMinedTiers = new LinkedHashMap<>() {{
        put(1, 1250); put(2, 500); put(3, 1000); put(4, 5000); put(5, 10000);
    }};

    // Playtime tiers in milliseconds (Hypestar)
    private Map<Integer, Integer> playtimeTiers = new LinkedHashMap<>() {{
        put(1, 3_600_000);
        put(2, 18_000_000);
        put(3, 36_000_000);
        put(4, 108_000_000);
        put(5, 216_000_000);
    }};

    private Map<Integer, Integer> cratesTiers = new LinkedHashMap<>() {{
        put(1, 10); put(2, 50); put(3, 100); put(4, 250); put(5, 500);
    }};

    private Map<Integer, Integer> pointsTiers = new LinkedHashMap<>() {{
        put(1, 1100); put(2, 1300); put(3, 1500); put(4, 1800); put(5, 2200);
    }};

    private Map<Integer, Integer> earnedPointsTiers = new LinkedHashMap<>() {{
        put(1, 100); put(2, 500); put(3, 1000); put(4, 2500); put(5, 5000);
    }};

    // Rewards stored as serialized item data: key = "TYPE_TIER", value = base64 serialized items
    private Map<String, String> rewards = new HashMap<>();

    // Block mining settings
    private List<String> allowedBlocks = List.of(
        "STONE", "DEEPSLATE", "IRON_ORE", "DEEPSLATE_IRON_ORE",
        "GOLD_ORE", "DEEPSLATE_GOLD_ORE", "DIAMOND_ORE", "DEEPSLATE_DIAMOND_ORE",
        "COAL_ORE", "DEEPSLATE_COAL_ORE", "LAPIS_ORE", "DEEPSLATE_LAPIS_ORE",
        "REDSTONE_ORE", "DEEPSLATE_REDSTONE_ORE", "EMERALD_ORE", "DEEPSLATE_EMERALD_ORE",
        "COPPER_ORE", "DEEPSLATE_COPPER_ORE"
    );
    private List<String> excludedRegions = List.of("spawn", "safe_zone");

    // Achievement item display
    private String notCompletedMaterial = "RED_CONCRETE";
    private String completedMaterial = "LIME_CONCRETE";
    private String claimedMaterial = "GRAY_CONCRETE";

    private String achievementItemName = "&c&lOSIĄGNIĘCIE &8#&f{tier}";
    private String progressLine = "&a» &7Posiadasz &f{current}&7/&f{required}";
    private String progressLinePlaytime = "&a» &7Czas gry: &f{current}&7/&f{required}";
    private String rewardHeader = "";
    private List<String> categoryIntroLore = List.of(
        "&cOdbieraj osiągnięcia i zdobywaj",
        "&cnagrody w postaci fragmentów, kluczów itp."
    );
    private String rewardLine = "&a» &f{item} &7x{amount}";
    private String statusNotCompleted = "&cNie możesz jeszcze tego odebrać!";
    private String statusCompleted = "&aKliknij, aby odebrać!";
    private String statusClaimed = "&7» Osiągnięcie odebrane!";

    // Messages
    private String achievementClaimed = "&#00FF3B✔ &8Ξ &#4EFA76Odebrano nagrodę z osiągnięcia!";
    private String achievementAlreadyClaimed = "&#FF0000✘ &8Ξ &cJuż odebrałeś to osiągnięcie!";
    private String achievementNotCompleted = "&#FF0000✘ &8Ξ &cNie ukończyłeś jeszcze tego osiągnięcia!";
    private String noRewardsSet = "&#FF0000✘ &8Ξ &cBrak nagród do odebrania!";
    private String mustClaimPrevious = "&#FF0000✘ &8Ξ &cNajpierw odbierz poprzednie nagrody!";

    // Back button
    private String backButtonName = "&c&l◀ Powrót";
    private List<String> backButtonLore = List.of("", "&a» &7Kliknij LPM aby powrócić do głównej sekcji");

    // Getters
    public String getGuiTitle() { return guiTitle; }
    public int getGuiRows() { return guiRows; }
    public String getSubGuiTitle() { return subGuiTitle; }
    public int getSubGuiRows() { return subGuiRows; }
    public String getEnableDate() { return enableDate; }
    public long getEnableDateMillis() { return dev.grzybalson.utils.TimeUtil.fromDate(enableDate); }
    public String getCategoryClaimedLine() { return categoryClaimedLine; }

    public String getKillsName() { return killsName; }
    public List<String> getKillsLore() { return killsLore; }
    public String getKillsMaterial() { return killsMaterial; }
    public int getKillsSlot() { return killsSlot; }

    public String getDeathsName() { return deathsName; }
    public List<String> getDeathsLore() { return deathsLore; }
    public String getDeathsMaterial() { return deathsMaterial; }
    public int getDeathsSlot() { return deathsSlot; }

    public String getBlocksMinedName() { return blocksMinedName; }
    public List<String> getBlocksMinedLore() { return blocksMinedLore; }
    public String getBlocksMinedMaterial() { return blocksMinedMaterial; }
    public int getBlocksMinedSlot() { return blocksMinedSlot; }

    public String getPlaytimeName() { return playtimeName; }
    public List<String> getPlaytimeLore() { return playtimeLore; }
    public String getPlaytimeMaterial() { return playtimeMaterial; }
    public int getPlaytimeSlot() { return playtimeSlot; }

    public String getCratesName() { return cratesName; }
    public List<String> getCratesLore() { return cratesLore; }
    public String getCratesMaterial() { return cratesMaterial; }
    public int getCratesSlot() { return cratesSlot; }

    public String getPointsName() { return pointsName; }
    public List<String> getPointsLore() { return pointsLore; }
    public String getPointsMaterial() { return pointsMaterial; }
    public int getPointsSlot() { return pointsSlot; }

    public String getEarnedPointsName() { return earnedPointsName; }
    public List<String> getEarnedPointsLore() { return earnedPointsLore; }
    public String getEarnedPointsMaterial() { return earnedPointsMaterial; }
    public int getEarnedPointsSlot() { return earnedPointsSlot; }

    public Map<Integer, Integer> getKillsTiers() { return killsTiers; }
    public Map<Integer, Integer> getDeathsTiers() { return deathsTiers; }
    public Map<Integer, Integer> getBlocksMinedTiers() { return blocksMinedTiers; }
    public Map<Integer, Integer> getPlaytimeTiers() { return playtimeTiers; }
    public Map<Integer, Integer> getCratesTiers() { return cratesTiers; }
    public Map<Integer, Integer> getPointsTiers() { return pointsTiers; }
    public Map<Integer, Integer> getEarnedPointsTiers() { return earnedPointsTiers; }

    public Map<String, String> getRewards() { return rewards; }

    public void setReward(String key, String serializedItems) {
        rewards.put(key, serializedItems);
        this.save();
    }

    public String getReward(String key) {
        return rewards.get(key);
    }

    public List<String> getAllowedBlocks() { return allowedBlocks; }
    public List<String> getExcludedRegions() { return excludedRegions; }

    public String getNotCompletedMaterial() { return notCompletedMaterial; }
    public String getCompletedMaterial() { return completedMaterial; }
    public String getClaimedMaterial() { return claimedMaterial; }

    public String getAchievementItemName() { return achievementItemName; }
    public String getProgressLine() { return progressLine; }
    public String getProgressLinePlaytime() { return progressLinePlaytime; }
    public String getRewardHeader() { return rewardHeader; }
    public List<String> getCategoryIntroLore() { return categoryIntroLore; }
    public String getRewardLine() { return rewardLine; }
    public String getStatusNotCompleted() { return statusNotCompleted; }
    public String getStatusCompleted() { return statusCompleted; }
    public String getStatusClaimed() { return statusClaimed; }

    public String getAchievementClaimed() { return achievementClaimed; }
    public String getAchievementAlreadyClaimed() { return achievementAlreadyClaimed; }
    public String getAchievementNotCompleted() { return achievementNotCompleted; }
    public String getNoRewardsSet() { return noRewardsSet; }
    public String getMustClaimPrevious() { return mustClaimPrevious; }

    public String getBackButtonName() { return backButtonName; }
    public List<String> getBackButtonLore() { return backButtonLore; }

    public Map<Integer, Integer> getTiersForType(dev.grzybalson.modules.achievement.AchievementType type) {
        return switch (type) {
            case KILLS -> killsTiers;
            case DEATHS -> deathsTiers;
            case BLOCKS_MINED -> blocksMinedTiers;
            case PLAYTIME -> playtimeTiers;
            case CRATES -> cratesTiers;
            case POINTS -> pointsTiers;
            case EARNED_POINTS -> earnedPointsTiers;
        };
    }

    public String getCategoryName(dev.grzybalson.modules.achievement.AchievementType type) {
        return switch (type) {
            case KILLS -> killsName;
            case DEATHS -> deathsName;
            case BLOCKS_MINED -> blocksMinedName;
            case PLAYTIME -> playtimeName;
            case CRATES -> cratesName;
            case POINTS -> pointsName;
            case EARNED_POINTS -> earnedPointsName;
        };
    }

    public String getCategoryMaterial(dev.grzybalson.modules.achievement.AchievementType type) {
        return switch (type) {
            case KILLS -> killsMaterial;
            case DEATHS -> deathsMaterial;
            case BLOCKS_MINED -> blocksMinedMaterial;
            case PLAYTIME -> playtimeMaterial;
            case CRATES -> cratesMaterial;
            case POINTS -> pointsMaterial;
            case EARNED_POINTS -> earnedPointsMaterial;
        };
    }

    public List<String> getCategoryLore(dev.grzybalson.modules.achievement.AchievementType type) {
        return switch (type) {
            case KILLS -> killsLore;
            case DEATHS -> deathsLore;
            case BLOCKS_MINED -> blocksMinedLore;
            case PLAYTIME -> playtimeLore;
            case CRATES -> cratesLore;
            case POINTS -> pointsLore;
            case EARNED_POINTS -> earnedPointsLore;
        };
    }

    public int getCategorySlot(dev.grzybalson.modules.achievement.AchievementType type) {
        return switch (type) {
            case KILLS -> killsSlot;
            case DEATHS -> deathsSlot;
            case BLOCKS_MINED -> blocksMinedSlot;
            case PLAYTIME -> playtimeSlot;
            case CRATES -> cratesSlot;
            case POINTS -> pointsSlot;
            case EARNED_POINTS -> earnedPointsSlot;
        };
    }
}
