package dev.grzybalson.config.configs;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.NameModifier;
import eu.okaeri.configs.annotation.NameStrategy;
import eu.okaeri.configs.annotation.Names;

import java.util.Arrays;
import java.util.List;

@Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
public class GUIConfig extends OkaeriConfig {
    private int defaultRows = 5;
    private int maxRows = 6;
    private List<String> background3Rows = Arrays.asList(
        "GGGGGGGGG",
        "GXXXXXXXG",
        "GGGGGGGGG"
    );
    private List<String> background5Rows = Arrays.asList(
        "GGGGGGGGG",
        "GXXXXXXXG",
        "GXXXXXXXG",
        "GXXXXXXXG",
        "GGGGGGGGG"
    );
    private List<String> background6Rows = Arrays.asList(
        "GGGGGGGGG",
        "GXXXXXXXG",
        "GXXXXXXXG",
        "GXXXXXXXG",
        "GXXXXXXXG",
        "GGGGGGGGG"
    );
    private String backgroundItemName = " ";

    public int getDefaultRows() { return normalizeRows(defaultRows); }
    public int getMaxRows() { return Math.max(3, Math.min(6, maxRows)); }
    public List<String> getBackground3Rows() { return background3Rows; }
    public List<String> getBackground5Rows() { return background5Rows; }
    public List<String> getBackground6Rows() { return background6Rows; }
    public String getBackgroundItemName() { return backgroundItemName; }

    public int normalizeRows(int rows) {
        return Math.max(3, Math.min(getMaxRows(), rows));
    }

    public List<String> getBackgroundForRows(int rows) {
        return switch (normalizeRows(rows)) {
            case 3 -> background3Rows;
            case 5 -> background5Rows;
            case 6 -> background6Rows;
            default -> background5Rows;
        };
    }
}
