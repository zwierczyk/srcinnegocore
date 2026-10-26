package dev.grzybalson.anarchiagg.customitems.items;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import de.oliver.fancynpcs.api.FancyNpcsPlugin;
import de.oliver.fancynpcs.api.Npc;
import de.oliver.fancynpcs.api.NpcData;
import de.oliver.fancynpcs.api.skins.SkinData;
import de.oliver.fancynpcs.api.utils.NpcEquipmentSlot;
import dev.grzybalson.HollyCore;
import dev.grzybalson.anarchiagg.AnarchiaGGSettings;

import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import dev.grzybalson.anarchiagg.customitems.handlers.RegionHandler;
import dev.grzybalson.anarchiagg.customitems.util.EventItemRegistry;
import dev.grzybalson.anarchiagg.customitems.util.EventItemUseGuard;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RozdzkaIluzjonisty implements Listener {
    private static final String CONFIG_PATH = "eventowki.rozdzkailuzjonisty.";
    private static JavaPlugin pluginInstance;
    private final JavaPlugin plugin;

    private final RegionHandler regionHandler;
    private final String rodName;
    private final int time;
    private final int lpmCooldown;
    private final int ppmCooldown;
    private final double damage;
    private final Material rodMaterial;
    private final double illusionSpeed;
    private final Set<String> blockedRegions = new HashSet<>();

    public final static Map<UUID, Long> lpmCooldowns = new HashMap<>();
    public final static Map<UUID, Long> ppmCooldowns = new HashMap<>();
    private final static Map<UUID, IllusionNpc> activeIllusions = new ConcurrentHashMap<>();
    private static final double NPC_SPAWN_FORWARD_OFFSET = 0.75D;
    private static final long HIDE_SOURCE_DELAY_TICKS = 1L;

    public RozdzkaIluzjonisty(JavaPlugin plugin, RegionHandler regionHandler) {
        this.plugin = plugin;
        pluginInstance = plugin;
        this.regionHandler = regionHandler;
        YamlConfiguration config = AnarchiaGGSettings.getConfig(plugin);
        ensureConfigDefaults(config);

        List<String> regionList = config.getStringList("blocked-regions");
        if (regionList.isEmpty() && !config.isSet("blocked-regions")) {
            config.set("blocked-regions", List.of("spawn"));
            this.blockedRegions.add("spawn");
        } else {
            this.blockedRegions.addAll(regionList);
        }

        this.rodName = translate(config.getString("eventowki.rozdzkailuzjonisty.name", "&5&lRóżdżka Iluzjonisty"));
        String matName = config.getString(CONFIG_PATH + "material", "GOLDEN_HOE").toUpperCase();
        Material matchedMaterial = Material.matchMaterial(matName);
        this.rodMaterial = matchedMaterial != null ? matchedMaterial : Material.GOLDEN_HOE;

        this.lpmCooldown = getInt(config, 30, "cooldown_lpm", "cooldown-lpm", "lpm-cooldown");
        this.time = getInt(config, 6, "time");
        this.ppmCooldown = getInt(config, 60, "cooldown_ppm", "cooldown-ppm", "ppm-cooldown");
        this.damage = getDouble(config, 30.0, "damage");
        this.illusionSpeed = getDouble(config, 0.215D, "illusion-speed");
    }

    private void ensureConfigDefaults(YamlConfiguration config) {
        boolean changed = false;
        changed |= setDefault(config, CONFIG_PATH + "name", "&5&lRozdzka iluzjonisty");
        changed |= setDefault(config, CONFIG_PATH + "material", "GOLDEN_HOE");
        changed |= setDefault(config, CONFIG_PATH + "cooldown_lpm", 30);
        changed |= setDefault(config, CONFIG_PATH + "cooldown_ppm", 60);
        changed |= setDefault(config, CONFIG_PATH + "damage", 30.0);
        changed |= setDefault(config, CONFIG_PATH + "time", 6);
        changed |= setDefault(config, CONFIG_PATH + "illusion-speed", 0.215D);

        if (changed) {
            AnarchiaGGSettings.save(plugin, config);
        }
    }

    private boolean setDefault(FileConfiguration config, String path, Object value) {
        if (config.isSet(path)) {
            return false;
        }
        config.set(path, value);
        return true;
    }

    private int getInt(FileConfiguration config, int defaultValue, String... keys) {
        for (String key : keys) {
            String path = CONFIG_PATH + key;
            if (config.isSet(path)) {
                return config.getInt(path, defaultValue);
            }
        }
        return defaultValue;
    }

    private double getDouble(FileConfiguration config, double defaultValue, String... keys) {
        for (String key : keys) {
            String path = CONFIG_PATH + key;
            if (config.isSet(path)) {
                return config.getDouble(path, defaultValue);
            }
        }
        return defaultValue;
    }


    public static void resetCooldowns(UUID playerUuid) {
        lpmCooldowns.remove(playerUuid);
        ppmCooldowns.remove(playerUuid);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null || item.getType() == Material.AIR) return;
        if (!EventItemRegistry.matches(plugin, "rozdzkailuzjonisty", item)) return;
        if (!EventItemUseGuard.tryUse(plugin, player)) return;

        event.setCancelled(true);

        if (isLocationBlocked(player.getLocation())) return;

        if (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {
            handleLPM(player);
        } else if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            handlePPM(player, item);
        }
    }

    private void handleLPM(Player player) {
        if (checkCooldown(player, lpmCooldowns, lpmCooldown, "LPM")) {
            Location loc = player.getLocation();
            Vector dir = loc.getDirection().setY(0).normalize();
            Vector sideDir = new Vector(-dir.getZ(), 0, dir.getX()).normalize();

            player.playSound(player.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 1f, 1f);
            dev.grzybalson.utils.ColorUtils.sendTitle(player, "", "§aSzczęki Evokera §7zostały §aaktywowane§7!");

            new BukkitRunnable() {
                int i = 1;
                @Override
                public void run() {
                    if (i > 5) {
                        this.cancel();
                        return;
                    }
                    Location center = loc.clone().add(dir.clone().multiply(i * 2));
                    Vector sideOffset = sideDir.clone().multiply(2.0);
                    spawnFangsAtGround(center, player);
                    spawnFangsAtGround(center.clone().add(sideOffset), player);
                    spawnFangsAtGround(center.clone().subtract(sideOffset), player);
                    i++;
                }
            }.runTaskTimer(plugin, 0L, 3L);
            player.playSound(player.getLocation(), Sound.ENTITY_EVOKER_FANGS_ATTACK, 1f, 1f);
        }
    }

    private void spawnFangsAtGround(Location loc, Player owner) {
        Location spawnLoc = new Location(loc.getWorld(), Math.floor(loc.getX()) + 0.5, loc.getY(), Math.floor(loc.getZ()) + 0.5);
        if (isLocationBlocked(loc)) return;

        EvokerFangs fangs = (EvokerFangs) loc.getWorld().spawnEntity(spawnLoc, EntityType.EVOKER_FANGS);
        fangs.setOwner(owner);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (fangs.isDead()) return;
                for (Entity entity : fangs.getNearbyEntities(1.5, 1.5, 1.5)) {
                    if (entity instanceof Player target && !target.getUniqueId().equals(owner.getUniqueId())) {
                        if (target.hasMetadata("fangs_cooldown")) {
                            long lastDamage = target.getMetadata("fangs_cooldown").get(0).asLong();
                            if (System.currentTimeMillis() - lastDamage < 500) continue;
                        }
                        target.setMetadata("fangs_cooldown", new FixedMetadataValue(plugin, System.currentTimeMillis()));
                        target.setNoDamageTicks(0);

                        double currentDamage = (double) damage;
                        double absorption = target.getAbsorptionAmount();

                        if (absorption > 0) {
                            if (absorption >= currentDamage) {
                                target.setAbsorptionAmount(absorption - currentDamage);
                                currentDamage = 0;
                            } else {
                                currentDamage -= absorption;
                                target.setAbsorptionAmount(0);
                            }
                        }

                        if (currentDamage > 0) {
                            double currentHealth = target.getHealth();
                            if (currentDamage >= currentHealth) {
                                target.setHealth(0.5);
                            } else {
                                target.setHealth(currentHealth - currentDamage);
                            }
                        }
                        target.damage(0.01, owner);
                    }
                }
            }
        }.runTaskLater(plugin, 10L);
    }

    private void handlePPM(Player player, ItemStack usedItem) {
        if (!isFancyNpcsReady()) {
            dev.grzybalson.utils.ColorUtils.sendTitle(player, "", "§cFancyNpcs nie jest gotowy.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        if (activeIllusions.containsKey(player.getUniqueId())) {
            return;
        }
        if (!checkCooldown(player, ppmCooldowns, ppmCooldown, "PPM")) {
            return;
        }

            Location useLocation = player.getLocation().clone();
            Vector moveVec = useLocation.getDirection().setY(0);
            if (moveVec.lengthSquared() <= 0.0001D) {
                moveVec = yawToDirection(useLocation.getYaw());
            }
            Vector forward = moveVec.clone().normalize();
            moveVec.normalize().multiply(illusionSpeed);
            final Vector illusionMoveVec = moveVec;

            dev.grzybalson.utils.ColorUtils.sendTitle(player, "", "§aZniknięcie §7zostało §aaktywowane§7!");
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);

            Location spawnLocation = useLocation.clone().add(forward.clone().multiply(NPC_SPAWN_FORWARD_OFFSET));
            spawnLocation.setYaw(useLocation.getYaw());
            spawnLocation.setPitch(0.0F);
            spawnLocation.setY(getGroundY(spawnLocation));

            IllusionNpc npc = new IllusionNpc(player, spawnLocation, usedItem.clone());
            activeIllusions.put(player.getUniqueId(), npc);
            try {
                npc.spawn();
            } catch (Throwable exception) {
                activeIllusions.remove(player.getUniqueId(), npc);
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Nie udalo sie wyslac NPC rozzki iluzjonisty dla " + player.getName(), exception);
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return;
            }

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (activeIllusions.get(player.getUniqueId()) != npc || !player.isOnline()) {
                    return;
                }
                hideSourcePlayer(player);
            }, HIDE_SOURCE_DELAY_TICKS);

            new BukkitRunnable() {
                int ticks = 0;
                final int maxTicks = time * 20;
                @Override
                public void run() {
                    if (ticks >= maxTicks || !player.isOnline() || player.isDead() || !activeIllusions.containsKey(player.getUniqueId())) {
                        removeIllusion(player.getUniqueId());
                        this.cancel();
                        return;
                    }
                    Location npcLocation = npc.getLocation();
                    if (npcLocation.getWorld() == null || !npcLocation.getWorld().isChunkLoaded(
                        npcLocation.getBlockX() >> 4,
                        npcLocation.getBlockZ() >> 4
                    )) {
                        removeIllusion(player.getUniqueId());
                        this.cancel();
                        return;
                    }
                    npc.move(illusionMoveVec);
                    ticks++;
                }
            }.runTaskTimer(plugin, HIDE_SOURCE_DELAY_TICKS, 1L);
    }

    private void hideSourcePlayer(Player player) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.hideEntity(plugin, player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onIllusionDripstoneDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!isIllusionActive(player.getUniqueId()) || !isDripstoneDamage(event)) {
            return;
        }

        event.setCancelled(true);
        event.setDamage(0.0D);
    }

    private boolean checkCooldown(Player player, Map<UUID, Long> map, int cooldownSec, String type) {
        long now = System.currentTimeMillis();
        if (map.containsKey(player.getUniqueId())) {
            long end = map.get(player.getUniqueId());
            if (now < end) {
                long remaining = (end - now) / 1000;
                dev.grzybalson.utils.ColorUtils.sendTitle(player, "", "§cMusisz poczekać jeszcze §4" + remaining + "s");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return false;
            }
        }
        map.put(player.getUniqueId(), now + (cooldownSec * 1000L));
        return true;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        removeIllusion(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player viewer = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!viewer.isOnline()) {
                return;
            }
            for (IllusionNpc npc : activeIllusions.values()) {
                npc.spawnFor(viewer);
                Player source = npc.getSource();
                if (source != null && source.isOnline()) {
                    viewer.hideEntity(plugin, source);
                }
            }
        }, 5L);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        removeIllusion(event.getEntity().getUniqueId());
    }

    private static void removeIllusion(UUID uuid) {
        IllusionNpc npc = activeIllusions.remove(uuid);
        if (npc != null) {
            npc.destroy();
        }
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                online.showEntity(pluginInstance, p);
            }
        }
    }

    public static void disable() {
        for (UUID uuid : activeIllusions.keySet()) {
            removeIllusion(uuid);
        }
        activeIllusions.clear();
    }

    public static boolean isIllusionActive(UUID playerUuid) {
        return playerUuid != null && activeIllusions.containsKey(playerUuid);
    }

    private boolean isFancyNpcsReady() {
        try {
            return Bukkit.getPluginManager().isPluginEnabled("FancyNpcs") && FancyNpcsPlugin.get() != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Vector yawToDirection(float yaw) {
        double radians = Math.toRadians(yaw);
        return new Vector(-Math.sin(radians), 0.0D, Math.cos(radians));
    }

    private static boolean isDripstoneDamage(EntityDamageEvent event) {
        String key = getDamageTypeKey(event);
        if (key != null) {
            String normalized = key.toLowerCase(Locale.ROOT);
            if (normalized.contains("dripstone")
                || normalized.contains("stalagmite")
                || normalized.contains("stalactite")) {
                return true;
            }
        }

        if (event instanceof EntityDamageByEntityEvent byEntity && byEntity.getDamager() instanceof FallingBlock fallingBlock) {
            Material material = fallingBlock.getBlockData() != null ? fallingBlock.getBlockData().getMaterial() : null;
            return material == Material.POINTED_DRIPSTONE;
        }

        if (event.getCause() == EntityDamageEvent.DamageCause.CONTACT) {
            Location loc = event.getEntity().getLocation();
            return isPointedDripstone(loc)
                || isPointedDripstone(loc.clone().add(0, -1, 0))
                || isPointedDripstone(loc.clone().add(0, 1, 0))
                || isPointedDripstone(loc.clone().add(1, 0, 0))
                || isPointedDripstone(loc.clone().add(-1, 0, 0))
                || isPointedDripstone(loc.clone().add(0, 0, 1))
                || isPointedDripstone(loc.clone().add(0, 0, -1));
        }

        return false;
    }

    private static boolean isPointedDripstone(Location loc) {
        return loc != null
            && loc.getWorld() != null
            && loc.getBlock().getType() == Material.POINTED_DRIPSTONE;
    }

    private static String getDamageTypeKey(EntityDamageEvent event) {
        try {
            Object damageSource = event.getClass().getMethod("getDamageSource").invoke(event);
            if (damageSource == null) return null;
            Object damageType = damageSource.getClass().getMethod("getDamageType").invoke(damageSource);
            if (damageType == null) return null;
            Object key = damageType.getClass().getMethod("getKey").invoke(damageType);
            return key == null ? null : String.valueOf(key);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private boolean isLocationBlocked(Location loc) {
        for (String region : blockedRegions) {
            if (regionHandler.isLocationInRegion(loc, region)) return true;
        }
        return false;
    }

    private String translate(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private static double getGroundY(Location loc) {
        World world = loc.getWorld();
        if (world == null) {
            return loc.getY();
        }
        Location checkLoc = loc.clone();
        double startY = loc.getY();
        double minCheckY = Math.max(world.getMinHeight(), startY - 50.0);
        for (double y = startY; y >= minCheckY; y -= 1.0) {
            checkLoc.setY(y);
            if (checkLoc.getBlock().getType().isSolid()) {
                return y + 1.0;
            }
        }
        try {
            int highestY = world.getHighestBlockYAt(loc.getBlockX(), loc.getBlockZ());
            if (highestY < startY) {
                return highestY + 1.0;
            }
        } catch (Throwable ignored) {}
        return loc.getY();
    }

    private static class IllusionNpc {
        private final HollyCore plugin;
        private final Player source;
        private final Npc npc;
        private Location location;

        private IllusionNpc(Player source, Location location, ItemStack handItem) {
            this.plugin = HollyCore.getPlugin(HollyCore.class);
            this.source = source;
            this.location = location.clone();
            NpcData data = new NpcData(createNpcId(source), UUID.randomUUID(), this.location.clone())
                .setType(EntityType.PLAYER)
                .setDisplayName(source.getName())
                .setShowInTab(false)
                .setSpawnEntity(true)
                .setCollidable(false)
                .setTurnToPlayer(false)
                .setSkinData(createSkinData(source))
                .setEquipment(copyEquipment(source, handItem));

            this.npc = FancyNpcsPlugin.get().getNpcAdapter().apply(data);
            this.npc.setSaveToFile(false);
        }

        private void spawn() {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    FancyNpcsPlugin.get().getNpcManager().registerNpc(npc);
                    npc.create();
                    npc.spawnForAll();
                } catch (Throwable throwable) {
                    plugin.getLogger().log(java.util.logging.Level.SEVERE, "Blad podczas spawnowania NPC iluzji: " + throwable.getMessage(), throwable);
                }
            });
        }

        private void spawnFor(Player viewer) {
            if (!canView(viewer) || !isActive()) {
                return;
            }
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    npc.spawn(viewer);
                } catch (Throwable throwable) {
                    plugin.getLogger().log(java.util.logging.Level.SEVERE, "Blad podczas spawnowania NPC iluzji dla " + viewer.getName() + ": " + throwable.getMessage(), throwable);
                }
            });
        }

        private void move(Vector vector) {
            if (vector == null || vector.lengthSquared() <= 0.0D) {
                return;
            }
            location.add(vector);
            location.setY(getGroundY(location));
            location.setYaw(vectorToYaw(vector));
            npc.getData().setLocation(location.clone());
            npc.moveForAll();
        }

        private Location getLocation() {
            return location.clone();
        }

        private void destroy() {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    npc.removeForAll();
                } catch (Throwable ignored) {
                } finally {
                    try {
                        FancyNpcsPlugin.get().getNpcManager().removeNpc(npc);
                    } catch (Throwable ignored) {
                    }
                }
            });
        }

        private boolean canView(Player viewer) {
            return viewer != null && viewer.isOnline() && viewer.getWorld() == location.getWorld();
        }

        private boolean isActive() {
            return activeIllusions.get(source.getUniqueId()) == this;
        }

        private Player getSource() {
            return source;
        }

        private static SkinData createSkinData(Player player) {
            PlayerProfile bukkitProfile = player.getPlayerProfile();
            for (ProfileProperty property : bukkitProfile.getProperties()) {
                if ("textures".equalsIgnoreCase(property.getName())) {
                    return new SkinData(
                        player.getUniqueId().toString(),
                        SkinData.SkinVariant.AUTO,
                        property.getValue(),
                        property.getSignature()
                    );
                }
            }
            try {
                SkinData cached = FancyNpcsPlugin.get().getSkinManager().getByUUID(player.getUniqueId(), SkinData.SkinVariant.AUTO);
                if (cached != null) {
                    return cached;
                }
            } catch (Throwable ignored) {}
            return new SkinData(player.getUniqueId().toString(), SkinData.SkinVariant.AUTO);
        }

        private static Map<NpcEquipmentSlot, ItemStack> copyEquipment(Player player, ItemStack handItem) {
            EntityEquipment eq = player.getEquipment();
            if (eq == null) {
                return Map.of();
            }
            Map<NpcEquipmentSlot, ItemStack> result = new EnumMap<>(NpcEquipmentSlot.class);
            addEquipment(result, NpcEquipmentSlot.MAINHAND, handItem != null && handItem.getType() != Material.AIR ? handItem : eq.getItemInMainHand());
            addEquipment(result, NpcEquipmentSlot.OFFHAND, eq.getItemInOffHand());
            addEquipment(result, NpcEquipmentSlot.HEAD, eq.getHelmet());
            addEquipment(result, NpcEquipmentSlot.CHEST, eq.getChestplate());
            addEquipment(result, NpcEquipmentSlot.LEGS, eq.getLeggings());
            addEquipment(result, NpcEquipmentSlot.FEET, eq.getBoots());
            return result;
        }

        private static void addEquipment(Map<NpcEquipmentSlot, ItemStack> equipment, NpcEquipmentSlot slot, ItemStack item) {
            if (item != null && item.getType() != Material.AIR) {
                equipment.put(slot, item.clone());
            }
        }

        private static float vectorToYaw(Vector vector) {
            return (float) Math.toDegrees(Math.atan2(-vector.getX(), vector.getZ()));
        }

        private static String createNpcId(Player player) {
            return "holly_illusion_" + player.getUniqueId().toString().replace("-", "");
        }
    }
}
