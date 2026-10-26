package dev.grzybalson.anarchiagg.adminitems.listener;

import dev.grzybalson.anarchiagg.util.ItemCompatibilityUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import dev.grzybalson.HollyCore;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsConfig;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsCooldowns;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsInventoryUtil;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsRegionUtil;
import dev.grzybalson.utils.ColorUtils;

public class RodListener
implements Listener {
    private final HollyCore plugin;
    private final AdminItemsConfig config;
    private final AdminItemsConfig.RodSettings rozgaConfig;
    private final Map<UUID, Long> cooldowns = new HashMap<UUID, Long>();
    private final Map<UUID, Long> rozgaEffects = new HashMap<UUID, Long>();
    private final Map<UUID, Long> targetCooldowns = new HashMap<UUID, Long>();

    public RodListener(HollyCore plugin) {
        this.plugin = plugin;
        this.config = plugin.getAdminItemsConfig();
        this.rozgaConfig = this.config.getRod();
    }

    private boolean isRozgaItem(ItemStack item) {
        if (item == null || item.getType() != Material.STICK) {
            return false;
        }
        if (!item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (!meta.hasDisplayName()) {
            return false;
        }
        return meta.getDisplayName().equals(dev.grzybalson.utils.ColorUtils.colorize(this.rozgaConfig.getName()));
    }

    private void forceRemoveElytra(Player player) {
        ItemStack totemStack;
        ItemStack elytra = player.getInventory().getChestplate();
        if (elytra == null || elytra.getType() != Material.ELYTRA) {
            return;
        }
        int totemSlot = AdminItemsInventoryUtil.findStorageTotemSlot(player.getInventory());
        if (totemSlot != -1 && (totemStack = player.getInventory().getItem(totemSlot)) != null) {
            player.getInventory().setChestplate(null);
            if (totemStack.getAmount() > 1) {
                totemStack.setAmount(totemStack.getAmount() - 1);
            } else {
                player.getInventory().setItem(totemSlot, null);
            }
            player.getInventory().setItem(totemSlot, elytra);
            player.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(this.rozgaConfig.getName() + " &8:: &cZosta\u0142e\u015b uderzony r\u00f3zg\u0105! Usuni\u0119to 1 totem, aby bezpiecznie schowa\u0107 Twoj\u0105 elytr\u0119!"));
            return;
        }
        player.getInventory().setChestplate(null);
        HashMap left = player.getInventory().addItem(new ItemStack[]{elytra});
        if (!left.isEmpty()) {
            ItemCompatibilityUtil.dropItemNaturally(player, elytra);
        }
    }

    @EventHandler(priority=EventPriority.HIGH, ignoreCancelled=true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player) || !(event.getEntity() instanceof Player)) {
            return;
        }
        Player damager = (Player)event.getDamager();
        final Player target = (Player)event.getEntity();
        ItemStack itemInHand = damager.getInventory().getItemInMainHand();
        if (!this.isRozgaItem(itemInHand)) {
            return;
        }
        if (!this.rozgaConfig.isEnabled()) {
            damager.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(this.rozgaConfig.getName() + " &8:: &cTo narz\u0119dzie jest wy\u0142\u0105czone!"));
            return;
        }
        if (AdminItemsRegionUtil.isInBlockedRegion(damager, this.plugin) || AdminItemsRegionUtil.isInBlockedRegion(target, this.plugin)) {
            damager.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(this.rozgaConfig.getName() + " &8:: &cNie mo\u017cesz u\u017cy\u0107 r\u00f3zgi na chronionym terenie!"));
            return;
        }
        if (target.hasMetadata("event-immune")) {
            damager.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(this.rozgaConfig.getName() + " &8:: &cTen gracz ma ochron\u0119!"));
            return;
        }
        long now = System.currentTimeMillis();
        this.targetCooldowns.entrySet().removeIf(entry -> entry.getValue() <= now);
        Long targetCooldownUntil = this.targetCooldowns.get(target.getUniqueId());
        if (targetCooldownUntil != null && targetCooldownUntil > now) {
            long secondsLeft = Math.max(1L, (targetCooldownUntil - now + 999L) / 1000L);
            damager.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(this.rozgaConfig.getName() + " &8:: &cTen gracz byl juz trafiony rozga! Poczekaj jeszcze &4" + secondsLeft + "s"));
            return;
        }

        long cooldownTime = AdminItemsCooldowns.secondsToMillis(this.rozgaConfig.getCooldown());
        Long cooldownUntil = this.cooldowns.get(damager.getUniqueId());
        if (cooldownUntil != null && System.currentTimeMillis() < cooldownUntil) {
            AdminItemsCooldowns.refresh(damager, itemInHand.getType(), cooldownUntil);
            damager.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(this.rozgaConfig.getName() + " &8:: &cOdczekaj przed ponownym u\u017cyciem r\u00f3zgi!"));
            return;
        }
        this.cooldowns.remove(damager.getUniqueId());
        if (damager.isFlying() || damager.isGliding()) {
            damager.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(this.rozgaConfig.getName() + " &8:: &cNie mo\u017cesz u\u017cy\u0107 r\u00f3zgi podczas lotu!"));
            return;
        }
        if (target.isFlying() || target.isGliding()) {
            damager.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(this.rozgaConfig.getName() + " &8:: &cNie mo\u017cesz u\u017cy\u0107 r\u00f3zgi na graczu w locie!"));
            return;
        }
        long effectDuration = (long)this.rozgaConfig.getElytraBlockDuration() * 1000L;
        if (this.rozgaEffects.containsKey(target.getUniqueId()) && System.currentTimeMillis() < this.rozgaEffects.get(target.getUniqueId())) {
            damager.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(this.rozgaConfig.getName() + " &8:: &cTen gracz jest ju\u017c pod wp\u0142ywem r\u00f3\u017cgi!"));
            return;
        }
        this.rozgaEffects.put(target.getUniqueId(), now + effectDuration);
        this.targetCooldowns.put(target.getUniqueId(), now + AdminItemsCooldowns.secondsToMillis(Math.max(0, this.rozgaConfig.getTargetCooldown())));
        this.cooldowns.put(damager.getUniqueId(), System.currentTimeMillis() + cooldownTime);
        damager.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(this.rozgaConfig.getName() + " &8:: &cZablokowa\u0142e\u015b graczowi elytre na czas &f" + this.rozgaConfig.getElytraBlockDuration() + " sekund"));
        target.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(this.rozgaConfig.getName() + " &8:: &cTwoja elytra zosta\u0142a zablokowana na &f" + this.rozgaConfig.getElytraBlockDuration() + " sekund"));
        damager.getWorld().playSound(damager.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.0f, 1.0f);
        target.getWorld().playSound(target.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.0f, 1.0f);
        AdminItemsCooldowns.apply(damager, itemInHand.getType(), cooldownTime);
        target.setCooldown(Material.ELYTRA, this.rozgaConfig.getElytraBlockDuration() * 20);
        target.setCooldown(Material.FIREWORK_ROCKET, this.rozgaConfig.getElytraBlockDuration() * 20);
        target.setCooldown(Material.FISHING_ROD, this.rozgaConfig.getElytraBlockDuration() * 20);
        this.forceRemoveElytra(target);
        new BukkitRunnable(){

            public void run() {
                if (!RodListener.this.rozgaEffects.containsKey(target.getUniqueId()) || System.currentTimeMillis() >= RodListener.this.rozgaEffects.get(target.getUniqueId())) {
                    this.cancel();
                    return;
                }
                if (target.isGliding()) {
                    target.setGliding(false);
                }
                RodListener.this.forceRemoveElytra(target);
            }
        }.runTaskTimer((Plugin)this.plugin, 0L, 20L);
        new BukkitRunnable(){

            public void run() {
                RodListener.this.rozgaEffects.remove(target.getUniqueId());
                target.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(RodListener.this.rozgaConfig.getName() + " &8:: &aBlokada elytry min\u0119\u0142a!"));
            }
        }.runTaskLater((Plugin)this.plugin, (long)this.rozgaConfig.getElytraBlockDuration() * 20L);
    }

    @EventHandler
    public void onPlayerToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (this.rozgaEffects.containsKey(player.getUniqueId()) && System.currentTimeMillis() < this.rozgaEffects.get(player.getUniqueId()) && event.isFlying() && player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
            event.setCancelled(true);
            player.setFlying(false);
            player.setGliding(false);
            player.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(this.rozgaConfig.getName() + " &8:: &cTwoja elytra jest zablokowana!"));
        }
    }

    @EventHandler
    public void onEntityToggleGlide(EntityToggleGlideEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Player)) {
            return;
        }
        Player player = (Player)entity;
        if (this.rozgaEffects.containsKey(player.getUniqueId()) && System.currentTimeMillis() < this.rozgaEffects.get(player.getUniqueId()) && event.isGliding()) {
            event.setCancelled(true);
            player.setGliding(false);
            player.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(this.rozgaConfig.getName() + " &8:: &cNie mo\u017cesz szybowa\u0107 podczas blokady r\u00f3zgi!"));
        }
    }

    public Map<UUID, Long> getRodEffects() {
        return this.rozgaEffects;
    }
}
