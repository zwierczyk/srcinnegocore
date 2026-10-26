package dev.grzybalson.modules.anticheat;

import java.util.EnumMap;
import java.util.Map;

public final class CheatPlayerData {

    private static final long DEFAULT_DECAY_MS = 8_000L;
    private static final long COBWEB_PHASE_DECAY_MS = 3_500L;
    private static final long HIT_THROUGH_DECAY_MS = 6_000L;

    private final Map<CheatType, Integer> violations = new EnumMap<>(CheatType.class);
    private final Map<CheatType, Long> lastViolationAt = new EnumMap<>(CheatType.class);
    // Pola dotykane z watku packetevents (AimbotCheck) i z main threada.
    private volatile long lastHit;
    private volatile long lastNotify;
    private volatile long blockHitUntil;
    private volatile long blockMoveUntil;
    private volatile long blockPlaceUntil;

    public synchronized int addViolation(CheatType type) {
        long now = System.currentTimeMillis();
        Long last = lastViolationAt.get(type);
        int current = violations.getOrDefault(type, 0);
        long decay = type == CheatType.HIT_THROUGH_BLOCK
            ? HIT_THROUGH_DECAY_MS
            : (type == CheatType.COBWEB_PHASE
                || type == CheatType.COBWEB_THROUGH_WALL
                || type == CheatType.AUTO_TRAP
                ? COBWEB_PHASE_DECAY_MS
                : DEFAULT_DECAY_MS);
        if (last != null && now - last > decay) {
            current = 0;
        }
        int next = current + 1;
        violations.put(type, next);
        lastViolationAt.put(type, now);
        return next;
    }

    public synchronized void resetViolation(CheatType type) {
        violations.put(type, 0);
    }

    public long getLastHit() { return lastHit; }
    public void setLastHit(long lastHit) { this.lastHit = lastHit; }
    public long getLastNotify() { return lastNotify; }
    public void setLastNotify(long lastNotify) { this.lastNotify = lastNotify; }
    public long getBlockHitUntil() { return blockHitUntil; }
    public void setBlockHitUntil(long blockHitUntil) { this.blockHitUntil = blockHitUntil; }
    public long getBlockMoveUntil() { return blockMoveUntil; }
    public void setBlockMoveUntil(long blockMoveUntil) { this.blockMoveUntil = blockMoveUntil; }
    public long getBlockPlaceUntil() { return blockPlaceUntil; }
    public void setBlockPlaceUntil(long blockPlaceUntil) { this.blockPlaceUntil = blockPlaceUntil; }
}