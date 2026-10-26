package dev.grzybalson.gui;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.configs.CheckConfig;
import dev.grzybalson.managers.CheckManager;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;

public class CheckMenuGUI implements InventoryHolder {

    private final HollyCore plugin;
    private final Player admin;
    private final Player target;
    private Inventory inventory;

    public CheckMenuGUI(HollyCore plugin, Player admin, Player target) {
        this.plugin = plugin;
        this.admin = admin;
        this.target = target;
    }

    public void open() {
        CheckConfig config = plugin.getConfigManager().getCheckConfig();
        String title = config.getGuiTitle().replace("{player}", target.getName());

        inventory = Bukkit.createInventory(this, 27, ColorUtils.colorizeComponent(title));

        fillBackground();

        ItemStack playerHead = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skullMeta = (SkullMeta) playerHead.getItemMeta();
        skullMeta.setOwningPlayer(target);
        skullMeta.displayName(ColorUtils.colorizeComponent("&e" + target.getName()));
        skullMeta.lore(List.of(
            ColorUtils.colorizeComponent("&7Sprawdzany gracz")
        ));
        playerHead.setItemMeta(skullMeta);
        inventory.setItem(4, playerHead);

        ItemStack cleanItem = new ItemStack(Material.LIME_WOOL);
        ItemMeta cleanMeta = cleanItem.getItemMeta();
        cleanMeta.displayName(ColorUtils.colorizeComponent(config.getGuiCleanName()));
        cleanMeta.lore(ColorUtils.colorizeLore(config.getGuiCleanLore()));
        cleanItem.setItemMeta(cleanMeta);
        inventory.setItem(10, cleanItem);

        ItemStack noCoopItem = new ItemStack(Material.ORANGE_WOOL);
        ItemMeta noCoopMeta = noCoopItem.getItemMeta();
        noCoopMeta.displayName(ColorUtils.colorizeComponent(config.getGuiNoCooperationName()));
        noCoopMeta.lore(ColorUtils.colorizeLore(config.getGuiNoCooperationLore()));
        noCoopItem.setItemMeta(noCoopMeta);
        inventory.setItem(12, noCoopItem);

        ItemStack cheaterItem = new ItemStack(Material.RED_WOOL);
        ItemMeta cheaterMeta = cheaterItem.getItemMeta();
        cheaterMeta.displayName(ColorUtils.colorizeComponent(config.getGuiCheaterName()));
        cheaterMeta.lore(ColorUtils.colorizeLore(config.getGuiCheaterLore()));
        cheaterItem.setItemMeta(cheaterMeta);
        inventory.setItem(14, cheaterItem);

        ItemStack admittedItem = new ItemStack(Material.YELLOW_WOOL);
        ItemMeta admittedMeta = admittedItem.getItemMeta();
        admittedMeta.displayName(ColorUtils.colorizeComponent(config.getGuiAdmittedName()));
        admittedMeta.lore(ColorUtils.colorizeLore(config.getGuiAdmittedLore()));
        admittedItem.setItemMeta(admittedMeta);
        inventory.setItem(16, admittedItem);

        admin.openInventory(inventory);
    }

    private void fillBackground() {
        ItemStack glass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = glass.getItemMeta();
        meta.displayName(ColorUtils.colorizeComponent(" "));
        glass.setItemMeta(meta);

        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, glass);
        }
    }

    public void handleClick(int slot) {
        CheckManager.CheckResult result = switch (slot) {
            case 10 -> CheckManager.CheckResult.CLEAN;
            case 12 -> CheckManager.CheckResult.NO_COOPERATION;
            case 14 -> CheckManager.CheckResult.CHEATER;
            case 16 -> CheckManager.CheckResult.ADMITTED;
            default -> null;
        };

        if (result != null) {
            admin.closeInventory();
            plugin.getCheckManager().endCheck(admin, result);
        }
    }

    public Player getTarget() {
        return target;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
