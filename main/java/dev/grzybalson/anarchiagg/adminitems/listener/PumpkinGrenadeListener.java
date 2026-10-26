package dev.grzybalson.anarchiagg.adminitems.listener;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitRunnable;
import dev.grzybalson.HollyCore;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsConfig;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsCooldowns;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsGUI;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsRegionUtil;
import dev.grzybalson.utils.ColorUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PumpkinGrenadeListener implements Listener {

    private final HollyCore plugin;
    private final AdminItemsConfig config;

    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, Long> cursedPlayers = new HashMap<>();

    public PumpkinGrenadeListener(HollyCore plugin) {
        this.plugin = plugin;
        this.config = plugin.getAdminItemsConfig();
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (!isPumpkinGrenade(item)) {
            return;
        }

        if (!event.getAction().toString().contains("RIGHT_CLICK")) {
            return;
        }

        event.setCancelled(true);

        if (AdminItemsRegionUtil.isInBlockedRegion(player, plugin)) {
            player.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(
                    "&#FFAB57&lᴅ&#FCA752&lʏ&#F9A24C&lɴ&#F59E47&lɪ&#F29A41&lᴏ&#EF953C&lɢ&#EC9136&lʟ&#E88D31&lᴏ&#E5892C&lᴡ&#E28426&lʏ &#DB7C1B&lɢ&#D87716&lʀ&#D57310&lᴀ&#D26F0B&lɴ&#CE6A05&lᴀ&#CB6600&lᴛ &8:: &cNie możesz używać tego przedmiotu na spawnie!"
            ));
            return;
        }

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        long cooldownMillis = AdminItemsCooldowns.secondsToMillis(config.getPumpkinGrenade().getCooldown());

        if (cooldowns.containsKey(uuid)) {

            long cooldownUntil = cooldowns.get(uuid);
            long timeLeft = cooldownUntil - now;

            if (timeLeft > 0) {
                AdminItemsCooldowns.refresh(player, item.getType(), cooldownUntil);
                player.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(
                        "&#FFAB57&lᴅ&#FCA752&lʏ&#F9A24C&lɴ&#F59E47&lɪ&#F29A41&lᴏ&#EF953C&lɢ&#EC9136&lʟ&#E88D31&lᴏ&#E5892C&lᴡ&#E28426&lʏ &#DB7C1B&lɢ&#D87716&lʀ&#D57310&lᴀ&#D26F0B&lɴ&#CE6A05&lᴀ&#CB6600&lᴛ &8:: &cMusisz poczekać jeszcze &4" + (timeLeft / 1000) + "s"
                ));
                return;
            }
            dev.grzybalson.anarchiagg.util.MapCompatibilityUtil.remove(cooldowns, uuid, cooldownUntil);
        }

        if (cooldownMillis > 0L) {
            cooldowns.put(uuid, now + cooldownMillis);
            AdminItemsCooldowns.apply(player, item.getType(), cooldownMillis);
        }

        ThrownPotion potion = player.launchProjectile(ThrownPotion.class);

        potion.setShooter((ProjectileSource) player);
        potion.setMetadata(
                "PumpkinGrenade",
                new FixedMetadataValue(plugin, true)
        );

        potion.setItem(createSplashPotion());

        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }
    }

    @EventHandler
    public void onPotionSplash(PotionSplashEvent event) {

        if (!event.getPotion().hasMetadata("PumpkinGrenade")) {
            return;
        }

        if (!(event.getPotion().getShooter() instanceof Player shooter)) {
            return;
        }

        int radius = config.getPumpkinGrenade().getRadius();
        int duration = config.getPumpkinGrenade().getDuration();

        boolean anyTargetAffected = false;

        for (LivingEntity entity : event.getAffectedEntities()) {

            if (!(entity instanceof Player target)) {
                continue;
            }

            if (target.getLocation().distance(event.getPotion().getLocation()) > radius) {
                continue;
            }

            if (target.getInventory().firstEmpty() == -1) {
                target.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(
                        "&#FFAB57&lᴅ&#FCA752&lʏ&#F9A24C&lɴ&#F59E47&lɪ&#F29A41&lᴏ&#EF953C&lɢ&#EC9136&lʟ&#E88D31&lᴏ&#E5892C&lᴡ&#E28426&lʏ &#DB7C1B&lɢ&#D87716&lʀ&#D57310&lᴀ&#D26F0B&lɴ&#CE6A05&lᴀ&#CB6600&lᴛ &8:: &cTwój ekwipunek jest pełny!"
                ));
                continue;
            }

            if (cursedPlayers.containsKey(target.getUniqueId())
                    && System.currentTimeMillis() < cursedPlayers.get(target.getUniqueId())) {

                target.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(
                        "&#FFAB57&lᴅ&#FCA752&lʏ&#F9A24C&lɴ&#F59E47&lɪ&#F29A41&lᴏ&#EF953C&lɢ&#EC9136&lʟ&#E88D31&lᴏ&#E5892C&lᴡ&#E28426&lʏ &#DB7C1B&lɢ&#D87716&lʀ&#D57310&lᴀ&#D26F0B&lɴ&#CE6A05&lᴀ&#CB6600&lᴛ &8:: &cJesteś już pod wpływem klątwy!"
                ));
                continue;
            }

            anyTargetAffected = true;

            ItemStack oldHelmet = target.getInventory().getHelmet();

            cursedPlayers.put(
                    target.getUniqueId(),
                    System.currentTimeMillis() + (duration * 1000L)
            );

            target.getInventory().setHelmet(createPumpkin());

            dev.grzybalson.anarchiagg.util.TextUtil.sendTitle(target,
                    "",
                    dev.grzybalson.utils.ColorUtils.colorize("&cTwój hełm został zdjęty!"),
                    10,
                    40,
                    10
            );

            target.playSound(
                    target.getLocation(),
                    Sound.ENTITY_EVOKER_HURT,
                    1.0F,
                    1.0F
            );

            new BukkitRunnable() {
                @Override
                public void run() {

                    target.getInventory().setHelmet(oldHelmet);

                    dev.grzybalson.anarchiagg.util.TextUtil.sendTitle(target,
                            "",
                            dev.grzybalson.utils.ColorUtils.colorize("&#36ff9aMożesz założyć swój hełm z powrotem"),
                            10,
                            40,
                            10
                    );

                    cursedPlayers.remove(target.getUniqueId());
                }
            }.runTaskLater(plugin, duration * 20L);
        }

        if (anyTargetAffected) {
            shooter.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(
                    "&#FFAB57&lᴅ&#FCA752&lʏ&#F9A24C&lɴ&#F59E47&lɪ&#F29A41&lᴏ&#EF953C&lɢ&#EC9136&lʟ&#E88D31&lᴏ&#E5892C&lᴡ&#E28426&lʏ &#DB7C1B&lɢ&#D87716&lʀ&#D57310&lᴀ&#D26F0B&lɴ&#CE6A05&lᴀ&#CB6600&lᴛ &8:: &aKlątwa została rzucona pomyślnie!"
            ));
        } else {
            shooter.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(
                    "&#FFAB57&lᴅ&#FCA752&lʏ&#F9A24C&lɴ&#F59E47&lɪ&#F29A41&lᴏ&#EF953C&lɢ&#EC9136&lʟ&#E88D31&lᴏ&#E5892C&lᴡ&#E28426&lʏ &#DB7C1B&lɢ&#D87716&lʀ&#D57310&lᴀ&#D26F0B&lɴ&#CE6A05&lᴀ&#CB6600&lᴛ &8:: &cNikt nie został trafiony!"
            ));
        }
    }

    private boolean isPumpkinGrenade(ItemStack item) {

        if (item == null) {
            return false;
        }

        if (!item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();

        if (AdminItemsGUI.KEY_DYNIO_GRANAT != null
                && meta.getPersistentDataContainer().has(AdminItemsGUI.KEY_DYNIO_GRANAT, PersistentDataType.BYTE)) {
            return true;
        }

        return item.getType() == Material.JACK_O_LANTERN
                && meta.hasCustomModelData()
                && meta.getCustomModelData()
                == config.getPumpkinGrenade().getCustomModelData();
    }

    private ItemStack createSplashPotion() {
        return new ItemStack(Material.SPLASH_POTION);
    }

    private ItemStack createPumpkin() {

        ItemStack pumpkin = new ItemStack(Material.JACK_O_LANTERN);

        ItemMeta meta = pumpkin.getItemMeta();

        if (meta == null) {
            return pumpkin;
        }

        meta.addEnchant(Enchantment.BINDING_CURSE, 1, true);
        meta.addEnchant(Enchantment.VANISHING_CURSE, 1, true);

        meta.setCustomModelData(
                config.getPumpkinGrenade().getCustomModelData()
        );

        pumpkin.setItemMeta(meta);

        return pumpkin;
    }

    public void resetCooldown(UUID uuid) {
        cooldowns.remove(uuid);
    }
}
