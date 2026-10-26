package dev.grzybalson.config.configs;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.NameModifier;
import eu.okaeri.configs.annotation.NameStrategy;
import eu.okaeri.configs.annotation.Names;

import java.util.List;

@Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
public class PlayerCommandConfig extends OkaeriConfig {

    private String playerNotFound = "&#FF0000✘ &8Ξ &cNie znaleziono gracza &4{player}&c.";

    private String reloadMessage = "&#00FF3B✔ &8Ξ &#4EFA76Przeładowano konfigurację komendy /gracz!";

    private String clanFormat = " &#12BF00{clan}";

    private String noClan = "";

    private List<String> message = List.of(
        "                             < 🔥 >                          ",
        "  &8» &7Nazwa: &#18FF00{name}{clan}",
        "",
        " &8» &7Smierci: &#18FF00{deaths}",
        " &8» &7Zabójstwa: &#18FF00{kills}",
        " &8» &7Punkty: &#18FF00{points}",
        " &8» &7Spędzony Czas: &#18FF00{playtime}",
        "",
        " &8» &7Kill Streak: &#18FF00{killstreak}",
        " &8» &7High Streak: &#18FF00{highstreak}",
        "",
        "                           < 🔥 >"
    );

    public String getPlayerNotFound() {
        return playerNotFound;
    }

    public String getReloadMessage() {
        return reloadMessage;
    }

    public String getClanFormat() {
        return clanFormat;
    }

    public String getNoClan() {
        return noClan;
    }

    public List<String> getMessage() {
        return message;
    }
}
