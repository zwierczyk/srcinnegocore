package dev.grzybalson.gui;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.guis.GuiTemplateConfig;
import dev.grzybalson.internal.HollyIntegrityGuard;
import dev.grzybalson.utils.ColorUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class GuiFactory {

    private final HollyCore plugin;
    private final Map<Material, ItemStack> backgroundItemCache = new ConcurrentHashMap<>();
    private volatile Component backgroundDisplayNameCache;

    public GuiFactory(HollyCore plugin) {
        HollyIntegrityGuard.requireBound(plugin, "gui:init");
        this.plugin = plugin;
    }

    public Inventory createInventory(InventoryHolder holder, int size, String title) {
        HollyIntegrityGuard.requireBound(plugin, "gui:create");
        return Bukkit.createInventory(holder, size, ColorUtils.colorizeComponent(title));
    }

    public int normalizeRows(int rows) {
        return plugin.getConfigManager().getGuiConfig().normalizeRows(rows);
    }

    public int getDefaultRows() {
        return plugin.getConfigManager().getGuiConfig().getDefaultRows();
    }

    public int getInventorySize(int rows) {
        return normalizeRows(rows) * 9;
    }

    public int getDefaultSize() {
        return getDefaultRows() * 9;
    }

    public int bottomRowSlot(Inventory inventory, int column) {
        int safeColumn = Math.max(0, Math.min(8, column));
        return Math.max(0, inventory.getSize() - 9) + safeColumn;
    }

    public int resolveSlot(Inventory inventory, int configuredSlot, int fallbackSlot) {
        if (configuredSlot >= 0 && configuredSlot < inventory.getSize()) {
            return configuredSlot;
        }
        return fallbackSlot >= 0 && fallbackSlot < inventory.getSize() ? fallbackSlot : -1;
    }

    public void setItemIfInBounds(Inventory inventory, int slot, ItemStack item) {
        if (slot >= 0 && slot < inventory.getSize()) {
            inventory.setItem(slot, item);
        }
    }

    public int[] getContentSlots(Inventory inventory) {
        int size = inventory.getSize();
        int rows = size / 9;
        int lastContentRow = Math.max(1, rows - 2);
        int maxSlots = lastContentRow * 7;
        int[] slots = new int[maxSlots];
        int index = 0;

        for (int row = 1; row <= lastContentRow; row++) {
            int rowStart = row * 9;
            for (int column = 1; column <= 7; column++) {
                int slot = rowStart + column;
                if (slot < size) {
                    slots[index++] = slot;
                }
            }
        }

        if (index == slots.length) {
            return slots;
        }

        int[] trimmed = new int[index];
        System.arraycopy(slots, 0, trimmed, 0, index);
        return trimmed;
    }

    public void applyTemplate(Inventory inventory, Map<String, List<Integer>> template) {
        for (Map.Entry<String, List<Integer>> entry : template.entrySet()) {
            Material material;
            try {
                material = Material.valueOf(entry.getKey().toUpperCase());
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid material in GUI template: " + entry.getKey());
                continue;
            }

            ItemStack backgroundItem = createBackgroundItem(material);

            for (Integer slot : entry.getValue()) {
                if (slot >= 0 && slot < inventory.getSize()) {
                    inventory.setItem(slot, backgroundItem);
                }
            }
        }
    }

    public void applyDefaultTemplate(Inventory inventory) {
        int size = inventory.getSize();
        GuiTemplateConfig config = plugin.getModuleConfigManager().getGuiTemplateConfig();
        Map<String, List<Integer>> template;

        if (size >= 54) {
            template = config.getTemplate54();
        } else if (size >= 45) {
            template = config.getTemplate45();
        } else if (size >= 27) {
            Map<String, List<Integer>> small = config.getTemplate27();
            template = (small == null || small.isEmpty()) ? config.getTemplate45() : small;
        } else {
            template = config.getTemplate45();
        }

        applyTemplate(inventory, template);
    }

    public void applyKitTemplate(Inventory inventory) {
        applyDefaultTemplate(inventory);
    }

    /** Osobny wygląd rynku — szare obramowanie, bez guis/template.yml. */
    public void applyMarketTemplate(Inventory inventory) {
        if (inventory == null) {
            return;
        }
        ItemStack filler = createMarketBorderItem();
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler.clone());
        }
    }

    public void applyPickupTemplate(Inventory inventory) {
        applyDefaultTemplate(inventory);
    }

    public void fillEmptySlots(Inventory inventory, Material material) {
        ItemStack filler = createBackgroundItem(material);
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, filler);
            }
        }
    }

    public void fillEmptySlotsExcept(Inventory inventory, Material material, Set<Integer> excludedSlots) {
        ItemStack filler = createBackgroundItem(material);
        for (int i = 0; i < inventory.getSize(); i++) {
            if (!excludedSlots.contains(i) && inventory.getItem(i) == null) {
                inventory.setItem(i, filler);
            }
        }
    }

    public ItemStack createBackgroundItem(Material material) {
        ItemStack cached = backgroundItemCache.computeIfAbsent(material, this::createBackgroundItemUncached);
        return cached.clone();
    }

    /** Panele tła rynku (szare) — osobne od guis/template.yml. */
    public ItemStack createMarketBorderItem() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(ColorUtils.colorizeComponent(" ").decoration(TextDecoration.ITALIC, false));
            meta.lore(null);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createBackgroundItemUncached(Material material) {
        GuiTemplateConfig config = plugin.getModuleConfigManager().getGuiTemplateConfig();

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(getBackgroundDisplayName());
            meta.lore(null);
            if (config.isHideAttributes()) {
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private Component getBackgroundDisplayName() {
        Component cached = backgroundDisplayNameCache;
        if (cached == null) {
            GuiTemplateConfig config = plugin.getModuleConfigManager().getGuiTemplateConfig();
            cached = ColorUtils.colorizeComponent("&r" + config.getBackgroundItemName()).decoration(TextDecoration.ITALIC, false);
            backgroundDisplayNameCache = cached;
        }
        return cached;
    }

    public void clearTemplateCache() {
        backgroundItemCache.clear();
        backgroundDisplayNameCache = null;
    }

    public boolean isBackgroundItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        Component displayName = meta.displayName();
        if (displayName == null) return false;

        return displayName.equals(getBackgroundDisplayName());
    }

    public Set<Integer> getTemplateSlots(Map<String, List<Integer>> template) {
        Set<Integer> slots = new HashSet<>();
        for (List<Integer> slotList : template.values()) {
            slots.addAll(slotList);
        }
        return slots;
    }

    public Inventory createWithTemplate(InventoryHolder holder, int size, String title, Map<String, List<Integer>> template) {
        Inventory inventory = createInventory(holder, size, title);
        applyTemplate(inventory, template);
        return inventory;
    }

    public Inventory createWithDefaultTemplate(InventoryHolder holder, int size, String title) {
        Inventory inventory = createInventory(holder, size, title);
        applyDefaultTemplate(inventory);
        return inventory;
    }

    public static GuiFactory getInstance() {
        return HollyCore.getInstance().getGuiFactory();
    }
}
