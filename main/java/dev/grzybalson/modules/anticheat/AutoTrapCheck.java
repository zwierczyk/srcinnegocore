package dev.grzybalson.modules.anticheat;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fastplace / auto-trap.
 * Cobweby są wyłączone z tego checka — w PvP stawia się je bardzo szybko (masowe FP).
 */
public final class AutoTrapCheck implements Listener {

    private static final long MACHINE_INTERVAL_MS = 45L;

    private final AnticheatManager manager;
    private final Map<UUID, List<Long>> placeTimes = new ConcurrentHashMap<>();

    public AutoTrapCheck(AnticheatManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        placeTimes.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!manager.getConfig().isAutoTrap()) {
            return;
        }

        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        // Cobweb place ≠ auto-trap — osobne checki (through-wall / offhand).
        if (event.getBlock().getType() == Material.COBWEB) {
            return;
        }
        if (manager.data(player).getBlockPlaceUntil() >= System.currentTimeMillis()) {
            event.setCancelled(true);
            event.setBuild(false);
            return;
        }

        long now = System.currentTimeMillis();
        long window = Math.max(manager.getConfig().getAutoTrapTimeWindowMs(), 320L);
        int maxBlocks = Math.max(manager.getConfig().getAutoTrapMaxBlocks(), 5);

        List<Long> times = placeTimes.computeIfAbsent(player.getUniqueId(), ignored -> new ArrayList<>());
        Iterator<Long> iterator = times.iterator();
        while (iterator.hasNext()) {
            if (now - iterator.next() > Math.max(window, 500L)) {
                iterator.remove();
            }
        }
        times.add(now);

        boolean burst = times.size() >= maxBlocks && (now - times.get(times.size() - maxBlocks)) <= window;
        boolean machine = isMachinePlace(times);
        if (!burst && !machine) {
            return;
        }

        int vl = manager.data(player).addViolation(CheatType.AUTO_TRAP);
        // Nie cancel od razu — dopiero powtórzenia (mniej FP przy burście budowania).
        if (vl < 2) {
            return;
        }

        event.setCancelled(true);
        event.setBuild(false);
        manager.punishBlockPlace(player, event.getBlock().getType(), 20 * 3, 2000L);

        int counted = burst ? maxBlocks : times.size();
        long elapsed = Math.max(1L, now - times.get(Math.max(0, times.size() - counted)));
        Player target = findNearbyTarget(player, event.getBlock());
        String targetText = target != null ? " na " + target.getName() : "";

        if (vl >= 3) {
            manager.notifyStaff(player, CheatType.AUTO_TRAP, vl,
                manager.getConfig().getAutoTrapAlert(),
                Map.of(
                    "{count}", String.valueOf(counted),
                    "{time}", String.valueOf(elapsed),
                    "{target}", targetText
                ));
            if (vl >= 10) {
                manager.data(player).resetViolation(CheatType.AUTO_TRAP);
            }
        }
        times.clear();
    }

    private boolean isMachinePlace(List<Long> times) {
        if (times.size() < 4) {
            return false;
        }
        for (int i = times.size() - 3; i < times.size(); i++) {
            if (times.get(i) - times.get(i - 1) > MACHINE_INTERVAL_MS) {
                return false;
            }
        }
        return true;
    }

    private Player findNearbyTarget(Player placer, Block placed) {
        Location center = placed.getLocation().add(0.5, 0.5, 0.5);
        Player closest = null;
        double best = 25.0;
        for (Player other : placer.getWorld().getPlayers()) {
            if (other.equals(placer) || other.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            double distance = other.getLocation().distanceSquared(center);
            if (distance <= best) {
                best = distance;
                closest = other;
            }
        }
        return closest;
    }
}
