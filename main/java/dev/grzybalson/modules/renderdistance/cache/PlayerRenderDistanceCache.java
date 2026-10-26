package dev.grzybalson.modules.renderdistance.cache;

import org.bukkit.Location;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class PlayerRenderDistanceCache {
    private final UUID uuid;
    private String lastWorldName;
    private int lastBlockX;
    private int lastBlockY;
    private int lastBlockZ;
    private int lastChunkX;
    private int lastChunkZ;
    private boolean hasLocation;
    private String lastActiveEntryName;
    private String lastWorldGuardRegionName;
    private int lastRenderDistance = Integer.MIN_VALUE;
    private boolean usingDefaults = true;
    private long lastQueuedTick = -1L;
    private long lastUpdatedTick = -1L;
    private final Set<UUID> hiddenPlayers = new HashSet<>();

    public PlayerRenderDistanceCache(UUID uuid) {
        this.uuid = uuid;
    }

    public UUID getUuid() {
        return uuid;
    }

    public boolean matchesLocation(Location location, boolean checkOnlyOnBlockChange) {
        if (!hasLocation || location == null || location.getWorld() == null) {
            return false;
        }
        if (!Objects.equals(lastWorldName, location.getWorld().getName())) {
            return false;
        }
        if (checkOnlyOnBlockChange) {
            return lastBlockX == location.getBlockX()
                && lastBlockY == location.getBlockY()
                && lastBlockZ == location.getBlockZ();
        }
        return lastChunkX == location.getBlockX() >> 4
            && lastChunkZ == location.getBlockZ() >> 4;
    }

    public void updateLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        this.lastWorldName = location.getWorld().getName();
        this.lastBlockX = location.getBlockX();
        this.lastBlockY = location.getBlockY();
        this.lastBlockZ = location.getBlockZ();
        this.lastChunkX = location.getBlockX() >> 4;
        this.lastChunkZ = location.getBlockZ() >> 4;
        this.hasLocation = true;
    }

    public String getLastWorldName() {
        return lastWorldName;
    }

    public String getLastActiveEntryName() {
        return lastActiveEntryName;
    }

    public void setLastActiveEntryName(String lastActiveEntryName) {
        this.lastActiveEntryName = lastActiveEntryName;
    }

    public String getLastWorldGuardRegionName() {
        return lastWorldGuardRegionName;
    }

    public void setLastWorldGuardRegionName(String lastWorldGuardRegionName) {
        this.lastWorldGuardRegionName = lastWorldGuardRegionName;
    }

    public int getLastRenderDistance() {
        return lastRenderDistance;
    }

    public void setLastRenderDistance(int lastRenderDistance) {
        this.lastRenderDistance = lastRenderDistance;
    }

    public boolean isUsingDefaults() {
        return usingDefaults;
    }

    public void setUsingDefaults(boolean usingDefaults) {
        this.usingDefaults = usingDefaults;
    }

    public long getLastQueuedTick() {
        return lastQueuedTick;
    }

    public void setLastQueuedTick(long lastQueuedTick) {
        this.lastQueuedTick = lastQueuedTick;
    }

    public long getLastUpdatedTick() {
        return lastUpdatedTick;
    }

    public void setLastUpdatedTick(long lastUpdatedTick) {
        this.lastUpdatedTick = lastUpdatedTick;
    }

    public Set<UUID> getHiddenPlayers() {
        return hiddenPlayers;
    }
}
