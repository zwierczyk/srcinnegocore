package dev.grzybalson.anarchiagg.ksiegi;

import dev.grzybalson.anarchiagg.AnarchiaGGSettings;
import dev.grzybalson.utils.ConfigItemStackReader;
import dev.grzybalson.utils.ColorUtils;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class KsiegiManager {
    public enum TargetType {
        SWORD,
        PICKAXE,
        HELMET,
        CHESTPLATE,
        LEGGINGS,
        BOOTS,
        ARMOR,
        ANY
    }

    public enum BookType {
        OTRUCIE("otrucie", "Otrucie przeciwnika I", 1, TargetType.SWORD),
        NIEPODPALANIE("niepodpalanie", "Niepodpalanie I", 2, TargetType.ARMOR),
        REGENERACJI("regeneracji", "Regeneracji I", 4, TargetType.ARMOR),
        POSPIECH("pospiech", "Pośpiech I", 5, TargetType.PICKAXE),
        SZYBKOSCI("szybkosci", "Szybkości I", 6, TargetType.ARMOR),
        SPOWOLNIENIE("spowolnienie", "Spowolnienie I", 7, TargetType.SWORD),
        WYTRZYMALOSCI("wytrzymalosci", "Wytrzymałości I", 9, TargetType.ARMOR),
        WAMPIRYZM("wampiryzm", "Wampiryzm I", 10, TargetType.SWORD),
        UNIK("unik", "Unik I", 11, TargetType.ARMOR),
        KRYTYCZNE("krytyczne", "Obrażenia krytyczne I", 12, TargetType.SWORD),
        ULASKAWIENIA("ulaskawienia", "U\u0142askawienia I", 13, TargetType.ANY);

        private final String id;
        private final String loreLine;
        private final int customModelData;
        private final TargetType targetType;

        BookType(String id, String loreLine, int customModelData, TargetType targetType) {
            this.id = id;
            this.loreLine = loreLine;
            this.customModelData = customModelData;
            this.targetType = targetType;
        }

        public String id() {
            return id;
        }

        public String loreLine() {
            return loreLine;
        }

        public int customModelData() {
            return customModelData;
        }

        public TargetType targetType() {
            return targetType;
        }

        public static BookType byId(String raw) {
            if (raw == null) {
                return null;
            }
            String normalized = raw.toLowerCase(Locale.ROOT);
            for (BookType type : values()) {
                if (type.id.equals(normalized)) {
                    return type;
                }
            }
            return null;
        }
    }

    private final JavaPlugin plugin;
    private final File guiItemsFile;
    private final NamespacedKey bookIdKey;
    private final Map<BookType, NamespacedKey> appliedKeys = new EnumMap<>(BookType.class);
    private final Map<BookType, ItemStack> bookTemplates = new EnumMap<>(BookType.class);
    private final Map<Integer, BookType> cmdToType = new HashMap<>();
    private YamlConfiguration guiConfig;
    private String guiTitle;

    public KsiegiManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.guiItemsFile = dev.grzybalson.anarchiagg.AnarchiaGGPaths.getFile(plugin, "ksiegi/guiitems.yml");
        this.bookIdKey = new NamespacedKey(plugin, "ksiega-book-id");
        for (BookType type : BookType.values()) {
            this.appliedKeys.put(type, new NamespacedKey(plugin, "ksiega-applied-" + type.id()));
            this.cmdToType.put(type.customModelData(), type);
        }
        this.guiTitle = resolveGuiTitle();
    }

    public void ensureResource() {
        if (!guiItemsFile.exists()) {
            dev.grzybalson.anarchiagg.AnarchiaGGPaths.ensureResource(plugin, "ksiegi/guiitems.yml");
        }
    }

    public void reload() {
        ensureResource();
        this.guiConfig = YamlConfiguration.loadConfiguration(guiItemsFile);
        this.bookTemplates.clear();
        this.guiTitle = resolveGuiTitle();

        ConfigurationSection contents = getContentsSection();
        if (contents != null) {
            for (String key : contents.getKeys(false)) {
                ItemStack item = getConfiguredItem(key);
                if (item == null || item.getType() == Material.AIR) {
                    continue;
                }

                ItemMeta meta = item.getItemMeta();
                if (meta == null || !meta.hasCustomModelData()) {
                    continue;
                }

                BookType type = this.cmdToType.get(meta.getCustomModelData());
                if (type == null) {
                    continue;
                }

                ItemStack tagged = tagBookItem(item.clone(), type);
                this.bookTemplates.put(type, tagged);
            }
        }
        ensureDefaultBookTemplates();
    }

    public Inventory createGui() {
        Inventory inventory = Bukkit.createInventory(null, 18, guiTitle);
        if (this.guiConfig == null) {
            reload();
        }

        ConfigurationSection contents = getContentsSection();
        if (contents == null) {
            placeMissingBooks(inventory, Set.of());
            return inventory;
        }

        Set<BookType> placedTypes = new HashSet<>();
        for (String key : contents.getKeys(false)) {
            try {
                int slot = Integer.parseInt(key);
                ItemStack item = getConfiguredItem(key);
                if (item == null || item.getType() == Material.AIR) {
                    continue;
                }
                ItemMeta meta = item.getItemMeta();
                if (meta != null && meta.hasCustomModelData()) {
                    BookType type = this.cmdToType.get(meta.getCustomModelData());
                    if (type != null) {
                        inventory.setItem(slot, getBook(type));
                        placedTypes.add(type);
                        continue;
                    }
                }
                inventory.setItem(slot, item.clone());
            } catch (NumberFormatException ignored) {
            }
        }
        placeMissingBooks(inventory, placedTypes);
        return inventory;
    }



    private ItemStack getConfiguredItem(String key) {
        if (this.guiConfig == null) {
            return null;
        }

        String[] paths = {
                "inventory.contents." + key,
                "gui.items." + key,
                "gui.contents." + key,
                "items." + key
        };

        for (String path : paths) {
            ItemStack item = ConfigItemStackReader.getItem(this.guiConfig, path);
            if (item != null) {
                return item;
            }
        }
        return null;
    }

    private ConfigurationSection getContentsSection() {
        if (this.guiConfig == null) {
            return null;
        }

        ConfigurationSection contents = this.guiConfig.getConfigurationSection("inventory.contents");
        if (contents != null) {
            return contents;
        }

        contents = this.guiConfig.getConfigurationSection("gui.items");
        if (contents != null) {
            return contents;
        }

        contents = this.guiConfig.getConfigurationSection("gui.contents");
        if (contents != null) {
            return contents;
        }

        return this.guiConfig.getConfigurationSection("items");
    }

    public ItemStack getBook(BookType type) {
        ItemStack template = this.bookTemplates.get(type);
        if (template == null) {
            template = createDefaultBook(type);
            this.bookTemplates.put(type, template);
        }
        return template.clone();
    }

    private void ensureDefaultBookTemplates() {
        for (BookType type : BookType.values()) {
            this.bookTemplates.computeIfAbsent(type, this::createDefaultBook);
        }
    }

    private ItemStack createDefaultBook(BookType type) {
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.setDisplayName(color("&eZakleta ksiazka"));
        meta.setCustomModelData(type.customModelData());
        meta.setLore(java.util.List.of(
            color("&f" + type.loreLine()),
            "",
            color("&2&lZaklecie specjalne!"),
            color("&7Moze zostac nalozone na &a" + targetLabel(type.targetType()) + "&8!"),
            color(type == BookType.ULASKAWIENIA
                ? "&7Po smierci chroniony przedmiot wraca do ekwipunku."
                : "&7Nadaje przedmiotowi specjalny efekt.")
        ));
        item.setItemMeta(meta);
        return tagBookItem(item, type);
    }

    private String targetLabel(TargetType type) {
        return switch (type) {
            case SWORD -> "miecz";
            case PICKAXE -> "kilof";
            case HELMET -> "helm";
            case CHESTPLATE -> "napiersnik";
            case LEGGINGS -> "spodnie";
            case BOOTS -> "buty";
            case ARMOR -> "zbroje";
            case ANY -> "dowolny przedmiot";
        };
    }

    private void placeMissingBooks(Inventory inventory, Set<BookType> placedTypes) {
        for (BookType type : BookType.values()) {
            if (placedTypes.contains(type)) {
                continue;
            }
            int slot = firstEmptySlot(inventory);
            if (slot == -1) {
                return;
            }
            inventory.setItem(slot, getBook(type));
        }
    }

    private int firstEmptySlot(Inventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType() == Material.AIR) {
                return slot;
            }
        }
        return -1;
    }

    public boolean isKsiegiGuiTitle(String title) {
        return guiTitle.equals(title);
    }

    private String resolveGuiTitle() {
        return color(AnarchiaGGSettings.getConfig(plugin).getString("ksiegi.gui-title", "&8Księgi"));
    }

    public BookType identifyBook(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        String id = container.get(bookIdKey, PersistentDataType.STRING);
        if (id != null) {
            return BookType.byId(id);
        }
        if (meta.hasCustomModelData()) {
            return cmdToType.get(meta.getCustomModelData());
        }
        return null;
    }

    public ItemStack tagBookItem(ItemStack item, BookType type) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.getPersistentDataContainer().set(bookIdKey, PersistentDataType.STRING, type.id());
        item.setItemMeta(meta);
        return item;
    }

    public boolean updateItemLore(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        boolean metaModified = false;
        java.util.List<String> lore = meta.hasLore() && meta.getLore() != null
                ? new java.util.ArrayList<>(meta.getLore())
                : new java.util.ArrayList<>();

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        for (BookType type : BookType.values()) {
            NamespacedKey pdcKey = appliedKeys.get(type);
            boolean hasPdc = pdc.has(pdcKey, PersistentDataType.BYTE);
            String expectedTarget = color("&f" + type.loreLine());
            String strippedExpected = ChatColor.stripColor(type.loreLine()).toLowerCase(Locale.ROOT);

            boolean hasLoreMatch = false;
            int matchedLoreIndex = -1;

            for (int i = 0; i < lore.size(); i++) {
                String line = lore.get(i);
                if (line == null) continue;
                String stripped = ChatColor.stripColor(line).toLowerCase(Locale.ROOT);
                if (stripped.equals(strippedExpected) || (type == BookType.ULASKAWIENIA && (stripped.contains("ułaskawien") || stripped.contains("ulaskawien")))) {
                    hasLoreMatch = true;
                    matchedLoreIndex = i;
                    break;
                }
            }

            if (hasPdc) {
                // Sync wizualnego lore tylko gdy PDC już jest (applyEnchant).
                if (hasLoreMatch) {
                    if (!lore.get(matchedLoreIndex).equals(expectedTarget)) {
                        lore.set(matchedLoreIndex, expectedTarget);
                        metaModified = true;
                    }
                } else {
                    lore.add(expectedTarget);
                    metaModified = true;
                }
            } else if (hasLoreMatch) {
                // Forge: lore bez PDC — zdejmij linię, nie przyznawaj enchantu.
                lore.remove(matchedLoreIndex);
                metaModified = true;
            }
        }

        if (metaModified) {
            meta.setLore(lore.isEmpty() ? null : lore);
            item.setItemMeta(meta);
            return true;
        }
        return false;
    }

    /** Tylko PDC — match lore = forge (edycja lore bez książki). */
    public boolean hasEnchant(ItemStack item, BookType type) {
        if (item == null || item.getType() == Material.AIR || type == null) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        NamespacedKey pdcKey = appliedKeys.get(type);
        return pdcKey != null && meta.getPersistentDataContainer().has(pdcKey, PersistentDataType.BYTE);
    }

    public boolean hasAnyEnchant(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        for (BookType type : BookType.values()) {
            if (hasEnchant(item, type)) {
                return true;
            }
        }
        return false;
    }

    public ItemStack applyEnchantVisual(ItemStack item, BookType type) {
        if (item == null || item.getType() == Material.AIR) {
            return item;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(appliedKeys.get(type), PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        updateItemLore(item);
        return item;
    }

    public ItemStack removeEnchantVisual(ItemStack item, BookType type) {
        if (item == null || item.getType() == Material.AIR) {
            return item;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.getPersistentDataContainer().remove(appliedKeys.get(type));
        if (meta.hasLore() && meta.getLore() != null) {
            java.util.List<String> lore = new java.util.ArrayList<>(meta.getLore());
            lore.removeIf(line -> line != null && (ChatColor.stripColor(line).equalsIgnoreCase(type.loreLine()) || (type == BookType.ULASKAWIENIA && (ChatColor.stripColor(line).toLowerCase(Locale.ROOT).contains("ułaskawien") || ChatColor.stripColor(line).toLowerCase(Locale.ROOT).contains("ulaskawien")))));
            meta.setLore(lore.isEmpty() ? null : lore);
        }

        item.setItemMeta(meta);
        return item;
    }

    public boolean isTargetAllowed(ItemStack item, BookType type) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        Material material = item.getType();
        if (type == BookType.ULASKAWIENIA) {
            return isAllowedForUlaskawienia(material);
        }
        return switch (type.targetType()) {
            case ANY -> true;
            case SWORD -> material.name().endsWith("_SWORD");
            case PICKAXE -> material.name().endsWith("_PICKAXE");
            case HELMET, CHESTPLATE, LEGGINGS, BOOTS, ARMOR -> isArmorMaterial(material);
        };
    }

    public static boolean isAllowedForUlaskawienia(Material material) {
        if (material == null || material == Material.AIR) {
            return false;
        }
        if (material == Material.GOLDEN_HELMET) {
            return false;
        }
        String name = material.name();
        return isArmorMaterial(material)
                || name.endsWith("_SWORD")
                || name.endsWith("_PICKAXE")
                || name.endsWith("_HOE")
                || material == Material.ELYTRA;
    }

    public static boolean isArmorMaterial(Material material) {
        if (material == null) return false;
        String name = material.name();
        return name.endsWith("_HELMET")
                || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS")
                || name.endsWith("_BOOTS")
                || name.equals("ELYTRA")
                || name.equals("TURTLE_HELMET");
    }

    public boolean isDamageable(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        if (isArmorMaterial(item.getType())) {
            return true;
        }
        ItemMeta meta = item.getItemMeta();
        return meta instanceof Damageable;
    }

    public String color(String text) {
        return ColorUtils.colorize(text);
    }
}
