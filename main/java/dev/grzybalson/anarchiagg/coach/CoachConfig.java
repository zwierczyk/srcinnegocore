package dev.grzybalson.anarchiagg.coach;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.NameModifier;
import eu.okaeri.configs.annotation.NameStrategy;
import eu.okaeri.configs.annotation.Names;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;

@Names(strategy=NameStrategy.HYPHEN_CASE, modifier=NameModifier.TO_LOWER_CASE)
public class CoachConfig
extends OkaeriConfig {
    private GuiSettings gui = new GuiSettings();
    private ActionBarSettings actionBar = new ActionBarSettings();
    private ShardSettings shards = new ShardSettings();
    private AllSkillsPotionSettings allSkillsPotion = new AllSkillsPotionSettings();
    private StrengthSettings strength = new StrengthSettings();
    private DexteritySettings dexterity = new DexteritySettings();
    private DefenseSettings defense = new DefenseSettings();
    private LuckSettings luck = new LuckSettings();
    private IntelligenceSettings intelligence = new IntelligenceSettings();
    private MessagesSettings messages = new MessagesSettings();
    private MongoDbSettings mongodb = new MongoDbSettings();

    public boolean isEnabled() {
        return true;
    }

    public MongoDbSettings getMongodb() {
        return this.mongodb;
    }

    public void setMongodb(MongoDbSettings mongodb) {
        this.mongodb = mongodb;
    }

    public GuiSettings getGui() {
        return this.gui;
    }

    public void setGui(GuiSettings gui) {
        this.gui = gui;
    }

    public ActionBarSettings getActionBar() {
        return this.actionBar;
    }

    public void setActionBar(ActionBarSettings actionBar) {
        this.actionBar = actionBar;
    }

    public ShardSettings getShards() {
        return this.shards;
    }

    public void setShards(ShardSettings shards) {
        this.shards = shards;
    }

    public AllSkillsPotionSettings getAllSkillsPotion() {
        return this.allSkillsPotion;
    }

    public void setAllSkillsPotion(AllSkillsPotionSettings allSkillsPotion) {
        this.allSkillsPotion = allSkillsPotion;
    }

    public StrengthSettings getStrength() {
        return this.strength;
    }

    public void setStrength(StrengthSettings strength) {
        this.strength = strength;
    }

    public DexteritySettings getDexterity() {
        return this.dexterity;
    }

    public void setDexterity(DexteritySettings dexterity) {
        this.dexterity = dexterity;
    }

    public DefenseSettings getDefense() {
        return this.defense;
    }

    public void setDefense(DefenseSettings defense) {
        this.defense = defense;
    }

    public LuckSettings getLuck() {
        return this.luck;
    }

    public void setLuck(LuckSettings luck) {
        this.luck = luck;
    }

    public IntelligenceSettings getIntelligence() {
        return this.intelligence;
    }

    public void setIntelligence(IntelligenceSettings intelligence) {
        this.intelligence = intelligence;
    }

    public MessagesSettings getMessages() {
        return this.messages;
    }

    public void setMessages(MessagesSettings messages) {
        this.messages = messages;
    }

    public static class GuiSettings
    extends OkaeriConfig {
        private int mainSize = 45;
        private int strengthTreeSize = 54;
        private int strengthUpgradeSize = 45;
        private String mainTitle = "&8Trener";
        private String strengthTitle = "&#E41B53&lU&#E11A4F&lm&#DF194B&li&#DC1748&le&#DA1644&lj&#D71540&l\u0119&#D5133D&lt&#D21239&ln&#CF1135&lo&#CD0F32&l\u015b&#CA0E2E&l\u0107 &#C50B27&ls&#C30A23&li&#C0091F&l\u0142&#BE071C&ly";
        private String dexterityTitle = "&#F1E03C&lU&#F1DE3B&lm&#F1DC3A&li&#F0DB38&le&#F0D937&lj&#F0D736&l\u0119&#EFD535&lt&#EFD433&ln&#EFD232&lo&#EED031&l\u015b&#EECF2F&l\u0107 &#EDCB2D&lz&#EDCA2B&lr&#ECC82A&l\u0119&#ECC629&lc&#ECC428&lz&#EBC326&ln&#EBC125&lo&#EBBF24&l\u015b&#EABE22&lc&#EABC21&li";
        private String defenseTitle = "&#44CBF0&lU&#42C7EF&lm&#3FC4EE&li&#3DC1ED&le&#3ABEEC&lj&#38BAEB&l\u0119&#35B7EA&lt&#33B4E9&ln&#30B1E8&lo&#2EADE7&l\u015b&#2BAAE6&l\u0107 &#26A4E4&lo&#24A0E3&lb&#219DE2&lr&#1F9AE1&lo&#1C97E0&ln&#1A93DF&ly";
        private String luckTitle = "&#5FE03D&lU&#5BDE3B&lm&#57DC39&li&#53DA36&le&#4FD934&lj&#4BD731&l\u0119&#47D52F&lt&#43D32D&ln&#3FD12A&lo&#3CCF28&l\u015b&#38CD26&l\u0107 &#30C921&ls&#2CC71F&lz&#28C51C&lc&#24C41A&lz&#20C217&l\u0119&#1CC015&l\u015b&#18BE13&lc&#14BC10&li&#10BA0E&la";
        private String intelligenceTitle = "&#D878F6&lU&#D776F6&lm&#D674F5&li&#D672F5&le&#D570F5&lj&#D46EF5&l\u0119&#D46CF5&lt&#D36AF5&ln&#D268F4&lo&#D266F4&l\u015b&#D164F4&l\u0107 &#CF61F4&li&#CF5FF4&ln&#CE5DF3&lt&#CD5BF3&le&#CD59F3&ll&#CC57F3&li&#CB55F3&lg&#CB53F3&le&#CA51F2&ln&#C94FF2&lc&#C94DF2&lj&#C84BF2&li";
        private String fillerName = " ";
        private Material mainBorderEvenMaterial = Material.WHITE_STAINED_GLASS_PANE;
        private Material mainBorderOddMaterial = Material.ORANGE_STAINED_GLASS_PANE;
        private Material strengthBorderMaterial = Material.GRAY_STAINED_GLASS_PANE;
        private String closeName = "&cZamknij";
        private Material closeMaterial = Material.BARRIER;
        private int mainCloseSlot = 40;
        private int strengthCloseSlot = 49;
        private int strengthUpgradeCloseSlot = 40;
        private List<Integer> strengthLevelSlots = List.of(10, 19, 28, 37, 38, 39, 30, 21, 12, 13, 14, 23);
        private int strengthUpgradePreviewSlot = 13;
        private int strengthUpgradeKillsSlot = 30;
        private int strengthUpgradeShardsSlot = 32;
        private int strengthUpgradeOnlyShardsSlot = 31;
        private Material lockedLevelMaterial = Material.RED_STAINED_GLASS_PANE;
        private Material availableLevelMaterial = Material.ORANGE_STAINED_GLASS_PANE;
        private Material unlockedLevelMaterial = Material.LIME_STAINED_GLASS_PANE;
        private SkillItemSettings strengthItem = new SkillItemSettings(20, Material.DIAMOND_SWORD, "&#E41B53&lU&#E11A4F&lm&#DF194B&li&#DC1748&le&#DA1644&lj&#D71540&l\u0119&#D5133D&lt&#D21239&ln&#CF1135&lo&#CD0F32&l\u015b&#CA0E2E&l\u0107 &#C50B27&ls&#C30A23&li&#C0091F&l\u0142&#BE071C&ly", List.of("&8x &fPostep: &e%progress%%", "", "&8x &fW\u0142a\u015bciwo\u015bci:", "&6\u2460 &8\u2022 &#F8297DSzansa na uleczenie po uderzeniu", "&b\u2461 &8\u2022 &#BC195BSzansa na extra uderzenie", "&#FF9F22\u2462 &8\u2022 &#FF0000Zwieksza twoje obrazenia", "", "&aKliknij, aby wy\u015bwietli\u0107 post\u0119p!"));
        private SkillItemSettings dexterityItem = new SkillItemSettings(21, Material.HONEYCOMB, "&#F1E03C&lU&#F1DE3B&lm&#F1DC3A&li&#F0DB38&le&#F0D937&lj&#F0D736&l\u0119&#EFD535&lt&#EFD433&ln&#EFD232&lo&#EED031&l\u015b&#EECF2F&l\u0107 &#EDCB2D&lz&#EDCA2B&lr&#ECC82A&l\u0119&#ECC629&lc&#ECC428&lz&#EBC326&ln&#EBC125&lo&#EBBF24&l\u015b&#EABE22&lc&#EABC21&li", List.of("&8x &fPostep: &e%progress%%", "", "&8x &fW\u0142a\u015bciwo\u015bci:", "&b\u2461 &8\u2022 &6Szansa na nadanie efektow po zbiciu totemu", "&#FF9F22\u2462 &8\u2022 &#FF6700Szansa na zablokowanie przeciwnikowi fajerwerek", "", "&aKliknij, aby wy\u015bwietli\u0107 post\u0119p!"));
        private SkillItemSettings defenseItem = new SkillItemSettings(22, Material.SHIELD, "&#44CBF0&lU&#42C7EF&lm&#3FC4EE&li&#3DC1ED&le&#3ABEEC&lj&#38BAEB&l\u0119&#35B7EA&lt&#33B4E9&ln&#30B1E8&lo&#2EADE7&l\u015b&#2BAAE6&l\u0107 &#26A4E4&lo&#24A0E3&lb&#219DE2&lr&#1F9AE1&lo&#1C97E0&ln&#1A93DF&ly", List.of("&8x &fPostep: &e%progress%%", "", "&8x &fW\u0142a\u015bciwo\u015bci:", "&6\u2460 &8\u2022 &bSzansa na zachowanie 3 czesci zbroi po smierci", "&b\u2461 &8\u2022 &#0098FFDodatkowe zycie", "&#FF9F22\u2462 &8\u2022 &#8EBAD7Szansa na blokade", "", "&aKliknij, aby wy\u015bwietli\u0107 post\u0119p!"));
        private SkillItemSettings luckItem = new SkillItemSettings(23, Material.SCUTE, "&#5FE03D&lU&#5BDE3B&lm&#57DC39&li&#53DA36&le&#4FD934&lj&#4BD731&l\u0119&#47D52F&lt&#43D32D&ln&#3FD12A&lo&#3CCF28&l\u015b&#38CD26&l\u0107 &#30C921&ls&#2CC71F&lz&#28C51C&lc&#24C41A&lz&#20C217&l\u0119&#1CC015&l\u015b&#18BE13&lc&#14BC10&li&#10BA0E&la", List.of("&8x &fPostep: &e%progress%%", "", "&8x &fW\u0142a\u015bciwo\u015bci:", "&6\u2460 &8\u2022 &aWiekszy ranking z zabojstw", "&b\u2461 &8\u2022 &2Szansa na zachowanie miecza po smierci", "&#FF9F22\u2462 &8\u2022 &#FCFF38Zwiekszony drop z generatorow", "", "&aKliknij, aby wy\u015bwietli\u0107 post\u0119p!"));
        private SkillItemSettings intelligenceItem = new SkillItemSettings(24, Material.WRITABLE_BOOK, "&#D878F6&lU&#D776F6&lm&#D674F5&li&#D672F5&le&#D570F5&lj&#D46EF5&l\u0119&#D46CF5&lt&#D36AF5&ln&#D268F4&lo&#D266F4&l\u015b&#D164F4&l\u0107 &#CF61F4&li&#CF5FF4&ln&#CE5DF3&lt&#CD5BF3&le&#CD59F3&ll&#CC57F3&li&#CB55F3&lg&#CB53F3&le&#CA51F2&ln&#C94FF2&lc&#C94DF2&lj&#C84BF2&li", List.of("&8x &fPostep: &e%progress%%", "", "&8x &fW\u0142a\u015bciwo\u015bci:", "&6\u2460 &8\u2022 &dSzansa na zablokowanie mistycznego przedmiotu", "&b\u2461 &8\u2022 &#F438FFSzansa na zachowanie wszystkich mistycznych przedmiotow", "&#FF9F22\u2462 &8\u2022 &#4D65CFSzansa zbicie trzech totemow", "", "&aKliknij, aby wy\u015bwietli\u0107 post\u0119p!"));

        public int getMainSize() {
            return this.mainSize;
        }

        public void setMainSize(int mainSize) {
            this.mainSize = mainSize;
        }

        public int getStrengthTreeSize() {
            return this.strengthTreeSize;
        }

        public void setStrengthTreeSize(int strengthTreeSize) {
            this.strengthTreeSize = strengthTreeSize;
        }

        public int getStrengthUpgradeSize() {
            return this.strengthUpgradeSize;
        }

        public void setStrengthUpgradeSize(int strengthUpgradeSize) {
            this.strengthUpgradeSize = strengthUpgradeSize;
        }

        public String getMainTitle() {
            return this.mainTitle;
        }

        public void setMainTitle(String mainTitle) {
            this.mainTitle = mainTitle;
        }

        public String getStrengthTitle() {
            return this.strengthTitle;
        }

        public void setStrengthTitle(String strengthTitle) {
            this.strengthTitle = strengthTitle;
        }

        public String getDexterityTitle() {
            return this.dexterityTitle;
        }

        public void setDexterityTitle(String dexterityTitle) {
            this.dexterityTitle = dexterityTitle;
        }

        public String getDefenseTitle() {
            return this.defenseTitle;
        }

        public void setDefenseTitle(String defenseTitle) {
            this.defenseTitle = defenseTitle;
        }

        public String getLuckTitle() {
            return this.luckTitle;
        }

        public void setLuckTitle(String luckTitle) {
            this.luckTitle = luckTitle;
        }

        public String getIntelligenceTitle() {
            return this.intelligenceTitle;
        }

        public void setIntelligenceTitle(String intelligenceTitle) {
            this.intelligenceTitle = intelligenceTitle;
        }

        public String getFillerName() {
            return this.fillerName;
        }

        public void setFillerName(String fillerName) {
            this.fillerName = fillerName;
        }

        public Material getMainBorderEvenMaterial() {
            return this.mainBorderEvenMaterial;
        }

        public void setMainBorderEvenMaterial(Material mainBorderEvenMaterial) {
            this.mainBorderEvenMaterial = mainBorderEvenMaterial;
        }

        public Material getMainBorderOddMaterial() {
            return this.mainBorderOddMaterial;
        }

        public void setMainBorderOddMaterial(Material mainBorderOddMaterial) {
            this.mainBorderOddMaterial = mainBorderOddMaterial;
        }

        public Material getStrengthBorderMaterial() {
            return this.strengthBorderMaterial;
        }

        public void setStrengthBorderMaterial(Material strengthBorderMaterial) {
            this.strengthBorderMaterial = strengthBorderMaterial;
        }

        public String getCloseName() {
            return this.closeName;
        }

        public void setCloseName(String closeName) {
            this.closeName = closeName;
        }

        public Material getCloseMaterial() {
            return this.closeMaterial;
        }

        public void setCloseMaterial(Material closeMaterial) {
            this.closeMaterial = closeMaterial;
        }

        public int getMainCloseSlot() {
            return this.mainCloseSlot;
        }

        public void setMainCloseSlot(int mainCloseSlot) {
            this.mainCloseSlot = mainCloseSlot;
        }

        public int getStrengthCloseSlot() {
            return this.strengthCloseSlot;
        }

        public void setStrengthCloseSlot(int strengthCloseSlot) {
            this.strengthCloseSlot = strengthCloseSlot;
        }

        public int getStrengthUpgradeCloseSlot() {
            return this.strengthUpgradeCloseSlot;
        }

        public void setStrengthUpgradeCloseSlot(int strengthUpgradeCloseSlot) {
            this.strengthUpgradeCloseSlot = strengthUpgradeCloseSlot;
        }

        public List<Integer> getStrengthLevelSlots() {
            return this.strengthLevelSlots;
        }

        public void setStrengthLevelSlots(List<Integer> strengthLevelSlots) {
            this.strengthLevelSlots = strengthLevelSlots;
        }

        public int getStrengthUpgradePreviewSlot() {
            return this.strengthUpgradePreviewSlot;
        }

        public void setStrengthUpgradePreviewSlot(int strengthUpgradePreviewSlot) {
            this.strengthUpgradePreviewSlot = strengthUpgradePreviewSlot;
        }

        public int getStrengthUpgradeKillsSlot() {
            return this.strengthUpgradeKillsSlot;
        }

        public void setStrengthUpgradeKillsSlot(int strengthUpgradeKillsSlot) {
            this.strengthUpgradeKillsSlot = strengthUpgradeKillsSlot;
        }

        public int getStrengthUpgradeShardsSlot() {
            return this.strengthUpgradeShardsSlot;
        }

        public void setStrengthUpgradeShardsSlot(int strengthUpgradeShardsSlot) {
            this.strengthUpgradeShardsSlot = strengthUpgradeShardsSlot;
        }

        public int getStrengthUpgradeOnlyShardsSlot() {
            return this.strengthUpgradeOnlyShardsSlot;
        }

        public void setStrengthUpgradeOnlyShardsSlot(int strengthUpgradeOnlyShardsSlot) {
            this.strengthUpgradeOnlyShardsSlot = strengthUpgradeOnlyShardsSlot;
        }

        public Material getLockedLevelMaterial() {
            return this.lockedLevelMaterial;
        }

        public void setLockedLevelMaterial(Material lockedLevelMaterial) {
            this.lockedLevelMaterial = lockedLevelMaterial;
        }

        public Material getAvailableLevelMaterial() {
            return this.availableLevelMaterial;
        }

        public void setAvailableLevelMaterial(Material availableLevelMaterial) {
            this.availableLevelMaterial = availableLevelMaterial;
        }

        public Material getUnlockedLevelMaterial() {
            return this.unlockedLevelMaterial;
        }

        public void setUnlockedLevelMaterial(Material unlockedLevelMaterial) {
            this.unlockedLevelMaterial = unlockedLevelMaterial;
        }

        public SkillItemSettings getStrengthItem() {
            return this.strengthItem;
        }

        public void setStrengthItem(SkillItemSettings strengthItem) {
            this.strengthItem = strengthItem;
        }

        public SkillItemSettings getDexterityItem() {
            return this.dexterityItem;
        }

        public void setDexterityItem(SkillItemSettings dexterityItem) {
            this.dexterityItem = dexterityItem;
        }

        public SkillItemSettings getDefenseItem() {
            return this.defenseItem;
        }

        public void setDefenseItem(SkillItemSettings defenseItem) {
            this.defenseItem = defenseItem;
        }

        public SkillItemSettings getLuckItem() {
            return this.luckItem;
        }

        public void setLuckItem(SkillItemSettings luckItem) {
            this.luckItem = luckItem;
        }

        public SkillItemSettings getIntelligenceItem() {
            return this.intelligenceItem;
        }

        public void setIntelligenceItem(SkillItemSettings intelligenceItem) {
            this.intelligenceItem = intelligenceItem;
        }
    }

    public static class ActionBarSettings
    extends OkaeriConfig {
        private boolean enabled = true;
        private String format = "&c🪓 &f%strength%% &8| &e⚡ &f%dexterity%% &8| &b🛡 &f%defense%% &8| &a☘ &f%luck%% &8| &d☀ &f%intelligence%%";

        public boolean isEnabled() {
            return this.enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getFormat() {
            if (this.format != null && this.format.toLowerCase(java.util.Locale.ROOT).contains("suma")) {
                String[] parts = this.format.split("(?i)\\s*(&[0-9a-fk-orX])?\\|\\s*");
                java.util.List<String> cleanParts = new java.util.ArrayList<>();
                for (String part : parts) {
                    if (!part.toLowerCase(java.util.Locale.ROOT).contains("suma")) {
                        cleanParts.add(part.trim());
                    }
                }
                return String.join(" &8| ", cleanParts);
            }
            return this.format;
        }

        public void setFormat(String format) {
            this.format = format;
        }
    }

    public static class ShardSettings
    extends OkaeriConfig {
        private Material material = Material.PRISMARINE_SHARD;
        private String name = "&x&5&E&1&4&E&C&lS&x&6&6&1&4&E&D&lm&x&6&E&1&4&E&D&lo&x&7&6&1&5&E&E&lc&x&7&E&1&5&E&F&lz&x&8&6&1&5&E&F&ly &x&9&6&1&6&F&1&lo&x&9&E&1&6&F&2&ld&x&A&6&1&6&F&2&l\u0142&x&A&E&1&6&F&3&la&x&B&6&1&7&F&4&lm&x&B&E&1&7&F&4&le&x&C&6&1&7&F&5&lk";
        private List<String> lore = List.of("&8\u00d7 &fOd\u0142amek mo\u017cesz wykorzysta\u0107", "&8\u00d7 &fu &cTrenera &fna spawn");
        private int customModelData = 1001;

        public Material getMaterial() {
            return this.material;
        }

        public void setMaterial(Material material) {
            this.material = material;
        }

        public String getName() {
            return this.name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<String> getLore() {
            return this.lore;
        }

        public void setLore(List<String> lore) {
            this.lore = lore;
        }

        public int getCustomModelData() {
            return this.customModelData;
        }

        public void setCustomModelData(int customModelData) {
            this.customModelData = customModelData;
        }
    }

    public static class AllSkillsPotionSettings
    extends OkaeriConfig {
        private boolean enabled = true;
        private Material material = Material.POTION;
        private String name = "&x&E&6&E&B&2&1&l\u1d1b&x&E&6&E&6&1&F&l\u0280&x&E&5&E&0&1&E&l\u1d07&x&E&5&D&B&1&C&l\u0274&x&E&5&D&5&1&A&l\u026a&x&E&5&D&0&1&9&l\u0274&x&E&4&C&B&1&7&l\u0262 &x&E&3&B&A&1&2&l\u1d1c&x&E&3&B&8&1&1&l\u1d0d&x&E&3&B&5&1&0&l\u026a&x&E&4&B&A&1&2&l\u1d07&x&E&4&B&E&1&3&l\u1d0a&x&E&4&C&3&1&5&l\u0119&x&E&5&C&8&1&7&l\u1d1b&x&E&5&C&C&1&8&l\u0274&x&E&6&D&1&1&A&l\u1d0f&x&E&6&D&6&1&C&l\u015b&x&E&7&D&A&1&D&l\u1d04&x&E&7&D&F&1&F&l\u026a";
        private List<String> lore = List.of("&8\u00d7 &7Czas trwania: &b30 dni", "", "&8\u00d7 &7Po u\u017cyciu:", "&8\u00d7 &eWszystkie twoje umiej\u0119tno\u015bci u trenera zostan\u0105 ulepszone", "&8\u00d7 &edo &f12 poziomu&e. Po up\u0142ywie 30 dni, wr\u00f3c\u0105 do obecnego poziomu", "", "&aKliknij, aby u\u017cy\u0107!");
        private int customModelData = 1007;

        public boolean isEnabled() {
            return this.enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Material getMaterial() {
            return this.material;
        }

        public void setMaterial(Material material) {
            this.material = material;
        }

        public String getName() {
            return this.name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<String> getLore() {
            return this.lore;
        }

        public void setLore(List<String> lore) {
            this.lore = lore;
        }

        public int getCustomModelData() {
            return this.customModelData;
        }

        public void setCustomModelData(int customModelData) {
            this.customModelData = customModelData;
        }
    }

    public static class StrengthSettings
    extends OkaeriConfig {
        private boolean enabled = true;
        private int maxLevel = 12;
        private double healHearts = 0.5;
        private double extraHitDamage = 2.0;
        private Map<String, StrengthLevelSettings> levels = StrengthSettings.defaultLevels();

        private static Map<String, StrengthLevelSettings> defaultLevels() {
            LinkedHashMap<String, StrengthLevelSettings> levels = new LinkedHashMap<String, StrengthLevelSettings>();
            levels.put("1", new StrengthLevelSettings(10, 5, 45, 1.5, 0.0, 0.0));
            levels.put("2", new StrengthLevelSettings(20, 0, 45, 1.5, 0.0, 0.0));
            levels.put("3", new StrengthLevelSettings(30, 0, 45, 2.0, 1.0, 2.0));
            levels.put("4", new StrengthLevelSettings(40, 0, 45, 3.0, 1.5, 3.0));
            levels.put("5", new StrengthLevelSettings(50, 0, 45, 3.0, 2.0, 4.0));
            levels.put("6", new StrengthLevelSettings(60, 0, 45, 4.0, 3.0, 6.0));
            levels.put("7", new StrengthLevelSettings(70, 0, 45, 5.0, 4.0, 8.0));
            levels.put("8", new StrengthLevelSettings(80, 0, 45, 5.0, 5.0, 10.0));
            levels.put("9", new StrengthLevelSettings(90, 0, 45, 6.0, 6.0, 12.0));
            levels.put("10", new StrengthLevelSettings(100, 0, 45, 7.0, 7.0, 14.0));
            levels.put("11", new StrengthLevelSettings(110, 0, 45, 7.0, 8.0, 16.0));
            levels.put("12", new StrengthLevelSettings(120, 0, 45, 7.0, 10.0, 18.0));
            return levels;
        }

        public boolean isEnabled() {
            return this.enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxLevel() {
            return this.maxLevel;
        }

        public void setMaxLevel(int maxLevel) {
            this.maxLevel = maxLevel;
        }

        public double getHealHearts() {
            return this.healHearts;
        }

        public void setHealHearts(double healHearts) {
            this.healHearts = healHearts;
        }

        public double getExtraHitDamage() {
            return this.extraHitDamage;
        }

        public void setExtraHitDamage(double extraHitDamage) {
            this.extraHitDamage = extraHitDamage;
        }

        public Map<String, StrengthLevelSettings> getLevels() {
            return this.levels;
        }

        public void setLevels(Map<String, StrengthLevelSettings> levels) {
            this.levels = levels;
        }
    }

    public static class DexteritySettings
    extends OkaeriConfig {
        private boolean enabled = true;
        private int maxLevel = 12;
        private int poisonDurationSeconds = 4;
        private int poisonAmplifier = 0;
        private int totemStrengthDurationSeconds = 5;
        private int fireworkCooldownSeconds = 8;
        private Map<String, DexterityLevelSettings> levels = DexteritySettings.defaultLevels();

        private static Map<String, DexterityLevelSettings> defaultLevels() {
            LinkedHashMap<String, DexterityLevelSettings> levels = new LinkedHashMap<String, DexterityLevelSettings>();
            levels.put("1", new DexterityLevelSettings(10, 5, 45, 0.0, 0.0, 0.0, 8));
            levels.put("2", new DexterityLevelSettings(20, 0, 45, 0.0, 1.0, 0.0, 8));
            levels.put("3", new DexterityLevelSettings(30, 0, 45, 0.0, 1.0, 8.0, 8));
            levels.put("4", new DexterityLevelSettings(40, 0, 45, 0.0, 1.0, 8.0, 8));
            levels.put("5", new DexterityLevelSettings(50, 0, 45, 0.0, 3.0, 8.0, 8));
            levels.put("6", new DexterityLevelSettings(60, 0, 45, 0.0, 3.0, 8.0, 3));
            levels.put("7", new DexterityLevelSettings(70, 0, 45, 0.0, 3.0, 8.0, 3));
            levels.put("8", new DexterityLevelSettings(80, 0, 45, 0.0, 3.5, 8.0, 3));
            levels.put("9", new DexterityLevelSettings(90, 0, 45, 0.0, 3.5, 8.0, 4));
            levels.put("10", new DexterityLevelSettings(100, 0, 45, 0.0, 3.5, 8.0, 4));
            levels.put("11", new DexterityLevelSettings(110, 0, 45, 0.0, 5.0, 8.0, 4));
            levels.put("12", new DexterityLevelSettings(120, 0, 45, 0.0, 5.0, 8.0, 5));
            return levels;
        }

        public boolean isEnabled() {
            return this.enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxLevel() {
            return this.maxLevel;
        }

        public void setMaxLevel(int maxLevel) {
            this.maxLevel = maxLevel;
        }

        public int getPoisonDurationSeconds() {
            return this.poisonDurationSeconds;
        }

        public void setPoisonDurationSeconds(int poisonDurationSeconds) {
            this.poisonDurationSeconds = poisonDurationSeconds;
        }

        public int getPoisonAmplifier() {
            return this.poisonAmplifier;
        }

        public void setPoisonAmplifier(int poisonAmplifier) {
            this.poisonAmplifier = poisonAmplifier;
        }

        public int getTotemStrengthDurationSeconds() {
            return this.totemStrengthDurationSeconds;
        }

        public void setTotemStrengthDurationSeconds(int totemStrengthDurationSeconds) {
            this.totemStrengthDurationSeconds = totemStrengthDurationSeconds;
        }

        public int getFireworkCooldownSeconds() {
            return this.fireworkCooldownSeconds;
        }

        public void setFireworkCooldownSeconds(int fireworkCooldownSeconds) {
            this.fireworkCooldownSeconds = fireworkCooldownSeconds;
        }

        public Map<String, DexterityLevelSettings> getLevels() {
            return this.levels;
        }

        public void setLevels(Map<String, DexterityLevelSettings> levels) {
            this.levels = levels;
        }
    }

    public static class DefenseSettings
    extends OkaeriConfig {
        private boolean enabled = true;
        private int maxLevel = 12;
        private Map<String, DefenseLevelSettings> levels = DefenseSettings.defaultLevels();

        private static Map<String, DefenseLevelSettings> defaultLevels() {
            LinkedHashMap<String, DefenseLevelSettings> levels = new LinkedHashMap<String, DefenseLevelSettings>();
            levels.put("1", new DefenseLevelSettings(10, 5, 45, 0.0, 0.0, 0.0));
            levels.put("2", new DefenseLevelSettings(20, 0, 45, 0.0, 0.5, 0.0));
            levels.put("3", new DefenseLevelSettings(30, 0, 45, 0.0, 0.5, 1.5));
            levels.put("4", new DefenseLevelSettings(40, 0, 45, 0.0, 0.5, 1.5));
            levels.put("5", new DefenseLevelSettings(50, 0, 45, 0.0, 1.0, 1.5));
            levels.put("6", new DefenseLevelSettings(60, 0, 45, 0.0, 1.0, 3.0));
            levels.put("7", new DefenseLevelSettings(70, 0, 45, 0.0, 1.0, 3.0));
            levels.put("8", new DefenseLevelSettings(80, 0, 45, 0.0, 1.5, 3.0));
            levels.put("9", new DefenseLevelSettings(90, 0, 45, 0.0, 1.5, 4.5));
            levels.put("10", new DefenseLevelSettings(100, 0, 45, 0.0, 1.5, 4.5));
            levels.put("11", new DefenseLevelSettings(110, 0, 45, 0.0, 2.0, 4.5));
            levels.put("12", new DefenseLevelSettings(120, 0, 45, 0.0, 2.0, 7.0));
            return levels;
        }

        public boolean isEnabled() {
            return this.enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxLevel() {
            return this.maxLevel;
        }

        public void setMaxLevel(int maxLevel) {
            this.maxLevel = maxLevel;
        }

        public Map<String, DefenseLevelSettings> getLevels() {
            return this.levels;
        }

        public void setLevels(Map<String, DefenseLevelSettings> levels) {
            this.levels = levels;
        }
    }

    public static class LuckSettings
    extends OkaeriConfig {
        private boolean enabled = true;
        private int maxLevel = 12;
        private Map<String, LuckLevelSettings> levels = LuckSettings.defaultLevels();

        private static Map<String, LuckLevelSettings> defaultLevels() {
            LinkedHashMap<String, LuckLevelSettings> levels = new LinkedHashMap<String, LuckLevelSettings>();
            levels.put("1", new LuckLevelSettings(10, 5, 45, 1.1, 0.0, 0.0));
            levels.put("2", new LuckLevelSettings(20, 0, 45, 1.1, 0.0, 0.0));
            levels.put("3", new LuckLevelSettings(30, 0, 45, 1.1, 0.0, 1.5));
            levels.put("4", new LuckLevelSettings(40, 0, 45, 1.2, 0.0, 1.5));
            levels.put("5", new LuckLevelSettings(50, 0, 45, 1.2, 0.0, 1.5));
            levels.put("6", new LuckLevelSettings(60, 0, 45, 1.2, 0.0, 2.0));
            levels.put("7", new LuckLevelSettings(70, 0, 45, 1.4, 0.0, 2.0));
            levels.put("8", new LuckLevelSettings(80, 0, 45, 1.4, 0.0, 2.0));
            levels.put("9", new LuckLevelSettings(90, 0, 45, 1.4, 0.0, 2.5));
            levels.put("10", new LuckLevelSettings(100, 0, 45, 1.5, 0.0, 2.5));
            levels.put("11", new LuckLevelSettings(110, 0, 45, 1.5, 0.0, 2.5));
            levels.put("12", new LuckLevelSettings(120, 0, 45, 1.5, 0.0, 3.0));
            return levels;
        }

        public boolean isEnabled() {
            return this.enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxLevel() {
            return this.maxLevel;
        }

        public void setMaxLevel(int maxLevel) {
            this.maxLevel = maxLevel;
        }

        public Map<String, LuckLevelSettings> getLevels() {
            return this.levels;
        }

        public void setLevels(Map<String, LuckLevelSettings> levels) {
            this.levels = levels;
        }
    }

    public static class IntelligenceSettings
    extends OkaeriConfig {
        private boolean enabled = true;
        private int maxLevel = 12;
        private Map<String, IntelligenceLevelSettings> levels = IntelligenceSettings.defaultLevels();

        private static Map<String, IntelligenceLevelSettings> defaultLevels() {
            LinkedHashMap<String, IntelligenceLevelSettings> levels = new LinkedHashMap<String, IntelligenceLevelSettings>();
            levels.put("1", new IntelligenceLevelSettings(10, 5, 45, 0.1, 0.0, 0.0));
            levels.put("2", new IntelligenceLevelSettings(20, 0, 45, 5.0, 4.0, 0.0));
            levels.put("3", new IntelligenceLevelSettings(30, 0, 45, 5.0, 4.0, 0.0));
            levels.put("4", new IntelligenceLevelSettings(40, 0, 45, 10.0, 4.0, 0.0));
            levels.put("5", new IntelligenceLevelSettings(50, 0, 45, 10.0, 6.0, 0.0));
            levels.put("6", new IntelligenceLevelSettings(60, 0, 45, 10.0, 6.0, 0.0));
            levels.put("7", new IntelligenceLevelSettings(70, 0, 45, 15.0, 6.0, 0.0));
            levels.put("8", new IntelligenceLevelSettings(80, 0, 45, 15.0, 9.0, 0.0));
            levels.put("9", new IntelligenceLevelSettings(90, 0, 45, 15.0, 9.0, 0.0));
            levels.put("10", new IntelligenceLevelSettings(100, 0, 45, 20.0, 9.0, 0.0));
            levels.put("11", new IntelligenceLevelSettings(110, 0, 45, 20.0, 12.0, 0.0));
            levels.put("12", new IntelligenceLevelSettings(120, 0, 45, 20.0, 12.0, 0.0));
            return levels;
        }

        public boolean isEnabled() {
            return this.enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxLevel() {
            return this.maxLevel;
        }

        public void setMaxLevel(int maxLevel) {
            this.maxLevel = maxLevel;
        }

        public Map<String, IntelligenceLevelSettings> getLevels() {
            return this.levels;
        }

        public void setLevels(Map<String, IntelligenceLevelSettings> levels) {
            this.levels = levels;
        }
    }

    public static class MessagesSettings
    extends OkaeriConfig {
        private String fireworkBlockedSubtitleVictim = "&cZablokowano ci fajerwerki!";
        private String fireworkBlockedSubtitleAttacker = "&aZablokowales fajerwerke graczowi!";
        private String unlocked = "&#00FF3B\u2714 &8\u039e &#4EFA76Odblokowano &aumiejetno\u015b\u0107 &f%skill% &6#%level%";
        private String notEnoughKills = "&#FF0000\u2718 &8\u039e &cMusisz zabic jeszcze &4%amount% &cos\u00f3b.";
        private String notEnoughShards = "&#FF0000\u2718 &8\u039e &cBrakuje ci &4%amount%x &cSmoczych Odlamkow.";
        private String maxLevel = "&#00FF3B\u2714 &8\u039e &#4EFA76Masz juz maksymalny poziom sily.";
        private String previousRequired = "&cNajpierw odblokuj poprzedni poziom.";
        private String allSkillsPotionUsed = "&x&E&6&E&B&2&1&l\u1d1b&x&E&6&E&6&1&F&l\u0280&x&E&5&E&0&1&E&l\u1d07&x&E&5&D&B&1&C&l\u0274&x&E&5&D&5&1&A&l\u026a&x&E&5&D&0&1&9&l\u0274&x&E&4&C&B&1&7&l\u0262 &x&E&3&B&A&1&2&l\u1d1c&x&E&3&B&8&1&1&l\u1d0d&x&E&3&B&5&1&0&l\u026a&x&E&4&B&A&1&2&l\u1d07&x&E&4&B&E&1&3&l\u1d0a&x&E&4&C&3&1&5&l\u0119&x&E&5&C&8&1&7&l\u1d1b&x&E&5&C&C&1&8&l\u0274&x&E&6&D&1&1&A&l\u1d0f&x&E&6&D&6&1&C&l\u015b&x&E&7&D&A&1&D&l\u1d04&x&E&7&D&F&1&F&l\u026a &8:: &aOdblokowano wszystkie umiejetno\u015bci.";
        private String reset = "&#00FF3B\u2714 &8\u039e &#4EFA76Zresetowano trenera gracza &f%player%&a.";
        private String reloaded = "&#00FF3B\u2714 &8\u039e &#4EFA76Przeladowano konfiguracje trenera.";
        private String extraHitSubtitle = "&cExtra hit!";
        private String healSubtitle = "&aZosta\u0142e\u015b uleczony!";
        private String defenseBlockSubtitleVictim = "&cZablokowale\u015b uderzenie";
        private String defenseBlockSubtitleAttacker = "&cGracz zablokowa\u0142 ci uderzenie!";
        private String mysticBlockedSubtitleVictim = "&cZablokowale\u015b event\u00f3wki!";
        private String mysticBlockedSubtitleAttacker = "&cZablokowano ci event\u00f3wki!";
        private String threeTotemsBrokenSubtitle = "&aUkradle\u015b totemy dla gracza!";
        private String threeTotemsBrokenSubtitleVictim = "&cGracz &4%attacker% &czabra\u0142 ci totemy!";

        public String getFireworkBlockedSubtitleVictim() {
            return this.fireworkBlockedSubtitleVictim;
        }

        public void setFireworkBlockedSubtitleVictim(String fireworkBlockedSubtitleVictim) {
            this.fireworkBlockedSubtitleVictim = fireworkBlockedSubtitleVictim;
        }

        public String getFireworkBlockedSubtitleAttacker() {
            return this.fireworkBlockedSubtitleAttacker;
        }

        public void setFireworkBlockedSubtitleAttacker(String fireworkBlockedSubtitleAttacker) {
            this.fireworkBlockedSubtitleAttacker = fireworkBlockedSubtitleAttacker;
        }

        public String getUnlocked() {
            return this.unlocked;
        }

        public void setUnlocked(String unlocked) {
            this.unlocked = unlocked;
        }

        public String getNotEnoughKills() {
            return this.notEnoughKills;
        }

        public void setNotEnoughKills(String notEnoughKills) {
            this.notEnoughKills = notEnoughKills;
        }

        public String getNotEnoughShards() {
            return this.notEnoughShards;
        }

        public void setNotEnoughShards(String notEnoughShards) {
            this.notEnoughShards = notEnoughShards;
        }

        public String getMaxLevel() {
            return this.maxLevel;
        }

        public void setMaxLevel(String maxLevel) {
            this.maxLevel = maxLevel;
        }

        public String getPreviousRequired() {
            return this.previousRequired;
        }

        public void setPreviousRequired(String previousRequired) {
            this.previousRequired = previousRequired;
        }

        public String getAllSkillsPotionUsed() {
            return this.allSkillsPotionUsed;
        }

        public void setAllSkillsPotionUsed(String allSkillsPotionUsed) {
            this.allSkillsPotionUsed = allSkillsPotionUsed;
        }

        public String getReset() {
            return this.reset;
        }

        public void setReset(String reset) {
            this.reset = reset;
        }

        public String getReloaded() {
            return this.reloaded;
        }

        public void setReloaded(String reloaded) {
            this.reloaded = reloaded;
        }

        public String getExtraHitSubtitle() {
            return this.extraHitSubtitle;
        }

        public void setExtraHitSubtitle(String extraHitSubtitle) {
            this.extraHitSubtitle = extraHitSubtitle;
        }

        public String getHealSubtitle() {
            return this.healSubtitle;
        }

        public void setHealSubtitle(String healSubtitle) {
            this.healSubtitle = healSubtitle;
        }

        public String getDefenseBlockSubtitleVictim() {
            return this.defenseBlockSubtitleVictim;
        }

        public void setDefenseBlockSubtitleVictim(String defenseBlockSubtitleVictim) {
            this.defenseBlockSubtitleVictim = defenseBlockSubtitleVictim;
        }

        public String getDefenseBlockSubtitleAttacker() {
            return this.defenseBlockSubtitleAttacker;
        }

        public void setDefenseBlockSubtitleAttacker(String defenseBlockSubtitleAttacker) {
            this.defenseBlockSubtitleAttacker = defenseBlockSubtitleAttacker;
        }

        public String getMysticBlockedSubtitleVictim() {
            return this.mysticBlockedSubtitleVictim;
        }

        public void setMysticBlockedSubtitleVictim(String mysticBlockedSubtitleVictim) {
            this.mysticBlockedSubtitleVictim = mysticBlockedSubtitleVictim;
        }

        public String getMysticBlockedSubtitleAttacker() {
            return this.mysticBlockedSubtitleAttacker;
        }

        public void setMysticBlockedSubtitleAttacker(String mysticBlockedSubtitleAttacker) {
            this.mysticBlockedSubtitleAttacker = mysticBlockedSubtitleAttacker;
        }

        public String getThreeTotemsBrokenSubtitle() {
            return this.threeTotemsBrokenSubtitle;
        }

        public void setThreeTotemsBrokenSubtitle(String threeTotemsBrokenSubtitle) {
            this.threeTotemsBrokenSubtitle = threeTotemsBrokenSubtitle;
        }

        public String getThreeTotemsBrokenSubtitleVictim() {
            return this.threeTotemsBrokenSubtitleVictim;
        }

        public void setThreeTotemsBrokenSubtitleVictim(String threeTotemsBrokenSubtitleVictim) {
            this.threeTotemsBrokenSubtitleVictim = threeTotemsBrokenSubtitleVictim;
        }
    }

    public static class IntelligenceLevelSettings
    extends OkaeriConfig {
        private int progressPercent;
        private int requiredKills;
        private int shardCost;
        private double mysticBlockChancePercent;
        private double keepMysticsChancePercent;
        private double breakThreeTotemsChancePercent;

        public IntelligenceLevelSettings() {
        }

        public IntelligenceLevelSettings(int progressPercent, int requiredKills, int shardCost, double mysticBlockChancePercent, double keepMysticsChancePercent, double breakThreeTotemsChancePercent) {
            this.progressPercent = progressPercent;
            this.requiredKills = requiredKills;
            this.shardCost = shardCost;
            this.mysticBlockChancePercent = mysticBlockChancePercent;
            this.keepMysticsChancePercent = keepMysticsChancePercent;
            this.breakThreeTotemsChancePercent = breakThreeTotemsChancePercent;
        }

        public int getProgressPercent() {
            return this.progressPercent;
        }

        public void setProgressPercent(int progressPercent) {
            this.progressPercent = progressPercent;
        }

        public int getRequiredKills() {
            return this.requiredKills;
        }

        public void setRequiredKills(int requiredKills) {
            this.requiredKills = requiredKills;
        }

        public int getShardCost() {
            return this.shardCost;
        }

        public void setShardCost(int shardCost) {
            this.shardCost = shardCost;
        }

        public double getMysticBlockChancePercent() {
            return this.mysticBlockChancePercent;
        }

        public void setMysticBlockChancePercent(double mysticBlockChancePercent) {
            this.mysticBlockChancePercent = mysticBlockChancePercent;
        }

        public double getKeepMysticsChancePercent() {
            return this.keepMysticsChancePercent;
        }

        public void setKeepMysticsChancePercent(double keepMysticsChancePercent) {
            this.keepMysticsChancePercent = keepMysticsChancePercent;
        }

        public double getBreakThreeTotemsChancePercent() {
            return this.breakThreeTotemsChancePercent;
        }

        public void setBreakThreeTotemsChancePercent(double breakThreeTotemsChancePercent) {
            this.breakThreeTotemsChancePercent = breakThreeTotemsChancePercent;
        }
    }

    public static class LuckLevelSettings
    extends OkaeriConfig {
        private int progressPercent;
        private int requiredKills;
        private int shardCost;
        private double rankingMultiplier;
        private double keepSwordChancePercent;
        private double generatorDropBonusPercent;

        public LuckLevelSettings() {
        }

        public LuckLevelSettings(int progressPercent, int requiredKills, int shardCost, double rankingMultiplier, double keepSwordChancePercent, double generatorDropBonusPercent) {
            this.progressPercent = progressPercent;
            this.requiredKills = requiredKills;
            this.shardCost = shardCost;
            this.rankingMultiplier = rankingMultiplier;
            this.keepSwordChancePercent = keepSwordChancePercent;
            this.generatorDropBonusPercent = generatorDropBonusPercent;
        }

        public int getProgressPercent() {
            return this.progressPercent;
        }

        public void setProgressPercent(int progressPercent) {
            this.progressPercent = progressPercent;
        }

        public int getRequiredKills() {
            return this.requiredKills;
        }

        public void setRequiredKills(int requiredKills) {
            this.requiredKills = requiredKills;
        }

        public int getShardCost() {
            return this.shardCost;
        }

        public void setShardCost(int shardCost) {
            this.shardCost = shardCost;
        }

        public double getRankingMultiplier() {
            return this.rankingMultiplier;
        }

        public void setRankingMultiplier(double rankingMultiplier) {
            this.rankingMultiplier = rankingMultiplier;
        }

        public double getKeepSwordChancePercent() {
            return this.keepSwordChancePercent;
        }

        public void setKeepSwordChancePercent(double keepSwordChancePercent) {
            this.keepSwordChancePercent = keepSwordChancePercent;
        }

        public double getGeneratorDropBonusPercent() {
            return this.generatorDropBonusPercent;
        }

        public void setGeneratorDropBonusPercent(double generatorDropBonusPercent) {
            this.generatorDropBonusPercent = generatorDropBonusPercent;
        }
    }

    public static class DefenseLevelSettings
    extends OkaeriConfig {
        private int progressPercent;
        private int requiredKills;
        private int shardCost;
        private double keepArmorChancePercent;
        /** Serca widoczne dla gracza (MC). 2.0 = +2 serca = +4 HP w atrybucie. */
        private double extraHearts;
        private double blockChancePercent;

        public DefenseLevelSettings() {
        }

        public DefenseLevelSettings(int progressPercent, int requiredKills, int shardCost, double keepArmorChancePercent, double extraHearts, double blockChancePercent) {
            this.progressPercent = progressPercent;
            this.requiredKills = requiredKills;
            this.shardCost = shardCost;
            this.keepArmorChancePercent = keepArmorChancePercent;
            this.extraHearts = extraHearts;
            this.blockChancePercent = blockChancePercent;
        }

        public int getProgressPercent() {
            return this.progressPercent;
        }

        public void setProgressPercent(int progressPercent) {
            this.progressPercent = progressPercent;
        }

        public int getRequiredKills() {
            return this.requiredKills;
        }

        public void setRequiredKills(int requiredKills) {
            this.requiredKills = requiredKills;
        }

        public int getShardCost() {
            return this.shardCost;
        }

        public void setShardCost(int shardCost) {
            this.shardCost = shardCost;
        }

        public double getKeepArmorChancePercent() {
            return this.keepArmorChancePercent;
        }

        public void setKeepArmorChancePercent(double keepArmorChancePercent) {
            this.keepArmorChancePercent = keepArmorChancePercent;
        }

        public double getExtraHearts() {
            return this.extraHearts;
        }

        public void setExtraHearts(double extraHearts) {
            this.extraHearts = extraHearts;
        }

        public double getBlockChancePercent() {
            return this.blockChancePercent;
        }

        public void setBlockChancePercent(double blockChancePercent) {
            this.blockChancePercent = blockChancePercent;
        }
    }

    public static class DexterityLevelSettings
    extends OkaeriConfig {
        private int progressPercent;
        private int requiredKills;
        private int shardCost;
        private double poisonChancePercent;
        private double totemStrengthChancePercent;
        private double fireworkBlockChancePercent;
        private int fireworkCooldownSeconds;

        public DexterityLevelSettings() {
        }

        public DexterityLevelSettings(int progressPercent, int requiredKills, int shardCost, double poisonChancePercent, double totemStrengthChancePercent, double fireworkBlockChancePercent, int fireworkCooldownSeconds) {
            this.progressPercent = progressPercent;
            this.requiredKills = requiredKills;
            this.shardCost = shardCost;
            this.poisonChancePercent = poisonChancePercent;
            this.totemStrengthChancePercent = totemStrengthChancePercent;
            this.fireworkBlockChancePercent = fireworkBlockChancePercent;
            this.fireworkCooldownSeconds = fireworkCooldownSeconds;
        }

        public int getProgressPercent() {
            return this.progressPercent;
        }

        public void setProgressPercent(int progressPercent) {
            this.progressPercent = progressPercent;
        }

        public int getRequiredKills() {
            return this.requiredKills;
        }

        public void setRequiredKills(int requiredKills) {
            this.requiredKills = requiredKills;
        }

        public int getShardCost() {
            return this.shardCost;
        }

        public void setShardCost(int shardCost) {
            this.shardCost = shardCost;
        }

        public double getPoisonChancePercent() {
            return this.poisonChancePercent;
        }

        public void setPoisonChancePercent(double poisonChancePercent) {
            this.poisonChancePercent = poisonChancePercent;
        }

        public double getTotemStrengthChancePercent() {
            return this.totemStrengthChancePercent;
        }

        public void setTotemStrengthChancePercent(double totemStrengthChancePercent) {
            this.totemStrengthChancePercent = totemStrengthChancePercent;
        }

        public double getFireworkBlockChancePercent() {
            return this.fireworkBlockChancePercent;
        }

        public void setFireworkBlockChancePercent(double fireworkBlockChancePercent) {
            this.fireworkBlockChancePercent = fireworkBlockChancePercent;
        }

        public int getFireworkCooldownSeconds() {
            return this.fireworkCooldownSeconds;
        }

        public void setFireworkCooldownSeconds(int fireworkCooldownSeconds) {
            this.fireworkCooldownSeconds = fireworkCooldownSeconds;
        }
    }

    public static class StrengthLevelSettings
    extends OkaeriConfig {
        private int progressPercent;
        private int requiredKills;
        private int shardCost;
        private double healChancePercent;
        private double extraHitChancePercent;
        private double damageBonusPercent;

        public StrengthLevelSettings() {
        }

        public StrengthLevelSettings(int progressPercent, int requiredKills, int shardCost, double healChancePercent, double extraHitChancePercent, double damageBonusPercent) {
            this.progressPercent = progressPercent;
            this.requiredKills = requiredKills;
            this.shardCost = shardCost;
            this.healChancePercent = healChancePercent;
            this.extraHitChancePercent = extraHitChancePercent;
            this.damageBonusPercent = damageBonusPercent;
        }

        public int getProgressPercent() {
            return this.progressPercent;
        }

        public void setProgressPercent(int progressPercent) {
            this.progressPercent = progressPercent;
        }

        public int getRequiredKills() {
            return this.requiredKills;
        }

        public void setRequiredKills(int requiredKills) {
            this.requiredKills = requiredKills;
        }

        public int getShardCost() {
            return this.shardCost;
        }

        public void setShardCost(int shardCost) {
            this.shardCost = shardCost;
        }

        public double getHealChancePercent() {
            return this.healChancePercent;
        }

        public void setHealChancePercent(double healChancePercent) {
            this.healChancePercent = healChancePercent;
        }

        public double getExtraHitChancePercent() {
            return this.extraHitChancePercent;
        }

        public void setExtraHitChancePercent(double extraHitChancePercent) {
            this.extraHitChancePercent = extraHitChancePercent;
        }

        public double getDamageBonusPercent() {
            return this.damageBonusPercent;
        }

        public void setDamageBonusPercent(double damageBonusPercent) {
            this.damageBonusPercent = damageBonusPercent;
        }
    }

    public static class SkillItemSettings
    extends OkaeriConfig {
        private int slot;
        private Material material;
        private String name;
        private List<String> lore;
        private int customModelData = 0;

        public SkillItemSettings() {
        }

        public SkillItemSettings(int slot, Material material, String name, List<String> lore) {
            this(slot, material, name, lore, 0);
        }

        public SkillItemSettings(int slot, Material material, String name, List<String> lore, int customModelData) {
            this.slot = slot;
            this.material = material;
            this.name = name;
            this.lore = lore;
            this.customModelData = customModelData;
        }

        public int getSlot() {
            return this.slot;
        }

        public void setSlot(int slot) {
            this.slot = slot;
        }

        public Material getMaterial() {
            return this.material;
        }

        public void setMaterial(Material material) {
            this.material = material;
        }

        public String getName() {
            return this.name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<String> getLore() {
            return this.lore;
        }

        public void setLore(List<String> lore) {
            this.lore = lore;
        }

        public int getCustomModelData() {
            return this.customModelData;
        }

        public void setCustomModelData(int customModelData) {
            this.customModelData = customModelData;
        }
    }

    public static class MongoDbSettings extends OkaeriConfig {
        private boolean enabled = true;

        private String collection = "coachbeta01_players";

        public boolean isEnabled() {
            return this.enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getCollection() {
            return this.collection != null && !this.collection.isBlank() ? this.collection : "coachbeta01_players";
        }

        public void setCollection(String collection) {
            this.collection = collection;
        }

        public String getUri() {
            return "";
        }

        public String getDatabase() {
            return "";
        }
    }
}
