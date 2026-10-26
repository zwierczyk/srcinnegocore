package dev.grzybalson.anarchiagg.customitems.util;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class EventowkiEffects {

    private static final Map<UUID, Location> HOVER_LOCK = new ConcurrentHashMap<>();
    private static final Map<UUID, BukkitTask> HOVER_TASKS = new ConcurrentHashMap<>();
    private static final Map<UUID, BukkitTask> PULL_TASKS = new ConcurrentHashMap<>();
    private static final Map<UUID, SkywalkerSession> SKYWALKER = new ConcurrentHashMap<>();
    private static final Set<UUID> CLAIMED_ENTITIES = ConcurrentHashMap.newKeySet();

    public static boolean claimEntity(Entity entity) {
        return entity != null && CLAIMED_ENTITIES.add(entity.getUniqueId());
    }

    public static void shutdownAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            stopHoverLock(player);
            cancelSkywalkerRewind(player, true);
        }
        HOVER_LOCK.clear();
        HOVER_TASKS.values().forEach(BukkitTask::cancel);
        HOVER_TASKS.clear();
        PULL_TASKS.values().forEach(BukkitTask::cancel);
        PULL_TASKS.clear();
        for (SkywalkerSession session : new HashSet<>(SKYWALKER.values())) {
            if (session.task != null) {
                session.task.cancel();
            }
            if (session.camera != null && session.camera.isValid()) {
                session.camera.remove();
            }
        }
        SKYWALKER.clear();
        CLAIMED_ENTITIES.clear();
    }

    public static void startHoverLock(JavaPlugin plugin, Player player, double liftBlocks) {
        if (player == null || plugin == null) {
            return;
        }
        stopHoverLock(player);
        Location hover = player.getLocation().clone().add(0.0D, liftBlocks, 0.0D);
        player.setGliding(false);
        player.setGravity(false);
        player.setVelocity(new Vector(0, 0, 0));
        player.setFallDistance(0.0F);
        player.teleport(hover);
        HOVER_LOCK.put(player.getUniqueId(), hover.clone());

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || !HOVER_LOCK.containsKey(player.getUniqueId())) {
                stopHoverLock(player);
                return;
            }
            player.setGravity(false);
            player.setGliding(false);
            player.setVelocity(new Vector(0, 0, 0));
            player.setFallDistance(0.0F);
            Location lock = HOVER_LOCK.get(player.getUniqueId());
            if (lock == null || lock.getWorld() == null || player.getWorld() != lock.getWorld()) {
                return;
            }
            Location current = player.getLocation();
            if (distanceSquaredHorizontalAndY(current, lock) > 0.0008D) {
                Location next = lock.clone();
                next.setYaw(current.getYaw());
                next.setPitch(current.getPitch());
                player.teleport(next);
            }
        }, 1L, 1L);
        HOVER_TASKS.put(player.getUniqueId(), task);
    }

    public static void stopHoverLock(Player player) {
        if (player == null) {
            return;
        }
        HOVER_LOCK.remove(player.getUniqueId());
        BukkitTask task = HOVER_TASKS.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
        if (player.isOnline()) {
            player.setGravity(true);
            player.setFallDistance(0.0F);
            player.setVelocity(new Vector(0, 0, 0));
        }
    }

    public static boolean isHoverLocked(Player player) {
        return player != null && HOVER_LOCK.containsKey(player.getUniqueId());
    }

    public static void clampHoverMove(PlayerMoveEvent event) {
        if (event == null) {
            return;
        }
        Location lock = HOVER_LOCK.get(event.getPlayer().getUniqueId());
        if (lock == null) {
            return;
        }
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        Location next = lock.clone();
        next.setYaw(to.getYaw());
        next.setPitch(to.getPitch());
        event.setTo(next);
    }

    public static void pullToShooter(JavaPlugin plugin, Player victim, Player shooter) {
        pullToShooter(plugin, victim, shooter, 15.0D);
    }

    public static void pullToShooter(JavaPlugin plugin, Player victim, Player shooter, double maxRange) {
        if (plugin == null || victim == null || shooter == null || !victim.isOnline() || !shooter.isOnline()) {
            return;
        }
        double range = Math.max(1.0D, maxRange);
        double maxDistance = range + 1.5D;
        BukkitTask previous = PULL_TASKS.remove(victim.getUniqueId());
        if (previous != null) {
            previous.cancel();
        }
        AttributeInstance knockback = victim.getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE);
        double previousKnockback = knockback == null ? 0.0D : knockback.getBaseValue();
        if (knockback != null) {
            knockback.setBaseValue(0.0D);
        }
        victim.setFallDistance(0.0F);
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!victim.isOnline() || !shooter.isOnline() || victim.getWorld() != shooter.getWorld() || ticks++ >= 18) {
                    stopPull(victim, knockback, previousKnockback);
                    return;
                }
                Location dest = inFrontOf(shooter, 1.8D);
                Vector delta = dest.toVector().subtract(victim.getLocation().toVector());
                double distance = delta.length();
                if (distance > maxDistance) {
                    victim.setVelocity(new Vector(0.0D, 0.0D, 0.0D));
                    victim.setFallDistance(0.0F);
                    stopPull(victim, knockback, previousKnockback);
                    return;
                }
                drawRope(shooter.getEyeLocation(), victim.getLocation().clone().add(0.0D, 1.0D, 0.0D));
                victim.getWorld().spawnParticle(Particle.CLOUD, victim.getLocation().add(0.0D, 1.0D, 0.0D), 4, 0.12, 0.18, 0.12, 0.01);
                if (distance < 0.55D) {
                    victim.teleport(dest.clone());
                    victim.setVelocity(new Vector(0.0D, 0.0D, 0.0D));
                    victim.setFallDistance(0.0F);
                    stopPull(victim, knockback, previousKnockback);
                    return;
                }
                double speed = Math.min(1.05D, 0.45D + Math.min(distance, range) * 0.14D);
                Vector velocity = delta.normalize().multiply(speed);
                victim.setVelocity(velocity);
                victim.setFallDistance(0.0F);
            }
        }, 0L, 1L);
        PULL_TASKS.put(victim.getUniqueId(), task);
        shooter.playSound(shooter.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1.0f, 0.7f);
        victim.playSound(victim.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1.0f, 0.5f);
    }

    private static void stopPull(Player victim, AttributeInstance knockback, double previousKnockback) {
        restoreKnockback(knockback, previousKnockback);
        BukkitTask current = PULL_TASKS.remove(victim.getUniqueId());
        if (current != null) {
            current.cancel();
        }
    }

    public static Player findPullTarget(Projectile projectile, Player shooter, double radius) {
        if (projectile == null || shooter == null || projectile.getWorld() == null) {
            return null;
        }
        Player closest = null;
        double closestDistance = radius * radius;
        for (Entity entity : projectile.getNearbyEntities(radius, radius, radius)) {
            if (!(entity instanceof Player victim)
                || victim.getUniqueId().equals(shooter.getUniqueId())
                || victim.getGameMode() == GameMode.SPECTATOR
                || !victim.isValid()) {
                continue;
            }
            double distance = victim.getLocation().distanceSquared(projectile.getLocation());
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = victim;
            }
        }
        return closest;
    }

    public static void startSkywalkerRewind(
        JavaPlugin plugin,
        Player player,
        Location from,
        Location to,
        int rewindTicks,
        Consumer<Player> onComplete
    ) {
        cancelSkywalkerRewind(player, false);
        if (plugin == null || player == null || !player.isOnline() || from == null || to == null || from.getWorld() == null) {
            if (onComplete != null) {
                onComplete.accept(player);
            }
            return;
        }

        GameMode previousMode = player.getGameMode();
        boolean allowFlight = player.getAllowFlight();
        boolean flying = player.isFlying();
        float flySpeed = player.getFlySpeed();

        ArmorStand camera = from.getWorld().spawn(from.clone(), ArmorStand.class, stand -> {
            stand.setInvisible(true);
            stand.setVisible(false);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setCollidable(false);
            stand.setPersistent(false);
            stand.setSilent(true);
            stand.setBasePlate(false);
            stand.setArms(false);
            stand.setSmall(false);
            stand.setMarker(false);
            stand.setCustomNameVisible(false);
            stand.addScoreboardTag("holly-skywalker-cam");
        });
        camera.teleport(from.clone());

        player.setGravity(false);
        player.setVelocity(new Vector(0, 0, 0));
        player.setGameMode(GameMode.SPECTATOR);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setFlySpeed(0.01F);
        player.setSpectatorTarget(camera);

        long startedAt = System.currentTimeMillis();
        long durationMillis = Math.max(8, rewindTicks) * 50L;
        SkywalkerSession session = new SkywalkerSession(previousMode, allowFlight, flying, flySpeed, camera, null, onComplete);
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || camera.isDead() || !SKYWALKER.containsKey(player.getUniqueId())) {
                finishSkywalker(player, to, true);
                return;
            }
            double progress = (System.currentTimeMillis() - startedAt) / (double) durationMillis;
            if (progress >= 1.0D) {
                finishSkywalker(player, to, true);
                return;
            }
            Location step = lerp(from, to, easeInOutCubic(progress));
            Vector delta = step.toVector().subtract(camera.getLocation().toVector());
            if (delta.lengthSquared() > 16.0D) {
                camera.teleport(step);
            } else {
                camera.setVelocity(delta);
            }
            camera.setRotation(step.getYaw(), step.getPitch());
            if (step.getWorld() != null) {
                step.getWorld().spawnParticle(Particle.REVERSE_PORTAL, step.clone().add(0.0D, 1.0D, 0.0D), 10, 0.18, 0.35, 0.18, 0.01);
                step.getWorld().spawnParticle(Particle.END_ROD, step.clone().add(0.0D, 1.0D, 0.0D), 3, 0.1, 0.22, 0.1, 0.0);
            }
            EventItemTools.sendCastleActionBar(
                player,
                "skywalker",
                "&#7AD4FF⚠ &8Ξ &#7AD4FFRetrospekcja Skywalkera &8〢 &#7AD4FFPowrót..."
            );
        }, 0L, 1L);
        session.task = task;
        SKYWALKER.put(player.getUniqueId(), session);
    }

    public static boolean isSkywalkerRewinding(Player player) {
        return player != null && SKYWALKER.containsKey(player.getUniqueId());
    }

    public static void cancelSkywalkerRewind(Player player, boolean restore) {
        if (player == null) {
            return;
        }
        finishSkywalker(player, restore ? player.getLocation() : null, restore);
    }

    private static void finishSkywalker(Player player, Location destination, boolean restore) {
        SkywalkerSession session = SKYWALKER.remove(player.getUniqueId());
        if (session == null) {
            return;
        }
        if (session.task != null) {
            session.task.cancel();
        }
        if (session.camera != null && session.camera.isValid()) {
            session.camera.remove();
        }
        if (player.isOnline() && restore) {
            player.setSpectatorTarget(null);
            if (destination != null && destination.getWorld() != null) {
                player.teleport(destination);
            }
            player.setFlySpeed(session.flySpeed);
            player.setGameMode(session.previousMode);
            player.setAllowFlight(session.allowFlight);
            player.setFlying(session.flying && (session.allowFlight || session.previousMode == GameMode.SPECTATOR || session.previousMode == GameMode.CREATIVE));
            player.setGravity(true);
            player.setFallDistance(0.0F);
            player.setVelocity(new Vector(0, 0, 0));
            player.removePotionEffect(PotionEffectType.INVISIBILITY);
            if (session.onComplete != null) {
                session.onComplete.accept(player);
            }
        }
    }

    private static void restoreKnockback(AttributeInstance knockback, double previous) {
        if (knockback != null) {
            knockback.setBaseValue(previous);
        }
    }

    private static void drawRope(Location from, Location to) {
        if (from == null || to == null || from.getWorld() == null || from.getWorld() != to.getWorld()) {
            return;
        }
        Vector diff = to.toVector().subtract(from.toVector());
        double length = diff.length();
        if (length < 0.2D) {
            return;
        }
        Vector step = diff.normalize().multiply(0.32D);
        Location cursor = from.clone();
        int points = Math.min(48, (int) Math.ceil(length / 0.32D));
        for (int i = 0; i < points; i++) {
            cursor.add(step);
            from.getWorld().spawnParticle(Particle.CRIT, cursor, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private static Location inFrontOf(Player player, double distance) {
        Location origin = player.getLocation();
        Vector forward = origin.getDirection();
        forward.setY(0.0D);
        if (forward.lengthSquared() < 0.0001D) {
            forward = new Vector(0.0D, 0.0D, 1.0D);
        }
        Location dest = origin.clone().add(forward.normalize().multiply(distance));
        dest.setY(origin.getY());
        dest.setYaw(origin.getYaw());
        dest.setPitch(0.0F);
        return dest;
    }

    private static Location lerp(Location from, Location to, double t) {
        double clamped = Math.max(0.0D, Math.min(1.0D, t));
        Location result = from.clone();
        result.setX(from.getX() + (to.getX() - from.getX()) * clamped);
        result.setY(from.getY() + (to.getY() - from.getY()) * clamped);
        result.setZ(from.getZ() + (to.getZ() - from.getZ()) * clamped);
        result.setYaw(from.getYaw() + (to.getYaw() - from.getYaw()) * (float) clamped);
        result.setPitch(from.getPitch() + (to.getPitch() - from.getPitch()) * (float) clamped);
        return result;
    }

    private static double easeInOutCubic(double t) {
        double clamped = Math.max(0.0D, Math.min(1.0D, t));
        return clamped < 0.5D
            ? 4.0D * clamped * clamped * clamped
            : 1.0D - Math.pow(-2.0D * clamped + 2.0D, 3.0D) / 2.0D;
    }

    private static double distanceSquaredHorizontalAndY(Location a, Location b) {
        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private static final class SkywalkerSession {
        private final GameMode previousMode;
        private final boolean allowFlight;
        private final boolean flying;
        private final float flySpeed;
        private final ArmorStand camera;
        private BukkitTask task;
        private final Consumer<Player> onComplete;

        private SkywalkerSession(
            GameMode previousMode,
            boolean allowFlight,
            boolean flying,
            float flySpeed,
            ArmorStand camera,
            BukkitTask task,
            Consumer<Player> onComplete
        ) {
            this.previousMode = previousMode;
            this.allowFlight = allowFlight;
            this.flying = flying;
            this.flySpeed = flySpeed;
            this.camera = camera;
            this.task = task;
            this.onComplete = onComplete;
        }
    }
}
