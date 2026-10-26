package dev.grzybalson.anarchiagg.customitems.items;

import dev.grzybalson.anarchiagg.AnarchiaGGSettings;
import dev.grzybalson.anarchiagg.customitems.handlers.RegionHandler;
import dev.grzybalson.anarchiagg.customitems.util.EventItemRegistry;
import dev.grzybalson.anarchiagg.customitems.util.EventItemTools;
import dev.grzybalson.anarchiagg.customitems.util.EventItemUseGuard;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Set;

public class Kokos implements Listener {

    public static final String ITEM_KEY = "kokos";

    private final JavaPlugin plugin;
    private final RegionHandler regionHandler;
    private final Set<String> blockedRegions;
    private final int cooldownSeconds;
    private final int resistanceSeconds;
    private final int regenSeconds;
    private final int goldenHearts;

    public Kokos(JavaPlugin plugin, RegionHandler regionHandler) {
        this.plugin = plugin;
        this.regionHandler = regionHandler;
        this.blockedRegions = EventItemTools.loadBlockedRegions(plugin, ITEM_KEY);
        FileConfiguration config = AnarchiaGGSettings.getConfig(plugin);
        this.cooldownSeconds = config.getInt("eventowki." + ITEM_KEY + ".cooldown", 0);
        this.resistanceSeconds = Math.max(1, config.getInt("eventowki." + ITEM_KEY + ".resistance-seconds", 15));
        this.regenSeconds = Math.max(1, config.getInt("eventowki." + ITEM_KEY + ".regen-seconds", 10));
        this.goldenHearts = Math.max(1, config.getInt("eventowki." + ITEM_KEY + ".golden-hearts", 3));
    }

    public static ItemStack createDefaultItem(JavaPlugin plugin) {
        return EventItemTools.createFromConfig(plugin, ITEM_KEY, Material.GOLDEN_APPLE, "&6&lKokos", 1);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPrepareEat(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack used = event.getItem();
        if (used == null || used.getType() != Material.ENCHANTED_GOLDEN_APPLE) {
            return;
        }
        if (!EventItemRegistry.isTaggedAs(plugin, ITEM_KEY, used) && !EventItemRegistry.matches(plugin, ITEM_KEY, used)) {
            return;
        }
        used.setType(Material.GOLDEN_APPLE);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEat(PlayerItemConsumeEvent event) {
        ItemStack used = event.getItem();
        if (!EventItemRegistry.isTaggedAs(plugin, ITEM_KEY, used) && !EventItemRegistry.matches(plugin, ITEM_KEY, used)) {
            return;
        }
        Player player = event.getPlayer();
        if (!EventItemUseGuard.tryUse(plugin, player)) {
            event.setCancelled(true);
            return;
        }
        if (EventItemTools.isLocationBlocked(regionHandler, blockedRegions, player.getLocation())) {
            event.setCancelled(true);
            return;
        }
        if (EventItemUseGuard.hasItemCooldown(plugin, player, ITEM_KEY, used, cooldownSeconds)) {
            event.setCancelled(true);
            return;
        }
        // Tylko własny cooldown kokosa — bez setCooldown(GOLDEN_APPLE), bo blokuje zwykle jabłka.
        EventItemUseGuard.applyItemCooldown(plugin, player, ITEM_KEY, used, cooldownSeconds, false);
        player.setCooldown(Material.GOLDEN_APPLE, 0);
        player.setCooldown(Material.ENCHANTED_GOLDEN_APPLE, 0);
        Bukkit.getScheduler().runTask(plugin, () -> applyEffects(player));
        Bukkit.getScheduler().runTaskLater(plugin, () -> applyEffects(player), 2L);
    }

    private void applyEffects(Player player) {
        if (!player.isOnline()) {
            return;
        }
        player.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);
        player.removePotionEffect(PotionEffectType.DAMAGE_RESISTANCE);
        player.removePotionEffect(PotionEffectType.REGENERATION);
        player.removePotionEffect(PotionEffectType.ABSORPTION);
        player.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, resistanceSeconds * 20, 0, true, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, regenSeconds * 20, 1, true, true, true));
        int absorptionAmplifier = Math.max(0, (goldenHearts / 2) - 1);
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 20 * 60, absorptionAmplifier, true, true, true));
    }
}
