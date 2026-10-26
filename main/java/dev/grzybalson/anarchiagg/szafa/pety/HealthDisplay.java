package dev.grzybalson.anarchiagg.szafa.pety;

import dev.grzybalson.anarchiagg.szafa.BoostHandler;
import dev.grzybalson.anarchiagg.szafa.kostiumy.KostiumyHandler;
import dev.grzybalson.anarchiagg.szafa.pety.PetyHandler;
import dev.grzybalson.managers.MultiActionBarService;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.scheduler.BukkitRunnable;

public class HealthDisplay
extends BukkitRunnable
implements Listener {
    private static final String CHANNEL = "health-display";
    private final BoostHandler boostHandler;
    private final Map<UUID, UUID> attackerToVictim = new ConcurrentHashMap<UUID, UUID>();
    private final Map<UUID, Long> lastAttackTime = new ConcurrentHashMap<UUID, Long>();

    public HealthDisplay(BoostHandler boostHandler) {
        this.boostHandler = boostHandler;
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.isCancelled()) {
            return;
        }
        Entity entity = event.getEntity();
        if (!(entity instanceof Player)) {
            return;
        }
        Player victim = (Player)entity;
        Entity entity2 = event.getDamager();
        if (!(entity2 instanceof Player)) {
            return;
        }
        Player attacker = (Player)entity2;
        this.attackerToVictim.put(attacker.getUniqueId(), victim.getUniqueId());
        this.lastAttackTime.put(attacker.getUniqueId(), System.currentTimeMillis());
    }

    @Override
    public void run() {
        if (this.attackerToVictim.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (UUID attackerId : new java.util.ArrayList<>(this.attackerToVictim.keySet())) {
            Player player = Bukkit.getPlayer(attackerId);
            UUID victimId = this.attackerToVictim.get(attackerId);
            Long attackTime = this.lastAttackTime.get(attackerId);
            if (player == null || !player.isOnline() || victimId == null || attackTime == null) {
                this.attackerToVictim.remove(attackerId);
                this.lastAttackTime.remove(attackerId);
                if (player != null) {
                    clearHealthBar(player);
                }
                continue;
            }
            if (now - attackTime > 30000L) {
                this.attackerToVictim.remove(attackerId);
                this.lastAttackTime.remove(attackerId);
                clearHealthBar(player);
                continue;
            }
            Player victim = Bukkit.getPlayer(victimId);
            if (victim == null || !victim.isOnline()) {
                this.attackerToVictim.remove(attackerId);
                this.lastAttackTime.remove(attackerId);
                clearHealthBar(player);
                continue;
            }
            KostiumyHandler.BoostsData kostiumBoosts = this.boostHandler.getKostiumBoosts(attackerId);
            PetyHandler.BoostsData petBoosts = this.boostHandler.getPetBoosts(attackerId);
            boolean seeHealth = kostiumBoosts != null && kostiumBoosts.seeHealth() || petBoosts != null && petBoosts.seeHealth();
            if (!seeHealth) {
                this.attackerToVictim.remove(attackerId);
                this.lastAttackTime.remove(attackerId);
                clearHealthBar(player);
                continue;
            }
            double remainingHealth = victim.getHealth();
            double healthInHearts = remainingHealth / 2.0;
            String nick = victim.getName();
            org.bukkit.plugin.Plugin raw = Bukkit.getPluginManager().getPlugin("HollyCore");
            if (raw instanceof dev.grzybalson.HollyCore holly) {
                dev.grzybalson.managers.IncognitoManager incognito = holly.getIncognitoManager();
                if (incognito != null && incognito.isIncognito(victim)) {
                    nick = incognito.displayName(victim);
                }
            }
            String healthText = String.format("&7%s &#FD7A7A%.2f\u2764", nick, healthInHearts);
            sendHealthBar(player, healthText);
        }
    }

    public void clear() {
        for (UUID uuid : this.attackerToVictim.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                clearHealthBar(player);
            }
        }
        this.attackerToVictim.clear();
        this.lastAttackTime.clear();
    }

    private void sendHealthBar(Player player, String message) {
        MultiActionBarService service = MultiActionBarService.get();
        if (service != null) {
            service.send(player, CHANNEL, message, 20L);
            return;
        }
        player.sendActionBar(dev.grzybalson.utils.ColorUtils.colorizeComponent(message));
    }

    private void clearHealthBar(Player player) {
        MultiActionBarService service = MultiActionBarService.get();
        if (service != null) {
            service.remove(player.getUniqueId(), CHANNEL);
        }
    }
}
