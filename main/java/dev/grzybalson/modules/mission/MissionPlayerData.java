package dev.grzybalson.modules.mission;

import org.bson.Document;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MissionPlayerData {

    private final UUID uuid;
    private final Map<String, Integer> progress = new HashMap<>();

    public MissionPlayerData(UUID uuid) {
        this.uuid = uuid;
    }

    public UUID getUuid() {
        return uuid;
    }

    public int getProgress(String missionId) {
        return progress.getOrDefault(missionId, 0);
    }

    public void setProgress(String missionId, int value) {
        progress.put(missionId, Math.max(0, value));
    }

    public void increment(String missionId, int amount) {
        if (amount <= 0) {
            return;
        }
        progress.merge(missionId, amount, Integer::sum);
    }

    public void reset(String missionId) {
        progress.put(missionId, 0);
    }

    public static MissionPlayerData fromDocument(UUID uuid, Document document) {
        MissionPlayerData data = new MissionPlayerData(uuid);
        if (document == null) {
            return data;
        }

        Object rawProgress = document.get("progress");
        if (rawProgress instanceof Document progressDocument) {
            for (String key : progressDocument.keySet()) {
                Object value = progressDocument.get(key);
                if (value instanceof Number number) {
                    data.setProgress(key, number.intValue());
                }
            }
        }
        return data;
    }

    public Document toDocument() {
        Document progressDocument = new Document();
        for (Map.Entry<String, Integer> entry : progress.entrySet()) {
            progressDocument.append(entry.getKey(), entry.getValue());
        }
        return new Document("uuid", uuid.toString()).append("progress", progressDocument);
    }
}
