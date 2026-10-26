package dev.grzybalson.modules.combat;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.configs.CombatConfig;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.block.Action;
import org.bukkit.util.Vector;

public class CombatListener implements Listener {

    private final HollyCore plugin;
    private final CombatManager combatManager;

    public CombatListener(HollyCore plugin) {
        this.plugin = plugin;
        this.combatManager = plugin.getCombatManager();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        Player attacker = null;

        if (event.getDamager() instanceof Player) {
            attacker = (Player) event.getDamager();
        } else if (event.getDamager() instanceof org.bukkit.entity.Projectile projectile) {
            if (projectile.getShooter() instanceof Player) {
                attacker = (Player) projectile.getShooter();
            }
        }

        if (attacker == null || attacker.equals(victim)) return;

        combatManager.tagPlayer(victim, attacker);

        plugin.getTeleportRequestManager().cancelPendingTeleport(victim);
        plugin.getTeleportRequestManager().cancelPendingTeleport(attacker);
        if (plugin.getTeleportAnimator() != null) {
            plugin.getTeleportAnimator().cancel(victim.getUniqueId(), true);
            plugin.getTeleportAnimator().cancel(attacker.getUniqueId(), true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // Optymalizacja: ignoruj same obroty głowy (nie zmieniają pozycji bloku)
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }

        // Szybka wczesna weryfikacja - najpierw sprawdź światy (rzadko się zmieniają)
        if (from.getWorld() != to.getWorld()) {
            return;
        }

        // Ignoruj ruchy które nie zmieniają pozycji bloku (tylko obroty głowy)
        if (from.getBlockX() == to.getBlockX() &&
            from.getBlockY() == to.getBlockY() &&
            from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();

        // Wczesne sprawdzenie combatu - jeśli nie jest w walce, nie rób nic więcej
        if (!combatManager.isInCombat(player)) {
            return;
        }

        if (!combatManager.tryEnterSafeRegion(player, from, to)) {
            event.setTo(from);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEnderPearlThrow(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        if (!combatManager.isInCombat(player)) {
            return;
        }
        if (event.getItem() == null || event.getItem().getType() != Material.ENDER_PEARL) {
            return;
        }

        Location from = player.getLocation();
        Location probe = from.clone();
        // from sie nie zmienia — liczymy kierunek raz zamiast 64 razy.
        Vector step = from.getDirection();
        for (int i = 1; i <= 64; i++) {
            probe.add(step);
            if (combatManager.isInSafeRegionOrPvpDeny(probe)) {
                event.setCancelled(true);
                combatManager.tryEnterSafeRegion(player, from, probe);
                player.updateInventory();
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEnderPearlTeleport(PlayerTeleportEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
            return;
        }
        Player player = event.getPlayer();
        if (!combatManager.isInCombat(player) || event.getTo() == null) {
            return;
        }
        if (!combatManager.tryEnterSafeRegion(player, event.getFrom(), event.getTo())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEnderchestOpen(InventoryOpenEvent event) {
        if (event.getInventory().getType() != InventoryType.ENDER_CHEST) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (combatManager.blockEnderchest(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (combatManager.isInCombat(player)) {
            combatManager.handleCombatLogout(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        combatManager.handlePlayerDeath(player);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();

        if (!combatManager.isInCombat(player)) return;

        CombatConfig config = plugin.getConfigManager().getCombatConfig();
        if (!config.isBlockCommandsInCombat()) return;

        String command = event.getMessage().substring(1).split(" ")[0].toLowerCase(java.util.Locale.ROOT);
        if (command.contains(":")) {
            command = command.substring(command.indexOf(':') + 1);
        }
        final String baseCommand = command;

        // Zawsze pozwól na szybkie zgłoszenie w walce (niezależnie od starego combat.yml).
        if (baseCommand.equals("szybkiezgloszenie") || baseCommand.equals("fastreport")) {
            return;
        }

        if (config.getAllowedCommandsInCombat().stream()
                .anyMatch(allowed -> allowed.equalsIgnoreCase(baseCommand))) {
            return;
        }

        if (baseCommand.equalsIgnoreCase("lobby") && combatManager.isInSafeRegionOrPvpDeny(player.getLocation())) {
            return;
        }
        if ((baseCommand.equalsIgnoreCase("spawn") || baseCommand.equalsIgnoreCase("lobby"))
            && plugin.getGroupTeleportManager() != null
            && plugin.getGroupTeleportManager().isInTeleportWorld(player)) {
            return;
        }

        event.setCancelled(true);
        player.sendMessage(ColorUtils.colorizeComponent(config.getCommandBlockedMessage()));
    }
}
