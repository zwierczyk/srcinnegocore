package dev.grzybalson.config.modules.clans;

import eu.okaeri.configs.OkaeriConfig;

import java.util.List;

public class EloSettingsConfig extends OkaeriConfig {

    private int kFactor = 48;
    private int startingElo = 1000;
    private int minElo = 0;
    private int maxElo = 15000;
    private int maxGain = 300;
    private int maxLoss = 200;
    private int highEloThreshold = 5000;
    private int highEloKFactor = 72;
    private boolean enabled = true;

    private String broadcast = "§4%victim%&8(&c%victim-points-count%&8) &czostał zabity przez &4%attacker%&8(&c%attacker-points-count%&8)";
    private String attackerTitle = "&2Zabójstwo";
    private String attackerSubtitle = "&4%victim% &#92FF99%attacker-points-count% &8(&#C3E9FF%attacker-points%pkt &8| &2↑%attacker-position-change% &8| &fPozycja: &#C3E9FF%attacker-position%&8)";
    private String victimTitle = "&x&C&6&C&3&C&3&lś&x&A&7&A&6&A&6&lᴍ&x&8&8&8&8&8&8&lɪ&x&A&0&A&0&A&0&lᴇ&x&B&8&B&8&B&8&lʀ&x&D&0&D&0&D&0&lć";
    private String victimSubtitle = "&7%attacker% &8(&c%victim-points-count%&8) &f#%victim-position%";

    private String deathTitle = "&c&lŚMIERĆ";
    private String deathSubtitle = "&7Zginąłeś przez &f{killer} &c(-{lost})";
    private String killTitle = "&b&lZABÓJSTWO";
    private String killSubtitle = "&7Zabiłeś gracza &f{victim} &c(+{gain})";
    private String killChatMessage = "&d⚔ &fGracz &c{victim} &c(-{lost}) &fzostał zabity przez gracza &a{killer} &a(+{gain})";

    private int killCooldownSeconds = 300;
    private int titleFadeIn = 10;
    private int titleStay = 40;
    private int titleFadeOut = 20;

    private boolean killAnimationEnabled = true;
    private String killAnimationTitle = "ᴢᴀʙᴏᴊꜱᴛᴡᴏ";
    private int killAnimationTitleCenterIndex = 4;
    private String killAnimationTitleGray = "#8A8A8A";
    private String killAnimationTitleColorStart = "#FFB347";
    private String killAnimationTitleColorEnd = "#FF2200";
    private String deathAnimationTitle = "ꜱᴍɪᴇʀć";
    private int deathAnimationTitleCenterIndex = 2;
    private String deathAnimationTitleGray = "#666666";
    private String deathAnimationTitleColorStart = "#FF6666";
    private String deathAnimationTitleColorEnd = "#AA0000";
    private int killAnimationSpreadEveryTicks = 2;
    private int killAnimationSubtitleDelayTicks = 6;
    private int killAnimationSubtitlePartEveryTicks = 3;
    private int killAnimationHoldTicks = 55;
    private String killAnimationSoundMain = "ENTITY_PLAYER_ATTACK_CRIT";
    private String killAnimationSoundExtra = "none";
    private double killAnimationSoundVolume = 0.8D;
    private double killAnimationSoundMainPitch = 1.1D;
    private double killAnimationSoundExtraPitch = 1.65D;
    private String deathAnimationSoundMain = "ENTITY_WITHER_HURT";
    private String deathAnimationSoundExtra = "BLOCK_ANVIL_LAND";
    private double deathAnimationSoundVolume = 0.55D;
    private double deathAnimationSoundMainPitch = 0.85D;
    private double deathAnimationSoundExtraPitch = 1.35D;
    private List<String> killSubtitleParts = List.of(
        "&#8B0000{VICTIM} &#FFD700{GAIN}",
        " &#C1C1C1({POINTS}pkt",
        " &#C1C1C1| {POS_CHANGE}",
        " &#C1C1C1| Pozycja: &#FF5C5C{NEW_POS})"
    );
    private List<String> deathSubtitleParts = List.of(
        "&#C1C1C1Zabity przez &#8B0000{KILLER}",
        " &#FF5C5C{LOSS}",
        " &#C1C1C1({POINTS}pkt",
        " &#C1C1C1| {POS_CHANGE}",
        " &#C1C1C1| Pozycja: &#FF5C5C{NEW_POS})"
    );
    private String posChangeUp = "&a↑{AMOUNT}";
    private String posChangeDown = "&c↓{AMOUNT}";
    private String posChangeNone = "&a↑0";

