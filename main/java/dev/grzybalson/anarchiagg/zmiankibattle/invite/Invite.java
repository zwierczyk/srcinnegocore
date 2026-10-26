package dev.grzybalson.anarchiagg.zmiankibattle.invite;

import java.util.UUID;

public final class Invite {
    private final UUID inviter;
    private final UUID invited;
    private final double amount;
    private final long expiresAtMillis;

    public Invite(UUID inviter, UUID invited, double amount, long expiresAtMillis) {
        this.inviter = inviter;
        this.invited = invited;
        this.amount = amount;
        this.expiresAtMillis = expiresAtMillis;
    }

    public UUID inviter() { return inviter; }
    public UUID invited() { return invited; }
    public double amount() { return amount; }
    public long expiresAtMillis() { return expiresAtMillis; }

    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAtMillis;
    }
}
