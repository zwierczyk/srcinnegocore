package dev.grzybalson.anarchiagg.customitems.handlers;

import dev.grzybalson.anarchiagg.customitems.util.EventItemRegistry;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumSet;
import java.util.Set;

public class FlowerPlaceBlocker implements Listener {
    private static final Set<Material> BLOCKED_FLOWERS = EnumSet.of(
            Material.DANDELION,
            Material.POPPY,
            Material.BLUE_ORCHID,
            Material.ALLIUM,
            Material.AZURE_BLUET,
            Material.RED_TULIP,
            Material.ORANGE_TULIP,
            Material.WHITE_TULIP,
            Material.PINK_TULIP,
            Material.OXEYE_DAISY,
            Material.CORNFLOWER,
            Material.LILY_OF_THE_VALLEY,
            Material.WITHER_ROSE,
            Material.SUNFLOWER,
            Material.LILAC,
            Material.ROSE_BUSH,
            Material.PEONY,
            Material.TORCHFLOWER,
            Material.PITCHER_PLANT
    );

    private final JavaPlugin plugin;

    public FlowerPlaceBlocker(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (item == null || item.getType() == Material.AIR) {
            return;
        }

        if (!BLOCKED_FLOWERS.contains(item.getType())) {
            return;
        }

        if (!EventItemRegistry.isTrackedEventItem(plugin, item)) {
            return;
        }

        event.setCancelled(true);
        event.getPlayer().updateInventory();
    }
}
