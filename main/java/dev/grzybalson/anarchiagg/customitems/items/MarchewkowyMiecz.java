package dev.grzybalson.anarchiagg.customitems.items;

import dev.grzybalson.anarchiagg.AnarchiaGGSettings;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import dev.grzybalson.anarchiagg.customitems.handlers.RegionHandler;
import dev.grzybalson.anarchiagg.customitems.util.EventItemRegistry;
import dev.grzybalson.anarchiagg.customitems.util.EventItemUseGuard;
import java.util.*;

public class MarchewkowyMiecz implements Listener {
    private final RegionHandler regionHandler;
    private final int marchewkowymieczCooldown;
    private final int time;
    private final String marchewkowymieczName;
    private final Material marchewkowymieczMaterial;
    private final Set<String> blockedRegions = new HashSet<>();
    private final Set<UUID> moveblock = new HashSet<>();
    private final JavaPlugin plugin;

    public MarchewkowyMiecz(JavaPlugin plugin, RegionHandler regionHandler) {
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
        marchewkowymieczCooldown = eventowkiConfig.getInt("eventowki.marchewkowymiecz.cooldown", 10);
        time = eventowkiConfig.getInt("eventowki.marchewkowymiecz.time", 10);
        marchewkowymieczName = eventowkiConfig.getString("eventowki.marchewkowymiecz.name", "§6§lMarchewkowy miecz").replace("&", "§");

        Material configuredMaterial = Material.getMaterial(eventowkiConfig.getString("eventowki.marchewkowymiecz.material", "GOLDEN_SWORD"));
        this.marchewkowymieczMaterial = (configuredMaterial != null) ? configuredMaterial : Material.GOLDEN_SWORD;
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (event.isCancelled()) return;

        if (isLocationBlocked(attacker.getLocation()) || isLocationBlocked(victim.getLocation())) {
            return;
        }

        ItemStack mainHandItem = attacker.getInventory().getItemInMainHand();
        if (EventItemUseGuard.hasItemCooldown(plugin, attacker, "marchewkowymiecz", mainHandItem, marchewkowymieczCooldown)) {
            return;
        }

        if (!EventItemRegistry.matches(plugin, "marchewkowymiecz", mainHandItem)) {
            return;
        }
        if (!EventItemUseGuard.tryUse(plugin, attacker)) {
            return;
        }

        EventItemUseGuard.applyItemCooldown(plugin, attacker, "marchewkowymiecz", mainHandItem, marchewkowymieczCooldown);
        moveblock.add(victim.getUniqueId());
        dev.grzybalson.utils.ColorUtils.sendTitle(attacker, "", "§bZamroziłeś §3" + victim.getName() + "§b!", 0, 50, 10);
        dev.grzybalson.utils.ColorUtils.sendTitle(victim, "", "§bGracz §3" + attacker.getName() + " §bzamroził cię!", 0, 50, 10);
        new BukkitRunnable() {
            @Override
            public void run() {
                moveblock.remove(victim.getUniqueId());
            }
        }.runTaskLater(plugin, 20L * time);
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
    public void onMove(PlayerMoveEvent event) {
        if (moveblock.isEmpty() || !moveblock.contains(event.getPlayer().getUniqueId())) {
            return;
        }
        if (event.getTo() == null) {
            event.setCancelled(true);
            return;
        }
        if (event.getFrom().getX() != event.getTo().getX()
                || event.getFrom().getY() != event.getTo().getY()
                || event.getFrom().getZ() != event.getTo().getZ()) {
            event.setCancelled(true);
        }
    }
}
