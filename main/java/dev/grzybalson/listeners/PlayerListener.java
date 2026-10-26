package dev.grzybalson.listeners;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.grzybalson.HollyCore;
import dev.grzybalson.internal.HollyIntegrityGuard;
import dev.grzybalson.utils.PlayerHealthBase;
import org.bukkit.Bukkit;
import org.bukkit.EntityEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class PlayerListener implements Listener {

    private final HollyCore plugin;
    private final Set<UUID> pearlKeptInHand = ConcurrentHashMap.newKeySet();
    // Kto faktycznie wypuscil perle — bez tego skan swiata przy kazdej smierci lecial na darmo.
    private final Cache<UUID, Long> lastPearlThrow = Caffeine.newBuilder()
        .expireAfterWrite(60L, TimeUnit.SECONDS)
        .build();

    public PlayerListener(HollyCore plugin) {
        HollyIntegrityGuard.requireBound(plugin, "listener:player:init");
        this.plugin = plugin;
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            applyGamma(onlinePlayer);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        HollyIntegrityGuard.requireBound(plugin, "listener:player:join");
        Player player = event.getPlayer();
        applyGamma(player);
        java.util.UUID uuid = player.getUniqueId();
        String playerName = player.getName();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (plugin.getTopManager() != null) {
                plugin.getTopManager().rememberPlayerName(uuid, playerName);
                plugin.getTopManager().rememberPlayerKillStatistic(player);
            }
        }, 1L);
        // Synchroniczne operacje (szybkie, nie wymagają I/O)
        if (plugin.getVanishManager() != null) {
            plugin.getVanishManager().handlePlayerJoin(player);
        }
        if (plugin.getRestartManager() != null) {
            plugin.getRestartManager().handlePlayerJoin(player);
        }

        // BoostHandler i tak nałoży serca po async loadzie szafki — unikaj podwójnego refreshu na ticku joinu.
        if (plugin.getOgHubSzafkaManager() == null || plugin.getOgHubSzafkaManager().getBoostHandler() == null) {
            applyHeartsModule(player, false);
        }

        // Asynchroniczne operacje bazodanowe
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            if (plugin.getEconomy() != null) {
                plugin.getEconomy().loadPlayer(uuid);
                plugin.getEconomy().touchPlayer(uuid, playerName);
            }
            if (plugin.getIgnoreManager() != null) {
                plugin.getIgnoreManager().loadPlayer(uuid);
            }
            if (plugin.getKitManager() != null) {
                plugin.getKitManager().loadPlayerCooldowns(uuid);
            }
            if (plugin.getUserSettingsManager() != null) {
                plugin.getUserSettingsManager().loadPlayer(uuid);
            }
            if (plugin.getKillstreakManager() != null) {
                plugin.getKillstreakManager().loadPlayer(uuid);
            }

            // Powrót na główny wątek dla operacji wymagających API Bukkita
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                if (plugin.getPickupManager() != null) {
                    plugin.getPickupManager().checkAndShowBossBar(player);
                }
                if (plugin.getMarketManager() != null) {
                    plugin.getMarketManager().checkOfflinePayments(player);
                }
            });
        });
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Wyczyść cache ruchu gracza dla optymalizacji pamięci
        dev.grzybalson.utils.PlayerMovementCache.remove(player.getUniqueId());

        if (plugin.getEconomy() != null) {
            plugin.getEconomy().invalidateCache(player.getUniqueId());
        }
        if (plugin.getIgnoreManager() != null) {
            plugin.getIgnoreManager().unloadPlayer(player.getUniqueId());
        }
        if (plugin.getAfkManager() != null) {
            plugin.getAfkManager().handlePlayerQuit(player);
        }
        if (plugin.getVanishManager() != null) {
            plugin.getVanishManager().handlePlayerQuit(player);
        }

        if (plugin.getPickupManager() != null) {
            plugin.getPickupManager().hideBossBar(player);
        }

        plugin.getCooldownManager().clearCooldowns(player.getUniqueId());

        if (plugin.getKitManager() != null) {
            plugin.getKitManager().unloadPlayerCooldowns(player.getUniqueId());
        }
        if (plugin.getKillstreakManager() != null) {
            plugin.getKillstreakManager().unloadPlayer(player.getUniqueId());
        }
        if (plugin.getUserSettingsManager() != null) {
            plugin.getUserSettingsManager().unloadPlayer(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Player killer = player.getKiller();

        event.setDeathMessage(null);

        removeThrownPearls(player);

        if (killer != null && !killer.equals(player)) {
            if (plugin.getKillstreakManager() != null) {
                plugin.getKillstreakManager().addKillstreak(killer.getUniqueId(), 1);
            }
            if (!plugin.isAddonEnabled("killeffect") && plugin.getUserSettingsManager() != null && plugin.getUserSettingsManager().isKillTotemEffectEnabled(killer.getUniqueId())) {
                playKillTotemEffect(killer);
            }
        }
        if (plugin.getKillstreakManager() != null) {
            plugin.getKillstreakManager().resetKillstreak(player.getUniqueId());
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isDead()) {
                player.spigot().respawn();
            }
        }, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeathDrops(PlayerDeathEvent event) {
        if (event.getKeepInventory()) {
            return;
        }

        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim) || event.getDrops().isEmpty()) {
            return;
        }

        Location dropLocation = victim.getLocation();
        List<ItemStack> drops = new ArrayList<>(event.getDrops().size());
        for (ItemStack drop : event.getDrops()) {
            if (drop != null && !drop.getType().isAir()) {
                drops.add(drop.clone());
            }
        }
        event.getDrops().clear();

        for (ItemStack drop : drops) {
            Map<Integer, ItemStack> leftovers = killer.getInventory().addItem(drop);
            for (ItemStack leftover : leftovers.values()) {
                if (leftover != null && !leftover.getType().isAir()) {
                    victim.getWorld().dropItemNaturally(dropLocation, leftover);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        // Resetuj cooldowny eventówek przy respawnie
        dev.grzybalson.anarchiagg.customitems.util.EventItemUseGuard.clear(player);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            applyHeartsModule(player, true);
            applyGamma(player);
        }, 1L);
    }

    private void applyHeartsModule(Player player, boolean healToFull) {
        if (healToFull) {
            PlayerHealthBase.resetBaseAndHeal(plugin, player);
        } else {
            PlayerHealthBase.resetBaseMaxHealth(plugin, player, true);
        }
        // Kostium (oghub-szafka / szafka) — po resecie bazy nałóż bonus raz.
        if (plugin.getOgHubSzafkaManager() != null && plugin.getOgHubSzafkaManager().getBoostHandler() != null) {
            plugin.getOgHubSzafkaManager().getBoostHandler().refreshHearts(player);
        }
    }

    private void playKillTotemEffect(Player killer) {
        if (killer == null || !killer.isOnline()) {
            return;
        }
        killer.playEffect(EntityEffect.TOTEM_RESURRECT);
        killer.playSound(killer.getLocation(), Sound.ITEM_TOTEM_USE, 1.0F, 1.0F);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerItemConsume(PlayerItemConsumeEvent event) {
        if (event.getItem().getType() == Material.MILK_BUCKET) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                applyGamma(event.getPlayer());
            }, 1L);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        applyGamma(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPearlInteractNoPvp(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.ENDER_PEARL) {
            return;
        }
        Player player = event.getPlayer();
        if (player.getWorld().getPVP()) {
            return;
        }
        event.setCancelled(true);
        event.setUseItemInHand(Result.DENY);
        UUID uuid = player.getUniqueId();
        pearlKeptInHand.add(uuid);
        Bukkit.getScheduler().runTaskLater(plugin, () -> pearlKeptInHand.remove(uuid), 2L);
        player.updateInventory();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPearlLaunchNoPvp(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof EnderPearl pearl)) {
            return;
        }
        if (!(pearl.getShooter() instanceof Player player)) {
            return;
        }
        if (player.getWorld().getPVP()) {
            return;
        }
        event.setCancelled(true);
        pearl.remove();
        if (pearlKeptInHand.remove(player.getUniqueId())) {
            return;
        }
        refundPearl(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void trackPearlLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity() instanceof EnderPearl pearl && pearl.getShooter() instanceof Player shooter) {
            lastPearlThrow.put(shooter.getUniqueId(), System.currentTimeMillis());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPearlTeleportNoPvp(PlayerTeleportEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
            return;
        }
        if (event.getPlayer().getWorld().getPVP()) {
            return;
        }
        event.setCancelled(true);
    }

    // Perla rzucona tuz przed smiercia wciaz teleportuje gracza po respie — kasujemy jego perly w locie.
    private void removeThrownPearls(Player player) {
        UUID uuid = player.getUniqueId();
        Long last = lastPearlThrow.getIfPresent(uuid);
        if (last == null || System.currentTimeMillis() - last > 60_000L) {
            return;
        }
        for (EnderPearl pearl : player.getWorld().getEntitiesByClass(EnderPearl.class)) {
            if (pearl.getShooter() instanceof Player shooter && shooter.getUniqueId().equals(uuid)) {
                pearl.remove();
            }
        }
    }

    private void refundPearl(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        GameMode mode = player.getGameMode();
        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) {
            return;
        }
        ItemStack pearl = new ItemStack(Material.ENDER_PEARL, 1);
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(pearl);
        for (ItemStack drop : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), drop);
        }
        player.updateInventory();
    }

    private void applyGamma(Player player) {
        if (player != null && player.isOnline()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, PotionEffect.INFINITE_DURATION, 0, false, false, false));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityResurrect(EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        if (plugin.getAchievementManager() != null) {
            plugin.getAchievementManager().incrementTotemPops(victim);
        }

        Player attacker = victim.getKiller();
        if (attacker == null && victim.getLastDamageCause() instanceof EntityDamageByEntityEvent damageEvent) {
            if (damageEvent.getDamager() instanceof Player damager) {
                attacker = damager;
            } else if (damageEvent.getDamager() instanceof org.bukkit.entity.Projectile projectile && projectile.getShooter() instanceof Player damager) {
                attacker = damager;
            }
        }

        if (attacker != null && !attacker.equals(victim)) {
            if (plugin.getAchievementManager() != null) {
                plugin.getAchievementManager().incrementTotemPops(attacker);
            }
        }
    }
}
