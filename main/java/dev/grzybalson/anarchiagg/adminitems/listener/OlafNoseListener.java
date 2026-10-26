package dev.grzybalson.anarchiagg.adminitems.listener;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import dev.grzybalson.HollyCore;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsConfig;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsCooldowns;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsRegionUtil;
import dev.grzybalson.utils.ColorUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class OlafNoseListener implements Listener {

    private final HollyCore plugin;
    private final AdminItemsConfig config;

    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public OlafNoseListener(HollyCore plugin) {
        this.plugin = plugin;
        this.config = plugin.getAdminItemsConfig();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }

        if (!(event.getEntity() instanceof Player target)) {
            return;
        }

        ItemStack item = attacker.getInventory().getItemInMainHand();

        if (!isOlafNose(item)) {
            return;
        }

        AdminItemsConfig.OlafNoseSettings settings = config.getOlafNose();
        if (AdminItemsRegionUtil.isInBlockedRegion(attacker, this.plugin) || AdminItemsRegionUtil.isInBlockedRegion(target, this.plugin)) {
            event.setCancelled(true);
            AdminItemsRegionUtil.sendBlockedMessage(attacker, this.plugin);
            return;
        }

        UUID uuid = attacker.getUniqueId();
        long now = System.currentTimeMillis();
        long cooldownMillis = settings.getCooldown() * 1000L;

        if (cooldowns.containsKey(uuid)) {
            long cooldownUntil = cooldowns.get(uuid);
            long timeLeft = cooldownUntil - now;

            if (timeLeft > 0) {
                AdminItemsCooldowns.refresh(attacker, item.getType(), cooldownUntil);
                event.setCancelled(true);
                attacker.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(
                        settings.getName() + " &8:: &cMusisz poczekać jeszcze &4" + (timeLeft / 1000) + "s&c!"
                ));
                return;
            }
            dev.grzybalson.anarchiagg.util.MapCompatibilityUtil.remove(cooldowns, uuid, cooldownUntil);
        }

        cooldowns.put(uuid, now + cooldownMillis);
        AdminItemsCooldowns.apply(attacker, item.getType(), cooldownMillis);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (isOlafNose(item) && event.getAction().toString().contains("RIGHT_CLICK")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();

        if (isOlafNose(item)) {
            event.setCancelled(true);
        }
    }

    private boolean isOlafNose(ItemStack item) {
        if (item == null) {
            return false;
        }

        if (item.getType() != Material.CARROT) {
            return false;
        }

        if (!item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();

        return meta.hasCustomModelData()
                && meta.getCustomModelData()
                == config.getOlafNose().getCustomModelData();
    }

    public void resetCooldown(UUID uuid) {
        cooldowns.remove(uuid);
    }
}
