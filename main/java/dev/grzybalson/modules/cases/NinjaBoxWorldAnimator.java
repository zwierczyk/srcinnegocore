package dev.grzybalson.modules.cases;

import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * World-visible ninjabox: chest spins under centered title → burst of 8 small items → winner flies in.
 */
public final class NinjaBoxWorldAnimator {

    private static final int TOTAL_TICKS = 220;
    private static final int BURST_TICK = 85;
    private static final int ORBIT_END = 135;
    private static final int PICK_TICK = 145;
    private static final int FLY_START = 170;

    /** Small display items (were too huge before). */
    private static final float ITEM_SCALE = 0.40F;
    private static final float ITEM_WIN_SCALE = 0.58F;
    private static final float CHEST_SCALE = 0.88F;

    private final HollyCore plugin;
    private final CaseManager manager;
    private final UUID playerId;
    private final CaseOpenResult result;
    private final Location origin;
    private final AtomicBoolean finished = new AtomicBoolean(false);
    private final List<Entity> entities = new ArrayList<>();
    private final List<ItemDisplay> burstItems = new ArrayList<>();

    private BukkitTask task;
    private BlockDisplay chestDisplay;
    private ItemDisplay rewardDisplay;
    private ArmorStand nameStand;
    private int tick;
    private int winnerIndex;
    private boolean burstSpawned;
    private boolean pickAnnounced;
    /** Chest/name share this pivot so the box spins in the middle of the title. */
    private Location pivot;

    public NinjaBoxWorldAnimator(
        HollyCore plugin,
        CaseManager manager,
        Player player,
        CaseOpenResult result,
        Location placeBlock
    ) {
        this.plugin = plugin;
        this.manager = manager;
        this.playerId = player.getUniqueId();
        this.result = result;
        this.origin = placeBlock.clone().add(0.5D, 0.0D, 0.5D);
        this.pivot = this.origin.clone().add(0, 1.05D, 0);
    }

    public void start() {
        World world = origin.getWorld();
        if (world == null) {
            complete(true);
            return;
        }

        try {
            spawnAndSchedule(world);
        } catch (Throwable throwable) {
            // Spawn odrzucony (region/limiter) — nie blokuj gracza, oddaj nagrode bez animacji.
            plugin.getLogger().warning("Nie udalo sie wystartowac animacji ninjabox dla " + playerId + ": " + throwable);
            complete(true);
        }
    }

