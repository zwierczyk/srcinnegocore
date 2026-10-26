package dev.grzybalson.modules.npc;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NpcVisibilityTracker {
    private final Map<UUID, Set<String>> visibleNpcs = new ConcurrentHashMap<>();

    public boolean isVisible(UUID player, String npcId) {
        Set<String> set = this.visibleNpcs.get(player);
        return set != null && set.contains(npcId.toLowerCase());
    }

    public void markVisible(UUID player, String npcId) {
        this.visibleNpcs.computeIfAbsent(player, u -> ConcurrentHashMap.newKeySet()).add(npcId.toLowerCase());
    }

    public void markHidden(UUID player, String npcId) {
        Set<String> set = this.visibleNpcs.get(player);
        if (set != null) set.remove(npcId.toLowerCase());
    }

    public void forgetPlayer(UUID player) {
        this.visibleNpcs.remove(player);
    }

    public Set<String> getVisible(UUID player) {
        Set<String> set = this.visibleNpcs.get(player);
        return set == null ? new HashSet<>() : set;
    }
}
