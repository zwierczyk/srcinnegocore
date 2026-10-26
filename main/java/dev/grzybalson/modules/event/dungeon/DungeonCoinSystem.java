package dev.grzybalson.modules.event.dungeon;

import dev.grzybalson.HollyCore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Coins visible only to the killer. Short 3x3 magnet — walk up to pick up.
 */
public final class DungeonCoinSystem {

    private static final String SOUND_DROP = "minecraft:event.dungeon_coin_drop";
    private static final String SOUND_PICKUP = "minecraft:event.dungeon_coin_pickup";
    private static final int COIN_CMD = 7001;
    private static final float COIN_SCALE = 1.15F;
    /** ~3x3 area: magnet from ~1.5 blocks. */
    private static final double MAGNET_RANGE = 1.55D;
    private static final double PICKUP_RANGE = 0.85D;
    private static final double GROUND_Y_OFFSET = 0.32D;

    private final DungeonSession session;
    private final HollyCore plugin;
    private final List<Coin> coins = new ArrayList<>();
    private final Map<UUID, Integer> collected = new ConcurrentHashMap<>();

    public DungeonCoinSystem(DungeonSession session) {
        this.session = session;
        this.plugin = session.getPlugin();
    }

    /** Kept for API compat — magnet is always local 3x3, never global after-wave. */
    public void setCollectable(boolean ignored) {
        // no-op
    }

    public boolean isCollectable() {
        return true;
    }

    public int getCoins(UUID uuid) {
        return collected.getOrDefault(uuid, 0);
    }

    public void setCoins(UUID uuid, int amount) {
        if (uuid == null) {
            return;
        }
        if (amount <= 0) {
            collected.remove(uuid);
        } else {
            collected.put(uuid, amount);
        }
    }

    public Map<UUID, Integer> getAllCollected() {
        return collected;
    }

    public void spawnFromKill(Location at, boolean boss, Player killer) {
        if (at == null || at.getWorld() == null || killer == null) {
            return;
        }
        UUID ownerId = killer != null ? killer.getUniqueId() : null;
        int count = boss
            ? 10 + ThreadLocalRandom.current().nextInt(5)
            : 2 + ThreadLocalRandom.current().nextInt(3);
        World world = at.getWorld();
        Location base = at.clone().add(0, 0.15D, 0);
        for (int i = 0; i < count; i++) {
            double angle = ThreadLocalRandom.current().nextDouble() * Math.PI * 2.0D;
            double speed = 0.08D + ThreadLocalRandom.current().nextDouble() * 0.12D;
            Vector vel = new Vector(
                Math.cos(angle) * speed,
                0.18D + ThreadLocalRandom.current().nextDouble() * 0.12D,
                Math.sin(angle) * speed
            );
            int value = boss ? 3 + ThreadLocalRandom.current().nextInt(3) : 1;
            Location spawnAt = base.clone().add(
                (ThreadLocalRandom.current().nextDouble() - 0.5D) * 0.35D,
                0,
                (ThreadLocalRandom.current().nextDouble() - 0.5D) * 0.35D
            );
            spawnOne(spawnAt, vel, value, ownerId);
        }
        if (killer != null && killer.isOnline()) {
            killer.playSound(base, SOUND_DROP, SoundCategory.PLAYERS, 0.7F,
                0.9F + ThreadLocalRandom.current().nextFloat() * 0.3F);
        } else {
            world.playSound(base, SOUND_DROP, SoundCategory.PLAYERS, 0.55F, 1.0F);
        }
    }

    public boolean trySpend(UUID uuid, int amount) {
        if (amount <= 0) {
            return true;
        }
        int have = collected.getOrDefault(uuid, 0);
        if (have < amount) {
            return false;
        }
        collected.put(uuid, have - amount);
        return true;
    }

    public int coinMultiplier() {
        return Math.max(1, session.getCoinMultiplier());
    }

    private void spawnOne(Location loc, Vector vel, int value, UUID ownerId) {
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        int scaled = Math.max(1, value * coinMultiplier());
        ItemStack stack = coinItem();
        ItemDisplay display = world.spawn(loc, ItemDisplay.class, d -> {
            d.setItemStack(stack);
            d.setPersistent(false);
            d.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
            d.setTransformation(scale(COIN_SCALE));
            d.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
            d.addScoreboardTag("holly_dungeon_coin");
        });
        hideFromOthers(display, ownerId);
        Coin coin = new Coin(display, loc.clone(), vel, scaled, ownerId);
        snapCoinToSurface(coin);
        coins.add(coin);
    }

