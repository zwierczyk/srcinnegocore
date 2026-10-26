package dev.grzybalson.anarchiagg.util;


public final class ObfuscatorStackFix {
    public static volatile Object objectSink;
    public static volatile boolean booleanSink;
    public static volatile int intSink;
    public static volatile long longSink;

    private ObfuscatorStackFix() {
    }

    public static void consume(Object value) {
        objectSink = value;
    }

    public static void consume(boolean value) {
        booleanSink = value;
    }

    public static void consume(int value) {
        intSink = value;
    }

    public static void consume(long value) {
        longSink = value;
    }
}
