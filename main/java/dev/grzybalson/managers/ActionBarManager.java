package dev.grzybalson.managers;

import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ColorUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Actionbar ultra-light:
 * - przy zmianie tekstu: od razu colorize + send
 * - przy tym samym tekście: keepalive co 40 ticków (bez ColorUtils)
 */
public class ActionBarManager {

    private final HollyCore plugin;
    private final Set<UUID> activePlayers;
    private final Map<UUID, String> lastRawMessage = new ConcurrentHashMap<>();
    private final Map<UUID, Component> lastComponent = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> keepaliveCounter = new ConcurrentHashMap<>();
    private final String separator = " &8| ";
    private BukkitTask sharedTask;
    private volatile boolean combatUsesBossbar;

    public ActionBarManager(HollyCore plugin) {
        this.plugin = plugin;
        this.activePlayers = ConcurrentHashMap.newKeySet();
        refreshCombatNotificationType();
        startSharedTimer();
    }

    public void refreshCombatNotificationType() {
        try {
            combatUsesBossbar = plugin.getConfigManager() != null
                && plugin.getConfigManager().getCombatConfig() != null
                && plugin.getConfigManager().getCombatConfig().getNotificationType().equalsIgnoreCase("BOSSBAR");
        } catch (Exception e) {
            combatUsesBossbar = false;
        }
    }

    private void startSharedTimer() {
        sharedTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private void tick() {
        Set<UUID> candidates = new HashSet<>(activePlayers);
        MultiActionBarService multi = MultiActionBarService.get();
        if (multi != null) {
            candidates.addAll(multi.activePlayerIds());
        }
        for (UUID uuid : candidates) {
            flushPlayer(uuid, false);
        }
    }

    public void flushPlayer(UUID uuid) {
        flushPlayer(uuid, true);
    }

    public void flushPlayer(Player player) {
        if (player != null) {
            flushPlayer(player.getUniqueId(), true);
        }
    }

    private void flushPlayer(UUID uuid, boolean immediate) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null || !player.isOnline()) {
            activePlayers.remove(uuid);
            lastRawMessage.remove(uuid);
            lastComponent.remove(uuid);
            keepaliveCounter.remove(uuid);
            return;
        }

        String message = buildActionbarMessage(player);
        MultiActionBarService multi = MultiActionBarService.get();
        if (multi != null) {
            String multiMsg = multi.peekCombined(uuid);
            if (multiMsg != null && !multiMsg.isEmpty()) {
                if (message != null && !message.isEmpty()) {
                    message = message + separator + multiMsg;
                } else {
                    message = multiMsg;
                }
            }
        }

        if (message == null || message.isEmpty()) {
            lastRawMessage.remove(uuid);
            lastComponent.remove(uuid);
            keepaliveCounter.remove(uuid);
            return;
        }

        String previous = lastRawMessage.get(uuid);
        Component component = lastComponent.get(uuid);
        if (previous != null && previous.equals(message) && component != null) {
            if (immediate) {
                player.sendActionBar(component);
                keepaliveCounter.put(uuid, 0);
                return;
            }
            // Keepalive co 2s zamiast co 1s — mniej pakietów przy 100+ graczy.
            int ticks = keepaliveCounter.merge(uuid, 1, Integer::sum);
            if (ticks >= 2) {
                keepaliveCounter.put(uuid, 0);
                player.sendActionBar(component);
            }
            return;
        }

