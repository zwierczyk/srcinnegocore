package dev.grzybalson.gui;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.configs.ShopConfig;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.MoneyAmountUtil;
import net.kyori.adventure.text.Component;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ShopPurchaseConfirmGUI implements InventoryHolder {

    private final HollyCore plugin;
    private final Player player;
    private final ShopGUI.ShopType returnType;
    private final String shopType;
    private final ItemStack originalItem;
    private final double moneyPrice;
    private final long timePrice;
    private final int killsPrice;
    private Inventory inventory;
    private int selectedAmount = 1;
    private final Map<Integer, Integer> quickSetAmounts = new LinkedHashMap<>();
    private final Map<Integer, Integer> addAmounts = new LinkedHashMap<>();
    private final Map<Integer, Integer> removeAmounts = new LinkedHashMap<>();

    public ShopPurchaseConfirmGUI(HollyCore plugin, Player player, ShopGUI.ShopType returnType, String shopType, ItemStack originalItem, double moneyPrice, long timePrice, int killsPrice) {
        this.plugin = plugin;
        this.player = player;
        this.returnType = returnType;
        this.shopType = shopType;
        this.originalItem = originalItem == null ? new ItemStack(Material.STONE) : originalItem.clone();
        this.moneyPrice = moneyPrice;
        this.timePrice = timePrice;
        this.killsPrice = killsPrice;
    }

    public void open() {
        ShopConfig config = plugin.getConfigManager().getShopConfig();
        inventory = Bukkit.createInventory(this, 45, ColorUtils.colorizeComponent(config.getPurchaseConfirmTitle()));
        selectedAmount = 1;
        redraw();
        player.openInventory(inventory);
    }

    private void redraw() {
        if (inventory == null) return;
        ShopConfig config = plugin.getConfigManager().getShopConfig();
        quickSetAmounts.clear();
        addAmounts.clear();
        removeAmounts.clear();

        inventory.clear();
        plugin.getGuiFactory().applyDefaultTemplate(inventory);

        boolean isUnstackable = originalItem != null && originalItem.getMaxStackSize() == 1;

        int[] quickSlots = config.getPurchaseQuickSlots();
        int[] quickAmounts = config.getPurchaseQuickAmounts();
        for (int i = 0; i < quickSlots.length && i < quickAmounts.length; i++) {
            int slot = quickSlots[i];
            if (slot < 0 || slot >= 9) continue;
            int amount = Math.max(1, quickAmounts[i]);
            if (isUnstackable && amount > 9) continue;
            inventory.setItem(slot, createQuickButton(amount));
            quickSetAmounts.put(slot, amount);
        }

        inventory.setItem(config.getPurchasePreviewSlot(), createPreviewItem());

        int[] addSlots = config.getPurchaseAddSlots();
        int[] configuredAddAmounts = config.getPurchaseAddAmounts();
        for (int i = 0; i < addSlots.length && i < configuredAddAmounts.length; i++) {
            int amount = Math.max(1, configuredAddAmounts[i]);
            int slot = addSlots[i];
            if (isUnstackable && amount > 9) continue;
            inventory.setItem(slot, createAddButton(amount));
            addAmounts.put(slot, amount);
        }

        int[] removeSlots = config.getPurchaseRemoveSlots();
        int[] configuredRemoveAmounts = config.getPurchaseRemoveAmounts();
        for (int i = 0; i < removeSlots.length && i < configuredRemoveAmounts.length; i++) {
            int amount = Math.max(1, configuredRemoveAmounts[i]);
            int slot = removeSlots[i];
            if (isUnstackable && amount > 9) continue;
            inventory.setItem(slot, createRemoveButton(amount));
            removeAmounts.put(slot, amount);
        }

        inventory.setItem(getEffectiveConfirmSlot(config), createConfirmButton());
        ItemStack cancel = createSimpleItem(Material.BARRIER, config.getPurchaseCancelName(), config.getPurchaseCancelLore());
        inventory.setItem(config.getPurchaseCancelSlot(), cancel);
    }

    private void clearSlot(int slot) {
        if (slot >= 0 && slot < inventory.getSize()) {
            inventory.setItem(slot, null);
        }
    }

    private ItemStack createPreviewItem() {
        ShopConfig config = plugin.getConfigManager().getShopConfig();
        ItemStack preview = originalItem.clone();
        int iconAmount = originalItem.getMaxStackSize() == 1 ? 1 : Math.min(Math.max(1, selectedAmount), 64);
        preview.setAmount(iconAmount);
        ItemMeta meta = preview.getItemMeta();
        if (meta != null) {
            String itemName = originalItem.getType().name();
            if (originalItem.hasItemMeta() && originalItem.getItemMeta() != null && originalItem.getItemMeta().hasDisplayName()) {
                // komponentu nie rozbijamy na legacy, zostawiamy oryginalna nazwe itemu
            } else {
                meta.displayName(ColorUtils.colorizeComponent(config.getPurchasePreviewName()
                    .replace("{ITEM}", itemName)
                    .replace("{AMOUNT}", String.valueOf(selectedAmount))
                    .replace("{PRICE}", formatPrice(selectedAmount))).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(parseLines(config.getPurchasePreviewLore(), selectedAmount));
            preview.setItemMeta(meta);
        }
        return preview;
    }

    private ItemStack createQuickButton(int amount) {
        ItemStack icon = originalItem.clone();
        int iconAmount = originalItem.getMaxStackSize() == 1 ? 1 : Math.min(Math.max(1, amount), 64);
        icon.setAmount(iconAmount);
        ShopConfig config = plugin.getConfigManager().getShopConfig();
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            String name = config.getPurchaseQuickName()
                .replace("{AMOUNT}", String.valueOf(amount))
                .replace("{PRICE}", formatPrice(amount));
            meta.displayName(ColorUtils.colorizeComponent(name).decoration(TextDecoration.ITALIC, false));
            meta.lore(parseLines(config.getPurchaseQuickLore(), amount));
            icon.setItemMeta(meta);
        }
        return icon;
    }

    private ItemStack createAddButton(int amount) {
        ShopConfig config = plugin.getConfigManager().getShopConfig();
        ItemStack item = new ItemStack(Material.LIME_STAINED_GLASS_PANE, Math.min(Math.max(1, amount), 64));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String name = config.getPurchaseAddName()
                .replace("{AMOUNT}", String.valueOf(amount))
                .replace("{PRICE}", formatPrice(amount));
            meta.displayName(ColorUtils.colorizeComponent(name).decoration(TextDecoration.ITALIC, false));
            meta.lore(parseLines(config.getPurchaseAddLore(), amount));
            item.setItemMeta(meta);
        }
        return item;
    }


    private ItemStack createRemoveButton(int amount) {
        ShopConfig config = plugin.getConfigManager().getShopConfig();
        ItemStack item = new ItemStack(Material.RED_STAINED_GLASS_PANE, Math.min(Math.max(1, amount), 64));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String name = config.getPurchaseRemoveName()
                .replace("{AMOUNT}", String.valueOf(amount))
                .replace("{PRICE}", formatPrice(amount));
            meta.displayName(ColorUtils.colorizeComponent(name).decoration(TextDecoration.ITALIC, false));
            meta.lore(parseLines(config.getPurchaseRemoveLore(), amount));
            item.setItemMeta(meta);
        }
        return item;
    }

    private int getEffectiveConfirmSlot(ShopConfig config) {
        int configured = config.getPurchaseConfirmSlot();
        for (int removeSlot : config.getPurchaseRemoveSlots()) {
            if (removeSlot == configured) {
                return 30;
            }
        }
        return configured;
    }

    private ItemStack createConfirmButton() {
        ShopConfig config = plugin.getConfigManager().getShopConfig();
        ItemStack item = new ItemStack(Material.SLIME_BALL);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(ColorUtils.colorizeComponent(config.getPurchaseConfirmName()
                .replace("{AMOUNT}", String.valueOf(selectedAmount))
                .replace("{PRICE}", formatPrice(selectedAmount))).decoration(TextDecoration.ITALIC, false));
            meta.lore(parseLines(config.getPurchaseConfirmLore(), selectedAmount));
            item.setItemMeta(meta);
        }
        return item;
    }

    private List<Component> parseLines(List<String> lines, int amount) {
        List<Component> lore = new ArrayList<>();
        if (lines == null) return lore;
        for (String line : lines) {
            String parsed = line == null ? "" : line
                .replace("{AMOUNT}", String.valueOf(amount))
                .replace("{STACKS}", formatStacks(amount, Math.max(1, originalItem.getMaxStackSize())))
                .replace("{PRICE}", formatPrice(amount));
            if (parsed.isEmpty()) lore.add(Component.empty());
            else lore.addAll(ColorUtils.colorizeLore(parsed));
        }
        return lore;
    }

    private String formatStacks(int amount, int maxStack) {
        if (maxStack <= 1) return "1";
        double stacks = amount / (double) maxStack;
        if (stacks == Math.floor(stacks)) return String.valueOf((int) stacks);
        return String.format(java.util.Locale.US, "%.1f", stacks);
    }

    private String formatPrice(int amount) {
        return switch (shopType) {
            case "time" -> formatTime(timePrice * amount);
            case "kills" -> String.valueOf(killsPrice * amount);
            default -> MoneyAmountUtil.formatCompact(moneyPrice * amount);
        };
    }

    public void handleClick(int slot) {
        ShopConfig config = plugin.getConfigManager().getShopConfig();
        if (slot == config.getPurchaseCancelSlot()) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 0.8f);
            Bukkit.getScheduler().runTask(plugin, () -> new ShopGUI(plugin, player, returnType).open());
            return;
        }
        if (slot == getEffectiveConfirmSlot(config)) {
            buy(selectedAmount);
            return;
        }
        Integer setAmount = quickSetAmounts.get(slot);
        if (setAmount != null) {
            int limit = getMaxPurchasableLimit();
            if (limit <= 0) {
                fail("&#FF0000✘ &8Ξ &cNie masz wolnego miejsca w ekwipunku na ten zakup.");
                return;
            }
            if (setAmount > limit) {
                player.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cOsiągnięto limit lub brak miejsca w ekwipunku (max: " + limit + ")."));
                selectedAmount = limit;
            } else {
                selectedAmount = Math.max(1, setAmount);
            }
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.3f);
            redraw();
            return;
        }
        Integer add = addAmounts.get(slot);
        if (add != null) {
            int limit = getMaxPurchasableLimit();
            if (limit <= 0) {
                fail("&#FF0000✘ &8Ξ &cNie masz wolnego miejsca w ekwipunku na ten zakup.");
                return;
            }
            if (selectedAmount + add > limit) {
                player.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cOsiągnięto limit lub brak miejsca w ekwipunku (max: " + limit + ")."));
                selectedAmount = limit;
            } else {
                selectedAmount = Math.max(1, selectedAmount + add);
            }
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.5f);
            redraw();
            return;
        }
        Integer remove = removeAmounts.get(slot);
        if (remove != null) {
            selectedAmount = Math.max(1, selectedAmount - remove);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 0.7f);
            redraw();
        }
    }

    private void buy(int amount) {
        if (amount <= 0) return;
        int limit = getMaxPurchasableLimit();
        if (limit <= 0) {
            fail("&#FF0000✘ &8Ξ &cNie masz wolnego miejsca w ekwipunku na ten zakup.");
            return;
        }
        if (amount > limit) {
            fail("&#FF0000✘ &8Ξ &cNie masz miejsca w ekwipunku na zakupy w ilości " + amount + " (max: " + limit + ").");
            return;
        }
        switch (shopType) {
            case "money", "license" -> buyWithMoney(amount);
            case "time" -> buyWithTime(amount);
            case "kills" -> buyWithKills(amount);
        }
    }

    private void buyWithMoney(int amount) {
        double totalPrice = moneyPrice * amount;
        plugin.getEconomy().hasBalance(player.getUniqueId(), totalPrice).thenAccept(hasBalance -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!hasBalance) {
                    fail(plugin.getConfigManager().getMessagesConfig().getNotEnoughMoney());
                    return;
                }
                plugin.getEconomy().removeBalance(player.getUniqueId(), totalPrice).thenAccept(success -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!Boolean.TRUE.equals(success)) {
                            fail(plugin.getConfigManager().getMessagesConfig().getNotEnoughMoney());
                            return;
                        }
                        completePurchase(amount);
                    });
                });
            });
        });
    }

    private void buyWithTime(int amount) {
        long total = timePrice * amount;
        plugin.getStatsDataManager().spendShopPlaytime(player.getUniqueId(), total).thenAccept(success -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!success) {
                fail(plugin.getConfigManager().getMessagesConfig().getShopNotEnoughPlaytime());
                return;
            }
            completePurchase(amount);
        })).exceptionally(ex -> {
            Bukkit.getScheduler().runTask(plugin, () -> fail("&cNie udało się odjąć czasu gry."));
            return null;
        });
    }

    private void buyWithKills(int amount) {
        int total = killsPrice * amount;
        int playerKills = plugin.getKillstreakManager().getCachedKillstreak(player.getUniqueId());
        if (playerKills < total) {
            fail(plugin.getConfigManager().getShopConfig().getNotEnoughKillsMessage());
            return;
        }
        plugin.getKillstreakManager().removeKillstreak(player.getUniqueId(), total).thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> completePurchase(amount)));
    }

    private void completePurchase(int amount) {
        if (!canFitItems(amount)) {
            fail("&#FF0000✘ &8Ξ &cNie masz miejsca w ekwipunku na ten zakup.");
            return;
        }
        giveItems(amount);
        player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_DISPENSE, 0.5f, 1.2f);
        player.sendMessage(ColorUtils.colorizeComponent(plugin.getConfigManager().getMessagesConfig().getShopItemBought()));
        Bukkit.getScheduler().runTask(plugin, () -> new ShopGUI(plugin, player, returnType).open());
    }

    public int getMaxPurchasableLimit() {
        int maxFit = getMaxFitItems();
        if (originalItem != null && originalItem.getMaxStackSize() == 1) {
            return Math.min(9, maxFit);
        }
        return maxFit;
    }

    public int getMaxFitItems() {
        int maxStack = Math.max(1, originalItem.getMaxStackSize());
        int capacity = 0;
        for (ItemStack content : player.getInventory().getStorageContents()) {
            if (content == null || content.getType().isAir()) {
                capacity += maxStack;
            } else if (content.isSimilar(originalItem)) {
                capacity += Math.max(0, maxStack - content.getAmount());
            }
        }
        return Math.max(0, capacity);
    }

    private boolean canFitItems(int amount) {
        if (amount <= 0) return true;
        return amount <= getMaxPurchasableLimit();
    }

    private void giveItems(int amount) {
        int remaining = amount;
        int maxStack = Math.max(1, originalItem.getMaxStackSize());
        while (remaining > 0) {
            int give = Math.min(maxStack, remaining);
            ItemStack item = originalItem.clone();
            item.setAmount(give);
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
            if (!leftover.isEmpty()) {
                leftover.values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
            }
            remaining -= give;
        }
    }

    private void fail(String message) {
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        player.sendMessage(ColorUtils.colorizeComponent(message));
    }

    private ItemStack createSimpleItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(ColorUtils.colorizeComponent(name).decoration(TextDecoration.ITALIC, false));
            List<Component> components = new ArrayList<>();
            if (lore != null) {
                for (String line : lore) {
                    if (line == null || line.isEmpty()) components.add(Component.empty());
                    else components.addAll(ColorUtils.colorizeLore(line));
                }
            }
            meta.lore(components);
            item.setItemMeta(meta);
        }
        return item;
    }

    private String formatTime(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        if (days > 0) return days + "d " + (hours % 24) + "h";
        if (hours > 0) return hours + "h " + (minutes % 60) + "m";
        return minutes + "m";
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
