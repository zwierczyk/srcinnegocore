package dev.grzybalson.config.configs;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.NameModifier;
import eu.okaeri.configs.annotation.NameStrategy;
import eu.okaeri.configs.annotation.Names;

import java.util.Arrays;
import java.util.List;

@Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
public class IpLimitConfig extends OkaeriConfig {

    private boolean enabled = true;
    private int maxPlayersPerIp = 4;
    private String bypassPermission = "hollycore.iplimit.bypass";
    private String kickMessage = "&#FF0000✘ &8Ξ &cOsiągnięto limit graczy z Twojego adresu IP (&4{limit}&c)!";
    private List<String> exemptIps = Arrays.asList("127.0.0.1");

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxPlayersPerIp() {
        return maxPlayersPerIp;
    }

    public void setMaxPlayersPerIp(int maxPlayersPerIp) {
        this.maxPlayersPerIp = maxPlayersPerIp;
    }

    public String getBypassPermission() {
        return bypassPermission;
    }

    public void setBypassPermission(String bypassPermission) {
        this.bypassPermission = bypassPermission;
    }

    public String getKickMessage() {
        return kickMessage;
    }

    public void setKickMessage(String kickMessage) {
        this.kickMessage = kickMessage;
    }

    public List<String> getExemptIps() {
        return exemptIps;
    }

    public void setExemptIps(List<String> exemptIps) {
        this.exemptIps = exemptIps;
    }
}
