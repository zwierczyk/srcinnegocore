package dev.grzybalson.modules.groupteleport;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Switch;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class GroupTeleportInteractListener implements Listener {

    private final GroupTeleportManager manager;

    public GroupTeleportInteractListener(GroupTeleportManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onButton(PlayerInteractEvent event) {
        if (!event.hasBlock() || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        GroupTeleportConfig config = manager.getConfig();

        if (config.isRequireRegion() && !WorldGuardRegionHelper.isInRegion(player, config.getRequiredRegion())) {
            return;
        }

        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }

        Material buttonMat = Material.matchMaterial(config.getButtonMaterial());
        Material backingMat = Material.matchMaterial(config.getBackingMaterial());
        Material plateMat = Material.matchMaterial(config.getPlateMaterial());
        if (buttonMat == null) {
            buttonMat = Material.OAK_BUTTON;
        }
        if (backingMat == null) {
            backingMat = Material.SPONGE;
        }
        if (plateMat == null) {
            plateMat = Material.OAK_PRESSURE_PLATE;
        }

        if (clicked.getType() != buttonMat) {
            return;
        }

        BlockData data = clicked.getBlockData();
        if (data instanceof Switch button) {
            Block relative = clicked.getRelative(button.getFacing().getOppositeFace());
            if (relative.getType() != backingMat) {
                return;
            }
        }

        if (!isOnPlate(player.getLocation().getBlock(), plateMat)) {
            return;
        }

        List<Player> onPlates = playersOnPlate(clicked.getLocation(), config.getPlateRadius(), plateMat);
        if (onPlates.size() < 2) {
            manager.getConfig().send(player, config.getAloneMessage());
            player.sendTitle(" ", manager.getConfig().color(config.getAloneMessage()), 5, 30, 5);
            return;
        }

        Player first = onPlates.get(0);
        Player second = onPlates.get(1);
        manager.startTeleport(first, second);
    }

    private boolean isOnPlate(Block block, Material plate) {
        Block below = block.getRelative(BlockFace.DOWN);
        return block.getType() == plate || below.getType() == plate;
    }

    private List<Player> playersOnPlate(Location location, double radius, Material plate) {
        if (location == null || location.getWorld() == null) {
            return new ArrayList<>();
        }
        World world = location.getWorld();
        double radiusSq = radius * radius;
        return world.getPlayers().stream()
            .filter(player -> player.getLocation().getWorld() == world)
            .filter(player -> player.getLocation().distanceSquared(location) <= radiusSq)
            .filter(player -> player.getLocation().getBlock().getType() == plate)
            .collect(Collectors.toList());
    }
}