    public int getKFactor() { return kFactor; }
    public int getStartingElo() { return startingElo; }
    public int getMinElo() { return minElo; }
    public int getMaxElo() { return maxElo; }
    public int getMaxGain() { return maxGain; }
    public int getMaxLoss() { return maxLoss; }
    public int getHighEloThreshold() { return highEloThreshold; }
    public int getHighEloKFactor() { return highEloKFactor; }
    public boolean isEnabled() { return enabled; }

    public String getBroadcast() { return broadcast; }
    public String getAttackerTitle() { return attackerTitle; }
    public String getAttackerSubtitle() { return attackerSubtitle; }
    public String getVictimTitle() { return victimTitle; }
    public String getVictimSubtitle() { return victimSubtitle; }

    public String getDeathTitle() { return deathTitle; }
    public String getDeathSubtitle() { return deathSubtitle; }
    public String getKillTitle() { return killTitle; }
    public String getKillSubtitle() { return killSubtitle; }
    public String getKillChatMessage() { return killChatMessage; }

    public int getKillCooldownSeconds() { return killCooldownSeconds; }
    public int getTitleFadeIn() { return titleFadeIn; }
    public int getTitleStay() { return titleStay; }
    public int getTitleFadeOut() { return titleFadeOut; }

    public boolean isKillAnimationEnabled() { return killAnimationEnabled; }
    public String getKillAnimationTitle() { return killAnimationTitle; }
    public int getKillAnimationTitleCenterIndex() { return killAnimationTitleCenterIndex; }
    public String getKillAnimationTitleGray() { return killAnimationTitleGray; }
    public String getKillAnimationTitleColorStart() { return killAnimationTitleColorStart; }
    public String getKillAnimationTitleColorEnd() { return killAnimationTitleColorEnd; }
    public String getDeathAnimationTitle() { return deathAnimationTitle; }
    public int getDeathAnimationTitleCenterIndex() { return deathAnimationTitleCenterIndex; }
    public String getDeathAnimationTitleGray() { return deathAnimationTitleGray; }
    public String getDeathAnimationTitleColorStart() { return deathAnimationTitleColorStart; }
    public String getDeathAnimationTitleColorEnd() { return deathAnimationTitleColorEnd; }
    public int getKillAnimationSpreadEveryTicks() { return killAnimationSpreadEveryTicks; }
    public int getKillAnimationSubtitleDelayTicks() { return killAnimationSubtitleDelayTicks; }
    public int getKillAnimationSubtitlePartEveryTicks() { return killAnimationSubtitlePartEveryTicks; }
    public int getKillAnimationHoldTicks() { return killAnimationHoldTicks; }
    public String getKillAnimationSoundMain() { return killAnimationSoundMain; }
    public String getKillAnimationSoundExtra() { return killAnimationSoundExtra; }
    public float getKillAnimationSoundVolume() { return (float) killAnimationSoundVolume; }
    public float getKillAnimationSoundMainPitch() { return (float) killAnimationSoundMainPitch; }
    public float getKillAnimationSoundExtraPitch() { return (float) killAnimationSoundExtraPitch; }
    public String getDeathAnimationSoundMain() { return deathAnimationSoundMain; }
    public String getDeathAnimationSoundExtra() { return deathAnimationSoundExtra; }
    public float getDeathAnimationSoundVolume() { return (float) deathAnimationSoundVolume; }
    public float getDeathAnimationSoundMainPitch() { return (float) deathAnimationSoundMainPitch; }
    public float getDeathAnimationSoundExtraPitch() { return (float) deathAnimationSoundExtraPitch; }
    public List<String> getKillSubtitleParts() { return killSubtitleParts; }
    public List<String> getDeathSubtitleParts() { return deathSubtitleParts; }
    public String getPosChangeUp() { return posChangeUp; }
    public String getPosChangeDown() { return posChangeDown; }
    public String getPosChangeNone() { return posChangeNone; }
}
