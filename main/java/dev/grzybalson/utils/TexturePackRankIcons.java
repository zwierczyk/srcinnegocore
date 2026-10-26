package dev.grzybalson.utils;

import java.util.Locale;
import java.util.Map;

/**
 * Ikony rang z texturepacka OGHUB (Nowy folder (7)/emotki.txt).
 * Fallback gdy LuckPerms nie ma prefixu / name-color na grupie bota.
 */
public final class TexturePackRankIcons {

    private static final Map<String, String> PREFIX_BY_GROUP = Map.ofEntries(
        Map.entry("root", "\uE023 "),
        Map.entry("wlasciciel", "\uE023 "),
        Map.entry("owner", "\uE023 "),
        Map.entry("tiktok", "\uE047 "),
        Map.entry("media", "\uE057 "),
        Map.entry("gracz", "\uE059 "),
        Map.entry("player", "\uE059 "),
        Map.entry("default", "\uE059 "),
        Map.entry("headadmin", "\uE05E "),
        Map.entry("hadmin", "\uE05E "),
        Map.entry("admin", "\uE069 "),
        Map.entry("moderator", "\uE074 "),
        Map.entry("mod", "\uE074 "),
        Map.entry("helper", "\uE02D "),
        Map.entry("thelper", "\uE02D "),
        Map.entry("trialhelper", "\uE02D "),
        Map.entry("svip", "\uE041 "),
        Map.entry("vip", "\uE042 "),
        Map.entry("mvip", "\uE042 "),
        Map.entry("sponsor", "\uE042 "),
        Map.entry("patron", "\uE042 "),
        Map.entry("elita", "\uE042 "),
        Map.entry("champion", "\uE042 "),
        Map.entry("boss", "\uE042 "),
        Map.entry("dig", "\uE042 "),
        Map.entry("partner", "\uE057 "),
        Map.entry("tworca", "\uE057 "),
        Map.entry("youtuber", "\uE057 "),
        Map.entry("yt", "\uE057 "),
        Map.entry("minimedia", "\uE057 ")
    );

    private static final Map<String, String> NAME_COLOR_BY_GROUP = Map.ofEntries(
        Map.entry("root", "&c"),
        Map.entry("wlasciciel", "&c"),
        Map.entry("owner", "&c"),
        Map.entry("headadmin", "&c"),
        Map.entry("hadmin", "&c"),
        Map.entry("admin", "&c"),
        Map.entry("mod", "&2"),
        Map.entry("moderator", "&2"),
        Map.entry("helper", "&b"),
        Map.entry("thelper", "&b"),
        Map.entry("trialhelper", "&b"),
        Map.entry("vip", "&e"),
        Map.entry("svip", "&e"),
        Map.entry("mvip", "&e"),
        Map.entry("gracz", "&b"),
        Map.entry("player", "&b"),
        Map.entry("default", "&b"),
        Map.entry("media", "&d"),
        Map.entry("tiktok", "&d"),
        Map.entry("partner", "&d"),
        Map.entry("sponsor", "&6"),
        Map.entry("patron", "&6")
    );

    private TexturePackRankIcons() {
    }

    public static String prefixFor(String groupName) {
        if (groupName == null || groupName.isBlank()) {
            return PREFIX_BY_GROUP.getOrDefault("gracz", "");
        }
        String key = groupName.toLowerCase(Locale.ROOT);
        return PREFIX_BY_GROUP.getOrDefault(key, PREFIX_BY_GROUP.getOrDefault("gracz", ""));
    }

    public static String nameColorFor(String groupName) {
        if (groupName == null || groupName.isBlank()) {
            return NAME_COLOR_BY_GROUP.getOrDefault("gracz", "&7");
        }
        String key = groupName.toLowerCase(Locale.ROOT);
        return NAME_COLOR_BY_GROUP.getOrDefault(key, NAME_COLOR_BY_GROUP.getOrDefault("gracz", "&7"));
    }
}
