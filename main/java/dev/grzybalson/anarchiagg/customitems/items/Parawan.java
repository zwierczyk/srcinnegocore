package dev.grzybalson.anarchiagg.customitems.items;

import dev.grzybalson.anarchiagg.AnarchiaGGSettings;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import dev.grzybalson.anarchiagg.customitems.handlers.RegionHandler;
import dev.grzybalson.anarchiagg.customitems.util.EventItemRegistry;
import dev.grzybalson.anarchiagg.customitems.util.EventItemUseGuard;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Parawan implements Listener {
    private final JavaPlugin plugin;
    private final String parawanName;
    private final Material parawanMaterial;
    private final RegionHandler regionHandler;
    private final Set<String> blockedRegions = new HashSet<>();
    private final int cooldownSeconds;

    public Parawan(JavaPlugin plugin, RegionHandler regionHandler) {
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

        String parawanItem = eventowkiConfig.getString("eventowki.parawan.item", "FEATHER");
        parawanName = eventowkiConfig.getString("eventowki.parawan.name", "§e§lParawan");
        parawanMaterial = Material.valueOf(parawanItem.toUpperCase());
        cooldownSeconds = eventowkiConfig.getInt("eventowki.parawan.cooldown", 5);
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

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (itemInHand.getType() == Material.AIR) return;
        if (!EventItemRegistry.matches(plugin, "parawan", itemInHand)) return;
        if (!EventItemUseGuard.tryUse(plugin, player)) return;

        event.setCancelled(true);

        if (EventItemUseGuard.hasItemCooldown(plugin, player, "parawan", itemInHand, cooldownSeconds)) {
            return;
        }

        if (isLocationBlocked(player.getLocation())) {
            return;
        }

        double radius = 6.0;
        List<Entity> nearbyEntities = player.getNearbyEntities(radius, radius, radius);
        Location userLocation = player.getLocation();

        for (Entity entity : nearbyEntities) {
            if (entity instanceof Player target) {

                if (isLocationBlocked(target.getLocation())) {
                    continue;
                }

                Location targetLocation = target.getLocation();
                Vector direction = targetLocation.toVector().subtract(userLocation.toVector());

                if (direction.lengthSquared() > 0) {
                    direction.setY(0).normalize();
                } else {
                    direction = userLocation.getDirection().setY(0).normalize();
                }

                direction.multiply(1.1);
                direction.setY(0.7);

                target.setVelocity(direction);
            }
        }

        if (itemInHand.getAmount() > 1) {
            itemInHand.setAmount(itemInHand.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }

        EventItemUseGuard.applyItemCooldown(plugin, player, "parawan", itemInHand, cooldownSeconds);

        dev.grzybalson.utils.ColorUtils.sendTitle(player, "", "§7Odrzuciłeś graczy §fparawanem", 0, 50, 10);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1f);
    }
}
