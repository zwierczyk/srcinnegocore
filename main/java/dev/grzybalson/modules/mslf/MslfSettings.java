package dev.grzybalson.modules.mslf;

import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ItemSerializer;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

public final class MslfSettings {

    public record ItemDef(
        boolean enabled,
        Material material,
        int customModelData,
        String name,
        List<String> lore,
        String serialized,
        List<String> blockedRegions,
        Integer cooldownSeconds
    ) {
        public List<String> blockedRegions() {
            return blockedRegions == null ? List.of() : blockedRegions;
        }
    }

    private final HollyCore plugin;
    private final File file;
    private YamlConfiguration yaml;
    private String guiTitle = "&8MSLF &7| &fLF Minestar";
    private List<String> blockedRegions = List.of("spawn");
    private String blockedRegionMessage = "&#FF0000✘ &8Ξ &cNie mozesz uzywac itemow MSLF na spawnie!";
    private int iceStickSeconds = 7;
    private int iceStickAmplifier = 0;
    private double iceStickRange = 3.0D;
    private boolean iceStickApplyToVictim = true;
    private boolean iceStickApplyToAttacker = false;
    private int sandCastleSeconds = 10;
    private String sandCastleSchematic = "zamek";
    private double sandCastleRadius = 3.0D;
    private double sandCastleMinDistance = 5.0D;
    private boolean sandCastleBossbarEnabled = true;
    private String sandCastleBossbarTitle = "&6Koniec zamku za: &e%czas%s";
    private String sandCastleBossbarColor = "YELLOW";
    private String sandCastleBossbarStyle = "SOLID";
    private int bombRadius = 3;
    private int deathFieldRadius = 3;
    private int deathFieldSeconds = 6;
    private double deathFieldDamage = 4.0D;
    private int deathFieldTickInterval = 10;
    private boolean deathFieldIgnoreArmor = true;
    private boolean sandCastleBlockPearls = true;
    private String sandCastlePearlMessage = "&#3B4F63&l✘&r &#FF2555Nie mozesz cykac perel w zamku!";
    private int cooldownReusableSeconds = 70;
    private int cooldownConsumableSeconds = 3;
    private String cooldownMessage = "&#3B4F63&l✘&r &#FF2555Musisz odczekać jeszcze %seconds%s!";
    private final Map<MslfItemType, ItemDef> items = new EnumMap<>(MslfItemType.class);
    /** Cache list blocked-regions — czytane per blok przy bombie/polu smierci, wiec bez re-parsowania YAML. */
    private final Map<MslfItemType, List<String>> itemBlockedCache = new EnumMap<>(MslfItemType.class);
    private List<String> blockedRegionsView = Collections.unmodifiableList(List.of("spawn"));

