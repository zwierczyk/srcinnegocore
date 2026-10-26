package dev.grzybalson.modules.cases;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CaseDefinition {

    private final String name;
    private boolean enabled;
    private String inventoryName;
    private Boolean messageEnabled;
    private String messageText;
    private final List<CaseBlockLocation> locations;
    private ItemStack key;
    private CaseKeyType keyType;
    private final List<CaseReward> rewards;
    private double rewardChanceTotal;
    private boolean individualGui;
    private List<CaseGuiDecoration> previewDecorations;
    private List<CaseGuiDecoration> animationDecorations;
    private List<CaseGuiDecoration> resultDecorations;

    public CaseDefinition(
        String name,
        boolean enabled,
        String inventoryName,
        Boolean messageEnabled,
        String messageText,
        List<CaseBlockLocation> locations,
        ItemStack key,
        CaseKeyType keyType,
        List<CaseReward> rewards
    ) {
        this.name = name;
        this.enabled = enabled;
        this.inventoryName = inventoryName;
        this.messageEnabled = messageEnabled;
        this.messageText = messageText;
        this.locations = new ArrayList<>(locations == null ? Collections.emptyList() : locations);
        this.key = key;
        this.keyType = keyType == null ? CaseKeyType.KEY : keyType;
        this.rewards = new ArrayList<>(rewards);
        this.individualGui = false;
        this.previewDecorations = null;
        this.animationDecorations = null;
        this.resultDecorations = null;
        recalculateRewardChanceTotal();
    }

    public String name() {
        return name;
    }

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String inventoryName() {
        return inventoryName;
    }

    public void setInventoryName(String inventoryName) {
        this.inventoryName = inventoryName;
    }

    public Boolean messageEnabled() {
        return messageEnabled;
    }

    public void setMessageEnabled(Boolean messageEnabled) {
        this.messageEnabled = messageEnabled;
    }

    public String messageText() {
        return messageText;
    }

    public void setMessageText(String messageText) {
        this.messageText = messageText;
    }

    public List<CaseBlockLocation> locations() {
        return Collections.unmodifiableList(locations);
    }

    public boolean addLocation(CaseBlockLocation location) {
        if (location == null || locations.contains(location)) {
            return false;
        }
        locations.add(location);
        return true;
    }

    public boolean removeLocation(CaseBlockLocation location) {
        return locations.remove(location);
    }

    public void clearLocations() {
        locations.clear();
    }

    public ItemStack key() {
        return key;
    }

    public void setKey(ItemStack key) {
        this.key = key;
    }

    public CaseKeyType keyType() {
        return keyType;
    }

    public void setKeyType(CaseKeyType keyType) {
        this.keyType = keyType == null ? CaseKeyType.KEY : keyType;
    }

    public List<CaseReward> rewards() {
        return Collections.unmodifiableList(rewards);
    }

    public double rewardChanceTotal() {
        return rewardChanceTotal;
    }

    public void replaceRewards(List<CaseReward> rewards) {
        this.rewards.clear();
        this.rewards.addAll(rewards);
        recalculateRewardChanceTotal();
    }

    private void recalculateRewardChanceTotal() {
        double total = 0.0D;
        for (CaseReward reward : rewards) {
            total += Math.max(0.0D, reward.chance());
        }
        this.rewardChanceTotal = total;
    }

    public boolean individualGui() {
        return individualGui;
    }

    public void setIndividualGui(boolean individualGui) {
        this.individualGui = individualGui;
    }

    public List<CaseGuiDecoration> previewDecorations() {
        return previewDecorations == null ? null : Collections.unmodifiableList(previewDecorations);
    }

    public void setPreviewDecorations(List<CaseGuiDecoration> decorations) {
        this.previewDecorations = decorations == null ? null : new ArrayList<>(decorations);
    }

    public List<CaseGuiDecoration> animationDecorations() {
        return animationDecorations == null ? null : Collections.unmodifiableList(animationDecorations);
    }

    public void setAnimationDecorations(List<CaseGuiDecoration> decorations) {
        this.animationDecorations = decorations == null ? null : new ArrayList<>(decorations);
    }

    public List<CaseGuiDecoration> resultDecorations() {
        return resultDecorations == null ? null : Collections.unmodifiableList(resultDecorations);
    }

    public void setResultDecorations(List<CaseGuiDecoration> decorations) {
        this.resultDecorations = decorations == null ? null : new ArrayList<>(decorations);
    }
}
