package dev.grzybalson.anarchiagg.customitems.items;

import dev.grzybalson.anarchiagg.AnarchiaGGSettings;

import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Rotatable;
import org.bukkit.block.data.type.Fence;
import org.bukkit.block.data.type.TrapDoor;
import org.bukkit.block.data.type.Wall;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Egg;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.block.BlockFace;
import dev.grzybalson.anarchiagg.customitems.handlers.RegionHandler;
import dev.grzybalson.anarchiagg.customitems.util.EventItemRegistry;
import dev.grzybalson.anarchiagg.customitems.util.EventItemUseGuard;

import java.util.*;

public class TurboTrap implements Listener {
    private final String itemName;
    private final Material itemMaterial = Material.EGG;
    private final JavaPlugin plugin;
    private final int cooldownSeconds;
    private final List<BlockPlacement> blockPlacements;
    private final HydroKlatka hydroKlatka;
    private final RegionHandler regionHandler;
    private final Set<String> blockedRegions = new HashSet<>();

    private final Set<UUID> activeTurboTraps = new HashSet<>();
    private final Map<UUID, Float> throwerRotation = new HashMap<>();

    private record BlockPlacement(Material material, int xOffset, int yOffset, int zOffset, int rotation) {
    }

    public TurboTrap(JavaPlugin plugin, HydroKlatka hydroKlatka, RegionHandler regionHandler) {
        this.plugin = plugin;
        this.hydroKlatka = hydroKlatka;
        this.regionHandler = regionHandler;
        FileConfiguration eventowkiConfig = AnarchiaGGSettings.getConfig(plugin);
        List<String> regionList = eventowkiConfig.getStringList("blocked-regions");
        if (regionList.isEmpty() && !eventowkiConfig.isSet("blocked-regions")) {
            eventowkiConfig.set("blocked-regions", List.of("spawn"));
            this.blockedRegions.add("spawn");
        } else {
            this.blockedRegions.addAll(regionList);
        }
        itemName = eventowkiConfig.getString("eventowki.turbotrap.name", "§a§lTurboTrap");
        cooldownSeconds = eventowkiConfig.getInt("eventowki.turbotrap.cooldown", 5);

        List<String> itemregionList = eventowkiConfig.getStringList("eventowki.turbotrap.blocked-regions");
        if (itemregionList.isEmpty() && !eventowkiConfig.isSet("eventowki.turbotrap.blocked-regions")) {
            eventowkiConfig.set("eventowki.turbotrap.blocked-regions", List.of("pvp"));
            this.blockedRegions.add("pvp");
        } else {
            this.blockedRegions.addAll(itemregionList);
        }

        this.blockPlacements = parseBlockPlacements(eventowkiConfig.getStringList("eventowki.turbotrap.blocks"));
    }

