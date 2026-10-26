package dev.grzybalson.anarchiagg.customitems.items;

import dev.grzybalson.anarchiagg.AnarchiaGGSettings;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import dev.grzybalson.anarchiagg.customitems.handlers.RegionHandler;
import dev.grzybalson.anarchiagg.customitems.util.EventItemRegistry;
import dev.grzybalson.anarchiagg.customitems.util.EventItemUseGuard;

import java.util.*;
import org.bukkit.configuration.file.FileConfiguration;

public class HydroTrojzab implements Listener {
    private final String trojzabName;
    private final Material triggerMaterial;
    private final JavaPlugin plugin;
    private final RegionHandler regionHandler;
    private final int normalCooldown;
    private final int shiftCooldown;
    private final int damage;
    private static final Map<UUID, Long> normalCooldowns = new HashMap<>();
    private static final Map<UUID, Long> shiftCooldowns = new HashMap<>();
    private final Set<String> blockedRegions = new HashSet<>();
    private static final double KNOCKBACK_RADIUS = 3.0;
    private static final double KNOCKBACK_STRENGTH = 1.1;

    public HydroTrojzab(JavaPlugin plugin, RegionHandler regionHandler) {
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

        String itemString = eventowkiConfig.getString("eventowki.hydrotrojzab.item", "BOW");
        trojzabName = ChatColor.translateAlternateColorCodes('&',
                eventowkiConfig.getString("eventowki.hydrotrojzab.name", "&3&lHydro Trójząb")
        );
        triggerMaterial = Material.valueOf(itemString.toUpperCase());
        normalCooldown = eventowkiConfig.getInt("eventowki.hydrotrojzab.shoot-cooldown", 30);
        shiftCooldown = eventowkiConfig.getInt("eventowki.hydrotrojzab.fly-cooldown", 30);
        damage = eventowkiConfig.getInt("eventowki.hydrotrojzab.damage", 30);
    }


    public static void resetCooldowns(UUID playerUuid) {
        normalCooldowns.remove(playerUuid);
        shiftCooldowns.remove(playerUuid);
    }

    private boolean isLocationBlocked(Location loc) {
        if (loc == null || loc.getWorld() == null) return true;
        for (String regionName : blockedRegions) if (regionHandler.isLocationInRegion(loc, regionName)) return true;
        return false;
    }

    @EventHandler
    public void onEntityShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack bow = event.getBow();
        if (bow == null || bow.getType() == Material.AIR) return;
        if (!EventItemRegistry.matches(plugin, "hydrotrojzab", bow)) return;
        if (!EventItemUseGuard.tryUse(plugin, player)) return;

        if (isLocationBlocked(player.getLocation())) { event.setCancelled(true); return; }

        boolean isShift = player.isSneaking();
        Map<UUID, Long> cooldownMap = isShift ? shiftCooldowns : normalCooldowns;
        int cooldownTime = isShift ? shiftCooldown : normalCooldown;

