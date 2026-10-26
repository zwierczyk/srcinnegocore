package dev.grzybalson.anarchiagg.szafa.gui;

import dev.grzybalson.anarchiagg.szafa.SzafkaConfig;
import dev.grzybalson.anarchiagg.szafa.kostiumy.KostiumyHandler;
import dev.grzybalson.anarchiagg.szafa.papugi.PapugiHandler;
import dev.grzybalson.anarchiagg.szafa.pety.PetyHandler;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SzafaGUIHandler {
    public static final int INVENTORY_SIZE = 45;
    private int inventorySize = INVENTORY_SIZE;
    public static String ZWOJ_KOSTIUM_DISPLAY_NAME;
    public static String ZWOJ_PET_DISPLAY_NAME;
    public static final Material ZWOJ_KOSTIUM_TYPE;
    public static final Material ZWOJ_PET_TYPE;

    private static final Pattern DURATION_EXTRACTOR;

    private static final String DEFAULT_GUI_RESOURCE = "addons/anarchiagg/szafa/szafa.yml";

    private final KostiumyHandler kostiumyHandler;
    private final PetyHandler petyHandler;
    private final PapugiHandler papugiHandler;
    private final JavaPlugin plugin;
    private final FileConfiguration customConfig;
    private final String guiResourcePath;
    private final boolean costumesOnly;
    private FileConfiguration guiConfig;
    private FileConfiguration defaultGuiConfig;

    private final Set<UUID> hiddenPets = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private boolean slotOutOfBoundsWarned;

    public int ACTIVE_KOSTIUM_SLOT;
    public int REMOVE_KOSTIUM_SLOT;
    public int EXTEND_KOSTIUM_SLOT;
    public int ACTIVE_PET_SLOT;
    public int REMOVE_PET_SLOT;
    public int EXTEND_PET_SLOT;
    public int ACTIVE_PAPUGA_SLOT;
    public int REMOVE_PAPUGA_SLOT;
    public int REROLL_PAPUGA_SLOT;
    public int CLOSE_BUTTON_SLOT;
    public int KOSTIUMY_LIST_SLOT;
    public int PET_VISIBILITY_SLOT;
    public int PETY_LIST_SLOT;
    public String NO_KOSTIUM_NAME;
    public String REMOVE_KOSTIUM_NAME;
    public String EXTEND_KOSTIUM_NAME;
    public String NO_PET_NAME;
    public String REMOVE_PET_NAME;
    public String EXTEND_PET_NAME;
    public String NO_PAPUGA_NAME;
    public String REMOVE_PAPUGA_NAME;
    public String REROLL_PAPUGA_NAME;
    public String CLOSE_BUTTON_NAME;
    private String GUI_TITLE;

    public SzafaGUIHandler(KostiumyHandler kostiumyHandler, PetyHandler petyHandler, PapugiHandler papugiHandler, JavaPlugin plugin) {
        this(kostiumyHandler, petyHandler, papugiHandler, plugin, null, DEFAULT_GUI_RESOURCE, false);
    }

    /**
     * Wariant dla dodatkow trzymajacych szafke w wlasnym pliku. Gdy {@code costumesOnly} jest ustawione,
     * sloty petow i papug sa calkowicie pomijane.
     */
    public SzafaGUIHandler(KostiumyHandler kostiumyHandler, PetyHandler petyHandler, PapugiHandler papugiHandler, JavaPlugin plugin,
                           FileConfiguration customConfig, String guiResourcePath, boolean costumesOnly) {
        this.kostiumyHandler = kostiumyHandler;
        this.petyHandler = petyHandler;
        this.papugiHandler = papugiHandler;
        this.plugin = plugin;
        this.customConfig = customConfig;
        this.guiResourcePath = guiResourcePath == null || guiResourcePath.isBlank() ? DEFAULT_GUI_RESOURCE : guiResourcePath;
        this.costumesOnly = costumesOnly;
        reload();
    }

    public boolean isCostumesOnly() {
        return this.costumesOnly;
    }

    public void reload() {
        loadGuiConfig();
        this.GUI_TITLE = ChatColor.translateAlternateColorCodes('&', getGuiString("gui.szafa-title", "&7Szafka"));
        int configuredSize = getGuiInt("gui.size", INVENTORY_SIZE);
        this.inventorySize = (configuredSize == 27 || configuredSize == 36 || configuredSize == 45 || configuredSize == 54)
            ? configuredSize
            : INVENTORY_SIZE;
        this.ACTIVE_KOSTIUM_SLOT = getGuiInt("gui.kostium-slot.kostium.slot", 20);
        this.REMOVE_KOSTIUM_SLOT = getGuiInt("gui.zdejmij-button.kostium.slot", 11);
        this.EXTEND_KOSTIUM_SLOT = getGuiInt("gui.zwoj-button.kostium.slot", 29);
        this.ACTIVE_PET_SLOT = getGuiInt("gui.pet-slot.pet.slot", 24);
        this.REMOVE_PET_SLOT = getGuiInt("gui.zdejmij-button.pet.slot", 15);
        this.EXTEND_PET_SLOT = getGuiInt("gui.zwoj-button.pet.slot", 33);
        this.ACTIVE_PAPUGA_SLOT = getGuiInt("gui.papuga-slot.papuga.slot", 31);
        this.REMOVE_PAPUGA_SLOT = getGuiInt("gui.zdejmij-button.papuga.slot", 30);
        this.REROLL_PAPUGA_SLOT = getGuiInt("gui.rolluj-button.papuga.slot", 32);
        this.CLOSE_BUTTON_SLOT = getGuiInt("gui.close-button.slot", 40);
        this.KOSTIUMY_LIST_SLOT = getGuiInt("gui.dekoracje.4.slot", 39);
        this.PET_VISIBILITY_SLOT = getGuiInt("gui.dekoracje.5.slot", 22);
        this.PETY_LIST_SLOT = getGuiInt("gui.dekoracje.6.slot", 41);
        this.NO_KOSTIUM_NAME = color(getGuiString("gui.kostium-slot.brak-kostiumu.name", "&cBrak kostiumu"));
        this.REMOVE_KOSTIUM_NAME = color(getGuiString("gui.zdejmij-button.kostium.name", "&cZdejmij Aktywny Kostium"));
        this.EXTEND_KOSTIUM_NAME = color(getGuiString("gui.zwoj-button.kostium.name", "&aPrzedluz Kostium"));
        this.NO_PET_NAME = color(getGuiString("gui.pet-slot.brak-peta.name", "&cBrak peta"));
        this.REMOVE_PET_NAME = color(getGuiString("gui.zdejmij-button.pet.name", "&cZdejmij Aktywnego Peta"));
        this.EXTEND_PET_NAME = color(getGuiString("gui.zwoj-button.pet.name", "&aPrzedluz Peta"));
        this.NO_PAPUGA_NAME = color(getGuiString("gui.papuga-slot.brak-papugi.name", "&cBrak papuszki"));
        this.REMOVE_PAPUGA_NAME = color(getGuiString("gui.zdejmij-button.papuga.name", "&cZdejmij papuge"));
        this.REROLL_PAPUGA_NAME = color(getGuiString("gui.rolluj-button.papuga.name", "&aPrzelosuj bonus papugi"));
        this.CLOSE_BUTTON_NAME = color(getGuiString("gui.close-button.name", " "));

        ZWOJ_KOSTIUM_DISPLAY_NAME = color(getConfigString(SzafkaConfig.path("zwoj-kostium.name"), "zwoj-kostium.name", "&6&lZwoj przedluzenia"));
        ZWOJ_PET_DISPLAY_NAME = color(getConfigString(SzafkaConfig.path("zwoj-pet.name"), "zwoj-pet.name", "&6&lZwoj przedluzenia"));
    }

    private void loadGuiConfig() {
        // W trybie dodatku plik GUI lezy w data folderze i administrator moze go edytowac.
        File guiFile = this.customConfig == null ? null : new File(this.plugin.getDataFolder(), this.guiResourcePath);
        if (guiFile != null && guiFile.isFile()) {
            this.defaultGuiConfig = YamlConfiguration.loadConfiguration(guiFile);
        } else {
            try (InputStream stream = this.plugin.getResource(this.guiResourcePath)) {
                this.defaultGuiConfig = stream != null ? YamlConfiguration.loadConfiguration(new InputStreamReader(stream)) : new YamlConfiguration();
            } catch (IOException exception) {
                this.defaultGuiConfig = new YamlConfiguration();
            }
        }
        this.guiConfig = this.customConfig != null ? this.customConfig : SzafkaConfig.getConfig(this.plugin);
    }

    private String getGuiString(String path, String fallback) {
        String value = getConfigString(SzafkaConfig.path(path), path, null);
        if (value != null) {
            return value;
        }
        return this.defaultGuiConfig != null ? this.defaultGuiConfig.getString(path, fallback) : fallback;
    }

    private int getGuiInt(String path, int fallback) {
        String szafkaPath = SzafkaConfig.path(path);
        if (this.guiConfig.isInt(szafkaPath)) {
            return this.guiConfig.getInt(szafkaPath);
        }
        if (this.guiConfig.isInt(path)) {
            return this.guiConfig.getInt(path);
        }
        return this.defaultGuiConfig != null ? this.defaultGuiConfig.getInt(path, fallback) : fallback;
    }

    private List<String> getGuiStringList(String path) {
        List<String> list = this.guiConfig.getStringList(SzafkaConfig.path(path));
        if (list != null && !list.isEmpty()) {
            return list;
        }
        list = this.guiConfig.getStringList(path);
        if (list != null && !list.isEmpty()) {
            return list;
        }
        return this.defaultGuiConfig != null ? this.defaultGuiConfig.getStringList(path) : List.of();
    }

    private ConfigurationSection getGuiSection(String path) {
        ConfigurationSection section = this.guiConfig.getConfigurationSection(SzafkaConfig.path(path));
        if (section != null) {
            return section;
        }
        section = this.guiConfig.getConfigurationSection(path);
        if (section != null) {
            return section;
        }
        return this.defaultGuiConfig == null ? null : this.defaultGuiConfig.getConfigurationSection(path);
    }

    private String getConfigString(String primaryPath, String legacyPath, String fallback) {
        String value = this.guiConfig.getString(primaryPath);
        if (value != null) {
            return value;
        }
        return this.guiConfig.getString(legacyPath, fallback);
    }

    private ItemStack createGUIItem(String path) {
        String typeString = getGuiString(path + ".type", "GRAY_STAINED_GLASS_PANE");
        Material material = Material.matchMaterial(typeString != null ? typeString : "GRAY_STAINED_GLASS_PANE");
        ItemStack item = new ItemStack(material != null ? material : Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String rawName = getGuiString(path + ".name", " ");
            if (rawName == null || rawName.isBlank() || rawName.equalsIgnoreCase("Blad") || rawName.equalsIgnoreCase("Błąd")) {
                rawName = " ";
            }
            meta.setDisplayName(color(rawName));
            ArrayList<String> lore = new ArrayList<>();
            for (String line : getGuiStringList(path + ".lore")) {
                lore.add(color(line));
            }
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.values());
            hideAdditionalTooltip(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void hideAdditionalTooltip(ItemMeta meta) {
        try {
            meta.addItemFlags(ItemFlag.valueOf("HIDE_ADDITIONAL_TOOLTIP"));
        } catch (IllegalArgumentException ignored) {
        }
    }

    public ItemStack createPapugaRerollItem(Player player) {
        ItemStack item = createGUIItem("gui.rolluj-button.papuga");
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        int rerolls = this.papugiHandler.getDatabase().getActivePapugaRerolls(player.getUniqueId());
        String cost = this.papugiHandler.formatMoney(this.papugiHandler.getRollCost(rerolls));
        meta.setDisplayName(meta.getDisplayName().replace("%cost%", cost));
        if (meta.hasLore() && meta.getLore() != null) {
            ArrayList<String> lore = new ArrayList<>();
            for (String line : meta.getLore()) {
                lore.add(line.replace("%cost%", cost));
            }
            meta.setLore(lore);
        }
        hideAdditionalTooltip(meta);
        item.setItemMeta(meta);
        return item;
    }

    public void openGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, this.inventorySize, this.GUI_TITLE);
        applyGuiTemplate(gui);
        setIfInBounds(gui, this.CLOSE_BUTTON_SLOT, createGUIItem("gui.close-button"));

        ConfigurationSection decorations = getGuiSection("gui.dekoracje");
        if (decorations != null) {
            for (String key : decorations.getKeys(false)) {
                int slot = getGuiInt("gui.dekoracje." + key + ".slot", -1);
                if (slot < 0 || slot >= gui.getSize()) {
                    continue;
                }
                gui.setItem(slot, createGUIItem("gui.dekoracje." + key));
            }
        }

        String activeKostiumName = this.kostiumyHandler.getDatabase().getActiveKostium(player.getUniqueId());
        String resolvedKostiumKey = resolveKostiumKey(activeKostiumName);
        ItemStack currentKostiumItem;
        if (resolvedKostiumKey != null) {
            KostiumyHandler.KostiumData activeData = this.kostiumyHandler.getKostiumy().get(resolvedKostiumKey);
            currentKostiumItem = createActiveKostiumItem(player, activeData);
            setIfInBounds(gui, this.REMOVE_KOSTIUM_SLOT, createGUIItem("gui.zdejmij-button.kostium"));
            String expirationDate = this.kostiumyHandler.getDatabase().getKostiumExpiration(player.getUniqueId());
            if (expirationDate != null && !expirationDate.equalsIgnoreCase("NA ZAWSZE")) {
                setIfInBounds(gui, this.EXTEND_KOSTIUM_SLOT, createGUIItem("gui.zwoj-button.kostium"));
            }
        } else {
            currentKostiumItem = createGUIItem("gui.kostium-slot.brak-kostiumu");
        }
        setIfInBounds(gui, this.ACTIVE_KOSTIUM_SLOT, currentKostiumItem);

        if (this.costumesOnly || this.petyHandler == null) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            player.openInventory(gui);
            return;
        }

        String activePetName = this.petyHandler.getDatabase().getActivePet(player.getUniqueId());
        String resolvedPetKey = resolvePetKey(activePetName);
        ItemStack currentPetItem;
        if (resolvedPetKey != null) {
            PetyHandler.PetData activeData = this.petyHandler.getPety().get(resolvedPetKey);
            currentPetItem = createActivePetItem(player, activeData);
            setIfInBounds(gui, this.REMOVE_PET_SLOT, createGUIItem("gui.zdejmij-button.pet"));
            String expirationDate = this.petyHandler.getDatabase().getPetExpiration(player.getUniqueId());
            if (expirationDate != null && !expirationDate.equalsIgnoreCase("NA ZAWSZE")) {
                setIfInBounds(gui, this.EXTEND_PET_SLOT, createGUIItem("gui.zwoj-button.pet"));
            }
        } else {
            currentPetItem = createGUIItem("gui.pet-slot.brak-peta");
        }
        setIfInBounds(gui, this.ACTIVE_PET_SLOT, currentPetItem);

        if (this.papugiHandler != null) {
            String activePapugaKey = this.papugiHandler.getDatabase().getActivePapuga(player.getUniqueId());
            ItemStack currentPapugaItem;
            if (activePapugaKey != null && this.papugiHandler.getPapugi().containsKey(activePapugaKey)) {
                currentPapugaItem = this.papugiHandler.createGuiItem(player);
                setIfInBounds(gui, this.REMOVE_PAPUGA_SLOT, createGUIItem("gui.zdejmij-button.papuga"));
                setIfInBounds(gui, this.REROLL_PAPUGA_SLOT, createPapugaRerollItem(player));
            } else {
                currentPapugaItem = createGUIItem("gui.papuga-slot.brak-papugi");
            }
            setIfInBounds(gui, this.ACTIVE_PAPUGA_SLOT, currentPapugaItem);
        }
        if (isPetVisibilityEnabled()) {
            setIfInBounds(gui, this.PET_VISIBILITY_SLOT, createPetVisibilityItem(player));
        }

        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        player.openInventory(gui);
    }

    /** Sloty przyciskow sa czytane niezaleznie od gui.size — zla konfiguracja nie moze wywalac calego GUI. */
    private void setIfInBounds(Inventory gui, int slot, ItemStack item) {
        if (slot < 0 || slot >= gui.getSize()) {
            if (!this.slotOutOfBoundsWarned) {
                this.slotOutOfBoundsWarned = true;
                this.plugin.getLogger().warning("[szafka] Slot " + slot + " nie miesci sie w GUI o rozmiarze " + gui.getSize()
                    + " - przycisk pominiety. Sprawdz gui.size i sloty przyciskow w konfiguracji.");
            }
            return;
        }
        gui.setItem(slot, item);
    }

    private void applyGuiTemplate(Inventory gui) {
        if (gui == null || !getGuiBoolean("gui.use-template", true)) {
            return;
        }
        if (this.plugin instanceof dev.grzybalson.HollyCore core && core.getGuiFactory() != null) {
            core.getGuiFactory().applyDefaultTemplate(gui);
        }
    }

    private boolean getGuiBoolean(String path, boolean fallback) {
        if (this.defaultGuiConfig != null && this.defaultGuiConfig.contains(path)) {
            return this.defaultGuiConfig.getBoolean(path, fallback);
        }
        if (this.guiConfig != null && this.guiConfig.contains(path)) {
            return this.guiConfig.getBoolean(path, fallback);
        }
        return fallback;
    }

    public boolean isPetVisibilityEnabled() {
        if (this.costumesOnly || this.petyHandler == null) {
            return false;
        }
        if (this.plugin instanceof dev.grzybalson.HollyCore core && core.getAnarchiaGGConfigManager() != null && core.getAnarchiaGGConfigManager().getModulesConfig() != null) {
            return core.getAnarchiaGGConfigManager().getModulesConfig().isPetVisibility();
        }
        return true;
    }

    public boolean isPetHidden(UUID uuid) {
        if (!isPetVisibilityEnabled()) {
            return true;
        }
        return this.hiddenPets.contains(uuid);
    }

    public void togglePetVisibility(UUID uuid) {
        if (this.hiddenPets.contains(uuid)) {
            this.hiddenPets.remove(uuid);
        } else {
            this.hiddenPets.add(uuid);
        }
    }

    public ItemStack createPetVisibilityItem(Player player) {
        ItemStack item = new ItemStack(Material.REDSTONE_TORCH);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color("&aWidoczność peta"));
            boolean hidden = isPetHidden(player.getUniqueId());
            ArrayList<String> lore = new ArrayList<>();
            lore.add(color(" &8» &7Denerwuje ciebie twój zwierzak?"));
            lore.add(color(" &8» &7Jeżeli tak to wystarczy, że ukryjesz"));
            lore.add(color(" &8» &7go ta funkcją!"));
            lore.add("");
            lore.add(color(" &8» &7Status: " + (hidden ? "&cniewidoczny" : "&awidoczny")));
            lore.add(color(" &8» " + (hidden ? "&aKliknij, aby pokazać!" : "&cKliknij, aby ukryć!")));
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.values());
            hideAdditionalTooltip(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    public void openKostiumyList(Player player) {
        int size = this.inventorySize >= 54 ? 54 : 45;
        Inventory gui = Bukkit.createInventory(null, size, color("&7Lista kostiumów"));
        applyGuiTemplate(gui);
        int[] validSlots = listContentSlots(gui);
        int index = 0;
        for (Map.Entry<String, KostiumyHandler.KostiumData> entry : this.kostiumyHandler.getKostiumy().entrySet()) {
            if (entry.getKey().equalsIgnoreCase("zwoj-kostium") || (entry.getKey().equalsIgnoreCase("grincz") && this.kostiumyHandler.getKostiumy().containsKey("grinch")) || index >= validSlots.length) continue;
            KostiumyHandler.KostiumData data = entry.getValue();
            ItemStack item = data.displayItem() != null ? data.displayItem().clone() : new ItemStack(data.type());
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(dev.grzybalson.anarchiagg.szafa.pety.PetyVisualHandler.cleanPetDisplayName(safe(data.displayName())));
                ArrayList<String> lore = new ArrayList<>();
                for (String line : data.lore() == null ? List.<String>of() : data.lore()) {
                    if (line.contains("%uzycie%")) continue;
                    lore.add(color(line.replace("%czas%", "NA ZAWSZE")));
                }
                meta.setLore(lore);
                meta.addItemFlags(ItemFlag.values());
                hideAdditionalTooltip(meta);
                item.setItemMeta(meta);
            }
            String effectiveColor = KostiumyHandler.getEffectiveColor(entry.getKey(), data.displayName(), data.color());
            if (item.getType().name().startsWith("LEATHER_") && effectiveColor != null && !effectiveColor.isEmpty()) {
                applyColorToLeatherArmor(item, effectiveColor);
            }
            gui.setItem(validSlots[index++], item);
        }
        ItemStack returnItem = new ItemStack(Material.ARROW);
        ItemMeta returnMeta = returnItem.getItemMeta();
        if (returnMeta != null) {
            returnMeta.setDisplayName(color("&cPowrót"));
            returnMeta.addItemFlags(ItemFlag.values());
            hideAdditionalTooltip(returnMeta);
            returnItem.setItemMeta(returnMeta);
        }
        int backSlot = Math.max(0, gui.getSize() - 5);
        gui.setItem(backSlot, returnItem);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        player.openInventory(gui);
    }

    public void openPetyList(Player player) {
        int size = this.inventorySize >= 54 ? 54 : 45;
        Inventory gui = Bukkit.createInventory(null, size, color("&7Lista petów"));
        applyGuiTemplate(gui);
        int[] validSlots = listContentSlots(gui);
        int index = 0;
        for (Map.Entry<String, PetyHandler.PetData> entry : this.petyHandler.getPety().entrySet()) {
            if (entry.getKey().equalsIgnoreCase("zwoj-pet") || index >= validSlots.length) continue;
            PetyHandler.PetData data = entry.getValue();
            ItemStack baseItem = this.petyHandler.getPetItem(data.itemKey());
            ItemStack item = baseItem != null ? baseItem.clone() : new ItemStack(Material.STONE);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(dev.grzybalson.anarchiagg.szafa.pety.PetyVisualHandler.cleanPetDisplayName(safe(data.displayName())));
                ArrayList<String> lore = new ArrayList<>();
                for (String line : data.lore() == null ? List.<String>of() : data.lore()) {
                    if (line.contains("%uzycie%")) continue;
                    lore.add(color(line.replace("%czas%", "NA ZAWSZE")));
                }
                meta.setLore(lore);
                meta.addItemFlags(ItemFlag.values());
                hideAdditionalTooltip(meta);
                item.setItemMeta(meta);
            }
            if (item.getType().name().startsWith("LEATHER_") && data.color() != null && !data.color().isEmpty()) {
                applyColorToLeatherArmor(item, data.color());
            }
            gui.setItem(validSlots[index++], item);
        }
        ItemStack returnItem = new ItemStack(Material.ARROW);
        ItemMeta returnMeta = returnItem.getItemMeta();
        if (returnMeta != null) {
            returnMeta.setDisplayName(color("&cPowrót"));
            returnMeta.addItemFlags(ItemFlag.values());
            hideAdditionalTooltip(returnMeta);
            returnItem.setItemMeta(returnMeta);
        }
        int backSlot = Math.max(0, gui.getSize() - 5);
        gui.setItem(backSlot, returnItem);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        player.openInventory(gui);
    }

    private int[] listContentSlots(Inventory gui) {
        if (this.plugin instanceof dev.grzybalson.HollyCore core && core.getGuiFactory() != null) {
            return core.getGuiFactory().getContentSlots(gui);
        }
        return new int[] {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
        };
    }

    public boolean isKostiumGUI(Inventory inventory) {
        if (inventory == null || inventory.getViewers().isEmpty()) {
            return false;
        }
        try {
            String stripped = ChatColor.stripColor(inventory.getViewers().get(0).getOpenInventory().getTitle());
            if ("Szafka".equalsIgnoreCase(stripped)
                || "Lista kostiumów".equalsIgnoreCase(stripped)
                || "Lista petów".equalsIgnoreCase(stripped)) {
                return true;
            }
            String ownTitle = ChatColor.stripColor(this.GUI_TITLE);
            return ownTitle != null && !ownTitle.isBlank() && ownTitle.equalsIgnoreCase(stripped);
        } catch (Exception exception) {
            return inventory.getHolder() == null && (inventory.getSize() == this.inventorySize || inventory.getSize() == INVENTORY_SIZE || inventory.getSize() == 54);
        }
    }

    public boolean isKostiumView(org.bukkit.inventory.InventoryView view) {
        if (view == null) {
            return false;
        }
        try {
            String stripped = ChatColor.stripColor(view.getTitle());
            if ("Szafka".equalsIgnoreCase(stripped)
                || "Lista kostiumów".equalsIgnoreCase(stripped)
                || "Lista petów".equalsIgnoreCase(stripped)) {
                return true;
            }
            String ownTitle = ChatColor.stripColor(this.GUI_TITLE);
            return ownTitle != null && !ownTitle.isBlank() && ownTitle.equalsIgnoreCase(stripped);
        } catch (Exception exception) {
            return isKostiumGUI(view.getTopInventory());
        }
    }

    public ItemStack createActiveKostiumItem(Player player, KostiumyHandler.KostiumData data) {
        ItemStack item = data.displayItem() != null ? data.displayItem().clone() : new ItemStack(data.type());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        String storedDisplayName = this.kostiumyHandler.getDatabase().getActiveKostiumDisplayName(player.getUniqueId());
        String defaultDisplayName = data.displayName();
        String duration = getDurationFromDisplayName(storedDisplayName != null ? storedDisplayName : defaultDisplayName);
        String rawDisplayName = defaultDisplayName == null ? storedDisplayName : defaultDisplayName.replace("%czas%", duration == null || duration.isBlank() ? "NA ZAWSZE" : duration);
        meta.setDisplayName(dev.grzybalson.anarchiagg.szafa.pety.PetyVisualHandler.cleanPetDisplayName(rawDisplayName));
        meta.setLore(buildActiveLore(data.lore(), duration, this.kostiumyHandler.getDatabase().getKostiumExpiration(player.getUniqueId())));
        hideAdditionalTooltip(meta);
        item.setItemMeta(meta);
        String activeKostiumKey = this.kostiumyHandler.getDatabase().getActiveKostium(player.getUniqueId());
        String effectiveColor = KostiumyHandler.getEffectiveColor(activeKostiumKey, data.displayName(), data.color());
        if (item.getType().name().startsWith("LEATHER_") && effectiveColor != null && !effectiveColor.isEmpty()) {
            applyColorToLeatherArmor(item, effectiveColor);
        }
        return item;
    }

    public ItemStack createActivePetItem(Player player, PetyHandler.PetData data) {
        ItemStack baseItem = this.petyHandler.getPetItem(data.itemKey());
        ItemStack item = baseItem != null ? baseItem.clone() : new ItemStack(Material.STONE);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        String storedDisplayName = this.petyHandler.getDatabase().getActivePetDisplayName(player.getUniqueId());
        String defaultDisplayName = data.displayName();
        String rawDisplayName = storedDisplayName != null ? storedDisplayName : defaultDisplayName;
        meta.setDisplayName(dev.grzybalson.anarchiagg.szafa.pety.PetyVisualHandler.cleanPetDisplayName(rawDisplayName));
        meta.setLore(buildActiveLore(data.lore(), getDurationFromDisplayName(rawDisplayName), this.petyHandler.getDatabase().getPetExpiration(player.getUniqueId())));
        hideAdditionalTooltip(meta);
        item.setItemMeta(meta);
        if (item.getType().name().startsWith("LEATHER_") && data.color() != null && !data.color().isEmpty()) {
            applyColorToLeatherArmor(item, data.color());
        }
        return item;
    }

    private ArrayList<String> buildActiveLore(List<String> sourceLore, String duration, String expirationDate) {
        ArrayList<String> lore = new ArrayList<>();
        String safeDuration = duration == null || duration.isBlank() ? "NA ZAWSZE" : duration;
        for (String line : sourceLore == null ? List.<String>of() : sourceLore) {
            String formatted = line.replace("%czas%", safeDuration);
            if (formatted.contains("%uzycie%")) {
                formatted = formatted.replace("%uzycie%", expirationDate != null && !expirationDate.isEmpty() ? expirationDate : "NA ZAWSZE");
            }
            lore.add(color(formatted));
        }
        return lore;
    }

    public void applyColorToLeatherArmor(ItemStack item, String hexColor) {
        ItemMeta itemMeta = item.getItemMeta();
        if (itemMeta instanceof LeatherArmorMeta meta) {
            Color bukkitColor = hexToBukkitColor(hexColor);
            if (bukkitColor != null) {
                meta.setColor(bukkitColor);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_DESTROYS, ItemFlag.HIDE_PLACED_ON, ItemFlag.HIDE_DYE);
                item.setItemMeta(meta);
            }
        }
    }

    private Color hexToBukkitColor(String hex) {
        if (hex == null || hex.length() != 7 || !hex.startsWith("#")) {
            return null;
        }
        try {
            int red = Integer.parseInt(hex.substring(1, 3), 16);
            int green = Integer.parseInt(hex.substring(3, 5), 16);
            int blue = Integer.parseInt(hex.substring(5, 7), 16);
            return Color.fromRGB(red, green, blue);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String getDurationFromDisplayName(String displayName) {
        if (displayName == null) {
            return "NA ZAWSZE";
        }
        String stripped = ChatColor.stripColor(displayName);
        if (stripped == null) {
            return "NA ZAWSZE";
        }
        Matcher matcher = DURATION_EXTRACTOR.matcher(stripped);
        if (matcher.find()) {
            return matcher.group(2);
        }
        return "NA ZAWSZE";
    }

    public KostiumyHandler getKostiumyHandler() {
        return this.kostiumyHandler;
    }

    public PetyHandler getPetyHandler() {
        return this.petyHandler;
    }

    private String resolvePetKey(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return null;
        }
        if (this.petyHandler.getPety().containsKey(rawKey)) {
            return rawKey;
        }
        for (String key : this.petyHandler.getPety().keySet()) {
            if (key.equalsIgnoreCase(rawKey)) {
                return key;
            }
        }
        return null;
    }

    private String resolveKostiumKey(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return null;
        }
        if (this.kostiumyHandler.getKostiumy().containsKey(rawKey)) {
            return rawKey;
        }
        for (String key : this.kostiumyHandler.getKostiumy().keySet()) {
            if (key.equalsIgnoreCase(rawKey)) {
                return key;
            }
        }
        return null;
    }

    public PapugiHandler getPapugiHandler() {
        return this.papugiHandler;
    }

    private static String color(String text) {
        return dev.grzybalson.utils.ColorUtils.colorize(text == null ? "" : text);
    }

    private static String safe(String text) {
        return text == null ? "" : text;
    }

    static {
        ZWOJ_KOSTIUM_TYPE = Material.FLOWER_BANNER_PATTERN;
        ZWOJ_PET_TYPE = Material.FLOWER_BANNER_PATTERN;
        DURATION_EXTRACTOR = Pattern.compile(".*\\s(na\\s)(\\d+[dhm])$");
    }
}
