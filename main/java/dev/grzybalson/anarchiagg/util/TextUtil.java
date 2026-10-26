package dev.grzybalson.anarchiagg.util;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import dev.grzybalson.utils.ColorUtils;

import java.time.Duration;

public class TextUtil {
    private static final int COLOR_CACHE_LIMIT = 4096;
    private static final String CONSOLE_PREFIX = "&8[&c&lHolly&4&lCore&8]";
    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("(?i)(?:&|\u00a7)#([0-9a-f]{6})");
    private static final Pattern AMPERSAND_HEX_COLOR_PATTERN = Pattern.compile("(?i)&#[0-9a-f]{6}");
    private static final Pattern SECTION_HEX_COLOR_PATTERN = Pattern.compile("(?i)\u00a7#[0-9a-f]{6}");
    private static final Map<String, String> COLOR_CACHE = Collections.synchronizedMap(new LinkedHashMap<>(512, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > COLOR_CACHE_LIMIT;
        }
    });

    public static String fixColor(String string) {
        if (string == null || string.isEmpty()) {
            return "";
        }

        String input = string;
        String cached = COLOR_CACHE.get(string);
        if (cached != null) {
            return cached;
        }

        if (string.indexOf("&#") != -1 || string.indexOf("\u00a7#") != -1) {
            Matcher matcher = HEX_COLOR_PATTERN.matcher(string);
            StringBuffer colored = null;
            while (matcher.find()) {
                if (colored == null) {
                    colored = new StringBuffer();
                }
                matcher.appendReplacement(colored, Matcher.quoteReplacement(String.valueOf(ChatColor.of("#" + matcher.group(1)))));
            }
            if (colored != null) {
                matcher.appendTail(colored);
                string = colored.toString();
            }
        }

        String fixed = ChatColor.translateAlternateColorCodes((char)'&', (String)string).replace(">>", "\u00bb").replace("<<", "\u00ab").replace("->", "\u2192").replace("<-", "\u2190").replace("**", "\u2022");
        COLOR_CACHE.put(input, fixed);
        return fixed;
    }

    public static String sanitizeChatMessage(String message) {
        if (message == null || message.isEmpty()) {
            return "";
        }

        String sanitized = AMPERSAND_HEX_COLOR_PATTERN.matcher(message).replaceAll("");
        sanitized = SECTION_HEX_COLOR_PATTERN.matcher(sanitized).replaceAll("");
        sanitized = ChatColor.translateAlternateColorCodes('&', sanitized);
        sanitized = ChatColor.stripColor(sanitized);

        return sanitized == null ? "" : sanitized;
    }

    public static List<String> fixColor(List<String> list) {
        ArrayList<String> finallist = new ArrayList<String>(list.size());
        for (String s : list) {
            finallist.add(dev.grzybalson.anarchiagg.util.TextUtil.fixColor(s));
        }
        return finallist;
    }

    public static void sendLogger(String text) {
        sendConsole("&e" + text);
    }

    public static void sendConsole(String text) {
        Bukkit.getConsoleSender().sendMessage(dev.grzybalson.anarchiagg.util.TextUtil.fixColor(CONSOLE_PREFIX + " " + text));
    }

    public static void printStackTrace(Throwable throwable) {
        if (throwable == null) {
            return;
        }

        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        for (String line : writer.toString().split("\\R")) {
            sendConsole("&c" + line);
        }
    }

    public static void sendMessage(CommandSender player, String text) {
        if (player == null || text == null || text.isEmpty()) {
            return;
        }
        if (player instanceof Player onlinePlayer) {
            onlinePlayer.sendMessage(component(text));
            return;
        }
        player.sendMessage(dev.grzybalson.anarchiagg.util.TextUtil.fixColor(text));
    }

    public static void sendTitle(Player player, String title, String subtitle) {
        sendTitle(player, title, subtitle, 10, 70, 20);
    }

    public static void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        if (player == null) {
            return;
        }
        player.showTitle(Title.title(
                component(title),
                component(subtitle),
                Title.Times.times(ticks(fadeIn), ticks(stay), ticks(fadeOut))
        ));
    }

    public static void sendActionBar(Player player, String text) {
        if (player == null || text == null || text.isEmpty()) {
            return;
        }
        player.sendActionBar(component(text));
    }

    public static Component component(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        if (text.indexOf('\u00A7') >= 0 && !text.toLowerCase().contains("<shadow")) {
            return ColorUtils.colorizeComponent(text);
        }
        return ColorUtils.colorizeComponent(text);
    }

    private static Duration ticks(int ticks) {
        return Duration.ofMillis(Math.max(0L, ticks) * 50L);
    }
}
