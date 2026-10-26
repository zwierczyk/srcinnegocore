package dev.grzybalson.modules.anticheat;

import dev.grzybalson.anarchiagg.util.SoundsUtil;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Map;

/**
 * Cobweb place — blokada offhand.
 * Ghost-hand bez cancelu (szybki hotbar swap = FP).
 */
public final class CobwebCheck implements Listener {

    private final AnticheatManager manager;

    public CobwebCheck(AnticheatManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!manager.getConfig().isCobweb() || event.getBlock().getType() != Material.COBWEB) {
            return;
        }
        Player player = event.getPlayer();

        if (event.getHand() == EquipmentSlot.OFF_HAND) {
            event.setCancelled(true);
            event.setBuild(false);
            player.setCooldown(Material.COBWEB, 30);
            SoundsUtil.error(player);
            player.sendMessage(ColorUtils.colorizeComponent(manager.getConfig().getCobwebOffhand()));
            return;
        }

        // Ghost place — tylko VL/alert, bez cancel (FP przy switchu).
        if (player.getInventory().getItemInMainHand().getType() != Material.COBWEB) {
            int vl = manager.data(player).addViolation(CheatType.COBWEB_PLACER);
            if (vl >= 12) {
                manager.data(player).resetViolation(CheatType.COBWEB_PLACER);
                manager.notifyStaff(player, CheatType.COBWEB_PLACER, vl, manager.getConfig().getCobwebAlert(), Map.of());
            }
        }
    }
}