        component = ColorUtils.colorizeComponent(message);
        lastRawMessage.put(uuid, message);
        lastComponent.put(uuid, component);
        keepaliveCounter.put(uuid, 0);
        player.sendActionBar(component);
    }

    public Consumer<UUID> changeListener() {
        return this::flushPlayer;
    }

    public void startActionbar(Player player) {
        if (player != null) {
            activePlayers.add(player.getUniqueId());
            flushPlayer(player.getUniqueId(), true);
        }
    }

    public void stopActionbar(Player player) {
        if (player != null) {
            UUID uuid = player.getUniqueId();
            activePlayers.remove(uuid);
            lastRawMessage.remove(uuid);
            lastComponent.remove(uuid);
            keepaliveCounter.remove(uuid);
        }
    }

    public void refreshActionbar(Player player) {
        if (player == null) return;
        boolean needsActionbar = needsActionbar(player);
        boolean hasActionbar = activePlayers.contains(player.getUniqueId());

        if (needsActionbar && !hasActionbar) {
            startActionbar(player);
        } else if (!needsActionbar && hasActionbar) {
            MultiActionBarService multi = MultiActionBarService.get();
            if (multi == null || !multi.hasEntries(player.getUniqueId())) {
                stopActionbar(player);
            }
        } else if (needsActionbar || (MultiActionBarService.get() != null
            && MultiActionBarService.get().hasEntries(player.getUniqueId()))) {
            flushPlayer(player.getUniqueId(), true);
        }
    }

    public void refreshAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            refreshActionbar(player);
        }
    }

    private boolean needsActionbar(Player player) {
        boolean combatActionbar = !combatUsesBossbar
                && plugin.getCombatManager() != null
                && plugin.getCombatManager().isInCombat(player);
        boolean vanishActionbar = plugin.getVanishManager() != null
                && plugin.getVanishManager().isVanished(player)
                && plugin.getConfigManager().getVanishConfig().isActionbarEnabled();
        boolean creatorActionbar = plugin.getCreatorModeManager() != null
                && plugin.getCreatorModeManager().isInCreatorMode(player)
                && plugin.getConfigManager().getCreatorModeConfig().isActionbarEnabled();
        boolean coachActionbar = plugin.getCoachManager() != null
                && plugin.getCoachManager().isActionBarEnabled();

        return combatActionbar || vanishActionbar || creatorActionbar || coachActionbar;
    }

    private String buildActionbarMessage(Player player) {
        StringBuilder sb = new StringBuilder();

        if (!combatUsesBossbar && plugin.getCombatManager() != null
                && plugin.getCombatManager().isInCombat(player)) {
            int timeLeft = plugin.getCombatManager().getRemainingCombatTime(player);
            String combatMsg = plugin.getConfigManager().getCombatConfig().getCombatMessage()
                .replace("{time}", String.valueOf(timeLeft));
            if (combatMsg != null && !combatMsg.isEmpty()) {
                sb.append(combatMsg);
            }
        }

        if (plugin.getCoachManager() != null) {
            String coachMsg = plugin.getCoachManager().getFullActionBarMessage(player);
            if (coachMsg != null && !coachMsg.isEmpty()) {
                if (sb.length() > 0) sb.append(separator);
                sb.append(coachMsg);
            }
        }

        if (plugin.getVanishManager() != null && plugin.getVanishManager().isVanished(player)) {
            if (plugin.getConfigManager().getVanishConfig().isActionbarEnabled()) {
                String vanishMsg = plugin.getConfigManager().getVanishConfig().getActionbarMessage();
                if (vanishMsg != null && !vanishMsg.isEmpty()) {
                    if (sb.length() > 0) sb.append(separator);
                    sb.append(vanishMsg);
                }
            }
        }

        if (plugin.getCreatorModeManager() != null && plugin.getCreatorModeManager().isInCreatorMode(player)) {
            if (plugin.getConfigManager().getCreatorModeConfig().isActionbarEnabled()) {
                String creatorMsg = plugin.getConfigManager().getCreatorModeConfig().getActionbarMessage();
                if (creatorMsg != null && !creatorMsg.isEmpty()) {
                    if (sb.length() > 0) sb.append(separator);
                    sb.append(creatorMsg);
                }
            }
        }

        return sb.toString();
    }

    public void handlePlayerQuit(Player player) {
        stopActionbar(player);
    }

    public void shutdown() {
        if (sharedTask != null) {
            sharedTask.cancel();
        }
        activePlayers.clear();
        lastRawMessage.clear();
        lastComponent.clear();
    }
}
