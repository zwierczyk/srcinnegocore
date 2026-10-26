package dev.grzybalson.modules.renderdistance.model;

public record ConfiguredRenderRegion(
    String configKey,
    String worldName,
    String regionId,
    String normalizedRegionId,
    int priority,
    int renderDistance
) {
}
