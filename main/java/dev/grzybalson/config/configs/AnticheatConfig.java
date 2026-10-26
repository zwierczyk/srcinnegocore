package dev.grzybalson.config.configs;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.NameModifier;
import eu.okaeri.configs.annotation.NameStrategy;
import eu.okaeri.configs.annotation.Names;

@Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
public class AnticheatConfig extends OkaeriConfig {

    private String notifyPermission = "Holly.anticheat.notify";

    private String prefix = "&8[&4AntyCheat&8]";

    private boolean eatWhileHit = true;
    private boolean inventoryMove = true;
    private boolean elytraSwap = true;
    private boolean cobweb = true;
    private boolean aura = true;
    private boolean aimbot = true;
    private boolean elytraFirework = true;
    private boolean reach = true;
    private boolean cobwebPhase = true;
    private boolean autoTrap = true;
    private boolean cobwebThroughWall = true;
    private boolean hitThroughBlock = true;

    private double maxReach = 4.80;

    private double maxCobwebSpeed = 0.20;

    private int autoTrapMaxBlocks = 5;
    private long autoTrapTimeWindowMs = 320;

    private String eatWhileHitAlert = "{prefix} &7Gracz &f{player} &7prawdopodobnie bije i je &8(&cx{vl}&8)";
    private String cobwebAlert = "{prefix} &7Gracz &f{player} &7stawia cobweby bez trzymania &8(&cx{vl}&8)";
    private String auraAlert = "{prefix} &7Gracz &f{player} &cAURA&8, &7wchodzi w przeciwnika &8(&cx{vl}&8)";
    private String aimbotAlert = "{prefix} &7Gracz &f{player} &7prawdopodobnie uzywa aimbota &8(&cx{vl}&8)";
    private String reachAlert = "{prefix} &7Gracz &f{player} &7prawdopodobnie uzywa komara/reach &8(&e{reach} &7> &c{max}&8) &8(&cx{vl}&8)";
    private String cobwebOffhand = "&cPajeczyne mozesz postawic tylko w glownej rece!";
    private String fireworkOffhand = "&cNie mozesz uzywac fajerwerkow w drugiej dloni!";
    private String cobwebPhaseAlert = "{prefix} &7Gracz &f{player} &7przechodzi przez cobweby (speed: &e{speed}&7) &8(&cx{vl}&8)";
    private String autoTrapAlert = "{prefix} &7Gracz &f{player} &7uzywa auto-trap/fastplace &8(&e{count} blokow w {time}ms{target}&8) &8(&cx{vl}&8)";
    private String cobwebThroughWallAlert = "{prefix} &7Gracz &f{player} &7stawia cobweby przez sciane &8(&cx{vl}&8)";
    private String hitThroughBlockAlert = "{prefix} &7Gracz &f{player} &7bije przez {kind} (&f{victim}&7) &8→ &ccancel &8(&cx{vl}&8)";

    private String elytraBanCommand = "";

    public String getNotifyPermission() { return notifyPermission; }
    public String getPrefix() { return prefix; }
    public boolean isEatWhileHit() { return eatWhileHit; }
    public boolean isInventoryMove() { return inventoryMove; }
    public boolean isElytraSwap() { return elytraSwap; }
    public boolean isCobweb() { return cobweb; }
    public boolean isAura() { return aura; }
    public boolean isAimbot() { return aimbot; }
    public boolean isElytraFirework() { return elytraFirework; }
    public boolean isReach() { return reach; }
    public double getMaxReach() { return maxReach; }
    public String getEatWhileHitAlert() { return eatWhileHitAlert; }
    public String getCobwebAlert() { return cobwebAlert; }
    public String getAuraAlert() { return auraAlert; }
    public String getAimbotAlert() { return aimbotAlert; }
    public String getReachAlert() { return reachAlert; }
    public String getCobwebOffhand() { return cobwebOffhand; }
    public String getFireworkOffhand() { return fireworkOffhand; }
    public String getElytraBanCommand() { return elytraBanCommand; }

    public boolean isCobwebPhase() { return cobwebPhase; }
    public boolean isAutoTrap() { return autoTrap; }
    public boolean isCobwebThroughWall() { return cobwebThroughWall; }
    public boolean isHitThroughBlock() { return hitThroughBlock; }
    public double getMaxCobwebSpeed() { return maxCobwebSpeed; }
    public int getAutoTrapMaxBlocks() { return autoTrapMaxBlocks; }
    public long getAutoTrapTimeWindowMs() { return autoTrapTimeWindowMs; }
    public String getCobwebPhaseAlert() { return cobwebPhaseAlert; }
    public String getAutoTrapAlert() { return autoTrapAlert; }
    public String getCobwebThroughWallAlert() { return cobwebThroughWallAlert; }
    public String getHitThroughBlockAlert() { return hitThroughBlockAlert; }
}
