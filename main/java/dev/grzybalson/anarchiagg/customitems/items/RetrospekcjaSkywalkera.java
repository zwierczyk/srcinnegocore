package dev.grzybalson.anarchiagg.customitems.items;

import dev.grzybalson.HollyCore;
import dev.grzybalson.anarchiagg.AnarchiaGGSettings;
import dev.grzybalson.anarchiagg.customitems.handlers.RegionHandler;
import dev.grzybalson.anarchiagg.customitems.util.EventItemTools;
import dev.grzybalson.anarchiagg.customitems.util.EventItemUseGuard;
import dev.grzybalson.anarchiagg.customitems.util.EventowkiEffects;
import dev.grzybalson.managers.VanishManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RetrospekcjaSkywalkera implements Listener {

    public static final String ITEM_KEY = "retrospekcjaskywalkera";

    private final JavaPlugin plugin;
    private final RegionHandler regionHandler;
    private final Set<String> blockedRegions;
    private final int cooldownSeconds;
    private final int delayTicks;
    private final int rewindTicks;
    private final Map<UUID, Recall> recalls = new ConcurrentHashMap<>();

    public RetrospekcjaSkywalkera(JavaPlugin plugin, RegionHandler regionHandler) {
        this.plugin = plugin;
        this.regionHandler = regionHandler;
        this.blockedRegions = EventItemTools.loadBlockedRegions(plugin, ITEM_KEY);
        FileConfiguration config = AnarchiaGGSettings.getConfig(plugin);
        this.cooldownSeconds = config.getInt("eventowki." + ITEM_KEY + ".cooldown", 0);
        this.delayTicks = Math.max(20, config.getInt("eventowki." + ITEM_KEY + ".delay-ticks", 120));
        this.rewindTicks = Math.max(8, config.getInt("eventowki." + ITEM_KEY + ".rewind-ticks", 16));
    }

    public static ItemStack createDefaultItem(JavaPlugin plugin) {
        return EventItemTools.createFromConfig(plugin, ITEM_KEY, Material.LIGHT_BLUE_DYE, "&b&lRetrospekcja Skywalkera", 1);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack used = EventItemTools.findUsed(plugin, player, ITEM_KEY, event);
        if (used == null) {
            return;
        }
        event.setCancelled(true);
        if (recalls.containsKey(player.getUniqueId())) {
            return;
        }
        if (!EventItemUseGuard.tryUse(plugin, player)) {
            return;
        }
        if (EventItemTools.isLocationBlocked(regionHandler, blockedRegions, player.getLocation())) {
            return;
        }
        if (EventItemUseGuard.hasItemCooldown(plugin, player, ITEM_KEY, used, cooldownSeconds)) {
            return;
        }
        EventItemUseGuard.applyItemCooldown(plugin, player, ITEM_KEY, used, cooldownSeconds);

        Location saved = player.getLocation().clone();
        long endsAt = System.currentTimeMillis() + delayTicks * 50L;
        BukkitTask bar = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || !recalls.containsKey(player.getUniqueId())) {
                return;
            }
            long left = Math.max(0L, endsAt - System.currentTimeMillis());
            String seconds = String.format(java.util.Locale.US, "%.1f", left / 1000.0D);
            EventItemTools.sendCastleActionBar(
                player,
                "skywalker",
                "&#7AD4FF⚠ &8Ξ &#7AD4FFRetrospekcja Skywalkera &8〢 &#7AD4FFPowrót za: &f" + seconds + "s"
            );
        }, 0L, 5L);
        BukkitTask finish = Bukkit.getScheduler().runTaskLater(plugin, () -> startRewind(player), delayTicks);
        recalls.put(player.getUniqueId(), new Recall(saved, bar, finish));
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.6f);
    }

    private void startRewind(Player player) {
        Recall recall = recalls.get(player.getUniqueId());
        if (recall == null || !player.isOnline()) {
            clear(player);
            return;
        }
        recall.bar().cancel();
        Location from = player.getLocation().clone();
        Location to = recall.saved();
        if (from.getWorld() == null || to.getWorld() == null || from.getWorld() != to.getWorld()) {
            clear(player);
            return;
        }

        hide(player);
        heal(player);
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, rewindTicks + 40, 0, false, false, false));
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.6f);
        EventowkiEffects.startSkywalkerRewind(plugin, player, from, to, rewindTicks, finished -> {
            if (finished != null && finished.isOnline()) {
                heal(finished);
                show(finished);
                finished.removePotionEffect(PotionEffectType.INVISIBILITY);
                finished.playSound(to, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.4f);
                if (to.getWorld() != null) {
                    to.getWorld().spawnParticle(Particle.PORTAL, to.clone().add(0, 1, 0), 40, 0.4, 0.6, 0.4, 0.2);
                }
            }
            clear(player);
        });
    }

    private void hide(Player player) {
        player.setInvisible(true);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.getUniqueId().equals(player.getUniqueId())) {
                viewer.hidePlayer(plugin, player);
            }
        }
    }

    private void show(Player player) {
        player.setInvisible(false);
        VanishManager vanish = null;
        if (plugin instanceof HollyCore core) {
            vanish = core.getVanishManager();
        }
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            if (vanish != null) {
                vanish.showRespectingVanish(viewer, player);
            } else {
                viewer.showPlayer(plugin, player);
            }
        }
    }

    private void heal(Player player) {
        AttributeInstance max = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        double maxHealth = max != null ? max.getValue() : 20.0D;
        player.setHealth(Math.min(maxHealth, player.getHealth() + (maxHealth - player.getHealth())));
        player.setHealth(maxHealth);
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setFireTicks(0);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        EventowkiEffects.cancelSkywalkerRewind(event.getEntity(), true);
        clear(event.getEntity());
        show(event.getEntity());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        EventowkiEffects.cancelSkywalkerRewind(event.getPlayer(), true);
        Recall recall = recalls.remove(event.getPlayer().getUniqueId());
        if (recall != null) {
            recall.bar().cancel();
            recall.finish().cancel();
        }
        show(event.getPlayer());
    }

    private void clear(Player player) {
        Recall recall = recalls.remove(player.getUniqueId());
        if (recall != null) {
            recall.bar().cancel();
            recall.finish().cancel();
        }
    }

    private record Recall(Location saved, BukkitTask bar, BukkitTask finish) {}
}
