package dev.grzybalson.anarchiagg.coach.gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import dev.grzybalson.anarchiagg.coach.CoachManager;
import dev.grzybalson.anarchiagg.coach.CoachSkill;
import dev.grzybalson.anarchiagg.coach.CoachConfig;
import dev.grzybalson.anarchiagg.util.ItemCompatibilityUtil;
import dev.grzybalson.anarchiagg.util.SoundsUtil;
import dev.grzybalson.utils.ColorUtils;

import java.util.ArrayList;
import java.util.List;

public class StrengthMenuGUI implements InventoryHolder {

    private final CoachManager manager;
    private final CoachConfig config;
    private final Player player;
    private final Inventory inventory;

    public StrengthMenuGUI(CoachManager manager, CoachConfig config, Player player) {
        this.manager = manager;
        this.config = config;
        this.player = player;
        this.inventory = Bukkit.createInventory(this, config.getGui().getStrengthTreeSize(), dev.grzybalson.utils.ColorUtils.colorize(config.getGui().getStrengthTitle()));
        fill();
    }

    private void fill() {
        fillBorders();
        int max = Math.min(config.getStrength().getMaxLevel(), config.getGui().getStrengthLevelSlots().size());
        for (int level = 1; level <= max; level++) {
            inventory.setItem(config.getGui().getStrengthLevelSlots().get(level - 1), buildLevelItem(level));
        }
        inventory.setItem(config.getGui().getStrengthCloseSlot(), item(config.getGui().getCloseMaterial(), config.getGui().getCloseName(), List.of(), 1));
    }

    private void fillBorders() {
        int rows = Math.max(1, inventory.getSize() / 9);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            int row = slot / 9;
            int col = slot % 9;
            if (row == 0 || row == rows - 1 || col == 0 || col == 8) {
                inventory.setItem(slot, item(config.getGui().getStrengthBorderMaterial(), config.getGui().getFillerName(), List.of(), 1));
            }
        }
    }

    private ItemStack buildLevelItem(int targetLevel) {
        int current = manager.getData(player.getUniqueId()).getLevel(CoachSkill.STRENGTH);
        CoachConfig.StrengthLevelSettings level = manager.getStrengthLevel(targetLevel);
        boolean unlocked = current >= targetLevel;
        boolean available = targetLevel == current + 1;

        List<String> lore = new ArrayList<>();
        lore.add("&8x &fPostep: &e" + level.getProgressPercent() + "%");
        lore.add("&8x &fStatus: " + status(unlocked, available));
        lore.add("");
        lore.add("&8x &fWłaściwości:");
        lore.addAll(manager.buildProperties(CoachSkill.STRENGTH, targetLevel));
        lore.add("");

        if (available) {
            lore.add("&aKliknij, aby odblokować!");
        } else {
            lore.add(unlocked ? "&aPoziom odblokowany!" : "&cMusisz odblokowacć pierw poprzednie poziomy");
        }

        return item(levelMaterial(unlocked, available), "&#E41B53&lU&#E11A4F&lm&#DF194B&li&#DC1748&le&#DA1644&lj&#D71540&lę&#D5133D&lt&#D21239&ln&#CF1135&lo&#CD0F32&lś&#CA0E2E&lć &#C50B27&ls&#C30A23&li&#C0091F&lł&#BE071C&ly &8#" + targetLevel, lore, targetLevel);
    }

    private String status(boolean unlocked, boolean available) {
        if (unlocked) return "&aOdblokowany";
        if (available) return "&eMożliwy do odblokowania!";
        return "&cZablokowany";
    }

    private org.bukkit.Material levelMaterial(boolean unlocked, boolean available) {
        if (unlocked) return config.getGui().getUnlockedLevelMaterial();
        if (available) return config.getGui().getAvailableLevelMaterial();
        return config.getGui().getLockedLevelMaterial();
    }

    private ItemStack item(org.bukkit.Material material, String name, List<String> lore, int amount) {
        ItemStack item = new ItemStack(material);
        item.setAmount(ItemCompatibilityUtil.safeAmount(material, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(dev.grzybalson.utils.ColorUtils.colorize(name));
        meta.setLore(lore.stream().map(dev.grzybalson.utils.ColorUtils::colorize).collect(java.util.stream.Collectors.toList()));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    public void open() {
        player.openInventory(inventory);
        SoundsUtil.gui(player);
    }

    public int getClickedLevel(int slot) {
        List<Integer> levelSlots = config.getGui().getStrengthLevelSlots();
        for (int i = 0; i < levelSlots.size(); i++) {
            if (levelSlots.get(i) == slot) {
                return i + 1;
            }
        }
        return 0;
    }

    public boolean isCloseSlot(int slot) {
        return slot == config.getGui().getStrengthCloseSlot();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
