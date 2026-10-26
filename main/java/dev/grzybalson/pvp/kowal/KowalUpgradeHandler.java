package dev.grzybalson.pvp.kowal;

import dev.grzybalson.pvp.kowal.KowalGUIHandler;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.RegisteredServiceProvider;

public class KowalUpgradeHandler {
    private final FileConfiguration config;
    private final File configFile;
    private final String LORE_PHRASE = "odporno\u015bci na obra\u017cenia";
    private final Pattern LORE_REGEX = Pattern.compile("(?i)\\+([0-9.]+)\\s*odporno\u015bci na obra\u017cenia");
    private static Economy econ = null;
    private static NamespacedKey UPGRADE_LEVEL_KEY;
    private static NamespacedKey UPGRADE_VALUE_KEY;
    public static NamespacedKey KOWAL_STONE_KEY;

    public KowalUpgradeHandler(FileConfiguration config, File configFile) {
        this.config = config;
        this.configFile = configFile;
        this.ensureDefaultConfig();
        if (UPGRADE_LEVEL_KEY == null) {
            UPGRADE_LEVEL_KEY = new NamespacedKey("anarchia_addons", "kowal_level");
            UPGRADE_VALUE_KEY = new NamespacedKey("anarchia_addons", "kowal_value");
            KOWAL_STONE_KEY = new NamespacedKey("anarchia_addons", "kamien_kowalski");
        }
    }

    public void reloadConfig() {
        try {
            if (configFile != null && configFile.exists()) {
                YamlConfiguration loaded = YamlConfiguration.loadConfiguration(configFile);
                for (String key : loaded.getKeys(true)) {
                    config.set(key, loaded.get(key));
                }
            }
        } catch (Exception e) {
            Bukkit.getLogger().warning("[Kowal] Błąd podczas przeładowywania kowal.yml: " + e.getMessage());
        }
    }

