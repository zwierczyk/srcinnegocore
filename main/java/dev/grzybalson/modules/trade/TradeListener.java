package dev.grzybalson.modules.trade;

import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public final class TradeListener implements Listener {

    private final TradeManager manager;

    public TradeListener(HollyCore plugin, TradeManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShiftTrade(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Player target)) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.isSneaking()) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        String world = manager.getConfig().shiftClickWorld();
        if (!player.getWorld().getName().equalsIgnoreCase(world)) {
            player.sendMessage(ColorUtils.colorizeComponent(manager.getConfig().format("wrong-world")));
            return;
        }

        event.setCancelled(true);
        manager.tryTrade(player, target);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder == null) {
            return;
        }

        if (manager.isTradeGui(holder)) {
            event.setCancelled(true);
            handleTradeClick(player, event, (TradeGui) holder);
            return;
        }

        if (manager.isWithdrawGui(holder)) {
            event.setCancelled(true);
            if (event.getClickedInventory() != null && event.getClickedInventory().equals(event.getView().getTopInventory())) {
                manager.handleWithdrawClick(player, (TradeWithdrawGui) holder, event.getRawSlot());
            }
        }
    }

    private void handleTradeClick(Player player, InventoryClickEvent event, TradeGui gui) {
        TradeSession session = gui.getSession();
        if (!session.involves(player.getUniqueId())) {
            return;
        }

        boolean clickedTop = event.getClickedInventory() != null
            && event.getClickedInventory().equals(event.getView().getTopInventory());

        if (!clickedTop) {
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType().isAir()) {
                return;
            }
            ItemStack moved = clicked.clone();
            event.getClickedInventory().setItem(event.getSlot(), null);
            if (manager.addItemToOffer(player, session, moved)) {
                manager.playSound(player, "add");
                session.resetReady();
                manager.refreshTradeGui(session);
            } else {
                event.getClickedInventory().setItem(event.getSlot(), clicked);
            }
            return;
        }

        int rawSlot = event.getRawSlot();
        boolean ownReadySlot = rawSlot == (session.isSideA(player.getUniqueId()) ? TradeSlots.READY_A : TradeSlots.READY_B);
        if (ownReadySlot) {
            manager.toggleReady(player, session);
            return;
        }

        if (TradeSlots.isOwnOfferSlot(rawSlot, session.isSideA(player.getUniqueId()))) {
            if (manager.removeFromOfferSlot(player, session, rawSlot)) {
                manager.playSound(player, "remove");
                session.resetReady();
                manager.refreshTradeGui(session);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder != null && (manager.isTradeGui(holder) || manager.isWithdrawGui(holder))) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        InventoryHolder holder = event.getInventory().getHolder();
        if (manager.isTradeGui(holder)) {
            manager.handleTradeClose(player);
        } else if (manager.isWithdrawGui(holder)) {
            manager.handleWithdrawClose(player, (TradeWithdrawGui) holder);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.handleQuit(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent event) {
        // Anuluj trade przed dropami / sync — inaczej oferty znikają albo wracają po śmierci.
        manager.cancelTradeFor(event.getEntity().getUniqueId(), "smierc");
    }
}
