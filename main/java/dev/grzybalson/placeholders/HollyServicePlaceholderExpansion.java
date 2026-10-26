package dev.grzybalson.placeholders;

import dev.grzybalson.HollyCore;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public class HollyServicePlaceholderExpansion extends PlaceholderExpansion {

    private final HollyCore plugin;

    public HollyServicePlaceholderExpansion(HollyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "hollyservice";
    }

    @Override
    public @NotNull String getAuthor() {
        return "HollyCore";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        return onRequest(player, params);
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        String normalized = params.toLowerCase(Locale.ROOT);

        if (normalized.equals("joininfo") || normalized.equals("join_info")
            || normalized.equals("joinnumber") || normalized.equals("join_number")) {
            if (plugin.getJoinInfoManager() == null) {
                return "#…";
            }
            if (player == null) {
                return "#…";
            }
            return plugin.getJoinInfoManager().getJoinInfo(player.getUniqueId(), player.getName());
        }

        if (normalized.equals("joininfo_raw") || normalized.equals("join_info_raw")
            || normalized.equals("joinnumber_raw") || normalized.equals("join_number_raw")) {
            if (plugin.getJoinInfoManager() == null) {
                return "0";
            }
            if (player == null) {
                return "0";
            }
            return String.valueOf(plugin.getJoinInfoManager().getJoinNumber(player.getUniqueId(), player.getName()));
        }

        if (normalized.equals("joininfo_total") || normalized.equals("join_info_total")
            || normalized.equals("jointotal") || normalized.equals("join_total")) {
            if (plugin.getJoinInfoManager() == null) {
                return "1";
            }
            return String.valueOf(plugin.getJoinInfoManager().getTotalJoinedUsers());
        }

        // Delegate other params to main holly expansion if available
        if (plugin.getHollyPlaceholderExpansion() != null) {
            return plugin.getHollyPlaceholderExpansion().onRequest(player, params);
        }

        return null;
    }
}
