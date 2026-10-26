package dev.grzybalson.config.configs;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.NameModifier;
import eu.okaeri.configs.annotation.NameStrategy;
import eu.okaeri.configs.annotation.Names;

@Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
public class IgnoreConfig extends OkaeriConfig {

    private String playerIgnored = "&#FF0000✘ &8Ξ &cTen gracz Cię zignorował!";

    private String youIgnored = "&#00FF3B✔ &8Ξ &#4EFA76Zignorowałeś gracza &e{player}&a!";

    private String youUnignored = "&#00FF3B✔ &8Ξ &#4EFA76Odblokowano gracza &e{player}&a!";

    private String ignoredAll = "&#00FF3B✔ &8Ξ &#4EFA76Zignorowałeś wszystkich graczy!";

    private String unignoredAll = "&#00FF3B✔ &8Ξ &#4EFA76Odblokowano wszystkich graczy!";

    public String getPlayerIgnored() { return playerIgnored; }
    public String getYouIgnored() { return youIgnored; }
    public String getYouUnignored() { return youUnignored; }
    public String getIgnoredAll() { return ignoredAll; }
    public String getUnignoredAll() { return unignoredAll; }
}