    /** Nie spadaj pod mape — znajdz solid pod spawna / aktualna pozycja. */
    private void snapCoinToSurface(Coin coin) {
        World world = coin.loc.getWorld();
        if (world == null) {
            return;
        }
        int x = coin.loc.getBlockX();
        int z = coin.loc.getBlockZ();
        int fromY = Math.min(world.getMaxHeight() - 1, coin.loc.getBlockY() + 2);
        int minY = world.getMinHeight();
        for (int y = fromY; y >= minY; y--) {
            Material type = world.getBlockAt(x, y, z).getType();
            if (type.isSolid()) {
                coin.loc.setX(x + 0.5D);
                coin.loc.setZ(z + 0.5D);
                coin.loc.setY(y + 1.0D + GROUND_Y_OFFSET);
                // Jesli nadal w solidzie (grubosc), wypchnij wyzej.
                while (coin.loc.getBlock().getType().isSolid() && coin.loc.getY() < world.getMaxHeight() - 2) {
                    coin.loc.add(0, 0.25D, 0);
                }
                return;
            }
        }
        // Brak podlogi — trzymaj nad minHeight.
        coin.loc.setY(minY + 2.0D + GROUND_Y_OFFSET);
        coin.velocity.setY(0.12D);
    }

    private void hideFromOthers(ItemDisplay display, UUID ownerId) {
        if (plugin == null) {
            return;
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (ownerId != null && online.getUniqueId().equals(ownerId)) {
                continue;
            }
            try {
                online.hideEntity(plugin, display);
            } catch (Throwable ignored) {
            }
        }
    }

    public void tick() {
        if (coins.isEmpty()) {
            return;
        }
        Iterator<Coin> it = coins.iterator();
        while (it.hasNext()) {
            Coin coin = it.next();
            if (coin.display == null || !coin.display.isValid()) {
                it.remove();
                continue;
            }

            // Magnet tylko w 3x3 — trzeba podejsc.
            if (coin.ownerId != null) {
                Player owner = Bukkit.getPlayer(coin.ownerId);
                if (owner != null && owner.isOnline()
                    && !session.isDeadSpectator(coin.ownerId)
                    && owner.getGameMode() != org.bukkit.GameMode.SPECTATOR
                    && owner.getWorld().equals(coin.loc.getWorld())) {
                    double distSq = owner.getLocation().distanceSquared(coin.loc);
                    double magnetSq = MAGNET_RANGE * MAGNET_RANGE;
                    if (distSq <= PICKUP_RANGE * PICKUP_RANGE) {
                        collect(owner, coin);
                        it.remove();
                        continue;
                    }
                    if (distSq <= magnetSq) {
                        Vector pull = owner.getLocation().toVector().add(new Vector(0, 0.4D, 0))
                            .subtract(coin.loc.toVector());
                        if (pull.lengthSquared() > 0.0001D) {
                            pull.normalize().multiply(0.22D);
                            coin.velocity = pull;
                            coin.grounded = false;
                        }
                    }
                }
            }

            if (!coin.grounded) {
                coin.velocity.setY(coin.velocity.getY() - 0.045D);
                World world = coin.loc.getWorld();
                if (world == null) {
                    removeCoin(coin);
                    it.remove();
                    continue;
                }
                if (coin.velocity.getY() < -0.55D) {
                    coin.velocity.setY(-0.55D);
                }
                Location probeX = coin.loc.clone().add(coin.velocity.getX(), 0.2D, 0);
                if (probeX.getBlock().getType().isSolid()) {
                    coin.velocity.setX(-coin.velocity.getX() * 0.55D);
                }
                Location probeZ = coin.loc.clone().add(0, 0.2D, coin.velocity.getZ());
                if (probeZ.getBlock().getType().isSolid()) {
                    coin.velocity.setZ(-coin.velocity.getZ() * 0.55D);
                }
                Location next = coin.loc.clone().add(coin.velocity);
                if (next.getBlock().getType().isSolid()
                    || next.clone().add(0, 0.35D, 0).getBlock().getType().isSolid()) {
                    coin.velocity.setX(-coin.velocity.getX() * 0.4D);
                    coin.velocity.setZ(-coin.velocity.getZ() * 0.4D);
                    coin.velocity.setY(Math.abs(coin.velocity.getY()) * 0.25D + 0.08D);
                    snapCoinToSurface(coin);
                } else {
                    coin.loc.add(coin.velocity);
                }
                Material feet = coin.loc.clone().subtract(0, 0.2D, 0).getBlock().getType();
                Material here = coin.loc.getBlock().getType();
                if (here.isSolid()) {
                    snapCoinToSurface(coin);
                    coin.velocity.setY(0);
                    coin.velocity.multiply(0.3D);
                } else if (feet.isSolid() && coin.velocity.getY() <= 0) {
                    coin.loc.setY(coin.loc.getBlockY() + GROUND_Y_OFFSET);
                    if (coin.loc.getBlock().getType().isSolid()) {
                        snapCoinToSurface(coin);
                    }
                    if (Math.abs(coin.velocity.getY()) > 0.18D) {
                        coin.velocity.setY(-coin.velocity.getY() * 0.28D);
                        coin.velocity.setX(coin.velocity.getX() * 0.6D);
                        coin.velocity.setZ(coin.velocity.getZ() * 0.6D);
                    } else {
                        coin.velocity.multiply(0.4D);
                        coin.velocity.setY(0);
                        if (coin.velocity.lengthSquared() < 0.002D) {
                            coin.grounded = true;
                            coin.velocity.zero();
                            snapCoinToSurface(coin);
                        }
                    }
                }
                if (coin.loc.getY() < world.getMinHeight() + 1.5D) {
                    snapCoinToSurface(coin);
                    if (coin.loc.getY() < world.getMinHeight() + 1.0D) {
                        removeCoin(coin);
                        it.remove();
                        continue;
                    }
                }
            } else {
                if (coin.loc.getBlock().getType().isSolid()
                    || !coin.loc.clone().subtract(0, 0.35D, 0).getBlock().getType().isSolid()) {
                    snapCoinToSurface(coin);
                }
                coin.bobPhase += 0.22D;
                Location bob = coin.loc.clone().add(0, Math.sin(coin.bobPhase) * 0.04D, 0);
                try {
                    coin.display.teleport(bob);
                    coin.display.setTransformation(scale(COIN_SCALE));
                } catch (Throwable ignored) {
                }
                continue;
            }

            try {
                coin.display.teleport(coin.loc);
                coin.spin += 14F;
                coin.display.setTransformation(scaleRot(COIN_SCALE, coin.spin));
            } catch (Throwable ignored) {
            }
        }
    }

