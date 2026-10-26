package dev.grzybalson.modules.mslfkostiumy;

import dev.grzybalson.HollyCore;
import dev.grzybalson.anarchiagg.szafa.kostiumy.KostiumyHandler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Custom efekty kostiumow MSLF (stivka / plazowicz / kostka / diabelek).
 */
public final class MslfKostiumyEffectsListener implements Listener {

    private static final long SNEAK_WINDOW_MS = 3000L;
    private static final long SNEAK_COOLDOWN_MS = 60_000L;
    private static final int SNEAKS_REQUIRED = 3;
    private static final double SLOW_FALL_CHANCE = 0.05D;
    private static final int SLOW_FALL_TICKS = 40;
    private static final long SLOW_FALL_COOLDOWN_MS = 10_000L;

    private static final Set<Material> FARM_CROPS = EnumSet.of(
        Material.WHEAT,
        Material.CARROTS,
        Material.POTATOES,
        Material.BEETROOTS,
        Material.NETHER_WART,
        Material.MELON,
        Material.PUMPKIN,
        Material.SUGAR_CANE,
        Material.COCOA,
        Material.SWEET_BERRY_BUSH,
        Material.CACTUS
    );

    private static final Map<Material, Material> ORE_MAIN_DROP = new EnumMap<>(Material.class);

    static {
        putOre(Material.COAL_ORE, Material.COAL);
        putOre(Material.DEEPSLATE_COAL_ORE, Material.COAL);
        putOre(Material.IRON_ORE, Material.RAW_IRON);
        putOre(Material.DEEPSLATE_IRON_ORE, Material.RAW_IRON);
        putOre(Material.GOLD_ORE, Material.RAW_GOLD);
        putOre(Material.DEEPSLATE_GOLD_ORE, Material.RAW_GOLD);
        putOre(Material.DIAMOND_ORE, Material.DIAMOND);
        putOre(Material.DEEPSLATE_DIAMOND_ORE, Material.DIAMOND);
        putOre(Material.EMERALD_ORE, Material.EMERALD);
        putOre(Material.DEEPSLATE_EMERALD_ORE, Material.EMERALD);
        putOre(Material.LAPIS_ORE, Material.LAPIS_LAZULI);
        putOre(Material.DEEPSLATE_LAPIS_ORE, Material.LAPIS_LAZULI);
        putOre(Material.REDSTONE_ORE, Material.REDSTONE);
        putOre(Material.DEEPSLATE_REDSTONE_ORE, Material.REDSTONE);
        putOre(Material.COPPER_ORE, Material.RAW_COPPER);
        putOre(Material.DEEPSLATE_COPPER_ORE, Material.RAW_COPPER);
        putOre(Material.NETHER_QUARTZ_ORE, Material.QUARTZ);
        putOre(Material.ANCIENT_DEBRIS, Material.ANCIENT_DEBRIS);
    }

    private static void putOre(Material ore, Material drop) {
        ORE_MAIN_DROP.put(ore, drop);
    }

    private final HollyCore plugin;
    private final KostiumyHandler kostiumyHandler;

    private final Map<UUID, Integer> sneakCounter = new HashMap<>();
    private final Map<UUID, Long> sneakWindowUntil = new HashMap<>();
    private final Map<UUID, Long> sneakCooldownUntil = new HashMap<>();
    private final Map<UUID, Long> slowFallCooldownUntil = new HashMap<>();
    private final Map<UUID, Long> cobwebProtectUntil = new HashMap<>();
    private final Map<UUID, BukkitTask> cobwebClearTasks = new HashMap<>();
    private final Map<UUID, BukkitTask> scaleRestoreTasks = new HashMap<>();
    private final Map<UUID, Double> previousScale = new HashMap<>();
    private final Map<UUID, BossBar> sneakBossBars = new HashMap<>();
    private final Map<UUID, BukkitTask> sneakBossBarTasks = new HashMap<>();

    private final Attribute scaleAttribute;
    private final PotionEffectType confusionEffect;
    private final PotionEffectType slowFallingEffect;

