package dev.grzybalson.anarchiagg.coach.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import dev.grzybalson.anarchiagg.coach.CoachManager;
import dev.grzybalson.anarchiagg.coach.CoachSkill;
import dev.grzybalson.anarchiagg.coach.CoachConfig;
import dev.grzybalson.anarchiagg.util.SoundsUtil;
import dev.grzybalson.utils.ColorUtils;

import java.util.ArrayList;
import java.util.List;

public class CoachMenuGUI implements InventoryHolder {

    private final CoachManager manager;
    private final CoachConfig config;
    private final Player player;
    private final Inventory inventory;

    public CoachMenuGUI(CoachManager manager, CoachConfig config, Player player) {
        this.manager = manager;
        this.config = config;
        this.player = player;
        this.inventory = Bukkit.createInventory(this, config.getGui().getMainSize(), dev.grzybalson.utils.ColorUtils.colorize(config.getGui().getMainTitle()));
        fill();
    }

    private void fill() {
        fillBorders();
        for (CoachSkill skill : CoachSkill.values()) {
            if (!manager.isSkillEnabled(skill)) continue;
            CoachConfig.SkillItemSettings settings = getSkillSettings(skill);
            inventory.setItem(settings.getSlot(), buildSkillItem(skill, settings));
        }
        inventory.setItem(config.getGui().getMainCloseSlot(), simpleItem(config.getGui().getCloseMaterial(), config.getGui().getCloseName(), List.of()));
    }

    private void fillBorders() {
        if (CoachGuiTemplateUtil.applyGuiTemplate(inventory)) {
            return;
        }
        if (inventory.getSize() == 45) {
            fillMainPatternBorders();
            return;
        }

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            int row = slot / 9;
            int col = slot % 9;
            if (row == 0 || row == 4 || col == 0 || col == 8) {
                Material material = (slot % 2 == 0) ? config.getGui().getMainBorderEvenMaterial() : config.getGui().getMainBorderOddMaterial();
                inventory.setItem(slot, simpleItem(material, config.getGui().getFillerName(), List.of()));
            }
        }
    }

    private void fillMainPatternBorders() {
        fillSlots(Material.YELLOW_STAINED_GLASS_PANE, 0, 8, 36, 44);
        fillSlots(config.getGui().getMainBorderOddMaterial(), 1, 7, 9, 17, 27, 35, 37, 43);
        fillSlots(config.getGui().getMainBorderEvenMaterial(), 2, 3, 5, 6, 18, 26, 38, 39, 41, 42);
    }

    private void fillSlots(Material material, int... slots) {
        for (int slot : slots) {
            if (slot >= 0 && slot < inventory.getSize()) {
                inventory.setItem(slot, simpleItem(material, config.getGui().getFillerName(), List.of()));
            }
        }
    }

    private ItemStack buildSkillItem(CoachSkill skill, CoachConfig.SkillItemSettings settings) {
        int progress = manager.getProgress(player, skill);
        List<String> lore = settings.getLore().stream()
                .map(line -> line.replace("%progress%", String.valueOf(progress)))
                .toList();
        return simpleItem(settings.getMaterial(), settings.getName(), lore, settings.getCustomModelData());
    }

    private CoachConfig.SkillItemSettings getSkillSettings(CoachSkill skill) {
        return switch (skill) {
            case STRENGTH -> config.getGui().getStrengthItem();
            case DEXTERITY -> config.getGui().getDexterityItem();
            case DEFENSE -> config.getGui().getDefenseItem();
            case LUCK -> config.getGui().getLuckItem();
            case INTELLIGENCE -> config.getGui().getIntelligenceItem();
        };
    }

    private ItemStack simpleItem(Material material, String name, List<String> lore) {
        return simpleItem(material, name, lore, 0);
    }

    private ItemStack simpleItem(Material material, String name, List<String> lore, int customModelData) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(dev.grzybalson.utils.ColorUtils.colorize(name));
        meta.setLore(lore.stream().map(dev.grzybalson.utils.ColorUtils::colorize).collect(java.util.stream.Collectors.toList()));
        if (customModelData != 0) {
            meta.setCustomModelData(Integer.valueOf(customModelData));
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    public void open() {
        player.openInventory(inventory);
        SoundsUtil.gui(player);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
