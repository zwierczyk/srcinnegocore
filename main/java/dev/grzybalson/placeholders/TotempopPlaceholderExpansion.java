package dev.grzybalson.placeholders;

import dev.grzybalson.HollyCore;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TotempopPlaceholderExpansion extends PlaceholderExpansion {

    private final HollyCore plugin;

    public TotempopPlaceholderExpansion(HollyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "totempop";
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
        if (plugin.getHollyPlaceholderExpansion() != null) {
            return plugin.getHollyPlaceholderExpansion().onRequest(player, "totempop_" + params);
        }
        return "---";
    }
}
