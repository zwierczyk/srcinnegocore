package dev.grzybalson.modules.cases;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.function.LongSupplier;

/** One rolling window shared by every player, case and opening mode on the server. */
final class GlobalCaseOpeningLimiter {
    private final Deque<Permit> openings = new ArrayDeque<>();
    private final LongSupplier clock;
    private int maximum;
    private long windowMillis;

    GlobalCaseOpeningLimiter(int maximum, long windowMillis) {
        this(maximum, windowMillis, () -> System.nanoTime() / 1_000_000L);
    }

    GlobalCaseOpeningLimiter(int maximum, long windowMillis, LongSupplier clock) {
        this.clock = Objects.requireNonNull(clock);
        configure(maximum, windowMillis);
    }

    /** Reloading settings must not grant a fresh allowance to players already opening cases. */
    synchronized void configure(int maximum, long windowMillis) {
        if (maximum < 1 || windowMillis < 1) throw new IllegalArgumentException("Opening limits must be positive");
        this.maximum = maximum;
        this.windowMillis = windowMillis;
    }

    synchronized Attempt tryAcquire() {
        long now = clock.getAsLong();
        while (!openings.isEmpty() && now - openings.peekFirst().startedAt >= windowMillis) openings.removeFirst();
        if (openings.size() >= maximum) {
            return new Attempt(null, Math.max(1L, windowMillis - (now - openings.peekFirst().startedAt)));
        }
        Permit permit = new Permit(now);
        openings.addLast(permit);
        return new Attempt(permit, 0L);
    }

    /** Failed preparation does not count as an opening or free another player's reservation. */
    synchronized void release(Permit permit) {
        if (permit != null) openings.remove(permit);
    }

    record Attempt(Permit permit, long retryAfterMillis) { }

    static final class Permit {
        private final long startedAt;
        private Permit(long startedAt) { this.startedAt = startedAt; }
    }
}
