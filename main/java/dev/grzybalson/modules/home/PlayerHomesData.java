package dev.grzybalson.modules.home;

import org.bson.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerHomesData {

    private final UUID uuid;
    private final Map<Integer, Home> homes;

    public PlayerHomesData(UUID uuid) {
        this.uuid = uuid;
        this.homes = new ConcurrentHashMap<>();
    }

    public UUID getUuid() { return uuid; }
    public Map<Integer, Home> getHomes() { return homes; }

    public Home getHome(int slotNumber) {
        return homes.get(slotNumber);
    }

    public Home getHomeByName(String name) {
        if (name == null || name.isBlank()) return null;
        for (Home home : homes.values()) {
            if (home.getName() != null && home.getName().equalsIgnoreCase(name)) {
                return home;
            }
        }
        return null;
    }

    public void setHome(Home home) {
        homes.put(home.getHomeNumber(), home);
    }

    public Home removeHome(int slotNumber) {
        return homes.remove(slotNumber);
    }

    public Document toDocument() {
        Document doc = new Document();
        doc.put("uuid", uuid.toString());
        List<Document> list = new ArrayList<>();
        for (Home home : homes.values()) {
            list.add(home.toDocument());
        }
        doc.put("homes", list);
        return doc;
    }

    public static PlayerHomesData fromDocument(Document doc) {
        if (doc == null) return null;
        String uuidStr = doc.getString("uuid");
        if (uuidStr == null) return null;
        UUID uuid;
        try {
            uuid = UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            return null;
        }
        PlayerHomesData data = new PlayerHomesData(uuid);

        List<Document> list = doc.getList("homes", Document.class);
        if (list != null) {
            for (Document homeDoc : list) {
                Home home = Home.fromDocument(homeDoc);
                if (home != null) {
                    data.setHome(home);
                }
            }
        }
        return data;
    }
}
