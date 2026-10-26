package dev.grzybalson.config.anarchiagg;

import eu.okaeri.configs.OkaeriConfig;

public class AnarchiaGGModulesConfig extends OkaeriConfig {
    private boolean eventowki = true;
    private boolean szafka = true;
    private boolean kowal = true;
    private boolean ksiegi = true;
    private boolean zmianki = true;

    private boolean petVisibility = true;

    public boolean isEventowki() { return eventowki; }
    public boolean isSzafka() { return szafka; }
    public boolean isKowal() { return kowal; }
    public boolean isKsiegi() { return ksiegi; }
    public boolean isZmianki() { return zmianki; }
    public boolean isPetVisibility() { return petVisibility; }
    public void setPetVisibility(boolean petVisibility) { this.petVisibility = petVisibility; }
}
