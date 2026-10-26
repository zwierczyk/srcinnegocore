package dev.grzybalson.modules.coinflip;

public enum CoinflipFilter {

    ALL,
    SHARDS,
    MONEY;

    public CoinflipFilter next() {
        return switch (this) {
            case ALL -> SHARDS;
            case SHARDS -> MONEY;
            case MONEY -> ALL;
        };
    }

    public boolean accepts(CoinflipCurrency currency) {
        return this == ALL
                || this == SHARDS && currency == CoinflipCurrency.SHARDS
                || this == MONEY && currency == CoinflipCurrency.MONEY;
    }
}