    private void spawnAndSchedule(World world) {
        Player opener = Bukkit.getPlayer(playerId);
        if (opener != null) {
            ColorUtils.sendTitle(opener,
                "<gradient:#FF3B3B:#FFD900><bold>NINJA BOX</bold></gradient></shadow>",
                "Pieczętowanie skrzyni…</shadow>",
                5, 35, 8);
        }

        playAround(Sound.BLOCK_END_PORTAL_FRAME_FILL, 1.15F, 0.55F);
        playAround(Sound.BLOCK_BEACON_ACTIVATE, 0.85F, 1.35F);
        playAround(Sound.ENTITY_EVOKER_PREPARE_SUMMON, 0.95F, 0.85F);

        // Title first — fixed above pivot. Chest spins AT pivot (center of the name).
        nameStand = world.spawn(pivot.clone().add(0, 0.95D, 0), ArmorStand.class, as -> {
            as.setVisible(false);
            as.setGravity(false);
            as.setMarker(true);
            as.setPersistent(false);
            as.setCustomNameVisible(true);
            as.customName(ColorUtils.colorizeComponent(
                "<gradient:#FFD900:#FF3B3B><bold>✦ NINJA BOX ✦</bold></gradient></shadow>"));
            as.addScoreboardTag("holly_ninjabox");
        });
        entities.add(nameStand);

        // BlockDisplay origin is corner-ish — offset so visual center = pivot while yaw-spinning.
        chestDisplay = world.spawn(chestLoc(0F), BlockDisplay.class, d -> {
            d.setBlock(Material.CHEST.createBlockData());
            d.setPersistent(false);
            d.setInterpolationDuration(0);
            d.setTransformation(chestTransform(0.02F, 0F));
            d.addScoreboardTag("holly_ninjabox");
        });
        entities.add(chestDisplay);

        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    private void tick() {
        if (finished.get()) {
            return;
        }
        World world = origin.getWorld();
        if (world == null) {
            complete(true);
            return;
        }

        tick++;
        Player opener = Bukkit.getPlayer(playerId);

        // Soft ambient ring around pivot
        if (tick % 2 == 0 && tick < BURST_TICK) {
            double a = tick * 0.4D;
            double r = 0.9D;
            Location p = pivot.clone().add(Math.cos(a) * r, -0.35D + Math.sin(tick * 0.15D) * 0.1D, Math.sin(a) * r);
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, p, 1, 0, 0, 0, 0);
            world.spawnParticle(Particle.END_ROD, p.clone().add(0, 0.25D, 0), 1, 0, 0, 0, 0);
        }

        if (tick <= 20) {
            // Materialize under title
            float s = easeOut(tick / 20.0F) * CHEST_SCALE;
            float yaw = tick * 6F;
            syncChest(s, yaw, 0.0D);
            keepTitleAbove();
            if (tick % 3 == 0) {
                playAround(Sound.BLOCK_NOTE_BLOCK_PLING, 0.45F, 0.75F + tick * 0.05F);
            }
            if (tick == 12) {
                playAround(Sound.BLOCK_CHEST_CLOSE, 0.9F, 0.8F);
            }
            world.spawnParticle(Particle.SPELL_WITCH, pivot, 4, 0.25, 0.2, 0.25, 0.01);
        } else if (tick <= 55) {
            // Rise as one unit: pivot goes up, title stays above chest center
            double rise = (tick - 20) * 0.028D;
            pivot = origin.clone().add(0, 1.05D + rise, 0);
            float yaw = tick * 10F;
            syncChest(CHEST_SCALE, yaw, 0.0D);
            keepTitleAbove();
            if (tick % 4 == 0) {
                playAround(Sound.BLOCK_NOTE_BLOCK_HAT, 0.35F, 1.05F);
            }
            if (tick == 40) {
                playAround(Sound.ENTITY_ENDER_DRAGON_FLAP, 0.65F, 1.45F);
            }
        } else if (tick < BURST_TICK) {
            // Tight spin under title (no random shake teleport — that looked broken)
            float yaw = tick * 16F;
            float pulse = CHEST_SCALE + (float) Math.sin(tick * 0.45D) * 0.04F;
            syncChest(pulse, yaw, 0.0D);
            keepTitleAbove();
            if (tick % 2 == 0) {
                playAround(Sound.UI_BUTTON_CLICK, 0.2F, 1.6F + ThreadLocalRandom.current().nextFloat() * 0.3F);
            }
            if (tick == 70) {
                playAround(Sound.BLOCK_BEACON_POWER_SELECT, 1.0F, 0.55F);
                playAround(Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.55F, 0.8F);
                broadcastNear("<gradient:#FF3B3B:#FFD900>" + (opener != null ? opener.getName() : "Gracz")
                    + "</gradient> &fodsłania &cNinja Box&f…");
            }
            if (tick == 85) {
                playAround(Sound.ENTITY_ENDER_DRAGON_GROWL, 0.75F, 1.25F);
                playAround(Sound.BLOCK_PORTAL_TRIGGER, 0.65F, 1.55F);
            }
            if (tick == 95) {
                playAround(Sound.BLOCK_CHEST_OPEN, 1.35F, 0.6F);
            }
            // Charge particles into pivot
            for (int i = 0; i < 3; i++) {
                double a = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
                double r = 1.4D;
                Location from = pivot.clone().add(Math.cos(a) * r, ThreadLocalRandom.current().nextDouble(-0.3D, 0.5D), Math.sin(a) * r);
                world.spawnParticle(Particle.CRIT, from, 0, pivot.getX() - from.getX(), pivot.getY() - from.getY(), pivot.getZ() - from.getZ(), 0.15);
            }
            world.spawnParticle(Particle.FLAME, pivot, 6, 0.25, 0.2, 0.25, 0.01);
        } else if (tick == BURST_TICK) {
            // OPEN / BURST
            playAround(Sound.ENTITY_GENERIC_EXPLODE, 1.15F, 0.85F);
            playAround(Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.9F, 1.15F);
            playAround(Sound.BLOCK_CHEST_OPEN, 1.5F, 0.5F);
            playAround(Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.1F, 0.9F);
            playAround(Sound.ENTITY_WITHER_BREAK_BLOCK, 0.7F, 0.75F);
            world.strikeLightningEffect(pivot.clone());
            world.spawnParticle(Particle.EXPLOSION_LARGE, pivot, 2, 0.1, 0.1, 0.1, 0);
            world.spawnParticle(Particle.FLASH, pivot, 1);
            world.spawnParticle(Particle.SONIC_BOOM, pivot, 1);
            world.spawnParticle(Particle.TOTEM, pivot.clone().add(0, 0.3D, 0), 55, 0.55, 0.55, 0.55, 0.2);
            world.spawnParticle(Particle.SCULK_SOUL, pivot, 25, 0.5, 0.4, 0.5, 0.05);
            spawnFirework(pivot.clone().add(0, 0.4D, 0));
            spawnFirework(pivot.clone().add(0.6D, 0.2D, -0.4D));
            removeEntity(chestDisplay);
            chestDisplay = null;
            if (nameStand != null && nameStand.isValid()) {
                nameStand.customName(ColorUtils.colorizeComponent(
                    "<gradient:#FFFFFF:#FFD900><bold>✧ LOSOWANIE ✧</bold></gradient></shadow>"));
                nameStand.teleport(pivot.clone().add(0, 1.35D, 0));
            }
            spawnBurstItems(world);
        } else if (tick < PICK_TICK && burstSpawned) {
            double t = (tick - BURST_TICK) / (double) Math.max(1, ORBIT_END - BURST_TICK);
            double expand = easeOut(Math.min(1.0D, t * 1.8D));
            double radius = 0.25D + expand * 1.55D;
            double spin = tick * 0.14D;
            double height = pivot.getY() + 0.15D;
            for (int i = 0; i < burstItems.size(); i++) {
                ItemDisplay display = burstItems.get(i);
                if (display == null || !display.isValid()) {
                    continue;
                }
                double a = spin + (Math.PI * 2.0D * i) / 8.0D;
                double bob = Math.sin(tick * 0.32D + i * 0.7D) * 0.08D;
                Location at = new Location(world, pivot.getX() + Math.cos(a) * radius, height + bob, pivot.getZ() + Math.sin(a) * radius);
                display.teleport(at);
                float scale = i == winnerIndex
                    ? ITEM_SCALE + 0.06F + (float) Math.sin(tick * 0.4D) * 0.03F
                    : ITEM_SCALE;
                display.setInterpolationDuration(2);
                display.setTransformation(scaleItem(scale, (float) Math.toDegrees(a)));
            }
            keepTitleAboveOrbit();
            if (tick % 5 == 0) {
                playAround(Sound.BLOCK_NOTE_BLOCK_CHIME, 0.4F, 0.95F + (tick % 20) * 0.03F);
            }
            if (tick % 3 == 0) {
                world.spawnParticle(Particle.ENCHANTMENT_TABLE, pivot.clone().add(0, 0.2D, 0), 10, 0.7, 0.35, 0.7, 0.4);
            }
            // Spotlight beam
            world.spawnParticle(Particle.END_ROD, pivot.clone().add(0, 0.1D, 0), 2, 0.05, 0.5, 0.05, 0.01);
        } else if (tick == PICK_TICK) {
            playAround(Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 0.7F);
            playAround(Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.05F);
            playAround(Sound.ITEM_TRIDENT_RETURN, 0.9F, 0.8F);
            playAround(Sound.BLOCK_BELL_USE, 0.7F, 1.4F);
            world.spawnParticle(Particle.TOTEM, pivot.clone().add(0, 0.5D, 0), 50, 0.4, 0.5, 0.4, 0.18);
            world.spawnParticle(Particle.GLOW, pivot.clone().add(0, 0.6D, 0), 20, 0.35, 0.35, 0.35, 0);

            for (int i = 0; i < burstItems.size(); i++) {
                ItemDisplay display = burstItems.get(i);
                if (display == null || !display.isValid()) {
                    continue;
                }
                if (i == winnerIndex) {
                    rewardDisplay = display;
                    try {
                        display.setGlowing(true);
                    } catch (Throwable ignored) {
                    }
                    display.teleport(pivot.clone().add(0, 0.55D, 0));
                    display.setTransformation(scaleItem(ITEM_WIN_SCALE, 0F));
                    continue;
                }
                Location loc = display.getLocation();
                double a = (Math.PI * 2.0D * i) / 8.0D;
                // Shatter outward in arcs
                display.teleport(loc.clone().add(Math.cos(a) * 0.9D, 0.55D, Math.sin(a) * 0.9D));
                display.setTransformation(scaleItem(0.12F, tick * 40F));
                world.spawnParticle(Particle.CRIT, loc, 10, 0.1, 0.1, 0.1, 0.08);
                world.spawnParticle(Particle.SMOKE_NORMAL, loc, 6, 0.1, 0.1, 0.1, 0.02);
                final ItemDisplay loser = display;
                final int delay = 4 + (i % 4) * 2;
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (loser.isValid()) {
                        Location l = loser.getLocation();
                        l.getWorld().spawnParticle(Particle.ASH, l, 12, 0.15, 0.15, 0.15, 0.01);
                        playAt(l, Sound.BLOCK_GLASS_BREAK, 0.45F, 1.3F);
                    }
                    removeEntity(loser);
                    burstItems.remove(loser);
                }, delay);
            }