    private List<BlockPlacement> parseBlockPlacements(List<String> blockList) {
        List<BlockPlacement> placements = new ArrayList<>();
        for (String blockData : blockList) {
            try {
                String[] parts = blockData.split(":");
                Material material = Material.getMaterial(parts[0].toUpperCase());
                int x = Integer.parseInt(parts[1]);
                int y = Integer.parseInt(parts[2]);
                int z = Integer.parseInt(parts[3]);
                int rotation = parts.length > 4 ? Integer.parseInt(parts[4]) : 0;
                if (material != null) {
                    placements.add(new BlockPlacement(material, x, y, z, rotation));
                } else {
                    plugin.getLogger().warning("Nieznany materiał w TurboTrap config: " + parts[0]);
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Błąd podczas parsowania bloku TurboTrap: " + blockData + " -> " + e.getMessage());
            }
        }
        placements.sort(Comparator.comparingInt(BlockPlacement::yOffset));
        return placements;
    }

    private boolean isTurboTrapItem(ItemStack item) {
        return EventItemRegistry.matches(plugin, "turbotrap", item);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        ItemStack itemInOffHand = player.getInventory().getItemInOffHand();

        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!isTurboTrapItem(itemInHand) && !isTurboTrapItem(itemInOffHand)) return;

        event.setCancelled(true);
        ItemStack usedItem = isTurboTrapItem(itemInHand) ? itemInHand : itemInOffHand;
        if (EventItemUseGuard.hasItemCooldown(plugin, player, "turbotrap", usedItem, cooldownSeconds)) {
            return;
        }

        if (isLocationBlocked(player.getLocation())) return;

        Egg egg = player.launchProjectile(Egg.class);
        activeTurboTraps.add(egg.getUniqueId());
        throwerRotation.put(egg.getUniqueId(), player.getLocation().getYaw());

        EventItemUseGuard.applyItemCooldown(plugin, player, "turbotrap", usedItem, cooldownSeconds);
        if (isTurboTrapItem(itemInHand)) {
            if (itemInHand.getAmount() > 1) {
                itemInHand.setAmount(itemInHand.getAmount() - 1);
            } else {
                player.getInventory().setItemInMainHand(null);
            }
        } else if (isTurboTrapItem(itemInOffHand)) {
            if (itemInOffHand.getAmount() > 1) {
                itemInOffHand.setAmount(itemInOffHand.getAmount() - 1);
            } else {
                player.getInventory().setItemInOffHand(null);
            }
        }
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Egg egg)) return;
        if (!activeTurboTraps.contains(egg.getUniqueId())) return;

        event.setCancelled(true);
        Float rawYaw = throwerRotation.remove(egg.getUniqueId());
        activeTurboTraps.remove(egg.getUniqueId());

        if (!(event.getEntity().getShooter() instanceof Player player)) return;

        Location hitLoc = event.getHitBlock() != null ? event.getHitBlock().getLocation() :
                (event.getHitEntity() != null ? event.getHitEntity().getLocation() : egg.getLocation());

        if (hitLoc.getWorld() == null || isLocationBlocked(hitLoc)) return;
        if (rawYaw == null) return;

        float finalYaw = getFinalYaw(rawYaw);
        buildTrap(hitLoc.getBlock().getLocation(), finalYaw, player);
    }

    private static float getFinalYaw(Float rawYaw) {
        float normalizedYaw = (rawYaw % 360 + 360) % 360;
        float roundedYaw;

        if (normalizedYaw >= 315 || normalizedYaw < 45) {
            roundedYaw = 0.0f;
        } else if (normalizedYaw >= 45 && normalizedYaw < 135) {
            roundedYaw = 90.0f;
        } else if (normalizedYaw >= 135 && normalizedYaw < 225) {
            roundedYaw = 180.0f;
        } else {
            roundedYaw = 270.0f;
        }

        float finalYaw = roundedYaw;
        if (roundedYaw == 0.0f || roundedYaw == 180.0f) {
            finalYaw = (roundedYaw + 180.0f) % 360.0f;
        }
        return finalYaw;
    }

    private BlockFace getRotationBlockFaceFromAngle(int angle) {
        BlockFace[] faces = {
                BlockFace.SOUTH, BlockFace.SOUTH_SOUTH_WEST, BlockFace.SOUTH_WEST, BlockFace.WEST_SOUTH_WEST,
                BlockFace.WEST, BlockFace.WEST_NORTH_WEST, BlockFace.NORTH_WEST, BlockFace.NORTH_NORTH_WEST,
                BlockFace.NORTH, BlockFace.NORTH_NORTH_EAST, BlockFace.NORTH_EAST, BlockFace.EAST_NORTH_EAST,
                BlockFace.EAST, BlockFace.EAST_SOUTH_EAST, BlockFace.SOUTH_EAST, BlockFace.SOUTH_SOUTH_EAST
        };

        int slot = (int) Math.round(angle / 22.5) % 16;
        if (slot < 0) slot += 16;
        return faces[slot];
    }

    private BlockFace getCardinalFaceFromAngle(int angle) {
        angle = (angle % 360 + 360) % 360;
        if (angle >= 315 || angle < 45) {
            return BlockFace.SOUTH;
        } else if (angle < 135) {
            return BlockFace.WEST;
        } else if (angle < 225) {
            return BlockFace.NORTH;
        } else {
            return BlockFace.EAST;
        }
    }

    private boolean isLocationBlocked(Location loc) {
        if (loc == null || loc.getWorld() == null) return true;
        for (String regionName : blockedRegions) {
            if (regionHandler.isLocationInRegion(loc, regionName)) {
                return true;
            }
        }
        return false;
    }

    private void buildTrap(Location centerLoc, float yaw, Player player) {
        if (blockPlacements.isEmpty()) return;

        double radians = Math.toRadians(-yaw);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);

        for (BlockPlacement bp : blockPlacements) {
            double rotatedX = bp.xOffset() * cos - bp.zOffset() * sin;
            double rotatedZ = bp.xOffset() * sin + bp.zOffset() * cos;

            Location blockLoc = centerLoc.clone().add(rotatedX, bp.yOffset(), rotatedZ);
            if (isLocationBlocked(blockLoc)) return;
            if (hydroKlatka.isLocationControlled(blockLoc)) continue;
            if (blockLoc.getBlockY() < -63) continue;

            Block block = blockLoc.getBlock();
            if (!isLocationBlocked(blockLoc)) {
                if (block.getType() == Material.BEDROCK) continue;

                block.setType(bp.material(), true);
                BlockData blockData = block.getBlockData();

                int rotationYaw = (int) (yaw + bp.rotation());
                int absoluteRotation = (rotationYaw % 360 + 360) % 360;
                if (yaw == 90.0f || yaw == 270.0f) {
                    absoluteRotation = (absoluteRotation + 180) % 360;
                }

                if (blockData instanceof Rotatable rotatable) {
                    rotatable.setRotation(getRotationBlockFaceFromAngle(absoluteRotation));
                }

                if (blockData instanceof TrapDoor trapDoor) {
                    trapDoor.setOpen(true);
                    BlockFace face = getCardinalFaceFromAngle(absoluteRotation);
                    if (face == BlockFace.NORTH || face == BlockFace.EAST || face == BlockFace.SOUTH || face == BlockFace.WEST) {
                        trapDoor.setFacing(face);
                    }
                }

                if (blockData instanceof Fence fence) {
                    for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST}) {
                        Block neighbor = block.getRelative(face);
                        boolean shouldConnect = isShouldConnect(neighbor);
                        fence.setFace(face, shouldConnect);
                    }
                    block.setBlockData(fence, true);
                    continue;
                }

                block.setBlockData(blockData, true);
            }
        }
    }

    private static boolean isShouldConnect(Block neighbor) {
        BlockData neighborData = neighbor.getBlockData();
        boolean shouldConnect;
        if (neighborData instanceof Rotatable && !(neighborData instanceof Fence) && !(neighborData instanceof Wall)) {
            shouldConnect = false;
        } else if (neighborData instanceof Fence || neighborData instanceof Wall) {
            shouldConnect = true;
        } else {
            shouldConnect = neighbor.getType().isSolid() && !neighbor.getType().isTransparent();
        }
        return shouldConnect;
    }
}
