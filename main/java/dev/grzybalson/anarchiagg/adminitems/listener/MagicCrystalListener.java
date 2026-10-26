package dev.grzybalson.anarchiagg.adminitems.listener;

import dev.grzybalson.HollyCore;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsConfig;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsGUI;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsRegionUtil;
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

public class MagicCrystalListener implements Listener {

    private final HollyCore plugin;
    private final AdminItemsConfig config;

    public MagicCrystalListener(HollyCore plugin) {
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
        if (!isMagicCrystal(item)) {
            return;
        }

        event.setCancelled(true);
        AdminItemsConfig.MagicCrystalSettings settings = config.getMagicCrystal();
        if (AdminItemsRegionUtil.isInBlockedRegion(player, plugin)) {
            AdminItemsRegionUtil.sendBlockedMessage(player, plugin);
            return;
        }

        int hearts = Math.max(1, settings.getGoldenHearts());
        // Absorption amp 0 = 2 serca; amp N = (N+1)*2 serc
        int amplifier = Math.max(0, (hearts / 2) - 1);
        int durationTicks = Math.max(1, settings.getAbsorptionDurationSeconds()) * 20;
        double absorptionHp = hearts * 2.0D;

        player.removePotionEffect(PotionEffectType.ABSORPTION);
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, durationTicks, amplifier, false, true, true));
        player.setAbsorptionAmount(absorptionHp);

        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }

        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0F, 1.2F);
        player.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(
            settings.getName() + " &8:: &aOtrzymales &e" + hearts + " &azlotych serc!"
        ));
    }

    private boolean isMagicCrystal(ItemStack item) {
        if (item == null || item.getType() != Material.AMETHYST_SHARD || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(AdminItemsGUI.KEY_MAGICZNY_KRYSZTAL, PersistentDataType.BYTE);
    }
}
