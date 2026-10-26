package dev.grzybalson.config.guis;

import eu.okaeri.configs.OkaeriConfig;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GuiTemplateConfig extends OkaeriConfig {

    private Map<String, List<Integer>> template27 = createTemplate27();

    private Map<String, List<Integer>> template45 = createTemplate45();

    private Map<String, List<Integer>> template54 = createTemplate54();
    private String backgroundItemName = "&c";

    private boolean hideAttributes = true;

    private static Map<String, List<Integer>> createTemplate27() {
        Map<String, List<Integer>> template = new HashMap<>();
        template.put("LIGHT_BLUE_STAINED_GLASS_PANE", Arrays.asList(0, 8, 18, 26));
        template.put("CYAN_STAINED_GLASS_PANE", Arrays.asList(1, 7, 9, 17, 19, 25));
        template.put("WHITE_STAINED_GLASS_PANE", Arrays.asList(2, 6));
        return template;
    }

    private static Map<String, List<Integer>> createTemplate45() {
        Map<String, List<Integer>> template = new HashMap<>();
        template.put("LIGHT_BLUE_STAINED_GLASS_PANE", Arrays.asList(0, 8, 36, 44));
        template.put("CYAN_STAINED_GLASS_PANE", Arrays.asList(1, 9, 7, 17, 43, 35, 27, 37));
        template.put("WHITE_STAINED_GLASS_PANE", Arrays.asList(2, 6, 18, 26, 38, 42));
        return template;
    }

    private static Map<String, List<Integer>> createTemplate54() {
        Map<String, List<Integer>> template = new HashMap<>();
        template.put("LIGHT_BLUE_STAINED_GLASS_PANE", Arrays.asList(0, 8, 45, 53));
        template.put("CYAN_STAINED_GLASS_PANE", Arrays.asList(1, 9, 7, 17, 52, 44, 36, 46));
        template.put("WHITE_STAINED_GLASS_PANE", Arrays.asList(2, 6, 18, 26, 47, 51));
        return template;
    }

    public Map<String, List<Integer>> getTemplate27() { return template27; }
    public Map<String, List<Integer>> getTemplate45() { return template45; }
    public Map<String, List<Integer>> getTemplate54() { return template54; }
    public String getBackgroundItemName() { return backgroundItemName; }
    public boolean isHideAttributes() { return hideAttributes; }
}
