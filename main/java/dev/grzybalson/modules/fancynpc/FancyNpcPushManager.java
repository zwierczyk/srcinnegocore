package dev.grzybalson.modules.fancynpc;

import de.oliver.fancynpcs.api.FancyNpcsPlugin;
import de.oliver.fancynpcs.api.Npc;
import de.oliver.fancynpcs.api.NpcData;
import de.oliver.fancynpcs.api.NpcManager;
import dev.grzybalson.HollyCore;
import dev.grzybalson.config.HollyDataPaths;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.SoundCategory;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FancyNPC nie ma solidnej kolizji — miękkie odpychanie (impuls), bez ściany
 * uniemożliwiającej podejście.
 */
public final class FancyNpcPushManager implements Listener {

    private static final String RESOURCE = "addons/fancynpc-push/fancynpc-push.yml";
    private static final long NPC_CACHE_TTL_MS = 1500L;

    private static final Sound WIND_CHARGE_SOUND = resolveSound();
    private static final Particle WIND_BURST_PARTICLE = resolveParticle();

    private static Sound resolveSound() {
        for (String soundName : List.of(
            "ENTITY_WIND_CHARGE_WIND_BURST",
            "BLOCK_WIND_CHARGE_BURST",
            "ENTITY_BREEZE_WIND_BURST",
            "ENTITY_BREEZE_SHOOT",
            "ITEM_TRIDENT_RIPTIDE_1",
            "ENTITY_FIREWORK_ROCKET_BLAST"
        )) {
            try {
                return Sound.valueOf(soundName);
            } catch (IllegalArgumentException | NoSuchFieldError ignored) {
            }
        }
        return Sound.ENTITY_FIREWORK_ROCKET_BLAST;
    }

    private static Particle resolveParticle() {
        for (String partName : List.of("WIND_BURST", "GUST_EMITTER_LARGE", "GUST", "CLOUD", "EXPLOSION_NORMAL")) {
            try {
                return Particle.valueOf(partName);
            } catch (IllegalArgumentException | NoSuchFieldError ignored) {
            }
        }
        return Particle.CLOUD;
    }

    private final HollyCore plugin;
    private final Map<UUID, Long> messageCooldown = new ConcurrentHashMap<>();
    private final Map<UUID, Long> pushCooldown = new ConcurrentHashMap<>();
    private final Map<UUID, List<Location>> npcLocationsByWorld = new ConcurrentHashMap<>();
    private volatile long npcCacheBuiltAt;
    private volatile boolean fancyReadyCached;
    private volatile long fancyReadyCheckedAt;

    private boolean enabled = true;
    private boolean soundEnabled = true;
    private boolean particlesEnabled = true;
    private double radius = 1.25D;
    private double radiusSquared = radius * radius;
    /** Jak blisko można stanąć — miękka „powłoka” kolizji (nie ściana na radius). */
    private double minDistance = 0.65D;
    private double pushStrength = 0.65D;
    private double pushY = 0.36D;
    private long pushCooldownMs = 350L;
    private String message = "";
    private long messageCooldownMs = 2500L;
    private String bypassPermission = "Holly.fancynpc.push.bypass";
    private boolean onlySavedNpcs = true;
    private Set<String> onlyNpcs = Set.of();
    private Set<String> excludedNpcs = Set.of();

