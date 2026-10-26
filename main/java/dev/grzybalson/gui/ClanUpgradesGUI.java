package dev.grzybalson.gui;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.configs.ClanConfig;
import dev.grzybalson.database.ClanDataManager.Clan;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.NBTUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ClanUpgradesGUI implements InventoryHolder {

    private final HollyCore plugin;
    private final Player player;
    private Inventory inventory;

    public ClanUpgradesGUI(HollyCore plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public void open() {
        ClanConfig.ClanUpgradesSettings upgrades = plugin.getConfigManager().getClanConfig().getUpgrades();
        if (upgrades == null || !upgrades.isEnabled()) {
            player.sendMessage(ColorUtils.colorizeComponent(
                plugin.getConfigManager().getClanConfig().getClanUpgradeDisabledMessage()
            ));
            plugin.getSoundManager().playError(player);
            return;
        }

        plugin.getClanManager().getPlayerClan(player.getUniqueId()).thenAccept(clanTag -> {
            if (clanTag == null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage(ColorUtils.colorizeComponent(
                        plugin.getConfigManager().getClanConfig().getClanNotInClanMessage()
                    ));
                    plugin.getSoundManager().playError(player);
                });
                return;
            }

            plugin.getClanManager().getClan(clanTag).thenAccept(clan -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (clan == null) {
                        player.sendMessage(ColorUtils.colorizeComponent(
                            plugin.getConfigManager().getClanConfig().getClanNotInClanMessage()
                        ));
                        plugin.getSoundManager().playError(player);
                        return;
                    }
                    openInventory(clan, upgrades);
                });
            });
        });
    }

    private void openInventory(Clan clan, ClanConfig.ClanUpgradesSettings upgrades) {
        int rows = Math.max(1, Math.min(6, upgrades.getGuiRows()));
        inventory = Bukkit.createInventory(this, rows * 9,
            ColorUtils.colorizeComponent(upgrades.getGuiTitle()));
        plugin.getGuiFactory().applyDefaultTemplate(inventory);

        Map<String, ClanConfig.UpgradeCategory> categories = upgrades.getCategories();
        if (categories != null) {
            for (Map.Entry<String, ClanConfig.UpgradeCategory> entry : categories.entrySet()) {
                ClanConfig.UpgradeCategory category = entry.getValue();
                if (category == null) {
                    continue;
                }
                int slot = category.getSlot();
                if (slot < 0 || slot >= inventory.getSize()) {
                    continue;
                }
                inventory.setItem(slot, createCategoryItem(entry.getKey(), category, clan));
            }
        }

        int backSlot = plugin.getGuiFactory().resolveSlot(
            inventory,
            upgrades.getBackSlot(),
            plugin.getGuiFactory().bottomRowSlot(inventory, 4)
        );
        inventory.setItem(backSlot, createNamedItem(Material.OAK_FENCE_GATE, upgrades.getBackName(), "back"));

        player.openInventory(inventory);
        plugin.getSoundManager().playGuiOpen(player);
    }

    private ItemStack createCategoryItem(String type, ClanConfig.UpgradeCategory category, Clan clan) {
        Material material;
        try {
            material = Material.valueOf(category.getMaterial().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException exception) {
            material = Material.PAPER;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        ClanConfig.ClanUpgradesSettings upgrades = plugin.getConfigManager().getClanConfig().getUpgrades();
        ClanConfig.UpgradeGuiItem template = upgrades != null ? upgrades.getUpgradeInfo() : null;
        String displayTemplate = template != null && template.getDisplay() != null && !template.getDisplay().isBlank()
            ? template.getDisplay()
            : "{NAME}";
        String display = applyVars(displayTemplate, Map.of(
            "{NAME}", category.getName() == null ? "&fUlepszenie" : category.getName()
        ));
        meta.displayName(ColorUtils.colorizeComponent(display).decoration(TextDecoration.ITALIC, false));

        int stageCount = category.getStages() != null ? category.getStages().size() : 0;
        int level = clan.getUpgradeLevel(type);
        String currency = plugin.getConfigManager().getClanConfig().getUpgrades().getCurrencyName();
        String coinName = plugin.getConfigManager().getClanConfig().getClanCoinDisplay();
        if (coinName == null || coinName.isBlank()) {
            coinName = currency;
        }

        Map<String, String> vars = Map.of(
            "{NAME}", category.getName() == null ? "&fUlepszenie" : category.getName(),
            "{UPGRADES}", String.valueOf(stageCount),
            "{LEVEL}", String.valueOf(level),
            "{SHARD}", coinName,
            "{CURRENCY}", currency
        );

        List<Component> lore = new ArrayList<>();
        List<String> loreLines = template != null && template.getLore() != null && !template.getLore().isEmpty()
            ? template.getLore()
            : List.of("", " &8× &7W tej kategorii są dostępne &f{UPGRADES}&7 ulepszenia.", "", "&fKliknij, aby otworzyć!");
        for (String line : loreLines) {
            ColorUtils.appendLore(lore, applyVars(line, vars));
        }
        meta.lore(lore);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);

        return NBTUtils.setString(item, "clan_upgrade_type", type.toUpperCase(Locale.ROOT));
    }

    private ItemStack createNamedItem(Material material, String name, String action) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(ColorUtils.colorizeComponent(name).decoration(TextDecoration.ITALIC, false));
            item.setItemMeta(meta);
        }
        return NBTUtils.setString(item, "clan_upgrade_action", action);
    }

    private static String applyVars(String text, Map<String, String> vars) {
        if (text == null) {
            return "";
        }
        String result = text;
        for (Map.Entry<String, String> entry : vars.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }

    public void handleClick(int slot) {
        if (inventory == null) {
            return;
        }
        ItemStack clicked = inventory.getItem(slot);
        if (clicked == null || plugin.getGuiFactory().isBackgroundItem(clicked)) {
            return;
        }

        String action = NBTUtils.getString(clicked, "clan_upgrade_action");
        if ("back".equals(action)) {
            player.closeInventory();
            return;
        }

        String type = NBTUtils.getString(clicked, "clan_upgrade_type");
        if (type == null || type.isBlank()) {
            return;
        }

        new ClanUpgradeStagesGUI(plugin, player, type).open();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
