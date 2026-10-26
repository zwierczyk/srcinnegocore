package dev.grzybalson.modules.anticheat;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.grzybalson.HollyCore;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class AnticheatHandler implements Listener {

    private final AnticheatManager manager;
    private final Cache<UUID, Long> inventoryOpenTimes = Caffeine.newBuilder()
        .expireAfterAccess(2, TimeUnit.MINUTES)
        .build();

    public AnticheatHandler(HollyCore plugin, AnticheatManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.remove(event.getPlayer().getUniqueId());
        inventoryOpenTimes.invalidate(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        inventoryOpenTimes.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        inventoryOpenTimes.invalidate(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onFastArmorClick(InventoryClickEvent event) {
        if (event.getWhoClicked().getGameMode() == GameMode.CREATIVE) {
            return;
        }
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || (clicked.getType() != Material.ELYTRA && !clicked.getType().name().endsWith("_CHESTPLATE"))) {
            return;
        }
        Long opened = inventoryOpenTimes.getIfPresent(event.getWhoClicked().getUniqueId());
        if (opened != null && System.currentTimeMillis() - opened < 250L) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryMove(InventoryClickEvent event) {
        if (!manager.getConfig().isInventoryMove()) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (player.isSprinting() || player.isSneaking()) {
            event.setCancelled(true);
            player.closeInventory();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player damager) || !(event.getEntity() instanceof Player)) {
            return;
        }
        CheatPlayerData data = manager.data(damager);
        if (data.getBlockHitUntil() >= System.currentTimeMillis()) {
            event.setCancelled(true);
            event.setDamage(0.0D);
            return;
        }
        data.setLastHit(System.currentTimeMillis());
        if (!manager.getConfig().isEatWhileHit() || !isEatingFood(damager)) {
            return;
        }
        event.setDamage(0);
        int vl = data.addViolation(CheatType.EAT_WHILE_HIT);
        if (vl >= 8) {
            data.resetViolation(CheatType.EAT_WHILE_HIT);
            manager.notifyStaff(damager, CheatType.EAT_WHILE_HIT, vl, manager.getConfig().getEatWhileHitAlert(), Map.of());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        CheatPlayerData data = manager.data(event.getPlayer());
        if (data.getBlockMoveUntil() < System.currentTimeMillis()) {
            return;
        }
        // Blokuj tylko zmiane pozycji, nie obracanie kamery.
        var from = event.getFrom();
        var to = event.getTo();
        if (to == null) {
            return;
        }
        if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockedPlace(BlockPlaceEvent event) {
        CheatPlayerData data = manager.data(event.getPlayer());
        if (data.getBlockPlaceUntil() >= System.currentTimeMillis()) {
            event.setCancelled(true);
            event.setBuild(false);
            Material placed = event.getBlock().getType();
            if (placed.isItem()) {
                event.getPlayer().setCooldown(placed, 20);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEat(PlayerItemConsumeEvent event) {
        if (!manager.getConfig().isEatWhileHit()) {
            return;
        }
        ItemStack offhand = event.getPlayer().getInventory().getItem(EquipmentSlot.OFF_HAND);
        if (offhand == null || offhand.getType().isAir() || offhand.getType() != event.getItem().getType()) {
            return;
        }
        Player player = event.getPlayer();
        CheatPlayerData data = manager.data(player);
        if (System.currentTimeMillis() - data.getLastHit() >= 850L) {
            return;
        }
        event.setCancelled(true);
        int vl = data.addViolation(CheatType.EAT_WHILE_HIT);
        data.setBlockHitUntil(System.currentTimeMillis());
        if (vl >= 8) {
            data.resetViolation(CheatType.EAT_WHILE_HIT);
            manager.notifyStaff(player, CheatType.EAT_WHILE_HIT, vl, manager.getConfig().getEatWhileHitAlert(), Map.of());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void blockElytraEquip(PlayerInteractEvent event) {
        if (!manager.getConfig().isElytraSwap() || !event.getAction().isRightClick()) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack chest = player.getInventory().getChestplate();
        ItemStack main = player.getInventory().getItemInMainHand();
        ItemStack off = player.getInventory().getItemInOffHand();
        boolean wearingElytra = chest != null && chest.getType() == Material.ELYTRA;
        boolean mainChest = main.getType().name().endsWith("_CHESTPLATE") || main.getType() == Material.ELYTRA;
        boolean offChest = off.getType().name().endsWith("_CHESTPLATE") || off.getType() == Material.ELYTRA;
        if (wearingElytra && (mainChest || offChest)) {
            event.setCancelled(true);
        }
        if (player.getInventory().getChestplate() != null && (main.getType() == Material.ELYTRA || off.getType() == Material.ELYTRA)) {
            event.setCancelled(true);
        }
    }

    private boolean isEatingFood(Player player) {
        if (player == null || !player.isHandRaised()) {
            return false;
        }
        ItemStack using = player.getActiveItem();
        return using != null && !using.getType().isAir() && using.getType().isEdible();
    }
}