            if (nameStand != null && nameStand.isValid()) {
                nameStand.customName(ColorUtils.colorizeComponent(
                    "<gradient:#00FF5A:#FFD900><bold>★ WYGRANA ★</bold></gradient></shadow>"));
                nameStand.teleport(pivot.clone().add(0, 1.25D, 0));
            }
            if (!pickAnnounced && opener != null) {
                pickAnnounced = true;
                ColorUtils.sendTitle(opener,
                    "<gradient:#FFD900:#00FF5A><bold>WYLOSOWANO</bold></gradient></shadow>",
                    manager.getReadableItemName(result.reward().item()) + "</shadow>",
                    5, 45, 10);
            }
        } else if (tick < FLY_START && rewardDisplay != null && rewardDisplay.isValid()) {
            double bob = Math.sin(tick * 0.38D) * 0.12D;
            rewardDisplay.teleport(pivot.clone().add(0, 0.55D + bob, 0));
            rewardDisplay.setInterpolationDuration(2);
            rewardDisplay.setTransformation(scaleItem(ITEM_WIN_SCALE + 0.05F, tick * 9F));
            if (nameStand != null && nameStand.isValid()) {
                nameStand.teleport(pivot.clone().add(0, 1.25D + bob * 0.3D, 0));
            }
            if (tick % 4 == 0) {
                playAround(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.55F, 1.25F);
            }
            // Spiral collapse into winner
            double a = tick * 0.55D;
            Location swirl = pivot.clone().add(Math.cos(a) * 0.85D, 0.3D, Math.sin(a) * 0.85D);
            world.spawnParticle(Particle.END_ROD, swirl, 1, 0, 0, 0, 0);
            world.spawnParticle(Particle.GLOW, rewardDisplay.getLocation(), 3, 0.12, 0.12, 0.12, 0);
            if (tick == FLY_START - 1) {
                playAround(Sound.ENTITY_ENDER_PEARL_THROW, 0.9F, 0.7F);
                playAround(Sound.ITEM_TRIDENT_THROW, 0.7F, 1.4F);
            }
        } else if (tick <= TOTAL_TICKS) {
            if (opener != null && opener.isOnline() && rewardDisplay != null && rewardDisplay.isValid()) {
                Location from = rewardDisplay.getLocation();
                Location to = opener.getEyeLocation();
                double pull = 0.26D;
                Location lerped = from.clone().add(
                    (to.getX() - from.getX()) * pull,
                    (to.getY() - from.getY()) * pull,
                    (to.getZ() - from.getZ()) * pull
                );
                rewardDisplay.teleport(lerped);
                float p = (tick - FLY_START) / (float) Math.max(1, TOTAL_TICKS - FLY_START);
                rewardDisplay.setTransformation(scaleItem(Math.max(0.22F, ITEM_WIN_SCALE - p * 0.25F), tick * 16F));
                world.spawnParticle(Particle.CRIT, lerped, 3, 0.04, 0.04, 0.04, 0.01);
                world.spawnParticle(Particle.FIREWORKS_SPARK, lerped, 2, 0.03, 0.03, 0.03, 0.01);
                if (tick % 2 == 0) {
                    playAround(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5F, 1.25F + p * 0.4F);
                }
            }
            if (tick == TOTAL_TICKS) {
                playAround(Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.15F);
                playAround(Sound.BLOCK_NOTE_BLOCK_BELL, 1.0F, 1.05F);
                if (opener != null) {
                    opener.getWorld().spawnParticle(Particle.TOTEM, opener.getLocation().add(0, 1.2D, 0), 25, 0.35, 0.5, 0.35, 0.1);
                }
                complete(true);
            }
        }
    }

    /** Chest visual center locked to pivot; yaw spin only (no wild teleport jitter). */
    private void syncChest(float scale, float yawDeg, double yOffset) {
        if (chestDisplay == null || !chestDisplay.isValid()) {
            return;
        }
        Location at = chestLoc(yawDeg).add(0, yOffset, 0);
        chestDisplay.teleport(at);
        chestDisplay.setInterpolationDuration(1);
        chestDisplay.setTransformation(chestTransform(scale, yawDeg));
    }

    private Location chestLoc(float yawDeg) {
        // Keep entity at pivot; transformation centers the block model on pivot.
        return pivot.clone();
    }

    private void keepTitleAbove() {
        if (nameStand != null && nameStand.isValid()) {
            nameStand.teleport(pivot.clone().add(0, 0.95D, 0));
        }
    }

    private void keepTitleAboveOrbit() {
        if (nameStand != null && nameStand.isValid()) {
            nameStand.teleport(pivot.clone().add(0, 1.35D, 0));
        }
    }

    private void spawnBurstItems(World world) {
        if (burstSpawned) {
            return;
        }
        burstSpawned = true;
        winnerIndex = ThreadLocalRandom.current().nextInt(8);
        List<ItemStack> fillers = buildFillerItems(7);
        ItemStack prize = result.reward().item().clone();
        prize.setAmount(1);

        Location center = pivot.clone();
        for (int i = 0; i < 8; i++) {
            ItemStack stack = (i == winnerIndex) ? prize.clone() : fillers.get(i < winnerIndex ? i : i - 1);
            double a = (Math.PI * 2.0D * i) / 8.0D;
            Location spawnAt = center.clone().add(Math.cos(a) * 0.15D, 0.05D, Math.sin(a) * 0.15D);
            ItemDisplay display = world.spawn(spawnAt, ItemDisplay.class, d -> {
                d.setItemStack(stack);
                d.setPersistent(false);
                d.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
                d.setTransformation(scaleItem(0.08F, 0F));
                d.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
                d.addScoreboardTag("holly_ninjabox");
                d.addScoreboardTag("holly_ninjabox_item");
            });
            burstItems.add(display);
            entities.add(display);
            playAround(Sound.ENTITY_ITEM_PICKUP, 0.55F, 0.9F + i * 0.06F);
        }
        playAround(Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.95F, 1.15F);
        playAround(Sound.BLOCK_AMETHYST_BLOCK_BREAK, 0.8F, 0.7F);
    }

    private List<ItemStack> buildFillerItems(int need) {
        ItemStack winner = result.reward().item().clone();
        winner.setAmount(1);

        List<ItemStack> fillers = new ArrayList<>();
        List<CaseReward> rewards = result.definition().rewards();
        if (rewards != null) {
            for (CaseReward reward : rewards) {
                if (reward == null || reward.item() == null || reward.item().getType().isAir()) {
                    continue;
                }
                ItemStack copy = reward.item().clone();
                copy.setAmount(1);
                if (sameLook(copy, winner)) {
                    continue;
                }
                boolean dup = false;
                for (ItemStack existing : fillers) {
                    if (sameLook(existing, copy)) {
                        dup = true;
                        break;
                    }
                }
                if (!dup) {
                    fillers.add(copy);
                }
            }
        }
        Collections.shuffle(fillers, ThreadLocalRandom.current());

        Material[] fallbacks = {
            Material.DIAMOND, Material.EMERALD, Material.NETHERITE_INGOT, Material.GOLDEN_APPLE,
            Material.ENDER_PEARL, Material.BLAZE_ROD, Material.TOTEM_OF_UNDYING, Material.ENCHANTED_GOLDEN_APPLE,
            Material.TRIDENT, Material.NETHER_STAR
        };
        int fb = 0;
        while (fillers.size() < need) {
            ItemStack fbItem = new ItemStack(fallbacks[fb % fallbacks.length]);
            fb++;
            boolean dup = sameLook(fbItem, winner);
            for (ItemStack existing : fillers) {
                if (sameLook(existing, fbItem)) {
                    dup = true;
                    break;
                }
            }
            if (!dup) {
                fillers.add(fbItem);
            }
        }
        return new ArrayList<>(fillers.subList(0, need));
    }

    private static boolean sameLook(ItemStack a, ItemStack b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.getType() != b.getType()) {
            return false;
        }
        try {
            return a.isSimilar(b);
        } catch (Throwable ignored) {
            return true;
        }
    }

    private static float easeOut(float t) {
        float x = Math.max(0F, Math.min(1F, t));
        return 1F - (1F - x) * (1F - x);
    }

    private static double easeOut(double t) {
        double x = Math.max(0D, Math.min(1D, t));
        return 1D - (1D - x) * (1D - x);
    }

    public void cancelAndReward() {
        complete(true);
    }

    public void cancelDrop() {
        complete(false);
    }

    private void complete(boolean giveToPlayer) {
        if (!finished.compareAndSet(false, true)) {
            return;
        }
        if (task != null) {
            task.cancel();
            task = null;
        }
        cleanupEntities();
        Player player = Bukkit.getPlayer(playerId);
        manager.onNinjaBoxAnimationFinished(playerId, result, player, origin, giveToPlayer);
    }

    private void cleanupEntities() {
        for (Entity entity : entities) {
            removeEntity(entity);
        }
        entities.clear();
        burstItems.clear();
        chestDisplay = null;
        rewardDisplay = null;
        nameStand = null;
        World world = origin.getWorld();
        if (world != null) {
            for (Entity nearby : world.getNearbyEntities(origin, 10, 10, 10)) {
                if (nearby.getScoreboardTags().contains("holly_ninjabox")) {
                    removeEntity(nearby);
                }
            }
        }
    }

    private void removeEntity(Entity entity) {
        if (entity == null) {
            return;
        }
        try {
            entity.remove();
        } catch (Throwable ignored) {
        }
    }

    private void playAround(Sound sound, float volume, float pitch) {
        World world = origin.getWorld();
        if (world == null) {
            return;
        }
        world.playSound(pivot != null ? pivot : origin, sound, SoundCategory.MASTER, volume, pitch);
    }

    private void playAt(Location loc, Sound sound, float volume, float pitch) {
        if (loc == null || loc.getWorld() == null) {
            return;
        }
        loc.getWorld().playSound(loc, sound, SoundCategory.MASTER, volume, pitch);
    }

    private void broadcastNear(String message) {
        World world = origin.getWorld();
        if (world == null) {
            return;
        }
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(origin) <= 48 * 48) {
                player.sendMessage(ColorUtils.colorizeComponent(message));
            }
        }
    }

    private void spawnFirework(Location loc) {
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        Firework fw = world.spawn(loc, Firework.class, f -> {
            FireworkMeta meta = f.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder()
                .with(FireworkEffect.Type.BALL_LARGE)
                .withColor(Color.RED, Color.ORANGE, Color.YELLOW, Color.FUCHSIA)
                .withFade(Color.BLACK, Color.MAROON)
                .flicker(true)
                .trail(true)
                .build());
            meta.setPower(0);
            f.setFireworkMeta(meta);
            f.addScoreboardTag("holly_ninjabox");
            f.addScoreboardTag("holly_ninjabox_fx");
        });
        entities.add(fw);
        Bukkit.getScheduler().runTaskLater(plugin, fw::detonate, 1L);
    }

    public Location getOrigin() {
        return origin.clone();
    }

    public boolean isNear(Location loc, double radius) {
        if (loc == null || origin.getWorld() == null || loc.getWorld() == null) {
            return false;
        }
        if (!origin.getWorld().equals(loc.getWorld())) {
            return false;
        }
        double r = Math.max(1.0D, radius);
        return origin.distanceSquared(loc) <= r * r;
    }

    /** Centers chest block model on pivot while rotating around Y. */
    private static Transformation chestTransform(float scale, float yawDeg) {
        float rad = (float) Math.toRadians(yawDeg);
        return new Transformation(
            new Vector3f(-0.5F * scale, -0.5F * scale, -0.5F * scale),
            new AxisAngle4f(rad, 0F, 1F, 0F),
            new Vector3f(scale, scale, scale),
            new AxisAngle4f(0F, 0F, 1F, 0F)
        );
    }

    private static Transformation scaleItem(float scale, float yawDeg) {
        float rad = (float) Math.toRadians(yawDeg);
        return new Transformation(
            new Vector3f(0F, 0F, 0F),
            new AxisAngle4f(rad, 0F, 1F, 0F),
            new Vector3f(scale, scale, scale),
            new AxisAngle4f(0F, 0F, 1F, 0F)
        );
    }

    public static boolean isNinjaBoxCase(CaseDefinition definition) {
        if (definition == null || definition.name() == null) {
            return false;
        }
        String n = definition.name().trim().toLowerCase(java.util.Locale.ROOT);
        return n.equals("ninjabox") || n.equals("ninja_box") || n.equals("ninja-box");
    }
}
