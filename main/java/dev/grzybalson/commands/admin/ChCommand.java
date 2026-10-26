package dev.grzybalson.commands.admin;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.HollyDataPaths;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.SpooferCountHelper;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.TabCompleter;
import org.bukkit.Location;

public class ChCommand implements CommandExecutor, TabCompleter {

    private static final DateTimeFormatter UPDATE_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final HollyCore plugin;
    private final File configFile;

    public ChCommand(HollyCore plugin) {
        this.plugin = plugin;
        this.configFile = new File(HollyDataPaths.getAddonFolder(plugin, "commands"), "sectors.yml");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("Holly.cmd.ch") && !sender.hasPermission("holly.cmd.sector") && !sender.isOp()) {
            sender.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.ch"));
            return true;
        }

        if (args.length > 0) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("check") || sub.equals("sprawdz") || sub.equals("gracze")) {
                handleCheck(sender);
                return true;
            }
            if (sub.equals("status") || sub.equals("info")) {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ColorUtils.error("GUI statusu sektorow jest tylko dla graczy."));
                    return true;
                }
                if (plugin.getSectorService() != null) {
                    plugin.getSectorService().openStatusGui(player);
                } else {
                    player.sendMessage(ColorUtils.error("System sektorow jest wylaczony."));
                }
                return true;
            }
        }

        if (!(sender instanceof Player player)) {
            handleCheck(sender);
            return true;
        }

        if (plugin.getSectorService() != null) {
            plugin.getSectorService().openGui(player);
            return true;
        }

        player.openInventory(createInventory());
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> list = new ArrayList<>();
            for (String s : List.of("check", "gui", "status", "gracze", "info")) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    list.add(s);
                }
            }
            return list;
        }
        return List.of();
    }

    private void handleCheck(CommandSender sender) {
        int total = Bukkit.getOnlinePlayers().size();
        int afk = 0;
        int spawn = 0;
        int pvp = 0;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isPlayerInAfk(player)) {
                afk++;
            } else if (isPlayerInSpawn(player)) {
                spawn++;
            } else {
                pvp++;
            }
        }

        int afkPercent = total > 0 ? (afk * 100 / total) : 0;
        int spawnPercent = total > 0 ? (spawn * 100 / total) : 0;
        int pvpPercent = total > 0 ? (pvp * 100 / total) : 0;

        sender.sendMessage(ColorUtils.colorizeComponent("&8&m--------------------------------------------------"));
        sender.sendMessage(ColorUtils.colorizeComponent("&#00E0FFℹ &8Ξ &#00E0FF&lSTATUS SEKTORA (ROZMIESZCZENIE GRACZY):"));
        sender.sendMessage(ColorUtils.colorizeComponent("&8▪ &7Wszyscy gracze online: &f" + total));
        sender.sendMessage(ColorUtils.colorizeComponent("&8▪ &6Strefa AFK: &#FFB300&l" + afk + " &8(&7" + afkPercent + "%&8)"));
        sender.sendMessage(ColorUtils.colorizeComponent("&8▪ &aStrefa Spawn: &#4EFA76&l" + spawn + " &8(&7" + spawnPercent + "%&8)"));
        sender.sendMessage(ColorUtils.colorizeComponent("&8▪ &cStrefa PvP: &#FF4D4D&l" + pvp + " &8(&7" + pvpPercent + "%&8)"));
        sender.sendMessage(ColorUtils.colorizeComponent("&8&m--------------------------------------------------"));
    }

    private boolean isPlayerInAfk(Player player) {
        Location loc = player.getLocation();
        if (plugin.getConfigManager() != null && plugin.getConfigManager().getAfkConfig() != null) {
            var zones = plugin.getConfigManager().getAfkConfig().getZones();
            if (zones != null) {
                for (var zone : zones.values()) {
                    if (zone != null && zone.isInside(loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isPlayerInSpawn(Player player) {
        Location loc = player.getLocation();

        // 1. WorldGuard check
        if (Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) {
            try {
                com.sk89q.worldguard.protection.managers.RegionManager rm =
                    com.sk89q.worldguard.WorldGuard.getInstance().getPlatform().getRegionContainer()
                        .get(com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(loc.getWorld()));
                if (rm != null) {
                    com.sk89q.worldedit.math.BlockVector3 pt = com.sk89q.worldedit.bukkit.BukkitAdapter.asBlockVector(loc);
                    com.sk89q.worldguard.protection.ApplicableRegionSet set = rm.getApplicableRegions(pt);
                    for (com.sk89q.worldguard.protection.regions.ProtectedRegion reg : set) {
                        String id = reg.getId().toLowerCase(Locale.ROOT);
                        if (id.contains("spawn") || id.contains("safezone") || id.contains("lobby")) {
                            return true;
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        // 2. SpawnConfig radius fallback
        if (plugin.getConfigManager() != null && plugin.getConfigManager().getSpawnConfig() != null) {
            var spawnCfg = plugin.getConfigManager().getSpawnConfig();
            if (spawnCfg.isSpawnSet() && loc.getWorld().getName().equalsIgnoreCase(spawnCfg.getWorld())) {
                double dx = loc.getX() - spawnCfg.getX();
                double dz = loc.getZ() - spawnCfg.getZ();
                if ((dx * dx + dz * dz) <= 120 * 120) {
                    return true;
                }
            }
        }

        return false;
    }

    private Inventory createInventory() {
        YamlConfiguration config = loadSectorsConfig();
        int rows = Math.max(1, Math.min(6, config.getInt("inventory.rows", 5)));
        String title = config.getString("inventory.title", "&7Kanaly");
        Inventory inventory = Bukkit.createInventory(new ChannelGuiHolder(), rows * 9, ColorUtils.colorizeComponent(title));

        applyFillers(inventory, config);
        int slot = Math.max(0, Math.min(inventory.getSize() - 1, getInt(config, "startChannelSlot", "start-channel-slot", 20)));
        inventory.setItem(slot, createSectorItem(config));
        return inventory;
    }

    private YamlConfiguration loadSectorsConfig() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        try (InputStream stream = plugin.getResource("addons/commands/sectors.yml")) {
            if (stream != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
                config.setDefaults(defaults);
            }
        } catch (Exception ignored) {
        }
        return config;
    }

    private void applyFillers(Inventory inventory, YamlConfiguration config) {
        for (Map<?, ?> entry : config.getMapList("filler")) {
            Object itemRaw = entry.get("item");
            if (!(itemRaw instanceof Map<?, ?> itemMap)) {
                continue;
            }

            Object materialRaw = itemMap.get("material");
            Material material = getMaterial(materialRaw == null ? "GRAY_STAINED_GLASS_PANE" : String.valueOf(materialRaw));
            ItemStack item = createItem(material, " ", List.of());

            Object slotsRaw = entry.get("slot");
            if (slotsRaw instanceof List<?> slots) {
                for (Object rawSlot : slots) {
                    setFiller(inventory, rawSlot, item);
                }
            } else {
                setFiller(inventory, slotsRaw, item);
            }
        }
    }

    private void setFiller(Inventory inventory, Object rawSlot, ItemStack item) {
        if (!(rawSlot instanceof Number number)) {
            return;
        }
        int slot = number.intValue();
        if (slot >= 0 && slot < inventory.getSize()) {
            inventory.setItem(slot, item.clone());
        }
    }

    private ItemStack createSectorItem(YamlConfiguration config) {
        String sectorName = config.getString("sector-name", "Globalny");
        String tps = String.format(Locale.US, "%.2f", getTps());
        String online = SpooferCountHelper.getVisibleOnlineCount() + "/" + Bukkit.getMaxPlayers();
        String lastUpdate = LocalTime.now().format(UPDATE_FORMAT);

        String title = replacePlaceholders(getString(config, "sectorTitle", "sector-title", "&b> &fKanal: &#68CEFF{SECTORNAME}"), sectorName, tps, online, lastUpdate, "");
        String connect = getString(config, "sectorCantConnect", "sector-cant-connect", "&8> &cJestes juz polaczony z tym sektorem!");

        List<String> lore = new ArrayList<>();
        for (String line : getStringList(config, "sectorLore", "sector-lore")) {
            lore.add(replacePlaceholders(line, sectorName, tps, online, lastUpdate, connect));
        }

        return createItem(getMaterial(getString(config, "sectorMaterial", "sector-material", "CYAN_DYE")), title, lore);
    }

    private String getString(YamlConfiguration config, String primaryPath, String fallbackPath, String defaultValue) {
        return config.isSet(primaryPath) ? config.getString(primaryPath, defaultValue) : config.getString(fallbackPath, defaultValue);
    }

    private int getInt(YamlConfiguration config, String primaryPath, String fallbackPath, int defaultValue) {
        return config.isSet(primaryPath) ? config.getInt(primaryPath, defaultValue) : config.getInt(fallbackPath, defaultValue);
    }

    private List<String> getStringList(YamlConfiguration config, String primaryPath, String fallbackPath) {
        return config.isSet(primaryPath) ? config.getStringList(primaryPath) : config.getStringList(fallbackPath);
    }

    private String replacePlaceholders(String text, String sectorName, String tps, String online, String lastUpdate, String connect) {
        if (text == null) {
            return "";
        }
        return text
            .replace("{SECTORNAME}", sectorName)
            .replace("{TPS}", tps)
            .replace("{ONLINE}", online)
            .replace("{LASTUPDATE}", lastUpdate)
            .replace("{CONNECT}", connect == null ? "" : connect);
    }

    private ItemStack createItem(Material material, String name, List<String> loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.displayName(ColorUtils.colorizeComponent(name));
        List<Component> lore = new ArrayList<>();
        for (String line : loreLines) {
            lore.add(ColorUtils.colorizeComponent(line));
        }
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return item;
    }

    private Material getMaterial(String materialName) {
        Material material = Material.matchMaterial(materialName == null ? "" : materialName);
        return material == null ? Material.GRAY_STAINED_GLASS_PANE : material;
    }

    private double getTps() {
        try {
            Object server = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
            double[] recentTps = (double[]) server.getClass().getField("recentTps").get(server);
            return Math.min(20.0, recentTps[0]);
        } catch (Exception exception) {
            return 20.0;
        }
    }

    public static final class ChannelGuiHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
