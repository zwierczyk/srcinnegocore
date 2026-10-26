package dev.grzybalson.config.configs;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.NameModifier;
import eu.okaeri.configs.annotation.NameStrategy;
import eu.okaeri.configs.annotation.Names;

@Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
public class EconomyConfig extends OkaeriConfig {

    private double startingBalance = 100.0;
    private String currencyNameSingular = "dolar";
    private String currencyNamePlural = "dolarów";

    private String currencySymbol = "$";

    private String moneyFormat = "#,##0.00";

    public double getStartingBalance() { return startingBalance; }
    public String getCurrencyNameSingular() { return currencyNameSingular; }
    public String getCurrencyNamePlural() { return currencyNamePlural; }
    public String getCurrencySymbol() { return currencySymbol; }
    public String getMoneyFormat() { return moneyFormat; }
}
