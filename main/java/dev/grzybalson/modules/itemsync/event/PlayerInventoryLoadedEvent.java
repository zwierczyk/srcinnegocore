package dev.grzybalson.modules.itemsync.event;

import dev.grzybalson.modules.itemsync.PlayerInventorySnapshot;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class PlayerInventoryLoadedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final PlayerInventorySnapshot snapshot;

    public PlayerInventoryLoadedEvent(Player player, PlayerInventorySnapshot snapshot) {
        this.player = player;
        this.snapshot = snapshot;
    }

    public Player getPlayer() {
        return player;
    }

    public PlayerInventorySnapshot getSnapshot() {
        return snapshot;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
