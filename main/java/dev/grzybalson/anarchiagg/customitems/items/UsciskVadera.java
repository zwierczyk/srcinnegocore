package dev.grzybalson.anarchiagg.customitems.items;

import dev.grzybalson.anarchiagg.AnarchiaGGSettings;
import dev.grzybalson.anarchiagg.customitems.handlers.RegionHandler;
import dev.grzybalson.anarchiagg.customitems.util.EventItemRegistry;
import dev.grzybalson.anarchiagg.customitems.util.EventItemTools;
import dev.grzybalson.anarchiagg.customitems.util.EventItemUseGuard;
import dev.grzybalson.anarchiagg.customitems.util.EventowkiEffects;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class UsciskVadera implements Listener {

    public static final String ITEM_KEY = "usciskvadera";

    private final JavaPlugin plugin;
    private final RegionHandler regionHandler;
    private final Set<String> blockedRegions;
    private final int cooldownSeconds;
    private final int stunTicks;
    private final double liftBlocks;
    private final double damageMultiplier;
    private final Map<UUID, Long> stunnedUntil = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> actionBars = new ConcurrentHashMap<>();

    public UsciskVadera(JavaPlugin plugin, RegionHandler regionHandler) {
        this.plugin = plugin;
        this.regionHandler = regionHandler;
        this.blockedRegions = EventItemTools.loadBlockedRegions(plugin, ITEM_KEY);
        FileConfiguration config = AnarchiaGGSettings.getConfig(plugin);
        this.cooldownSeconds = config.getInt("eventowki." + ITEM_KEY + ".cooldown", 0);
        this.stunTicks = Math.max(20, config.getInt("eventowki." + ITEM_KEY + ".stun-ticks", 80));
        this.liftBlocks = config.getDouble("eventowki." + ITEM_KEY + ".lift-blocks", 2.0D);
        this.damageMultiplier = config.getDouble("eventowki." + ITEM_KEY + ".damage-multiplier", 0.70D);
    }

    public static ItemStack createDefaultItem(JavaPlugin plugin) {
        return EventItemTools.createFromConfig(plugin, ITEM_KEY, Material.BLACK_DYE, "&8&lUścisk Vadera", 1);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof Player victim)) {
            return;
        }
        ItemStack used = attacker.getInventory().getItemInMainHand();
        if (!EventItemRegistry.matches(plugin, ITEM_KEY, used)) {
            return;
        }
        if (!EventItemUseGuard.tryUse(plugin, attacker)) {
            return;
        }
        if (EventItemTools.isLocationBlocked(regionHandler, blockedRegions, attacker.getLocation())
            || EventItemTools.isLocationBlocked(regionHandler, blockedRegions, victim.getLocation())) {
            return;
        }
        if (EventItemUseGuard.hasItemCooldown(plugin, attacker, ITEM_KEY, used, cooldownSeconds)) {
            return;
        }
        EventItemUseGuard.applyItemCooldown(plugin, attacker, ITEM_KEY, used, cooldownSeconds);
        stun(victim);
        attacker.playSound(attacker.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 0.7f, 0.5f);
        victim.playSound(victim.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.8f, 0.6f);
    }

    private void stun(Player victim) {
        long until = System.currentTimeMillis() + stunTicks * 50L;
        stunnedUntil.put(victim.getUniqueId(), until);
        EventowkiEffects.startHoverLock(plugin, victim, liftBlocks);
        victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, stunTicks, 255, false, false, false));
        startActionBar(victim, until);
        Bukkit.getScheduler().runTaskLater(plugin, () -> clear(victim), stunTicks);
    }

    private void startActionBar(Player victim, long untilMillis) {
        BukkitTask previous = actionBars.remove(victim.getUniqueId());
        if (previous != null) {
            previous.cancel();
        }
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!victim.isOnline() || !isStunned(victim)) {
                BukkitTask current = actionBars.remove(victim.getUniqueId());
                if (current != null) {
                    current.cancel();
                }
                return;
            }
            long left = Math.max(0L, stunnedUntil.getOrDefault(victim.getUniqueId(), 0L) - System.currentTimeMillis());
            String seconds = String.format(java.util.Locale.US, "%.1f", left / 1000.0D);
            EventItemTools.sendCastleActionBar(
                victim,
                "uscisk-vadera",
                "&#B266FF⚠ &8Ξ &#B266FFUścisk Vadera &8〢 &#B266FFPozostało: &f" + seconds + "s"
            );
        }, 0L, 5L);
        actionBars.put(victim.getUniqueId(), task);
    }

    private boolean isStunned(Player player) {
        Long until = stunnedUntil.get(player.getUniqueId());
        return until != null && until > System.currentTimeMillis();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        Location from = event.getFrom();
        if (to == null || hasOnlyLookChange(from, to)) {
            return;
        }
        Player player = event.getPlayer();
        if (!isStunned(player) && !EventowkiEffects.isHoverLocked(player)) {
            return;
        }
        EventowkiEffects.clampHoverMove(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent event) {
        if (event.getEntity() instanceof Player player && event.isGliding() && isStunned(player)) {
            event.setCancelled(true);
            player.setGliding(false);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (!isStunned(event.getPlayer())) {
            return;
        }
        if (shouldBlockStunInteract(event)) {
            event.setCancelled(true);
        }
    }

    private static boolean hasOnlyLookChange(Location from, Location to) {
        return from.getBlockX() == to.getBlockX()
            && from.getBlockY() == to.getBlockY()
            && from.getBlockZ() == to.getBlockZ();
    }

    private static boolean shouldBlockStunInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            return true;
        }
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return false;
        }
        ItemStack item = event.getItem();
        return item == null || !item.getType().isEdible();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player attacker && isStunned(attacker)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && isStunned(player)) {
            event.setDamage(event.getDamage() * damageMultiplier);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (isStunned(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        clear(event.getEntity());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clear(event.getPlayer());
    }

    private void clear(Player player) {
        stunnedUntil.remove(player.getUniqueId());
        EventowkiEffects.stopHoverLock(player);
        BukkitTask task = actionBars.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }
}