    private void collect(Player player, Coin coin) {
        collected.merge(player.getUniqueId(), coin.value, Integer::sum);
        removeCoin(coin);
        // Cichy, delikatny pickup.
        player.playSound(player.getLocation(), SOUND_PICKUP, SoundCategory.PLAYERS, 0.28F, 1.35F);
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.15F, 1.6F);
        player.getWorld().spawnParticle(Particle.CRIT, coin.loc, 3, 0.08, 0.08, 0.08, 0.01);
        int total = getCoins(player.getUniqueId());
        int vault = session.grantCoinPickupVault(player, coin.value);
        String bar = "<gradient:#FFD900:#FF9B00>+" + coin.value + " coin</gradient> &8|&f Razem: &#FFD900" + total;
        if (vault > 0) {
            bar += " &8|&a+$" + vault;
        }
        dev.grzybalson.managers.MultiActionBarService.get().send(player, "dungeon-coin", bar, 22L);
    }

    public void clear() {
        for (Coin coin : coins) {
            removeCoin(coin);
        }
        coins.clear();
    }

    private void removeCoin(Coin coin) {
        if (coin.display != null) {
            try {
                coin.display.remove();
            } catch (Throwable ignored) {
            }
        }
    }

    private static ItemStack coinItem() {
        ItemStack stack = new ItemStack(Material.GOLD_NUGGET);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(COIN_CMD);
            meta.setDisplayName("Dungeon Coin");
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static Transformation scale(float s) {
        return new Transformation(
            new Vector3f(0, 0, 0),
            new AxisAngle4f(0, 0, 1, 0),
            new Vector3f(s, s, s),
            new AxisAngle4f(0, 0, 1, 0)
        );
    }

    private static Transformation scaleRot(float s, float yawDeg) {
        float rad = (float) Math.toRadians(yawDeg);
        return new Transformation(
            new Vector3f(0, 0, 0),
            new AxisAngle4f(rad, 0, 1, 0),
            new Vector3f(s, s, s),
            new AxisAngle4f(0, 0, 1, 0)
        );
    }

    private static final class Coin {
        private final ItemDisplay display;
        private final Location loc;
        private Vector velocity;
        private final int value;
        private final UUID ownerId;
        private boolean grounded;
        private float spin;
        private double bobPhase;

        private Coin(ItemDisplay display, Location loc, Vector velocity, int value, UUID ownerId) {
            this.display = display;
            this.loc = loc;
            this.velocity = velocity;
            this.value = value;
            this.ownerId = ownerId;
        }
    }
}
