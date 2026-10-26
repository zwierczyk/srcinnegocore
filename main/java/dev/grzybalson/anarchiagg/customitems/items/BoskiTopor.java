package dev.grzybalson.anarchiagg.customitems.items;

import dev.grzybalson.anarchiagg.AnarchiaGGSettings;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
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
import java.util.UUID;

public class BoskiTopor implements Listener {
    private static final int INVULNERABILITY_SECONDS = 5;

    private final String boskiToporName;
    private final Material boskiToporMaterial;
    private final JavaPlugin plugin;
    private final RegionHandler regionHandler;
    private final Set<String> blockedRegions = new HashSet<>();
    private final int cooldownSeconds;
    private final Set<UUID> invulnerablePlayers = new HashSet<>();
    private final double knockbackMagnitude = 1.1;
    private final double knockbackHeight = 0.7;
    private final int knockbackRadius = 6;
    private int invulnerability;

    public BoskiTopor(JavaPlugin plugin, RegionHandler regionHandler) {
        this.regionHandler = regionHandler;
        this.plugin = plugin;
        FileConfiguration eventowkiConfig = AnarchiaGGSettings.getConfig(plugin);
        List<String> regionList = eventowkiConfig.getStringList("blocked-regions");
        if (regionList.isEmpty() && !eventowkiConfig.isSet("blocked-regions")) {
            eventowkiConfig.set("blocked-regions", List.of("spawn"));
            this.blockedRegions.add("spawn");
        } else {
            this.blockedRegions.addAll(regionList);
        }

        String boskiToporItem = eventowkiConfig.getString("eventowki.boskitopor.item", "IRON_AXE");
        boskiToporMaterial = Material.valueOf(boskiToporItem.toUpperCase());
        cooldownSeconds = eventowkiConfig.getInt("eventowki.boskitopor.cooldown", 15);
        boskiToporName = eventowkiConfig.getString("eventowki.boskitopor.name", "§6§lBoski Topór");
        invulnerability = INVULNERABILITY_SECONDS;
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
        if (!EventItemRegistry.matches(plugin, "boskitopor", itemInHand)) return;
        if (!EventItemUseGuard.tryUse(plugin, player)) return;

        event.setCancelled(true);

        if (EventItemUseGuard.hasItemCooldown(plugin, player, "boskitopor", itemInHand, cooldownSeconds)) {
            return;
        }

        if (isLocationBlocked(player.getLocation())) {
            return;
        }

        EventItemUseGuard.applyItemCooldown(plugin, player, "boskitopor", itemInHand, cooldownSeconds);
        dev.grzybalson.utils.ColorUtils.sendTitle(player, "", "§bAktywowałeś boski topór§7!", 0, 50, 10);

        invulnerablePlayers.add(player.getUniqueId());
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            invulnerablePlayers.remove(player.getUniqueId());
        }, invulnerability * 20L);
        Location effectCenter = player.getLocation();
        effectCenter.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, effectCenter, 40, 0.5, 0.5, 0.5, 0.05);

        for (Player target : player.getWorld().getPlayers()) {
            if (target.getLocation().distance(player.getLocation()) <= knockbackRadius) {
                if (target != player) {
                    if (!isLocationBlocked(target.getLocation())) {
                        Vector direction = target.getLocation().toVector().subtract(player.getLocation().toVector()).normalize();
                        Vector knockback = direction.multiply(knockbackMagnitude).setY(knockbackHeight);
                        target.setVelocity(knockback);
                        dev.grzybalson.utils.ColorUtils.sendTitle(target, "", "§b" + player.getName() + " §7aktywował §bboski topór§7!", 0, 50, 10);

                    }
                }
                target.playSound(target.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1f);
            }
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (invulnerablePlayers.contains(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }
}
