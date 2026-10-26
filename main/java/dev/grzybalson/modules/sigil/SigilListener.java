package dev.grzybalson.modules.sigil;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public class SigilListener implements Listener {

    private final SigilManager manager;

    public SigilListener(SigilManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        manager.handlePlayerJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        manager.unloadPlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        InventoryHolder holder = event.getView().getTopInventory().getHolder();

        if (holder instanceof SigilManageGui manageGui) {
            event.setCancelled(true);
            if (event.getClickedInventory() != event.getView().getTopInventory()) {
                return;
            }
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType().isAir()) {
                return;
            }
            manager.handleManageClick(player, manageGui, clicked, event.getSlot());
            return;
        }

        if (holder instanceof SigilRollGui rollGui) {
            event.setCancelled(true);
            manager.finishRollImmediately(player, rollGui);
            player.closeInventory();
            return;
        }

        if (!(holder instanceof SigilGui)) {
            return;
        }

        event.setCancelled(true);

        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) {
            return;
        }

        int slot = event.getSlot();
        boolean leftClick = event.isLeftClick();

        String action = manager.getAction(clicked);
        if ("roll".equals(action) || slot == manager.getRollSlot()) {
            manager.rollSigil(player);
            return;
        }
        if ("buy_slots".equals(action) || slot == manager.getBuySlotsSlot()) {
            manager.buyExtraSlots(player);
            return;
        }
        if (manager.isEquipSlot(slot)) {
            manager.handleEquipSlotClick(player, slot);
            return;
        }
        if (manager.isDisplaySlot(slot) || manager.identifySigilType(clicked) != null) {
            manager.handleCatalogClick(player, clicked, leftClick);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof SigilGui || holder instanceof SigilManageGui || holder instanceof SigilRollGui) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (event.getView().getTopInventory().getHolder() instanceof SigilRollGui holder) {
            manager.finishRollImmediately(player, holder);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSigilUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        if (manager.identifySigilType(item) == null) {
            return;
        }
        if (manager.isItemUseSuppressed(event.getPlayer())) {
            event.setCancelled(true);
            return;
        }

        manager.refreshPhysicalSigilItem(item);
        event.setCancelled(true);
        manager.unlockFromItem(event.getPlayer(), item);
    }
}
