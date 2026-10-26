package dev.grzybalson.gui;

import dev.grzybalson.HollyCore;
import dev.grzybalson.modules.zonebox.ZoneBoxManager;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.MoneyAmountUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class BlockEconomyGUI implements InventoryHolder {

    private final HollyCore plugin;
    private final ZoneBoxManager manager;
    private final Player player;
    private final int stepIndex;
    private final int page;
    private final Inventory inventory;
    private final Map<Integer, Material> slotMaterials = new HashMap<>();

    public BlockEconomyGUI(HollyCore plugin, ZoneBoxManager manager, Player player) {
        this(plugin, manager, player, 0, 0);
    }

    private BlockEconomyGUI(HollyCore plugin, ZoneBoxManager manager, Player player, int stepIndex, int page) {
        this.plugin = plugin;
        this.manager = manager;
        this.player = player;
        List<Double> steps = manager.getEditorStepValues();
        this.stepIndex = steps.isEmpty() ? 0 : Math.max(0, Math.min(stepIndex, steps.size() - 1));

        int rows = manager.getBlockEconomyGuiRows();
        this.inventory = plugin.getGuiFactory().createWithDefaultTemplate(
            this,
            plugin.getGuiFactory().getInventorySize(rows),
            manager.getBlockEconomyGuiTitle()
        );

        int[] contentSlots = plugin.getGuiFactory().getContentSlots(inventory);
        List<Material> materials = manager.getConfiguredBlockEconomyMaterials();
        int perPage = Math.max(1, contentSlots.length);
        int maxPage = Math.max(0, (materials.size() - 1) / perPage);
        this.page = Math.max(0, Math.min(page, maxPage));

        fillItems(materials, contentSlots, perPage, maxPage, steps);
    }

    public static void registerListener(HollyCore plugin) {
        Bukkit.getPluginManager().registerEvents(new ClickListener(), plugin);
    }

    public void open() {
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_DISPENSE, 0.7F, 1.2F);
    }

    private void reopen(int nextStep, int nextPage) {
        new BlockEconomyGUI(plugin, manager, player, nextStep, nextPage).open();
    }

    private void fillItems(List<Material> materials, int[] contentSlots, int perPage, int maxPage, List<Double> steps) {
        double step = steps.get(stepIndex);
        int start = page * perPage;
        int end = Math.min(materials.size(), start + perPage);

        for (int i = start; i < end; i++) {
            int slot = contentSlots[i - start];
            Material material = materials.get(i);
            slotMaterials.put(slot, material);
            inventory.setItem(slot, createBlockItem(material, manager.getBlockEconomyPrices().getOrDefault(material, 0.0D), step));
        }

        GuiFactory factory = plugin.getGuiFactory();
        if (page > 0) {
            factory.setItemIfInBounds(inventory, factory.bottomRowSlot(inventory, 0),
                navItem(Material.ARROW, "&e⬅ Poprzednia strona", "&7Strona &f" + page + "&7/&f" + (maxPage + 1)));
        }
        factory.setItemIfInBounds(inventory, factory.bottomRowSlot(inventory, 4), createInfoItem(steps, materials.size(), maxPage));
        factory.setItemIfInBounds(inventory, factory.bottomRowSlot(inventory, 7), createAddItem());
        if (page < maxPage) {
            factory.setItemIfInBounds(inventory, factory.bottomRowSlot(inventory, 8),
                navItem(Material.ARROW, "&aNastępna strona ➡", "&7Strona &f" + (page + 2) + "&7/&f" + (maxPage + 1)));
        }
    }

    private ItemStack createBlockItem(Material material, double price, double step) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(ColorUtils.createLoreComponent("&f" + prettify(material)));
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            lore.add(ColorUtils.createLoreComponent("&8» &7Cena: &#92FF74" + MoneyAmountUtil.formatCompact(price) + "$"));
            lore.add(ColorUtils.createLoreComponent("&8» &7Na minutę: &#92FF74" + MoneyAmountUtil.formatCompact(price * 120.0D) + "$"));
            lore.add(net.kyori.adventure.text.Component.empty());
            lore.add(ColorUtils.createLoreComponent("&8» &fLPM &7dodaje cenę"));
            lore.add(ColorUtils.createLoreComponent("&8» &fPPM &7zabiera cenę"));
            lore.add(ColorUtils.createLoreComponent("&8» &fSHIFT + LPM/PPM &7zmienia krok"));
            lore.add(ColorUtils.createLoreComponent("&8» &fQ &7usuwa blok z ekonomii"));
            lore.add(net.kyori.adventure.text.Component.empty());
            lore.add(ColorUtils.createLoreComponent("&8» &7Wybrany krok: &#92FF74" + MoneyAmountUtil.formatCompact(step)));
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createInfoItem(List<Double> steps, int total, int maxPage) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(ColorUtils.createLoreComponent("&8Block Economy"));
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            lore.add(net.kyori.adventure.text.Component.empty());
            lore.add(ColorUtils.createLoreComponent("&8» &7Skonfigurowane bloki: &f" + total));
            lore.add(ColorUtils.createLoreComponent("&8» &7Strona: &f" + (page + 1) + "&7/&f" + (maxPage + 1)));
            lore.add(ColorUtils.createLoreComponent("&8» &7Aktualny krok: &#92FF74" + MoneyAmountUtil.formatCompact(steps.get(stepIndex))));
            lore.add(net.kyori.adventure.text.Component.empty());
            lore.add(ColorUtils.createLoreComponent("&8» &7Dostępne kroki:"));
            for (int i = 0; i < steps.size(); i++) {
                String prefix = i == stepIndex ? "&#92FF74➜ " : "&8- ";
                lore.add(ColorUtils.createLoreComponent(prefix + "&f" + MoneyAmountUtil.formatCompact(steps.get(i))));
            }
            lore.add(net.kyori.adventure.text.Component.empty());
            lore.add(ColorUtils.createLoreComponent("&8» &fKliknij &7żeby zmienić krok"));
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createAddItem() {
        ItemStack item = new ItemStack(Material.HOPPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(ColorUtils.createLoreComponent("&aDodaj blok z ręki"));
            meta.lore(List.of(
                ColorUtils.createLoreComponent("&7Trzymaj blok i kliknij,"),
                ColorUtils.createLoreComponent("&7żeby dodać go do ekonomii.")
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack navItem(Material material, String name, String lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(ColorUtils.createLoreComponent(name));
            meta.lore(List.of(ColorUtils.createLoreComponent(lore)));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static final class ClickListener implements Listener {
        @EventHandler
        public void onClick(InventoryClickEvent event) {
            if (!(event.getView().getTopInventory().getHolder() instanceof BlockEconomyGUI gui)) {
                return;
            }
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player clicker) || !clicker.getUniqueId().equals(gui.player.getUniqueId())) {
                return;
            }
            if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getView().getTopInventory())) {
                return;
            }

            int slot = event.getRawSlot();
            GuiFactory factory = gui.plugin.getGuiFactory();
            int prevSlot = factory.bottomRowSlot(gui.inventory, 0);
            int infoSlot = factory.bottomRowSlot(gui.inventory, 4);
            int addSlot = factory.bottomRowSlot(gui.inventory, 7);
            int nextSlot = factory.bottomRowSlot(gui.inventory, 8);
            List<Double> steps = gui.manager.getEditorStepValues();

            if (slot == prevSlot && gui.page > 0) {
                gui.reopen(gui.stepIndex, gui.page - 1);
                return;
            }
            if (slot == nextSlot) {
                gui.reopen(gui.stepIndex, gui.page + 1);
                return;
            }
            if (slot == infoSlot) {
                clicker.playSound(clicker.getLocation(), Sound.UI_BUTTON_CLICK, 0.6F, 1.4F);
                gui.reopen((gui.stepIndex + 1) % Math.max(1, steps.size()), gui.page);
                return;
            }
            if (slot == addSlot) {
                gui.addFromHand(clicker);
                return;
            }

            Material material = gui.slotMaterials.get(slot);
            if (material == null) {
                return;
            }

            if (event.getClick() == ClickType.DROP || event.getClick() == ClickType.CONTROL_DROP) {
                if (gui.manager.removeBlockPrice(material)) {
                    clicker.playSound(clicker.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5F, 1.2F);
                    clicker.sendMessage(ColorUtils.success("Usunięto &f" + material.name() + " &7z blockseconomy."));
                    gui.reopen(gui.stepIndex, gui.page);
                }
                return;
            }

            if (event.getClick() == ClickType.SHIFT_LEFT) {
                clicker.playSound(clicker.getLocation(), Sound.UI_BUTTON_CLICK, 0.6F, 1.2F);
                gui.reopen((gui.stepIndex - 1 + steps.size()) % steps.size(), gui.page);
                return;
            }
            if (event.getClick() == ClickType.SHIFT_RIGHT) {
                clicker.playSound(clicker.getLocation(), Sound.UI_BUTTON_CLICK, 0.6F, 1.4F);
                gui.reopen((gui.stepIndex + 1) % steps.size(), gui.page);
                return;
            }

            double current = gui.manager.getBlockEconomyPrices().getOrDefault(material, 0.0D);
            double step = steps.get(gui.stepIndex);
            if (event.isLeftClick()) {
                gui.manager.setBlockPrice(material, current + step);
                clicker.playSound(clicker.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.4F, 1.6F);
                gui.reopen(gui.stepIndex, gui.page);
            } else if (event.isRightClick()) {
                gui.manager.setBlockPrice(material, Math.max(0.0D, current - step));
                clicker.playSound(clicker.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5F, 0.9F);
                gui.reopen(gui.stepIndex, gui.page);
            }
        }

        @EventHandler
        public void onDrag(InventoryDragEvent event) {
            if (event.getView().getTopInventory().getHolder() instanceof BlockEconomyGUI) {
                event.setCancelled(true);
            }
        }
    }

    private void addFromHand(Player clicker) {
        ItemStack hand = clicker.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir() || !hand.getType().isBlock()) {
            clicker.sendMessage(ColorUtils.error("Trzymaj blok w ręce, żeby dodać go do ekonomii."));
            return;
        }
        Material material = hand.getType();
        if (manager.getBlockEconomyPrices().containsKey(material)) {
            clicker.sendMessage(ColorUtils.error("Ten blok już jest w blockseconomy."));
            return;
        }
        manager.setBlockPrice(material, 0.01D);
        clicker.playSound(clicker.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5F, 1.7F);
        clicker.sendMessage(ColorUtils.success("Dodano &f" + material.name() + " &7za &a0.01$&7. Ustaw cenę w GUI."));
        reopen(stepIndex, page);
    }

    private static String prettify(Material material) {
        String[] parts = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
