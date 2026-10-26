package dev.grzybalson.config.configs;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.NameModifier;
import eu.okaeri.configs.annotation.NameStrategy;
import eu.okaeri.configs.annotation.Names;

import java.util.Collections;
import java.util.List;

@Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
public class AdminConfig extends OkaeriConfig {

    private String permission = "Holly.cmd.rozdaj";
    private int countdownSeconds = 5;

    private String rozdajCountdownTitle = "&aGracz &e{player}";
    private String rozdajCountdownSubtitle = "&frozdaje eventowy klucz dla kazdego";
    private String rozdajFinishedTitle = "&a&lodbierz";
    private String rozdajFinishedSubtitle = "&fdostałeś eventowy kluczyk do &a/odbierz";
    private String rozdajFinalMessage = "&8[&2&8] &aGracz &e{player} &arozdał nagrody dla każdego!";

    private String rozdajCountdownSound = "BLOCK_NOTE_BLOCK_PLING";
    private String rozdajFinishedSound = "UI_TOAST_CHALLENGE_COMPLETE";

    private List<String> commands = Collections.singletonList("odbierz giveall event_key 1");

    public String getPermission() { return permission; }
    public int getCountdownSeconds() { return countdownSeconds; }
    public String getRozdajCountdownTitle() { return rozdajCountdownTitle; }
    public String getRozdajCountdownSubtitle() { return rozdajCountdownSubtitle; }
    public String getRozdajFinishedTitle() { return rozdajFinishedTitle; }
    public String getRozdajFinishedSubtitle() { return rozdajFinishedSubtitle; }
    public String getRozdajFinalMessage() { return rozdajFinalMessage; }
    public String getRozdajCountdownSound() { return rozdajCountdownSound; }
    public String getRozdajFinishedSound() { return rozdajFinishedSound; }
    public List<String> getCommands() { return commands; }
}

