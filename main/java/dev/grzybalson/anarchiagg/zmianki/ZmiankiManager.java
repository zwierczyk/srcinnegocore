package dev.grzybalson.anarchiagg.zmianki;

import dev.grzybalson.anarchiagg.AnarchiaGGSettings;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.ItemSerializer;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

public class ZmiankiManager {
    protected static final String DAMAGE_PHRASE = "Dodatkowe obrażenia";
    protected static final String DAMAGE_PHRASE_ALT = "Zwiększone obrażenia";

    private final JavaPlugin plugin;
    private final File configFile;
    private final boolean anarchiaLegacyConfig;
    private YamlConfiguration config;
    private final NamespacedKey zmiankaItemKey;
    private final NamespacedKey zmiankaBonusKey;
    private String guiTitle;
    private int guiSize;
    private int applySlot;
    private int mainSlot;
    private int closeSlot;
    private double damageMultiplier;
    private Material expectedZmiankaType;
    private String expectedZmiankaName;
    private int expectedZmiankaCustomModelData;
    private ItemStack zmiankaItemTemplate;
    private ItemStack applyItemTemplate;
    private List<String> applyItemBaseLore = List.of();
    private ItemStack mainSlotTemplate;
    private ItemStack closeItemTemplate;

    public ZmiankiManager(JavaPlugin plugin) {
        this(plugin, AnarchiaGGSettings.getFile(plugin), true);
    }

    public ZmiankiManager(JavaPlugin plugin, File configFile) {
        this(plugin, configFile, false, "zmianka");
    }

    public ZmiankiManager(JavaPlugin plugin, File configFile, String keyPrefix) {
        this(plugin, configFile, false, keyPrefix);
    }

    private ZmiankiManager(JavaPlugin plugin, File configFile, boolean anarchiaLegacyConfig) {
        this(plugin, configFile, anarchiaLegacyConfig, "zmianka");
    }

    private ZmiankiManager(JavaPlugin plugin, File configFile, boolean anarchiaLegacyConfig, String keyPrefix) {
        this.plugin = plugin;
        this.configFile = configFile;
        this.anarchiaLegacyConfig = anarchiaLegacyConfig;
        String prefix = (keyPrefix == null || keyPrefix.isBlank()) ? "zmianka" : keyPrefix.trim().toLowerCase(Locale.ROOT);
        this.zmiankaItemKey = new NamespacedKey(plugin, prefix + "-item");
        this.zmiankaBonusKey = new NamespacedKey(plugin, prefix + "-bonus");
        reload();
    }

    public NamespacedKey itemKey() {
        return zmiankaItemKey;
    }

    public NamespacedKey bonusKey() {
        return zmiankaBonusKey;
    }

    protected YamlConfiguration rawConfig() {
        return config;
    }

    protected JavaPlugin plugin() {
        return plugin;
    }

    public void reload() {
        if (anarchiaLegacyConfig) {
            AnarchiaGGSettings.reload(plugin);
            this.config = AnarchiaGGSettings.getConfig(plugin);
        } else {
            this.config = YamlConfiguration.loadConfiguration(configFile);
        }
        loadCachedSettings();
    }

    private void saveConfig() {
        if (anarchiaLegacyConfig) {
            AnarchiaGGSettings.save(plugin, config);
            return;
        }
        try {
            config.save(configFile);
        } catch (IOException exception) {
            plugin.getLogger().warning("Nie udalo sie zapisac configu zmianek: " + exception.getMessage());
        }
    }

    public String color(String input) {
        return ColorUtils.colorize(input);
    }

    public String getGuiTitle() {
        return guiTitle;
    }

    public int getGuiSize() {
        return guiSize;
    }

    public int getDamageMultiplierPercentBase() {
        return 0;
    }

    public double getDamageMultiplier() {
        return damageMultiplier;
    }

    public int getApplySlot() {
        return applySlot;
    }

    public int getMainSlot() {
        return mainSlot;
    }

    public int getCloseSlot() {
        return closeSlot;
    }

    public ItemStack buildZmiankaItem(int amount) {
        ItemStack item = zmiankaItemTemplate.clone();
        item.setAmount(Math.max(1, amount));
        return item;
    }

