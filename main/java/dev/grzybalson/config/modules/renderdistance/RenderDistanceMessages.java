package dev.grzybalson.config.modules.renderdistance;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.NameModifier;
import eu.okaeri.configs.annotation.NameStrategy;
import eu.okaeri.configs.annotation.Names;

@Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
public class RenderDistanceMessages extends OkaeriConfig {
    private String reload = "&#00FF3B✔ &8Ξ &#4EFA76Pomyślnie przeładowano konfigurację widoczności graczy!";
    private String noPermission = "&#FF0000✘ &8Ξ &cNie posiadasz dostępu do &#FF0000/renderdistance &8(&4Holly.cmd.renderdistance.reload&8)";
    private String playerOnly = "&#FF0000✘ &8Ξ &cTej komendy może użyć tylko gracz.";
    private String disabled = "&#FF0000✘ &8Ξ &cModuł widoczności graczy jest wyłączony.";
    private String unavailable = "&#FF0000✘ &8Ξ &cModuł widoczności graczy jest niedostępny, ponieważ WorldGuard nie jest włączony.";
    private String invalidPlayer = "&#FF0000✘ &8Ξ &cNie znaleziono tego gracza.";

    public String getReload() {
        return reload;
    }

    public String getNoPermission() {
        return noPermission;
    }

    public String getPlayerOnly() {
        return playerOnly;
    }

    public String getDisabled() {
        return disabled;
    }

    public String getUnavailable() {
        return unavailable;
    }

    public String getInvalidPlayer() {
        return invalidPlayer;
    }
}
