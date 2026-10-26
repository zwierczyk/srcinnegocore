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
import dev.grzybalson.anarchiagg.coach.data.PlayerCoachData;
import dev.grzybalson.anarchiagg.coach.CoachConfig;
import dev.grzybalson.anarchiagg.util.ItemCompatibilityUtil;
import dev.grzybalson.anarchiagg.util.SoundsUtil;
import dev.grzybalson.utils.ColorUtils;

import java.util.ArrayList;
import java.util.List;

public class StrengthUpgradeGUI implements InventoryHolder {

    private final CoachManager manager;
    private final CoachConfig config;
    private final Player player;
    private final int targetLevel;
    private final Inventory inventory;

    public StrengthUpgradeGUI(CoachManager manager, CoachConfig config, Player player, int targetLevel) {
        this.manager = manager;
        this.config = config;
        this.player = player;
        this.targetLevel = targetLevel;
        this.inventory = Bukkit.createInventory(this, config.getGui().getStrengthUpgradeSize(), dev.grzybalson.utils.ColorUtils.colorize(config.getGui().getStrengthTitle()));
        fill();
    }

    private void fill() {
        fillBorders();
        CoachConfig.StrengthLevelSettings level = manager.getStrengthLevel(targetLevel);

        inventory.setItem(config.getGui().getStrengthUpgradePreviewSlot(), item(
                CoachSkill.STRENGTH.getMaterial(),
                "&#E41B53&lU&#E11A4F&lm&#DF194B&li&#DC1748&le&#DA1644&lj&#D71540&lę&#D5133D&lt&#D21239&ln&#CF1135&lo&#CD0F32&lś&#CA0E2E&lć &#C50B27&ls&#C30A23&li&#C0091F&lł&#BE071C&ly &8#" + targetLevel,
                buildPreviewLore(level),
                targetLevel
        ));

        if (targetLevel == 1) {
            fillKillOption(level);
            fillShardOption(config.getGui().getStrengthUpgradeShardsSlot(), level);
        } else {
            fillShardOption(config.getGui().getStrengthUpgradeOnlyShardsSlot(), level);
        }

        inventory.setItem(config.getGui().getStrengthUpgradeCloseSlot(), item(config.getGui().getCloseMaterial(), config.getGui().getCloseName(), List.of(), 1));
    }

    private void fillKillOption(CoachConfig.StrengthLevelSettings level) {
        PlayerCoachData data = manager.getData(player.getUniqueId());
        List<String> lore = new ArrayList<>();
        lore.add("&8x &fSzansa: &e100%");
        lore.add("&8x &fWymagania: &7Zabij &c" + level.getRequiredKills() + " osob &7("
                + data.getStrengthKills() + "/" + level.getRequiredKills() + ")");
        lore.add("");
        lore.add("&aKliknij, aby odblokować!");
        inventory.setItem(config.getGui().getStrengthUpgradeKillsSlot(), item(Material.PAPER, "&f", lore, 1));
    }

    private void fillShardOption(int slot, CoachConfig.StrengthLevelSettings level) {
        List<String> lore = new ArrayList<>();
        lore.add("&8x &fSzansa: &e100%");
        lore.add("&8x &fKoszt: &7x" + manager.getShardCost(targetLevel, level) + " &5Smoczych Odlamkow");
        lore.add("");
        lore.add("&aKliknij, aby odblokować!");
        inventory.setItem(slot, item(config.getShards().getMaterial(), "&f", lore, 1));
    }

    private List<String> buildPreviewLore(CoachConfig.StrengthLevelSettings level) {
        List<String> lore = new ArrayList<>();
        lore.add("&8x &fPostep: &e" + level.getProgressPercent() + "%");
        lore.add("&8x &fStatus: &eMozliwy do odblokowania");
        lore.add("");
        lore.add("&8x &fWłaściwości:");
        lore.addAll(manager.buildProperties(CoachSkill.STRENGTH, targetLevel));
        return lore;
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

    private ItemStack item(Material material, String name, List<String> lore, int amount) {
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

    public boolean isKillUnlockSlot(int slot) {
        return targetLevel == 1 && slot == config.getGui().getStrengthUpgradeKillsSlot();
    }

    public boolean isShardUnlockSlot(int slot) {
        if (targetLevel == 1) {
            return slot == config.getGui().getStrengthUpgradeShardsSlot();
        }
        return slot == config.getGui().getStrengthUpgradeOnlyShardsSlot();
    }

    public boolean isCloseSlot(int slot) {
        return slot == config.getGui().getStrengthUpgradeCloseSlot();
    }

    public int getTargetLevel() {
        return targetLevel;
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
