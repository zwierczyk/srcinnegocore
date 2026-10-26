package dev.grzybalson.gui;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.configs.KitConfig;
import dev.grzybalson.modules.kit.Kit;
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

public class KitGUI implements InventoryHolder {

    private final HollyCore plugin;
    private final Player player;
    private Inventory inventory;

    public KitGUI(HollyCore plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public void open() {
        KitConfig config = plugin.getConfigManager().getKitConfig();
        int rows = plugin.getGuiFactory().normalizeRows(config.getGuiRows());

        inventory = Bukkit.createInventory(this, rows * 9, ColorUtils.colorizeComponent(config.getGuiTitle()));

        GuiFactory guiFactory = plugin.getGuiFactory();
        guiFactory.applyKitTemplate(inventory);

        for (Kit kit : plugin.getKitManager().getAllKits()) {
            ItemStack icon = createKitIcon(kit, config);
            int slot = Math.max(0, Math.min(inventory.getSize() - 1, kit.getSlot()));
            inventory.setItem(slot, icon);
        }

        player.openInventory(inventory);
    }

    private ItemStack createKitIcon(Kit kit, KitConfig config) {
        ItemStack icon;
        if (kit.getIcon() != null && !kit.getIcon().getType().isAir()) {
            icon = kit.getIcon().clone();
        } else {
            icon = new ItemStack(Material.CHEST);
        }

        ItemMeta meta = icon.getItemMeta();
        if (meta == null) {
            return icon;
        }

        // Zachowaj texture head — tylko name/lore
        meta.displayName(ColorUtils.colorizeComponent("&#FFD89B&l" + kit.getDisplayName())
            .decoration(TextDecoration.ITALIC, false));

        boolean hasPermission = player.hasPermission(kit.getPermission());
        String status = hasPermission ? config.getGuiStatusAvailable() : config.getGuiStatusUnavailable();
        String cooldownStr = Kit.formatTime(kit.getCooldown());

        List<Component> lore = new ArrayList<>();
        for (String line : config.getGuiItemFormat()) {
            if (line.contains("{LORE}")) {
                for (String kitLore : kit.getLore()) {
                    if (ColorUtils.isBlankLoreLine(kitLore)) {
                        continue;
                    }
                    lore.add(ColorUtils.colorizeComponent(applyPlaceholders(line.replace("{LORE}", kitLore), kit, cooldownStr, status))
                        .decoration(TextDecoration.ITALIC, false));
                }
                continue;
            }
            String formatted = applyPlaceholders(line, kit, cooldownStr, status);
            if (ColorUtils.isBlankLoreLine(formatted)) {
                continue;
            }
            lore.add(ColorUtils.colorizeComponent(formatted).decoration(TextDecoration.ITALIC, false));
        }

        if (!kit.getLore().isEmpty() && config.getGuiItemFormat().stream().noneMatch(line -> line.contains("{LORE}"))) {
            for (String kitLore : kit.getLore()) {
                if (ColorUtils.isBlankLoreLine(kitLore)) {
                    continue;
                }
                lore.add(ColorUtils.colorizeComponent(kitLore).decoration(TextDecoration.ITALIC, false));
            }
        }

        if (!lore.isEmpty()) {
            meta.lore(lore);
        }
        icon.setItemMeta(meta);
        NBTUtils.setString(icon, "kit_name", kit.getName());
        return icon;
    }

    private String applyPlaceholders(String line, Kit kit, String cooldownStr, String status) {
        return line
            .replace("{KIT}", kit.getPrefix().isBlank() ? kit.getDisplayName() : kit.getPrefix())
            .replace("{NAME}", kit.getDisplayName())
            .replace("{COOLDOWN}", cooldownStr)
            .replace("{ITEMS_COUNT}", String.valueOf(kit.getItems().size()))
            .replace("{STATUS}", status);
    }

    public void handleClick(int slot, boolean isRightClick) {
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }
        ItemStack clicked = inventory.getItem(slot);
        if (clicked == null || plugin.getGuiFactory().isBackgroundItem(clicked)) {
            return;
        }

        String kitName = NBTUtils.getString(clicked, "kit_name");
        if (kitName == null) {
            return;
        }

        new KitPreviewGUI(plugin, player, kitName).open();
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
    }

    public Player getPlayer() {
        return player;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
