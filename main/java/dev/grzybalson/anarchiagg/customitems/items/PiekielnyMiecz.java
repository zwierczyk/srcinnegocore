package dev.grzybalson.anarchiagg.customitems.items;

import dev.grzybalson.anarchiagg.AnarchiaGGSettings;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import dev.grzybalson.anarchiagg.customitems.handlers.RegionHandler;
import dev.grzybalson.anarchiagg.customitems.util.EventItemRegistry;
import dev.grzybalson.anarchiagg.customitems.util.EventItemUseGuard;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class PiekielnyMiecz implements Listener {

    private final JavaPlugin plugin;
    private final RegionHandler regionHandler;
    private final String mieczName;
    private final Material mieczMaterial;
    private final int cooldownSeconds;
    private static final Map<UUID, BukkitRunnable> activeHellFire = new HashMap<>();
    private final Set<String> blockedRegions = new HashSet<>();

    public PiekielnyMiecz(JavaPlugin plugin, RegionHandler regionHandler) {
        this.plugin = plugin;
        this.regionHandler = regionHandler;

        FileConfiguration config = AnarchiaGGSettings.getConfig(plugin);
        List<String> regionList = config.getStringList("blocked-regions");
        if (regionList.isEmpty()) {
            this.blockedRegions.add("spawn");
        } else {
            this.blockedRegions.addAll(regionList);
        }

        this.mieczName = config.getString("eventowki.piekielnymiecz.name", "§c§lPiekielny Miecz").replace("&", "§");
        String mieczItem = config.getString("eventowki.piekielnymiecz.material", "NETHERITE_SWORD");
        mieczMaterial = Material.valueOf(mieczItem.toUpperCase());
        cooldownSeconds = config.getInt("eventowki.piekielnymiecz.cooldown", 0);
    }

    @EventHandler
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof Player victim)) return;
        if (event.isCancelled()) return;

        ItemStack item = attacker.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR) return;
        if (!EventItemRegistry.matches(plugin, "piekielnymiecz", item)) return;
        if (!EventItemUseGuard.tryUse(plugin, attacker)) return;
        if (EventItemUseGuard.hasItemCooldown(plugin, attacker, "piekielnymiecz", item, cooldownSeconds)) return;

        startHellFire(victim);
        EventItemUseGuard.applyItemCooldown(plugin, attacker, "piekielnymiecz", item, cooldownSeconds);
    }

    private void startHellFire(Player victim) {
        UUID victimUUID = victim.getUniqueId();

        if (activeHellFire.containsKey(victimUUID)) {
            activeHellFire.get(victimUUID).cancel();
            activeHellFire.remove(victimUUID);
        }

        BukkitRunnable task = new BukkitRunnable() {
            int hitsCount = 0;
            final int maxHits = 6;

            @Override
            public void run() {
                if (!victim.isOnline() || victim.isDead()) {
                    activeHellFire.remove(victimUUID);
                    this.cancel();
                    return;
                }

                if (hitsCount >= maxHits) {
                    activeHellFire.remove(victimUUID);
                    this.cancel();
                    return;
                }
                if (victim.hasPotionEffect(PotionEffectType.FIRE_RESISTANCE) || victim.getLocation().getBlock().getType().equals(Material.WATER)) {
                    victim.damage(1.0);
                    victim.setNoDamageTicks(0);
                    victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_PLAYER_HURT_ON_FIRE, 1.0f, 1.0f);
                }
                hitsCount++;
            }
        };

        activeHellFire.put(victimUUID, task);
        task.runTaskTimer(plugin, 20L, 20L);
    }

    public static void disable() {
        for (BukkitRunnable task : activeHellFire.values()) {
            if (task != null) {
                task.cancel();
            }
        }
        activeHellFire.clear();
    }
}
