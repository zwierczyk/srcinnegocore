package dev.grzybalson.modules.itemsync;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.List;
import java.util.UUID;

public record PlayerInventorySnapshot(
    UUID uuid,
    String playerName,
    boolean death,
    boolean redirecting,
    ItemStack[] inventory,
    ItemStack[] armor,
    ItemStack[] extra,
    ItemStack[] enderChest,
    List<PotionEffect> potionEffects,
    StoredLocation location,
    double health,
    double maxHealth,
    int foodLevel,
    int level,
    float exp,
    int totalExp,
    int heldSlot,
    float walkSpeed,
    float flySpeed,
    boolean allowFlight,
    boolean flying,
    boolean sprinting,
    int fireTicks,
    float saturation,
    float exhaustion,
    boolean op,
    GameMode gameMode
) {

    public PlayerInventorySnapshot withRedirecting(boolean redirecting) {
        return new PlayerInventorySnapshot(
            uuid, playerName, death, redirecting, inventory, armor, extra, enderChest,
            potionEffects, location, health, maxHealth, foodLevel, level, exp, totalExp,
            heldSlot, walkSpeed, flySpeed, allowFlight, flying, sprinting, fireTicks,
            saturation, exhaustion, op, gameMode
        );
    }

    public PlayerInventorySnapshot withDeath(boolean deathFlag) {
        return new PlayerInventorySnapshot(
            uuid, playerName, deathFlag, redirecting, inventory, armor, extra, enderChest,
            potionEffects, location, health, maxHealth, foodLevel, level, exp, totalExp,
            heldSlot, walkSpeed, flySpeed, allowFlight, flying, sprinting, fireTicks,
            saturation, exhaustion, op, gameMode
        );
    }

    public record StoredLocation(
        String world,
        double x,
        double y,
        double z,
        float yaw,
        float pitch
    ) {
        public Location toLocation() {
            if (world == null || world.isBlank()) {
                return null;
            }
            org.bukkit.World bukkitWorld = org.bukkit.Bukkit.getWorld(world);
            if (bukkitWorld == null) {
                return null;
            }
            return new Location(bukkitWorld, x, y, z, yaw, pitch);
        }
    }
}
