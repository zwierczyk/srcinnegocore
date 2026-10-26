package dev.grzybalson.anarchiagg.customitems.items;

import dev.grzybalson.anarchiagg.AnarchiaGGSettings;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Location;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import dev.grzybalson.anarchiagg.customitems.handlers.RegionHandler;
import dev.grzybalson.anarchiagg.customitems.util.EventItemRegistry;
import dev.grzybalson.anarchiagg.customitems.util.EventItemUseGuard;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Piernik implements Listener {
    private final JavaPlugin plugin;
    private final String piernikName;
    private final Material piernikMaterial;
    private final RegionHandler regionHandler;
    private final Set<String> blockedRegions = new HashSet<>();
    private final int cooldownSeconds;
    private final int time;

    public Piernik(JavaPlugin plugin, RegionHandler regionHandler) {
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

        String wataCukrowaItem = eventowkiConfig.getString("eventowki.piernik.item", "COOKIE");
        piernikName = eventowkiConfig.getString("eventowki.piernik.name", "§6§lPiernik");
        piernikMaterial = Material.valueOf(wataCukrowaItem.toUpperCase());
        cooldownSeconds = eventowkiConfig.getInt("eventowki.piernik.cooldown", 5);
        time = eventowkiConfig.getInt("eventowki.piernik.time", 5);
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
        if (!EventItemRegistry.matches(plugin, "piernik", itemInHand)) return;
        if (!EventItemUseGuard.tryUse(plugin, player)) return;
        event.setCancelled(true);
        if (EventItemUseGuard.hasItemCooldown(plugin, player, "piernik", itemInHand, cooldownSeconds)) {
            return;
        }

        if (isLocationBlocked(player.getLocation())) {
            return;
        }
        if (itemInHand.getAmount() > 1) {
            itemInHand.setAmount(itemInHand.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.FAST_DIGGING, 20 * time, 9));
        EventItemUseGuard.applyItemCooldown(plugin, player, "piernik", itemInHand, cooldownSeconds);
    }
}