    public FancyNpcPushManager(HollyCore plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        HollyDataPaths.ensureResource(plugin, RESOURCE);
        File file = HollyDataPaths.getRootFile(plugin, RESOURCE);
        FileConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        enabled = yaml.getBoolean("enabled", true);
        soundEnabled = yaml.getBoolean("sound-enabled", true);
        particlesEnabled = yaml.getBoolean("particles-enabled", true);
        radius = Math.max(0.5D, yaml.getDouble("radius", 1.25D));
        radiusSquared = radius * radius;
        minDistance = Math.max(0.35D, Math.min(radius - 0.05D, yaml.getDouble("min-distance", 0.65D)));
        pushStrength = Math.max(0.05D, yaml.getDouble("push-strength", 0.65D));
        pushY = yaml.getDouble("push-y", 0.36D);
        pushCooldownMs = Math.max(0L, yaml.getLong("push-cooldown-ms", 350L));
        message = yaml.getString("message", "");
        messageCooldownMs = Math.max(0L, yaml.getLong("message-cooldown-ms", 2500L));
        bypassPermission = yaml.getString("bypass-permission", "Holly.fancynpc.push.bypass");
        onlySavedNpcs = yaml.getBoolean("only-saved-npcs", true);
        onlyNpcs = toLowerSet(yaml.getStringList("only-npcs"));
        excludedNpcs = toLowerSet(yaml.getStringList("excluded-npcs"));
        invalidateNpcCache();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!enabled) {
            return;
        }
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        Location from = event.getFrom();
        // Szybkie odrzucenie ruchów głowy (obrót kamery bez zmiany pozycji)
        if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) {
            return;
        }

        Player player = event.getPlayer();
        if (player.isDead() || !player.isOnline()) {
            return;
        }
        if (bypassPermission != null && !bypassPermission.isBlank() && player.hasPermission(bypassPermission)) {
            return;
        }
        if (!isFancyNpcsReady()) {
            return;
        }

        NpcHit nearest = findNearestNpc(to);
        if (nearest == null) {
            return;
        }

        Location npcLoc = nearest.location;
        double dist = Math.sqrt(nearest.distSq);
        Vector away = horizontalAway(to, npcLoc, player);
        boolean movingCloser = isMovingCloser(from, to, npcLoc);

        // Impuls odpychania (jak eksplozja Wind Charge) — dynamiczny wyrzut w tył i lekko w górę
        if (dist < radius && movingCloser && canPush(player.getUniqueId())) {
            double penetration = Math.max(0.0D, (radius - dist) / radius);
            double strength = pushStrength * (0.65D + 0.35D * penetration);
            Vector velocity = away.clone().multiply(strength);
            velocity.setY(Math.max(0.32D, pushY));
            // Zachowaj część obecnej prędkości gracza, żeby nie „kleić” w miejscu
            Vector current = player.getVelocity();
            velocity.setX(velocity.getX() + current.getX() * 0.20D);
            velocity.setZ(velocity.getZ() + current.getZ() * 0.20D);
            player.setVelocity(velocity);
            player.setFallDistance(0.0F);

            Location effectLoc = player.getLocation().add(0, 0.75D, 0);
            if (soundEnabled && effectLoc.getWorld() != null) {
                try {
                    effectLoc.getWorld().playSound(effectLoc, "entity.wind_charge.wind_burst", SoundCategory.PLAYERS, 1.25F, 1.15F);
                } catch (Throwable ignored) {
                }
                if (WIND_CHARGE_SOUND != null) {
                    effectLoc.getWorld().playSound(effectLoc, WIND_CHARGE_SOUND, 0.9F, 1.2F);
                }
            }
            if (particlesEnabled && effectLoc.getWorld() != null) {
                if (WIND_BURST_PARTICLE != null) {
                    effectLoc.getWorld().spawnParticle(WIND_BURST_PARTICLE, effectLoc, 3, 0.25D, 0.2D, 0.25D, 0.04D);
                }
                effectLoc.getWorld().spawnParticle(Particle.CLOUD, effectLoc, 6, 0.3D, 0.1D, 0.3D, 0.06D);
            }

            pushCooldown.put(player.getUniqueId(), System.currentTimeMillis());
            sendMessage(player);
        } else if (dist < minDistance && movingCloser) {
            // Gdy cooldown odepchnięcia jeszcze trwa (< 350ms) i gracz forsuje wejście w NPC — zatrzymaj na powłoce
            Location clamped = npcLoc.clone().add(away.clone().multiply(minDistance));
            clamped.setY(to.getY());
            clamped.setYaw(to.getYaw());
            clamped.setPitch(to.getPitch());
            if (clamped.getWorld() != null) {
                event.setTo(clamped);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        messageCooldown.remove(id);
        pushCooldown.remove(id);
    }

    private boolean canPush(UUID playerId) {
        if (pushCooldownMs <= 0L) {
            return true;
        }
        Long last = pushCooldown.get(playerId);
        return last == null || System.currentTimeMillis() - last >= pushCooldownMs;
    }

    private static boolean isMovingCloser(Location from, Location to, Location npcLoc) {
        double fromDist = horizontalDistSq(from, npcLoc);
        double toDist = horizontalDistSq(to, npcLoc);
        return toDist + 1.0E-6D < fromDist;
    }

    private static double horizontalDistSq(Location a, Location b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    private static Vector horizontalAway(Location playerLoc, Location npcLoc, Player player) {
        Vector away = playerLoc.toVector().subtract(npcLoc.toVector());
        away.setY(0.0D);
        if (away.lengthSquared() < 1.0E-4D) {
            Vector look = player.getLocation().getDirection();
            away = new Vector(-look.getX(), 0.0D, -look.getZ());
            if (away.lengthSquared() < 1.0E-4D) {
                away = new Vector(1.0D, 0.0D, 0.0D);
            }
        }
        return away.normalize();
    }

    private NpcHit findNearestNpc(Location at) {
        World world = at.getWorld();
        if (world == null) {
            return null;
        }
        List<Location> cached = npcLocationsFor(world);
        if (cached.isEmpty()) {
            return null;
        }

        NpcHit nearest = null;
        double best = Double.MAX_VALUE;
        double px = at.getX();
        double py = at.getY();
        double pz = at.getZ();

        for (Location npcLoc : cached) {
            if (npcLoc == null || npcLoc.getWorld() == null) {
                continue;
            }
            double dy = py - npcLoc.getY();
            if (Math.abs(dy) > 2.5D) {
                continue;
            }
            double dx = px - npcLoc.getX();
            if (dx > radius || dx < -radius) {
                continue;
            }
            double dz = pz - npcLoc.getZ();
            if (dz > radius || dz < -radius) {
                continue;
            }
            double distSq = dx * dx + dz * dz;
            if (distSq > radiusSquared) {
                continue;
            }
            if (distSq < best) {
                best = distSq;
                nearest = new NpcHit(npcLoc, distSq);
            }
        }
        return nearest;
    }

    private List<Location> npcLocationsFor(World world) {
        long now = System.currentTimeMillis();
        if (now - npcCacheBuiltAt > NPC_CACHE_TTL_MS) {
            rebuildNpcCache();
        }
        return npcLocationsByWorld.getOrDefault(world.getUID(), List.of());
    }

    private void invalidateNpcCache() {
        npcCacheBuiltAt = 0L;
        npcLocationsByWorld.clear();
    }

    private void rebuildNpcCache() {
        Map<UUID, List<Location>> next = new ConcurrentHashMap<>();
        try {
            NpcManager manager = FancyNpcsPlugin.get().getNpcManager();
            if (manager == null) {
                npcLocationsByWorld.clear();
                npcCacheBuiltAt = System.currentTimeMillis();
                return;
            }
            Collection<?> all = manager.getAllNpcs();
            if (all == null || all.isEmpty()) {
                npcLocationsByWorld.clear();
                npcCacheBuiltAt = System.currentTimeMillis();
                return;
            }
            for (Object raw : all) {
                if (!(raw instanceof Npc npc)) {
                    continue;
                }
                if (onlySavedNpcs && !npc.isSaveToFile()) {
                    continue;
                }
                NpcData data = npc.getData();
                if (data == null || !data.isSpawnEntity()) {
                    continue;
                }
                String name = data.getName();
                String id = data.getId();
                if (isExcluded(name, id)) {
                    continue;
                }
                if (!onlyNpcs.isEmpty() && !isInOnlyList(name, id)) {
                    continue;
                }
                Location npcLoc = data.getLocation();
                if (npcLoc == null || npcLoc.getWorld() == null) {
                    continue;
                }
                next.computeIfAbsent(npcLoc.getWorld().getUID(), ignored -> new java.util.ArrayList<>()).add(npcLoc);
            }
        } catch (Throwable ignored) {
            npcLocationsByWorld.clear();
            npcCacheBuiltAt = System.currentTimeMillis();
            return;
        }
        npcLocationsByWorld.clear();
        for (Map.Entry<UUID, List<Location>> entry : next.entrySet()) {
            npcLocationsByWorld.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        npcCacheBuiltAt = System.currentTimeMillis();
    }

    private void sendMessage(Player player) {
        if (message == null || message.isBlank()) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = messageCooldown.get(player.getUniqueId());
        if (last != null && now - last < messageCooldownMs) {
            return;
        }
        messageCooldown.put(player.getUniqueId(), now);
        player.sendMessage(ColorUtils.colorizeComponent(message));
    }

    private boolean isExcluded(String name, String id) {
        if (excludedNpcs.isEmpty()) {
            return false;
        }
        return containsIgnoreCase(excludedNpcs, name) || containsIgnoreCase(excludedNpcs, id);
    }

    private boolean isInOnlyList(String name, String id) {
        return containsIgnoreCase(onlyNpcs, name) || containsIgnoreCase(onlyNpcs, id);
    }

    private static boolean containsIgnoreCase(Set<String> set, String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return set.contains(value.toLowerCase(Locale.ROOT));
    }

    private static Set<String> toLowerSet(List<String> list) {
        if (list == null || list.isEmpty()) {
            return Set.of();
        }
        Set<String> out = new HashSet<>();
        for (String entry : list) {
            if (entry != null && !entry.isBlank()) {
                out.add(entry.trim().toLowerCase(Locale.ROOT));
            }
        }
        return out.isEmpty() ? Set.of() : Set.copyOf(out);
    }

    private boolean isFancyNpcsReady() {
        long now = System.currentTimeMillis();
        if (now - fancyReadyCheckedAt < 2000L) {
            return fancyReadyCached;
        }
        fancyReadyCheckedAt = now;
        try {
            fancyReadyCached = Bukkit.getPluginManager().isPluginEnabled("FancyNpcs") && FancyNpcsPlugin.get() != null;
        } catch (Throwable ignored) {
            fancyReadyCached = false;
        }
        return fancyReadyCached;
    }

    private record NpcHit(Location location, double distSq) {
    }
}
