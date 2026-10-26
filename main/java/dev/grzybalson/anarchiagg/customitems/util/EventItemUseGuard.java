package dev.grzybalson.anarchiagg.customitems.util;

import dev.grzybalson.anarchiagg.AnarchiaGGSettings;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class EventItemUseGuard {
    private static final int REUSABLE_COOLDOWN_SECONDS = 30;
    private static final int CONSUMABLE_COOLDOWN_SECONDS = 5;
    private static final long DEFAULT_GLOBAL_DELAY_MILLIS = 0L;
    private static final Map<UUID, Long> NEXT_USE_AT = new HashMap<>();
    private static final Map<UUID, Map<String, Long>> ITEM_NEXT_USE_AT = new ConcurrentHashMap<>();

    private EventItemUseGuard() {
    }

    public static boolean tryUse(JavaPlugin plugin, Player player) {
        if (player == null) {
            return false;
        }

        long now = System.currentTimeMillis();
        UUID playerId = player.getUniqueId();
        long nextUseAt = NEXT_USE_AT.getOrDefault(playerId, 0L);
        if (nextUseAt > now) {
            return false;
        }

        long delayMillis = getDelayMillis(plugin);
        if (delayMillis > 0L) {
            NEXT_USE_AT.put(playerId, now + delayMillis);
        }
        return true;
    }

    public static void clear(Player player) {
        if (player != null) {
            NEXT_USE_AT.remove(player.getUniqueId());
            ITEM_NEXT_USE_AT.remove(player.getUniqueId());
        }
    }

    public static boolean hasItemCooldown(JavaPlugin plugin, Player player, String itemKey, ItemStack item, int defaultSeconds) {
        if (player == null || itemKey == null || itemKey.isBlank()) {
            return false;
        }

        int cooldownSeconds = getCooldownSeconds(plugin, itemKey, defaultSeconds);
        if (cooldownSeconds <= 0) {
            return false;
        }

        String normalizedKey = normalizeItemKey(itemKey);
        Map<String, Long> playerCooldowns = ITEM_NEXT_USE_AT.get(player.getUniqueId());
        if (playerCooldowns == null) {
            return false;
        }

        long now = System.currentTimeMillis();
        long nextUseAt = playerCooldowns.getOrDefault(normalizedKey, 0L);
        if (nextUseAt > now) {
            return true;
        }

        playerCooldowns.remove(normalizedKey);
        if (playerCooldowns.isEmpty()) {
            ITEM_NEXT_USE_AT.remove(player.getUniqueId());
        }
        return false;
    }

    public static void applyItemCooldown(JavaPlugin plugin, Player player, String itemKey, ItemStack item, int defaultSeconds) {
        applyItemCooldown(plugin, player, itemKey, item, defaultSeconds, true);
    }

    public static void applyItemCooldown(JavaPlugin plugin, Player player, String itemKey, ItemStack item, int defaultSeconds, boolean showVanillaCooldown) {
        if (player == null || itemKey == null || itemKey.isBlank()) {
            return;
        }

        int cooldownSeconds = getCooldownSeconds(plugin, itemKey, defaultSeconds);
        if (cooldownSeconds <= 0) {
            return;
        }

        String normalizedKey = normalizeItemKey(itemKey);
        ITEM_NEXT_USE_AT.computeIfAbsent(player.getUniqueId(), ignored -> new ConcurrentHashMap<>())
                .put(normalizedKey, System.currentTimeMillis() + (cooldownSeconds * 1000L));

        if (showVanillaCooldown && item != null && item.getType() != Material.AIR) {
            player.setCooldown(item.getType(), secondsToTicks(cooldownSeconds));
        }
    }

    public static int getCooldownSeconds(JavaPlugin plugin, String itemKey, int defaultSeconds) {
        if (itemKey == null || itemKey.isBlank()) {
            return Math.max(0, defaultSeconds);
        }

        String path = "eventowki." + normalizeItemKey(itemKey) + ".cooldown";
        return Math.max(0, AnarchiaGGSettings.getConfig(plugin).getInt(path, Math.max(0, defaultSeconds)));
    }

    public static void ensureDefaultCooldowns(JavaPlugin plugin) {
        YamlConfiguration config = AnarchiaGGSettings.getConfig(plugin);
        boolean changed = false;

        if (!config.contains("eventowki.global-use-delay-ticks")) {
            config.set("eventowki.global-use-delay-ticks", 0);
            changed = true;
        }

        for (Map.Entry<String, Integer> entry : createDefaultCooldowns().entrySet()) {
            String path = "eventowki." + entry.getKey() + ".cooldown";
            if (!config.contains(path)) {
                config.set(path, entry.getValue());
                changed = true;
            }
        }

        if (changed) {
            AnarchiaGGSettings.save(plugin, config);
        }
    }

    private static long getDelayMillis(JavaPlugin plugin) {
        int configuredTicks = AnarchiaGGSettings.getConfig(plugin).getInt("eventowki.global-use-delay-ticks", 0);
        if (configuredTicks <= 0) {
            return DEFAULT_GLOBAL_DELAY_MILLIS;
        }
        return configuredTicks * 50L;
    }

    private static int secondsToTicks(int seconds) {
        long ticks = seconds * 20L;
        return ticks > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) ticks;
    }

    private static String normalizeItemKey(String itemKey) {
        return itemKey.toLowerCase(Locale.ROOT);
    }

    private static Map<String, Integer> createDefaultCooldowns() {
        Map<String, Integer> defaults = new LinkedHashMap<>();
        defaults.put("ulepszonaelytra", 0);
        defaults.put("wedkasurfera", REUSABLE_COOLDOWN_SECONDS);
        defaults.put("nielota", REUSABLE_COOLDOWN_SECONDS);
        defaults.put("smoczymiecz", REUSABLE_COOLDOWN_SECONDS);
        defaults.put("sakwa", REUSABLE_COOLDOWN_SECONDS);
        defaults.put("watacukrowa", CONSUMABLE_COOLDOWN_SECONDS);
        defaults.put("hydroklatka", REUSABLE_COOLDOWN_SECONDS);
        defaults.put("boskitopor", REUSABLE_COOLDOWN_SECONDS);
        defaults.put("bombarda", CONSUMABLE_COOLDOWN_SECONDS);
        defaults.put("zlamaneserduszko", CONSUMABLE_COOLDOWN_SECONDS);
        defaults.put("totemulaskawienia", 0);
        defaults.put("korona", 0);
        defaults.put("balonik", CONSUMABLE_COOLDOWN_SECONDS);
        defaults.put("krewwampira", CONSUMABLE_COOLDOWN_SECONDS);
        defaults.put("kosa", REUSABLE_COOLDOWN_SECONDS);
        defaults.put("siekieragrincza", REUSABLE_COOLDOWN_SECONDS);
        defaults.put("blokwidmo", REUSABLE_COOLDOWN_SECONDS);
        defaults.put("turbotrap", CONSUMABLE_COOLDOWN_SECONDS);
        defaults.put("parawan", CONSUMABLE_COOLDOWN_SECONDS);
        defaults.put("excalibur", REUSABLE_COOLDOWN_SECONDS);
        defaults.put("nowyexcalibur", REUSABLE_COOLDOWN_SECONDS);
        defaults.put("kroliczymiecz", REUSABLE_COOLDOWN_SECONDS);
        defaults.put("marchewkowymiecz", REUSABLE_COOLDOWN_SECONDS);
        defaults.put("kostkarubika", CONSUMABLE_COOLDOWN_SECONDS);
        defaults.put("lizak", 0);
        defaults.put("rozakupidyna", 0);
        defaults.put("supernieskonczonamarchewka", REUSABLE_COOLDOWN_SECONDS);
        defaults.put("cieplemleko", CONSUMABLE_COOLDOWN_SECONDS);
        defaults.put("zatrutyolowek", REUSABLE_COOLDOWN_SECONDS);
        defaults.put("jablko", CONSUMABLE_COOLDOWN_SECONDS);
        defaults.put("jajkoolafa", REUSABLE_COOLDOWN_SECONDS);
        defaults.put("lopatagrincza", REUSABLE_COOLDOWN_SECONDS);
        defaults.put("piernik", CONSUMABLE_COOLDOWN_SECONDS);
        defaults.put("sniezkateleportacji", CONSUMABLE_COOLDOWN_SECONDS);
        defaults.put("cudownalatarnia", REUSABLE_COOLDOWN_SECONDS);
        defaults.put("lukkupidyna", REUSABLE_COOLDOWN_SECONDS);
        defaults.put("marchewkowakusza", REUSABLE_COOLDOWN_SECONDS);
        defaults.put("dynamit", CONSUMABLE_COOLDOWN_SECONDS);
        defaults.put("rogjednorozca", REUSABLE_COOLDOWN_SECONDS);
        defaults.put("piekielnymiecz", REUSABLE_COOLDOWN_SECONDS);
        defaults.put("sniezynka", CONSUMABLE_COOLDOWN_SECONDS);
        defaults.put("anarchiczeneserce", CONSUMABLE_COOLDOWN_SECONDS);
        defaults.put("kokos", CONSUMABLE_COOLDOWN_SECONDS);
        defaults.put("bombakapitana", CONSUMABLE_COOLDOWN_SECONDS);
        defaults.put("linacecco", REUSABLE_COOLDOWN_SECONDS);
        defaults.put("usciskvadera", REUSABLE_COOLDOWN_SECONDS);
        defaults.put("retrospekcjaskywalkera", REUSABLE_COOLDOWN_SECONDS);
        defaults.put("hydrotrojzab", REUSABLE_COOLDOWN_SECONDS);
        defaults.put("rozdzkailuzjonisty", REUSABLE_COOLDOWN_SECONDS);
        defaults.put("usuwaczpajeczyn", REUSABLE_COOLDOWN_SECONDS);
        defaults.put("sercewampira", 0);
        defaults.put("egzekutor", REUSABLE_COOLDOWN_SECONDS);
        defaults.put("maskazludzenia", REUSABLE_COOLDOWN_SECONDS);
        defaults.put("rdzenneutralizacji", REUSABLE_COOLDOWN_SECONDS);
        return defaults;
    }
}
