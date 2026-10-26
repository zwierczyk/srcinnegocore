package dev.grzybalson.anarchiagg.adminitems.listener;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import dev.grzybalson.HollyCore;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsConfig;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsCooldowns;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsRegionUtil;
import dev.grzybalson.utils.ColorUtils;

public class MinerCurseListener
implements Listener {
    private final HollyCore plugin;
    private final AdminItemsConfig config;
    private final NamespacedKey minerCurseKey;
    private final Set<UUID> cursedPlayers = new HashSet<UUID>();
    private final Map<UUID, Long> cooldownPlayers = new HashMap<UUID, Long>();

    public MinerCurseListener(HollyCore plugin) {
        this.plugin = plugin;
        this.config = plugin.getAdminItemsConfig();
        this.minerCurseKey = new NamespacedKey((Plugin)plugin, "admin_item_klatwa_gornika");
    }

    @EventHandler(priority=EventPriority.HIGH, ignoreCancelled=true)
    public void onPlayerHit(EntityDamageByEntityEvent event) {
        Entity entity = event.getDamager();
        if (!(entity instanceof Player)) {
            return;
        }
        Player attacker = (Player)entity;
        Entity entity2 = event.getEntity();
        if (!(entity2 instanceof Player)) {
            return;
        }
        Player target = (Player)entity2;
        AdminItemsConfig.MinerCurseSettings klatwa = this.config.getMinerCurse();
        if (!klatwa.isEnabled()) {
            return;
        }
        ItemStack item = attacker.getInventory().getItemInMainHand();
        if (item.getType() != Material.GOLDEN_PICKAXE) {
            return;
        }
        if (!item.hasItemMeta()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        if (!this.isMinerCurseItem(meta, klatwa)) {
            return;
        }
        if (AdminItemsRegionUtil.isInBlockedRegion(attacker, this.plugin) || AdminItemsRegionUtil.isInBlockedRegion(target, this.plugin)) {
            attacker.sendMessage(dev.grzybalson.utils.ColorUtils.colorize("&#00BC08&l\u1d0b&#0EBD16&l\u029f&#1DBF24&l\u1d00&#2BC031&l\u1d1b&#39C23F&l\u1d21&#48C34D&l\u1d00 &#64C668&l\u0262&#72C876&l\u00f3&#81C984&l\u0280&#8FCB92&l\u0274&#9DCC9F&l\u026a&#ACCEAD&l\u1d0b&#BACFBB&l\u1d00 &8:: &cNie mo\u017cesz u\u017cy\u0107 przedmiotu w tym miejscu!"));
            return;
        }
        UUID attackerUUID = attacker.getUniqueId();
        UUID targetUUID = target.getUniqueId();
        long now = System.currentTimeMillis();
        long cooldownMillis = AdminItemsCooldowns.secondsToMillis(klatwa.getCooldown());
        long cooldownTicks = AdminItemsCooldowns.secondsToTicks(klatwa.getCooldown());
        Long cooldownUntil = this.cooldownPlayers.get(attackerUUID);
        if (cooldownUntil != null && cooldownUntil > now) {
            AdminItemsCooldowns.refresh(attacker, item.getType(), cooldownUntil);
            attacker.sendMessage(dev.grzybalson.utils.ColorUtils.colorize("&#00BC08&l\u1d0b&#0EBD16&l\u029f&#1DBF24&l\u1d00&#2BC031&l\u1d1b&#39C23F&l\u1d21&#48C34D&l\u1d00 &#64C668&l\u0262&#72C876&l\u00f3&#81C984&l\u0280&#8FCB92&l\u0274&#9DCC9F&l\u026a&#ACCEAD&l\u1d0b&#BACFBB&l\u1d00 &8:: &cPrzedmiot posiada cooldown!"));
            return;
        }
        if (this.cursedPlayers.contains(targetUUID)) {
            attacker.sendMessage(dev.grzybalson.utils.ColorUtils.colorize("&#00BC08&l\u1d0b&#0EBD16&l\u029f&#1DBF24&l\u1d00&#2BC031&l\u1d1b&#39C23F&l\u1d21&#48C34D&l\u1d00 &#64C668&l\u0262&#72C876&l\u00f3&#81C984&l\u0280&#8FCB92&l\u0274&#9DCC9F&l\u026a&#ACCEAD&l\u1d0b&#BACFBB&l\u1d00 &8:: &cTen gracz jest ju\u017c pod kl\u0105tw\u0105!"));
            return;
        }
        this.cursedPlayers.add(targetUUID);
        int effectDurationTicks = Math.max(1, klatwa.getDuration()) * 20;
        target.addPotionEffect(new PotionEffect(
                PotionEffectType.SLOW_DIGGING,
                effectDurationTicks,
                2,
                false,
                true,
                true
        ), true);
        target.sendMessage(dev.grzybalson.utils.ColorUtils.colorize("&#00BC08&l\u1d0b&#0EBD16&l\u029f&#1DBF24&l\u1d00&#2BC031&l\u1d1b&#39C23F&l\u1d21&#48C34D&l\u1d00 &#64C668&l\u0262&#72C876&l\u00f3&#81C984&l\u0280&#8FCB92&l\u0274&#9DCC9F&l\u026a&#ACCEAD&l\u1d0b&#BACFBB&l\u1d00 &8:: &cNie mo\u017cesz kopa\u0107 blok\u00f3w przez &e" + klatwa.getDuration() + " &csekund!"));
        attacker.sendMessage(dev.grzybalson.utils.ColorUtils.colorize("&#00BC08&l\u1d0b&#0EBD16&l\u029f&#1DBF24&l\u1d00&#2BC031&l\u1d1b&#39C23F&l\u1d21&#48C34D&l\u1d00 &#64C668&l\u0262&#72C876&l\u00f3&#81C984&l\u0280&#8FCB92&l\u0274&#9DCC9F&l\u026a&#ACCEAD&l\u1d0b&#BACFBB&l\u1d00 &8:: &aNa\u0142o\u017cono Kl\u0105tw\u0119 G\u00f3rnika na gracza &2" + target.getName() + "&a!"));
        long newCooldownUntil = now + cooldownMillis;
        this.cooldownPlayers.put(attackerUUID, newCooldownUntil);
        AdminItemsCooldowns.apply(attacker, item.getType(), cooldownMillis);
        this.plugin.getServer().getScheduler().runTaskLater((Plugin)this.plugin, () -> dev.grzybalson.anarchiagg.util.MapCompatibilityUtil.remove(this.cooldownPlayers, attackerUUID, newCooldownUntil), cooldownTicks);
        long durationTicks = (long)klatwa.getDuration() * 20L;
        this.plugin.getServer().getScheduler().runTaskLater((Plugin)this.plugin, () -> {
            this.cursedPlayers.remove(targetUUID);
            Player online = this.plugin.getServer().getPlayer(targetUUID);
            if (online != null && online.isOnline()) {
                online.sendMessage(dev.grzybalson.utils.ColorUtils.colorize("&#00BC08&l\u1d0b&#0EBD16&l\u029f&#1DBF24&l\u1d00&#2BC031&l\u1d1b&#39C23F&l\u1d21&#48C34D&l\u1d00 &#64C668&l\u0262&#72C876&l\u00f3&#81C984&l\u0280&#8FCB92&l\u0274&#9DCC9F&l\u026a&#ACCEAD&l\u1d0b&#BACFBB&l\u1d00 &8:: &aMo\u017cesz znowu kopa\u0107 bloki."));
            }
        }, durationTicks);
    }

    private boolean isMinerCurseItem(ItemMeta meta, AdminItemsConfig.MinerCurseSettings settings) {
        if (meta.getPersistentDataContainer().has(this.minerCurseKey, PersistentDataType.BYTE)) {
            return true;
        }
        int requiredCMD = settings.getCustomModelData();
        if (!(requiredCMD == 0 || meta.hasCustomModelData() && meta.getCustomModelData() == requiredCMD)) {
            return false;
        }
        return meta.hasDisplayName() && meta.getDisplayName().equals(dev.grzybalson.utils.ColorUtils.colorize(settings.getName()));
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=false)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!this.cursedPlayers.contains(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        player.sendMessage(dev.grzybalson.utils.ColorUtils.colorize("&#00BC08&l\u1d0b&#0EBD16&l\u029f&#1DBF24&l\u1d00&#2BC031&l\u1d1b&#39C23F&l\u1d21&#48C34D&l\u1d00 &#64C668&l\u0262&#72C876&l\u00f3&#81C984&l\u0280&#8FCB92&l\u0274&#9DCC9F&l\u026a&#ACCEAD&l\u1d0b&#BACFBB&l\u1d00 &8:: &cPosiadasz kl\u0105tw\u0119 g\u00f3rnika, nie mo\u017cesz kopa\u0107 blok\u00f3w!"));
    }
}
