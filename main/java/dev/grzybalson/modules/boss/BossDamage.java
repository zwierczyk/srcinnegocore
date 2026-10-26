package dev.grzybalson.modules.boss;

import java.util.UUID;

public final class BossDamage {

    private final UUID uniqueId;
    private int damage;

    public BossDamage(UUID uniqueId) {
        this.uniqueId = uniqueId;
        this.damage = 0;
    }

    public UUID getUniqueId() {
        return uniqueId;
    }

    public void addHit() {
        damage++;
    }

    public int getDamage() {
        return damage;
    }
}
