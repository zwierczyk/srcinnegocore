package dev.grzybalson.config.configs;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.NameModifier;
import eu.okaeri.configs.annotation.NameStrategy;
import eu.okaeri.configs.annotation.Names;

import java.util.ArrayList;
import java.util.List;

@Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
public class CheckConfig extends OkaeriConfig {

    private boolean checkLocationSet = false;
    private String checkWorld = "world";
    private double checkX = 0;
    private double checkY = 100;
    private double checkZ = 0;
    private float checkYaw = 0;
    private float checkPitch = 0;

    private List<String> allowedCommands = new ArrayList<>(List.of(
        "helpop",
        "msg",
        "r",
        "reply"
    ));

    private String cleanBanCommand = "";
    private int cleanBanDuration = 0;

    private String noCooperationBanCommand = "ban {player} 7d Brak współpracy podczas sprawdzania";
    private int noCooperationBanDuration = 7;

    private String cheaterBanCommand = "ban {player} 30d Cheaty";
    private int cheaterBanDuration = 30;

    private String admittedBanCommand = "ban {player} 14d Przyznanie się do cheatów";
    private int admittedBanDuration = 14;

    private String logoutBanCommand = "ban {player} 7d Wylogowanie podczas sprawdzania";
    private int logoutBanDuration = 7;

    private String messageBeingChecked = "&#FFB800⚠ &8Ξ &eJesteś sprawdzany! Nie możesz używać tej komendy.";
    private String messageCheckStarted = "&#FFB800⚠ &8Ξ &eZostałeś wezwany na sprawdzenie przez &e{admin}&c!";
    private String messageCheckEnded = "&#00FF3B✔ &8Ξ &#4EFA76Sprawdzenie zakończone. Jesteś czysty!";
    private String messageAdminCheckStarted = "&#00FF3B✔ &8Ξ &#4EFA76Rozpoczęto sprawdzanie gracza &e{player}&a!";
    private String messageAdminCheckEnded = "&#00FF3B✔ &8Ξ &#4EFA76Zakończono sprawdzanie gracza &e{player}&a!";
    private String messagePlayerOffline = "&#FF0000✘ &8Ξ &cGracz nie jest online!";
    private String messageNotChecking = "&#FF0000✘ &8Ξ &cNie sprawdzasz żadnego gracza!";
    private String messageAlreadyBeingChecked = "&#FF0000✘ &8Ξ &cTen gracz jest już sprawdzany!";
    private String messageCheckLocationSet = "&#00FF3B✔ &8Ξ &#4EFA76Ustawiono lokalizację sprawdzania!";
    private String messageCheckLocationNotSet = "&#FF0000✘ &8Ξ &cLokalizacja sprawdzania nie została ustawiona!";

    private String guiTitle = "&8Sprawdzanie: &c{player}";
    private String guiCleanName = "&a&lCZYSTY";
    private String guiCleanLore = "&7Gracz jest czysty, teleportuj na spawn.";
    private String guiNoCooperationName = "&6&lBRAK WSPÓŁPRACY";
    private String guiNoCooperationLore = "&7Gracz nie współpracuje.";
    private String guiCheaterName = "&c&lCHEATER";
    private String guiCheaterLore = "&7Gracz używa cheatów.";
    private String guiAdmittedName = "&e&lPRZYZNANIE SIĘ";
    private String guiAdmittedLore = "&7Gracz przyznał się do cheatów.";

    public boolean isCheckLocationSet() { return checkLocationSet; }
    public void setCheckLocationSet(boolean set) { this.checkLocationSet = set; }
    public String getCheckWorld() { return checkWorld; }
    public void setCheckWorld(String world) { this.checkWorld = world; }
    public double getCheckX() { return checkX; }
    public void setCheckX(double x) { this.checkX = x; }
    public double getCheckY() { return checkY; }
    public void setCheckY(double y) { this.checkY = y; }
    public double getCheckZ() { return checkZ; }
    public void setCheckZ(double z) { this.checkZ = z; }
    public float getCheckYaw() { return checkYaw; }
    public void setCheckYaw(float yaw) { this.checkYaw = yaw; }
    public float getCheckPitch() { return checkPitch; }
    public void setCheckPitch(float pitch) { this.checkPitch = pitch; }

    public List<String> getAllowedCommands() { return allowedCommands; }

    public String getCleanBanCommand() { return cleanBanCommand; }
    public String getNoCooperationBanCommand() { return noCooperationBanCommand; }
    public String getCheaterBanCommand() { return cheaterBanCommand; }
    public String getAdmittedBanCommand() { return admittedBanCommand; }
    public String getLogoutBanCommand() { return logoutBanCommand; }

    public String getMessageBeingChecked() { return messageBeingChecked; }
    public String getMessageCheckStarted() { return messageCheckStarted; }
    public String getMessageCheckEnded() { return messageCheckEnded; }
    public String getMessageAdminCheckStarted() { return messageAdminCheckStarted; }
    public String getMessageAdminCheckEnded() { return messageAdminCheckEnded; }
    public String getMessagePlayerOffline() { return messagePlayerOffline; }
    public String getMessageNotChecking() { return messageNotChecking; }
    public String getMessageAlreadyBeingChecked() { return messageAlreadyBeingChecked; }
    public String getMessageCheckLocationSet() { return messageCheckLocationSet; }
    public String getMessageCheckLocationNotSet() { return messageCheckLocationNotSet; }

    public String getGuiTitle() { return guiTitle; }
    public String getGuiCleanName() { return guiCleanName; }
    public String getGuiCleanLore() { return guiCleanLore; }
    public String getGuiNoCooperationName() { return guiNoCooperationName; }
    public String getGuiNoCooperationLore() { return guiNoCooperationLore; }
    public String getGuiCheaterName() { return guiCheaterName; }
    public String getGuiCheaterLore() { return guiCheaterLore; }
    public String getGuiAdmittedName() { return guiAdmittedName; }
    public String getGuiAdmittedLore() { return guiAdmittedLore; }
}
