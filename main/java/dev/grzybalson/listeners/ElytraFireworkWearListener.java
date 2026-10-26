package dev.grzybalson.listeners;

import com.destroystokyo.paper.event.player.PlayerElytraBoostEvent;
import dev.grzybalson.HollyCore;
import dev.grzybalson.config.HollyDataPaths;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ElytraFireworkWearListener implements Listener {

    private static final String RESOURCE_PATH = "addons/elytra/elytra.yml";
    private static final double WARN_HALF = 0.50D;
    private static final double WARN_LOW = 0.20D;

    private final HollyCore plugin;
    private final File configFile;
    private final Map<UUID, Integer> lastBoostTick = new ConcurrentHashMap<>();
    private final Map<UUID, Long> boostCooldownUntil = new ConcurrentHashMap<>();

    private int cooldownSeconds = 0;
    private int durationSeconds = 1;
    private int durabilityPerBoost = 2;
    private String cooldownMessage = "&#FF0000✘ &8Ξ &cMusisz odczekać jeszcze &4%time%s &cprzed następnym boostem elytry.";

    public ElytraFireworkWearListener(HollyCore plugin) {
        this.plugin = plugin;
        this.configFile = new File(HollyDataPaths.getAddonFolder(plugin, "elytra"), "elytra.yml");
        reload();
    }

    public void reload() {
        HollyDataPaths.ensureResource(plugin, RESOURCE_PATH);
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        boolean changed = false;
        changed |= setIfMissing(config, "cooldown", 0);
        changed |= setIfMissing(config, "duration", 1);
        changed |= setIfMissing(config, "durability-per-boost", 2);
        changed |= setIfMissing(config, "messages.cooldown", cooldownMessage);
        if (changed) {
            try {
                config.save(configFile);
            } catch (IOException ignored) {
            }
        }

        cooldownSeconds = Math.max(0, config.getInt("cooldown", 0));
        durationSeconds = Math.max(0, config.getInt("duration", 1));
        durabilityPerBoost = Math.max(1, config.getInt("durability-per-boost", 2));
        cooldownMessage = config.getString("messages.cooldown", cooldownMessage);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onElytraBoost(PlayerElytraBoostEvent event) {
        Player player = event.getPlayer();
        if (isOnCooldown(player)) {
            event.setCancelled(true);
            sendCooldown(player);
            return;
        }
        applyBoostCooldown(player);
        applyBoostDuration(event.getFirework());
        wearFromBoost(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFireworkInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack used = event.getItem();
        if (used == null || used.getType() != Material.FIREWORK_ROCKET) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.isGliding()) {
            return;
        }
        if (event.getHand() == EquipmentSlot.OFF_HAND
            && player.getInventory().getItemInMainHand().getType() == Material.FIREWORK_ROCKET) {
            return;
        }
        wearFromBoost(player);
    }

    private boolean isOnCooldown(Player player) {
        if (cooldownSeconds <= 0) {
            return false;
        }
        Long until = boostCooldownUntil.get(player.getUniqueId());
        return until != null && until > System.currentTimeMillis();
    }

    private void sendCooldown(Player player) {
        Long until = boostCooldownUntil.get(player.getUniqueId());
        long left = until == null ? cooldownSeconds : Math.max(1L, (until - System.currentTimeMillis() + 999L) / 1000L);
        player.sendMessage(ColorUtils.colorize(cooldownMessage.replace("%time%", String.valueOf(left))));
    }

    private void applyBoostCooldown(Player player) {
        if (cooldownSeconds <= 0) {
            return;
        }
        boostCooldownUntil.put(player.getUniqueId(), System.currentTimeMillis() + cooldownSeconds * 1000L);
        player.setCooldown(Material.FIREWORK_ROCKET, cooldownSeconds * 20);
    }

    private void applyBoostDuration(Firework firework) {
        if (firework == null || durationSeconds <= 0) {
            return;
        }
        firework.setTicksToDetonate(durationSeconds * 20);
    }

    private void wearFromBoost(Player player) {
        int tick = org.bukkit.Bukkit.getCurrentTick();
        Integer last = lastBoostTick.put(player.getUniqueId(), tick);
        if (last != null && last == tick) {
            return;
        }

        ItemStack elytra = player.getInventory().getChestplate();
        if (elytra == null || elytra.getType() != Material.ELYTRA) {
            return;
        }
        ItemMeta meta = elytra.getItemMeta();
        if (!(meta instanceof Damageable damageable)) {
            return;
        }
        if (meta.isUnbreakable()) {
            meta.setUnbreakable(false);
        }

        int max = Material.ELYTRA.getMaxDurability();
        if (max <= 0) {
            return;
        }

        int damageBefore = Math.max(0, damageable.getDamage());
        double remainingBefore = (max - damageBefore) / (double) max;
        int damageAfter = Math.min(max, damageBefore + durabilityPerBoost);
        double remainingAfter = (max - damageAfter) / (double) max;

        if (damageAfter >= max) {
            player.getInventory().setChestplate(null);
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0F, 1.0F);
            ColorUtils.sendTitle(player, "&#FF5C5C⚠", "&cElytra została zniszczona", 5, 40, 10);
            return;
        }

        damageable.setDamage(damageAfter);
        elytra.setItemMeta(meta);
        player.getInventory().setChestplate(elytra);

        if (remainingBefore > WARN_HALF && remainingAfter <= WARN_HALF) {
            ColorUtils.sendTitle(player, "&#FFD84A⚠", "&fElytra ma już tylko &e50% &fwytrzymałości", 5, 35, 8);
            player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_ELYTRA, 1.0F, 0.7F);
        } else if (remainingBefore > WARN_LOW && remainingAfter <= WARN_LOW) {
            ColorUtils.sendTitle(player, "&#FF5C5C⚠", "&cElytra ma tylko &420% &cwytrzymałości — napraw ją!", 5, 40, 10);
            player.playSound(player.getLocation(), Sound.ITEM_SHIELD_BREAK, 0.9F, 0.8F);
        }
    }

    private static boolean setIfMissing(YamlConfiguration config, String path, Object value) {
        if (config.contains(path)) {
            return false;
        }
        config.set(path, value);
        return true;
    }
}
