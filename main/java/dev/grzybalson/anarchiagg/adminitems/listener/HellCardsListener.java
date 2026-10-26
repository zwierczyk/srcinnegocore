package dev.grzybalson.anarchiagg.adminitems.listener;

import dev.grzybalson.HollyCore;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsConfig;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsGUI;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsRegionUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.concurrent.ThreadLocalRandom;

public class HellCardsListener implements Listener {

    private final HollyCore plugin;
    private final AdminItemsConfig config;

    public HellCardsListener(HollyCore plugin) {
        this.plugin = plugin;
        this.config = plugin.getAdminItemsConfig();
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (!isHellCards(item)) {
            return;
        }

        event.setCancelled(true);
        AdminItemsConfig.HellCardsSettings settings = config.getHellCards();
        if (AdminItemsRegionUtil.isInBlockedRegion(player, plugin)) {
            AdminItemsRegionUtil.sendBlockedMessage(player, plugin);
            return;
        }

        int radius = Math.max(1, settings.getRadius());
        int durationTicks = Math.max(1, settings.getEffectDurationSeconds()) * 20;
        Location origin = player.getLocation();
        PotionEffectType type;
        int amplifier;
        String effectName;
        int roll = ThreadLocalRandom.current().nextInt(3);
        if (roll == 0) {
            type = PotionEffectType.INCREASE_DAMAGE;
            amplifier = 0;
            effectName = "&cSiłę I";
        } else if (roll == 1) {
            type = PotionEffectType.SLOW;
            amplifier = 2;
            effectName = "&8Spowolnienie III";
        } else {
            type = PotionEffectType.REGENERATION;
            amplifier = 4;
            effectName = "&dRegenerację V";
        }

        int affected = 0;
        for (Player target : player.getWorld().getPlayers()) {
            if (!target.getWorld().equals(origin.getWorld())) {
                continue;
            }
            if (target.getLocation().distanceSquared(origin) > radius * (double) radius) {
                continue;
            }
            if (AdminItemsRegionUtil.isInBlockedRegion(target, plugin)) {
                continue;
            }
            target.addPotionEffect(new PotionEffect(type, durationTicks, amplifier, false, true, true));
            affected++;
        }

        consumeOne(player, item);
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0F, 0.7F);
        player.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(
            settings.getName() + " &8:: &aNalozyles " + effectName + " &ana &f" + affected + " &aosob!"
        ));
    }

    private void consumeOne(Player player, ItemStack item) {
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }
    }

    private boolean isHellCards(ItemStack item) {
        if (item == null || item.getType() != Material.PAPER || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(AdminItemsGUI.KEY_KARTY_PIEKLA, PersistentDataType.BYTE);
    }
}
