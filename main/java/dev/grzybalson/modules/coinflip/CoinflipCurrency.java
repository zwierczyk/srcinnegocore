package dev.grzybalson.modules.coinflip;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;

public enum CoinflipCurrency {

    MONEY("kasa", "pieniadze", "money"),
    SHARDS("odlamki", "odlamki", "shards");

    private final String argumentName;
    private final String displayName;
    private final String databaseField;

    CoinflipCurrency(String argumentName, String displayName, String databaseField) {
        this.argumentName = argumentName;
        this.displayName = displayName;
        this.databaseField = databaseField;
    }

    public String getArgumentName() {
        return argumentName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDatabaseField() {
        return databaseField;
    }

    public boolean isWholeNumber() {
        return this == SHARDS;
    }

    public static Optional<CoinflipCurrency> parse(String input) {
        if (input == null) {
            return Optional.empty();
        }

        String normalized = Normalizer.normalize(input.trim(), Normalizer.Form.NFD)
                .replace("\u0142", "l")
                .replace("\u0141", "l")
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);

        return switch (normalized) {
            case "kasa", "money", "pieniadze", "cash", "eco" -> Optional.of(MONEY);
            case "odlamki", "odlamek", "shards", "shard", "fragmenty" -> Optional.of(SHARDS);
            default -> Optional.empty();
        };
    }
}
