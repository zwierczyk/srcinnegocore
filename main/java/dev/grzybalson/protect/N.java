package dev.grzybalson.protect;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Native anti-debug / anti-attach. Nazwy klas nie rename'ować. */
public final class N {

    private static volatile boolean loaded;

    private N() {
    }

    public static synchronized void attach() {
        load();
        if (ok() != 1) {
            throw new IllegalStateException("protected runtime");
        }
    }

    public static native int ok();

    private static void load() {
        if (loaded) {
            return;
        }
        String os = System.getProperty("os.name", "").toLowerCase();
        boolean windows = os.contains("win");
        boolean linux = os.contains("linux") || os.contains("nux");
        if (!windows && !linux) {
            throw new IllegalStateException("unsupported os");
        }
        String resource = windows ? "META-INF/gb/w" : "META-INF/gb/l";
        byte[] packed;
        try (InputStream stream = N.class.getClassLoader().getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("missing native image");
            }
            packed = stream.readAllBytes();
        } catch (Exception exception) {
            throw new IllegalStateException("native image", exception);
        }
        byte[] raw = packed.clone();
        for (int i = 0; i < raw.length; i++) {
            raw[i] ^= (byte) (0x3D + i * 7);
        }
        try {
            Path file = Files.createTempFile(".g", windows ? ".dll" : ".so");
            Files.write(file, raw);
            file.toFile().deleteOnExit();
            System.load(file.toAbsolutePath().toString());
            loaded = true;
        } catch (Exception exception) {
            throw new IllegalStateException("native load", exception);
        } finally {
            for (int i = 0; i < raw.length; i++) {
                raw[i] = 0;
            }
        }
    }
}
