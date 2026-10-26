package dev.grzybalson.listeners;

import dev.grzybalson.HollyCore;
import dev.grzybalson.gui.ShopGUI;
import dev.grzybalson.gui.KitGUI;
import dev.grzybalson.gui.WarpGUI;
import dev.grzybalson.gui.MarketGUI;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.NBTUtils;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

public class DeepslateInteractListener implements Listener {

    private final HollyCore plugin;

    public DeepslateInteractListener(HollyCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.REINFORCED_DEEPSLATE) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null || item.getType().isAir()) {
            return;
        }

        if (item.getItemMeta() instanceof Damageable damageable) {
            event.setCancelled(true);
            if (damageable.hasDamage()) {
                repairItemInHand(player, item, event.getHand());
            } else {
                player.sendActionBar(ColorUtils.colorizeComponent("&cTego przedmiotu nie możesz naprawić!"));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }
            return;
        }

        String action = NBTUtils.getString(item, "deepslate_action");
        if (action == null) return;

        event.setCancelled(true);

        switch (action) {
            case "open_shop" -> {
                if (!plugin.isAddonEnabled("shop")) return;
                new ShopGUI(plugin, player, ShopGUI.ShopType.MAIN).open();
                player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
            }
            case "open_kit" -> {
                if (!plugin.isAddonEnabled("kit")) return;
                new KitGUI(plugin, player).open();
                player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
            }
            case "open_warp" -> {
                if (!plugin.isAddonEnabled("warps")) return;
                new WarpGUI(plugin, player).open();
                player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
            }
            case "open_market" -> {
                if (!plugin.isAddonEnabled("market")) return;
                new MarketGUI(plugin, player).open();
                player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
            }
            case "repair_item" -> {
                repairItem(player, item);
            }
            default -> {
                String command = NBTUtils.getString(item, "deepslate_command");
                if (command != null && !command.isEmpty()) {
                    player.performCommand(command);
                }
            }
        }
    }

    private void repairItem(Player player, ItemStack repairTool) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();

        ItemStack toRepair = null;
        boolean isMainHand = false;

        if (mainHand != repairTool && mainHand.getItemMeta() instanceof Damageable damageable && damageable.hasDamage()) {
            toRepair = mainHand;
            isMainHand = true;
        } else if (offHand != repairTool && offHand.getItemMeta() instanceof Damageable damageable && damageable.hasDamage()) {
            toRepair = offHand;
        }

        if (toRepair == null) {
            player.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cNie masz przedmiotu do naprawy w ręce!"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        if (!dev.grzybalson.utils.ItemRepair.repair(toRepair)) {
            player.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cNie masz przedmiotu do naprawy w ręce!"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        if (isMainHand) {
            player.getInventory().setItemInMainHand(toRepair);
        } else {
            player.getInventory().setItemInOffHand(toRepair);
        }

        player.sendMessage(ColorUtils.colorizeComponent("&#00FF3B✔ &8Ξ &#4EFA76Przedmiot został naprawiony!"));
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
    }

    private void repairItemInHand(Player player, ItemStack item, EquipmentSlot hand) {
        if (!dev.grzybalson.utils.ItemRepair.repair(item)) {
            return;
        }

        if (hand == EquipmentSlot.HAND) {
            player.getInventory().setItemInMainHand(item);
        } else {
            player.getInventory().setItemInOffHand(item);
        }

        player.sendMessage(ColorUtils.colorizeComponent("&#00FF3B✔ &8Ξ &#4EFA76Przedmiot został naprawiony!"));
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
    }
}
