package dev.grzybalson.modules.market.filter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MarketFilters {

    private Map<String, MarketFilterDefinition> filters;
    private final Map<String, List<String>> indexes = new LinkedHashMap<>();

    public MarketFilters() {
        this(MarketFilterDefinition.createDefaults());
    }

    public MarketFilters(Map<String, MarketFilterDefinition> filters) {
        this.filters = filters == null ? MarketFilterDefinition.createDefaults() : filters;
        rebuildIndexes();
    }

    public void rebuildIndexes() {
        indexes.clear();
        for (Map.Entry<String, MarketFilterDefinition> entry : filters.entrySet()) {
            MarketFilterDefinition definition = entry.getValue();
            if (definition.getCategories() == null || definition.getCategories().isEmpty()) {
                continue;
            }
            indexes.put(entry.getKey(), new ArrayList<>(definition.getCategories().keySet()));
        }
    }

    public void reload(Map<String, MarketFilterDefinition> newFilters) {
        this.filters = newFilters == null ? MarketFilterDefinition.createDefaults() : newFilters;
        rebuildIndexes();
    }

    public Map<String, MarketFilterDefinition> getFilters() {
        return filters;
    }

    public List<String> getIndexSafely(String type) {
        List<String> list = indexes.get(type);
        return list == null ? Collections.emptyList() : list;
    }

    public int indexOf(String type, String key) {
        List<String> list = indexes.get(type);
        return list == null ? 0 : list.indexOf(key);
    }
}
