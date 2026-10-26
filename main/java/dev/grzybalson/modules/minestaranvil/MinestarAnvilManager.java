package dev.grzybalson.modules.minestaranvil;

import dev.grzybalson.HollyCore;
import dev.grzybalson.anarchiagg.ksiegi.KsiegiManager;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MinestarAnvilManager implements Listener {
    private static final String TITLE = "§8minestarAnvil";
    private final HollyCore plugin;
    private final KsiegiManager ksiegiManager;

    public MinestarAnvilManager(HollyCore plugin) {
        this.plugin = plugin;
        this.ksiegiManager = new KsiegiManager(plugin);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        if (!block.getType().name().endsWith("ANVIL") && block.getType() != Material.SMITHING_TABLE) return;
        event.setCancelled(true);
        event.getPlayer().openInventory(createGui());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
            return;
        }
        if (event.getView().getTopInventory().getHolder() instanceof AnvilHolder) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (event.getWhoClicked() instanceof Player player) {
                    updateAnvilResult(event.getView().getTopInventory(), player);
                }
            });
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory topInv = event.getView().getTopInventory();
        if (topInv.getHolder() instanceof AnvilHolder) {
            if (!(event.getWhoClicked() instanceof Player player)) return;

            int rawSlot = event.getRawSlot();

            // 1. Kliknięcie slotu wyniku (slot 2)
            if (rawSlot == 2) {
                event.setCancelled(true);
                ItemStack result = topInv.getItem(2);
                if (result == null || result.getType().isAir()) {
                    return;
                }

                if (event.isShiftClick()) {
                    HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(result.clone());
                    if (!leftover.isEmpty()) {
                        return;
                    }
                } else {
                    ItemStack cursor = event.getCursor();
                    if (cursor != null && cursor.getType() != Material.AIR) {
                        return;
                    }
                    event.getView().setCursor(result.clone());
                }

                topInv.setItem(0, null);
                ItemStack addition = topInv.getItem(1);
                if (addition != null && addition.getAmount() > 1) {
                    addition.setAmount(addition.getAmount() - 1);
                    topInv.setItem(1, addition);
                } else {
                    topInv.setItem(1, null);
                }
                topInv.setItem(2, null);

                player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.2f);
                updateAnvilResult(topInv, player);
                player.updateInventory();
                return;
            }

            // 2. Shift-click z ekwipunku gracza do kowadła
            if (event.isShiftClick() && rawSlot >= topInv.getSize()) {
                event.setCancelled(true);
                ItemStack clicked = event.getCurrentItem();
                if (clicked == null || clicked.getType().isAir()) return;

                ItemStack slot0 = topInv.getItem(0);
                ItemStack slot1 = topInv.getItem(1);

                if (slot0 == null || slot0.getType().isAir()) {
                    topInv.setItem(0, clicked.clone());
                    event.setCurrentItem(null);
                } else if (slot1 == null || slot1.getType().isAir()) {
                    topInv.setItem(1, clicked.clone());
                    event.setCurrentItem(null);
                } else {
                    return;
                }

                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.5f);
                updateAnvilResult(topInv, player);
                player.updateInventory();
                return;
            }

            // 3. Shift-click ze slotów kowadła (0 lub 1) do ekwipunku
            if (event.isShiftClick() && rawSlot < 2) {
                event.setCancelled(true);
                ItemStack clicked = topInv.getItem(rawSlot);
                if (clicked == null || clicked.getType().isAir()) return;

                HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(clicked.clone());
                if (leftover.isEmpty()) {
                    topInv.setItem(rawSlot, null);
                } else {
                    topInv.setItem(rawSlot, leftover.values().iterator().next());
                }
                updateAnvilResult(topInv, player);
                player.updateInventory();
                return;
            }

            // 4. Zwykłe kliknięcia w slota 0 lub 1 oraz ekwipunek -> przelicz wynik na następnym ticku
            Bukkit.getScheduler().runTask(plugin, () -> updateAnvilResult(topInv, player));
            return;
        }

        if (!(topInv.getHolder() instanceof Holder)) return;
        event.setCancelled(true);
        if (event.getRawSlot() < 0 || event.getRawSlot() >= topInv.getSize()) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (event.getRawSlot() == 21) {
            player.openInventory(Bukkit.createInventory(new AnvilHolder(), InventoryType.ANVIL, "§8Kowadlo"));
            return;
        }
        if (event.getRawSlot() == 22) repair(player);
        if (event.getRawSlot() == 23) swap(player);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof AnvilHolder)) return;
        Inventory inv = event.getInventory();
        HumanEntity player = event.getPlayer();

        for (int slot = 0; slot <= 1; slot++) {
            ItemStack item = inv.getItem(slot);
            if (item != null && !item.getType().isAir()) {
                inv.setItem(slot, null);
                for (ItemStack leftover : player.getInventory().addItem(item).values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                }
            }
        }
        inv.setItem(2, null);
    }

    public Inventory createGui() {
        Inventory inv = Bukkit.createInventory(new Holder(), 45, TITLE);
        decorate(inv);
        inv.setItem(21, item(Material.ANVIL, "§fKowadło", List.of("§eKliknij, aby otworzyć kowadło")));
        inv.setItem(22, item(Material.EXPERIENCE_BOTTLE, "§fNapraw przedmiot", List.of("§eKliknij, aby naprawić przedmiot", "§8minecraft:experience_bottle", "§810 component(s)")));
        inv.setItem(23, item(Material.SMITHING_TABLE, "§fWymień przedmiot", List.of("§7Dostępne wymiany:", "§8↪ §cNETHERYTOWY §f► §bDIAMENTOWY", "§8↪ §bDIAMENTOWY §f► §cNETHERYTOWY", "", "§eKliknij, aby zamienić!", "§8minecraft:smithing_table", "§89 component(s)")));
        return inv;
    }

    private void updateAnvilResult(Inventory topInv, Player player) {
        ItemStack base = topInv.getItem(0);
        ItemStack addition = topInv.getItem(1);

        if (base == null || base.getType().isAir() || addition == null || addition.getType().isAir()) {
            topInv.setItem(2, null);
            return;
        }

        // 1. Sprawdzanie czy dodatek to Księga AnarchiaGG
        KsiegiManager.BookType bookType = ksiegiManager.identifyBook(addition);
        if (bookType != null) {
            if (ksiegiManager.isTargetAllowed(base, bookType)
                && !ksiegiManager.hasEnchant(base, bookType)
                && (bookType == KsiegiManager.BookType.ULASKAWIENIA || ksiegiManager.isDamageable(base))) {

                ItemStack result = ksiegiManager.applyEnchantVisual(base.clone(), bookType);
                topInv.setItem(2, result);
            } else {
                topInv.setItem(2, null);
            }
            return;
        }

        // 2. Łączenie z zaklecaną księgą vanilla (ENCHANTED_BOOK)
        if (addition.getType() == Material.ENCHANTED_BOOK) {
            ItemStack result = base.clone();
            ItemMeta baseMeta = result.getItemMeta();
            ItemMeta addMeta = addition.getItemMeta();
            if (baseMeta != null && addMeta instanceof EnchantmentStorageMeta storeMeta) {
                boolean addedAny = false;
                for (Map.Entry<Enchantment, Integer> entry : storeMeta.getStoredEnchants().entrySet()) {
                    Enchantment ench = entry.getKey();
                    if (ench.canEnchantItem(result) || result.getType() == Material.BOOK) {
                        int level = entry.getValue();
                        int currentLevel = baseMeta.getEnchantLevel(ench);
                        int newLevel = level > currentLevel ? level : (level == currentLevel ? level + 1 : currentLevel);
                        int maxLevel = ench.getMaxLevel();
                        newLevel = Math.min(newLevel, maxLevel);
                        baseMeta.addEnchant(ench, newLevel, true);
                        addedAny = true;
                    }
                }
                if (addedAny) {
                    result.setItemMeta(baseMeta);
                    topInv.setItem(2, result);
                    return;
                }
            }
        } else if (addition.getType() == base.getType()) {
            // 3. Naprawa przez połączenie 2 tych samych przedmiotów
            if (base.getItemMeta() instanceof Damageable baseDam && addition.getItemMeta() instanceof Damageable addDam) {
                if (baseDam.hasDamage()) {
                    ItemStack result = base.clone();
                    Damageable resultMeta = (Damageable) result.getItemMeta();
                    int maxDur = base.getType().getMaxDurability();
                    int dur1 = maxDur - baseDam.getDamage();
                    int dur2 = maxDur - addDam.getDamage();
                    int newDur = dur1 + dur2 + (int)(maxDur * 0.12);
                    int newDamage = Math.max(0, maxDur - newDur);
                    resultMeta.setDamage(newDamage);
                    result.setItemMeta(resultMeta);
                    topInv.setItem(2, result);
                    return;
                }
            }
        }

        topInv.setItem(2, null);
    }

    private void repair(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            player.sendMessage(ColorUtils.error("Musisz trzymać przedmiot w ręce."));
            return;
        }
        if (!dev.grzybalson.utils.ItemRepair.repair(hand)) {
            player.sendMessage(ColorUtils.error("Ten przedmiot nie wymaga naprawy."));
            return;
        }
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1.2f);
        player.sendMessage(ColorUtils.success("Naprawiono przedmiot."));
    }

    private void swap(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            player.sendMessage(ColorUtils.error("Musisz trzymać przedmiot w ręce."));
            return;
        }
        Material changed = swapMaterial(hand.getType());
        if (changed == null) {
            player.sendMessage(ColorUtils.error("Ten przedmiot nie jest diamentowy ani netherytowy."));
            return;
        }
        hand.setType(changed);
        player.playSound(player.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 1f, 1f);
        player.sendMessage(ColorUtils.success("Wymieniono materiał przedmiotu."));
    }

    private Material swapMaterial(Material material) {
        String name = material.name();
        if (name.startsWith("DIAMOND_")) return Material.matchMaterial(name.replace("DIAMOND_", "NETHERITE_"));
        if (name.startsWith("NETHERITE_")) return Material.matchMaterial(name.replace("NETHERITE_", "DIAMOND_"));
        return null;
    }

    private void decorate(Inventory inv) {
        if (applyGuiTemplate(inv)) {
            return;
        }
        int[] cyan = {1, 9, 7, 17, 43, 35, 27, 37};
        int[] white = {2, 6, 18, 26, 38, 42};
        int[] blue = {0, 8, 36, 44};
        for (int slot : cyan) inv.setItem(slot, item(Material.CYAN_STAINED_GLASS_PANE, " ", List.of()));
        for (int slot : white) inv.setItem(slot, item(Material.WHITE_STAINED_GLASS_PANE, " ", List.of()));
        for (int slot : blue) inv.setItem(slot, item(Material.LIGHT_BLUE_STAINED_GLASS_PANE, " ", List.of()));
    }

    private boolean applyGuiTemplate(Inventory inv) {
        File templateFile = new File(plugin.getDataFolder(), "guis/template.yml");
        if (!templateFile.exists()) {
            try {
                plugin.saveResource("guis/template.yml", false);
            } catch (IllegalArgumentException ignored) {
                return false;
            }
        }

        YamlConfiguration guiTemplate = YamlConfiguration.loadConfiguration(templateFile);
        ConfigurationSection section = guiTemplate.getConfigurationSection("template" + inv.getSize());
        if (section == null) return false;

        String itemName = guiTemplate.getString("backgroundItemName", "&c");
        boolean hideAttributes = guiTemplate.getBoolean("hideAttributes", true);
        for (String materialName : section.getKeys(false)) {
            Material material = Material.matchMaterial(materialName);
            if (material == null || !material.isItem()) continue;
            ItemStack decoration = new ItemStack(material);
            ItemMeta meta = decoration.getItemMeta();
            if (meta != null) {
                meta.displayName(ColorUtils.colorizeComponent(itemName));
                if (hideAttributes) meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                decoration.setItemMeta(meta);
            }
            for (int slot : section.getIntegerList(materialName)) {
                if (slot >= 0 && slot < inv.getSize()) {
                    inv.setItem(slot, decoration.clone());
                }
            }
        }
        return true;
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static final class Holder implements InventoryHolder {
        @Override public @NotNull Inventory getInventory() { return Bukkit.createInventory(null, 9); }
    }

    private static final class AnvilHolder implements InventoryHolder {
        @Override public @NotNull Inventory getInventory() { return Bukkit.createInventory(null, InventoryType.ANVIL); }
    }
}
