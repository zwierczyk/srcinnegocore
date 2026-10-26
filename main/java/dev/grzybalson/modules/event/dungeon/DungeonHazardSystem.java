package dev.grzybalson.modules.event.dungeon;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Hazardy na fali: kolce, trujacy dym, wind zone. */
final class DungeonHazardSystem {

    enum Type { SPIKE, SMOKE, WIND }

    private final DungeonSession session;
    private final List<Zone> zones = new ArrayList<>();

    DungeonHazardSystem(DungeonSession session) {
        this.session = session;
    }

    void clear() {
        for (Zone zone : zones) {
            if (zone.marker != null && zone.marker.isValid()) {
                zone.marker.remove();
            }
        }
        zones.clear();
    }

    void spawnForWave(Location waveCenter) {
        clear();
        if (waveCenter == null || waveCenter.getWorld() == null) {
            return;
        }
        World world = waveCenter.getWorld();
        zones.add(spawnZone(Type.SPIKE, offset(waveCenter, 3.5D, 2.0D), 1.6D));
        zones.add(spawnZone(Type.SPIKE, offset(waveCenter, -3.2D, -2.4D), 1.5D));
        zones.add(spawnZone(Type.SMOKE, offset(waveCenter, 1.8D, -3.5D), 2.4D));
        zones.add(spawnZone(Type.WIND, offset(waveCenter, -2.5D, 3.2D), 2.8D));
        world.playSound(waveCenter, Sound.BLOCK_BEACON_AMBIENT, 0.35F, 1.6F);
    }

    private Location offset(Location base, double dx, double dz) {
        Location at = base.clone().add(dx, 0, dz);
        World world = at.getWorld();
        if (world != null) {
            at.setY(world.getHighestBlockYAt(at) + 1.05D);
        }
        return at;
    }

    private Zone spawnZone(Type type, Location at, double radius) {
        ArmorStand marker = at.getWorld().spawn(at.clone().add(0, type == Type.SPIKE ? 0.1D : 0.4D, 0), ArmorStand.class, as -> {
            as.setVisible(false);
            as.setGravity(false);
            as.setMarker(true);
            as.setSmall(true);
            as.setBasePlate(false);
            as.setArms(false);
            as.setCollidable(false);
            as.setCustomNameVisible(true);
            as.setGlowing(true);
            as.addScoreboardTag("holly_dungeon_hazard");
            String label = switch (type) {
                case SPIKE -> "&#FF5C5C⚠ KOLCE";
                case SMOKE -> "&#9FFF00☁ TRUJACY DYM";
                case WIND -> "&#00C1FF≋ WIATR";
            };
            as.customName(dev.grzybalson.utils.ColorUtils.colorizeComponent(label)
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            if (as.getEquipment() != null) {
                Material helm = switch (type) {
                    case SPIKE -> Material.IRON_BARS;
                    case SMOKE -> Material.LIME_DYE;
                    case WIND -> Material.FEATHER;
                };
                as.getEquipment().setHelmet(new org.bukkit.inventory.ItemStack(helm));
            }
        });
        return new Zone(type, at.clone(), radius, marker);
    }

    void tick(long hazardTick) {
        if (zones.isEmpty()) {
            return;
        }
        Iterator<Zone> it = zones.iterator();
        while (it.hasNext()) {
            Zone zone = it.next();
            if (zone.center.getWorld() == null) {
                it.remove();
                continue;
            }
            // FX rzadziej — mniej particle spam na main thread.
            if (hazardTick % 8L == 0L) {
                switch (zone.type) {
                    case SPIKE -> zone.center.getWorld().spawnParticle(
                        Particle.CRIT, zone.center.clone().add(0, 0.2D, 0), 3, 0.45, 0.05, 0.45, 0.01);
                    case SMOKE -> zone.center.getWorld().spawnParticle(
                        Particle.REDSTONE, zone.center.clone().add(0, 0.6D, 0), 5, 0.7, 0.35, 0.7, 0.0,
                        new Particle.DustOptions(org.bukkit.Color.fromRGB(120, 220, 60), 1.2F));
                    case WIND -> zone.center.getWorld().spawnParticle(
                        Particle.CLOUD, zone.center.clone().add(0, 0.5D, 0), 3, 0.55, 0.25, 0.55, 0.01);
                }
            }
            if (hazardTick % 10L != 0L) {
                continue;
            }
            double r2 = zone.radius * zone.radius;
            for (Player player : session.getOnlineFighters()) {
                if (!player.getWorld().equals(zone.center.getWorld())) {
                    continue;
                }
                if (player.getLocation().distanceSquared(zone.center) > r2) {
                    continue;
                }
                apply(zone, player);
            }
        }
    }

    private void apply(Zone zone, Player player) {
        switch (zone.type) {
            case SPIKE -> {
                player.damage(2.5D);
                player.getWorld().playSound(player.getLocation(), Sound.BLOCK_STONE_HIT, 0.45F, 1.4F);
                player.getWorld().spawnParticle(Particle.BLOCK_CRACK, player.getLocation().add(0, 0.2D, 0),
                    10, 0.2, 0.1, 0.2, 0.02, Material.IRON_BLOCK.createBlockData());
                MultiActionBarServiceHint.warn(player, "Kolce! Odejdz z czerwonej strefy");
            }
            case SMOKE -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 40, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.CONFUSION, 40, 0, false, false, false));
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT_SWEET_BERRY_BUSH, 0.35F, 0.7F);
                MultiActionBarServiceHint.warn(player, "Trujacy dym! Wyjdz z zielonej chmury");
            }
            case WIND -> {
                Vector push = player.getLocation().toVector().subtract(zone.center.toVector());
                if (push.lengthSquared() < 0.05D) {
                    push = new Vector(
                        ThreadLocalRandom.current().nextDouble(-1, 1),
                        0.35D,
                        ThreadLocalRandom.current().nextDouble(-1, 1)
                    );
                } else {
                    push = push.setY(0).normalize().multiply(0.85D);
                    push.setY(0.42D);
                }
                player.setVelocity(push);
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 0.35F, 1.8F);
                MultiActionBarServiceHint.warn(player, "Wiatr Cię spycha!");
            }
        }
    }

    private static final class Zone {
        private final Type type;
        private final Location center;
        private final double radius;
        private final ArmorStand marker;

        private Zone(Type type, Location center, double radius, ArmorStand marker) {
            this.type = type;
            this.center = center;
            this.radius = radius;
            this.marker = marker;
        }
    }

    /** Cienka warstwa nad MultiActionBar — zeby nie spamowac importami w session. */
    private static final class MultiActionBarServiceHint {
        static void warn(Player player, String text) {
            dev.grzybalson.managers.MultiActionBarService.get().send(player, "dungeon-hazard",
                "<gradient:#FF5C5C:#FFD900>" + text + "</gradient></shadow>", 25L);
        }
    }
}
