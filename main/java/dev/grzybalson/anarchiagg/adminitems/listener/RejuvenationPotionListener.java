package dev.grzybalson.anarchiagg.adminitems.listener;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import dev.grzybalson.HollyCore;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsConfig;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsCooldowns;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsRegionUtil;
import dev.grzybalson.utils.ColorUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RejuvenationPotionListener implements Listener {

    private static final int ABSORPTION_AMPLIFIER = 9;
    private static final double ABSORPTION_HEALTH_PER_LEVEL = 4.0;

    private final HollyCore plugin;
    private final AdminItemsConfig config;
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public RejuvenationPotionListener(HollyCore plugin) {
        this.plugin = plugin;
        this.config = plugin.getAdminItemsConfig();
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (!isRejuvenationPotion(item)) {
            return;
        }

        if (!event.getAction().toString().contains("RIGHT_CLICK")) {
            return;
        }

        event.setCancelled(true);

        AdminItemsConfig.RejuvenationPotionSettings settings = config.getRejuvenationPotion();

        if (AdminItemsRegionUtil.isInBlockedRegion(player, plugin)) {
            player.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(
                    settings.getName() + " &8:: &cNie możesz używać tego przedmiotu na spawnie!"
            ));
            return;
        }

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        long cooldownMillis = settings.getCooldown() * 1000L;

        if (cooldowns.containsKey(uuid)) {
            long cooldownUntil = cooldowns.get(uuid);
            long timeLeft = cooldownUntil - now;
            if (timeLeft > 0) {
                AdminItemsCooldowns.refresh(player, item.getType(), cooldownUntil);
                player.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(
                        settings.getName() + " &8:: &cMusisz poczekać jeszcze &4" + (timeLeft / 1000) + "s"
                ));
                return;
            }
            dev.grzybalson.anarchiagg.util.MapCompatibilityUtil.remove(cooldowns, uuid, cooldownUntil);
        }

        double maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        player.setHealth(maxHealth);

        int durationTicks = settings.getGoldHeartsDuration() * 20;
        player.removePotionEffect(PotionEffectType.ABSORPTION);
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, durationTicks, ABSORPTION_AMPLIFIER, false, true, true));
        player.setAbsorptionAmount((ABSORPTION_AMPLIFIER + 1) * ABSORPTION_HEALTH_PER_LEVEL);

        player.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(
                settings.getName() + " &8:: &aTwoje zdrowie zostało w pełni zregenerowane!"
        ));
        player.playSound(player.getLocation(), Sound.ITEM_HONEY_BOTTLE_DRINK, 1.0F, 0.7F);

        cooldowns.put(uuid, now + cooldownMillis);
        AdminItemsCooldowns.apply(player, item.getType(), cooldownMillis);
        player.updateInventory();

    }

    private boolean isRejuvenationPotion(ItemStack item) {
        if (item == null || item.getType() != Material.HONEY_BOTTLE || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta.hasCustomModelData() && meta.getCustomModelData() == config.getRejuvenationPotion().getCustomModelData();
    }
}
