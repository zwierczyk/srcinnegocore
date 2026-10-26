package dev.grzybalson.modules.antigrief;

import dev.grzybalson.HollyCore;
import dev.grzybalson.anarchiagg.customitems.util.EventItemRegistry;
import dev.grzybalson.config.HollyDataPaths;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AntiGriefManager implements Listener {

    private static final String CONFIG_RESOURCE = "addons/antigrief/antigrief.yml";

    private final HollyCore plugin;
    private final File configFile;
    private final Map<String, TrackedBlock> trackedBlocks = new ConcurrentHashMap<>();

    private YamlConfiguration config;
    private Set<Material> allowedBlocks = EnumSet.noneOf(Material.class);
    private boolean enabled;
    private boolean useZoneBoxAllowedBlocks;
    private boolean creativeBypass;
    private String bypassPermission;
    private String blockedMessage;
    private long removeAfterTicks;
    private boolean trackBypassAllowedBlocks;
    private boolean removeTrackedOnShutdown;

    public AntiGriefManager(HollyCore plugin) {
        this.plugin = plugin;
        HollyDataPaths.ensureResource(plugin, CONFIG_RESOURCE);
        this.configFile = new File(plugin.getDataFolder(), CONFIG_RESOURCE);
        reload();
    }

    public void reload() {
        this.config = YamlConfiguration.loadConfiguration(configFile);
        this.enabled = config.getBoolean("enabled", true);
        this.useZoneBoxAllowedBlocks = config.getBoolean("use-zonebox-allowed-blocks", true);
        this.creativeBypass = config.getBoolean("creative-bypass", true);
        this.bypassPermission = config.getString("bypass-permission", "Holly.antigrief.bypass");
        this.blockedMessage = config.getString("messages.blocked", "&#FF0000✘ &8Ξ &cNie mozesz postawic tego bloku.");
        this.removeAfterTicks = Math.max(1L, config.getLong("remove-after-seconds", 300L)) * 20L;
        this.trackBypassAllowedBlocks = config.getBoolean("track-bypass-allowed-blocks", true);
        this.removeTrackedOnShutdown = config.getBoolean("remove-tracked-on-shutdown", true);
        this.allowedBlocks = loadAllowedBlocks();
    }

    public void shutdown() {
        if (removeTrackedOnShutdown) {
            for (TrackedBlock trackedBlock : trackedBlocks.values()) {
                removeTrackedBlock(trackedBlock);
            }
        }
        trackedBlocks.clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!enabled) {
            return;
        }

        Player player = event.getPlayer();
        Material material = event.getBlockPlaced().getType();

        if (isCudownaLatarniaItem(event.getItemInHand())) {
            return;
        }

        // Creative/spectator: zero tracking, zero zonebox/antigrief — bloki zostają na stałe.
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        if (canBypass(player)) {
            if (trackBypassAllowedBlocks && isAllowedToPlace(material)) {
                trackTemporaryBlock(event.getBlockPlaced(), material);
            }
            return;
        }

        if (!isAllowedToPlace(material)) {
            event.setCancelled(true);
            player.sendMessage(ColorUtils.colorizeComponent(blockedMessage));
            return;
        }

        trackTemporaryBlock(event.getBlockPlaced(), material);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        trackedBlocks.remove(blockKey(event.getBlock().getLocation()));
    }

    private boolean isCudownaLatarniaItem(ItemStack item) {
        return plugin.isAddonEnabled("anarchiagg")
            && item != null
            && item.getType() == Material.BEACON
            && EventItemRegistry.matches(plugin, "cudownalatarnia", item);
    }

    private boolean canBypass(Player player) {
        return (creativeBypass && player.getGameMode() == GameMode.CREATIVE)
            || (bypassPermission != null && !bypassPermission.isBlank() && player.hasPermission(bypassPermission));
    }

    private boolean isAllowedToPlace(Material material) {
        if (material == null || !material.isBlock()) {
            return false;
        }

        if (plugin.getZoneBoxManager() != null && plugin.getZoneBoxManager().isBlockEconomyBlock(material)) {
            return false;
        }

        if (useZoneBoxAllowedBlocks && plugin.getZoneBoxManager() != null) {
            return plugin.getZoneBoxManager().isAllowedBlock(material);
        }

        return allowedBlocks.contains(material);
    }

    private void trackTemporaryBlock(Block block, Material material) {
        Location location = block.getLocation();
        String key = blockKey(location);
        long removeAt = System.currentTimeMillis() + (removeAfterTicks * 50L);
        TrackedBlock trackedBlock = new TrackedBlock(location.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ(), material, removeAt);
        trackedBlocks.put(key, trackedBlock);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            TrackedBlock currentTrackedBlock = trackedBlocks.get(key);
            if (currentTrackedBlock == null || currentTrackedBlock.removeAt() != removeAt) {
                return;
            }

            removeTrackedBlock(currentTrackedBlock);
            trackedBlocks.remove(key, currentTrackedBlock);
        }, removeAfterTicks);
    }

    private void removeTrackedBlock(TrackedBlock trackedBlock) {
        World world = Bukkit.getWorld(trackedBlock.worldId());
        if (world == null) {
            return;
        }

        Block currentBlock = world.getBlockAt(trackedBlock.x(), trackedBlock.y(), trackedBlock.z());
        if (currentBlock.getType() == trackedBlock.material()) {
            currentBlock.setType(Material.AIR);
        }
    }

    private Set<Material> loadAllowedBlocks() {
        EnumSet<Material> materials = EnumSet.noneOf(Material.class);
        for (String rawMaterial : config.getStringList("allowed-blocks")) {
            Material material = parseMaterial(rawMaterial);
            if (material != null) {
                materials.add(material);
            }
        }
        return materials;
    }

    private Material parseMaterial(String rawMaterial) {
        if (rawMaterial == null || rawMaterial.isBlank()) {
            return null;
        }

        Material material = Material.matchMaterial(rawMaterial.trim().toUpperCase(Locale.ROOT));
        if (material == null || !material.isBlock()) {
            plugin.getLogger().warning("Nieprawidlowy blok w antigrief configu: " + rawMaterial);
            return null;
        }
        return material;
    }

    private String blockKey(Location location) {
        return location.getWorld().getUID() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }

    private record TrackedBlock(UUID worldId, int x, int y, int z, Material material, long removeAt) {
    }
}
