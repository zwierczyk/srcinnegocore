package dev.grzybalson.gui.home;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.modules.home.HomeConfig;
import dev.grzybalson.modules.home.HomeManager;
import dev.grzybalson.utils.ColorUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HomeIconPickerGUI implements InventoryHolder {

    private static final List<Material> DEFAULT_ICONS = new ArrayList<>();

    static {
        for (Material m : Material.values()) {
            if (m.isLegacy() || !m.isItem() || m.isAir()) continue;
            String name = m.name();
            if (name.contains("BED") || name.contains("BANNER") || name.contains("HEAD") || name.contains("SKULL")
                || name.contains("CHEST") || name.contains("BOX") || name.contains("BLOCK") || name.contains("ORE")
                || name.contains("DOOR") || name.contains("LANTERN") || name.contains("CAMPFIRE") || name.contains("FLOWER")
                || name.equals("ANVIL") || name.equals("CRAFTING_TABLE") || name.equals("ENCHANTING_TABLE")
                || name.equals("BOOKSHELF") || name.equals("BEACON") || name.equals("COMPASS") || name.equals("CLOCK")
                || name.equals("RECOVERY_COMPASS") || name.equals("TARGET") || name.equals("RESPAWN_ANCHOR")) {
                DEFAULT_ICONS.add(m);
            }
        }
    }

    private final HollyCore plugin;
    private final Player player;
    private final HomeManager homeManager;
    private final int homeNumber;
    private final int page;
    private final Map<Integer, Material> slotToMaterial = new HashMap<>();
    private Inventory inventory;

    public HomeIconPickerGUI(HollyCore plugin, Player player, HomeManager homeManager, int homeNumber) {
        this(plugin, player, homeManager, homeNumber, 0);
    }

    public HomeIconPickerGUI(HollyCore plugin, Player player, HomeManager homeManager, int homeNumber, int page) {
        this.plugin = plugin;
        this.player = player;
        this.homeManager = homeManager;
        this.homeNumber = homeNumber;
        this.page = Math.max(0, page);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public void open() {
        HomeConfig.IconPickerMenuSection config = homeManager.getConfig().getGui().getIconPickerMenu();
        inventory = Bukkit.createInventory(this, config.getSize(), ColorUtils.colorizeComponent(config.getTitle()));

        if (plugin.getGuiFactory() != null) {
            plugin.getGuiFactory().applyDefaultTemplate(inventory);
        }

        Set<String> hidden = new HashSet<>(config.getHiddenMaterials());
        List<Material> filtered = new ArrayList<>();
        for (Material m : DEFAULT_ICONS) {
            if (!hidden.contains(m.name())) {
                filtered.add(m);
            }
        }

        List<Integer> slots = config.getItemSlots();
        int itemsPerPage = slots.size();
        int startIndex = page * itemsPerPage;
        int maxPage = (int) Math.ceil((double) filtered.size() / itemsPerPage) - 1;
        if (maxPage < 0) maxPage = 0;

        for (int i = 0; i < itemsPerPage; i++) {
            int itemIndex = startIndex + i;
            if (itemIndex >= filtered.size()) break;

            Material mat = filtered.get(itemIndex);
            int slot = slots.get(i);
            slotToMaterial.put(slot, mat);

            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                String matName = mat.name().replace("_", " ");
                String name = config.getItemFormat().getName().replace("{MATERIAL_NAME}", matName);
                meta.displayName(ColorUtils.colorizeComponent(name).decoration(TextDecoration.ITALIC, false));

                List<Component> lore = new ArrayList<>();
                if (config.getItemFormat().getLore() != null) {
                    for (String line : config.getItemFormat().getLore()) {
                        lore.add(ColorUtils.colorizeComponent(line).decoration(TextDecoration.ITALIC, false));
                    }
                }
                meta.lore(lore);
                item.setItemMeta(meta);
            }
            inventory.setItem(slot, item);
        }

        // Previous Page (48)
        if (page > 0) {
            HomeConfig.GuiItemConfig prevBtn = config.getPreviousPageButton();
            if (prevBtn.getSlot() >= 0 && prevBtn.getSlot() < inventory.getSize()) {
                inventory.setItem(prevBtn.getSlot(), createNavItem(prevBtn));
            }
        }

        // Next Page (50)
        if (page < maxPage) {
            HomeConfig.GuiItemConfig nextBtn = config.getNextPageButton();
            if (nextBtn.getSlot() >= 0 && nextBtn.getSlot() < inventory.getSize()) {
                inventory.setItem(nextBtn.getSlot(), createNavItem(nextBtn));
            }
        }

        // Back button (49)
        HomeConfig.GuiItemConfig backBtn = config.getBackButton();
        if (backBtn.getSlot() >= 0 && backBtn.getSlot() < inventory.getSize()) {
            inventory.setItem(backBtn.getSlot(), createNavItem(backBtn));
        }

        player.openInventory(inventory);
    }

    public void handleClick(int slot) {
        HomeConfig.IconPickerMenuSection config = homeManager.getConfig().getGui().getIconPickerMenu();

        if (slot == config.getBackButton().getSlot()) {
            homeManager.playSound(player, homeManager.getConfig().getSounds().getGuiClick());
            new HomeEditMenuGUI(plugin, player, homeManager, homeNumber).open();
            return;
        }

        if (slot == config.getPreviousPageButton().getSlot() && page > 0) {
            homeManager.playSound(player, homeManager.getConfig().getSounds().getGuiClick());
            new HomeIconPickerGUI(plugin, player, homeManager, homeNumber, page - 1).open();
            return;
        }

        if (slot == config.getNextPageButton().getSlot()) {
            homeManager.playSound(player, homeManager.getConfig().getSounds().getGuiClick());
            new HomeIconPickerGUI(plugin, player, homeManager, homeNumber, page + 1).open();
            return;
        }

        Material selected = slotToMaterial.get(slot);
        if (selected != null) {
            homeManager.updateHomeIcon(player, homeNumber, selected.name());
            new HomeEditMenuGUI(plugin, player, homeManager, homeNumber).open();
        }
    }

    private ItemStack createNavItem(HomeConfig.GuiItemConfig itemConfig) {
        Material mat;
        try {
            mat = Material.valueOf(itemConfig.getMaterial().toUpperCase());
        } catch (IllegalArgumentException e) {
            mat = Material.BARRIER;
        }

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(ColorUtils.colorizeComponent(itemConfig.getName()).decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        if (itemConfig.getLore() != null) {
            for (String line : itemConfig.getLore()) {
                lore.add(ColorUtils.colorizeComponent(line).decoration(TextDecoration.ITALIC, false));
            }
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
