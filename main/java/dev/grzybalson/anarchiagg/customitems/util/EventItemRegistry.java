package dev.grzybalson.anarchiagg.customitems.util;

import dev.grzybalson.anarchiagg.AnarchiaGGSettings;
import dev.grzybalson.utils.ConfigItemStackReader;
import dev.grzybalson.utils.ColorUtils;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class EventItemRegistry {
    private static final String GUI_ITEMS_PATH = "gui.items.";
    private static final String FULL_ITEM_PATH = "eventowki.%s.full-item";
    private static final String ITEM_ID_TAG = "event-item-id";
    private static final Set<String> VANILLA_STACKING_ITEMS = Set.of("supernieskonczonamarchewka");

    private static final Map<String, Integer> ITEM_SLOTS = createItemSlots();
    private static final Map<Integer, String> SLOT_TO_ITEM = createSlotMap();
    private static final Map<String, ItemStack> GUI_TEMPLATE_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, ItemStack> CONFIG_TEMPLATE_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, ItemStack> GUI_COMPARISON_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, ItemStack> CONFIG_COMPARISON_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Material> EXPECTED_MATERIAL_CACHE = new ConcurrentHashMap<>();
    private static final Map<Material, List<String>> ITEM_KEYS_BY_MATERIAL_CACHE = new ConcurrentHashMap<>();
    private static volatile FileConfiguration cachedGuiConfig;
    private static volatile long cachedGuiConfigLastModified = -1L;
    private static volatile long cachedGuiConfigLastCheckTime = 0L;
    private static final long GUI_CONFIG_CHECK_INTERVAL_MS = 5000L;
    private static volatile NamespacedKey cachedItemIdKey;

    private EventItemRegistry() {
    }

    /**
     * Fast path: checks only our PDC tag, without any expensive ItemMeta comparisons.
     * Useful for hot paths (e.g. repeating tasks).
     */
    public static int getDefaultCustomModelData(String itemKey) {
        if (itemKey == null) {
            return 0;
        }
        return ITEM_SLOTS.getOrDefault(itemKey.toLowerCase(Locale.ROOT), 0);
    }

    public static boolean isTaggedAs(JavaPlugin plugin, String itemKey, ItemStack item) {
        if (itemKey == null || item == null || item.getType() == Material.AIR) {
            return false;
        }
        String taggedId = getTaggedItemId(plugin, item);
        return itemKey.equals(taggedId);
    }

    public static boolean matches(JavaPlugin plugin, String itemKey, ItemStack candidate) {
        if (candidate == null || candidate.getType() == Material.AIR) {
            return false;
        }

        if (usesVanillaStacking(itemKey)) {
            return normalizeStackableItem(plugin, itemKey, candidate);
        }

        Material expectedMaterial = getExpectedMaterial(plugin, itemKey);
        boolean kokosAppleAlias = isKokosAppleAlias(itemKey, expectedMaterial, candidate.getType());
        if (expectedMaterial != null && candidate.getType() != expectedMaterial && !kokosAppleAlias) {
            return false;
        }

        String taggedId = getTaggedItemId(plugin, candidate);
        if (itemKey.equals(taggedId)) {
            return true;
        }

        ItemStack strippedCandidate = stripPluginTag(plugin, candidate);
        if (kokosAppleAlias && expectedMaterial != null && strippedCandidate.getType() != expectedMaterial) {
            strippedCandidate = strippedCandidate.clone();
            strippedCandidate.setType(expectedMaterial);
        }

        ItemStack template = getGuiComparisonTemplate(plugin, itemKey);
        if (template != null && template.isSimilar(strippedCandidate)) {
            tagItem(plugin, candidate, itemKey);
            return true;
        }

        ItemStack configTemplate = getConfigComparisonTemplate(plugin, itemKey);
        if (configTemplate != null && configTemplate.isSimilar(strippedCandidate)) {
            tagItem(plugin, candidate, itemKey);
            return true;
        }

        return false;
    }

    public static boolean normalizeStackableItem(JavaPlugin plugin, String itemKey, ItemStack candidate) {
        if (!usesVanillaStacking(itemKey) || candidate == null || candidate.getType() == Material.AIR) {
            return false;
        }

        Material expectedMaterial = getExpectedMaterial(plugin, itemKey);
        if (expectedMaterial != null && candidate.getType() != expectedMaterial) {
            return false;
        }

        String taggedId = getTaggedItemId(plugin, candidate);
        if (itemKey.equals(taggedId)) {
            stripPluginTagInPlace(plugin, candidate);
            return true;
        }

        ItemStack strippedCandidate = stripPluginTag(plugin, candidate);

        ItemStack template = getGuiComparisonTemplate(plugin, itemKey);
        if (template != null && template.isSimilar(strippedCandidate)) {
            stripPluginTagInPlace(plugin, candidate);
            return true;
        }

        ItemStack configTemplate = getConfigComparisonTemplate(plugin, itemKey);
        if (configTemplate != null && configTemplate.isSimilar(strippedCandidate)) {
            stripPluginTagInPlace(plugin, candidate);
            return true;
        }

        return false;
    }

    public static String resolveItemId(JavaPlugin plugin, ItemStack candidate) {
        if (candidate == null || candidate.getType() == Material.AIR) {
            return null;
        }

        String taggedId = getTaggedItemId(plugin, candidate);
        if (taggedId != null && ITEM_SLOTS.containsKey(taggedId)) {
            return taggedId;
        }

        for (String itemKey : getCandidateItemKeys(plugin, candidate.getType())) {
            if (matches(plugin, itemKey, candidate)) {
                return itemKey;
            }
        }

        return null;
    }


    private static List<String> getCandidateItemKeys(JavaPlugin plugin, Material material) {
        return ITEM_KEYS_BY_MATERIAL_CACHE.computeIfAbsent(material, ignored -> {
            List<String> candidates = new ArrayList<>();
            for (String itemKey : ITEM_SLOTS.keySet()) {
                Material expected = getExpectedMaterial(plugin, itemKey);
                if (expected == null || expected == material) {
                    candidates.add(itemKey);
                }
            }
            return List.copyOf(candidates);
        });
    }

    public static boolean isTrackedEventItem(JavaPlugin plugin, ItemStack candidate) {
        return resolveItemId(plugin, candidate) != null;
    }

    public static boolean hasKnownEventItemTag(JavaPlugin plugin, ItemStack candidate) {
        if (candidate == null || candidate.getType() == Material.AIR) {
            return false;
        }
        String taggedId = getTaggedItemId(plugin, candidate);
        return taggedId != null && ITEM_SLOTS.containsKey(taggedId);
    }

    public static boolean normalizeKnownEventItem(JavaPlugin plugin, ItemStack candidate) {
        if (candidate == null || candidate.getType() == Material.AIR) {
            return false;
        }

        String taggedId = getTaggedItemId(plugin, candidate);
        if (taggedId != null && ITEM_SLOTS.containsKey(taggedId)) {
            if (usesVanillaStacking(taggedId)) {
                stripPluginTagInPlace(plugin, candidate);
                return true;
            }
            return false;
        }

        // Legacy, untagged items are migrated only when their changed slot is processed.
        // The material fast-path inside matches() skips expensive metadata comparisons for
        // almost every registered item key.
        return resolveItemId(plugin, candidate) != null;
    }

    public static Set<String> getKnownItemKeys() {
        return Collections.unmodifiableSet(ITEM_SLOTS.keySet());
    }

    public static String getItemIdForSlot(int slot) {
        return SLOT_TO_ITEM.get(slot);
    }

    public static ItemStack prepareGuiItem(JavaPlugin plugin, int slot, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return item;
        }

        String itemKey = getItemIdForSlot(slot);
        if (itemKey == null) {
            return item;
        }

        ItemStack clone = item.clone();
        if (usesVanillaStacking(itemKey)) {
            stripPluginTagInPlace(plugin, clone);
        } else {
            tagItem(plugin, clone, itemKey);
        }
        syncItemToConfig(plugin, itemKey, clone);
        return clone;
    }

    public static void prepareInventory(JavaPlugin plugin, Inventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            inventory.setItem(slot, prepareGuiItem(plugin, slot, item));
        }
    }

    public static void syncAllKnownItemsToConfig(JavaPlugin plugin) {
        clearCaches();
        FileConfiguration guiConfig = loadGuiConfig(plugin);
        boolean updated = false;

        for (Map.Entry<String, Integer> entry : ITEM_SLOTS.entrySet()) {
            ItemStack item = ConfigItemStackReader.getItem(guiConfig, GUI_ITEMS_PATH + entry.getValue());
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }

            ItemStack tagged = item.clone();
            if (usesVanillaStacking(entry.getKey())) {
                stripPluginTagInPlace(plugin, tagged);
            } else {
                tagItem(plugin, tagged, entry.getKey());
            }
            syncItemToConfig(plugin, entry.getKey(), tagged);
            updated = true;
        }

        if (updated) {
            dev.grzybalson.anarchiagg.AnarchiaGGSettings.save(plugin, (YamlConfiguration) dev.grzybalson.anarchiagg.AnarchiaGGSettings.getConfig(plugin));
        }
    }

    public static ItemStack getTemplateFromGui(JavaPlugin plugin, String itemKey) {
        ItemStack cached = GUI_TEMPLATE_CACHE.get(itemKey);
        if (cached != null) {
            return cached.clone();
        }

        Integer slot = ITEM_SLOTS.get(itemKey);
        if (slot == null) {
            return null;
        }

        ItemStack item = ConfigItemStackReader.getItem(loadGuiConfig(plugin), GUI_ITEMS_PATH + slot);
        if (item == null || item.getType() == Material.AIR) {
            return null;
        }

        ItemStack clone = item.clone();
        if (usesVanillaStacking(itemKey)) {
            stripPluginTagInPlace(plugin, clone);
        } else {
            tagItem(plugin, clone, itemKey);
        }
        GUI_TEMPLATE_CACHE.put(itemKey, clone.clone());
        return clone;
    }

    private static ItemStack buildTemplateFromConfig(JavaPlugin plugin, String itemKey) {
        ItemStack cached = CONFIG_TEMPLATE_CACHE.get(itemKey);
        if (cached != null) {
            return cached.clone();
        }

        YamlConfiguration config = AnarchiaGGSettings.getConfig(plugin);
        String fullItemBase = String.format(FULL_ITEM_PATH, itemKey);
        String materialName = config.getString(fullItemBase + ".material");

        if (materialName == null || materialName.isBlank()) {
            materialName = config.getString("eventowki." + itemKey + ".material");
        }
        if (materialName == null || materialName.isBlank()) {
            materialName = config.getString("eventowki." + itemKey + ".item");
        }
        if (materialName == null || materialName.isBlank()) {
            return null;
        }

        Material material = Material.matchMaterial(materialName);
        if (material == null) {
            return null;
        }

        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }

        String displayName = config.getString(fullItemBase + ".name");
        if (displayName == null) {
            displayName = config.getString("eventowki." + itemKey + ".name");
        }
        if (displayName != null && !displayName.isBlank()) {
            meta.setDisplayName(colorize(displayName));
        }

        List<String> lore = config.getStringList(fullItemBase + ".lore");
        if (!lore.isEmpty()) {
            List<String> coloredLore = new ArrayList<>();
            for (String line : lore) {
                coloredLore.add(colorize(line));
            }
            meta.setLore(coloredLore);
        }

        if (config.isInt(fullItemBase + ".custom-model-data")) {
            meta.setCustomModelData(config.getInt(fullItemBase + ".custom-model-data"));
        }

        if (config.contains(fullItemBase + ".unbreakable")) {
            meta.setUnbreakable(config.getBoolean(fullItemBase + ".unbreakable"));
        }

        for (String rawFlag : config.getStringList(fullItemBase + ".item-flags")) {
            try {
                meta.addItemFlags(ItemFlag.valueOf(rawFlag.toUpperCase()));
            } catch (IllegalArgumentException ignored) {
            }
        }

        stack.setItemMeta(meta);

        ConfigurationSection enchantsSection = config.getConfigurationSection(fullItemBase + ".enchants");
        if (enchantsSection != null) {
            for (String key : enchantsSection.getKeys(false)) {
                Enchantment enchantment = Enchantment.getByName(key.toUpperCase());
                if (enchantment != null) {
                    stack.addUnsafeEnchantment(enchantment, enchantsSection.getInt(key));
                }
            }
        }

        if (usesVanillaStacking(itemKey)) {
            stripPluginTagInPlace(plugin, stack);
        } else {
            tagItem(plugin, stack, itemKey);
        }
        CONFIG_TEMPLATE_CACHE.put(itemKey, stack.clone());
        return stack;
    }


    private static ItemStack getGuiComparisonTemplate(JavaPlugin plugin, String itemKey) {
        ItemStack cached = GUI_COMPARISON_CACHE.get(itemKey);
        if (cached != null) {
            return cached;
        }
        ItemStack template = getTemplateFromGui(plugin, itemKey);
        if (template == null) {
            return null;
        }
        ItemStack stripped = stripPluginTag(plugin, template);
        GUI_COMPARISON_CACHE.put(itemKey, stripped);
        return stripped;
    }

    private static ItemStack getConfigComparisonTemplate(JavaPlugin plugin, String itemKey) {
        ItemStack cached = CONFIG_COMPARISON_CACHE.get(itemKey);
        if (cached != null) {
            return cached;
        }
        ItemStack template = buildTemplateFromConfig(plugin, itemKey);
        if (template == null) {
            return null;
        }
        ItemStack stripped = stripPluginTag(plugin, template);
        CONFIG_COMPARISON_CACHE.put(itemKey, stripped);
        return stripped;
    }

    private static void syncItemToConfig(JavaPlugin plugin, String itemKey, ItemStack item) {
        YamlConfiguration config = AnarchiaGGSettings.getConfig(plugin);
        String basePath = String.format(FULL_ITEM_PATH, itemKey);
        ItemMeta meta = item.getItemMeta();

        config.set(basePath + ".material", item.getType().name());
        config.set(basePath + ".name", meta != null && meta.hasDisplayName() ? toAmpersand(meta.getDisplayName()) : null);
        config.set(basePath + ".lore", meta != null && meta.hasLore() ? toAmpersandList(Objects.requireNonNull(meta.getLore())) : Collections.emptyList());
        config.set(basePath + ".custom-model-data", meta != null && meta.hasCustomModelData() ? meta.getCustomModelData() : null);
        config.set(basePath + ".unbreakable", meta != null && meta.isUnbreakable());
        config.set(basePath + ".item-flags", serializeFlags(meta));
        config.set(basePath + ".enchants", serializeEnchants(item));
        config.set(basePath + ".pdc-key", usesVanillaStacking(itemKey) ? null : itemKey);
    }

    private static Map<String, Integer> createItemSlots() {
        Map<String, Integer> map = new LinkedHashMap<>();
        map.put("ulepszonaelytra", 0);
        map.put("wedkasurfera", 1);
        map.put("nielota", 2);
        map.put("smoczymiecz", 3);
        map.put("sakwa", 4);
        map.put("watacukrowa", 5);
        map.put("hydrotrojzab", 6);
        map.put("hydroklatka", 7);
        map.put("boskitopor", 8);
        map.put("bombarda", 9);
        map.put("zlamaneserduszko", 10);
        map.put("totemulaskawienia", 11);
        map.put("korona", 12);
        map.put("balonik", 13);
        map.put("krewwampira", 14);
        map.put("kosa", 15);
        map.put("siekieragrincza", 16);
        map.put("blokwidmo", 17);
        map.put("turbotrap", 18);
        map.put("parawan", 19);
        map.put("excalibur", 20);
        map.put("kroliczymiecz", 21);
        map.put("marchewkowymiecz", 22);
        map.put("kostkarubika", 23);
        map.put("lizak", 24);
        map.put("rozakupidyna", 25);
        map.put("rozga", 26);
        map.put("cieplemleko", 27);
        map.put("zatrutyolowek", 28);
        map.put("jablko", 29);
        map.put("jajkoolafa", 30);
        map.put("rozdzkailuzjonisty", 31);
        map.put("lopatagrincza", 32);
        map.put("piernik", 33);
        map.put("sniezkateleportacji", 34);
        map.put("cudownalatarnia", 35);
        map.put("lukkupidyna", 36);
        map.put("marchewkowakusza", 37);
        map.put("dynamit", 38);
        map.put("rogjednorozca", 39);
        map.put("piekielnymiecz", 40);
        map.put("supernieskonczonamarchewka", 41);
        map.put("nowyexcalibur", 42);
        map.put("anarchiczeneserce", 43);
        map.put("usuwaczpajeczyn", 44);
        map.put("kokos", 45);
        map.put("bombakapitana", 46);
        map.put("linacecco", 47);
        map.put("usciskvadera", 48);
        map.put("retrospekcjaskywalkera", 49);
        map.put("sercewampira", 50);
        map.put("egzekutor", 51);
        map.put("maskazludzenia", 52);
        map.put("rdzenneutralizacji", 53);
        return map;
    }

    private static Map<Integer, String> createSlotMap() {
        Map<Integer, String> map = new HashMap<>();
        for (Map.Entry<String, Integer> entry : ITEM_SLOTS.entrySet()) {
            map.put(entry.getValue(), entry.getKey());
        }
        return map;
    }

    private static List<String> serializeFlags(ItemMeta meta) {
        if (meta == null || meta.getItemFlags().isEmpty()) {
            return Collections.emptyList();
        }

        List<String> flags = new ArrayList<>();
        for (ItemFlag itemFlag : meta.getItemFlags()) {
            flags.add(itemFlag.name());
        }
        return flags;
    }

    private static Map<String, Integer> serializeEnchants(ItemStack item) {
        if (item.getEnchantments().isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Integer> enchants = new LinkedHashMap<>();
        item.getEnchantments().forEach((enchantment, level) -> {
            String key = enchantment.getKey().getKey().toUpperCase().replace('-', '_');
            enchants.put(key, level);
        });
        return enchants;
    }

    private static String getTaggedItemId(JavaPlugin plugin, ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(getItemIdKey(plugin), PersistentDataType.STRING);
    }

    private static void tagItem(JavaPlugin plugin, ItemStack item, String itemKey) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(getItemIdKey(plugin), PersistentDataType.STRING, itemKey);
        item.setItemMeta(meta);
    }

    private static ItemStack stripPluginTag(JavaPlugin plugin, ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return item;
        }
        ItemMeta originalMeta = item.getItemMeta();
        if (originalMeta == null) {
            return item;
        }
        NamespacedKey itemIdKey = getItemIdKey(plugin);
        NamespacedKey uniqueUidKey = getUniqueUidKey(plugin);
        var pdc = originalMeta.getPersistentDataContainer();
        boolean hasItemId = pdc.has(itemIdKey, PersistentDataType.STRING);
        boolean hasUniqueUid = pdc.has(uniqueUidKey, PersistentDataType.STRING);
        if (!hasItemId && !hasUniqueUid) {
            return item;
        }

        ItemStack clone = item.clone();
        ItemMeta meta = clone.getItemMeta();
        if (meta != null) {
            if (hasItemId) {
                meta.getPersistentDataContainer().remove(itemIdKey);
            }
            if (hasUniqueUid) {
                meta.getPersistentDataContainer().remove(uniqueUidKey);
            }
            clone.setItemMeta(meta);
        }
        return clone;
    }

    /** UniqueItemService key — musi zejść z porównania isSimilar, inaczej /ulepsz nie łapie itemów z UUID. */
    private static NamespacedKey getUniqueUidKey(JavaPlugin plugin) {
        return new NamespacedKey(plugin, "uid");
    }

    private static boolean usesVanillaStacking(String itemKey) {
        return itemKey != null && VANILLA_STACKING_ITEMS.contains(itemKey);
    }

    private static void stripPluginTagInPlace(JavaPlugin plugin, ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.getPersistentDataContainer().has(getItemIdKey(plugin), PersistentDataType.STRING)) {
            return;
        }
        meta.getPersistentDataContainer().remove(getItemIdKey(plugin));
        item.setItemMeta(meta);
    }

    private static NamespacedKey getItemIdKey(JavaPlugin plugin) {
        if (cachedItemIdKey == null) {
            cachedItemIdKey = new NamespacedKey(plugin, ITEM_ID_TAG);
        }
        return cachedItemIdKey;
    }

    public static void clearCaches() {
        GUI_TEMPLATE_CACHE.clear();
        CONFIG_TEMPLATE_CACHE.clear();
        GUI_COMPARISON_CACHE.clear();
        CONFIG_COMPARISON_CACHE.clear();
        EXPECTED_MATERIAL_CACHE.clear();
        ITEM_KEYS_BY_MATERIAL_CACHE.clear();
        cachedGuiConfig = null;
        cachedGuiConfigLastModified = -1L;
        cachedGuiConfigLastCheckTime = 0L;
    }

    private static boolean isKokosAppleAlias(String itemKey, Material expected, Material actual) {
        if (!"kokos".equalsIgnoreCase(itemKey) || expected == null || actual == null) {
            return false;
        }
        return isGoldenAppleFamily(expected) && isGoldenAppleFamily(actual);
    }

    private static boolean isGoldenAppleFamily(Material material) {
        return material == Material.GOLDEN_APPLE || material == Material.ENCHANTED_GOLDEN_APPLE;
    }

    private static Material getExpectedMaterial(JavaPlugin plugin, String itemKey) {
        if (itemKey == null) {
            return null;
        }

        Material cached = EXPECTED_MATERIAL_CACHE.get(itemKey);
        if (cached != null) {
            return cached;
        }

        // Prefer "full-item" material if present, otherwise fall back to legacy keys.
        YamlConfiguration config = AnarchiaGGSettings.getConfig(plugin);
        String fullItemBase = String.format(FULL_ITEM_PATH, itemKey);
        String materialName = config.getString(fullItemBase + ".material");
        if (materialName == null || materialName.isBlank()) {
            materialName = config.getString("eventowki." + itemKey + ".material");
        }
        if (materialName == null || materialName.isBlank()) {
            materialName = config.getString("eventowki." + itemKey + ".item");
        }
        if (materialName == null || materialName.isBlank()) {
            return null;
        }

        Material material = Material.matchMaterial(materialName);
        if (material != null) {
            EXPECTED_MATERIAL_CACHE.put(itemKey, material);
        }
        return material;
    }

    private static FileConfiguration loadGuiConfig(JavaPlugin plugin) {
        long now = System.currentTimeMillis();
        FileConfiguration localCache = cachedGuiConfig;
        if (localCache != null && (now - cachedGuiConfigLastCheckTime) < GUI_CONFIG_CHECK_INTERVAL_MS) {
            return localCache;
        }
        cachedGuiConfigLastCheckTime = now;

        File guiFile = dev.grzybalson.anarchiagg.AnarchiaGGPaths.getFile(plugin, "customitems/guidata.yml");
        if (!guiFile.exists()) {
            dev.grzybalson.anarchiagg.AnarchiaGGPaths.ensureResource(plugin, "customitems/guidata.yml");
        }

        long lastModified = guiFile.lastModified();
        if (localCache != null && cachedGuiConfigLastModified == lastModified) {
            return localCache;
        }

        FileConfiguration loaded = YamlConfiguration.loadConfiguration(guiFile);
        GUI_TEMPLATE_CACHE.clear();
        GUI_COMPARISON_CACHE.clear();
        cachedGuiConfig = loaded;
        cachedGuiConfigLastModified = lastModified;
        return loaded;
    }

    private static String colorize(String text) {
        return ColorUtils.colorize(text);
    }

    private static List<String> toAmpersandList(List<String> lines) {
        List<String> result = new ArrayList<>(lines.size());
        for (String line : lines) {
            result.add(toAmpersand(line));
        }
        return result;
    }

    private static String toAmpersand(String text) {
        return text == null ? null : text.replace(ChatColor.COLOR_CHAR, '&');
    }
}
