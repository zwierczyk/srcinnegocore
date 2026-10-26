package dev.grzybalson.modules.oghubcustomitems;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BaseBlock;
import dev.grzybalson.HollyCore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Slab;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

final class SchematicPaster {

    private SchematicPaster() {
    }

    static Animation startAnimated(HollyCore plugin, Location origin, String schematicName, Collection<Player> viewers) {
        if (origin == null || origin.getWorld() == null || schematicName == null || schematicName.isBlank()) {
            return null;
        }
        if (Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit") == null
            && Bukkit.getPluginManager().getPlugin("WorldEdit") == null) {
            plugin.getLogger().warning("Zamek piaskowy: brak FastAsyncWorldEdit/WorldEdit, schemat nie zostanie wklejony.");
            return null;
        }

        File file = findSchematic(plugin, schematicName.trim());
        if (file == null) {
            plugin.getLogger().warning("Zamek piaskowy: nie znaleziono schematu \"" + schematicName + "\" (.schem).");
            return null;
        }

        try {
            PastePlan plan = loadLayers(origin, file);
            if (plan.layers.isEmpty()) {
                return null;
            }
            Animation animation = new Animation(plugin, origin.clone(), viewers, plan.layers, plan.stand);
            animation.start();
            return animation;
        } catch (Throwable throwable) {
            plugin.getLogger().log(Level.WARNING, "Zamek piaskowy: nie udalo sie wczytac schematu " + file.getAbsolutePath(), throwable);
            return null;
        }
    }

    /** Stopy na bloku pod graczem — nigdy w powietrzu nad schematem. */
    static Location alignFeet(Location from) {
        if (from == null || from.getWorld() == null) {
            return from;
        }
        int support = findSupportY(from);
        return new Location(
            from.getWorld(),
            from.getBlockX() + 0.5D,
            support + 1.0D,
            from.getBlockZ() + 0.5D,
            from.getYaw(),
            from.getPitch()
        );
    }

    private static int findSupportY(Location origin) {
        World world = origin.getWorld();
        int x = origin.getBlockX();
        int z = origin.getBlockZ();
        int from = origin.getBlockY();
        for (int y = from; y >= from - 12; y--) {
            if (isSupport(world.getBlockAt(x, y, z))) {
                return y;
            }
        }
        return Math.max(world.getMinHeight(), from - 1);
    }

    private static boolean isSupport(Block block) {
        if (block == null) {
            return false;
        }
        Material type = block.getType();
        return type.isSolid() && !type.isAir();
    }

