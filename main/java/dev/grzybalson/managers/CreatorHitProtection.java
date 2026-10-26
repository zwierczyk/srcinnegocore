package dev.grzybalson.managers;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.function.LongSupplier;

/** Protection belongs to the victim, independently of the attacker and the final killer. */
final class CreatorHitProtection {
    private static final long DURATION_MILLIS = 30_000L;
    private final NamespacedKey key;
    private final LongSupplier clock;

    CreatorHitProtection(NamespacedKey key) { this(key, System::currentTimeMillis); }

    CreatorHitProtection(NamespacedKey key, LongSupplier clock) {
        this.key = key;
        this.clock = clock;
    }

    void protect(Player victim) {
        if (victim == null) return;
        var data = victim.getPersistentDataContainer();
        Long previous = data.get(key, PersistentDataType.LONG);
        long until = clock.getAsLong() + DURATION_MILLIS;
        data.set(key, PersistentDataType.LONG, previous == null ? until : Math.max(previous, until));
    }

    boolean protectsDeath(Player victim, boolean victimIsCreator, boolean killerIsCreator) {
        return victim != null && (victimIsCreator || killerIsCreator || active(victim));
    }

    boolean active(Player victim) {
        if (victim == null) return false;
        var data = victim.getPersistentDataContainer();
        Long until = data.get(key, PersistentDataType.LONG);
        if (until == null) return false;
        if (until > clock.getAsLong()) return true;
        data.remove(key);
        return false;
    }
}
