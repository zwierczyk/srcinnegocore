package dev.grzybalson.anarchiagg.customitems.items;

import dev.grzybalson.anarchiagg.AnarchiaGGSettings;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Location;
import org.bukkit.potion.PotionEffectType;
import dev.grzybalson.anarchiagg.customitems.handlers.RegionHandler;
import dev.grzybalson.anarchiagg.customitems.util.EventItemRegistry;
import dev.grzybalson.anarchiagg.customitems.util.EventItemUseGuard;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CiepleMleko implements Listener {
    private final JavaPlugin plugin;
    private final String mlekoName;
    private final Material mlekoMaterial;
    private final RegionHandler regionHandler;
    private final Set<String> blockedRegions = new HashSet<>();
    private final int cooldownSeconds;

    public CiepleMleko(JavaPlugin plugin, RegionHandler regionHandler) {
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

        String wataCukrowaItem = eventowkiConfig.getString("eventowki.cieplemleko.item", "BOWL");
        mlekoName = eventowkiConfig.getString("eventowki.cieplemleko.name", "§9§lCiepłe mleko");
        mlekoMaterial = Material.valueOf(wataCukrowaItem.toUpperCase());
        cooldownSeconds = eventowkiConfig.getInt("eventowki.cieplemleko.cooldown", 5);
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
        if (!EventItemRegistry.matches(plugin, "cieplemleko", itemInHand)) return;
        if (!EventItemUseGuard.tryUse(plugin, player)) return;
        event.setCancelled(true);
        if (EventItemUseGuard.hasItemCooldown(plugin, player, "cieplemleko", itemInHand, cooldownSeconds)) {
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
        player.removePotionEffect(PotionEffectType.WEAKNESS);
        player.removePotionEffect(PotionEffectType.POISON);
        player.removePotionEffect(PotionEffectType.WITHER);
        player.removePotionEffect(PotionEffectType.SLOW);
        player.removePotionEffect(PotionEffectType.SLOW_DIGGING);
        player.removePotionEffect(PotionEffectType.HUNGER);
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.removePotionEffect(PotionEffectType.CONFUSION);
        player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_DRINK, 1f, 1f);
        EventItemUseGuard.applyItemCooldown(plugin, player, "cieplemleko", itemInHand, cooldownSeconds);
    }
}
