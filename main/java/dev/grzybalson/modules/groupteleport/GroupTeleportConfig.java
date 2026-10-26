package dev.grzybalson.modules.groupteleport;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.HollyDataPaths;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GroupTeleportConfig {

    private static final String RESOURCE = "addons/groupteleport/groupteleport.yml";
    private static final Pattern TIME = Pattern.compile("(\\d+)\\s*([smhSMH]?)");

    private final HollyCore plugin;
    private final File file;
    private YamlConfiguration yaml;

    private final List<Location> teleportLocations = new ArrayList<>();
    private long endMillis = TimeUnit.MINUTES.toMillis(2);
    private String teleportationTitle = "&cTeparka bedzie trwala jeszcze: &4{TIME}";
    private BarColor teleportationColor = BarColor.RED;
    private String endTitle = "&cKoniec teparki za: &4{TIME}";
    private BarColor endColor = BarColor.RED;
    private String teleportWorld = "teparki";
    private String pvpWorld = "pvp";
    private String requiredRegion = "spawn";
    private boolean requireRegion = true;
    private String aloneMessage = "&cNie mozesz przeteleportowac sie sam!";
    private String spawnBlockedMessage = "&cNie mozesz użyć &4/spawn &cpodczas teparki!";
    private String spawnBlockedOtherMessage = "&cGracz &4{player} &cjest na teparce i nie może zostać przeniesiony!";
    private boolean actionbarEnabled = true;
    private long actionbarDurationTicks = 80L;
    private String victimTeleportActionBar = "&#FF5555☠ &fPrzegrałeś teparę! &7Za chwilę zostaniesz przeniesiony na spawn...";
    private String killerWaitActionBar = "&#55FF55✔ &fWygrałeś teparę! &7Teleport na spawn za &e{TIME}";
    private String killerTeleportActionBar = "&#55FF55✔ &fTeparka zakończona! &7Witaj na spawnie.";
    private double borderRadius = 10.0D;
    private int pvpCountdownSeconds = 3;
    private boolean blockElytra = true;
    private String elytraBlockedMessage = "&cNie możesz używać elytry na teparce!";
    private String buttonMaterial = "OAK_BUTTON";
    private String backingMaterial = "SPONGE";
    private String plateMaterial = "OAK_PRESSURE_PLATE";
    private double plateRadius = 5.0D;

    public GroupTeleportConfig(HollyCore plugin) {
        this.plugin = plugin;
        this.file = HollyDataPaths.getRootFile(plugin, RESOURCE);
        reload();
    }

    public void reload() {
        HollyDataPaths.ensureResource(plugin, RESOURCE);
        yaml = YamlConfiguration.loadConfiguration(file);
        setDefaults();
        teleportLocations.clear();
        List<?> raw = yaml.getList("teleport-locations");
        if (raw != null) {
            for (Object entry : raw) {
                Location location = deserializeLocation(String.valueOf(entry));
                if (location != null) {
                    teleportLocations.add(location);
                }
            }
        }
        endMillis = parseTime(yaml.getString("end-time", "2m"));
        teleportationTitle = yaml.getString("titles.active", teleportationTitle);
        endTitle = yaml.getString("titles.end", endTitle);
        teleportationColor = parseColor(yaml.getString("titles.active-color", "RED"));
        endColor = parseColor(yaml.getString("titles.end-color", "RED"));
        teleportWorld = yaml.getString("worlds.teleport", "teparki");
        pvpWorld = yaml.getString("worlds.pvp", "pvp");
        requiredRegion = yaml.getString("interaction.required-region", "spawn");
        requireRegion = yaml.getBoolean("interaction.require-region", true);
        aloneMessage = yaml.getString("messages.alone", aloneMessage);
        spawnBlockedMessage = yaml.getString("messages.spawn-blocked", spawnBlockedMessage);
        spawnBlockedOtherMessage = yaml.getString("messages.spawn-blocked-other", spawnBlockedOtherMessage);
        actionbarEnabled = yaml.getBoolean("actionbar.enabled", true);
        actionbarDurationTicks = Math.max(1L, yaml.getLong("actionbar.duration-ticks", 80L));
        victimTeleportActionBar = yaml.getString("actionbar.victim-teleport", victimTeleportActionBar);
        killerWaitActionBar = yaml.getString("actionbar.killer-wait", killerWaitActionBar);
        killerTeleportActionBar = yaml.getString("actionbar.killer-teleport", killerTeleportActionBar);
        borderRadius = yaml.getDouble("arena.border-radius", 10.0D);
        pvpCountdownSeconds = Math.max(0, yaml.getInt("arena.pvp-countdown-seconds", 3));
        blockElytra = yaml.getBoolean("arena.block-elytra", true);
        elytraBlockedMessage = yaml.getString("messages.elytra-blocked", elytraBlockedMessage);
        buttonMaterial = yaml.getString("interaction.button-material", "OAK_BUTTON");
        backingMaterial = yaml.getString("interaction.backing-material", "SPONGE");
        plateMaterial = yaml.getString("interaction.plate-material", "OAK_PRESSURE_PLATE");
        plateRadius = yaml.getDouble("interaction.plate-radius", 5.0D);
        save();
    }

    private void setDefaults() {
        setIfMissing("end-time", "2m");
        setIfMissing("titles.active", "&cTeparka bedzie trwala jeszcze: &4{TIME}");
        setIfMissing("titles.end", "&cKoniec teparki za: &4{TIME}");
        setIfMissing("titles.active-color", "RED");
        setIfMissing("titles.end-color", "RED");
        setIfMissing("worlds.teleport", "teparki");
        setIfMissing("worlds.pvp", "pvp");
        setIfMissing("interaction.require-region", true);
        setIfMissing("interaction.required-region", "spawn");
        setIfMissing("interaction.button-material", "OAK_BUTTON");
        setIfMissing("interaction.backing-material", "SPONGE");
        setIfMissing("interaction.plate-material", "OAK_PRESSURE_PLATE");
        setIfMissing("interaction.plate-radius", 5.0D);
        setIfMissing("arena.border-radius", 10.0D);
        setIfMissing("arena.pvp-countdown-seconds", 3);
        setIfMissing("arena.block-elytra", true);
        setIfMissing("messages.alone", "&cNie mozesz przeteleportowac sie sam!");
        setIfMissing("messages.spawn-blocked", "&cNie mozesz użyć &4/spawn &cpodczas teparki!");
        setIfMissing("messages.spawn-blocked-other", "&cGracz &4{player} &cjest na teparce i nie może zostać przeniesiony!");
        setIfMissing("messages.elytra-blocked", "&cNie możesz używać elytry na teparce!");
        setIfMissing("actionbar.enabled", true);
        setIfMissing("actionbar.duration-ticks", 80);
        setIfMissing("actionbar.victim-teleport", "&#FF5555☠ &fPrzegrałeś teparę! &7Za chwilę zostaniesz przeniesiony na spawn...");
        setIfMissing("actionbar.killer-wait", "&#55FF55✔ &fWygrałeś teparę! &7Teleport na spawn za &e{TIME}");
        setIfMissing("actionbar.killer-teleport", "&#55FF55✔ &fTeparka zakończona! &7Witaj na spawnie.");
        if (!yaml.contains("teleport-locations")) {
            yaml.set("teleport-locations", new ArrayList<String>());
        }
    }

    private void setIfMissing(String path, Object value) {
        if (!yaml.contains(path)) {
            yaml.set(path, value);
        }
    }

    public void save() {
        List<String> serialized = new ArrayList<>();
        for (Location location : teleportLocations) {
            serialized.add(serializeLocation(location));
        }
        yaml.set("teleport-locations", serialized);
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("[GroupTeleport] Nie udalo sie zapisac configu: " + exception.getMessage());
        }
    }

    public void addLocation(Location location) {
        if (location != null) {
            teleportLocations.add(location.clone());
            save();
        }
    }

    public Location randomLocation() {
        if (teleportLocations.isEmpty()) {
            return null;
        }
        return teleportLocations.get(ThreadLocalRandom.current().nextInt(teleportLocations.size())).clone();
    }

    public long getEndMillis() {
        return endMillis;
    }

    public String getTeleportationTitle() {
        return teleportationTitle;
    }

    public BarColor getTeleportationColor() {
        return teleportationColor;
    }

    public String getEndTitle() {
        return endTitle;
    }

    public BarColor getEndColor() {
        return endColor;
    }

    public String getTeleportWorld() {
        return teleportWorld;
    }

    public String getPvpWorld() {
        return pvpWorld;
    }

    public String getRequiredRegion() {
        return requiredRegion;
    }

    public boolean isRequireRegion() {
        return requireRegion;
    }

    public String getAloneMessage() {
        return aloneMessage;
    }

    public String getSpawnBlockedMessage() {
        return spawnBlockedMessage;
    }

    public String getSpawnBlockedOtherMessage() {
        return spawnBlockedOtherMessage;
    }

    public boolean isActionbarEnabled() {
        return actionbarEnabled;
    }

    public long getActionbarDurationTicks() {
        return actionbarDurationTicks;
    }

    public String getVictimTeleportActionBar() {
        return victimTeleportActionBar;
    }

    public String getKillerWaitActionBar() {
        return killerWaitActionBar;
    }

    public String getKillerTeleportActionBar() {
        return killerTeleportActionBar;
    }

    public double getBorderRadius() {
        return borderRadius;
    }

    public int getPvpCountdownSeconds() {
        return pvpCountdownSeconds;
    }

    public boolean isBlockElytra() {
        return blockElytra;
    }

    public String getElytraBlockedMessage() {
        return elytraBlockedMessage;
    }

    public String getButtonMaterial() {
        return buttonMaterial;
    }

    public String getBackingMaterial() {
        return backingMaterial;
    }

    public String getPlateMaterial() {
        return plateMaterial;
    }

    public double getPlateRadius() {
        return plateRadius;
    }

    public int locationCount() {
        return teleportLocations.size();
    }

    public static String formatTime(long millis) {
        long totalSeconds = Math.max(0L, millis / 1000L);
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        }
        return seconds + "s";
    }

    public static long parseTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return TimeUnit.MINUTES.toMillis(2);
        }
        Matcher matcher = TIME.matcher(raw.trim());
        if (!matcher.matches()) {
            return TimeUnit.MINUTES.toMillis(2);
        }
        long amount = Long.parseLong(matcher.group(1));
        String unit = matcher.group(2) == null ? "s" : matcher.group(2).toLowerCase(Locale.ROOT);
        return switch (unit) {
            case "h" -> TimeUnit.HOURS.toMillis(amount);
            case "m" -> TimeUnit.MINUTES.toMillis(amount);
            default -> TimeUnit.SECONDS.toMillis(amount);
        };
    }

    private static BarColor parseColor(String raw) {
        try {
            return BarColor.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return BarColor.RED;
        }
    }

    private static String serializeLocation(Location location) {
        World world = location.getWorld();
        String worldName = world != null ? world.getName() : "world";
        return worldName + ";" + location.getX() + ";" + location.getY() + ";" + location.getZ()
            + ";" + location.getYaw() + ";" + location.getPitch();
    }

    private static Location deserializeLocation(String raw) {
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

    public String color(String text) {
        return ColorUtils.colorize(text);
    }

    public void send(Player player, String text) {
        if (player != null && text != null) {
            player.sendMessage(ColorUtils.colorizeComponent(text));
        }
    }
}
