package dev.grzybalson.modules.coinflip;

import java.util.UUID;

public class GameSession {

    private final String id;
    private final UUID creatorUuid;
    private final String creatorName;
    private final CoinflipCurrency currency;
    private final double amount;
    private final long createdAtMillis;

    private UUID opponentUuid;
    private String opponentName;
    private State state = State.OPEN;

    public GameSession(String id, UUID creatorUuid, String creatorName, CoinflipCurrency currency, double amount) {
        this(id, creatorUuid, creatorName, currency, amount, System.currentTimeMillis());
    }

    public GameSession(String id, UUID creatorUuid, String creatorName, CoinflipCurrency currency, double amount, long createdAtMillis) {
        this.id = id;
        this.creatorUuid = creatorUuid;
        this.creatorName = creatorName;
        this.currency = currency;
        this.amount = amount;
        this.createdAtMillis = createdAtMillis;
    }

    public synchronized boolean reserveOpponent(UUID uuid, String name) {
        if (state != State.OPEN || creatorUuid.equals(uuid)) {
            return false;
        }

        state = State.JOINING;
        opponentUuid = uuid;
        opponentName = name;
        return true;
    }

    public synchronized void releaseOpponent() {
        if (state != State.JOINING) {
            return;
        }

        state = State.OPEN;
        opponentUuid = null;
        opponentName = null;
    }

    public synchronized boolean startDuel() {
        if (state != State.JOINING || opponentUuid == null) {
            return false;
        }

        state = State.IN_PROGRESS;
        return true;
    }

    public synchronized void reopen() {
        state = State.OPEN;
        opponentUuid = null;
        opponentName = null;
    }

    public synchronized boolean isOpen() {
        return state == State.OPEN;
    }

    public String getId() {
        return id;
    }

    public UUID getCreatorUuid() {
        return creatorUuid;
    }

    public String getCreatorName() {
        return creatorName;
    }

    public CoinflipCurrency getCurrency() {
        return currency;
    }

    public double getAmount() {
        return amount;
    }

    public long getCreatedAtMillis() {
        return createdAtMillis;
    }

    public synchronized UUID getOpponentUuid() {
        return opponentUuid;
    }

    public synchronized String getOpponentName() {
        return opponentName;
    }

    public enum State {
        OPEN,
        JOINING,
        IN_PROGRESS
    }
}
