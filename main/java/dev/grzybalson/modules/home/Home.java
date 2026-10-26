package dev.grzybalson.modules.home;

import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public class Home {

    private int homeNumber;
    private String name;
    private String world;
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;
    private String iconMaterial;
    private boolean showCoordinates;
    private long createdAt;

    public Home(int homeNumber, String name, String world, double x, double y, double z, float yaw, float pitch, String iconMaterial, boolean showCoordinates, long createdAt) {
        this.homeNumber = homeNumber;
        this.name = name;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.iconMaterial = iconMaterial != null ? iconMaterial : "WHITE_BED";
        this.showCoordinates = showCoordinates;
        this.createdAt = createdAt;
    }

    public int getHomeNumber() { return homeNumber; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getWorld() { return world; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
    public String getIconMaterial() { return iconMaterial; }
    public void setIconMaterial(String iconMaterial) { this.iconMaterial = iconMaterial; }
    public boolean isShowCoordinates() { return showCoordinates; }
    public void setShowCoordinates(boolean showCoordinates) { this.showCoordinates = showCoordinates; }
    public long getCreatedAt() { return createdAt; }

    public Location getLocation() {
        World w = Bukkit.getWorld(world);
        if (w == null) return null;
        return new Location(w, x, y, z, yaw, pitch);
    }

    public Document toDocument() {
        Document doc = new Document();
        doc.put("homeNumber", homeNumber);
        doc.put("name", name);
        doc.put("world", world);
        doc.put("x", x);
        doc.put("y", y);
        doc.put("z", z);
        doc.put("yaw", (double) yaw);
        doc.put("pitch", (double) pitch);
        doc.put("iconMaterial", iconMaterial);
        doc.put("showCoordinates", showCoordinates);
        doc.put("createdAt", createdAt);
        return doc;
    }

    public static Home fromDocument(Document doc) {
        if (doc == null) return null;
        int homeNumber = doc.getInteger("homeNumber", 1);
        String name = doc.getString("name");
        String world = doc.getString("world");
        double x = doc.getDouble("x");
        double y = doc.getDouble("y");
        double z = doc.getDouble("z");
        float yaw = doc.getDouble("yaw") != null ? doc.getDouble("yaw").floatValue() : 0.0f;
        float pitch = doc.getDouble("pitch") != null ? doc.getDouble("pitch").floatValue() : 0.0f;
        String iconMaterial = doc.getString("iconMaterial");
        boolean showCoordinates = doc.getBoolean("showCoordinates", false);
        long createdAt = doc.getLong("createdAt") != null ? doc.getLong("createdAt") : System.currentTimeMillis();

        return new Home(homeNumber, name, world, x, y, z, yaw, pitch, iconMaterial, showCoordinates, createdAt);
    }
}
