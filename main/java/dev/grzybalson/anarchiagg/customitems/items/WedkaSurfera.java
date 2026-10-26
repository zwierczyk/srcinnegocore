package dev.grzybalson.anarchiagg.customitems.items;

import dev.grzybalson.anarchiagg.AnarchiaGGSettings;
import dev.grzybalson.anarchiagg.customitems.handlers.RegionHandler;
import dev.grzybalson.anarchiagg.customitems.util.EventItemRegistry;
import dev.grzybalson.anarchiagg.customitems.util.EventItemUseGuard;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class WedkaSurfera implements Listener {
    private final RegionHandler regionHandler;
    private final String wedkaSurferaName;
    private final int wedkaSurferaCooldown;
    private final double wedkaSurferaMultiply;
    private final double earlyPullMinDistance;
    private final Set<String> blockedRegions = new HashSet<>();
    public final Set<UUID> wedkaSurferaUsgae = new HashSet<>();
    private final JavaPlugin plugin;

    public WedkaSurfera(JavaPlugin plugin, RegionHandler regionHandler) {
        this.plugin = plugin;
        this.regionHandler = regionHandler;
        FileConfiguration eventowkiConfig = AnarchiaGGSettings.getConfig(plugin);

        List<String> regionList = eventowkiConfig.getStringList("blocked-regions");
        if (regionList.isEmpty() && !eventowkiConfig.isSet("blocked-regions")) {
            eventowkiConfig.set("blocked-regions", List.of("spawn"));
            this.blockedRegions.add("spawn");
        } else {
            this.blockedRegions.addAll(regionList);
        }

        this.wedkaSurferaCooldown = eventowkiConfig.getInt("eventowki.wedkasurfera.cooldown", 5);
        this.wedkaSurferaMultiply = eventowkiConfig.getDouble("eventowki.wedkasurfera.multiply", 2.5);
        this.earlyPullMinDistance = eventowkiConfig.getDouble("eventowki.wedkasurfera.early-pull-min-distance", 2.0);
        this.wedkaSurferaName = eventowkiConfig.getString("eventowki.wedkasurfera.name", "&b&lWedka surferka").replace("&", "§");
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || item.getType() == Material.AIR) return;
        if (!EventItemRegistry.matches(plugin, "wedkasurfera", item)) return;
        if (player.getFishHook() == null) return;

        pullToHook(player, item, player.getFishHook());
    }

    @EventHandler
    public void onFish(PlayerFishEvent event) {
        Player player = event.getPlayer();
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();

        ItemStack item = null;
        if (EventItemRegistry.matches(plugin, "wedkasurfera", mainHand)) {
            item = mainHand;
        } else if (EventItemRegistry.matches(plugin, "wedkasurfera", offHand)) {
            item = offHand;
        }

        if (item == null) return;

        if (event.getState() == PlayerFishEvent.State.REEL_IN
         || event.getState() == PlayerFishEvent.State.IN_GROUND
         || event.getState() == PlayerFishEvent.State.CAUGHT_ENTITY
         || event.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
            pullToHook(player, item, event.getHook());
        }
    }

    private void pullToHook(Player player, ItemStack item, FishHook hook) {
        if (hook == null || hook.isDead()) return;
        if (!EventItemUseGuard.tryUse(plugin, player)) return;

        if (EventItemUseGuard.hasItemCooldown(plugin, player, "wedkasurfera", item, wedkaSurferaCooldown)) {
            return;
        }

        if (isLocationBlocked(player.getLocation())) {
            return;
        }

        Location hookLoc = hook.getLocation();
        Vector difference = hookLoc.toVector().subtract(player.getLocation().toVector());
        if (difference.lengthSquared() < earlyPullMinDistance * earlyPullMinDistance) {
            return;
        }

        Vector direction = difference.normalize();
        double yVel = Math.max(0.5, direction.getY() * wedkaSurferaMultiply);
        Vector vel = direction.multiply(wedkaSurferaMultiply);
        vel.setY(yVel);
        player.setVelocity(vel);
        player.playSound(player.getLocation(), Sound.ENTITY_EGG_THROW, 1.0f, 1.0f);

        wedkaSurferaUsgae.add(player.getUniqueId());
        Bukkit.getScheduler().runTaskLater(plugin, () -> wedkaSurferaUsgae.remove(player.getUniqueId()), 40L);

        hook.remove();
        EventItemUseGuard.applyItemCooldown(plugin, player, "wedkasurfera", item, wedkaSurferaCooldown);
        dev.grzybalson.utils.ColorUtils.sendTitle(player, "", "§7Odleciałeś §bwędką surfera§7!", 0, 50, 10);
    }

    private boolean isLocationBlocked(Location loc) {
        if (loc == null || loc.getWorld() == null) return true;

        for (String regionName : blockedRegions) {
            if (regionHandler.isLocationInRegion(loc, regionName)) {
                return true;
            }
        }
        return false;
    }
}
