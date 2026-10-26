package dev.grzybalson.anarchiagg.customitems.items;

import dev.grzybalson.anarchiagg.AnarchiaGGSettings;
import dev.grzybalson.anarchiagg.customitems.handlers.RegionHandler;
import dev.grzybalson.anarchiagg.customitems.util.EventItemRegistry;
import dev.grzybalson.anarchiagg.customitems.util.EventItemUseGuard;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class NowyExcalibur implements Listener {
    private static final String ITEM_KEY = "nowyexcalibur";
    private static final String CONFIG_PATH = "eventowki.nowyexcalibur";
    private static final String KILLS_PLACEHOLDER = "{kills}";
    private static final String PROGRESS_PLACEHOLDER = "{progress}";
    private static final String PROGRESS_PERCENT_PLACEHOLDER = "{progress_percent}";
    private static final String SHARPNESS_PLACEHOLDER = "{sharpness}";
    private static final String ATTACK_DAMAGE_PLACEHOLDER = "{attack_damage}";
    private static final String DAMAGE_POINTS_PLACEHOLDER = "{damage_points}";
    private static final String ATTACK_SPEED_PLACEHOLDER = "{attack_speed}";
    private static final int MAX_PROGRESS_KILLS = 100;
    private static final int DASH_COUNT = 10;
    private static final String DAMAGE_MODIFIER_NAME = "holly_sharp_netherite_damage";
    private static final String SPEED_MODIFIER_NAME = "NowyExcaliburAttackSpeed";
    private static final double NETHERITE_SWORD_DAMAGE_MODIFIER = 7.0D;

    private final JavaPlugin plugin;
    private final RegionHandler regionHandler;
    private final int sameVictimCooldownSeconds;
    private final Set<String> blockedRegions = new HashSet<>();
    private final Map<UUID, Map<UUID, Long>> lastKillOnPlayer = new HashMap<>();

    public NowyExcalibur(JavaPlugin plugin, RegionHandler regionHandler) {
        this.plugin = plugin;
        this.regionHandler = regionHandler;
        FileConfiguration config = AnarchiaGGSettings.getConfig(plugin);
        this.sameVictimCooldownSeconds = config.getInt(CONFIG_PATH + ".cooldown", 600);
        List<String> regionList = config.getStringList(CONFIG_PATH + ".blocked-regions");
        if (regionList.isEmpty()) {
            regionList = config.getStringList("blocked-regions");
        }
        if (regionList.isEmpty()) {
            this.blockedRegions.add("spawn");
        } else {
            this.blockedRegions.addAll(regionList);
        }
    }

    public static void ensureDefaults(JavaPlugin plugin) {
        YamlConfiguration config = AnarchiaGGSettings.getConfig(plugin);
        boolean changed = false;

        changed |= setDefault(config, CONFIG_PATH + ".material", "NETHERITE_SWORD");
        changed |= setDefault(config, CONFIG_PATH + ".name", "&e&lExcalibur");
        changed |= setDefault(config, CONFIG_PATH + ".cooldown", 0);
        changed |= setDefault(config, CONFIG_PATH + ".sharpness", 6);
        changed |= setDefault(config, CONFIG_PATH + ".attack-speed", 1.6D);
        changed |= setDefault(config, CONFIG_PATH + ".unbreakable", true);
        changed |= setDefault(config, CONFIG_PATH + ".item-flags", List.of("HIDE_ENCHANTS", "HIDE_ATTRIBUTES", "HIDE_UNBREAKABLE"));
        changed |= setDefault(config, CONFIG_PATH + ".enchants.FIRE_ASPECT", 2);
        changed |= setDefault(config, CONFIG_PATH + ".enchants.DURABILITY", 3);
        changed |= setDefault(config, CONFIG_PATH + ".lore", List.of(
            "&cDodatkowe obrazenia 0%",
            "",
            "&8» &7Jest to przedmiot zdobyty z",
            "&8» &fwydarzenia wakacyjnego 2024&7!",
            "",
            "&8» &7Aktualnie zabojstw: &f{kills}",
            "&8» {progress} &e{progress_percent}%",
            "",
            "&8» &7Zapelnienie paska zapewnia",
            "&8» &f{damage_points} punktow obrazen&7, co",
            "&8» &7jest rownoznaczne z &eostroscia {sharpness}&7!",
            "",
            "&8» &7Obrazenia od ataku: &f{attack_damage}",
            "&8» &7Sharpness: &f{sharpness}",
            "&8» &7Attack speed: &f{attack_speed}"
        ));

        if (changed) {
            AnarchiaGGSettings.save(plugin, config);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null) return;
        if (isLocationBlocked(killer.getLocation())) return;
        if (isLocationBlocked(victim.getLocation())) return;

        ItemStack item = killer.getInventory().getItemInMainHand();
        if (item == null || !item.hasItemMeta()) return;
        if (!EventItemRegistry.matches(plugin, ITEM_KEY, item)) return;
        if (!EventItemUseGuard.tryUse(plugin, killer)) return;
        if (!item.getType().name().endsWith("_SWORD")) return;

        UUID killerId = killer.getUniqueId();
        UUID victimId = victim.getUniqueId();
        long now = System.currentTimeMillis();
        long cooldownTime = EventItemUseGuard.getCooldownSeconds(plugin, ITEM_KEY, sameVictimCooldownSeconds) * 1000L;
        Map<UUID, Long> victimsMap = lastKillOnPlayer.computeIfAbsent(killerId, k -> new HashMap<>());

        if (cooldownTime > 0 && victimsMap.containsKey(victimId) && now - victimsMap.get(victimId) < cooldownTime) {
            return;
        }

        victimsMap.put(victimId, now);
        int kills = extractKills(item) + 1;
        applyConfig(item, kills);
        killer.getInventory().setItemInMainHand(item);
    }

    public static void refreshOnlineItems(JavaPlugin plugin) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerInventory inventory = player.getInventory();
            ItemStack[] contents = inventory.getContents();
            refreshContents(plugin, contents);
            inventory.setContents(contents);

            ItemStack[] armorContents = inventory.getArmorContents();
            refreshContents(plugin, armorContents);
            inventory.setArmorContents(armorContents);

            ItemStack offHand = inventory.getItemInOffHand();
            if (EventItemRegistry.matches(plugin, ITEM_KEY, offHand)) {
                applyConfig(plugin, offHand, extractKills(offHand));
                inventory.setItemInOffHand(offHand);
            }
            player.updateInventory();
        }
    }

    private static void refreshContents(JavaPlugin plugin, ItemStack[] contents) {
        for (ItemStack item : contents) {
            if (EventItemRegistry.matches(plugin, ITEM_KEY, item)) {
                applyConfig(plugin, item, extractKills(item));
            }
        }
    }

    public static void setKills(JavaPlugin plugin, ItemStack item, int kills) {
        applyConfig(plugin, item, kills);
    }

    private void applyConfig(ItemStack item, int kills) {
        applyConfig(plugin, item, kills);
    }

    private static void applyConfig(JavaPlugin plugin, ItemStack item, int kills) {
        FileConfiguration config = AnarchiaGGSettings.getConfig(plugin);
        Material material = Material.matchMaterial(config.getString(CONFIG_PATH + ".material", "NETHERITE_SWORD"));
        if (material != null && item.getType() != material) {
            item.setType(material);
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        meta.setDisplayName(color(config.getString(CONFIG_PATH + ".name", "&e&lExcalibur")));
        meta.setLore(buildLore(config, kills));
        meta.setUnbreakable(config.getBoolean(CONFIG_PATH + ".unbreakable", true));

        for (Enchantment enchantment : new HashSet<>(meta.getEnchants().keySet())) {
            meta.removeEnchant(enchantment);
        }
        int sharpness = config.getInt(CONFIG_PATH + ".sharpness", 6);
        if (sharpness > 0) {
            meta.addEnchant(Enchantment.DAMAGE_ALL, sharpness, true);
        }

        int customModelData = config.getInt(CONFIG_PATH + ".custom-model-data", EventItemRegistry.getDefaultCustomModelData(ITEM_KEY));
        if (customModelData > 0) {
            meta.setCustomModelData(customModelData);
        }

        applyConfiguredEnchants(config, meta);
        applyAttackDamage(meta);
        applyAttackSpeed(config, meta);
        applyItemFlags(config, meta);
        item.setItemMeta(meta);
        EventItemRegistry.matches(plugin, ITEM_KEY, item);
    }

    public static ItemStack createConfiguredItem(JavaPlugin plugin, int kills) {
        FileConfiguration config = AnarchiaGGSettings.getConfig(plugin);
        Material material = Material.matchMaterial(config.getString(CONFIG_PATH + ".material", "NETHERITE_SWORD"));
        ItemStack item = new ItemStack(material == null ? Material.NETHERITE_SWORD : material);
        applyConfig(plugin, item, kills);
        return item;
    }

    private static void applyConfiguredEnchants(FileConfiguration config, ItemMeta meta) {
        ConfigurationSection section = config.getConfigurationSection(CONFIG_PATH + ".enchants");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            Enchantment enchantment = Enchantment.getByName(key.toUpperCase());
            if (enchantment != null) {
                meta.addEnchant(enchantment, section.getInt(key), true);
            }
        }
    }

    private static void applyAttackDamage(ItemMeta meta) {
        meta.removeAttributeModifier(Attribute.GENERIC_ATTACK_DAMAGE);
        meta.addAttributeModifier(Attribute.GENERIC_ATTACK_DAMAGE, new AttributeModifier(
            UUID.nameUUIDFromBytes(DAMAGE_MODIFIER_NAME.getBytes(StandardCharsets.UTF_8)),
            DAMAGE_MODIFIER_NAME,
            NETHERITE_SWORD_DAMAGE_MODIFIER,
            AttributeModifier.Operation.ADD_NUMBER,
            EquipmentSlot.HAND
        ));
    }

    private static void applyAttackSpeed(FileConfiguration config, ItemMeta meta) {
        meta.removeAttributeModifier(Attribute.GENERIC_ATTACK_SPEED);
        meta.addAttributeModifier(Attribute.GENERIC_ATTACK_SPEED, new AttributeModifier(
            UUID.nameUUIDFromBytes(SPEED_MODIFIER_NAME.getBytes(StandardCharsets.UTF_8)),
            SPEED_MODIFIER_NAME,
            -2.5D,
            AttributeModifier.Operation.ADD_NUMBER,
            EquipmentSlot.HAND
        ));
    }

    private static void applyItemFlags(FileConfiguration config, ItemMeta meta) {
        meta.removeItemFlags(ItemFlag.values());
        List<String> flags = config.getStringList(CONFIG_PATH + ".item-flags");
        if (flags.isEmpty()) {
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
            return;
        }
        for (String flag : flags) {
            try {
                meta.addItemFlags(ItemFlag.valueOf(flag.toUpperCase()));
            } catch (IllegalArgumentException ignored) {
            }
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
    }

    private static List<String> buildLore(FileConfiguration config, int kills) {
        List<String> lines = config.getStringList(CONFIG_PATH + ".lore");
        if (lines.isEmpty()) {
            lines = List.of(
                "&cDodatkowe obrazenia 0%",
                "",
                "&8» &7Jest to przedmiot zdobyty z",
                "&8» &fwydarzenia wakacyjnego 2024&7!",
                "",
                "&8» &7Aktualnie zabojstw: &f{kills}",
                "&8» {progress} &e{progress_percent}%",
                "",
                "&8» &7Zapelnienie paska zapewnia",
                "&8» &f{damage_points} punktow obrazen&7, co",
                "&8» &7jest rownoznaczne z &eostroscia {sharpness}&7!",
                "",
                "&8» &7Obrazenia od ataku: &f{attack_damage}",
                "&8» &7Sharpness: &f{sharpness}",
                "&8» &7Attack speed: &f{attack_speed}"
            );
        }

        int progressPercent = Math.min(kills, MAX_PROGRESS_KILLS);
        int sharpness = config.getInt(CONFIG_PATH + ".sharpness", 6);
        Material material = Material.matchMaterial(config.getString(CONFIG_PATH + ".material", "NETHERITE_SWORD"));
        double attackDamageValue = getAttackDamage(material, sharpness);
        String attackDamage = formatNumber(attackDamageValue);
        String damagePoints = String.valueOf((int) Math.ceil(attackDamageValue));
        List<String> result = new ArrayList<>(lines.size());
        for (String line : lines) {
            result.add(color(line
                .replace(KILLS_PLACEHOLDER, String.valueOf(kills))
                .replace(PROGRESS_PLACEHOLDER, generateProgressBar(kills))
                .replace(PROGRESS_PERCENT_PLACEHOLDER, String.valueOf(progressPercent))
                .replace(SHARPNESS_PLACEHOLDER, String.valueOf(sharpness))
                .replace(ATTACK_DAMAGE_PLACEHOLDER, attackDamage)
                .replace(DAMAGE_POINTS_PLACEHOLDER, damagePoints)
                .replace("12 punktow obrazen", damagePoints + " punktow obrazen")
                .replace(ATTACK_SPEED_PLACEHOLDER, String.valueOf(config.getDouble(CONFIG_PATH + ".attack-speed", 1.6D)))));
        }
        return result;
    }

    private static double getAttackDamage(Material material, int sharpness) {
        double baseDamage = switch (material == null ? Material.NETHERITE_SWORD : material) {
            case WOODEN_SWORD, GOLDEN_SWORD -> 4.0D;
            case STONE_SWORD -> 5.0D;
            case IRON_SWORD -> 6.0D;
            case DIAMOND_SWORD -> 7.0D;
            case NETHERITE_SWORD -> 8.0D;
            default -> 1.0D;
        };
        double sharpnessDamage = sharpness > 0 ? 0.5D * sharpness + 0.5D : 0.0D;
        return baseDamage + sharpnessDamage;
    }

    private static String formatNumber(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((int) value);
        }
        return String.valueOf(value);
    }

    private static int extractKills(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore() || meta.getLore() == null) return 0;
        for (String line : meta.getLore()) {
            String stripped = ChatColor.stripColor(line);
            if (stripped == null || !stripped.toLowerCase().contains("zab")) {
                continue;
            }
            String digits = stripped.replaceAll("[^0-9]", "");
            if (!digits.isEmpty()) {
                try {
                    return Integer.parseInt(digits);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return 0;
    }

    private static String generateProgressBar(int kills) {
        int filledDashes = Math.min(kills / 10, DASH_COUNT);
        int emptyDashes = DASH_COUNT - filledDashes;
        StringBuilder bar = new StringBuilder();
        String style = ChatColor.STRIKETHROUGH.toString() + ChatColor.BOLD;
        for (int i = 0; i < filledDashes; i++) {
            bar.append(ChatColor.GREEN).append(style).append("-");
        }
        for (int i = 0; i < emptyDashes; i++) {
            bar.append(ChatColor.WHITE).append(style).append("-");
        }
        return bar.append(ChatColor.RESET).toString();
    }

    private boolean isLocationBlocked(Location loc) {
        if (loc == null || loc.getWorld() == null) return true;
        for (String regionName : blockedRegions) {
            if (regionHandler.isLocationInRegion(loc, regionName)) return true;
        }
        return false;
    }

    private static String color(String text) {
        return text == null ? "" : text.replace('&', ChatColor.COLOR_CHAR);
    }

    private static boolean setDefault(YamlConfiguration config, String path, Object value) {
        if (config.contains(path)) {
            return false;
        }
        config.set(path, value);
        return true;
    }
}
