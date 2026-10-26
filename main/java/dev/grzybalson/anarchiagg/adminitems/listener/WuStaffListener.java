package dev.grzybalson.anarchiagg.adminitems.listener;

import dev.grzybalson.HollyCore;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsConfig;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsCooldowns;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsGUI;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsRegionUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class WuStaffListener implements Listener {

    private final HollyCore plugin;
    private final AdminItemsConfig config;
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public WuStaffListener(HollyCore plugin) {
        this.plugin = plugin;
        this.config = plugin.getAdminItemsConfig();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }
        if (!(event.getEntity() instanceof Player target)) {
            return;
        }

        ItemStack item = attacker.getInventory().getItemInMainHand();
        if (!isWuStaff(item)) {
            return;
        }

        AdminItemsConfig.WuStaffSettings settings = config.getWuStaff();
        if (AdminItemsRegionUtil.isInBlockedRegion(attacker, plugin)
            || AdminItemsRegionUtil.isInBlockedRegion(target, plugin)) {
            event.setCancelled(true);
            AdminItemsRegionUtil.sendBlockedMessage(attacker, plugin);
            return;
        }

        UUID uuid = attacker.getUniqueId();
        long now = System.currentTimeMillis();
        long cooldownMillis = Math.max(1, settings.getCooldown()) * 1000L;
        Long until = cooldowns.get(uuid);
        if (until != null && until > now) {
            event.setCancelled(true);
            AdminItemsCooldowns.refresh(attacker, item.getType(), until);
            attacker.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(
                settings.getName() + " &8:: &cMusisz poczekac jeszcze &4" + ((until - now) / 1000) + "s&c!"
            ));
            return;
        }

        cooldowns.put(uuid, now + cooldownMillis);
        AdminItemsCooldowns.apply(attacker, item.getType(), cooldownMillis);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (isWuStaff(item) && event.getAction().toString().contains("RIGHT_CLICK")) {
            event.setCancelled(true);
        }
    }

    public void resetCooldown(UUID uuid) {
        cooldowns.remove(uuid);
    }

    private boolean isWuStaff(ItemStack item) {
        if (item == null || item.getType() != Material.BLAZE_ROD || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(AdminItemsGUI.KEY_LASKA_MISTRZA_WU, PersistentDataType.BYTE);
    }
}
