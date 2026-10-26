package dev.grzybalson.anarchiagg.customitems.items;

import dev.grzybalson.anarchiagg.AnarchiaGGSettings;

import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.attribute.AttributeModifier.Operation;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import dev.grzybalson.anarchiagg.customitems.handlers.RegionHandler;
import dev.grzybalson.anarchiagg.customitems.util.EventItemRegistry;
import dev.grzybalson.anarchiagg.customitems.util.EventItemUseGuard;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class BlokWidmo implements Listener {
    private final String itemName;
    private final Material itemMaterial;
    private final JavaPlugin plugin;
    private final RegionHandler regionHandler;
    private final Set<String> blockedRegions = new HashSet<>();
    private final int cooldownSeconds;
    private static int timeSeconds = 0;

    private static final double HEALTH_DECREMENT = -20.0;
    private static final double RADIUS = 5.0;
    private static final UUID HEALTH_MODIFIER_ID = UUID.fromString("b376d47b-118e-4a6f-b1e8-7828004f295b");

    private static final Map<UUID, BukkitRunnable> ACTIVE_DEBUFF_TASKS = new HashMap<>();
    private static final Map<UUID, BossBar> ACTIVE_BOSS_BARS = new HashMap<>();

    public BlokWidmo(JavaPlugin plugin, RegionHandler regionHandler) {
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

        itemMaterial = Material.getMaterial(eventowkiConfig.getString("eventowki.blokwidmo.material", "ENDER_EYE"));
        itemName = eventowkiConfig.getString("eventowki.blokwidmo.name", "§5§lBlok Widmo");
        cooldownSeconds = eventowkiConfig.getInt("eventowki.blokwidmo.cooldown", 30);
        timeSeconds = eventowkiConfig.getInt("eventowki.blokwidmo.time", 30);
    }

    public static void onDisable() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            removeMaxHealthDebuff(player);
        }
        for (BukkitRunnable task : ACTIVE_DEBUFF_TASKS.values()) {
            task.cancel();
        }
        ACTIVE_DEBUFF_TASKS.clear();

        for (BossBar bar : ACTIVE_BOSS_BARS.values()) {
            bar.removeAll();
        }
        ACTIVE_BOSS_BARS.clear();
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

    private static String formatTime(int totalSeconds) {
        if (totalSeconds <= 0) return "0s";
        long minutes = TimeUnit.SECONDS.toMinutes(totalSeconds);
        long seconds = totalSeconds - TimeUnit.MINUTES.toSeconds(minutes);

        if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
        }
    }

    private static void updateBossBar(Player player, int remainingTicks) {
        BossBar bossBar = ACTIVE_BOSS_BARS.get(player.getUniqueId());
        if (bossBar == null) return;

        int remainingSeconds = remainingTicks / 20;
        double progress = (double) remainingTicks / timeSeconds * 20;
        String timeString = formatTime(remainingSeconds);

        bossBar.setTitle("§cBlok widmo: §fPosiadasz obniżony limit serc przez " + timeString);
        bossBar.setProgress(Math.max(0.0, Math.min(1.0, progress)));

        if (remainingTicks <= 0) {
            removeBossBar(player);
        }
    }

    private static void removeBossBar(Player player) {
        BossBar bossBar = ACTIVE_BOSS_BARS.remove(player.getUniqueId());
        if (bossBar != null) {
            bossBar.removePlayer(player);
        }
    }

    private static void applyMaxHealthDebuff(Player player, JavaPlugin plugin) {
        AttributeInstance maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealth == null) return;

        boolean hasDebuff = maxHealth.getModifiers().stream().anyMatch(m -> m.getUniqueId().equals(HEALTH_MODIFIER_ID));

        if (ACTIVE_DEBUFF_TASKS.containsKey(player.getUniqueId())) {
            ACTIVE_DEBUFF_TASKS.get(player.getUniqueId()).cancel();
            ACTIVE_DEBUFF_TASKS.remove(player.getUniqueId());
            removeBossBar(player);
        }

        if (!hasDebuff) {
            AttributeModifier modifier = new AttributeModifier(HEALTH_MODIFIER_ID, "BlokWidmoHealthDebuff", HEALTH_DECREMENT, Operation.ADD_NUMBER);
            maxHealth.addModifier(modifier);
        }

        if (player.getHealth() > maxHealth.getValue()) {
            player.setHealth(Math.max(1.0, maxHealth.getValue()));
        }

        BossBar bossBar = Bukkit.createBossBar("", BarColor.PURPLE, BarStyle.SOLID);
        bossBar.addPlayer(player);
        ACTIVE_BOSS_BARS.put(player.getUniqueId(), bossBar);

        BukkitRunnable debuffTask = new BukkitRunnable() {
            int ticksLeft = timeSeconds * 20;
            @Override
            public void run() {
                if (ticksLeft <= 0) {
                    removeMaxHealthDebuff(player);
                    this.cancel();
                    return;
                }
                updateBossBar(player, ticksLeft);
                ticksLeft -= 20;
            }
        };

        debuffTask.runTaskTimer(plugin, 0L, 20L);
        ACTIVE_DEBUFF_TASKS.put(player.getUniqueId(), debuffTask);
    }

    private static void removeMaxHealthDebuff(Player player) {
        AttributeInstance maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealth == null) return;

        maxHealth.getModifiers().stream()
                .filter(m -> m.getUniqueId().equals(HEALTH_MODIFIER_ID))
                .findFirst()
                .ifPresent(maxHealth::removeModifier);

        if (ACTIVE_DEBUFF_TASKS.containsKey(player.getUniqueId())) {
            ACTIVE_DEBUFF_TASKS.get(player.getUniqueId()).cancel();
            ACTIVE_DEBUFF_TASKS.remove(player.getUniqueId());
        }

        removeBossBar(player);
    }

    private boolean hasBlokWidmoDebuff(Player player) {
        AttributeInstance maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        return maxHealth != null && maxHealth.getModifiers().stream().anyMatch(m -> m.getUniqueId().equals(HEALTH_MODIFIER_ID));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        removeMaxHealthDebuff(event.getPlayer());
    }

    @EventHandler
    public void onRedDyeInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();

        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        boolean holdingRedDye = mainHand.getType() == Material.RED_DYE || offHand.getType() == Material.RED_DYE;

        if (holdingRedDye && hasBlokWidmoDebuff(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (itemInHand.getType() == Material.AIR) return;

        boolean isBlokWidmo = EventItemRegistry.matches(plugin, "blokwidmo", itemInHand);

        if (!isBlokWidmo) return;
        if (!EventItemUseGuard.tryUse(plugin, player)) return;

        event.setCancelled(true);

        if (EventItemUseGuard.hasItemCooldown(plugin, player, "blokwidmo", itemInHand, cooldownSeconds)) {
            return;
        }

        Location effectCenter;
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            effectCenter = event.getClickedBlock().getLocation().add(0.5, 1.0, 0.5);
        } else {
            effectCenter = player.getLocation();
        }

        if (isLocationBlocked(effectCenter)) {
            return;
        }

        EventItemUseGuard.applyItemCooldown(plugin, player, "blokwidmo", itemInHand, cooldownSeconds);

        if (effectCenter.getWorld() != null) {
            effectCenter.getWorld().playSound(effectCenter, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 0.6f);

            effectCenter.getWorld().spawnParticle(Particle.SMOKE_LARGE, effectCenter, 50, 0.5, 0.5, 0.5, 0.05);
            effectCenter.getWorld().spawnParticle(Particle.FLAME, effectCenter, 40, 0.5, 0.5, 0.5, 0.05);

            effectCenter.getWorld().spawnParticle(Particle.LAVA, effectCenter, 20, 0.5, 0.5, 0.5, 0.05);
        }

        for (Player target : player.getWorld().getPlayers()) {
            if (target.getLocation().distance(effectCenter) <= RADIUS) {
                if (target.equals(player)) {
                    continue;
                }
                applyMaxHealthDebuff(target, plugin);
                dev.grzybalson.utils.ColorUtils.sendTitle(target, "§cBlok widmo", "§7Coś dzieje się z twoim zdrowiem...", 0, 50, 10);
            }
        }
        dev.grzybalson.utils.ColorUtils.sendTitle(player, "", "§cPostawiono blok widmo", 0, 50, 10);
    }
}
