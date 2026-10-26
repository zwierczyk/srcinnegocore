package dev.grzybalson.listeners;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.configs.IpLimitConfig;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class IpLimitListener implements Listener {

    private final HollyCore plugin;

    public IpLimitListener(HollyCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerLogin(PlayerLoginEvent event) {
        if (!plugin.isAddonEnabled("iplimit")) {
            return;
        }

        IpLimitConfig config = plugin.getConfigManager() != null ? plugin.getConfigManager().getIpLimitConfig() : null;
        if (config == null || !config.isEnabled()) {
            return;
        }

        Player player = event.getPlayer();
        String bypassPerm = config.getBypassPermission();
        if (bypassPerm != null && !bypassPerm.isBlank() && player.hasPermission(bypassPerm)) {
            return;
        }

        InetAddress incomingAddress = event.getAddress();
        if (incomingAddress == null) {
            return;
        }

        String hostAddress = incomingAddress.getHostAddress();
        if (hostAddress == null) {
            return;
        }

        if (config.getExemptIps() != null) {
            for (String exemptIp : config.getExemptIps()) {
                if (exemptIp != null && exemptIp.equalsIgnoreCase(hostAddress)) {
                    return;
                }
            }
        }

        int maxLimit = config.getMaxPlayersPerIp();
        if (maxLimit <= 0) {
            return;
        }

        int onlineSameIp = 0;
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (onlinePlayer.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            InetSocketAddress address = onlinePlayer.getAddress();
            if (address != null && address.getAddress() != null) {
                String onlineHost = address.getAddress().getHostAddress();
                if (hostAddress.equalsIgnoreCase(onlineHost)) {
                    onlineSameIp++;
                }
            }
        }

        if (onlineSameIp >= maxLimit) {
            String kickMsg = config.getKickMessage();
            if (kickMsg == null || kickMsg.isEmpty()) {
                kickMsg = "&#FF0000✘ &8Ξ &cOsiągnięto limit graczy z Twojego adresu IP (&4{limit}&c)!";
            }

            String hashedIp = hashIpAddress(hostAddress);

            kickMsg = kickMsg
                .replace("{limit}", String.valueOf(maxLimit))
                .replace("{ip}", hashedIp)
                .replace("{ip_hash}", hashedIp)
                .replace("{player}", player.getName())
                .replace("{online}", String.valueOf(onlineSameIp));

            event.disallow(PlayerLoginEvent.Result.KICK_OTHER, ColorUtils.colorizeComponent(kickMsg));
        }
    }

    private String hashIpAddress(String ip) {
        if (ip == null || ip.isEmpty()) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedHash = digest.digest(ip.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : encodedHash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            String fullHash = hexString.toString();
            return fullHash.length() >= 8 ? fullHash.substring(0, 8) : fullHash;
        } catch (NoSuchAlgorithmException e) {
            String fallback = Integer.toHexString(ip.hashCode());
            return fallback.length() >= 8 ? fallback.substring(0, 8) : fallback;
        }
    }
}
