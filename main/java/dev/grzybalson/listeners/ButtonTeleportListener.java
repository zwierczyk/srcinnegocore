package dev.grzybalson.listeners;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import dev.grzybalson.HollyCore;
import dev.grzybalson.commands.RtpCommand;
import dev.grzybalson.config.configs.RtpConfig;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ButtonTeleportListener implements Listener {

    private final HollyCore plugin;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final RtpCommand rtpCommand;

    public ButtonTeleportListener(HollyCore plugin) {
        this.plugin = plugin;
        this.rtpCommand = plugin.getRtpCommand() != null ? plugin.getRtpCommand() : new RtpCommand(plugin);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onButtonClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        if (block.getType() != Material.STONE_BUTTON) return;
        if (!isPvpDenyRegion(block.getLocation())) return;

        Player player = event.getPlayer();
        RtpConfig config = plugin.getConfigManager().getRtpConfig();

        if (!config.isEnabled()) return;
        if (!rtpCommand.canStartTeleport(player)) return;

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        long cooldownMs = config.getCooldownSeconds() * 1000L;

        if (!player.hasPermission("Holly.rtp.bypass.cooldown")) {
            Long lastUse = cooldowns.get(uuid);
            if (lastUse != null && now - lastUse < cooldownMs) {
                long remaining = (cooldownMs - (now - lastUse)) / 1000;
                player.sendMessage(ColorUtils.colorizeComponent(
                    config.getCooldownMessage().replace("{time}", formatTime(remaining))
                ));
                return;
            }
        }

        World world = Bukkit.getWorld(config.getWorldName());
        if (world == null) {
            player.sendMessage(ColorUtils.colorizeComponent(config.getWorldNotFoundMessage()));
            return;
        }

        player.sendMessage(ColorUtils.colorizeComponent(config.getTeleportingMessage()));

        rtpCommand.findSafeLocationAsync(world, config, 0, safeLocation -> {
            if (safeLocation != null) {
                player.teleportAsync(safeLocation).thenAccept(success -> {
                    if (success) {
                        cooldowns.put(uuid, System.currentTimeMillis());
                        player.sendMessage(ColorUtils.colorizeComponent(config.getTeleportedMessage()));
                    }
                });
            } else {
                player.sendMessage(ColorUtils.colorizeComponent(config.getNoSafeLocationMessage()));
            }
        });
    }

    private boolean isPvpDenyRegion(Location location) {
        try {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionQuery query = container.createQuery();
            ApplicableRegionSet regions = query.getApplicableRegions(BukkitAdapter.adapt(location));
            StateFlag.State pvpState = regions.queryState(null, Flags.PVP);
            return pvpState == StateFlag.State.DENY;
        } catch (Exception exception) {
            return false;
        }
    }

    private String formatTime(long seconds) {
        if (seconds >= 60) {
            long minutes = seconds / 60;
            long secs = seconds % 60;
            return minutes + "m " + secs + "s";
        }
        return seconds + "s";
    }
}
