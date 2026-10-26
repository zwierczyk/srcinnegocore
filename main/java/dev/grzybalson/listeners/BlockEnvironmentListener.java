package dev.grzybalson.listeners;

import dev.grzybalson.HollyCore;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Fence;
import org.bukkit.block.data.type.GlassPane;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.block.data.type.Wall;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * - Coral / coral fans nigdy nie obumieraja.
 * - Schody, plotki, murki, szyby itp. zawsze dostaja update fizyki (laczenie).
 */
public class BlockEnvironmentListener implements Listener {

    private static final BlockFace[] HORIZONTAL = {
        BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST
    };

    private final HollyCore plugin;

    public BlockEnvironmentListener(HollyCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCoralFade(BlockFadeEvent event) {
        Material type = event.getBlock().getType();
        if (isLiveCoral(type)) {
            event.setCancelled(true);
        }
    }

    /**
     * Jesli inny plugin/antylag anuluje fizyke, przywroc ja dla blokow laczacych sie.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPhysics(BlockPhysicsEvent event) {
        if (!event.isCancelled()) {
            return;
        }
        Material blockType = event.getBlock().getType();
        Material sourceType = event.getSourceBlock().getType();
        if (isConnectingBlock(blockType) || isConnectingBlock(sourceType)) {
            event.setCancelled(false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConnectingBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (!isConnectingBlock(block.getType())) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> forceConnectUpdate(block));
    }

    private void forceConnectUpdate(Block block) {
        if (block == null || !isConnectingBlock(block.getType())) {
            return;
        }
        BlockData data = block.getBlockData();
        block.setBlockData(data, true);
        for (BlockFace face : HORIZONTAL) {
            Block neighbor = block.getRelative(face);
            if (isConnectingBlock(neighbor.getType())) {
                neighbor.setBlockData(neighbor.getBlockData(), true);
            }
        }
        Block above = block.getRelative(BlockFace.UP);
        Block below = block.getRelative(BlockFace.DOWN);
        if (isConnectingBlock(above.getType()) || above.getBlockData() instanceof Bisected) {
            above.setBlockData(above.getBlockData(), true);
        }
        if (isConnectingBlock(below.getType()) || below.getBlockData() instanceof Bisected) {
            below.setBlockData(below.getBlockData(), true);
        }
    }

    private static boolean isLiveCoral(Material material) {
        if (material == null) {
            return false;
        }
        String name = material.name();
        if (!name.contains("CORAL")) {
            return false;
        }
        return !name.startsWith("DEAD_");
    }

    private static boolean isConnectingBlock(Material material) {
        if (material == null || material.isAir()) {
            return false;
        }
        String name = material.name();
        if (name.endsWith("_STAIRS")
            || name.endsWith("_FENCE")
            || name.endsWith("_FENCE_GATE")
            || name.endsWith("_WALL")
            || name.endsWith("_PANE")
            || name.equals("IRON_BARS")
            || name.equals("CHAIN")
            || name.equals("COBBLESTONE_WALL")
            || name.equals("MOSSY_COBBLESTONE_WALL")) {
            return true;
        }
        try {
            BlockData data = material.createBlockData();
            return data instanceof Stairs
                || data instanceof Fence
                || data instanceof Wall
                || data instanceof GlassPane;
        } catch (Exception ignored) {
            return false;
        }
    }
}
