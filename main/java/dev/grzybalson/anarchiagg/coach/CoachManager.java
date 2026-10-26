package dev.grzybalson.anarchiagg.coach;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import dev.grzybalson.HollyCore;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsGUI;
import dev.grzybalson.anarchiagg.coach.CoachConfig;
import dev.grzybalson.anarchiagg.coach.CoachDatabase;
import dev.grzybalson.anarchiagg.coach.CoachSkill;
import dev.grzybalson.anarchiagg.coach.data.PlayerCoachData;
import dev.grzybalson.anarchiagg.util.ItemCompatibilityUtil;
import dev.grzybalson.anarchiagg.util.SchedulerUtil;
import dev.grzybalson.utils.ColorUtils;

public class CoachManager {
    private static final Pattern ACTIONBAR_SPLIT_PATTERN = Pattern.compile("(?i)\\s*(&[0-9a-fk-orX#])?\\|\\s*");
    private static final int STORAGE_SIZE = 36;
    private static final CoachConfig.StrengthLevelSettings EMPTY_STRENGTH_LEVEL = new CoachConfig.StrengthLevelSettings(0, 0, 45, 0.0, 0.0, 0.0);
    private static final CoachConfig.DefenseLevelSettings EMPTY_DEFENSE_LEVEL = new CoachConfig.DefenseLevelSettings(0, 0, 45, 0.0, 0.0, 0.0);
    private static final CoachConfig.LuckLevelSettings EMPTY_LUCK_LEVEL = new CoachConfig.LuckLevelSettings(0, 0, 45, 1.0, 0.0, 0.0);
    private static final CoachConfig.IntelligenceLevelSettings EMPTY_INTELLIGENCE_LEVEL = new CoachConfig.IntelligenceLevelSettings(0, 0, 45, 0.0, 0.0, 0.0);
    private static final Set<Material> MYSTIC_ITEM_MATERIALS = EnumSet.of(Material.LEAD, new Material[]{Material.STICK, Material.JACK_O_LANTERN, Material.GOLDEN_PICKAXE, Material.HEART_OF_THE_SEA, Material.WEEPING_VINES, Material.FLOWER_POT, Material.GOLDEN_APPLE, Material.BONE, Material.CARROT, Material.HONEY_BOTTLE, Material.SHULKER_SHELL, Material.FISHING_ROD, Material.PHANTOM_MEMBRANE, Material.SOUL_TORCH, Material.CHORUS_FRUIT, Material.TRIDENT, Material.ORANGE_SHULKER_BOX, Material.CHAINMAIL_CHESTPLATE, Material.FIREWORK_ROCKET, Material.PLAYER_HEAD});
    private final HollyCore plugin;
    private final CoachConfig config;
    private final CoachDatabase database;
    private final NamespacedKey shardKey;
    private final NamespacedKey allSkillsPotionKey;
    private final NamespacedKey defenseHealthModifierKey;
    private final NamespacedKey[] mysticItemKeys;
    private final Map<UUID, PlayerCoachData> cache = new ConcurrentHashMap<UUID, PlayerCoachData>();
    private final Set<UUID> loading = ConcurrentHashMap.newKeySet();
    private final Set<CompletableFuture<Void>> pendingSaves = ConcurrentHashMap.newKeySet();
    private final ExecutorService saveExecutor;
    private volatile CoachConfig.StrengthLevelSettings[] strengthLevels = new CoachConfig.StrengthLevelSettings[0];
    private volatile CoachConfig.DexterityLevelSettings[] dexterityLevels = new CoachConfig.DexterityLevelSettings[0];
    private volatile CoachConfig.DefenseLevelSettings[] defenseLevels = new CoachConfig.DefenseLevelSettings[0];
    private volatile CoachConfig.LuckLevelSettings[] luckLevels = new CoachConfig.LuckLevelSettings[0];
    private volatile CoachConfig.IntelligenceLevelSettings[] intelligenceLevels = new CoachConfig.IntelligenceLevelSettings[0];
    private volatile CoachConfig.DexterityLevelSettings emptyDexterityLevel;
    private org.bukkit.scheduler.BukkitTask actionBarTask;

    public CoachManager(HollyCore plugin, CoachConfig config, CoachDatabase database) {
        this.plugin = plugin;
        this.config = config;
        this.database = database;
        this.shardKey = new NamespacedKey((Plugin)plugin, "coach_shard");
        this.allSkillsPotionKey = new NamespacedKey((Plugin)plugin, "coach_all_skills_potion");
        this.defenseHealthModifierKey = new NamespacedKey((Plugin)plugin, "coach_defense_health");
        this.mysticItemKeys = new NamespacedKey[]{AdminItemsGUI.KEY_LASSO, AdminItemsGUI.KEY_ROZGA, AdminItemsGUI.KEY_DYNIO_GRANAT, AdminItemsGUI.KEY_KLATWA_GORNIKA, AdminItemsGUI.KEY_POKEBALL, AdminItemsGUI.KEY_KOLCE, AdminItemsGUI.KEY_PUSZKA_PANDORY, AdminItemsGUI.KEY_LODOWA_ROZDZKA, AdminItemsGUI.KEY_NOS_OLAFA, AdminItemsGUI.KEY_MIKSTURA_ODMLODZENIA, AdminItemsGUI.KEY_KOZA, AdminItemsGUI.KEY_HAK, AdminItemsGUI.KEY_TORNADO, AdminItemsGUI.KEY_LUPA_OSWIECENIA, AdminItemsGUI.KEY_CHORUS_UCIECZKI, AdminItemsGUI.KEY_TROJZAB_ZNISZCZENIA, AdminItemsGUI.KEY_TORBA_KANGURA, AdminItemsGUI.KEY_GWIEZDNA_KLATA, AdminItemsGUI.KEY_SMOCZA_FAJERWERKA, AdminItemsGUI.KEY_ZLOTA_CZASZKA, AdminItemsGUI.KEY_ANTY_COBWEB, AdminItemsGUI.KEY_NIESKONCZONE_ZLOTE_JAPKO};

        this.saveExecutor = Executors.newSingleThreadExecutor(new CoachSaveThreadFactory());
        this.reloadRuntime();
    }

    public void reloadRuntime() {
        if (this.database != null) {
            this.database.connect();
        }
        this.strengthLevels = this.buildStrengthLevels();
        this.dexterityLevels = this.buildDexterityLevels();
        this.defenseLevels = this.buildDefenseLevels();
        this.luckLevels = this.buildLuckLevels();
        this.intelligenceLevels = this.buildIntelligenceLevels();
        this.emptyDexterityLevel = new CoachConfig.DexterityLevelSettings(0, 0, 45, 0.0, 0.0, 0.0, this.config.getDexterity().getFireworkCooldownSeconds());
        this.coachActionBarCache.clear();
        for (Player online : Bukkit.getOnlinePlayers()) {
            this.applyDefenseHealth(online);
            if (this.plugin.getActionBarManager() != null && this.isActionBarEnabled()) {
                this.plugin.getActionBarManager().refreshActionbar(online);
            }
        }
    }

    public CoachDatabase getDatabase() {
        return this.database;
    }

    public PlayerCoachData getData(UUID uuid) {
        PlayerCoachData cached = this.cache.get(uuid);
        if (cached != null && !cached.isLoadFailed()) {
            return cached;
        }
        PlayerCoachData loaded = this.database.load(uuid);
        return this.cache.compute(uuid, (id, existing) -> {
            if (existing != null && !existing.isLoadFailed()) {
                return existing;
            }
            if (loaded.isLoadFailed() && existing != null) {
                return existing;
            }
            return loaded;
        });
    }

    public PlayerCoachData getCachedData(UUID uuid) {
        return this.cache.get(uuid);
    }

