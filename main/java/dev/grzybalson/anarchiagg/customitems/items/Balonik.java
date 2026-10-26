package dev.grzybalson.anarchiagg.customitems.items;

import dev.grzybalson.anarchiagg.AnarchiaGGSettings;

import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import dev.grzybalson.anarchiagg.customitems.handlers.RegionHandler;
import dev.grzybalson.anarchiagg.customitems.util.EventItemRegistry;
import dev.grzybalson.anarchiagg.customitems.util.EventItemUseGuard;
import java.util.*;

public class Balonik implements Listener {
    private final String balonikName;
    private final Material balonikMaterial;
    private final JavaPlugin plugin;
    private final RegionHandler regionHandler;
    private final Set<String> blockedRegions = new HashSet<>();
    private final int cooldownSeconds;

    private static final Map<UUID, BalonikInstance> ACTIVE_BALONIKI = new HashMap<>();

    public Balonik(JavaPlugin plugin, RegionHandler regionHandler) {
        this.plugin = plugin;
        this.regionHandler = regionHandler;
        FileConfiguration eventowkiConfig = AnarchiaGGSettings.getConfig(plugin);
        List<String> regionList = eventowkiConfig.getStringList("blocked-regions");
        if (regionList.isEmpty() && !eventowkiConfig.isSet("blocked-regions")) {
            eventowkiConfig.set("blocked-regions", List.of("spawn"));
            this.blockedRegions.add("spawn");
        } else {
            this.blockedRegions.addAll(regionList);
        }

        balonikMaterial = Material.PLAYER_HEAD;

        balonikName = eventowkiConfig.getString("eventowki.balonik.name", "§3§lBalonik z helem");
        cooldownSeconds = eventowkiConfig.getInt("eventowki.balonik.cooldown", 20);

        List<String> itemregionList = eventowkiConfig.getStringList("eventowki.balonik.blocked-regions");
        if (itemregionList.isEmpty() && !eventowkiConfig.isSet("eventowki.balonik.blocked-regions")) {
            eventowkiConfig.set("eventowki.balonik.blocked-regions", List.of("pvp"));
            this.blockedRegions.add("pvp");
        } else {
            this.blockedRegions.addAll(itemregionList);
        }
    }

    public static void balonikDisable() {
        for (BalonikInstance instance : new HashSet<>(ACTIVE_BALONIKI.values())) {
            instance.removeInstantly();
        }
        ACTIVE_BALONIKI.clear();
    }

    private boolean isLocationBlocked(Location loc) {
        if (loc == null || loc.getWorld() == null) return true;
        for (String regionName : blockedRegions) {
            if (regionHandler.isLocationInRegion(loc, regionName)) {
                return true;
            }
        }
        for (String regionName : blockedRegions) {
            if (regionHandler.isLocationInRegion(loc, regionName)) {
                return true;
            }
        }
        return false;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (itemInHand.getType() == Material.AIR) return;
        if (!EventItemRegistry.matches(plugin, "balonik", itemInHand)) return;
        if (!EventItemUseGuard.tryUse(plugin, player)) return;
        event.setCancelled(true);

        if (EventItemUseGuard.hasItemCooldown(plugin, player, "balonik", itemInHand, cooldownSeconds)) {
            return;
        }

        Location spawnLocation = player.getLocation().clone();
        spawnLocation.setX(spawnLocation.getBlockX() + 0.5);
        spawnLocation.setZ(spawnLocation.getBlockZ() + 0.5);
        spawnLocation.setY(spawnLocation.getBlockY() + 1.0);

        if (isLocationBlocked(spawnLocation)) {
            return;
        }

        EventItemUseGuard.applyItemCooldown(plugin, player, "balonik", itemInHand, cooldownSeconds);

        ItemStack itemCopy = itemInHand.clone();

        BalonikInstance newBalonik = new BalonikInstance(spawnLocation, itemCopy, plugin, this);
        ACTIVE_BALONIKI.put(newBalonik.getId(), newBalonik);
        newBalonik.start();

        if (itemInHand.getAmount() > 1) {
            itemInHand.setAmount(itemInHand.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }
    }

    private static class BalonikInstance {
        private final UUID id;
        private final ArmorStand armorStand;
        private final JavaPlugin plugin;
        private final Balonik mainClass;
        private BukkitRunnable task;
        private final double riseSpeed = 0.6;
        private final int MAX_AIR_CHECK = 300;

        public BalonikInstance(Location location, ItemStack item, JavaPlugin plugin, Balonik mainClass) {
            this.id = UUID.randomUUID();
            this.plugin = plugin;
            this.mainClass = mainClass;
            this.armorStand = (ArmorStand) location.getWorld().spawnEntity(location, EntityType.ARMOR_STAND);
            this.armorStand.setVisible(false);
            this.armorStand.setGravity(false);
            this.armorStand.setInvulnerable(true);
            this.armorStand.setSmall(true);
            this.armorStand.getEquipment().setHelmet(item);
        }

        public UUID getId() {
            return id;
        }

        public void start() {
            task = new BukkitRunnable() {
                @Override
                public void run() {
                    if (armorStand.isDead() || !plugin.isEnabled()) {
                        removeInstantly();
                        return;
                    }

                    Location loc = armorStand.getLocation();

                    if (mainClass.isLocationBlocked(loc)) {
                        removeInstantly();
                        return;
                    }

                    if (loc.getBlockY() >= loc.getWorld().getMaxHeight() - 1) {
                        removeInstantly();
                        return;
                    }

                    boolean foundAnyBlock = false;
                    for (int i = 1; i <= MAX_AIR_CHECK; i++) {
                        Block b = loc.clone().add(0, i, 0).getBlock();
                        if (b.getType() != Material.AIR && b.getType() != Material.CAVE_AIR && b.getType() != Material.VOID_AIR) {
                            foundAnyBlock = true;
                            break;
                        }
                    }

                    if (!foundAnyBlock) {
                        removeInstantly();
                        return;
                    }

                    Block blockAbove = loc.clone().add(0, 1, 0).getBlock();

                    if (blockAbove.getType() == Material.BLUE_GLAZED_TERRACOTTA) {
                        removeInstantly();
                        return;
                    }

                    if (blockAbove.getType() != Material.AIR && blockAbove.getType() != Material.CAVE_AIR && blockAbove.getType() != Material.VOID_AIR) {
                        if (blockAbove.getType() != Material.BEDROCK && blockAbove.getType() != Material.BARRIER) {
                            if (!mainClass.isLocationBlocked(blockAbove.getLocation())) {
                                if (blockAbove.getType() == Material.BEACON) {
                                    blockAbove.setType(Material.AIR);
                                } else {
                                    blockAbove.breakNaturally(new ItemStack(Material.AIR));
                                }
                            } else {
                                removeInstantly();
                                return;
                            }
                        } else {
                            removeInstantly();
                            return;
                        }
                    }

                    loc.add(0, riseSpeed, 0);
                    armorStand.teleport(loc);
                }
            };
            task.runTaskTimer(plugin, 0L, 1L);
        }

        public void removeInstantly() {
            if (task != null) task.cancel();
            if (armorStand != null && !armorStand.isDead()) armorStand.remove();
            ACTIVE_BALONIKI.remove(id);
        }
    }
}
