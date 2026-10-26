package dev.grzybalson.modules.masakraszafka;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SzafkaCatalog {
    static final long PERMANENT = Long.MAX_VALUE;
    static final int GUI_SIZE = 54;
    private static final Pattern ID = Pattern.compile("[a-z0-9_-]{1,32}");
    private static final Pattern DURATION_PART = Pattern.compile("(\\d{1,6})(msc|mo|w|d|h|m|s)");
    private static final long MAX_DURATION = TimeUnit.DAYS.toMillis(3650);

    private final YamlConfiguration config;
    private final Map<SzafkaItem.Kind, Map<String, SzafkaItem>> items = new EnumMap<>(SzafkaItem.Kind.class);
    private final Map<String, SoundSpec> sounds = new HashMap<>();
    private final Set<Material> miningBlocks = EnumSet.noneOf(Material.class);
    final int maxTalismans;
    final boolean keepElytra;
    final boolean hideWhenInvisible;
    final boolean nameTagEnabled;
    final long clickCooldown;
    final List<String> blockedRegions;
    final List<String> blockedWorlds;
    private final DateTimeFormatter dateFormat;

    SzafkaCatalog(YamlConfiguration config, Logger logger) {
        this.config = config;
        Consumer<String> warn = message -> logger.warning("[masakra-szafka] " + message);
        for (SzafkaItem.Kind kind : SzafkaItem.Kind.values()) items.put(kind, loadItems(kind, warn));
        maxTalismans = Math.max(0, Math.min(talismanSlots().size(), config.getInt("settings.max-talismans", 3)));
        keepElytra = config.getBoolean("settings.keep-elytra-visible", true);
        hideWhenInvisible = config.getBoolean("settings.hide-costume-when-invisible", true);
        nameTagEnabled = config.getBoolean("nametag.enabled", true);
        clickCooldown = Math.max(0, config.getLong("settings.gui-click-cooldown-ms", 200));
        blockedRegions = List.copyOf(config.getStringList("settings.effects-blocked-regions"));
        blockedWorlds = List.copyOf(config.getStringList("settings.effects-blocked-worlds"));
        dateFormat = dateFormat(config.getString("settings.date-format", "dd.MM.yyyy HH:mm:ss"), warn);
        loadMiningBlocks(warn);
        loadSounds(warn);
    }

    private Map<String, SzafkaItem> loadItems(SzafkaItem.Kind kind, Consumer<String> warn) {
        Map<String, SzafkaItem> loaded = new LinkedHashMap<>();
        ConfigurationSection root = config.getConfigurationSection(kind.section);
        if (root == null) return Collections.unmodifiableMap(loaded);
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null || !section.getBoolean("enabled", true)) continue;
            if (!ID.matcher(id).matches()) {
                warn.accept("pominięto '" + kind.section + "." + id + "' — ID może zawierać tylko a-z, 0-9, _ i -.");
                continue;
            }
            long duration = parseDuration(section.getString("default-duration", "7d"));
            if (duration < 0) {
                warn.accept("nieprawidłowy default-duration w " + section.getCurrentPath() + ", użyto 7d.");
                duration = TimeUnit.DAYS.toMillis(7);
            }
            Material fallback = kind == SzafkaItem.Kind.COSTUME ? Material.LEATHER_CHESTPLATE : Material.NETHER_STAR;
            String texture = section.getString("helmet-texture", section.getString("texture", ""));
            loaded.put(id, new SzafkaItem(kind, id,
                section.getString("name", id),
                section.getString("rarity", ""),
                List.copyOf(section.getStringList("properties")),
                parseMaterial(section.getString("icon"), fallback),
                color(section.getString("color"), section.getCurrentPath(), warn),
                texture == null || texture.isBlank() ? null : texture.trim(),
                Math.max(0, section.getInt("custom-model-data")),
                duration,
                section.getString("symbol", ""),
                SzafkaPerks.parse(section.getConfigurationSection("perks"), warn)));
        }
        return Collections.unmodifiableMap(loaded);
    }

    private void loadMiningBlocks(Consumer<String> warn) {
        for (String raw : config.getStringList("settings.mining-blocks")) {
            String value = raw.trim().toUpperCase(Locale.ROOT);
            if (value.contains("*")) {
                Pattern pattern = Pattern.compile(String.join(".*", Arrays.stream(value.split("\\*", -1)).map(Pattern::quote).toList()));
                boolean matched = false;
                for (Material material : Material.values()) {
                    if (!material.isLegacy() && material.isBlock() && pattern.matcher(material.name()).matches()) {
                        miningBlocks.add(material);
                        matched = true;
                    }
                }
                if (!matched) warn.accept("wzorzec mining-blocks '" + raw + "' nie pasuje do żadnego bloku.");
                continue;
            }
            Material material = Material.matchMaterial(value);
            if (material == null || !material.isBlock()) warn.accept("nieznany blok w mining-blocks: " + raw);
            else miningBlocks.add(material);
        }
    }

    private void loadSounds(Consumer<String> warn) {
        ConfigurationSection section = config.getConfigurationSection("sounds");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            String[] parts = section.getString(key, "").split(":");
            if (parts[0].isBlank() || parts[0].equalsIgnoreCase("NONE")) continue;
            try {
                Sound sound = Sound.valueOf(parts[0].trim().toUpperCase(Locale.ROOT));
                float volume = parts.length > 1 ? Float.parseFloat(parts[1]) : 1f;
                float pitch = parts.length > 2 ? Float.parseFloat(parts[2]) : 1f;
                sounds.put(key, new SoundSpec(sound, Math.max(0f, Math.min(4f, volume)), Math.max(0.5f, Math.min(2f, pitch))));
            } catch (IllegalArgumentException exception) {
                warn.accept("nieprawidłowy dźwięk sounds." + key + ": " + section.getString(key));
            }
        }
    }

    SzafkaItem item(SzafkaItem.Kind kind, String id) {
        return kind == null || id == null ? null : items.get(kind).get(id);
    }

    SzafkaItem find(String id) {
        SzafkaItem costume = item(SzafkaItem.Kind.COSTUME, id);
        return costume != null ? costume : item(SzafkaItem.Kind.TALISMAN, id);
    }

    Collection<SzafkaItem> items(SzafkaItem.Kind kind) {
        return items.get(kind).values();
    }

    boolean isMiningBlock(Material material) {
        return miningBlocks.contains(material);
    }

    void play(Player player, String key) {
        SoundSpec spec = sounds.get(key);
        if (spec != null) player.playSound(player.getLocation(), spec.sound, spec.volume, spec.pitch);
    }

    String text(String path, String fallback) {
        return config.getString(path, fallback);
    }

    List<String> lines(String path) {
        return config.getStringList(path);
    }

    String message(String key) {
        return config.getString("messages." + key, "&c" + key);
    }

    Material material(String path, Material fallback) {
        return parseMaterial(config.getString(path, ""), fallback);
    }

    int slot(String path, int fallback) {
        int slot = config.getInt(path, fallback);
        return slot >= 0 && slot < GUI_SIZE ? slot : fallback;
    }

    List<Integer> slots(String path) {
        Set<Integer> slots = new LinkedHashSet<>();
        for (Integer slot : config.getIntegerList(path)) if (slot != null && slot >= 0 && slot < GUI_SIZE) slots.add(slot);
        return List.copyOf(slots);
    }

    List<Integer> talismanSlots() {
        return slots("gui.talisman-slots");
    }

    String formatDate(long epochMillis) {
        return epochMillis == PERMANENT ? text("time.never", "nigdy") : dateFormat.format(Instant.ofEpochMilli(epochMillis));
    }

    String formatDuration(long millis) {
        if (millis >= PERMANENT / 2) return text("time.permanent", "na zawsze");
        long seconds = Math.max(0, millis / 1000);
        long days = seconds / 86_400;
        long hours = seconds % 86_400 / 3_600;
        long minutes = seconds % 3_600 / 60;
        StringJoiner joiner = new StringJoiner(" ");
        if (days > 0) joiner.add(days + "d");
        if (hours > 0) joiner.add(hours + "h");
        if (minutes > 0) joiner.add(minutes + "min");
        return joiner.length() == 0 ? seconds + "s" : joiner.toString();
    }

    static long parseDuration(String raw) {
        if (raw == null) return -1;
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (List.of("perm", "permanent", "nazawsze", "na-zawsze").contains(value)) return PERMANENT;
        Matcher matcher = DURATION_PART.matcher(value);
        long total = 0;
        int consumed = 0;
        while (matcher.find()) {
            if (matcher.start() != consumed) return -1;
            consumed = matcher.end();
            long amount = Long.parseLong(matcher.group(1));
            total += switch (matcher.group(2)) {
                case "msc", "mo" -> TimeUnit.DAYS.toMillis(amount * 30);
                case "w" -> TimeUnit.DAYS.toMillis(amount * 7);
                case "d" -> TimeUnit.DAYS.toMillis(amount);
                case "h" -> TimeUnit.HOURS.toMillis(amount);
                case "m" -> TimeUnit.MINUTES.toMillis(amount);
                default -> TimeUnit.SECONDS.toMillis(amount);
            };
            if (total > MAX_DURATION) return -1;
        }
        return consumed == value.length() && total > 0 ? total : -1;
    }

    static List<String> expand(List<String> template, Map<String, List<String>> blocks, Map<String, String> values) {
        List<String> lines = new ArrayList<>();
        for (String line : template) {
            List<String> block = blocks.get(line.trim());
            if (block != null) {
                block.stream().filter(entry -> entry != null && !entry.isEmpty()).forEach(entry -> lines.add(replace(entry, values)));
            } else {
                lines.add(replace(line, values));
            }
        }
        List<String> compact = new ArrayList<>();
        for (String line : lines) {
            boolean blank = line.isBlank();
            if (blank && (compact.isEmpty() || compact.get(compact.size() - 1).isBlank())) continue;
            compact.add(line);
        }
        while (!compact.isEmpty() && compact.get(compact.size() - 1).isBlank()) compact.remove(compact.size() - 1);
        return compact;
    }

    static String replace(String text, Map<String, String> values) {
        String result = text;
        for (Map.Entry<String, String> entry : values.entrySet()) result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        return result;
    }

    private static Material parseMaterial(String raw, Material fallback) {
        Material material = raw == null ? null : Material.matchMaterial(raw);
        return material != null && material.isItem() && !material.isAir() ? material : fallback;
    }

    private static Color color(String raw, String path, Consumer<String> warn) {
        if (raw == null || raw.isBlank()) return null;
        String hex = raw.trim().replace("#", "");
        try {
            if (hex.length() == 6) return Color.fromRGB(Integer.parseInt(hex, 16));
        } catch (IllegalArgumentException ignored) {
        }
        warn.accept("nieprawidłowy kolor '" + raw + "' w " + path + " (użyj formatu #RRGGBB).");
        return null;
    }

    private static DateTimeFormatter dateFormat(String pattern, Consumer<String> warn) {
        try {
            return DateTimeFormatter.ofPattern(pattern).withZone(ZoneId.systemDefault());
        } catch (IllegalArgumentException exception) {
            warn.accept("nieprawidłowy settings.date-format, użyto dd.MM.yyyy HH:mm:ss.");
            return DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(ZoneId.systemDefault());
        }
    }

    private record SoundSpec(Sound sound, float volume, float pitch) {
    }
}