    public MslfKostiumyEffectsListener(HollyCore plugin, KostiumyHandler kostiumyHandler) {
        this.plugin = plugin;
        this.kostiumyHandler = kostiumyHandler;
        this.scaleAttribute = resolveScaleAttribute();
        this.confusionEffect = resolvePotion("CONFUSION", "NAUSEA");
        this.slowFallingEffect = resolvePotion("SLOW_FALLING");
    }

    private String activeKostium(Player player) {
        if (player == null) {
            return null;
        }
        return kostiumyHandler.getDatabase().getActiveKostium(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!"stivka".equalsIgnoreCase(activeKostium(player))) {
            return;
        }
        Material type = event.getBlock().getType();
        Location dropLoc = event.getBlock().getLocation().add(0.5, 0.5, 0.5);

        if (FARM_CROPS.contains(type)) {
            if (ThreadLocalRandom.current().nextDouble() < 0.10) {
                ItemStack tool = player.getInventory().getItemInMainHand();
                for (ItemStack drop : event.getBlock().getDrops(tool, player)) {
                    if (drop == null || drop.getType().isAir() || drop.getAmount() <= 0) {
                        continue;
                    }
                    event.getBlock().getWorld().dropItemNaturally(dropLoc, drop.clone());
                }
            }
            return;
        }

        Material oreDrop = ORE_MAIN_DROP.get(type);
        if (oreDrop != null) {
            event.getBlock().getWorld().dropItemNaturally(dropLoc, new ItemStack(oreDrop, 1));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWaterDamage(EntityDamageByEntityEvent event) {
        Player damager = resolvePlayerDamager(event.getDamager());
        if (damager == null || !"plazowicz".equalsIgnoreCase(activeKostium(damager))) {
            return;
        }
        if (!isInLiquid(damager)) {
            return;
        }
        event.setDamage(event.getDamage() * 1.30);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplosionDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!"kostka".equalsIgnoreCase(activeKostium(player))) {
            return;
        }
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
            || cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
            event.setDamage(event.getDamage() * 0.85);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onKostkaHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (!"kostka".equalsIgnoreCase(activeKostium(victim))) {
            return;
        }
        Player attacker = resolvePlayerDamager(event.getDamager());
        if (attacker == null || attacker.equals(victim)) {
            return;
        }
        if (slowFallingEffect == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (slowFallCooldownUntil.getOrDefault(attacker.getUniqueId(), 0L) > now) {
            return;
        }
        if (ThreadLocalRandom.current().nextDouble() < SLOW_FALL_CHANCE) {
            slowFallCooldownUntil.put(attacker.getUniqueId(), now + SLOW_FALL_COOLDOWN_MS);
            attacker.addPotionEffect(new PotionEffect(slowFallingEffect, SLOW_FALL_TICKS, 0, false, true, true));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCobwebPlace(BlockPlaceEvent event) {
        if (event.getBlock().getType() != Material.COBWEB) {
            return;
        }
        Player placer = event.getPlayer();
        Location placeLoc = event.getBlock().getLocation();
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Long> entry : cobwebProtectUntil.entrySet()) {
            if (entry.getValue() < now) {
                continue;
            }
            Player owner = Bukkit.getPlayer(entry.getKey());
            if (owner == null || !owner.isOnline() || owner.equals(placer)) {
                continue;
            }
            if (owner.getWorld() != placeLoc.getWorld()) {
                continue;
            }
            if (owner.getLocation().distanceSquared(placeLoc) <= 64.0) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onToggleSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) {
            return;
        }
        Player player = event.getPlayer();
        String kostium = activeKostium(player);
        if (kostium == null) {
            return;
        }
        String key = kostium.toLowerCase();
        Ability ability = Ability.fromKey(key);
        if (ability == null) {
            return;
        }

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        if (sneakCooldownUntil.getOrDefault(uuid, 0L) > now) {
            return;
        }
        if (cobwebClearTasks.containsKey(uuid) || scaleRestoreTasks.containsKey(uuid)) {
            return;
        }

        long windowUntil = sneakWindowUntil.getOrDefault(uuid, 0L);
        int count = sneakCounter.getOrDefault(uuid, 0);
        if (now > windowUntil) {
            count = 0;
            clearBossBar(player);
        }
        count++;
        sneakCounter.put(uuid, count);
        sneakWindowUntil.put(uuid, now + SNEAK_WINDOW_MS);

        if (count == 1) {
            startSneakCountdown(player, ability);
            return;
        }
        if (count < SNEAKS_REQUIRED) {
            updateSneakCountdown(player, ability, count);
            return;
        }

        sneakCounter.remove(uuid);
        sneakWindowUntil.remove(uuid);
        sneakCooldownUntil.put(uuid, now + SNEAK_COOLDOWN_MS);
        clearBossBar(player);

        switch (ability) {
            case PLAZOWICZ -> activatePlazowiczShrink(player);
            case KOSTKA -> activateKostkaNausea(player);
            case DIABELEK -> activateDiabelekCobweb(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        sneakCounter.remove(uuid);
        sneakWindowUntil.remove(uuid);
        sneakCooldownUntil.remove(uuid);
        slowFallCooldownUntil.remove(uuid);
        cobwebProtectUntil.remove(uuid);
        cancelTask(cobwebClearTasks.remove(uuid));
        cancelTask(scaleRestoreTasks.remove(uuid));
        clearBossBar(event.getPlayer());
        restoreScale(event.getPlayer());
    }

    private void activatePlazowiczShrink(Player player) {
        if (scaleAttribute == null) {
            startCooldownBossBar(player, Ability.PLAZOWICZ);
            return;
        }
        AttributeInstance instance = player.getAttribute(scaleAttribute);
        if (instance == null) {
            startCooldownBossBar(player, Ability.PLAZOWICZ);
            return;
        }
        UUID uuid = player.getUniqueId();
        cancelTask(scaleRestoreTasks.remove(uuid));
        previousScale.putIfAbsent(uuid, instance.getBaseValue());
        instance.setBaseValue(0.6);
        startTimedBossBar(
            player,
            Ability.PLAZOWICZ.barColor,
            10,
            remaining -> "§bPłażowicza §7— pomniejszenie jeszcze §f" + remaining + "s",
            () -> startCooldownBossBar(player, Ability.PLAZOWICZ)
        );
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            scaleRestoreTasks.remove(uuid);
            if (player.isOnline()) {
                restoreScale(player);
            } else {
                previousScale.remove(uuid);
            }
        }, 20L * 10);
        scaleRestoreTasks.put(uuid, task);
    }

    private void restoreScale(Player player) {
        if (scaleAttribute == null) {
            return;
        }
        Double previous = previousScale.remove(player.getUniqueId());
        if (previous == null) {
            return;
        }
        AttributeInstance instance = player.getAttribute(scaleAttribute);
        if (instance != null) {
            instance.setBaseValue(previous);
        }
    }

    private void activateKostkaNausea(Player player) {
        if (confusionEffect != null) {
            Location origin = player.getLocation();
            for (Player target : player.getWorld().getPlayers()) {
                if (target.equals(player) || !target.isOnline()) {
                    continue;
                }
                if (target.getLocation().distanceSquared(origin) > 25.0) {
                    continue;
                }
                target.addPotionEffect(new PotionEffect(confusionEffect, 80, 0, false, true, true));
            }
        }
        startTimedBossBar(
            player,
            Ability.KOSTKA.barColor,
            4,
            remaining -> "§fKostka §7— nudności aktywne jeszcze §f" + remaining + "s",
            () -> startCooldownBossBar(player, Ability.KOSTKA)
        );
    }

    private void activateDiabelekCobweb(Player player) {
        UUID uuid = player.getUniqueId();
        cancelTask(cobwebClearTasks.remove(uuid));
        cobwebProtectUntil.put(uuid, System.currentTimeMillis() + 5000L);

        startTimedBossBar(
            player,
            Ability.DIABELEK.barColor,
            3,
            remaining -> "§cDiabelek §7— czyszczenie pajęczyn jeszcze §f" + remaining + "s",
            () -> startCooldownBossBar(player, Ability.DIABELEK)
        );

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int ticksLeft = 60;

            @Override
            public void run() {
                if (!player.isOnline() || this.ticksLeft <= 0) {
                    cancelTask(cobwebClearTasks.remove(uuid));
                    return;
                }
                clearCobwebsAround(player, 5);
                this.ticksLeft--;
            }
        }, 0L, 1L);
        cobwebClearTasks.put(uuid, task);
    }

    private void startSneakCountdown(Player player, Ability ability) {
        clearBossBar(player);
        UUID uuid = player.getUniqueId();
        BossBar bar = Bukkit.createBossBar(
            ability.prefix + "Shiftuj jeszcze §f2 " + ability.prefix + "razy, aby uruchomić "
                + ability.displayName + ability.prefix + "! §7(3s)",
            ability.barColor,
            BarStyle.SOLID,
            new BarFlag[0]
        );
        bar.addPlayer(player);
        bar.setProgress(1.0);
        sneakBossBars.put(uuid, bar);

        BukkitTask task = new BukkitRunnable() {
            int remainingSeconds = 3;

            @Override
            public void run() {
                if (!player.isOnline() || this.remainingSeconds <= 0) {
                    sneakCounter.remove(uuid);
                    sneakWindowUntil.remove(uuid);
                    clearBossBar(player);
                    cancel();
                    return;
                }
                int sneaksNeeded = Math.max(1, SNEAKS_REQUIRED - sneakCounter.getOrDefault(uuid, 1));
                bar.setTitle(String.format(
                    "%sShiftuj jeszcze §f%d %srazy, aby uruchomić %s%s! §7(%ds)",
                    ability.prefix, sneaksNeeded, ability.prefix, ability.displayName, ability.prefix, this.remainingSeconds
                ));
                bar.setProgress(Math.max(0.0, (double) (this.remainingSeconds - 1) / 3.0));
                --this.remainingSeconds;
            }
        }.runTaskTimer(plugin, 0L, 20L);
        sneakBossBarTasks.put(uuid, task);
    }

    private void updateSneakCountdown(Player player, Ability ability, int count) {
        BossBar bar = sneakBossBars.get(player.getUniqueId());
        if (bar == null) {
            startSneakCountdown(player, ability);
            bar = sneakBossBars.get(player.getUniqueId());
            if (bar == null) {
                return;
            }
        }
        int needed = Math.max(1, SNEAKS_REQUIRED - count);
        int secondsLeft = (int) Math.ceil(Math.max(0.0, bar.getProgress()) * 3.0);
        bar.setTitle(String.format(
            "%sShiftuj jeszcze §f%d %srazy, aby uruchomić %s%s! §7(%ds)",
            ability.prefix, needed, ability.prefix, ability.displayName, ability.prefix, Math.max(1, secondsLeft)
        ));
    }

    private void startTimedBossBar(Player player, BarColor color, int seconds,
                                   java.util.function.IntFunction<String> title, Runnable onEnd) {
        clearBossBar(player);
        UUID uuid = player.getUniqueId();
        BossBar bar = Bukkit.createBossBar(title.apply(seconds), color, BarStyle.SOLID, new BarFlag[0]);
        bar.addPlayer(player);
        bar.setProgress(1.0);
        sneakBossBars.put(uuid, bar);

        BukkitTask task = new BukkitRunnable() {
            int remaining = seconds;

            @Override
            public void run() {
                if (!player.isOnline() || this.remaining <= 0) {
                    clearBossBar(player);
                    cancel();
                    if (onEnd != null && player.isOnline()) {
                        onEnd.run();
                    }
                    return;
                }
                bar.setTitle(title.apply(this.remaining));
                bar.setProgress((double) this.remaining / (double) seconds);
                --this.remaining;
            }
        }.runTaskTimer(plugin, 0L, 20L);
        sneakBossBarTasks.put(uuid, task);
    }

    private void startCooldownBossBar(Player player, Ability ability) {
        long until = sneakCooldownUntil.getOrDefault(player.getUniqueId(), 0L);
        long remainingMs = until - System.currentTimeMillis();
        if (remainingMs <= 0L || !player.isOnline()) {
            return;
        }
        int seconds = (int) Math.ceil(remainingMs / 1000.0);
        clearBossBar(player);
        UUID uuid = player.getUniqueId();
        BossBar bar = Bukkit.createBossBar(
            ability.cooldownLabel + " §7— możesz użyć za §f" + seconds + "s",
            ability.barColor,
            BarStyle.SOLID,
            new BarFlag[0]
        );
        bar.addPlayer(player);
        bar.setProgress(1.0);
        sneakBossBars.put(uuid, bar);

        final int totalSeconds = seconds;
        BukkitTask task = new BukkitRunnable() {
            int remaining = totalSeconds;

            @Override
            public void run() {
                if (!player.isOnline() || this.remaining <= 0) {
                    clearBossBar(player);
                    cancel();
                    return;
                }
                bar.setTitle(ability.cooldownLabel + " §7— możesz użyć za §f" + this.remaining + "s");
                bar.setProgress((double) this.remaining / (double) totalSeconds);
                --this.remaining;
            }
        }.runTaskTimer(plugin, 0L, 20L);
        sneakBossBarTasks.put(uuid, task);
    }

    private void clearBossBar(Player player) {
        UUID uuid = player.getUniqueId();
        cancelTask(sneakBossBarTasks.remove(uuid));
        BossBar bar = sneakBossBars.remove(uuid);
        if (bar != null) {
            bar.removeAll();
        }
    }

    private enum Ability {
        PLAZOWICZ("§bPłażowicza", "§b", "§bPłażowicza", BarColor.BLUE),
        KOSTKA("§fKostki", "§f", "§fKostka", BarColor.WHITE),
        DIABELEK("§cDiabelka", "§c", "§cDiabelek", BarColor.RED);

        private final String displayName;
        private final String prefix;
        private final String cooldownLabel;
        private final BarColor barColor;

        Ability(String displayName, String prefix, String cooldownLabel, BarColor barColor) {
            this.displayName = displayName;
            this.prefix = prefix;
            this.cooldownLabel = cooldownLabel;
            this.barColor = barColor;
        }

        static Ability fromKey(String key) {
            return switch (key) {
                case "plazowicz" -> PLAZOWICZ;
                case "kostka" -> KOSTKA;
                case "diabelek" -> DIABELEK;
                default -> null;
            };
        }
    }

    private static void clearCobwebsAround(Player player, int radius) {
        Location center = player.getLocation();
        if (center.getWorld() == null) {
            return;
        }
        Block origin = center.getBlock();
        int radiusSquared = radius * radius;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x * x + y * y + z * z > radiusSquared) {
                        continue;
                    }
                    Block block = origin.getRelative(x, y, z);
                    if (block.getType() == Material.COBWEB) {
                        block.setType(Material.AIR, false);
                    }
                }
            }
        }
    }

    private static boolean isInLiquid(Player player) {
        if (player.isInWater()) {
            return true;
        }
        Block feetBlock = player.getLocation().getBlock();
        Block eyeBlock = player.getEyeLocation().getBlock();
        Material feet = feetBlock.getType();
        Material eyes = eyeBlock.getType();
        return feet == Material.WATER || feet == Material.LAVA
            || eyes == Material.WATER || eyes == Material.LAVA
            || feetBlock.isLiquid()
            || eyeBlock.isLiquid();
    }

    private static Player resolvePlayerDamager(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof org.bukkit.entity.Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    private static Attribute resolveScaleAttribute() {
        for (String candidate : new String[]{"GENERIC_SCALE", "SCALE"}) {
            try {
                return Attribute.valueOf(candidate);
            } catch (IllegalArgumentException ignored) {
            }
        }
        try {
            Object registry = Class.forName("org.bukkit.Registry").getField("ATTRIBUTE").get(null);
            Object key = NamespacedKey.minecraft("scale");
            Object attr = registry.getClass().getMethod("get", NamespacedKey.class).invoke(registry, key);
            if (attr instanceof Attribute attribute) {
                return attribute;
            }
        } catch (Throwable ignored) {
        }
        try {
            for (Attribute attribute : Attribute.values()) {
                String name = attribute.name();
                if ("SCALE".equals(name) || "GENERIC_SCALE".equals(name) || name.endsWith("_SCALE")) {
                    return attribute;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static PotionEffectType resolvePotion(String... names) {
        for (String name : names) {
            try {
                PotionEffectType type = PotionEffectType.getByName(name);
                if (type != null) {
                    return type;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static void cancelTask(BukkitTask task) {
        if (task != null) {
            task.cancel();
        }
    }
}
