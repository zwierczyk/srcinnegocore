package dev.grzybalson.anarchiagg.customitems.items;

import dev.grzybalson.anarchiagg.AnarchiaGGSettings;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.block.Action;
import dev.grzybalson.anarchiagg.customitems.handlers.RegionHandler;
import dev.grzybalson.anarchiagg.customitems.util.EventItemRegistry;
import dev.grzybalson.anarchiagg.customitems.util.EventItemUseGuard;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SmoczyMiecz implements Listener {
    private final JavaPlugin plugin;
    private final RegionHandler regionHandler;
    private final int smoczyMieczCooldown;
    private final String smoczyMieczName;
    private final Set<String> blockedRegions = new HashSet<>();

    public SmoczyMiecz(JavaPlugin plugin, RegionHandler regionHandler) {
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

        smoczyMieczCooldown = eventowkiConfig.getInt("eventowki.smoczymiecz.cooldown", 5);
        smoczyMieczName = eventowkiConfig.getString("eventowki.smoczymiecz.name", "&d&lSmoczy miecz").replace("&", "§");

    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (event.getPlayer().getWorld().getName().equals("event")) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (!EventItemRegistry.matches(plugin, "smoczymiecz", mainHand)) return;
        if (!EventItemUseGuard.tryUse(plugin, player)) return;
        if (EventItemUseGuard.hasItemCooldown(plugin, player, "smoczymiecz", mainHand, smoczyMieczCooldown)) return;
        if (isLocationBlocked(player.getLocation())) return;

        player.launchProjectile(org.bukkit.entity.EnderPearl.class);
        EventItemUseGuard.applyItemCooldown(plugin, player, "smoczymiecz", mainHand, smoczyMieczCooldown);
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
