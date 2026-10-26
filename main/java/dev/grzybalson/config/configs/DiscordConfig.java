package dev.grzybalson.config.configs;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.NameModifier;
import eu.okaeri.configs.annotation.NameStrategy;
import eu.okaeri.configs.annotation.Names;

import java.util.List;

@Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
public class DiscordConfig extends OkaeriConfig {

    private String discordLink = "https://dc.szansamc.pl";

    private List<String> messageLines = List.of(
        "",
        "  &b&lDISCORD &8» &fDołącz do naszego Discorda!",
        "",
        "  &7Link: &a&n{link}",
        "",
        "  &8↳ &eTutaj odbierzesz nagrodę!",
        ""
    );

    private String reloadMessage = "&#00FF3B✔ &8Ξ &#4EFA76Przeładowano konfigurację komendy Discord!";

    public String getDiscordLink() {
        return discordLink;
    }

    public List<String> getMessageLines() {
        return messageLines;
    }

    public String getReloadMessage() {
        return reloadMessage;
    }
}
