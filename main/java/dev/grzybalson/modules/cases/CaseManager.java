package dev.grzybalson.modules.cases;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.grzybalson.HollyCore;
import dev.grzybalson.config.HollyDataPaths;
import dev.grzybalson.modules.minestarogcustomitems.MinestarOGCustomItemsManager;
import dev.grzybalson.modules.oghubcustomitems.OgHubCustomItemsManager;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.ConfigItemStackReader;
import dev.grzybalson.utils.ItemSerializer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CaseManager {

    private static final String CONFIG_RESOURCE = "addons/cases/cases-settings.yml";
    private static final String CASES_RESOURCE = "addons/cases/cases.yml";
    private static final String COBWEB_REMOVER_ID = "usuwacz-pajeczyn";
    private static final String LEGACY_NEED_KEY_MESSAGE = "&#FF0000✘ &8Ξ &cMusisz trzymac klucz do tej skrzynki w glownej rece.";
    private static final String NEED_KEY_MESSAGE = "&#FF0000✘ &8Ξ &cMusisz mieć klucz do tej skrzynki w ekwipunku.";
    private static final String INVENTORY_FULL_MESSAGE = "&#FF0000✘ &8Ξ &cNie możesz otworzyć skrzynki z pełnym ekwipunkiem.";
    private static final double MIN_EFFECTIVE_CHANCE = 0.0001D;
    private static final DecimalFormat CHANCE_FORMAT = new DecimalFormat("0.#", DecimalFormatSymbols.getInstance(Locale.US));
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final Pattern CHANCE_NUMBER_PATTERN = Pattern.compile("[+-]?\\d+(?:\\.\\d+)?");
    private static final int[] DEFAULT_ANIM_DELAYS = {
        1, 1, 1, 1, 1, 1, 1, 1,
        1, 1, 2, 2, 2, 2,
        3, 3, 3,
        4, 4, 5,
        6, 8,
        10, 13,
        18,
        6,
        25
    };

    private final HollyCore plugin;
    private final File configFile;
    private final File casesFile;
    private final NamespacedKey chanceValueKey;
    private final Random random = new Random();
    private final Map<String, CaseDefinition> cases = new LinkedHashMap<>();
    private final Map<CaseBlockLocation, String> casesByLocation = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private final GlobalCaseOpeningLimiter globalOpeningLimiter = new GlobalCaseOpeningLimiter(20, 15_000L);
    private final Map<UUID, java.util.concurrent.atomic.AtomicBoolean> animationCancelled = new ConcurrentHashMap<>();
    private final Map<UUID, CaseOpenResult> animationResults = new ConcurrentHashMap<>();
    private final Map<String, RewardRotation> rewardRotations = new LinkedHashMap<>();

    // Memory leak fix: Use Caffeine cache with TTL instead of unbounded ConcurrentHashMap
    private final Cache<String, Long> rewardRotationOffsets = Caffeine.newBuilder()
        .maximumSize(500)
        .expireAfterWrite(30, TimeUnit.MINUTES)
        .build();

    private final Map<CaseReward, RewardDisplayCache> rewardDisplayCache = Collections.synchronizedMap(new IdentityHashMap<>());
    private final Map<String, ItemStack[]> previewContentsCache = new ConcurrentHashMap<>();
    private final Map<Integer, Set<Integer>> editorSaveSlotsCache = new HashMap<>();
    private final Set<UUID> openingPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, NinjaBoxWorldAnimator> ninjaBoxAnimations = new ConcurrentHashMap<>();

    private YamlConfiguration config;
    private YamlConfiguration casesConfig;
    private GuiLayout previewLayout = GuiLayout.empty("&8Podglad skrzynki!", 6);
    private GuiLayout resultLayout = GuiLayout.empty("&7Losowanko....", 3);
    private GuiLayout animationLayout = GuiLayout.empty("&7Losowanko....", 3);
    private GuiLayout editorLayout = GuiLayout.empty("&8Edycja skrzynki: {case}", 6);
    private int itemSlot = 13;
    private int animationFinalSlot = 13;
    private int animationTicks = 40;
    private int animationFramePeriodTicks = 2;
    private int[] animationDelayTicks = DEFAULT_ANIM_DELAYS.clone();
    private List<Integer> animationRollSlots = List.of(10, 11, 12, 13, 14, 15, 16);
    private List<Integer> effectiveAnimationRollSlots = List.of(10, 11, 12, 13, 14, 15, 16);
    private int effectiveAnimationFinalSlot = 13;
    private List<Integer> caseBattleSlots = List.of(4);
    private Set<Integer> caseBattleSlotSet = Set.of(4);
    private ItemStack caseBattleItem = new ItemStack(Material.LECTERN);
    private CaseOpenType luckyBlockOpenType = CaseOpenType.ANIMATION;
    private long openCooldownMillis = 300L;
    private boolean consumeKey = true;
    private boolean globalMessageEnabled = false;
    private String globalMessage = "&#00FF3B✔ &8Ξ &#4EFA76Gracz &f{player} &aotworzył skrzynkę &f{case} &ai wylosował &f{item} &az szansą &e{chance}%&a!";
    private String caseBattleDisabledTitle = "&cBitwy na skrzynki";
    private String caseBattleDisabledSubtitle = "&7są aktualnie wyłączone";
    private int caseBattleTitleFadeIn = 10;
    private int caseBattleTitleStay = 40;
    private int caseBattleTitleFadeOut = 10;
    private volatile ItemStack[] resultTemplateContents;
    private volatile ItemStack[] animationTemplateContents;
    private BukkitTask rewardRotationTask;

    public CaseManager(HollyCore plugin) {
        this.plugin = plugin;
        dev.grzybalson.internal.HollyIntegrityGuard.requireBound(plugin, "random:seed");
        this.configFile = new File(plugin.getDataFolder(), CONFIG_RESOURCE);
        this.casesFile = new File(plugin.getDataFolder(), CASES_RESOURCE);
        this.chanceValueKey = new NamespacedKey(plugin, "case-chance-value");
    }

    public void reload() {
        HollyDataPaths.ensureResource(plugin, CONFIG_RESOURCE);
        HollyDataPaths.ensureResource(plugin, CASES_RESOURCE);
        this.config = YamlConfiguration.loadConfiguration(configFile);
        this.casesConfig = YamlConfiguration.loadConfiguration(casesFile);
        clearRuntimeGuiCaches();
        migrateLegacyCasesFromConfig();
        ensureRuntimeDefaults();
        ensureCasesDefaults();
        loadGuiSettings();
        loadCases();
        loadRewardRotations();
        restartRewardRotationTask();
    }

    private void ensureRuntimeDefaults() {
        boolean changed = false;
        changed |= setIfMissing("settings.openCooldownMillis", 300);
        changed |= setIfMissing("settings.global-open-limit.max-openings", 20);
        changed |= setIfMissing("settings.global-open-limit.window-seconds", 15);
        changed |= setIfMissing("messages.global-open-limit", "&cLimit otwierania skrzynek na serwerze został osiągnięty. Spróbuj ponownie za &f{time}s&c.");
        if (config.getLong("settings.openCooldownMillis", 300L) >= 1000L
            && config.getInt("settings.cooldown-version", 0) < 1) {
            config.set("settings.openCooldownMillis", 300);
            config.set("settings.cooldown-version", 1);
            changed = true;
        }
        changed |= setIfMissing("settings.consumeKey", true);
        changed |= setIfMissing("settings.luckyBlockOpenType", "ANIMATION");
        changed |= setIfMissing("sounds.open-gui.enabled", true);
        changed |= setIfMissing("sounds.open-gui.sound", "UI_BUTTON_CLICK");
        changed |= setIfMissing("sounds.open-gui.volume", 1.0D);
        changed |= setIfMissing("sounds.open-gui.pitch", 1.0D);
        changed |= setIfMissing("messages.global.enabled", false);
        changed |= setIfMissing("messages.global.text", globalMessage);
        changed |= setIfMissing("messages.message-enabled", "&#00FF3B✔ &8Ξ &#4EFA76Włączono powiadomienie o otwarciu skrzynki &f{case}&a.");
        changed |= setIfMissing("messages.message-disabled", "&#00FF3B✔ &8Ξ &#4EFA76Wyłączono powiadomienie o otwarciu skrzynki &f{case}&a.");
        changed |= setIfMissing("messages.only-player", "&#FF0000✘ &8Ξ &cTa komenda jest tylko dla gracza.");
        changed |= setIfMissing("messages.case-created", "&#00FF3B✔ &8Ξ &#4EFA76Utworzono skrzynkę &f{case}&a.");
        changed |= setIfMissing("messages.case-exists", "&#FF0000✘ &8Ξ &cSkrzynka &f{case} &cjuż istnieje.");
        changed |= setIfMissing("messages.case-not-found", "&#FF0000✘ &8Ξ &cSkrzynka &f{case} &cnie istnieje.");
        changed |= setIfMissing("messages.location-set", "&#00FF3B✔ &8Ξ &#4EFA76Ustawiono lokalizację skrzynki &f{case}&a.");
        changed |= setIfMissing("messages.location-removed", "&#00FF3B✔ &8Ξ &#4EFA76Usunięto lokalizację skrzynki &f{case}&a.");
        changed |= setIfMissing("messages.location-not-set", "&#FF0000✘ &8Ξ &cSkrzynka &f{case} &cnie ma ustawionej lokalizacji.");
        changed |= setIfMissing("messages.no-target-block", "&#FF0000✘ &8Ξ &cMusisz patrzeć na blok.");
        changed |= setIfMissing("messages.enabled", "&#00FF3B✔ &8Ξ &#4EFA76Włączono skrzynkę &f{case}&a.");
        changed |= setIfMissing("messages.disabled", "&#00FF3B✔ &8Ξ &#4EFA76Wyłączono skrzynkę &f{case}&a.");
        changed |= setIfMissing("messages.key-set", "&#00FF3B✔ &8Ξ &#4EFA76Ustawiono klucz do skrzynki &f{case}&a.");
        changed |= setIfMissing("messages.key-given", "&#00FF3B✔ &8Ξ &#4EFA76Nadano &fx{amount} &akluczy do skrzynki &f{case} &adla &f{player}&a.");
        changed |= setIfMissing("messages.key-given-all", "&#00FF3B✔ &8Ξ &#4EFA76Nadano &fx{amount} &akluczy do skrzynki &f{case} &adla wszystkich online.");
        changed |= setIfMissing("messages.invalid-amount", "&#FF0000✘ &8Ξ &cPodaj poprawną ilość większą od 0.");
        changed |= setIfMissing("messages.player-not-found", "&#FF0000✘ &8Ξ &cGracz &f{player} &cnie jest online.");
        changed |= setIfMissing("messages.no-key-in-hand", "&#FF0000✘ &8Ξ &cMusisz trzymać klucz w głównej ręce.");
        changed |= setIfMissing("messages.need-key", NEED_KEY_MESSAGE);
        changed |= setIfMissing("messages.key-not-configured", "&#FF0000✘ &8Ξ &cTa skrzynka nie ma ustawionego klucza.");
        changed |= setIfMissing("messages.case-disabled", "&#FF0000✘ &8Ξ &cTa skrzynka jest aktualnie wyłączona.");
        changed |= setIfMissing("messages.empty-case", "&#FF0000✘ &8Ξ &cTa skrzynka nie ma jeszcze przedmiotów.");
        changed |= setIfMissing("messages.cooldown", "&#FF0000✘ &8Ξ &cOdczekaj chwilę przed kolejnym otwarciem.");
        changed |= setIfMissing("messages.already-opening", "&#FF0000✘ &8Ξ &cJuż otwierasz skrzynkę.");
        changed |= setIfMissing("messages.ninjabox-busy", "&#FF0000✘ &8Ξ &cJuż otwierasz Ninja Box — poczekaj na koniec animacji.");
        changed |= setIfMissing("messages.inventory-full", INVENTORY_FULL_MESSAGE);
        changed |= setIfMissing("messages.editor-saved", "&#00FF3B✔ &8Ξ &#4EFA76Zapisano przedmioty skrzynki &f{case}&a.");
        changed |= setIfMissing("messages.invalid-chance", "&#FF0000✘ &8Ξ &cPodaj poprawną szansę od 0 do 100.");
        changed |= setIfMissing("messages.chance-set", "&#00FF3B✔ &8Ξ &#4EFA76Ustawiono szansę &f{chance}% &adla itemu w skrzynce &f{case}&a.");
        changed |= setIfMissing("messages.chance-input-start", "&#FFB800⚠ &8Ξ &7Wpisz na czacie szansę od &f0 &7do &f100 &7dla itemu w skrzynce &f{case}&7. Wpisz &canuluj &7aby anulować.");
        changed |= setIfMissing("messages.chance-input-cancelled", "&#FF0000✘ &8Ξ &cAnulowano ustawianie szansy w skrzynce &f{case}&c.");
        changed |= setIfMissing("messages.rotation-forced", "&#00FF3B✔ &8Ξ &#4EFA76Wymuszono rotację custom itemu w skrzynce &f{case}&a. Aktualny item: &f{item}&a. Następna zmiana za: &e{time}&a.");
        changed |= setIfMissing("messages.rotation-not-available", "&#FF0000✘ &8Ξ &cRotacja custom itemu dla skrzynki &f{case} &cnie jest dostępna.");
        changed |= setIfMissing("messages.reloaded", "&#00FF3B✔ &8Ξ &#4EFA76Przeładowano skrzynki.");
        changed |= setIfMissing("caseBattle.disabledTitle", "&cBitwy na skrzynki");
        changed |= setIfMissing("caseBattle.disabledSubtitle", "&7są aktualnie wyłączone");
        changed |= setIfMissing("caseBattle.titleFadeIn", 10);
        changed |= setIfMissing("caseBattle.titleStay", 40);
        changed |= setIfMissing("caseBattle.titleFadeOut", 10);
        changed |= setIfMissing("reward-rotations.luckyblock.enabled", true);
        changed |= setIfMissing("reward-rotations.luckyblock.interval-minutes", 120);
        changed |= setIfMissing("reward-rotations.luckyblock.source-item-id", "hak");
        changed |= setIfMissing("reward-rotations.luckyblock.item-ids", List.of(
            "hak",
            "rozdzka-harrego-pottera",
            "smocza-fajerwerka",
            "granat-odepchniecia",
            "sniezka-teleportacji",
            "granat-uwiezienia"
        ));
        if (LEGACY_NEED_KEY_MESSAGE.equals(config.getString("messages.need-key"))) {
            config.set("messages.need-key", NEED_KEY_MESSAGE);
            changed = true;
        }

        if (changed) {
            saveConfig();
        }
    }

    private void ensureCasesDefaults() {
        if (casesConfig.isSet("cases")) {
            return;
        }
        casesConfig.set("cases", new LinkedHashMap<>());
        saveCasesConfig();
    }

    private void migrateLegacyCasesFromConfig() {
        ConfigurationSection legacyCases = config.getConfigurationSection("cases");
        if (legacyCases == null) {
            return;
        }

        boolean changedCases = false;
        for (String caseName : legacyCases.getKeys(false)) {
            if (casesConfig.isSet("cases." + caseName)) {
                continue;
            }
            ConfigurationSection legacyCase = legacyCases.getConfigurationSection(caseName);
            if (legacyCase == null) {
                casesConfig.set("cases." + caseName, legacyCases.get(caseName));
                changedCases = true;
                continue;
            }
            ConfigurationSection targetCase = casesConfig.createSection("cases." + caseName);
            copySection(legacyCase, targetCase);
            changedCases = true;
        }

        config.set("cases", null);
        saveConfig();
        if (changedCases) {
            saveCasesConfig();
        }
    }

    private void copySection(ConfigurationSection source, ConfigurationSection target) {
        for (String key : source.getKeys(false)) {
            ConfigurationSection child = source.getConfigurationSection(key);
            if (child != null) {
                copySection(child, target.createSection(key));
                continue;
            }
            target.set(key, source.get(key));
        }
    }

    private boolean setIfMissing(String path, Object value) {
        if (config.isSet(path)) {
            return false;
        }
        config.set(path, value);
        return true;
    }

    private void loadGuiSettings() {
        openCooldownMillis = Math.max(0L, config.getLong("settings.openCooldownMillis", 300L));
        globalOpeningLimiter.configure(
            Math.max(1, config.getInt("settings.global-open-limit.max-openings", 20)),
            TimeUnit.SECONDS.toMillis(Math.max(1L, config.getLong("settings.global-open-limit.window-seconds", 15L))));
        consumeKey = config.getBoolean("settings.consumeKey", true);
        luckyBlockOpenType = parseRewardOpenType(config.getString("settings.luckyBlockOpenType", "ANIMATION"), CaseOpenType.ANIMATION);
        globalMessageEnabled = config.getBoolean("messages.global.enabled", false);
        globalMessage = config.getString("messages.global.text", globalMessage);
        caseBattleDisabledTitle = config.getString("caseBattle.disabledTitle", caseBattleDisabledTitle);
        caseBattleDisabledSubtitle = config.getString("caseBattle.disabledSubtitle", caseBattleDisabledSubtitle);
        caseBattleTitleFadeIn = Math.max(0, config.getInt("caseBattle.titleFadeIn", 10));
        caseBattleTitleStay = Math.max(1, config.getInt("caseBattle.titleStay", 40));
        caseBattleTitleFadeOut = Math.max(0, config.getInt("caseBattle.titleFadeOut", 10));

        caseBattleSlots = readIntegerList(config.get("caseBattleSlots"));
        if (caseBattleSlots.isEmpty()) {
            caseBattleSlots = List.of(4);
        }
        caseBattleSlotSet = Set.copyOf(caseBattleSlots);
        ItemStack configuredCaseBattleItem = ConfigItemStackReader.getItem(config, "caseBattleItem");
        caseBattleItem = configuredCaseBattleItem == null || configuredCaseBattleItem.getType().isAir()
            ? new ItemStack(Material.LECTERN)
            : configuredCaseBattleItem;

        previewLayout = readLayout("caseKeyTypeInventory", "&8Podglad skrzynki!", 6);
        resultLayout = readLayout("caseWithOutAnimation", "&7Losowanko....", 3);
        animationLayout = readLayout("caseWithAnimation", resultLayout.title(), 3);
        editorLayout = readLayout("editorInventory", "&8Edycja skrzynki: {case}", 6);
        editorSaveSlotsCache.clear();

        itemSlot = Math.max(0, config.getInt("itemSlot", 13));
        animationFinalSlot = Math.max(0, config.getInt("caseWithAnimation.finalSlot", itemSlot));
        animationTicks = Math.max(1, config.getInt("caseWithAnimation.animationTicks", 40));
        animationFramePeriodTicks = Math.max(1, config.getInt("caseWithAnimation.framePeriodTicks", 2));
        animationRollSlots = readIntegerList(config.get("caseWithAnimation.rollSlots"));
        if (animationRollSlots.isEmpty()) {
            animationRollSlots = List.of(10, 11, 12, 13, 14, 15, 16);
        }
        animationDelayTicks = parseAnimDelayTicks(config.getStringList("caseWithAnimation.anim-delay-ticks"));
        rebuildAnimationSlotCache();
    }

    private int[] parseAnimDelayTicks(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return DEFAULT_ANIM_DELAYS.clone();
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

    private void rebuildAnimationSlotCache() {
        int animationSize = inventorySize(animationLayout.rows());
        effectiveAnimationFinalSlot = animationFinalSlot >= 0 && animationFinalSlot < animationSize ? animationFinalSlot : animationSize / 2;
        List<Integer> rollSlots = new ArrayList<>();
        for (int slot : animationRollSlots) {
            if (slot >= 0 && slot < animationSize) {
                rollSlots.add(slot);
            }
        }
        effectiveAnimationRollSlots = rollSlots.isEmpty()
            ? List.of(Math.min(effectiveAnimationFinalSlot, animationSize - 1))
            : List.copyOf(rollSlots);
    }

    private GuiLayout readLayout(String path, String defaultTitle, int defaultRows) {
        String title = firstString(defaultTitle, path + ".inventoryName", path + ".title");
        int rows = normalizeRows(config.getInt(path + ".inventoryRows", config.getInt(path + ".rows", defaultRows)));
        List<CaseGuiDecoration> decorations = readDecorations(path + ".decoration");
        List<CaseGuiButton> buttons = readButtons(path + ".caseInventoryItems");
        if (buttons.isEmpty()) {
            buttons = readButtons(path + ".buttons");
        }
        return new GuiLayout(title, rows, decorations, buttons, buildButtonSlots(buttons));
    }

    private Map<Integer, CaseOpenType> buildButtonSlots(List<CaseGuiButton> buttons) {
        if (buttons == null || buttons.isEmpty()) {
            return Map.of();
        }

        Map<Integer, CaseOpenType> slots = new HashMap<>();
        for (CaseGuiButton button : buttons) {
            for (int slot : button.slots()) {
                slots.put(slot, button.type());
            }
        }
        return Map.copyOf(slots);
    }

    private String firstString(String fallback, String... paths) {
        for (String path : paths) {
            String value = config.getString(path);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return fallback;
    }

    private List<CaseGuiDecoration> readDecorations(String path) {
        List<Map<?, ?>> maps = config.getMapList(path);
        if (maps.isEmpty()) {
            return Collections.emptyList();
        }

        List<CaseGuiDecoration> decorations = new ArrayList<>();
        for (Map<?, ?> map : maps) {
            List<Integer> slots = readIntegerList(firstPresent(map, "slot", "slots"));
            ItemStack item = readConfigItem(firstPresent(map, "item", "itemStack"));
            if (!slots.isEmpty() && item != null && !item.getType().isAir()) {
                decorations.add(new CaseGuiDecoration(slots, item));
            }
        }
        return decorations;
    }

    private List<CaseGuiDecoration> readDecorations(ConfigurationSection section, String path) {
        List<Map<?, ?>> maps = section.getMapList(path);
        if (maps.isEmpty()) {
            return Collections.emptyList();
        }

        List<CaseGuiDecoration> decorations = new ArrayList<>();
        for (Map<?, ?> map : maps) {
            List<Integer> slots = readIntegerList(firstPresent(map, "slot", "slots"));
            ItemStack item = readConfigItem(firstPresent(map, "item", "itemStack"));
            if (!slots.isEmpty() && item != null && !item.getType().isAir()) {
                decorations.add(new CaseGuiDecoration(slots, item));
            }
        }
        return decorations;
    }

    private List<CaseGuiButton> readButtons(String path) {
        List<Map<?, ?>> maps = config.getMapList(path);
        if (maps.isEmpty()) {
            return Collections.emptyList();
        }

        List<CaseGuiButton> buttons = new ArrayList<>();
        for (Map<?, ?> map : maps) {
            String name = String.valueOf(firstPresent(map, "name", "id"));
            String typeName = String.valueOf(firstPresent(map, "caseInventoryType", "type"));
            CaseOpenType type = parseOpenType(typeName);
            List<Integer> slots = readIntegerList(firstPresent(map, "slot", "slots"));
            ItemStack item = readConfigItem(firstPresent(map, "itemStack", "item"));
            if (type != null && !slots.isEmpty() && item != null && !item.getType().isAir()) {
                buttons.add(new CaseGuiButton(name, type, slots, item));
            }
        }
        return buttons;
    }

    private Object firstPresent(Map<?, ?> map, String... keys) {
        if (map == null) {
            return null;
        }
        for (String key : keys) {
            if (map.containsKey(key)) {
                return map.get(key);
            }
        }
        return null;
    }

    private CaseOpenType parseOpenType(String raw) {
        if (raw == null || raw.isBlank() || raw.equals("null")) {
            return null;
        }
        try {
            return CaseOpenType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private CaseOpenType parseRewardOpenType(String raw, CaseOpenType fallback) {
        CaseOpenType type = parseOpenType(raw);
        return type == CaseOpenType.ANIMATION || type == CaseOpenType.NO_ANIMATION ? type : fallback;
    }

    private ItemStack readConfigItem(Object raw) {
        if (raw instanceof ItemStack itemStack) {
            return itemStack.clone();
        }
        if (!(raw instanceof Map<?, ?> map)) {
            return null;
        }

        YamlConfiguration temporary = new YamlConfiguration();
        ConfigurationSection section = temporary.createSection("item");
        copyMap(section, map);
        ItemStack item = ConfigItemStackReader.getItem(temporary, "item");
        return item == null ? null : item;
    }

    @SuppressWarnings("unchecked")
    private void copyMap(ConfigurationSection target, Map<?, ?> map) {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = String.valueOf(entry.getKey());
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> childMap) {
                ConfigurationSection child = target.createSection(key);
                copyMap(child, childMap);
                continue;
            }
            if (value instanceof List<?> list) {
                target.set(key, list);
                continue;
            }
            target.set(key, value);
        }
    }

    private List<Integer> readIntegerList(Object raw) {
        if (raw == null) {
            return Collections.emptyList();
        }

        List<Integer> slots = new ArrayList<>();
        if (raw instanceof Iterable<?> iterable) {
            for (Object value : iterable) {
                Integer parsed = parseInteger(value);
                if (parsed != null) {
                    slots.add(parsed);
                }
            }
            return slots;
        }

        Integer parsed = parseInteger(raw);
        if (parsed != null) {
            slots.add(parsed);
        }
        return slots;
    }

    private Integer parseInteger(Object raw) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(raw).trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void loadCases() {
        cases.clear();
        casesByLocation.clear();

        ConfigurationSection section = casesConfig.getConfigurationSection("cases");
        if (section == null) {
            return;
        }

        boolean changed = false;
        for (String key : section.getKeys(false)) {
            ConfigurationSection caseSection = section.getConfigurationSection(key);
            if (caseSection == null) {
                continue;
            }

            CaseDefinition definition = loadCase(key, caseSection);
            cases.put(normalizeCaseName(key), definition);
            for (CaseBlockLocation location : definition.locations()) {
                casesByLocation.put(location, normalizeCaseName(key));
            }
            if (needsCaseFormatMigration(caseSection, definition)) {
                saveCase(definition);
                changed = true;
            }
        }

        if (changed) {
            saveCasesConfig();
        }
    }

    private void loadRewardRotations() {
        rewardRotations.clear();
        ConfigurationSection section = config.getConfigurationSection("reward-rotations");
        if (section == null) {
            return;
        }

        for (String caseName : section.getKeys(false)) {
            String path = "reward-rotations." + caseName;
            if (!config.getBoolean(path + ".enabled", true)) {
                continue;
            }

            long intervalMinutes = Math.max(1L, config.getLong(path + ".interval-minutes", 120L));
            String sourceItemId = normalizeCustomItemId(config.getString(path + ".source-item-id", "hak"));
            List<String> itemIds = config.getStringList(path + ".item-ids").stream()
                .map(this::normalizeCustomItemId)
                .filter(id -> !id.isBlank())
                .filter(id -> !COBWEB_REMOVER_ID.equals(id))
                .distinct()
                .toList();

            if (itemIds.isEmpty()) {
                continue;
            }

            long intervalMillis = Math.max(60_000L, intervalMinutes * 60_000L);
            rewardRotations.put(normalizeCaseName(caseName), new RewardRotation(normalizeCaseName(caseName), intervalMillis, sourceItemId, List.copyOf(itemIds)));
        }
    }

    private void restartRewardRotationTask() {
        if (rewardRotationTask != null) {
            rewardRotationTask.cancel();
            rewardRotationTask = null;
        }
        if (rewardRotations.isEmpty()) {
            return;
        }
        scheduleNextRewardRotationTask();
    }

    private void scheduleNextRewardRotationTask() {
        if (rewardRotations.isEmpty()) {
            return;
        }

        long delayMillis = getNextRewardRotationDelayMillis();
        long delayTicks = Math.max(1L, (delayMillis + 49L) / 50L);
        rewardRotationTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            rewardRotationTask = null;
            applyRewardRotations(true);
            scheduleNextRewardRotationTask();
        }, delayTicks);
    }

    private long getNextRewardRotationDelayMillis() {
        long now = System.currentTimeMillis();
        long delay = Long.MAX_VALUE;
        for (RewardRotation rotation : rewardRotations.values()) {
            delay = Math.min(delay, rotation.millisUntilNextChange(now));
        }
        return delay == Long.MAX_VALUE ? 60_000L : delay;
    }

    private void applyRewardRotations(boolean forceDifferent) {
        if (rewardRotations.isEmpty()) {
            return;
        }

        CustomItemSource customItemsManager = resolveCustomItemSource();
        if (customItemsManager == null) {
            return;
        }

        long now = System.currentTimeMillis();
        for (RewardRotation rotation : rewardRotations.values()) {
            applyRewardRotation(rotation, customItemsManager, now, forceDifferent);
        }
    }

    private boolean applyRewardRotation(RewardRotation rotation, CustomItemSource customItemsManager, long now, boolean forceDifferent) {
        CaseDefinition definition = getCase(rotation.caseName());
        if (definition == null || definition.rewards().isEmpty()) {
            return false;
        }

        String selectedId = currentRotationItemId(rotation, now);
        String currentItemId = findCurrentRotationItemId(definition, rotation, customItemsManager);
        if (forceDifferent && rotation.itemIds().size() > 1 && !currentItemId.isBlank() && selectedId.equals(currentItemId)) {
            selectedId = moveRotationOffsetUntilDifferent(rotation, now, currentItemId);
        }

        ItemStack selectedItem = customItemsManager.getConfiguredItem(selectedId);
        if (selectedItem == null || selectedItem.getType().isAir()) {
            return false;
        }

        List<CaseReward> updatedRewards = new ArrayList<>(definition.rewards().size());
        boolean replaced = false;
        boolean changed = false;

        for (CaseReward reward : definition.rewards()) {
            ItemStack rewardItem = reward.item().clone();
            String currentId = normalizeCustomItemId(customItemsManager.identifyCustomItem(reward.item()));

            if (!replaced && rotation.matches(currentId)) {
                replaced = true;
                if (selectedId.equals(currentId)) {
                    updatedRewards.add(new CaseReward(rewardItem, reward.chance(), reward.editorSlot()));
                    continue;
                }

                ItemStack rotatedItem = selectedItem.clone();
                rotatedItem.setAmount(Math.max(1, Math.min(reward.item().getAmount(), rotatedItem.getMaxStackSize())));
                updatedRewards.add(new CaseReward(rotatedItem, reward.chance(), reward.editorSlot()));
                changed = true;
                continue;
            }

            updatedRewards.add(new CaseReward(rewardItem, reward.chance(), reward.editorSlot()));
        }

        if (changed) {
            definition.replaceRewards(updatedRewards);
            rewardDisplayCache.clear();
            saveCase(definition);
        }
        return changed;
    }

    public RotationResult forceDefaultRewardRotation() {
        return forceRewardRotation("luckyblock");
    }

    public RotationResult forceRewardRotation(String caseName) {
        String normalizedCase = normalizeCaseName(caseName == null || caseName.isBlank() ? "luckyblock" : caseName);
        RewardRotation rotation = rewardRotations.get(normalizedCase);
        if (rotation == null) {
            return RotationResult.unavailable(caseName == null || caseName.isBlank() ? "luckyblock" : caseName);
        }

        CustomItemSource customItemsManager = resolveCustomItemSource();
        if (customItemsManager == null) {
            return RotationResult.unavailable(rotation.caseName());
        }

        CaseDefinition definition = getCase(rotation.caseName());
        if (definition == null || definition.rewards().isEmpty()) {
            return RotationResult.unavailable(rotation.caseName());
        }

        String currentItemId = findCurrentRotationItemId(definition, rotation, customItemsManager);
        if (currentItemId.isBlank()) {
            return RotationResult.unavailable(rotation.caseName());
        }

        long now = System.currentTimeMillis();
        String selectedId = currentRotationItemId(rotation, now);
        if (selectedId.equals(currentItemId)) {
            selectedId = moveRotationOffsetUntilDifferent(rotation, now, currentItemId);
        }

        boolean changed = applyRewardRotation(rotation, customItemsManager, now, false);
        return new RotationResult(changed, rotation.caseName(), selectedId, getRewardRotationCountdown(rotation.caseName()));
    }

    private String moveRotationOffsetUntilDifferent(RewardRotation rotation, long now, String currentItemId) {
        Long cachedOffset = rewardRotationOffsets.getIfPresent(rotation.caseName());
        long offset = cachedOffset != null ? cachedOffset : 0L;
        String selectedId = currentRotationItemId(rotation, now);
        for (int attempts = 0; attempts < rotation.itemIds().size() + 3 && selectedId.equals(currentItemId); attempts++) {
            offset++;
            rewardRotationOffsets.put(rotation.caseName(), offset);
            selectedId = currentRotationItemId(rotation, now);
        }
        return selectedId;
    }

    private String findCurrentRotationItemId(CaseDefinition definition, RewardRotation rotation, CustomItemSource customItemsManager) {
        for (CaseReward reward : definition.rewards()) {
            String currentId = normalizeCustomItemId(customItemsManager.identifyCustomItem(reward.item()));
            if (rotation.matches(currentId)) {
                return currentId;
            }
        }
        return "";
    }

    private String currentRotationItemId(RewardRotation rotation, long now) {
        Long cachedOffset = rewardRotationOffsets.getIfPresent(rotation.caseName());
        long offset = cachedOffset != null ? cachedOffset : 0L;
        return rotation.currentItemId(now, offset);
    }

    public String getDefaultRewardRotationCountdown() {
        return getRewardRotationCountdown("luckyblock");
    }

    public String getRewardRotationCountdown(String caseName) {
        RewardRotation rotation = rewardRotations.get(normalizeCaseName(caseName == null || caseName.isBlank() ? "luckyblock" : caseName));
        if (rotation == null) {
            return "0s";
        }
        return formatRotationTime(rotation.millisUntilNextChange(System.currentTimeMillis()));
    }

    private String formatRotationTime(long millis) {
        long totalSeconds = Math.max(0L, (millis + 999L) / 1000L);
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0L) {
            return hours + "h " + minutes + "m " + seconds + "s";
        }
        if (minutes > 0L) {
            return minutes + "m " + seconds + "s";
        }
        return seconds + "s";
    }

    private CaseDefinition loadCase(String name, ConfigurationSection section) {
        boolean enabled = section.getBoolean("enabled", true);
        String inventoryName = section.getString("inventoryName", section.getString("inventory-name"));
        Boolean messageEnabled = null;
        if (section.isSet("messageEnabled")) {
            messageEnabled = Boolean.valueOf(section.getBoolean("messageEnabled"));
        } else if (section.isSet("message-enabled")) {
            messageEnabled = Boolean.valueOf(section.getBoolean("message-enabled"));
        }
        String messageText = readCaseMessage(section);
        List<CaseBlockLocation> locations = readCaseLocations(section);
        ItemStack key = ItemSerializer.deserializeItem(section.getString("key", ""));
        CaseKeyType keyType = readCaseType(section);
        List<CaseReward> rewards = readRewards(section);

        CaseDefinition definition = new CaseDefinition(name, enabled, inventoryName, messageEnabled, messageText, locations, key, keyType, rewards);

        // Load individual GUI settings
        boolean individualGui = section.getBoolean("individualGui", false);
        definition.setIndividualGui(individualGui);

        if (individualGui) {
            if (section.isSet("gui.preview.decoration")) {
                definition.setPreviewDecorations(readDecorations(section, "gui.preview.decoration"));
            }
            if (section.isSet("gui.animation.decoration")) {
                definition.setAnimationDecorations(readDecorations(section, "gui.animation.decoration"));
            }
            if (section.isSet("gui.result.decoration")) {
                definition.setResultDecorations(readDecorations(section, "gui.result.decoration"));
            }
        }

        return definition;
    }

    private boolean needsCaseFormatMigration(ConfigurationSection section, CaseDefinition definition) {
        if (!section.isSet("messages.open")) {
            return true;
        }
        if (section.isSet("location") || !section.isSet("locations")) {
            return true;
        }
        if (section.isSet("keyType") || section.isSet("key-type") || section.isSet("key_type")) {
            return true;
        }
        String type = section.getString("type");
        return type == null || type.isBlank() || !CaseKeyType.fromConfig(type).name().equals(definition.keyType().name());
    }

    private List<CaseBlockLocation> readCaseLocations(ConfigurationSection section) {
        List<CaseBlockLocation> locations = new ArrayList<>();

        for (Map<?, ?> map : section.getMapList("locations")) {
            YamlConfiguration temporary = new YamlConfiguration();
            ConfigurationSection locationSection = temporary.createSection("location");
            copyMap(locationSection, map);
            CaseBlockLocation location = CaseBlockLocation.fromSection(locationSection);
            if (location != null && !locations.contains(location)) {
                locations.add(location);
            }
        }

        CaseBlockLocation legacyLocation = CaseBlockLocation.fromSection(section.getConfigurationSection("location"));
        if (legacyLocation != null && !locations.contains(legacyLocation)) {
            locations.add(legacyLocation);
        }

        return locations;
    }

    private CaseKeyType readCaseType(ConfigurationSection section) {
        String raw = section.getString("type");
        if (raw == null || raw.isBlank()) {
            raw = section.getString("keyType");
        }
        if (raw == null || raw.isBlank()) {
            raw = section.getString("key-type");
        }
        if (raw == null || raw.isBlank()) {
            raw = section.getString("key_type");
        }
        return CaseKeyType.fromConfig(raw);
    }

    private String readCaseMessage(ConfigurationSection section) {
        String message = section.getString("messages.open");
        if (message == null || message.isBlank()) {
            message = section.getString("messageText");
        }
        if (message == null || message.isBlank()) {
            message = section.getString("message");
        }
        return message == null || message.isBlank() ? globalMessage : message;
    }

    private List<CaseReward> readRewards(ConfigurationSection section) {
        List<RawReward> rawRewards = new ArrayList<>();
        List<Map<?, ?>> rewardMaps = section.getMapList("items");
        if (rewardMaps.isEmpty()) {
            rewardMaps = section.getMapList("rewards");
        }

        for (Map<?, ?> map : rewardMaps) {
            String itemData = asString(firstPresent(map, "item", "itemStack", "serialized"));
            ItemStack item = ItemSerializer.deserializeItem(itemData);
            if (item == null || item.getType().isAir()) {
                continue;
            }
            Double chance = parseDoubleObject(firstPresent(map, "chance", "percent", "szansa"));
            Integer slot = parseIntegerObject(firstPresent(map, "slot", "editorSlot", "editor-slot"));
            rawRewards.add(new RawReward(item, chance == null ? -1.0D : chance, slot));
        }

        return normalizeRawRewards(rawRewards);
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        return String.valueOf(value);
    }

    private Double parseDoubleObject(Object raw) {
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        if (raw == null) {
            return null;
        }
        return parseChance(String.valueOf(raw));
    }

    private Integer parseIntegerObject(Object raw) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(raw).trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private List<CaseReward> normalizeRawRewards(List<RawReward> rawRewards) {
        if (rawRewards.isEmpty()) {
            return Collections.emptyList();
        }

        double positiveSum = 0.0D;
        int missing = 0;
        for (RawReward reward : rawRewards) {
            if (reward.chance() > MIN_EFFECTIVE_CHANCE) {
                positiveSum += reward.chance();
            } else {
                missing++;
            }
        }

        List<CaseReward> normalized = new ArrayList<>(rawRewards.size());
        if (positiveSum <= 0.0D) {
            double equal = 100.0D / rawRewards.size();
            for (RawReward reward : rawRewards) {
                normalized.add(new CaseReward(reward.item().clone(), equal, reward.editorSlot()));
            }
            return normalized;
        }

        double missingChance = 0.0D;
        double scale = 1.0D;
        if (missing > 0 && positiveSum < 100.0D) {
            missingChance = (100.0D - positiveSum) / missing;
        } else if (missing > 0) {
            missingChance = 100.0D / rawRewards.size();
            double reservedForMissing = missingChance * missing;
            scale = Math.max(0.0D, 100.0D - reservedForMissing) / positiveSum;
        } else if (positiveSum != 100.0D) {
            scale = 100.0D / positiveSum;
        }

        for (RawReward reward : rawRewards) {
            double chance = reward.chance() > MIN_EFFECTIVE_CHANCE ? reward.chance() * scale : missingChance;
            normalized.add(new CaseReward(reward.item().clone(), clampChance(chance), reward.editorSlot()));
        }
        fixChanceRounding(normalized);
        return normalized;
    }

    private void normalizeRewards(List<CaseReward> rewards) {
        if (rewards.isEmpty()) {
            return;
        }

        List<RawReward> rawRewards = new ArrayList<>(rewards.size());
        for (CaseReward reward : rewards) {
            rawRewards.add(new RawReward(reward.item().clone(), reward.chance(), reward.editorSlot()));
        }

        List<CaseReward> normalized = normalizeRawRewards(rawRewards);
        rewards.clear();
        rewards.addAll(normalized);
    }

    private void fixChanceRounding(List<CaseReward> rewards) {
        if (rewards.isEmpty()) {
            return;
        }
        double sum = 0.0D;
        for (CaseReward reward : rewards) {
            sum += reward.chance();
        }
        double diff = 100.0D - sum;
        if (Math.abs(diff) > 0.0001D) {
            CaseReward last = rewards.get(rewards.size() - 1);
            last.setChance(clampChance(last.chance() + diff));
        }
    }

    public boolean createCase(String name) {
        String normalizedName = normalizeCaseName(name);
        if (cases.containsKey(normalizedName)) {
            return false;
        }

        CaseDefinition definition = new CaseDefinition(name, true, null, null, globalMessage, Collections.emptyList(), null, CaseKeyType.KEY, Collections.emptyList());
        cases.put(normalizedName, definition);
        saveCase(definition);
        return true;
    }

    public boolean setLocation(String name, CaseBlockLocation location) {
        CaseDefinition definition = getCase(name);
        if (definition == null) {
            return false;
        }

        if (location == null) {
            return false;
        }
        String existingCaseName = casesByLocation.get(location);
        if (existingCaseName != null && !existingCaseName.equals(normalizeCaseName(definition.name()))) {
            CaseDefinition existing = cases.get(existingCaseName);
            if (existing != null) {
                existing.removeLocation(location);
                saveCase(existing);
            }
        }
        definition.addLocation(location);
        casesByLocation.put(location, normalizeCaseName(definition.name()));
        saveCase(definition);
        return true;
    }

    public boolean removeLocation(String name, CaseBlockLocation location) {
        CaseDefinition definition = getCase(name);
        if (definition == null || location == null || !definition.removeLocation(location)) {
            return false;
        }

        casesByLocation.remove(location);
        saveCase(definition);
        return true;
    }

    public boolean removeAllLocations(String name) {
        CaseDefinition definition = getCase(name);
        if (definition == null || definition.locations().isEmpty()) {
            return false;
        }

        for (CaseBlockLocation location : definition.locations()) {
            casesByLocation.remove(location);
        }
        definition.clearLocations();
        saveCase(definition);
        return true;
    }

    public boolean setEnabled(String name, boolean enabled) {
        CaseDefinition definition = getCase(name);
        if (definition == null) {
            return false;
        }
        definition.setEnabled(enabled);
        saveCase(definition);
        return true;
    }

    public boolean setMessageEnabled(String name, Boolean messageEnabled) {
        CaseDefinition definition = getCase(name);
        if (definition == null) {
            return false;
        }
        definition.setMessageEnabled(messageEnabled);
        saveCase(definition);
        return true;
    }

    public boolean setIndividualGui(String name, boolean individualGui) {
        CaseDefinition definition = getCase(name);
        if (definition == null) {
            return false;
        }
        definition.setIndividualGui(individualGui);
        if (!individualGui) {
            // Clear individual decorations when disabling
            definition.setPreviewDecorations(null);
            definition.setAnimationDecorations(null);
            definition.setResultDecorations(null);
        }
        invalidateCaseGuiCache(name);
        saveCase(definition);
        return true;
    }

    public boolean isMessageEnabled(CaseDefinition definition) {
        if (definition == null) {
            return globalMessageEnabled;
        }
        return definition.messageEnabled() == null ? globalMessageEnabled : definition.messageEnabled();
    }

    public boolean setKey(String name, ItemStack key) {
        CaseDefinition definition = getCase(name);
        if (definition == null) {
            return false;
        }
        definition.setKey(key == null ? null : key.clone());
        saveCase(definition);
        return true;
    }

    public boolean hasConfiguredKey(CaseDefinition definition) {
        return definition != null && definition.key() != null && !definition.key().getType().isAir();
    }

    public void giveKey(Player player, CaseDefinition definition, int amount) {
        if (!hasConfiguredKey(definition) || amount <= 0) {
            return;
        }

        int remaining = amount;
        int maxStackSize = Math.max(1, definition.key().getMaxStackSize());
        while (remaining > 0) {
            int stackAmount = Math.min(maxStackSize, remaining);
            ItemStack key = definition.key().clone();
            key.setAmount(stackAmount);
            player.getInventory().addItem(key).values()
                .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
            remaining -= stackAmount;
        }
    }

    public CaseDefinition getCase(String name) {
        if (name == null) {
            return null;
        }
        return cases.get(normalizeCaseName(name));
    }

    public Collection<String> getCaseNames() {
        List<String> names = cases.values().stream()
            .map(CaseDefinition::name)
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .toList();
        return names;
    }

    public CaseDefinition findByBlock(org.bukkit.block.Block block) {
        if (block == null) {
            return null;
        }
        String name = casesByLocation.get(CaseBlockLocation.fromBlock(block));
        return name == null ? null : cases.get(name);
    }

    public CaseDefinition findLuckyBlockCase(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }

        for (CaseDefinition definition : cases.values()) {
            if (definition.keyType() == CaseKeyType.LUCKYBLOCK
                && hasConfiguredKey(definition)
                && isSimilarIgnoringAmount(item, definition.key())) {
                return definition;
            }
        }
        return null;
    }

    public CaseOpenType getLuckyBlockOpenType() {
        return luckyBlockOpenType;
    }

    public boolean isCaseBattleSlot(int slot) {
        return caseBattleSlotSet.contains(slot);
    }

    public void showCaseBattleDisabledTitle(Player player) {
        player.closeInventory();
        dev.grzybalson.utils.ColorUtils.sendTitle(player, 
            ColorUtils.colorize(caseBattleDisabledTitle),
            ColorUtils.colorize(caseBattleDisabledSubtitle),
            caseBattleTitleFadeIn,
            caseBattleTitleStay,
            caseBattleTitleFadeOut
        );
    }

    public CaseOpenType getPreviewButtonType(int rawSlot) {
        return previewLayout.buttonSlots().get(rawSlot);
    }

    public CaseOpenType getResultButtonType(int rawSlot) {
        return resultLayout.buttonSlots().get(rawSlot);
    }

    public CaseOpenType getEditorButtonType(int rawSlot, int inventorySize) {
        CaseOpenType mappedType = editorLayout.buttonSlots().get(rawSlot);
        if (mappedType != null) {
            return mappedType;
        }
        return rawSlot == inventorySize - 1 ? CaseOpenType.SAVE : null;
    }

    public Inventory createPreviewInventory(CaseDefinition definition) {
        int size = inventorySize(previewLayout.rows());
        String title = resolveTitle(definition.inventoryName(), previewLayout.title(), definition.name());
        CasePreviewHolder holder = new CasePreviewHolder(definition.name());
        Inventory inventory = Bukkit.createInventory(holder, size, ColorUtils.colorizeComponent(title));
        holder.setInventory(inventory);
        inventory.setContents(cloneContents(getPreviewContents(definition, size)));
        return inventory;
    }

    public Inventory createEditorInventory(CaseDefinition definition) {
        int size = inventorySize(editorLayout.rows());
        String title = resolveTitle(null, editorLayout.title(), definition.name());
        CaseEditHolder holder = new CaseEditHolder(definition.name());
        Inventory inventory = Bukkit.createInventory(holder, size, ColorUtils.colorizeComponent(title));
        holder.setInventory(inventory);

        List<Integer> itemSlots = getEditorItemSlots(inventory);
        Set<Integer> usedSlots = new HashSet<>();
        for (CaseReward reward : definition.rewards()) {
            int slot = resolveEditorSlot(reward, itemSlots, usedSlots);
            if (slot < 0) {
                break;
            }
            inventory.setItem(slot, reward.item().clone());
            usedSlots.add(slot);
        }
        applyButtons(inventory, editorLayout);
        ensureSaveButton(inventory);
        return inventory;
    }

    public Inventory createResultInventory(CaseOpenResult result, CaseOpenType openType) {
        int size = inventorySize(resultLayout.rows());
        CaseResultHolder holder = new CaseResultHolder(result.definition().name(), openType);
        Inventory inventory = Bukkit.createInventory(holder, size, ColorUtils.colorizeComponent(resolveTitle(null, resultLayout.title(), result.definition().name())));
        holder.setInventory(inventory);

        inventory.setContents(cloneContents(getResultTemplateContents(result.definition(), size)));
        int slot = resultItemSlot(size);
        inventory.setItem(slot, createRewardDisplay(result.reward()));
        return inventory;
    }

    public Inventory createOnlyRewardInventory(CaseOpenResult result) {
        int size = inventorySize(resultLayout.rows());
        CaseResultHolder holder = new CaseResultHolder(result.definition().name(), CaseOpenType.ANIMATION);
        Inventory inventory = Bukkit.createInventory(holder, size, ColorUtils.colorizeComponent(resolveTitle(null, resultLayout.title(), result.definition().name())));
        holder.setInventory(inventory);

        int slot = resultItemSlot(size);
        inventory.setItem(slot, createRewardDisplay(result.reward()));
        return inventory;
    }

    public Inventory createAnimationInventory(CaseDefinition definition) {
        int size = inventorySize(animationLayout.rows());
        CaseAnimationHolder holder = new CaseAnimationHolder(definition.name());
        Inventory inventory = Bukkit.createInventory(holder, size, ColorUtils.colorizeComponent(resolveTitle(null, animationLayout.title(), definition.name())));
        holder.setInventory(inventory);
        inventory.setContents(cloneContents(getAnimationTemplateContents(definition, size)));
        return inventory;
    }

    private ItemStack[] getPreviewContents(CaseDefinition definition, int size) {
        String cacheKey = normalizeCaseName(definition.name());
        ItemStack[] cached = previewContentsCache.get(cacheKey);
        if (cached != null && cached.length == size) {
            return cached;
        }

        Inventory template = Bukkit.createInventory(null, size);
        Set<Integer> blockedSlots;

        if (definition.individualGui() && definition.previewDecorations() != null) {
            // Use individual decorations for this case
            blockedSlots = applyDecorationsFromList(template, definition.previewDecorations());
        } else {
            // Use global decorations
            blockedSlots = applyDecorations(template, previewLayout);
        }

        blockedSlots.addAll(applyButtons(template, previewLayout));
        blockedSlots.addAll(applyCaseBattleItem(template));
        placePreviewRewards(template, definition, blockedSlots);

        ItemStack[] contents = cloneContents(template.getContents());
        previewContentsCache.put(cacheKey, contents);
        return contents;
    }

    private ItemStack[] getResultTemplateContents(int size) {
        ItemStack[] cached = resultTemplateContents;
        if (cached != null && cached.length == size) {
            return cached;
        }

        Inventory template = Bukkit.createInventory(null, size);
        applyDecorations(template, resultLayout);
        applyButtons(template, resultLayout);

        ItemStack[] contents = cloneContents(template.getContents());
        resultTemplateContents = contents;
        return contents;
    }

    private ItemStack[] getResultTemplateContents(CaseDefinition definition, int size) {
        if (!definition.individualGui() || definition.resultDecorations() == null) {
            return getResultTemplateContents(size);
        }

        // Individual GUI for this case - don't use cache
        Inventory template = Bukkit.createInventory(null, size);
        applyDecorationsFromList(template, definition.resultDecorations());
        applyButtons(template, resultLayout);

        return cloneContents(template.getContents());
    }

    private ItemStack[] getAnimationTemplateContents(int size) {
        ItemStack[] cached = animationTemplateContents;
        if (cached != null && cached.length == size) {
            return cached;
        }

        Inventory template = Bukkit.createInventory(null, size);
        applyDecorations(template, animationLayout);

        ItemStack[] contents = cloneContents(template.getContents());
        animationTemplateContents = contents;
        return contents;
    }

    private ItemStack[] getAnimationTemplateContents(CaseDefinition definition, int size) {
        if (!definition.individualGui() || definition.animationDecorations() == null) {
            return getAnimationTemplateContents(size);
        }

        // Individual GUI for this case - don't use cache
        Inventory template = Bukkit.createInventory(null, size);
        applyDecorationsFromList(template, definition.animationDecorations());

        return cloneContents(template.getContents());
    }

    private ItemStack[] cloneContents(ItemStack[] contents) {
        ItemStack[] clone = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            clone[i] = item == null ? null : item.clone();
        }
        return clone;
    }

    private int resultItemSlot(int inventorySize) {
        return itemSlot >= 0 && itemSlot < inventorySize ? itemSlot : inventorySize / 2;
    }

    private void clearRuntimeGuiCaches() {
        rewardDisplayCache.clear();
        previewContentsCache.clear();
        resultTemplateContents = null;
        animationTemplateContents = null;
    }

    private void invalidateCaseGuiCache(String caseName) {
        if (caseName != null) {
            previewContentsCache.remove(normalizeCaseName(caseName));
        }
    }

    public Inventory createChanceAnvil(Player player, String caseName, int rewardIndex) {
        CaseDefinition definition = getCase(caseName);
        if (definition == null || rewardIndex < 0 || rewardIndex >= definition.rewards().size()) {
            return null;
        }

        String initialChance = formatChance(definition.rewards().get(rewardIndex).chance());
        CaseChanceAnvilHolder holder = new CaseChanceAnvilHolder(definition.name(), rewardIndex, initialChance);
        Inventory inventory = Bukkit.createInventory(holder, InventoryType.ANVIL, ColorUtils.colorizeComponent("&8Szansa dropu"));
        holder.setInventory(inventory);
        inventory.setItem(0, createChanceInput(definition.rewards().get(rewardIndex).chance()));
        return inventory;
    }

    private ItemStack createChanceInput(double chance) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(formatChance(chance)));
            meta.lore(List.of(ColorUtils.colorizeComponent("&7Wpisz szansę od 0 do 100.")));
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createChanceResult(String rawChance) {
        ItemStack item = new ItemStack(Material.LIME_DYE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String value = rawChance == null || rawChance.isBlank() ? "0" : rawChance;
            meta.displayName(ColorUtils.colorizeComponent("&aZapisz szansę: &f" + value + "%"));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(chanceValueKey, PersistentDataType.STRING, value);
            item.setItemMeta(meta);
        }
        return item;
    }

    public Double parseChanceResult(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }

        if (meta.displayName() != null) {
            Double parsedDisplayName = parseChance(LEGACY.serialize(meta.displayName()));
            if (parsedDisplayName != null) {
                return parsedDisplayName;
            }
        }

        String storedValue = meta.getPersistentDataContainer().get(chanceValueKey, PersistentDataType.STRING);
        return parseChance(storedValue);
    }

    public Double parsePlainChanceItemName(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null || meta.displayName() == null) {
            return null;
        }

        String displayName = LEGACY.serialize(meta.displayName());
        if (displayName.toLowerCase(Locale.ROOT).contains("zapisz szansę")) {
            return null;
        }
        return parseChance(displayName);
    }

    public void saveEditorInventory(String caseName, Inventory inventory) {
        CaseDefinition definition = getCase(caseName);
        if (definition == null) {
            return;
        }

        List<CaseReward> previousRewards = new ArrayList<>(definition.rewards());
        Set<Integer> saveSlots = getSaveSlots(inventory.getSize());
        Set<Integer> decorationSlots = getEditorDecorationSlots(inventory.getSize());
        List<CaseReward> newRewards = new ArrayList<>();
        Set<Integer> matchedPrevious = new HashSet<>();

        int compactIndex = 0;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (saveSlots.contains(slot) || decorationSlots.contains(slot)) {
                continue;
            }
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) {
                continue;
            }

            double chance = -1.0D;
            if (compactIndex < previousRewards.size() && isSameRewardItem(item, previousRewards.get(compactIndex).item())) {
                chance = previousRewards.get(compactIndex).chance();
                matchedPrevious.add(compactIndex);
            } else {
                int matchedIndex = findMatchingPreviousReward(item, previousRewards, matchedPrevious);
                if (matchedIndex >= 0) {
                    chance = previousRewards.get(matchedIndex).chance();
                    matchedPrevious.add(matchedIndex);
                }
            }

            newRewards.add(new CaseReward(item.clone(), chance, slot));
            compactIndex++;
        }

        boolean rewardListChanged = newRewards.size() != previousRewards.size() || matchedPrevious.size() != newRewards.size();
        if (rewardListChanged) {
            recalculateEqualChances(newRewards);
        } else {
            normalizeRewards(newRewards);
        }
        definition.replaceRewards(newRewards);
        rewardDisplayCache.clear();
        saveCase(definition);
    }

    private void recalculateEqualChances(List<CaseReward> rewards) {
        if (rewards.isEmpty()) {
            return;
        }

        double equal = 100.0D / rewards.size();
        for (CaseReward reward : rewards) {
            reward.setChance(equal);
        }
        fixChanceRounding(rewards);
    }

    private int findMatchingPreviousReward(ItemStack item, List<CaseReward> previousRewards, Set<Integer> matchedPrevious) {
        for (int i = 0; i < previousRewards.size(); i++) {
            if (matchedPrevious.contains(i)) {
                continue;
            }
            if (isSameRewardItem(item, previousRewards.get(i).item())) {
                return i;
            }
        }
        return -1;
    }

    private boolean isSameRewardItem(ItemStack first, ItemStack second) {
        if (first == null || second == null) {
            return false;
        }
        return first.getAmount() == second.getAmount() && first.isSimilar(second);
    }

    public int findRewardIndexInEditor(Inventory inventory, int rawSlot) {
        Set<Integer> saveSlots = getSaveSlots(inventory.getSize());
        if (rawSlot < 0 || rawSlot >= inventory.getSize() || saveSlots.contains(rawSlot)) {
            return -1;
        }

        int index = 0;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (saveSlots.contains(slot)) {
                continue;
            }
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) {
                continue;
            }
            if (slot == rawSlot) {
                return index;
            }
            index++;
        }
        return -1;
    }

    public boolean setRewardChance(String caseName, int rewardIndex, double chance) {
        CaseDefinition definition = getCase(caseName);
        if (definition == null || rewardIndex < 0 || rewardIndex >= definition.rewards().size()) {
            return false;
        }

        List<CaseReward> rewards = new ArrayList<>();
        for (CaseReward reward : definition.rewards()) {
            rewards.add(new CaseReward(reward.item().clone(), reward.chance(), reward.editorSlot()));
        }

        if (rewards.size() == 1) {
            rewards.get(0).setChance(100.0D);
        } else {
            double selectedChance = clampChance(chance);
            double remaining = Math.max(0.0D, 100.0D - selectedChance);
            double otherSum = 0.0D;
            boolean hasMissingOtherChance = false;
            for (int i = 0; i < rewards.size(); i++) {
                if (i != rewardIndex && rewards.get(i).chance() > MIN_EFFECTIVE_CHANCE) {
                    otherSum += rewards.get(i).chance();
                } else if (i != rewardIndex) {
                    hasMissingOtherChance = true;
                }
            }

            for (int i = 0; i < rewards.size(); i++) {
                CaseReward reward = rewards.get(i);
                if (i == rewardIndex) {
                    reward.setChance(selectedChance);
                    continue;
                }
                if (otherSum <= 0.0D || hasMissingOtherChance) {
                    reward.setChance(remaining / (rewards.size() - 1));
                } else {
                    reward.setChance((reward.chance() / otherSum) * remaining);
                }
            }
            fixChanceRounding(rewards);
        }

        definition.replaceRewards(rewards);
        rewardDisplayCache.clear();
        saveCase(definition);
        return true;
    }

    public CaseOpenResult tryPrepareOpening(Player player, String caseName, CaseOpenType openType) {
        return tryPrepareOpening(player, caseName, openType, null);
    }

    public CaseOpenResult tryPrepareOpening(Player player, String caseName, CaseOpenType openType, EquipmentSlot keySlot) {
        UUID uuid = player.getUniqueId();

        // Atomic check-and-set to prevent double-open exploit (race condition fix)
        if (!openingPlayers.add(uuid)) {
            player.sendMessage(colorMessage("messages.already-opening", Map.of("case", caseName)));
            playFailSound(player);
            return null;
        }

        // Use try-finally to ensure cleanup even if method returns early
        CaseOpenResult result = null;
        GlobalCaseOpeningLimiter.Permit globalPermit = null;
        try {
            CaseDefinition definition = getCase(caseName);
            if (definition == null) {
                player.sendMessage(colorMessage("messages.case-not-found", Map.of("case", caseName)));
                return null;
            }
            if (!definition.enabled()) {
                player.sendMessage(colorMessage("messages.case-disabled", Map.of("case", definition.name())));
                playFailSound(player);
                return null;
            }
            if (definition.rewards().isEmpty()) {
                player.sendMessage(colorMessage("messages.empty-case", Map.of("case", definition.name())));
                playFailSound(player);
                return null;
            }
            if (definition.key() == null || definition.key().getType().isAir()) {
                player.sendMessage(colorMessage("messages.key-not-configured", Map.of("case", definition.name())));
                playFailSound(player);
                return null;
            }
            if (isOnCooldown(player)) {
                player.sendMessage(colorMessage("messages.cooldown", Map.of("case", definition.name())));
                playFailSound(player);
                return null;
            }
            int inventoryKeySlot = keySlot == null ? findKeySlotInInventory(player, definition.key()) : -1;
            if (keySlot == null ? inventoryKeySlot < 0 : !hasKeyInHand(player, definition.key(), keySlot)) {
                player.sendMessage(colorMessage("messages.need-key", Map.of("case", definition.name())));
                playFailSound(player);
                return null;
            }
            if (!hasEmptyInventorySlot(player)) {
                player.sendMessage(colorMessage("messages.inventory-full", Map.of("case", definition.name())));
                playFailSound(player);
                return null;
            }

            // Reserve only after case/key/inventory checks, but before drawing or consuming a key.
            GlobalCaseOpeningLimiter.Attempt admission = globalOpeningLimiter.tryAcquire();
            globalPermit = admission.permit();
            if (globalPermit == null) {
                long seconds = Math.max(1L, admission.retryAfterMillis() / 1000L
                    + (admission.retryAfterMillis() % 1000L == 0 ? 0 : 1));
                player.sendMessage(colorMessage("messages.global-open-limit", Map.of(
                    "case", definition.name(), "time", String.valueOf(seconds))));
                playFailSound(player);
                return null;
            }

            CaseReward reward = selectReward(definition);
            if (reward == null) {
                player.sendMessage(colorMessage("messages.empty-case", Map.of("case", definition.name())));
                playFailSound(player);
                return null;
            }

            cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
            if (consumeKey) {
                if (keySlot == null) {
                    consumeKeyFromInventory(player, inventoryKeySlot);
                } else {
                    consumeKey(player, keySlot);
                }
            }
            // Keep player in openingPlayers set - will be removed when animation finishes or on error
            result = new CaseOpenResult(definition, reward);
            return result;

        } finally {
            if (result == null) globalOpeningLimiter.release(globalPermit);
            // Remove from openingPlayers if opening failed (result is null)
            // For successful opening: remove immediately for NO_ANIMATION, keep for ANIMATION (removed when animation completes)
            if (result == null || openType != CaseOpenType.ANIMATION) {
                openingPlayers.remove(uuid);
            }
        }
    }

    private boolean isOnCooldown(Player player) {
        long last = cooldowns.getOrDefault(player.getUniqueId(), 0L);
        return System.currentTimeMillis() - last < openCooldownMillis;
    }

    /** Sprzatanie stanu gracza przy wyjsciu gracza. */
    public void handleQuit(UUID uuid) {
        if (uuid == null) {
            return;
        }
        cooldowns.remove(uuid);
        openingPlayers.remove(uuid);
        java.util.concurrent.atomic.AtomicBoolean cancelled = animationCancelled.remove(uuid);
        if (cancelled != null) {
            cancelled.set(true);
        }
        animationResults.remove(uuid);
        NinjaBoxWorldAnimator ninja = ninjaBoxAnimations.remove(uuid);
        if (ninja != null) {
            ninja.cancelDrop();
        }
    }

    private boolean hasKeyInHand(Player player, ItemStack configuredKey, EquipmentSlot keySlot) {
        ItemStack held = getItemInHand(player, keySlot);
        if (held == null || held.getType().isAir()) {
            return false;
        }
        return isSimilarIgnoringAmount(held, configuredKey);
    }

    private int findKeySlotInInventory(Player player, ItemStack configuredKey) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (isSimilarIgnoringAmount(contents[slot], configuredKey)) {
                return slot;
            }
        }
        return -1;
    }

    private boolean hasEmptyInventorySlot(Player player) {
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType().isAir()) {
                return true;
            }
        }
        return false;
    }

    private boolean isSimilarIgnoringAmount(ItemStack first, ItemStack second) {
        if (first == null || second == null || first.getType().isAir() || second.getType().isAir()) {
            return false;
        }
        // Tani test przed klonowaniem — isSimilar i tak wymaga tego samego materialu.
        if (first.getType() != second.getType()) {
            return false;
        }
        ItemStack firstOne = first.clone();
        ItemStack keyOne = second.clone();
        firstOne.setAmount(1);
        keyOne.setAmount(1);

        if (firstOne.isSimilar(keyOne)) {
            return true;
        }

        ItemMeta meta1 = firstOne.getItemMeta();
        ItemMeta meta2 = keyOne.getItemMeta();
        if (meta1 == null || meta2 == null) {
            return false;
        }

        if (meta1.hasCustomModelData()) {
            meta1.setCustomModelData(null);
            firstOne.setItemMeta(meta1);
        }
        if (meta2.hasCustomModelData()) {
            meta2.setCustomModelData(null);
            keyOne.setItemMeta(meta2);
        }

        return firstOne.isSimilar(keyOne);
    }

    private void consumeKey(Player player, EquipmentSlot keySlot) {
        ItemStack held = getItemInHand(player, keySlot);
        if (held == null || held.getType().isAir()) {
            return;
        }
        int amount = held.getAmount();
        if (amount <= 1) {
            setItemInHand(player, keySlot, null);
            return;
        }
        held.setAmount(amount - 1);
        setItemInHand(player, keySlot, held);
    }

    private void consumeKeyFromInventory(Player player, int slot) {
        if (slot < 0) {
            return;
        }
        ItemStack item = player.getInventory().getItem(slot);
        if (item == null || item.getType().isAir()) {
            return;
        }
        int amount = item.getAmount();
        if (amount <= 1) {
            player.getInventory().setItem(slot, null);
            return;
        }
        item.setAmount(amount - 1);
        player.getInventory().setItem(slot, item);
    }

    private ItemStack getItemInHand(Player player, EquipmentSlot keySlot) {
        return keySlot == EquipmentSlot.OFF_HAND
            ? player.getInventory().getItemInOffHand()
            : player.getInventory().getItemInMainHand();
    }

    private void setItemInHand(Player player, EquipmentSlot keySlot, ItemStack item) {
        if (keySlot == EquipmentSlot.OFF_HAND) {
            player.getInventory().setItemInOffHand(item);
            return;
        }
        player.getInventory().setItemInMainHand(item);
    }

    private CaseReward selectReward(CaseDefinition definition) {
        List<CaseReward> rewards = definition.rewards();
        if (rewards.isEmpty()) {
            return null;
        }

        double total = definition.rewardChanceTotal();
        if (total <= 0.0D) {
            return rewards.get(random.nextInt(rewards.size()));
        }

        double roll = random.nextDouble() * total;
        double current = 0.0D;
        for (CaseReward reward : rewards) {
            current += Math.max(0.0D, reward.chance());
            if (roll <= current) {
                return reward;
            }
        }
        return rewards.get(rewards.size() - 1);
    }

    public void finishOpening(Player player, CaseOpenResult result, CaseOpenType openType, boolean openResultInventory) {
        giveReward(player, result.reward().item());
        announceReward(player, result);
        playSuccessSound(player);
        if (plugin.getAchievementManager() != null) {
            plugin.getAchievementManager().incrementCasesOpened(player);
        }
        if (plugin.getMissionManager() != null) {
            plugin.getMissionManager().incrementCases(player.getUniqueId());
        }
        if (openResultInventory && player.isOnline()) {
            if (!renderResultInCurrentInventory(player, result, openType)) {
                player.openInventory(createResultInventory(result, openType));
            }
        }
    }

    private boolean renderResultInCurrentInventory(Player player, CaseOpenResult result, CaseOpenType openType) {
        return renderResultInCurrentResult(player, result, openType)
            || (openType == CaseOpenType.ANIMATION && renderResultInCurrentAnimation(player, result));
    }

    private boolean renderResultInCurrentResult(Player player, CaseOpenResult result, CaseOpenType openType) {
        Inventory inventory = player.getOpenInventory().getTopInventory();
        if (!(inventory.getHolder() instanceof CaseResultHolder holder)) {
            return false;
        }
        if (!holder.caseName().equalsIgnoreCase(result.definition().name()) || holder.previousOpenType() != openType) {
            return false;
        }
        if (inventory.getSize() != inventorySize(resultLayout.rows())) {
            return false;
        }

        inventory.setItem(resultItemSlot(inventory.getSize()), createRewardDisplay(result.reward()));
        return true;
    }

    private boolean renderResultInCurrentAnimation(Player player, CaseOpenResult result) {
        Inventory inventory = player.getOpenInventory().getTopInventory();
        if (!(inventory.getHolder() instanceof CaseAnimationHolder holder)) {
            return false;
        }

        int expectedSize = inventorySize(resultLayout.rows());
        String animationTitle = resolveTitle(null, animationLayout.title(), result.definition().name());
        String resultTitle = resolveTitle(null, resultLayout.title(), result.definition().name());
        if (inventory.getSize() != expectedSize || !Objects.equals(animationTitle, resultTitle)) {
            return false;
        }

        holder.markFinished();
        inventory.setContents(cloneContents(getResultTemplateContents(result.definition(), inventory.getSize())));
        int slot = resultItemSlot(inventory.getSize());
        inventory.setItem(slot, createRewardDisplay(result.reward()));
        return true;
    }

    public boolean cancelAnimationAndShowReward(Player player) {
        return cancelAnimationAndShowReward(player, false);
    }

    public boolean cancelAnimationAndShowReward(Player player, boolean openResultGui) {
        UUID uuid = player.getUniqueId();
        NinjaBoxWorldAnimator ninja = ninjaBoxAnimations.get(uuid);
        if (ninja != null) {
            ninja.cancelAndReward();
            return true;
        }
        CaseOpenResult result = animationResults.remove(uuid);
        if (result == null) {
            return false;
        }

        java.util.concurrent.atomic.AtomicBoolean cancelled = animationCancelled.remove(uuid);
        if (cancelled != null) {
            cancelled.set(true);
        }
        openingPlayers.remove(uuid);

        // Always next tick — openInventory inside InventoryCloseEvent desyncs viewers and enables dupes.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                Location location = player.getLocation();
                if (location.getWorld() != null) {
                    location.getWorld().dropItemNaturally(location, result.reward().item().clone());
                }
                return;
            }
            giveReward(player, result.reward().item());
            announceReward(player, result);
            playSuccessSound(player);
            if (plugin.getAchievementManager() != null) {
                plugin.getAchievementManager().incrementCasesOpened(player);
            }
            if (plugin.getMissionManager() != null) {
                plugin.getMissionManager().incrementCases(player.getUniqueId());
            }
            if (openResultGui) {
                player.openInventory(createResultInventory(result, CaseOpenType.ANIMATION));
            }
        });
        return true;
    }

    /** World animation for case name = ninjabox (LUCKYBLOCK place). Max 1 per player. */
    public void startNinjaBoxWorldAnimation(Player player, CaseOpenResult result, Location placeBlock) {
        UUID uuid = player.getUniqueId();
        if (ninjaBoxAnimations.containsKey(uuid)) {
            player.sendMessage(colorMessage("messages.ninjabox-busy", Map.of("case", result.definition().name())));
            // Key already consumed — still give reward without anim.
            openingPlayers.remove(uuid);
            finishOpening(player, result, CaseOpenType.NO_ANIMATION, false);
            return;
        }
        openingPlayers.add(uuid);
        animationResults.put(uuid, result);
        Location at = placeBlock == null ? player.getLocation().getBlock().getLocation() : placeBlock.clone();
        NinjaBoxWorldAnimator animator = new NinjaBoxWorldAnimator(plugin, this, player, result, at);
        ninjaBoxAnimations.put(uuid, animator);
        animator.start();
    }

    public void onNinjaBoxAnimationFinished(
        UUID playerId,
        CaseOpenResult result,
        Player player,
        Location origin,
        boolean giveToPlayer
    ) {
        ninjaBoxAnimations.remove(playerId);
        animationResults.remove(playerId);
        openingPlayers.remove(playerId);
        if (result == null) {
            return;
        }
        if (giveToPlayer && player != null && player.isOnline()) {
            finishOpening(player, result, CaseOpenType.NO_ANIMATION, false);
            return;
        }
        Location dropAt = origin != null ? origin : (player != null ? player.getLocation() : null);
        if (dropAt != null && dropAt.getWorld() != null) {
            dropAt.getWorld().dropItemNaturally(dropAt.clone().add(0, 1.2D, 0), result.reward().item().clone());
        }
    }

    /** True gdy aktywna animacja ninjabox blisko lokalizacji (FX bez damage). */
    public boolean isNinjaBoxFxNear(org.bukkit.Location loc, double radius) {
        if (loc == null || ninjaBoxAnimations.isEmpty()) {
            return false;
        }
        for (NinjaBoxWorldAnimator animator : ninjaBoxAnimations.values()) {
            if (animator != null && animator.isNear(loc, radius)) {
                return true;
            }
        }
        return false;
    }

    public void startAnimation(Player player, CaseOpenResult result) {
        if (NinjaBoxWorldAnimator.isNinjaBoxCase(result.definition())) {
            startNinjaBoxWorldAnimation(player, result, player.getLocation().getBlock().getLocation());
            return;
        }
        Inventory inventory = createAnimationInventory(result.definition());
        player.openInventory(inventory);

        UUID uuid = player.getUniqueId();
        Location fallbackLocation = player.getLocation().clone();
        List<CaseReward> rewards = result.definition().rewards();
        if (rewards == null || rewards.isEmpty()) {
            openingPlayers.remove(uuid);
            return;
        }

        int[] delays = animationDelayTicks != null && animationDelayTicks.length > 0
            ? animationDelayTicks.clone()
            : DEFAULT_ANIM_DELAYS.clone();
        int steps = delays.length;
        int winnerIdx = steps - 1 + 4;
        int len = steps + 9;
        ItemStack[] strip = new ItemStack[len];

        ItemStack landed = result.reward().item().clone();
        landed.setAmount(1);
        for (int i = 0; i < len; i++) {
            if (i == winnerIdx) {
                strip[i] = landed.clone();
            } else {
                CaseReward filler = rewards.get(random.nextInt(rewards.size()));
                ItemStack src = filler != null ? filler.item() : null;
                if (src == null || src.getType().isAir()) {
                    strip[i] = new ItemStack(Material.STONE);
                } else {
                    ItemStack copy = src.clone();
                    copy.setAmount(1);
                    strip[i] = copy;
                }
            }
        }

        java.util.concurrent.atomic.AtomicBoolean cancelled = new java.util.concurrent.atomic.AtomicBoolean(false);
        animationCancelled.put(uuid, cancelled);
        animationResults.put(uuid, result);

        int rows = animationLayout.rows();
        GuiScrollAnimator.run(plugin, inventory, strip, delays, rows, cancelled, () -> {
            // Claim result atomically — cancel path may have already removed + rewarded.
            CaseOpenResult claimed = animationResults.remove(uuid);
            animationCancelled.remove(uuid);
            openingPlayers.remove(uuid);
            if (cancelled.get() || claimed == null) {
                return;
            }
            Player finishPlayer = Bukkit.getPlayer(uuid);
            if (finishPlayer != null && finishPlayer.isOnline()) {
                finishOpening(finishPlayer, claimed, CaseOpenType.ANIMATION, true);
            } else if (fallbackLocation.getWorld() != null) {
                fallbackLocation.getWorld().dropItemNaturally(fallbackLocation, claimed.reward().item().clone());
            }
        });
    }

    private void giveReward(Player player, ItemStack item) {
        ItemStack reward = item.clone();
        player.getInventory().addItem(reward).values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }

    private void announceReward(Player player, CaseOpenResult result) {
        boolean enabled = result.definition().messageEnabled() == null ? globalMessageEnabled : result.definition().messageEnabled();
        if (!enabled) {
            return;
        }

        String rawMessage = result.definition().messageText() == null || result.definition().messageText().isBlank()
            ? globalMessage
            : result.definition().messageText();

        String message = rawMessage
            .replace("{player}", player.getName())
            .replace("{case}", result.definition().name())
            .replace("{item}", getReadableItemName(result.reward().item()))
            .replace("{chance}", formatChance(result.reward().chance()))
            .replace("%chance%", formatChance(result.reward().chance()))
            .replace("%szansa%", formatChance(result.reward().chance()));
        Bukkit.broadcast(ColorUtils.colorizeComponent(message));
    }

    public void playOpenGuiSound(Player player) {
        playConfiguredSound(player, "sounds.open-gui", Sound.UI_BUTTON_CLICK, 1.0F, 1.0F);
    }

    private void playAnimationTick(Player player) {
        playConfiguredSound(player, "caseWithAnimation.sounds.tick", Sound.UI_BUTTON_CLICK, 0.7F, 1.4F);
    }

    private void playSuccessSound(Player player) {
        playConfiguredSound(player, "caseWithAnimation.sounds.finish", Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.0F);
    }

    private void playFailSound(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
    }

    private void playConfiguredSound(Player player, String path, Sound fallback, float fallbackVolume, float fallbackPitch) {
        if (!config.getBoolean(path + ".enabled", true)) {
            return;
        }

        String soundName = config.getString(path + ".sound");
        if (soundName == null || soundName.isBlank()) {
            soundName = config.getString(path + ".sound-name", fallback.name());
        }

        Sound sound;
        try {
            sound = Sound.valueOf(soundName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            sound = fallback;
        }
        float volume = (float) config.getDouble(path + ".volume", fallbackVolume);
        float pitch = (float) config.getDouble(path + ".pitch", fallbackPitch);
        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    private Set<Integer> applyDecorations(Inventory inventory, GuiLayout layout) {
        Set<Integer> slots = new HashSet<>();
        for (CaseGuiDecoration decoration : layout.decorations()) {
            for (int slot : decoration.slots()) {
                if (slot >= 0 && slot < inventory.getSize()) {
                    inventory.setItem(slot, decoration.item().clone());
                    slots.add(slot);
                }
            }
        }
        return slots;
    }

    private Set<Integer> applyDecorationsFromList(Inventory inventory, List<CaseGuiDecoration> decorations) {
        Set<Integer> slots = new HashSet<>();
        if (decorations == null) {
            return slots;
        }
        for (CaseGuiDecoration decoration : decorations) {
            for (int slot : decoration.slots()) {
                if (slot >= 0 && slot < inventory.getSize()) {
                    inventory.setItem(slot, decoration.item().clone());
                    slots.add(slot);
                }
            }
        }
        return slots;
    }

    private Set<Integer> applyButtons(Inventory inventory, GuiLayout layout) {
        Set<Integer> slots = new HashSet<>();
        for (CaseGuiButton button : layout.buttons()) {
            for (int slot : button.slots()) {
                if (slot >= 0 && slot < inventory.getSize()) {
                    inventory.setItem(slot, button.item().clone());
                    slots.add(slot);
                }
            }
        }
        return slots;
    }

    private Set<Integer> applyCaseBattleItem(Inventory inventory) {
        Set<Integer> slots = new HashSet<>();
        for (int slot : caseBattleSlots) {
            if (slot >= 0 && slot < inventory.getSize()) {
                inventory.setItem(slot, caseBattleItem.clone());
                slots.add(slot);
            }
        }
        return slots;
    }

    private void ensureSaveButton(Inventory inventory) {
        Set<Integer> saveSlots = getSaveSlots(inventory.getSize());
        if (!saveSlots.isEmpty()) {
            return;
        }
        int slot = inventory.getSize() - 1;
        inventory.setItem(slot, createDefaultSaveButton());
    }

    private ItemStack createDefaultSaveButton() {
        ItemStack item = new ItemStack(Material.LIME_DYE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(ColorUtils.colorizeComponent("&aZapisz przedmioty"));
            meta.lore(List.of(
                ColorUtils.colorizeComponent("&7Kliknij, aby zapisac itemy."),
                ColorUtils.colorizeComponent("&7Shift + PPM na item ustawia szansę.")
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    public CaseOpenType getButtonType(Inventory inventory, CaseOpenType defaultType, int rawSlot) {
        GuiLayout layout;
        if (inventory.getHolder() instanceof CasePreviewHolder) {
            layout = previewLayout;
        } else if (inventory.getHolder() instanceof CaseResultHolder) {
            layout = resultLayout;
        } else if (inventory.getHolder() instanceof CaseEditHolder) {
            layout = editorLayout;
        } else {
            return defaultType;
        }

        CaseOpenType mappedType = layout.buttonSlots().get(rawSlot);
        if (mappedType != null) {
            return mappedType;
        }
        if (inventory.getHolder() instanceof CaseEditHolder && rawSlot == inventory.getSize() - 1) {
            return CaseOpenType.SAVE;
        }
        return defaultType;
    }

    public Set<Integer> getSaveSlots(int inventorySize) {
        return editorSaveSlotsCache.computeIfAbsent(inventorySize, this::computeSaveSlots);
    }

    private Set<Integer> computeSaveSlots(int inventorySize) {
        Set<Integer> slots = new HashSet<>();
        for (CaseGuiButton button : editorLayout.buttons()) {
            if (button.type() != CaseOpenType.SAVE) {
                continue;
            }
            for (int slot : button.slots()) {
                if (slot >= 0 && slot < inventorySize) {
                    slots.add(slot);
                }
            }
        }
        if (slots.isEmpty() && inventorySize > 0) {
            slots.add(inventorySize - 1);
        }
        return Set.copyOf(slots);
    }

    private Set<Integer> getEditorDecorationSlots(int inventorySize) {
        Set<Integer> slots = new HashSet<>();
        for (CaseGuiDecoration decoration : editorLayout.decorations()) {
            for (int slot : decoration.slots()) {
                if (slot >= 0 && slot < inventorySize) {
                    slots.add(slot);
                }
            }
        }
        for (CaseGuiButton button : editorLayout.buttons()) {
            for (int slot : button.slots()) {
                if (slot >= 0 && slot < inventorySize) {
                    slots.add(slot);
                }
            }
        }
        return slots;
    }

    private List<Integer> getEditorItemSlots(Inventory inventory) {
        Set<Integer> saveSlots = getSaveSlots(inventory.getSize());
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < inventory.getSize(); i++) {
            if (!saveSlots.contains(i)) {
                slots.add(i);
            }
        }
        return slots;
    }

    private void placePreviewRewards(Inventory inventory, CaseDefinition definition, Set<Integer> blockedSlots) {
        List<Integer> availableSlots = getPreviewRewardSlots(inventory, blockedSlots, definition.rewards().size());
        List<CaseReward> rewards = definition.rewards();

        for (int i = 0; i < rewards.size() && i < availableSlots.size(); i++) {
            int targetSlot = availableSlots.get(i);
            inventory.setItem(targetSlot, createRewardDisplay(rewards.get(i)));
        }
    }

    private List<Integer> getPreviewRewardSlots(Inventory inventory, Set<Integer> blockedSlots, int itemCount) {
        List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (!blockedSlots.contains(slot)) {
                slots.add(slot);
            }
        }
        return slots;
    }

    private ItemStack createRewardDisplay(CaseReward reward) {
        RewardDisplayCache cached = rewardDisplayCache.get(reward);
        if (cached != null && Double.compare(cached.chance(), reward.chance()) == 0) {
            return cached.item().clone();
        }

        ItemStack item = reward.item().clone();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
            List<String> chanceLoreConfig;
            if (config.isList("chance-lore")) {
                chanceLoreConfig = config.getStringList("chance-lore");
            } else if (config.isString("chance-lore")) {
                chanceLoreConfig = List.of(config.getString("chance-lore"));
            } else {
                chanceLoreConfig = List.of("", "&8> &7Szansa: &f{chance}%");
            }

            String formattedChance = formatChance(reward.chance());
            for (String line : chanceLoreConfig) {
                String processed = line
                    .replace("{chance}", formattedChance)
                    .replace("%chance%", formattedChance)
                    .replace("{szansa}", formattedChance)
                    .replace("%szansa%", formattedChance);
                lore.add(ColorUtils.colorizeComponent(processed));
            }
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        rewardDisplayCache.put(reward, new RewardDisplayCache(reward.chance(), item.clone()));
        return item;
    }

    private String resolveTitle(String caseTitle, String fallback, String caseName) {
        String title = caseTitle == null || caseTitle.isBlank() ? fallback : caseTitle;
        return title.replace("{case}", caseName);
    }

    private int inventorySize(int rows) {
        return normalizeRows(rows) * 9;
    }

    private int normalizeRows(int rows) {
        return Math.max(1, Math.min(6, rows));
    }

    public Component colorMessage(String path, Map<String, String> placeholders) {
        String raw = config.getString(path, "");
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            raw = raw.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return ColorUtils.colorizeComponent(raw);
    }

    public String getMessage(String path) {
        return config.getString("messages." + path, "");
    }

    public String formatChance(double chance) {
        synchronized (CHANCE_FORMAT) {
            return CHANCE_FORMAT.format(chance);
        }
    }

    public Double parseChance(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim()
            .replaceAll("(?i)§[0-9A-FK-ORX]", "")
            .replace("%", "")
            .replace(',', '.');
        if (normalized.isBlank()) {
            return null;
        }
        if (!CHANCE_NUMBER_PATTERN.matcher(normalized).matches()) {
            Matcher matcher = CHANCE_NUMBER_PATTERN.matcher(normalized);
            if (!matcher.find()) {
                return null;
            }
            normalized = matcher.group();
        }
        try {
            return clampChance(Double.parseDouble(normalized));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private double clampChance(double chance) {
        if (Double.isNaN(chance) || Double.isInfinite(chance)) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(100.0D, chance));
    }

    private double chanceForStorage(double chance) {
        double clamped = clampChance(chance);
        return Math.round(clamped * 1_000_000D) / 1_000_000D;
    }

    private String normalizeCustomItemId(String raw) {
        if (raw == null) {
            return "";
        }
        String normalized = Normalizer.normalize(raw, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT)
            .trim()
            .replace(' ', '-')
            .replace('_', '-')
            .replaceAll("[^a-z0-9-]", "");
        while (normalized.contains("--")) {
            normalized = normalized.replace("--", "-");
        }
        return normalized;
    }

    public String getReadableItemName(ItemStack item) {
        if (item == null) {
            return "";
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.displayName() != null) {
            return LEGACY.serialize(meta.displayName());
        }
        return prettifyMaterial(item.getType());
    }

    private String prettifyMaterial(Material material) {
        String[] parts = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }

    private void saveCase(CaseDefinition definition) {
        invalidateCaseGuiCache(definition.name());
        String path = "cases." + definition.name();
        casesConfig.set(path + ".enabled", definition.enabled());
        if (definition.inventoryName() != null && !definition.inventoryName().isBlank()) {
            casesConfig.set(path + ".inventoryName", definition.inventoryName());
        } else {
            casesConfig.set(path + ".inventoryName", null);
        }
        casesConfig.set(path + ".messageEnabled", definition.messageEnabled());
        String messageText = definition.messageText() == null || definition.messageText().isBlank()
            ? globalMessage
            : definition.messageText();
        casesConfig.set(path + ".messages.open", messageText);

        casesConfig.set(path + ".location", null);
        List<Map<String, Object>> serializedLocations = new ArrayList<>();
        for (CaseBlockLocation location : definition.locations()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("world", location.world());
            map.put("x", location.x());
            map.put("y", location.y());
            map.put("z", location.z());
            serializedLocations.add(map);
        }
        casesConfig.set(path + ".locations", serializedLocations);

        casesConfig.set(path + ".type", definition.keyType().name());
        casesConfig.set(path + ".keyType", null);
        casesConfig.set(path + ".key-type", null);
        casesConfig.set(path + ".key_type", null);
        casesConfig.set(path + ".key", definition.key() == null ? "" : ItemSerializer.serializeItem(definition.key()));

        // Save individual GUI settings
        casesConfig.set(path + ".individualGui", definition.individualGui());
        if (definition.individualGui()) {
            saveGuiDecorations(path + ".gui.preview.decoration", definition.previewDecorations());
            saveGuiDecorations(path + ".gui.animation.decoration", definition.animationDecorations());
            saveGuiDecorations(path + ".gui.result.decoration", definition.resultDecorations());
        } else {
            casesConfig.set(path + ".gui", null);
        }

        List<Map<String, Object>> serializedRewards = new ArrayList<>();
        for (CaseReward reward : definition.rewards()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("chance", chanceForStorage(reward.chance()));
            if (reward.editorSlot() != null) {
                map.put("slot", reward.editorSlot());
            }
            map.put("item", ItemSerializer.serializeItem(reward.item()));
            serializedRewards.add(map);
        }
        casesConfig.set(path + ".items", serializedRewards);
        saveCasesConfig();
    }

    private void saveGuiDecorations(String path, List<CaseGuiDecoration> decorations) {
        if (decorations == null || decorations.isEmpty()) {
            casesConfig.set(path, null);
            return;
        }

        List<Map<String, Object>> serialized = new ArrayList<>();
        for (CaseGuiDecoration decoration : decorations) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("slot", decoration.slots());

            YamlConfiguration temp = new YamlConfiguration();
            temp.set("item", decoration.item());
            Map<String, Object> itemMap = temp.getConfigurationSection("item").getValues(true);
            map.put("item", itemMap);

            serialized.add(map);
        }
        casesConfig.set(path, serialized);
    }

    public void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException exception) {
            plugin.getLogger().warning("Nie udalo sie zapisac addons/cases/cases-settings.yml: " + exception.getMessage());
        }
    }

    public void saveCasesConfig() {
        try {
            casesConfig.save(casesFile);
        } catch (IOException exception) {
            plugin.getLogger().warning("Nie udalo sie zapisac addons/cases/cases.yml: " + exception.getMessage());
        }
    }

    public void shutdown() {
        if (rewardRotationTask != null) {
            rewardRotationTask.cancel();
            rewardRotationTask = null;
        }
        for (NinjaBoxWorldAnimator animator : ninjaBoxAnimations.values()) {
            try {
                animator.cancelAndReward();
            } catch (Throwable ignored) {
            }
        }
        ninjaBoxAnimations.clear();
        for (java.util.concurrent.atomic.AtomicBoolean cancelled : animationCancelled.values()) {
            cancelled.set(true);
        }
        animationCancelled.clear();
        animationResults.clear();
        openingPlayers.clear();
        cooldowns.clear();
        rewardRotationOffsets.invalidateAll(); // Caffeine Cache method
    }

    private String normalizeCaseName(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private int resolveEditorSlot(CaseReward reward, List<Integer> itemSlots, Set<Integer> usedSlots) {
        Integer configuredSlot = reward.editorSlot();
        if (configuredSlot != null && itemSlots.contains(configuredSlot) && !usedSlots.contains(configuredSlot)) {
            return configuredSlot;
        }
        for (int slot : itemSlots) {
            if (!usedSlots.contains(slot)) {
                return slot;
            }
        }
        return -1;
    }

    private record RawReward(ItemStack item, double chance, Integer editorSlot) {
    }

    public record RotationResult(boolean success, String caseName, String itemId, String time) {
        private static RotationResult unavailable(String caseName) {
            return new RotationResult(false, caseName, "", "0s");
        }
    }

    private record RewardRotation(String caseName, long intervalMillis, String sourceItemId, List<String> itemIds) {
        private String currentItemId(long now, long bucketOffset) {
            if (itemIds.size() == 1) {
                return itemIds.get(0);
            }

            long bucket = (now / intervalMillis) + bucketOffset;
            int selectedIndex = selectIndex(bucket);
            int previousIndex = selectIndex(bucket - 1L);
            if (selectedIndex == previousIndex) {
                selectedIndex = Math.floorMod(selectedIndex + 1, itemIds.size());
            }
            return itemIds.get(selectedIndex);
        }

        private int selectIndex(long bucket) {
            Random seeded = new Random(Objects.hash(caseName, bucket));
            return seeded.nextInt(itemIds.size());
        }

        private boolean matches(String itemId) {
            return itemId != null && (itemId.equals(sourceItemId) || itemIds.contains(itemId));
        }

        private long millisUntilNextChange(long now) {
            long elapsed = Math.floorMod(now, intervalMillis);
            return elapsed == 0L ? intervalMillis : intervalMillis - elapsed;
        }
    }

    private CustomItemSource resolveCustomItemSource() {
        OgHubCustomItemsManager ogHub = plugin.getOgHubCustomItemsManager();
        if (ogHub != null) {
            return new CustomItemSource() {
                @Override
                public ItemStack getConfiguredItem(String id) {
                    return ogHub.getConfiguredItem(id);
                }

                @Override
                public String identifyCustomItem(ItemStack item) {
                    return ogHub.identifyCustomItem(item);
                }
            };
        }
        MinestarOGCustomItemsManager minestar = plugin.getMinestarOGCustomItemsManager();
        if (minestar == null) {
            return null;
        }
        return new CustomItemSource() {
            @Override
            public ItemStack getConfiguredItem(String id) {
                return minestar.getConfiguredItem(id);
            }

            @Override
            public String identifyCustomItem(ItemStack item) {
                return minestar.identifyCustomItem(item);
            }
        };
    }

    private interface CustomItemSource {
        ItemStack getConfiguredItem(String id);

        String identifyCustomItem(ItemStack item);
    }

    private record GuiLayout(
        String title,
        int rows,
        List<CaseGuiDecoration> decorations,
        List<CaseGuiButton> buttons,
        Map<Integer, CaseOpenType> buttonSlots
    ) {
        private static GuiLayout empty(String title, int rows) {
            return new GuiLayout(title, rows, Collections.emptyList(), Collections.emptyList(), Map.of());
        }
    }

    private record RewardDisplayCache(double chance, ItemStack item) {}
}
