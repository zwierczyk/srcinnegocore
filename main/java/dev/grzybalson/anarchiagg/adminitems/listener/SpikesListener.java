package dev.grzybalson.anarchiagg.adminitems.listener;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import dev.grzybalson.HollyCore;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsConfig;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsCooldowns;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsGUI;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsRegionUtil;

import dev.grzybalson.utils.ColorUtils;

public class SpikesListener
implements Listener {
    private final HollyCore plugin;
    private final Map<UUID, Long> shieldCooldowns = new HashMap<UUID, Long>();
    private final Map<UUID, Long> cooldowns = new HashMap<UUID, Long>();
    private final NamespacedKey key;

    public SpikesListener(HollyCore plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey((Plugin)plugin, "admin_item_kolce");
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
        AdminItemsConfig.SpikesSettings kolceSettings = this.plugin.getAdminItemsConfig().getSpikes();
        if (!kolceSettings.isEnabled() || !this.isTarcza(item)) {
            return;
        }
        event.setCancelled(true);
        event.setUseItemInHand(Event.Result.DENY);
        event.setUseInteractedBlock(Event.Result.DENY);
        Player player = event.getPlayer();
        String prefix = kolceSettings.getName() + " &8:: ";
        if (AdminItemsRegionUtil.isInBlockedRegion(player, this.plugin)) {
            player.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(prefix + "&cNie mo\u017cesz u\u017cy\u0107 na spawnie!"));
            return;
        }
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long cooldownUntil = this.cooldowns.get(uuid);
        if (cooldownUntil != null && now < cooldownUntil) {
            AdminItemsCooldowns.refresh(player, item.getType(), cooldownUntil);
            long seconds = (cooldownUntil - now) / 1000L;
            player.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(prefix + "&cKolce dost\u0119pne za &4" + seconds + "s"));
            return;
        }
        this.cooldowns.remove(uuid);
        long durationMs = (long)kolceSettings.getDuration() * 1000L;
        long cooldownMs = (long)kolceSettings.getCooldown() * 1000L;
        long shieldUntil = now + durationMs;
        this.shieldCooldowns.put(uuid, shieldUntil);
        this.cooldowns.put(uuid, now + cooldownMs);
        this.plugin.getServer().getScheduler().runTaskLater((Plugin)this.plugin, () -> {
            Long currentShieldUntil = this.shieldCooldowns.get(uuid);
            if (currentShieldUntil != null && currentShieldUntil == shieldUntil) {
                this.shieldCooldowns.remove(uuid);
            }
        }, Math.max(1L, (long)kolceSettings.getDuration() * 20L));
        AdminItemsCooldowns.apply(player, item.getType(), cooldownMs);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.0f, 1.5f);
        player.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(prefix + "&aAktywowano kolce na &f" + kolceSettings.getDuration() + "sekund!"));
    }

    @EventHandler(priority=EventPriority.HIGH, ignoreCancelled=true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Player)) {
            return;
        }
        Player victim = (Player)entity;
        UUID victimUuid = victim.getUniqueId();
        Long shieldUntil = this.shieldCooldowns.get(victimUuid);
        if (shieldUntil == null) {
            return;
        }
        long now = System.currentTimeMillis();
        AdminItemsConfig.SpikesSettings kolceSettings = this.plugin.getAdminItemsConfig().getSpikes();
        String prefix = kolceSettings.getName() + " &8:: ";
        if (now <= shieldUntil) {
            // PvpCancelTracker.mark(this.getAttackingPlayer(event), victim, "kolce");
            event.setCancelled(true);
            victim.getWorld().playSound(victim.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.0f, 0.8f);
        } else {
            this.shieldCooldowns.remove(victimUuid);
            victim.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(prefix + "&cOchrona kolc\u00f3w wygas\u0142a!"));
        }
    }

    private Player getAttackingPlayer(EntityDamageByEntityEvent event) {
        Projectile projectile;
        Entity entity = event.getDamager();
        if (entity instanceof Player) {
            Player player = (Player)entity;
            return player;
        }
        Entity entity2 = event.getDamager();
        if (entity2 instanceof Projectile && (projectile = (Projectile)entity2).getShooter() instanceof Player) {
            Player player = (Player)projectile.getShooter();
            return player;
        }
        return null;
    }

    private boolean isTarcza(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        if (AdminItemsGUI.KEY_KOLCE != null
                && meta.getPersistentDataContainer().has(AdminItemsGUI.KEY_KOLCE, PersistentDataType.BYTE)) {
            return true;
        }
        if (meta.getPersistentDataContainer().has(this.key, PersistentDataType.BYTE)) {
            return true;
        }
        AdminItemsConfig.SpikesSettings settings = this.plugin.getAdminItemsConfig().getSpikes();
        return item.getType() == Material.WEEPING_VINES
                && meta.hasCustomModelData()
                && meta.getCustomModelData() == settings.getCustomModelData();
    }
}
