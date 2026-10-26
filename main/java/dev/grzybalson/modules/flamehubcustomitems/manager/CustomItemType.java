package dev.grzybalson.modules.flamehubcustomitems.manager;

public enum CustomItemType {
    YODA("yoda"),
    VADER("vader");

    private final String id;

    CustomItemType(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public static CustomItemType fromId(String id) {
        if (id == null) {
            return null;
        }
        for (CustomItemType type : values()) {
            if (type.id.equalsIgnoreCase(id)) {
                return type;
            }
        }
        return null;
    }
}
