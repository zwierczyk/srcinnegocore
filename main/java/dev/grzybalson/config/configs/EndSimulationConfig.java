package dev.grzybalson.config.configs;

import eu.okaeri.configs.OkaeriConfig;
import java.util.Arrays;
import java.util.List;

public class EndSimulationConfig extends OkaeriConfig {

    // Module enabled
    private boolean enabled = true;

    // End Crystal settings
    private boolean crystalEnabled = true;
    private List<String> crystalAllowedWorlds = Arrays.asList("world_the_end", "end_simulation");
    private boolean crystalBlockDamage = false;
    private double crystalDamageMultiplier = 1.0;
    private double crystalMaxDamage = 20.0;
    private double crystalExplosionRadius = 6.0;
    private boolean crystalRemoveFire = true;

    // Block placement restrictions in allowed worlds
    private boolean blockPlacementRestriction = true;
    private List<String> allowedBlocks = Arrays.asList("OBSIDIAN", "COBWEB");
    private int cobwebRemoveTimeSeconds = 300;
    private String blockNotAllowedMessage = "&#FF0000✘ &8Ξ &cMożesz stawiać tylko obsydian i pajęczyny!";

    // End Simulation settings
    private String simulationWarpWorld = "world";
    private double simulationWarpX = 0.0;
    private double simulationWarpY = 100.0;
    private double simulationWarpZ = 0.0;
    private float simulationWarpYaw = 0.0f;
    private float simulationWarpPitch = 0.0f;
    private boolean simulationWarpSet = false;

    // Kit data (serialized)
    private String kitContentsData = "";
    private String kitArmorData = "";
    private String kitOffhandData = "";

    // Messages
    private String joinMessage = "&#00FF3B✔ &8Ξ &#4EFA76Dołączyłeś do symulacji endu!";
    private String leaveMessage = "&#00FF3B✔ &8Ξ &#4EFA76Opuściłeś symulację endu!";
    private String alreadyInSimulation = "&#FF0000✘ &8Ξ &cJesteś już w symulacji endu!";
    private String notInSimulation = "&#FF0000✘ &8Ξ &cNie jesteś w symulacji endu!";
    private String warpNotSet = "&#FF0000✘ &8Ξ &cWarp symulacji nie został ustawiony!";
    private String warpSetMessage = "&#00FF3B✔ &8Ξ &#4EFA76Ustawiono warp symulacji endu!";
    private String kitNotSet = "&#FF0000✘ &8Ξ &cKit symulacji nie został ustawiony!";
    private String kitSavedMessage = "&#00FF3B✔ &8Ξ &#4EFA76Zapisano kit symulacji endu!";
    private String kitEditGuiTitle = "&8» &c&lEdytuj Kit Symulacji";
    private String crystalWorldNotAllowed = "&#FF0000✘ &8Ξ &cCrystale nie działają w tym świecie!";

    // Getters - Module
    public boolean isEnabled() { return enabled; }

    // Getters - Crystal
    public boolean isCrystalEnabled() { return crystalEnabled; }
    public List<String> getCrystalAllowedWorlds() { return crystalAllowedWorlds; }
    public boolean isCrystalBlockDamage() { return crystalBlockDamage; }
    public double getCrystalDamageMultiplier() { return crystalDamageMultiplier; }
    public double getCrystalMaxDamage() { return crystalMaxDamage; }
    public double getCrystalExplosionRadius() { return crystalExplosionRadius; }
    public boolean isCrystalRemoveFire() { return crystalRemoveFire; }

    // Getters - Block placement
    public boolean isBlockPlacementRestriction() { return blockPlacementRestriction; }
    public List<String> getAllowedBlocks() { return allowedBlocks; }
    public int getCobwebRemoveTimeSeconds() { return cobwebRemoveTimeSeconds; }
    public String getBlockNotAllowedMessage() { return blockNotAllowedMessage; }

    // Getters/Setters - Kit data
    public String getKitContentsData() { return kitContentsData; }
    public String getKitArmorData() { return kitArmorData; }
    public String getKitOffhandData() { return kitOffhandData; }

    public void setKitData(String contents, String armor, String offhand) {
        this.kitContentsData = contents != null ? contents : "";
        this.kitArmorData = armor != null ? armor : "";
        this.kitOffhandData = offhand != null ? offhand : "";
    }

    // Getters - Simulation
    public String getSimulationWarpWorld() { return simulationWarpWorld; }
    public double getSimulationWarpX() { return simulationWarpX; }
    public double getSimulationWarpY() { return simulationWarpY; }
    public double getSimulationWarpZ() { return simulationWarpZ; }
    public float getSimulationWarpYaw() { return simulationWarpYaw; }
    public float getSimulationWarpPitch() { return simulationWarpPitch; }
    public boolean isSimulationWarpSet() { return simulationWarpSet; }

    // Setters - Simulation warp
    public void setSimulationWarp(String world, double x, double y, double z, float yaw, float pitch) {
        this.simulationWarpWorld = world;
        this.simulationWarpX = x;
        this.simulationWarpY = y;
        this.simulationWarpZ = z;
        this.simulationWarpYaw = yaw;
        this.simulationWarpPitch = pitch;
        this.simulationWarpSet = true;
    }

    // Getters - Messages
    public String getJoinMessage() { return joinMessage; }
    public String getLeaveMessage() { return leaveMessage; }
    public String getAlreadyInSimulation() { return alreadyInSimulation; }
    public String getNotInSimulation() { return notInSimulation; }
    public String getWarpNotSet() { return warpNotSet; }
    public String getWarpSetMessage() { return warpSetMessage; }
    public String getKitNotSet() { return kitNotSet; }
    public String getKitSavedMessage() { return kitSavedMessage; }
    public String getKitEditGuiTitle() { return kitEditGuiTitle; }
    public String getCrystalWorldNotAllowed() { return crystalWorldNotAllowed; }
}