    public void preload(UUID uuid) {
        PlayerCoachData cached = this.cache.get(uuid);
        if ((cached != null && !cached.isLoadFailed()) || !this.loading.add(uuid)) {
            return;
        }
        SchedulerUtil.runAsync((Plugin)this.plugin, () -> {
            PlayerCoachData loaded;
            try {
                loaded = this.database.load(uuid);
            }
            catch (Exception e) {
                this.loading.remove(uuid);
                dev.grzybalson.anarchiagg.util.TextUtil.sendConsole("&eNie mozna wczytac danych coach dla " + String.valueOf(uuid) + ": " + e.getMessage());
                return;
            }
            if (!this.plugin.isEnabled()) {
                this.loading.remove(uuid);
                return;
            }
            Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> {
                this.loading.remove(uuid);
                Player player = Bukkit.getPlayer((UUID)uuid);
                if (player == null || !player.isOnline()) {
                    return;
                }
                if (!loaded.isLoadFailed()) {
                    PlayerCoachData existing = this.cache.get(uuid);
                    if (existing == null || existing.isLoadFailed()) {
                        this.cache.put(uuid, loaded);
                    }
                }
                this.applyDefenseHealth(player);
                this.coachActionBarCache.remove(uuid);
                if (this.plugin.getActionBarManager() != null && this.isActionBarEnabled()) {
                    this.plugin.getActionBarManager().refreshActionbar(player);
                }
            });
        });
    }

    public CoachConfig getConfig() {
        return this.config;
    }

    public void onPlayerJoin(Player player) {
        if (player == null) {
            return;
        }
        this.coachActionBarCache.remove(player.getUniqueId());
        if (this.plugin.getActionBarManager() != null && this.isActionBarEnabled()) {
            this.plugin.getActionBarManager().refreshActionbar(player);
            Bukkit.getScheduler().runTaskLater((Plugin)this.plugin, () -> {
                if (player.isOnline()) {
                    this.plugin.getActionBarManager().refreshActionbar(player);
                }
            }, 20L);
        }
    }

    public void onPlayerQuit(Player player) {
        if (player == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        this.coachActionBarCache.remove(uuid);
        if (this.plugin.getActionBarManager() != null) {
            this.plugin.getActionBarManager().handlePlayerQuit(player);
        }
        this.unload(uuid);
    }

    public void unload(UUID uuid) {
        PlayerCoachData data = this.cache.get(uuid);
        if (data != null && data.canPersist()) {
            this.saveAsync(data, () -> this.removeOfflineCache(uuid, data));
            return;
        }
        if (data != null) {
            this.removeOfflineCache(uuid, data);
        }
    }

    public void saveAll() {
        this.waitForPendingSaves();
        for (PlayerCoachData data : this.cache.values()) {
            this.saveSafely(data.copy());
        }
        this.shutdownSaveExecutor();
    }

    public void reset(UUID uuid) {
        this.waitForPendingSaves();
        this.cache.remove(uuid);
        this.database.reset(uuid);
        Player player = Bukkit.getPlayer((UUID)uuid);
        if (player != null) {
            this.applyDefenseHealth(player);
        }
    }

    public void addKill(Player player, CoachSkill skill) {
        if (!this.config.isEnabled() || !this.isSkillEnabled(skill)) {
            return;
        }
        PlayerCoachData data = this.getData(player.getUniqueId());
        if (data.getLevel(skill) > 0) {
            return;
        }
        data.addKill(skill);
        this.saveAsync(data);
    }

    public void addKills(Player player) {
        if (!this.config.isEnabled()) {
            return;
        }
        PlayerCoachData data = this.getData(player.getUniqueId());
        boolean changed = false;
        for (CoachSkill skill : CoachSkill.values()) {
            if (!this.isSkillEnabled(skill) || data.getLevel(skill) > 0) continue;
            data.addKill(skill);
            changed = true;
        }
        if (changed) {
            this.saveAsync(data);
        }
    }

    public void addStrengthKill(Player player) {
        this.addKill(player, CoachSkill.STRENGTH);
    }

    public boolean tryUpgradeSkill(Player player, CoachSkill skill, int targetLevel) {
        return this.tryUpgradeSkill(player, skill, targetLevel, false);
    }

    public boolean tryUpgradeSkillWithShards(Player player, CoachSkill skill, int targetLevel) {
        return this.tryUpgradeSkill(player, skill, targetLevel, true);
    }

    public boolean tryUpgradeStrength(Player player, int targetLevel) {
        return this.tryUpgradeSkill(player, CoachSkill.STRENGTH, targetLevel, false);
    }

    public boolean tryUpgradeStrengthWithShards(Player player, int targetLevel) {
        return this.tryUpgradeSkill(player, CoachSkill.STRENGTH, targetLevel, true);
    }

    private boolean tryUpgradeSkill(Player player, CoachSkill skill, int targetLevel, boolean useShards) {
        int max;
        PlayerCoachData data = this.getData(player.getUniqueId());
        int current = data.getLevel(skill);
        if (current >= (max = this.getMaxLevel(skill))) {
            player.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(this.config.getMessages().getMaxLevel()));
            return false;
        }
        if (targetLevel != current + 1) {
            player.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(this.config.getMessages().getPreviousRequired()));
            return false;
        }
        if (targetLevel == 1 && !useShards) {
            int requiredKills = this.getRequiredKills(skill, targetLevel);
            int missing = Math.max(0, requiredKills - data.getKills(skill));
            if (missing > 0) {
                player.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(this.config.getMessages().getNotEnoughKills().replace("%amount%", String.valueOf(missing))));
                return false;
            }
        } else {
            int cost = this.getShardCost(skill, targetLevel);
            int missing = Math.max(0, cost - this.countShards(player));
            if (missing > 0) {
                player.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(this.config.getMessages().getNotEnoughShards().replace("%amount%", String.valueOf(missing))));
                return false;
            }
            this.removeShards(player, cost);
        }
        data.setLevel(skill, targetLevel);
        this.saveAsync(data);
        if (skill == CoachSkill.DEFENSE) {
            this.applyDefenseHealth(player);
        }
        player.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(this.config.getMessages().getUnlocked().replace("%level%", String.valueOf(targetLevel)).replace("%skill%", dev.grzybalson.utils.ColorUtils.colorize(skill.getDisplayName()))));
        return true;
    }

    public int getProgress(Player player, CoachSkill skill) {
        int level = this.getData(player.getUniqueId()).getLevel(skill);
        return this.getProgress(skill, level);
    }

    public int getStrengthProgress(Player player) {
        return this.getProgress(player, CoachSkill.STRENGTH);
    }

    public int getProgress(CoachSkill skill, int level) {
        if (level <= 0) {
            return 0;
        }
        return switch (skill) {
            default -> throw new IllegalArgumentException("Unknown skill");
            case STRENGTH -> this.getStrengthLevel(level).getProgressPercent();
            case DEXTERITY -> this.getDexterityLevel(level).getProgressPercent();
            case DEFENSE -> this.getDefenseLevel(level).getProgressPercent();
            case LUCK -> this.getLuckLevel(level).getProgressPercent();
            case INTELLIGENCE -> this.getIntelligenceLevel(level).getProgressPercent();
        };
    }

    public int getMaxLevel(CoachSkill skill) {
        return switch (skill) {
            default -> throw new IllegalArgumentException("Unknown skill");
            case STRENGTH -> this.config.getStrength().getMaxLevel();
            case DEXTERITY -> this.config.getDexterity().getMaxLevel();
            case DEFENSE -> this.config.getDefense().getMaxLevel();
            case LUCK -> this.config.getLuck().getMaxLevel();
            case INTELLIGENCE -> this.config.getIntelligence().getMaxLevel();
        };
    }

    public boolean isSkillEnabled(CoachSkill skill) {
        if (skill == null || !this.config.isEnabled()) return false;
        return switch (skill) {
            case STRENGTH -> this.config.getStrength().isEnabled();
            case DEXTERITY -> this.config.getDexterity().isEnabled();
            case DEFENSE -> this.config.getDefense().isEnabled();
            case LUCK -> this.config.getLuck().isEnabled();
            case INTELLIGENCE -> this.config.getIntelligence().isEnabled();
            default -> false;
        };
    }

    public int getRequiredKills(CoachSkill skill, int targetLevel) {
        return switch (skill) {
            default -> throw new IllegalArgumentException("Unknown skill");
            case STRENGTH -> this.getStrengthLevel(targetLevel).getRequiredKills();
            case DEXTERITY -> this.getDexterityLevel(targetLevel).getRequiredKills();
            case DEFENSE -> this.getDefenseLevel(targetLevel).getRequiredKills();
            case LUCK -> this.getLuckLevel(targetLevel).getRequiredKills();
            case INTELLIGENCE -> this.getIntelligenceLevel(targetLevel).getRequiredKills();
        };
    }

    public int getShardCost(CoachSkill skill, int targetLevel) {
        int cost = switch (skill) {
            default -> throw new IllegalArgumentException("Unknown skill");
            case STRENGTH -> this.getStrengthLevel(targetLevel).getShardCost();
            case DEXTERITY -> this.getDexterityLevel(targetLevel).getShardCost();
            case DEFENSE -> this.getDefenseLevel(targetLevel).getShardCost();
            case LUCK -> this.getLuckLevel(targetLevel).getShardCost();
            case INTELLIGENCE -> this.getIntelligenceLevel(targetLevel).getShardCost();
        };
        return targetLevel == 1 && cost <= 0 ? 45 : Math.max(0, cost);
    }

    public int getShardCost(int targetLevel, CoachConfig.StrengthLevelSettings level) {
        if (targetLevel == 1 && level.getShardCost() <= 0) {
            return 45;
        }
        return Math.max(0, level.getShardCost());
    }

    public double getStrengthDamageBonusPercent(Player player) {
        if (!this.config.isEnabled() || !this.config.getStrength().isEnabled()) {
            return 0.0;
        }
        int level = this.getData(player.getUniqueId()).getLevel(CoachSkill.STRENGTH);
        return this.getStrengthLevel(level).getDamageBonusPercent();
    }

    public double rollStrengthExtraHitDamage(Player player) {
        if (!this.config.isEnabled() || !this.config.getStrength().isEnabled()) {
            return 0.0;
        }
        int level = this.getData(player.getUniqueId()).getLevel(CoachSkill.STRENGTH);
        double chance = this.getStrengthLevel(level).getExtraHitChancePercent();
        if (!this.roll(chance)) {
            return 0.0;
        }
        return Math.max(0.0, this.config.getStrength().getExtraHitDamage());
    }

    public boolean tryApplyStrengthHeal(Player player) {
        double maxHealth;
        if (!this.config.isEnabled() || !this.config.getStrength().isEnabled()) {
            return false;
        }
        int level = this.getData(player.getUniqueId()).getLevel(CoachSkill.STRENGTH);
        if (!this.roll(this.getStrengthLevel(level).getHealChancePercent())) {
            return false;
        }
        AttributeInstance maxHealthAttribute = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        double d = maxHealth = maxHealthAttribute != null ? maxHealthAttribute.getValue() : 20.0;
        if (player.getHealth() >= maxHealth) {
            return false;
        }
        player.setHealth(Math.min(maxHealth, player.getHealth() + this.getStrengthHealHealth()));
        return true;
    }

    public boolean rollDexterityTotemStrength(Player player) {
        int level = this.getData(player.getUniqueId()).getLevel(CoachSkill.DEXTERITY);
        return this.config.isEnabled() && this.config.getDexterity().isEnabled() && this.roll(this.getDexterityLevel(level).getTotemStrengthChancePercent());
    }

    public boolean rollDexterityFireworkBlock(Player player) {
        int level = this.getData(player.getUniqueId()).getLevel(CoachSkill.DEXTERITY);
        return this.config.isEnabled() && this.config.getDexterity().isEnabled() && this.roll(this.getDexterityLevel(level).getFireworkBlockChancePercent());
    }

    public boolean rollDefenseBlock(Player player) {
        int level = this.getData(player.getUniqueId()).getLevel(CoachSkill.DEFENSE);
        return this.config.isEnabled() && this.config.getDefense().isEnabled() && this.roll(this.getDefenseLevel(level).getBlockChancePercent());
    }

    public boolean rollDefenseKeepArmor(Player player) {
        int level = this.getData(player.getUniqueId()).getLevel(CoachSkill.DEFENSE);
        return this.config.isEnabled() && this.config.getDefense().isEnabled() && this.roll(this.getDefenseLevel(level).getKeepArmorChancePercent());
    }

    public boolean rollLuckKeepSword(Player player) {
        int level = this.getData(player.getUniqueId()).getLevel(CoachSkill.LUCK);
        return this.config.isEnabled() && this.config.getLuck().isEnabled() && this.roll(this.getLuckLevel(level).getKeepSwordChancePercent());
    }

    public double getLuckRankingMultiplier(Player player) {
        int level = this.getData(player.getUniqueId()).getLevel(CoachSkill.LUCK);
        double multiplier = this.getLuckLevel(level).getRankingMultiplier();
        return this.config.isEnabled() && this.config.getLuck().isEnabled() && multiplier > 0.0 ? multiplier : 1.0;
    }

    public double getLuckGeneratorDropBonusPercent(Player player) {
        if (!this.config.isEnabled() || !this.config.getLuck().isEnabled()) {
            return 0.0;
        }
        UUID uuid = player.getUniqueId();
        PlayerCoachData data = this.cache.get(uuid);
        if (data == null) {
            this.preload(uuid);
            return 0.0;
        }
        return this.getLuckLevel(data.getLevel(CoachSkill.LUCK)).getGeneratorDropBonusPercent();
    }

    public boolean rollIntelligenceMysticBlock(Player player) {
        int level = this.getData(player.getUniqueId()).getLevel(CoachSkill.INTELLIGENCE);
        return this.config.isEnabled() && this.config.getIntelligence().isEnabled() && this.roll(this.getIntelligenceLevel(level).getMysticBlockChancePercent());
    }

    public boolean rollIntelligenceKeepMystics(Player player) {
        int level = this.getData(player.getUniqueId()).getLevel(CoachSkill.INTELLIGENCE);
        return this.config.isEnabled() && this.config.getIntelligence().isEnabled() && this.roll(this.getIntelligenceLevel(level).getKeepMysticsChancePercent());
    }

    public boolean rollIntelligenceBreakThreeTotems(Player player) {
        int level = this.getData(player.getUniqueId()).getLevel(CoachSkill.INTELLIGENCE);
        return this.config.isEnabled() && this.config.getIntelligence().isEnabled() && this.roll(this.getIntelligenceLevel(level).getBreakThreeTotemsChancePercent());
    }

    private static final UUID DEFENSE_HEALTH_MODIFIER_UUID = UUID.nameUUIDFromBytes("hollycore:coach_defense_health".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    private static final String DEFENSE_HEALTH_MODIFIER_NAME = "coach_defense_health";

    public void applyDefenseHealth(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        AttributeInstance attribute = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (attribute == null) {
            return;
        }

        int level = this.getData(player.getUniqueId()).getLevel(CoachSkill.DEFENSE);
        // extra-hearts w configu = serca widoczne dla gracza (MC), nie HP z Javy.
        // 2 serca MC = 4 HP (Attribute.GENERIC_MAX_HEALTH).
        double extraHearts = this.config.isEnabled() && this.config.getDefense().isEnabled()
            ? this.getDefenseLevel(level).getExtraHearts()
            : 0.0;
        double extraHealth = Math.max(0.0, extraHearts * 2.0);

        this.normalizeLegacyDefenseBase(attribute);
        this.removeDefenseHealthModifier(attribute);

        if (extraHealth > 0.0) {
            try {
                attribute.addModifier(new AttributeModifier(
                    DEFENSE_HEALTH_MODIFIER_UUID,
                    DEFENSE_HEALTH_MODIFIER_NAME,
                    extraHealth,
                    AttributeModifier.Operation.ADD_NUMBER
                ));
            } catch (IllegalArgumentException alreadyExists) {
                this.removeDefenseHealthModifier(attribute);
                try {
                    attribute.addModifier(new AttributeModifier(
                        DEFENSE_HEALTH_MODIFIER_UUID,
                        DEFENSE_HEALTH_MODIFIER_NAME,
                        extraHealth,
                        AttributeModifier.Operation.ADD_NUMBER
                    ));
                } catch (Exception ignored) {
                }
            }
        }

        double maxHealth = attribute.getValue();
        if (player.getHealth() > maxHealth) {
            player.setHealth(Math.max(1.0, maxHealth));
        }
    }

    private void normalizeLegacyDefenseBase(AttributeInstance attribute) {
        boolean hasModifier = attribute.getModifiers().stream()
            .anyMatch(this::isDefenseHealthModifier);
        if (hasModifier) {
            return;
        }
        double baseValue = attribute.getBaseValue();
        if (baseValue <= 20.0) {
            return;
        }
        CoachConfig.DefenseLevelSettings[] levels = this.defenseLevels;
        for (int level = 1; level < levels.length; level++) {
            CoachConfig.DefenseLevelSettings settings = levels[level];
            if (settings == null) {
                continue;
            }
            double legacyValue = 20.0 + Math.max(0.0, settings.getExtraHearts() * 2.0);
            if (legacyValue > 20.0 && Math.abs(baseValue - legacyValue) <= 0.01) {
                attribute.setBaseValue(20.0);
                return;
            }
        }
    }

    private void removeDefenseHealthModifier(AttributeInstance attribute) {
        new java.util.ArrayList<>(attribute.getModifiers()).forEach(modifier -> {
            if (isDefenseHealthModifier(modifier)) {
                try {
                    attribute.removeModifier(modifier);
                } catch (Exception ignored) {
                }
            }
        });
        try {
            attribute.removeModifier(DEFENSE_HEALTH_MODIFIER_UUID);
        } catch (Exception ignored) {
        }
    }

    private boolean isDefenseHealthModifier(AttributeModifier modifier) {
        if (modifier == null) {
            return false;
        }
        if (DEFENSE_HEALTH_MODIFIER_UUID.equals(modifier.getUniqueId())) {
            return true;
        }
        return DEFENSE_HEALTH_MODIFIER_NAME.equalsIgnoreCase(modifier.getName());
    }

    /**
     * Ustawia poziom skilla na podstawie procentu postępu (0-120).
     * @return ustawiony poziom skilla
     */
    public int setSkillByProgressPercent(UUID uuid, CoachSkill skill, int progressPercent) {
        if (uuid == null || skill == null) {
            return 0;
        }
        int clampedPercent = Math.max(0, Math.min(120, progressPercent));
        int targetLevel = this.findLevelByProgressPercent(skill, clampedPercent);
        PlayerCoachData data = this.getData(uuid);
        data.setLevel(skill, targetLevel);
        this.saveAsync(data);

        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            if (skill == CoachSkill.DEFENSE) {
                this.applyDefenseHealth(player);
            }
            this.coachActionBarCache.remove(uuid);
            if (this.plugin.getActionBarManager() != null && this.isActionBarEnabled()) {
                this.plugin.getActionBarManager().refreshActionbar(player);
            }
        }
        return targetLevel;
    }

    public int findLevelByProgressPercent(CoachSkill skill, int progressPercent) {
        if (progressPercent <= 0 || !this.isSkillEnabled(skill)) {
            return 0;
        }
        int maxLevel = this.getMaxLevel(skill);
        int bestLevel = 0;
        for (int level = 1; level <= maxLevel; level++) {
            int levelPercent = this.getProgress(skill, level);
            if (levelPercent == progressPercent) {
                return level;
            }
            if (levelPercent <= progressPercent) {
                bestLevel = level;
            }
        }
        return bestLevel;
    }

    public CoachSkill parseSkill(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String key = raw.trim().toLowerCase(Locale.ROOT);
        return switch (key) {
            case "strength", "sila", "siła", "str" -> CoachSkill.STRENGTH;
            case "dexterity", "zrecznosc", "zręczność", "dex", "zrecz" -> CoachSkill.DEXTERITY;
            case "defense", "obrona", "def", "ochrona" -> CoachSkill.DEFENSE;
            case "luck", "szczescie", "szczęście", "lucky" -> CoachSkill.LUCK;
            case "intelligence", "inteligencja", "int", "intel" -> CoachSkill.INTELLIGENCE;
            default -> {
                for (CoachSkill skill : CoachSkill.values()) {
                    if (skill.getId().equalsIgnoreCase(key) || skill.name().equalsIgnoreCase(key)) {
                        yield skill;
                    }
                }
                yield null;
            }
        };
    }

    public void sendStrengthExtraHitSubtitle(Player player, double damage) {
        String subtitle = this.config.getMessages().getExtraHitSubtitle().replace("%damage%", this.fmt(damage));
        dev.grzybalson.anarchiagg.util.TextUtil.sendTitle(player, "", subtitle, 5, 25, 10);
    }

    public void sendStrengthHealSubtitle(Player player) {
    }

    public void sendDefenseBlock(Player victim, Player attacker) {
        if (victim != null && victim.isOnline()) {
            victim.playSound(victim.getLocation(), org.bukkit.Sound.ITEM_SHIELD_BLOCK, 1.0f, 1.0f);
        }
        if (attacker != null && attacker.isOnline()) {
            attacker.playSound(attacker.getLocation(), org.bukkit.Sound.ITEM_SHIELD_BLOCK, 1.0f, 1.0f);
        }
    }

    public void sendMysticBlocked(Player victim, Player attacker) {
    }

    public void sendFireworkBlocked(Player attacker, Player victim, int seconds) {
    }

    public void blockFireworks(Player player, int seconds) {
        int ticks = Math.max(1, seconds) * 20;
        player.setCooldown(Material.FIREWORK_ROCKET, Math.max(player.getCooldown(Material.FIREWORK_ROCKET), ticks));
    }

    private String getFireworkBlockedVictimSubtitle() {
        String subtitle = this.config.getMessages().getFireworkBlockedSubtitleVictim();
        if (subtitle == null || subtitle.isBlank() || subtitle.equals("&cZablokowano ci fajerwerke!")) {
            return "&cZablokowano ci fajerwerki!";
        }
        return subtitle;
    }

    public void sendKeptItems(Player player, int amount) {
        if (amount <= 0) {
            return;
        }
    }

    public void sendThreeTotemsBroken(Player attacker, Player victim, int amount) {
    }

    public void breakThreeTotemsLater(Player attacker, Player victim) {
        this.plugin.getServer().getScheduler().runTask((Plugin)this.plugin, () -> {
            if (!attacker.isOnline() || !victim.isOnline()) {
                return;
            }
            int removed = this.removeTotems(victim, 3);
            this.sendThreeTotemsBroken(attacker, victim, removed);
        });
    }

    public double getStrengthHealHealth() {
        return Math.max(0.0, this.config.getStrength().getHealHearts() * 2.0);
    }

    private final Map<UUID, CoachActionBarCacheEntry> coachActionBarCache = new ConcurrentHashMap<>();
    private record CoachActionBarCacheEntry(String value, long timestamp) {}

    public boolean isActionBarEnabled() {
        return this.config != null && this.config.isEnabled() && this.config.getActionBar() != null && this.config.getActionBar().isEnabled();
    }

    public String getActionBar(Player player) {
        if (!this.config.isEnabled() || !this.config.getActionBar().isEnabled() || player == null) {
            return "";
        }
        long now = System.currentTimeMillis();
        CoachActionBarCacheEntry cached = coachActionBarCache.get(player.getUniqueId());
        if (cached != null && (now - cached.timestamp()) < 500L) {
            return cached.value();
        }

        String computed = computeActionBar(player);
        coachActionBarCache.put(player.getUniqueId(), new CoachActionBarCacheEntry(computed, now));
        return computed;
    }

    private String computeActionBar(Player player) {
        int strength = this.getProgress(player, CoachSkill.STRENGTH);
        int dexterity = this.getProgress(player, CoachSkill.DEXTERITY);
        int defense = this.getProgress(player, CoachSkill.DEFENSE);
        int luck = this.getProgress(player, CoachSkill.LUCK);
        int intelligence = this.getProgress(player, CoachSkill.INTELLIGENCE);

        int enabledCount = 0;
        int totalSum = 0;
        if (isSkillEnabled(CoachSkill.STRENGTH)) { totalSum += strength; enabledCount++; }
        if (isSkillEnabled(CoachSkill.DEXTERITY)) { totalSum += dexterity; enabledCount++; }
        if (isSkillEnabled(CoachSkill.DEFENSE)) { totalSum += defense; enabledCount++; }
        if (isSkillEnabled(CoachSkill.LUCK)) { totalSum += luck; enabledCount++; }
        if (isSkillEnabled(CoachSkill.INTELLIGENCE)) { totalSum += intelligence; enabledCount++; }

        if (enabledCount == 0) {
            return "";
        }

        int totalPercent = Math.min(100, Math.max(0, totalSum / enabledCount));

        String rawFormat = this.config.getActionBar().getFormat();
        if (rawFormat == null) return "";

        String[] parts = ACTIONBAR_SPLIT_PATTERN.split(rawFormat);
        java.util.List<String> cleanParts = new java.util.ArrayList<>();
        for (String part : parts) {
            if (part == null || part.isBlank()) continue;
            if (part.toLowerCase(java.util.Locale.ROOT).contains("suma")) continue;
            if (part.contains("%strength%") && !isSkillEnabled(CoachSkill.STRENGTH)) continue;
            if (part.contains("%dexterity%") && !isSkillEnabled(CoachSkill.DEXTERITY)) continue;
            if (part.contains("%defense%") && !isSkillEnabled(CoachSkill.DEFENSE)) continue;
            if (part.contains("%luck%") && !isSkillEnabled(CoachSkill.LUCK)) continue;
            if (part.contains("%intelligence%") && !isSkillEnabled(CoachSkill.INTELLIGENCE)) continue;
            cleanParts.add(part.trim());
        }

        String format = String.join(" &8| ", cleanParts);
        return dev.grzybalson.utils.ColorUtils.colorize(format
            .replace("%strength%", String.valueOf(strength))
            .replace("%dexterity%", String.valueOf(dexterity))
            .replace("%defense%", String.valueOf(defense))
            .replace("%luck%", String.valueOf(luck))
            .replace("%intelligence%", String.valueOf(intelligence))
            .replace("%total%", String.valueOf(totalPercent))
            .replace("%percent%", String.valueOf(totalPercent))
        );
    }

    public String getFullActionBarMessage(Player player) {
        if (!this.config.isEnabled() || !this.config.getActionBar().isEnabled()) {
            return "";
        }
        String msg = getActionBar(player);
        if (msg == null) msg = "";

        if (player.hasMetadata("ms_actionbar_msg")) {
            for (org.bukkit.metadata.MetadataValue meta : player.getMetadata("ms_actionbar_msg")) {
                String extMsg = meta.asString();
                if (extMsg != null && !extMsg.isEmpty()) {
                    if (msg.isEmpty()) {
                        msg = extMsg;
                    } else {
                        msg = msg + " &8| " + extMsg;
                    }
                    break;
                }
            }
        }
        return msg;
    }

    public void startActionBarTask() {
        if (this.actionBarTask != null) {
            this.actionBarTask.cancel();
            this.actionBarTask = null;
        }
        if (this.plugin.getActionBarManager() != null) {
            this.plugin.getActionBarManager().refreshAll();
        }
    }

    public ItemStack createShardItem(int amount) {
        CoachConfig.ShardSettings shard = this.config.getShards();
        ItemStack item = ItemCompatibilityUtil.stack(shard.getMaterial(), amount);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.setDisplayName(dev.grzybalson.utils.ColorUtils.colorize(shard.getName()));
        meta.setLore(shard.getLore().stream().map(dev.grzybalson.utils.ColorUtils::colorize).collect(java.util.stream.Collectors.toList()));
        meta.getPersistentDataContainer().set(this.shardKey, PersistentDataType.BYTE, (byte)1);
        if (shard.getCustomModelData() != 0) {
            meta.setCustomModelData(Integer.valueOf(shard.getCustomModelData()));
        }
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createAllSkillsPotionItem(int amount) {
        CoachConfig.AllSkillsPotionSettings potion = this.config.getAllSkillsPotion();
        ItemStack item = ItemCompatibilityUtil.stack(potion.getMaterial(), amount);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.setDisplayName(dev.grzybalson.utils.ColorUtils.colorize(potion.getName()));
        meta.setLore(potion.getLore().stream().map(dev.grzybalson.utils.ColorUtils::colorize).collect(java.util.stream.Collectors.toList()));
        meta.getPersistentDataContainer().set(this.allSkillsPotionKey, PersistentDataType.BYTE, (byte)1);
        if (potion.getCustomModelData() != 0) {
            meta.setCustomModelData(Integer.valueOf(potion.getCustomModelData()));
        }
        item.setItemMeta(meta);
        return item;
    }

    public boolean tryUseAllSkillsPotion(Player player, ItemStack item) {
        if (!this.config.isEnabled() || !this.config.getAllSkillsPotion().isEnabled()) {
            return false;
        }
        if (!this.isAllSkillsPotion(item)) {
            return false;
        }
        this.consumeOne(player, item);
        PlayerCoachData data = this.getData(player.getUniqueId());
        for (CoachSkill skill : CoachSkill.values()) {
            if (!this.isSkillEnabled(skill)) continue;
            data.setLevel(skill, this.getMaxLevel(skill));
        }
        this.saveAsync(data);
        this.applyDefenseHealth(player);
        dev.grzybalson.anarchiagg.util.TextUtil.sendMessage(player, this.config.getMessages().getAllSkillsPotionUsed());
        dev.grzybalson.anarchiagg.util.TextUtil.sendTitle(player, "", this.config.getMessages().getAllSkillsPotionUsed(), 5, 35, 10);
        return true;
    }

    private void saveAsync(PlayerCoachData data) {
        this.saveAsync(data, null);
    }

    private void saveAsync(PlayerCoachData data, Runnable afterSave) {
        if (data == null || !data.canPersist()) {
            if (afterSave != null) {
                afterSave.run();
            }
            return;
        }
        PlayerCoachData snapshot = data.copy();
        if (this.saveExecutor.isShutdown()) {
            this.saveSafely(snapshot);
            if (afterSave != null) {
                afterSave.run();
            }
            return;
        }
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> this.saveSafely(snapshot), this.saveExecutor);
        this.pendingSaves.add(future);
        future.whenComplete((unused, error) -> {
            this.pendingSaves.remove(future);
            if (error == null && afterSave != null) {
                afterSave.run();
            }
        });
    }

    private void saveSafely(PlayerCoachData data) {
        try {
            this.database.save(data);
        }
        catch (Exception e) {
            dev.grzybalson.anarchiagg.util.TextUtil.sendConsole("&eNie mozna zapisac danych coach dla " + String.valueOf(data.getUuid()) + ": " + e.getMessage());
            throw new CompletionException(e);
        }
    }

    private void waitForPendingSaves() {
        while (!this.pendingSaves.isEmpty()) {
            CompletableFuture[] saves = this.pendingSaves.toArray(new CompletableFuture[0]);
            try {
                CompletableFuture.allOf(saves).join();
            }
            catch (CompletionException completionException) {}
        }
    }

    private void shutdownSaveExecutor() {
        this.saveExecutor.shutdown();
        try {
            if (!this.saveExecutor.awaitTermination(5L, TimeUnit.SECONDS)) {
                this.saveExecutor.shutdownNow();
            }
        }
        catch (InterruptedException e) {
            this.saveExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void removeOfflineCache(UUID uuid, PlayerCoachData data) {
        if (!this.plugin.isEnabled()) {
            dev.grzybalson.anarchiagg.util.MapCompatibilityUtil.remove(this.cache, uuid, data);
            return;
        }
        Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> {
            Player player = Bukkit.getPlayer((UUID)uuid);
            if (!(player != null && player.isOnline() || this.cache.get(uuid) != data)) {
                dev.grzybalson.anarchiagg.util.MapCompatibilityUtil.remove(this.cache, uuid, data);
            }
        });
    }

    private void consumeOne(Player player, ItemStack item) {
        if (item.getAmount() <= 1) {
            player.getInventory().setItemInMainHand(null);
        } else {
            item.setAmount(item.getAmount() - 1);
        }
        player.updateInventory();
    }

    public int countShards(Player player) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (!this.isShard(item)) continue;
            count += item.getAmount();
        }
        return count;
    }

    public boolean withdrawShardsIfEnough(Player player, int amount) {
        if (amount <= 0) {
            return false;
        }
        if (this.countShards(player) < amount) {
            return false;
        }
        this.removeShards(player, amount);
        return true;
    }

    private static Class<?> msCustomItemsPluginClass;
    private static boolean msCustomItemsClassChecked = false;
    private static Set<String> cachedMsCustomItemDisplayNames = null;
    private static long lastMsCustomItemCacheTime = 0L;
    private static final long MS_CUSTOM_ITEM_CACHE_TTL = 30000L;

    private static final Set<String> MS_CUSTOM_ITEM_KEYWORDS = Set.of(
        "lodowa", "rozdzka", "różdżka", "hak", "zlota", "złota", "czaszka",
        "siersc", "sierść", "niedzwiedzia", "niedźwiedzia", "dragon", "fajerwerka",
        "lupa", "oswiecenia", "oświecenia", "gumka", "pandora", "pandory",
        "chorus", "ucieczki", "klatwa", "klątwa", "gornika", "górnika",
        "slodkie", "słodkie", "japko", "jabłko", "skora", "skóra", "koza", "kozy",
        "kangura", "torba", "trojzab", "trójząb", "zniszczenia", "gwiezdna", "klata",
        "lasso", "pumpkin", "dynio", "granat", "rozga", "rózga", "kolce", "jez", "jeża",
        "tornado", "mikstura", "odmlodzenia", "odmłodzenia", "kiel", "kieł", "wampira",
        "karuzela", "voodoo", "pochlaniacz", "pochłaniacz", "serc", "pokeball",
        "voldemort", "kwiatek", "piglin", "topor", "topór", "maczuga", "jaskiniowca",
        "banana", "banan", "wiedzmy", "wiedźmy", "yeti", "oko", "piernik",
        "zazaboj", "zaboj", "zabój", "olaf", "nos"
    );

    public boolean isMysticItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        // 1. Check direct keys from AdminItemsGUI & HollyCore
        if (this.hasAnyKey(meta, this.mysticItemKeys)) {
            return true;
        }

        // 2. Check PDC keys & namespaces (ms-customitems, minestarog, adminitems, etc.)
        Set<NamespacedKey> pdcKeys = meta.getPersistentDataContainer().getKeys();
        if (pdcKeys != null && !pdcKeys.isEmpty()) {
            for (NamespacedKey key : pdcKeys) {
                if (key == null) continue;
                String ns = key.getNamespace().toLowerCase(Locale.ROOT);
                String k = key.getKey().toLowerCase(Locale.ROOT);
                if (ns.contains("customitem") || ns.contains("msitem") || ns.contains("minestar")
                    || ns.contains("adminitem") || ns.contains("holly")
                    || k.contains("admin_item") || k.contains("custom_item") || k.contains("heart_absorber")
                    || k.contains("kangaroo_bag") || k.contains("star_breastplate") || k.contains("gold_skull")) {
                    return true;
                }
            }
        }

        // Fast material check
        boolean isMysticMaterial = MYSTIC_ITEM_MATERIALS.contains(item.getType());

        // 3. Check reflection for ms-customitems PluginConfig items (cached)
        if (isMsCustomItemReflective(meta)) {
            return true;
        }

        // 4. Fallback: check uncolored display name against custom item keywords
        if (isMysticMaterial && meta.hasDisplayName()) {
            String uncolored = ColorUtils.stripColors(meta.getDisplayName()).toLowerCase(Locale.ROOT).trim();
            if (!uncolored.isEmpty()) {
                for (String keyword : MS_CUSTOM_ITEM_KEYWORDS) {
                    if (uncolored.contains(keyword)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private boolean isMsCustomItemReflective(ItemMeta meta) {
        if (!meta.hasDisplayName()) {
            return false;
        }
        Set<String> customNames = getMsCustomItemDisplayNames();
        return !customNames.isEmpty() && customNames.contains(meta.getDisplayName());
    }

    private synchronized Set<String> getMsCustomItemDisplayNames() {
        long now = System.currentTimeMillis();
        if (cachedMsCustomItemDisplayNames != null && (now - lastMsCustomItemCacheTime) < MS_CUSTOM_ITEM_CACHE_TTL) {
            return cachedMsCustomItemDisplayNames;
        }

        if (!msCustomItemsClassChecked) {
            try {
                msCustomItemsPluginClass = Class.forName("dev.klakier.msitems.CustomItemsPlugin");
            } catch (ClassNotFoundException e) {
                msCustomItemsPluginClass = null;
            }
            msCustomItemsClassChecked = true;
        }

        Set<String> names = new HashSet<>();
        if (msCustomItemsPluginClass != null) {
            try {
                Object instance = msCustomItemsPluginClass.getMethod("getInstance").invoke(null);
                if (instance != null) {
                    Object pluginConfig = instance.getClass().getMethod("getPluginConfig").invoke(instance);
                    if (pluginConfig != null) {
                        for (java.lang.reflect.Method method : pluginConfig.getClass().getMethods()) {
                            if (method.getParameterCount() == 0 && method.getReturnType() != void.class) {
                                Object result = method.invoke(pluginConfig);
                                if (result != null) {
                                    try {
                                        java.lang.reflect.Method getItemStackMethod = result.getClass().getMethod("getItemStack");
                                        ItemStack configuredItem = (ItemStack) getItemStackMethod.invoke(result);
                                        if (configuredItem != null && configuredItem.hasItemMeta()) {
                                            ItemMeta configuredMeta = configuredItem.getItemMeta();
                                            if (configuredMeta != null && configuredMeta.hasDisplayName()) {
                                                names.add(configuredMeta.getDisplayName());
                                            }
                                        }
                                    } catch (NoSuchMethodException ignored) {}
                                }
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        cachedMsCustomItemDisplayNames = names;
        lastMsCustomItemCacheTime = now;
        return names;
    }


    public boolean isGoldenSkull(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        if (this.hasKey(meta, AdminItemsGUI.KEY_ZLOTA_CZASZKA)) {
            return true;
        }
        return item.getType() == Material.PLAYER_HEAD && this.plugin.getAdminItemsConfig().getGoldenSkull() != null && meta.hasCustomModelData() && meta.getCustomModelData() == this.plugin.getAdminItemsConfig().getGoldenSkull().getCustomModelData();
    }

    private boolean hasKey(ItemMeta meta, NamespacedKey key) {
        return key != null && meta.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    private boolean hasAnyKey(ItemMeta meta, NamespacedKey[] keys) {
        for (NamespacedKey key : keys) {
            if (!this.hasKey(meta, key)) continue;
            return true;
        }
        return false;
    }

    private int removeTotems(Player player, int amount) {
        int left = amount;
        int removed = 0;
        PlayerInventory inventory = player.getInventory();
        int heldSlot = inventory.getHeldItemSlot();
        for (int slot = 0; slot < 36 && left > 0; ++slot) {
            ItemStack item;
            if (slot == heldSlot || (item = inventory.getItem(slot)) == null || item.getType() != Material.TOTEM_OF_UNDYING) continue;
            int take = Math.min(left, item.getAmount());
            item.setAmount(item.getAmount() - take);
            removed += take;
            left -= take;
            if (item.getAmount() > 0) continue;
            inventory.setItem(slot, null);
        }
        if (removed > 0) {
            player.updateInventory();
        }
        return removed;
    }

    public List<String> buildProperties(CoachSkill skill, int targetLevel) {
        return switch (skill) {
            default -> throw new IllegalArgumentException("Unknown skill");
            case STRENGTH -> this.buildStrengthProperties(targetLevel);
            case DEXTERITY -> this.buildDexterityProperties(targetLevel);
            case DEFENSE -> this.buildDefenseProperties(targetLevel);
            case LUCK -> this.buildLuckProperties(targetLevel);
            case INTELLIGENCE -> this.buildIntelligenceProperties(targetLevel);
        };
    }

    private List<String> buildStrengthProperties(int targetLevel) {
        CoachConfig.StrengthLevelSettings level = this.getStrengthLevel(targetLevel);
        CoachConfig.StrengthLevelSettings previous = this.getStrengthLevel(targetLevel - 1);
        ArrayList<String> lore = new ArrayList<String>();
        lore.add(this.propertyLine(CoachSkill.STRENGTH, 1, "&6\u2460 &8\u2022 &cSzansa na uleczenie po uderzeniu", this.valueChange(previous.getHealChancePercent(), level.getHealChancePercent())));
        if (targetLevel >= 2) {
            lore.add(this.propertyLine(CoachSkill.STRENGTH, 2, "&b\u2461 &8\u2022 &dSzansa na extra uderzenie", this.valueChange(previous.getExtraHitChancePercent(), level.getExtraHitChancePercent())));
        }
        if (targetLevel >= 3) {
            lore.add(this.propertyLine(CoachSkill.STRENGTH, 3, "&#FF9F22\u2462 &8\u2022 &6Zwieksza twoje obrazenia", this.valueChange(previous.getDamageBonusPercent(), level.getDamageBonusPercent())));
        }
        return lore;
    }

    private List<String> buildDexterityProperties(int targetLevel) {
        CoachConfig.DexterityLevelSettings level = this.getDexterityLevel(targetLevel);
        CoachConfig.DexterityLevelSettings previous = this.getDexterityLevel(targetLevel - 1);
        ArrayList<String> lore = new ArrayList<String>();
        if (targetLevel >= 2) {
            lore.add(this.propertyLine(CoachSkill.DEXTERITY, 1, "&b\u2461 &8\u2022 &6Szansa na nadanie efektow po zbiciu totemu", this.valueChange(previous.getTotemStrengthChancePercent(), level.getTotemStrengthChancePercent())));
        }
        if (targetLevel >= 3) {
            lore.add(this.propertyLine(CoachSkill.DEXTERITY, 2, "&#FF9F22\u2462 &8\u2022 &6Szansa na zablokowanie przeciwnikowi fajerwerek", this.secondsChange(previous.getFireworkCooldownSeconds(), level.getFireworkCooldownSeconds(), previous.getFireworkBlockChancePercent(), level.getFireworkBlockChancePercent())));
        }
        return lore;
    }

    private List<String> buildDefenseProperties(int targetLevel) {
        CoachConfig.DefenseLevelSettings level = this.getDefenseLevel(targetLevel);
        CoachConfig.DefenseLevelSettings previous = this.getDefenseLevel(targetLevel - 1);
        ArrayList<String> lore = new ArrayList<String>();
        lore.add(this.propertyLine(CoachSkill.DEFENSE, 1, "&6\u2460 &8\u2022 &bSzansa na zachowanie 3 czesci zbroi po smierci", "&8(&a" + this.fmt(level.getKeepArmorChancePercent()) + "%&8)"));
        if (targetLevel >= 2) {
            lore.add(this.propertyLine(CoachSkill.DEFENSE, 2, "&b\u2461 &8\u2022 &9Dodatkowe zycie", this.heartChange(previous.getExtraHearts(), level.getExtraHearts())));
        }
        if (targetLevel >= 3) {
            lore.add(this.propertyLine(CoachSkill.DEFENSE, 3, "&#FF9F22\u2462 &8\u2022 &9Szansa na blokade", this.valueChange(previous.getBlockChancePercent(), level.getBlockChancePercent())));
        }
        return lore;
    }

    private List<String> buildLuckProperties(int targetLevel) {
        CoachConfig.LuckLevelSettings level = this.getLuckLevel(targetLevel);
        CoachConfig.LuckLevelSettings previous = this.getLuckLevel(targetLevel - 1);
        ArrayList<String> lore = new ArrayList<String>();
        lore.add(this.propertyLine(CoachSkill.LUCK, 1, "&6\u2460 &8\u2022 &aWiekszy ranking z zabojstw", this.multiplierChange(previous.getRankingMultiplier(), level.getRankingMultiplier())));
        if (targetLevel >= 2) {
            lore.add(this.propertyLine(CoachSkill.LUCK, 2, "&b\u2461 &8\u2022 &2Szansa na zachowanie miecza po smierci", this.valueChange(previous.getKeepSwordChancePercent(), level.getKeepSwordChancePercent())));
        }
        if (targetLevel >= 3) {
            lore.add(this.propertyLine(CoachSkill.LUCK, 3, "&#FF9F22\u2462 &8\u2022 &2Zwiekszony drop z generatorow", this.valueChange(previous.getGeneratorDropBonusPercent(), level.getGeneratorDropBonusPercent())));
        }
        return lore;
    }

    private List<String> buildIntelligenceProperties(int targetLevel) {
        CoachConfig.IntelligenceLevelSettings level = this.getIntelligenceLevel(targetLevel);
        CoachConfig.IntelligenceLevelSettings previous = this.getIntelligenceLevel(targetLevel - 1);
        ArrayList<String> lore = new ArrayList<String>();
        lore.add(this.propertyLine(CoachSkill.INTELLIGENCE, 1, "&6\u2460 &8\u2022 &dSzansa na zablokowanie mistycznego przedmiotu", this.valueChange(previous.getMysticBlockChancePercent(), level.getMysticBlockChancePercent())));
        if (targetLevel >= 2) {
            lore.add(this.propertyLine(CoachSkill.INTELLIGENCE, 2, "&b\u2461 &8\u2022 &dSzansa na zachowanie wszystkich mistycznych przedmiotow", this.valueChange(previous.getKeepMysticsChancePercent(), level.getKeepMysticsChancePercent())));
        }
        if (targetLevel >= 3) {
            lore.add(this.propertyLine(CoachSkill.INTELLIGENCE, 3, "&#FF9F22\u2462 &8\u2022 &dSzansa zbicie trzech totemow", this.valueChange(previous.getBreakThreeTotemsChancePercent(), level.getBreakThreeTotemsChancePercent())));
        }
        return lore;
    }

    private String propertyLine(CoachSkill skill, int index, String fallback, String suffix) {
        String label = this.configuredPropertyLabel(skill, index, fallback);
        if (suffix == null || suffix.isBlank()) {
            return label;
        }
        return label + " " + suffix;
    }

    private String configuredPropertyLabel(CoachSkill skill, int index, String fallback) {
        List<String> labels = this.configuredPropertyLabels(skill);
        if (index > 0 && labels.size() >= index) {
            return labels.get(index - 1);
        }
        return fallback;
    }

    private List<String> configuredPropertyLabels(CoachSkill skill) {
        List<String> source = switch (skill) {
            default -> throw new IllegalArgumentException("Unknown skill");
            case STRENGTH -> this.config.getGui().getStrengthItem().getLore();
            case DEXTERITY -> this.config.getGui().getDexterityItem().getLore();
            case DEFENSE -> this.config.getGui().getDefenseItem().getLore();
            case LUCK -> this.config.getGui().getLuckItem().getLore();
            case INTELLIGENCE -> this.config.getGui().getIntelligenceItem().getLore();
        };
        ArrayList<String> labels = new ArrayList<String>();
        boolean inProperties = false;
        for (String line : source) {
            String plain = this.stripColors(line).toLowerCase();
            if (!inProperties) {
                if (!plain.contains("wlasciwosci") && !plain.contains("w\u0142a\u015bciwo\u015bci")) continue;
                inProperties = true;
                continue;
            }
            if (line == null || line.isBlank() || plain.contains("kliknij")) break;
            if (skill == CoachSkill.DEXTERITY && (plain.contains("zatrucie") || plain.contains("truciz") || plain.contains("poison"))) continue;
            labels.add(line);
            if (labels.size() < 3) continue;
            break;
        }
        return labels;
    }

    private String stripColors(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("(?i)[&\u00a7]#[0-9a-f]{6}", "").replaceAll("(?i)[&\u00a7][0-9a-fk-orx]", "");
    }

    private void removeShards(Player player, int amount) {
        if (amount <= 0) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        int left = amount;
        for (int slot = 0; slot < inventory.getSize() && left > 0; ++slot) {
            ItemStack item = inventory.getItem(slot);
            if (!this.isShard(item)) continue;
            int take = Math.min(left, item.getAmount());
            item.setAmount(item.getAmount() - take);
            left -= take;
            if (item.getAmount() <= 0) {
                inventory.setItem(slot, null);
                continue;
            }
            inventory.setItem(slot, item);
        }
        player.updateInventory();
    }

    private boolean isShard(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        if (!item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        if (meta.getPersistentDataContainer().has(this.shardKey, PersistentDataType.BYTE)) {
            return true;
        }
        return item.getType() == this.config.getShards().getMaterial() && meta.hasDisplayName() && meta.getDisplayName().equals(dev.grzybalson.utils.ColorUtils.colorize(this.config.getShards().getName()));
    }

    private boolean isAllSkillsPotion(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        if (!item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        if (meta.getPersistentDataContainer().has(this.allSkillsPotionKey, PersistentDataType.BYTE)) {
            return true;
        }
        return item.getType() == this.config.getAllSkillsPotion().getMaterial() && meta.hasDisplayName() && meta.getDisplayName().equals(dev.grzybalson.utils.ColorUtils.colorize(this.config.getAllSkillsPotion().getName()));
    }

    public CoachConfig.StrengthLevelSettings getStrengthLevel(int level) {
        if (level <= 0) {
            return EMPTY_STRENGTH_LEVEL;
        }
        CoachConfig.StrengthLevelSettings[] levels = this.strengthLevels;
        CoachConfig.StrengthLevelSettings settings = level < levels.length ? levels[level] : null;
        return settings != null ? settings : new CoachConfig.StrengthLevelSettings(level * 10, 0, 45, 7.0, 3.0, 4.0);
    }

    public CoachConfig.DexterityLevelSettings getDexterityLevel(int level) {
        if (level <= 0) {
            CoachConfig.DexterityLevelSettings settings = this.emptyDexterityLevel;
            return settings != null ? settings : new CoachConfig.DexterityLevelSettings(0, 0, 45, 0.0, 0.0, 0.0, this.config.getDexterity().getFireworkCooldownSeconds());
        }
        CoachConfig.DexterityLevelSettings[] levels = this.dexterityLevels;
        CoachConfig.DexterityLevelSettings settings = level < levels.length ? levels[level] : null;
        return settings != null ? settings : new CoachConfig.DexterityLevelSettings(level * 10, 0, 45, 0.0, 5.0, 8.0, this.config.getDexterity().getFireworkCooldownSeconds());
    }

    public CoachConfig.DefenseLevelSettings getDefenseLevel(int level) {
        if (level <= 0) {
            return EMPTY_DEFENSE_LEVEL;
        }
        CoachConfig.DefenseLevelSettings[] levels = this.defenseLevels;
        CoachConfig.DefenseLevelSettings settings = level < levels.length ? levels[level] : null;
        return settings != null ? settings : new CoachConfig.DefenseLevelSettings(level * 10, 0, 45, 0.0, 2.0, 7.0);
    }

    public CoachConfig.LuckLevelSettings getLuckLevel(int level) {
        if (level <= 0) {
            return EMPTY_LUCK_LEVEL;
        }
        CoachConfig.LuckLevelSettings[] levels = this.luckLevels;
        CoachConfig.LuckLevelSettings settings = level < levels.length ? levels[level] : null;
        return settings != null ? settings : new CoachConfig.LuckLevelSettings(level * 10, 0, 45, 1.5, 0.0, 3.0);
    }

    public CoachConfig.IntelligenceLevelSettings getIntelligenceLevel(int level) {
        if (level <= 0) {
            return EMPTY_INTELLIGENCE_LEVEL;
        }
        CoachConfig.IntelligenceLevelSettings[] levels = this.intelligenceLevels;
        CoachConfig.IntelligenceLevelSettings settings = level < levels.length ? levels[level] : null;
        return settings != null ? settings : new CoachConfig.IntelligenceLevelSettings(level * 10, 0, 45, 20.0, 12.0, 0.0);
    }

    private CoachConfig.StrengthLevelSettings[] buildStrengthLevels() {
        int maxLevel = this.maxConfiguredLevel(this.config.getStrength().getMaxLevel(), this.config.getStrength().getLevels());
        CoachConfig.StrengthLevelSettings[] result = new CoachConfig.StrengthLevelSettings[maxLevel + 1];
        if (this.config.getStrength().getLevels() == null) {
            return result;
        }
        for (Map.Entry<String, CoachConfig.StrengthLevelSettings> entry : this.config.getStrength().getLevels().entrySet()) {
            int level = this.parseLevel(entry.getKey());
            if (level <= 0 || level >= result.length) continue;
            result[level] = entry.getValue();
        }
        return result;
    }

    private CoachConfig.DexterityLevelSettings[] buildDexterityLevels() {
        int maxLevel = this.maxConfiguredLevel(this.config.getDexterity().getMaxLevel(), this.config.getDexterity().getLevels());
        CoachConfig.DexterityLevelSettings[] result = new CoachConfig.DexterityLevelSettings[maxLevel + 1];
        if (this.config.getDexterity().getLevels() == null) {
            return result;
        }
        for (Map.Entry<String, CoachConfig.DexterityLevelSettings> entry : this.config.getDexterity().getLevels().entrySet()) {
            int level = this.parseLevel(entry.getKey());
            if (level <= 0 || level >= result.length) continue;
            result[level] = entry.getValue();
        }
        return result;
    }

    private CoachConfig.DefenseLevelSettings[] buildDefenseLevels() {
        int maxLevel = this.maxConfiguredLevel(this.config.getDefense().getMaxLevel(), this.config.getDefense().getLevels());
        CoachConfig.DefenseLevelSettings[] result = new CoachConfig.DefenseLevelSettings[maxLevel + 1];
        if (this.config.getDefense().getLevels() == null) {
            return result;
        }
        for (Map.Entry<String, CoachConfig.DefenseLevelSettings> entry : this.config.getDefense().getLevels().entrySet()) {
            int level = this.parseLevel(entry.getKey());
            if (level <= 0 || level >= result.length) continue;
            result[level] = entry.getValue();
        }
        return result;
    }

    private CoachConfig.LuckLevelSettings[] buildLuckLevels() {
        int maxLevel = this.maxConfiguredLevel(this.config.getLuck().getMaxLevel(), this.config.getLuck().getLevels());
        CoachConfig.LuckLevelSettings[] result = new CoachConfig.LuckLevelSettings[maxLevel + 1];
        if (this.config.getLuck().getLevels() == null) {
            return result;
        }
        for (Map.Entry<String, CoachConfig.LuckLevelSettings> entry : this.config.getLuck().getLevels().entrySet()) {
            int level = this.parseLevel(entry.getKey());
            if (level <= 0 || level >= result.length) continue;
            result[level] = entry.getValue();
        }
        return result;
    }

    private CoachConfig.IntelligenceLevelSettings[] buildIntelligenceLevels() {
        int maxLevel = this.maxConfiguredLevel(this.config.getIntelligence().getMaxLevel(), this.config.getIntelligence().getLevels());
        CoachConfig.IntelligenceLevelSettings[] result = new CoachConfig.IntelligenceLevelSettings[maxLevel + 1];
        if (this.config.getIntelligence().getLevels() == null) {
            return result;
        }
        for (Map.Entry<String, CoachConfig.IntelligenceLevelSettings> entry : this.config.getIntelligence().getLevels().entrySet()) {
            int level = this.parseLevel(entry.getKey());
            if (level <= 0 || level >= result.length) continue;
            result[level] = entry.getValue();
        }
        return result;
    }

    private int maxConfiguredLevel(int maxLevel, Map<String, ?> levels) {
        int result = Math.max(0, maxLevel);
        if (levels == null || levels.isEmpty()) {
            return result;
        }
        for (String key : levels.keySet()) {
            result = Math.max(result, this.parseLevel(key));
        }
        return result;
    }

    private int parseLevel(String value) {
        if (value == null) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(value));
        }
        catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private boolean roll(double percent) {
        return percent > 0.0 && ThreadLocalRandom.current().nextDouble(100.0) < percent;
    }

    private String valueChange(double previous, double current) {
        if (previous != current) {
            return "&8(&b" + this.fmt(previous) + "% &8-> &3" + this.fmt(current) + "%&8)";
        }
        return "&8(&3" + this.fmt(current) + "%&8)";
    }

    private String multiplierChange(double previous, double current) {
        if (previous != current) {
            return "&8(&b" + this.fmt(previous) + "x &8-> &3" + this.fmt(current) + "x&8)";
        }
        return "&8(&3" + this.fmt(current) + "x&8)";
    }

    private String heartChange(double previous, double current) {
        if (previous != current) {
            return "&8(&b" + this.fmt(previous) + " serc &8-> &3" + this.fmt(current) + " serc&7)";
        }
        return "&8(&3" + this.fmt(current) + " serc&8)";
    }

    private String secondsChange(int previousSeconds, int currentSeconds, double previousChance, double currentChance) {
        String chance = this.valueChange(previousChance, currentChance);
        if (previousSeconds != currentSeconds) {
            return chance + " &8(&b" + previousSeconds + "s &8-> &3" + currentSeconds + "s&8)";
        }
        return chance + " &8(&3" + currentSeconds + "s&8)";
    }

    private String fmt(double value) {
        if (value == (double)((long)value)) {
            return String.valueOf((long)value);
        }
        return String.format("%.1f", value);
    }

    private static class CoachSaveThreadFactory
    implements ThreadFactory {
        private CoachSaveThreadFactory() {
        }

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "holly-coach-save");
            thread.setDaemon(true);
            return thread;
        }
    }
}
