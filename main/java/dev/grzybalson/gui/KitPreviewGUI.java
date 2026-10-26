package dev.grzybalson.gui;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.configs.KitConfig;
import dev.grzybalson.modules.kit.Kit;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.NBTUtils;
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

public class KitPreviewGUI implements InventoryHolder {

    private final HollyCore plugin;
    private final Player player;
    private final String kitName;
    private Inventory inventory;
    private int claimSlot = -1;
    private int backSlot = -1;

    public KitPreviewGUI(HollyCore plugin, Player player, String kitName) {
        this.plugin = plugin;
        this.player = player;
        this.kitName = kitName;
    }

    public void open() {
        Kit kit = plugin.getKitManager().getKit(kitName);
        if (kit == null) {
            player.sendMessage(ColorUtils.colorizeComponent(
                plugin.getConfigManager().getKitConfig().getKitNotFoundMessage()
            ));
            return;
        }

        KitConfig config = plugin.getConfigManager().getKitConfig();
        String title = config.getKitPreviewGuiTitle().replace("{kit}", kit.getName());

        inventory = Bukkit.createInventory(this, plugin.getGuiFactory().getDefaultSize(), ColorUtils.colorizeComponent(title));

        GuiFactory guiFactory = plugin.getGuiFactory();
        guiFactory.applyKitTemplate(inventory);

        backSlot = guiFactory.bottomRowSlot(inventory, 0);
        claimSlot = inventory.getSize() - 1;

        int[] itemSlots = guiFactory.getContentSlots(inventory);
        int slotIndex = 0;
        for (ItemStack item : kit.getItems()) {
            if (item == null) continue;
            while (slotIndex < itemSlots.length
                && (itemSlots[slotIndex] == claimSlot || itemSlots[slotIndex] == backSlot)) {
                slotIndex++;
            }
            if (slotIndex >= itemSlots.length) break;
            inventory.setItem(itemSlots[slotIndex], item.clone());
            slotIndex++;
        }

        inventory.setItem(backSlot, createBackItem(config));
        inventory.setItem(claimSlot, createClaimItem(kit, config));

        player.openInventory(inventory);
    }

    private ItemStack createBackItem(KitConfig config) {
        String rawMaterial = config.getBackItemMaterial();
        Material backMaterial = rawMaterial != null ? Material.matchMaterial(rawMaterial) : null;
        if (backMaterial == null) {
            backMaterial = Material.BARRIER;
        }

        ItemStack backItem = new ItemStack(backMaterial);
        ItemMeta backMeta = backItem.getItemMeta();
        if (backMeta != null) {
            String name = config.getBackItemName();
            if (name == null || name.isBlank()) {
                name = "&#FFD89B&lᴘᴏᴡʀᴏᴛ";
            }
            backMeta.displayName(ColorUtils.colorizeComponent(name).decoration(TextDecoration.ITALIC, false));

            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            List<String> rawLore = config.getBackItemLore();
            if (rawLore != null) {
                for (String line : rawLore) {
                    if (ColorUtils.isBlankLoreLine(line)) {
                        continue;
                    }
                    lore.add(ColorUtils.colorizeComponent(line).decoration(TextDecoration.ITALIC, false));
                }
            }
            if (!lore.isEmpty()) {
                backMeta.lore(lore);
            }
            backItem.setItemMeta(backMeta);
        }
        NBTUtils.setString(backItem, "kit_action", "back");
        return backItem;
    }

    private ItemStack createClaimItem(Kit kit, KitConfig config) {
        boolean hasPerm = player.hasPermission(kit.getPermission());
        long remaining = plugin.getKitManager().getRemainingCooldown(player, kit.getName());

        Material claimMat;
        String claimName;
        List<String> claimLore = new ArrayList<>();

        if (!hasPerm) {
            claimMat = Material.BARRIER;
            claimName = "&#FF5C5C&lʙʀᴀᴋ ᴘᴇʀᴍɪꜱᴊɪ";
            claimLore.add("&7Nie posiadasz permisji do tego kita");
        } else if (remaining > 0) {
            claimMat = Material.CLOCK;
            claimName = "&#FF8300&lᴄᴏᴏʟᴅᴏᴡɴ";
            claimLore.add("&7Pozostało: &#FFFFFF" + Kit.formatTime(remaining));
        } else {
            claimMat = Material.LIME_DYE;
            claimName = config.getGuiClaimButton().replace("{KIT}", kit.getPrefix()).replace("{NAME}", kit.getDisplayName());
            claimLore.add(config.getGuiClaimLore().replace("{KIT}", kit.getPrefix()).replace("{NAME}", kit.getDisplayName()));
        }

        ItemStack claimItem = new ItemStack(claimMat);
        ItemMeta meta = claimItem.getItemMeta();
        if (meta != null) {
            meta.displayName(ColorUtils.colorizeComponent(claimName).decoration(TextDecoration.ITALIC, false));
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            for (String line : claimLore) {
                if (ColorUtils.isBlankLoreLine(line)) {
                    continue;
                }
                lore.add(ColorUtils.colorizeComponent(line).decoration(TextDecoration.ITALIC, false));
            }
            if (!lore.isEmpty()) {
                meta.lore(lore);
            }
            claimItem.setItemMeta(meta);
        }
        NBTUtils.setString(claimItem, "kit_action", "claim");
        return claimItem;
    }

    private String formatTime(long seconds) {
        return Kit.formatTime(seconds);
    }

    public void handleClick(int slot) {
        if (slot < 0 || slot >= inventory.getSize()) return;
        ItemStack clicked = inventory.getItem(slot);
        if (clicked == null) return;

        String action = NBTUtils.getString(clicked, "kit_action");
        if ("back".equals(action)) {
            new KitGUI(plugin, player).open();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            return;
        }

        if (!"claim".equals(action)) {
            return;
        }

        Kit kit = plugin.getKitManager().getKit(kitName);
        if (kit == null) {
            player.closeInventory();
            return;
        }

        if (!player.hasPermission(kit.getPermission())) {
            String message = plugin.getConfigManager().getKitConfig().getNoPermission()
                .replace("{NAME}", kit.getName())
                .replace("{KIT}", kit.getPrefix());
            player.sendMessage(ColorUtils.colorizeComponent(message));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.8f);
            return;
        }

        if (plugin.getKitManager().isOnCooldown(player, kit.getName())) {
            long remaining = plugin.getKitManager().getRemainingCooldown(player, kit.getName());
            String message = plugin.getConfigManager().getKitConfig().getCooldown()
                .replace("{TIME}", Kit.formatTime(remaining))
                .replace("{time}", Kit.formatTime(remaining));
            player.sendMessage(ColorUtils.colorizeComponent(message));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.8f);
            return;
        }

        player.closeInventory();
        if (plugin.getKitManager().isStartKit(kit.getName())) {
            plugin.getKitManager().tryGiveStartKit(player,
                () -> player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.6f),
                () -> player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            );
            return;
        }
        if (plugin.getKitManager().giveKit(player, kit.getName())) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.6f);
        } else {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        }
    }

    public Player getPlayer() {
        return player;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
