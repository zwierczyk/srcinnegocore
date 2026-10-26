package dev.grzybalson.modules.mslf;

import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ColorUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MslfItemsGUI implements InventoryHolder, Listener {

    private final MslfManager manager;
    private final Inventory inventory;
    private final Map<Integer, MslfItemType> slots = new HashMap<>();

    public MslfItemsGUI(HollyCore plugin, MslfManager manager) {
        this.manager = manager;
        this.inventory = Bukkit.createInventory(this, 54,
            Component.text(ColorUtils.colorize(manager.settings().guiTitle())));
        fill();
    }

    private void fill() {
        inventory.clear();
        slots.clear();
        int slot = 0;
        for (MslfItemType type : MslfItemType.values()) {
            ItemStack item = manager.items().create(type);
            if (item == null) {
                continue;
            }
            if (slot >= inventory.getSize()) {
                break;
            }
            inventory.setItem(slot, withGuiLore(item, type));
            slots.put(slot, type);
            slot++;
        }
    }

    private ItemStack withGuiLore(ItemStack item, MslfItemType type) {
        ItemStack clone = item.clone();
        ItemMeta meta = clone.getItemMeta();
        if (meta == null) {
            return clone;
        }
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        List<String> extra = manager.settings().itemBlockedRegions(type);
        lore.add(Component.text(" ").decoration(TextDecoration.ITALIC, false));
        lore.add(ColorUtils.colorizeComponent("&8× &eLPM &7= daj item  &8|  &ePPM &7= regiony").decoration(TextDecoration.ITALIC, false));
        if (extra == null || extra.isEmpty()) {
            lore.add(ColorUtils.colorizeComponent("&8× &7Blokada itemu: &fbrak").decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(ColorUtils.colorizeComponent("&8× &7Blokada itemu: &f" + String.join("&7, &f", extra))
                .decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        clone.setItemMeta(meta);
        return clone;
    }

    public void open(Player player) {
        fill();
        player.openInventory(inventory);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getView().getTopInventory().getHolder() instanceof MslfItemsGUI clickedGui)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        MslfItemType type = clickedGui.slots.get(event.getSlot());
        if (type == null) {
            return;
        }
        if (event.getClick().isRightClick()) {
            clickedGui.manager.openRegionGui(player, type);
            return;
        }
        ItemStack give = clickedGui.manager.items().create(type);
        if (give == null) {
            return;
        }
        Map<Integer, ItemStack> left = player.getInventory().addItem(give);
        if (!left.isEmpty()) {
            player.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cBrak miejsca w EQ."));
            return;
        }
        player.sendMessage(ColorUtils.colorizeComponent("&#00FF3B✔ &8Ξ &#4EFA76Dano item MSLF: &f" + type.id()));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MslfItemsGUI) {
            event.setCancelled(true);
        }
    }
}
