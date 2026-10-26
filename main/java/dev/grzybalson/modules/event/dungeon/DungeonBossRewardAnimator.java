package dev.grzybalson.modules.event.dungeon;

import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Lekka animacja losowania przy bossie: mniej entity, rzadziej teleporty, mniej cząsteczek.
 * Picks wracają callbackiem; grant inventory może być odroczony do końca dungeona.
 */
public final class DungeonBossRewardAnimator {

    /** Ticki schedulera (co 2 ticki gry) — ~4s zamiast ~6.5s na 1-ticku. */
    private static final int TOTAL_STEPS = 40;
    private static final int BURST_STEP = 1;
    private static final int ORBIT_END = 22;
    private static final int PICK_STEP = 24;
    private static final int SHOWCASE_END = 36;

    private static final float ITEM_SCALE = 0.40F;
    private static final float ITEM_WIN_SCALE = 0.58F;
    private static final int ORBIT_COUNT = 4;
    private static final int MOVE_EVERY = 2;
    private static final int INTERP_TICKS = 4;

    private final HollyCore plugin;
    private final Location origin;
    private final List<ItemStack> pool;
    private final int pickCount;
    private final List<UUID> participantIds;
    private final Consumer<List<ItemStack>> onPicked;
    private final Runnable onDone;
    private final AtomicBoolean finished = new AtomicBoolean(false);
    private final List<Entity> entities = new ArrayList<>();
    private final List<ItemDisplay> orbitItems = new ArrayList<>();
    private final List<ItemDisplay> winnerDisplays = new ArrayList<>();
    private final List<ItemStack> winners = new ArrayList<>();

    private BukkitTask task;
    private ArmorStand nameStand;
    private Location pivot;
    private int step;
    private boolean burstSpawned;
    private boolean granted;

    public DungeonBossRewardAnimator(
        HollyCore plugin,
        Location origin,
        List<ItemStack> pool,
        List<Player> participants,
        Consumer<List<ItemStack>> onPicked,
        Runnable onDone
    ) {
        this(plugin, origin, pool, 3, participants, onPicked, onDone);
    }

    public DungeonBossRewardAnimator(
        HollyCore plugin,
        Location origin,
        List<ItemStack> pool,
        int pickCount,
        List<Player> participants,
        Consumer<List<ItemStack>> onPicked,
        Runnable onDone
    ) {
        this.plugin = plugin;
        this.origin = origin.clone().add(0.0D, 0.2D, 0.0D);
        this.pivot = this.origin.clone().add(0.0D, 1.15D, 0.0D);
        this.pickCount = Math.max(1, pickCount);
        this.pool = new ArrayList<>();
        if (pool != null) {
            int kept = 0;
            for (ItemStack stack : pool) {
                if (stack != null && stack.getType() != Material.AIR && stack.getAmount() > 0) {
                    this.pool.add(new ItemStack(stack.getType()));
                    kept++;
                    if (kept >= ORBIT_COUNT) {
                        break;
                    }
                }
            }
        }
        this.participantIds = new ArrayList<>();
        if (participants != null) {
            for (Player player : participants) {
                if (player != null) {
                    this.participantIds.add(player.getUniqueId());
                }
            }
        }
        this.onPicked = onPicked;
        this.onDone = onDone;
    }

