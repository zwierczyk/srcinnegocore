package dev.grzybalson.config.configs;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.NameModifier;
import eu.okaeri.configs.annotation.NameStrategy;
import eu.okaeri.configs.annotation.Names;

@Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
public class LogiConfig extends OkaeriConfig {

    private boolean enabled = true;

    /**
     * Globalny webhook. Jeśli wpiszesz go tutaj, wszystkie typy logów będą używać tego URL-a,
     * o ile konkretny typ nie ma własnego webhook-url.
     */
    private String webhookUrl = "";

    private Webhook marketListed = new Webhook();

    private Webhook gamemodeChanged = new Webhook();

    private Webhook backupRequested = new Webhook();

    private Webhook backupApproved = new Webhook();

    private Webhook backupClaimed = new Webhook();

    private Webhook payTransfer = new Webhook();

    public boolean isEnabled() {
        return enabled;
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }

    public Webhook getMarketListed() {
        return marketListed;
    }

    public Webhook getGamemodeChanged() {
        return gamemodeChanged;
    }

    public Webhook getBackupRequested() {
        return backupRequested;
    }

    public Webhook getBackupApproved() {
        return backupApproved;
    }

    public Webhook getBackupClaimed() {
        return backupClaimed;
    }

    public Webhook getPayTransfer() {
        return payTransfer;
    }

    @Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
    public static class Webhook extends OkaeriConfig {

        private boolean enabled = true;

        private String webhookUrl = "";

        public boolean isEnabled() {
            return enabled;
        }

        public String getWebhookUrl() {
            return webhookUrl;
        }
    }
}
