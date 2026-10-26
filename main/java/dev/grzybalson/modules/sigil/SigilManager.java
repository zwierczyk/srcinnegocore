package dev.grzybalson.modules.sigil;

import dev.grzybalson.HollyCore;
import dev.grzybalson.anarchiagg.customitems.util.EventItemTools;
import dev.grzybalson.config.HollyDataPaths;
import dev.grzybalson.managers.IncognitoManager;
import dev.grzybalson.modules.cases.GuiScrollAnimator;
import dev.grzybalson.modules.szaman.PerksManager;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.ItemSerializer;
import dev.grzybalson.utils.NBTUtils;
import dev.grzybalson.utils.TimeUtil;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class SigilManager {

    public static final String SIGIL_TYPE_KEY = "holly_sigil_type";
    public static final String SIGIL_POWER_KEY = "holly_sigil_power";
    public static final String SIGIL_ACTION_KEY = "holly_sigil_action";
    public static final String SIGIL_EXPIRES_KEY = "holly_sigil_expires";
    public static final String SIGIL_DURATION_KEY = "holly_sigil_duration";

    private static final String CONFIG_RESOURCE = "addons/sigil/sigil.yml";

    private final HollyCore plugin;
    private final File configFile;
    private final SigilDataManager dataManager;
    private final Random random = new Random();
    private final Set<UUID> pendingActions = new HashSet<>();
    private final Map<UUID, AtomicBoolean> rollAnimationCancelled = new ConcurrentHashMap<>();
    private final Map<UUID, Long> ignoreUseUntil = new ConcurrentHashMap<>();
    private org.bukkit.scheduler.BukkitTask temporaryGrantTask;

    private YamlConfiguration config;
    private String inventoryTitle;
    private int inventorySize;
    private List<Integer> equipSlots = List.of(2, 3, 4, 5, 6);
    private Map<SigilType, Integer> displaySlotsByType = Map.of();
    private List<Integer> displaySlots = List.of();
    private int rollSlot = 38;
    private int buySlotsSlot = 39;
    private double rollCost = 100.0D;
    private double rerollCost = 50.0D;
    private double extraSlotsCost = 200.0D;
    private int extraSlotsDurationDays = 3;
    private double maxPower = 25.0D;
    private double minPower = 1.0D;
    private Material emptyEquipMaterial = Material.GRAY_DYE;
    private ItemStack shardItem;
    private String shardName;

    private boolean rollAnimationEnabled = true;
    private String rollAnimationTitle = "&8Losowanie sigila...";
    private int rollAnimationRows = 3;
    private int[] rollAnimDelays = SigilScreenRollAnimator.DEFAULT_DELAYS.clone();
    private Sound rollSoundFinish = Sound.ENTITY_PLAYER_LEVELUP;
    private Sound rollSoundFail = Sound.BLOCK_NOTE_BLOCK_BASS;

    private String manageTitle = "&8Zarządzanie sigilem";
    private int manageSize = 27;
    private int manageDisplaySlot = 13;
    private int manageRerollSlot = 15;
    private int manageBackSlot = 11;

    private boolean broadcastRollEnabled = true;
    private boolean broadcastRerollEnabled = true;
    private String broadcastRollMessage = "&#FF59E8❖ &#7AD7FF{PLAYER} &f{VERB} {COLOR}{SIGIL}\n&8( {POWER_COLOR}{POWER}% &8)";
    private String broadcastRerollMessage = "&#FF59E8❖ &#7AD7FF{PLAYER} &f{VERB} {COLOR}{SIGIL}\n&8( &7{OLD}% &8→ {POWER_COLOR}{NEW}% &8)";
    private String alreadyOwnedTitle = "&#FF0000✘";
    private String alreadyOwnedSubtitle = "&cnie mozesz uzyc tego sigila";
    private double nonFortunaPowerMultiplier = 0.5D;
    /** Skala bonusu obrażeń (Furia/Gniew/Przebicie). 0.5 = -50% damage z sigili. */
    private double damageBonusMultiplier = 0.5D;
    private boolean perkActionBarEnabled = true;
    private String perkActionBarDealt = "&cPerk &8» &f{SIGIL} &7(&f{POWER}%&7) &8→ &f{PLAYER}";
    private String perkActionBarTaken = "&cPerk &8» &f{PLAYER} &7{SIGIL} &8(&f{POWER}%&8)";
    private String perkActionBarSelf = "&aPerk &8» &f{SIGIL} &7(&f{POWER}%&7)";

    private Sound soundGuiOpen = Sound.BLOCK_CHEST_OPEN;
    private Sound soundClick = Sound.UI_BUTTON_CLICK;
    private Sound soundEquip = Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
    private Sound soundUnequip = Sound.BLOCK_NOTE_BLOCK_PLING;
    private Sound soundUnlock = Sound.ENTITY_PLAYER_LEVELUP;
    private Sound soundManageOpen = Sound.BLOCK_ENDER_CHEST_OPEN;
    private Sound soundFail = Sound.BLOCK_NOTE_BLOCK_BASS;

    public SigilManager(HollyCore plugin) {
        this.plugin = plugin;
        HollyDataPaths.ensureResource(plugin, CONFIG_RESOURCE);
        this.configFile = new File(plugin.getDataFolder(), CONFIG_RESOURCE);
        this.dataManager = new SigilDataManager(plugin);
        reload();
        startTemporaryGrantTask();
    }

    private void startTemporaryGrantTask() {
        if (temporaryGrantTask != null) {
            temporaryGrantTask.cancel();
        }
        temporaryGrantTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickTemporaryGrants, 20L * 10L, 20L * 10L);
    }

    public void shutdown() {
        if (temporaryGrantTask != null) {
            temporaryGrantTask.cancel();
            temporaryGrantTask = null;
        }
    }

    private void tickTemporaryGrants() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            if (!dataManager.isLoaded(uuid)) {
                continue;
            }
            SigilPlayerData data = dataManager.getCachedIfPresent(uuid);
            if (data == null) {
                continue;
            }
            boolean expiredOwned = data.pruneExpiredOwned();
            boolean migratedGrant = data.pruneTemporaryGrant();
            if (!expiredOwned && !migratedGrant) {
                continue;
            }
            dataManager.save(data);
            if (expiredOwned) {
                send(player, config.getString("messages.temporary-grant-expired",
                    "&#FFAA00⚠ &8Ξ &eTymczasowe sigile wygasły i zostały usunięte."));
            }
        }
    }

    public void reload() {
        this.config = YamlConfiguration.loadConfiguration(configFile);
        boolean wroteDefaults = false;
        if (!config.isSet("damage-bonus-multiplier")) {
            config.set("damage-bonus-multiplier", 0.5D);
            wroteDefaults = true;
        }
        this.inventoryTitle = config.getString("inventory.title", "&8SIGILE");
        this.inventorySize = Math.max(9, Math.min(54, config.getInt("inventory.size", 54)));
        if (inventorySize % 9 != 0) {
            inventorySize = 54;
        }

        this.equipSlots = readIntList("inventory.equip-slots", List.of(2, 3, 4, 5, 6));
        boolean wroteSlots = loadDisplaySlots();
        if (wroteDefaults || wroteSlots) {
            try {
                config.save(configFile);
            } catch (Exception exception) {
                plugin.getLogger().warning("Nie udało się zapisać domyślnych slotów w sigil.yml: " + exception.getMessage());
            }
        }

        this.rollSlot = config.getInt("inventory.roll-slot", 38);
        this.buySlotsSlot = config.getInt("inventory.buy-slots-slot", 39);
        this.rollCost = Math.max(0.0D, config.getDouble("rollCost", 100.0D));
        this.rerollCost = Math.max(0.0D, config.getDouble("rerollCost", 50.0D));
        this.extraSlotsCost = Math.max(0.0D, config.getDouble("extraSlotsCost", 200.0D));
        this.extraSlotsDurationDays = Math.max(1, config.getInt("extraSlotsDurationDays", 3));
        this.maxPower = Math.max(1.0D, config.getDouble("maxPower", 25.0D));
        this.minPower = Math.max(1.0D, Math.min(maxPower, config.getDouble("minPower", 1.0D)));
        this.emptyEquipMaterial = readMaterial("emptyEquipMaterial", Material.GRAY_DYE);

        this.shardName = config.getString("shardName", "");
        String serializedShard = firstNonBlank(
            config.getString("shard.serializedItem"),
            config.getString("shard.serialized-item")
        );
        this.shardItem = serializedShard == null ? null : ItemSerializer.deserializeItem(serializedShard);

        this.rollAnimationEnabled = config.getBoolean("rollAnimation.enabled", true);
        this.rollAnimationTitle = config.getString("rollAnimation.gui-title", "&8Losowanie sigila...");
        this.rollAnimationRows = Math.max(3, Math.min(6, config.getInt("rollAnimation.gui-rows", 3)));
        this.rollAnimDelays = parseAnimDelays(config.getStringList("rollAnimation.anim-delay-ticks"));
        this.rollSoundFinish = readSound("rollAnimation.sounds.finish", Sound.ENTITY_PLAYER_LEVELUP);
        this.rollSoundFail = readSound("rollAnimation.sounds.fail", Sound.BLOCK_NOTE_BLOCK_BASS);

        this.manageTitle = config.getString("manageGui.title", "&8Zarządzanie sigilem");
        this.manageSize = Math.max(9, Math.min(54, config.getInt("manageGui.size", 27)));
        if (manageSize % 9 != 0) {
            manageSize = 27;
        }
        this.manageDisplaySlot = config.getInt("manageGui.display-slot", 13);
        this.manageRerollSlot = config.getInt("manageGui.reroll-slot", 15);
        this.manageBackSlot = config.getInt("manageGui.back-slot", 11);

        this.broadcastRollEnabled = config.getBoolean("broadcast.roll-enabled", true);
        this.broadcastRerollEnabled = config.getBoolean("broadcast.reroll-enabled", true);
        this.broadcastRollMessage = config.getString("broadcast.roll",
            "&#FF59E8❖ &#7AD7FF{PLAYER} &f{VERB} {COLOR}{SIGIL}\n&8( {POWER_COLOR}{POWER}% &8)");
        this.broadcastRerollMessage = config.getString("broadcast.reroll",
            "&#FF59E8❖ &#7AD7FF{PLAYER} &f{VERB} {COLOR}{SIGIL}\n&8( &7{OLD}% &8→ {POWER_COLOR}{NEW}% &8)");
        this.alreadyOwnedTitle = config.getString("messages.already-owned-title", "&#FF0000✘");
        this.alreadyOwnedSubtitle = config.getString("messages.already-owned-subtitle",
            "&cnie mozesz uzyc tego sigila");
        this.nonFortunaPowerMultiplier = Math.max(0.0D, config.getDouble("non-fortuna-power-multiplier", 0.5D));
        this.damageBonusMultiplier = Math.max(0.0D, config.getDouble("damage-bonus-multiplier", 0.5D));
        this.perkActionBarEnabled = config.getBoolean("actionbar.enabled", true);
        this.perkActionBarDealt = config.getString("actionbar.dealt",
            "&cPerk &8» &f{SIGIL} &7(&f{POWER}%&7) &8→ &f{PLAYER}");
        this.perkActionBarTaken = config.getString("actionbar.taken",
            "&cPerk &8» &f{PLAYER} &7{SIGIL} &8(&f{POWER}%&8)");
        this.perkActionBarSelf = config.getString("actionbar.self",
            "&aPerk &8» &f{SIGIL} &7(&f{POWER}%&7)");

        this.soundGuiOpen = readSound("sounds.gui-open", Sound.BLOCK_CHEST_OPEN);
        this.soundClick = readSound("sounds.click", Sound.UI_BUTTON_CLICK);
        this.soundEquip = readSound("sounds.equip", Sound.ENTITY_EXPERIENCE_ORB_PICKUP);
        this.soundUnequip = readSound("sounds.unequip", Sound.BLOCK_NOTE_BLOCK_PLING);
        this.soundUnlock = readSound("sounds.unlock", Sound.ENTITY_PLAYER_LEVELUP);
        this.soundManageOpen = readSound("sounds.manage-open", Sound.BLOCK_ENDER_CHEST_OPEN);
        this.soundFail = readSound("sounds.fail", Sound.BLOCK_NOTE_BLOCK_BASS);
    }

    public void openGui(Player player) {
        loadPlayer(player.getUniqueId()).thenAccept(data ->
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                new SigilGui(this, player, data).open();
                playSound(player, soundGuiOpen);
            })
        );
    }

    public CompletableFuture<SigilPlayerData> loadPlayer(UUID uuid) {
        return dataManager.load(uuid);
    }

    public HollyCore getPlugin() {
        return plugin;
    }

    public void unloadPlayer(UUID uuid) {
        dataManager.unload(uuid);
        pendingActions.remove(uuid);
        ignoreUseUntil.remove(uuid);
    }

    public SigilPlayerData getCachedData(UUID uuid) {
        return dataManager.getCached(uuid);
    }

    public CompletableFuture<Void> savePlayer(UUID uuid) {
        return dataManager.save(dataManager.getCached(uuid));
    }

    public Inventory buildInventory(SigilGui holder, Player player, SigilPlayerData data) {
        data.pruneExpiredExtraSlots();
        if (pruneTimedOwnership(data)) {
            dataManager.save(data);
        }
        Inventory inventory = Bukkit.createInventory(holder, inventorySize, ColorUtils.colorizeComponent(inventoryTitle));
        plugin.getGuiFactory().applyDefaultTemplate(inventory);

        for (int i = 0; i < equipSlots.size() && i < SigilPlayerData.MAX_SLOTS; i++) {
            int slot = equipSlots.get(i);
            boolean lockedPremium = i >= SigilPlayerData.BASE_SLOTS && !data.hasExtraSlots();
            SigilType equipped = data.getEquippedAt(i);
            if (equipped != null && i < data.getAvailableSlotCount()) {
                setIfValid(inventory, slot, createDisplayItem(equipped, data.getPower(equipped), true, true, LoreHints.NONE,
                    resolveDisplayExpires(data, equipped)));
            } else if (lockedPremium) {
                setIfValid(inventory, slot, createLockedPremiumSlotItem());
            } else {
                setIfValid(inventory, slot, createEmptyEquipItem(i));
            }
        }

        for (SigilType type : SigilType.values()) {
            Integer slot = displaySlotsByType.get(type);
            if (slot == null) {
                continue;
            }
            boolean owned = data.owns(type);
            boolean active = data.isEquipped(type);
            double power = owned ? data.getPower(type) : 0.0D;
            setIfValid(inventory, slot, createDisplayItem(type, power, owned, active, LoreHints.GUI,
                owned ? resolveDisplayExpires(data, type) : null));
        }

        setIfValid(inventory, rollSlot, createRollButton());
        setIfValid(inventory, buySlotsSlot, createBuySlotsButton(data));

        ConfigurationSection fillers = config.getConfigurationSection("fillers");
        if (fillers != null) {
            for (String key : fillers.getKeys(false)) {
                try {
                    int slot = Integer.parseInt(key);
                    Material material = parseMaterial(fillers.getString(key + ".material"), Material.GRAY_STAINED_GLASS_PANE);
                    String name = fillers.getString(key + ".name", " ");
                    ItemStack filler = new ItemStack(material);
                    ItemMeta meta = filler.getItemMeta();
                    if (meta != null) {
                        meta.displayName(ColorUtils.colorizeComponent(name).decoration(TextDecoration.ITALIC, false));
                        filler.setItemMeta(meta);
                    }
                    setIfValid(inventory, slot, filler);
                } catch (NumberFormatException ignored) {
                }
            }
        }

        return inventory;
    }

    public void handleCatalogClick(Player player, ItemStack clicked, boolean leftClick) {
        SigilType type = identifySigilType(clicked);
        if (type == null) {
            return;
        }

        SigilPlayerData data = dataManager.getCached(player.getUniqueId());
        if (!data.owns(type)) {
            playSound(player, soundFail);
            send(player, config.getString("messages.not-unlocked", "&#FF0000✘ &8Ξ &cTen sigil nie zostal jeszcze odblokowany."));
            return;
        }

        if (leftClick) {
            if (data.isEquipped(type)) {
                playSound(player, soundFail);
                send(player, config.getString("messages.already-equipped", "&#FFAA00⚠ &8Ξ &eTen sigil jest juz aktywny."));
                return;
            }
            if (data.countEquipped(type.getCategory()) >= config.getInt("max-per-category", 2)) {
                playSound(player, soundFail);
                send(player, config.getString(
                    "messages.category-limit",
                    "&#FF0000✘ &8Ξ &cMozesz miec maksymalnie &f2 &csigile z kategorii &f{CATEGORY}&c."
                ).replace("{CATEGORY}", type.getCategory().getDisplayName()));
                return;
            }
            if (!data.equip(type)) {
                playSound(player, soundFail);
                send(player, config.getString("messages.no-free-slot", "&#FF0000✘ &8Ξ &cBrak wolnego slotu na sigil."));
                return;
            }
            dataManager.save(data);
            playSound(player, soundEquip);
            send(player, config.getString("messages.equipped", "&#00FF3B✔ &8Ξ &aAktywowano sigil &f{SIGIL}&a.")
                .replace("{SIGIL}", type.getDisplayName()));
            openGui(player);
            return;
        }

        playSound(player, soundClick);
        openManageGui(player, type);
    }

    public void handleEquipSlotClick(Player player, int rawSlot) {
        int index = equipSlots.indexOf(rawSlot);
        if (index < 0) {
            return;
        }

        SigilPlayerData data = dataManager.getCached(player.getUniqueId());
        data.pruneExpiredExtraSlots();

        if (index >= data.getAvailableSlotCount()) {
            playSound(player, soundFail);
            send(player, config.getString("messages.premium-slot-locked",
                "&#FFAA00⚠ &8Ξ &eTen slot jest premium. Kup dodatkowe sloty, aby go odblokowac."));
            return;
        }

        SigilType equipped = data.getEquippedAt(index);
        if (equipped == null) {
            return;
        }

        data.unequipSlot(index);
        dataManager.save(data);
        playSound(player, soundUnequip);
        send(player, config.getString("messages.unequipped", "&#00FF3B✔ &8Ξ &aDezaktywowano sigil &f{SIGIL}&a.")
            .replace("{SIGIL}", equipped.getDisplayName()));
        openGui(player);
    }

    public void openManageGui(Player player, SigilType type) {
        SigilPlayerData data = dataManager.getCached(player.getUniqueId());
        if (type == null || !data.owns(type)) {
            playSound(player, soundFail);
            return;
        }

        SigilManageGui holder = new SigilManageGui(player.getUniqueId(), type);
        Inventory inventory = Bukkit.createInventory(holder, manageSize, ColorUtils.colorizeComponent(manageTitle));
        holder.setInventory(inventory);
        plugin.getGuiFactory().applyDefaultTemplate(inventory);

        setIfValid(inventory, manageDisplaySlot,
            createDisplayItem(type, data.getPower(type), true, data.isEquipped(type), LoreHints.NONE,
                resolveDisplayExpires(data, type)));
        setIfValid(inventory, manageRerollSlot, createRerollButton());
        setIfValid(inventory, manageBackSlot, createBackButton());

        player.openInventory(inventory);
        playSound(player, soundManageOpen);
    }

    public void handleManageClick(Player player, SigilManageGui holder, ItemStack clicked, int slot) {
        if (holder == null || !player.getUniqueId().equals(holder.getPlayerUuid())) {
            return;
        }

        String action = getAction(clicked);
        if ("back".equals(action) || slot == manageBackSlot) {
            playSound(player, soundClick);
            openGui(player);
            return;
        }
        if ("reroll_power".equals(action) || slot == manageRerollSlot) {
            startPowerReroll(player, holder.getType());
        }
    }

    public void startPowerReroll(Player player, SigilType type) {
        if (!tryStartAction(player)) {
            return;
        }

        boolean animationStarted = false;
        try {
            SigilPlayerData data = dataManager.getCached(player.getUniqueId());
            if (type == null || !data.owns(type)) {
                playSound(player, soundFail);
                return;
            }

            if (!consumeShards(player, rerollCost)) {
                playSound(player, soundFail);
                return;
            }

            double oldPower = data.getPower(type);
            double newPower = rollPower();
            animationStarted = true;
            startPowerRollAnimation(player, type, oldPower, newPower);
        } finally {
            if (!animationStarted) {
                finishAction(player);
            }
        }
    }

    public void rollSigil(Player player) {
        if (!tryStartAction(player)) {
            return;
        }

        boolean animationStarted = false;
        try {
            if (!hasEmptyStorageSlot(player)) {
                playSound(player, soundFail);
                send(player, config.getString("messages.inventory-full",
                    "&#FF0000✘ &8Ξ &cMusisz miec wolne miejsce w ekwipunku, aby losowac sigile."));
                return;
            }

            if (!consumeShards(player, rollCost)) {
                playSound(player, soundFail);
                return;
            }

            SigilType type = randomType();
            double power = rollPower();
            if (rollAnimationEnabled) {
                animationStarted = true;
                startRollAnimation(player, type, power);
            } else {
                finishRoll(player, type, power);
            }
        } finally {
            if (!animationStarted) {
                finishAction(player);
            }
        }
    }

    public void buyExtraSlots(Player player) {
        if (!tryStartAction(player)) {
            return;
        }
        try {
            SigilPlayerData data = dataManager.getCached(player.getUniqueId());
            if (!consumeShards(player, extraSlotsCost)) {
                playSound(player, soundFail);
                return;
            }

            long duration = TimeUnit.DAYS.toMillis(extraSlotsDurationDays);
            data.renewExtraSlots(duration);
            dataManager.save(data);
            playSound(player, rollSoundFinish);
            send(player, config.getString("messages.extra-slots-bought",
                "&#00FF3B✔ &8Ξ &aOdblokowano +2 sloty na &f{DAYS} &adni.")
                .replace("{DAYS}", String.valueOf(extraSlotsDurationDays)));
            openGui(player);
        } finally {
            finishAction(player);
        }
    }

    public boolean unlockFromItem(Player player, ItemStack item) {
        SigilType type = identifySigilType(item);
        if (type == null) {
            return false;
        }

        Long duration = getSigilDuration(item);
        Long expiresAt = getSigilExpiresAt(item);
        Double powerTag = NBTUtils.getDouble(item, SIGIL_POWER_KEY);
        boolean adminPower = powerTag != null && powerTag > maxPower + 0.05D;
        boolean timedItem = (duration != null && duration > 0L) || expiresAt != null || adminPower;

        double power = powerTag == null
            ? minPower
            : (timedItem ? clampAdminPower(powerTag) : clampPower(powerTag));

        SigilPlayerData data = dataManager.getCached(player.getUniqueId());
        pruneTimedOwnership(data);
        boolean ownedBefore = data.owns(type);
        double previous = ownedBefore ? data.getPower(type) : 0.0D;
        boolean permanentOwned = ownedBefore && !data.hasOwnedExpiry(type);

        if (ownedBefore && power < previous) {
            playSound(player, soundFail);
            player.showTitle(net.kyori.adventure.title.Title.title(
                ColorUtils.colorizeComponent(alreadyOwnedTitle),
                ColorUtils.colorizeComponent(formatAlreadyOwnedSubtitle()),
                net.kyori.adventure.title.Title.Times.times(
                    java.time.Duration.ofMillis(80),
                    java.time.Duration.ofMillis(2200),
                    java.time.Duration.ofMillis(350)
                )
            ));
            send(player, config.getString("messages.unlock-weaker",
                "&#FFAA00⚠ &8Ξ &ePosiadasz juz silniejszy sigil &f{SIGIL}&e (&f{POWER}%&e).")
                .replace("{SIGIL}", type.getDisplayName())
                .replace("{POWER}", SigilType.formatValue(previous)));
            return true;
        }

        if (permanentOwned && power <= previous) {
            playSound(player, soundFail);
            player.showTitle(net.kyori.adventure.title.Title.title(
                ColorUtils.colorizeComponent(alreadyOwnedTitle),
                ColorUtils.colorizeComponent(formatAlreadyOwnedSubtitle()),
                net.kyori.adventure.title.Title.Times.times(
                    java.time.Duration.ofMillis(80),
                    java.time.Duration.ofMillis(2200),
                    java.time.Duration.ofMillis(350)
                )
            ));
            send(player, config.getString("messages.unlock-weaker",
                "&#FFAA00⚠ &8Ξ &ePosiadasz juz silniejszy sigil &f{SIGIL}&e (&f{POWER}%&e).")
                .replace("{SIGIL}", type.getDisplayName())
                .replace("{POWER}", SigilType.formatValue(previous)));
            return true;
        }

        long unlockedUntil = 0L;
        if (!timedItem) {
            data.unlockOrUpgradePermanent(type, power);
        } else if (permanentOwned) {
            data.unlockOrUpgradePermanent(type, power);
        } else {
            data.unlockOrUpgrade(type, power);
            long useDuration = duration != null && duration > 0L
                ? duration
                : TimeUnit.DAYS.toMillis(1L);
            unlockedUntil = System.currentTimeMillis() + useDuration;
            data.extendOwnedExpiry(type, unlockedUntil);
        }
        dataManager.save(data);
        consumeOne(player, item);

        playSound(player, soundUnlock);
        String success = config.getString("messages.unlock-success",
            "&#00FF3B✔ &8Ξ &aOdblokowano sigil &f{SIGIL} &a(&f{POWER}%&a).")
            .replace("{SIGIL}", type.getDisplayName())
            .replace("{POWER}", SigilType.formatValue(data.getPower(type)));
        if (unlockedUntil > 0L) {
            success = config.getString("messages.unlock-temporary-success",
                "&#00FF3B✔ &8Ξ &aOdblokowano sigil &f{SIGIL} &a(&f{POWER}%&a) na &e{TIME}&a.")
                .replace("{SIGIL}", type.getDisplayName())
                .replace("{POWER}", SigilType.formatValue(data.getPower(type)))
                .replace("{TIME}", TimeUtil.convert(unlockedUntil - System.currentTimeMillis()));
        }
        send(player, success);
        return true;
    }

    public ItemStack createSigilItem(SigilType type, double power) {
        return createSigilItem(type, power, null, null);
    }

    public ItemStack createTemporarySigilItem(SigilType type, double power, long durationMillis) {
        return createSigilItem(type, clampAdminPower(power), null, Math.max(TimeUnit.MINUTES.toMillis(1L), durationMillis));
    }

    private ItemStack createSigilItem(SigilType type, double power, Long expiresAtMillis, Long durationMillis) {
        Long duration = durationMillis;
        if ((duration == null || duration <= 0L) && expiresAtMillis != null) {
            duration = TimeUnit.DAYS.toMillis(1L);
        }
        boolean timed = duration != null && duration > 0L;
        double safePower = timed ? clampAdminPower(power) : clampPower(power);
        ItemStack item = new ItemStack(type.getDye());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(ColorUtils.colorizeComponent(gradientTitle(type)).decoration(TextDecoration.ITALIC, false));
            List<String> loreLines = new ArrayList<>(buildOwnedLore(type, safePower, false, LoreHints.PHYSICAL));
            appendTimedItemLore(loreLines, null, duration);
            meta.lore(ColorUtils.colorizeLore(String.join("\n", loreLines)));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        NBTUtils.setString(item, SIGIL_TYPE_KEY, type.name());
        NBTUtils.setDouble(item, SIGIL_POWER_KEY, safePower);
        if (timed) {
            NBTUtils.setLong(item, SIGIL_DURATION_KEY, duration);
            NBTUtils.removeKey(item, SIGIL_EXPIRES_KEY);
        }
        return item;
    }

    /**
     * Przebudowuje nazwę i lore fizycznego itemu sigila (stare opisy → aktualne).
     * Zachowuje typ, moc i ilość.
     */
    public boolean refreshPhysicalSigilItem(ItemStack item) {
        SigilType type = identifySigilType(item);
        if (type == null || item.getItemMeta() == null) {
            return false;
        }
        Long duration = getSigilDuration(item);
        Long expiresAt = getSigilExpiresAt(item);
        Double stored = NBTUtils.getDouble(item, SIGIL_POWER_KEY);
        boolean adminPower = stored != null && stored > maxPower + 0.05D;
        if (duration == null || duration <= 0L) {
            if (expiresAt != null || adminPower) {
                duration = TimeUnit.DAYS.toMillis(1L);
            }
        }
        boolean timed = duration != null && duration > 0L;
        double power = stored != null
            ? (timed ? clampAdminPower(stored) : clampPower(stored))
            : minPower;
        ItemMeta meta = item.getItemMeta();
        meta.displayName(ColorUtils.colorizeComponent(gradientTitle(type)).decoration(TextDecoration.ITALIC, false));
        List<String> loreLines = new ArrayList<>(buildOwnedLore(type, power, false, LoreHints.PHYSICAL));
        appendTimedItemLore(loreLines, null, duration);
        meta.lore(ColorUtils.colorizeLore(String.join("\n", loreLines)));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        NBTUtils.setString(item, SIGIL_TYPE_KEY, type.name());
        NBTUtils.setDouble(item, SIGIL_POWER_KEY, power);
        if (timed) {
            NBTUtils.setLong(item, SIGIL_DURATION_KEY, duration);
        }
        NBTUtils.removeKey(item, SIGIL_EXPIRES_KEY);
        return true;
    }

    public void refreshPhysicalSigilItems(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        ItemStack[] contents = player.getInventory().getContents();
        boolean changed = false;
        for (ItemStack stack : contents) {
            if (stack != null && refreshPhysicalSigilItem(stack)) {
                changed = true;
            }
        }
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand != null && refreshPhysicalSigilItem(offhand)) {
            changed = true;
        }
        if (changed) {
            player.updateInventory();
        }
    }

    public SigilType identifySigilType(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }
        return SigilType.fromString(NBTUtils.getString(item, SIGIL_TYPE_KEY));
    }

    public String getAction(ItemStack item) {
        return NBTUtils.getString(item, SIGIL_ACTION_KEY);
    }

    public boolean isEquipSlot(int slot) {
        return equipSlots.contains(slot);
    }

    public boolean isDisplaySlot(int slot) {
        return displaySlots.contains(slot);
    }

    /**
     * Czyta {@code inventory.slots.<typ>} (np. {@code jad: 27}).
     * Brakujące klucze dopisuje do configu — Jad domyślnie na slocie 27.
     */
    private boolean loadDisplaySlots() {
        Map<SigilType, Integer> byType = new EnumMap<>(SigilType.class);
        List<Integer> slots = new ArrayList<>();
        boolean wrote = false;
        for (SigilType type : SigilType.values()) {
            String path = "inventory.slots." + type.name().toLowerCase(Locale.ROOT);
            int fallback = defaultDisplaySlot(type);
            if (!config.isSet(path)) {
                config.set(path, fallback);
                wrote = true;
            }
            int slot = clampInventorySlot(config.getInt(path, fallback));
            byType.put(type, slot);
            if (!slots.contains(slot)) {
                slots.add(slot);
            }
        }
        this.displaySlotsByType = Map.copyOf(byType);
        this.displaySlots = List.copyOf(slots);
        return wrote;
    }

    private int clampInventorySlot(int slot) {
        return Math.max(0, Math.min(inventorySize - 1, slot));
    }

    private static int defaultDisplaySlot(SigilType type) {
        return switch (type) {
            case FURIA -> 19;
            case GNIEW -> 20;
            case PRZEBICIE -> 21;
            case WYTRZYMALOSC -> 22;
            case TARCZA -> 23;
            case ODPORNOSC -> 24;
            case JAD -> 27;
            case KLATWA -> 29;
            case DRENAZ -> 30;
            case SKARB -> 31;
            case SZCZESCIE -> 32;
            case DOBROBYT -> 33;
            case SPETANIA -> 25;
            case LOWCY -> 26;
            case PODMUCHU -> 28;
        };
    }

    public int getRollSlot() {
        return rollSlot;
    }

    public int getBuySlotsSlot() {
        return buySlotsSlot;
    }

    public double getActivePower(UUID uuid, SigilType type) {
        SigilPlayerData data = dataManager.getCached(uuid);
        if (pruneTimedOwnership(data)) {
            dataManager.save(data);
        }
        double power = data.getActivePower(type);
        if (type != null && type.getCategory() != SigilCategory.FORTUNA) {
            power *= nonFortunaPowerMultiplier;
        }
        return power;
    }

    /** Moc zalozonego sigila (wartosc z itemu, bez non-fortuna multiplier). 0 jesli niezalozony. */
    public double getEquippedPower(UUID uuid, SigilType type) {
        SigilPlayerData data = dataManager.getCached(uuid);
        if (pruneTimedOwnership(data)) {
            dataManager.save(data);
        }
        return data.getActivePower(type);
    }

    /** Mnożnik bonusu obrażeń (Furia / Przebicie). Gniew = osobny krytyk. */
    public double getDamageBonusMultiplier() {
        return damageBonusMultiplier;
    }

    public double getDropBonus(Player player) {
        if (player == null) {
            return 0.0D;
        }
        return getActivePower(player.getUniqueId(), SigilType.SZCZESCIE);
    }

    public double getDropChanceBonus(Player player) {
        if (player == null) {
            return 0.0D;
        }
        return getActivePower(player.getUniqueId(), SigilType.SKARB);
    }

    public double getMiningBonus(Player player) {
        if (player == null) {
            return 0.0D;
        }
        return getActivePower(player.getUniqueId(), SigilType.DOBROBYT);
    }

    public double getDropMultiplier(Player player) {
        return 1.0D + (getDropBonus(player) / 100.0D);
    }

    public double getMiningMultiplier(Player player) {
        return 1.0D + (getMiningBonus(player) / 100.0D);
    }

    public void giveSigil(Player target, SigilType type, double power) {
        giveItem(target, createSigilItem(type, power));
    }

    /**
     * Nadaje wszystkie sigile do EQ. Czas ({@code durationMillis}) startuje dopiero po PPM.
     * Nieużyte itemy zostają w ekwipunku.
     */
    public CompletableFuture<Void> giveAllTemporarySigils(Player target, double power, long durationMillis) {
        if (target == null || !target.isOnline()) {
            return CompletableFuture.completedFuture(null);
        }
        long duration = Math.max(TimeUnit.MINUTES.toMillis(1L), durationMillis);
        double safePower = clampAdminPower(power);

        return dataManager.load(target.getUniqueId()).thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!target.isOnline()) {
                return;
            }
            for (SigilType type : SigilType.values()) {
                giveItem(target, createTemporarySigilItem(type, safePower, duration));
            }
            send(target, config.getString("messages.temporary-grant-received",
                "&#00FF3B✔ &8Ξ &aOtrzymałeś sigile do ekwipunku. Użyj PPM, aby odblokować na &e{TIME}&a (&f{POWER}%&a).")
                .replace("{POWER}", SigilType.formatValue(safePower))
                .replace("{TIME}", TimeUtil.convert(duration)));
        }));
    }

    public Long getSigilDuration(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }
        return NBTUtils.getLong(item, SIGIL_DURATION_KEY);
    }

    public Long getSigilExpiresAt(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }
        return NBTUtils.getLong(item, SIGIL_EXPIRES_KEY);
    }

    public boolean isTemporarySigilItem(ItemStack item) {
        return getSigilDuration(item) != null || getSigilExpiresAt(item) != null;
    }

    private boolean pruneTimedOwnership(SigilPlayerData data) {
        if (data == null) {
            return false;
        }
        boolean expiredOwned = data.pruneExpiredOwned();
        boolean expiredGrant = data.pruneTemporaryGrant();
        return expiredOwned || expiredGrant;
    }

    private Long resolveDisplayExpires(SigilPlayerData data, SigilType type) {
        Long perType = data.getOwnedExpiresAtMillis(type);
        if (perType != null) {
            return perType;
        }
        if (data.hasActiveTemporaryGrant()) {
            return data.getTemporaryGrantExpiresAtMillis();
        }
        return null;
    }

    private void appendTimedItemLore(List<String> loreLines, Long expiresAtMillis, Long durationMillis) {
        long shown = 0L;
        if (durationMillis != null && durationMillis > 0L) {
            shown = durationMillis;
        } else if (expiresAtMillis != null) {
            long leftover = expiresAtMillis - System.currentTimeMillis();
            shown = leftover > 0L ? leftover : TimeUnit.DAYS.toMillis(1L);
        }
        if (shown <= 0L) {
            return;
        }
        loreLines.add("");
        loreLines.add("&c⏱ &f" + SigilText.toSmallCaps("po uzyciu") + ": &e" + TimeUtil.convert(shown));
        loreLines.add("&7" + SigilText.toSmallCaps("czas leci dopiero po ppm"));
    }

    public int scrubExpiredSigilItems(Player player) {
        refreshPhysicalSigilItems(player);
        return 0;
    }

    /** Nieużyte sigile zostają w EQ — czas startuje dopiero po PPM. */
    public int scrubAllTemporarySigilItems(Player player) {
        refreshPhysicalSigilItems(player);
        return 0;
    }

    public void handlePlayerJoin(Player player) {
        loadPlayer(player.getUniqueId()).thenAccept(data ->
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                boolean expiredOwned = data.pruneExpiredOwned();
                boolean migratedGrant = data.pruneTemporaryGrant();
                if (expiredOwned || migratedGrant) {
                    dataManager.save(data);
                }
                if (expiredOwned) {
                    send(player, config.getString("messages.temporary-grant-expired",
                        "&#FFAA00⚠ &8Ξ &eTymczasowe sigile wygasły i zostały usunięte."));
                }
                refreshPhysicalSigilItems(player);
            })
        );
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                refreshPhysicalSigilItems(player);
            }
        }, 40L);
    }

    public void prepareGrantedSigilItem(ItemStack item) {
        refreshPhysicalSigilItem(item);
    }

    public void suppressItemUse(Player player, long millis) {
        if (player == null || millis <= 0L) {
            return;
        }
        ignoreUseUntil.put(player.getUniqueId(), System.currentTimeMillis() + millis);
    }

    public boolean isItemUseSuppressed(Player player) {
        if (player == null) {
            return false;
        }
        Long until = ignoreUseUntil.get(player.getUniqueId());
        if (until == null) {
            return false;
        }
        if (until <= System.currentTimeMillis()) {
            ignoreUseUntil.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    public CompletableFuture<Void> grantSigilUnlock(UUID uuid, SigilType type, double power) {
        if (uuid == null || type == null) {
            return CompletableFuture.completedFuture(null);
        }
        return dataManager.load(uuid).thenCompose(data -> {
            data.unlockOrUpgrade(type, power);
            return dataManager.save(data);
        });
    }

    public ItemStack createShardGrantItem(int amount) {
        if (shardItem == null || amount <= 0) {
            return null;
        }
        ItemStack item = shardItem.clone();
        item.setAmount(Math.min(amount, Math.max(1, item.getMaxStackSize())));
        return item;
    }

    public CompletableFuture<Void> resetPlayer(UUID uuid) {
        SigilPlayerData data = dataManager.getCached(uuid);
        data.reset();
        return dataManager.save(data);
    }

    public CompletableFuture<Long> resetAllPlayers() {
        return dataManager.resetAll();
    }

    public String formatPerkLabel(SigilType type, double power) {
        if (type == null) {
            return "";
        }
        return type.getDisplayName() + " " + SigilType.formatValue(power) + "%";
    }

    public void notifyPerk(Player attacker, Player victim, SigilType type, double power, boolean defensive) {
        if (!perkActionBarEnabled || type == null) {
            return;
        }
        String label = formatPerkLabel(type, power);
        if (defensive) {
            sendPerkLine(victim, perkActionBarSelf, victim, type, power, label);
            sendPerkLine(attacker, perkActionBarTaken, victim, type, power, label);
            return;
        }
        sendPerkLine(attacker, perkActionBarDealt, victim, type, power, label);
        sendPerkLine(victim, perkActionBarTaken, attacker, type, power, label);
    }

    public void sendPerkActionBars(Player attacker, Player victim, List<String> attackerPerks, List<String> victimPerks) {
        if (!perkActionBarEnabled) {
            return;
        }
        if (attackerPerks != null && !attackerPerks.isEmpty() && attacker != null) {
            String joined = String.join(" &8| &f", attackerPerks);
            String other = visibleName(victim);
            EventItemTools.sendCastleActionBar(attacker, "sigil-deal",
                "&cPerk &8» &f" + joined + (other.isBlank() ? "" : " &8→ &f" + other));
        }
        if (victimPerks != null && !victimPerks.isEmpty() && victim != null) {
            String joined = String.join(" &8| &f", victimPerks);
            String other = visibleName(attacker);
            EventItemTools.sendCastleActionBar(victim, "sigil-take",
                "&cPerk &8» &f" + other + " &8→ &f" + joined);
        }
    }

    private void sendPerkLine(Player player, String template, Player other, SigilType type, double power, String label) {
        if (player == null || template == null || template.isBlank()) {
            return;
        }
        String message = template
            .replace("{PLAYER}", other != null ? visibleName(other) : visibleName(player))
            .replace("{SIGIL}", type != null ? type.getDisplayName() : "")
            .replace("{POWER}", SigilType.formatValue(power))
            .replace("{LABEL}", label == null ? "" : label);
        EventItemTools.sendCastleActionBar(player, "sigil", message);
    }

    /** Nick do actionbara — incognito jeśli aktywne. */
    private String visibleName(Player player) {
        if (player == null) {
            return "";
        }
        IncognitoManager incognito = plugin.getIncognitoManager();
        if (incognito != null && incognito.isIncognito(player)) {
            return incognito.displayName(player);
        }
        return player.getName();
    }

    public void finishRollImmediately(Player player, SigilRollGui holder) {
        finishRollFromAnimation(player, holder);
    }

    public void finishPowerRollImmediately(Player player, SigilPowerRollGui holder) {
        // Legacy — power roll używa teraz SigilRollGui.
    }

    private void finishRollFromAnimation(Player player, SigilRollGui holder) {
        if (holder == null || !player.getUniqueId().equals(holder.getPlayerUuid())) {
            return;
        }
        if (!holder.markFinished()) {
            return;
        }

        cancelRollAnimation(player.getUniqueId());
        rollAnimationCancelled.remove(player.getUniqueId());

        try {
            if (holder.getKind() == SigilRollGui.RollKind.POWER) {
                applyPowerReroll(player, holder.getType(), holder.getOldPower(), holder.getPower());
            } else {
                finishRoll(player, holder.getType(), holder.getPower());
            }
        } finally {
            finishAction(player);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    player.closeInventory();
                    openGui(player);
                }
            }, 2L);
        }
    }

    public void sendDebug(Player player) {
        loadPlayer(player.getUniqueId()).thenAccept(data ->
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                data.pruneExpiredExtraSlots();
                player.sendMessage(ColorUtils.colorizeComponent(""));
                player.sendMessage(ColorUtils.colorizeComponent("&8&m----------&r &#00E0FF&lSIGIL DEBUG &8&m----------"));
                player.sendMessage(ColorUtils.colorizeComponent("&7Gracz: &f" + player.getName()));
                player.sendMessage(ColorUtils.colorizeComponent("&7Sloty: &f" + data.getAvailableSlotCount()
                    + "&8/&f" + SigilPlayerData.MAX_SLOTS
                    + (data.hasExtraSlots() ? " &a(+premium)" : " &c(bez premium)")));
                if (data.hasExtraSlots()) {
                    long remaining = Math.max(0L, data.getExtraSlotsExpiresAtMillis() - System.currentTimeMillis());
                    long hours = TimeUnit.MILLISECONDS.toHours(remaining);
                    long minutes = TimeUnit.MILLISECONDS.toMinutes(remaining) % 60L;
                    player.sendMessage(ColorUtils.colorizeComponent("&7Premium wygasa za: &f" + hours + "h " + minutes + "m"));
                }
                player.sendMessage(ColorUtils.colorizeComponent(""));
                player.sendMessage(ColorUtils.colorizeComponent("&eAktywne:"));
                List<SigilType> active = data.getActiveEquipped();
                if (active.isEmpty()) {
                    player.sendMessage(ColorUtils.colorizeComponent("  &8• &7brak"));
                } else {
                    for (int i = 0; i < SigilPlayerData.MAX_SLOTS; i++) {
                        SigilType equipped = data.getEquippedAt(i);
                        if (equipped == null) {
                            continue;
                        }
                        if (i >= data.getAvailableSlotCount()) {
                            continue;
                        }
                        player.sendMessage(ColorUtils.colorizeComponent("  &8• &f#" + (i + 1) + " "
                            + equipped.getCategory().getColor() + equipped.getDisplayName()
                            + " &7→ &e" + SigilType.formatValue(data.getPower(equipped)) + "%"));
                    }
                }
                player.sendMessage(ColorUtils.colorizeComponent(""));
                player.sendMessage(ColorUtils.colorizeComponent("&bPosiadane:"));
                if (data.getOwned().isEmpty()) {
                    player.sendMessage(ColorUtils.colorizeComponent("  &8• &7brak"));
                } else {
                    for (SigilType type : SigilType.values()) {
                        if (!data.owns(type)) {
                            continue;
                        }
                        String status = data.isEquipped(type) ? "&aAKTYWNY" : "&cNIEAKTYWNY";
                        player.sendMessage(ColorUtils.colorizeComponent("  &8• "
                            + type.getCategory().getColor() + type.getDisplayName()
                            + " &7| &e" + SigilType.formatValue(data.getPower(type)) + "%"
                            + " &7| " + status
                            + " &8[" + type.getCategory().getDisplayName() + "]"));
                    }
                }
                player.sendMessage(ColorUtils.colorizeComponent("&8&m--------------------------------"));
                playSound(player, soundClick);
            })
        );
    }

    public List<String> getTypeNames() {
        return Arrays.stream(SigilType.values())
            .map(type -> type.name().toLowerCase(Locale.ROOT))
            .toList();
    }

    public int getManageRerollSlot() {
        return manageRerollSlot;
    }

    public int getManageBackSlot() {
        return manageBackSlot;
    }

    public void cancelRollAnimation(UUID uuid) {
        AtomicBoolean cancelled = rollAnimationCancelled.get(uuid);
        if (cancelled != null) {
            cancelled.set(true);
        }
    }

    public boolean isRollAnimationActive(UUID uuid) {
        return rollAnimationCancelled.containsKey(uuid);
    }

    private void startRollAnimation(Player player, SigilType rewardType, double rewardPower) {
        if (!player.isOnline()) {
            finishRoll(player, rewardType, rewardPower);
            finishAction(player);
            return;
        }

        SigilType[] values = SigilType.values();
        if (values.length == 0) {
            finishRoll(player, rewardType, rewardPower);
            finishAction(player);
            return;
        }

        int steps = rollAnimDelays.length;
        ItemStack[] strip = buildTypeRollStrip(rewardType, rewardPower, steps);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        rollAnimationCancelled.put(player.getUniqueId(), cancelled);

        SigilRollGui holder = new SigilRollGui(
            player.getUniqueId(),
            SigilRollGui.RollKind.TYPE,
            rewardType,
            rewardPower,
            0.0D
        );
        Inventory inventory = createRollAnimationInventory(holder);
        player.openInventory(inventory);
        playSound(player, soundClick);

        GuiScrollAnimator.run(plugin, inventory, strip, rollAnimDelays, rollAnimationRows, cancelled, () -> {
            rollAnimationCancelled.remove(player.getUniqueId());
            if (cancelled.get() || !player.isOnline() || holder.isFinished()) {
                if (!holder.isFinished()) {
                    finishAction(player);
                }
                return;
            }
            if (!holder.markFinished()) {
                finishAction(player);
                return;
            }
            try {
                finishRoll(player, rewardType, rewardPower);
            } finally {
                finishAction(player);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        openGui(player);
                    }
                }, 8L);
            }
        });
    }

    private void startPowerRollAnimation(Player player, SigilType type, double oldPower, double newPower) {
        if (!player.isOnline()) {
            applyPowerReroll(player, type, oldPower, newPower);
            finishAction(player);
            return;
        }

        int steps = rollAnimDelays.length;
        ItemStack[] strip = buildPowerRollStrip(type, newPower, steps);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        rollAnimationCancelled.put(player.getUniqueId(), cancelled);

        SigilRollGui holder = new SigilRollGui(
            player.getUniqueId(),
            SigilRollGui.RollKind.POWER,
            type,
            newPower,
            oldPower
        );
        Inventory inventory = createRollAnimationInventory(holder);
        player.openInventory(inventory);
        playSound(player, soundClick);

        GuiScrollAnimator.run(plugin, inventory, strip, rollAnimDelays, rollAnimationRows, cancelled, () -> {
            rollAnimationCancelled.remove(player.getUniqueId());
            if (cancelled.get() || !player.isOnline() || holder.isFinished()) {
                if (!holder.isFinished()) {
                    finishAction(player);
                }
                return;
            }
            if (!holder.markFinished()) {
                finishAction(player);
                return;
            }
            try {
                applyPowerReroll(player, type, oldPower, newPower);
            } finally {
                finishAction(player);
            }
        });
    }

    private Inventory createRollAnimationInventory(SigilRollGui holder) {
        Inventory inventory = Bukkit.createInventory(
            holder,
            rollAnimationRows * 9,
            ColorUtils.colorizeComponent(rollAnimationTitle)
        );
        holder.bind(inventory);
        return inventory;
    }

    private ItemStack[] buildTypeRollStrip(SigilType winnerType, double winnerPower, int steps) {
        int winnerIdx = steps - 1 + 4;
        int len = steps + 9;
        ItemStack[] strip = new ItemStack[len];
        SigilType[] values = SigilType.values();
        for (int i = 0; i < len; i++) {
            if (i == winnerIdx) {
                strip[i] = createSigilItem(winnerType, winnerPower);
            } else {
                SigilType filler = values[random.nextInt(values.length)];
                strip[i] = createSigilItem(filler, rollPowerPreview());
            }
        }
        return strip;
    }

    private ItemStack[] buildPowerRollStrip(SigilType type, double winnerPower, int steps) {
        int winnerIdx = steps - 1 + 4;
        int len = steps + 9;
        ItemStack[] strip = new ItemStack[len];
        for (int i = 0; i < len; i++) {
            double power = i == winnerIdx ? winnerPower : rollPowerPreview();
            strip[i] = createSigilItem(type, power);
        }
        return strip;
    }

    private double rollPower() {
        if (maxPower <= minPower) {
            return clampPower(minPower);
        }

        // Soft cap: powyżej 12.5% dużo rzadziej
        double softCap = Math.min(maxPower, Math.max(minPower, 12.5D));
        double roll = random.nextDouble();

        double raw;
        if (softCap >= maxPower) {
            // Cały zakres poniżej/równy softCap
            raw = minPower + Math.pow(random.nextDouble(), 1.15D) * (maxPower - minPower);
        } else if (roll < 0.90D) {
            // ~90%: [min, softCap] z lekkim biasem w dół
            raw = minPower + Math.pow(random.nextDouble(), 1.25D) * (softCap - minPower);
        } else if (roll < 0.985D) {
            // ~8.5%: lekko powyżej softCap (do ~60% drogi do max)
            double mid = softCap + (maxPower - softCap) * 0.55D;
            raw = softCap + Math.pow(random.nextDouble(), 2.4D) * (mid - softCap);
        } else {
            // ~1.5%: bardzo rzadki top (softCap → max), silny bias w dół
            raw = softCap + Math.pow(random.nextDouble(), 3.6D) * (maxPower - softCap);
        }

        return clampPower(Math.round(raw * 10.0D) / 10.0D);
    }

    private double rollPowerPreview() {
        return rollPower();
    }

    private void finishRoll(Player player, SigilType type, double power) {
        giveItem(player, createSigilItem(type, power));
        playSound(player, rollSoundFinish);
        send(player, config.getString("messages.roll-success",
            "&#00FF3B✔ &8Ξ &#4EFA76Wylosowano &f{SIGIL} &a(&f{POWER}%&a)!")
            .replace("{SIGIL}", type.getDisplayName())
            .replace("{POWER}", SigilType.formatValue(power)));
        broadcastRoll(player, type, power);
    }

    private void applyPowerReroll(Player player, SigilType type, double oldPower, double newPower) {
        SigilPlayerData data = dataManager.getCached(player.getUniqueId());
        double safePower = clampPower(newPower);
        data.setOwned(type, safePower);
        dataManager.save(data);

        playSound(player, rollSoundFinish);

        send(player, config.getString("messages.reroll-success",
            "&#00FF3B✔ &8Ξ &aPrzelosowano moc sigila &f{SIGIL}&a: &7{OLD}% &8→ &e{NEW}%")
            .replace("{SIGIL}", type.getDisplayName())
            .replace("{OLD}", SigilType.formatValue(oldPower))
            .replace("{NEW}", SigilType.formatValue(safePower)));
        broadcastReroll(player, type, oldPower, safePower);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                openGui(player);
            }
        }, 8L);
    }

    private void broadcastRoll(Player player, SigilType type, double power) {
        if (!broadcastRollEnabled || broadcastRollMessage == null || broadcastRollMessage.isBlank()) {
            return;
        }
        String message = broadcastRollMessage
            .replace("{PLAYER}", player.getName())
            .replace("{SIGIL}", SigilText.toSmallCaps(type.getDisplayName()))
            .replace("{COLOR}", type.getCategory().getColor())
            .replace("{POWER_COLOR}", SigilScreenRollAnimator.powerColor(power, minPower, maxPower))
            .replace("{POWER}", SigilType.formatValue(power))
            .replace("{VERB}", SigilText.toSmallCaps("wylosowal sigil"));
        broadcastSeparated(message);
    }

    private void broadcastReroll(Player player, SigilType type, double oldPower, double newPower) {
        if (!broadcastRerollEnabled || broadcastRerollMessage == null || broadcastRerollMessage.isBlank()) {
            return;
        }
        String message = broadcastRerollMessage
            .replace("{PLAYER}", player.getName())
            .replace("{SIGIL}", SigilText.toSmallCaps(type.getDisplayName()))
            .replace("{COLOR}", type.getCategory().getColor())
            .replace("{POWER_COLOR}", SigilScreenRollAnimator.powerColor(newPower, minPower, maxPower))
            .replace("{OLD}", SigilType.formatValue(oldPower))
            .replace("{NEW}", SigilType.formatValue(newPower))
            .replace("{VERB}", SigilText.toSmallCaps("ulepszyl moc sigila"));
        broadcastSeparated(message);
    }

    private void broadcastSeparated(String message) {
        Bukkit.broadcast(ColorUtils.colorizeComponent(" "));
        Bukkit.broadcast(ColorUtils.colorizeComponent(" "));
        Bukkit.broadcast(ColorUtils.colorizeComponent(message));
    }

    private String formatAlreadyOwnedSubtitle() {
        String raw = stripColorCodes(alreadyOwnedSubtitle);
        if (raw == null || raw.isBlank()) {
            raw = "nie mozesz uzyc tego sigila";
        }
        return "&c" + SigilText.toSmallCaps(raw);
    }

    private static String stripColorCodes(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        return input
            .replaceAll("(?i)&#[0-9a-f]{6}", "")
            .replaceAll("(?i)&[0-9a-fk-or]", "")
            .replaceAll("(?i)§[0-9a-fk-or]", "")
            .trim();
    }

    private ItemStack createDisplayItem(SigilType type, double power, boolean owned, boolean active, LoreHints hints) {
        return createDisplayItem(type, power, owned, active, hints, null);
    }

    private ItemStack createDisplayItem(SigilType type, double power, boolean owned, boolean active,
                                        LoreHints hints, Long temporaryExpiresAt) {
        ItemStack item = new ItemStack(type.getDye());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(ColorUtils.colorizeComponent(gradientTitle(type)).decoration(TextDecoration.ITALIC, false));
            List<String> lore = owned
                ? buildOwnedLore(type, power, active, hints, temporaryExpiresAt)
                : buildLockedLore(type);
            meta.lore(ColorUtils.colorizeLore(String.join("\n", lore)));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        NBTUtils.setString(item, SIGIL_TYPE_KEY, type.name());
        if (owned) {
            NBTUtils.setDouble(item, SIGIL_POWER_KEY, power);
        }
        return item;
    }

    private List<String> buildOwnedLore(SigilType type, double power, boolean active, LoreHints hints) {
        return buildOwnedLore(type, power, active, hints, null);
    }

    private List<String> buildOwnedLore(SigilType type, double power, boolean active, LoreHints hints,
                                        Long temporaryExpiresAt) {
        String color = type.getCategory().getColor();
        String value = SigilType.formatValue(power);
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.addAll(buildEffectLore(type, color, value, true));
        lore.add("");
        lore.add("&8≡ &f" + SigilText.toSmallCaps("poziom") + ": " + color + value + "%");
        if (temporaryExpiresAt != null && temporaryExpiresAt > System.currentTimeMillis()) {
            long left = temporaryExpiresAt - System.currentTimeMillis();
            lore.add("&c⏱ &f" + SigilText.toSmallCaps("tymczasowy") + ": &e" + TimeUtil.convert(left));
            lore.add("&7" + SigilText.toSmallCaps("po tym czasie znika"));
        }
        if (hints == LoreHints.GUI) {
            if (active) {
                lore.add("&a" + SigilText.toSmallCaps("aktywny"));
            } else {
                lore.add("&c" + SigilText.toSmallCaps("nieaktywny"));
            }
            lore.add("");
            lore.add("&7● &f" + SigilText.toSmallCaps("kliknij") + " &a" + SigilText.toSmallCaps("lpm")
                + " &f" + SigilText.toSmallCaps("aby aktywowac"));
            lore.add("&7● &f" + SigilText.toSmallCaps("kliknij") + " &e" + SigilText.toSmallCaps("ppm")
                + " &f" + SigilText.toSmallCaps("aby przelosowac") + " &f✕");
        } else if (hints == LoreHints.PHYSICAL) {
            lore.add("&7● &f" + SigilText.toSmallCaps("kliknij") + " &e" + SigilText.toSmallCaps("ppm")
                + " &f" + SigilText.toSmallCaps("aby aktywowac"));
        } else {
            lore.add("&8≡ &f" + SigilText.toSmallCaps("kategoria") + ": " + color
                + SigilText.toSmallCaps(type.getCategory().getDisplayName()));
        }
        return lore;
    }

    private List<String> buildLockedLore(SigilType type) {
        String color = type.getCategory().getColor();
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.addAll(buildEffectLore(type, color, "???", false));
        lore.add("");
        lore.add("&c" + SigilText.toSmallCaps("nie odblokowano"));
        lore.add("");
        lore.add("&7" + SigilText.toSmallCaps("wylosuj ten sigil aby odblokowac") + "!");
        return lore;
    }

    private List<String> buildEffectLore(SigilType type, String color, String value, boolean ownedStyle) {
        List<String> lines = new ArrayList<>();
        if (ownedStyle) {
            lines.add("&8✕ &f" + SigilText.toSmallCaps("moc") + " " + color + value + "% &f"
                + SigilText.toSmallCaps("tego") + " " + color + SigilText.toSmallCaps("sigila")
                + " &f" + SigilText.toSmallCaps("sprawia") + ",");
        } else {
            lines.add("&8✕ &f" + SigilText.toSmallCaps("moc tego") + " " + color + SigilText.toSmallCaps("sigila")
                + " &f" + SigilText.toSmallCaps("sprawia") + ",");
        }

        switch (type) {
            case FURIA -> {
                lines.add("&8✕ &f" + SigilText.toSmallCaps("ze zadajesz") + " " + color + value + "%" );
                lines.add("&8✕ &f" + SigilText.toSmallCaps("wiecej obrazen przy kazdym trafieniu"));
            }
            case GNIEW -> {
                lines.add("&8✕ &f" + SigilText.toSmallCaps("ze zadajesz") + " " + color + "+0.5");
                lines.add("&8✕ &f" + SigilText.toSmallCaps("serca dodatkowych obrazen"));
            }
            case PRZEBICIE -> {
                lines.add("&8✕ &f" + SigilText.toSmallCaps("ze ignorujesz") + " " + color + value + "%");
                lines.add("&8✕ &f" + SigilText.toSmallCaps("pancerza przeciwnika"));
            }
            case WYTRZYMALOSC -> {
                lines.add("&8✕ &f" + SigilText.toSmallCaps("ze otrzymujesz") + " " + color + value + "%");
                lines.add("&8✕ &f" + SigilText.toSmallCaps("mniej obrazen"));
            }
            case TARCZA -> {
                lines.add("&8✕ &f" + SigilText.toSmallCaps("ze masz") + " " + color + value + "% &f" + SigilText.toSmallCaps("szans"));
                lines.add("&8✕ &f" + SigilText.toSmallCaps("na zablokowanie ataku"));
            }
            case ODPORNOSC -> {
                lines.add("&8✕ &f" + SigilText.toSmallCaps("ze masz") + " " + color + value + "% &f" + SigilText.toSmallCaps("szans"));
                lines.add("&8✕ &f" + SigilText.toSmallCaps("na zignorowanie negatywnych efektow"));
            }
            case JAD -> {
                lines.add("&8✕ &f" + SigilText.toSmallCaps("ze nakladasz na przeciwnika"));
                lines.add("&8✕ &f" + SigilText.toSmallCaps("lewitacje") + " " + color + "I &f" + SigilText.toSmallCaps("na") + " " + color + "2s");
            }
            case KLATWA -> {
                lines.add("&8✕ &f" + SigilText.toSmallCaps("ze masz") + " " + color + value + "% &f" + SigilText.toSmallCaps("szans"));
                lines.add("&8✕ &f" + SigilText.toSmallCaps("na oslabienie przeciwnika"));
            }
            case DRENAZ -> {
                lines.add("&8✕ &f" + SigilText.toSmallCaps("ze kradniesz") + " " + color + value + "%");
                lines.add("&8✕ &f" + SigilText.toSmallCaps("obrazen jako zycie"));
            }
            case SKARB -> {
                lines.add("&8✕ &f" + SigilText.toSmallCaps("ze masz") + " " + color + value + "% &f" + SigilText.toSmallCaps("szans"));
                lines.add("&8✕ &f" + SigilText.toSmallCaps("na podwojny drop z generatora"));
            }
            case SZCZESCIE -> {
                lines.add("&8✕ &f" + SigilText.toSmallCaps("ze drop z generatora"));
                lines.add("&8✕ &f" + SigilText.toSmallCaps("zwieksza sie o") + " " + color + value + "%");
            }
            case DOBROBYT -> {
                lines.add("&8✕ &f" + SigilText.toSmallCaps("ze masz") + " " + color + value + "% &f" + SigilText.toSmallCaps("szans"));
                lines.add("&8✕ &f" + SigilText.toSmallCaps("na odzyskanie miecza po smierci"));
            }
            case SPETANIA -> {
                lines.add("&8✕ &f" + SigilText.toSmallCaps("ze masz bardzo mala szanse"));
                lines.add("&8✕ &f" + SigilText.toSmallCaps("na spetanie przeciwnika na") + " " + color + "4s");
            }
            case LOWCY -> {
                lines.add("&8✕ &f" + SigilText.toSmallCaps("ze po zabojstwie gracza masz") + " " + color + value + "%");
                lines.add("&8✕ &f" + SigilText.toSmallCaps("szans na sile") + " " + color + "I &f" + SigilText.toSmallCaps("na") + " " + color + "30s");
            }
            case PODMUCHU -> {
                lines.add("&8✕ &f" + SigilText.toSmallCaps("ze gdy masz lewitacje i cie bija"));
                lines.add("&8✕ &f" + SigilText.toSmallCaps("masz") + " " + color + value + "% &f" + SigilText.toSmallCaps("szans na odepchniecie"));
            }
        }
        return lines;
    }

    private String gradientTitle(SigilType type) {
        return SigilText.gradientBoldTitle(
            type.getDisplayName(),
            type.getCategory().getGradientStart(),
            type.getCategory().getGradientEnd()
        );
    }

    private ItemStack createRerollButton() {
        ItemStack item = new ItemStack(Material.ENDER_EYE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(ColorUtils.colorizeComponent("&e&l" + SigilText.toSmallCaps("przelosuj moc"))
                .decoration(TextDecoration.ITALIC, false));
            meta.lore(ColorUtils.colorizeLore(
                "&7" + SigilText.toSmallCaps("koszt") + ": &f" + (int) Math.ceil(rerollCost) + " "
                    + SigilText.toSmallCaps("odlamkow") + "\n"
                    + "&8● &f" + SigilText.toSmallCaps("kliknij aby przelosowac") + " %"
            ));
            item.setItemMeta(meta);
        }
        NBTUtils.setString(item, SIGIL_ACTION_KEY, "reroll_power");
        return item;
    }

    private ItemStack createBackButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(ColorUtils.colorizeComponent("&c" + SigilText.toSmallCaps("wroc"))
                .decoration(TextDecoration.ITALIC, false));
            meta.lore(ColorUtils.colorizeLore("&7" + SigilText.toSmallCaps("powrot do katalogu")));
            item.setItemMeta(meta);
        }
        NBTUtils.setString(item, SIGIL_ACTION_KEY, "back");
        return item;
    }

    private enum LoreHints {
        NONE,
        GUI,
        PHYSICAL
    }

    private ItemStack createEmptyEquipItem(int index) {
        ItemStack item = new ItemStack(emptyEquipMaterial);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(ColorUtils.colorizeComponent("&7" + SigilText.toSmallCaps("pusty slot") + " &8#" + (index + 1))
                .decoration(TextDecoration.ITALIC, false));
            meta.lore(ColorUtils.colorizeLore("&8• &f" + SigilText.toSmallCaps("aktywuj sigil z katalogu")));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createLockedPremiumSlotItem() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(ColorUtils.colorizeComponent("&c" + SigilText.toSmallCaps("slot premium"))
                .decoration(TextDecoration.ITALIC, false));
            meta.lore(ColorUtils.colorizeLore(
                "&7" + SigilText.toSmallCaps("kup +2 sloty") + "\n&7" + SigilText.toSmallCaps("aby odblokowac")
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createRollButton() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof SkullMeta skullMeta) {
            skullMeta.displayName(ColorUtils.colorizeComponent("&e" + SigilText.toSmallCaps("wylosuj sigil"))
                .decoration(TextDecoration.ITALIC, false));
            skullMeta.lore(ColorUtils.colorizeLore(
                "&7" + SigilText.toSmallCaps("koszt") + ": &f" + (int) Math.ceil(rollCost) + " "
                    + SigilText.toSmallCaps("odlamkow") + "\n&8• &f" + SigilText.toSmallCaps("kliknij aby losowac")
            ));
            item.setItemMeta(skullMeta);
        }
        NBTUtils.setString(item, SIGIL_ACTION_KEY, "roll");
        return item;
    }

    private ItemStack createBuySlotsButton(SigilPlayerData data) {
        ItemStack item = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(ColorUtils.colorizeComponent("&6" + SigilText.toSmallCaps("kup +2 sloty"))
                .decoration(TextDecoration.ITALIC, false));
            List<String> lore = new ArrayList<>();
            lore.add("&7" + SigilText.toSmallCaps("koszt") + ": &f" + (int) Math.ceil(extraSlotsCost) + " "
                + SigilText.toSmallCaps("odlamkow"));
            lore.add("&7" + SigilText.toSmallCaps("czas") + ": &f" + extraSlotsDurationDays + " "
                + SigilText.toSmallCaps("dni"));
            if (data.hasExtraSlots()) {
                long remaining = Math.max(0L, data.getExtraSlotsExpiresAtMillis() - System.currentTimeMillis());
                long hours = TimeUnit.MILLISECONDS.toHours(remaining);
                lore.add("&a" + SigilText.toSmallCaps("aktywne") + ": &f" + hours + "h");
                lore.add("&8• &f" + SigilText.toSmallCaps("kliknij aby przedluzyc"));
            } else {
                lore.add("&8• &f" + SigilText.toSmallCaps("kliknij aby kupic"));
            }
            meta.lore(ColorUtils.colorizeLore(String.join("\n", lore)));
            item.setItemMeta(meta);
        }
        NBTUtils.setString(item, SIGIL_ACTION_KEY, "buy_slots");
        return item;
    }

    private boolean consumeShards(Player player, double rawAmount) {
        int amount = shardCost(rawAmount);
        if (amount <= 0) {
            return true;
        }

        int available = countShardItems(player.getInventory());
        if (available < amount) {
            send(player, config.getString("messages.not-enough-shards",
                "&#FF0000✘ &8Ξ &cNie masz wystarczajaco odlamkow. Potrzebujesz &f{AMOUNT}&c.")
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

    private boolean matchesShard(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }

        PerksManager szaman = plugin.getSzamanManager();
        if (szaman != null && szaman.matchesShard(item)) {
            return true;
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

        if (shardName != null && !shardName.isBlank()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasDisplayName()) {
                String name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(meta.displayName());
                String expected = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                    .serialize(ColorUtils.colorizeComponent(shardName));
                String plainExpected = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(ColorUtils.colorizeComponent(shardName));
                if (name.equalsIgnoreCase(plainExpected) || name.equalsIgnoreCase(expected)) {
                    return true;
                }
            }
        }
        return false;
    }

    private SigilType randomType() {
        SigilType[] values = SigilType.values();
        return values[random.nextInt(values.length)];
    }

    private double clampPower(double power) {
        if (!Double.isFinite(power)) {
            return minPower;
        }
        return Math.max(minPower, Math.min(maxPower, power));
    }

    /** Moc dla grantów admina (np. 30%) — nie jest ograniczona maxPower z losowań. */
    private double clampAdminPower(double power) {
        if (!Double.isFinite(power)) {
            return minPower;
        }
        return Math.max(1.0D, Math.min(100.0D, power));
    }

    private boolean hasEmptyStorageSlot(Player player) {
        return player.getInventory().firstEmpty() != -1;
    }

    private void giveItem(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        for (ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    private void consumeOne(Player player, ItemStack item) {
        if (item == null) {
            return;
        }
        int amount = item.getAmount();
        if (amount <= 1) {
            player.getInventory().setItemInMainHand(null);
        } else {
            item.setAmount(amount - 1);
        }
    }

    private boolean tryStartAction(Player player) {
        return pendingActions.add(player.getUniqueId());
    }

    private void finishAction(Player player) {
        pendingActions.remove(player.getUniqueId());
    }

    private void setIfValid(Inventory inventory, int slot, ItemStack item) {
        if (slot < 0 || slot >= inventory.getSize() || item == null) {
            return;
        }
        inventory.setItem(slot, item);
    }

    private void send(Player player, String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        player.sendMessage(ColorUtils.colorizeComponent(message));
    }

    private void playSound(Player player, Sound sound) {
        if (sound == null) {
            return;
        }
        player.playSound(player.getLocation(), sound, 0.8F, 1.0F);
    }

    private List<Integer> readIntList(String path, List<Integer> fallback) {
        List<Integer> list = config.getIntegerList(path);
        if (list == null || list.isEmpty()) {
            return fallback;
        }
        return List.copyOf(list);
    }

    private int[] parseAnimDelays(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return SigilScreenRollAnimator.DEFAULT_DELAYS.clone();
        }
        int[] out = new int[raw.size()];
        for (int i = 0; i < raw.size(); i++) {
            try {
                out[i] = Math.max(1, Integer.parseInt(raw.get(i).trim()));
            } catch (NumberFormatException e) {
                out[i] = 1;
            }
        }
        return out;
    }

    private Material readMaterial(String path, Material fallback) {
        return parseMaterial(config.getString(path), fallback);
    }

    private Material parseMaterial(String raw, Material fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Material.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private Sound readSound(String path, Sound fallback) {
        String raw = config.getString(path);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Sound.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
