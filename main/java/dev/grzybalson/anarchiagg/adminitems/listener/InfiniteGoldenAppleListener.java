package dev.grzybalson.anarchiagg.adminitems.listener;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import dev.grzybalson.HollyCore;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsConfig;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsCooldowns;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsGUI;
import dev.grzybalson.utils.ColorUtils;

public class InfiniteGoldenAppleListener
implements Listener {
    private static final int REGENERATION_DURATION_TICKS = 400;
    private static final int ABSORPTION_DURATION_TICKS = 2400;
    private static final int RESISTANCE_DURATION_TICKS = 6000;
    private static final int FIRE_RESISTANCE_DURATION_TICKS = 6000;
    private static final double KOX_ABSORPTION_HEALTH = 16.0;
    private static final double EXTRA_GOLDEN_HEARTS_HEALTH = 4.0;
    private final HollyCore plugin;
    private final AdminItemsConfig config;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<UUID, Long>();

    public InfiniteGoldenAppleListener(HollyCore plugin) {
        this.plugin = plugin;
        this.config = plugin.getAdminItemsConfig();
    }

    @EventHandler(priority=EventPriority.HIGH, ignoreCancelled=true)
    public void onConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        if (!this.isInfiniteGoldenApple(item)) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        AdminItemsConfig.InfiniteGoldenAppleSettings settings = this.config.getInfiniteGoldenApple();
        if (settings == null || !settings.isEnabled()) {
            return;
        }
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        long cooldownMillis = Math.max(0L, (long)settings.getCooldown()) * 1000L;
        Long cooldownUntil = this.cooldowns.get(uuid);
        if (cooldownUntil != null && cooldownUntil > now) {
            AdminItemsCooldowns.refresh(player, Material.GOLDEN_APPLE, cooldownUntil);
            player.updateInventory();
            return;
        }
        if (cooldownUntil != null) {
            dev.grzybalson.anarchiagg.util.MapCompatibilityUtil.remove(this.cooldowns, uuid, cooldownUntil);
        }
        this.applyEnchantedGoldenAppleEffects(player);
        this.applyFood(player);
        boolean extraHearts = ThreadLocalRandom.current().nextDouble(100.0) < settings.getExtraGoldenHeartsChance();
        double absorptionAmount = Math.max(player.getAbsorptionAmount(), 16.0);
        if (extraHearts) {
            absorptionAmount = Math.max(absorptionAmount, 20.0);
        }
        player.setAbsorptionAmount(absorptionAmount);
        if (cooldownMillis > 0L) {
            this.cooldowns.put(uuid, now + cooldownMillis);
            AdminItemsCooldowns.apply(player, Material.GOLDEN_APPLE, cooldownMillis);
        }
        player.updateInventory();
    }

    private void applyEnchantedGoldenAppleEffects(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 400, 1, false, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 2400, 3, false, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 6000, 0, false, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 6000, 0, false, true, true));
    }

    private void applyFood(Player player) {
        AdminItemsConfig.InfiniteGoldenAppleSettings settings = this.config.getInfiniteGoldenApple();
        player.setFoodLevel(Math.min(20, player.getFoodLevel() + Math.max(0, settings.getFoodLevel())));
        player.setSaturation(Math.min(20.0f, player.getSaturation() + Math.max(0.0f, settings.getSaturation())));
    }

    private boolean isInfiniteGoldenApple(ItemStack item) {
        if (item == null || item.getType() != Material.GOLDEN_APPLE || !item.hasItemMeta()) {
            return false;
        }
        AdminItemsConfig.InfiniteGoldenAppleSettings settings = this.config.getInfiniteGoldenApple();
        if (settings == null || !settings.isEnabled()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        if (AdminItemsGUI.KEY_NIESKONCZONE_ZLOTE_JAPKO != null && meta.getPersistentDataContainer().has(AdminItemsGUI.KEY_NIESKONCZONE_ZLOTE_JAPKO, PersistentDataType.BYTE)) {
            return true;
        }
        if (!(settings.getCustomModelData() == 0 || meta.hasCustomModelData() && meta.getCustomModelData() == settings.getCustomModelData())) {
            return false;
        }
        return meta.hasDisplayName() && meta.getDisplayName().equals(dev.grzybalson.utils.ColorUtils.colorize(settings.getName()));
    }
}
