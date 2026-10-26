package dev.grzybalson.modules.renderdistance.model;

public record RenderDistanceStatus(
    String playerName,
    String worldName,
    String activeEntryName,
    String worldGuardRegionName,
    int renderDistance,
    int hiddenPlayers,
    boolean defaults
) {
}
