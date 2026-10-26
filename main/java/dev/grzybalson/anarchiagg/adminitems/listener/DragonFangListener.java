package dev.grzybalson.anarchiagg.adminitems.listener;

import dev.grzybalson.HollyCore;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsConfig;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsGUI;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsRegionUtil;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class DragonFangListener implements Listener {

    private final HollyCore plugin;
    private final AdminItemsConfig config;
    private final Map<UUID, Long> activeUntil = new ConcurrentHashMap<>();

    public DragonFangListener(HollyCore plugin) {
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
        if (!isDragonFang(item)) {
            return;
        }

        event.setCancelled(true);
        AdminItemsConfig.DragonFangSettings settings = config.getDragonFang();
        if (AdminItemsRegionUtil.isInBlockedRegion(player, plugin)) {
            AdminItemsRegionUtil.sendBlockedMessage(player, plugin);
            return;
        }

        long durationMs = Math.max(1, settings.getDurationSeconds()) * 1000L;
        activeUntil.put(player.getUniqueId(), System.currentTimeMillis() + durationMs);

        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6F, 1.4F);
        player.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(
            settings.getName() + " &8:: &aAktywowano na &f" + settings.getDurationSeconds() + "s&a!"
        ));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (!isActive(attacker.getUniqueId())) {
            return;
        }

        AdminItemsConfig.DragonFangSettings settings = config.getDragonFang();
        if (AdminItemsRegionUtil.isInBlockedRegion(attacker, plugin)
            || AdminItemsRegionUtil.isInBlockedRegion(victim, plugin)) {
            return;
        }

        if (ThreadLocalRandom.current().nextDouble(100.0D) >= settings.getLightningChance()) {
            return;
        }

        victim.getWorld().strikeLightningEffect(victim.getLocation());
        event.setDamage(Math.max(0.0D, settings.getLightningDamage()));
        attacker.playSound(attacker.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5F, 1.3F);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        activeUntil.remove(event.getPlayer().getUniqueId());
    }

    private boolean isActive(UUID uuid) {
        Long until = activeUntil.get(uuid);
        if (until == null) {
            return false;
        }
        if (until <= System.currentTimeMillis()) {
            activeUntil.remove(uuid);
            return false;
        }
        return true;
    }

    private boolean isDragonFang(ItemStack item) {
        if (item == null || item.getType() != Material.PRISMARINE_SHARD || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(AdminItemsGUI.KEY_SMOCZY_KIEL, PersistentDataType.BYTE);
    }
}
