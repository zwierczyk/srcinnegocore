package dev.grzybalson.modules.market.filter;

import org.bukkit.Material;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MarketFilterDefinition {

    private final Map<String, List<MarketFilterItem>> categories;
    private final Material icon;
    private final String name;

    public MarketFilterDefinition(Map<String, List<MarketFilterItem>> categories, Material icon, String name) {
        this.categories = categories == null ? Map.of() : categories;
        this.icon = icon;
        this.name = name;
    }

    public Map<String, List<MarketFilterItem>> getCategories() {
        return categories;
    }

    public Material getIcon() {
        return icon;
    }

    public String getName() {
        return name;
    }

    public static Map<String, MarketFilterDefinition> createDefaults() {
        Map<String, MarketFilterDefinition> filters = new LinkedHashMap<>();
        filters.put("all", new MarketFilterDefinition(null, Material.NETHER_STAR, "&8× &#00C356&lWszystko"));
        filters.put("armor", new MarketFilterDefinition(Map.of(
            "Hełmy", List.of(new MarketFilterItem(Material.NETHERITE_HELMET, -1)),
            "Napierśniki", List.of(new MarketFilterItem(Material.NETHERITE_CHESTPLATE, -1)),
            "Spodnie", List.of(new MarketFilterItem(Material.NETHERITE_LEGGINGS, -1)),
            "Buty", List.of(new MarketFilterItem(Material.NETHERITE_BOOTS, -1)),
            "Elytry", List.of(new MarketFilterItem(Material.ELYTRA, -1))
        ), Material.NETHERITE_CHESTPLATE, "&8× &#AC2222&lUzbrojenie"));
        filters.put("weapons", new MarketFilterDefinition(Map.of(
            "Miecze", List.of(new MarketFilterItem(Material.NETHERITE_SWORD, -1)),
            "Kilofy", List.of(new MarketFilterItem(Material.NETHERITE_PICKAXE, -1)),
            "Nożyce", List.of(new MarketFilterItem(Material.SHEARS, -1))
        ), Material.NETHERITE_SWORD, "&8× &#FF8CF9&lNarzędzia"));
        filters.put("special", new MarketFilterDefinition(Map.of(
            "Eventówki", List.of(
                new MarketFilterItem(Material.PURPLE_DYE, 1),
                new MarketFilterItem(Material.YELLOW_DYE, 1),
                new MarketFilterItem(Material.BLUE_DYE, 1)
            )
        ), Material.ENDER_EYE, "&8× &#00C2FF&lSpecjalne"));
        filters.put("premium", new MarketFilterDefinition(Map.of(
            "Odłamki", List.of(new MarketFilterItem(Material.PAPER, 1)),
            "Fragmenty Klanu", List.of(new MarketFilterItem(Material.PAPER, 2)),
            "OgBoxy", List.of(new MarketFilterItem(Material.PURPLE_GLAZED_TERRACOTTA, -1)),
            "Klucze", List.of(new MarketFilterItem(Material.TRIPWIRE_HOOK, -1)),
            "Vouchery", List.of(new MarketFilterItem(Material.PAPER, -1)),
            "Sigile", List.of(
                new MarketFilterItem(Material.RED_DYE, -1),
                new MarketFilterItem(Material.LIGHT_BLUE_DYE, -1),
                new MarketFilterItem(Material.PURPLE_DYE, -1),
                new MarketFilterItem(Material.ORANGE_DYE, -1),
                new MarketFilterItem(Material.ENCHANTED_BOOK, -1)
            )
        ), Material.GOLDEN_CARROT, "&8× &#F6FF93&lPremium"));
        return filters;
    }
}
