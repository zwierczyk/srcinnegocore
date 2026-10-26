package dev.grzybalson.modules.event;

import dev.grzybalson.HollyCore;
import dev.grzybalson.gui.GuiFactory;
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

/**
 * Podglad / edycja puli nagród bossa (free lub premium).
 * Admin: wkladanie itemow + ZAPISZ. Gracz: tylko podglad.
 */
public class DungeonBossRewardEditGUI implements InventoryHolder {

    private final HollyCore plugin;
    private final Player player;
    private final EventManager manager;
    private final int bossLevel;
    private final boolean premium;
    private final boolean editable;
    private Inventory inventory;
    private final Set<Integer> editableSlotSet = new HashSet<>();
    private int[] editableSlots = new int[0];

    public DungeonBossRewardEditGUI(
        HollyCore plugin,
        Player player,
        EventManager manager,
        int bossLevel,
        boolean premium,
        boolean editable
    ) {
        this.plugin = plugin;
        this.player = player;
        this.manager = manager;
        this.bossLevel = Math.max(1, Math.min(3, bossLevel));
        this.premium = premium;
        this.editable = editable;
    }

    public void open() {
        String title = (premium ? "&8Premium" : "&8Darmowe") + " &8· Boss " + bossLevel;
        inventory = Bukkit.createInventory(this, 54, ColorUtils.colorizeComponent(title));
        GuiFactory guiFactory = plugin.getGuiFactory();
        guiFactory.applyDefaultTemplate(inventory);
        this.editableSlots = guiFactory.getContentSlots(inventory);
        editableSlotSet.clear();
        for (int slot : editableSlots) {
            editableSlotSet.add(slot);
            inventory.setItem(slot, null);
        }

        List<ItemStack> rewards = manager.getDungeonSessions() != null
            ? manager.getDungeonSessions().getBossRewards(bossLevel, premium)
            : List.of();
        int index = 0;
        for (ItemStack stack : rewards) {
            if (stack != null && index < editableSlots.length) {
                inventory.setItem(editableSlots[index++], stack.clone());
            }
        }

        if (editable) {
            ItemStack save = new ItemStack(Material.LIME_DYE);
            ItemMeta saveMeta = save.getItemMeta();
            if (saveMeta != null) {
                saveMeta.displayName(ColorUtils.colorizeComponent("&a&lZAPISZ").decoration(TextDecoration.ITALIC, false));
                saveMeta.lore(List.of(
                    ColorUtils.colorizeComponent("&7Wloz przedmioty do srodka").decoration(TextDecoration.ITALIC, false),
                    ColorUtils.colorizeComponent("&7i kliknij, aby zapisac pule.").decoration(TextDecoration.ITALIC, false)
                ));
                save.setItemMeta(saveMeta);
            }
            NBTUtils.setString(save, "boss_reward_action", "save");
            inventory.setItem(guiFactory.bottomRowSlot(inventory, 3), save);
        } else {
            ItemStack info = new ItemStack(Material.BOOK);
            ItemMeta infoMeta = info.getItemMeta();
            if (infoMeta != null) {
                infoMeta.displayName(ColorUtils.colorizeComponent("&e&lPODGLAD").decoration(TextDecoration.ITALIC, false));
                infoMeta.lore(List.of(
                    ColorUtils.colorizeComponent("&7To tylko podglad nagród.").decoration(TextDecoration.ITALIC, false),
                    ColorUtils.colorizeComponent("&7Edycja: &fholly.adungeon.admin").decoration(TextDecoration.ITALIC, false)
                ));
                info.setItemMeta(infoMeta);
            }
            NBTUtils.setString(info, "boss_reward_action", "info");
            inventory.setItem(guiFactory.bottomRowSlot(inventory, 3), info);
        }

        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            backMeta.displayName(ColorUtils.colorizeComponent("&c&lPOWROT").decoration(TextDecoration.ITALIC, false));
            backMeta.lore(List.of(
                ColorUtils.colorizeComponent("&7Wroc do wyboru bossa").decoration(TextDecoration.ITALIC, false)
            ));
            back.setItemMeta(backMeta);
        }
        NBTUtils.setString(back, "boss_reward_action", "back");
        inventory.setItem(guiFactory.bottomRowSlot(inventory, 5), back);

        player.openInventory(inventory);
    }

    public void handleActionClick(int slot) {
        ItemStack clicked = inventory.getItem(slot);
        String action = NBTUtils.getString(clicked, "boss_reward_action");
        if (action == null) {
            return;
        }
        switch (action) {
            case "save" -> {
                if (!editable) {
                    return;
                }
                if (saveRewards()) {
                    player.sendMessage(ColorUtils.colorizeComponent(
                        "&#00FF3B✔ &8Ξ &#4EFA76Zapisano pule &f" + (premium ? "premium" : "free")
                            + " &abossa &e" + bossLevel + "&a. &7Druga pula nie ruszona."
                    ));
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                } else {
                    player.sendMessage(ColorUtils.colorizeComponent(
                        "&#FF0000✘ &8Ξ &cNie udalo sie zapisac nagród."
                    ));
                    manager.playError(player);
                }
                player.closeInventory();
            }
            case "back" -> new DungeonBossRewardsMenuGUI(plugin, player, manager, premium).open();
            default -> {
            }
        }
    }

    public boolean saveRewards() {
        if (manager.getDungeonSessions() == null) {
            return false;
        }
        List<ItemStack> items = new ArrayList<>();
        for (int slot : editableSlots) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && !item.getType().isAir() && !plugin.getGuiFactory().isBackgroundItem(item)) {
                items.add(item.clone());
            }
        }
        return manager.getDungeonSessions().saveBossRewards(bossLevel, premium, items);
    }

    public int addItemToEditableSlots(ItemStack item) {
        if (!editable || item == null || item.getType().isAir()) {
            return item == null ? 0 : item.getAmount();
        }

        int remaining = item.getAmount();
        for (int slot : editableSlots) {
            ItemStack existing = inventory.getItem(slot);
            if (existing == null || existing.getType().isAir() || !existing.isSimilar(item)) {
                continue;
            }
            int freeAmount = existing.getMaxStackSize() - existing.getAmount();
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
        return editable && editableSlotSet.contains(slot);
    }

    public boolean isEditable() {
        return editable;
    }

    public Player getPlayer() {
        return player;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
