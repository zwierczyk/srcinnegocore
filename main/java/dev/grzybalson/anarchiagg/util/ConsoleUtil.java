package dev.grzybalson.anarchiagg.util;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public class ConsoleUtil {
    public static void execute(String command) {
        Bukkit.dispatchCommand((CommandSender)Bukkit.getConsoleSender(), (String)command);
    }

    public static void printStartupBanner(JavaPlugin plugin, boolean licenseActive) {
        String version = plugin.getDescription().getVersion();
        String licenseStatus = licenseActive ? "&aAKTYWNA" : "&cNIEAKTYWNA";

        String[] lines = {
                "",
                "&b _  ___     _    __  __    &3 ____ ___  ____  _____",
                "&b| |/ / |   /_\\   \\ \\/ /    &3/ ___/ _ \\|  _ \\| ____|",
                "&b| ' <| |  / _ \\   \\  /     &3| |  | | | | |_) |  _|",
                "&b| . \\| |_/ ___ \\  /  \\     &3| |__| |_| |  _ <| |___",
                "&b|_|\\_\\____/_/ \\_\\ /_/\\_\\    &3\\____\\___/|_| \\_\\_____|",
                "",
                "&b        /\\_/\\     &3~ Stworzone przez &ftazsyv&3 ~",
                "&b       ( o.o )    &3~ Wersja &f" + version + "&3 ~",
                "&b        > ^ <     &3~ Licencja " + licenseStatus + "&3 ~",
                ""
        };

        for (String line : lines) {
            dev.grzybalson.anarchiagg.util.TextUtil.sendConsole(line);
        }
    }
}
