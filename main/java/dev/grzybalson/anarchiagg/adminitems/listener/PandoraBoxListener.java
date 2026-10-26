package dev.grzybalson.anarchiagg.adminitems.listener;

import org.bukkit.Material;
import org.bukkit.Sound;
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
import java.util.Random;
import java.util.UUID;

public class PandoraBoxListener implements Listener {

    private final HollyCore plugin;
    private final AdminItemsConfig config;
    private final Random random = new Random();

    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public PandoraBoxListener(HollyCore plugin) {
        this.plugin = plugin;
        this.config = plugin.getAdminItemsConfig();
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (!isPandoraBox(item)) {
            return;
        }

        if (!event.getAction().toString().contains("RIGHT_CLICK")) {
            return;
        }

        event.setCancelled(true);

        AdminItemsConfig.PandoraBoxSettings settings = config.getPandoraBox();

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

        cooldowns.put(uuid, now + cooldownMillis);
        AdminItemsCooldowns.apply(player, item.getType(), cooldownMillis);

        int durationTicks = settings.getEffectDuration() * 20;

        if (random.nextDouble() * 100 < settings.getPositiveChance()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, durationTicks, 1));
            player.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(
                    settings.getName() + " &8:: &aOtrzymałeś efekt &cSiły II &ana &f" + settings.getEffectDuration() + "s!"
            ));
        } else {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, durationTicks, 1));
            player.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(
                    settings.getName() + " &8:: &aOtrzymałeś efekt &7Spowolnienia &ana &f" + settings.getEffectDuration() + "s!"
            ));
        }

        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }
    }

    private boolean isPandoraBox(ItemStack item) {

        if (item == null) {
            return false;
        }

        if (item.getType() != Material.FLOWER_POT) {
            return false;
        }

        if (!item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();

        return meta.hasCustomModelData()
                && meta.getCustomModelData()
                == config.getPandoraBox().getCustomModelData();
    }

    public void resetCooldown(UUID uuid) {
        cooldowns.remove(uuid);
    }
}
