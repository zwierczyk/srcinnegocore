package dev.grzybalson.anarchiagg.util;

public final class PrimitiveCompatibilityUtil {

    private static volatile boolean lastBoolean;

    private PrimitiveCompatibilityUtil() {
    }

    public static void consume(boolean value) {
        lastBoolean = value;
    }
}
