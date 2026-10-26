package dev.grzybalson.gui;

import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ArmorStarsUtil;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.ItemClientSanitizer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

public class EqPreviewGUI implements InventoryHolder {

    private final HollyCore plugin;
    private final Player viewer;
    private final Player target;
    private Inventory inventory;

    public EqPreviewGUI(HollyCore plugin, Player viewer, Player target) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.target = target;
    }

    public void open() {
        if (target == null || !target.isOnline()) {
            viewer.sendMessage(ColorUtils.colorize("&cTen gracz jest offline!"));
            return;
        }

        openWithItems(target.getName(), target.getInventory().getHelmet(),
            findChestplate(target.getInventory()),
            target.getInventory().getLeggings(),
            target.getInventory().getBoots(),
            findWeapon(target.getInventory()),
            findElytra(target.getInventory()),
            findShears(target.getInventory()));
    }

    public void openFake(String targetName, ItemStack helmet, ItemStack chestplate, ItemStack leggings, ItemStack boots, ItemStack weapon) {
        openWithItems(targetName, helmet, chestplate, leggings, boots, weapon, null, null);
    }

    private void openWithItems(
            String targetName,
            ItemStack helmet,
            ItemStack chestplate,
            ItemStack leggings,
            ItemStack boots,
            ItemStack weapon,
            ItemStack elytra,
            ItemStack shears
    ) {
        String title = "&8Podgląd ekwipunku: &6" + targetName;
        inventory = Bukkit.createInventory(this, 27, ColorUtils.colorizeComponent(title));

        ItemStack grayGlass = createGlass(Material.GRAY_STAINED_GLASS_PANE);
        ItemStack blackGlass = createGlass(Material.BLACK_STAINED_GLASS_PANE);

        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, i % 2 == 0 ? grayGlass.clone() : blackGlass.clone());
        }

        inventory.setItem(10, getPreviewItem(helmet, "Hełm"));
        inventory.setItem(11, getPreviewItem(chestplate, "Klatka piersiowa"));
        inventory.setItem(12, getPreviewItem(leggings, "Nogawice"));
        inventory.setItem(13, getPreviewItem(boots, "Buty"));
        inventory.setItem(14, getPreviewItem(elytra, "Elytra"));
        inventory.setItem(15, getPreviewItem(weapon, "Miecz / Broń"));
        inventory.setItem(16, getPreviewItem(shears, "Nożyczki"));

        viewer.openInventory(inventory);
    }

    private ItemStack findChestplate(PlayerInventory inv) {
        ItemStack chest = inv.getChestplate();
        if (chest != null && isChestplateArmor(chest.getType())) {
            return chest;
        }
        for (ItemStack item : inv.getContents()) {
            if (item != null && isChestplateArmor(item.getType())) {
                return item;
            }
        }
        // chest przeszedl juz walidacje wyzej — gdy to np. elytra, slot ma byc pusty, nie klamac o zbroi.
        return null;
    }

    private boolean isChestplateArmor(Material material) {
        if (material == null) return false;
        String name = material.name();
        return name.endsWith("_CHESTPLATE");
    }

    private ItemStack findElytra(PlayerInventory inv) {
        if (inv.getChestplate() != null && inv.getChestplate().getType() == Material.ELYTRA) {
            return inv.getChestplate();
        }
        if (inv.getItemInOffHand() != null && inv.getItemInOffHand().getType() == Material.ELYTRA) {
            return inv.getItemInOffHand();
        }
        for (ItemStack item : inv.getContents()) {
            if (item != null && item.getType() == Material.ELYTRA) {
                return item;
            }
        }
        return null;
    }

    private ItemStack findWeapon(PlayerInventory inv) {
        ItemStack best = ArmorStarsUtil.findBestWeapon(inv);
        if (best != null) {
            return best;
        }
        ItemStack main = inv.getItemInMainHand();
        return main != null && main.getType() != Material.AIR ? main : null;
    }

    private ItemStack findShears(PlayerInventory inv) {
        if (inv.getItemInMainHand() != null && inv.getItemInMainHand().getType() == Material.SHEARS) {
            return inv.getItemInMainHand();
        }
        if (inv.getItemInOffHand() != null && inv.getItemInOffHand().getType() == Material.SHEARS) {
            return inv.getItemInOffHand();
        }
        for (ItemStack item : inv.getContents()) {
            if (item != null && item.getType() == Material.SHEARS) {
                return item;
            }
        }
        return null;
    }

    private ItemStack getPreviewItem(ItemStack item, String defaultName) {
        if (item == null || item.getType() == Material.AIR) {
            ItemStack empty = new ItemStack(Material.BARRIER);
            ItemMeta meta = empty.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ColorUtils.colorize("&c" + defaultName + " &7(Brak)"));
                empty.setItemMeta(meta);
            }
            return empty;
        }
        // Enchanty > 255 (anarchia) wywalaja klienta 1.21 przy wysylce okna — sanitizer klonuje sam.
        return ItemClientSanitizer.forClient(item);
    }

    private ItemStack createGlass(Material type) {
        ItemStack item = new ItemStack(type);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
