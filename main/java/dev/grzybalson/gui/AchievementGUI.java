package dev.grzybalson.gui;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.modules.achievement.AchievementConfig;
import dev.grzybalson.modules.achievement.AchievementManager;
import dev.grzybalson.modules.achievement.AchievementType;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.NBTUtils;
import dev.grzybalson.utils.ProgressUtil;
import dev.grzybalson.utils.TimeUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AchievementGUI implements InventoryHolder {

    public enum ViewType {
        MAIN, CATEGORY
    }

    private final HollyCore plugin;
    private final Player player;
    private final ViewType viewType;
    private final AchievementType categoryType;
    private Inventory inventory;

    public AchievementGUI(HollyCore plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.viewType = ViewType.MAIN;
        this.categoryType = null;
    }

    public AchievementGUI(HollyCore plugin, Player player, AchievementType categoryType) {
        this.plugin = plugin;
        this.player = player;
        this.viewType = ViewType.CATEGORY;
        this.categoryType = categoryType;
    }

    public void open() {
        AchievementConfig config = plugin.getModuleConfigManager().getAchievementConfig();

        if (viewType == ViewType.MAIN) {
            inventory = Bukkit.createInventory(this, plugin.getGuiFactory().getInventorySize(config.getGuiRows()),
                ColorUtils.colorizeComponent(config.getGuiTitle()));
            plugin.getGuiFactory().applyDefaultTemplate(inventory);
            setupMainGUI();
        } else {
            String title = config.getSubGuiTitle().replace("{category}",
                categoryType != null ? categoryType.getDisplayName() : "");
            inventory = Bukkit.createInventory(this, plugin.getGuiFactory().getInventorySize(config.getSubGuiRows()),
                ColorUtils.colorizeComponent(title));
            plugin.getGuiFactory().applyDefaultTemplate(inventory);
            setupCategoryGUI();
        }

        player.openInventory(inventory);
    }

    private void setupMainGUI() {
        AchievementConfig config = plugin.getModuleConfigManager().getAchievementConfig();
        AchievementManager manager = plugin.getAchievementManager();

        for (AchievementType type : AchievementType.values()) {
            Material mat = Material.getMaterial(config.getCategoryMaterial(type));
            if (mat == null) mat = Material.STONE;

            Map<Integer, Integer> tiers = config.getTiersForType(type);
            int claimed = manager.getClaimedLevel(player.getUniqueId(), type);
            int total = tiers.size();

            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.displayName(ColorUtils.colorizeComponent(config.getCategoryName(type))
                    .decoration(TextDecoration.ITALIC, false));

                List<Component> lore = new ArrayList<>();
                lore.add(Component.empty());
                lore.addAll(ColorUtils.colorizeLore(config.getCategoryClaimedLine()
                    .replace("{claimed}", String.valueOf(claimed))
                    .replace("{total}", String.valueOf(total))));
                lore.add(Component.empty());
                for (String line : config.getCategoryLore(type)) {
                    lore.addAll(ColorUtils.colorizeLore(line));
                }
                meta.lore(lore);
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                item.setItemMeta(meta);
            }

            NBTUtils.setString(item, "achievement_category", type.name());
            inventory.setItem(config.getCategorySlot(type), item);
        }
    }

    private void setupCategoryGUI() {
        if (categoryType == null) return;

        AchievementConfig config = plugin.getModuleConfigManager().getAchievementConfig();
        AchievementManager manager = plugin.getAchievementManager();
        Map<Integer, Integer> tiers = config.getTiersForType(categoryType);

        Material categoryMaterial = Material.getMaterial(config.getCategoryMaterial(categoryType));
        if (categoryMaterial == null) categoryMaterial = Material.STONE;

        int slot = 20;
        for (Map.Entry<Integer, Integer> entry : tiers.entrySet()) {
            int tier = entry.getKey();
            int required = entry.getValue();
            int progress = manager.getProgress(player.getUniqueId(), categoryType);
            boolean completed = progress >= required;
            boolean claimed = manager.isClaimed(player.getUniqueId(), categoryType, tier);

            ItemStack item = new ItemStack(categoryMaterial);
            item.setAmount(Math.max(1, Math.min(64, tier)));
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                String name = config.getCategoryName(categoryType) + " &8#" + tier;
                meta.displayName(ColorUtils.colorizeComponent(name).decoration(TextDecoration.ITALIC, false));

                List<Component> lore = new ArrayList<>();
                for (String line : config.getCategoryIntroLore()) {
                    lore.addAll(ColorUtils.colorizeLore(line));
                }
                lore.add(Component.empty());
                lore.addAll(ColorUtils.colorizeLore("&5&lNagrody:"));

                ItemStack[] rewards = manager.getRewardItems(categoryType, tier);
                if (rewards != null) {
                    for (ItemStack reward : rewards) {
                        if (reward != null && !reward.getType().isAir()) {
                            lore.addAll(ColorUtils.colorizeLore(" &8- &fx" + reward.getAmount() + " " + getItemDisplayName(reward)));
                        }
                    }
                }

                lore.add(Component.empty());
                if (categoryType == AchievementType.PLAYTIME) {
                    lore.addAll(ColorUtils.colorizeLore(" &7Posiadasz: &f" + TimeUtil.convert(progress) + "&8/&5" + TimeUtil.convert(required)));
                } else {
                    lore.addAll(ColorUtils.colorizeLore(" &7Posiadasz: &f" + progress + "&8/&5" + required));
                }
                lore.addAll(ColorUtils.colorizeLore(" &7Progres: &8[" + ProgressUtil.bar(progress, required, 10) + "&8] &f" + ProgressUtil.percent(progress, required)));
                lore.add(Component.empty());

                if (claimed) {
                    lore.addAll(ColorUtils.colorizeLore(config.getStatusClaimed()));
                } else if (completed) {
                    lore.addAll(ColorUtils.colorizeLore(config.getStatusCompleted()));
                } else {
                    lore.addAll(ColorUtils.colorizeLore(config.getStatusNotCompleted()));
                }

                meta.lore(lore);
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                item.setItemMeta(meta);
            }

            NBTUtils.setString(item, "achievement_type", categoryType.name());
            NBTUtils.setInt(item, "achievement_tier", tier);
            inventory.setItem(slot, item);
            slot++;
        }

        ItemStack backItem = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = backItem.getItemMeta();
        if (backMeta != null) {
            backMeta.displayName(ColorUtils.colorizeComponent(config.getBackButtonName())
                .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            for (String line : config.getBackButtonLore()) {
                lore.addAll(ColorUtils.colorizeLore(line));
            }
            backMeta.lore(lore);
            backItem.setItemMeta(backMeta);
        }
        NBTUtils.setString(backItem, "achievement_action", "back");
        inventory.setItem(inventory.getSize() - 5, backItem);
    }

    private String getItemDisplayName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            Component displayName = item.getItemMeta().displayName();
            if (displayName != null) {
                return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                    .legacyAmpersand().serialize(displayName);
            }
        }
        String name = item.getType().name().replace("_", " ");
        StringBuilder sb = new StringBuilder();
        for (String word : name.split(" ")) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(word.charAt(0)).append(word.substring(1).toLowerCase());
        }
        return sb.toString();
    }

    public ViewType getViewType() { return viewType; }
    public AchievementType getCategoryType() { return categoryType; }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
