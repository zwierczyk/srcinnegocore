package dev.grzybalson.listeners;

import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CommandCooldownListener implements Listener {

    private final HollyCore plugin;
    private final Map<UUID, CommandSpamState> globalCooldowns;
    private static final long COOLDOWN_MS = 1000L;
    private static final int MAX_FAST_COMMANDS = 3;

    public CommandCooldownListener(HollyCore plugin) {
        this.plugin = plugin;
        this.globalCooldowns = new ConcurrentHashMap<>();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (player.hasPermission("Holly.bypass.commandcooldown")) {
            return;
        }

        CommandSpamState state = globalCooldowns.get(uuid);
        long now = System.currentTimeMillis();

        if (state == null || now - state.windowStart() >= COOLDOWN_MS) {
            globalCooldowns.put(uuid, new CommandSpamState(now, 1));
            return;
        }

        int commandCount = state.commandCount() + 1;
        globalCooldowns.put(uuid, new CommandSpamState(state.windowStart(), commandCount));

        if (commandCount > MAX_FAST_COMMANDS) {
            event.setCancelled(true);

            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            player.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cZwolnij! Nie spamuj komendami."));
        }
    }

    public void clearCooldown(UUID uuid) {
        globalCooldowns.remove(uuid);
    }

    public void clearAllCooldowns() {
        globalCooldowns.clear();
    }

    private record CommandSpamState(long windowStart, int commandCount) {
    }
}
