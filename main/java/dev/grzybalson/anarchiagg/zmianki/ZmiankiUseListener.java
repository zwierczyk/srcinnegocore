package dev.grzybalson.anarchiagg.zmianki;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;

public class ZmiankiUseListener implements Listener {
    private final ZmiankiManager manager;
    private final ZmiankiGUIHandler guiHandler;

    public ZmiankiUseListener(ZmiankiManager manager, ZmiankiGUIHandler guiHandler) {
        this.manager = manager;
        this.guiHandler = guiHandler;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (!manager.isZmiankaItem(mainHand)) {
            return;
        }

        if (player.getOpenInventory().getTopInventory().getType() != InventoryType.CRAFTING) {
            return;
        }

        guiHandler.openGUI(player);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0F, 1.0F);
        event.setCancelled(true);
    }
}
