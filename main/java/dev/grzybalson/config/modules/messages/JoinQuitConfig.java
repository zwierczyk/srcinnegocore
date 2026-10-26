package dev.grzybalson.config.modules.messages;

import eu.okaeri.configs.OkaeriConfig;
public class JoinQuitConfig extends OkaeriConfig {

    private boolean enabled = true;

    private boolean disableAllMessages = false;

    private boolean hideDefault = true;

    /** Własne komunikaty join/quit w czacie (domyślnie wyłączone — bez spamu botów). */
    private boolean broadcastJoinQuit = false;

    private String joinMessage = "";
    private String quitMessage = "";
    private String firstJoinMessage = "&8[&6★&8] &eGracz &f{player} &edołączył po raz pierwszy!";

    private boolean firstJoinEnabled = true;
    private String joinTitle = "";

    private String joinSubtitle = "";

    private int titleFadeIn = 10;

    private int titleStay = 40;

    private int titleFadeOut = 20;

    public boolean isEnabled() { return enabled; }
    public boolean isDisableAllMessages() { return disableAllMessages; }
    public boolean isHideDefault() { return hideDefault; }
    public boolean isBroadcastJoinQuit() { return broadcastJoinQuit; }
    public String getJoinMessage() { return joinMessage; }
    public String getQuitMessage() { return quitMessage; }
    public String getFirstJoinMessage() { return firstJoinMessage; }
    public boolean isFirstJoinEnabled() { return firstJoinEnabled; }
    public String getJoinTitle() { return joinTitle; }
    public String getJoinSubtitle() { return joinSubtitle; }
    public int getTitleFadeIn() { return titleFadeIn; }
    public int getTitleStay() { return titleStay; }
    public int getTitleFadeOut() { return titleFadeOut; }
}
