package dev.grzybalson.placeholders;

import dev.grzybalson.HollyCore;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public class PortfelPlaceholderExpansion extends PlaceholderExpansion {

    private final HollyCore plugin;

    public PortfelPlaceholderExpansion(HollyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "portfel";
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
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return "0.00";
        }
        if (params.equalsIgnoreCase("money") || params.equalsIgnoreCase("balance")) {
            return String.format(Locale.US, "%.2f", plugin.getWalletManager().getCachedBalance(player.getUniqueId()));
        }
        return null;
    }
}
