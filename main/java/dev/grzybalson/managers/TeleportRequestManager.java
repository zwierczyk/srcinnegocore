package dev.grzybalson.managers;

import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TeleportRequestManager {

    private final HollyCore plugin;
    private final Map<UUID, Map<UUID, TeleportRequest>> requests; // Target -> Sender -> Request
    private final Map<UUID, Map<UUID, BukkitTask>> expiryTasks; // Target -> Sender -> Task
    private final Map<UUID, Long> tpaCooldowns;
    private final Map<UUID, BukkitTask> pendingTeleports;
    private static final long TPA_COOLDOWN_MS = 5000;

    public TeleportRequestManager(HollyCore plugin) {
        this.plugin = plugin;
        this.requests = new ConcurrentHashMap<>();
        this.expiryTasks = new ConcurrentHashMap<>();
        this.tpaCooldowns = new ConcurrentHashMap<>();
        this.pendingTeleports = new ConcurrentHashMap<>();
    }

    public boolean isOnCooldown(UUID playerUuid) {
        Long lastUse = tpaCooldowns.get(playerUuid);
        if (lastUse == null) return false;
        return System.currentTimeMillis() - lastUse < TPA_COOLDOWN_MS;
    }

    public long getRemainingCooldown(UUID playerUuid) {
        Long lastUse = tpaCooldowns.get(playerUuid);
        if (lastUse == null) return 0;
        long remaining = TPA_COOLDOWN_MS - (System.currentTimeMillis() - lastUse);
        return Math.max(0, remaining);
    }

    public void setCooldown(UUID playerUuid) {
        tpaCooldowns.put(playerUuid, System.currentTimeMillis());
    }

    public void createRequest(Player sender, Player target, RequestType type) {
        UUID senderUUID = sender.getUniqueId();
        UUID targetUUID = target.getUniqueId();

        Map<UUID, BukkitTask> currentExpiryTasks = expiryTasks.computeIfAbsent(targetUUID, k -> new ConcurrentHashMap<>());
        if (currentExpiryTasks.containsKey(senderUUID)) {
            currentExpiryTasks.get(senderUUID).cancel();
        }

        TeleportRequest request = new TeleportRequest(senderUUID, targetUUID, type);
        requests.computeIfAbsent(targetUUID, k -> new ConcurrentHashMap<>()).put(senderUUID, request);
        setCooldown(senderUUID);

        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Map<UUID, TeleportRequest> targetRequests = requests.get(targetUUID);
            if (targetRequests != null && targetRequests.remove(senderUUID) != null) {
                Player senderPlayer = Bukkit.getPlayer(senderUUID);
                if (senderPlayer != null) {
                    senderPlayer.sendMessage(ColorUtils.colorizeComponent(
                        plugin.getConfigManager().getMessagesConfig().getTpaExpired()
                    ));
                }
            }
            Map<UUID, BukkitTask> targetExpiry = expiryTasks.get(targetUUID);
            if (targetExpiry != null) {
                targetExpiry.remove(senderUUID);
            }
        }, 60 * 20L);

        currentExpiryTasks.put(senderUUID, task);
    }

    public TeleportRequest getLatestRequest(UUID target) {
        Map<UUID, TeleportRequest> targetRequests = requests.get(target);
        if (targetRequests == null || targetRequests.isEmpty()) return null;

        TeleportRequest latest = null;
        for (TeleportRequest req : targetRequests.values()) {
            if (latest == null || req.getTimestamp() > latest.getTimestamp()) {
                latest = req;
            }
        }
        return latest;
    }

    public List<TeleportRequest> getAllRequests(UUID target) {
        Map<UUID, TeleportRequest> targetRequests = requests.get(target);
        if (targetRequests == null) return new ArrayList<>();
        return new ArrayList<>(targetRequests.values());
    }

    public TeleportRequest removeLatestRequest(UUID target) {
        TeleportRequest latest = getLatestRequest(target);
        if (latest != null) {
            removeRequest(target, latest.getSender());
        }
        return latest;
    }

    public TeleportRequest removeRequest(UUID target, UUID sender) {
        Map<UUID, BukkitTask> targetExpiry = expiryTasks.get(target);
        if (targetExpiry != null) {
            BukkitTask task = targetExpiry.remove(sender);
            if (task != null) task.cancel();
        }

        Map<UUID, TeleportRequest> targetRequests = requests.get(target);
        if (targetRequests != null) {
            return targetRequests.remove(sender);
        }
        return null;
    }

    public void acceptRequest(Player target) {
        TeleportRequest request = removeLatestRequest(target.getUniqueId());
        if (request == null) {
            target.sendMessage(ColorUtils.colorizeComponent(
                plugin.getConfigManager().getMessagesConfig().getNoTpaRequest()
            ));
            return;
        }
        processAcceptance(target, request);
    }

    public void acceptAllRequests(Player target) {
        List<TeleportRequest> all = getAllRequests(target.getUniqueId());
        if (all.isEmpty()) {
            target.sendMessage(ColorUtils.colorizeComponent(
                plugin.getConfigManager().getMessagesConfig().getNoTpaRequest()
            ));
            return;
        }

        for (TeleportRequest request : all) {
            removeRequest(target.getUniqueId(), request.getSender());
            processAcceptance(target, request);
        }
    }

    private void processAcceptance(Player target, TeleportRequest request) {
        Player sender = Bukkit.getPlayer(request.getSender());
        if (sender == null) {
            target.sendMessage(ColorUtils.colorizeComponent(
                plugin.getConfigManager().getMessagesConfig().getPlayerNotFound()
            ));
            return;
        }

        final Location targetLocation = target.getLocation().clone();
        final Location senderStartLoc = sender.getLocation().clone();
        final UUID senderUUID = request.getSender();
        final UUID targetUUID = request.getTarget();
        final RequestType type = request.getType();

        sender.sendMessage(ColorUtils.colorizeComponent(
            plugin.getConfigManager().getMessagesConfig().getTpaTeleportingMessage()
        ));
        target.sendMessage(ColorUtils.colorizeComponent(
            plugin.getConfigManager().getMessagesConfig().getTpaAccepted()
        ));

        BukkitTask teleportTask = new BukkitRunnable() {
            private int remaining = 5;

            @Override
            public void run() {
                Player senderPlayer = Bukkit.getPlayer(senderUUID);
                if (senderPlayer == null || !senderPlayer.isOnline()) {
                    pendingTeleports.remove(senderUUID);
                    cancel();
                    return;
                }

                if (hasMoved(senderPlayer.getLocation(), senderStartLoc)) {
                    pendingTeleports.remove(senderUUID);
                    senderPlayer.sendMessage(ColorUtils.colorizeComponent(
                        plugin.getConfigManager().getMessagesConfig().getTpaTeleportCancelled()
                    ));
                    cancel();
                    return;
                }

                if (remaining > 0) {
                    if (plugin.getTitleAddonManager() != null) {
                        plugin.getTitleAddonManager().sendTeleportCountdown(senderPlayer, remaining);
                    }
                    remaining--;
                    return;
                }

                pendingTeleports.remove(senderUUID);

                if (type == RequestType.TPA) {
                    senderPlayer.teleportAsync(targetLocation).thenAccept(success -> Bukkit.getScheduler().runTask(plugin, () -> {
                        if (success) {
                            playTeleportSuccess(senderPlayer);
                        } else {
                            senderPlayer.sendMessage(ColorUtils.error("Teleportacja nie powiodła się!"));
                        }
                    }));
                } else {
                    Player targetPlayer = Bukkit.getPlayer(targetUUID);
                    if (targetPlayer != null && targetPlayer.isOnline()) {
                        targetPlayer.teleportAsync(senderPlayer.getLocation()).thenAccept(success -> Bukkit.getScheduler().runTask(plugin, () -> {
                            if (success) {
                                playTeleportSuccess(targetPlayer);
                            }
                        }));
                    }
                }
                cancel();
            }
        }.runTaskTimer(plugin, 0L, 20L);

        pendingTeleports.put(senderUUID, teleportTask);
    }

    private void playTeleportSuccess(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
        if (plugin.getTitleAddonManager() != null) {
            plugin.getTitleAddonManager().sendTeleportSuccess(player);
        } else {
            var messages = plugin.getConfigManager().getMessagesConfig();
            ColorUtils.sendTitle(
                player,
                messages.getSpawnTeleportSuccessTitle(),
                messages.getSpawnTeleportSuccessSubtitle(),
                2,
                36,
                6
            );
        }
        player.sendMessage(ColorUtils.success("Teleportacja zakończona!"));
    }

    private boolean hasMoved(Location currentLocation, Location startLocation) {
        if (currentLocation == null || startLocation == null) {
            return true;
        }
        if (currentLocation.getWorld() == null || startLocation.getWorld() == null) {
            return true;
        }
        if (!currentLocation.getWorld().equals(startLocation.getWorld())) {
            return true;
        }
        return currentLocation.distanceSquared(startLocation) > 1;
    }

    public void cancelPendingTeleport(Player player) {
        BukkitTask task = pendingTeleports.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
            player.sendMessage(ColorUtils.colorizeComponent(
                plugin.getConfigManager().getMessagesConfig().getTpaTeleportCancelled()
            ));
        }
    }

    public boolean hasPendingTeleport(UUID playerUuid) {
        return pendingTeleports.containsKey(playerUuid);
    }

    public void denyRequest(Player target) {
        TeleportRequest request = removeLatestRequest(target.getUniqueId());
        if (request == null) {
            target.sendMessage(ColorUtils.colorizeComponent(
                plugin.getConfigManager().getMessagesConfig().getNoTpaRequest()
            ));
            return;
        }

        Player sender = Bukkit.getPlayer(request.getSender());
        if (sender != null) {
            sender.sendMessage(ColorUtils.colorizeComponent(
                plugin.getConfigManager().getMessagesConfig().getTpaDenied()
            ));
        }
        target.sendMessage(ColorUtils.colorizeComponent(
            plugin.getConfigManager().getMessagesConfig().getTpaDenied()
        ));
    }

    public void denyAllRequests(Player target) {
        List<TeleportRequest> all = getAllRequests(target.getUniqueId());
        if (all.isEmpty()) {
            target.sendMessage(ColorUtils.colorizeComponent(
                plugin.getConfigManager().getMessagesConfig().getNoTpaRequest()
            ));
            return;
        }

        for (TeleportRequest request : all) {
            removeRequest(target.getUniqueId(), request.getSender());
            Player sender = Bukkit.getPlayer(request.getSender());
            if (sender != null) {
                sender.sendMessage(ColorUtils.colorizeComponent(
                    plugin.getConfigManager().getMessagesConfig().getTpaDenied()
                ));
            }
        }
        target.sendMessage(ColorUtils.colorizeComponent(
            plugin.getConfigManager().getMessagesConfig().getTpaDenied()
        ));
    }

    public enum RequestType {
        TPA,
        TPAHERE
    }

    public static class TeleportRequest {
        private final UUID sender;
        private final UUID target;
        private final RequestType type;
        private final long timestamp;
        private org.bukkit.Location acceptLocation;

        public TeleportRequest(UUID sender, UUID target, RequestType type) {
            this.sender = sender;
            this.target = target;
            this.type = type;
            this.timestamp = System.currentTimeMillis();
        }

        public UUID getSender() { return sender; }
        public UUID getTarget() { return target; }
        public RequestType getType() { return type; }
        public long getTimestamp() { return timestamp; }
        public org.bukkit.Location getAcceptLocation() { return acceptLocation; }
        public void setAcceptLocation(org.bukkit.Location location) { this.acceptLocation = location; }
    }
}
