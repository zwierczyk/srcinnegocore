package dev.grzybalson.modules.upgrade;

import dev.grzybalson.HollyCore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class UpgradeBestEffectsListener implements Listener {

    private static final int EFFECT_DURATION_TICKS = 5 * 20;
    private static final int REFRESH_PERIOD_TICKS = 5 * 20;
    private static final int REFRESH_PLAYERS_PER_CYCLE = 12;
    private static final int APPLY_DEBOUNCE_TICKS = 3;
    private static final int STRENGTH_AMPLIFIER = 0;
    private static final int SPEED_AMPLIFIER = 0;

    private final HollyCore plugin;
    private final BukkitTask task;
    private final Set<UUID> pendingApply = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> lastEquipmentFingerprint = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> cachedStrength = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> cachedSpeed = new ConcurrentHashMap<>();
    private int refreshCursor;

    public UpgradeBestEffectsListener(HollyCore plugin) {
        this.plugin = plugin;
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tickOnlinePlayers, REFRESH_PERIOD_TICKS, REFRESH_PERIOD_TICKS);
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
        }
        pendingApply.clear();
        lastEquipmentFingerprint.clear();
        cachedStrength.clear();
        cachedSpeed.clear();
        for (Player player : Bukkit.getOnlinePlayers()) {
            clearOwnedEffects(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        scheduleApply(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        pendingApply.remove(uuid);
        lastEquipmentFingerprint.remove(uuid);
        cachedStrength.remove(uuid);
        cachedSpeed.remove(uuid);
        clearOwnedEffects(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        UUID uuid = event.getEntity().getUniqueId();
        lastEquipmentFingerprint.remove(uuid);
        cachedStrength.remove(uuid);
        cachedSpeed.remove(uuid);
        clearOwnedEffects(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        lastEquipmentFingerprint.remove(uuid);
        cachedStrength.remove(uuid);
        cachedSpeed.remove(uuid);
        scheduleApply(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent event) {
        scheduleApply(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        scheduleApply(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        scheduleApply(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            scheduleApply(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            scheduleApply(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            scheduleApply(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || item.getType().isAir()) {
            return;
        }
        String materialName = item.getType().name();
        if (materialName.endsWith("_HELMET")
            || materialName.endsWith("_CHESTPLATE")
            || materialName.endsWith("_LEGGINGS")
            || materialName.endsWith("_BOOTS")
            || materialName.equals("ELYTRA")
            || materialName.equals("PLAYER_HEAD")) {
            scheduleApply(event.getPlayer());
        }
    }

    private void tickOnlinePlayers() {
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (online.isEmpty()) {
            refreshCursor = 0;
            return;
        }
        int batch = Math.min(REFRESH_PLAYERS_PER_CYCLE, Math.max(4, online.size() / 25));
        batch = Math.min(batch, online.size());
        for (int i = 0; i < batch; i++) {
            if (refreshCursor >= online.size()) {
                refreshCursor = 0;
            }
            apply(online.get(refreshCursor++));
        }
    }

    private void scheduleApply(Player player) {
        UUID uuid = player.getUniqueId();
        if (!pendingApply.add(uuid)) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pendingApply.remove(uuid);
            if (player.isOnline()) {
                apply(player);
            }
        }, APPLY_DEBOUNCE_TICKS);
    }

    private void apply(Player player) {
        UpgradeManager manager = plugin.getUpgradeManager();
        UUID uuid = player.getUniqueId();
        if (manager == null) {
            lastEquipmentFingerprint.remove(uuid);
            cachedStrength.remove(uuid);
            cachedSpeed.remove(uuid);
            clearOwnedEffects(player);
            return;
        }

        int fingerprint = equipmentFingerprint(player);
        Integer previous = lastEquipmentFingerprint.get(uuid);
        boolean sameGear = previous != null && previous == fingerprint;
        if (sameGear && hasFreshOwnedEffects(player)) {
            return;
        }

        boolean strength;
        boolean speed;
        if (sameGear) {
            strength = Boolean.TRUE.equals(cachedStrength.get(uuid));
            speed = Boolean.TRUE.equals(cachedSpeed.get(uuid));
        } else {
            strength = manager.isBestSwordEffectEnabled() && manager.isHoldingMaxUpgradeSword(player);
            speed = manager.isBestSetEffectEnabled() && manager.isWearingMaxUpgradeSet(player);
            lastEquipmentFingerprint.put(uuid, fingerprint);
            cachedStrength.put(uuid, strength);
            cachedSpeed.put(uuid, speed);
        }

        syncEffect(player, PotionEffectType.INCREASE_DAMAGE, strength, STRENGTH_AMPLIFIER);
        syncEffect(player, PotionEffectType.SPEED, speed, SPEED_AMPLIFIER);
    }

    private boolean hasFreshOwnedEffects(Player player) {
        PotionEffect strength = player.getPotionEffect(PotionEffectType.INCREASE_DAMAGE);
        PotionEffect speed = player.getPotionEffect(PotionEffectType.SPEED);
        return (strength == null || strength.getDuration() > 40)
            && (speed == null || speed.getDuration() > 40);
    }

    private static int equipmentFingerprint(Player player) {
        PlayerInventory inventory = player.getInventory();
        int hash = 1;
        hash = 31 * hash + itemFingerprint(inventory.getItemInMainHand());
        hash = 31 * hash + itemFingerprint(inventory.getHelmet());
        hash = 31 * hash + itemFingerprint(inventory.getChestplate());
        hash = 31 * hash + itemFingerprint(inventory.getLeggings());
        hash = 31 * hash + itemFingerprint(inventory.getBoots());
        return hash;
    }

    private static int itemFingerprint(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return 0;
        }
        int hash = 31 * item.getType().ordinal() + item.getAmount();
        if (!item.hasItemMeta()) {
            return hash;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return hash;
        }
        if (meta.hasCustomModelData()) {
            hash = 31 * hash + meta.getCustomModelData();
        }
        hash = 31 * hash + item.getEnchantments().hashCode();
        return hash;
    }

    private void clearOwnedEffects(Player player) {
        syncEffect(player, PotionEffectType.INCREASE_DAMAGE, false, STRENGTH_AMPLIFIER);
        syncEffect(player, PotionEffectType.SPEED, false, SPEED_AMPLIFIER);
    }

    private void syncEffect(Player player, PotionEffectType type, boolean shouldHave, int amplifier) {
        PotionEffect existing = player.getPotionEffect(type);
        if (shouldHave) {
            if (existing != null && existing.getAmplifier() > amplifier) {
                return;
            }
            if (existing != null && existing.getAmplifier() == amplifier && existing.getDuration() > 20) {
                return;
            }
            player.addPotionEffect(new PotionEffect(type, EFFECT_DURATION_TICKS, amplifier, true, false, true), true);
            return;
        }

        if (existing != null && existing.getAmplifier() == amplifier && existing.getDuration() > 0
            && existing.getDuration() <= EFFECT_DURATION_TICKS) {
            player.removePotionEffect(type);
        }
    }
}