    private static PastePlan loadLayers(Location origin, File file) throws Exception {
        ClipboardFormat format = ClipboardFormats.findByFile(file);
        if (format == null) {
            throw new IllegalArgumentException("Nieznany format schematu: " + file.getName());
        }

        Clipboard clipboard;
        try (FileInputStream input = new FileInputStream(file);
             ClipboardReader reader = format.getReader(input)) {
            clipboard = reader.read();
        }

        BlockVector3 min = clipboard.getMinimumPoint();
        BlockVector3 max = clipboard.getMaximumPoint();

        int minSolidX = Integer.MAX_VALUE;
        int minSolidY = Integer.MAX_VALUE;
        int minSolidZ = Integer.MAX_VALUE;
        int maxSolidX = Integer.MIN_VALUE;
        int maxSolidZ = Integer.MIN_VALUE;
        List<BlockVector3> solids = new ArrayList<>();

        for (int y = min.getBlockY(); y <= max.getBlockY(); y++) {
            for (int x = min.getBlockX(); x <= max.getBlockX(); x++) {
                for (int z = min.getBlockZ(); z <= max.getBlockZ(); z++) {
                    BlockVector3 pos = BlockVector3.at(x, y, z);
                    if (!clipboard.getRegion().contains(pos)) {
                        continue;
                    }
                    BaseBlock block = clipboard.getFullBlock(pos);
                    if (block.getBlockType().getMaterial().isAir()) {
                        continue;
                    }
                    solids.add(pos);
                    minSolidX = Math.min(minSolidX, x);
                    minSolidY = Math.min(minSolidY, y);
                    minSolidZ = Math.min(minSolidZ, z);
                    maxSolidX = Math.max(maxSolidX, x);
                    maxSolidZ = Math.max(maxSolidZ, z);
                }
            }
        }
        if (solids.isEmpty()) {
            return new PastePlan(List.of(), alignFeet(origin));
        }

        int centerX = (minSolidX + maxSolidX) / 2;
        int centerZ = (minSolidZ + maxSolidZ) / 2;
        int baseX = origin.getBlockX();
        int supportY = findSupportY(origin);
        int baseY = supportY;
        int baseZ = origin.getBlockZ();

        TreeMap<Integer, Map<Location, BlockData>> byWorldY = new TreeMap<>();
        java.util.Set<BlockPos> occupied = new java.util.HashSet<>();
        int minWorldX = Integer.MAX_VALUE;
        int minWorldY = Integer.MAX_VALUE;
        int minWorldZ = Integer.MAX_VALUE;
        int maxWorldX = Integer.MIN_VALUE;
        int maxWorldY = Integer.MIN_VALUE;
        int maxWorldZ = Integer.MIN_VALUE;
        for (BlockVector3 pos : solids) {
            BaseBlock block = clipboard.getFullBlock(pos);
            Location target = new Location(
                origin.getWorld(),
                baseX + (pos.getBlockX() - centerX),
                baseY + (pos.getBlockY() - minSolidY),
                baseZ + (pos.getBlockZ() - centerZ)
            );
            occupied.add(BlockPos.of(target));
            minWorldX = Math.min(minWorldX, target.getBlockX());
            minWorldY = Math.min(minWorldY, target.getBlockY());
            minWorldZ = Math.min(minWorldZ, target.getBlockZ());
            maxWorldX = Math.max(maxWorldX, target.getBlockX());
            maxWorldY = Math.max(maxWorldY, target.getBlockY());
            maxWorldZ = Math.max(maxWorldZ, target.getBlockZ());
            byWorldY
                .computeIfAbsent(target.getBlockY(), key -> new LinkedHashMap<>())
                .put(target, toFullBlock(BukkitAdapter.adapt(block.toImmutableState())));
        }
        Location stand = findStandLocation(
            origin.getWorld(),
            occupied,
            minWorldX, maxWorldX, minWorldY, maxWorldY, minWorldZ, maxWorldZ,
            supportY,
            origin.getYaw(),
            origin.getPitch()
        );
        return new PastePlan(new ArrayList<>(byWorldY.values()), stand);
    }

