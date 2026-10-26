package dev.grzybalson.modules.sigil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SigilPlayerData {

    public static final int BASE_SLOTS = 3;
    public static final int MAX_SLOTS = 5;

    private final UUID uuid;
    private final EnumMap<SigilType, Double> owned = new EnumMap<>(SigilType.class);
    private final EnumMap<SigilType, Long> ownedExpiresAt = new EnumMap<>(SigilType.class);
    private final SigilType[] equipped = new SigilType[MAX_SLOTS];
    private long extraSlotsExpiresAtMillis;
    /** Backup owned sprzed tymczasowego grantu admina; null = brak aktywnego grantu. */
    private EnumMap<SigilType, Double> ownedBackup;
    private long temporaryGrantExpiresAtMillis;

    public SigilPlayerData(UUID uuid) {
        this.uuid = uuid;
    }

    public UUID getUuid() {
        return uuid;
    }

    public boolean owns(SigilType type) {
        pruneExpiredOwned();
        return type != null && owned.containsKey(type);
    }

    public double getPower(SigilType type) {
        if (type == null) {
            return 0.0D;
        }
        return owned.getOrDefault(type, 0.0D);
    }

    public void setOwned(SigilType type, double power) {
        if (type == null) {
            return;
        }
        owned.put(type, clampPower(power));
    }

    public void unlockOrUpgrade(SigilType type, double power) {
        if (type == null) {
            return;
        }
        double next = clampPower(power);
        Double current = owned.get(type);
        if (current == null || next > current) {
            owned.put(type, next);
        }
    }

    /** Odblokowanie trwałego sigila — trafia też do backupu przy aktywnym grantcie tymczasowym. */
    public void unlockOrUpgradePermanent(SigilType type, double power) {
        unlockOrUpgrade(type, power);
        clearOwnedExpiry(type);
        if (type == null || ownedBackup == null) {
            return;
        }
        double next = clampPower(power);
        Double current = ownedBackup.get(type);
        if (current == null || next > current) {
            ownedBackup.put(type, next);
        }
    }

    public boolean hasOwnedExpiry(SigilType type) {
        Long expiresAt = ownedExpiresAt.get(type);
        return expiresAt != null && expiresAt > System.currentTimeMillis();
    }

    public Long getOwnedExpiresAtMillis(SigilType type) {
        if (type == null) {
            return null;
        }
        Long expiresAt = ownedExpiresAt.get(type);
        if (expiresAt == null || expiresAt <= System.currentTimeMillis()) {
            return null;
        }
        return expiresAt;
    }

    public Map<SigilType, Long> getOwnedExpiresAt() {
        return Collections.unmodifiableMap(ownedExpiresAt);
    }

    public void setOwnedExpiresAt(SigilType type, long expiresAtMillis) {
        if (type == null) {
            return;
        }
        long safe = Math.max(0L, expiresAtMillis);
        if (safe <= 0L) {
            ownedExpiresAt.remove(type);
            return;
        }
        ownedExpiresAt.put(type, safe);
    }

    public void extendOwnedExpiry(SigilType type, long expiresAtMillis) {
        if (type == null) {
            return;
        }
        long next = Math.max(0L, expiresAtMillis);
        Long current = ownedExpiresAt.get(type);
        if (current == null || next > current) {
            ownedExpiresAt.put(type, next);
        }
    }

    public void clearOwnedExpiry(SigilType type) {
        if (type != null) {
            ownedExpiresAt.remove(type);
        }
    }

    /** Usuwa sigile, których czas od użycia już minął. */
    public boolean pruneExpiredOwned() {
        if (ownedExpiresAt.isEmpty()) {
            return false;
        }
        long now = System.currentTimeMillis();
        boolean changed = false;
        var iterator = ownedExpiresAt.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<SigilType, Long> entry = iterator.next();
            if (entry.getValue() != null && entry.getValue() > now) {
                continue;
            }
            owned.remove(entry.getKey());
            unequip(entry.getKey());
            iterator.remove();
            changed = true;
        }
        return changed;
    }

    public Map<SigilType, Double> getOwned() {
        return Collections.unmodifiableMap(owned);
    }

    public boolean hasActiveTemporaryGrant() {
        return temporaryGrantExpiresAtMillis > System.currentTimeMillis();
    }

    public long getTemporaryGrantExpiresAtMillis() {
        return temporaryGrantExpiresAtMillis;
    }

    public void setTemporaryGrantExpiresAtMillis(long temporaryGrantExpiresAtMillis) {
        this.temporaryGrantExpiresAtMillis = Math.max(0L, temporaryGrantExpiresAtMillis);
    }

    public Map<SigilType, Double> getOwnedBackup() {
        return ownedBackup == null ? Map.of() : Collections.unmodifiableMap(ownedBackup);
    }

    public boolean hasOwnedBackup() {
        return ownedBackup != null;
    }

    public void setOwnedBackup(Map<SigilType, Double> backup) {
        if (backup == null) {
            this.ownedBackup = null;
            return;
        }
        this.ownedBackup = new EnumMap<>(SigilType.class);
        for (Map.Entry<SigilType, Double> entry : backup.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                this.ownedBackup.put(entry.getKey(), clampPower(entry.getValue()));
            }
        }
    }

    /**
     * Nadaje wszystkie sigile na czas {@code expiresAtMillis}. Poprzednie owned ląduje w backupie.
     */
    public void applyTemporaryGrant(double power, long expiresAtMillis) {
        if (ownedBackup == null) {
            ownedBackup = new EnumMap<>(SigilType.class);
            ownedBackup.putAll(owned);
        }
        double safe = clampPower(power);
        for (SigilType type : SigilType.values()) {
            owned.put(type, safe);
        }
        this.temporaryGrantExpiresAtMillis = Math.max(System.currentTimeMillis() + 1000L, expiresAtMillis);
    }

    /**
     * Stary grant odliczał czas od nadania. Zostawiamy tylko sigile faktycznie użyte (PPM).
     */
    public boolean pruneTemporaryGrant() {
        if (temporaryGrantExpiresAtMillis <= 0L && ownedBackup == null) {
            return false;
        }
        EnumMap<SigilType, Double> granted = new EnumMap<>(SigilType.class);
        granted.putAll(owned);
        owned.clear();
        if (ownedBackup != null) {
            owned.putAll(ownedBackup);
            ownedBackup = null;
        }
        long now = System.currentTimeMillis();
        var iterator = ownedExpiresAt.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<SigilType, Long> entry = iterator.next();
            if (entry.getValue() == null || entry.getValue() <= now) {
                iterator.remove();
                continue;
            }
            Double power = granted.get(entry.getKey());
            if (power != null) {
                owned.put(entry.getKey(), power);
            }
        }
        temporaryGrantExpiresAtMillis = 0L;
        ownedExpiresAt.keySet().removeIf(type -> !owned.containsKey(type));
        for (int i = 0; i < MAX_SLOTS; i++) {
            if (equipped[i] != null && !owns(equipped[i])) {
                equipped[i] = null;
            }
        }
        pruneExpiredExtraSlots();
        return true;
    }

    public boolean hasExtraSlots() {
        return extraSlotsExpiresAtMillis > System.currentTimeMillis();
    }

    public int getAvailableSlotCount() {
        return hasExtraSlots() ? MAX_SLOTS : BASE_SLOTS;
    }

    public long getExtraSlotsExpiresAtMillis() {
        return extraSlotsExpiresAtMillis;
    }

    public void setExtraSlotsExpiresAtMillis(long extraSlotsExpiresAtMillis) {
        this.extraSlotsExpiresAtMillis = Math.max(0L, extraSlotsExpiresAtMillis);
        pruneExpiredExtraSlots();
    }

    public void renewExtraSlots(long durationMillis) {
        long now = System.currentTimeMillis();
        long base = Math.max(now, extraSlotsExpiresAtMillis);
        this.extraSlotsExpiresAtMillis = base + Math.max(0L, durationMillis);
    }

    public void pruneExpiredExtraSlots() {
        if (hasExtraSlots()) {
            return;
        }
        for (int i = BASE_SLOTS; i < MAX_SLOTS; i++) {
            equipped[i] = null;
        }
    }

    public boolean equip(SigilType type) {
        if (type == null || !owns(type)) {
            return false;
        }
        if (isEquipped(type)) {
            return true;
        }

        pruneExpiredExtraSlots();
        int limit = getAvailableSlotCount();
        for (int i = 0; i < limit; i++) {
            if (equipped[i] == null) {
                equipped[i] = type;
                return true;
            }
        }
        return false;
    }

    public int countEquipped(SigilCategory category) {
        if (category == null) {
            return 0;
        }
        pruneExpiredExtraSlots();
        int count = 0;
        int limit = getAvailableSlotCount();
        for (int i = 0; i < limit; i++) {
            SigilType equippedType = equipped[i];
            if (equippedType != null && equippedType.getCategory() == category) {
                count++;
            }
        }
        return count;
    }

    public boolean unequip(SigilType type) {
        if (type == null) {
            return false;
        }
        boolean changed = false;
        for (int i = 0; i < MAX_SLOTS; i++) {
            if (equipped[i] == type) {
                equipped[i] = null;
                changed = true;
            }
        }
        return changed;
    }

    public boolean unequipSlot(int index) {
        if (index < 0 || index >= MAX_SLOTS) {
            return false;
        }
        if (equipped[index] == null) {
            return false;
        }
        equipped[index] = null;
        return true;
    }

    public boolean isEquipped(SigilType type) {
        if (type == null) {
            return false;
        }
        pruneExpiredExtraSlots();
        int limit = getAvailableSlotCount();
        for (int i = 0; i < limit; i++) {
            if (equipped[i] == type) {
                return true;
            }
        }
        return false;
    }

    public SigilType getEquippedAt(int index) {
        if (index < 0 || index >= MAX_SLOTS) {
            return null;
        }
        return equipped[index];
    }

    public void setEquippedAt(int index, SigilType type) {
        if (index < 0 || index >= MAX_SLOTS) {
            return;
        }
        equipped[index] = type;
    }

    public List<SigilType> getEquipped() {
        List<SigilType> list = new ArrayList<>(MAX_SLOTS);
        for (SigilType type : equipped) {
            if (type != null) {
                list.add(type);
            }
        }
        return list;
    }

    public List<SigilType> getActiveEquipped() {
        pruneExpiredExtraSlots();
        int limit = getAvailableSlotCount();
        List<SigilType> list = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            if (equipped[i] != null) {
                list.add(equipped[i]);
            }
        }
        return list;
    }

    public double getActivePower(SigilType type) {
        pruneExpiredOwned();
        if (!isEquipped(type)) {
            return 0.0D;
        }
        return getPower(type);
    }

    public boolean hasProgress() {
        return !owned.isEmpty()
            || !ownedExpiresAt.isEmpty()
            || ownedBackup != null
            || temporaryGrantExpiresAtMillis > 0L
            || extraSlotsExpiresAtMillis > 0L;
    }

    public void mergeFrom(SigilPlayerData other) {
        if (other == null) {
            return;
        }
        for (Map.Entry<SigilType, Double> entry : other.owned.entrySet()) {
            unlockOrUpgrade(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<SigilType, Long> entry : other.ownedExpiresAt.entrySet()) {
            if (entry.getValue() != null) {
                extendOwnedExpiry(entry.getKey(), entry.getValue());
            }
        }
        if (other.extraSlotsExpiresAtMillis > extraSlotsExpiresAtMillis) {
            extraSlotsExpiresAtMillis = other.extraSlotsExpiresAtMillis;
        }
        if (other.temporaryGrantExpiresAtMillis > temporaryGrantExpiresAtMillis) {
            temporaryGrantExpiresAtMillis = other.temporaryGrantExpiresAtMillis;
        }
        if (ownedBackup == null && other.ownedBackup != null) {
            setOwnedBackup(other.ownedBackup);
        }
        for (int i = 0; i < MAX_SLOTS; i++) {
            if (equipped[i] == null && other.equipped[i] != null && owns(other.equipped[i])) {
                equipped[i] = other.equipped[i];
            }
        }
    }

    public void reset() {
        owned.clear();
        ownedExpiresAt.clear();
        for (int i = 0; i < MAX_SLOTS; i++) {
            equipped[i] = null;
        }
        extraSlotsExpiresAtMillis = 0L;
        ownedBackup = null;
        temporaryGrantExpiresAtMillis = 0L;
    }

    private static double clampPower(double power) {
        if (!Double.isFinite(power)) {
            return 1.0D;
        }
        return Math.max(1.0D, Math.min(100.0D, power));
    }
}
