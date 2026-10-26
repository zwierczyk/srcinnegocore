package dev.grzybalson.modules.combat;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.configs.CombatConfig;
import dev.grzybalson.managers.MultiActionBarService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class CombatDisplayListener implements Listener {

    private final HollyCore plugin;

    public CombatDisplayListener(HollyCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        CombatConfig config = combatConfig();
        if (config == null || !config.isCombatActionBarEnabled()) {
            return;
        }
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        long duration = config.getCombatActionBarDurationTicks();
        String channel = config.getCombatActionBarChannel();
        String template = config.getCombatActionBarTemplate();

        sendCombatBar(attacker, victim, channel, template, duration, event.getFinalDamage());
        // HP na actionbarze tylko dla atakującego — ofiara nie widzi cudzego/swojego HP z tego kanału.
    }

    private void sendCombatBar(Player viewer, Player target, String channel, String template, long durationTicks, double finalDamage) {
        String nick = target.getName();
        dev.grzybalson.managers.IncognitoManager incognito = plugin.getIncognitoManager();
        if (incognito != null && incognito.isIncognito(target)) {
            // Na actionbarze HP zawsze nick incognito — nie realny.
            nick = incognito.displayName(target);
        }
        String message = CombatHealthFormat.buildActionBar(target, nick, template, finalDamage);
        MultiActionBarService service = MultiActionBarService.get();
        if (service != null) {
            service.send(viewer, channel, message, durationTicks);
        }
    }

    private CombatConfig combatConfig() {
        if (plugin.getConfigManager() == null) {
            return null;
        }
        return plugin.getConfigManager().getCombatConfig();
    }
}
