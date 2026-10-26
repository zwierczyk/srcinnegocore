package dev.grzybalson.placeholders;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Alias expansion so %core_sector%, %core_kills% etc. resolve like %holly_*%.
 */
public class CorePlaceholderExpansion extends PlaceholderExpansion {

    private final HollyPlaceholderExpansion holly;

    public CorePlaceholderExpansion(HollyPlaceholderExpansion holly) {
        this.holly = holly;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "core";
    }

    @Override
    public @NotNull String getAuthor() {
        return "HollyCore";
    }

    @Override
    public @NotNull String getVersion() {
        return holly.getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        return holly.onPlaceholderRequest(player, params);
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        return holly.onRequest(player, params);
    }
}
