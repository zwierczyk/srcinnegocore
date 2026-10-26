package dev.grzybalson.modules.moderation;

import java.util.UUID;

public final class ModerationPunishment {

    public enum Type {
        BAN,
        MUTE
    }

    private final Type type;
    private final UUID uuid;
    private final String name;
    private final String ip;
    private final String reason;
    private final String staff;
    private final long createdAt;
    private final long expiresAt;
    private final boolean ipBan;

    public ModerationPunishment(
        Type type,
        UUID uuid,
        String name,
        String ip,
        String reason,
        String staff,
        long createdAt,
        long expiresAt
    ) {
        this(type, uuid, name, ip, reason, staff, createdAt, expiresAt, false);
    }

    public ModerationPunishment(
        Type type,
        UUID uuid,
        String name,
        String ip,
        String reason,
        String staff,
        long createdAt,
        long expiresAt,
        boolean ipBan
    ) {
        this.type = type;
        this.uuid = uuid;
        this.name = name;
        this.ip = ip == null ? "" : ip;
        this.reason = reason == null || reason.isBlank() ? "Brak powodu" : reason;
        this.staff = staff == null || staff.isBlank() ? "Konsola" : staff;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.ipBan = ipBan;
    }

    public Type type() {
        return type;
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public String ip() {
        return ip;
    }

    public String reason() {
        return reason;
    }

    public String staff() {
        return staff;
    }

    public long createdAt() {
        return createdAt;
    }

    public long expiresAt() {
        return expiresAt;
    }

    public boolean ipBan() {
        return ipBan;
    }

    public boolean isPermanent() {
        return expiresAt <= 0L;
    }

    public boolean isActive(long now) {
        return isPermanent() || expiresAt > now;
    }

    public long remainingMillis(long now) {
        if (isPermanent()) {
            return -1L;
        }
        return Math.max(0L, expiresAt - now);
    }
}