    /**
     * Ustawia pełny template zmianki z itemu w ręce (typ, CMD, lore, meta) i zapisuje do configu.
     */
    public boolean setTemplateItem(ItemStack source) {
        if (source == null || source.getType() == Material.AIR) {
            return false;
        }
        ItemStack template = source.clone();
        template.setAmount(1);
        ItemMeta meta = template.getItemMeta();
        if (meta == null) {
            return false;
        }
        meta.getPersistentDataContainer().set(zmiankaItemKey, PersistentDataType.BYTE, (byte) 1);
        template.setItemMeta(meta);

        this.zmiankaItemTemplate = template;
        this.expectedZmiankaType = template.getType();
        this.expectedZmiankaName = meta.hasDisplayName() ? meta.getDisplayName() : color("&b&lZmianka");
        this.expectedZmiankaCustomModelData = meta.hasCustomModelData() ? meta.getCustomModelData() : 0;

        YamlConfiguration config = this.config;
        config.set("zmianki.item.type", template.getType().name());
        if (meta.hasDisplayName()) {
            config.set("zmianki.item.name", meta.getDisplayName().replace('§', '&'));
        }
        if (meta.hasLore() && meta.getLore() != null) {
            List<String> lore = new ArrayList<>();
            for (String line : meta.getLore()) {
                lore.add(line == null ? "" : line.replace('§', '&'));
            }
            config.set("zmianki.item.lore", lore);
        }
        config.set("zmianki.item.custom-model-data", expectedZmiankaCustomModelData);
        config.set("zmianki.item.serialized", ItemSerializer.serializeItem(template));
        saveConfig();
        return true;
    }

    public ItemStack buildApplyItem(Integer lastBonus) {
        ItemStack item = applyItemTemplate.clone();

        if (lastBonus != null) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                List<String> lore = new ArrayList<>(applyItemBaseLore.size() + 3);
                lore.add(color(""));
                lore.add(color("&8✕ &#A8A8A8Ostatni wynik &8· &f" + lastBonus + "%"));
                lore.add(color(""));
                lore.addAll(applyItemBaseLore);
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
        }

