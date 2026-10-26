package dev.grzybalson.modules.market;

public final class MarketViewState {

    private int page = 1;
    private MarketSortType sortType = MarketSortType.NEWEST;
    private String filterType = "all";
    private String categoryFilter;
    private boolean onlyAffordable;

    public MarketViewState copy() {
        MarketViewState copy = new MarketViewState();
        copy.page = page;
        copy.sortType = sortType;
        copy.filterType = filterType;
        copy.categoryFilter = categoryFilter;
        copy.onlyAffordable = onlyAffordable;
        return copy;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = Math.max(1, page);
    }

    public MarketSortType getSortType() {
        return sortType;
    }

    public void setSortType(MarketSortType sortType) {
        this.sortType = sortType == null ? MarketSortType.NEWEST : sortType;
    }

    public String getFilterType() {
        return filterType == null ? "all" : filterType;
    }

    public void setFilterType(String filterType) {
        this.filterType = filterType == null ? "all" : filterType;
    }

    public String getCategoryFilter() {
        return categoryFilter;
    }

    public void setCategoryFilter(String categoryFilter) {
        this.categoryFilter = categoryFilter;
    }

    public boolean isOnlyAffordable() {
        return onlyAffordable;
    }

    public void setOnlyAffordable(boolean onlyAffordable) {
        this.onlyAffordable = onlyAffordable;
    }
}
