package dev.grzybalson.modules.sectors;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.HollyDataPaths;
import dev.grzybalson.modules.diagnostics.ServerDiagnosticsService;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.NBTUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class SectorStatusGUI implements InventoryHolder {

    private final HollyCore plugin;
    private final SectorService service;
    private final Player player;
    private Inventory inventory;
    private YamlConfiguration config;

    public SectorStatusGUI(HollyCore plugin, SectorService service, Player player) {
        this.plugin = plugin;
        this.service = service;
        this.player = player;
        reloadConfig();
    }

    public void open() {
        int rows = Math.max(1, Math.min(6, config.getInt("gui.rows", 6)));
        inventory = Bukkit.createInventory(this, rows * 9, ColorUtils.colorizeComponent(config.getString("gui.title", "&8Status sektorow")));
        renderItems();
        player.openInventory(inventory);
    }

    public void handleClick(int slot) {
        ItemStack clicked = inventory.getItem(slot);
        if (clicked == null || clicked.getType().isAir()) {
            return;
        }
        String target = NBTUtils.getString(clicked, "sector_status_target");
        if (target == null || target.isBlank()) {
            return;
        }
        if (target.equalsIgnoreCase(service.getCurrentSector())) {
            player.sendMessage(ColorUtils.colorizeComponent(config.getString("messages.already-here", service.getConfig().getAlreadyHere())));
            return;
        }
        SectorInfo info = service.getSector(target);
        if (info == null || !info.isAlive()) {
            player.sendMessage(ColorUtils.colorizeComponent(config.getString("messages.offline", service.getConfig().getOffline())));
            return;
        }
        player.closeInventory();
        service.transfer(player, target);
    }

    private void renderItems() {
        List<SectorInfo> sectors = service.getSectorsForGui();
        List<Integer> slots = config.getIntegerList("gui.sector-slots");
        if (slots.isEmpty()) {
            slots = defaultSlots(inventory.getSize());
        }

        ServerDiagnosticsService.DiagnosticsSnapshot local = plugin.getServerDiagnosticsService() != null
            ? plugin.getServerDiagnosticsService().getLatest()
            : ServerDiagnosticsService.DiagnosticsSnapshot.empty();

        for (int index = 0; index < sectors.size() && index < slots.size(); index++) {
            int slot = slots.get(index);
            if (slot < 0 || slot >= inventory.getSize()) {
                continue;
            }
            SectorInfo info = sectors.get(index);
            boolean current = info.getName().equalsIgnoreCase(service.getCurrentSector());
            inventory.setItem(slot, createSectorItem(info, current, local));
        }

        applyFillers();
    }

    private ItemStack createSectorItem(SectorInfo info, boolean current, ServerDiagnosticsService.DiagnosticsSnapshot local) {
        boolean alive = info.isAlive();
        String path = !alive ? "items.offline" : current ? "items.current" : healthPath(info);
        Material material = Material.matchMaterial(config.getString(path + ".material", alive ? "LIME_TERRACOTTA" : "RED_TERRACOTTA"));
        if (material == null) {
            material = alive ? Material.LIME_TERRACOTTA : Material.RED_TERRACOTTA;
        }

        Map<String, String> placeholders = buildPlaceholders(info, local);
        String name = apply(config.getString(path + ".name", "&e&l{SECTORNAME}"), placeholders);
        List<String> loreTemplate = config.getStringList(path + ".lore");
        if (loreTemplate.isEmpty()) {
            loreTemplate = defaultLore();
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.displayName(ColorUtils.colorizeComponent(name).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        for (String line : loreTemplate) {
            String applied = apply(line, placeholders);
            if (applied == null || applied.isBlank()) {
                continue;
            }
            lore.add(ColorUtils.colorizeComponent(applied).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ITEM_SPECIFICS);
        item.setItemMeta(meta);
        if (alive && !current) {
            NBTUtils.setString(item, "sector_status_target", info.getName());
        }
        return item;
    }

    private Map<String, String> buildPlaceholders(SectorInfo info, ServerDiagnosticsService.DiagnosticsSnapshot local) {
        ServerDiagnosticsService.DiagnosticsSnapshot source = info.getName().equalsIgnoreCase(service.getCurrentSector())
            ? local
            : toSnapshot(info);
        Map<String, String> placeholders = source.placeholders(info.getName(), info.getLastUpdate());
        placeholders.put("{LOAD}", formatLoad(info.getLoad()));
        placeholders.put("{load}", formatLoad(info.getLoad()));
        placeholders.put("{HEALTH}", healthLabel(info));
        placeholders.put("{health}", healthLabel(info));
        return placeholders;
    }

    private ServerDiagnosticsService.DiagnosticsSnapshot toSnapshot(SectorInfo info) {
        List<String> lagSources = info.getLagSources().isEmpty() ? List.of("&7Brak danych diagnostycznych") : info.getLagSources();
        List<String> predictions = info.getPredictions().isEmpty() ? List.of("&7Brak prognozy") : info.getPredictions();
        return new ServerDiagnosticsService.DiagnosticsSnapshot(
            info.getTps(),
            info.getMspt(),
            info.getCpuPercent(),
            0.0D,
            info.getRamUsedGb(),
            info.getAvgPingMs(),
            info.getOnline(),
            info.getMaxPlayers(),
            info.getWorldName(),
            info.getBorderText(),
            info.getSectorType(),
            info.getChunks(),
            info.getEntities(),
            info.getItems(),
            info.getMonsters(),
            0,
            0,
            info.getWorldName(),
            info.getEntities(),
            lagSources,
            predictions,
            0L,
            0L,
            info.getLastUpdate()
        );
    }

    private void applyFillers() {
        for (Map<?, ?> entry : config.getMapList("gui.filler")) {
            Object itemRaw = entry.get("item");
            if (!(itemRaw instanceof Map<?, ?> itemMap)) {
                continue;
            }
            Material material = Material.matchMaterial(String.valueOf(itemMap.get("material")));
            if (material == null) {
                material = Material.GRAY_STAINED_GLASS_PANE;
            }
            ItemStack filler = new ItemStack(material);
            ItemMeta meta = filler.getItemMeta();
            if (meta != null) {
                meta.displayName(ColorUtils.colorizeComponent(" "));
                filler.setItemMeta(meta);
            }

            Object slotsRaw = entry.get("slot");
            if (slotsRaw instanceof List<?> slots) {
                for (Object rawSlot : slots) {
                    if (rawSlot instanceof Number number) {
                        int slot = number.intValue();
                        if (slot >= 0 && slot < inventory.getSize()) {
                            inventory.setItem(slot, filler.clone());
                        }
                    }
                }
            }
        }
    }

    private String healthPath(SectorInfo info) {
        if (!info.isAlive()) {
            return "items.offline";
        }
        if (info.getTps() < 18.5D || info.getMspt() >= 50.0D) {
            return "items.critical";
        }
        if (info.getTps() < 19.5D || info.getMspt() >= 40.0D) {
            return "items.warning";
        }
        return "items.online";
    }

    private String healthLabel(SectorInfo info) {
        if (!info.isAlive()) {
            return "&cOFFLINE";
        }
        if (info.getTps() < 18.5D || info.getMspt() >= 50.0D) {
            return "&cKRYTYCZNY";
        }
        if (info.getTps() < 19.5D || info.getMspt() >= 40.0D) {
            return "&eOSTRZEZENIE";
        }
        return "&aSTABILNY";
    }

    private static List<Integer> defaultSlots(int size) {
        List<Integer> slots = new ArrayList<>();
        for (int row = 1; row <= 4; row++) {
            for (int col = 1; col <= 7; col++) {
                int slot = row * 9 + col;
                if (slot < size) {
                    slots.add(slot);
                }
            }
        }
        return slots;
    }

    private static List<String> defaultLore() {
        return List.of(
            "",
            " &8× &7TPS: &a{TPS}",
            " &8× &7MSPT: &a{MSPT}",
            " &8× &7CPU: &b{CPU}",
            " &8× &7RAM: &e{RAM}",
            " &8× &7Sredni Ping: &d{PING}",
            " &8× &7Graczy Online: &6{ONLINE}",
            " &8× &7Ostatni ping: &6{LAST_PING}",
            " &8× &7Typ: &6{TYPE}",
            " &8× &7Swiat: &6{WORLD}",
            " &8× &7Border X: &a{BORDER}",
            "",
            " &8× &cDiagnoza lagu:",
            "  &8» {LAG_1}",
            "  &8» {LAG_2}",
            "  &8» {LAG_3}",
            "",
            " &8× &ePrognoza obciazenia:",
            "  &8» {PRED_1}",
            "  &8» {PRED_2}",
            "",
            " &aKliknij, aby przeteleportowac sie"
        );
    }

    private static String apply(String template, Map<String, String> placeholders) {
        if (template == null) {
            return "";
        }
        String result = template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
        }
        return result;
    }

    private static String formatLoad(double load) {
        if (load < 0) {
            return "0";
        }
        return String.format(Locale.US, "%.0f", Math.min(100.0, load * 100.0));
    }

    private void reloadConfig() {
        HollyDataPaths.ensureResource(plugin, "addons/sectors/sector-status.yml");
        File file = new File(HollyDataPaths.getAddonFolder(plugin, "sectors"), "sector-status.yml");
        config = YamlConfiguration.loadConfiguration(file);
        try (InputStream stream = plugin.getResource("addons/sectors/sector-status.yml")) {
            if (stream != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
                config.setDefaults(defaults);
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
