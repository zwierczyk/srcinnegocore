package dev.grzybalson.config.configs;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.NameModifier;
import eu.okaeri.configs.annotation.NameStrategy;
import eu.okaeri.configs.annotation.Names;

@Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
public class VanishConfig extends OkaeriConfig {

    private String vanishEnabled = "&#00FF3B✔ &8Ξ &#4EFA76Jesteś teraz niewidzialny!";

    private String vanishDisabled = "&#00FF3B✔ &8Ξ &#4EFA76Jesteś teraz widzialny!";

    private String vanishJoinMessage = "&#00E0FFℹ &8Ξ &f{player} &7dołączył w trybie vanish.";

    private String vanishQuitMessage = "&#00E0FFℹ &8Ξ &f{player} &7opuścił serwer (vanish).";

    private boolean actionbarEnabled = true;

    private String actionbarMessage = "&7&l[&c&lVANISH&7&l] &fJesteś niewidzialny!";

    private int actionbarInterval = 20;

    private String vanishPlaceholder = "&7[V]";

    public String getVanishEnabled() { return vanishEnabled; }
    public String getVanishDisabled() { return vanishDisabled; }
    public String getVanishJoinMessage() { return vanishJoinMessage; }
    public String getVanishQuitMessage() { return vanishQuitMessage; }
    public boolean isActionbarEnabled() { return actionbarEnabled; }
    public String getActionbarMessage() { return actionbarMessage; }
    public int getActionbarInterval() { return actionbarInterval; }
    public String getVanishPlaceholder() { return vanishPlaceholder; }
}
