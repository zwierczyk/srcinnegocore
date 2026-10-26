package dev.grzybalson.commands.daily;

import java.util.UUID;

public class DailyPlayerData {

    private final UUID uuid;
    private int lastClaimedDay;
    private String lastClaimDate;
    private long lastClaimAt;
    private boolean dirty;
    private boolean loaded;

    public DailyPlayerData(UUID uuid) {
        this.uuid = uuid;
        this.lastClaimedDay = 0;
        this.lastClaimDate = null;
        this.lastClaimAt = 0L;
    }

    public UUID getUuid() {
        return uuid;
    }

    public int getLastClaimedDay() {
        return lastClaimedDay;
    }

    public void setLastClaimedDay(int lastClaimedDay) {
        this.lastClaimedDay = lastClaimedDay;
        this.dirty = true;
    }

    public String getLastClaimDate() {
        return lastClaimDate;
    }

    public void setLastClaimDate(String lastClaimDate) {
        this.lastClaimDate = lastClaimDate;
        this.dirty = true;
    }

    public long getLastClaimAt() {
        return lastClaimAt;
    }

    public void setLastClaimAt(long lastClaimAt) {
        this.lastClaimAt = lastClaimAt;
        this.dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void markClean() {
        this.dirty = false;
    }

    public boolean isLoaded() {
        return loaded;
    }

    public void markLoaded() {
        this.loaded = true;
    }
}
