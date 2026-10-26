package dev.grzybalson.config.configs;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.NameModifier;
import eu.okaeri.configs.annotation.NameStrategy;
import eu.okaeri.configs.annotation.Names;

@Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
public class CreatorModeConfig extends OkaeriConfig {

    private String enabledMessage = "&#00FF3B✔ &8Ξ &#4EFA76Włączyłeś tryb twórcy!";

    private String disabledMessage = "&#00FF3B✔ &8Ξ &#4EFA76Wyłączyłeś tryb twórcy!";

    private String noPermissionMessage = "&#FF0000✘ &8Ξ &cNie posiadasz dostępu do &#FF0000/tryb-tworca &8(&4hollycore.creatormode&8)";

    private boolean actionbarEnabled = true;

    private String actionbarMessage = "&7&l[&e&lTRYB TWÓRCY&7&l] &fPrzedmioty nie wypadają!";

    private int actionbarInterval = 20;

    private String creatorPlaceholder = "&e[T]";

    private String grantedStaffMessage = "&#00FF3B✔ &8Ξ &#4EFA76Nadano tryb twórcy graczowi &f{player} &ana &e{time}&a.";

    private String grantedPlayerMessage = "&#00FF3B✔ &8Ξ &#4EFA76Otrzymałeś tryb twórcy na &e{time}&a.";

    private String expiredMessage = "&#FF0000✘ &8Ξ &cTwój tryb twórcy wygasł.";

    private String revokedStaffMessage = "&#00FF3B✔ &8Ξ &#4EFA76Zabrano tryb twórcy graczowi &f{player}&a.";

    private String revokedPlayerMessage = "&#FF0000✘ &8Ξ &cTwój tryb twórcy został zabrany.";

    private String grantLockedMessage = "&#FF0000✘ &8Ξ &cMasz nadany tryb twórcy jeszcze przez &e{time}&c.";

    public String getEnabledMessage() { return enabledMessage; }
    public String getDisabledMessage() { return disabledMessage; }
    public String getNoPermissionMessage() { return noPermissionMessage; }
    public boolean isActionbarEnabled() { return actionbarEnabled; }
    public String getActionbarMessage() { return actionbarMessage; }
    public int getActionbarInterval() { return actionbarInterval; }
    public String getCreatorPlaceholder() { return creatorPlaceholder; }
    public String getGrantedStaffMessage() { return grantedStaffMessage; }
    public String getGrantedPlayerMessage() { return grantedPlayerMessage; }
    public String getExpiredMessage() { return expiredMessage; }
    public String getRevokedStaffMessage() { return revokedStaffMessage; }
    public String getRevokedPlayerMessage() { return revokedPlayerMessage; }
    public String getGrantLockedMessage() { return grantLockedMessage; }
}
