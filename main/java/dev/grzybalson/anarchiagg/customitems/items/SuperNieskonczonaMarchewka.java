package dev.grzybalson.anarchiagg.customitems.items;

import dev.grzybalson.anarchiagg.AnarchiaGGSettings;
import dev.grzybalson.anarchiagg.customitems.handlers.RegionHandler;
import dev.grzybalson.anarchiagg.customitems.util.EventItemRegistry;
import dev.grzybalson.anarchiagg.customitems.util.EventItemUseGuard;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.attribute.Attribute;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SuperNieskonczonaMarchewka implements Listener {

    public static final String ITEM_KEY = "supernieskonczonamarchewka";
    private static final int DURATION_SECONDS = 3;
    private static final int DEFAULT_COOLDOWN_SECONDS = 60;
    private static final double DEFAULT_SCALE = 1.3D;
    private static final double DEFAULT_CRIT_MULTIPLIER = 1.5D;
    private static final Map<UUID, Double> PREVIOUS_SCALE = new ConcurrentHashMap<>();
    private static final Map<UUID, BukkitTask> ACTIVE_TASKS = new ConcurrentHashMap<>();
    private static final Set<UUID> ACTIVE_PLAYERS = ConcurrentHashMap.newKeySet();
    private static volatile Attribute cachedScaleAttribute;
    private static volatile boolean scaleAttributeResolved = false;

    private final JavaPlugin plugin;
    private final RegionHandler regionHandler;
    private final Set<String> blockedRegions = new HashSet<>();
    private final int durationSeconds;
    private final int cooldownSeconds;
    private final double targetScale;
    private final double critMultiplier;

    public SuperNieskonczonaMarchewka(JavaPlugin plugin, RegionHandler regionHandler) {
        this.plugin = plugin;
        this.regionHandler = regionHandler;

        FileConfiguration config = AnarchiaGGSettings.getConfig(plugin);
        List<String> globalBlocked = config.getStringList("blocked-regions");
        List<String> itemBlocked = config.getStringList("eventowki." + ITEM_KEY + ".blocked-regions");

        if (globalBlocked.isEmpty() && !config.isSet("blocked-regions")) {
            this.blockedRegions.add("spawn");
        } else {
        this.blockedRegions.addAll(globalBlocked);
        }
        this.blockedRegions.addAll(itemBlocked);

        this.durationSeconds = DURATION_SECONDS;
        this.cooldownSeconds = Math.max(0, config.getInt("eventowki." + ITEM_KEY + ".cooldown", DEFAULT_COOLDOWN_SECONDS));
        this.targetScale = Math.max(1.0D, config.getDouble("eventowki." + ITEM_KEY + ".scale", DEFAULT_SCALE));
        this.critMultiplier = Math.max(1.0D, config.getDouble("eventowki." + ITEM_KEY + ".crit-multiplier", DEFAULT_CRIT_MULTIPLIER));
    }

    public static ItemStack createDefaultItem() {
        ItemStack item = new ItemStack(Material.GOLDEN_CARROT);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.setDisplayName(colorize("&6&lSuper Nieskończona Marchewka"));

        List<String> lore = new ArrayList<>();
        lore.add(colorize("&8» &7Jest to przedmiot zdobyty podczas"));
        lore.add(colorize("&8» &fwielkanocnego eventu 2026&7!"));
        lore.add(colorize("&8» &f"));
        lore.add(colorize("&8» &7Po użyciu &fpowiększa cię do &6ogromnej wielkości&7,"));
        lore.add(colorize("&8» &7nakłada &cSpowolnienie II &7i &aOdporność III"));
        lore.add(colorize("&8» &7oraz &czwiększa &dobrażenia krytyczne&7."));
        meta.setLore(lore);
        meta.setCustomModelData(2026);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_POTION_EFFECTS);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || item.getType() == Material.AIR) {
            return;
        }
        if (!EventItemRegistry.matches(plugin, ITEM_KEY, item)) {
            return;
        }
        if (!EventItemUseGuard.tryUse(plugin, player)) {
            return;
        }
        EventItemRegistry.normalizeStackableItem(plugin, ITEM_KEY, item);

        event.setCancelled(true);

        if (isLocationBlocked(player.getLocation())) {
            return;
        }

        if (EventItemUseGuard.hasItemCooldown(plugin, player, ITEM_KEY, item, cooldownSeconds)) {
            return;
        }
        EventItemUseGuard.applyItemCooldown(plugin, player, ITEM_KEY, item, cooldownSeconds);

        activate(player);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        normalizeItem(event.getCurrentItem());
        normalizeItem(event.getCursor());
        if (event.getWhoClicked() instanceof Player player) {
            plugin.getServer().getScheduler().runTask(plugin, () -> normalizeInventory(player.getInventory()));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        normalizeItem(event.getOldCursor());
        if (event.getWhoClicked() instanceof Player player) {
            plugin.getServer().getScheduler().runTask(plugin, () -> normalizeInventory(player.getInventory()));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTask(plugin, () -> normalizeInventory(event.getPlayer().getInventory()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }
        if (!ACTIVE_PLAYERS.contains(attacker.getUniqueId())) {
            return;
        }
        if (!isCriticalAttack(attacker)) {
            return;
        }

        event.setDamage(event.getDamage() * critMultiplier);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        deactivate(event.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        deactivate(event.getEntity());
    }

    private void normalizeInventory(Inventory inventory) {
        if (inventory == null) {
            return;
        }
        for (ItemStack item : inventory.getContents()) {
            normalizeItem(item);
        }
    }

    private void normalizeItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return;
        }
        // This item uses vanilla stacking (golden carrot), so only normalize candidates of the same material.
        // Avoids expensive meta comparisons for unrelated items during inventory scans.
        if (item.getType() != Material.GOLDEN_CARROT) {
            return;
        }
        EventItemRegistry.normalizeStackableItem(plugin, ITEM_KEY, item);
    }

    private void activate(Player player) {
        UUID uuid = player.getUniqueId();
        if (!ACTIVE_PLAYERS.contains(uuid)) {
            PREVIOUS_SCALE.put(uuid, getCurrentScale(player));
        }

        ACTIVE_PLAYERS.add(uuid);
        applyScale(player, targetScale);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, durationSeconds * 20, 1, false, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, durationSeconds * 20, 2, false, false, true));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 0.8F);

        BukkitTask previousTask = ACTIVE_TASKS.remove(uuid);
        if (previousTask != null) {
            previousTask.cancel();
        }

        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Player online = plugin.getServer().getPlayer(uuid);
            if (online != null) {
                deactivate(online);
            } else {
                ACTIVE_PLAYERS.remove(uuid);
                PREVIOUS_SCALE.remove(uuid);
                ACTIVE_TASKS.remove(uuid);
            }
        }, durationSeconds * 20L);
        ACTIVE_TASKS.put(uuid, task);
    }

    private void deactivate(Player player) {
        UUID uuid = player.getUniqueId();
        ACTIVE_PLAYERS.remove(uuid);

        BukkitTask task = ACTIVE_TASKS.remove(uuid);
        if (task != null) {
            task.cancel();
        }

        Double previousScale = PREVIOUS_SCALE.remove(uuid);
        if (previousScale != null) {
            applyScale(player, previousScale);
        }
    }

    public static void disable(JavaPlugin plugin) {
        List<UUID> active = new ArrayList<>(ACTIVE_PLAYERS);
        for (UUID uuid : active) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null) {
                Double previousScale = PREVIOUS_SCALE.remove(uuid);
                if (previousScale != null) {
                    applyScale(player, previousScale);
                }
            }

            BukkitTask task = ACTIVE_TASKS.remove(uuid);
            if (task != null) {
                task.cancel();
            }
            ACTIVE_PLAYERS.remove(uuid);
        }
        PREVIOUS_SCALE.clear();
        ACTIVE_TASKS.clear();
        ACTIVE_PLAYERS.clear();
    }

    private boolean isLocationBlocked(Location location) {
        if (location == null || location.getWorld() == null) {
            return true;
        }

        for (String regionName : blockedRegions) {
            if (regionHandler.isLocationInRegion(location, regionName)) {
                return true;
            }
        }
        return false;
    }

    private boolean isCriticalAttack(Player player) {
        return player.getAttackCooldown() > 0.9F
                && player.getFallDistance() > 0.0F
                && !player.isOnGround()
                && !player.isInsideVehicle()
                && !player.isClimbing()
                && !player.isInWater()
                && !player.hasPotionEffect(PotionEffectType.BLINDNESS);
    }


    private static double getCurrentScale(Player player) {
        Attribute scaleAttribute = resolveScaleAttribute();
        if (scaleAttribute == null) {
            return 1.0D;
        }

        AttributeInstance attributeInstance = player.getAttribute(scaleAttribute);
        if (attributeInstance == null) {
            return 1.0D;
        }

        return attributeInstance.getBaseValue();
    }

    private static void applyScale(Player player, double value) {
        Attribute scaleAttribute = resolveScaleAttribute();
        if (scaleAttribute != null) {
            AttributeInstance attributeInstance = player.getAttribute(scaleAttribute);
            if (attributeInstance != null) {
                attributeInstance.setBaseValue(value);
            }
        }

        try {
            Class<?> scaleTypesClass = Class.forName("virtuoel.pehkui.api.ScaleTypes");
            java.lang.reflect.Field baseField = scaleTypesClass.getField("BASE");
            Object baseType = baseField.get(null);
            java.lang.reflect.Method getScaleDataMethod = baseType.getClass().getMethod("getScaleData", org.bukkit.entity.Entity.class);
            Object scaleData = getScaleDataMethod.invoke(baseType, player);
            java.lang.reflect.Method setTargetScaleMethod = scaleData.getClass().getMethod("setTargetScale", float.class);
            setTargetScaleMethod.invoke(scaleData, (float) value);
        } catch (Throwable ignored) {
        }
    }

    private static Attribute resolveScaleAttribute() {
        if (scaleAttributeResolved) {
            return cachedScaleAttribute;
        }

        scaleAttributeResolved = true;
        for (String candidate : List.of("GENERIC_SCALE", "SCALE")) {
            try {
                cachedScaleAttribute = Attribute.valueOf(candidate);
                return cachedScaleAttribute;
            } catch (IllegalArgumentException ignored) {
            }
        }
        return null;
    }

    private static String colorize(String text) {
        return ColorUtils.colorize(text);
    }
}
