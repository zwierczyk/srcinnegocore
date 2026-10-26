package dev.grzybalson.modules.szaman;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.HollyDataPaths;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.ItemSerializer;
import dev.grzybalson.utils.NBTUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class PerksManager {

    public static final String PERK_ITEM_KEY = "perks_type";
    public static final UUID LIVE_HEALTH_MODIFIER_ID = UUID.fromString("93d33b3b-2cc4-4cb7-8547-1fc58591f30d");

    private static final String CONFIG_RESOURCE = "addons/szaman/szaman.yml";

    private final HollyCore plugin;
    private final File configFile;
    private final PerksDataManager dataManager;
    private final Random random = new Random();
    private final Set<UUID> pendingActions = new HashSet<>();
    private final Map<UUID, Long> fireworkBlockedUntil = new ConcurrentHashMap<>();

    private YamlConfiguration config;
    private Map<PerkType, PerkDefinition> definitions = new EnumMap<>(PerkType.class);
    private List<FillerItem> fillerItems = List.of();
    private MenuItem perkInfoItem;
    private MenuItem rollItem;
    private MenuItem turboDropItem;
    private MenuItem turboRankingItem;
    private MenuItem mysticKeyItem;
    private boolean mysticKeyEnabled;
    private int mysticKeySlot;
    private double mysticKeyCost;
    private String mysticKeyPickupPackage;
    private ItemStack shardItem;
    private String shardName;

    private int inventoryRows;
    private String inventoryTitle;
    private int perkInfoSlot;
    private int rollSlot;
    private int turboDropSlot;
    private int turboRankingSlot;
    private double dropMinPercent;
    private double dropMaxPercent;
    private double rankingMinPercent;
    private double rankingMaxPercent;
    private double rollCost;
    private double turboDropCost;
    private double turboRankingCost;
    private double maxTurboDropBonus;
    private double maxTurboRankingBonus;
    private int globalMaxLevel;
    private double lifestealHealAmount;
    private int slownessDurationTicks;
    private int slownessAmplifier;
    private double trapChancePercent;
    private int trapDefaultDurationTicks;
    private boolean trapAttributeAsDuration;
    private boolean liveValuesAreHearts;
    private boolean liveValuesAreHalfHearts;
    private boolean rollAnimationEnabled;
    private String rollAnimationTitle;
    private int rollAnimationSteps;
    private int rollAnimationPeriodTicks;
    private int rollAnimationCenterSlot;
    private List<Integer> rollAnimationSlots = List.of();
    private MenuItem rollAnimationFillerItem;
    private Sound rollSoundTick;
    private Sound rollSoundFinish;
    private Sound rollSoundFail;
    private Sound lifestealSound;
    private Sound trapSound;
    private Sound blockSound;

    private String upgradeLineFormat;

    public PerksManager(HollyCore plugin) {
        this.plugin = plugin;
        HollyDataPaths.ensureResource(plugin, CONFIG_RESOURCE);
        this.configFile = new File(plugin.getDataFolder(), CONFIG_RESOURCE);
        this.dataManager = new PerksDataManager(plugin);
        reload();
    }

    public void blockFireworks(UUID uuid, long untilMillis) {
        if (uuid != null && untilMillis > System.currentTimeMillis()) {
            fireworkBlockedUntil.put(uuid, untilMillis);
        }
    }

    public long getFireworkBlockRemainingMillis(UUID uuid) {
        if (uuid == null) {
            return 0L;
        }

        Long until = fireworkBlockedUntil.get(uuid);
        if (until == null) {
            return 0L;
        }

        long remaining = until - System.currentTimeMillis();
        if (remaining <= 0L) {
            fireworkBlockedUntil.remove(uuid);
            return 0L;
        }
        return remaining;
    }

    public void clearFireworkBlock(UUID uuid) {
        if (uuid != null) {
            fireworkBlockedUntil.remove(uuid);
        }
    }

    public void reload() {
        this.config = YamlConfiguration.loadConfiguration(configFile);
        ensureMysticKeyConfigDefaults();

        this.inventoryRows = plugin.getGuiFactory().normalizeRows(config.getInt("inventory.rows", 5));
        this.inventoryTitle = config.getString("inventory.title", "&5&lSZAMAN");
        this.perkInfoSlot = config.getInt("perkInfoSlot", 22);
        this.rollSlot = config.getInt("rollSlot", 31);
        this.turboDropSlot = config.getInt("turboDropSlot", 29);
        this.turboRankingSlot = config.getInt("turboRankingSlot", 33);

        this.dropMinPercent = sanePercent(config.getDouble("dropMinPrecent", 0.01D));
        this.dropMaxPercent = sanePercent(config.getDouble("dropMaxPrecent", 0.05D));
        this.rankingMinPercent = sanePercent(config.getDouble("rankingMinPrecent", 0.01D));
        this.rankingMaxPercent = sanePercent(config.getDouble("rankingMaxPrecent", 0.03D));
        this.rollCost = Math.max(0.0D, config.getDouble("rollCost", 100.0D));
        this.turboDropCost = Math.max(0.0D, config.getDouble("turboDropCost", 20.0D));
        this.turboRankingCost = Math.max(0.0D, config.getDouble("turboRankingCost", 20.0D));
        this.maxTurboDropBonus = Math.max(0.0D, config.getDouble("maxTurboDropBonus", 2.0D));
        this.maxTurboRankingBonus = Math.max(0.0D, config.getDouble("maxTurboRankingBonus", 2.0D));
        this.globalMaxLevel = Math.max(1, config.getInt("maxLevel", config.getInt("max-level", 4)));

        this.lifestealHealAmount = Math.max(0.0D, config.getDouble("effects.lifesteal-heal-amount", 2.0D));
        this.slownessDurationTicks = Math.max(1, config.getInt("effects.slowness-duration-seconds", 3)) * 20;
        this.slownessAmplifier = Math.max(0, config.getInt("effects.slowness-amplifier", 0));
        this.trapChancePercent = sanePercent(config.getDouble("effects.trap-chance-percent", 10.0D));
        this.trapDefaultDurationTicks = Math.max(1, config.getInt("effects.trap-default-duration-seconds", 3)) * 20;
        this.trapAttributeAsDuration = config.getBoolean("effects.trap-attribute-as-duration", true);
        this.liveValuesAreHearts = config.getBoolean("effects.live-values-are-hearts", true);
        this.liveValuesAreHalfHearts = config.getBoolean("effects.live-values-are-half-hearts", true);
        this.upgradeLineFormat = config.getString("upgrade-line-format", "&8┃ &f{NAME}: &e{LEVEL}&7/&e{MAX} &8(&7{VALUE}&8)");
        this.rollAnimationEnabled = config.getBoolean("rollAnimation.enabled", true);
        this.rollAnimationTitle = config.getString("rollAnimation.title", "&8Losowanie perku");
        this.rollAnimationSteps = Math.max(8, config.getInt("rollAnimation.steps", 36));
        this.rollAnimationPeriodTicks = Math.max(1, config.getInt("rollAnimation.period-ticks", 2));
        this.rollAnimationCenterSlot = config.getInt("rollAnimation.center-slot", 13);
        this.rollAnimationSlots = loadRollAnimationSlots();
        this.rollAnimationFillerItem = MenuItem.fromSection(config.getConfigurationSection("rollAnimation.filler"), Material.BLACK_STAINED_GLASS_PANE, " ");
        this.rollSoundTick = readSound("rollAnimation.sounds.tick", Sound.UI_BUTTON_CLICK);
        this.rollSoundFinish = readSound("rollAnimation.sounds.finish", Sound.ENTITY_PLAYER_LEVELUP);
        this.rollSoundFail = readSound("rollAnimation.sounds.fail", Sound.BLOCK_NOTE_BLOCK_BASS);
        this.lifestealSound = readSound("effects.sounds.lifesteal", Sound.ENTITY_SPLASH_POTION_BREAK);
        this.trapSound = readSound("effects.sounds.trap", Sound.BLOCK_CHAIN_PLACE);
        this.blockSound = readSound("effects.sounds.block", Sound.ITEM_SHIELD_BLOCK);

        this.shardName = config.getString("shardName", "shard");
        String serializedShard = getStringAny(config, "shard.serializedItem", "shard.serialized-item");
        this.shardItem = serializedShard == null ? null : ItemSerializer.deserializeItem(serializedShard);

        this.fillerItems = loadFillerItems();
        this.perkInfoItem = MenuItem.fromSection(config.getConfigurationSection("perkInfoItem"), Material.BOOK, "&fTwoje perki");
        this.rollItem = MenuItem.fromSection(config.getConfigurationSection("rollItem"), Material.NETHER_STAR, "&eWylosuj perk");
        this.turboDropItem = MenuItem.fromSection(config.getConfigurationSection("turboDropItem"), Material.GOLDEN_PICKAXE, "&6Turbo drop");
        this.turboRankingItem = MenuItem.fromSection(config.getConfigurationSection("turboRankingItem"), Material.GOLDEN_SWORD, "&bTurbo ranking");
        this.mysticKeyEnabled = config.getBoolean("mysticKey.enabled", true);
        this.mysticKeySlot = config.getInt("mysticKey.slot", 13);
        this.mysticKeyCost = Math.max(0.0D, config.getDouble("mysticKey.cost", 330.0D));
        this.mysticKeyPickupPackage = config.getString("mysticKey.pickupPackage", "mistyczny");
        this.mysticKeyItem = MenuItem.fromSection(config.getConfigurationSection("mysticKey.item"), Material.TRIPWIRE_HOOK, "&d&lMistyczny klucz");
        this.definitions = loadDefinitions();
    }

    private void ensureMysticKeyConfigDefaults() {
        boolean changed = false;
        changed |= setDefault("mysticKey.enabled", true);
        changed |= setDefault("mysticKey.slot", 13);
        changed |= setDefault("mysticKey.cost", 330);
        changed |= setDefault("mysticKey.pickupPackage", "mistyczny");
        changed |= setDefault("mysticKey.item.material", "TRIPWIRE_HOOK");
        changed |= setDefault("mysticKey.item.displayName", "&d&lMistyczny klucz");
        changed |= setDefault("mysticKey.item.lore", List.of(
            "&8✕ &7Do zakupu potrzebne:",
            "&8- &dx{COST} Smoczych Odłamków",
            "",
            "&aKliknij, aby wytworzyć!"
        ));

        if (changed) {
            try {
                config.save(configFile);
            } catch (IOException exception) {
                plugin.getLogger().warning("[Szaman] Nie mozna zapisac domyslnego mysticKey do configu: " + exception.getMessage());
            }
        }
    }

    private boolean setDefault(String path, Object value) {
        if (config.contains(path)) {
            return false;
        }
        config.set(path, value);
        return true;
    }

    public void openGui(Player player) {
        loadPlayer(player.getUniqueId()).thenAccept(data ->
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                new PerksGui(this, player, data).open();
            })
        );
    }

    public CompletableFuture<PerkPlayerData> loadPlayer(UUID uuid) {
        return dataManager.load(uuid).thenApply(data -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                Bukkit.getScheduler().runTask(plugin, () -> applyLiveHealth(player));
            }
            return data;
        });
    }

    public void unloadPlayer(UUID uuid) {
        dataManager.unload(uuid);
        pendingActions.remove(uuid);
    }

    public PerkPlayerData getCachedData(UUID uuid) {
        return dataManager.getCached(uuid);
    }

    public double getTurboDropBonus(UUID uuid) {
        return effectiveMultiplier(dataManager.getCached(uuid).getTurboDropBonus());
    }

    public double getTurboRankingBonus(UUID uuid) {
        return effectiveMultiplier(dataManager.getCached(uuid).getTurboRankingBonus());
    }

    public int getLevel(UUID uuid, PerkType type) {
        PerkDefinition definition = definitions.get(type);
        if (definition != null && !definition.isEnabled()) {
            return 0;
        }
        return dataManager.getCached(uuid).getLevel(type);
    }

    public double getPerkValue(UUID uuid, PerkType type) {
        PerkDefinition definition = definitions.get(type);
        if (definition == null || !definition.isEnabled()) {
            return 0.0D;
        }
        return definition.getValue(getLevel(uuid, type));
    }

    public double getLifestealHealAmount() {
        return lifestealHealAmount;
    }

    public int getSlownessDurationTicks() {
        return slownessDurationTicks;
    }

    public int getSlownessAmplifier() {
        return slownessAmplifier;
    }

    public Sound getLifestealSound() { return lifestealSound; }
    public Sound getTrapSound() { return trapSound; }
    public Sound getBlockSound() { return blockSound; }

    public double getTrapChancePercent() {
        return trapChancePercent;
    }

    public int getTrapDurationTicks(UUID uuid) {
        if (!trapAttributeAsDuration) {
            return trapDefaultDurationTicks;
        }

        double seconds = getPerkValue(uuid, PerkType.TRAP);
        if (seconds <= 0.0D) {
            return trapDefaultDurationTicks;
        }
        return Math.max(1, (int) Math.round(seconds * 20.0D));
    }

    public void rollPerk(Player player) {
        if (!tryStartAction(player)) {
            return;
        }

        boolean animationStarted = false;
        try {
            if (!hasEmptyStorageSlot(player)) {
                playSound(player, rollSoundFail);
                send(player, config.getString("messages.inventory-full", "&#FF0000✘ &8Ξ &cMusisz miec wolne miejsce w ekwipunku, aby losowac perki."));
                return;
            }

            if (!consumeShards(player, rollCost)) {
                playSound(player, rollSoundFail);
                return;
            }

            PerkDefinition definition = randomDefinition();
            if (definition == null) {
                send(player, "&#FF0000✘ &8Ξ &cBrak skonfigurowanych perkow.");
                return;
            }

            if (rollAnimationEnabled) {
                animationStarted = true;
                startRollAnimation(player, definition);
                return;
            }

            finishRoll(player, definition);
        } finally {
            if (!animationStarted) {
                finishAction(player);
            }
        }
    }

    public void craftMysticKey(Player player) {
        if (!tryStartAction(player)) {
            return;
        }

        try {
            if (!mysticKeyEnabled) {
                return;
            }

            dev.grzybalson.managers.PickupManager pickupManager;
            try {
                pickupManager = plugin.getPickupManager();
            } catch (IllegalStateException exception) {
                pickupManager = null;
            }
            if (pickupManager == null) {
                send(player, config.getString("messages.mystic-key-pickup-disabled", "&#FF0000✘ &8Ξ &cSystem /odbierz jest wylaczony."));
                return;
            }

            String packageName = mysticKeyPickupPackage == null || mysticKeyPickupPackage.isBlank() ? "mistyczny" : mysticKeyPickupPackage;
            if (!pickupManager.packageExists(packageName)) {
                send(player, config.getString("messages.mystic-key-package-missing", "&#FF0000✘ &8Ξ &cNie znaleziono przedmiotów /odbierz &f{PACKAGE}&c.")
                    .replace("{PACKAGE}", packageName));
                return;
            }

            if (!consumeShards(player, mysticKeyCost)) {
                playSound(player, rollSoundFail);
                return;
            }

            pickupManager.givePackage(player.getUniqueId(), packageName, 1);
            playSound(player, rollSoundFinish);
            send(player, config.getString("messages.mystic-key-crafted", "&#00FF3B✔ &8Ξ &#4EFA76Wytworzono &dMistyczny klucz&a. Odbierz go pod &f/odbierz&a.")
                .replace("{PACKAGE}", packageName)
                .replace("{COST}", formatCost(mysticKeyCost)));
        } finally {
            finishAction(player);
        }
    }

    public void rollTurbo(Player player, boolean drop) {
        if (!tryStartAction(player)) {
            return;
        }

        PerkPlayerData data = dataManager.getCached(player.getUniqueId());
        double currentMultiplier = drop ? effectiveMultiplier(data.getTurboDropBonus()) : effectiveMultiplier(data.getTurboRankingBonus());
        double maxMultiplier = drop ? maxTurboDropBonus : maxTurboRankingBonus;

        if (currentMultiplier >= maxMultiplier) {
            playSound(player, rollSoundFail);
            String message = drop
                ? config.getString("messages.turbo-drop-max", "&#FF0000✘ &8Ξ &cZwiekszony drop ma juz maksymalny mnoznik &fx{MAX}&c.")
                : config.getString("messages.turbo-ranking-max", "&#FF0000✘ &8Ξ &cZwiekszony ranking ma juz maksymalny mnoznik &fx{MAX}&c.");
            send(player, message.replace("{MAX}", formatMultiplierValue(maxMultiplier)));
            finishAction(player);
            return;
        }

        double cost = drop ? turboDropCost : turboRankingCost;
        if (!consumeShards(player, cost)) {
            playSound(player, rollSoundFail);
            finishAction(player);
            return;
        }

        double min = drop ? dropMinPercent : rankingMinPercent;
        double max = drop ? dropMaxPercent : rankingMaxPercent;
        if (max < min) {
            double tmp = min;
            min = max;
            max = tmp;
        }
        double rolled = roundMultiplier(min + (random.nextDouble() * (max - min)));
        double newMultiplier = Math.min(maxMultiplier, roundMultiplier(currentMultiplier + rolled));

        if (drop) {
            data.setTurboDropBonus(newMultiplier);
        } else {
            data.setTurboRankingBonus(newMultiplier);
        }

        dataManager.save(data).thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> {
            finishAction(player);
            if (!player.isOnline()) {
                return;
            }
            String message = drop
                ? config.getString("messages.turbo-drop-success", "&#00FF3B✔ &8Ξ &#4EFA76Dodano &fx{BONUS} &ado mnoznika dropu. &7Aktualnie: &fx{TOTAL}")
                : config.getString("messages.turbo-ranking-success", "&#00FF3B✔ &8Ξ &#4EFA76Dodano &fx{BONUS} &ado mnoznika rankingu. &7Aktualnie: &fx{TOTAL}");
            playSound(player, rollSoundFinish);
            send(player, message
                .replace("{BONUS}", formatMultiplierValue(rolled))
                .replace("{TOTAL}", formatMultiplierValue(newMultiplier)));
            openGui(player);
        })).exceptionally(throwable -> {
            plugin.getLogger().warning("Nie udalo sie zapisac turbo perkow: " + throwable.getMessage());
            Bukkit.getScheduler().runTask(plugin, () -> finishAction(player));
            return null;
        });
    }

    public void upgradeFromItem(Player player, ItemStack clickedItem) {
        PerkDefinition definition = identifyPerkItem(clickedItem);
        if (definition == null) {
            return;
        }

        if (!tryStartAction(player)) {
            return;
        }

        loadPlayer(player.getUniqueId()).thenAccept(data ->
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    finishAction(player);
                    return;
                }
                upgradeFromItem(player, definition, data);
            })
        ).exceptionally(throwable -> {
            plugin.getLogger().warning("Nie udalo sie wczytac perkow gracza " + player.getName() + ": " + throwable.getMessage());
            Bukkit.getScheduler().runTask(plugin, () -> finishAction(player));
            return null;
        });
    }

    private void upgradeFromItem(Player player, PerkDefinition definition, PerkPlayerData data) {
        int currentLevel = data.getLevel(definition.getType());
        int nextLevel = currentLevel + 1;
        int maxLevel = definition.getMaxLevel();

        if (currentLevel >= maxLevel) {
            send(player, config.getString("messages.max-level", "&#FF0000✘ &8Ξ &cTen perk ma juz maksymalny poziom."));
            finishAction(player);
            return;
        }

        int required = getUpgradeCost(definition, nextLevel);
        int available = countShardItems(player.getInventory());
        if (available < required) {
            String message = config.getString(
                "messages.not-enough-items",
                "&#FF0000✘ &8Ξ &cAby ulepszyc &f{PERK} &cna poziom &f{LEVEL}&c, musisz miec &f{AMOUNT} &cszt. tego itemu. Masz &f{HAVE}&c."
            );
            if (!message.contains("{PERK}") && !message.contains("{LEVEL}") && !message.contains("{HAVE}")) {
                message = "&#FF0000✘ &8Ξ &cAby ulepszyc &f{PERK} &cna poziom &f{LEVEL}&c, musisz miec &f{AMOUNT} &cszt. tego itemu. Masz &f{HAVE}&c.";
            }
            send(player, message
                .replace("{AMOUNT}", String.valueOf(required))
                .replace("{HAVE}", String.valueOf(available))
                .replace("{PERK}", definition.getDisplayName())
                .replace("{LEVEL}", String.valueOf(nextLevel)));
            finishAction(player);
            return;
        }

        consumeShardItems(player.getInventory(), required);
        data.setLevel(definition.getType(), nextLevel);
        dataManager.save(data).thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> {
            finishAction(player);
            if (!player.isOnline()) {
                return;
            }
            if (definition.getType() == PerkType.LIVE) {
                applyLiveHealth(player);
            }
            send(player, config.getString("messages.upgrade-success", "&#00FF3B✔ &8Ξ &#4EFA76Ulepszono &f{PERK} &ana poziom &e{LEVEL}&a.")
                .replace("{PERK}", definition.getDisplayName())
                .replace("{LEVEL}", String.valueOf(nextLevel)));
            player.closeInventory();
        })).exceptionally(throwable -> {
            plugin.getLogger().warning("Nie udalo sie zapisac perkow gracza " + player.getName() + ": " + throwable.getMessage());
            Bukkit.getScheduler().runTask(plugin, () -> finishAction(player));
            return null;
        });
    }

    public void setShardItem(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            send(player, config.getString("messages.no-item-in-hand", "&#FF0000✘ &8Ξ &cTrzymaj item w rece."));
            return;
        }

        applyShardItem(hand);
        send(player, config.getString("messages.shard-set", "&#00FF3B✔ &8Ξ &#4EFA76Ustawiono odlamkowy item: &f{ITEM}&a.")
            .replace("{ITEM}", shardName));
    }

    public void applyShardItem(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return;
        }

        ItemStack stored = item.clone();
        stored.setAmount(1);
        String serialized = ItemSerializer.serializeItem(stored);
        if (serialized == null) {
            return;
        }

        this.shardItem = stored;
        this.shardName = readableItemName(stored);
        config.set("shard.serializedItem", serialized);
        config.set("shardName", shardName);
        saveConfig();
    }

    public void applyShardItemIfAbsent(ItemStack item) {
        if (this.shardItem != null) {
            return;
        }
        applyShardItem(item);
    }

    public void setPerkItem(Player player, PerkType type) {
        if (type == null || !definitions.containsKey(type)) {
            send(player, "&#FF0000✘ &8Ξ &cNieprawidlowy typ perku.");
            return;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            send(player, config.getString("messages.no-item-in-hand", "&#FF0000✘ &8Ξ &cTrzymaj item w rece."));
            return;
        }

        ItemStack stored = hand.clone();
        stored.setAmount(1);
        NBTUtils.setString(stored, PERK_ITEM_KEY, type.name());

        String serialized = ItemSerializer.serializeItem(stored);
        if (serialized == null) {
            send(player, "&#FF0000✘ &8Ξ &cNie udalo sie zapisac itemu perku.");
            return;
        }

        if (!setSerializedPerkItem(type, serialized)) {
            send(player, "&#FF0000✘ &8Ξ &cNie znaleziono tego perku w configu.");
            return;
        }

        saveConfig();
        reload();
        send(player, config.getString("messages.perk-set", "&#00FF3B✔ &8Ξ &#4EFA76Ustawiono item perku &f{PERK}&a.")
            .replace("{PERK}", type.name()));
    }

    public CompletableFuture<Void> giveAllPerks(UUID uuid) {
        return dataManager.load(uuid).thenCompose(data -> {
            for (PerkDefinition definition : definitions.values()) {
                if (!definition.isEnabled()) continue;
                data.setLevel(definition.getType(), definition.getMaxLevel());
            }
            return dataManager.save(data);
        }).thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                applyLiveHealth(player);
            }
        }));
    }

    public CompletableFuture<Void> resetPerks(UUID uuid) {
        return dataManager.load(uuid).thenCompose(data -> {
            for (PerkType type : PerkType.values()) {
                data.setLevel(type, 0);
            }
            data.setTurboDropBonus(0.0D);
            data.setTurboRankingBonus(0.0D);
            return dataManager.save(data);
        }).thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                applyLiveHealth(player);
            }
        }));
    }

    public void giveShard(Player target, int amount) {
        int remaining = Math.max(1, amount);
        while (remaining > 0) {
            ItemStack item = createShardItem();
            int stackAmount = Math.min(remaining, item.getMaxStackSize());
            item.setAmount(stackAmount);
            giveItem(target, item);
            remaining -= stackAmount;
        }
    }

    public List<String> getPerkTypeNames() {
        return definitions.values().stream()
            .filter(PerkDefinition::isEnabled)
            .map(def -> def.getType().name().toLowerCase(Locale.ROOT))
            .sorted()
            .toList();
    }

    public PerkType resolvePerkType(String raw) {
        PerkType type = PerkType.fromString(raw);
        if (type == null) return null;
        PerkDefinition def = definitions.get(type);
        return def != null && def.isEnabled() ? type : null;
    }

    public boolean matchesShard(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }

        if (shardItem != null) {
            ItemStack checked = item.clone();
            ItemStack expected = shardItem.clone();
            checked.setAmount(1);
            expected.setAmount(1);
            if (checked.isSimilar(expected)) {
                return true;
            }
        }

        String expectedName = normalizeName(shardName);
        if (!expectedName.isBlank() && normalizeName(readableItemName(item)).equals(expectedName)) {
            return true;
        }

        return false;
    }

    public Inventory buildInventory(PerksGui holder, Player player, PerkPlayerData data) {
        Inventory inventory = Bukkit.createInventory(holder, inventoryRows * 9, ColorUtils.colorizeComponent(inventoryTitle));
        Map<String, String> basePlaceholders = new HashMap<>();
        basePlaceholders.put("{COST}", formatCost(rollCost));
        basePlaceholders.put("{DROP_COST}", formatCost(turboDropCost));
        basePlaceholders.put("{RANKING_COST}", formatCost(turboRankingCost));
        basePlaceholders.put("{DROP_BONUS}", formatMultiplier(data.getTurboDropBonus()));
        basePlaceholders.put("{RANKING_BONUS}", formatMultiplier(data.getTurboRankingBonus()));
        basePlaceholders.put("{MYSTIC_KEY_COST}", formatCost(mysticKeyCost));
        basePlaceholders.put("{BONUS}", "0");

        plugin.getGuiFactory().applyDefaultTemplate(inventory);

        setIfValid(inventory, perkInfoSlot, perkInfoItem.build(basePlaceholders, buildRunes(data)));

        if (mysticKeyEnabled) {
            Map<String, String> keyPlaceholders = new HashMap<>(basePlaceholders);
            keyPlaceholders.put("{COST}", formatCost(mysticKeyCost));
            keyPlaceholders.put("{PACKAGE}", mysticKeyPickupPackage == null ? "" : mysticKeyPickupPackage);
            setIfValid(inventory, mysticKeySlot, NBTUtils.setString(mysticKeyItem.build(keyPlaceholders, List.of()), "perks_action", "mystic_key"));
        }

        Map<String, String> rollPlaceholders = new HashMap<>(basePlaceholders);
        rollPlaceholders.put("{COST}", formatCost(rollCost));
        setIfValid(inventory, rollSlot, NBTUtils.setString(rollItem.build(rollPlaceholders, List.of()), "perks_action", "roll"));

        Map<String, String> dropPlaceholders = new HashMap<>(basePlaceholders);
        dropPlaceholders.put("{COST}", formatCost(turboDropCost));
        dropPlaceholders.put("{BONUS}", formatMultiplier(data.getTurboDropBonus()));
        setIfValid(inventory, turboDropSlot, NBTUtils.setString(turboDropItem.build(dropPlaceholders, List.of()), "perks_action", "turbo_drop"));

        Map<String, String> rankingPlaceholders = new HashMap<>(basePlaceholders);
        rankingPlaceholders.put("{COST}", formatCost(turboRankingCost));
        rankingPlaceholders.put("{BONUS}", formatMultiplier(data.getTurboRankingBonus()));
        setIfValid(inventory, turboRankingSlot, NBTUtils.setString(turboRankingItem.build(rankingPlaceholders, List.of()), "perks_action", "turbo_ranking"));

        for (PerkDefinition definition : definitions.values()) {
            if (!definition.isEnabled()) continue;
            int slot = config.getInt("perkSlots." + definition.getType().name().toLowerCase(Locale.ROOT), defaultPerkSlot(definition.getType()));
            if (slot < 0) continue;
            ItemStack item = buildGuiPerkItem(definition, data);
            setIfValid(inventory, slot, item);
        }

        return inventory;
    }

    public String getAction(ItemStack item) {
        return NBTUtils.getString(item, "perks_action");
    }

    private int defaultPerkSlot(PerkType type) {
        return switch (type) {
            case STRENGTH -> 20;
            case DROP -> 21;
            case LIVE -> 22;
            case LIFESTEAL -> 23;
            case RANKING -> 24;
            case TRAP -> 30;
            case BLOCK -> 32;
            case SLOWNESS -> -1;
        };
    }

    private ItemStack buildGuiPerkItem(PerkDefinition definition, PerkPlayerData data) {
        ItemStack item = createPerkItem(definition);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            int level = data.getLevel(definition.getType());
            ConfigurationSection rockSection = getRockSection(definition.getType());
            ConfigurationSection guiSection = rockSection == null ? null : rockSection.getConfigurationSection("gui");

            String guiName = guiSection == null
                ? definition.getDisplayName()
                : getStringAny(guiSection, "displayName", "display-name", "name");
            if (guiName == null || guiName.isBlank()) {
                guiName = definition.getDisplayName();
            }
            meta.displayName(ColorUtils.colorizeComponent(guiName).decoration(TextDecoration.ITALIC, false));

            Map<String, String> placeholders = buildPerkPlaceholders(definition, level);
            List<String> rawLore = guiSection == null ? List.of() : guiSection.getStringList("lore");
            if (rawLore.isEmpty()) {
                rawLore = defaultGuiLore();
            }

            List<String> levelLines = buildLevelLines(definition, level, guiSection);
            List<String> statusLines = buildStatusLines(definition, level, guiSection);
            List<Component> lore = new ArrayList<>();
            for (String rawLine : rawLore) {
                if (rawLine.contains("{LEVEL_LINES}")) {
                    String prefix = rawLine.replace("{LEVEL_LINES}", "");
                    for (String levelLine : levelLines) {
                        ColorUtils.appendLore(lore, replacePerkPlaceholders(prefix + levelLine, placeholders));
                    }
                    continue;
                }
                if (rawLine.contains("{STATUS_LINES}")) {
                    String prefix = rawLine.replace("{STATUS_LINES}", "");
                    for (String statusLine : statusLines) {
                        ColorUtils.appendLore(lore, replacePerkPlaceholders(prefix + statusLine, placeholders));
                    }
                    continue;
                }
                ColorUtils.appendLore(lore, replacePerkPlaceholders(rawLine, placeholders));
            }
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        NBTUtils.setString(item, PERK_ITEM_KEY, definition.getType().name());
        return item;
    }

    private String roman(int level) {
        return switch (level) { case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; case 4 -> "IV"; case 5 -> "V"; default -> String.valueOf(level); };
    }

    private ConfigurationSection getRockSection(PerkType type) {
        if (type == null) {
            return null;
        }
        for (Map<?, ?> rawRock : config.getMapList("rocks")) {
            ConfigurationSection section = mapToSection(rawRock);
            if (section == null) {
                continue;
            }
            PerkType sectionType = PerkType.fromString(section.getString("type"));
            if (sectionType == type) {
                return section;
            }
        }
        return null;
    }

    private Map<String, String> buildPerkPlaceholders(PerkDefinition definition, int level) {
        Map<String, String> placeholders = new HashMap<>();
        int nextLevel = Math.min(definition.getMaxLevel(), level + 1);
        placeholders.put("{TYPE}", definition.getType().name());
        placeholders.put("{DISPLAYNAME}", definition.getDisplayName());
        placeholders.put("{LEVEL}", String.valueOf(level));
        placeholders.put("{MAX}", String.valueOf(definition.getMaxLevel()));
        placeholders.put("{NEXT_LEVEL}", String.valueOf(nextLevel));
        placeholders.put("{NEXT_ROMAN}", roman(nextLevel));
        placeholders.put("{COST}", String.valueOf(getUpgradeCost(definition, Math.max(1, level + 1))));
        placeholders.put("{VALUE}", getGuiValueText(definition, Math.max(1, level)));
        for (int i = 1; i <= definition.getMaxLevel(); i++) {
            placeholders.put("{VALUE_" + i + "}", getGuiValueText(definition, i));
            placeholders.put("{ROMAN_" + i + "}", roman(i));
        }
        return placeholders;
    }

    private List<String> defaultGuiLore() {
        return List.of(
            "&8✕ &fAktualnie poziom: &a{LEVEL}",
            "",
            "&8✕ &fPoziomy:",
            "{LEVEL_LINES}",
            "",
            "&eKliknij, aby zakupić",
            "",
            "{STATUS_LINES}"
        );
    }

    private List<String> buildLevelLines(PerkDefinition definition, int level, ConfigurationSection guiSection) {
        String format = guiSection == null ? null : guiSection.getString("levelLineFormat");
        if (format == null || format.isBlank()) {
            format = "&8✕ {COLOR}{ROMAN} &8- {COLOR}{VALUE}";
        }
        String ownedColor = guiSection == null ? "&a" : guiSection.getString("ownedColor", "&a");
        String lockedColor = guiSection == null ? "&c" : guiSection.getString("lockedColor", "&c");
        List<String> lines = new ArrayList<>();
        for (int i = 1; i <= definition.getMaxLevel(); i++) {
            String color = i <= level ? ownedColor : lockedColor;
            lines.add(format
                .replace("{COLOR}", color)
                .replace("{ROMAN}", roman(i))
                .replace("{LEVEL_INDEX}", String.valueOf(i))
                .replace("{VALUE}", getGuiValueText(definition, i)));
        }
        return lines;
    }

    private List<String> buildStatusLines(PerkDefinition definition, int level, ConfigurationSection guiSection) {
        if (level >= definition.getMaxLevel()) {
            List<String> maxed = guiSection == null ? List.of() : guiSection.getStringList("maxedLines");
            return maxed.isEmpty() ? List.of("&cPosiadasz maksymalny poziom!") : maxed;
        }
        List<String> upgrade = guiSection == null ? List.of() : guiSection.getStringList("upgradeLines");
        return upgrade.isEmpty()
            ? List.of("&8✕ &7Koszt: &d{COST}x Smoczych Odłamków", "&aKliknij aby ulepszyć")
            : upgrade;
    }

    private String getGuiValueText(PerkDefinition definition, int level) {
        ConfigurationSection rockSection = getRockSection(definition.getType());
        ConfigurationSection guiSection = rockSection == null ? null : rockSection.getConfigurationSection("gui");
        if (guiSection != null) {
            String value = guiSection.getString("levelTexts." + level);
            if (value != null) {
                return value;
            }
        }
        return formatPerkValue(definition, level);
    }

    private String replacePerkPlaceholders(String text, Map<String, String> placeholders) {
        String result = text == null ? "" : text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }

    private int getUpgradeCost(PerkDefinition definition, int nextLevel) {
        int level = Math.max(1, nextLevel);
        ConfigurationSection rockSection = getRockSection(definition.getType());
        if (rockSection != null) {
            if (rockSection.contains("upgradeCostByLevel." + level)) {
                return Math.max(1, rockSection.getInt("upgradeCostByLevel." + level));
            }
            if (rockSection.contains("upgrade-cost-by-level." + level)) {
                return Math.max(1, rockSection.getInt("upgrade-cost-by-level." + level));
            }
            if (rockSection.contains("upgradeCostPerLevel")) {
                return Math.max(1, (int) Math.ceil(rockSection.getDouble("upgradeCostPerLevel") * level));
            }
        }
        if (config.contains("upgradeCostByLevel." + level)) {
            return Math.max(1, config.getInt("upgradeCostByLevel." + level));
        }
        double perLevel = Math.max(1.0D, config.getDouble("upgradeCostPerLevel", 100.0D));
        return Math.max(1, (int) Math.ceil(perLevel * level));
    }


    private void startRollAnimation(Player player, PerkDefinition reward) {
        List<PerkDefinition> values = definitions.values().stream()
            .filter(PerkDefinition::isEnabled)
            .toList();
        if (values.isEmpty()) {
            finishRoll(player, reward);
            finishAction(player);
            return;
        }

        PerksRollGui holder = new PerksRollGui(player.getUniqueId(), reward);
        Inventory inventory = Bukkit.createInventory(holder, 27, ColorUtils.colorizeComponent(rollAnimationTitle));
        holder.setInventory(inventory);

        ItemStack filler = rollAnimationFillerItem.build(Map.of(), List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (!rollAnimationSlots.contains(slot)) {
                inventory.setItem(slot, filler);
            }
        }

        player.openInventory(inventory);

        int centerIndex = Math.max(0, rollAnimationSlots.indexOf(rollAnimationCenterSlot));
        List<PerkDefinition> rollStrip = buildRollStrip(values, reward, centerIndex);

        new BukkitRunnable() {
            private int step;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    finishAction(player);
                    cancel();
                    return;
                }

                if (holder.isFinished()) {
                    cancel();
                    return;
                }

                renderRollFrame(inventory, rollStrip, step);
                playSound(player, rollSoundTick);
                if (step >= rollAnimationSteps) {
                    finishRollFromAnimation(player, holder);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (player.isOnline() && player.getOpenInventory().getTopInventory().getHolder() == holder) {
                            player.closeInventory();
                        }
                    }, 20L);
                    cancel();
                    return;
                }

                step++;
            }
        }.runTaskTimer(plugin, 0L, rollAnimationPeriodTicks);
    }

    private List<PerkDefinition> buildRollStrip(List<PerkDefinition> values, PerkDefinition reward, int centerIndex) {
        int size = rollAnimationSteps + rollAnimationSlots.size() + 1;
        List<PerkDefinition> strip = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            strip.add(values.get(random.nextInt(values.size())));
        }

        int rewardIndex = Math.min(size - 1, rollAnimationSteps + centerIndex);
        strip.set(rewardIndex, reward);
        return strip;
    }

    private void renderRollFrame(Inventory inventory, List<PerkDefinition> rollStrip, int step) {
        for (int i = 0; i < rollAnimationSlots.size(); i++) {
            int slot = rollAnimationSlots.get(i);
            PerkDefinition definition = rollStrip.get(Math.min(step + i, rollStrip.size() - 1));
            setIfValid(inventory, slot, createPerkItem(definition));
        }
    }

    public void finishRollImmediately(Player player, PerksRollGui holder) {
        if (holder == null || !player.getUniqueId().equals(holder.getPlayerUuid())) {
            return;
        }
        finishRollFromAnimation(player, holder);
    }

    private void finishRollFromAnimation(Player player, PerksRollGui holder) {
        if (holder == null || !holder.markFinished()) {
            return;
        }
        try {
            finishRoll(player, holder.getReward());
        } finally {
            finishAction(player);
        }
    }

    private void finishRoll(Player player, PerkDefinition definition) {
        giveItem(player, createPerkItem(definition));
        playSound(player, rollSoundFinish);
        send(player, config.getString("messages.roll-success", "&#00FF3B✔ &8Ξ &#4EFA76Wylosowano &f{PERK}&a!")
            .replace("{PERK}", definition.getDisplayName()));
    }

    public void applyLiveHealth(Player player) {
        AttributeInstance maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealth == null) {
            return;
        }

        removeLiveModifier(maxHealth);

        double value = getPerkValue(player.getUniqueId(), PerkType.LIVE);
        if (value <= 0.0D) {
            if (player.getHealth() > maxHealth.getValue()) {
                player.setHealth(Math.max(1.0D, maxHealth.getValue()));
            }
            return;
        }

        double healthAmount = liveValuesAreHalfHearts ? value : (liveValuesAreHearts ? value * 2.0D : value);
        AttributeModifier modifier = new AttributeModifier(
            LIVE_HEALTH_MODIFIER_ID,
            "holly_perks_live",
            healthAmount,
            AttributeModifier.Operation.ADD_NUMBER
        );
        maxHealth.addModifier(modifier);
    }

    public void removeLiveHealth(Player player) {
        AttributeInstance maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealth == null) {
            return;
        }
        removeLiveModifier(maxHealth);
    }

    public PerkDefinition identifyPerkItem(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }

        String typeTag = NBTUtils.getString(item, PERK_ITEM_KEY);
        PerkType type = PerkType.fromString(typeTag);
        if (type != null && definitions.containsKey(type)) {
            PerkDefinition def = definitions.get(type);
            if (def != null && def.isEnabled()) {
                return def;
            }
        }

        String itemName = normalizeName(readableItemName(item));
        if (itemName.isBlank()) {
            return null;
        }

        for (PerkDefinition definition : definitions.values()) {
            if (!definition.isEnabled()) {
                continue;
            }
            ItemStack definedItem = definition.getItem();
            if (definedItem == null) {
                continue;
            }
            if (normalizeName(readableItemName(definedItem)).equals(itemName)) {
                return definition;
            }
        }
        return null;
    }

    private void removeLiveModifier(AttributeInstance maxHealth) {
        maxHealth.getModifiers().stream()
            .filter(modifier -> LIVE_HEALTH_MODIFIER_ID.equals(modifier.getUniqueId()))
            .findFirst()
            .ifPresent(maxHealth::removeModifier);
    }

    private CompletableFuture<Boolean> charge(Player player, double amount, String detail) {
        if (amount <= 0.0D) {
            return CompletableFuture.completedFuture(true);
        }

        UUID uuid = player.getUniqueId();
        return plugin.getEconomy().hasBalance(uuid, amount).thenCompose(hasBalance -> {
            if (!hasBalance) {
                Bukkit.getScheduler().runTask(plugin, () -> send(player,
                    config.getString("messages.not-enough-money", "&#FF0000✘ &8Ξ &cNie masz wystarczajaco plomyczkow.")));
                return CompletableFuture.completedFuture(false);
            }
            return plugin.getEconomy().removeBalance(uuid, amount);
        }).exceptionally(throwable -> {
            plugin.getLogger().warning("Nie udalo sie pobrac oplaty za szamana (" + detail + "): " + throwable.getMessage());
            return false;
        });
    }

    private boolean consumeShards(Player player, double rawAmount) {
        int amount = shardCost(rawAmount);
        if (amount <= 0) {
            return true;
        }

        int available = countShardItems(player.getInventory());
        if (available < amount) {
            send(player, config.getString("messages.not-enough-shards", "&#FF0000✘ &8Ξ &cNie masz wystarczajaco odlamkow. Potrzebujesz &f{AMOUNT}&c.")
                .replace("{AMOUNT}", String.valueOf(amount))
                .replace("{HAVE}", String.valueOf(available)));
            return false;
        }

        consumeShardItems(player.getInventory(), amount);
        return true;
    }

    private int shardCost(double rawAmount) {
        if (rawAmount <= 0.0D) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(rawAmount));
    }

    private boolean tryStartAction(Player player) {
        if (!pendingActions.add(player.getUniqueId())) {
            return false;
        }
        return true;
    }

    private void finishAction(Player player) {
        pendingActions.remove(player.getUniqueId());
    }

    private PerkDefinition randomDefinition() {
        List<PerkDefinition> enabledDefs = definitions.values().stream()
            .filter(PerkDefinition::isEnabled)
            .toList();

        if (enabledDefs.isEmpty()) {
            return null;
        }

        double total = enabledDefs.stream()
            .mapToDouble(definition -> Math.max(0.0D, definition.getChance()))
            .sum();

        if (total <= 0.0D) {
            return enabledDefs.get(random.nextInt(enabledDefs.size()));
        }

        double roll = random.nextDouble() * total;
        double current = 0.0D;
        for (PerkDefinition definition : enabledDefs) {
            current += Math.max(0.0D, definition.getChance());
            if (roll <= current) {
                return definition;
            }
        }
        return enabledDefs.get(0);
    }

    private ItemStack createPerkItem(PerkDefinition definition) {
        ItemStack item = definition.getItem();
        if (item == null || item.getType().isAir()) {
            item = new ItemStack(Material.BOOK);
        }
        item.setAmount(1);
        NBTUtils.setString(item, PERK_ITEM_KEY, definition.getType().name());
        return item;
    }

    private int countShardItems(PlayerInventory inventory) {
        int count = 0;
        for (ItemStack item : inventory.getContents()) {
            if (matchesShard(item)) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private void consumeShardItems(PlayerInventory inventory, int amount) {
        int remaining = amount;
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (!matchesShard(item)) {
                continue;
            }

            int remove = Math.min(remaining, item.getAmount());
            item.setAmount(item.getAmount() - remove);
            remaining -= remove;
            if (item.getAmount() <= 0) {
                inventory.setItem(i, null);
            } else {
                inventory.setItem(i, item);
            }
        }
    }

    private int countPerkItems(PlayerInventory inventory, PerkDefinition definition) {
        int count = 0;
        for (ItemStack item : inventory.getContents()) {
            if (matchesPerkItem(item, definition)) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private void consumePerkItems(PlayerInventory inventory, PerkDefinition definition, int amount) {
        int remaining = amount;
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (!matchesPerkItem(item, definition)) {
                continue;
            }

            int remove = Math.min(remaining, item.getAmount());
            item.setAmount(item.getAmount() - remove);
            remaining -= remove;
            if (item.getAmount() <= 0) {
                inventory.setItem(i, null);
            } else {
                inventory.setItem(i, item);
            }
        }
    }

    private boolean matchesPerkItem(ItemStack item, PerkDefinition definition) {
        if (item == null || item.getType().isAir() || definition == null) {
            return false;
        }

        PerkDefinition identified = identifyPerkItem(item);
        return identified != null && identified.getType() == definition.getType();
    }


    private boolean hasEmptyStorageSlot(Player player) {
        for (int slot = 0; slot < 36; slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (item == null || item.getType().isAir()) {
                return true;
            }
        }
        return false;
    }

    private void playSound(Player player, Sound sound) {
        if (player == null || sound == null || !player.isOnline()) {
            return;
        }
        player.playSound(player.getLocation(), sound, 0.7F, 1.0F);
    }

    private Sound readSound(String path, Sound fallback) {
        String raw = config.getString(path);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Sound.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private void giveItem(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        for (ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    private ItemStack createShardItem() {
        if (shardItem != null) {
            return shardItem.clone();
        }

        ItemStack item = new ItemStack(Material.PRISMARINE_SHARD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(ColorUtils.colorizeComponent(shardName == null || shardName.isBlank() ? "shard" : shardName));
            item.setItemMeta(meta);
        }
        return item;
    }

    private List<String> buildRunes(PerkPlayerData data) {
        List<String> lines = new ArrayList<>();
        definitions.values().stream()
            .filter(PerkDefinition::isEnabled)
            .sorted(Comparator.comparing(definition -> definition.getType().ordinal()))
            .forEach(definition -> {
                int level = data.getLevel(definition.getType());
                lines.add(upgradeLineFormat
                    .replace("{NAME}", definition.getDisplayName())
                    .replace("{TYPE}", definition.getType().name())
                    .replace("{LEVEL}", String.valueOf(level))
                    .replace("{MAX}", String.valueOf(definition.getMaxLevel()))
                    .replace("{VALUE}", formatPerkValue(definition, level)));
            });
        return lines;
    }

    private String formatPerkValue(PerkDefinition definition, int level) {
        double value = definition.getValue(level);
        return switch (definition.getType()) {
            case LIVE -> "+" + formatPercent(liveValuesAreHalfHearts ? value / 2.0D : value) + (liveValuesAreHearts || liveValuesAreHalfHearts ? " serc" : " hp");
            case STRENGTH -> formatPercent(value) + "% szans na +0.5❤";
            case DROP -> "x" + formatPercent(value) + " większy drop";
            case RANKING -> "+" + formatPercent(value) + "% rankingu";
            case BLOCK -> formatPercent(value) + "% szans na blokadę uderzenia";
            case SLOWNESS, LIFESTEAL -> formatPercent(value) + "%";
            case TRAP -> trapAttributeAsDuration ? "Uwięzienie na " + formatPercent(value) + "s" : formatPercent(trapChancePercent) + "%";
        };
    }

    private Map<PerkType, PerkDefinition> loadDefinitions() {
        EnumMap<PerkType, PerkDefinition> loaded = new EnumMap<>(PerkType.class);
        List<Map<?, ?>> rawRocks = config.getMapList("rocks");
        for (Map<?, ?> rawRock : rawRocks) {
            ConfigurationSection section = mapToSection(rawRock);
            if (section == null) {
                continue;
            }

            PerkType type = PerkType.fromString(section.getString("type"));
            if (type == null) {
                continue;
            }

            int maxLevel = Math.max(1, section.getInt("maxLevel", globalMaxLevel));
            maxLevel = Math.min(maxLevel, globalMaxLevel);
            String displayName = section.getString("displayName", type.name());
            double chance = Math.max(0.0D, section.getDouble("chance", 1.0D));
            ItemStack item = loadRockItem(section, type, displayName);
            Map<Integer, Double> values = loadAttributes(section, type, maxLevel);
            boolean enabled = section.getBoolean("enabled", true);
            loaded.put(type, new PerkDefinition(type, displayName, maxLevel, chance, item, values, enabled));
        }

        loaded.putIfAbsent(PerkType.LIVE, defaultDefinition(PerkType.LIVE, "&a&lPerk Życia", Material.RED_DYE, 10.0D, 1.0D, 2.0D, 3.0D, 4.0D));
        loaded.putIfAbsent(PerkType.STRENGTH, defaultDefinition(PerkType.STRENGTH, "&c&lPerk Siły", Material.IRON_SWORD, 10.0D, 1.0D, 2.0D, 3.0D, 4.0D));
        loaded.putIfAbsent(PerkType.LIFESTEAL, defaultDefinition(PerkType.LIFESTEAL, "&a&lPerk Wampiryzmu", Material.REDSTONE, 10.0D, 1.5D, 2.5D, 3.75D, 5.0D));
        loaded.putIfAbsent(PerkType.TRAP, defaultDefinition(PerkType.TRAP, "&d&lPerk Uwięzienia", Material.ENDER_PEARL, 10.0D, 2.0D, 3.0D, 4.0D, 5.0D));
        loaded.putIfAbsent(PerkType.DROP, defaultDefinition(PerkType.DROP, "&a&lPerk Dropu", Material.GOLDEN_PICKAXE, 10.0D, 1.5D, 2.0D, 2.5D, 3.0D));
        loaded.putIfAbsent(PerkType.RANKING, defaultDefinition(PerkType.RANKING, "&e&lPerk Rankingu", Material.GOLDEN_SWORD, 10.0D, 1.0D, 2.0D, 3.0D, 5.0D));
        loaded.putIfAbsent(PerkType.BLOCK, defaultDefinition(PerkType.BLOCK, "&b&lPerk Blokady", Material.SHIELD, 10.0D, 1.0D, 2.0D, 3.0D, 5.0D));

        return loaded;
    }

    private ConfigurationSection mapToSection(Map<?, ?> map) {
        YamlConfiguration yaml = new YamlConfiguration();
        copyMap(yaml, map);
        return yaml;
    }

    private boolean setSerializedPerkItem(PerkType type, String serialized) {
        List<Map<?, ?>> rawRocks = config.getMapList("rocks");
        List<Map<String, Object>> updatedRocks = new ArrayList<>();
        boolean changed = false;

        for (Map<?, ?> rawRock : rawRocks) {
            Map<String, Object> rock = toMutableStringMap(rawRock);
            PerkType rockType = PerkType.fromString(String.valueOf(rock.get("type")));
            if (rockType == type) {
                Object rawCraftItem = rock.get("craftItem");
                if (rawCraftItem == null) {
                    rawCraftItem = rock.get("craft-item");
                }
                Map<String, Object> craftItem = rawCraftItem instanceof Map<?, ?> map
                    ? toMutableStringMap(map)
                    : new LinkedHashMap<>();
                craftItem.put("serializedItem", serialized);
                rock.put("craftItem", craftItem);
                rock.remove("craft-item");
                changed = true;
            }
            updatedRocks.add(rock);
        }

        if (changed) {
            config.set("rocks", updatedRocks);
        }
        return changed;
    }

    private Map<String, Object> toMutableStringMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }

            Object value = entry.getValue();
            if (value instanceof Map<?, ?> childMap) {
                value = toMutableStringMap(childMap);
            } else if (value instanceof List<?> list) {
                value = toMutableList(list);
            }

            result.put(String.valueOf(entry.getKey()), value);
        }
        return result;
    }

    private List<Object> toMutableList(List<?> list) {
        List<Object> result = new ArrayList<>();
        for (Object value : list) {
            if (value instanceof Map<?, ?> childMap) {
                result.add(toMutableStringMap(childMap));
            } else if (value instanceof List<?> childList) {
                result.add(toMutableList(childList));
            } else {
                result.add(value);
            }
        }
        return result;
    }

    private void copyMap(ConfigurationSection section, Map<?, ?> map) {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null) {
                String key = String.valueOf(entry.getKey());
                Object value = entry.getValue();
                if (value instanceof Map<?, ?> childMap) {
                    ConfigurationSection childSection = section.createSection(key);
                    copyMap(childSection, childMap);
                } else {
                    section.set(key, value);
                }
            }
        }
    }

    private PerkDefinition defaultDefinition(PerkType type, String name, Material material, double chance, double... values) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(ColorUtils.colorizeComponent(name));
            item.setItemMeta(meta);
        }

        Map<Integer, Double> levels = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i++) {
            levels.put(i + 1, values[i]);
        }
        return new PerkDefinition(type, name, Math.min(globalMaxLevel, values.length), chance, item, levels, true);
    }

    private ItemStack loadRockItem(ConfigurationSection section, PerkType type, String displayName) {
        ConfigurationSection craftItem = section.getConfigurationSection("craftItem");
        if (craftItem == null) {
            craftItem = section.getConfigurationSection("craft-item");
        }

        ItemStack item = null;
        if (craftItem != null) {
            String serialized = getStringAny(craftItem, "serializedItem", "serialized-item");
            if (serialized != null && !serialized.isBlank()) {
                item = ItemSerializer.deserializeItem(serialized);
            }

            if (item == null) {
                item = MenuItem.fromSection(craftItem, Material.BOOK, displayName).build(Map.of(), List.of());
            }
        }

        if (item == null) {
            item = new ItemStack(Material.BOOK);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.displayName(ColorUtils.colorizeComponent(displayName));
                item.setItemMeta(meta);
            }
        }

        item.setAmount(1);
        NBTUtils.setString(item, PERK_ITEM_KEY, type.name());
        return item;
    }

    private Map<Integer, Double> loadAttributes(ConfigurationSection section, PerkType type, int maxLevel) {
        LinkedHashMap<Integer, Double> values = new LinkedHashMap<>();
        ConfigurationSection attributes = section.getConfigurationSection("attributes");
        ConfigurationSection source = attributes == null ? null : attributes.getConfigurationSection(type.name());

        if (source == null && attributes != null) {
            for (String key : attributes.getKeys(false)) {
                ConfigurationSection child = attributes.getConfigurationSection(key);
                if (child != null) {
                    source = child;
                    break;
                }
            }
        }

        if (source != null) {
            ConfigurationSection attributeSource = source;
            attributeSource.getKeys(false).stream()
                .sorted(Comparator.comparingInt(this::parseLevelKey))
                .forEach(key -> {
                    int level = parseLevelKey(key);
                    if (level > 0) {
                        values.put(level, attributeSource.getDouble(key));
                    }
                });
        }

        if (values.isEmpty()) {
            for (int level = 1; level <= maxLevel; level++) {
                values.put(level, defaultValue(type, level));
            }
        }
        return values;
    }

    private double defaultValue(PerkType type, int level) {
        return switch (type) {
            case LIVE -> level;
            case STRENGTH -> 1.0D + (level * 0.05D);
            case SLOWNESS -> level * 3.0D;
            case LIFESTEAL -> level * 1.25D;
            case TRAP -> level + 1.0D;
            case DROP -> 1.0D + (level * 0.5D);
            case RANKING -> level;
            case BLOCK -> level;
        };
    }

    private List<FillerItem> loadFillerItems() {
        List<FillerItem> loaded = new ArrayList<>();
        List<Map<?, ?>> rawFillers = config.getMapList("filler");
        for (Map<?, ?> rawFiller : rawFillers) {
            ConfigurationSection section = mapToSection(rawFiller);

            List<Integer> slots = readSlots(section);
            MenuItem item = MenuItem.fromSection(section.getConfigurationSection("item"), Material.GRAY_STAINED_GLASS_PANE, " ");
            loaded.add(new FillerItem(slots, item));
        }
        return loaded;
    }

    private List<Integer> loadRollAnimationSlots() {
        List<Integer> slots = new ArrayList<>();
        for (int slot : config.getIntegerList("rollAnimation.display-slots")) {
            if (slot >= 0 && slot < 27) {
                slots.add(slot);
            }
        }

        if (slots.isEmpty()) {
            for (int slot = 9; slot <= 17; slot++) {
                slots.add(slot);
            }
        }

        if (!slots.contains(rollAnimationCenterSlot) && rollAnimationCenterSlot >= 0 && rollAnimationCenterSlot < 27) {
            slots.add(rollAnimationCenterSlot);
            slots.sort(Integer::compareTo);
        }
        return List.copyOf(slots);
    }

    private List<Integer> readSlots(ConfigurationSection section) {
        Object raw = section.get("slot");
        List<Integer> slots = new ArrayList<>();
        if (raw instanceof Number number) {
            slots.add(number.intValue());
            return slots;
        }
        if (raw instanceof List<?> list) {
            for (Object value : list) {
                if (value instanceof Number number) {
                    slots.add(number.intValue());
                }
            }
        }
        return slots;
    }

    private void setIfValid(Inventory inventory, int slot, ItemStack item) {
        if (slot < 0 || slot >= inventory.getSize() || item == null) {
            return;
        }
        inventory.setItem(slot, item.clone());
    }

    private void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException exception) {
            plugin.getLogger().warning("Nie udalo sie zapisac configu perkow: " + exception.getMessage());
        }
    }

    private void send(Player player, String message) {
        player.sendMessage(ColorUtils.colorizeComponent(message));
    }

    private String readableItemName(ItemStack item) {
        if (item == null) {
            return "";
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return meta.getDisplayName();
        }
        return item.getType().name();
    }

    private String normalizeName(String raw) {
        String stripped = ColorUtils.stripColors(raw);
        return stripped == null ? "" : stripped.trim().toLowerCase(Locale.ROOT);
    }

    private String formatCost(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return String.format(Locale.US, "%.2f", value);
    }

    private String formatPercent(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return String.format(Locale.US, "%.2f", value);
    }

    private String formatMultiplier(double rawValue) {
        return "x" + formatMultiplierValue(effectiveMultiplier(rawValue));
    }

    private String formatMultiplierValue(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return String.format(Locale.US, "%.2f", value);
    }

    private double effectiveMultiplier(double rawValue) {
        if (rawValue <= 0.0D) {
            return 1.0D;
        }
        if (rawValue < 1.0D) {
            return roundMultiplier(1.0D + rawValue);
        }
        return rawValue;
    }

    private double roundPercent(double value) {
        return Math.round(value * 100.0D) / 100.0D;
    }

    private double roundMultiplier(double value) {
        return Math.round(value * 100.0D) / 100.0D;
    }

    private double sanePercent(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0.0D) {
            return 0.0D;
        }
        return Math.min(100.0D, value);
    }

    private int parseLevelKey(String key) {
        try {
            return Integer.parseInt(key);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String getStringAny(ConfigurationSection section, String... paths) {
        for (String path : paths) {
            String value = section.getString(path);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private record FillerItem(List<Integer> slots, MenuItem item) {}

    private static class MenuItem {
        private final Material material;
        private final int amount;
        private final String displayName;
        private final int customModelData;
        private final List<String> lore;
        private final List<String> itemFlags;

        private MenuItem(Material material, int amount, String displayName, int customModelData, List<String> lore, List<String> itemFlags) {
            this.material = material;
            this.amount = amount;
            this.displayName = displayName;
            this.customModelData = customModelData;
            this.lore = lore;
            this.itemFlags = itemFlags;
        }

        static MenuItem fromSection(ConfigurationSection section, Material fallbackMaterial, String fallbackName) {
            if (section == null) {
                return new MenuItem(fallbackMaterial, 1, fallbackName, 0, List.of(), List.of());
            }

            Material material = Material.matchMaterial(section.getString("material", fallbackMaterial.name()));
            if (material == null) {
                material = fallbackMaterial;
            }

            String displayName = readString(section, fallbackName, "displayName", "displayname", "display-name", "display_name", "name", "display");
            int customModelData = readInt(section, 0, "customModelData", "custommodeldata", "custom-model-data", "custom_model_data");
            int amount = Math.max(1, section.getInt("amount", 1));
            List<String> lore = readStringList(section, "lore", "lores", "description", "opis");
            List<String> itemFlags = section.getStringList("itemFlags");
            if (itemFlags.isEmpty()) {
                itemFlags = section.getStringList("item-flags");
            }

            return new MenuItem(material, amount, displayName, customModelData, List.copyOf(lore), List.copyOf(itemFlags));
        }

        ItemStack build(Map<String, String> placeholders, List<String> runeLines) {
            ItemStack item = new ItemStack(material, amount);
            ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                return item;
            }

            meta.displayName(ColorUtils.colorizeComponent(replace(displayName, placeholders))
                .decoration(TextDecoration.ITALIC, false));

            if (customModelData > 0) {
                meta.setCustomModelData(customModelData);
            }

            List<Component> components = new ArrayList<>();
            for (String rawLine : lore) {
                if (rawLine.contains("{RUNES}")) {
                    if (runeLines.isEmpty()) {
                        components.add(ColorUtils.colorizeComponent(replace(rawLine.replace("{RUNES}", ""), placeholders)));
                    } else {
                        for (String runeLine : runeLines) {
                            components.add(ColorUtils.colorizeComponent(replace(rawLine.replace("{RUNES}", runeLine), placeholders))
                                .decoration(TextDecoration.ITALIC, false));
                        }
                    }
                    continue;
                }
                components.add(ColorUtils.colorizeComponent(replace(rawLine, placeholders))
                    .decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(components);

            for (String rawFlag : itemFlags) {
                try {
                    meta.addItemFlags(ItemFlag.valueOf(rawFlag.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ignored) {
                }
            }

            item.setItemMeta(meta);
            return item;
        }

        private static String replace(String text, Map<String, String> placeholders) {
            String result = text == null ? "" : text;
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                result = result.replace(entry.getKey(), entry.getValue());
            }
            return result;
        }

        private static String readString(ConfigurationSection section, String fallback, String... paths) {
            for (String path : paths) {
                String value = section.getString(path);
                if (value != null) {
                    return value;
                }
            }
            return fallback;
        }

        private static List<String> readStringList(ConfigurationSection section, String... paths) {
            for (String path : paths) {
                if (!section.contains(path)) {
                    continue;
                }
                if (section.isList(path)) {
                    return section.getStringList(path);
                }
                String single = section.getString(path);
                if (single != null) {
                    return List.of(single);
                }
            }
            return List.of();
        }

        private static int readInt(ConfigurationSection section, int fallback, String... paths) {
            for (String path : paths) {
                if (section.contains(path)) {
                    return section.getInt(path, fallback);
                }
            }
            return fallback;
        }
    }
}
