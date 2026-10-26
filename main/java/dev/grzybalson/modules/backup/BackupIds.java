package dev.grzybalson.modules.backup;

import org.bson.types.ObjectId;

import java.util.Locale;
import java.util.UUID;

public final class BackupIds {

    private BackupIds() {
    }

    public static String newRandom() {
        return "HB-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
    }

    public static String fromObjectId(ObjectId objectId) {
        if (objectId == null) {
            return newRandom();
        }
        String hex = objectId.toHexString().toUpperCase(Locale.ROOT);
        return "HB-" + hex.substring(Math.max(0, hex.length() - 8));
    }

    public static boolean isPublicId(String value) {
        if (value == null) {
            return false;
        }
        String normalized = normalize(value);
        return normalized.length() >= 6 && normalized.startsWith("HB-");
    }

    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
