package dev.grzybalson.modules.trade;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

final class TradeSlots {

    static final int SIZE = 54;
    static final int OFFER_CAPACITY = 16;

    private static final int[] SIDE_A = {
        0, 1, 2, 3,
        9, 10, 11, 12,
        18, 19, 20, 21,
        27, 28, 29, 30
    };

    private static final int[] SIDE_B = {
        5, 6, 7, 8,
        14, 15, 16, 17,
        23, 24, 25, 26,
        32, 33, 34, 35
    };

    private static final int[] DIVIDER = {4, 13, 22, 31, 40};
    static final int READY_A = 45;
    static final int READY_B = 53;
    static final int STATUS = 49;

    private static final Set<Integer> ALL_OFFER = new HashSet<>();

    static {
        for (int slot : SIDE_A) {
            ALL_OFFER.add(slot);
        }
        for (int slot : SIDE_B) {
            ALL_OFFER.add(slot);
        }
    }

    private TradeSlots() {
    }

    static int[] sideSlots(boolean sideA) {
        return sideA ? SIDE_A : SIDE_B;
    }

    static int offerIndex(int guiSlot, boolean sideA) {
        int[] slots = sideSlots(sideA);
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == guiSlot) {
                return i;
            }
        }
        return -1;
    }

    static int guiSlot(int offerIndex, boolean sideA) {
        return sideSlots(sideA)[offerIndex];
    }

    static boolean isOfferSlot(int slot) {
        return ALL_OFFER.contains(slot);
    }

    static boolean isOwnOfferSlot(int slot, boolean viewerIsA) {
        int[] own = sideSlots(viewerIsA);
        for (int value : own) {
            if (value == slot) {
                return true;
            }
        }
        return false;
    }

    static boolean isPartnerOfferSlot(int slot, boolean viewerIsA) {
        return isOfferSlot(slot) && !isOwnOfferSlot(slot, viewerIsA);
    }

    static boolean isInteractiveSlot(int slot) {
        return isOfferSlot(slot) || slot == READY_A || slot == READY_B;
    }

    static int[] dividerSlots() {
        return DIVIDER.clone();
    }

    static Set<Integer> decorativeSlots() {
        Set<Integer> blocked = new HashSet<>(Arrays.asList(
            36, 37, 38, 39, 41, 42, 43, 44,
            46, 47, 48, 50, 51, 52
        ));
        for (int slot : DIVIDER) {
            blocked.add(slot);
        }
        blocked.add(STATUS);
        return blocked;
    }
}
