package dev.grzybalson.config.modules.clearlag;

import eu.okaeri.configs.OkaeriConfig;
import java.util.Arrays;
import java.util.List;

public class ClearlagMessagesConfig extends OkaeriConfig {
    private List<Integer> warningTimes = Arrays.asList(60, 30, 10, 5, 3, 2, 1);

    private String warningMessage = "";

    private String clearMessage = "";

    private String noItemsMessage = "";

    public List<Integer> getWarningTimes() { return warningTimes; }
    public String getWarningMessage() { return warningMessage; }
    public String getClearMessage() { return clearMessage; }
    public String getNoItemsMessage() { return noItemsMessage; }
}
