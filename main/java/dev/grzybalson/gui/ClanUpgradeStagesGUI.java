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

public class ClanUpgradeStagesGUI implements InventoryHolder {

    private final HollyCore plugin;
    private final Player player;
    private final String upgradeType;
    private Inventory inventory;

    public ClanUpgradeStagesGUI(HollyCore plugin, Player player, String upgradeType) {
        this.plugin = plugin;
        this.player = player;
        this.upgradeType = upgradeType == null ? "" : upgradeType.trim().toUpperCase(Locale.ROOT);
    }

    public void open() {
        ClanConfig config = plugin.getConfigManager().getClanConfig();
        ClanConfig.ClanUpgradesSettings upgrades = config.getUpgrades();
        if (upgrades == null || !upgrades.isEnabled()) {
            player.sendMessage(ColorUtils.colorizeComponent(config.getClanUpgradeDisabledMessage()));
            plugin.getSoundManager().playError(player);
            return;
        }

        ClanConfig.UpgradeCategory category = config.findUpgradeCategory(upgradeType);
        if (category == null) {
            plugin.getSoundManager().playError(player);
            return;
        }

        plugin.getClanManager().getPlayerClan(player.getUniqueId()).thenAccept(clanTag -> {
            if (clanTag == null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage(ColorUtils.colorizeComponent(config.getClanNotInClanMessage()));
                    plugin.getSoundManager().playError(player);
                });
                return;
            }

            plugin.getClanManager().getClan(clanTag).thenAccept(clan -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (clan == null) {
                        player.sendMessage(ColorUtils.colorizeComponent(config.getClanNotInClanMessage()));
                        plugin.getSoundManager().playError(player);
                        return;
                    }
                    openInventory(clan, upgrades, category);
                });
            });
        });
    }

    private void openInventory(Clan clan, ClanConfig.ClanUpgradesSettings upgrades, ClanConfig.UpgradeCategory category) {
        int rows = Math.max(1, Math.min(6, upgrades.getGuiRows()));
        inventory = Bukkit.createInventory(this, rows * 9,
            ColorUtils.colorizeComponent(upgrades.getStagesGuiTitle()));
        plugin.getGuiFactory().applyDefaultTemplate(inventory);

        int level = clan.getUpgradeLevel(upgradeType);
        if (category.getStages() != null) {
            for (ClanConfig.UpgradeStage stage : category.getStages()) {
                if (stage == null) {
                    continue;
                }
                int slot = stage.getSlot();
                if (slot < 0 || slot >= inventory.getSize()) {
                    continue;
                }
                inventory.setItem(slot, createStageItem(category, stage, level, upgrades.getCurrencyName()));
            }
        }

        int backSlot = plugin.getGuiFactory().resolveSlot(
            inventory,
            upgrades.getBackSlot(),
            plugin.getGuiFactory().bottomRowSlot(inventory, 4)
        );
        ItemStack back = new ItemStack(Material.OAK_FENCE_GATE);
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            backMeta.displayName(ColorUtils.colorizeComponent(upgrades.getBackName()).decoration(TextDecoration.ITALIC, false));
            back.setItemMeta(backMeta);
        }
        inventory.setItem(backSlot, NBTUtils.setString(back, "clan_upgrade_action", "back"));

        player.openInventory(inventory);
        plugin.getSoundManager().playGuiOpen(player);
    }

    private ItemStack createStageItem(ClanConfig.UpgradeCategory category, ClanConfig.UpgradeStage stage,
                                     int currentLevel, String currencyName) {
        boolean owned = currentLevel >= stage.getStage();
        Material material = owned ? Material.LIME_DYE : Material.LIGHT_GRAY_DYE;
        ClanConfig.ClanUpgradesSettings upgrades = plugin.getConfigManager().getClanConfig().getUpgrades();
        ClanConfig.UpgradeGuiItem template = upgrades != null ? upgrades.getUpgradeItem() : null;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        String description = category.getDescription() == null ? "" : category.getDescription()
            .replace("{CHANCE}", formatNumber(stage.getChance()))
            .replace("{AMPLIFIER}", formatNumber(stage.getAmplifier()));

        String coinName = plugin.getConfigManager().getClanConfig().getClanCoinDisplay();
        if (coinName == null || coinName.isBlank()) {
            coinName = currencyName;
        }

        Map<String, String> vars = new java.util.LinkedHashMap<>();
        vars.put("{NAME}", category.getName() == null ? "&fUlepszenie" : category.getName());
        vars.put("{STAGE}", String.valueOf(stage.getStage()));
        vars.put("{PRICE}", String.valueOf(stage.getPrice()));
        vars.put("{SHARD}", coinName);
        vars.put("{CURRENCY}", currencyName);
        vars.put("{DESCRIPTION}", description);
        vars.put("{CHANCE}", formatNumber(stage.getChance()));
        vars.put("{AMPLIFIER}", formatNumber(stage.getAmplifier()));

        String displayTemplate = template != null && template.getDisplay() != null ? template.getDisplay() : "{NAME} &f#{STAGE}";
        meta.displayName(ColorUtils.colorizeComponent(applyVars(displayTemplate, vars)).decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        List<String> loreLines = template != null && template.getLore() != null && !template.getLore().isEmpty()
            ? template.getLore()
            : List.of("", " &8× &7Cena: &fx{PRICE} {SHARD}", " &8× &f{DESCRIPTION}", "", "&fKliknij, aby ulepszyć");
        for (String line : loreLines) {
            ColorUtils.appendLore(lore, applyVars(line, vars));
        }
        if (owned) {
            ColorUtils.appendLore(lore, "&aPosiadane");
        } else if (currentLevel + 1 == stage.getStage()) {
            ColorUtils.appendLore(lore, "&eKliknij, aby kupić (tylko lider)");
        } else {
            ColorUtils.appendLore(lore, "&cNajpierw kup poprzedni poziom");
        }
        meta.lore(lore);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);

        ItemStack tagged = NBTUtils.setString(item, "clan_upgrade_action", "buy");
        tagged = NBTUtils.setString(tagged, "clan_upgrade_type", upgradeType);
        tagged = NBTUtils.setInt(tagged, "clan_upgrade_stage", stage.getStage());
        return tagged;
    }

    private static String formatNumber(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0001D) {
            return String.valueOf((long) Math.rint(value));
        }
        return String.valueOf(value);
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
            new ClanUpgradesGUI(plugin, player).open();
            return;
        }
        if (!"buy".equals(action)) {
            return;
        }

        Integer stageNumber = NBTUtils.getInt(clicked, "clan_upgrade_stage");
        if (stageNumber == null || stageNumber <= 0) {
            return;
        }

        attemptPurchase(stageNumber);
    }

    private void attemptPurchase(int stageNumber) {
        ClanConfig config = plugin.getConfigManager().getClanConfig();
        ClanConfig.UpgradeCategory category = config.findUpgradeCategory(upgradeType);
        if (category == null) {
            return;
        }
        ClanConfig.UpgradeStage stage = category.findStage(stageNumber);
        if (stage == null) {
            return;
        }

        plugin.getClanManager().getPlayerClan(player.getUniqueId()).thenAccept(clanTag -> {
            if (clanTag == null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage(ColorUtils.colorizeComponent(config.getClanNotInClanMessage()));
                    plugin.getSoundManager().playError(player);
                });
                return;
            }

            plugin.getClanManager().getClan(clanTag).thenAccept(clan -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (clan == null) {
                        player.sendMessage(ColorUtils.colorizeComponent(config.getClanNotInClanMessage()));
                        plugin.getSoundManager().playError(player);
                        return;
                    }

                    if (!clan.isLeader(player.getUniqueId())) {
                        player.sendMessage(ColorUtils.colorizeComponent(config.getClanNotLeaderMessage()));
                        plugin.getSoundManager().playError(player);
                        return;
                    }

                    int level = clan.getUpgradeLevel(upgradeType);
                    if (level >= stage.getStage()) {
                        return;
                    }
                    if (level + 1 != stage.getStage()) {
                        player.sendMessage(ColorUtils.colorizeComponent(config.getClanUpgradeNeedPreviousMessage()));
                        plugin.getSoundManager().playError(player);
                        return;
                    }
                    if (clan.getCoins() < stage.getPrice()) {
                        String currency = config.getUpgrades() != null ? config.getUpgrades().getCurrencyName() : "";
                        player.sendMessage(ColorUtils.colorizeComponent(
                            config.getClanUpgradeNotEnoughCoinsMessage()
                                .replace("{currency}", currency)
                                .replace("{price}", String.valueOf(stage.getPrice()))
                        ));
                        plugin.getSoundManager().playError(player);
                        return;
                    }

                    plugin.getClanManager().purchaseUpgrade(clanTag, upgradeType, stage.getStage(), stage.getPrice())
                        .thenAccept(success -> Bukkit.getScheduler().runTask(plugin, () -> {
                            if (!success) {
                                player.sendMessage(ColorUtils.colorizeComponent(config.getClanUpgradeNeedPreviousMessage()));
                                plugin.getSoundManager().playError(player);
                                return;
                            }
                            player.sendMessage(ColorUtils.colorizeComponent(config.getClanUpgradePurchasedMessage()));
                            plugin.getSoundManager().playSuccess(player);
                            new ClanUpgradeStagesGUI(plugin, player, upgradeType).open();
                        }));
                });
            });
        });
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
