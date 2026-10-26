package dev.grzybalson.anarchiagg.adminitems.listener;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;
import dev.grzybalson.HollyCore;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsConfig;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsCooldowns;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsRegionUtil;
import dev.grzybalson.utils.ColorUtils;

public class HookListener
implements Listener {
    private final HollyCore plugin;
    private final AdminItemsConfig config;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<UUID, Long>();

    public HookListener(HollyCore plugin) {
        this.plugin = plugin;
        this.config = plugin.getAdminItemsConfig();
    }

    @EventHandler
    public void onPlayerFish(PlayerFishEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!this.isHak(item) && !this.isHak(item = player.getInventory().getItemInOffHand())) {
            return;
        }
        AdminItemsConfig.HookSettings settings = this.config.getHook();
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
        if (event.getState() == PlayerFishEvent.State.REEL_IN || event.getState() == PlayerFishEvent.State.IN_GROUND || event.getState() == PlayerFishEvent.State.CAUGHT_ENTITY) {
            if (this.cooldowns.containsKey(uuid)) {
                long cooldownUntil = this.cooldowns.get(uuid);
                long timeLeft = cooldownUntil - now;
                if (timeLeft > 0L) {
                    AdminItemsCooldowns.refresh(player, Material.FISHING_ROD, cooldownUntil);
                    player.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(settings.getName() + " &8:: &cMusisz poczeka\u0107 jeszcze &4" + timeLeft / 1000L + "s"));
                    event.setCancelled(true);
                    return;
                }
                dev.grzybalson.anarchiagg.util.MapCompatibilityUtil.remove(this.cooldowns, uuid, cooldownUntil);
            }
            FishHook hook = event.getHook();
            Location playerLoc = player.getLocation();
            Location hookLoc = hook.getLocation();
            if (AdminItemsRegionUtil.isInBlockedRegion(hookLoc, this.plugin)) {
                AdminItemsRegionUtil.sendBlockedMessage(player, this.plugin);
                event.setCancelled(true);
                return;
            }
            Vector direction = hookLoc.toVector().subtract(playerLoc.toVector());
            double distance = direction.length();
            if (distance > 0.0) {
                direction.normalize();
                double strength = settings.getPullStrength();
                direction.multiply(strength);
                direction.setY(direction.getY() * 0.6 + 0.4);
                player.setVelocity(direction);
                long cooldownMillis = (long)settings.getCooldown() * 1000L;
                this.cooldowns.put(uuid, now + cooldownMillis);
                AdminItemsCooldowns.apply(player, Material.FISHING_ROD, cooldownMillis);
            }
        }
    }

    private boolean isHak(ItemStack item) {
        if (item == null || item.getType() != Material.FISHING_ROD || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta.hasCustomModelData() && meta.getCustomModelData() == this.config.getHook().getCustomModelData();
    }
}