    public MslfSettings(HollyCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "addons/mslf/mslf.yml");
        reload();
    }

    public void reload() {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                //noinspection ResultOfMethodCallIgnored
                parent.mkdirs();
            }
            if (!file.exists()) {
                plugin.saveResource("addons/mslf/mslf.yml", false);
            }
            File icons = new File(plugin.getDataFolder(), "addons/mslf/icons-ref.yml");
            if (!icons.exists()) {
                try {
                    plugin.saveResource("addons/mslf/icons-ref.yml", false);
                } catch (IllegalArgumentException ignored) {
                }
            }
            yaml = YamlConfiguration.loadConfiguration(file);
            guiTitle = yaml.getString("gui-title", guiTitle);
            blockedRegions = readNameList(yaml, "blocked-regions");
            if (blockedRegions.isEmpty() && !yaml.contains("blocked-regions")) {
                blockedRegions = List.of("spawn");
            }
            blockedRegionsView = Collections.unmodifiableList(blockedRegions);
            blockedRegionMessage = yaml.getString("blocked-region-message", blockedRegionMessage);
            iceStickSeconds = Math.max(1, yaml.getInt("ice-stick-seconds", 7));
            iceStickAmplifier = Math.max(0, yaml.getInt("ice-stick-amplifier", 0));
            iceStickRange = Math.max(0.5D, yaml.getDouble("ice-stick-range", 3.0D));
            iceStickApplyToVictim = yaml.getBoolean("ice-stick-apply-to-victim", true);
            iceStickApplyToAttacker = false;
            sandCastleSeconds = Math.max(1, yaml.getInt("sand-castle-seconds", 10));
            sandCastleSchematic = yaml.getString("sand-castle-schematic", "zamek");
            double teleportRadius = 3.0D;
            if (yaml.contains("sand-castle-teleport-radius")) {
                teleportRadius = yaml.getDouble("sand-castle-teleport-radius");
            } else if (yaml.contains("sand-castle-radius")) {
                double legacyRadius = yaml.getDouble("sand-castle-radius");
                if (legacyRadius > 0.0D && legacyRadius <= 6.0D) {
                    teleportRadius = legacyRadius;
                }
            }
            sandCastleRadius = Math.max(0.5D, teleportRadius);
            sandCastleMinDistance = Math.max(0.0D, yaml.getDouble("sand-castle-min-distance", 5.0D));
            ConfigurationSection bossbar = yaml.getConfigurationSection("sand-castle-bossbar");
            if (bossbar != null) {
                sandCastleBossbarEnabled = bossbar.getBoolean("enabled", true);
                sandCastleBossbarTitle = bossbar.getString("title", sandCastleBossbarTitle);
                sandCastleBossbarColor = bossbar.getString("color", "YELLOW");
                sandCastleBossbarStyle = bossbar.getString("style", "SOLID");
            } else {
                sandCastleBossbarEnabled = yaml.getBoolean("sand-castle-bossbar-enabled", true);
                sandCastleBossbarTitle = yaml.getString("sand-castle-bossbar-title", sandCastleBossbarTitle);
                sandCastleBossbarColor = yaml.getString("sand-castle-bossbar-color", "YELLOW");
                sandCastleBossbarStyle = yaml.getString("sand-castle-bossbar-style", "SOLID");
            }
            bombRadius = Math.max(1, yaml.getInt("bomb-radius", 3));
            sandCastleBlockPearls = yaml.getBoolean("sand-castle-block-pearls", true);
            sandCastlePearlMessage = yaml.getString("sand-castle-pearl-message", sandCastlePearlMessage);
            deathFieldRadius = Math.max(0, yaml.getInt("death-field-radius", 3));
            deathFieldSeconds = Math.max(1, yaml.getInt("death-field-seconds", 6));
            deathFieldDamage = Math.max(0.1D, yaml.getDouble("death-field-damage", 4.0D));
            deathFieldTickInterval = Math.max(1, yaml.getInt("death-field-tick-interval", 10));
            deathFieldIgnoreArmor = yaml.getBoolean("death-field-ignore-armor", true);
            cooldownReusableSeconds = Math.max(0, yaml.getInt("cooldown-reusable-seconds", 70));
            cooldownConsumableSeconds = Math.max(0, yaml.getInt("cooldown-consumable-seconds", 3));
            cooldownMessage = yaml.getString("cooldown-message", cooldownMessage);
            if (patchIceStick() | patchSmoczyTotem() | patchConfigDefaults()) {
                yaml.save(file);
            }
            items.clear();
            itemBlockedCache.clear();
            ConfigurationSection section = yaml.getConfigurationSection("items");
            for (MslfItemType type : MslfItemType.values()) {
                items.put(type, readItem(section, type));
            }
            for (MslfItemType type : MslfItemType.values()) {
                ItemDef def = items.get(type);
                List<String> blocked = def == null ? new ArrayList<String>() : new ArrayList<>(def.blockedRegions());
                itemBlockedCache.put(type, Collections.unmodifiableList(blocked));
            }
        } catch (Exception exception) {
            plugin.getLogger().log(Level.WARNING, "[MSLF] Blad ladowania mslf.yml", exception);
        }
    }

    private ItemDef readItem(ConfigurationSection root, MslfItemType type) {
        ConfigurationSection sec = root != null ? root.getConfigurationSection(type.id()) : null;
        boolean enabled = sec == null || sec.getBoolean("enabled", true);
        Material material = type.defaultMaterial();
        if (sec != null) {
            String mat = sec.getString("material");
            if (mat != null && !mat.isBlank()) {
                try {
                    material = Material.valueOf(mat.trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        int cmd = sec != null ? sec.getInt("custom-model-data", type.defaultCmd()) : type.defaultCmd();
        String name = sec != null ? sec.getString("name", "&f" + type.id()) : "&f" + type.id();
        List<String> lore = sec != null ? sec.getStringList("lore") : List.of();
        if (lore == null) {
            lore = List.of();
        }
        String serialized = sec != null ? sec.getString("serialized", "") : "";
        List<String> itemBlocked = readNameList(sec, "blocked-regions");
        Integer cooldown = null;
        if (sec != null && sec.contains("cooldown-seconds")) {
            cooldown = Math.max(0, sec.getInt("cooldown-seconds"));
        }
        return new ItemDef(
            enabled,
            material,
            Math.max(0, cmd),
            name,
            new ArrayList<>(lore),
            serialized == null ? "" : serialized,
            new ArrayList<>(itemBlocked),
            cooldown
        );
    }

    private boolean patchIceStick() {
        boolean changed = false;
        if (yaml.getBoolean("ice-stick-apply-to-attacker", true)) {
            yaml.set("ice-stick-apply-to-attacker", false);
            changed = true;
        }
        List<String> lore = yaml.getStringList("items.lod_na_patyku.lore");
        boolean outdated = lore == null || lore.isEmpty();
        if (!outdated) {
            for (String line : lore) {
                if (line == null) {
                    continue;
                }
                String lower = line.toLowerCase(Locale.ROOT);
                if (lower.contains("lewit") || lower.contains("siebie")) {
                    outdated = true;
                    break;
                }
            }
        }
        if (outdated) {
            yaml.set("items.lod_na_patyku.lore", List.of(
                "",
                "&8× &7Po &cuderzeniu &7gracza zamrażasz",
                "&8× &7go na &f" + iceStickSeconds + " sekundy&7!",
                ""
            ));
            changed = true;
        }
        return changed;
    }

    private boolean patchSmoczyTotem() {
        List<String> lore = yaml.getStringList("items.smoczy_totem.lore");
        boolean outdated = lore == null || lore.isEmpty();
        if (!outdated) {
            for (String line : lore) {
                if (line == null) {
                    continue;
                }
                String lower = line.toLowerCase(Locale.ROOT);
                if (lower.contains("smierc") || lower.contains("śmierc") || lower.contains("smierci") || lower.contains("śmierci")) {
                    outdated = true;
                    break;
                }
            }
        }
        if (!outdated) {
            return false;
        }
        yaml.set("items.smoczy_totem.lore", List.of(
            "",
            "&8× &7Po &czbiciu totemu &7odzyskujesz",
            "&8× &7pełne &fHP &7oraz &esaturację&7!",
            "",
            "&8× &7Przedmiot jest &fjednorazowy&7!"
        ));
        return true;
    }

    private boolean patchConfigDefaults() {
        boolean changed = false;
        if (!yaml.contains("sand-castle-block-pearls")) {
            yaml.set("sand-castle-block-pearls", true);
            changed = true;
        }
        if (!yaml.contains("sand-castle-pearl-message")) {
            yaml.set("sand-castle-pearl-message", sandCastlePearlMessage);
            changed = true;
        }
        if (!yaml.contains("death-field-ignore-armor")) {
            yaml.set("death-field-ignore-armor", true);
            changed = true;
        }
        if (yaml.isSet("death-field-damage") && yaml.getDouble("death-field-damage") == 2.0D) {
            yaml.set("death-field-damage", 4.0D);
            deathFieldDamage = 4.0D;
            changed = true;
        }
        for (MslfItemType type : MslfItemType.values()) {
            String itemPath = "items." + type.id();
            if (!yaml.contains(itemPath)) {
                continue;
            }
            String blockedPath = itemPath + ".blocked-regions";
            if (!yaml.contains(blockedPath)) {
                yaml.set(blockedPath, new ArrayList<String>());
                changed = true;
            }
            if (!type.hasUseCooldown()) {
                continue;
            }
            String cooldownPath = itemPath + ".cooldown-seconds";
            if (!yaml.contains(cooldownPath)) {
                yaml.set(cooldownPath, type.consumableUse() ? cooldownConsumableSeconds : cooldownReusableSeconds);
                changed = true;
            }
        }
        return changed;
    }

    public String guiTitle() {
        return guiTitle;
    }

    public List<String> blockedRegions() {
        return blockedRegionsView;
    }

    private static List<String> readNameList(ConfigurationSection sec, String path) {
        List<String> out = new ArrayList<>();
        if (sec == null || path == null || path.isBlank()) {
            return out;
        }
        if (sec.isList(path)) {
            List<?> raw = sec.getList(path);
            if (raw != null) {
                for (Object value : raw) {
                    if (value != null && !value.toString().isBlank()) {
                        out.add(value.toString().trim());
                    }
                }
            }
            return out;
        }
        String asString = sec.getString(path, null);
        if (asString == null || asString.isBlank()) {
            return out;
        }
        for (String part : asString.split("[,;]+")) {
            if (!part.isBlank()) {
                out.add(part.trim());
            }
        }
        return out;
    }

    public List<String> itemBlockedRegions(MslfItemType type) {
        if (type == null) {
            return List.of();
        }
        List<String> cached = itemBlockedCache.get(type);
        return cached != null ? cached : List.of();
    }

    /**
     * Dodaje albo usuwa region z listy per-item. Zwraca true gdy region zostal dodany.
     */
    public boolean toggleItemBlockedRegion(MslfItemType type, String regionName) {
        if (type == null || regionName == null || regionName.isBlank() || yaml == null) {
            return false;
        }
        String key = regionName.trim();
        String path = "items." + type.id() + ".blocked-regions";
        List<String> list = new ArrayList<>(yaml.getStringList(path));
        boolean removed = list.removeIf(existing -> existing != null && existing.equalsIgnoreCase(key));
        if (!removed) {
            list.add(key);
        }
        yaml.set(path, list);
        itemBlockedCache.put(type, Collections.unmodifiableList(readNameList(yaml, path)));
        try {
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "[MSLF] Nie udalo sie zapisac blocked-regions", exception);
            return false;
        }
        ItemDef old = items.get(type);
        if (old != null) {
            items.put(type, new ItemDef(
                old.enabled(),
                old.material(),
                old.customModelData(),
                old.name(),
                old.lore(),
                old.serialized(),
                new ArrayList<>(list),
                old.cooldownSeconds()
            ));
        }
        return !removed;
    }

    public String blockedRegionMessage() {
        return blockedRegionMessage;
    }

    public int iceStickSeconds() {
        return iceStickSeconds;
    }

    public int iceStickAmplifier() {
        return iceStickAmplifier;
    }

    public double iceStickRange() {
        return iceStickRange;
    }

    public boolean iceStickApplyToVictim() {
        return iceStickApplyToVictim;
    }

    public boolean iceStickApplyToAttacker() {
        return iceStickApplyToAttacker;
    }

    public int sandCastleSeconds() {
        return sandCastleSeconds;
    }

    public String sandCastleSchematic() {
        return sandCastleSchematic;
    }

    public double sandCastleRadius() {
        return sandCastleRadius;
    }

    public double sandCastleMinDistance() {
        return sandCastleMinDistance;
    }

    public boolean sandCastleBossbarEnabled() {
        return sandCastleBossbarEnabled;
    }

    public String sandCastleBossbarTitle() {
        return sandCastleBossbarTitle;
    }

    public String sandCastleBossbarColor() {
        return sandCastleBossbarColor;
    }

    public String sandCastleBossbarStyle() {
        return sandCastleBossbarStyle;
    }

    public boolean sandCastleBlockPearls() {
        return sandCastleBlockPearls;
    }

    public String sandCastlePearlMessage() {
        return sandCastlePearlMessage;
    }

    public int bombRadius() {
        return bombRadius;
    }

    public int deathFieldRadius() {
        return deathFieldRadius;
    }

    public int deathFieldSeconds() {
        return deathFieldSeconds;
    }

    public double deathFieldDamage() {
        return deathFieldDamage;
    }

    public int deathFieldTickInterval() {
        return deathFieldTickInterval;
    }

    public boolean deathFieldIgnoreArmor() {
        return deathFieldIgnoreArmor;
    }

    public int cooldownSeconds(MslfItemType type) {
        if (type == null || !type.hasUseCooldown()) {
            return 0;
        }
        ItemDef def = items.get(type);
        if (def != null && def.cooldownSeconds() != null) {
            return Math.max(0, def.cooldownSeconds());
        }
        return type.consumableUse() ? cooldownConsumableSeconds : cooldownReusableSeconds;
    }

    public String cooldownMessage() {
        return cooldownMessage;
    }

    public ItemDef item(MslfItemType type) {
        return items.get(type);
    }

    public Map<MslfItemType, ItemDef> items() {
        return Collections.unmodifiableMap(items);
    }

    /**
     * Zapisuje wygląd itemu z ręki do mslf.yml (material, CMD, name, lore + serialized).
     */
    public boolean setItemFromHand(MslfItemType type, ItemStack hand) {
        if (type == null || hand == null || hand.getType() == Material.AIR || yaml == null) {
            return false;
        }
        ItemStack clone = hand.clone();
        clone.setAmount(1);
        ItemMeta meta = clone.getItemMeta();
        String path = "items." + type.id();
        yaml.set(path + ".enabled", true);
        yaml.set(path + ".material", clone.getType().name());
        int cmd = meta != null && meta.hasCustomModelData() ? meta.getCustomModelData() : type.defaultCmd();
        yaml.set(path + ".custom-model-data", cmd);
        if (meta != null && meta.hasDisplayName()) {
            yaml.set(path + ".name", meta.getDisplayName().replace(ChatColor.COLOR_CHAR, '&'));
        }
        if (meta != null && meta.hasLore() && meta.getLore() != null) {
            List<String> lore = new ArrayList<>();
            for (String line : meta.getLore()) {
                lore.add(line == null ? "" : line.replace(ChatColor.COLOR_CHAR, '&'));
            }
            yaml.set(path + ".lore", lore);
        }
        yaml.set(path + ".serialized", ItemSerializer.serializeItem(clone));
        try {
            yaml.save(file);
            reload();
            return true;
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "[MSLF] Nie udalo sie zapisac setitem", exception);
            return false;
        }
    }
}
