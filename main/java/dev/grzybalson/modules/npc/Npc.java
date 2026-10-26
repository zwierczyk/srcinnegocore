package dev.grzybalson.modules.npc;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Npc {

    private final String id;
    private final int entityId;
    private final UUID uuid;

    private Location location;
    private String displayName;
    private boolean glowing;
    private String glowingColor;
    private String skinName;
    private String skinTexture;
    private String skinSignature;
    /** Maksymalny dystans (w blokach) w jakim NPC jest widoczny dla gracza. -1 = nielimitowany. */
    private int renderDistance;

    private final List<String> interactActions = new ArrayList<>();

    private final Map<String, ItemStack> equipment;

    public Npc(String id, int entityId, Location location) {
        this.id = id;
        this.entityId = entityId;
        this.uuid = UUID.randomUUID();
        this.location = location;
        this.displayName = id;
        this.glowing = false;
        this.glowingColor = "WHITE";
        this.skinName = null;
        this.skinTexture = null;
        this.skinSignature = null;
        this.renderDistance = -1;
        this.equipment = new HashMap<>();
    }

    public String getId() { return id; }
    public int getEntityId() { return entityId; }
    public UUID getUuid() { return uuid; }

    public Location getLocation() { return location; }
    public void setLocation(Location location) { this.location = location; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public boolean isGlowing() { return glowing; }
    public void setGlowing(boolean glowing) { this.glowing = glowing; }

    public String getGlowingColor() { return glowingColor; }
    public void setGlowingColor(String glowingColor) { this.glowingColor = glowingColor; }

    public String getSkinName() { return skinName; }
    public void setSkinName(String skinName) { this.skinName = skinName; }

    public String getSkinTexture() { return skinTexture; }
    public void setSkinTexture(String skinTexture) { this.skinTexture = skinTexture; }

    public String getSkinSignature() { return skinSignature; }
    public void setSkinSignature(String skinSignature) { this.skinSignature = skinSignature; }

    public int getRenderDistance() { return this.renderDistance; }
    public void setRenderDistance(int renderDistance) { this.renderDistance = renderDistance; }

    public List<String> getInteractActions() {
        return Collections.unmodifiableList(this.interactActions);
    }

    public void setInteractActions(List<String> actions) {
        this.interactActions.clear();
        if (actions == null) return;
        for (String a : actions) {
            if (a != null && !a.isBlank() && !a.equalsIgnoreCase("@none")) {
                this.interactActions.add(a.trim());
            }
        }
    }

    public String getInteractAction() {
        return this.interactActions.isEmpty() ? null : this.interactActions.get(0);
    }

    public void setInteractAction(String interactAction) {
        this.interactActions.clear();
        if (interactAction != null && !interactAction.isBlank() && !interactAction.equalsIgnoreCase("@none")) {
            this.interactActions.add(interactAction.trim());
        }
    }

    public void addInteractAction(String action) {
        if (action == null || action.isBlank() || action.equalsIgnoreCase("@none")) return;
        this.interactActions.add(action.trim());
    }

    public void clearInteractActions() {
        this.interactActions.clear();
    }

    public boolean removeInteractAction(String toRemove) {
        if (toRemove == null) return false;
        String t = toRemove.trim();
        return this.interactActions.removeIf(s -> s.equals(t) || s.trim().equals(t));
    }

    public Map<String, ItemStack> getEquipment() { return equipment; }

    public void setEquipment(String slot, ItemStack item) {
        if (item == null) {
            this.equipment.remove(slot);
        } else {
            this.equipment.put(slot, item);
        }
    }
}
