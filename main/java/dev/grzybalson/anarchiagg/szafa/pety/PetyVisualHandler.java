package dev.grzybalson.anarchiagg.szafa.pety;

import dev.grzybalson.anarchiagg.szafa.gui.SzafaGUIHandler;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class PetyVisualHandler implements Listener {
    private static final long TICK_PERIOD = 4L;
    private static final int FULL_SCAN_EVERY_TICKS = 30;
    private static final int FULL_REFRESH_EVERY_TICKS = 20;
    private static final double SNAP_DISTANCE_SQ = 49.0D;
    private static final double SETTLE_DISTANCE_SQ = 0.04D;
    private static final float SETTLE_YAW_DEG = 3.0F;
    private static final double CHASE = 0.52D;
    private static final double SIDE_OFFSET = 0.50D;
    private static final double BACK_OFFSET = 1.25D;
    private static final double SPRINT_BACK_EXTRA = 0.40D;
    private static final double HEIGHT_OFFSET = 1.05D;
    private static final long SPAWN_WARN_COOLDOWN_MILLIS = 60000L;
    private static final int CLEAN_NAME_CACHE_LIMIT = 512;
    private static final Pattern TRAILING_COLOR = Pattern.compile("(?:§[0-9a-fk-orA-FK-OR])+$");
    // cleanPetDisplayName jest czysta funkcja argumentu — pelny refresh co 30 tickow nie musi jej liczyc od nowa.
    private static final Map<String, String> CLEAN_NAME_CACHE = new ConcurrentHashMap<>();

    private final JavaPlugin plugin;
    private final PetyHandler petyHandler;
    private final SzafaGUIHandler guiHandler;
    private final NamespacedKey petMarkerKey;
    private final Map<UUID, ArmorStand> petStands = new ConcurrentHashMap<>();
    private final Map<UUID, ArmorStand> nameStands = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastHelmetKey = new ConcurrentHashMap<>();
    private final Map<UUID, ItemStack> cachedHelmets = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastSentName = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastSpawnWarn = new ConcurrentHashMap<>();
    private int tickCounter;

    public PetyVisualHandler(JavaPlugin plugin, PetyHandler petyHandler, SzafaGUIHandler guiHandler) {
        this.plugin = plugin;
        this.petyHandler = petyHandler;
        this.guiHandler = guiHandler;
        this.petMarkerKey = new NamespacedKey(plugin, "szafka_pet_visual");
        removeOrphanedStands();
        startTask();
    }

    private void startTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                tickCounter++;
                boolean fullRefresh = tickCounter % FULL_REFRESH_EVERY_TICKS == 0;
                for (UUID uuid : petStands.keySet()) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player == null || !player.isOnline()) {
                        removePetStand(uuid);
                        continue;
                    }
                    if (fullRefresh) {
                        updatePlayerPet(player);
                    } else {
                        tickPetFollow(player);
                    }
                }
                if (tickCounter % FULL_SCAN_EVERY_TICKS == 0) {
                    for (UUID uuid : petyHandler.getDatabase().cachedActivePetOwners()) {
                        if (petStands.containsKey(uuid)) {
                            continue;
                        }
                        Player player = Bukkit.getPlayer(uuid);
                        if (player != null && player.isOnline()) {
                            updatePlayerPet(player);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, TICK_PERIOD, TICK_PERIOD);
    }

    private void tickPetFollow(Player player) {
        if (player == null || !player.isOnline() || player.isDead()) {
            removePetStand(player != null ? player.getUniqueId() : null);
            return;
        }
        UUID uuid = player.getUniqueId();
        ArmorStand stand = this.petStands.get(uuid);
        ArmorStand nameStand = this.nameStands.get(uuid);
        if (stand == null || !stand.isValid() || stand.isDead()) {
            updatePlayerPet(player);
            return;
        }
        Location playerLoc = player.getLocation();
        Location targetLoc = followTarget(player, playerLoc);
        if (targetLoc.getWorld() == null || !stand.getWorld().equals(targetLoc.getWorld())) {
            updatePlayerPet(player);
            return;
        }
        chaseStand(stand, targetLoc);
        if (nameStand != null && nameStand.isValid() && !nameStand.isDead()
            && nameStand.getWorld().equals(targetLoc.getWorld())) {
            chaseStand(nameStand, targetLoc.clone().add(0.0D, 0.95D, 0.0D));
        }
    }

    private Location followTarget(Player player, Location playerLoc) {
        Vector back = followBackVector(player, playerLoc);
        Vector left = new Vector(-back.getZ(), 0.0D, back.getX());
        if (left.lengthSquared() < 0.001D) {
            left = new Vector(1.0D, 0.0D, 0.0D);
        } else {
            left.normalize();
        }
        double backDist = player.isSprinting() ? BACK_OFFSET + SPRINT_BACK_EXTRA : BACK_OFFSET;
        Location targetLoc = playerLoc.clone()
                .add(back.multiply(backDist))
                .add(left.multiply(SIDE_OFFSET))
                .add(0.0D, HEIGHT_OFFSET, 0.0D);
        targetLoc.setYaw(playerLoc.getYaw());
        targetLoc.setPitch(0.0F);
        return targetLoc;
    }

    public void updatePlayerPet(Player player) {
        if (player == null || !player.isOnline() || player.isDead()) {
            removePetStand(player != null ? player.getUniqueId() : null);
            return;
        }

        UUID uuid = player.getUniqueId();
        if (this.guiHandler != null && this.guiHandler.isPetHidden(uuid)) {
            removePetStand(uuid);
            return;
        }

        String activePetKey = this.petyHandler.getDatabase().getActivePet(uuid);
        if (activePetKey == null || activePetKey.isEmpty()) {
            removePetStand(uuid);
            return;
        }

        PetyHandler.PetData data = this.petyHandler.getPetData(activePetKey);
        if (data == null) {
            removePetStand(uuid);
            return;
        }

        Location playerLoc = player.getLocation();
        Location targetLoc = followTarget(player, playerLoc);
        Location nameTargetLoc = targetLoc.clone().add(0, 0.95, 0);

        String helmetCacheKey = activePetKey + "|" + (data.color() == null ? "" : data.color());
        ItemStack helmetItem = cachedHelmets.get(uuid);
        boolean helmetDirty = helmetItem == null || !helmetCacheKey.equals(lastHelmetKey.get(uuid));
        if (helmetDirty) {
            ItemStack baseItem = this.petyHandler.getPetItem(data.itemKey());
            helmetItem = baseItem != null ? baseItem.clone() : new ItemStack(org.bukkit.Material.STONE);
            if (this.guiHandler != null && helmetItem.getType().name().startsWith("LEATHER_") && data.color() != null && !data.color().isEmpty()) {
                this.guiHandler.applyColorToLeatherArmor(helmetItem, data.color());
            }
            cachedHelmets.put(uuid, helmetItem);
            lastHelmetKey.put(uuid, helmetCacheKey);
        }

        String storedName = this.petyHandler.getDatabase().getActivePetDisplayName(uuid);
        String rawName = storedName != null ? storedName : (data.displayName() != null ? data.displayName() : "Pet");
        String displayName = cleanPetDisplayName(rawName);

        ArmorStand stand = this.petStands.get(uuid);
        if (stand != null && (!stand.isValid() || stand.isDead() || !stand.getWorld().equals(targetLoc.getWorld()))) {
            if (stand.isValid()) {
                stand.remove();
            }
            stand = null;
            this.petStands.remove(uuid);
        }

        ArmorStand nameStand = this.nameStands.get(uuid);
        if (nameStand != null && (!nameStand.isValid() || nameStand.isDead() || !nameStand.getWorld().equals(nameTargetLoc.getWorld()))) {
            if (nameStand.isValid()) {
                nameStand.remove();
            }
            nameStand = null;
            this.nameStands.remove(uuid);
        }

        if (stand == null) {
            final ItemStack finalHelmetItem = helmetItem;
            try {
                stand = targetLoc.getWorld().spawn(targetLoc, ArmorStand.class, s -> {
                    s.setInvisible(true);
                    s.setGravity(false);
                    s.setMarker(true);
                    s.setSmall(true);
                    s.setBasePlate(false);
                    s.setArms(false);
                    s.setCanPickupItems(false);
                    s.setCustomNameVisible(false);
                    s.setPersistent(false);
                    if (s.getEquipment() != null) {
                        s.getEquipment().setHelmet(finalHelmetItem);
                    }
                    s.getPersistentDataContainer().set(petMarkerKey, PersistentDataType.BYTE, (byte) 1);
                });
                this.petStands.put(uuid, stand);
            } catch (Exception exception) {
                warnSpawnFailure(uuid, player, "peta", exception);
            }
        } else {
            chaseStand(stand, targetLoc);
            if (helmetDirty && stand.getEquipment() != null) {
                stand.getEquipment().setHelmet(helmetItem);
            }
        }

        if (nameStand == null) {
            final String finalDisplayName = displayName;
            try {
                nameStand = nameTargetLoc.getWorld().spawn(nameTargetLoc, ArmorStand.class, s -> {
                    s.setInvisible(true);
                    s.setGravity(false);
                    s.setMarker(true);
                    s.setSmall(true);
                    s.setBasePlate(false);
                    s.setArms(false);
                    s.setCanPickupItems(false);
                    s.setCustomName(finalDisplayName);
                    s.setCustomNameVisible(true);
                    s.setPersistent(false);
                    s.getPersistentDataContainer().set(petMarkerKey, PersistentDataType.BYTE, (byte) 1);
                });
                this.nameStands.put(uuid, nameStand);
                lastSentName.put(uuid, displayName);
            } catch (Exception exception) {
                warnSpawnFailure(uuid, player, "nicku peta", exception);
            }
        } else {
            chaseStand(nameStand, nameTargetLoc);
            if (!displayName.equals(lastSentName.get(uuid))) {
                nameStand.setCustomName(displayName);
                lastSentName.put(uuid, displayName);
            }
        }
    }

    /** Spawn moze byc blokowany przez inny plugin — logujemy, ale rzadko, bo retry leci co 2 sekundy. */
    private void warnSpawnFailure(UUID uuid, Player player, String what, Exception exception) {
        long now = System.currentTimeMillis();
        Long last = this.lastSpawnWarn.get(uuid);
        if (last != null && now - last <= SPAWN_WARN_COOLDOWN_MILLIS) {
            return;
        }
        this.lastSpawnWarn.put(uuid, now);
        this.plugin.getLogger().log(java.util.logging.Level.WARNING,
            "[szafka] Nie udalo sie zespawnowac " + what + " dla " + player.getName(), exception);
    }

    private static Vector followBackVector(Player player, Location playerLoc) {
        Vector move = player.getVelocity().clone().setY(0.0D);
        if ((player.isSprinting() || move.lengthSquared() > 0.004D) && move.lengthSquared() > 0.0001D) {
            return move.normalize().multiply(-1.0D);
        }
        float yawRad = (float) Math.toRadians(playerLoc.getYaw());
        return new Vector(Math.sin(yawRad), 0.0D, -Math.cos(yawRad));
    }

    private static void chaseStand(ArmorStand stand, Location target) {
        Location current = stand.getLocation();
        if (current.getWorld() == null || target.getWorld() == null || current.getWorld() != target.getWorld()) {
            stand.teleport(target);
            return;
        }
        double dx = target.getX() - current.getX();
        double dy = target.getY() - current.getY();
        double dz = target.getZ() - current.getZ();
        double distSq = dx * dx + dy * dy + dz * dz;
        if (distSq > SNAP_DISTANCE_SQ) {
            stand.teleport(target);
            return;
        }
        if (distSq < SETTLE_DISTANCE_SQ) {
            float yawDelta = Math.abs(lerpYaw(current.getYaw(), target.getYaw(), 1.0F) - current.getYaw());
            if (yawDelta > SETTLE_YAW_DEG) {
                stand.setRotation(target.getYaw(), 0.0F);
            }
            return;
        }
        Location next = current.clone();
        next.setX(current.getX() + dx * CHASE);
        next.setY(current.getY() + dy * CHASE);
        next.setZ(current.getZ() + dz * CHASE);
        next.setYaw(lerpYaw(current.getYaw(), target.getYaw(), 0.28F));
        next.setPitch(0.0F);
        stand.teleport(next);
    }

    private static float lerpYaw(float from, float to, float t) {
        float delta = to - from;
        while (delta < -180.0F) {
            delta += 360.0F;
        }
        while (delta > 180.0F) {
            delta -= 360.0F;
        }
        return from + delta * t;
    }

    public static String cleanPetDisplayName(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return "Pet";
        }
        String cached = CLEAN_NAME_CACHE.get(rawName);
        if (cached != null) {
            return cached;
        }
        String cleaned = computeCleanPetDisplayName(rawName);
        // Kluczem jest nazwa z itemu gracza, wiec trzymamy limit zeby mapa nie rosla w nieskonczonosc.
        if (CLEAN_NAME_CACHE.size() >= CLEAN_NAME_CACHE_LIMIT) {
            CLEAN_NAME_CACHE.clear();
        }
        CLEAN_NAME_CACHE.put(rawName, cleaned);
        return cleaned;
    }

    private static String computeCleanPetDisplayName(String rawName) {
        String colored = dev.grzybalson.utils.ColorUtils.colorize(rawName);
        String stripped = ChatColor.stripColor(colored);
        if (stripped == null || stripped.isBlank()) {
            return "Pet";
        }
        String normalized = foldSmallCaps(stripped).toLowerCase(Locale.ROOT);
        int index = indexOfDurationMarker(normalized);
        if (index != -1) {
            colored = cutColoredAtVisualIndex(colored, index).trim();
        }
        colored = TRAILING_COLOR.matcher(colored).replaceAll("").trim();
        while (colored.endsWith(" ") || colored.endsWith("·") || colored.endsWith("-") || colored.endsWith("|") || colored.endsWith("—")) {
            colored = colored.substring(0, colored.length() - 1).trim();
        }
        return colored.isBlank() ? "Pet" : colored;
    }

    private static int indexOfDurationMarker(String normalized) {
        int naCzas = normalized.indexOf("na czas");
        int placeholder = normalized.indexOf("%czas%");
        if (naCzas < 0) {
            return placeholder;
        }
        if (placeholder < 0) {
            return naCzas;
        }
        return Math.min(naCzas, placeholder);
    }

    private static String cutColoredAtVisualIndex(String colored, int visualIndex) {
        int count = 0;
        int cutIndex = colored.length();
        for (int i = 0; i < colored.length(); i++) {
            if (colored.charAt(i) == '§' && i + 1 < colored.length()) {
                i++;
                continue;
            }
            if (count == visualIndex) {
                cutIndex = i;
                break;
            }
            count++;
        }
        return colored.substring(0, cutIndex);
    }

    private static String foldSmallCaps(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            out.append(foldSmallCap(text.charAt(i)));
        }
        return out.toString();
    }

    private static char foldSmallCap(char c) {
        return switch (c) {
            case 'ᴀ' -> 'a';
            case 'ʙ' -> 'b';
            case 'ᴄ' -> 'c';
            case 'ᴅ' -> 'd';
            case 'ᴇ' -> 'e';
            case 'ғ', 'ꜰ' -> 'f';
            case 'ɢ' -> 'g';
            case 'ʜ' -> 'h';
            case 'ɪ' -> 'i';
            case 'ᴊ' -> 'j';
            case 'ᴋ' -> 'k';
            case 'ʟ' -> 'l';
            case 'ᴍ' -> 'm';
            case 'ɴ' -> 'n';
            case 'ᴏ' -> 'o';
            case 'ᴘ' -> 'p';
            case 'ǫ', 'ꞯ' -> 'q';
            case 'ʀ' -> 'r';
            case 'ꜱ' -> 's';
            case 'ᴛ' -> 't';
            case 'ᴜ' -> 'u';
            case 'ᴠ' -> 'v';
            case 'ᴡ' -> 'w';
            case 'ʏ' -> 'y';
            case 'ᴢ' -> 'z';
            default -> c;
        };
    }

    @EventHandler
    public void onArmorStandManipulate(org.bukkit.event.player.PlayerArmorStandManipulateEvent event) {
        if (isPetArmorStand(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerInteractAtEntity(org.bukkit.event.player.PlayerInteractAtEntityEvent event) {
        if (isPetArmorStand(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerInteractEntity(org.bukkit.event.player.PlayerInteractEntityEvent event) {
        if (isPetArmorStand(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    private boolean isPetArmorStand(Entity entity) {
        return entity instanceof ArmorStand stand
            && stand.getPersistentDataContainer().has(petMarkerKey, PersistentDataType.BYTE);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        removePetStand(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        removePetStand(event.getEntity().getUniqueId());
    }

    @EventHandler
    public void onWorldChange(org.bukkit.event.player.PlayerChangedWorldEvent event) {
        removePetStand(event.getPlayer().getUniqueId());
        updatePlayerPet(event.getPlayer());
    }

    @EventHandler
    public void onTeleport(org.bukkit.event.player.PlayerTeleportEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || from.getWorld() != to.getWorld()) {
            removePetStand(event.getPlayer().getUniqueId());
            Bukkit.getScheduler().runTask(plugin, () -> updatePlayerPet(event.getPlayer()));
            return;
        }
        if (from.distanceSquared(to) < 9.0D) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> updatePlayerPet(event.getPlayer()));
    }

    public void removePetStand(UUID uuid) {
        if (uuid == null) return;
        ArmorStand stand = this.petStands.remove(uuid);
        if (stand != null && stand.isValid()) {
            stand.remove();
        }
        ArmorStand nameStand = this.nameStands.remove(uuid);
        if (nameStand != null && nameStand.isValid()) {
            nameStand.remove();
        }
        lastHelmetKey.remove(uuid);
        cachedHelmets.remove(uuid);
        lastSentName.remove(uuid);
        lastSpawnWarn.remove(uuid);
    }

    public void removeOrphanedStands() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntitiesByClass(ArmorStand.class)) {
                if (entity.getPersistentDataContainer().has(petMarkerKey, PersistentDataType.BYTE)) {
                    entity.remove();
                }
            }
        }
    }

    public void cleanupAllStands() {
        for (ArmorStand stand : this.petStands.values()) {
            if (stand != null && stand.isValid()) {
                stand.remove();
            }
        }
        this.petStands.clear();

        for (ArmorStand nameStand : this.nameStands.values()) {
            if (nameStand != null && nameStand.isValid()) {
                nameStand.remove();
            }
        }
        this.nameStands.clear();
        lastHelmetKey.clear();
        cachedHelmets.clear();
        lastSentName.clear();
        lastSpawnWarn.clear();

        removeOrphanedStands();
    }
}