    private static Location findStandLocation(
        World world,
        java.util.Set<BlockPos> occupied,
        int minX, int maxX, int minY, int maxY, int minZ, int maxZ,
        int supportY,
        float yaw,
        float pitch
    ) {
        int cx = (minX + maxX) / 2;
        int cz = (minZ + maxZ) / 2;
        for (int radius = 0; radius <= 6; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }
                    Location found = tryStandColumn(world, occupied, cx + dx, cz + dz, supportY, maxY, yaw, pitch);
                    if (found != null) {
                        return found;
                    }
                }
            }
        }
        return new Location(world, cx + 0.5D, Math.max(supportY + 1, minY), cz + 0.5D, yaw, pitch);
    }

    private static Location tryStandColumn(
        World world,
        java.util.Set<BlockPos> occupied,
        int x,
        int z,
        int supportY,
        int maxY,
        float yaw,
        float pitch
    ) {
        int yStart = supportY + 1;
        int yEnd = Math.max(yStart, maxY) + 2;
        for (int y = yStart; y <= yEnd; y++) {
            if (isBlocked(world, occupied, x, y, z) || isBlocked(world, occupied, x, y + 1, z)) {
                continue;
            }
            if (!isBlocked(world, occupied, x, y - 1, z) && !isSupport(world.getBlockAt(x, y - 1, z))) {
                continue;
            }
            return new Location(world, x + 0.5D, y, z + 0.5D, yaw, pitch);
        }
        return null;
    }

    private static boolean isBlocked(World world, java.util.Set<BlockPos> occupied, int x, int y, int z) {
        if (occupied.contains(new BlockPos(world.getUID(), x, y, z))) {
            return true;
        }
        return isSupport(world.getBlockAt(x, y, z));
    }

    private record PastePlan(List<Map<Location, BlockData>> layers, Location stand) {
    }

    private static BlockData toFullBlock(BlockData data) {
        if (data == null || !(data instanceof Slab)) {
            return data;
        }
        Material full = fullBlockForSlab(data.getMaterial());
        return Bukkit.createBlockData(full);
    }

    private static Material fullBlockForSlab(Material slab) {
        if (slab == null) {
            return Material.SANDSTONE;
        }
        String name = slab.name();
        if (!name.endsWith("_SLAB")) {
            return Material.SANDSTONE;
        }
        String base = name.substring(0, name.length() - "_SLAB".length());
        Material mapped = switch (base) {
            case "OAK", "SPRUCE", "BIRCH", "JUNGLE", "ACACIA", "DARK_OAK",
                 "MANGROVE", "CHERRY", "BAMBOO", "CRIMSON", "WARPED", "PALE_OAK" ->
                Material.getMaterial(base + "_PLANKS");
            case "PETRIFIED_OAK" -> Material.OAK_PLANKS;
            case "BRICK" -> Material.BRICKS;
            case "STONE_BRICK" -> Material.STONE_BRICKS;
            case "MOSSY_STONE_BRICK" -> Material.MOSSY_STONE_BRICKS;
            case "NETHER_BRICK" -> Material.NETHER_BRICKS;
            case "RED_NETHER_BRICK" -> Material.RED_NETHER_BRICKS;
            case "END_STONE_BRICK" -> Material.END_STONE_BRICKS;
            case "PRISMARINE_BRICK" -> Material.PRISMARINE_BRICKS;
            case "MUD_BRICK" -> Material.MUD_BRICKS;
            case "TUFF_BRICK" -> Material.TUFF_BRICKS;
            case "DEEPSLATE_BRICK" -> Material.DEEPSLATE_BRICKS;
            case "DEEPSLATE_TILE" -> Material.DEEPSLATE_TILES;
            case "POLISHED_BLACKSTONE_BRICK" -> Material.POLISHED_BLACKSTONE_BRICKS;
            case "QUARTZ" -> Material.QUARTZ_BLOCK;
            case "PURPUR" -> Material.PURPUR_BLOCK;
            case "BAMBOO_MOSAIC" -> Material.BAMBOO_MOSAIC;
            default -> null;
        };
        if (mapped != null && mapped.isBlock()) {
            return mapped;
        }
        Material direct = Material.getMaterial(base);
        if (direct != null && direct.isBlock() && !direct.name().contains("SLAB")) {
            return direct;
        }
        Material planks = Material.getMaterial(base + "_PLANKS");
        if (planks != null) {
            return planks;
        }
        if (base.endsWith("_BRICK")) {
            Material bricks = Material.getMaterial(base + "S");
            if (bricks != null) {
                return bricks;
            }
        }
        return Material.SANDSTONE;
    }

    private static File findSchematic(HollyCore plugin, String name) {
        String base = name.endsWith(".schem") || name.endsWith(".schematic")
            ? name.substring(0, name.lastIndexOf('.'))
            : name;
        String[] fileNames = {base + ".schem", base + ".schematic", name};
        File[] folders = {
            new File(plugin.getDataFolder(), "addons/oghub-customitems/schematics"),
            new File(plugin.getDataFolder(), "schematics"),
            pluginFolder("FastAsyncWorldEdit", "schematics"),
            pluginFolder("WorldEdit", "schematics")
        };
        for (File folder : folders) {
            if (folder == null || !folder.isDirectory()) {
                continue;
            }
            for (String fileName : fileNames) {
                File file = new File(folder, fileName);
                if (file.isFile()) {
                    return file;
                }
            }
        }
        return null;
    }

    private static File pluginFolder(String pluginName, String child) {
        org.bukkit.plugin.Plugin other = Bukkit.getPluginManager().getPlugin(pluginName);
        return other == null ? null : new File(other.getDataFolder(), child);
    }

    static final class Animation {
        private static final BlockFace[] NEIGHBORS = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN
        };

        private final HollyCore plugin;
        private final Location origin;
        private final List<Player> viewers;
        private final List<Map<Location, BlockData>> layers;
        private final Location stand;
        private final Map<BlockPos, BlockData> shown = new ConcurrentHashMap<>();
        private UUID boundsWorld;
        private int minX = Integer.MAX_VALUE;
        private int minY = Integer.MAX_VALUE;
        private int minZ = Integer.MAX_VALUE;
        private int maxX = Integer.MIN_VALUE;
        private int maxY = Integer.MIN_VALUE;
        private int maxZ = Integer.MIN_VALUE;
        private BukkitTask task;
        private int nextLayer;
        private boolean undoing;

        private Animation(
            HollyCore plugin,
            Location origin,
            Collection<Player> viewers,
            List<Map<Location, BlockData>> layers,
            Location stand
        ) {
            this.plugin = plugin;
            this.origin = origin;
            this.viewers = viewers == null ? List.of() : new ArrayList<>(viewers);
            this.layers = layers;
            this.stand = stand != null ? stand.clone() : origin.clone();
            for (Map<Location, BlockData> layer : layers) {
                if (layer == null) {
                    continue;
                }
                for (Location location : layer.keySet()) {
                    if (location != null && location.getWorld() != null) {
                        includeBounds(location);
                    }
                }
            }
        }

        private void start() {
            task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (nextLayer >= layers.size()) {
                    stopTask();
                    return;
                }
                sendFakeLayer(layers.get(nextLayer));
                nextLayer++;
            }, 0L, 1L);
        }

        Location standLocation() {
            return stand.clone();
        }

        boolean isUndoing() {
            return undoing;
        }

        boolean isShown(Location location) {
            return getShown(location) != null;
        }

        BlockData getShown(Location location) {
            if (location == null || location.getWorld() == null) {
                return null;
            }
            return getShown(location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        }

        BlockData getShown(World world, int x, int y, int z) {
            if (world == null) {
                return null;
            }
            return shown.get(new BlockPos(world.getUID(), x, y, z));
        }

        boolean contains(Location location, double padding) {
            if (undoing || location == null || location.getWorld() == null || boundsWorld == null) {
                return false;
            }
            if (minX == Integer.MAX_VALUE || !boundsWorld.equals(location.getWorld().getUID())) {
                return false;
            }
            double pad = Math.max(0.0, padding);
            return location.getX() >= minX - pad
                && location.getX() <= maxX + 1.0 + pad
                && location.getY() >= minY - pad
                && location.getY() <= maxY + 2.0 + pad
                && location.getZ() >= minZ - pad
                && location.getZ() <= maxZ + 1.0 + pad;
        }

        Location pushOut(Location location) {
            if (location == null || location.getWorld() == null || !contains(location, 0.0)) {
                return location;
            }
            double x = location.getX();
            double y = location.getY();
            double z = location.getZ();
            double left = x - (minX - 0.9);
            double right = (maxX + 1.9) - x;
            double bottom = y - (minY - 0.9);
            double top = (maxY + 2.4) - y;
            double north = z - (minZ - 0.9);
            double south = (maxZ + 1.9) - z;
            double best = Math.min(left, Math.min(right, Math.min(north, Math.min(south, Math.min(bottom, top)))));
            if (best == left) {
                x = minX - 0.9;
            } else if (best == right) {
                x = maxX + 1.9;
            } else if (best == north) {
                z = minZ - 0.9;
            } else if (best == south) {
                z = maxZ + 1.9;
            } else if (best == bottom) {
                y = minY - 0.9;
            } else {
                y = maxY + 2.4;
            }
            return new Location(location.getWorld(), x, y, z, location.getYaw(), location.getPitch());
        }

        Location rayTrace(Player player, double maxDistance) {
            if (player == null || shown.isEmpty() || player.getWorld() == null) {
                return null;
            }
            Location eye = player.getEyeLocation();
            Vector direction = eye.getDirection();
            if (direction.lengthSquared() < 1.0E-6) {
                return null;
            }
            direction.normalize();
            UUID worldId = player.getWorld().getUID();
            int lastX = Integer.MIN_VALUE;
            int lastY = Integer.MIN_VALUE;
            int lastZ = Integer.MIN_VALUE;
            for (double distance = 0.0; distance <= maxDistance; distance += 0.1) {
                int x = (int) Math.floor(eye.getX() + direction.getX() * distance);
                int y = (int) Math.floor(eye.getY() + direction.getY() * distance);
                int z = (int) Math.floor(eye.getZ() + direction.getZ() * distance);
                if (x == lastX && y == lastY && z == lastZ) {
                    continue;
                }
                lastX = x;
                lastY = y;
                lastZ = z;
                if (shown.containsKey(new BlockPos(worldId, x, y, z))) {
                    if (!player.getWorld().getBlockAt(x, y, z).getType().isAir()) {
                        continue;
                    }
                    return new Location(player.getWorld(), x, y, z);
                }
            }
            return null;
        }

        void resend() {
            if (shown.isEmpty() || undoing) {
                return;
            }
            sendPackets(toLocationMap());
        }

        void resend(Player player, Location location) {
            if (player == null || !player.isOnline() || location == null || location.getWorld() == null || undoing) {
                return;
            }
            BlockData data = getShown(location);
            if (data == null || !location.getBlock().getType().isAir()) {
                return;
            }
            player.sendBlockChange(new Location(location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ()), data);
        }

        void resendAround(Player player, Location location) {
            if (location == null) {
                return;
            }
            resend(player, location);
            for (BlockFace face : NEIGHBORS) {
                resend(player, location.clone().add(face.getModX(), face.getModY(), face.getModZ()));
            }
        }

        void undo() {
            if (undoing) {
                return;
            }
            undoing = true;
            stopTask();

            TreeMap<Integer, Map<Location, BlockData>> reverse = new TreeMap<>(Collections.reverseOrder());
            shown.forEach((pos, ignored) -> {
                World world = Bukkit.getWorld(pos.worldId());
                if (world == null) {
                    return;
                }
                Location location = new Location(world, pos.x(), pos.y(), pos.z());
                reverse.computeIfAbsent(location.getBlockY(), key -> new LinkedHashMap<>())
                    .put(location, location.getBlock().getBlockData().clone());
            });
            shown.clear();
            Iterator<Map<Location, BlockData>> iterator = reverse.values().iterator();
            if (!iterator.hasNext()) {
                return;
            }
            task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!iterator.hasNext()) {
                    stopTask();
                    return;
                }
                sendPackets(iterator.next());
            }, 0L, 1L);
        }

        private void sendFakeLayer(Map<Location, BlockData> layer) {
            Map<Location, BlockData> packets = new LinkedHashMap<>();
            layer.forEach((location, data) -> {
                if (location.getWorld() == null || data == null) {
                    return;
                }
                shown.put(BlockPos.of(location), data);
                includeBounds(location);
                if (!location.getBlock().getType().isAir()) {
                    return;
                }
                packets.put(location, data);
            });
            sendPackets(packets);
        }

        private void includeBounds(Location location) {
            boundsWorld = location.getWorld().getUID();
            int x = location.getBlockX();
            int y = location.getBlockY();
            int z = location.getBlockZ();
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }

        private Map<Location, BlockData> toLocationMap() {
            Map<Location, BlockData> packets = new LinkedHashMap<>();
            shown.forEach((pos, data) -> {
                if (data == null) {
                    return;
                }
                World world = Bukkit.getWorld(pos.worldId());
                if (world == null) {
                    return;
                }
                if (!world.getBlockAt(pos.x(), pos.y(), pos.z()).getType().isAir()) {
                    return;
                }
                packets.put(new Location(world, pos.x(), pos.y(), pos.z()), data);
            });
            return packets;
        }

        private void sendPackets(Map<Location, BlockData> packets) {
            if (packets.isEmpty()) {
                return;
            }
            for (Player player : viewers) {
                if (player != null && player.isOnline()) {
                    player.sendMultiBlockChange(packets);
                }
            }
        }

        private void stopTask() {
            if (task != null) {
                task.cancel();
                task = null;
            }
        }
    }

    private record BlockPos(UUID worldId, int x, int y, int z) {
        private static BlockPos of(Location location) {
            return new BlockPos(location.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        }
    }
}
