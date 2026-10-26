package dev.grzybalson.config.configs;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.NameModifier;
import eu.okaeri.configs.annotation.NameStrategy;
import eu.okaeri.configs.annotation.Names;

@Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
public class SpawnConfig extends OkaeriConfig {

    private boolean spawnSet = false;
    private boolean spawnOnJoin = true;
    private boolean spawnOnFirstJoin = true;
    private boolean spawnOnRespawn = true;

    @eu.okaeri.configs.annotation.Comment("Worldy (np. pvp/event): przy wejsciu na serwer TP na /spawn. Inne worldy = bez TP.")
    private java.util.List<String> forceSpawnWorlds = java.util.List.of("pvp", "event", "teparki");

    private String world = "world";

    private double x = 0.0;
    private double y = 64.0;
    private double z = 0.0;
    private float yaw = 0.0f;
    private float pitch = 0.0f;
    private int teleportDelay = 5;

    private String countdownTitle = "&x&3&C&E&4&F&3&lᴛ&x&3&A&D&2&E&0&lᴇ&x&3&9&C&0&C&C&lʟ&x&3&7&A&E&B&9&lᴇ&x&3&5&9&C&A&5&lᴘ&x&3&4&8&A&9&2&lᴏ&x&3&2&7&8&7&E&lʀ&x&3&4&8&E&9&5&lᴛ&x&3&6&A&3&A&D&lᴀ&x&3&8&B&9&C&4&lᴄ&x&3&A&C&E&D&C&lᴊ&x&3&C&E&4&F&3&lᴀ";
    private String countdownSubtitle = "&fzostaniesz przeteleportowany za: {time}";

    private String spawnSetMessage = "&a» &7Punkt spawnu został ustawiony!";
    private String spawnTeleportMessage = "&a» &7Przeteleportowano na spawn!";
    private String spawnNotSetMessage = "&c» &7Punkt spawnu nie jest jeszcze ustawiony!";
    private String spawnTeleportingMessage = "&fZostaniesz przeniesiony za &#77F454{TIME}";
    private String spawnTeleportCancelledMessage = "&c» &7Teleportacja przerwana &8(&#AAAAAAruszyłeś się&8)!";

    public String getSpawnSetMessage() { return spawnSetMessage; }
    public String getSpawnTeleportMessage() { return spawnTeleportMessage; }
    public String getSpawnNotSetMessage() { return spawnNotSetMessage; }
    public String getSpawnTeleportingMessage() { return spawnTeleportingMessage; }
    public String getSpawnTeleportCancelledMessage() { return spawnTeleportCancelledMessage; }

    public boolean isSpawnSet() { return spawnSet; }
    public void setSpawnSet(boolean spawnSet) { this.spawnSet = spawnSet; }
    public String getWorld() { return world; }
    public void setWorld(String world) { this.world = world; }
    public double getX() { return x; }
    public void setX(double x) { this.x = x; }
    public double getY() { return y; }
    public void setY(double y) { this.y = y; }
    public double getZ() { return z; }
    public void setZ(double z) { this.z = z; }
    public float getYaw() { return yaw; }
    public void setYaw(float yaw) { this.yaw = yaw; }
    public float getPitch() { return pitch; }
    public void setPitch(float pitch) { this.pitch = pitch; }
    public int getTeleportDelay() { return teleportDelay; }
    public String getCountdownTitle() { return countdownTitle; }
    public String getCountdownSubtitle() { return countdownSubtitle; }
    public boolean isSpawnOnJoin() { return spawnOnJoin; }
    public boolean isSpawnOnFirstJoin() { return spawnOnFirstJoin; }
    public void setSpawnOnFirstJoin(boolean spawnOnFirstJoin) { this.spawnOnFirstJoin = spawnOnFirstJoin; }
    public boolean isSpawnOnRespawn() { return spawnOnRespawn; }

    public java.util.List<String> getForceSpawnWorlds() {
        return forceSpawnWorlds == null || forceSpawnWorlds.isEmpty()
            ? java.util.List.of("pvp", "event", "teparki")
            : forceSpawnWorlds;
    }

    public boolean isForceSpawnWorld(String worldName) {
        if (worldName == null || worldName.isBlank()) {
            return false;
        }
        String trimmed = worldName.trim();
        if (trimmed.equalsIgnoreCase("teparki")) {
            return true;
        }
        if (dev.grzybalson.modules.event.dungeon.DungeonWorldService.isDungeonWorld(trimmed)) {
            return true;
        }
        for (String name : getForceSpawnWorlds()) {
            if (name != null && name.equalsIgnoreCase(trimmed)) {
                return true;
            }
        }
        return false;
    }
}
