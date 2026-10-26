package dev.grzybalson.anarchiagg.customitems.items;

import dev.grzybalson.anarchiagg.AnarchiaGGSettings;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import dev.grzybalson.anarchiagg.customitems.handlers.RegionHandler;
import dev.grzybalson.anarchiagg.customitems.util.EventItemRegistry;
import dev.grzybalson.anarchiagg.customitems.util.EventItemUseGuard;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CudownaLatarnia implements Listener {

    private final JavaPlugin plugin;
    private final RegionHandler regionHandler;
    private static final int DEFAULT_DURATION_SECONDS = 20;
    private final String beaconName;
    private final int beaconCooldown;
    private final int time;
    private final Material beaconMaterial;
    private static final Map<Location, LatarniaData> activeBeacons = new ConcurrentHashMap<>();
    private static final Map<UUID, Location> playerBeacons = new ConcurrentHashMap<>();
    private final Set<String> blockedRegions = new HashSet<>();

    public CudownaLatarnia(JavaPlugin plugin, RegionHandler regionHandler) {
        this.plugin = plugin;
        this.regionHandler = regionHandler;
        YamlConfiguration eventowkiConfig = AnarchiaGGSettings.getConfig(plugin);
        List<String> regionList = eventowkiConfig.getStringList("blocked-regions");
        if (regionList.isEmpty() && !eventowkiConfig.isSet("blocked-regions")) {
            eventowkiConfig.set("blocked-regions", List.of("spawn"));
            this.blockedRegions.add("spawn");
        } else {
            this.blockedRegions.addAll(regionList);
        }
        List<String> itemRegionList = eventowkiConfig.getStringList("eventowki.cudownalatarnia.blocked-regions");
        if (itemRegionList.isEmpty() && !eventowkiConfig.isSet("eventowki.cudownalatarnia.blocked-regions")) {
            eventowkiConfig.set("eventowki.cudownalatarnia.blocked-regions", List.of("spawn"));
            this.blockedRegions.add("spawn");
        } else {
            this.blockedRegions.addAll(itemRegionList);
        }
        AnarchiaGGSettings.save(plugin, eventowkiConfig);

        if (!eventowkiConfig.isSet("eventowki.cudownalatarnia.time")) {
            eventowkiConfig.set("eventowki.cudownalatarnia.time", DEFAULT_DURATION_SECONDS);
            AnarchiaGGSettings.save(plugin, eventowkiConfig);
        }

        this.beaconName = translate(eventowkiConfig.getString("eventowki.cudownalatarnia.name", "&d&lCudowna Latarnia"));
        this.beaconCooldown = Math.max(0, eventowkiConfig.getInt("eventowki.cudownalatarnia.cooldown", 60));
        this.time = Math.max(1, eventowkiConfig.getInt("eventowki.cudownalatarnia.time", DEFAULT_DURATION_SECONDS));

        Material configuredMaterial = Material.matchMaterial(eventowkiConfig.getString("eventowki.cudownalatarnia.material", "BEACON"));
        this.beaconMaterial = configuredMaterial != null ? configuredMaterial : Material.BEACON;
    }

    private record LatarniaData(UUID ownerUUID, BukkitTask task, long endTime, BossBar bossBar) {
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (item.getType() != beaconMaterial) return;
        if (!EventItemRegistry.matches(plugin, "cudownalatarnia", item)) return;

        Player player = event.getPlayer();
        if (!EventItemUseGuard.tryUse(plugin, player)) {
            event.setCancelled(true);
            return;
        }

        if (playerBeacons.containsKey(player.getUniqueId())) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            dev.grzybalson.utils.ColorUtils.sendTitle(player, "", "§cMasz już aktywną Cudowną Latarnię!");
            event.setCancelled(true);
            return;
        }

        if (EventItemUseGuard.hasItemCooldown(plugin, player, "cudownalatarnia", item, beaconCooldown)) {
            event.setCancelled(true);
            return;
        }

        if (isLocationBlocked(event.getBlockPlaced().getLocation())) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            dev.grzybalson.utils.ColorUtils.sendTitle(player, "", "§cNie możesz postawić latarni w bezpiecznej strefie!");
            event.setCancelled(true);
            return;
        }

        final int amount = item.getAmount();
        Bukkit.getScheduler().runTask(plugin, () -> {
            item.setAmount(amount);
            player.updateInventory();
        });

        Location loc = event.getBlockPlaced().getLocation();
        EventItemUseGuard.applyItemCooldown(plugin, player, "cudownalatarnia", item, beaconCooldown);
        player.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.0f);
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 20 * time, 5));
        BossBar bossBar = Bukkit.createBossBar(translate("&5Cudowna Latarnia &8(&d" + time + "s&8)"), BarColor.PURPLE, BarStyle.SOLID);
        bossBar.addPlayer(player);
        bossBar.setProgress(1.0);
        dev.grzybalson.utils.ColorUtils.sendTitle(player, "", "§5Cudowna Latarnia! §7Aktywowana na §d" + time + " sekund§7!");
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                LatarniaData data = activeBeacons.get(loc);
                if (data == null) {
                    this.cancel();
                    return;
                }

                long remainingMillis = data.endTime - System.currentTimeMillis();
                int remainingSecs = (int) (remainingMillis / 1000);

                if (remainingMillis <= 0) {
                    removeBeacon(loc);
                    this.cancel();
                    return;
                }

                data.bossBar.setTitle(translate("&5Cudowna Latarnia &8(&d" + remainingSecs + "s&8)"));
                double totalDurationMillis = time * 1000.0;
                double progress = Math.max(0.0, Math.min(1.0, (double) remainingMillis / totalDurationMillis));
                data.bossBar.setProgress(progress);

                Player owner = Bukkit.getPlayer(data.ownerUUID);
                if (owner == null || !owner.isOnline()) {
                    removeBeacon(loc);
                    this.cancel();
                    return;
                }

                if (owner.getWorld().equals(loc.getWorld()) && owner.getLocation().distance(loc) <= 30.0) {
                    applyEffects(owner);
                } else {
                    removeEffects(owner);
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);

        activeBeacons.put(loc, new LatarniaData(player.getUniqueId(), task, System.currentTimeMillis() + time * 1000L, bossBar));
        playerBeacons.put(player.getUniqueId(), loc);
    }

    private boolean isLocationBlocked(Location loc) {
        if (loc == null || loc.getWorld() == null) return true;
        for (String regionName : blockedRegions) {
            if (regionName != null && !regionName.isBlank() && regionHandler.isLocationInRegion(loc, regionName)) {
                return true;
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null || event.getClickedBlock().getType() != Material.BEACON) return;

        if (activeBeacons.containsKey(event.getClickedBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBreak(BlockBreakEvent event) {
        Location loc = event.getBlock().getLocation();
        LatarniaData data = activeBeacons.get(loc);

        if (data != null) {
            event.setCancelled(false);
            event.setDropItems(false);
            event.setExpToDrop(0);
            Player owner = Bukkit.getPlayer(data.ownerUUID);
            if (owner != null && owner.isOnline()) {
                owner.playSound(owner.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 1f);
                dev.grzybalson.utils.ColorUtils.sendTitle(owner, "", "§cTwoja latarnia została §4zniszczona§c!");
            }
            removeBeacon(loc);
        }
    }

    private void applyEffects(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 400, 4));
        player.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 200, 1));
    }

    private static void removeEffects(Player player) {
        if (player == null) return;
        player.removePotionEffect(PotionEffectType.REGENERATION);
        player.removePotionEffect(PotionEffectType.INCREASE_DAMAGE);
    }

    private void removeBeacon(Location loc) {
        LatarniaData data = activeBeacons.remove(loc);
        if (data != null) {
            data.task.cancel();
            data.bossBar.removeAll();
            playerBeacons.remove(data.ownerUUID);

            if (loc.getBlock().getType() == Material.BEACON) {
                loc.getBlock().setType(Material.AIR);
            }
            loc.getWorld().playSound(loc, Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 1.0f);

            Player owner = Bukkit.getPlayer(data.ownerUUID);
            if (owner != null) {
                removeEffects(owner);
                if (System.currentTimeMillis() >= data.endTime) {
                    dev.grzybalson.utils.ColorUtils.sendTitle(owner, "", "§cTwoja latarnia dobiegła §4końca§c!");
                    owner.playSound(owner.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1f, 1f);
                }
            }
        }
    }

    public static boolean isActiveBeacon(Location loc) {
        return loc != null && activeBeacons.containsKey(loc.getBlock().getLocation());
    }

    public static boolean clearActiveBeacon(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return false;
        }
        Location blockLocation = loc.getBlock().getLocation();
        LatarniaData data = activeBeacons.remove(blockLocation);
        if (data == null) {
            return false;
        }

        data.task.cancel();
        data.bossBar.removeAll();
        playerBeacons.remove(data.ownerUUID);
        if (blockLocation.getBlock().getType() == Material.BEACON) {
            blockLocation.getBlock().setType(Material.AIR);
        }

        Player owner = Bukkit.getPlayer(data.ownerUUID);
        if (owner != null) {
            removeEffects(owner);
        }
        return true;
    }

    public static void disable() {
        Iterator<Map.Entry<Location, LatarniaData>> it = activeBeacons.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Location, LatarniaData> entry = it.next();
            Location loc = entry.getKey();
            LatarniaData data = entry.getValue();

            data.task.cancel();
            data.bossBar.removeAll();
            if (loc.getBlock().getType() == Material.BEACON) {
                loc.getBlock().setType(Material.AIR);
            }

            Player owner = Bukkit.getPlayer(data.ownerUUID);
            if (owner != null) {
                removeEffects(owner);
            }
            it.remove();
        }
        playerBeacons.clear();
    }

    private String translate(String text) {
        return text.replace("&", "§");
    }
}
