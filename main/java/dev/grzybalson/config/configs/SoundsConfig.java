package dev.grzybalson.config.configs;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.NameModifier;
import eu.okaeri.configs.annotation.NameStrategy;
import eu.okaeri.configs.annotation.Names;

@Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
public class SoundsConfig extends OkaeriConfig {

    private SoundEntry teleportStart = new SoundEntry("BLOCK_NOTE_BLOCK_PLING", 1.0f, 1.0f);
    private SoundEntry teleportEnd = new SoundEntry("ENTITY_ENDERMAN_TELEPORT", 1.0f, 1.0f);
    private SoundEntry teleportCancelled = new SoundEntry("ENTITY_VILLAGER_NO", 1.0f, 1.0f);

    private SoundEntry gamemodeChange = new SoundEntry("BLOCK_NOTE_BLOCK_CHIME", 1.0f, 1.2f);

    private SoundEntry flyEnable = new SoundEntry("ENTITY_BAT_TAKEOFF", 1.0f, 1.0f);
    private SoundEntry flyDisable = new SoundEntry("ENTITY_BAT_DEATH", 1.0f, 1.0f);

    private SoundEntry purchaseSuccess = new SoundEntry("ENTITY_PLAYER_LEVELUP", 1.0f, 1.5f);
    private SoundEntry purchaseFail = new SoundEntry("ENTITY_VILLAGER_NO", 1.0f, 1.0f);
    private SoundEntry walletRecharge = new SoundEntry("UI_TOAST_CHALLENGE_COMPLETE", 1.0f, 1.0f);
    private SoundEntry walletRechargeGlobal = new SoundEntry("ENTITY_FIREWORK_ROCKET_LAUNCH", 1.0f, 1.0f);

    private SoundEntry clearlagTick = new SoundEntry("BLOCK_NOTE_BLOCK_HAT", 0.5f, 1.0f);
    private SoundEntry clearlagClear = new SoundEntry("ENTITY_GENERIC_EXPLODE", 0.3f, 1.5f);

    private SoundEntry afkReward = new SoundEntry("ENTITY_EXPERIENCE_ORB_PICKUP", 1.0f, 1.2f);

    private SoundEntry clanInvite = new SoundEntry("BLOCK_NOTE_BLOCK_BELL", 1.0f, 1.0f);
    private SoundEntry clanAnnouncement = new SoundEntry("BLOCK_NOTE_BLOCK_IRON_XYLOPHONE", 1.0f, 1.0f);
    private SoundEntry clanJoin = new SoundEntry("ENTITY_PLAYER_LEVELUP", 1.0f, 1.0f);
    private SoundEntry clanLeave = new SoundEntry("ENTITY_VILLAGER_HURT", 1.0f, 1.0f);

    private SoundEntry vanishEnable = new SoundEntry("ENTITY_ILLUSIONER_MIRROR_MOVE", 1.0f, 1.0f);
    private SoundEntry vanishDisable = new SoundEntry("ENTITY_ILLUSIONER_PREPARE_BLINDNESS", 1.0f, 1.0f);

    private SoundEntry guiOpen = new SoundEntry("BLOCK_CHEST_OPEN", 0.5f, 1.0f);
    private SoundEntry guiClick = new SoundEntry("UI_BUTTON_CLICK", 0.5f, 1.0f);
    private SoundEntry guiClose = new SoundEntry("BLOCK_CHEST_CLOSE", 0.5f, 1.0f);

    private SoundEntry success = new SoundEntry("ENTITY_EXPERIENCE_ORB_PICKUP", 1.0f, 1.0f);
    private SoundEntry error = new SoundEntry("ENTITY_VILLAGER_NO", 1.0f, 1.0f);

    private SoundEntry pickupReceive = new SoundEntry("ENTITY_ITEM_PICKUP", 1.0f, 1.0f);
    private SoundEntry pickupAvailable = new SoundEntry("BLOCK_NOTE_BLOCK_CHIME", 1.0f, 1.5f);

    public SoundEntry getTeleportStart() { return teleportStart; }
    public SoundEntry getTeleportEnd() { return teleportEnd; }
    public SoundEntry getTeleportCancelled() { return teleportCancelled; }
    public SoundEntry getGamemodeChange() { return gamemodeChange; }
    public SoundEntry getFlyEnable() { return flyEnable; }
    public SoundEntry getFlyDisable() { return flyDisable; }
    public SoundEntry getPurchaseSuccess() { return purchaseSuccess; }
    public SoundEntry getPurchaseFail() { return purchaseFail; }
    public SoundEntry getWalletRecharge() { return walletRecharge; }
    public SoundEntry getWalletRechargeGlobal() { return walletRechargeGlobal; }
    public SoundEntry getClearlagTick() { return clearlagTick; }
    public SoundEntry getClearlagClear() { return clearlagClear; }
    public SoundEntry getAfkReward() { return afkReward; }
    public SoundEntry getClanInvite() { return clanInvite; }
    public SoundEntry getClanAnnouncement() { return clanAnnouncement; }
    public SoundEntry getClanJoin() { return clanJoin; }
    public SoundEntry getClanLeave() { return clanLeave; }
    public SoundEntry getVanishEnable() { return vanishEnable; }
    public SoundEntry getVanishDisable() { return vanishDisable; }
    public SoundEntry getGuiOpen() { return guiOpen; }
    public SoundEntry getGuiClick() { return guiClick; }
    public SoundEntry getGuiClose() { return guiClose; }
    public SoundEntry getSuccess() { return success; }
    public SoundEntry getError() { return error; }
    public SoundEntry getPickupReceive() { return pickupReceive; }
    public SoundEntry getPickupAvailable() { return pickupAvailable; }

    public static class SoundEntry extends OkaeriConfig {
        private String sound;
        private float volume;
        private float pitch;

        public SoundEntry() {
            this.sound = "BLOCK_NOTE_BLOCK_PLING";
            this.volume = 1.0f;
            this.pitch = 1.0f;
        }

        public SoundEntry(String sound, float volume, float pitch) {
            this.sound = sound;
            this.volume = volume;
            this.pitch = pitch;
        }

        public String getSound() { return sound; }
        public float getVolume() { return volume; }
        public float getPitch() { return pitch; }
    }
}
