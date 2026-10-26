package dev.grzybalson.modules.market.filter;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;

public final class MarketFilterResolver {

    private MarketFilterResolver() {
    }

    public static boolean matches(ItemStack itemStack, String categoryName, Map<String, MarketFilterDefinition> filters) {
        if (categoryName == null || itemStack == null || itemStack.getType().isAir()) {
            return true;
        }

        // Znajdź kategorię w filtrach
        for (MarketFilterDefinition definition : filters.values()) {
            if (definition.getCategories() == null) {
                continue;
            }
            List<MarketFilterItem> filterItems = definition.getCategories().get(categoryName);
            if (filterItems != null && !filterItems.isEmpty()) {
                return filterItems.stream().anyMatch(filterItem -> matchesFilterItem(itemStack, filterItem));
            }
        }

        return false;
    }

    private static boolean matchesFilterItem(ItemStack itemStack, MarketFilterItem filterItem) {
        if (itemStack.getType() != filterItem.getMaterial()) {
            return false;
        }
        if (filterItem.getCustomModelData() == -1) {
            return true;
        }
        ItemMeta meta = itemStack.getItemMeta();
        return meta != null && meta.hasCustomModelData() && meta.getCustomModelData() == filterItem.getCustomModelData();
    }
}
