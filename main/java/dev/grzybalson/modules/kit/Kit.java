package dev.grzybalson.modules.kit;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class Kit {

    private final String name;
    private String displayName;
    private String permission;
    private int cooldown;
    private int slot;
    private ItemStack icon;
    private List<ItemStack> items;
    private List<String> lore;
    private String prefix;

    public Kit(String name) {
        this.name = name;
        this.displayName = name;
        this.permission = "Holly.kit." + name.toLowerCase();
        this.cooldown = 3600;
        this.slot = 0;
        this.items = new ArrayList<>();
        this.lore = new ArrayList<>();
        this.prefix = "";
    }

    public Kit(
        String name,
        String displayName,
        String permission,
        int cooldown,
        int slot,
        ItemStack icon,
        List<ItemStack> items
    ) {
        this(name, displayName, permission, cooldown, slot, icon, items, List.of(), "");
    }

    public Kit(
        String name,
        String displayName,
        String permission,
        int cooldown,
        int slot,
        ItemStack icon,
        List<ItemStack> items,
        List<String> lore,
        String prefix
    ) {
        this.name = name;
        this.displayName = displayName != null ? displayName : name;
        this.permission = permission;
        this.cooldown = cooldown;
        this.slot = slot;
        this.icon = icon;
        this.items = items != null ? items : new ArrayList<>();
        this.lore = lore != null ? new ArrayList<>(lore) : new ArrayList<>();
        this.prefix = prefix != null ? prefix : "";
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getPermission() {
        return permission;
    }

    public void setPermission(String permission) {
        this.permission = permission;
    }

    public int getCooldown() {
        return cooldown;
    }

    public void setCooldown(int cooldown) {
        this.cooldown = cooldown;
    }

    public int getSlot() {
        return slot;
    }

    public void setSlot(int slot) {
        this.slot = slot;
    }

    public ItemStack getIcon() {
        return icon;
    }

    public void setIcon(ItemStack icon) {
        this.icon = icon;
    }

    public List<ItemStack> getItems() {
        return items;
    }

    public void setItems(List<ItemStack> items) {
        this.items = items != null ? items : new ArrayList<>();
    }

    public void addItem(ItemStack item) {
        if (item != null) {
            this.items.add(item);
        }
    }

    public void clearItems() {
        this.items.clear();
    }

    public List<String> getLore() {
        return lore;
    }

    public void setLore(List<String> lore) {
        this.lore = lore != null ? new ArrayList<>(lore) : new ArrayList<>();
    }

    public String getPrefix() {
        return prefix != null ? prefix : "";
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix != null ? prefix : "";
    }

    /**
     * Parse cooldown like "1h30m", "2d", "30m", "90s" into seconds.
     * Digits-only values are treated as seconds (HollyCore style).
     */
    public static int parseCooldownSeconds(String input) {
        if (input == null || input.isBlank()) {
            return 0;
        }
        String raw = input.trim().toLowerCase();
        if (raw.chars().allMatch(Character::isDigit)) {
            return Integer.parseInt(raw);
        }

        long totalMs = 0L;
        StringBuilder num = new StringBuilder();
        for (char c : raw.toCharArray()) {
            if (Character.isDigit(c)) {
                num.append(c);
                continue;
            }
            if (num.isEmpty()) {
                continue;
            }
            long val = Long.parseLong(num.toString());
            switch (c) {
                case 'd' -> totalMs += val * 86_400_000L;
                case 'h' -> totalMs += val * 3_600_000L;
                case 'm' -> totalMs += val * 60_000L;
                case 's' -> totalMs += val * 1_000L;
                default -> {
                }
            }
            num.setLength(0);
        }
        if (!num.isEmpty()) {
            totalMs += Long.parseLong(num.toString()) * 60_000L;
        }
        return (int) Math.max(0L, totalMs / 1000L);
    }

    public static String formatTime(long seconds) {
        if (seconds <= 0) {
            return "0s";
        }
        long days = seconds / 86400;
        seconds %= 86400;
        long hours = seconds / 3600;
        seconds %= 3600;
        long minutes = seconds / 60;
        seconds %= 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append("d ");
        }
        if (hours > 0) {
            sb.append(hours).append("h ");
        }
        if (minutes > 0) {
            sb.append(minutes).append("m ");
        }
        if (seconds > 0 && days == 0) {
            sb.append(seconds).append("s");
        }
        return sb.toString().trim();
    }
}
