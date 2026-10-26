package dev.grzybalson.listeners;

import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ColorUtils;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Locale;

public class VplnVoucherListener implements Listener {

    private static final Material VOUCHER_MATERIAL = Material.LIME_DYE;
    private static final String VOUCHER_PLAIN_NAME = "20 VPLN";
    private static final double VOUCHER_AMOUNT = 20.0D;

    private final HollyCore plugin;

    public VplnVoucherListener(HollyCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        scheduleScan(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        ItemStack stack = event.getItem().getItemStack();
        if (!isVoucher(stack)) {
            return;
        }
        event.setCancelled(true);
        int count = stack.getAmount();
        event.getItem().remove();
        redeem(player, count);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        boolean currentVoucher = isVoucher(current);
        boolean cursorVoucher = isVoucher(cursor);
        if (!currentVoucher && !cursorVoucher) {
            return;
        }

        event.setCancelled(true);
        event.setResult(org.bukkit.event.Event.Result.DENY);

        int redeemed = 0;
        if (currentVoucher && current != null) {
            redeemed += takeFromSlot(event.getClickedInventory(), event.getSlot(), current.getAmount());
        }
        if (cursorVoucher && cursor != null && !cursor.getType().isAir()) {
            redeemed += cursor.getAmount();
            event.getView().setCursor(null);
        }

        if (redeemed > 0) {
            redeem(player, redeemed);
        }

        scheduleScan(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack oldCursor = event.getOldCursor();
        if (!isVoucher(oldCursor)) {
            return;
        }
        event.setCancelled(true);
        event.setResult(org.bukkit.event.Event.Result.DENY);
        event.getView().setCursor(null);
        redeem(player, oldCursor.getAmount());
        scheduleScan(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClickMonitor(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        InventoryAction action = event.getAction();
        if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY
            || action == InventoryAction.HOTBAR_SWAP
            || action == InventoryAction.SWAP_WITH_CURSOR
            || action == InventoryAction.PLACE_ALL
            || action == InventoryAction.PLACE_ONE
            || action == InventoryAction.PLACE_SOME) {
            scheduleScan(player);
        }
    }

    private void scheduleScan(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> scanAndRedeem(player));
    }

    private void scanAndRedeem(Player player) {
        if (!player.isOnline() || plugin.getWalletManager() == null) {
            return;
        }
        int redeemed = redeemFromInventory(player.getInventory());
        if (redeemed > 0) {
            redeem(player, redeemed);
        }
    }

    private int redeemFromInventory(PlayerInventory inventory) {
        int total = 0;
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (!isVoucher(item)) {
                continue;
            }
            total += item.getAmount();
            inventory.setItem(slot, null);
        }
        return total;
    }

    private int takeFromSlot(Inventory inventory, int slot, int amount) {
        if (inventory == null || slot < 0) {
            return 0;
        }
        ItemStack item = inventory.getItem(slot);
        if (!isVoucher(item)) {
            return 0;
        }
        int taken = Math.min(amount, item.getAmount());
        int remaining = item.getAmount() - taken;
        if (remaining <= 0) {
            inventory.setItem(slot, null);
        } else {
            item.setAmount(remaining);
            inventory.setItem(slot, item);
        }
        return taken;
    }

    private void redeem(Player player, int voucherCount) {
        if (voucherCount <= 0 || plugin.getWalletManager() == null) {
            return;
        }
        double totalAmount = VOUCHER_AMOUNT * voucherCount;
        plugin.getWalletManager().addBalance(
            player.getUniqueId(),
            totalAmount,
            "VOUCHER",
            player.getName(),
            "REDEEM",
            player.getUniqueId().toString(),
            VOUCHER_PLAIN_NAME + " x" + voucherCount
        ).thenAccept(success -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (success) {
                player.sendMessage(ColorUtils.colorizeComponent(
                    "&#00FF3B✔ &8Ξ &#4EFA76Do portfela dodano &f" + formatAmount(totalAmount) + " wPLN&a."));
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.25f);
            } else {
                player.sendMessage(ColorUtils.colorizeComponent(
                    "&#FF0000✘ &8Ξ &cNie udało się doładować portfela voucherem wPLN."));
            }
        }));
    }

    private static String formatAmount(double amount) {
        if (Math.rint(amount) == amount) {
            return String.format(Locale.US, "%.0f", amount);
        }
        return String.format(Locale.US, "%.2f", amount);
    }

    static boolean isVoucher(ItemStack item) {
        if (item == null || item.getType() != VOUCHER_MATERIAL || item.getAmount() <= 0) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || meta.displayName() == null) {
            return false;
        }
        String plain = PlainTextComponentSerializer.plainText().serialize(meta.displayName()).trim();
        return plain.equalsIgnoreCase(VOUCHER_PLAIN_NAME);
    }
}
