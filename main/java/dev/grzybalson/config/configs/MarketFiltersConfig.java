package dev.grzybalson.config.configs;

import dev.grzybalson.modules.market.filter.MarketFilterDefinition;
import dev.grzybalson.modules.market.filter.MarketFilterItem;
import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.CustomKey;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MarketFiltersConfig extends OkaeriConfig {

    @CustomKey("filters")
    private Map<String, FilterEntry> filters = createDefaultFilters();

    public Map<String, MarketFilterDefinition> buildDefinitions() {
        Map<String, MarketFilterDefinition> definitions = new LinkedHashMap<>();

        if (filters == null || filters.isEmpty()) {
            return MarketFilterDefinition.createDefaults();
        }

        for (Map.Entry<String, FilterEntry> entry : filters.entrySet()) {
            FilterEntry filterEntry = entry.getValue();
            if (filterEntry == null) {
                continue;
            }

            Material iconMaterial = parseMaterial(filterEntry.icon.material);
            if (iconMaterial == null) {
                iconMaterial = Material.BARRIER;
            }

            Map<String, List<MarketFilterItem>> categories = new LinkedHashMap<>();
            if (filterEntry.categories != null) {
                for (Map.Entry<String, List<CategoryItem>> catEntry : filterEntry.categories.entrySet()) {
                    List<MarketFilterItem> items = new ArrayList<>();
                    if (catEntry.getValue() != null) {
                        for (CategoryItem item : catEntry.getValue()) {
                            Material mat = parseMaterial(item.material);
                            if (mat != null) {
                                items.add(new MarketFilterItem(mat, item.customModelData));
                            }
                        }
                    }
                    if (!items.isEmpty()) {
                        categories.put(catEntry.getKey(), items);
                    }
                }
            }

            definitions.put(entry.getKey(), new MarketFilterDefinition(
                categories.isEmpty() ? null : categories,
                iconMaterial,
                filterEntry.name
            ));
        }

        return definitions;
    }

    private Material parseMaterial(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Map<String, FilterEntry> createDefaultFilters() {
        Map<String, FilterEntry> filters = new LinkedHashMap<>();

        // ALL filter
        FilterEntry all = new FilterEntry();
        all.icon = new IconEntry("NETHER_STAR", 0);
        all.name = "&8× &#00C356&lWszystko";
        all.lore = List.of("", "&7Wyświetla wszystkie przedmioty", "&7dostępne na rynku.", "", "&fKliknij, aby wybrać");
        all.categories = new LinkedHashMap<>();
        filters.put("all", all);

        // ARMOR filter
        FilterEntry armor = new FilterEntry();
        armor.icon = new IconEntry("NETHERITE_CHESTPLATE", 0);
        armor.name = "&8× &#AC2222&lUzbrojenie";
        armor.lore = List.of("", "&7Filtruj przedmioty według", "&7typu zbroi.", "", "&fKliknij, aby zmienić kategorię");
        armor.categories = new LinkedHashMap<>();
        armor.categories.put("Hełmy", List.of(
            new CategoryItem("NETHERITE_HELMET", -1),
            new CategoryItem("DIAMOND_HELMET", -1),
            new CategoryItem("IRON_HELMET", -1),
            new CategoryItem("GOLDEN_HELMET", -1),
            new CategoryItem("LEATHER_HELMET", -1),
            new CategoryItem("CHAINMAIL_HELMET", -1),
            new CategoryItem("TURTLE_HELMET", -1)
        ));
        armor.categories.put("Napierśniki", List.of(
            new CategoryItem("NETHERITE_CHESTPLATE", -1),
            new CategoryItem("DIAMOND_CHESTPLATE", -1),
            new CategoryItem("IRON_CHESTPLATE", -1),
            new CategoryItem("GOLDEN_CHESTPLATE", -1),
            new CategoryItem("LEATHER_CHESTPLATE", -1),
            new CategoryItem("CHAINMAIL_CHESTPLATE", -1)
        ));
        armor.categories.put("Spodnie", List.of(
            new CategoryItem("NETHERITE_LEGGINGS", -1),
            new CategoryItem("DIAMOND_LEGGINGS", -1),
            new CategoryItem("IRON_LEGGINGS", -1),
            new CategoryItem("GOLDEN_LEGGINGS", -1),
            new CategoryItem("LEATHER_LEGGINGS", -1),
            new CategoryItem("CHAINMAIL_LEGGINGS", -1)
        ));
        armor.categories.put("Buty", List.of(
            new CategoryItem("NETHERITE_BOOTS", -1),
            new CategoryItem("DIAMOND_BOOTS", -1),
            new CategoryItem("IRON_BOOTS", -1),
            new CategoryItem("GOLDEN_BOOTS", -1),
            new CategoryItem("LEATHER_BOOTS", -1),
            new CategoryItem("CHAINMAIL_BOOTS", -1)
        ));
        armor.categories.put("Elytry", List.of(
            new CategoryItem("ELYTRA", -1)
        ));
        filters.put("armor", armor);

        // WEAPONS filter
        FilterEntry weapons = new FilterEntry();
        weapons.icon = new IconEntry("NETHERITE_SWORD", 0);
        weapons.name = "&8× &#FF8CF9&lNarzędzia";
        weapons.lore = List.of("", "&7Filtruj broń i narzędzia", "&7według typu.", "", "&fKliknij, aby zmienić kategorię");
        weapons.categories = new LinkedHashMap<>();
        weapons.categories.put("Miecze", List.of(
            new CategoryItem("NETHERITE_SWORD", -1),
            new CategoryItem("DIAMOND_SWORD", -1),
            new CategoryItem("IRON_SWORD", -1),
            new CategoryItem("GOLDEN_SWORD", -1),
            new CategoryItem("STONE_SWORD", -1),
            new CategoryItem("WOODEN_SWORD", -1)
        ));
        weapons.categories.put("Kilofy", List.of(
            new CategoryItem("NETHERITE_PICKAXE", -1),
            new CategoryItem("DIAMOND_PICKAXE", -1),
            new CategoryItem("IRON_PICKAXE", -1),
            new CategoryItem("GOLDEN_PICKAXE", -1),
            new CategoryItem("STONE_PICKAXE", -1),
            new CategoryItem("WOODEN_PICKAXE", -1)
        ));
        weapons.categories.put("Nożyce", List.of(
            new CategoryItem("SHEARS", -1)
        ));
        filters.put("weapons", weapons);

        // SPECIAL filter
        FilterEntry special = new FilterEntry();
        special.icon = new IconEntry("ENDER_EYE", 0);
        special.name = "&8× &#00C2FF&lSpecjalne";
        special.lore = List.of("", "&7Specjalne przedmioty", "&7z eventów i nie tylko.", "", "&fKliknij, aby zmienić kategorię");
        special.categories = new LinkedHashMap<>();
        special.categories.put("Eventówki", List.of(
            new CategoryItem("PURPLE_DYE", 1),
            new CategoryItem("YELLOW_DYE", 1),
            new CategoryItem("BLUE_DYE", 1)
        ));
        filters.put("special", special);

        // PREMIUM filter
        FilterEntry premium = new FilterEntry();
        premium.icon = new IconEntry("GOLDEN_CARROT", 0);
        premium.name = "&8× &#F6FF93&lPremium";
        premium.lore = List.of("", "&7Premium przedmioty:", "&7klucze, vouchery, sigile,", "&7odłamki i więcej.", "", "&fKliknij, aby zmienić kategorię");
        premium.categories = new LinkedHashMap<>();
        premium.categories.put("Odłamki", List.of(
            new CategoryItem("PAPER", 1)
        ));
        premium.categories.put("Fragmenty Klanu", List.of(
            new CategoryItem("PAPER", 2)
        ));
        premium.categories.put("OgBoxy", List.of(
            new CategoryItem("PURPLE_GLAZED_TERRACOTTA", -1)
        ));
        premium.categories.put("Klucze", List.of(
            new CategoryItem("TRIPWIRE_HOOK", -1)
        ));
        premium.categories.put("Vouchery", List.of(
            new CategoryItem("PAPER", -1)
        ));
        premium.categories.put("Sigile", List.of(
            new CategoryItem("RED_DYE", -1),
            new CategoryItem("LIGHT_BLUE_DYE", -1),
            new CategoryItem("PURPLE_DYE", -1),
            new CategoryItem("ORANGE_DYE", -1),
            new CategoryItem("ENCHANTED_BOOK", -1)
        ));
        filters.put("premium", premium);

        return filters;
    }

    public static class FilterEntry extends OkaeriConfig {
        public IconEntry icon;
        public String name;
        public List<String> lore;
        public Map<String, List<CategoryItem>> categories;
    }

    public static class IconEntry extends OkaeriConfig {
        public String material;
        public int customModelData;

        public IconEntry() {}

        public IconEntry(String material, int customModelData) {
            this.material = material;
            this.customModelData = customModelData;
        }
    }

    public static class CategoryItem extends OkaeriConfig {
        public String material;
        public int customModelData;

        public CategoryItem() {}

        public CategoryItem(String material, int customModelData) {
            this.material = material;
            this.customModelData = customModelData;
        }
    }
}
