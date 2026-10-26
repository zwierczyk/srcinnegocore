package dev.grzybalson.gui;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.HollyDataPaths;
import dev.grzybalson.database.PickupDataManager;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.NBTUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class PickupGUI implements InventoryHolder {

    private final HollyCore plugin;
    private final Player player;
    private Inventory inventory;
    private List<PickupDataManager.PickupItem> items;

    public PickupGUI(HollyCore plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public void open() {
        plugin.getPickupManager().getPickupDataManager().getPickupItems(player.getUniqueId())
            .thenAccept(pickupItems -> {
                this.items = new ArrayList<>(pickupItems);
                Bukkit.getScheduler().runTask(plugin, this::createAndOpenInventory);
            });
    }

    private void createAndOpenInventory() {
        YamlConfiguration config = loadConfig();
        int rows = plugin.getGuiFactory().normalizeRows(config.getInt("gui.rows", 5));
        String title = config.getString("gui.title", "&8✦ Odbierz przedmioty");
        inventory = Bukkit.createInventory(this, rows * 9, ColorUtils.colorizeComponent(title));

        if (config.getBoolean("gui.use-template", true)) {
            plugin.getGuiFactory().applyPickupTemplate(inventory);
        }

        int[] itemSlots = plugin.getGuiFactory().getContentSlots(inventory);
        List<String> itemLore = config.getStringList("gui.item.lore");
        if (itemLore.isEmpty()) {
            itemLore = List.of(
                "",
                "&8▸ &fPrzedmiot: &#00E0FF{NAME}",
                "&8▸ &fIlość: &#FFB800{AMOUNT} szt.",
                "",
                "&#4EFA76✔ Kliknij, aby odebrać"
            );
        }

        for (int i = 0; i < items.size() && i < itemSlots.length; i++) {
            PickupDataManager.PickupItem pickupItem = items.get(i);
            ItemStack displayItem = pickupItem.getItem().clone();
            ItemMeta meta = displayItem.getItemMeta();
            if (meta != null) {
                List<Component> lore = meta.lore();
                lore = lore == null ? new ArrayList<>() : new ArrayList<>(lore);
                if (!lore.isEmpty() && config.getBoolean("gui.item.add-separator", true)) {
                    lore.add(Component.empty());
                }
                appendLore(lore, itemLore, pickupItem);
                meta.lore(lore);
                applyCleanFlags(meta);
                displayItem.setItemMeta(meta);
            }

            NBTUtils.setString(displayItem, "pickup_item", "true");
            NBTUtils.setInt(displayItem, "pickup_index", i);
            inventory.setItem(itemSlots[i], displayItem);
        }

        if (items.isEmpty()) {
            inventory.setItem(centerContentSlot(itemSlots), createEmptyItem(config));
        } else {
            int claimAllSlot = config.getInt("gui.claim-all.slot", plugin.getGuiFactory().bottomRowSlot(inventory, 4));
            claimAllSlot = plugin.getGuiFactory().resolveSlot(inventory, claimAllSlot,
                plugin.getGuiFactory().bottomRowSlot(inventory, 4));
            ItemStack claimAllItem = createClaimAllItem(config, items.size(), totalItemCount());
            NBTUtils.setString(claimAllItem, "pickup_action", "claim_all");
            plugin.getGuiFactory().setItemIfInBounds(inventory, claimAllSlot, claimAllItem);
        }

        player.openInventory(inventory);
    }

    private YamlConfiguration loadConfig() {
        File file = new File(HollyDataPaths.getAddonFolder(plugin, "commands"), "odbierz.yml");
        return YamlConfiguration.loadConfiguration(file);
    }

    private void appendLore(List<Component> target, List<String> rawLore, PickupDataManager.PickupItem pickupItem) {
        for (String rawLine : rawLore) {
            String line = replace(rawLine, pickupItem);
            if (line.isEmpty()) {
                target.add(Component.empty());
            } else {
                target.add(ColorUtils.colorizeComponent(line).decoration(TextDecoration.ITALIC, false));
            }
        }
    }

    private String replace(String text, PickupDataManager.PickupItem pickupItem) {
        return (text == null ? "" : text)
            .replace("{NAME}", pickupItem.getName())
            .replace("{AMOUNT}", String.valueOf(pickupItem.getAmount()));
    }

    private ItemStack createClaimAllItem(YamlConfiguration config, int packageCount, int totalItems) {
        Material material = Material.matchMaterial(config.getString("gui.claim-all.material", "CHEST"));
        if (material == null || material.isAir()) {
            material = Material.CHEST;
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(ColorUtils.colorizeComponent(config.getString(
                "gui.claim-all.name", "&#4EFA76&lODBIERZ WSZYSTKO"
            )).decoration(TextDecoration.ITALIC, false));
            List<String> rawLore = config.getStringList("gui.claim-all.lore");
            if (rawLore.isEmpty()) {
                rawLore = List.of(
                    "",
                    "&8▸ &fPozycje do odebrania: &#00E0FF{PACKAGES}",
                    "&8▸ &fŁącznie przedmiotów: &#FFB800{ITEMS} szt.",
                    "",
                    "&#4EFA76✔ Kliknij, aby odebrać wszystko"
                );
            }
            List<Component> lore = new ArrayList<>();
            for (String rawLine : rawLore) {
                String line = (rawLine == null ? "" : rawLine)
                    .replace("{PACKAGES}", String.valueOf(packageCount))
                    .replace("{ITEMS}", String.valueOf(totalItems));
                lore.add(line.isEmpty()
                    ? Component.empty()
                    : ColorUtils.colorizeComponent(line).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
            applyCleanFlags(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createEmptyItem(YamlConfiguration config) {
        Material material = Material.matchMaterial(config.getString("gui.empty.material", "BARRIER"));
        if (material == null || material.isAir()) {
            material = Material.BARRIER;
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(ColorUtils.colorizeComponent(config.getString(
                "gui.empty.name", "&#FF5C5C&lBRAK PRZEDMIOTÓW"
            )).decoration(TextDecoration.ITALIC, false));
            List<String> rawLore = config.getStringList("gui.empty.lore");
            if (rawLore.isEmpty()) {
                rawLore = List.of("", "&7Nie masz teraz żadnych przedmiotów", "&7oczekujących do odebrania.");
            }
            List<Component> lore = new ArrayList<>();
            for (String rawLine : rawLore) {
                lore.add(rawLine == null || rawLine.isEmpty()
                    ? Component.empty()
                    : ColorUtils.colorizeComponent(rawLine).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
            applyCleanFlags(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    private int totalItemCount() {
        long total = 0L;
        for (PickupDataManager.PickupItem pickupItem : items) {
            total += Math.max(1, pickupItem.getAmount());
        }
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    private int centerContentSlot(int[] contentSlots) {
        if (contentSlots.length == 0) {
            return Math.max(0, inventory.getSize() / 2);
        }
        return contentSlots[contentSlots.length / 2];
    }

    private void applyCleanFlags(ItemMeta meta) {
        meta.addItemFlags(
            ItemFlag.HIDE_ATTRIBUTES,
            ItemFlag.HIDE_ENCHANTS,
            ItemFlag.HIDE_UNBREAKABLE,
            ItemFlag.HIDE_DESTROYS,
            ItemFlag.HIDE_PLACED_ON
        );
    }

    public List<PickupDataManager.PickupItem> getItems() {
        return items;
    }

    public Player getPlayer() {
        return player;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
