package dev.grzybalson.anarchiagg.adminitems.listener;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import dev.grzybalson.HollyCore;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsConfig;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsCooldowns;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsRegionUtil;
import dev.grzybalson.anarchiagg.adminitems.listener.GoatListener;
import dev.grzybalson.anarchiagg.util.LocationUtil;
import dev.grzybalson.utils.ColorUtils;

public class EscapeChorusListener
implements Listener {
    private final HollyCore plugin;
    private final AdminItemsConfig config;
    private final GoatListener goatListener;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<UUID, Long>();
    private final Random random = new Random();
    private final List<UUID> bypassVanillaTeleport = new ArrayList<UUID>();

    public EscapeChorusListener(HollyCore plugin, GoatListener goatListener) {
        this.plugin = plugin;
        this.config = plugin.getAdminItemsConfig();
        this.goatListener = goatListener;
    }

    @EventHandler(priority=EventPriority.HIGH, ignoreCancelled=true)
    public void onChorusConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        if (!this.isEscapeChorus(item)) {
            return;
        }
        Player player = event.getPlayer();
        this.bypassVanillaTeleport.add(player.getUniqueId());
        ItemStack itemClone = item.clone();
        itemClone.setAmount(1);
        boolean success = this.executeChorusEscape(player);
        if (success) {
            Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> {
                if (player.isOnline()) {
                    player.getInventory().addItem(new ItemStack[]{itemClone});
                    player.updateInventory();
                }
            });
        } else {
            this.bypassVanillaTeleport.remove(player.getUniqueId());
            event.setCancelled(true);
            player.updateInventory();
        }
    }

    @EventHandler(priority=EventPriority.HIGH)
    public void onChorusTeleport(PlayerTeleportEvent event) {
        Player player;
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT && this.bypassVanillaTeleport.contains((player = event.getPlayer()).getUniqueId())) {
            event.setCancelled(true);
            this.bypassVanillaTeleport.remove(player.getUniqueId());
        }
    }

    private boolean executeChorusEscape(Player player) {
        AdminItemsConfig.EscapeChorusSettings settings = this.config.getEscapeChorus();
        if (settings == null || !settings.isEnabled()) {
            return false;
        }
        UUID uuid = player.getUniqueId();
        if (this.goatListener != null && this.goatListener.getActiveMatches().containsKey(uuid)) {
            player.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(settings.getMessageKozaBlocked()));
            return false;
        }
        if (AdminItemsRegionUtil.isInBlockedRegion(player, this.plugin)) {
            player.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(settings.getMessageSpawn()));
            return false;
        }
        long now = System.currentTimeMillis();
        long cooldownMillis = (long)settings.getCooldown() * 1000L;
        if (this.cooldowns.containsKey(uuid)) {
            long cooldownUntil = this.cooldowns.get(uuid);
            long timeLeft = cooldownUntil - now;
            if (timeLeft > 0L) {
                AdminItemsCooldowns.refresh(player, Material.CHORUS_FRUIT, cooldownUntil);
                player.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(settings.getName() + " &8:: &cMusisz poczeka\u0107 jeszcze &4" + timeLeft / 1000L + "s"));
                return false;
            }
            dev.grzybalson.anarchiagg.util.MapCompatibilityUtil.remove(this.cooldowns, uuid, cooldownUntil);
        }
        double radius = settings.getRadius();
        ArrayList<Player> nearbyPlayers = new ArrayList<Player>();
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            Player target;
            if (!(entity instanceof Player) || (target = (Player)entity).equals((Object)player) || this.goatListener != null && this.goatListener.getActiveMatches().containsKey(target.getUniqueId()) || !player.canSee(target) || AdminItemsRegionUtil.isInBlockedRegion(target, this.plugin)) continue;
            nearbyPlayers.add(target);
        }
        if (!nearbyPlayers.isEmpty()) {
            Player randomTarget = (Player)nearbyPlayers.get(this.random.nextInt(nearbyPlayers.size()));
            Location locBefore = player.getLocation();
            Location locAfter = LocationUtil.preservingPlayerRotation(player, randomTarget.getLocation());
            if (AdminItemsRegionUtil.isInBlockedRegion(locAfter, this.plugin)) {
                player.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(settings.getMessageSpawn()));
                return false;
            }
            player.teleport(locAfter);
            player.getWorld().playSound(locBefore, Sound.ITEM_CHORUS_FRUIT_TELEPORT, 1.0f, 0.8f);
            player.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(settings.getMessageSuccess()));
            this.cooldowns.put(uuid, now + cooldownMillis);
            AdminItemsCooldowns.apply(player, Material.CHORUS_FRUIT, cooldownMillis);
            return true;
        }
        player.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(settings.getMessageFail()));
        return false;
    }

    private boolean isEscapeChorus(ItemStack item) {
        if (item == null || item.getType() != Material.CHORUS_FRUIT || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta.hasCustomModelData() && meta.getCustomModelData() == this.config.getEscapeChorus().getCustomModelData();
    }
}
