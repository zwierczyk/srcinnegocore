package dev.grzybalson.managers;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MessageManager {

    private final Map<UUID, UUID> lastMessaged;

    public MessageManager() {
        this.lastMessaged = new ConcurrentHashMap<>();
    }

    public void setLastMessaged(UUID sender, UUID receiver) {
        lastMessaged.put(sender, receiver);
        lastMessaged.put(receiver, sender);
    }

    public UUID getLastMessaged(UUID player) {
        return lastMessaged.get(player);
    }

    public boolean hasLastMessaged(UUID player) {
        return lastMessaged.containsKey(player);
    }

    public void removePlayer(UUID player) {
        lastMessaged.remove(player);
    }
}
