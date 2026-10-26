package dev.grzybalson.modules.masakraszafka;

import org.bson.Document;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class SzafkaProfile {
    private final Map<SzafkaItem.Kind, Map<String, Long>> owned = new EnumMap<>(SzafkaItem.Kind.class);
    private final Set<String> talismans = new LinkedHashSet<>();
    private String costume;

    SzafkaProfile() {
        for (SzafkaItem.Kind kind : SzafkaItem.Kind.values()) owned.put(kind, new LinkedHashMap<>());
    }

    long expiry(SzafkaItem.Kind kind, String id) {
        return owned.get(kind).getOrDefault(id, 0L);
    }

    boolean owns(SzafkaItem.Kind kind, String id, long now) {
        return expiry(kind, id) > now;
    }

    Map<String, Long> owned(SzafkaItem.Kind kind) {
        return Collections.unmodifiableMap(owned.get(kind));
    }

    long grant(SzafkaItem.Kind kind, String id, long duration, long now) {
        long base = Math.max(now, expiry(kind, id));
        long expiry = base == SzafkaCatalog.PERMANENT || duration >= SzafkaCatalog.PERMANENT - base ? SzafkaCatalog.PERMANENT : base + duration;
        owned.get(kind).put(id, expiry);
        return expiry;
    }

    boolean revoke(SzafkaItem.Kind kind, String id) {
        unequip(kind, id);
        return owned.get(kind).remove(id) != null;
    }

    String costume() {
        return costume;
    }

    Set<String> talismans() {
        return Collections.unmodifiableSet(talismans);
    }

    boolean isEquipped(SzafkaItem.Kind kind, String id) {
        return kind == SzafkaItem.Kind.COSTUME ? id.equals(costume) : talismans.contains(id);
    }

    void equipCostume(String id) {
        costume = id;
    }

    boolean equipTalisman(String id, int max) {
        if (talismans.contains(id)) return true;
        if (talismans.size() >= max) return false;
        talismans.add(id);
        return true;
    }

    boolean unequip(SzafkaItem.Kind kind, String id) {
        if (kind == SzafkaItem.Kind.TALISMAN) return talismans.remove(id);
        if (!id.equals(costume)) return false;
        costume = null;
        return true;
    }

    void trimTalismans(int max) {
        while (talismans.size() > max) {
            String last = null;
            for (String id : talismans) last = id;
            talismans.remove(last);
        }
    }

    List<Expired> purge(long now) {
        List<Expired> expired = new ArrayList<>();
        owned.forEach((kind, entries) -> entries.entrySet().removeIf(entry -> {
            if (entry.getValue() > now) return false;
            expired.add(new Expired(kind, entry.getKey()));
            return true;
        }));
        if (costume != null && !owned.get(SzafkaItem.Kind.COSTUME).containsKey(costume)) costume = null;
        talismans.removeIf(id -> !owned.get(SzafkaItem.Kind.TALISMAN).containsKey(id));
        return expired;
    }

    Document toDocument(UUID uuid) {
        Document document = new Document("_id", uuid.toString());
        for (SzafkaItem.Kind kind : SzafkaItem.Kind.values()) document.append(kind.section, new Document(new LinkedHashMap<String, Object>(owned.get(kind))));
        return document.append("costume", costume)
            .append("equipped-talismans", new ArrayList<>(talismans))
            .append("updated", System.currentTimeMillis());
    }

    static SzafkaProfile from(Document document) {
        SzafkaProfile profile = new SzafkaProfile();
        if (document == null) return profile;
        for (SzafkaItem.Kind kind : SzafkaItem.Kind.values()) {
            Document entries = document.get(kind.section, Document.class);
            if (entries == null) continue;
            entries.forEach((id, value) -> {
                if (value instanceof Number number) profile.owned.get(kind).put(id, number.longValue());
            });
        }
        String costume = document.getString("costume");
        if (costume != null && profile.owned.get(SzafkaItem.Kind.COSTUME).containsKey(costume)) profile.costume = costume;
        List<String> equipped = document.getList("equipped-talismans", String.class, List.of());
        for (String id : equipped) if (profile.owned.get(SzafkaItem.Kind.TALISMAN).containsKey(id)) profile.talismans.add(id);
        return profile;
    }

    record Expired(SzafkaItem.Kind kind, String id) {
    }
}
