package dev.grzybalson.modules.coinflip;

public record CoinflipRewards(double money, double shards) {

    public double amount(CoinflipCurrency currency) {
        return switch (currency) {
            case MONEY -> money;
            case SHARDS -> shards;
        };
    }

    public boolean empty() {
        return money <= 0.0 && shards <= 0.0;
    }
}
