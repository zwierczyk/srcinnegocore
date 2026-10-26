package dev.grzybalson.anarchiagg.zmianki;

import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ZmiankiGUIHandler implements Listener {
    private final ZmiankiManager manager;
    private final Map<UUID, Integer> selectedWeaponSlots = new HashMap<>();
    private final Map<UUID, Integer> lastBonuses = new HashMap<>();

    public ZmiankiGUIHandler(ZmiankiManager manager) {
        this.manager = manager;
    }

    public void openGUI(Player player) {
        ZmiankiHolder holder = new ZmiankiHolder(player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(holder, manager.getGuiSize(), manager.getGuiTitle());
        holder.inventory(inventory);
        inventory.setItem(manager.getApplySlot(), manager.buildApplyItem(lastBonuses.get(player.getUniqueId())));
        inventory.setItem(manager.getMainSlot(), manager.buildMainSlotItem());
        inventory.setItem(manager.getCloseSlot(), manager.buildCloseItem());
        selectedWeaponSlots.remove(player.getUniqueId());
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Inventory gui = event.getView().getTopInventory();
        if (!(gui.getHolder() instanceof ZmiankiHolder holder) || !holder.ownerUuid().equals(player.getUniqueId())) {
            return;
        }

        int rawSlot = event.getRawSlot();
        if (rawSlot < event.getView().getTopInventory().getSize()) {
            event.setCancelled(true);

            if (rawSlot == manager.getApplySlot()) {
                handleApply(player, gui);
                return;
            }

            if (rawSlot == manager.getCloseSlot()) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0F, 1.0F);
                player.closeInventory();
            }
            return;
        }

        event.setCancelled(true);
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getView().getBottomInventory())) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (!manager.isSupportedWeapon(clicked)) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
            return;
        }

        selectedWeaponSlots.put(player.getUniqueId(), event.getSlot());
        gui.setItem(manager.getMainSlot(), clicked.clone());
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0F, 1.0F);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof ZmiankiHolder)) {
            return;
        }
        UUID uuid = event.getPlayer().getUniqueId();
        selectedWeaponSlots.remove(uuid);
    }

    private void handleApply(Player player, Inventory gui) {
        Integer selectedSlot = selectedWeaponSlots.get(player.getUniqueId());
        if (selectedSlot == null) {
            fail(player, "&cNajpierw wybierz miecz z ekwipunku.");
            return;
        }

        ItemStack weapon = player.getInventory().getItem(selectedSlot);
        if (!manager.isSupportedWeapon(weapon)) {
            fail(player, "&cWybrany przedmiot nie jest poprawnym mieczem.");
            return;
        }

        if (!manager.consumeOneZmianka(player)) {
            fail(player, "&cNie posiadasz zmianki.");
            return;
        }

        int bonus = manager.rollBonus();
        manager.applyBonus(weapon, bonus);
        player.getInventory().setItem(selectedSlot, weapon);
        gui.setItem(manager.getMainSlot(), weapon.clone());
        lastBonuses.put(player.getUniqueId(), bonus);
        gui.setItem(manager.getApplySlot(), manager.buildApplyItem(bonus));
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0F, 1.0F);
        player.sendMessage(ColorUtils.success("Przebonowano. &8✕ &f" + bonus + "%"));
    }

    private void fail(Player player, String message) {
        player.sendMessage(manager.color(message));
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
    }

    private static final class ZmiankiHolder implements InventoryHolder {
        private final UUID ownerUuid;
        private Inventory inventory;

        private ZmiankiHolder(UUID ownerUuid) {
            this.ownerUuid = ownerUuid;
        }

        private UUID ownerUuid() {
            return ownerUuid;
        }

        private void inventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
