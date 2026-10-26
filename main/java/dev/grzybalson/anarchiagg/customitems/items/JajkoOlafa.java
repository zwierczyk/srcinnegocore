package dev.grzybalson.anarchiagg.customitems.items;

import dev.grzybalson.anarchiagg.AnarchiaGGSettings;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Egg;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowman;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import dev.grzybalson.anarchiagg.customitems.handlers.RegionHandler;
import dev.grzybalson.anarchiagg.customitems.util.EventItemRegistry;
import dev.grzybalson.anarchiagg.customitems.util.EventItemUseGuard;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class JajkoOlafa implements Listener {
    private final JavaPlugin plugin;
    private final RegionHandler regionHandler;
    private final int jajkoCooldown;
    private final String jajkoName;
    private final Material jajkoMaterial = Material.EGG;
    private final NamespacedKey olafKey;
    private final Set<String> blockedRegions = new HashSet<>();
    private static final Map<Snowman, Player> olafOwners = new ConcurrentHashMap<>();
    private static final Map<Snowman, Integer> olafHealth = new ConcurrentHashMap<>();
    private static final Map<Snowman, BukkitTask> olafTasks = new ConcurrentHashMap<>();

    public JajkoOlafa(JavaPlugin plugin, RegionHandler regionHandler) {
        this.olafKey = new NamespacedKey(plugin, "jajko_olafa_tag");
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

        jajkoCooldown = eventowkiConfig.getInt("eventowki.jajkoolafa.cooldown", 10);
        jajkoName = translate(eventowkiConfig.getString("eventowki.jajkoolafa.name", "&bJajko Olafa"));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (item == null || item.getType() == Material.AIR) return;
        if (!EventItemRegistry.matches(plugin, "jajkoolafa", item)) return;
        Player player = event.getPlayer();
        if (!EventItemUseGuard.tryUse(plugin, player)) return;
        if (isLocationBlocked(event.getPlayer().getLocation())) return;

        event.setCancelled(true);

        if (EventItemUseGuard.hasItemCooldown(plugin, player, "jajkoolafa", item, jajkoCooldown)) return;

        EventItemUseGuard.applyItemCooldown(plugin, player, "jajkoolafa", item, jajkoCooldown);
        player.playSound(player.getLocation(), Sound.ENTITY_EGG_THROW, 1.0f, 1.0f);
        dev.grzybalson.utils.ColorUtils.sendTitle(player, " ", translate("&7Rzuciłeś jajko &bolafa&7!"), 10, 20, 10);

        Egg egg = player.launchProjectile(Egg.class);
        egg.getPersistentDataContainer().set(olafKey, PersistentDataType.BYTE, (byte) 1);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Egg egg)) return;
        if (!egg.getPersistentDataContainer().has(olafKey, PersistentDataType.BYTE)) return;
        egg.remove();

        if (!(event.getHitEntity() instanceof Player victim)) return;
        if (!(egg.getShooter() instanceof Player attacker)) return;

        if (isLocationBlocked(attacker.getLocation()) || isLocationBlocked(victim.getLocation())) return;

        spawnOlaf(victim);
    }

    @EventHandler
    public void onChickenSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.EGG) {
            event.setCancelled(true);
        }
    }
    @EventHandler
    public void onSnowTrail(EntityBlockFormEvent event) {
        if (event.getEntity() instanceof Snowman olaf) {
            if (olafOwners.containsKey(olaf)) {
                event.setCancelled(true);
            }
        }
    }

    private void spawnOlaf(Player victim) {
        new ArrayList<>(olafOwners.keySet()).forEach(olaf -> {
            if (olafOwners.get(olaf).equals(victim)) {
                removeOlaf(olaf);
            }
        });

        Location spawnLoc = victim.getLocation().add(0, 0.5, 0);
        Snowman olaf = (Snowman) victim.getWorld().spawnEntity(spawnLoc, EntityType.SNOWMAN);

        olaf.setCustomName(translate("&b⛄ Olaf &8(&f3&8/&f3&8)"));
        olaf.setCustomNameVisible(true);
        olaf.setAI(false);

        olafOwners.put(olaf, victim);
        olafHealth.put(olaf, 3);

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!victim.isOnline() || olaf.isDead()) {
                    removeOlaf(olaf);
                    this.cancel();
                    return;
                }
                olaf.teleport(victim.getLocation().add(0, 0.5, 0));
            }
        }.runTaskTimer(plugin, 1L, 1L);

        olafTasks.put(olaf, task);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onOlafHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Snowman olaf)) return;
        if (!olafOwners.containsKey(olaf)) return;

        event.setDamage(0);

        if (!(event.getDamager() instanceof Player attacker)) {
            event.setCancelled(true);
            return;
        }

        Player owner = olafOwners.get(olaf);
        if (!attacker.equals(owner)) {
            event.setCancelled(true);
            return;
        }

        attacker.playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 2.0f, 1.0f);
        int currentHp = olafHealth.get(olaf) - 1;

        if (currentHp <= 0) {
            dev.grzybalson.utils.ColorUtils.sendTitle(attacker, " ", translate("&bOlaf &7został zniszczony!"), 5, 20, 5);
            removeOlaf(olaf);
        } else {
            olafHealth.put(olaf, currentHp);
            olaf.setCustomName(translate("&b⛄ Olaf &8(&f" + currentHp + "&8/&f3&8)"));
            dev.grzybalson.utils.ColorUtils.sendTitle(attacker, " ", translate("&3Olaf! &7Pozostało uderzeń: &b" + currentHp), 5, 20, 5);
        }
    }

    @EventHandler
    public void onOlafNaturalDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Snowman olaf && olafOwners.containsKey(olaf)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        new ArrayList<>(olafOwners.keySet()).forEach(olaf -> {
            if (olafOwners.get(olaf).equals(player)) {
                removeOlaf(olaf);
            }
        });
    }

    private void removeOlaf(Snowman olaf) {
        if (olafTasks.containsKey(olaf)) {
            olafTasks.get(olaf).cancel();
            olafTasks.remove(olaf);
        }
        olafOwners.remove(olaf);
        olafHealth.remove(olaf);
        olaf.remove();
    }

    public static void disable() {
        for (Snowman olaf : olafOwners.keySet()) {
            if (olafTasks.containsKey(olaf)) {
                olafTasks.get(olaf).cancel();
            }
            olaf.remove();
        }
        olafTasks.clear();
        olafOwners.clear();
        olafHealth.clear();
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

    private String translate(String text) {
        return text.replace("&", "§");
    }
}
