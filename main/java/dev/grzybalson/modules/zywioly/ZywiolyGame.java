package dev.grzybalson.modules.zywioly;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

final class ZywiolyGame {

    enum State {
        OPEN,
        ROLLING,
        DONE
    }

    private final String id;
    private final UUID creatorUuid;
    private final String creatorName;
    private final double amount;
    private final ZywiolyElement creatorElement;
    private final AtomicBoolean open = new AtomicBoolean(true);

    private volatile UUID opponentUuid;
    private volatile String opponentName;
    private volatile ZywiolyElement opponentElement;
    private volatile State state = State.OPEN;

    ZywiolyGame(String id, UUID creatorUuid, String creatorName, double amount, ZywiolyElement creatorElement) {
        this.id = id;
        this.creatorUuid = creatorUuid;
        this.creatorName = creatorName;
        this.amount = amount;
        this.creatorElement = creatorElement;
    }

    String id() {
        return id;
    }

    UUID creatorUuid() {
        return creatorUuid;
    }

    String creatorName() {
        return creatorName;
    }

    double amount() {
        return amount;
    }

    ZywiolyElement creatorElement() {
        return creatorElement;
    }

    UUID opponentUuid() {
        return opponentUuid;
    }

    String opponentName() {
        return opponentName;
    }

    ZywiolyElement opponentElement() {
        return opponentElement;
    }

    State state() {
        return state;
    }

    boolean isOpen() {
        return open.get() && state == State.OPEN;
    }

    boolean reserveOpponent(UUID uuid, String name, ZywiolyElement element) {
        synchronized (this) {
            if (!isOpen() || opponentUuid != null) {
                return false;
            }
            this.opponentUuid = uuid;
            this.opponentName = name;
            this.opponentElement = element;
            this.state = State.ROLLING;
            this.open.set(false);
            return true;
        }
    }

    boolean cancelIfOpen() {
        synchronized (this) {
            if (!isOpen()) {
                return false;
            }
            this.open.set(false);
            this.state = State.DONE;
            return true;
        }
    }

    void markDone() {
        this.state = State.DONE;
        this.open.set(false);
    }
}
