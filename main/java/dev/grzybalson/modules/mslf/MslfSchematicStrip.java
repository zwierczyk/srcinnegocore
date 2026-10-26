package dev.grzybalson.modules.mslf;

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
import org.bukkit.block.data.BlockData;

import java.io.File;
import java.io.FileInputStream;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Wklejenie całego schematu zamku (jak HOLLY-SKYPVPCUSTOMITEMS): origin, nadpisanie terenu, powietrze od Y origin w górę.
 */
public final class MslfSchematicStrip {

    private MslfSchematicStrip() {
    }

    /**
     * Prostopadloscian, ktory zajmie (albo juz zajmuje) zamek.
     * Liczony PRZED wklejeniem, zeby dalo sie odrzucic zamek nachodzacy na inny zamek.
     */
    public record CastleBox(World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

        /** Czy oba zamki na siebie nachodza, zachowujac {@code gap} kratek luzu miedzy nimi. */
        public boolean intersects(CastleBox other, int gap) {
            if (other == null || world == null || other.world == null || !world.equals(other.world)) {
                return false;
            }
            return minX - gap <= other.maxX && maxX + gap >= other.minX
                && minY - gap <= other.maxY && maxY + gap >= other.minY
                && minZ - gap <= other.maxZ && maxZ + gap >= other.minZ;
        }
    }

    /** Zasieg, jaki zajmie zamek wklejony w {@code center} — bez dotykania swiata. */
    public static CastleBox planBox(HollyCore plugin, Location center, String schematicName) {
        if (plugin == null || center == null || center.getWorld() == null) {
            return null;
        }
        File file = findSchematic(plugin, schematicName);
        if (file != null) {
            CastleBox box = readSchematicBox(file, center);
            if (box != null) {
                return box;
            }
        }
        return fallbackBox(center);
    }

    private static CastleBox readSchematicBox(File schemFile, Location center) {
        try {
            ClipboardFormat format = ClipboardFormats.findByFile(schemFile);
            if (format == null) {
                return null;
            }
            try (ClipboardReader reader = format.getReader(new FileInputStream(schemFile))) {
                Clipboard clipboard = reader.read();
                BlockVector3 origin = clipboard.getOrigin();
                BlockVector3 min = clipboard.getMinimumPoint();
                BlockVector3 max = clipboard.getMaximumPoint();
                int cx = center.getBlockX();
                int cy = center.getBlockY();
                int cz = center.getBlockZ();
                int minX = Integer.MAX_VALUE;
                int minY = Integer.MAX_VALUE;
                int minZ = Integer.MAX_VALUE;
                int maxX = Integer.MIN_VALUE;
                int maxY = Integer.MIN_VALUE;
                int maxZ = Integer.MIN_VALUE;
                for (int x = min.getBlockX(); x <= max.getBlockX(); x++) {
                    for (int y = min.getBlockY(); y <= max.getBlockY(); y++) {
                        for (int z = min.getBlockZ(); z <= max.getBlockZ(); z++) {
                            int relX = x - origin.getBlockX();
                            int relY = y - origin.getBlockY();
                            int relZ = z - origin.getBlockZ();
                            // Ta sama zasada co przy wklejaniu — pusty margines schematu nie liczy sie do zamku.
                            if (relY < 0 && clipboard.getFullBlock(BlockVector3.at(x, y, z))
                                .getBlockType().getMaterial().isAir()) {
                                continue;
                            }
                            minX = Math.min(minX, cx + relX);
                            maxX = Math.max(maxX, cx + relX);
                            minY = Math.min(minY, cy + relY);
                            maxY = Math.max(maxY, cy + relY);
                            minZ = Math.min(minZ, cz + relZ);
                            maxZ = Math.max(maxZ, cz + relZ);
                        }
                    }
                }
                if (minX > maxX) {
                    return null;
                }
                return new CastleBox(center.getWorld(), minX, minY, minZ, maxX, maxY, maxZ);
            }
        } catch (Throwable throwable) {
            return null;
        }
    }

    private static CastleBox fallbackBox(Location center) {
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        return new CastleBox(center.getWorld(), cx - 2, cy, cz - 2, cx + 2, cy + 3, cz + 2);
    }

    /**
     * @param reserved bloki nalezace juz do innego zamku — pomijane, zeby dwa zamki nigdy nie
     *                 trzymaly stanu tej samej kratki (inaczej po wygasnieciu zostaje piasek).
     */
    public static void pasteOrFallback(HollyCore plugin, Location center, String schematicName,
                                       Map<Location, BlockData> originalBlocks, Predicate<Location> reserved) {
        if (plugin == null || center == null || center.getWorld() == null || originalBlocks == null) {
            return;
        }
        File file = findSchematic(plugin, schematicName);
        boolean pasted = false;
        if (file != null) {
            pasted = pasteWorldEditSchematic(plugin, file, center, originalBlocks, reserved);
        }
        if (!pasted) {
            if (file == null) {
                plugin.getLogger().warning("[MSLF] Nie znaleziono schematu zamku — uzywam zamku zapasowego.");
            } else {
                plugin.getLogger().warning("[MSLF] Schemat " + file.getName()
                    + " nie zostal wklejony — uzywam zamku zapasowego.");
            }
            buildFallbackCastle(center, originalBlocks, reserved);
        }
    }

