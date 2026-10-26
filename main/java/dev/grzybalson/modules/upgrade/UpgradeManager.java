package dev.grzybalson.modules.upgrade;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.grzybalson.HollyCore;
import dev.grzybalson.config.HollyDataPaths;
import dev.grzybalson.anarchiagg.customitems.util.EventItemRegistry;
import dev.grzybalson.gui.UpgradeGUI;
import dev.grzybalson.modules.unique.UniqueItemService;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.ItemSerializer;
import dev.grzybalson.utils.MoneyAmountUtil;
import dev.grzybalson.utils.NBTUtils;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.io.BukkitObjectInputStream;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UpgradeManager {

    private static final String NBT_LEVEL = "holly_upgrade_level";
    private static final String NBT_TYPE = "holly_upgrade_type";
    private static final String NBT_STYLE = "holly_upgrade_style";
    private static final String LEGACY_NBT_LEVEL = "upgradelevel";
    private static final String LEGACY_UPGRADE_CONFIG = "addons/ulepsz/itemupgrade.json";
    private static final String SHARP_DAMAGE_NAME = "holly_sharp_netherite_damage";
    private static final String SHARP_SPEED_NAME = "holly_sharp_netherite_speed";
    private static final String LEATHER_ARMOR_NAME = "holly_leather_netherite_armor";
    private static final String LEATHER_TOUGHNESS_NAME = "holly_leather_netherite_toughness";
    private static final String LEATHER_KB_NAME = "holly_leather_netherite_kb";
    private static final double NETHERITE_SWORD_DAMAGE_MODIFIER = 7.0D;
    private static final double NETHERITE_SWORD_SPEED_MODIFIER = -2.4D;
    private static final Pattern LEVEL_PATTERN = Pattern.compile("[✿★]\\s*(\\d+)");

    private static final List<NamespacedKey> LEGACY_LEVEL_KEYS = List.of(
        new NamespacedKey("pluginite-core", LEGACY_NBT_LEVEL),
        new NamespacedKey("marcuscore", LEGACY_NBT_LEVEL)
    );

    private final HollyCore plugin;
    private final File configFile;
    private YamlConfiguration config;
    private UpgradeStyle activeStyle = UpgradeStyle.STANDARD;
    private final Map<String, List<Integer>> configuredLevelsCache = new ConcurrentHashMap<>();
    private final Map<String, ItemStack> configuredItemCache = new HashMap<>();
    private final Map<String, Optional<ItemStack>> bestConfiguredItemCache = new HashMap<>();
    private final Map<String, ItemStack> guiItemCache = new HashMap<>();
    private final Set<String> invalidConfiguredItemsLogged = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pendingUpgrades = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> upgradeCooldowns = new ConcurrentHashMap<>();

    public UpgradeManager(HollyCore plugin) {
        this.plugin = plugin;
        dev.grzybalson.internal.HollyIntegrityGuard.requireBound(plugin, "config:parse");
        this.configFile = new File(HollyDataPaths.getAddonFolder(plugin, "upgrade"), "upgrade.yml");
        reload();
    }

    public void reload() {
        HollyDataPaths.ensureResource(plugin, "addons/upgrade/upgrade.yml");
        if (!configFile.exists()) {
            try {
                configFile.createNewFile();
            } catch (IOException exception) {
                plugin.getLogger().warning("Nie udalo sie utworzyc addons/upgrade/upgrade.yml: " + exception.getMessage());
            }
        }

        this.config = YamlConfiguration.loadConfiguration(configFile);
        configuredLevelsCache.clear();
        configuredItemCache.clear();
        bestConfiguredItemCache.clear();
        guiItemCache.clear();
        invalidConfiguredItemsLogged.clear();
        ensureDefaults();
        this.activeStyle = UpgradeStyle.fromArgument(config.getString("active-style", "STANDARD"));
        migrateLegacyUlepszConfig();
        save();
    }

    public UpgradeStyle getActiveStyle() {
        return activeStyle;
    }

    public void setActiveStyle(UpgradeStyle style) {
        if (style == null) {
            style = UpgradeStyle.STANDARD;
        }
        if (this.activeStyle == style) {
            return;
        }
        this.activeStyle = style;
        config.set("active-style", style.name());
        configuredLevelsCache.clear();
        configuredItemCache.clear();
        bestConfiguredItemCache.clear();
        guiItemCache.clear();
        saveAsync();
    }

    public boolean isArmorType(UpgradeType type) {
        return type == UpgradeType.HELMET
            || type == UpgradeType.CHEST_PLATE
            || type == UpgradeType.LEGGINGS
            || type == UpgradeType.BOOTS;
    }

    public int removeArmorLevel(int level) {
        if (level < 1) {
            return 0;
        }

        int removed = 0;
        for (UpgradeType type : UpgradeType.values()) {
            if (!isArmorType(type)) {
                continue;
            }
            String path = itemPath(type, level);
            if (config.contains(path)) {
                config.set(path, null);
                removed++;
            }
        }

        if (removed > 0) {
            configuredLevelsCache.clear();
            configuredItemCache.clear();
            bestConfiguredItemCache.clear();
            guiItemCache.clear();
            invalidConfiguredItemsLogged.clear();
            save();
        }
        return removed;
    }

    private void ensureDefaults() {
        setDefault("gui.title", "&8Ulepszenia przedmiotów");
        setDefault("gui.levels-title", "&8Poziomy: {TYPE}");
        setDefault("gui.rows", 5);
        setDefault("gui.back-slot", 40);
        setDefault("gui.prev-page-slot", 39);
        setDefault("gui.next-page-slot", 41);
        setDefault("gui.prev-page-name", "&d&lPoprzednia strona");
        setDefault("gui.next-page-name", "&d&lNastępna strona");
        setDefault("gui.back-item.material", "BARRIER");
        setDefault("gui.back-item.name", "&cPowrót");
        setDefault("gui.back-item.lore", List.of("&7Kliknij, aby wrócić do głównego GUI."));
        setDefault("gui.level-slots", List.of(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34));
        setDefault("gui.include-original-lore", true);
        setDefault("gui.upgrade-item-lore", List.of(
            "&f",
            "&7Cena: &a{PRICE}$",
            "&7Nazwa itemka: &f{NAME}",
            "",
            "&aKliknij lewym, aby ulepszyć",
            "&eKliknij prawym, aby sprawdzić itemki"
        ));
        setDefault("gui.level-item-lore", List.of(
            "&f",
            "&7Cena: &a{PRICE}$",
            "&7Nazwa itemka: &f{NAME}"
        ));

        setDefault("messages.no-item", "&#FF0000✘ &8Ξ &cMusisz trzymać ulepszany przedmiot w ręce!");
        setDefault("messages.invalid-item", "&#FF0000✘ &8Ξ &cTen przedmiot nie pasuje do wybranej kategorii!");
        setDefault("messages.minimum-level", "&#FF0000✘ &8Ξ &cTen typ można dodać dopiero od poziomu &e{MIN_LEVEL}&c.");
        setDefault("messages.no-next-level", "&#FF0000✘ &8Ξ &cNie ma kolejnego poziomu dla tego przedmiotu!");
        setDefault("messages.max-level", "&#FF0000✘ &8Ξ &cPosiadasz maksymalne ulepszenie tego przedmiotu!");
        setDefault("messages.no-free-slot", "&#FF0000✘ &8Ξ &cNie masz wolnego miejsca w ekwipunku!");
        setDefault("messages.not-enough-money", "&#FF0000✘ &8Ξ &cNie masz wystarczająco pieniędzy! Potrzebujesz &e{PRICE}$&c.");
        setDefault("messages.changed-item", "&#FF0000✘ &8Ξ &cPrzedmiot zmienił się podczas ulepszania. Pieniądze zostały zwrócone.");
        setDefault("messages.upgraded", "&#00FF3B✔ &8Ξ &#4EFA76Ulepszono &f{TYPE} &ado poziomu &e{LEVEL}&a za &e{PRICE}$&a!");
        setDefault("messages.bought", "&#00FF3B✔ &8Ξ &#4EFA76Kupiono &f{TYPE} &apoziom &e{LEVEL}&a za &e{PRICE}$&a!");
        setDefault("messages.created", "&#00FF3B✔ &8Ξ &#4EFA76Dodano &f{TYPE} &apoziom &e{LEVEL}&a za &e{PRICE}$&a.");
        setDefault("messages.generated", "&#00FF3B✔ &8Ξ &#4EFA76Wygenerowano poziom &e{LEVEL}&a dla &e{COUNT} &atypów.");
        setDefault("messages.cooldown", "&#FF0000✘ &8Ξ &cOdczekaj chwilę przed kolejnym ulepszeniem!");
        setDefault("click-cooldown-ms", 100);
        setDefault("active-style", "STANDARD");
        setDefault("apply-sword-custom-model-data", true);
        setDefault("messages.style-switched", "&#00FF3B✔ &8Ξ &#4EFA76Przełączono styl ulepszania na &e{STYLE}&a!");
        setDefault("best-effects.enabled", true);
        setDefault("best-effects.sword.enabled", true);
        setDefault("best-effects.full-set.enabled", true);

        setDefault("armor-create.hide-all-flags", true);
        setDefault("armor-create.hide-enchants", true);
        setDefault("armor-create.hide-attributes", true);
        setDefault("armor-create.level-emoji", "★");
        setDefault("armor-create.level-format", " &6{EMOJI}{LEVEL}");
        setDefault("armor-create.base-lore", List.of());
        setDefault("armor-create.armor-lore", List.of(
            "",
            "&x&0&C&9&4&C&E🛡 &8| &x&0&C&9&4&C&EOchrona {PROTECTION}",
            "&x&0&7&B&4&F&E❤ &8| &x&0&7&B&4&F&ENiezniszczalność {UNBREAKING}",
            ""
        ));
        setDefault("armor-create.sword-lore", List.of(
            "",
            "&x&B&5&0&0&0&0🪓 &8| &x&B&5&0&0&0&0Ostrość {SHARPNESS}",
            "&x&0&7&B&4&F&E❤ &8| &x&0&7&B&4&F&ENiezniszczalność {UNBREAKING}",
            "{FIRE_ASPECT_LINE}",
            ""
        ));
        setDefault("armor-create.fire-aspect-line", "&x&F&F&6&0&0&0🔥 &8| &x&F&F&6&0&0&0Zaklęty ogień {FIRE_ASPECT}");
        setDefault("armor-create.tool-lore", List.of(
            "",
            "&x&F&F&A&8&0&0⛏ &8| &x&F&F&A&8&0&0Wydajność {EFFICIENCY}",
            "&x&0&0&F&F&8&6✦ &8| &x&0&0&F&F&8&6Szczęście {FORTUNE}",
            "&x&0&7&B&4&F&E❤ &8| &x&0&7&B&4&F&ENiezniszczalność {UNBREAKING}",
            ""
        ));

        // Zawsze uzupełniaj brakujące typy / mnożniki (nie tylko przy pustym pliku).
        for (UpgradeType type : UpgradeType.values()) {
            String base = "types." + type.getConfigKey();
            boolean defaultEnabled = !(type == UpgradeType.HOE || type == UpgradeType.AXE);
            setDefault(base + ".enabled", defaultEnabled);
            setDefault(base + ".slot", type.getDefaultSlot());
            setDefault(base + ".preview-material", type.getDefaultMaterial().name());
            setDefault(base + ".item-material", type.getDefaultMaterial().name());
            setDefault(base + ".display-prefix", type.getDefaultPrefix());
            setDefault(base + ".minimum-level", type.getMinimumLevel());
            setDefault(base + ".price-multiplier", type.getDefaultPriceMultiplier());
            setDefault(base + ".accepted-materials", defaultAcceptedMaterials(type));
        }

        // v2 = mnożniki jak HollyService (miecz/kilof×4, nożyce×8, zbroja×1)
        if (config.getInt("price-multipliers-version", 0) < 2) {
            for (UpgradeType type : UpgradeType.values()) {
                config.set("types." + type.getConfigKey() + ".price-multiplier", type.getDefaultPriceMultiplier());
            }
            config.set("price-multipliers-version", 2);
        }
    }

    private List<String> defaultAcceptedMaterials(UpgradeType type) {
        return switch (type) {
            case SWORD -> List.of(
                "WOODEN_SWORD", "STONE_SWORD", "IRON_SWORD", "GOLDEN_SWORD", "DIAMOND_SWORD", "NETHERITE_SWORD"
            );
            case HOE -> List.of("WOODEN_HOE", "STONE_HOE", "IRON_HOE", "GOLDEN_HOE", "DIAMOND_HOE", "NETHERITE_HOE");
            case HELMET -> List.of("LEATHER_HELMET", "CHAINMAIL_HELMET", "IRON_HELMET", "GOLDEN_HELMET", "DIAMOND_HELMET", "NETHERITE_HELMET", "PLAYER_HEAD");
            case CHEST_PLATE -> List.of("LEATHER_CHESTPLATE", "CHAINMAIL_CHESTPLATE", "IRON_CHESTPLATE", "GOLDEN_CHESTPLATE", "DIAMOND_CHESTPLATE", "NETHERITE_CHESTPLATE");
            case LEGGINGS -> List.of("LEATHER_LEGGINGS", "CHAINMAIL_LEGGINGS", "IRON_LEGGINGS", "GOLDEN_LEGGINGS", "DIAMOND_LEGGINGS", "NETHERITE_LEGGINGS");
            case ELYTRA -> List.of("ELYTRA");
            case BOOTS -> List.of("LEATHER_BOOTS", "CHAINMAIL_BOOTS", "IRON_BOOTS", "GOLDEN_BOOTS", "DIAMOND_BOOTS", "NETHERITE_BOOTS");
            case PICKAXE -> List.of("DIAMOND_PICKAXE", "NETHERITE_PICKAXE");
            case SHEARS -> List.of("SHEARS", "DIAMOND_SHOVEL", "NETHERITE_SHOVEL");
            case AXE -> List.of("WOODEN_AXE", "STONE_AXE", "IRON_AXE", "GOLDEN_AXE", "DIAMOND_AXE", "NETHERITE_AXE");
        };
    }

    private void setDefault(String path, Object value) {
        if (!config.contains(path)) {
            config.set(path, value);
        }
    }

    private void migrateLegacyUlepszConfig() {
        File legacyFile = new File(plugin.getDataFolder(), LEGACY_UPGRADE_CONFIG);
        if (!legacyFile.isFile()) {
            return;
        }

        int migrated = 0;
        try (FileReader reader = new FileReader(legacyFile, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray upgrades = root.getAsJsonArray("upgrades");
            if (upgrades == null) {
                return;
            }

            for (JsonElement element : upgrades) {
                if (element == null || !element.isJsonObject()) {
                    continue;
                }

                JsonObject levelObject = element.getAsJsonObject();
                int level = jsonInt(levelObject, "thisLevel", -1);
                if (level < 1) {
                    continue;
                }

                JsonObject upgradeItems = jsonObject(levelObject, "upgradeItems");
                for (Map.Entry<String, JsonElement> entry : upgradeItems.entrySet()) {
                    UpgradeType type = legacyUpgradeType(entry.getKey());
                    if (type == null || entry.getValue() == null || !entry.getValue().isJsonObject()) {
                        continue;
                    }
                    if (hasValidConfiguredItem(type, level)) {
                        continue;
                    }

                    JsonObject offer = entry.getValue().getAsJsonObject();
                    JsonObject itemStack = jsonObject(offer, "itemStack");
                    ItemStack item = deserializeLegacyItem(jsonString(itemStack, "serializedItem", ""));
                    if (item == null || item.getType().isAir()) {
                        continue;
                    }

                    item.setAmount(1);
                    setMigratedConfiguredItem(type, level, Math.max(0.0D, jsonDouble(offer, "price", 0.0D)), item);
                    migrated++;
                }
            }
        } catch (Exception exception) {
            plugin.getLogger().warning("Nie udalo sie zmigrowac addons/ulepsz/itemupgrade.json: " + exception.getMessage());
        }

        if (migrated > 0) {
            plugin.getLogger().info("Zmigrowano " + migrated + " starych wpisow /ulepsz z addons/ulepsz/itemupgrade.json.");
        }
    }

    private boolean hasValidConfiguredItem(UpgradeType type, int level) {
        return loadConfiguredItem(type, level) != null;
    }

    private void setMigratedConfiguredItem(UpgradeType type, int level, double price, ItemStack item) {
        tagItem(item, type, level);
        hideTooltipDetails(item);
        applySwordCustomModelData(item, type, level);
        applyNetheriteSwordAttributes(item, type);
        config.set(itemPath(type, level) + ".price", price);
        config.set(itemPath(type, level) + ".item", ItemSerializer.serializeItem(item));
    }

    private ItemStack deserializeLegacyItem(String value) {
        ItemStack item = ItemSerializer.deserializeItem(value);
        if (item != null) {
            return item;
        }

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getMimeDecoder().decode(value));
             BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {
            Object object = dataInput.readObject();
            return object instanceof ItemStack itemStack ? itemStack : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private UpgradeType legacyUpgradeType(String value) {
        if (value == null) {
            return null;
        }

        return switch (value.toUpperCase(Locale.ROOT)) {
            case "SWORD" -> UpgradeType.SWORD;
            case "HOE", "MOTYKA" -> UpgradeType.HOE;
            case "HELMET" -> UpgradeType.HELMET;
            case "CHEST_PLATE", "CHESTPLATE" -> UpgradeType.CHEST_PLATE;
            case "LEGGINGS" -> UpgradeType.LEGGINGS;
            case "BOOTS" -> UpgradeType.BOOTS;
            case "PICKAXE" -> UpgradeType.PICKAXE;
            case "AXE" -> UpgradeType.AXE;
            case "ELYTRA" -> UpgradeType.ELYTRA;
            case "SHEARS", "SHOVEL" -> UpgradeType.SHEARS;
            default -> null;
        };
    }

    private JsonObject jsonObject(JsonObject object, String key) {
        return object != null && object.has(key) && object.get(key).isJsonObject()
            ? object.getAsJsonObject(key)
            : new JsonObject();
    }

    private String jsonString(JsonObject object, String key, String fallback) {
        return object != null && object.has(key) ? object.get(key).getAsString() : fallback;
    }

    private int jsonInt(JsonObject object, String key, int fallback) {
        return object != null && object.has(key) ? object.get(key).getAsInt() : fallback;
    }

    private double jsonDouble(JsonObject object, String key, double fallback) {
        return object != null && object.has(key) ? object.get(key).getAsDouble() : fallback;
    }

    public void save() {
        try {
            config.save(configFile);
        } catch (IOException exception) {
            plugin.getLogger().warning("Nie udalo sie zapisac addons/upgrade/upgrade.yml: " + exception.getMessage());
        }
    }

    public void saveAsync() {
        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, this::save);
    }

    public String getGuiTitle() {
        return config.getString("gui.title", "&8Ulepszenia przedmiotów");
    }

    public String getLevelsTitle(UpgradeType type) {
        return config.getString("gui.levels-title", "&8Poziomy: {TYPE}")
            .replace("{TYPE}", getDisplayPrefix(type));
    }

    public int getGuiSize() {
        return plugin.getGuiFactory().getInventorySize(config.getInt("gui.rows", 5));
    }

    public String getTypeConfigKey(UpgradeType type) {
        if (type == null) return "";
        String primaryKey = type.getConfigKey();
        if (config.isConfigurationSection("types." + primaryKey)) {
            return primaryKey;
        }
        if (type == UpgradeType.SHEARS && config.isConfigurationSection("types.SHOVEL")) {
            return "SHOVEL";
        }
        if (type == UpgradeType.SHEARS && config.isConfigurationSection("types.LOPATA")) {
            return "LOPATA";
        }
        if (type == UpgradeType.AXE && config.isConfigurationSection("types.SIEKIERA")) {
            return "SIEKIERA";
        }
        if (type == UpgradeType.SWORD && config.isConfigurationSection("types.BRON")) {
            return "BRON";
        }
        return primaryKey;
    }

    public boolean isCategoryEnabled(UpgradeType type) {
        if (type == null) return false;
        boolean defaultEnabled = !(type == UpgradeType.HOE || type == UpgradeType.AXE);

        String key = getTypeConfigKey(type);
        if (config.isBoolean("types." + key)) {
            return config.getBoolean("types." + key);
        }

        String lowerKey = key.toLowerCase(Locale.ROOT);
        if (config.isBoolean("types." + lowerKey)) {
            return config.getBoolean("types." + lowerKey);
        }

        if (config.contains("types." + key + ".enabled")) {
            return config.getBoolean("types." + key + ".enabled", defaultEnabled);
        }
        if (config.contains("types." + lowerKey + ".enabled")) {
            return config.getBoolean("types." + lowerKey + ".enabled", defaultEnabled);
        }

        if (config.isBoolean(key)) {
            return config.getBoolean(key);
        }
        if (config.isBoolean(lowerKey)) {
            return config.getBoolean(lowerKey);
        }

        return defaultEnabled;
    }

    public int getTypeSlot(UpgradeType type) {
        return config.getInt("types." + getTypeConfigKey(type) + ".slot", type.getDefaultSlot());
    }

    public Map<UpgradeType, Integer> getMainGuiTypeSlots() {
        int size = getGuiSize();
        Map<UpgradeType, Integer> slots = new EnumMap<>(UpgradeType.class);
        Set<Integer> occupied = new HashSet<>();

        for (UpgradeType type : UpgradeType.values()) {
            if (!isCategoryEnabled(type)) {
                continue;
            }
            if (activeStyle.isSkyPvp() && type == UpgradeType.AXE) {
                // W SkyPVP slot kilofa = siekiera, osobnej kategorii AXE nie dublujemy.
                continue;
            }
            int configuredSlot = getTypeSlot(type);
            int resolvedSlot = resolveTypeSlot(type, configuredSlot, size, occupied);
            slots.put(type, resolvedSlot);
            occupied.add(resolvedSlot);
        }
        return slots;
    }

    private int resolveTypeSlot(UpgradeType type, int configuredSlot, int inventorySize, Set<Integer> occupied) {
        if (isValidSlot(configuredSlot, inventorySize) && !occupied.contains(configuredSlot)) {
            return configuredSlot;
        }

        int recommendedSlot = recommendedTypeSlot(type);
        if (isValidSlot(recommendedSlot, inventorySize) && !occupied.contains(recommendedSlot)) {
            return recommendedSlot;
        }

        for (int slot : typeSlotFallbackOrder(inventorySize)) {
            if (!occupied.contains(slot)) {
                return slot;
            }
        }

        return Math.max(0, Math.min(configuredSlot, Math.max(0, inventorySize - 1)));
    }

    private int recommendedTypeSlot(UpgradeType type) {
        return switch (type) {
            case HELMET -> 20;
            case CHEST_PLATE -> 21;
            case LEGGINGS -> 22;
            case BOOTS -> 23;
            case ELYTRA -> 24;
            case SHEARS -> 29;
            case SWORD -> 30;
            case HOE -> 31;
            case PICKAXE -> 32;
            case AXE -> 33;
        };
    }

    private List<Integer> typeSlotFallbackOrder(int inventorySize) {
        List<Integer> slots = new ArrayList<>();
        int[] preferred = {20, 21, 22, 23, 24, 29, 30, 31, 32, 33, 10, 11, 12, 13, 14, 15, 16, 19, 25, 28, 34};
        for (int slot : preferred) {
            if (isValidSlot(slot, inventorySize) && !slots.contains(slot)) {
                slots.add(slot);
            }
        }
        for (int slot = 0; slot < inventorySize; slot++) {
            if (!slots.contains(slot)) {
                slots.add(slot);
            }
        }
        return slots;
    }

    private boolean isValidSlot(int slot, int inventorySize) {
        return slot >= 0 && slot < inventorySize;
    }

    public int getBackSlot() {
        return config.getInt("gui.back-slot", 40);
    }

    public int getPrevPageSlot() {
        return config.getInt("gui.prev-page-slot", 39);
    }

    public int getNextPageSlot() {
        return config.getInt("gui.next-page-slot", 41);
    }

    public String getPrevPageName() {
        return config.getString("gui.prev-page-name", "&d&lPoprzednia strona");
    }

    public String getNextPageName() {
        return config.getString("gui.next-page-name", "&d&lNastępna strona");
    }

    public long getClickCooldownMillis() {
        return 100L;
    }

    public List<Integer> getLevelSlots() {
        List<Integer> configured = config.getIntegerList("gui.level-slots");
        int size = getGuiSize();
        int rows = size / 9;
        int lastContentRow = Math.max(1, rows - 2);
        List<Integer> compactSlots = new ArrayList<>();
        for (int row = 1; row <= lastContentRow; row++) {
            for (int column = 1; column <= 7; column++) {
                int slot = row * 9 + column;
                if (slot < size) {
                    compactSlots.add(slot);
                }
            }
        }

        int requestedCount = configured.isEmpty()
            ? compactSlots.size()
            : new HashSet<>(configured).size();
        int count = Math.min(compactSlots.size(), requestedCount);
        return List.copyOf(compactSlots.subList(0, count));
    }

    public Material getPreviewMaterial(UpgradeType type) {
        if (activeStyle.isSkyPvp() && type == UpgradeType.PICKAXE) {
            return Material.NETHERITE_AXE;
        }
        return materialOrDefault(config.getString("types." + getTypeConfigKey(type) + ".preview-material"), type.getDefaultMaterial());
    }

    public Material getGeneratedMaterial(UpgradeType type) {
        if (activeStyle.isSkyPvp() && type == UpgradeType.PICKAXE) {
            return Material.NETHERITE_AXE;
        }
        return materialOrDefault(config.getString("types." + getTypeConfigKey(type) + ".item-material"), type.getDefaultMaterial());
    }

    public String getDisplayPrefix(UpgradeType type) {
        if (activeStyle.isSkyPvp() && type == UpgradeType.PICKAXE) {
            return "Siekiera";
        }
        return config.getString("types." + getTypeConfigKey(type) + ".display-prefix", type.getDefaultPrefix());
    }

    public int getMinimumLevel(UpgradeType type) {
        return config.getInt("types." + getTypeConfigKey(type) + ".minimum-level", type.getMinimumLevel());
    }

    public double getPriceMultiplier(UpgradeType type) {
        return config.getDouble("types." + getTypeConfigKey(type) + ".price-multiplier", type.getDefaultPriceMultiplier());
    }

    private Material materialOrDefault(String materialName, Material fallback) {
        if (materialName == null || materialName.isBlank()) {
            return fallback;
        }
        String normalized = materialName.trim().toUpperCase(Locale.ROOT);
        if (normalized.equals("SHOVEL")) {
            return Material.NETHERITE_SHOVEL;
        }
        if (normalized.equals("AXE")) {
            return Material.NETHERITE_AXE;
        }
        if (normalized.equals("SWORD")) {
            return Material.NETHERITE_SWORD;
        }
        if (normalized.equals("HOE")) {
            return Material.NETHERITE_HOE;
        }
        if (normalized.equals("HELMET")) {
            return Material.NETHERITE_HELMET;
        }
        if (normalized.equals("CHESTPLATE") || normalized.equals("CHEST_PLATE")) {
            return Material.NETHERITE_CHESTPLATE;
        }
        if (normalized.equals("LEGGINGS")) {
            return Material.NETHERITE_LEGGINGS;
        }
        if (normalized.equals("BOOTS")) {
            return Material.NETHERITE_BOOTS;
        }
        if (normalized.equals("PICKAXE")) {
            return Material.NETHERITE_PICKAXE;
        }
        Material material = Material.matchMaterial(normalized);
        return material != null ? material : fallback;
    }

    public UpgradeType detectType(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }

        if (isExcalibur(item) && isCategoryEnabled(UpgradeType.SWORD)) {
            return UpgradeType.SWORD;
        }

        String materialName = item.getType().name();
        // Miecze zawsze jako SWORD — zanim ostrość / accepted innych typów je przechwyci.
        if (materialName.endsWith("_SWORD") && isCategoryEnabled(UpgradeType.SWORD)) {
            return UpgradeType.SWORD;
        }

        String storedType = NBTUtils.getString(item, NBT_TYPE);
        UpgradeType nbtType = UpgradeType.fromArgument(storedType);
        if (nbtType != null) {
            if (nbtType == UpgradeType.SHEARS && materialName.endsWith("_AXE")) {
                if (isCategoryEnabled(UpgradeType.AXE)) {
                    return UpgradeType.AXE;
                }
            } else if (isCategoryEnabled(nbtType)) {
                return nbtType;
            }
        }

        if (item.getType() == Material.PLAYER_HEAD && getLegacyUpgradeLevel(item) != null && isCategoryEnabled(UpgradeType.HELMET)) {
            return UpgradeType.HELMET;
        }

        if (materialName.endsWith("_AXE") && isCategoryEnabled(UpgradeType.AXE)) {
            return UpgradeType.AXE;
        }

        if (hasSharpness(item) && isCategoryEnabled(UpgradeType.SWORD)) {
            return UpgradeType.SWORD;
        }

        for (UpgradeType type : UpgradeType.values()) {
            if (!isCategoryEnabled(type)) continue;
            List<String> accepted = config.getStringList("types." + getTypeConfigKey(type) + ".accepted-materials");
            if (accepted.stream().anyMatch(value -> value.equalsIgnoreCase(materialName))) {
                return type;
            }
        }

        if (materialName.contains("HELMET") && isCategoryEnabled(UpgradeType.HELMET)) return UpgradeType.HELMET;
        if (materialName.contains("CHESTPLATE") && isCategoryEnabled(UpgradeType.CHEST_PLATE)) return UpgradeType.CHEST_PLATE;
        if (materialName.contains("LEGGINGS") && isCategoryEnabled(UpgradeType.LEGGINGS)) return UpgradeType.LEGGINGS;
        if (materialName.contains("BOOTS") && isCategoryEnabled(UpgradeType.BOOTS)) return UpgradeType.BOOTS;
        if (materialName.equals("ELYTRA") && isCategoryEnabled(UpgradeType.ELYTRA)) return UpgradeType.ELYTRA;
        if (materialName.contains("HOE") && isCategoryEnabled(UpgradeType.HOE)) return UpgradeType.HOE;
        if (materialName.contains("PICKAXE") && isCategoryEnabled(UpgradeType.PICKAXE)) return UpgradeType.PICKAXE;
        if (materialName.endsWith("_AXE") && isCategoryEnabled(UpgradeType.AXE)) return UpgradeType.AXE;
        if ((materialName.contains("SHEARS") || materialName.contains("SHOVEL")) && isCategoryEnabled(UpgradeType.SHEARS)) return UpgradeType.SHEARS;
        return null;
    }

    private boolean hasSharpness(ItemStack item) {
        return item != null && item.containsEnchantment(Enchantment.DAMAGE_ALL);
    }

    public int getUpgradeLevel(ItemStack item) {
        Integer level = NBTUtils.getInt(item, NBT_LEVEL);
        if (level != null) {
            return level;
        }

        level = NBTUtils.getInt(item, LEGACY_NBT_LEVEL);
        if (level != null) {
            return Math.max(0, level);
        }

        level = getLegacyUpgradeLevel(item);
        if (level != null) {
            return Math.max(0, level);
        }

        if (item == null || !item.hasItemMeta()) {
            return 0;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return 0;
        }

        String stripped = ChatColor.stripColor(meta.getDisplayName());
        String customEmoji = ChatColor.stripColor(color(getArmorCreateEmoji()));
        Pattern pattern = (customEmoji == null || customEmoji.isBlank())
            ? LEVEL_PATTERN
            : Pattern.compile("(?:" + Pattern.quote(customEmoji) + "|[✿★])\\s*(\\d+)");
        Matcher matcher = pattern.matcher(stripped);
        if (!matcher.find()) {
            return 0;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private Integer getLegacyUpgradeLevel(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }

        PersistentDataContainer container = meta.getPersistentDataContainer();
        for (NamespacedKey key : LEGACY_LEVEL_KEYS) {
            Integer level = readInteger(container, key);
            if (level != null) {
                return level;
            }
        }
        return null;
    }

    private Integer readInteger(PersistentDataContainer container, NamespacedKey key) {
        Integer integerValue = container.get(key, PersistentDataType.INTEGER);
        if (integerValue != null) {
            return integerValue;
        }

        Short shortValue = container.get(key, PersistentDataType.SHORT);
        if (shortValue != null) {
            return shortValue.intValue();
        }

        Byte byteValue = container.get(key, PersistentDataType.BYTE);
        if (byteValue != null) {
            return byteValue.intValue();
        }

        String stringValue = container.get(key, PersistentDataType.STRING);
        if (stringValue == null) {
            return null;
        }
        try {
            return Integer.parseInt(stringValue);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public List<Integer> getConfiguredLevels(UpgradeType type) {
        return getConfiguredLevels(type, activeStyle);
    }

    public List<Integer> getConfiguredLevels(UpgradeType type, UpgradeStyle style) {
        UpgradeStyle resolved = style == null ? UpgradeStyle.STANDARD : style;
        return configuredLevelsCache.computeIfAbsent(resolved.name() + ":" + type.name(), ignored -> {
            Set<Integer> levelSet = new TreeSet<>();
            ConfigurationSection section = config.getConfigurationSection(itemBasePath(type, resolved));
            if (section != null) {
                for (String key : section.getKeys(false)) {
                    try {
                        int level = Integer.parseInt(key);
                        if (hasConfiguredItemEntry(type, level, resolved)) {
                            levelSet.add(level);
                        }
                    } catch (NumberFormatException ignoredEx) {
                    }
                }
            }

            // Standard: zachowaj stary fallback poziomow 1-10 gdy brak wpisow.
            // SkyPVP: pokazuj tylko realnie utworzone poziomy (osobny zestaw zbroi).
            if (levelSet.isEmpty() && resolved == UpgradeStyle.STANDARD) {
                int minLevel = getMinimumLevel(type);
                for (int lvl = minLevel; lvl <= 10; lvl++) {
                    levelSet.add(lvl);
                }
            }

            List<Integer> levels = new ArrayList<>(levelSet);
            levels.sort(Comparator.naturalOrder());
            return List.copyOf(levels);
        });
    }

    public int getMaxUpgradeLevel(UpgradeType type, UpgradeStyle style) {
        List<Integer> levels = getConfiguredLevels(type, style);
        if (levels.isEmpty()) {
            return 0;
        }
        return levels.get(levels.size() - 1);
    }

    public boolean isMaxUpgradeItem(ItemStack item, UpgradeType expectedType) {
        if (item == null || item.getType().isAir() || expectedType == null) {
            return false;
        }
        if (detectType(item) != expectedType) {
            return false;
        }
        int level = getUpgradeLevel(item);
        if (level <= 0) {
            return false;
        }
        int maxLevel = getMaxUpgradeLevel(expectedType, getItemStyle(item));
        return maxLevel > 0 && level >= maxLevel;
    }

    public boolean isHoldingMaxUpgradeSword(Player player) {
        return isMaxUpgradeItem(player.getInventory().getItemInMainHand(), UpgradeType.SWORD);
    }

    public boolean isWearingMaxUpgradeSet(Player player) {
        PlayerInventory inventory = player.getInventory();
        return isMaxUpgradeItem(inventory.getHelmet(), UpgradeType.HELMET)
            && isMaxUpgradeItem(inventory.getChestplate(), UpgradeType.CHEST_PLATE)
            && isMaxUpgradeItem(inventory.getLeggings(), UpgradeType.LEGGINGS)
            && isMaxUpgradeItem(inventory.getBoots(), UpgradeType.BOOTS);
    }

    public boolean isBestEffectsEnabled() {
        return config.getBoolean("best-effects.enabled", true);
    }

    public boolean isBestSwordEffectEnabled() {
        return isBestEffectsEnabled() && config.getBoolean("best-effects.sword.enabled", true);
    }

    public boolean isBestSetEffectEnabled() {
        return isBestEffectsEnabled() && config.getBoolean("best-effects.full-set.enabled", true);
    }

    public int getNextLevel(UpgradeType type, int currentLevel) {
        for (int level : getConfiguredLevels(type)) {
            if (level > currentLevel) {
                return level;
            }
        }
        return -1;
    }

    public double getPrice(UpgradeType type, int level) {
        return config.getDouble(itemPath(type, level) + ".price", 0.0);
    }

    public ItemStack getConfiguredItem(UpgradeType type, int level) {
        String key = itemCacheKey(type, level);
        ItemStack cached = configuredItemCache.get(key);
        if (cached == null) {
            ItemStack item = loadConfiguredItem(type, level);
            if (isBlankItem(item)) {
                item = generateDefaultItem(type, level);
            }
            if (isBlankItem(item)) {
                logInvalidConfiguredItem(type, level);
                return null;
            }
            tagItem(item, type, level);
            applySwordCustomModelData(item, type, level);
            applyNetheriteSwordAttributes(item, type);
            applySkyPvpArmorAttributes(item, type);
            hideTooltipDetails(item);
            clearUniqueUid(item);
            if (item != null && item.getType() == Material.PLAYER_HEAD) {
                String storedHead = NBTUtils.getString(item, "holly_head_value");
                if (storedHead != null && !storedHead.isBlank()) {
                    item = dev.grzybalson.utils.HeadUtils.setHeadTexture(item, storedHead);
                }
            }
            if (item != null && item.getType() == Material.SHEARS) {
                updateShearsIfOutdated(item);
            }
            configuredItemCache.put(key, item.clone());
            cached = item;
        }
        return cached.clone();
    }

    /** Highest real saved upgrade, without generated fallback levels or mixed enchant maxima. */
    public ItemStack getBestConfiguredItem(UpgradeType type, Material requiredMaterial) {
        String key = activeStyle.name() + ':' + type.name() + ':' + requiredMaterial;
        Optional<ItemStack> cached = bestConfiguredItemCache.computeIfAbsent(key, ignored -> {
            List<Integer> levels = getConfiguredLevels(type);
            for (int i = levels.size() - 1; i >= 0; i--) {
                int level = levels.get(i);
                ItemStack saved = loadConfiguredItem(type, level);
                if (isBlankItem(saved) || (requiredMaterial != null && saved.getType() != requiredMaterial)) continue;
                ItemStack item = getConfiguredItem(type, level);
                if (!isBlankItem(item)) return Optional.of(item);
            }
            return Optional.empty();
        });
        return cached.map(ItemStack::clone).orElse(null);
    }

    /** Preserve the upgrade's own stat lines; render missing lines from its configured format. */
    public List<String> getEnchantmentLore(ItemStack item, UpgradeType type) {
        if (isBlankItem(item)) return List.of();
        List<String> stats = new ArrayList<>();
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasLore()) {
            for (String line : meta.getLore()) {
                String plain = ChatColor.stripColor(color(line)).toLowerCase(Locale.ROOT);
                if (plain.matches(".*(?:ochron|protection|wydajno|efficien|fortuna|szczęści|szczesci|fortune|niezniszczal|unbreaking|ostroś|ostrosc|sharpness|zaklęty ogień|zaklety ogien).*")) {
                    stats.add(line);
                }
            }
        }
        if (!stats.isEmpty()) return List.copyOf(stats);
        Map<String, String> values = Map.of(
            "{PROTECTION}", String.valueOf(item.getEnchantmentLevel(Enchantment.PROTECTION_ENVIRONMENTAL)),
            "{EFFICIENCY}", String.valueOf(item.getEnchantmentLevel(Enchantment.DIG_SPEED)),
            "{FORTUNE}", String.valueOf(item.getEnchantmentLevel(Enchantment.LOOT_BONUS_BLOCKS)),
            "{UNBREAKING}", String.valueOf(item.getEnchantmentLevel(Enchantment.DURABILITY)));
        String section = type == UpgradeType.HELMET ? "armor-create.armor-lore" : "armor-create.tool-lore";
        for (String line : config.getStringList(section)) {
            if (!line.isBlank()) stats.add(color(applyPlaceholders(line, values)));
        }
        return List.copyOf(stats);
    }

    private ItemStack generateDefaultItem(UpgradeType type, int level) {
        Material mat = getGeneratedMaterial(type);
        if (mat == null || mat.isAir()) {
            mat = type.getDefaultMaterial();
        }
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color("&e" + getDisplayPrefix(type) + " ✿" + level));
            item.setItemMeta(meta);
        }
        applyGeneratedEnchantments(item, type, level);
        applySwordCustomModelData(item, type, level);
        return item;
    }

    private ItemStack loadConfiguredItem(UpgradeType type, int level) {
        return loadConfiguredItem(type, level, activeStyle);
    }

    private ItemStack loadConfiguredItem(UpgradeType type, int level, UpgradeStyle style) {
        for (String path : configuredItemDataPaths(type, level, style)) {
            Object value = config.get(path);
            if (value instanceof ItemStack itemStack) {
                return isBlankItem(itemStack) ? null : itemStack.clone();
            }
            if (value instanceof String data) {
                if (data.isBlank()) {
                    continue;
                }
                ItemStack item = ItemSerializer.deserializeItem(data);
                if (!isBlankItem(item)) {
                    return item;
                }
            }

            ItemStack item = config.getItemStack(path);
            if (!isBlankItem(item)) {
                return item.clone();
            }
        }
        return null;
    }

    /** Item niewidoczny / pusty — nie powinien zajmować slotu w GUI poziomów. */
    public static boolean isBlankItem(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0;
    }

    private boolean hasConfiguredItemEntry(UpgradeType type, int level) {
        return hasConfiguredItemEntry(type, level, activeStyle);
    }

    private boolean hasConfiguredItemEntry(UpgradeType type, int level, UpgradeStyle style) {
        // Tylko realnie odczytywalny item — pusty/AIR wpis nie zajmuje slotu w GUI.
        return loadConfiguredItem(type, level, style) != null;
    }

    private boolean hasConfiguredItemKey(UpgradeType type, int level, UpgradeStyle style) {
        for (String path : configuredItemDataPaths(type, level, style)) {
            if (config.contains(path)) {
                return true;
            }
        }
        return false;
    }

    private List<String> configuredItemDataPaths(UpgradeType type, int level) {
        return configuredItemDataPaths(type, level, activeStyle);
    }

    private List<String> configuredItemDataPaths(UpgradeType type, int level, UpgradeStyle style) {
        String base = itemPath(type, level, style);
        return List.of(
            base + ".item",
            base + ".serializedItem",
            base + ".serialized-item",
            base + ".itemStack",
            base + ".item-stack",
            base + ".itemStack.serializedItem",
            base + ".itemStack.serialized-item",
            base + ".item-stack.serializedItem",
            base + ".item-stack.serialized-item"
        );
    }

    private void logInvalidConfiguredItem(UpgradeType type, int level) {
        if (!hasConfiguredItemKey(type, level, activeStyle)) {
            return;
        }

        String path = itemPath(type, level) + ".item";
        if (invalidConfiguredItemsLogged.add(path)) {
            plugin.getLogger().warning("Nie udalo sie odczytac itemu z " + itemPath(type, level) + ". Sprawdz format item/serializedItem albo wygeneruj go ponownie komenda /ulepsz create lub /armorcreate create.");
        }
    }

    public Optional<ItemStack> getNextPreview(UpgradeType type, Player player) {
        UpgradeTarget target = findUpgradeTarget(player, type);
        int current = target != null ? getUpgradeLevel(target.get(player)) : 0;
        return getPreviewForLevel(type, current);
    }

    public Map<UpgradeType, ItemStack> getMainGuiPreviews(Player player) {
        Map<UpgradeType, Integer> currentLevels = getBestUpgradeLevels(player);
        Map<UpgradeType, ItemStack> previews = new EnumMap<>(UpgradeType.class);
        for (UpgradeType type : UpgradeType.values()) {
            if (!isCategoryEnabled(type)) {
                continue;
            }
            if (activeStyle.isSkyPvp() && type == UpgradeType.AXE) {
                continue;
            }
            if (getConfiguredLevels(type).isEmpty()) {
                continue;
            }
            getPreviewForLevel(type, currentLevels.getOrDefault(type, 0)).ifPresent(item -> previews.put(type, item));
        }
        return previews;
    }

    private Optional<ItemStack> getPreviewForLevel(UpgradeType type, int currentLevel) {
        int next = getNextLevel(type, currentLevel);
        if (next < 0) {
            List<Integer> levels = getConfiguredLevels(type);
            if (levels.isEmpty()) {
                return Optional.empty();
            }
            next = levels.get(levels.size() - 1);
        }
        return Optional.ofNullable(createGuiItem(type, next, true));
    }

    private Map<UpgradeType, Integer> getBestUpgradeLevels(Player player) {
        Map<UpgradeType, Integer> levels = new EnumMap<>(UpgradeType.class);
        PlayerInventory inventory = player.getInventory();
        collectBestLevel(levels, inventory.getHelmet());
        collectBestLevel(levels, inventory.getChestplate());
        collectBestLevel(levels, inventory.getLeggings());
        collectBestLevel(levels, inventory.getBoots());
        collectBestLevel(levels, inventory.getItemInOffHand());
        for (ItemStack item : inventory.getStorageContents()) {
            collectBestLevel(levels, item);
        }
        return levels;
    }

    private void collectBestLevel(Map<UpgradeType, Integer> levels, ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return;
        }
        UpgradeType type = detectType(item);
        if (type == null || !matchesActiveStyle(item)) {
            return;
        }
        if (item.getType() == Material.SHEARS) {
            updateShearsIfOutdated(item);
        }
        int level = getUpgradeLevel(item);
        if (level <= 0) {
            return;
        }
        levels.merge(type, level, Math::max);
    }

    public ItemStack createGuiItem(UpgradeType type, int level, boolean mainGui) {
        String cacheKey = activeStyle.name() + ':' + type.name() + ':' + level + ':' + mainGui;
        ItemStack cached = guiItemCache.get(cacheKey);
        if (cached != null) {
            return cached.clone();
        }

        ItemStack item = buildGuiItem(type, level, mainGui);
        if (item != null) {
            guiItemCache.put(cacheKey, item.clone());
        }
        return item;
    }

    private ItemStack buildGuiItem(UpgradeType type, int level, boolean mainGui) {
        // GUI poziomów: tylko realny item z configu — bez generowania „pustych” placeholderów.
        ItemStack item = mainGui ? getConfiguredItem(type, level) : loadConfiguredItem(type, level);
        if (isBlankItem(item)) {
            if (!mainGui) {
                return null;
            }
            Material preview = getPreviewMaterial(type);
            if (preview == null || preview.isAir()) {
                preview = type.getDefaultMaterial();
            }
            item = new ItemStack(preview);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(color("&e" + getDisplayPrefix(type) + " ✿" + level));
                meta.setLore(List.of(color("&cBrak skonfigurowanego itemu.")));
                item.setItemMeta(meta);
            }
        } else {
            item = item.clone();
        }

        if (isBlankItem(item)) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> newLore = new ArrayList<>();
            if (config.getBoolean("gui.include-original-lore", true) && meta.hasLore() && meta.getLore() != null) {
                newLore.addAll(meta.getLore());
            }

            String displayName = meta.hasDisplayName() ? meta.getDisplayName() : getDisplayPrefix(type) + " ✿" + level;
            String lorePath = mainGui ? "gui.upgrade-item-lore" : "gui.level-item-lore";
            for (String line : config.getStringList(lorePath)) {
                if (!mainGui && isOldPreviewLevelLine(line)) {
                    continue;
                }
                newLore.add(color(applyCommonPlaceholders(line, type, level, getPrice(type, level), displayName)));
            }
            meta.setLore(newLore);
            item.setItemMeta(meta);
        }

        NBTUtils.setString(item, "upgrade_gui_type", type.name());
        NBTUtils.setInt(item, "upgrade_gui_level", level);
        if (item.getType() == Material.SHEARS) {
            updateShearsIfOutdated(item);
        }
        hideTooltipDetails(item);
        return item;
    }

    public ItemStack createBackItem() {
        Material material = materialOrDefault(config.getString("gui.back-item.material"), Material.BARRIER);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(config.getString("gui.back-item.name", "&cPowrót")));
            meta.setLore(colorList(config.getStringList("gui.back-item.lore")));
            item.setItemMeta(meta);
        }
        NBTUtils.setString(item, "upgrade_action", "back");
        return item;
    }

    public String getStyleSwitchedMessage(UpgradeStyle style) {
        return config.getString("messages.style-switched", "&#00FF3B✔ &8Ξ &#4EFA76Przełączono styl ulepszania na &e{STYLE}&a!")
            .replace("{STYLE}", style.getDisplayName());
    }

    public void createFromHand(Player player, int level, double basePrice) {
        createFromHand(player, level, basePrice, null);
    }

    public void createFromHand(Player player, int level, double basePrice, UpgradeType forcedType) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            player.sendMessage(ColorUtils.colorizeComponent(config.getString("messages.no-item")));
            return;
        }
        UpgradeType type = forcedType != null ? forcedType : detectType(hand);
        if (type == null || !isCategoryEnabled(type)) {
            player.sendMessage(ColorUtils.colorizeComponent(config.getString("messages.invalid-item")));
            return;
        }
        if (level < getMinimumLevel(type)) {
            player.sendMessage(ColorUtils.colorizeComponent(
                config.getString("messages.minimum-level", "")
                    .replace("{MIN_LEVEL}", String.valueOf(getMinimumLevel(type)))
            ));
            return;
        }

        ItemStack stored = hand.clone();
        stored.setAmount(1);
        tagItem(stored, type, level);
        clearUniqueUid(stored);

        double price;
        if (basePrice <= 0.0) {
            double pickaxePrice = getPrice(UpgradeType.PICKAXE, level);
            if (pickaxePrice > 0.0) {
                price = pickaxePrice;
            } else {
                price = 1000.0 * level * getPriceMultiplier(type);
            }
        } else {
            price = basePrice * getPriceMultiplier(type);
        }

        setConfiguredItem(type, level, price, stored);
        player.sendMessage(ColorUtils.colorizeComponent(
            applyCommonPlaceholders(config.getString("messages.created"), type, level, price, getItemName(stored))
        ));
    }

    public int createGeneratedLevel(int level, double basePrice, String startHex, String endHex, String armorName) {
        return createGeneratedLevel(level, basePrice, startHex, endHex, null, null, armorName);
    }

    public int createGeneratedLevel(int level, double basePrice, String startHex, String endHex,
                                    String headValue, String armorName) {
        return createGeneratedLevel(level, basePrice, startHex, endHex, null, headValue, armorName);
    }

    public int createGeneratedLevel(int level, double basePrice, String startHex, String endHex,
                                    String hexArmor, String headValue, String armorName) {
        int created = 0;
        for (UpgradeType type : UpgradeType.values()) {
            if (!isCategoryEnabled(type)) {
                continue;
            }
            if (level < getMinimumLevel(type)) {
                continue;
            }
            if (activeStyle.isSkyPvp() && type == UpgradeType.AXE) {
                continue;
            }
            ItemStack item = createGeneratedItem(type, level, startHex, endHex, hexArmor, headValue, armorName);
            setConfiguredItem(type, level, basePrice * getPriceMultiplier(type), item);
            created++;
        }
        return created;
    }

    public List<ItemStack> getItemsForLevel(int level, UpgradeType requestedType) {
        List<ItemStack> items = new ArrayList<>();
        for (UpgradeType type : UpgradeType.values()) {
            if (!isCategoryEnabled(type)) {
                continue;
            }
            if (requestedType != null && requestedType != type) {
                continue;
            }
            ItemStack item = getConfiguredItem(type, level);
            if (item != null) {
                items.add(item);
            }
        }
        if ((requestedType == null || requestedType == UpgradeType.SWORD) && isCategoryEnabled(UpgradeType.SWORD)) {
            items.add(createExcaliburItem(level));
        }
        return items;
    }

    /** Losowy zestaw zbroi (4 sloty) + miecz z puli /ulepsz — dla botów spoofera. */
    public BotLoadout generateRandomBotLoadout() {
        java.util.concurrent.ThreadLocalRandom random = java.util.concurrent.ThreadLocalRandom.current();
        return new BotLoadout(
            randomConfiguredItem(UpgradeType.HELMET, random),
            randomConfiguredItem(UpgradeType.CHEST_PLATE, random),
            randomConfiguredItem(UpgradeType.LEGGINGS, random),
            randomConfiguredItem(UpgradeType.BOOTS, random),
            randomConfiguredItem(UpgradeType.SWORD, random)
        );
    }

    private ItemStack randomConfiguredItem(UpgradeType type, java.util.concurrent.ThreadLocalRandom random) {
        if (!isCategoryEnabled(type)) {
            return null;
        }
        List<Integer> levels = getConfiguredLevels(type);
        if (levels.isEmpty()) {
            return getConfiguredItem(type, Math.max(1, getMinimumLevel(type)));
        }
        int level = levels.get(random.nextInt(levels.size()));
        return getConfiguredItem(type, level);
    }

    public List<String> getDebugReport(UpgradeType requestedType, Integer requestedLevel) {
        List<String> lines = new ArrayList<>();
        lines.add("&8[&eUlepsz debug&8] &7Config: &f" + configFile.getPath());

        for (UpgradeType type : UpgradeType.values()) {
            if (requestedType != null && requestedType != type) {
                continue;
            }

            ConfigurationSection section = config.getConfigurationSection(itemBasePath(type));
            if (section == null) {
                lines.add("&8- &f" + type.name() + "&7: &cbrak sekcji items." + type.getConfigKey());
                continue;
            }

            List<Integer> levels = new ArrayList<>();
            for (String key : section.getKeys(false)) {
                try {
                    int level = Integer.parseInt(key);
                    if (requestedLevel == null || requestedLevel == level) {
                        levels.add(level);
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            levels.sort(Comparator.naturalOrder());

            if (levels.isEmpty()) {
                lines.add("&8- &f" + type.name() + "&7: &cbrak poziomow");
                continue;
            }

            for (int level : levels) {
                boolean hasKey = hasConfiguredItemKey(type, level, activeStyle);
                ItemStack item = loadConfiguredItem(type, level);
                if (item == null) {
                    lines.add("&8- &f" + type.name() + " " + level + "&7: " + (hasKey ? "&cNIE ODCZYTANO itemu" : "&cbrak item/serializedItem"));
                    continue;
                }

                lines.add("&8- &f" + type.name() + " " + level + "&7: &aOK &8(" + item.getType().name() + ", cena " + formatPrice(getPrice(type, level)) + ")");
            }
        }

        return lines;
    }

    private UpgradeTarget findUpgradeTarget(Player player, UpgradeType expectedType) {
        // Najpierw główna ręka — żeby /ulepsz ulepszał trzymany miecz/item.
        UpgradeTarget mainHand = UpgradeTarget.storage(player.getInventory().getHeldItemSlot());
        ItemStack held = mainHand.get(player);
        if (held != null && held.getType() == Material.SHEARS) {
            updateShearsIfOutdated(held);
        }
        if (held != null && !held.getType().isAir()
            && detectType(held) == expectedType
            && getUpgradeLevel(held) > 0
            && matchesActiveStyle(held)) {
            return mainHand;
        }

        List<UpgradeTarget> targets = findUpgradeTargets(player, expectedType);
        UpgradeTarget bestUpgradeable = null;
        UpgradeTarget bestAny = null;

        for (UpgradeTarget target : targets) {
            ItemStack item = target.get(player);
            int level = getUpgradeLevel(item);

            if (bestAny == null || level > getUpgradeLevel(bestAny.get(player))) {
                bestAny = target;
            }

            if (getNextLevel(expectedType, level) >= 0
                && (bestUpgradeable == null || level > getUpgradeLevel(bestUpgradeable.get(player)))) {
                bestUpgradeable = target;
            }
        }

        return bestUpgradeable != null ? bestUpgradeable : bestAny;
    }

    private List<UpgradeTarget> findUpgradeTargets(Player player, UpgradeType expectedType) {
        PlayerInventory inventory = player.getInventory();
        List<UpgradeTarget> targets = new ArrayList<>();

        addIfMatches(targets, UpgradeTarget.armor("helmet"), player, expectedType);
        addIfMatches(targets, UpgradeTarget.armor("chestplate"), player, expectedType);
        addIfMatches(targets, UpgradeTarget.armor("leggings"), player, expectedType);
        addIfMatches(targets, UpgradeTarget.armor("boots"), player, expectedType);
        addIfMatches(targets, UpgradeTarget.offhand(), player, expectedType);

        ItemStack[] storage = inventory.getStorageContents();
        for (int slot = 0; slot < storage.length; slot++) {
            addIfMatches(targets, UpgradeTarget.storage(slot), player, expectedType);
        }

        return targets;
    }

    private void addIfMatches(List<UpgradeTarget> targets, UpgradeTarget target, Player player, UpgradeType expectedType) {
        ItemStack item = target.get(player);
        if (item == null || item.getType().isAir()) {
            return;
        }
        if (item.getType() == Material.SHEARS) {
            updateShearsIfOutdated(item);
        }
        if (detectType(item) == expectedType && getUpgradeLevel(item) > 0 && matchesActiveStyle(item)) {
            targets.add(target);
        }
    }

    public void upgradeHeldItem(Player player, UpgradeType expectedType) {
        if (!isCategoryEnabled(expectedType)) {
            player.sendMessage(ColorUtils.colorizeComponent(config.getString("messages.invalid-item")));
            return;
        }
        if (expectedType == UpgradeType.SHEARS) {
            updatePlayerShears(player);
        }
        if (!tryStartUpgrade(player)) {
            player.sendMessage(ColorUtils.colorizeComponent(config.getString("messages.cooldown")));
            return;
        }

        UpgradeTarget target = findUpgradeTarget(player, expectedType);
        if (target == null) {
            buyFirstLevel(player, expectedType);
            return;
        }

        int currentLevel = getUpgradeLevel(target.get(player));
        int nextLevel = getNextLevel(expectedType, currentLevel);
        if (nextLevel < 0) {
            player.sendMessage(ColorUtils.colorizeComponent(config.getString("messages.max-level")));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            finishUpgrade(player);
            return;
        }

        ItemStack nextItem = getConfiguredItem(expectedType, nextLevel);
        if (nextItem == null) {
            player.sendMessage(ColorUtils.colorizeComponent(config.getString("messages.no-next-level")));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            finishUpgrade(player);
            return;
        }

        double price = getPrice(expectedType, nextLevel);
        plugin.getEconomy().hasBalance(player.getUniqueId(), price).thenCompose(hasMoney -> {
            if (!hasMoney) {
                return java.util.concurrent.CompletableFuture.completedFuture(false);
            }
            return plugin.getEconomy().removeBalance(player.getUniqueId(), price);
        }).thenAccept(success -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!success) {
                player.sendMessage(ColorUtils.colorizeComponent(
                    applyCommonPlaceholders(config.getString("messages.not-enough-money"), expectedType, nextLevel, price, getItemName(nextItem))
                ));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                finishUpgrade(player);
                return;
            }

            ItemStack currentItem = target.get(player);
            if (detectType(currentItem) != expectedType || getNextLevel(expectedType, getUpgradeLevel(currentItem)) != nextLevel) {
                plugin.getEconomy().addBalance(player.getUniqueId(), price);
                player.sendMessage(ColorUtils.colorizeComponent(config.getString("messages.changed-item")));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                finishUpgrade(player);
                return;
            }

            String preservedUid = null;
            UniqueItemService uniqueItems = plugin.getUniqueItemService();
            if (uniqueItems != null) {
                preservedUid = uniqueItems.getUid(currentItem);
            }

            ItemStack itemToSet = isExcalibur(currentItem) ? createExcaliburItem(nextLevel) : nextItem.clone();
            clearUniqueUid(itemToSet);
            preserveItemMetaData(currentItem, itemToSet);
            if (preservedUid != null && uniqueItems != null) {
                uniqueItems.setUid(itemToSet, preservedUid);
            }
            if (itemToSet.getType() == Material.SHEARS) {
                updateShearsIfOutdated(itemToSet);
            }
            target.set(player, itemToSet);
            player.updateInventory();
            player.sendMessage(ColorUtils.colorizeComponent(
                applyCommonPlaceholders(config.getString("messages.upgraded"), expectedType, nextLevel, price, getItemName(itemToSet))
            ));
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof UpgradeGUI upgradeGUI) {
                upgradeGUI.refreshContents();
            }
            finishUpgrade(player);
        })).exceptionally(throwable -> {
            Bukkit.getScheduler().runTask(plugin, () -> finishUpgrade(player));
            plugin.getLogger().warning("Blad podczas ulepszania itemu: " + throwable.getMessage());
            return null;
        });
    }

    private void buyFirstLevel(Player player, UpgradeType type) {
        List<Integer> levels = getConfiguredLevels(type);
        if (levels.isEmpty()) {
            player.sendMessage(ColorUtils.colorizeComponent(config.getString("messages.no-next-level")));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            finishUpgrade(player);
            return;
        }

        int firstLevel = levels.get(0);
        ItemStack item = getConfiguredItem(type, firstLevel);
        if (item == null) {
            player.sendMessage(ColorUtils.colorizeComponent(config.getString("messages.no-next-level")));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            finishUpgrade(player);
            return;
        }

        int freeSlot = player.getInventory().firstEmpty();
        if (freeSlot < 0) {
            player.sendMessage(ColorUtils.colorizeComponent(config.getString("messages.no-free-slot")));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            finishUpgrade(player);
            return;
        }

        double price = getPrice(type, firstLevel);
        plugin.getEconomy().hasBalance(player.getUniqueId(), price).thenCompose(hasMoney -> {
            if (!hasMoney) {
                return java.util.concurrent.CompletableFuture.completedFuture(false);
            }
            return plugin.getEconomy().removeBalance(player.getUniqueId(), price);
        }).thenAccept(success -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!success) {
                player.sendMessage(ColorUtils.colorizeComponent(
                    applyCommonPlaceholders(config.getString("messages.not-enough-money"), type, firstLevel, price, getItemName(item))
                ));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                finishUpgrade(player);
                return;
            }

            if (player.getInventory().getItem(freeSlot) != null) {
                plugin.getEconomy().addBalance(player.getUniqueId(), price);
                player.sendMessage(ColorUtils.colorizeComponent(config.getString("messages.no-free-slot")));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                finishUpgrade(player);
                return;
            }

            player.getInventory().setItem(freeSlot, withoutUniqueUid(item.clone()));
            player.updateInventory();
            player.sendMessage(ColorUtils.colorizeComponent(
                applyCommonPlaceholders(config.getString("messages.bought"), type, firstLevel, price, getItemName(item))
            ));
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof UpgradeGUI upgradeGUI) {
                upgradeGUI.refreshContents();
            }
            finishUpgrade(player);
        })).exceptionally(throwable -> {
            Bukkit.getScheduler().runTask(plugin, () -> finishUpgrade(player));
            plugin.getLogger().warning("Blad podczas kupowania pierwszego poziomu ulepszenia: " + throwable.getMessage());
            return null;
        });
    }

    private boolean tryStartUpgrade(Player player) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        long cooldownMillis = getClickCooldownMillis();
        Long until = upgradeCooldowns.get(uuid);
        if (until != null && until > now) {
            return false;
        }
        if (!pendingUpgrades.add(uuid)) {
            return false;
        }
        upgradeCooldowns.put(uuid, now + cooldownMillis);
        return true;
    }

    private void finishUpgrade(Player player) {
        if (player != null) {
            pendingUpgrades.remove(player.getUniqueId());
        }
    }

    private void setConfiguredItem(UpgradeType type, int level, double price, ItemStack item) {
        tagItem(item, type, level);
        applySwordCustomModelData(item, type, level);
        applyNetheriteSwordAttributes(item, type);
        applySkyPvpArmorAttributes(item, type);
        hideTooltipDetails(item);
        clearUniqueUid(item);
        if (item != null && item.getType() == Material.PLAYER_HEAD) {
            String storedHead = NBTUtils.getString(item, "holly_head_value");
            if (storedHead != null && !storedHead.isBlank()) {
                item = dev.grzybalson.utils.HeadUtils.setHeadTexture(item, storedHead);
            }
        }
        config.set(itemPath(type, level) + ".price", price);
        config.set(itemPath(type, level) + ".item", ItemSerializer.serializeItem(item));
        configuredLevelsCache.remove(levelsCacheKey(type));
        configuredItemCache.remove(itemCacheKey(type, level));
        bestConfiguredItemCache.clear();
        guiItemCache.keySet().removeIf(key -> key.startsWith(activeStyle.name() + ":" + type.name() + ":")
            || key.startsWith(type.name() + ":"));
        saveAsync();
    }

    private void tagItem(ItemStack item, UpgradeType type, int level) {
        NBTUtils.setString(item, NBT_TYPE, type.name());
        NBTUtils.setInt(item, NBT_LEVEL, level);
        NBTUtils.setString(item, NBT_STYLE, activeStyle.name());
    }

    private ItemStack createGeneratedItem(UpgradeType type, int level, String startHex, String endHex, String armorName) {
        return createGeneratedItem(type, level, startHex, endHex, null, null, armorName);
    }

    private ItemStack createGeneratedItem(UpgradeType type, int level, String startHex, String endHex,
                                          String hexArmor, String headValue, String armorName) {
        ItemStack item;
        boolean customHead = type == UpgradeType.HELMET && hasHeadValue(headValue);

        if (activeStyle.isSkyPvp() && isArmorType(type)) {
            item = createSkyPvpArmorBase(type, hexArmor, headValue);
        } else if (customHead) {
            item = new ItemStack(Material.PLAYER_HEAD);
            NBTUtils.setString(item, "holly_head_value", headValue);
            item = dev.grzybalson.utils.HeadUtils.setHeadTexture(item, headValue);
        } else {
            item = new ItemStack(getGeneratedMaterial(type));
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(formatGeneratedDisplayName(type, armorName, level, startHex, endHex));
            meta.setLore(colorList(buildGeneratedLore(type, level, armorName)));
            if (config.getBoolean("armor-create.hide-all-flags", true) || activeStyle.isSkyPvp()) {
                meta.addItemFlags(ItemFlag.values());
            } else {
                if (config.getBoolean("armor-create.hide-enchants", true)) {
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                }
                if (config.getBoolean("armor-create.hide-attributes", true)) {
                    meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                }
            }
            item.setItemMeta(meta);
        }

        applyGeneratedEnchantments(item, type, level);
        tagItem(item, type, level);
        applySwordCustomModelData(item, type, level);
        applyNetheriteSwordAttributes(item, type);
        applySkyPvpArmorAttributes(item, type);
        hideTooltipDetails(item);

        if (type == UpgradeType.HELMET && hasHeadValue(headValue)) {
            NBTUtils.setString(item, "holly_head_value", headValue);
            item = dev.grzybalson.utils.HeadUtils.setHeadTexture(item, headValue);
        }
        return item;
    }

    /**
     * Miecze z ulepszania: jeśli item nie ma CMD, ustaw poziom jako model.
     * Nie nadpisuj istniejącego CustomModelData (skórki z /ulepsz create zostają).
     * Excalibur pomijamy (ma własny model z eventówek).
     */
    private void applySwordCustomModelData(ItemStack item, UpgradeType type, int level) {
        if (type != UpgradeType.SWORD || item == null || level < 1 || isExcalibur(item)) {
            return;
        }
        if (config != null && !config.getBoolean("apply-sword-custom-model-data", true)) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        if (meta.hasCustomModelData()) {
            return;
        }
        meta.setCustomModelData(level);
        item.setItemMeta(meta);
    }

    private ItemStack createSkyPvpArmorBase(UpgradeType type, String hexArmor, String headValue) {
        if (type == UpgradeType.HELMET) {
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            if (headValue != null && !headValue.isBlank()) {
                NBTUtils.setString(head, "holly_head_value", headValue);
                return dev.grzybalson.utils.HeadUtils.setHeadTexture(head, headValue);
            }
            return head;
        }

        Material leather = switch (type) {
            case CHEST_PLATE -> Material.LEATHER_CHESTPLATE;
            case LEGGINGS -> Material.LEATHER_LEGGINGS;
            case BOOTS -> Material.LEATHER_BOOTS;
            default -> Material.LEATHER_HELMET;
        };
        ItemStack item = new ItemStack(leather);
        applyLeatherColor(item, hexArmor);
        return item;
    }

    private void applyLeatherColor(ItemStack item, String hexArmor) {
        if (item == null || hexArmor == null || hexArmor.isBlank()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof org.bukkit.inventory.meta.LeatherArmorMeta leatherMeta)) {
            return;
        }
        org.bukkit.Color color = parseHexColor(hexArmor);
        if (color != null) {
            leatherMeta.setColor(color);
            item.setItemMeta(leatherMeta);
        }
    }

    private org.bukkit.Color parseHexColor(String raw) {
        if (raw == null) {
            return null;
        }
        String hex = raw.trim();
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        if (hex.length() != 6) {
            return null;
        }
        try {
            int value = Integer.parseInt(hex, 16);
            return org.bukkit.Color.fromRGB(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    public boolean hasHeadValue(String headValue) {
        if (headValue == null || headValue.isBlank()) {
            return false;
        }
        String trimmed = headValue.trim();
        return !trimmed.equalsIgnoreCase("none")
            && !trimmed.equalsIgnoreCase("brak")
            && !trimmed.equalsIgnoreCase("-")
            && !trimmed.equalsIgnoreCase("netherite")
            && !trimmed.equalsIgnoreCase("default")
            && !trimmed.equalsIgnoreCase("null");
    }

    private void applySkyPvpArmorAttributes(ItemStack item, UpgradeType type) {
        if (item == null || !isArmorType(type)) {
            return;
        }
        boolean isSkyPvpArmor = activeStyle.isSkyPvp();
        boolean isStandardCustomHead = !activeStyle.isSkyPvp() && type == UpgradeType.HELMET && item.getType() == Material.PLAYER_HEAD;
        if (!isSkyPvpArmor && !isStandardCustomHead) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        EquipmentSlot slot = switch (type) {
            case HELMET -> EquipmentSlot.HEAD;
            case CHEST_PLATE -> EquipmentSlot.CHEST;
            case LEGGINGS -> EquipmentSlot.LEGS;
            case BOOTS -> EquipmentSlot.FEET;
            default -> null;
        };
        if (slot == null) {
            return;
        }

        double armor = switch (type) {
            case HELMET, BOOTS -> 3.0D;
            case CHEST_PLATE -> 8.0D;
            case LEGGINGS -> 6.0D;
            default -> 0.0D;
        };
        double toughness = 3.0D;
        double knockbackResistance = 0.1D;

        meta.removeAttributeModifier(Attribute.GENERIC_ARMOR);
        meta.removeAttributeModifier(Attribute.GENERIC_ARMOR_TOUGHNESS);
        meta.removeAttributeModifier(Attribute.GENERIC_KNOCKBACK_RESISTANCE);

        String materialKey = item.getType().name();
        meta.addAttributeModifier(Attribute.GENERIC_ARMOR, new AttributeModifier(
            UUID.nameUUIDFromBytes((LEATHER_ARMOR_NAME + "_" + materialKey).getBytes(StandardCharsets.UTF_8)),
            LEATHER_ARMOR_NAME,
            armor,
            AttributeModifier.Operation.ADD_NUMBER,
            slot
        ));
        meta.addAttributeModifier(Attribute.GENERIC_ARMOR_TOUGHNESS, new AttributeModifier(
            UUID.nameUUIDFromBytes((LEATHER_TOUGHNESS_NAME + "_" + materialKey).getBytes(StandardCharsets.UTF_8)),
            LEATHER_TOUGHNESS_NAME,
            toughness,
            AttributeModifier.Operation.ADD_NUMBER,
            slot
        ));
        meta.addAttributeModifier(Attribute.GENERIC_KNOCKBACK_RESISTANCE, new AttributeModifier(
            UUID.nameUUIDFromBytes((LEATHER_KB_NAME + "_" + materialKey).getBytes(StandardCharsets.UTF_8)),
            LEATHER_KB_NAME,
            knockbackResistance,
            AttributeModifier.Operation.ADD_NUMBER,
            slot
        ));

        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
    }

    private void hideTooltipDetails(ItemStack item) {
        if (item == null) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        if (config.getBoolean("armor-create.hide-all-flags", true)) {
            meta.addItemFlags(ItemFlag.values());
        } else {
            if (config.getBoolean("armor-create.hide-enchants", true)) {
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            if (config.getBoolean("armor-create.hide-attributes", true)) {
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            }
        }
        item.setItemMeta(meta);
    }

    private void applyNetheriteSwordAttributes(ItemStack item, UpgradeType type) {
        if (type != UpgradeType.SWORD || item == null || !item.containsEnchantment(Enchantment.DAMAGE_ALL) || isExcalibur(item)) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        meta.removeAttributeModifier(Attribute.GENERIC_ATTACK_DAMAGE);
        meta.addAttributeModifier(Attribute.GENERIC_ATTACK_DAMAGE, new AttributeModifier(
            UUID.nameUUIDFromBytes(SHARP_DAMAGE_NAME.getBytes()),
            SHARP_DAMAGE_NAME,
            NETHERITE_SWORD_DAMAGE_MODIFIER,
            AttributeModifier.Operation.ADD_NUMBER,
            EquipmentSlot.HAND
        ));
        meta.removeAttributeModifier(Attribute.GENERIC_ATTACK_SPEED);
        meta.addAttributeModifier(Attribute.GENERIC_ATTACK_SPEED, new AttributeModifier(
            UUID.nameUUIDFromBytes(SHARP_SPEED_NAME.getBytes(StandardCharsets.UTF_8)),
            SHARP_SPEED_NAME,
            NETHERITE_SWORD_SPEED_MODIFIER,
            AttributeModifier.Operation.ADD_NUMBER,
            EquipmentSlot.HAND
        ));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
    }

    private List<String> buildGeneratedLore(UpgradeType type, int level, String armorName) {
        Map<String, String> placeholders = generatedStatPlaceholders(type, level, armorName);
        List<String> result = new ArrayList<>();
        for (String line : config.getStringList("armor-create.base-lore")) {
            result.add(applyPlaceholders(line, placeholders));
        }

        String section = switch (type) {
            case SWORD -> "armor-create.sword-lore";
            case PICKAXE, HOE, SHEARS, AXE -> "armor-create.tool-lore";
            default -> "armor-create.armor-lore";
        };

        for (String line : config.getStringList(section)) {
            String applied = applyPlaceholders(line, placeholders);
            if ("{FIRE_ASPECT_LINE}".equals(line) && placeholders.get("{FIRE_ASPECT}").equals("0")) {
                continue;
            }
            result.add(applied);
        }
        return result;
    }

    private Map<String, String> generatedStatPlaceholders(UpgradeType type, int level, String armorName) {
        Map<String, String> values = new LinkedHashMap<>();
        int protection = level * 2;
        int sharpness = level * 2 + 1;
        int swordUnbreaking = level * 2;
        int fireAspect = level >= 9 ? 2 : (level >= 8 ? 1 : 0);
        int pickaxeEfficiency = 10 + level * 2;
        int pickaxeFortune = pickaxeEfficiency;

        int relativeToolLevel = Math.max(1, level - getMinimumLevel(type) + 1);
        int smallToolEfficiency = Math.min(3, 1 + (relativeToolLevel - 1) / 3);
        int smallToolFortune = 1 + (relativeToolLevel - 1) / 2;

        int efficiency;
        int fortune;
        int toolUnbreaking;
        if (type == UpgradeType.PICKAXE || type == UpgradeType.HOE) {
            efficiency = pickaxeEfficiency;
            fortune = pickaxeFortune;
            toolUnbreaking = efficiency;
        } else if (type == UpgradeType.SHEARS) {
            efficiency = getShearsEfficiency(level);
            fortune = getShearsFortune(level);
            toolUnbreaking = getShearsUnbreaking(level);
        } else {
            efficiency = smallToolEfficiency;
            fortune = smallToolFortune;
            toolUnbreaking = fortune;
        }

        values.put("{LEVEL}", String.valueOf(level));
        values.put("{ARMOR_NAME}", armorName);
        values.put("{PROTECTION}", String.valueOf(protection));
        values.put("{SHARPNESS}", String.valueOf(sharpness));
        values.put("{FIRE_ASPECT}", String.valueOf(fireAspect));
        values.put("{EFFICIENCY}", String.valueOf(efficiency));
        values.put("{FORTUNE}", String.valueOf(fortune));
        values.put("{UNBREAKING}", String.valueOf(type == UpgradeType.SWORD ? swordUnbreaking : (type == UpgradeType.PICKAXE || type == UpgradeType.HOE || type == UpgradeType.SHEARS || type == UpgradeType.AXE) ? toolUnbreaking : protection));
        values.put("{FIRE_ASPECT_LINE}", fireAspect > 0
            ? config.getString("armor-create.fire-aspect-line", "").replace("{FIRE_ASPECT}", String.valueOf(fireAspect))
            : "");
        return values;
    }

    private void applyGeneratedEnchantments(ItemStack item, UpgradeType type, int level) {
        switch (type) {
            case SWORD -> {
                item.addUnsafeEnchantment(Enchantment.DAMAGE_ALL, level * 2 + 1);
                item.addUnsafeEnchantment(Enchantment.DURABILITY, level * 2);
                if (level >= 8) {
                    item.addUnsafeEnchantment(Enchantment.FIRE_ASPECT, level >= 9 ? 2 : 1);
                }
            }
            case PICKAXE, HOE -> {
                int efficiency = 10 + level * 2;
                item.addUnsafeEnchantment(Enchantment.DIG_SPEED, efficiency);
                item.addUnsafeEnchantment(Enchantment.DURABILITY, efficiency);
                item.addUnsafeEnchantment(Enchantment.LOOT_BONUS_BLOCKS, efficiency);
            }
            case SHEARS -> {
                boolean isShovel = (item != null && item.getType().name().contains("SHOVEL"))
                        || "SHOVEL".equalsIgnoreCase(getTypeConfigKey(type))
                        || "LOPATA".equalsIgnoreCase(getTypeConfigKey(type));
                if (isShovel) {
                    item.addUnsafeEnchantment(Enchantment.DIG_SPEED, level);
                    item.addUnsafeEnchantment(Enchantment.DURABILITY, level);
                } else {
                    int efficiency = getShearsEfficiency(level);
                    int fortune = getShearsFortune(level);
                    int unbreaking = getShearsUnbreaking(level);
                    item.addUnsafeEnchantment(Enchantment.DIG_SPEED, efficiency);
                    item.addUnsafeEnchantment(Enchantment.LOOT_BONUS_BLOCKS, fortune);
                    item.addUnsafeEnchantment(Enchantment.DURABILITY, unbreaking);
                }
            }
            case AXE -> {
                int relative = Math.max(1, level - getMinimumLevel(type) + 1);
                int efficiency = Math.min(3, 1 + (relative - 1) / 3);
                int fortune = 1 + (relative - 1) / 2;
                item.addUnsafeEnchantment(Enchantment.DIG_SPEED, efficiency);
                item.addUnsafeEnchantment(Enchantment.LOOT_BONUS_BLOCKS, fortune);
                item.addUnsafeEnchantment(Enchantment.DURABILITY, fortune);
            }
            default -> {
                int protection = level * 2;
                item.addUnsafeEnchantment(Enchantment.PROTECTION_ENVIRONMENTAL, protection);
                item.addUnsafeEnchantment(Enchantment.DURABILITY, protection);
            }
        }
    }

    private String applyCommonPlaceholders(String text, UpgradeType type, int level, double price, String itemName) {
        if (text == null) {
            return "";
        }
        return text
            .replace("{TYPE}", getDisplayPrefix(type))
            .replace("{LEVEL}", String.valueOf(level))
            .replace("{PRICE}", formatPrice(price))
            .replace("{NAME}", itemName == null ? "" : itemName);
    }

    private String applyPlaceholders(String text, Map<String, String> placeholders) {
        String result = text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }

    public String formatPrice(double price) {
        return MoneyAmountUtil.formatCompact(price);
    }

    private String getItemName(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return "";
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return meta.getDisplayName();
        }
        return item.getType().name();
    }

    private List<String> colorList(List<String> lines) {
        List<String> colored = new ArrayList<>();
        for (String line : lines) {
            colored.add(color(line));
        }
        return colored;
    }

    private String color(String text) {
        return ColorUtils.colorize(text);
    }

    private boolean isOldPreviewLevelLine(String line) {
        String stripped = ChatColor.stripColor(color(line)).toLowerCase(Locale.ROOT);
        return stripped.contains("podgl") && stripped.contains("poziom");
    }

    private String itemCacheKey(UpgradeType type, int level) {
        return activeStyle.name() + ":" + type.name() + ":" + level;
    }

    private String levelsCacheKey(UpgradeType type) {
        return activeStyle.name() + ":" + type.name();
    }

    private String itemBasePath(UpgradeType type) {
        return itemBasePath(type, activeStyle);
    }

    private String itemBasePath(UpgradeType type, UpgradeStyle style) {
        if (style == UpgradeStyle.SKYPVP) {
            return "styles.skypvp.items." + type.getConfigKey();
        }
        return "items." + type.getConfigKey();
    }

    private String itemPath(UpgradeType type, int level) {
        return itemPath(type, level, activeStyle);
    }

    private String itemPath(UpgradeType type, int level, UpgradeStyle style) {
        return itemBasePath(type, style) + "." + level;
    }

    public UpgradeStyle getItemStyle(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return UpgradeStyle.STANDARD;
        }
        String raw = NBTUtils.getString(item, NBT_STYLE);
        if (raw == null || raw.isBlank()) {
            return UpgradeStyle.STANDARD;
        }
        return UpgradeStyle.fromArgument(raw);
    }

    public boolean matchesActiveStyle(ItemStack item) {
        return getItemStyle(item) == activeStyle;
    }

    public boolean isExcalibur(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        return isExcalibur(item, item.getItemMeta());
    }

    public boolean isExcalibur(ItemStack item, ItemMeta meta) {
        if (item == null || item.getType().isAir() || meta == null) {
            return false;
        }
        boolean isTagged = EventItemRegistry.isTaggedAs(plugin, "nowyexcalibur", item) || EventItemRegistry.isTaggedAs(plugin, "excalibur", item);
        boolean hasExcaliburName = meta.hasDisplayName() && ChatColor.stripColor(meta.getDisplayName()).toLowerCase(Locale.ROOT).contains("excalibur");
        if (!hasExcaliburName && !isTagged) {
            return false;
        }
        return isTagged || EventItemRegistry.matches(plugin, "nowyexcalibur", item) || EventItemRegistry.matches(plugin, "excalibur", item);
    }

    public ItemStack createExcaliburItem(int level) {
        org.bukkit.configuration.file.FileConfiguration ggConfig = dev.grzybalson.anarchiagg.AnarchiaGGSettings.getConfig(plugin);
        ItemStack baseExcalibur = null;

        if (ggConfig.contains("eventowki.excalibur.full-item")) {
            baseExcalibur = dev.grzybalson.utils.ConfigItemStackReader.getItem(ggConfig, "eventowki.excalibur.full-item");
        }
        if (baseExcalibur == null) {
            baseExcalibur = EventItemRegistry.getTemplateFromGui(plugin, "excalibur");
        }
        if (baseExcalibur == null || baseExcalibur.getType().isAir()) {
            baseExcalibur = dev.grzybalson.anarchiagg.customitems.items.NowyExcalibur.createConfiguredItem(plugin, 0);
        } else {
            baseExcalibur = baseExcalibur.clone();
        }

        ItemMeta meta = baseExcalibur.getItemMeta();
        if (meta != null) {
            int excaliburDisplayLevel = level + 4;
            meta.setDisplayName(ColorUtils.colorize("&e&lExcalibur &6&l⭐" + excaliburDisplayLevel));

            // Excalibur level 1 = SWORD level 5, level 2 = SWORD level 6, etc.
            int mappedSwordLevel = excaliburDisplayLevel;
            ItemStack swordTemplate = getConfiguredItem(UpgradeType.SWORD, mappedSwordLevel);
            if (swordTemplate == null || !swordTemplate.hasItemMeta()) {
                swordTemplate = getConfiguredItem(UpgradeType.SWORD, level);
            }

            if (swordTemplate != null && swordTemplate.hasItemMeta()) {
                for (Map.Entry<Enchantment, Integer> entry : swordTemplate.getItemMeta().getEnchants().entrySet()) {
                    meta.addEnchant(entry.getKey(), entry.getValue(), true);
                }
            }

            int customModelData = ggConfig.getInt("eventowki.excalibur.full-item.custom-model-data", 5);
            meta.setCustomModelData(customModelData);

            meta.removeAttributeModifier(Attribute.GENERIC_ATTACK_SPEED);
            meta.addAttributeModifier(Attribute.GENERIC_ATTACK_SPEED, new AttributeModifier(
                UUID.nameUUIDFromBytes("ExcaliburAttackSpeed".getBytes(StandardCharsets.UTF_8)),
                "ExcaliburAttackSpeed",
                -2.5D,
                AttributeModifier.Operation.ADD_NUMBER,
                EquipmentSlot.HAND
            ));

            if (meta.hasLore()) {
                List<String> cleanLore = new ArrayList<>();
                for (String line : Objects.requireNonNull(meta.getLore())) {
                    String stripped = ChatColor.stripColor(line).toLowerCase(Locale.ROOT);
                    if (stripped.contains("sharpness") || stripped.contains("fire aspect") || stripped.contains("unbreaking")
                        || stripped.contains("ostrość") || stripped.contains("zaklęty ogień") || stripped.contains("niezniszczalność")) {
                        continue;
                    }
                    cleanLore.add(line);
                }
                meta.setLore(cleanLore);
            }

            meta.addItemFlags(ItemFlag.values());
            meta.setUnbreakable(true);
            baseExcalibur.setItemMeta(meta);
        }

        tagItem(baseExcalibur, UpgradeType.SWORD, level);
        EventItemRegistry.matches(plugin, "excalibur", baseExcalibur);
        return withoutUniqueUid(baseExcalibur);
    }

    private void clearUniqueUid(ItemStack item) {
        UniqueItemService unique = plugin.getUniqueItemService();
        if (unique != null) {
            unique.clearUid(item);
        }
    }

    private ItemStack withoutUniqueUid(ItemStack item) {
        clearUniqueUid(item);
        return item;
    }

    private String gradientSmallCaps(String text, String startHex, String endHex) {
        String smallCaps = toSmallCaps(text);
        int[] start = parseHex(startHex);
        int[] end = parseHex(endHex);
        int steps = Math.max(1, smallCaps.length() - 1);
        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < smallCaps.length(); i++) {
            double ratio = steps == 0 ? 0.0 : (double) i / steps;
            int red = (int) Math.round(start[0] + (end[0] - start[0]) * ratio);
            int green = (int) Math.round(start[1] + (end[1] - start[1]) * ratio);
            int blue = (int) Math.round(start[2] + (end[2] - start[2]) * ratio);
            builder.append(toLegacyHex(red, green, blue)).append("&l").append(smallCaps.charAt(i));
        }
        return builder.toString();
    }

    public String getArmorCreateEmoji() {
        if (config == null) {
            return "★";
        }
        String emoji = config.getString("armor-create.level-emoji");
        if (emoji == null || emoji.isBlank()) {
            emoji = config.getString("armor-create.emoji");
        }
        if (emoji == null || emoji.isBlank()) {
            emoji = config.getString("armor-create.level-icon");
        }
        return (emoji != null && !emoji.isBlank()) ? emoji : "★";
    }

    public String formatGeneratedDisplayName(UpgradeType type, String armorName, int level, String startHex, String endHex) {
        String itemName = getDisplayPrefix(type) + " " + armorName;
        String levelFormat = config != null ? config.getString("armor-create.level-format", " &6{EMOJI}{LEVEL}") : " &6{EMOJI}{LEVEL}";
        String emoji = getArmorCreateEmoji();
        String suffix = levelFormat
            .replace("{EMOJI}", emoji)
            .replace("{LEVEL}", String.valueOf(level));
        return color(gradientSmallCaps(itemName, startHex, endHex) + suffix);
    }

    private int[] parseHex(String value) {
        String normalized = value == null ? "" : value.replace("#", "").replace("&", "");
        if (normalized.length() != 6) {
            return new int[]{255, 255, 255};
        }
        try {
            return new int[]{
                Integer.parseInt(normalized.substring(0, 2), 16),
                Integer.parseInt(normalized.substring(2, 4), 16),
                Integer.parseInt(normalized.substring(4, 6), 16)
            };
        } catch (NumberFormatException exception) {
            return new int[]{255, 255, 255};
        }
    }

    private String toLegacyHex(int red, int green, int blue) {
        String hex = String.format(Locale.ROOT, "%02X%02X%02X", red, green, blue);
        StringBuilder builder = new StringBuilder("&x");
        for (char character : hex.toCharArray()) {
            builder.append('&').append(character);
        }
        return builder.toString();
    }

    private String toSmallCaps(String text) {
        Map<Character, Character> chars = smallCapsMap();
        StringBuilder result = new StringBuilder();
        for (char original : text.toLowerCase(Locale.ROOT).toCharArray()) {
            result.append(chars.getOrDefault(original, original));
        }
        return result.toString();
    }

    private Map<Character, Character> smallCapsMap() {
        Map<Character, Character> chars = new HashMap<>();
        chars.put('a', 'ᴀ');
        chars.put('ą', 'ᴀ');
        chars.put('b', 'ʙ');
        chars.put('c', 'ᴄ');
        chars.put('ć', 'ᴄ');
        chars.put('d', 'ᴅ');
        chars.put('e', 'ᴇ');
        chars.put('ę', 'ᴇ');
        chars.put('f', 'ꜰ');
        chars.put('g', 'ɢ');
        chars.put('h', 'ʜ');
        chars.put('i', 'ɪ');
        chars.put('j', 'ᴊ');
        chars.put('k', 'ᴋ');
        chars.put('l', 'ʟ');
        chars.put('ł', 'ʟ');
        chars.put('m', 'ᴍ');
        chars.put('n', 'ɴ');
        chars.put('ń', 'ɴ');
        chars.put('o', 'ᴏ');
        chars.put('ó', 'ᴏ');
        chars.put('p', 'ᴘ');
        chars.put('r', 'ʀ');
        chars.put('s', 'ꜱ');
        chars.put('ś', 'ꜱ');
        chars.put('t', 'ᴛ');
        chars.put('u', 'ᴜ');
        chars.put('w', 'ᴡ');
        chars.put('y', 'ʏ');
        chars.put('z', 'ᴢ');
        chars.put('ź', 'ᴢ');
        chars.put('ż', 'ᴢ');
        return chars;
    }

    private void preserveItemMetaData(ItemStack sourceItem, ItemStack targetItem) {
        if (sourceItem == null || targetItem == null) {
            return;
        }
        ItemMeta sourceMeta = sourceItem.getItemMeta();
        ItemMeta targetMeta = targetItem.getItemMeta();
        if (sourceMeta == null || targetMeta == null) {
            return;
        }

        PersistentDataContainer sourcePdc = sourceMeta.getPersistentDataContainer();
        PersistentDataContainer targetPdc = targetMeta.getPersistentDataContainer();
        for (NamespacedKey key : sourcePdc.getKeys()) {
            String keyName = key.getKey();
            // Poziom/typ/styl bierze następny szablon; UID przywracamy osobno po upgradzie.
            if (keyName.equals(NBT_LEVEL)
                || keyName.equals(NBT_TYPE)
                || keyName.equals(NBT_STYLE)
                || keyName.equals("uid")) {
                continue;
            }
            copyPdcKey(sourcePdc, targetPdc, key);
        }

        boolean isShovel = (sourceItem != null && sourceItem.getType().name().contains("SHOVEL"))
                || (targetItem != null && targetItem.getType().name().contains("SHOVEL"));
        if (isShovel && sourceItem != null) {
            int currentEff = sourceItem.getEnchantmentLevel(Enchantment.DIG_SPEED);
            int currentUnb = sourceItem.getEnchantmentLevel(Enchantment.DURABILITY);
            if (currentEff > 0) {
                targetItem.addUnsafeEnchantment(Enchantment.DIG_SPEED, currentEff + 1);
            }
            if (currentUnb > 0) {
                targetItem.addUnsafeEnchantment(Enchantment.DURABILITY, currentUnb + 1);
            }
        }

        for (Map.Entry<Enchantment, Integer> entry : sourceItem.getEnchantments().entrySet()) {
            Enchantment ench = entry.getKey();
            int sourceLevel = entry.getValue();
            int targetLevel = targetItem.getEnchantmentLevel(ench);
            if (targetItem != null && targetItem.getType() == Material.SHEARS) {
                if (ench.equals(Enchantment.DIG_SPEED) || ench.equals(Enchantment.LOOT_BONUS_BLOCKS) || ench.equals(Enchantment.DURABILITY)) {
                    continue;
                }
            }
            if (sourceLevel > targetLevel) {
                targetItem.addUnsafeEnchantment(ench, sourceLevel);
            }
        }

        targetMeta = targetItem.getItemMeta();
        if (targetMeta == null) return;

        List<String> sourceLore = sourceMeta.getLore();
        if (sourceLore != null && !sourceLore.isEmpty()) {
            List<String> targetLore = targetMeta.hasLore() && targetMeta.getLore() != null
                    ? new ArrayList<>(targetMeta.getLore())
                    : new ArrayList<>();

            for (String line : sourceLore) {
                if (line == null || line.isBlank()) continue;
                String stripped = ChatColor.stripColor(line).toLowerCase(Locale.ROOT);

                boolean isBookLine = isBookLoreLine(stripped);
                boolean isZmiankaLine = stripped.contains("dodatkowe obrażenia")
                    || stripped.contains("dodatkowe obrazenia")
                    || stripped.contains("dodatkowe obraze");
                boolean isKowalLine = stripped.contains("odporności") || stripped.contains("odpornosci")
                    || (stripped.contains("%") && (isZmiankaLine || stripped.contains("odporn")));

                if (isBookLine || isZmiankaLine || isKowalLine) {
                    boolean alreadyPresent = targetLore.stream()
                            .filter(Objects::nonNull)
                            .anyMatch(tl -> ChatColor.stripColor(tl).equalsIgnoreCase(ChatColor.stripColor(line)));
                    if (!alreadyPresent) {
                        targetLore.add(line);
                    }
                }
            }
            targetMeta.setLore(targetLore);
        }

        targetItem.setItemMeta(targetMeta);
        if (targetItem.getType() == Material.SHEARS) {
            updateShearsIfOutdated(targetItem);
        }
    }

    private void copyPdcKey(PersistentDataContainer source, PersistentDataContainer target, NamespacedKey key) {
        if (source.has(key, PersistentDataType.STRING)) {
            target.set(key, PersistentDataType.STRING, Objects.requireNonNull(source.get(key, PersistentDataType.STRING)));
        } else if (source.has(key, PersistentDataType.INTEGER)) {
            target.set(key, PersistentDataType.INTEGER, Objects.requireNonNull(source.get(key, PersistentDataType.INTEGER)));
        } else if (source.has(key, PersistentDataType.DOUBLE)) {
            target.set(key, PersistentDataType.DOUBLE, Objects.requireNonNull(source.get(key, PersistentDataType.DOUBLE)));
        } else if (source.has(key, PersistentDataType.BYTE)) {
            target.set(key, PersistentDataType.BYTE, Objects.requireNonNull(source.get(key, PersistentDataType.BYTE)));
        } else if (source.has(key, PersistentDataType.LONG)) {
            target.set(key, PersistentDataType.LONG, Objects.requireNonNull(source.get(key, PersistentDataType.LONG)));
        } else if (source.has(key, PersistentDataType.FLOAT)) {
            target.set(key, PersistentDataType.FLOAT, Objects.requireNonNull(source.get(key, PersistentDataType.FLOAT)));
        }
    }

    private boolean isBookLoreLine(String strippedLine) {
        if (strippedLine == null) return false;
        return strippedLine.contains("otrucie przeciwnika")
                || strippedLine.contains("niepodpalanie")
                || strippedLine.contains("regeneracji")
                || strippedLine.contains("pośpiech")
                || strippedLine.contains("pospiech")
                || strippedLine.contains("szybkości")
                || strippedLine.contains("szybkosci")
                || strippedLine.contains("spowolnienie")
                || strippedLine.contains("wytrzymałości")
                || strippedLine.contains("wytrzymalosci")
                || strippedLine.contains("wampiryzm")
                || strippedLine.contains("unik")
                || strippedLine.contains("obrażenia krytyczne")
                || strippedLine.contains("obrazenia krytyczne")
                || strippedLine.contains("ułaskawienia")
                || strippedLine.contains("ulaskawienia");
    }

    public boolean isUpgradedShears(ItemStack item) {
        if (item == null || item.getType() != Material.SHEARS) {
            return false;
        }
        return getUpgradeLevel(item) > 0;
    }

    public int getShearsEfficiency(int level) {
        return Math.min(3, 1 + (Math.max(1, level) - 1) / 3);
    }

    public int getShearsFortune(int level) {
        return Math.max(1, level);
    }

    public int getShearsUnbreaking(int level) {
        return Math.max(1, level);
    }

    private static final Pattern STAT_LORE_NUMBER_PATTERN = Pattern.compile(
        "(.*(?:wydajność|wydajnosc|szczęście|szczescie|niezniszczalność|niezniszczalnosc)\\D*)(\\d+)(.*)",
        Pattern.CASE_INSENSITIVE
    );

    private String updateStatLoreLine(String line, int newValue) {
        if (line == null) {
            return null;
        }
        Matcher matcher = STAT_LORE_NUMBER_PATTERN.matcher(line);
        if (matcher.matches()) {
            return matcher.group(1) + newValue + matcher.group(3);
        }
        return line;
    }

    public boolean updateShearsIfOutdated(ItemStack item) {
        if (!isUpgradedShears(item)) {
            return false;
        }
        int level = getUpgradeLevel(item);
        if (level <= 0) {
            return false;
        }

        int expectedEff = getShearsEfficiency(level);
        int expectedFort = getShearsFortune(level);
        int expectedUnb = getShearsUnbreaking(level);

        int curEff = item.getEnchantmentLevel(Enchantment.DIG_SPEED);
        int curFort = item.getEnchantmentLevel(Enchantment.LOOT_BONUS_BLOCKS);
        int curUnb = item.getEnchantmentLevel(Enchantment.DURABILITY);

        boolean enchantsNeedUpdate = (curEff != expectedEff || curFort != expectedFort || curUnb != expectedUnb);

        ItemMeta meta = item.getItemMeta();
        boolean loreUpdated = false;
        if (meta != null && meta.hasLore() && meta.getLore() != null) {
            List<String> lore = new ArrayList<>(meta.getLore());
            for (int i = 0; i < lore.size(); i++) {
                String line = lore.get(i);
                if (line == null) continue;
                String stripped = ChatColor.stripColor(line).toLowerCase(Locale.ROOT);
                if (stripped.contains("wydajno") || stripped.contains("wydajnos") || stripped.contains("wydajność")) {
                    String updated = updateStatLoreLine(line, expectedEff);
                    if (!line.equals(updated)) {
                        lore.set(i, updated);
                        loreUpdated = true;
                    }
                } else if (stripped.contains("szczę") || stripped.contains("szczes") || stripped.contains("szczęście")) {
                    String updated = updateStatLoreLine(line, expectedFort);
                    if (!line.equals(updated)) {
                        lore.set(i, updated);
                        loreUpdated = true;
                    }
                } else if (stripped.contains("niezniszczaln") || stripped.contains("niezniszczalno") || stripped.contains("niezniszczalność")) {
                    String updated = updateStatLoreLine(line, expectedUnb);
                    if (!line.equals(updated)) {
                        lore.set(i, updated);
                        loreUpdated = true;
                    }
                }
            }
            if (loreUpdated) {
                meta.setLore(lore);
            }
        }

        if (enchantsNeedUpdate || loreUpdated) {
            // Write enchants and lore to the same metadata instance. Writing the old
            // lore snapshot after addUnsafeEnchantment restored the previous enchants.
            if (meta == null) return false;
            meta.addEnchant(Enchantment.DIG_SPEED, expectedEff, true);
            meta.addEnchant(Enchantment.LOOT_BONUS_BLOCKS, expectedFort, true);
            meta.addEnchant(Enchantment.DURABILITY, expectedUnb, true);
            item.setItemMeta(meta);
            return true;
        }

        return false;
    }

    public void updatePlayerShears(Player player) {
        if (player == null) return;
        PlayerInventory inv = player.getInventory();
        boolean updated = false;
        if (updateShearsIfOutdated(inv.getItemInMainHand())) updated = true;
        if (updateShearsIfOutdated(inv.getItemInOffHand())) updated = true;
        for (ItemStack item : inv.getStorageContents()) {
            if (updateShearsIfOutdated(item)) updated = true;
        }
        if (updated) {
            player.updateInventory();
        }
    }

    private static final class UpgradeTarget {
        private final String type;
        private final int slot;

        private UpgradeTarget(String type, int slot) {
            this.type = type;
            this.slot = slot;
        }

        private static UpgradeTarget storage(int slot) {
            return new UpgradeTarget("storage", slot);
        }

        private static UpgradeTarget offhand() {
            return new UpgradeTarget("offhand", -1);
        }

        private static UpgradeTarget armor(String armorType) {
            return new UpgradeTarget(armorType, -1);
        }

        private ItemStack get(Player player) {
            PlayerInventory inventory = player.getInventory();
            return switch (type) {
                case "storage" -> inventory.getItem(slot);
                case "offhand" -> inventory.getItemInOffHand();
                case "helmet" -> inventory.getHelmet();
                case "chestplate" -> inventory.getChestplate();
                case "leggings" -> inventory.getLeggings();
                case "boots" -> inventory.getBoots();
                default -> null;
            };
        }

        private void set(Player player, ItemStack item) {
            PlayerInventory inventory = player.getInventory();
            switch (type) {
                case "storage" -> inventory.setItem(slot, item);
                case "offhand" -> inventory.setItemInOffHand(item);
                case "helmet" -> inventory.setHelmet(item);
                case "chestplate" -> inventory.setChestplate(item);
                case "leggings" -> inventory.setLeggings(item);
                case "boots" -> inventory.setBoots(item);
            }
        }
    }
}
