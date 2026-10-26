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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class KitEditGUI implements InventoryHolder {

    private final HollyCore plugin;
    private final Player player;
    private final Kit kit;
    private Inventory inventory;
    private final Set<Integer> editableSlotSet;
    private int[] editableSlots = new int[0];

    public KitEditGUI(HollyCore plugin, Player player, Kit kit) {
        this.plugin = plugin;
        this.player = player;
        this.kit = kit;
        this.editableSlotSet = new HashSet<>();
    }

    public void open() {
        KitConfig config = plugin.getConfigManager().getKitConfig();
        String title = config.getKitEditGuiTitle().replace("{kit}", kit.getName());

        inventory = Bukkit.createInventory(this, plugin.getGuiFactory().getDefaultSize(), ColorUtils.colorizeComponent(title));

        GuiFactory guiFactory = plugin.getGuiFactory();
        guiFactory.applyKitTemplate(inventory);
        this.editableSlots = guiFactory.getContentSlots(inventory);
        this.editableSlotSet.clear();
        for (int slot : editableSlots) {
            editableSlotSet.add(slot);
        }

        for (int slot : editableSlots) {
            inventory.setItem(slot, null);
        }

        int slotIndex = 0;
        for (ItemStack item : kit.getItems()) {
            if (item != null && slotIndex < editableSlots.length) {
                inventory.setItem(editableSlots[slotIndex], item.clone());
                slotIndex++;
            }
        }

        ItemStack saveItem = new ItemStack(Material.LIME_DYE);
        ItemMeta saveMeta = saveItem.getItemMeta();
        if (saveMeta != null) {
            saveMeta.displayName(ColorUtils.colorizeComponent("&a&lZAPISZ").decoration(TextDecoration.ITALIC, false));
            saveMeta.lore(List.of(
                ColorUtils.colorizeComponent("&7Kliknij, aby zapisac zestaw").decoration(TextDecoration.ITALIC, false)
            ));
            saveItem.setItemMeta(saveMeta);
        }
        NBTUtils.setString(saveItem, "kit_action", "save");
        inventory.setItem(guiFactory.bottomRowSlot(inventory, 3), saveItem);

        ItemStack cancelItem = new ItemStack(Material.RED_DYE);
        ItemMeta cancelMeta = cancelItem.getItemMeta();
        if (cancelMeta != null) {
            cancelMeta.displayName(ColorUtils.colorizeComponent("&c&lANULUJ").decoration(TextDecoration.ITALIC, false));
            cancelMeta.lore(List.of(
                ColorUtils.colorizeComponent("&7Kliknij, aby anulowac").decoration(TextDecoration.ITALIC, false)
            ));
            cancelItem.setItemMeta(cancelMeta);
        }
        NBTUtils.setString(cancelItem, "kit_action", "cancel");
        inventory.setItem(guiFactory.bottomRowSlot(inventory, 5), cancelItem);

        player.openInventory(inventory);
    }

    public void handleClick(int slot, ItemStack cursor, boolean isShiftClick) {
        ItemStack clicked = inventory.getItem(slot);

        if (clicked != null) {
            String action = NBTUtils.getString(clicked, "kit_action");
            if (action != null) {
                switch (action) {
                    case "save" -> {
                        saveKit();
                        player.closeInventory();
                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                        String message = plugin.getConfigManager().getKitConfig().getKitSavedMessage()
                            .replace("{kit}", kit.getName());
                        player.sendMessage(ColorUtils.colorizeComponent(message));
                    }
                    case "cancel" -> {
                        player.closeInventory();
                        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                    }
                }
            }
        }
    }

    public void saveKit() {
        List<ItemStack> items = new ArrayList<>();

        for (int slot : editableSlots) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && !item.getType().isAir() && !plugin.getGuiFactory().isBackgroundItem(item)) {
                items.add(item.clone());
            }
        }

        kit.setItems(items);
        plugin.getKitManager().saveKit(kit);
    }

    public int addItemToEditableSlots(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return 0;
        }

        int remaining = item.getAmount();
        for (int slot : editableSlots) {
            ItemStack existing = inventory.getItem(slot);
            if (existing == null || existing.getType().isAir() || !existing.isSimilar(item)) {
                continue;
            }

            int maxStack = existing.getMaxStackSize();
            int freeAmount = maxStack - existing.getAmount();
            if (freeAmount <= 0) {
                continue;
            }

            int toMove = Math.min(remaining, freeAmount);
            existing.setAmount(existing.getAmount() + toMove);
            remaining -= toMove;
            if (remaining <= 0) {
                return 0;
            }
        }

        for (int slot : editableSlots) {
            ItemStack existing = inventory.getItem(slot);
            if (existing != null && !existing.getType().isAir() && !plugin.getGuiFactory().isBackgroundItem(existing)) {
                continue;
            }

            ItemStack copy = item.clone();
            int toMove = Math.min(remaining, copy.getMaxStackSize());
            copy.setAmount(toMove);
            inventory.setItem(slot, copy);
            remaining -= toMove;
            if (remaining <= 0) {
                return 0;
            }
        }

        return remaining;
    }

    public boolean isEditableSlot(int slot) {
        return editableSlotSet.contains(slot);
    }

    public Kit getKit() {
        return kit;
    }

    public Player getPlayer() {
        return player;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
