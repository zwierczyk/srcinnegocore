package dev.grzybalson.anarchiagg.customitems.items;

import dev.grzybalson.anarchiagg.AnarchiaGGSettings;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import dev.grzybalson.anarchiagg.customitems.handlers.RegionHandler;
import dev.grzybalson.anarchiagg.customitems.util.EventItemRegistry;
import dev.grzybalson.anarchiagg.customitems.util.EventItemUseGuard;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class Sniezynka implements Listener {
    private final JavaPlugin plugin;
    private final RegionHandler regionHandler;
    private final int sniezynkaCooldown;
    private final String sniezynkaName;
    private final Material sniezynkaMaterial = Material.SNOWBALL;
    private final NamespacedKey sniezynkaKey;
    private final Set<String> blockedRegions = new HashSet<>();
    private final Set<UUID> frozenPlayers = new HashSet<>();

    public Sniezynka(JavaPlugin plugin, RegionHandler regionHandler) {
        this.sniezynkaKey = new NamespacedKey(plugin, "sniezynka_tag");

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

        sniezynkaCooldown = eventowkiConfig.getInt("eventowki.sniezynka.cooldown", 15);
        sniezynkaName = translate(eventowkiConfig.getString("eventowki.sniezynka.name", "&bSniezynka"));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (!EventItemRegistry.matches(plugin, "sniezynka", item)) return;
        Player player = event.getPlayer();
        if (!EventItemUseGuard.tryUse(plugin, player)) return;
        if (isLocationBlocked(event.getPlayer().getLocation())) return;

        if (EventItemUseGuard.hasItemCooldown(plugin, player, "sniezynka", item, sniezynkaCooldown)) {
            event.setCancelled(true);
            return;
        }

        EventItemUseGuard.applyItemCooldown(plugin, player, "sniezynka", item, sniezynkaCooldown);
        player.playSound(player.getLocation(), Sound.ENTITY_SNOWBALL_THROW, 1.0f, 1.2f);
        dev.grzybalson.utils.ColorUtils.sendTitle(player, "", translate("&7Rzuciłeś &bŚnieżynkę&7!"), 10, 20, 10);

        Snowball snowball = player.launchProjectile(Snowball.class);
        snowball.getPersistentDataContainer().set(sniezynkaKey, PersistentDataType.BYTE, (byte) 1);
        ItemMeta meta = snowball.getItem().getItemMeta();
        meta.setCustomModelData(1);
        snowball.getItem().setItemMeta(meta);

        ItemStack itemInHand = event.getItem();
        itemInHand.setAmount(itemInHand.getAmount() - 1);
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Snowball snowball)) return;
        if (!snowball.getPersistentDataContainer().has(sniezynkaKey, PersistentDataType.BYTE)) return;
        snowball.remove();

        if (!(event.getHitEntity() instanceof Player victim)) return;
        if (!(snowball.getShooter() instanceof Player attacker)) return;

        if (isLocationBlocked(attacker.getLocation()) || isLocationBlocked(victim.getLocation())) return;

        freezePlayer(victim);
    }

    private void freezePlayer(Player victim) {
        UUID uuid = victim.getUniqueId();
        if (frozenPlayers.contains(uuid)) return;

        frozenPlayers.add(uuid);
        dev.grzybalson.utils.ColorUtils.sendTitle(victim, "", translate("&7Zostałeś &bzamrożony &7przez &bŚnieżynkę&7!"), 5, 40, 5);

        new BukkitRunnable() {
            @Override
            public void run() {
                frozenPlayers.remove(uuid);
            }
        }.runTaskLater(plugin, 60L);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (frozenPlayers.isEmpty()) {
            return;
        }
        if (frozenPlayers.contains(event.getPlayer().getUniqueId())) {
            Location from = event.getFrom();
            Location to = event.getTo();

            if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
                event.setTo(from);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        frozenPlayers.remove(event.getPlayer().getUniqueId());
    }

    private boolean isLocationBlocked(Location loc) {
        if (loc == null || loc.getWorld() == null) return true;
        for (String regionName : blockedRegions) {
            if (regionHandler.isLocationInRegion(loc, regionName)) {
                return true;
            }
        }
        return false;
    }

    private String translate(String text) {
        return text.replace("&", "§");
    }
}
