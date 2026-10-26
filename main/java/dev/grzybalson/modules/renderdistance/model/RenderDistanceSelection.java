package dev.grzybalson.modules.renderdistance.model;

public record RenderDistanceSelection(
    ConfiguredRenderRegion region,
    int renderDistance,
    boolean defaults
) {
    public String entryName() {
        return region == null ? "defaults" : region.configKey();
    }

    public String worldGuardRegionName() {
        return region == null ? "-" : region.regionId();
    }
}
