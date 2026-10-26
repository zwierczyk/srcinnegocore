package dev.grzybalson.gui;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.modules.warps.WarpDataConfig;
import dev.grzybalson.config.modules.warps.WarpsConfig;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.NBTUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class WarpGUI implements InventoryHolder {

    private final HollyCore plugin;
    private final Player player;
    private Inventory inventory;

    public WarpGUI(HollyCore plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public void open() {
        WarpsConfig config = plugin.getModuleConfigManager().getWarpsConfig();

        inventory = Bukkit.createInventory(this, config.getGuiRows() * 9,
            ColorUtils.colorizeComponent(config.getGuiTitle()));

        plugin.getGuiFactory().applyDefaultTemplate(inventory);

        Map<String, WarpDataConfig.WarpEntry> warps = plugin.getWarpManager().getAllWarps();

        for (Map.Entry<String, WarpDataConfig.WarpEntry> entry : warps.entrySet()) {
            String warpName = entry.getKey();
            WarpDataConfig.WarpEntry warp = entry.getValue();

            ItemStack item = createWarpItem(warpName, warp);
            if (warp.getSlot() >= 0 && warp.getSlot() < inventory.getSize()) {
                inventory.setItem(warp.getSlot(), item);
            }
        }

        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
    }

    private ItemStack createWarpItem(String warpName, WarpDataConfig.WarpEntry warp) {
        Material material;
        try {
            material = Material.valueOf(warp.getItemMaterial().toUpperCase());
        } catch (IllegalArgumentException e) {
            material = Material.ENDER_PEARL;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String displayName = warp.getDisplayName() != null ? warp.getDisplayName() : "&e&l" + warpName.toUpperCase();
        meta.displayName(ColorUtils.colorizeComponent(displayName).decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        if (warp.getLore() != null) {
            for (String line : warp.getLore()) {
                lore.addAll(ColorUtils.colorizeLore(line));
            }
        }

        boolean hasPermission = warp.getPermission() == null
            || warp.getPermission().isEmpty()
            || player.hasPermission(warp.getPermission());

        WarpsConfig config = plugin.getModuleConfigManager().getWarpsConfig();
        if (hasPermission) {
            String clickLore = config.getWarpClickToTeleport();
            if (clickLore != null && !clickLore.isEmpty()) {
                lore.add(Component.empty());
                ColorUtils.appendLore(lore, clickLore);
            }
        } else {
            String noPermLore = config.getWarpNoPermissionLore();
            if (noPermLore != null && !noPermLore.isEmpty()) {
                lore.add(Component.empty());
                ColorUtils.appendLore(lore, noPermLore);
            }
        }

        meta.lore(lore);
        item.setItemMeta(meta);

        NBTUtils.setString(item, "warp_name", warpName);

        return item;
    }

    public void handleClick(int slot) {
        ItemStack clicked = inventory.getItem(slot);
        if (clicked == null || plugin.getGuiFactory().isBackgroundItem(clicked)) return;

        String warpName = NBTUtils.getString(clicked, "warp_name");
        if (warpName == null) return;

        player.closeInventory();
        plugin.getWarpManager().teleportToWarp(player, warpName);
    }

    public Player getPlayer() {
        return player;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
