package dev.grzybalson.modules.boss;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.HollyDataPaths;
import dev.grzybalson.utils.ConfigItemStackReader;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class BossConfig {

    private static final String RESOURCE = "addons/boss/boss.yml";

    private final HollyCore plugin;
    private final File file;
    private YamlConfiguration yaml;

    private static final List<String> DEFAULT_SIDEBAR_LINES = List.of(
        "",
        " &8▪ &7Boss: &f{BOSS}",
        " &c❤ &7Życie: &#C80B0B❤{HEALTH}&7/&4{MAX_HEALTH}",
        " &8▪ &7Lokalizacja: &#FFFB7A{LOCATION}",
        "",
        " &8↓ &#F4BC54&lᴛ&#FAA449&lᴏ&#FF8C3D&lᴘ&#FB9C45&lᴋ&#F8AC4C&lᴀ &#FAA449&lᴏ&#FF8C3D&lʙ&#FB9C45&lʀ&#F8AC4C&lᴀ&#F4BC54&lᴢ&#FAA449&lᴇ&#FF8C3D&lɴ&r &8↓",
        "",
        " &#FF4747①. &#FF4747{TOP1}",
        " &#FF8347②. &#FF8347{TOP2}",
        " &#FFCD47③. &#FFCD47{TOP3}",
        "",
        " &8▪ &fZadałeś &#C80B0B❤{DAMAGE}",
        " &8▪ &fPozycja &#00B7FF#{POSITION}",
        ""
    );

    private String hoglinName = "&#FF9B00&lHoglin";
    private List<String> hoglinHours = new ArrayList<>(List.of("18:00"));
    private int hoglinMaxHealth = 100;
    private String hoglinLocation = "";
    private BossRewards hoglinRewards = emptyRewards();
    private String hoglinCountdownTitle = "&fBoss &cHoglin &fpojawi się za &e{TIME}";
    private String hoglinCountdownColor = "RED";
    private String hoglinSpawnTitle = "";
    private String hoglinSpawnSubtitle = "";
    private String hoglinNametag = "{NAME}&r &8[&c{HEALTH}❤&8/&4{MAX_HEALTH}❤&8]";

    private final Map<String, BossDefinition> bosses = new LinkedHashMap<>();
    private String spawnTitle = "&8♮ &#CD2121⚔ &8♮";
    private String spawnSubtitle = "&fPojawił się {NAME} &r&fna &#CD2121Strefie Eventowej";
    private int spawnTitleFadeIn = 250;
    private int spawnTitleStay = 3000;
    private int spawnTitleFadeOut = 500;
    private String noBossPlaceholder = "&7Brak";
    private String sidebarTitle = "&8x &c&lBOSS &8x";
    private List<String> sidebarLines = new ArrayList<>(DEFAULT_SIDEBAR_LINES);
    private String sidebarEmptyRank = "Nikt";
    private String sidebarRankFormat = "{PLAYER} &8- &#C80B0B❤{DAMAGE}";

    public BossConfig(HollyCore plugin) {
        this.plugin = plugin;
        this.file = HollyDataPaths.getRootFile(plugin, RESOURCE);
        reload();
    }

    public void reload() {
        HollyDataPaths.ensureResource(plugin, RESOURCE);
        yaml = YamlConfiguration.loadConfiguration(file);
        hoglinName = yaml.getString("hoglin.name", hoglinName);
        hoglinHours = new ArrayList<>(yaml.getStringList("hoglin.hours"));
        if (hoglinHours.isEmpty()) {
            hoglinHours.add("18:00");
        }
        hoglinMaxHealth = yaml.getInt("hoglin.max-health", 100);
        hoglinLocation = yaml.getString("hoglin.location", "");
        hoglinRewards = readRewards("hoglin");
        hoglinCountdownTitle = yaml.getString("hoglin.countdown-title", hoglinCountdownTitle);
        hoglinCountdownColor = yaml.getString("hoglin.countdown-color", "RED");
        hoglinSpawnTitle = yaml.getString("hoglin.spawn-title", "");
        hoglinSpawnSubtitle = yaml.getString("hoglin.spawn-subtitle", "");
        hoglinNametag = yaml.getString("hoglin.nametag", hoglinNametag);

        bosses.clear();
        ConfigurationSection section = yaml.getConfigurationSection("bosses");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection bossSection = section.getConfigurationSection(key);
                if (bossSection == null) {
                    continue;
                }
                EntityType type;
                try {
                    type = EntityType.valueOf(bossSection.getString("entity-type", key).toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ignored) {
                    type = EntityType.VINDICATOR;
                }
                bosses.put(key.toUpperCase(Locale.ROOT), new BossDefinition(
                    key.toUpperCase(Locale.ROOT),
                    bossSection.getString("name", "&cBoss"),
                    type,
                    new ArrayList<>(bossSection.getStringList("hours")),
                    bossSection.getInt("max-health", 200),
                    bossSection.getString("location", ""),
                    readRewards("bosses." + key),
                    bossSection.getString("countdown-title", "&fBoss pojawi się za &e{TIME}"),
                    bossSection.getString("countdown-color", "GREEN"),
                    bossSection.getString("spawn-title", ""),
                    bossSection.getString("spawn-subtitle", "")
                ));
            }
        }

        spawnTitle = yaml.getString("spawn-title", spawnTitle);
        spawnSubtitle = yaml.getString("spawn-subtitle", spawnSubtitle);
        spawnTitleFadeIn = Math.max(0, yaml.getInt("spawn-title-fade-in", spawnTitleFadeIn));
        spawnTitleStay = Math.max(0, yaml.getInt("spawn-title-stay", spawnTitleStay));
        spawnTitleFadeOut = Math.max(0, yaml.getInt("spawn-title-fade-out", spawnTitleFadeOut));
        noBossPlaceholder = yaml.getString("no-boss-placeholder", noBossPlaceholder);
        sidebarTitle = yaml.getString("sidebar.title", sidebarTitle);
        List<String> loadedSidebar = yaml.getStringList("sidebar.lines");
        sidebarLines = loadedSidebar.isEmpty() ? new ArrayList<>(DEFAULT_SIDEBAR_LINES) : new ArrayList<>(loadedSidebar);
        sidebarEmptyRank = yaml.getString("sidebar.empty-rank", sidebarEmptyRank);
        sidebarRankFormat = yaml.getString("sidebar.rank-format", sidebarRankFormat);
    }

    public void save() {
        yaml.set("hoglin.location", hoglinLocation);
        for (BossDefinition boss : bosses.values()) {
            yaml.set("bosses." + boss.id() + ".location", boss.locationRaw());
        }

        try {
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Nie udalo sie zapisac boss.yml: " + exception.getMessage());
        }
    }

    public String getHoglinName() {
        return hoglinName;
    }

    public List<String> getHoglinHours() {
        return hoglinHours;
    }

    public int getHoglinMaxHealth() {
        return hoglinMaxHealth;
    }

    public String getHoglinLocationRaw() {
        return hoglinLocation;
    }

    public Location getHoglinLocation() {
        return deserialize(hoglinLocation);
    }

    public void setHoglinLocation(Location location) {
        this.hoglinLocation = serialize(location);
    }

    public BossRewards getHoglinRewardPack() {
        return hoglinRewards;
    }

    public List<String> getHoglinRewards() {
        return hoglinRewards.commands();
    }

    public String getHoglinCountdownTitle() {
        return hoglinCountdownTitle;
    }

    public String getHoglinCountdownColor() {
        return hoglinCountdownColor;
    }

    public String getHoglinSpawnTitle() {
        return hoglinSpawnTitle;
    }

    public String getHoglinSpawnSubtitle() {
        return hoglinSpawnSubtitle;
    }

    public String getHoglinNametag() {
        return hoglinNametag == null || hoglinNametag.isBlank()
            ? "{NAME}&r &8[&c{HEALTH}❤&8/&4{MAX_HEALTH}❤&8]"
            : hoglinNametag;
    }

    public Map<String, BossDefinition> getBosses() {
        return bosses;
    }

    public BossDefinition getBoss(String id) {
        if (id == null) {
            return null;
        }
        return bosses.get(id.toUpperCase(Locale.ROOT));
    }

    public void setBossLocation(String id, Location location) {
        BossDefinition current = getBoss(id);
        if (current == null) {
            return;
        }
        bosses.put(current.id(), current.withLocation(serialize(location)));
    }

    public String getSpawnTitle() {
        return spawnTitle;
    }

    public String getSpawnSubtitle() {
        return spawnSubtitle;
    }

    public int getSpawnTitleFadeIn() {
        return spawnTitleFadeIn;
    }

    public int getSpawnTitleStay() {
        return spawnTitleStay;
    }

    public int getSpawnTitleFadeOut() {
        return spawnTitleFadeOut;
    }

    public String getNoBossPlaceholder() {
        return noBossPlaceholder;
    }

    public String getSidebarTitle() {
        return sidebarTitle;
    }

    public List<String> getSidebarLines() {
        return sidebarLines;
    }

    public String getSidebarEmptyRank() {
        return sidebarEmptyRank;
    }

    public String getSidebarRankFormat() {
        return sidebarRankFormat;
    }

    public static String serialize(Location location) {
        if (location == null || location.getWorld() == null) {
            return "";
        }
        return location.getWorld().getName() + ";" + location.getX() + ";" + location.getY() + ";"
            + location.getZ() + ";" + location.getYaw() + ";" + location.getPitch();
    }

    public static Location deserialize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.split(";");
        if (parts.length < 4) {
            return null;
        }
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) {
            return null;
        }
        try {
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);
            float yaw = parts.length > 4 ? Float.parseFloat(parts[4]) : 0F;
            float pitch = parts.length > 5 ? Float.parseFloat(parts[5]) : 0F;
            return new Location(world, x, y, z, yaw, pitch);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public record BossDefinition(
        String id,
        String name,
        EntityType entityType,
        List<String> hours,
        int maxHealth,
        String locationRaw,
        BossRewards rewards,
        String countdownTitle,
        String countdownColor,
        String spawnTitle,
        String spawnSubtitle
    ) {
        public Location location() {
            return deserialize(locationRaw);
        }

        public BossDefinition withLocation(String location) {
            return new BossDefinition(
                id, name, entityType, hours, maxHealth, location, rewards,
                countdownTitle, countdownColor, spawnTitle, spawnSubtitle
            );
        }
    }

    private BossRewards readRewards(String root) {
        List<String> commands = yaml.getStringList(root + ".rewards.commands");
        if (commands.isEmpty() && yaml.isList(root + ".rewards")) {
            commands = new ArrayList<>(yaml.getStringList(root + ".rewards"));
        }
        boolean dropToInventory = yaml.getBoolean(root + ".drop-to-inventory", true);
        String broadcast = yaml.getString(root + ".broadcast", "");
        List<BossRewards.ItemDrop> items = readItems(root + ".items");
        Map<Integer, BossRewards.PlaceReward> top = new LinkedHashMap<>();
        ConfigurationSection topSection = yaml.getConfigurationSection(root + ".top-rewards");
        if (topSection != null) {
            for (String key : topSection.getKeys(false)) {
                int place;
                try {
                    place = Integer.parseInt(key);
                } catch (NumberFormatException ignored) {
                    continue;
                }
                String path = root + ".top-rewards." + key;
                top.put(place, new BossRewards.PlaceReward(
                    new ArrayList<>(yaml.getStringList(path + ".commands")),
                    readItems(path + ".items")
                ));
            }
        }
        return new BossRewards(List.copyOf(commands), items, dropToInventory, broadcast, Map.copyOf(top));
    }

    private List<BossRewards.ItemDrop> readItems(String path) {
        List<BossRewards.ItemDrop> items = new ArrayList<>();
        ConfigurationSection section = yaml.getConfigurationSection(path);
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection itemSection = section.getConfigurationSection(key);
                if (itemSection == null) {
                    continue;
                }
                addItemDrop(items, itemSection);
            }
            return List.copyOf(items);
        }
        for (Map<?, ?> raw : yaml.getMapList(path)) {
            if (raw == null || raw.isEmpty()) {
                continue;
            }
            MemoryConfiguration memory = new MemoryConfiguration();
            ConfigurationSection itemSection = memory.createSection("item");
            for (Map.Entry<?, ?> entry : raw.entrySet()) {
                if (entry.getKey() != null) {
                    itemSection.set(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            addItemDrop(items, itemSection);
        }
        return List.copyOf(items);
    }

    private static void addItemDrop(List<BossRewards.ItemDrop> items, ConfigurationSection section) {
        ItemStack item = ConfigItemStackReader.fromSection(section);
        if (item == null) {
            return;
        }
        double chance = section.getDouble("chance", 100.0D);
        items.add(new BossRewards.ItemDrop(item, Math.max(0.0D, Math.min(100.0D, chance))));
    }

    private static BossRewards emptyRewards() {
        return new BossRewards(List.of(), List.of(), true, "", Map.of());
    }
}