    public void start() {
        World world = origin.getWorld();
        if (world == null || pool.isEmpty()) {
            complete();
            return;
        }

        pickWinners();

        for (UUID uuid : participantIds) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                ColorUtils.sendTitle(player,
                    "<gradient:#FFD900:#00FFC1><bold>NAGRODA BOSSA</bold></gradient></shadow>",
                    "Losowanie przedmiotow...</shadow>",
                    5, 35, 8);
            }
        }

        playAround(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.85F, 1.1F);
        playAround(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7F, 0.8F);

        nameStand = world.spawn(pivot.clone().add(0, 1.15D, 0), ArmorStand.class, as -> {
            as.setVisible(false);
            as.setGravity(false);
            as.setMarker(true);
            as.setCustomNameVisible(true);
            as.customName(ColorUtils.colorizeComponent(
                "<gradient:#FFD900:#00FFC1><bold>✦ LOSOWANIE ✦</bold></gradient></shadow>"));
            as.addScoreboardTag("holly_dungeon_boss_reward");
        });
        entities.add(nameStand);

        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 2L);
    }

    private void pickWinners() {
        winners.clear();
        if (pool.isEmpty()) {
            return;
        }
        List<ItemStack> bag = new ArrayList<>();
        for (ItemStack stack : pool) {
            if (stack != null && stack.getType() != Material.AIR && stack.getAmount() > 0) {
                bag.add(stack.clone());
            }
        }
        if (bag.isEmpty()) {
            return;
        }
        int take = Math.max(1, Math.min(pickCount, bag.size()));
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int i = 0; i < take && !bag.isEmpty(); i++) {
            int idx = rng.nextInt(bag.size());
            ItemStack picked = bag.remove(idx).clone();
            winners.add(picked);
            if (i + 1 < take) {
                bag.removeIf(s -> sameLook(s, picked));
            }
        }
        if (plugin != null) {
            StringBuilder names = new StringBuilder();
            for (int i = 0; i < winners.size(); i++) {
                if (i > 0) {
                    names.append(", ");
                }
                names.append(readableName(winners.get(i)));
            }
            plugin.getLogger().info("[Dungeon] Boss reward roll pool=" + pool.size()
                + " pick=" + winners.size() + " -> " + names);
        }
    }

    private void tick() {
        if (finished.get()) {
            return;
        }
        World world = origin.getWorld();
        if (world == null) {
            complete();
            return;
        }

        step++;

        if (step == BURST_STEP) {
            playAround(Sound.ENTITY_ITEM_PICKUP, 0.85F, 1.05F);
            playAround(Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7F, 1.2F);
            world.spawnParticle(Particle.END_ROD, pivot.clone().add(0, 0.2D, 0), 3, 0.2, 0.15, 0.2, 0.01);
            if (nameStand != null && nameStand.isValid()) {
                nameStand.customName(ColorUtils.colorizeComponent(
                    "<gradient:#FFFFFF:#FFD900><bold>✧ LOSOWANIE ✧</bold></gradient></shadow>"));
            }
            spawnOrbitItems(world);
        } else if (step < PICK_STEP && burstSpawned) {
            if (step % MOVE_EVERY == 0) {
                double t = (step - BURST_STEP) / (double) Math.max(1, ORBIT_END - BURST_STEP);
                double expand = easeOut(Math.min(1.0D, t * 1.6D));
                double radius = 0.3D + expand * 1.55D;
                double spin = step * 0.28D;
                int count = Math.max(1, orbitItems.size());
                for (int i = 0; i < orbitItems.size(); i++) {
                    ItemDisplay display = orbitItems.get(i);
                    if (display == null || !display.isValid()) {
                        continue;
                    }
                    double a = spin + (Math.PI * 2.0D * i) / count;
                    Location at = new Location(world,
                        pivot.getX() + Math.cos(a) * radius,
                        pivot.getY(),
                        pivot.getZ() + Math.sin(a) * radius);
                    display.teleport(at);
                    display.setInterpolationDuration(INTERP_TICKS);
                    display.setTransformation(scaleItem(ITEM_SCALE, (float) Math.toDegrees(a)));
                }
            }
            if (step % 8 == 0) {
                playAround(Sound.BLOCK_NOTE_BLOCK_CHIME, 0.28F, 0.95F + (step % 16) * 0.03F);
            }
        } else if (step == PICK_STEP) {
            announcePick(world);
        } else if (step < SHOWCASE_END) {
            if (step % MOVE_EVERY == 0) {
                for (int i = 0; i < winnerDisplays.size(); i++) {
                    ItemDisplay display = winnerDisplays.get(i);
                    if (display == null || !display.isValid()) {
                        continue;
                    }
                    double offsetX = (i - (winnerDisplays.size() - 1) / 2.0D) * 0.85D;
                    display.teleport(pivot.clone().add(offsetX, 0.4D, 0));
                    display.setInterpolationDuration(INTERP_TICKS);
                    display.setTransformation(scaleItem(ITEM_WIN_SCALE, step * 12F));
                }
            }
            if (step == PICK_STEP + 2 && !granted) {
                granted = true;
                if (onPicked != null) {
                    List<ItemStack> clones = new ArrayList<>();
                    for (ItemStack winner : winners) {
                        clones.add(winner.clone());
                    }
                    onPicked.accept(clones);
                }
            }
        } else if (step >= TOTAL_STEPS) {
            playAround(Sound.ENTITY_PLAYER_LEVELUP, 0.85F, 1.15F);
            complete();
        }
    }

    private void announcePick(World world) {
        playAround(Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 0.75F);
        playAround(Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.85F, 1.05F);
        world.spawnParticle(Particle.GLOW, pivot.clone().add(0, 0.55D, 0), 4, 0.25, 0.2, 0.25, 0);

        List<Integer> orbitOrder = new ArrayList<>();
        for (int i = 0; i < orbitItems.size(); i++) {
            orbitOrder.add(i);
        }
        Collections.shuffle(orbitOrder, ThreadLocalRandom.current());
        java.util.Set<Integer> keepSlots = new java.util.HashSet<>();
        for (int i = 0; i < winners.size() && i < orbitOrder.size(); i++) {
            keepSlots.add(orbitOrder.get(i));
        }
        int winnerSlot = 0;
        for (int i = 0; i < orbitItems.size(); i++) {
            ItemDisplay display = orbitItems.get(i);
            if (display == null || !display.isValid()) {
                continue;
            }
            boolean keep = keepSlots.contains(i) && winnerSlot < winners.size();
            if (keep) {
                try {
                    display.setGlowing(true);
                } catch (Throwable ignored) {
                }
                display.setItemStack(winners.get(winnerSlot).clone());
                winnerDisplays.add(display);
                double offsetX = (winnerSlot - (winners.size() - 1) / 2.0D) * 0.85D;
                winnerSlot++;
                display.teleport(pivot.clone().add(offsetX, 0.4D, 0));
                display.setTransformation(scaleItem(ITEM_WIN_SCALE, 0F));
                continue;
            }
            removeEntity(display);
        }

        if (nameStand != null && nameStand.isValid()) {
            nameStand.customName(ColorUtils.colorizeComponent(
                "<gradient:#00FF5A:#FFD900><bold>★ WYLOSOWANO ★</bold></gradient></shadow>"));
        }

        for (UUID uuid : participantIds) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                ColorUtils.sendTitle(player,
                    "<gradient:#FFD900:#00FF5A><bold>WYLOSOWANO</bold></gradient></shadow>",
                    "Twoja nagroda → &e/odbierz</shadow>",
                    5, 40, 10);
                player.sendMessage(ColorUtils.colorizeComponent(
                    "&#00FF3B✔ &8Ξ &#4EFA76Losowanie osobiste — &fnagrody roznia sie per gracz &7(/odbierz)"
                ));
            }
        }
    }

    private void spawnOrbitItems(World world) {
        if (burstSpawned) {
            return;
        }
        burstSpawned = true;
        List<ItemStack> displayStacks = buildOrbitStacks();
        int count = Math.min(ORBIT_COUNT, Math.max(1, displayStacks.size()));
        for (int i = 0; i < count; i++) {
            ItemStack stack = displayStacks.get(i % displayStacks.size()).clone();
            double a = (Math.PI * 2.0D * i) / count;
            Location spawnAt = pivot.clone().add(Math.cos(a) * 0.25D, 0.05D, Math.sin(a) * 0.25D);
            ItemDisplay display = world.spawn(spawnAt, ItemDisplay.class, d -> {
                d.setItemStack(stack);
                d.setPersistent(false);
                d.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
                d.setTransformation(scaleItem(0.12F, 0F));
                d.setInterpolationDuration(INTERP_TICKS);
                d.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
                d.addScoreboardTag("holly_dungeon_boss_reward");
                d.addScoreboardTag("holly_dungeon_boss_reward_item");
            });
            orbitItems.add(display);
            entities.add(display);
        }
        playAround(Sound.ENTITY_ITEM_PICKUP, 0.55F, 1.0F);
    }

    private List<ItemStack> buildOrbitStacks() {
        List<ItemStack> stacks = new ArrayList<>();
        for (ItemStack winner : winners) {
            stacks.add(winner.clone());
        }
        List<ItemStack> fillers = new ArrayList<>();
        for (ItemStack stack : pool) {
            boolean isWinner = false;
            for (ItemStack winner : winners) {
                if (sameLook(stack, winner)) {
                    isWinner = true;
                    break;
                }
            }
            if (isWinner) {
                continue;
            }
            boolean dup = false;
            for (ItemStack existing : fillers) {
                if (sameLook(existing, stack)) {
                    dup = true;
                    break;
                }
            }
            if (!dup) {
                fillers.add(stack.clone());
            }
        }
        Collections.shuffle(fillers, ThreadLocalRandom.current());
        for (ItemStack filler : fillers) {
            if (stacks.size() >= ORBIT_COUNT) {
                break;
            }
            stacks.add(filler);
        }
        Material[] fallbacks = {
            Material.DIAMOND, Material.EMERALD, Material.NETHERITE_INGOT, Material.GOLDEN_APPLE
        };
        int fb = 0;
        while (stacks.size() < ORBIT_COUNT) {
            stacks.add(new ItemStack(fallbacks[fb % fallbacks.length]));
            fb++;
        }
        Collections.shuffle(stacks, ThreadLocalRandom.current());
        if (!winners.isEmpty() && !stacks.isEmpty()) {
            List<Integer> slots = new ArrayList<>();
            for (int i = 0; i < stacks.size(); i++) {
                slots.add(i);
            }
            Collections.shuffle(slots, ThreadLocalRandom.current());
            for (int i = 0; i < winners.size() && i < slots.size(); i++) {
                stacks.set(slots.get(i), winners.get(i).clone());
            }
        }
        return stacks;
    }

    private void complete() {
        if (!finished.compareAndSet(false, true)) {
            return;
        }
        if (!granted && !winners.isEmpty() && onPicked != null) {
            granted = true;
            List<ItemStack> clones = new ArrayList<>();
            for (ItemStack winner : winners) {
                clones.add(winner.clone());
            }
            onPicked.accept(clones);
        }
        if (task != null) {
            task.cancel();
            task = null;
        }
        cleanupEntities();
        if (onDone != null) {
            onDone.run();
        }
    }

    private void cleanupEntities() {
        for (Entity entity : entities) {
            removeEntity(entity);
        }
        entities.clear();
        orbitItems.clear();
        winnerDisplays.clear();
        nameStand = null;
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

    private static Transformation scaleItem(float scale, float yawDeg) {
        float rad = (float) Math.toRadians(yawDeg);
        return new Transformation(
            new Vector3f(0F, 0F, 0F),
            new AxisAngle4f(rad, 0F, 1F, 0F),
            new Vector3f(scale, scale, scale),
            new AxisAngle4f(0F, 0F, 1F, 0F)
        );
    }

    private static double easeOut(double t) {
        double x = Math.max(0D, Math.min(1D, t));
        return 1D - (1D - x) * (1D - x);
    }

    private static boolean sameLook(ItemStack a, ItemStack b) {
        return a != null && b != null && a.getType() == b.getType();
    }

    private static String readableName(ItemStack stack) {
        if (stack == null) {
            return "?";
        }
        try {
            if (stack.hasItemMeta() && stack.getItemMeta() != null && stack.getItemMeta().hasDisplayName()) {
                var display = stack.getItemMeta().displayName();
                if (display != null) {
                    String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                        .serialize(display);
                    if (plain != null && !plain.isBlank()) {
                        return plain;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        String name = stack.getType().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        if (name.isEmpty()) {
            return stack.getType().name();
        }
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    /** Drop leftovers into world if inventory is full. */
    public static void giveItems(Player player, List<ItemStack> items) {
        if (player == null || !player.isOnline() || items == null) {
            return;
        }
        for (ItemStack item : items) {
            if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0) {
                continue;
            }
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(item.clone());
            for (ItemStack drop : leftover.values()) {
                if (drop != null && drop.getType() != Material.AIR) {
                    player.getWorld().dropItemNaturally(player.getLocation(), drop);
                }
            }
        }
        player.updateInventory();
    }
}