        return item;
    }

    public ItemStack buildMainSlotItem() {
        return mainSlotTemplate.clone();
    }

    public ItemStack buildCloseItem() {
        return closeItemTemplate.clone();
    }

    public boolean isSupportedWeapon(ItemStack item) {
        if (item == null) {
            return false;
        }
        Material type = item.getType();
        return type == Material.NETHERITE_SWORD || type == Material.DIAMOND_SWORD;
    }

    public boolean isZmiankaItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        PersistentDataContainer container = meta.getPersistentDataContainer();
        Byte tagged = container.get(zmiankaItemKey, PersistentDataType.BYTE);
        if (tagged != null && tagged == (byte) 1) {
            return true;
        }
        if (container.has(zmiankaItemKey, PersistentDataType.BYTE) ||
            container.has(zmiankaItemKey, PersistentDataType.INTEGER) ||
            container.has(zmiankaItemKey, PersistentDataType.STRING)) {
            return true;
        }

        if (item.getType() != expectedZmiankaType) {
            return false;
        }
        if (!meta.hasDisplayName() || !isZmiankaDisplayName(meta.getDisplayName())) {
            return false;
        }
        return !meta.hasCustomModelData() ? expectedZmiankaCustomModelData <= 0 : meta.getCustomModelData() == expectedZmiankaCustomModelData;
    }

    private boolean isZmiankaDisplayName(String displayName) {
        if (Objects.equals(displayName, expectedZmiankaName)) {
            return true;
        }
        String stripped = ChatColor.stripColor(displayName);
        if (stripped == null) {
            return false;
        }
        String lower = stripped.toLowerCase(Locale.ROOT);
        return lower.contains("zmianka") || lower.contains("zmianki") || lower.contains("zaczarowanie")
            || lower.contains("koniczynka") || lower.contains("szczesciarz") || lower.contains("szczęściarz");
    }

    public boolean consumeOneZmianka(Player player) {
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (isZmiankaItem(offhand)) {
            decrement(player.getInventory().getItemInOffHand(), () -> player.getInventory().setItemInOffHand(null));
            return true;
        }

        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (!isZmiankaItem(item)) {
                continue;
            }
            final int slot = i;
            decrement(item, () -> player.getInventory().setItem(slot, null));
            return true;
        }
        return false;
    }

    private void decrement(ItemStack item, Runnable removeAction) {
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            removeAction.run();
        }
    }

    public int rollBonus() {
        // 5%–43%: im wyżej, tym trudniej wylosować.
        // 5–13 normalnie, 14–30 trudniej, 31–43 bardzo trudno.
        int totalWeight = 0;
        int[] weights = new int[39]; // index 0 = 5%
        for (int i = 0; i < weights.length; i++) {
            int bonus = 5 + i;
            int weight;
            if (bonus <= 13) {
                weight = 90 - (bonus - 5) * 4; // 90 → 58
            } else if (bonus <= 30) {
                weight = Math.max(6, 36 - (bonus - 14) * 2); // 36 → 6
            } else {
                weight = Math.max(1, (int) Math.round(8.0 * Math.pow(0.68, bonus - 31))); // 8 → 1
            }
            weights[i] = weight;
            totalWeight += weight;
        }

        int roll = ThreadLocalRandom.current().nextInt(totalWeight);
        for (int i = 0; i < weights.length; i++) {
            roll -= weights[i];
            if (roll < 0) {
                return 5 + i;
            }
        }
        return 43;
    }

    public int clampBonus(int bonus) {
        return bonus;
    }

    public void applyBonus(ItemStack weapon, int bonus) {
        if (weapon == null || weapon.getType() == Material.AIR) {
            return;
        }
        ItemMeta meta = weapon.getItemMeta();
        if (meta == null) {
            return;
        }
        int clamped = clampBonus(bonus);
        meta.getPersistentDataContainer().set(zmiankaBonusKey, PersistentDataType.INTEGER, clamped);
        meta.setLore(updateDamageLore(meta.getLore(), clamped));
        weapon.setItemMeta(meta);
    }

    public Integer getBonus(ItemStack weapon) {
        if (weapon == null || weapon.getType() == Material.AIR || !weapon.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = weapon.getItemMeta();
        if (meta == null) {
            return null;
        }

        Integer pdcValue = meta.getPersistentDataContainer().get(zmiankaBonusKey, PersistentDataType.INTEGER);
        if (pdcValue != null) {
            return pdcValue;
        }

        if (!meta.hasLore() || meta.getLore() == null) {
            return null;
        }

        for (String line : meta.getLore()) {
            String stripped = ChatColor.stripColor(color(line));
            if (stripped == null || !isDamageLoreLine(stripped)) {
                continue;
            }

            StringBuilder digits = new StringBuilder();
            boolean foundDigit = false;
            for (char c : stripped.toCharArray()) {
                if ((c == '-' && !foundDigit && digits.length() == 0) || Character.isDigit(c)) {
                    digits.append(c);
                    if (Character.isDigit(c)) {
                        foundDigit = true;
                    }
                } else if (foundDigit) {
                    break;
                }
            }
            if (foundDigit) {
                try {
                    return Integer.parseInt(digits.toString());
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    public String formatDamageLoreColor(int bonus) {
        if (bonus >= 31) {
            return "&6Dodatkowe obrażenia ";
        }
        if (bonus >= 14) {
            return "&eDodatkowe obrażenia ";
        }
        if (bonus > 0) {
            return "&fDodatkowe obrażenia ";
        }
        return "&cDodatkowe obrażenia ";
    }

    protected boolean isDamageLoreLine(String stripped) {
        if (stripped == null || stripped.isBlank()) {
            return false;
        }
        String lower = stripped.toLowerCase(Locale.ROOT);
        return stripped.contains(DAMAGE_PHRASE)
            || stripped.contains(DAMAGE_PHRASE_ALT)
            || lower.contains("dodatkowe obraze")
            || lower.contains("zwiekszone obrazenia")
            || lower.contains("zwiększone obrażenia");
    }

    protected List<String> updateDamageLore(List<String> currentLore, int bonus) {
        List<String> lore = currentLore != null ? new ArrayList<>(currentLore) : new ArrayList<>();
        for (int i = lore.size() - 1; i >= 0; i--) {
            String stripped = ChatColor.stripColor(color(lore.get(i)));
            if (isDamageLoreLine(stripped)) {
                lore.remove(i);
                if (i > 0) {
                    String prev = ChatColor.stripColor(color(lore.get(i - 1)));
                    if (prev != null && prev.isBlank()) {
                        lore.remove(i - 1);
                        i--;
                    }
                }
            }
        }
        lore.addAll(buildDamageLoreTail(bonus));
        return lore;
    }

    protected List<String> buildDamageLoreTail(int bonus) {
        return List.of(color(formatDamageLoreColor(bonus) + bonus + "%"));
    }

    private void loadCachedSettings() {
        FileConfiguration config = this.config;
        this.guiTitle = color(getString(config, "zmianki.gui.title", "gui-title", "&8Zmianki"));
        this.guiSize = Math.max(9, getInt(config, "zmianki.gui.size", 27));
        this.applySlot = getInt(config, "zmianki.gui.apply-item.slot", getInt(config, "gui-zmianka-item.slot", 11));
        this.mainSlot = getInt(config, "zmianki.gui.main-slot.slot", getInt(config, "gui-mainslot-item.slot", 13));
        this.closeSlot = getInt(config, "zmianki.gui.close-button.slot", getInt(config, "close-button.slot", 15));
        this.damageMultiplier = getDouble(config, "zmianki.damage-multiplier", "damage-multiplier", 0.3D);

        this.expectedZmiankaType = getMaterial(config, "zmianki.item.type", "zmianka.type", Material.HEART_OF_THE_SEA);
        this.expectedZmiankaName = color(getString(config, "zmianki.item.name", "zmianka.name", "&b&lZmianka"));
        this.expectedZmiankaCustomModelData = getInt(config, "zmianki.item.custom-model-data", getInt(config, "zmianka.custom-model-data", 1));

        ItemStack serializedTemplate = null;
        String serialized = config.getString("zmianki.item.serialized", "");
        if (serialized != null && !serialized.isBlank()) {
            serializedTemplate = ItemSerializer.deserializeItem(serialized);
        }
        if (serializedTemplate != null && serializedTemplate.getType() != Material.AIR) {
            this.zmiankaItemTemplate = serializedTemplate.clone();
            this.zmiankaItemTemplate.setAmount(1);
            ItemMeta serMeta = this.zmiankaItemTemplate.getItemMeta();
            if (serMeta != null) {
                this.expectedZmiankaType = this.zmiankaItemTemplate.getType();
                if (serMeta.hasDisplayName()) {
                    this.expectedZmiankaName = serMeta.getDisplayName();
                }
                if (serMeta.hasCustomModelData()) {
                    this.expectedZmiankaCustomModelData = serMeta.getCustomModelData();
                }
                serMeta.getPersistentDataContainer().set(zmiankaItemKey, PersistentDataType.BYTE, (byte) 1);
                this.zmiankaItemTemplate.setItemMeta(serMeta);
            }
        } else {
            this.zmiankaItemTemplate = createItem(
                    expectedZmiankaType,
                    getString(config, "zmianki.item.name", "zmianka.name", "&b&lZmianka"),
                    getStringList(config, "zmianki.item.lore", "zmianka.lore"),
                    expectedZmiankaCustomModelData,
                    1
            );
            ItemMeta zmiankaMeta = this.zmiankaItemTemplate.getItemMeta();
            if (zmiankaMeta != null) {
                zmiankaMeta.getPersistentDataContainer().set(zmiankaItemKey, PersistentDataType.BYTE, (byte) 1);
                this.zmiankaItemTemplate.setItemMeta(zmiankaMeta);
            }
        }

        this.applyItemBaseLore = colorList(getStringList(config, "zmianki.gui.apply-item.lore", "gui-zmianka-item.lore"));
        this.applyItemTemplate = createItem(
                getMaterial(config, "zmianki.gui.apply-item.type", "gui-zmianka-item.type", Material.BLUE_DYE),
                getString(config, "zmianki.gui.apply-item.name", "gui-zmianka-item.name", "&3&lPrzebonuj przedmiot"),
                this.applyItemBaseLore,
                getInt(config, "zmianki.gui.apply-item.custom-model-data", getInt(config, "gui-zmianka-item.custom-model-data", 1)),
                1,
                false
        );
        this.mainSlotTemplate = createItem(
                getMaterial(config, "zmianki.gui.main-slot.type", "gui-mainslot-item.type", Material.PAPER),
                getString(config, "zmianki.gui.main-slot.name", "gui-mainslot-item.name", "&6&lWybierz przedmiot"),
                getStringList(config, "zmianki.gui.main-slot.lore", "gui-mainslot-item.lore"),
                getInt(config, "zmianki.gui.main-slot.custom-model-data", getInt(config, "gui-mainslot-item.custom-model-data", 0)),
                1
        );
        this.closeItemTemplate = createItem(
                getMaterial(config, "zmianki.gui.close-button.type", "close-button.type", Material.BARRIER),
                getString(config, "zmianki.gui.close-button.name", "close-button.name", "&c&lZamknij"),
                getStringList(config, "zmianki.gui.close-button.lore", "close-button.lore"),
                getInt(config, "zmianki.gui.close-button.custom-model-data", getInt(config, "close-button.custom-model-data", 0)),
                1
        );
    }

    private ItemStack createItem(Material material, String name, List<String> lore, int customModelData, int amount) {
        return createItem(material, name, lore, customModelData, amount, true);
    }

    private ItemStack createItem(Material material, String name, List<String> lore, int customModelData, int amount, boolean colorLore) {
        ItemStack item = new ItemStack(material, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        if (name != null && !name.isEmpty()) {
            meta.setDisplayName(color(name));
        }
        if (lore != null && !lore.isEmpty()) {
            List<String> coloredLore = new ArrayList<>(lore.size());
            for (String line : lore) {
                coloredLore.add(colorLore ? color(line) : line);
            }
            meta.setLore(coloredLore);
        }
        if (customModelData > 0) {
            meta.setCustomModelData(customModelData);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private List<String> colorList(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        List<String> colored = new ArrayList<>(lines.size());
        for (String line : lines) {
            colored.add(color(line));
        }
        return List.copyOf(colored);
    }

    private Material getMaterial(FileConfiguration config, String primaryPath, String legacyPath, Material fallback) {
        String raw = getString(config, primaryPath, legacyPath, fallback.name());
        Material material = Material.matchMaterial(raw == null ? fallback.name() : raw.toUpperCase(Locale.ROOT));
        return material == null ? fallback : material;
    }

    private String getString(FileConfiguration config, String primaryPath, String legacyPath, String fallback) {
        if (config.contains(primaryPath)) {
            return config.getString(primaryPath, fallback);
        }
        if (legacyPath != null && config.contains(legacyPath)) {
            return config.getString(legacyPath, fallback);
        }
        return fallback;
    }

    private List<String> getStringList(FileConfiguration config, String primaryPath, String legacyPath) {
        if (config.contains(primaryPath)) {
            return config.getStringList(primaryPath);
        }
        if (legacyPath != null && config.contains(legacyPath)) {
            return config.getStringList(legacyPath);
        }
        return new ArrayList<>();
    }

    private int getInt(FileConfiguration config, String path, int fallback) {
        return config.getInt(path, fallback);
    }

    private double getDouble(FileConfiguration config, String primaryPath, String legacyPath, double fallback) {
        if (config.contains(primaryPath)) {
            return config.getDouble(primaryPath, fallback);
        }
        if (legacyPath != null && config.contains(legacyPath)) {
            return config.getDouble(legacyPath, fallback);
        }
        return fallback;
    }

    private Material getMaterial(String primaryPath, String legacyPath, Material fallback) {
        String raw = getString(primaryPath, legacyPath, fallback.name());
        Material material = Material.matchMaterial(raw == null ? fallback.name() : raw.toUpperCase(Locale.ROOT));
        return material == null ? fallback : material;
    }

    private String getString(String primaryPath, String legacyPath, String fallback) {
        return getString(this.config, primaryPath, legacyPath, fallback);
    }

    private List<String> getStringList(String primaryPath, String legacyPath) {
        return getStringList(this.config, primaryPath, legacyPath);
    }

    private int getInt(String path, int fallback) {
        return this.config.getInt(path, fallback);
    }

    private double getDouble(String primaryPath, String legacyPath, double fallback) {
        return getDouble(this.config, primaryPath, legacyPath, fallback);
    }
}
