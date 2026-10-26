package dev.grzybalson.anarchiagg.customitems.items;

import dev.grzybalson.anarchiagg.AnarchiaGGSettings;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import dev.grzybalson.anarchiagg.customitems.handlers.RegionHandler;
import dev.grzybalson.anarchiagg.customitems.util.EventItemRegistry;
import dev.grzybalson.anarchiagg.customitems.util.EventItemUseGuard;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class Dynamit implements Listener {
    private final JavaPlugin plugin;
    private final RegionHandler regionHandler;
    private final int dynamitCooldown;
    private final String dynamitName;
    private final Material dynamitMaterial;
    private final Set<String> blockedRegions = new HashSet<>();

    public Dynamit(JavaPlugin plugin, RegionHandler regionHandler) {
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

        dynamitCooldown = eventowkiConfig.getInt("eventowki.dynamit.cooldown", 30);
        dynamitName = eventowkiConfig.getString("eventowki.dynamit.name", "§4§lDynamit").replace("&", "§");

        Material configuredMaterial = Material.getMaterial(eventowkiConfig.getString("eventowki.dynamit.material", "RED_CANDLE"));
        this.dynamitMaterial = (configuredMaterial != null) ? configuredMaterial : Material.NETHERITE_HOE;
    }

    @EventHandler
    public void onClick(PlayerInteractEvent event) {
        if (!event.getAction().isRightClick()) return;
        if (event.getClickedBlock() == null) return;

        Player player = event.getPlayer();
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();

        boolean isMainHand = false;
        ItemStack usedItem = null;

        if (mainHand.getType() != Material.AIR && EventItemRegistry.matches(plugin, "dynamit", mainHand)) {
            usedItem = mainHand;
            isMainHand = true;
        } else if (offHand.getType() != Material.AIR && EventItemRegistry.matches(plugin, "dynamit", offHand)) {
            usedItem = offHand;
            isMainHand = false;
        }

        if (usedItem == null) return;
        if (!EventItemUseGuard.tryUse(plugin, player)) return;
        if (EventItemUseGuard.hasItemCooldown(plugin, player, "dynamit", usedItem, dynamitCooldown)) return;

        event.setCancelled(true);
        if (isLocationBlocked(player.getLocation()) || isLocationBlocked(event.getClickedBlock().getLocation())) return;
        if (!Objects.requireNonNull(event.getClickedBlock()).getType().equals(Material.BEDROCK)) return;
        if (event.getClickedBlock().getLocation().getBlockY() > -64) {
            event.getClickedBlock().setType(Material.AIR);
        EventItemUseGuard.applyItemCooldown(plugin, player, "dynamit", usedItem, dynamitCooldown);
            player.playSound(player.getLocation(), Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 1f, 1f);
            if (usedItem.getAmount() > 1) {
                usedItem.setAmount(usedItem.getAmount() - 1);
            } else {
                if (isMainHand) {
                    mainHand.setAmount(0);
                } else {
                    offHand.setAmount(0);
                }
            }
        }
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