    private static Economy getEconomy() {
        if (econ != null) {
            return econ;
        }
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            Bukkit.getLogger().warning("[Kowal] Vault plugin not found!");
            return null;
        }
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            Bukkit.getLogger().warning("[Kowal] No economy provider registered with Vault!");
            return null;
        }
        econ = rsp.getProvider();
        return econ;
    }

    private boolean checkVaultBalance(Player player, int cost) {
        Economy economy = getEconomy();
        if (economy == null) {
            Bukkit.getLogger().warning("[Kowal] checkVaultBalance: Economy is null!");
            return false;
        }
        return economy.has((OfflinePlayer)player, (double)cost);
    }

    private boolean consumeVaultBalance(Player player, int cost) {
        Economy economy = getEconomy();
        if (economy == null) {
            Bukkit.getLogger().warning("[Kowal] consumeVaultBalance: Economy is null!");
            return false;
        }
        return economy.withdrawPlayer((OfflinePlayer)player, (double)cost).transactionSuccess();
    }

    private void ensureDefaultConfig() {
        if (!this.config.contains("kamien-kowalski")) {
            this.config.set("kamien-kowalski", "\u00a75Kamie\u0144 kowalski");
        }
        if (!this.config.contains("kamien-kowalski-material")) {
            this.config.set("kamien-kowalski-material", Material.REDSTONE_TORCH.name());
        }
        if (!this.config.contains("ulepszenia.1")) {
            this.config.set("ulepszenia.1.value", 0.5);
            this.config.set("ulepszenia.1.chance", 100.0);
            this.config.set("ulepszenia.1.cost", 500);
            this.config.set("ulepszenia.1.materials", Arrays.asList("COAL:16"));
        }
        if (!this.config.contains("gui-messages.title-main")) {
            this.config.set("gui-messages.title-main", "\u00a72Kowal");
            this.config.set("gui-messages.title-confirm", "\u00a72Czy chcesz kontynuowa\u0107?");
            this.config.set("gui-messages.cancel.slot", 11);
            this.config.set("gui-messages.cancel.material", Material.RED_DYE.name());
            this.config.set("gui-messages.cancel.name", "\u00a7cAnuluj");
            this.config.set("gui-messages.cancel.lore", Arrays.asList("\u00a7cNie chc\u0119 ryzykowa\u0107. Wol\u0119 zosta\u0107", "\u00a7cprzy aktualnym poziomie ulepszenia."));
            this.config.set("gui-messages.upgrade-button.slot", 15);
            this.config.set("gui-messages.upgrade-button.material", Material.LIME_DYE.name());
            this.config.set("gui-messages.upgrade-button.material-max-level", Material.BARRIER.name());
            this.config.set("gui-messages.upgrade-button.name", "\u00a7aZwi\u0119ksz poziom");
            this.config.set("gui-messages.upgrade-button.name-max-level", "\u00a74Maksymalny poziom!");
            this.config.set("gui-messages.upgrade-button.lore-prefix-upgrade", "\u00a77Ulepszenie: \u00a7a+<CURRENT_LEVEL> \u00a78\u00ab\u00a72\u279c \u00a7a+<NEXT_LEVEL>");
            this.config.set("gui-messages.upgrade-button.lore-prefix-requirements", "\u00a77Wymagane ulepszacze:");
            this.config.set("gui-messages.upgrade-button.lore-suffix-missing", "\u00a7cUzbi\u00e9raj wymagane przedmioty!");
            this.config.set("gui-messages.upgrade-button.lore-suffix-ready", "\u00a7eKliknij, aby ulepszy\u0107!");
            this.config.set("gui-messages.upgrade-button.lore-max-level", Arrays.asList("\u00a7cNie mo\u017cna ulepszy\u0107 tego przedmiotu dalej."));
            this.config.set("gui-messages.toggle-stone.slot", 22);
            this.config.set("gui-messages.toggle-stone.stone-active.material", Material.REDSTONE_TORCH.name());
            this.config.set("gui-messages.toggle-stone.stone-active.name", "\u00a75Ulepszanie kamieniem kowalskim");
            this.config.set("gui-messages.toggle-stone.stone-active.lore", Arrays.asList("\u00a77Gdy ulepszasz przedmiot Kamieniem Kowalskim", "\u00a77masz pewno\u015b\u0107, \u017ce gdy ulepszenie si\u0119", "\u00a77nie powiedzie poziom przedmiotu nie", "\u00a77zostanie obni\u017cony", "", "\u00a7eKliknij, aby prze\u0142\u0105czy\u0107!"));
            this.config.set("gui-messages.toggle-stone.metal-active.material", Material.LEVER.name());
            this.config.set("gui-messages.toggle-stone.metal-active.name", "\u00a79Ulepszasz metalem!");
            this.config.set("gui-messages.toggle-stone.metal-active.lore", Arrays.asList("\u00a77Ulepszasz przedmiot specjalnym", "\u00a77metalem, kt\u00f3ry zabezpiecza przed", "\u00a77obni\u017ceniem poziomu przedmiotu.", "", "\u00a7eKliknij, aby prze\u0142\u0105czy\u0107!"));
            this.config.set("gui-messages.toggle-stone.stone-not-owned", "\u00a7cNie posiadasz Kamienia Kowalskiego w ekwipunku!");
            this.config.set("gui-messages.toggle-stone.stone-level-limit", "\u00a7cKamie\u0144 Kowalski jest dost\u0119pny do u\u017cycia tylko od poziomu ulepszenia +1!");
            this.config.set("gui-messages.confirm-no.slot", 0);
            this.config.set("gui-messages.confirm-no.material", Material.RED_DYE.name());
            this.config.set("gui-messages.confirm-no.name", "\u00a7cNIE");
            this.config.set("gui-messages.confirm-no.lore", Arrays.asList("\u00a7cNie chc\u0119 ryzykowa\u0107. Wol\u0119 zosta\u0107", "\u00a7cprzy aktualnym poziomie ulepszenia."));
            this.config.set("gui-messages.confirm-yes.slot", 4);
            this.config.set("gui-messages.confirm-yes.material", Material.LIME_DYE.name());
            this.config.set("gui-messages.confirm-yes.name", "\u00a7aTAK");
            this.config.set("gui-messages.confirm-yes.lore-chance-line", "\u00a7aSzansa na ulepszenie: \u00a7e<CHANCE>%%");
            this.config.set("gui-messages.confirm-yes.lore-risk", Arrays.asList("\u00a7cJestem \u015bwiadomy, \u017ce przy nieudanej", "\u00a7cpr\u00f3bie ulepszenia poziom przedmiotu zostanie", "\u00a7cobni\u017cone o jeden."));
            this.config.set("gui-messages.confirm-yes.lore-safe", Arrays.asList("\u00a77Te ulepszenie jest \u00a7ezabezpieczone", "\u00a77przed obni\u017ceniem poziomu przedmiotu."));
        }
        try {
            this.config.save(this.configFile);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getGuiTitleMain() {
        return KowalGUIHandler.colorize(this.config.getString("gui-messages.title-main", "\u00a72Kowal"));
    }

    public String getGuiTitleConfirm() {
        return KowalGUIHandler.colorize(this.config.getString("gui-messages.title-confirm", "\u00a72Czy chcesz kontynuowa\u0107?"));
    }

    public Material getGuiMaterial(String path, Material defaultMaterial) {
        String materialName = this.config.getString(path);
        if (materialName == null) {
            return defaultMaterial;
        }
        try {
            return Material.valueOf(materialName);
        }
        catch (IllegalArgumentException e) {
            return defaultMaterial;
        }
    }

    public String getGuiName(String path, String defaultName) {
        return KowalGUIHandler.colorize(this.config.getString(path, defaultName));
    }

    public List<String> getGuiLore(String path, List<String> defaultLore) {
        List<String> lore = this.config.getStringList(path);
        if (lore.isEmpty()) {
            return defaultLore.stream().map(KowalGUIHandler::colorize).collect(Collectors.toList());
        }
        return lore.stream().map(KowalGUIHandler::colorize).collect(Collectors.toList());
    }

    public int getGuiSlot(String path, int defaultSlot) {
        return this.config.getInt(path, defaultSlot);
    }

    public int getCurrentUpgradeLevel(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return 0;
        }
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (pdc.has(UPGRADE_LEVEL_KEY, PersistentDataType.INTEGER)) {
            return pdc.get(UPGRADE_LEVEL_KEY, PersistentDataType.INTEGER);
        }
        if (!meta.hasLore()) {
            return 0;
        }
        List<String> lore = meta.getLore();
        if (lore == null) {
            return 0;
        }
        for (String line : lore) {
            String strippedLine = ChatColor.stripColor(KowalGUIHandler.colorize(line));
            Matcher matcher = this.LORE_REGEX.matcher(strippedLine);
            if (!matcher.find()) continue;
            try {
                double value = Double.parseDouble(matcher.group(1));
                return this.getLevelByValue(value);
            }
            catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    public double getItemUpgradeValue(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return 0.0;
        }
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (pdc.has(UPGRADE_VALUE_KEY, PersistentDataType.DOUBLE)) {
            return pdc.get(UPGRADE_VALUE_KEY, PersistentDataType.DOUBLE);
        }
        if (!meta.hasLore()) {
            return 0.0;
        }
        List<String> lore = meta.getLore();
        if (lore == null) {
            return 0.0;
        }
        for (String line_2 : lore) {
            String strippedLine = ChatColor.stripColor(KowalGUIHandler.colorize(line_2));
            Matcher matcher = this.LORE_REGEX.matcher(strippedLine);
            if (!matcher.find()) continue;
            try {
                return Double.parseDouble(matcher.group(1));
            }
            catch (NumberFormatException e) {
                return 0.0;
            }
        }
        return 0.0;
    }

    private int getLevelByValue(double value) {
        ConfigurationSection upgrades = this.config.getConfigurationSection("ulepszenia");
        if (upgrades == null) {
            return 0;
        }
        String formattedValue = String.format(Locale.US, "%.1f", value);
        for (String key : upgrades.getKeys(false)) {
            String configFormattedValue = String.format(Locale.US, "%.1f", upgrades.getDouble(key + ".value"));
            if (!configFormattedValue.equals(formattedValue)) continue;
            try {
                return Integer.parseInt(key);
            }
            catch (NumberFormatException numberFormatException) {
            }
        }
        return 0;
    }

    public int getNextUpgradeLevel(int currentLevel) {
        ConfigurationSection upgrades = this.config.getConfigurationSection("ulepszenia");
        if (upgrades == null) {
            return -1;
        }
        int nextLevel = currentLevel + 1;
        return upgrades.contains(String.valueOf(nextLevel)) ? nextLevel : -1;
    }

    public UpgradeInfo getUpgradeInfo(int level) {
        ConfigurationSection upgrades = this.config.getConfigurationSection("ulepszenia." + level);
        if (upgrades == null) {
            return null;
        }
        double value = upgrades.getDouble("value");
        double chance = upgrades.getDouble("chance");
        int cost = upgrades.getInt("cost");
        List<String> materials = upgrades.getStringList("materials");
        return new UpgradeInfo(value, chance, cost, materials);
    }

    public boolean canUpgrade(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        String typeName = item.getType().name();
        return !(!typeName.contains("DIAMOND_") && !typeName.contains("NETHERITE_") || !typeName.contains("_BOOTS") && !typeName.contains("_LEGGINGS") && !typeName.contains("_CHESTPLATE") && !typeName.contains("_HELMET"));
    }

    private int getInventoryAmount(Player player, Material material) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType() != material) continue;
            count += item.getAmount();
        }
        return count;
    }

    public Map<String, Boolean> checkRequirementsStatus(Player player, int nextLevel) {
        HashMap<String, Boolean> status = new HashMap<String, Boolean>();
        UpgradeInfo info = this.getUpgradeInfo(nextLevel);
        if (info == null) {
            return status;
        }
        boolean hasMoney = this.checkVaultBalance(player, info.getCost());
        status.put("money", hasMoney);
        for (String materialEntry : info.getMaterials()) {
            String[] parts = materialEntry.split(":");
            Material material = Material.matchMaterial(parts[0]);
            int amount = Integer.parseInt(parts[1]);
            boolean hasMaterial = material != null && this.getInventoryAmount(player, material) >= amount;
            status.put(materialEntry, hasMaterial);
        }
        return status;
    }

    public boolean checkMaterials(Player player, int nextLevel) {
        return !this.checkRequirementsStatus(player, nextLevel).containsValue(false);
    }

    private void consumeItem(Player player, Material material, int amount) {
        int needed = amount;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length && needed > 0; ++i) {
            ItemStack item = contents[i];
            if (item == null || item.getType() != material) continue;
            if (item.getAmount() >= needed) {
                item.setAmount(item.getAmount() - needed);
                needed = 0;
                continue;
            }
            needed -= item.getAmount();
            contents[i] = null;
        }
        player.getInventory().setStorageContents(contents);
    }

    public boolean consumeMaterials(Player player, int nextLevel) {
        if (!this.checkMaterials(player, nextLevel)) {
            return false;
        }
        UpgradeInfo info = this.getUpgradeInfo(nextLevel);
        if (info == null) {
            return false;
        }
        if (!this.consumeVaultBalance(player, info.getCost())) {
            player.sendMessage(String.valueOf(ChatColor.RED) + "B\u0142\u0105d: Nie uda\u0142o si\u0119 pobra\u0107 \u015brodk\u00f3w z konta Vault.");
            return false;
        }
        for (String materialEntry_2 : info.getMaterials()) {
            String[] parts = materialEntry_2.split(":");
            Material material = Material.matchMaterial(parts[0]);
            int amount = Integer.parseInt(parts[1]);
            if (material == null) continue;
            this.consumeItem(player, material, amount);
        }
        player.updateInventory();
        return true;
    }

    public boolean attemptUpgrade(Player player, ItemStack item, int nextLevel, boolean useKowalStone) {
        boolean success;
        UpgradeInfo info = this.getUpgradeInfo(nextLevel);
        if (info == null) {
            return false;
        }
        double chance = info.getChance();
        boolean bl = success = new Random().nextDouble() * 100.0 < chance;
        if (success) {
            this.applyUpgrade(item, nextLevel, info.getValue());
            this.updateItemName(item, nextLevel);
            return true;
        }
        if (!useKowalStone) {
            this.downgradeItem(item);
            this.updateItemName(item, this.getCurrentUpgradeLevel(item));
        }
        return false;
    }

    private void updateItemName(ItemStack item, int level) {
        Object namePart;
        String levelColor;
        Object prefix;
        boolean isNetheriteII;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        Material type = item.getType();
        String typeName = type.name();
        String originalName = meta.hasDisplayName() ? KowalGUIHandler.colorize(meta.getDisplayName()) : typeName;
        boolean bl = isNetheriteII = typeName.contains("NETHERITE_") && originalName.contains("II");
        if (typeName.contains("DIAMOND_")) {
            prefix = "\u00a73";
            levelColor = "\u00a7b\u00a7l+";
        } else if (typeName.contains("NETHERITE_")) {
            prefix = "\u00a74";
            levelColor = "\u00a7c\u00a7l+";
        } else {
            if (level == 0) {
                meta.setDisplayName(null);
            }
            item.setItemMeta(meta);
            return;
        }
        if (typeName.contains("_HELMET")) {
            namePart = " he\u0142m";
            prefix = (String)prefix + (typeName.contains("NETHERITE_") ? "Anarchiczny" : "Diamentowy");
        } else if (typeName.contains("_CHESTPLATE")) {
            namePart = " klata";
            prefix = (String)prefix + (typeName.contains("NETHERITE_") ? "Anarchiczna" : "Diamentowa");
        } else if (typeName.contains("_LEGGINGS")) {
            namePart = " spodnie";
            prefix = (String)prefix + (typeName.contains("NETHERITE_") ? "Anarchiczne" : "Diamentowe");
        } else if (typeName.contains("_BOOTS")) {
            namePart = " buty";
            prefix = (String)prefix + (typeName.contains("NETHERITE_") ? "Anarchiczne" : "Diamentowe");
        } else {
            return;
        }
        if (isNetheriteII) {
            namePart = (String)namePart + " II";
        }
        if (level == 0) {
            meta.setDisplayName(KowalGUIHandler.colorize((String)prefix + (String)namePart));
        } else {
            String newName = (String)prefix + (String)namePart + " " + levelColor + level;
            meta.setDisplayName(KowalGUIHandler.colorize(newName));
        }
        item.setItemMeta(meta);
    }

    private void applyUpgrade(ItemStack item, int newLevel, double newValue) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(UPGRADE_LEVEL_KEY, PersistentDataType.INTEGER, newLevel);
        pdc.set(UPGRADE_VALUE_KEY, PersistentDataType.DOUBLE, newValue);
        ArrayList<String> currentLore = new ArrayList<String>();
        if (meta.hasLore() && meta.getLore() != null) {
            currentLore.addAll(meta.getLore());
        }
        Pattern checkPattern = Pattern.compile("(?i)^.*" + "odporno\u015bci na obra\u017cenia".replace(" ", "\\s*") + "$");
        ArrayList<String> newLore = new ArrayList<String>();
        boolean wasUpgradeLineFound = false;
        for (String line_3 : currentLore) {
            String strippedLine = ChatColor.stripColor(KowalGUIHandler.colorize(line_3));
            if (checkPattern.matcher(strippedLine).find()) {
                wasUpgradeLineFound = true;
                continue;
            }
            newLore.add(line_3);
        }
        if (!wasUpgradeLineFound && !newLore.isEmpty() && ((String)newLore.get(newLore.size() - 1)).trim().length() > 0) {
            newLore.add("");
        }
        String newLoreLine = "\u00a7b+" + String.format(Locale.US, "%.1f", newValue) + " odporno\u015bci na obra\u017cenia";
        newLore.add(newLoreLine);
        meta.setLore(newLore);
        item.setItemMeta(meta);
    }

    private void downgradeItem(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        int currentLevel = pdc.has(UPGRADE_LEVEL_KEY, PersistentDataType.INTEGER) ? pdc.get(UPGRADE_LEVEL_KEY, PersistentDataType.INTEGER) : 0;
        int newLevel = Math.max(0, currentLevel - 1);
        if (newLevel == 0) {
            pdc.remove(UPGRADE_LEVEL_KEY);
            pdc.remove(UPGRADE_VALUE_KEY);
        } else {
            UpgradeInfo prevInfo = this.getUpgradeInfo(newLevel);
            if (prevInfo != null) {
                pdc.set(UPGRADE_LEVEL_KEY, PersistentDataType.INTEGER, newLevel);
                pdc.set(UPGRADE_VALUE_KEY, PersistentDataType.DOUBLE, prevInfo.getValue());
            }
        }
        List<String> currentLore = meta.hasLore() && meta.getLore() != null ? new ArrayList<String>(meta.getLore()) : new ArrayList<String>();
        Pattern checkPattern = Pattern.compile("(?i)^.*" + "odporno\u015bci na obra\u017cenia".replace(" ", "\\s*") + "$");
        ArrayList<String> newLore = new ArrayList<String>();
        for (String line_4 : currentLore) {
            String strippedLine = ChatColor.stripColor(KowalGUIHandler.colorize(line_4));
            if (checkPattern.matcher(strippedLine).find()) continue;
            newLore.add(line_4);
        }
        if (newLevel > 0) {
            UpgradeInfo prevInfo = this.getUpgradeInfo(newLevel);
            if (prevInfo != null) {
                if (!newLore.isEmpty() && !((String)newLore.get(newLore.size() - 1)).trim().isEmpty()) {
                    newLore.add("");
                }
                newLore.add("\u00a7b+" + String.format(Locale.US, "%.1f", prevInfo.getValue()) + " odporno\u015bci na obra\u017cenia");
            }
        } else {
            if (!newLore.isEmpty() && ((String)newLore.get(newLore.size() - 1)).trim().isEmpty()) {
                newLore.remove(newLore.size() - 1);
            }
        }
        meta.setLore(newLore);
        item.setItemMeta(meta);
    }

    public String getKowalStoneName() {
        return KowalGUIHandler.colorize(this.config.getString("kamien-kowalski", "\u00a75Kamie\u0144 kowalski"));
    }

    public Material getKowalStoneMaterial() {
        try {
            return Material.matchMaterial(this.config.getString("kamien-kowalski-material", Material.REDSTONE_TORCH.name()));
        }
        catch (Exception e) {
            return Material.REDSTONE_TORCH;
        }
    }


    public ItemStack createKowalStone(int amount) {
        Material mat = getKowalStoneMaterial();
        if (mat == null) {
            mat = Material.REDSTONE_TORCH;
        }
        ItemStack item = new ItemStack(mat, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(getKowalStoneName());
            List<String> lore = this.config.getStringList("kamien-kowalski-lore");
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(lore.stream().map(KowalGUIHandler::colorize).collect(Collectors.toList()));
            }
            if (KOWAL_STONE_KEY != null) {
                meta.getPersistentDataContainer().set(KOWAL_STONE_KEY, PersistentDataType.BYTE, (byte) 1);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isKowalStone(ItemStack item) {
        if (item == null || item.getType() != this.getKowalStoneMaterial() || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (KOWAL_STONE_KEY != null && (pdc.has(KOWAL_STONE_KEY, PersistentDataType.BYTE) ||
            pdc.has(KOWAL_STONE_KEY, PersistentDataType.INTEGER) ||
            pdc.has(KOWAL_STONE_KEY, PersistentDataType.STRING))) {
            return true;
        }
        return meta.hasDisplayName() && this.getKowalStoneName().equals(meta.getDisplayName());
    }

    public boolean hasKowalStone(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (this.isKowalStone(item)) {
                return true;
            }
        }
        return false;
    }

    public boolean consumeKowalStone(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (!this.isKowalStone(item)) {
                continue;
            }
            int amount = item.getAmount();
            if (amount > 1) {
                item.setAmount(amount - 1);
            } else {
                player.getInventory().removeItem(item);
            }
            player.updateInventory();
            return true;
        }
        return false;
    }


    public static class UpgradeInfo {
        private final double value;
        private final double chance;
        private final int cost;
        private final List<String> materials;

        public UpgradeInfo(double value, double chance, int cost, List<String> materials) {
            this.value = value;
            this.chance = chance;
            this.cost = cost;
            this.materials = materials;
        }

        public double getValue() {
            return this.value;
        }

        public double getChance() {
            return this.chance;
        }

        public int getCost() {
            return this.cost;
        }

        public List<String> getMaterials() {
            return this.materials;
        }
    }
}
