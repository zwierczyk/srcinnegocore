package dev.grzybalson.config.modules.warps;

import eu.okaeri.configs.OkaeriConfig;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WarpDataConfig extends OkaeriConfig {

    private Map<String, WarpEntry> warps = new HashMap<>();

    public Map<String, WarpEntry> getWarps() { return warps; }

    public void setWarps(Map<String, WarpEntry> warps) { this.warps = warps; }

    public void addWarp(String name, WarpEntry warp) {
        warps.put(name.toLowerCase(), warp);
        save();
    }

    public void removeWarp(String name) {
        warps.remove(name.toLowerCase());
        save();
    }

    public WarpEntry getWarp(String name) {
        return warps.get(name.toLowerCase());
    }

    public boolean hasWarp(String name) {
        return warps.containsKey(name.toLowerCase());
    }

    public static class WarpEntry extends OkaeriConfig {
        private String world;
        private double x;
        private double y;
        private double z;
        private float yaw;
        private float pitch;
        private String permission;
        private String itemMaterial;
        private String displayName;
        private List<String> lore;
        private int slot;

        public WarpEntry() {}

        public WarpEntry(String world, double x, double y, double z, float yaw, float pitch,
                        String permission, String itemMaterial, String displayName, List<String> lore, int slot) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
            this.permission = permission;
            this.itemMaterial = itemMaterial;
            this.displayName = displayName;
            this.lore = lore;
            this.slot = slot;
        }

        public String getWorld() { return world; }
        public double getX() { return x; }
        public double getY() { return y; }
        public double getZ() { return z; }
        public float getYaw() { return yaw; }
        public float getPitch() { return pitch; }
        public String getPermission() { return permission; }
        public String getItemMaterial() { return itemMaterial; }
        public String getDisplayName() { return displayName; }
        public List<String> getLore() { return lore; }
        public int getSlot() { return slot; }

        public void setWorld(String world) { this.world = world; }
        public void setX(double x) { this.x = x; }
        public void setY(double y) { this.y = y; }
        public void setZ(double z) { this.z = z; }
        public void setYaw(float yaw) { this.yaw = yaw; }
        public void setPitch(float pitch) { this.pitch = pitch; }
        public void setPermission(String permission) { this.permission = permission; }
        public void setItemMaterial(String itemMaterial) { this.itemMaterial = itemMaterial; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public void setLore(List<String> lore) { this.lore = lore; }
        public void setSlot(int slot) { this.slot = slot; }
    }
}
