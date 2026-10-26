package dev.grzybalson.config.configs;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.NameModifier;
import eu.okaeri.configs.annotation.NameStrategy;
import eu.okaeri.configs.annotation.Names;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
public class AntiLagConfig extends OkaeriConfig {

    private boolean disableRedstone = false;
    private boolean disablePistons = false;
    private boolean disableDispensersAndDroppers = false;
    private boolean disableHoppers = false;

    private int maxRedstoneUpdatesPerChunkTick = 80;
    private int maxPistonEventsPerChunkTick = 24;
    private int maxHopperMovesPerChunkTick = 48;
    private int maxHoppersPerChunk = 24;

    private int maxArmorStandsPerChunk = 4;
    private int maxMinecartsPerChunk = 2;
    private int maxBoatsPerChunk = 2;
    private int maxItemFramesPerChunk = 6;
    private int maxPaintingsPerChunk = 6;
    private int maxEndCrystalsPerChunk = 2;

    private boolean cleanupExcessEntities = true;
    private int entityCleanupIntervalTicks = 200;
    private int entityCleanupChunksPerRun = 32;
    private boolean logEntityCleanup = false;

    private String blockedMechanicMessage = "&#FF0000✘ &8Ξ &cTa mechanika jest wylaczona na serwerze.";
    private String blockedBlockMessage = "&#FF0000✘ &8Ξ &cTen blok jest wylaczony na serwerze.";
    private String armorStandLimitMessage = "&#FF0000✘ &8Ξ &cOsiagnieto limit {limit} armor standow na chunk!";
    private String minecartLimitMessage = "&#FF0000✘ &8Ξ &cOsiagnieto limit {limit} wagonikow na chunk!";
    private String boatLimitMessage = "&#FF0000✘ &8Ξ &cOsiagnieto limit {limit} lodzi na chunk!";
    private String itemFrameLimitMessage = "&#FF0000✘ &8Ξ &cOsiagnieto limit {limit} ramek na chunk!";
    private String paintingLimitMessage = "&#FF0000✘ &8Ξ &cOsiagnieto limit {limit} obrazow na chunk!";
    private String endCrystalLimitMessage = "&#FF0000✘ &8Ξ &cOsiagnieto limit {limit} krysztalow na chunk!";
    private String hopperLimitMessage = "&#FF0000✘ &8Ξ &cOsiagnieto limit {limit} hopperow na chunk!";

    private List<String> entityLimitIgnoredMetadata = Arrays.asList(
        "IS_ILLUSION_STAND",
        "NPC"
    );

    private List<String> blockedPlaceMaterials = new ArrayList<>();

    public boolean isDisableRedstone() { return disableRedstone; }
    public boolean isDisablePistons() { return disablePistons; }
    public boolean isDisableDispensersAndDroppers() { return disableDispensersAndDroppers; }
    public boolean isDisableHoppers() { return disableHoppers; }
    public int getMaxRedstoneUpdatesPerChunkTick() { return maxRedstoneUpdatesPerChunkTick; }
    public int getMaxPistonEventsPerChunkTick() { return maxPistonEventsPerChunkTick; }
    public int getMaxHopperMovesPerChunkTick() { return maxHopperMovesPerChunkTick; }
    public int getMaxHoppersPerChunk() { return maxHoppersPerChunk; }
    public int getMaxArmorStandsPerChunk() { return maxArmorStandsPerChunk; }
    public int getMaxMinecartsPerChunk() { return maxMinecartsPerChunk; }
    public int getMaxBoatsPerChunk() { return maxBoatsPerChunk; }
    public int getMaxItemFramesPerChunk() { return maxItemFramesPerChunk; }
    public int getMaxPaintingsPerChunk() { return maxPaintingsPerChunk; }
    public int getMaxEndCrystalsPerChunk() { return maxEndCrystalsPerChunk; }
    public boolean isCleanupExcessEntities() { return cleanupExcessEntities; }
    public int getEntityCleanupIntervalTicks() { return entityCleanupIntervalTicks; }
    public int getEntityCleanupChunksPerRun() { return entityCleanupChunksPerRun; }
    public boolean isLogEntityCleanup() { return logEntityCleanup; }
    public String getBlockedMechanicMessage() { return blockedMechanicMessage; }
    public String getBlockedBlockMessage() { return blockedBlockMessage; }
    public String getArmorStandLimitMessage() { return armorStandLimitMessage; }
    public String getMinecartLimitMessage() { return minecartLimitMessage; }
    public String getBoatLimitMessage() { return boatLimitMessage; }
    public String getItemFrameLimitMessage() { return itemFrameLimitMessage; }
    public String getPaintingLimitMessage() { return paintingLimitMessage; }
    public String getEndCrystalLimitMessage() { return endCrystalLimitMessage; }
    public String getHopperLimitMessage() { return hopperLimitMessage; }
    public List<String> getEntityLimitIgnoredMetadata() { return entityLimitIgnoredMetadata; }
    public List<String> getBlockedPlaceMaterials() { return blockedPlaceMaterials; }
}
