package dev.grzybalson.utils;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;

/**
 * Nakładanie EQ gracza bez wycierania zbroi.
 * Bukkit {@code PlayerInventory.setContents(items)} przy tablicy krótszej niż
 * {@code getSize()} (41: storage + armor + offhand) zeruje pozostałe sloty —
 * w tym klatę, elytrę i offhand.
 */
public final class PlayerInventories {

    public static final int STORAGE_SIZE = 36;
    public static final int ARMOR_SIZE = 4;

    private PlayerInventories() {
    }

    public static void apply(Player player, ItemStack[] inventory, ItemStack[] armor) {
        apply(player, inventory, armor, extraOf(inventory, null));
    }

    public static void applyStorageAndArmor(Player player, ItemStack[] inventory, ItemStack[] armor) {
        if (player == null || !player.isOnline()) {
            return;
        }
        PlayerInventory inv = player.getInventory();
        inv.setStorageContents(storageOf(inventory));
        inv.setArmorContents(armorOf(inventory, armor));
    }

    /**
     * @param extra {@code null} = offhand ze snapshotu 41-slotowego albo wyczyść;
     *              nie-null (także pusta tablica) = zawsze nałóż extra/offhand
     */
    public static void apply(Player player, ItemStack[] inventory, ItemStack[] armor, ItemStack[] extra) {
        if (player == null || !player.isOnline()) {
            return;
        }
        applyStorageAndArmor(player, inventory, armor);
        applyOffhand(player.getInventory(), inventory, extra);
    }

    public static ItemStack[] storageOf(ItemStack[] inventory) {
        ItemStack[] storage = new ItemStack[STORAGE_SIZE];
        if (inventory == null) {
            return storage;
        }
        System.arraycopy(inventory, 0, storage, 0, Math.min(STORAGE_SIZE, inventory.length));
        return storage;
    }

    public static ItemStack[] armorOf(ItemStack[] inventory, ItemStack[] armor) {
        ItemStack[] result = new ItemStack[ARMOR_SIZE];
        if (hasItems(armor)) {
            System.arraycopy(armor, 0, result, 0, Math.min(ARMOR_SIZE, armor.length));
            return result;
        }
        if (inventory != null && inventory.length >= STORAGE_SIZE + ARMOR_SIZE) {
            System.arraycopy(inventory, STORAGE_SIZE, result, 0, ARMOR_SIZE);
        }
        return result;
    }

    public static ItemStack[] extraOf(ItemStack[] inventory, ItemStack[] extra) {
        if (extra != null) {
            return extra;
        }
        if (inventory != null && inventory.length > STORAGE_SIZE + ARMOR_SIZE) {
            return new ItemStack[]{inventory[STORAGE_SIZE + ARMOR_SIZE]};
        }
        return new ItemStack[0];
    }

    private static void applyOffhand(PlayerInventory inv, ItemStack[] inventory, ItemStack[] extra) {
        if (extra != null) {
            inv.setExtraContents(extra);
            return;
        }
        if (inventory != null && inventory.length > STORAGE_SIZE + ARMOR_SIZE) {
            inv.setItemInOffHand(inventory[STORAGE_SIZE + ARMOR_SIZE]);
            return;
        }
        inv.setItemInOffHand(null);
    }

    public static boolean hasItems(ItemStack[] items) {
        if (items == null) {
            return false;
        }
        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Storage (0-35) + puste sloty zbroi (36-39) + offhand (40).
     * Zbroja musi iść osobnym polem — {@code getContents()} trzyma ją też na 36-39.
     */
    public static ItemStack[] combineStorageAndExtra(ItemStack[] storage, ItemStack[] extra) {
        ItemStack[] result = new ItemStack[STORAGE_SIZE + ARMOR_SIZE + 1];
        copyCloned(storage, result, 0, STORAGE_SIZE);
        ItemStack[] extras = extraOf(null, extra);
        if (extras.length > 0 && extras[0] != null && !extras[0].getType().isAir()) {
            result[STORAGE_SIZE + ARMOR_SIZE] = extras[0].clone();
        }
        return result;
    }

    /**
     * Stare death backupy pakowały {@code getContents()} (zbroja w inventory + osobne pole armor).
     * Restore dawał x2 armor. Tu wywalamy kopie zbroi z inventory, spary w storage zostają.
     */
    public static ItemStack[] withoutEquippedArmorCopies(ItemStack[] inventory, ItemStack[] armor) {
        if (inventory == null || inventory.length == 0) {
            return inventory;
        }
        if (inventory.length >= STORAGE_SIZE + ARMOR_SIZE) {
            ItemStack[] copy = inventory.clone();
            int armorEnd = Math.min(copy.length, STORAGE_SIZE + ARMOR_SIZE);
            for (int i = STORAGE_SIZE; i < armorEnd; i++) {
                copy[i] = null;
            }
            return copy;
        }
        if (!hasItems(armor)) {
            return inventory;
        }
        List<ItemStack> expected = new ArrayList<>(ARMOR_SIZE);
        for (ItemStack piece : armor) {
            if (piece != null && !piece.getType().isAir()) {
                expected.add(piece);
            }
        }
        if (expected.isEmpty()) {
            return inventory;
        }
        int end = inventory.length;
        while (end > 0 && isEmpty(inventory[end - 1])) {
            end--;
        }
        if (end > 0 && !isSimilar(inventory[end - 1], expected.get(expected.size() - 1))) {
            end--;
            while (end > 0 && isEmpty(inventory[end - 1])) {
                end--;
            }
        }
        if (end < expected.size()) {
            return inventory;
        }
        int start = end - expected.size();
        for (int i = 0; i < expected.size(); i++) {
            if (!isSimilar(inventory[start + i], expected.get(i))) {
                return inventory;
            }
        }
        ItemStack[] stripped = new ItemStack[inventory.length - expected.size()];
        System.arraycopy(inventory, 0, stripped, 0, start);
        System.arraycopy(inventory, end, stripped, start, inventory.length - end);
        return stripped;
    }

    private static void copyCloned(ItemStack[] source, ItemStack[] dest, int destOffset, int max) {
        if (source == null) {
            return;
        }
        int n = Math.min(max, source.length);
        for (int i = 0; i < n; i++) {
            ItemStack item = source[i];
            dest[destOffset + i] = item == null || item.getType().isAir() ? null : item.clone();
        }
    }

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir();
    }

    private static boolean isSimilar(ItemStack left, ItemStack right) {
        return left != null && right != null && left.isSimilar(right);
    }
}
