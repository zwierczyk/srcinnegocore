package dev.grzybalson.anarchiagg.adminitems.listener;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;
import dev.grzybalson.HollyCore;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsConfig;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsCooldowns;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsRegionUtil;
import dev.grzybalson.utils.ColorUtils;

public class TornadoListener
implements Listener {
    private final HollyCore plugin;
    private final AdminItemsConfig config;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<UUID, Long>();

    public TornadoListener(HollyCore plugin) {
        this.plugin = plugin;
        this.config = plugin.getAdminItemsConfig();
    }

    @EventHandler
    public void onUseTornado(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!this.isTornado(item)) {
            return;
        }
        AdminItemsConfig.TornadoSettings settings = this.config.getTornado();
        if (settings == null || !settings.isEnabled()) {
            return;
        }
        if (AdminItemsRegionUtil.isInBlockedRegion(player, this.plugin)) {
            player.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(settings.getName() + " &8:: &cNie mo\u017cesz u\u017cywa\u0107 tego przedmiotu na spawnie!"));
            event.setCancelled(true);
            return;
        }
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        long cooldownMillis = (long)settings.getCooldown() * 1000L;
        if (this.cooldowns.containsKey(uuid)) {
            long cooldownUntil = this.cooldowns.get(uuid);
            long timeLeft = cooldownUntil - now;
            if (timeLeft > 0L) {
                AdminItemsCooldowns.refresh(player, item.getType(), cooldownUntil);
                player.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(settings.getName() + " &8:: &cMusisz poczeka\u0107 jeszcze &4" + timeLeft / 1000L + "s"));
                event.setCancelled(true);
                return;
            }
            dev.grzybalson.anarchiagg.util.MapCompatibilityUtil.remove(this.cooldowns, uuid, cooldownUntil);
        }
        double radius = settings.getRadius();
        boolean affectedAnyone = false;
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            Vector pullDirection;
            if (!(entity instanceof LivingEntity)) continue;
            LivingEntity target = (LivingEntity)entity;
            if (entity == player || AdminItemsRegionUtil.isInBlockedRegion(target.getLocation(), this.plugin) || !((pullDirection = player.getLocation().toVector().subtract(target.getLocation().toVector())).lengthSquared() > 0.0)) continue;
            pullDirection.normalize();
            pullDirection.multiply(settings.getPullStrength());
            pullDirection.setY(pullDirection.getY() * 0.5 + 0.3);
            target.setVelocity(pullDirection);
            affectedAnyone = true;
        }
        player.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(settings.getMessageUse()));
        this.cooldowns.put(uuid, now + cooldownMillis);
        AdminItemsCooldowns.apply(player, item.getType(), cooldownMillis);
        event.setCancelled(true);
    }

    private boolean isTornado(ItemStack item) {
        if (item == null || item.getType() != Material.PHANTOM_MEMBRANE || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta.hasCustomModelData() && meta.getCustomModelData() == this.config.getTornado().getCustomModelData();
    }
}
