package dev.grzybalson.anarchiagg.adminitems.listener;

import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import dev.grzybalson.HollyCore;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsConfig;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsCooldowns;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsGUI;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsRegionUtil;
import dev.grzybalson.utils.ColorUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class IceWandListener implements Listener {

    private final HollyCore plugin;
    private final AdminItemsConfig config;

    private static final Map<UUID, OneHitTotemMark> oneHitTotemUntil = new HashMap<>();

    private final Map<UUID, Long> cooldowns = new HashMap<>();

    private final List<PotionEffectType> effectsToRemove = Arrays.asList(
            PotionEffectType.HEALTH_BOOST,
            PotionEffectType.ABSORPTION,
            PotionEffectType.REGENERATION,
            PotionEffectType.DAMAGE_RESISTANCE,
            PotionEffectType.FIRE_RESISTANCE
    );

    public IceWandListener(HollyCore plugin) {
        this.plugin = plugin;
        this.config = plugin.getAdminItemsConfig();
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {

        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (!isIceWand(item)) {
            return;
        }

        if (!event.getAction().toString().contains("RIGHT_CLICK")) {
            return;
        }

        event.setCancelled(true);

        AdminItemsConfig.IceWandSettings settings = config.getIceWand();

        if (AdminItemsRegionUtil.isInBlockedRegion(player, plugin)) {
            player.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(
                    settings.getName() + " &8:: &cNie mozesz uzywac tego przedmiotu na spawnie!"
            ));
            return;
        }

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        long cooldownMillis = settings.getCooldown() * 1000L;

        if (cooldowns.containsKey(uuid)) {

            long cooldownUntil = cooldowns.get(uuid);
            long timeLeft = cooldownUntil - now;

            if (timeLeft > 0) {
                AdminItemsCooldowns.refresh(player, item.getType(), cooldownUntil);
                player.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(
                        settings.getName() + " &8:: &cMusisz poczekac jeszcze &4" + (timeLeft / 1000) + "s"
                ));
                return;
            }
            dev.grzybalson.anarchiagg.util.MapCompatibilityUtil.remove(cooldowns, uuid, cooldownUntil);
        }

        int radius = settings.getRadius();
        boolean anyoneAffected = false;
        Location origin = player.getLocation();

        for (Player target : plugin.getServer().getOnlinePlayers()) {
            if (target.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }

            if (!target.getWorld().equals(origin.getWorld())) {
                continue;
            }

            Location targetLocation = target.getLocation();
            if (Math.abs(targetLocation.getX() - origin.getX()) > radius
                    || Math.abs(targetLocation.getY() - origin.getY()) > radius
                    || Math.abs(targetLocation.getZ() - origin.getZ()) > radius) {
                continue;
            }

            if (AdminItemsRegionUtil.isInBlockedRegion(target, plugin)) {
                continue;
            }

            boolean targetLostEffects = false;
            double healthBeforeEffects = target.getHealth();

            for (PotionEffectType effectType : effectsToRemove) {
                if (target.hasPotionEffect(effectType)) {
                    target.removePotionEffect(effectType);
                    targetLostEffects = true;
                }
            }
            if (targetLostEffects && target.getHealth() < healthBeforeEffects) {
                target.setHealth(Math.min(healthBeforeEffects, target.getMaxHealth()));
            }

            anyoneAffected = true;
            markOneHitTotem(target, settings.getOneHitTotemSeconds(),
                    settings.getTotemMaxHealth(), settings.getTotemMaxAbsorption());

            if (targetLostEffects) {
                target.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(
                        settings.getName() + " &8:: &cTwoje pozytywne efekty zostaly usuniete!"
                ));
            } else {
            }
        }

        if (!anyoneAffected) {
            player.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(
                    settings.getName() + " &8:: &cW poblizu &4" + radius + " kratek &cnie ma zadnego przeciwnika!"
            ));
            return;
        }

        cooldowns.put(uuid, now + cooldownMillis);

        AdminItemsCooldowns.apply(player, item.getType(), cooldownMillis);

        player.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(
                settings.getName() + " &8:: &aPomyslnie usunieto efekty!"
        ));
    }

    private boolean isIceWand(ItemStack item) {

        if (item == null) {
            return false;
        }

        if (item.getType() != Material.BONE) {
            return false;
        }

        if (!item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();

        if (AdminItemsGUI.KEY_LODOWA_ROZDZKA != null
                && meta.getPersistentDataContainer().has(AdminItemsGUI.KEY_LODOWA_ROZDZKA, PersistentDataType.BYTE)) {
            return true;
        }

        return meta.hasCustomModelData()
                && meta.getCustomModelData()
                == config.getIceWand().getCustomModelData();
    }

    public void resetCooldown(UUID uuid) {
        cooldowns.remove(uuid);
    }

    public static boolean hasActiveOneHitTotem(UUID uuid) {
        OneHitTotemMark mark = oneHitTotemUntil.get(uuid);
        if (mark == null) return false;

        if (mark.untilMillis() < System.currentTimeMillis()) {
            oneHitTotemUntil.remove(uuid);
            return false;
        }

        return true;
    }

    private static void markOneHitTotem(Player target, int seconds, double maxHealth, double maxAbsorption) {
        if (seconds <= 0) return;
        oneHitTotemUntil.put(target.getUniqueId(), new OneHitTotemMark(
                System.currentTimeMillis() + seconds * 1000L,
                Math.max(0.0, maxHealth),
                Math.max(0.0, maxAbsorption)
        ));
    }

    public static boolean consumeOneHitTotem(UUID uuid, double health, double absorption) {
        OneHitTotemMark mark = oneHitTotemUntil.remove(uuid);
        if (mark == null) return false;

        if (mark.untilMillis() < System.currentTimeMillis()) return false;
        return health <= mark.maxHealth() + 0.0001
                && absorption <= mark.maxAbsorption() + 0.0001;
    }

    private record OneHitTotemMark(long untilMillis, double maxHealth, double maxAbsorption) {
    }
}
