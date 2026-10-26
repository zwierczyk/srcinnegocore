package dev.grzybalson.modules.groupteleport;

import org.bukkit.Location;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class GroupTeleportSession {

    private final UUID id;
    private final Location location;
    private final long startTime;
    private final long endTime;
    private final List<UUID> players;
    private long barrierEndTime;
    private long pvpUnlockAt;
    private int taskId;
    private int endTaskId;
    private BossBar firstBar;
    private BossBar secondBar;

    public GroupTeleportSession(UUID id, List<UUID> players, Location location, long startTime, long endTime) {
        this.id = id;
        this.players = new ArrayList<>(players);
        this.location = location;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public UUID getId() {
        return id;
    }

    public Location getLocation() {
        return location;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public List<UUID> getPlayers() {
        return players;
    }

    public long getBarrierEndTime() {
        return barrierEndTime;
    }

    public void setBarrierEndTime(long barrierEndTime) {
        this.barrierEndTime = barrierEndTime;
    }

    public long getPvpUnlockAt() {
        return pvpUnlockAt;
    }

    public void setPvpUnlockAt(long pvpUnlockAt) {
        this.pvpUnlockAt = pvpUnlockAt;
    }

    public boolean isPvpLocked() {
        return pvpUnlockAt > 0L && System.currentTimeMillis() < pvpUnlockAt;
    }

    public int getTaskId() {
        return taskId;
    }

    public void setTaskId(int taskId) {
        this.taskId = taskId;
    }

    public int getEndTaskId() {
        return endTaskId;
    }

    public void setEndTaskId(int endTaskId) {
        this.endTaskId = endTaskId;
    }

    public BossBar getFirstBar() {
        return firstBar;
    }

    public void setFirstBar(BossBar firstBar) {
        this.firstBar = firstBar;
    }

    public BossBar getSecondBar() {
        return secondBar;
    }

    public void setSecondBar(BossBar secondBar) {
        this.secondBar = secondBar;
    }

    public BossBar createBar(String title, BarColor color) {
        return org.bukkit.Bukkit.createBossBar(title, color, BarStyle.SEGMENTED_10);
    }
}
