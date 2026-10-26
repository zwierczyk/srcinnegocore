package dev.grzybalson.anarchiagg.ksiegi;

import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

public class KsiegiGUIListener implements Listener {
    private final KsiegiManager manager;

    public KsiegiGUIListener(KsiegiManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!manager.isKsiegiGuiTitle(event.getView().getTitle())) {
            return;
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }

        ItemStack current = event.getCurrentItem();
        KsiegiManager.BookType type = manager.identifyBook(current);
        if (type == null) {
            return;
        }

        ItemStack book = manager.getBook(type);
        if (book == null) {
            return;
        }

        player.getInventory().addItem(book);
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.1f);
        player.sendMessage(ColorUtils.success("Otrzymałeś księgę &f" + type.id() + "&a."));
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (manager.isKsiegiGuiTitle(event.getView().getTitle())) {
            event.setCancelled(true);
        }
    }
}
