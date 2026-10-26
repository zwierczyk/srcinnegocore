package dev.grzybalson.modules.anticheat;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AimbotCheck extends PacketListenerAbstract implements Listener {

    private static final int ALERT_VL_THRESHOLD = 10;

    private final AnticheatManager manager;
    private final Map<UUID, Float> lastYaw = new ConcurrentHashMap<>();
    private final Map<UUID, Float> lastPitch = new ConcurrentHashMap<>();

    public AimbotCheck(AnticheatManager manager) {
        super(PacketListenerPriority.HIGHEST);
        this.manager = manager;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (!manager.getConfig().isAimbot()) {
            return;
        }
        if (event.getPacketType() != PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION
            && event.getPacketType() != PacketType.Play.Client.PLAYER_ROTATION) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        WrapperPlayClientPlayerFlying packet = new WrapperPlayClientPlayerFlying(event);
        if (!packet.hasRotationChanged()) {
            return;
        }
        float yaw = packet.getLocation().getYaw();
        float pitch = packet.getLocation().getPitch();
        UUID uuid = player.getUniqueId();
        float previousYaw = lastYaw.getOrDefault(uuid, yaw);
        float previousPitch = lastPitch.getOrDefault(uuid, pitch);
        float deltaYaw = Math.abs(yaw - previousYaw);
        float deltaPitch = Math.abs(pitch - previousPitch);
        lastYaw.put(uuid, yaw);
        lastPitch.put(uuid, pitch);

        boolean snap = deltaYaw != 0 && deltaYaw % 1.0f == 0 && deltaPitch % 1.0f == 0;
        if (!snap) {
            return;
        }

        CheatPlayerData data = manager.data(player);
        int vl = data.addViolation(CheatType.AIM_BOT);
        if (vl < ALERT_VL_THRESHOLD) {
            return;
        }
        data.resetViolation(CheatType.AIM_BOT);
        int alertVl = vl;
        org.bukkit.Bukkit.getScheduler().runTask(manager.getPlugin(), () -> {
            if (player.isOnline()) {
                manager.notifyStaff(player, CheatType.AIM_BOT, alertVl, manager.getConfig().getAimbotAlert(), Map.of());
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        lastYaw.remove(uuid);
        lastPitch.remove(uuid);
    }
}
