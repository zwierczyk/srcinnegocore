package dev.grzybalson.modules.flamehubcustomitems.manager;

import dev.grzybalson.HollyCore;
import dev.grzybalson.modules.flamehubcustomitems.config.FlameHubConfigManager;
import dev.grzybalson.modules.flamehubcustomitems.util.FlameColorUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public class ItemManager {

    private final FlameHubConfigManager configManager;
    private final NamespacedKey itemKey;
    private final NamespacedKey legacyItemKey;

    public ItemManager(HollyCore plugin, FlameHubConfigManager configManager) {
        this.configManager = configManager;
        this.itemKey = new NamespacedKey(plugin, "hood_item_id");
        // Kompatybilność z oryginalnym standalone HoodItems.
        this.legacyItemKey = new NamespacedKey("hooditems", "hood_item_id");
    }

    public ItemStack createItem(CustomItemType type) {
        Material material = configManager.getItemMaterial(type);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.displayName(FlameColorUtils.colorizeItem(configManager.getItemName(type)));

            List<String> loreLines = configManager.getItemLore(type);
            meta.lore(FlameColorUtils.colorizeList(loreLines));

            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(itemKey, PersistentDataType.STRING, type.getId());
            pdc.set(legacyItemKey, PersistentDataType.STRING, type.getId());

            item.setItemMeta(meta);
        }

        return item;
    }

    public CustomItemType getCustomItemType(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String id = pdc.get(itemKey, PersistentDataType.STRING);
        if (id == null) {
            id = pdc.get(legacyItemKey, PersistentDataType.STRING);
        }
        if (id == null) {
            return null;
        }

        return CustomItemType.fromId(id);
    }

    public void openGui(Player player) {
        HoodItemsGuiHolder holder = new HoodItemsGuiHolder();
        int size = configManager.getGuiSize();
        Component title = FlameColorUtils.colorize(configManager.getGuiTitle());

        Inventory inv = Bukkit.createInventory(holder, size, title);
        holder.setInventory(inv);

        if (configManager.isGuiFillerEnabled()) {
            ItemStack filler = createFillerItem();
            for (int i = 0; i < size; i++) {
                inv.setItem(i, filler);
            }
        }

        int yodaSlot = configManager.getGuiYodaSlot();
        if (yodaSlot >= 0 && yodaSlot < size) {
            inv.setItem(yodaSlot, createItem(CustomItemType.YODA));
        }

        int vaderSlot = configManager.getGuiVaderSlot();
        if (vaderSlot >= 0 && vaderSlot < size) {
            inv.setItem(vaderSlot, createItem(CustomItemType.VADER));
        }

        player.openInventory(inv);
    }

    private ItemStack createFillerItem() {
        Material material = configManager.getGuiFillerMaterial();
        ItemStack filler = new ItemStack(material);
        ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            meta.displayName(FlameColorUtils.colorizeItem(configManager.getGuiFillerName()));
            filler.setItemMeta(meta);
        }
        return filler;
    }

    public NamespacedKey getItemKey() {
        return itemKey;
    }
}