        long timeLeft = cooldownMap.getOrDefault(player.getUniqueId(), 0L) - System.currentTimeMillis();
        if (timeLeft > 0) {
            event.setCancelled(true);
            long seconds = (timeLeft / 1000) + 1;
            dev.grzybalson.utils.ColorUtils.sendTitle(player, "", "§cMusisz poczekać jeszcze §4" + seconds + "s§c!", 0, 20, 10);
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        event.setCancelled(true);
        float power = event.getForce();
        if (power < 0.1) return;

        if (!isShift) {
            Trident trident = (Trident) player.getWorld().spawnEntity(
                    player.getEyeLocation().add(player.getLocation().getDirection().multiply(0.5)),
                    EntityType.TRIDENT
            );
            trident.setShooter(player);
            trident.setVelocity(player.getLocation().getDirection().multiply(power * 3.0));
            trident.setPickupStatus(Trident.PickupStatus.DISALLOWED);
            trident.setCustomName("HydroTrojzab:" + player.getUniqueId() + ":NORMAL");
            trident.setCustomNameVisible(false);
            player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_THROW, 1f, 1f);
            cooldownMap.put(player.getUniqueId(), System.currentTimeMillis() + (cooldownTime * 1000L));

            new BukkitRunnable() { @Override public void run() { if (!trident.isDead()) trident.remove(); } }
                    .runTaskLater(plugin, 600L);

        } else {
            Vector direction = player.getLocation().getDirection().normalize().multiply(3.0f * power);
            direction.setY(direction.getY() + 0.5);
            player.setVelocity(direction);
            player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_3, 1f, 1f);
            cooldownMap.put(player.getUniqueId(), System.currentTimeMillis() + (cooldownTime * 1000L));
        }
    }
    @EventHandler
    public void onInteract(org.bukkit.event.player.PlayerInteractEvent event) {
        Player player = event.getPlayer();

        if (!event.getAction().isRightClick()) return;

        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();
        boolean hasTrojzab = false;
        boolean mainHandTrojzab = false;

        if (EventItemRegistry.matches(plugin, "hydrotrojzab", mainHand)) {
            hasTrojzab = true;
            mainHandTrojzab = true;
        } else if (EventItemRegistry.matches(plugin, "hydrotrojzab", offHand)) {
            hasTrojzab = true;
        }

        if (!hasTrojzab) return;

        boolean hasArrow = player.getInventory().contains(Material.ARROW);
        if (hasArrow) return;

        int slot;
        if (mainHandTrojzab) {
            int heldSlot = player.getInventory().getHeldItemSlot();
            slot = heldSlot == 8 ? 7 : heldSlot == 7 ? 6 : 8;
        } else {
            slot = 8;
        }

        ItemStack arrow = new ItemStack(Material.ARROW);
        ItemStack previous = player.getInventory().getItem(slot);
        player.getInventory().setItem(slot, arrow);
        player.updateInventory();

        org.bukkit.Bukkit.getScheduler().runTaskLater(JavaPlugin.getProvidingPlugin(getClass()), () -> {
            player.getInventory().setItem(slot, previous);
            player.updateInventory();
        }, 1L);
    }



    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        if (!(projectile instanceof Trident)) return;
        if (!(event.getEntity().getShooter() instanceof Player shooter)) return;
        if (projectile.getCustomName() == null || !projectile.getCustomName().startsWith("HydroTrojzab")) return;

        Entity hitEntity = event.getHitEntity();
        if (!(hitEntity instanceof Player hitPlayer)) { projectile.remove(); return; }
        if (isLocationBlocked(hitPlayer.getLocation())) return;

        boolean isShift = projectile.getCustomName().endsWith("SHIFT");

        if (!isShift) {
            hitPlayer.damage(damage);
            Location hitLoc = hitPlayer.getLocation();
            for (Entity nearbyEntity : hitLoc.getWorld().getNearbyEntities(hitLoc, KNOCKBACK_RADIUS, KNOCKBACK_RADIUS, KNOCKBACK_RADIUS)) {
                if (nearbyEntity instanceof Player nearbyPlayer) {
                    nearbyPlayer.getWorld().strikeLightningEffect(nearbyPlayer.getLocation());
                    nearbyPlayer.getWorld().playSound(nearbyPlayer.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1f, 1f);
                    if (!nearbyPlayer.getUniqueId().equals(hitPlayer.getUniqueId())) {
                        Vector vec = nearbyPlayer.getLocation().toVector().subtract(hitPlayer.getLocation().toVector());
                        vec.setY(0.7);
                        nearbyPlayer.setVelocity(vec.normalize().multiply(KNOCKBACK_STRENGTH));
                    }
                }
            }
            dev.grzybalson.utils.ColorUtils.sendTitle(shooter, "", "§7Zadałeś cios §fpiorunem §7z trójzęba!", 0, 50, 10);
            dev.grzybalson.utils.ColorUtils.sendTitle(hitPlayer, "", "§7Otrzymałeś cios §fpiorunem §7z trójzęba!", 0, 50, 10);
        }
        projectile.remove();
    }
}
