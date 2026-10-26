package dev.grzybalson.anarchiagg.customitems.items;

import dev.grzybalson.anarchiagg.AnarchiaGGSettings;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import dev.grzybalson.anarchiagg.customitems.handlers.RegionHandler;
import dev.grzybalson.anarchiagg.customitems.util.EventItemRegistry;
import dev.grzybalson.anarchiagg.customitems.util.EventItemUseGuard;
import java.util.*;

public class WedkaNielota implements Listener {
    private final JavaPlugin plugin;
    private final RegionHandler regionHandler;
    private final int nielotaCooldown;
    private final String nielotaName;
    private final Set<String> blockedRegions = new HashSet<>();
    private final Map<UUID, Long> elytraBlockEnd = new HashMap<>();
    private final Map<UUID, BossBar> playerBars = new HashMap<>();
    private final Map<UUID, BukkitTask> playerTimerTasks = new HashMap<>();
    private final Set<UUID> deadPlayers = new HashSet<>();
    private final Map<UUID, UUID> victimToAttacker = new HashMap<>();
    private final long DURATION_MS = 15_000;

    public WedkaNielota(JavaPlugin plugin, RegionHandler regionHandler) {
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
        nielotaCooldown = eventowkiConfig.getInt("eventowki.nielota.cooldown", 5);
        nielotaName = eventowkiConfig.getString("eventowki.nielota.name", "§5§lWędka nielota").replace("&", "§");
    }

    private boolean isNielotaInHand(Player player) {
        if (player == null || !player.isOnline()) return false;

        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();

        boolean isMainHandNielota = EventItemRegistry.matches(plugin, "nielota", mainHand);
        boolean isOffHandNielota = EventItemRegistry.matches(plugin, "nielota", offHand);

        return isMainHandNielota || isOffHandNielota;
    }

    @EventHandler
    public void onFishHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof FishHook hook)) return;
        if (!(event.getHitEntity() instanceof Player victim)) return;

        Entity shooter = hook.getShooter() instanceof Entity ? (Entity) hook.getShooter() : null;
        if (!(shooter instanceof Player attacker)) return;

        if (victim == attacker) return;

        ItemStack mainHand = attacker.getInventory().getItemInMainHand();
        ItemStack offHand = attacker.getInventory().getItemInOffHand();
        boolean isMainHandNielota = EventItemRegistry.matches(plugin, "nielota", mainHand);
        boolean isOffHandNielota = EventItemRegistry.matches(plugin, "nielota", offHand);
        if (!isMainHandNielota && !isOffHandNielota) return;

        if (isLocationBlocked(shooter.getLocation())) {
            event.setCancelled(true);
            return;
        }
        if (isLocationBlocked(victim.getLocation())) {
            event.setCancelled(true);
            return;
        }
        ItemStack usedItem = isMainHandNielota ? mainHand : offHand;
        if (EventItemUseGuard.hasItemCooldown(plugin, attacker, "nielota", usedItem, nielotaCooldown)) {
            return;
        }

        EventItemUseGuard.applyItemCooldown(plugin, attacker, "nielota", usedItem, nielotaCooldown);
        dev.grzybalson.utils.ColorUtils.sendTitle(victim, "§c§lZłapany!", "§7Złapany przez: §e" + attacker.getName(), 0, 50, 10);
        dev.grzybalson.utils.ColorUtils.sendTitle(attacker, "§a§lZłapałeś gracza!", "§7Złapany gracz: §e" + victim.getName(), 0, 50, 10);

        UUID victimId = victim.getUniqueId();
        UUID attackerId = attacker.getUniqueId();
        long end = System.currentTimeMillis() + DURATION_MS;

        if (elytraBlockEnd.containsKey(victimId)) {
            endNielotaEffect(victim, attacker);
            BukkitTask oldTask = playerTimerTasks.remove(victimId);
            if (oldTask != null) {
                oldTask.cancel();
            }
        }

        elytraBlockEnd.put(victimId, end);
        victimToAttacker.put(victimId, attackerId);
        victim.setGliding(false);

        BossBar bar = Bukkit.createBossBar("§cPosiadasz klątwę! Nie możesz latać przez §e", BarColor.RED, BarStyle.SOLID);
        bar.setProgress(1.0);
        bar.addPlayer(victim);
        playerBars.put(victimId, bar);
        BukkitTask mainTask = new BukkitRunnable() {
            @Override
            public void run() {
                long remaining = elytraBlockEnd.getOrDefault(victimId, 0L) - System.currentTimeMillis();

                if (remaining <= 0) {
                    endNielotaEffect(victim, attacker);
                    cancel();
                    return;
                }

                double progress = remaining / (double)DURATION_MS;
                bar.setProgress(Math.max(0, progress));
                bar.setTitle("§cPosiadasz klątwę! Nie możesz latać przez §e" + (remaining / 1000) + "s");
            }
        }.runTaskTimer(plugin, 0L, 20L);

        playerTimerTasks.put(victimId, mainTask);
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!elytraBlockEnd.containsKey(victimId)) {
                    cancel();
                    return;
                }

                Player currentAttacker = Bukkit.getPlayer(attackerId);
                if (!isNielotaInHand(currentAttacker)) {
                    endNielotaEffect(victim, attacker);

                    BukkitTask task = playerTimerTasks.remove(victimId);
                    if (task != null) {
                        task.cancel();
                    }
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 3L);
    }

    private void endNielotaEffect(Player victim, Player originalAttacker) {
        UUID victimId = victim.getUniqueId();
        UUID attackerId = victimToAttacker.remove(victimId);

        BossBar bar = playerBars.remove(victimId);
        if (bar != null) {
            bar.removeAll();
        }

        elytraBlockEnd.remove(victimId);

        Player currentAttacker = (originalAttacker != null && originalAttacker.isOnline()) ? originalAttacker : (attackerId != null ? Bukkit.getPlayer(attackerId) : null);

        if (!deadPlayers.contains(victimId)) {
            if (victim.isOnline()) {
                dev.grzybalson.utils.ColorUtils.sendTitle(victim, "§c§lPuścił Cię!", "§7Puścił cię gracz!", 0, 50, 10);
            }
            if (currentAttacker != null && currentAttacker.isOnline()) {
                dev.grzybalson.utils.ColorUtils.sendTitle(currentAttacker, "§c§lUwolniony!", "§7Klątwa została zdjęta", 0, 50, 10);
            }
        }
        deadPlayers.remove(victimId);
    }

    @EventHandler
    public void onElytraToggle(EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        UUID id = player.getUniqueId();
        if (!elytraBlockEnd.containsKey(id)) return;

        long end = elytraBlockEnd.get(id);
        if (System.currentTimeMillis() < end) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID id = player.getUniqueId();

        elytraBlockEnd.remove(id);
        victimToAttacker.remove(id);

        BossBar bar = playerBars.remove(id);
        if (bar != null) {
            bar.removeAll();
        }

        BukkitTask task = playerTimerTasks.remove(id);
        if (task != null) {
            task.cancel();
        }
        for (Entity entity : player.getWorld().getEntities()) {
            if (entity instanceof FishHook hook) {
                if (hook.getHookedEntity() != null && hook.getHookedEntity().getUniqueId().equals(id)) {
                    hook.remove();
                }
            }
        }

        deadPlayers.add(id);
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