    private static boolean pasteWorldEditSchematic(HollyCore plugin, File schemFile, Location center,
                                                   Map<Location, BlockData> originalBlocks, Predicate<Location> reserved) {
        try {
            ClipboardFormat format = ClipboardFormats.findByFile(schemFile);
            if (format == null) {
                plugin.getLogger().warning("[MSLF] Nieobslugiwany format schematu zamku: " + schemFile.getName());
                return false;
            }
            BlockData airData = Bukkit.createBlockData(Material.AIR);
            World world = center.getWorld();
            int cx = center.getBlockX();
            int cy = center.getBlockY();
            int cz = center.getBlockZ();
            try (ClipboardReader reader = format.getReader(new FileInputStream(schemFile))) {
                Clipboard clipboard = reader.read();
                BlockVector3 origin = clipboard.getOrigin();
                BlockVector3 min = clipboard.getMinimumPoint();
                BlockVector3 max = clipboard.getMaximumPoint();
                for (int x = min.getBlockX(); x <= max.getBlockX(); x++) {
                    for (int y = min.getBlockY(); y <= max.getBlockY(); y++) {
                        for (int z = min.getBlockZ(); z <= max.getBlockZ(); z++) {
                            BlockVector3 vec = BlockVector3.at(x, y, z);
                            BaseBlock baseBlock = clipboard.getFullBlock(vec);
                            int relX = x - origin.getBlockX();
                            int relY = y - origin.getBlockY();
                            int relZ = z - origin.getBlockZ();
                            Location targetLoc = new Location(world, cx + relX, cy + relY, cz + relZ);
                            boolean air = baseBlock.getBlockType().getMaterial().isAir();
                            if (air && relY < 0) {
                                continue;
                            }
                            Block block = targetLoc.getBlock();
                            if (isUnbreakable(block.getType())) {
                                continue;
                            }
                            if (reserved != null && reserved.test(targetLoc)) {
                                continue;
                            }
                            originalBlocks.putIfAbsent(targetLoc, block.getBlockData());
                            if (air) {
                                block.setBlockData(airData, false);
                            } else {
                                BlockData newData = BukkitAdapter.adapt(baseBlock);
                                if (newData != null) {
                                    block.setBlockData(newData, false);
                                }
                            }
                        }
                    }
                }
                return true;
            }
        } catch (Throwable throwable) {
            plugin.getLogger().log(java.util.logging.Level.WARNING,
                "[MSLF] Blad wklejania schematu zamku " + schemFile.getName(), throwable);
            return false;
        }
    }

    static void buildFallbackCastle(Location center, Map<Location, BlockData> originalBlocks, Predicate<Location> reserved) {
        BlockData sandstone = Bukkit.createBlockData(Material.SMOOTH_SANDSTONE);
        BlockData cutSandstone = Bukkit.createBlockData(Material.CUT_SANDSTONE);
        BlockData airData = Bukkit.createBlockData(Material.AIR);
        World world = center.getWorld();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                for (int y = 0; y <= 3; y++) {
                    boolean isWall = x == -2 || x == 2 || z == -2 || z == 2;
                    boolean isCorner = Math.abs(x) == 2 && Math.abs(z) == 2;
                    Location loc = new Location(world, cx + x, cy + y, cz + z);
                    Block block = loc.getBlock();
                    if (isUnbreakable(block.getType())) {
                        continue;
                    }
                    if (reserved != null && reserved.test(loc)) {
                        continue;
                    }
                    originalBlocks.putIfAbsent(loc, block.getBlockData());
                    if (y == 0) {
                        block.setBlockData(sandstone, false);
                    } else if (y <= 2 && isWall) {
                        block.setBlockData(sandstone, false);
                    } else if (y == 3 && isCorner) {
                        block.setBlockData(cutSandstone, false);
                    } else {
                        block.setBlockData(airData, false);
                    }
                }
            }
        }
    }

    static boolean isUnbreakable(Material type) {
        if (type == null) {
            return true;
        }
        return type == Material.BEDROCK
            || type == Material.BARRIER
            || type == Material.COMMAND_BLOCK
            || type == Material.CHAIN_COMMAND_BLOCK
            || type == Material.REPEATING_COMMAND_BLOCK
            || type == Material.STRUCTURE_BLOCK
            || type == Material.STRUCTURE_VOID
            || type == Material.JIGSAW
            || type == Material.END_PORTAL
            || type == Material.END_PORTAL_FRAME
            || type == Material.END_GATEWAY
            || type == Material.MOVING_PISTON;
    }

    static boolean isReplaceable(Material type) {
        if (type == null || type.isAir() || type == Material.CAVE_AIR || type == Material.VOID_AIR) {
            return true;
        }
        if (type == Material.SNOW || type == Material.FIRE || type == Material.SOUL_FIRE) {
            return true;
        }
        String name = type.name();
        if (name.endsWith("_CARPET") || name.endsWith("_BUTTON") || name.endsWith("_PRESSURE_PLATE")) {
            return true;
        }
        return !type.isSolid();
    }

    static Location blockLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return location;
        }
        return new Location(location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    static File findSchematic(HollyCore plugin, String name) {
        if (name == null || name.isBlank()) {
            name = "zamek";
        }
        String base = name.contains(".") ? name.substring(0, name.lastIndexOf('.')) : name;
        String[] fileNames = {base + ".schem", base + ".schematic", name, "zamek.schem"};
        File dataParent = plugin.getDataFolder().getParentFile();
        File[] folders = {
            new File(plugin.getDataFolder(), "addons/mslf/schematics"),
            new File(plugin.getDataFolder(), "schematics"),
            pluginFolder("FastAsyncWorldEdit", "schematics"),
            pluginFolder("WorldEdit", "schematics"),
            dataParent == null ? null : new File(dataParent, "FastAsyncWorldEdit/schematics"),
            dataParent == null ? null : new File(dataParent, "WorldEdit/schematics"),
            plugin.getDataFolder()
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
}
