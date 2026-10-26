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
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WinterHeartListener implements Listener {

    private final HollyCore plugin;
    private final AdminItemsConfig config;
    private final Map<UUID, Long> frozenUntil = new ConcurrentHashMap<>();

    public WinterHeartListener(HollyCore plugin) {
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
        if (!isWinterHeart(item)) {
            return;
        }

        event.setCancelled(true);
        AdminItemsConfig.WinterHeartSettings settings = config.getWinterHeart();
        if (AdminItemsRegionUtil.isInBlockedRegion(player, plugin)) {
            AdminItemsRegionUtil.sendBlockedMessage(player, plugin);
            return;
        }

        long durationMs = Math.max(1, settings.getDurationSeconds()) * 1000L;
        frozenUntil.put(player.getUniqueId(), System.currentTimeMillis() + durationMs);
        player.setVelocity(player.getVelocity().multiply(0));
        player.setFreezeTicks(Math.max(1, settings.getDurationSeconds()) * 20);

        player.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.0F, 0.5F);
        player.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(
            settings.getName() + " &8:: &bJestes zamrozony na &f" + settings.getDurationSeconds() + "s&b!"
        ));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!isFrozen(event.getPlayer().getUniqueId())) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
            Location locked = from.clone();
            locked.setYaw(to.getYaw());
            locked.setPitch(to.getPitch());
            event.setTo(locked);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (isFrozen(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (isFrozen(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        frozenUntil.remove(event.getPlayer().getUniqueId());
    }

    private boolean isFrozen(UUID uuid) {
        Long until = frozenUntil.get(uuid);
        if (until == null) {
            return false;
        }
        if (until <= System.currentTimeMillis()) {
            frozenUntil.remove(uuid);
            return false;
        }
        return true;
    }

    private boolean isWinterHeart(ItemStack item) {
        if (item == null || item.getType() != Material.BLUE_ICE || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(AdminItemsGUI.KEY_SERCE_ZIMY, PersistentDataType.BYTE);
    }
}
