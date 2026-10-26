package dev.grzybalson.modules.market;

import java.util.List;

public enum MarketSortType {
    NEWEST("Od najnowszych"),
    DESCENDING("Od największych cen"),
    ASCENDING("Od najmniejszych cen"),
    ENCHANT("Od poziomu");

    private final String displayName;

    MarketSortType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public MarketSortType next() {
        List<MarketSortType> values = List.of(values());
        int index = values.indexOf(this) + 1;
        return values.get(index >= values.size() ? 0 : index);
    }
}
