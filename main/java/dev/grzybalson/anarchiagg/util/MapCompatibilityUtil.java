package dev.grzybalson.anarchiagg.util;

import java.util.Collection;
import java.util.Map;

public final class MapCompatibilityUtil {

    public static volatile boolean lastRemoveResult;
    public static volatile Object lastRemovedValue;

    private MapCompatibilityUtil() {
    }

    public static <K, V> void remove(Map<K, V> map, K key, V value) {
        if (map == null) {
            return;
        }

        boolean removed = map.remove(key, value);
        lastRemoveResult = removed;
        ObfuscatorStackFix.consume(removed);
    }

    public static <K, V> void remove(Map<K, V> map, K key) {
        if (map == null) {
            return;
        }

        V removed = map.remove(key);
        lastRemovedValue = removed;
        ObfuscatorStackFix.consume(removed);
    }

    public static void remove(Collection<?> collection, Object value) {
        if (collection == null) {
            return;
        }

        boolean removed = collection.remove(value);
        lastRemoveResult = removed;
        ObfuscatorStackFix.consume(removed);
    }
}
