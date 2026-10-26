package dev.grzybalson.modules.mslfkostiumy;

import dev.grzybalson.HollyCore;
import dev.grzybalson.anarchiagg.szafa.pety.PetyHandler;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Custom efekty petow MSLF (endermanek / magmus / golemik / kostek / szybciorek / plazowicz-pet).
 */
public final class MslfPetsEffectsListener implements Listener {

    private static final Set<Material> FARM_CROPS = EnumSet.of(
        Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS,
        Material.NETHER_WART, Material.MELON, Material.PUMPKIN, Material.SUGAR_CANE,
        Material.COCOA, Material.SWEET_BERRY_BUSH, Material.CACTUS
    );

    private static final Set<Material> NETHER_FARM = EnumSet.of(
        Material.NETHER_WART, Material.CRIMSON_FUNGUS, Material.WARPED_FUNGUS,
        Material.CRIMSON_ROOTS, Material.WARPED_ROOTS, Material.SHROOMLIGHT
    );

    private final HollyCore plugin;
    private final PetyHandler petyHandler;
    private final Set<UUID> extendingPotion = ConcurrentHashMap.newKeySet();

    public MslfPetsEffectsListener(HollyCore plugin, PetyHandler petyHandler) {
        this.plugin = plugin;
        this.petyHandler = petyHandler;
    }

    public boolean hasPet(Player player, String key) {
        if (player == null || key == null) {
            return false;
        }
        String active = petyHandler.getDatabase().getActivePet(player.getUniqueId());
        return active != null && key.equalsIgnoreCase(active);
    }

    public boolean hasSzybciorek(UUID uuid) {
        String active = petyHandler.getDatabase().getActivePet(uuid);
        return active != null && "szybciorek".equalsIgnoreCase(active);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEndermanekEndDamage(EntityDamageByEntityEvent event) {
        Player damager = event.getDamager() instanceof Player player ? player : null;
        if (damager == null || !hasPet(damager, "endermanek")) {
            return;
        }
        if (damager.getWorld().getEnvironment() == World.Environment.THE_END) {
            event.setDamage(event.getDamage() + 1.0);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPearlReturn(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof EnderPearl)) {
            return;
        }
        if (!(event.getEntity().getShooter() instanceof Player player)) {
            return;
        }
        if (!hasPet(player, "endermanek")) {
            return;
        }
        if (ThreadLocalRandom.current().nextDouble() >= 0.25) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            player.getInventory().addItem(new ItemStack(Material.ENDER_PEARL, 1));
        }, 1L);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMagmusNetherFarm(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!hasPet(player, "magmus")) {
            return;
        }
        if (player.getWorld().getEnvironment() != World.Environment.NETHER) {
            return;
        }
        Material type = event.getBlock().getType();
        if (!NETHER_FARM.contains(type) && !FARM_CROPS.contains(type)) {
            return;
        }
        if (ThreadLocalRandom.current().nextDouble() >= 0.35) {
            return;
        }
        ItemStack tool = player.getInventory().getItemInMainHand();
        for (ItemStack drop : event.getBlock().getDrops(tool, player)) {
            if (drop == null || drop.getType().isAir() || drop.getAmount() <= 0) {
                continue;
            }
            event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation().add(0.5, 0.5, 0.5), drop.clone());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArmorDurability(PlayerItemDamageEvent event) {
        Player player = event.getPlayer();
        boolean golemik = hasPet(player, "golemik");
        boolean plazowicz = hasPet(player, "plazowicz-pet");
        if (!golemik && !plazowicz) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || !isArmor(item.getType())) {
            return;
        }
        int reduced = Math.max(0, (int) Math.floor(event.getDamage() * 0.80));
        event.setDamage(reduced);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onKostekHitRepair(EntityDamageByEntityEvent event) {
        Player damager = event.getDamager() instanceof Player player ? player : null;
        if (damager == null || !hasPet(damager, "kostek")) {
            return;
        }
        if (ThreadLocalRandom.current().nextDouble() >= 0.12) {
            return;
        }
        repairRandomArmor(damager, 1);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onKostekBlockProtect(BlockBreakEvent event) {
        Player breaker = event.getPlayer();
        World world = event.getBlock().getWorld();
        if (world == null) {
            return;
        }
        // Bez wlasciciela 'kostka' w swiecie skan encji i tak nic nie anuluje — sprawdzamy to najpierw.
        boolean anyHolder = false;
        for (Player nearby : world.getPlayers()) {
            if (!nearby.equals(breaker) && hasPet(nearby, "kostek")) {
                anyHolder = true;
                break;
            }
        }
        if (!anyHolder) {
            return;
        }
        org.bukkit.Location scan = event.getBlock().getLocation().add(0.5, 0.5, 0.5);
        for (org.bukkit.entity.Entity entity : world.getNearbyEntities(scan, 8.0, 8.0, 8.0)) {
            if (!(entity instanceof Player nearby) || nearby.equals(breaker) || !nearby.isOnline()) {
                continue;
            }
            if (!hasPet(nearby, "kostek")) {
                continue;
            }
            if (ThreadLocalRandom.current().nextDouble() < 0.15) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onKostekPotionExtend(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!hasPet(player, "kostek")) {
            return;
        }
        if (event.getAction() != EntityPotionEffectEvent.Action.ADDED
            && event.getAction() != EntityPotionEffectEvent.Action.CHANGED) {
            return;
        }
        PotionEffect effect = event.getNewEffect();
        if (effect == null || effect.getDuration() <= 0 || effect.getDuration() > 20 * 60 * 30) {
            return;
        }
        UUID uuid = player.getUniqueId();
        if (!extendingPotion.add(uuid)) {
            // Wlasne ponowne nalozenie efektu — mnoznik liczymy tylko raz, inaczej czas rosnie lawinowo.
            return;
        }
        double mult = 1.1 + ThreadLocalRandom.current().nextDouble() * 0.9;
        int newDuration = (int) Math.round(effect.getDuration() * mult);
        event.setCancelled(true);
        try {
            player.addPotionEffect(new PotionEffect(
                effect.getType(),
                newDuration,
                effect.getAmplifier(),
                effect.isAmbient(),
                effect.hasParticles(),
                effect.hasIcon()
            ), true);
        } finally {
            extendingPotion.remove(uuid);
        }
    }

    private static void repairRandomArmor(Player player, int amount) {
        PlayerInventory inv = player.getInventory();
        ItemStack[] pieces = {inv.getHelmet(), inv.getChestplate(), inv.getLeggings(), inv.getBoots()};
        for (ItemStack piece : pieces) {
            if (piece == null || !(piece.getItemMeta() instanceof Damageable damageable)) {
                continue;
            }
            if (damageable.getDamage() <= 0) {
                continue;
            }
            damageable.setDamage(Math.max(0, damageable.getDamage() - amount));
            piece.setItemMeta((ItemMeta) damageable);
            return;
        }
    }

    private static boolean isArmor(Material material) {
        String name = material.name();
        return name.endsWith("_HELMET")
            || name.endsWith("_CHESTPLATE")
            || name.endsWith("_LEGGINGS")
            || name.endsWith("_BOOTS");
    }
}
