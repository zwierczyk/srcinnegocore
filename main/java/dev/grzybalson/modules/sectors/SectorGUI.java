package dev.grzybalson.modules.sectors;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.configs.SectorConfig;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.NBTUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SectorGUI implements InventoryHolder {

    private final HollyCore plugin;
    private final SectorService service;
    private final Player player;
    private Inventory inventory;

    public SectorGUI(HollyCore plugin, SectorService service, Player player) {
        this.plugin = plugin;
        this.service = service;
        this.player = player;
    }

    public void open() {
        SectorConfig config = service.getConfig();
        int size = config.getGuiRows() * 9;
        inventory = Bukkit.createInventory(this, size, ColorUtils.colorizeComponent(config.getGuiTitle()));

        renderItems();
        player.openInventory(inventory);
    }

    private void renderItems() {
        SectorConfig config = service.getConfig();
        List<SectorInfo> sectors = service.getSectorsForGui();
        int slot = config.getStartChannelSlot();

        for (SectorInfo info : sectors) {
            if (slot >= inventory.getSize()) {
                break;
            }
            inventory.setItem(slot, createSectorItem(config, info));
            slot++;
        }
    }

    public void handleClick(int slot) {
        ItemStack clicked = inventory.getItem(slot);
        if (clicked == null || clicked.getType().isAir()) {
            return;
        }
        String target = NBTUtils.getString(clicked, "sector_target");
        if (target == null || target.isBlank()) {
            return;
        }
        player.closeInventory();
        service.transfer(player, target);
    }

    private ItemStack createSectorItem(SectorConfig config, SectorInfo info) {
        boolean current = info.getName().equalsIgnoreCase(config.getCurrentSector());
        boolean alive = info.isAlive();

        SectorConfig.SectorGuiItemConfig template;
        if (current) {
            template = config.getCurrentItem();
        } else if (alive) {
            template = config.getOnlineItem();
        } else {
            template = config.getOfflineItem();
        }

        Material material = Material.matchMaterial(template.getMaterial());
        if (material == null) {
            material = Material.MINECART;
        }

        ItemStack item = new ItemStack(material, template.getAmount());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.displayName(ColorUtils.colorizeComponent(applyPlaceholders(template.getName(), info, alive))
            .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        for (String line : template.getLore()) {
            lore.addAll(ColorUtils.colorizeLore(applyPlaceholders(line, info, alive)));
        }
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ITEM_SPECIFICS);
        item.setItemMeta(meta);
        NBTUtils.setString(item, "sector_target", info.getName());
        return item;
    }

    private String applyPlaceholders(String text, SectorInfo info, boolean alive) {
        if (text == null) {
            return "";
        }
        String sectorName = info.getName();
        String online = alive ? String.valueOf(info.getOnline()) : "-";
        String tps = alive ? formatTps(info.getTps()) : "-";
        String load = alive ? formatLoad(info.getLoad()) : "-";

        return text
            .replace("{SECTORNAME}", sectorName)
            .replace("{sector}", sectorName)
            .replace("{ONLINE}", online)
            .replace("{online}", online)
            .replace("{TPS}", tps)
            .replace("{tps}", tps)
            .replace("{LOAD}", load)
            .replace("{load}", load);
    }

    private String formatTps(double tps) {
        double value = Math.min(Math.round(tps * 100.0) / 100.0, 20.0);
        return String.format(Locale.US, "%.1f", value);
    }

    private String formatLoad(double load) {
        if (load < 0) {
            return "0";
        }
        return String.format(Locale.US, "%.0f", Math.min(100.0, load * 100.0));
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
