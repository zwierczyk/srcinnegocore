package dev.grzybalson.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ColorUtils {

    public static final String NO_PERMISSION_FORMAT = "&#FF0000✘ &8Ξ &cNie posiadasz dostępu do &#FF0000{command} &8(&4{permission}&8)";

    public static final String ERROR_PREFIX = "&#FF0000✘ &8Ξ &c";
    public static final String SUCCESS_PREFIX = "&#00FF3B✔ &8Ξ &#4EFA76";
    public static final String INFO_PREFIX = "&#00E0FFℹ &8Ξ &f";
    public static final String WARNING_PREFIX = "&#FFB800⚠ &8Ξ &e";
    public static final String USAGE_PREFIX = ERROR_PREFIX + "Poprawne użycie: &#FF0000";

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern LEGACY_HEX_PATTERN = Pattern.compile("(?i)[&\u00A7]x(?:[&\u00A7][0-9a-f]){6}");
    private static final Pattern SHADOW_TAG_PATTERN = Pattern.compile("(?i)<\\s*/?\\s*!?shadow(?:\\s*:[^>]*)?\\s*/?\\s*>");
    private static final Pattern NEGATED_SHADOW_TAG_PATTERN = Pattern.compile("(?i)<\\s*!shadow\\s*/?\\s*>");
    private static final Pattern MINI_TAG_PATTERN = Pattern.compile(
        "(?i)<\\s*/?\\s*!?(?:#[a-f0-9]{6}|gradient|rainbow|transition|color|colour|shadow|bold|b|italic|i|underlined|u|strikethrough|st|obfuscated|obf|reset|black|dark_blue|dark_green|dark_aqua|dark_red|dark_purple|gold|gray|grey|dark_gray|dark_grey|blue|green|aqua|red|light_purple|yellow|white)(?:\\s*:[^>]*)?\\s*/?\\s*>"
    );
    private static final String LEADING_FORMAT_TOKEN = "(?:&#[A-Fa-f0-9]{6}|[&§][0-9A-FK-ORa-fk-or])";
    private static final String OPTIONAL_FORMATS = "(?:" + LEADING_FORMAT_TOKEN + "\\s*)*";
    private static final Pattern LEGACY_SUCCESS_PREFIX_PATTERN = Pattern.compile(
        "^\\s*" + OPTIONAL_FORMATS + "\\[\\s*" + OPTIONAL_FORMATS + "(?:✔|✓)\\s*" + OPTIONAL_FORMATS + "]\\s*" + OPTIONAL_FORMATS
    );
    private static final Pattern LEGACY_ERROR_PREFIX_PATTERN = Pattern.compile(
        "^\\s*" + OPTIONAL_FORMATS + "\\[\\s*" + OPTIONAL_FORMATS + "(?:✘|✖|❌|x|X)\\s*" + OPTIONAL_FORMATS + "]\\s*" + OPTIONAL_FORMATS
    );
    private static final Pattern LEGACY_WARNING_PREFIX_PATTERN = Pattern.compile(
        "^\\s*" + OPTIONAL_FORMATS + "\\[\\s*" + OPTIONAL_FORMATS + "(?:!|⚠)\\s*" + OPTIONAL_FORMATS + "]\\s*" + OPTIONAL_FORMATS
    );
    private static final Pattern LEGACY_INFO_PREFIX_PATTERN = Pattern.compile(
        "^\\s*" + OPTIONAL_FORMATS + "\\[\\s*" + OPTIONAL_FORMATS + "(?:i|I|ℹ)\\s*" + OPTIONAL_FORMATS + "]\\s*" + OPTIONAL_FORMATS
    );
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.builder()
        .character('\u00A7')
        .hexColors()
        .useUnusualXRepeatedCharacterHexFormat()
        .build();
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final TagResolver SHADOW_TAG_RESOLVER = TagResolver.resolver("shadow", (arguments, context) -> {
        String color = arguments.popOr("Shadow tag requires a color").value();
        String alpha = arguments.hasNext() ? arguments.pop().value() : null;
        Integer argb = parseShadowColor(color, alpha);
        if (argb == null) {
            return Tag.styling(style -> {});
        }
        return Tag.styling((Style.Builder style) -> ShadowSupport.apply(style, argb));
    });
    private static final PlainTextComponentSerializer PLAIN_SERIALIZER = PlainTextComponentSerializer.plainText();
    private static final int CACHE_MAX_SIZE = 32768;
    private static final ConcurrentHashMap<String, Component> COMPONENT_CACHE = new ConcurrentHashMap<>(4096);
    private static final ConcurrentHashMap<String, String> LEGACY_CACHE = new ConcurrentHashMap<>(4096);
    private static final ConcurrentHashMap<String, String> STRING_CACHE = new ConcurrentHashMap<>(4096);
    private static final ConcurrentHashMap<String, List<Component>> LORE_CACHE = new ConcurrentHashMap<>(1024);
    private static final AtomicInteger COMPONENT_CACHE_ENTRIES = new AtomicInteger();
    private static final AtomicInteger LEGACY_CACHE_ENTRIES = new AtomicInteger();
    private static final AtomicInteger STRING_CACHE_ENTRIES = new AtomicInteger();
    private static final AtomicInteger LORE_CACHE_ENTRIES = new AtomicInteger();

    public static String formatNoPermission(String command, String permission) {
        String formattedCommand = command == null ? "" : command.trim();
        if (formattedCommand.isEmpty()) {
            formattedCommand = "/komenda";
        } else if (!formattedCommand.startsWith("/")) {
            formattedCommand = "/" + formattedCommand;
        }

        String formattedPermission = permission == null || permission.isBlank() ? "brak-permisji" : permission.trim();
        return NO_PERMISSION_FORMAT
            .replace("{command}", formattedCommand)
            .replace("{permission}", formattedPermission);
    }

    public static Component noPermission(String command, String permission) {
        return colorizeComponent(formatNoPermission(command, permission));
    }

    public static String colorize(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        text = normalizeLegacyMessagePrefix(text);
        String cached = STRING_CACHE.get(text);
        if (cached != null) {
            return cached;
        }

        String colored = LEGACY_SERIALIZER.serialize(colorizeComponent(text));
        putBounded(STRING_CACHE, STRING_CACHE_ENTRIES, text, colored);
        return colored;
    }

    public static List<String> colorizeList(List<String> list) {
        if (list == null) return Collections.emptyList();
        List<String> result = new ArrayList<>(list.size());
        for (String s : list) {
            result.add(colorize(s));
        }
        return result;
    }

    public static String colorizeLegacy(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        text = normalizeLegacyMessagePrefix(text);
        String cached = LEGACY_CACHE.get(text);
        if (cached != null) {
            return cached;
        }

        String colored;
        if (text.indexOf('<') >= 0 && text.indexOf('>') >= 0) {
            colored = colorize(text);
        } else {
            colored = translateHexColors(text).replace('&', '\u00A7');
        }
        putBounded(LEGACY_CACHE, LEGACY_CACHE_ENTRIES, text, colored);
        return colored;
    }

    public static Component colorizeComponent(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }

        text = normalizeLegacyMessagePrefix(text);
        Component cached = COMPONENT_CACHE.get(text);
        if (cached != null) {
            return cached;
        }

        Component component;
        // Szybka ścieżka: same & / § bez MiniMessage / hex (#) — bez legacyToMiniMessage.
        if (text.indexOf('<') < 0 && text.indexOf('#') < 0) {
            component = applyDefaults(LEGACY_SERIALIZER.deserialize(text.replace('&', '\u00A7')));
            putBounded(COMPONENT_CACHE, COMPONENT_CACHE_ENTRIES, text, component);
            return component;
        }

        String miniMessage = legacyToMiniMessage(text);
        try {
            component = parseMiniMessage(miniMessage);
        } catch (RuntimeException ignored) {
            String withoutShadow = SHADOW_TAG_PATTERN.matcher(miniMessage).replaceAll("");
            try {
                component = parseMiniMessage(withoutShadow);
            } catch (RuntimeException ignoredAgain) {
                component = applyDefaults(LEGACY_SERIALIZER.deserialize(translateHexColors(text).replace('&', '\u00A7')));
            }
        }

        putBounded(COMPONENT_CACHE, COMPONENT_CACHE_ENTRIES, text, component);
        return component;
    }

    public static Component colorizeComponentWithShadow(String text, String shadowColor) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }

        Integer argb = parseShadowColor(shadowColor == null || shadowColor.isBlank() ? "#000000C0" : shadowColor, null);
        if (argb == null) {
            return colorizeComponent(text);
        }
        return applyShadow(colorizeComponent(text), argb);
    }

    private static Component applyShadow(Component component, int argb) {
        Component styled = component.style(style -> ShadowSupport.apply(style, argb));
        List<Component> children = component.children();
        if (children.isEmpty()) {
            return styled;
        }

        List<Component> styledChildren = new ArrayList<>(children.size());
        for (Component child : children) {
            styledChildren.add(applyShadow(child, argb));
        }
        return styled.children(styledChildren);
    }

    public static Component colorizeComponentWithReset(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }

        if (!text.startsWith("&r") && !text.startsWith("<reset>")) {
            text = "&r" + text;
        }

        return colorizeComponent(text);
    }

    public static Component colorizeWithPlainPlaceholder(String text, String placeholder, String plainText) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        if (placeholder == null || placeholder.isEmpty() || !text.contains(placeholder)) {
            return colorizeComponent(text);
        }

        String token = "__HOLLYCORE_PLAIN_" + UUID.randomUUID().toString().replace("-", "") + "__";
        Component component = colorizeComponent(text.replace(placeholder, token));
        return component.replaceText(TextReplacementConfig.builder()
            .matchLiteral(token)
            .replacement((matchResult, builder) -> builder.content(plainText == null ? "" : plainText))
            .build());
    }

    private static Component applyDefaults(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }

    private static <T> void putBounded(ConcurrentHashMap<String, T> cache, AtomicInteger entries, String key, T value) {
        if (cache.putIfAbsent(key, value) != null) {
            return;
        }
        if (entries.incrementAndGet() > CACHE_MAX_SIZE) {
            cache.clear();
            entries.set(0);
            cache.put(key, value);
            entries.set(1);
        }
    }

    private static Component parseMiniMessage(String miniMessage) {
        String prepared = normalizeShadowAliases(miniMessage);
        Component component = applyDefaults(MINI_MESSAGE.deserialize(prepared, SHADOW_TAG_RESOLVER));
        if (SHADOW_TAG_PATTERN.matcher(PLAIN_SERIALIZER.serialize(component)).find()) {
            return applyDefaults(MINI_MESSAGE.deserialize(SHADOW_TAG_PATTERN.matcher(prepared).replaceAll(""), SHADOW_TAG_RESOLVER));
        }
        return component;
    }

    private static String normalizeShadowAliases(String text) {
        return NEGATED_SHADOW_TAG_PATTERN.matcher(text).replaceAll("<shadow:#00000000>");
    }

    private static Integer parseShadowColor(String colorInput, String alphaInput) {
        if (colorInput == null || colorInput.isBlank()) {
            return null;
        }

        String color = colorInput.trim();
        if (color.equalsIgnoreCase("none") || color.equalsIgnoreCase("off") || color.equalsIgnoreCase("false")) {
            return 0x00000000;
        }

        int alpha = parseShadowAlpha(alphaInput, 0x40);
        if (color.startsWith("#")) {
            String hex = color.substring(1);
            if (hex.length() == 6) {
                Integer rgb = parseHexInt(hex);
                return rgb == null ? null : (alpha << 24) | rgb;
            }
            if (hex.length() == 8) {
                Integer rgba = parseHexInt(hex);
                if (rgba == null) {
                    return null;
                }
                int rgb = (rgba >>> 8) & 0x00FFFFFF;
                int hexAlpha = rgba & 0xFF;
                return (hexAlpha << 24) | rgb;
            }
            return null;
        }

        NamedTextColor namedColor = namedTextColor(color);
        if (namedColor == null) {
            return null;
        }
        return (alpha << 24) | namedColor.value();
    }

    private static Integer parseHexInt(String hex) {
        try {
            return (int) Long.parseLong(hex, 16);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static int parseShadowAlpha(String input, int fallback) {
        if (input == null || input.isBlank()) {
            return fallback;
        }
        try {
            double value = Double.parseDouble(input.trim().replace(',', '.'));
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                return fallback;
            }
            return Math.max(0, Math.min(255, (int) Math.round(value * 255.0D)));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static NamedTextColor namedTextColor(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "black" -> NamedTextColor.BLACK;
            case "dark_blue" -> NamedTextColor.DARK_BLUE;
            case "dark_green" -> NamedTextColor.DARK_GREEN;
            case "dark_aqua" -> NamedTextColor.DARK_AQUA;
            case "dark_red" -> NamedTextColor.DARK_RED;
            case "dark_purple" -> NamedTextColor.DARK_PURPLE;
            case "gold" -> NamedTextColor.GOLD;
            case "gray", "grey" -> NamedTextColor.GRAY;
            case "dark_gray", "dark_grey" -> NamedTextColor.DARK_GRAY;
            case "blue" -> NamedTextColor.BLUE;
            case "green" -> NamedTextColor.GREEN;
            case "aqua" -> NamedTextColor.AQUA;
            case "red" -> NamedTextColor.RED;
            case "light_purple" -> NamedTextColor.LIGHT_PURPLE;
            case "yellow" -> NamedTextColor.YELLOW;
            case "white" -> NamedTextColor.WHITE;
            default -> null;
        };
    }

    public static NamedTextColor parseToNamedTextColor(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String clean = input.trim();

        // 1. Handle &x&1&2&3&4&5&6 format (Minecraft hex format)
        if (clean.toLowerCase(java.util.Locale.ROOT).replaceAll("§", "&").startsWith("&x")) {
            String hex = clean.replaceAll("[&§]x?", "").replaceAll("[&§]", "");
            if (hex.length() == 6) {
                try {
                    int rgb = Integer.parseInt(hex, 16);
                    int r = (rgb >> 16) & 0xFF;
                    int g = (rgb >> 8) & 0xFF;
                    int b = rgb & 0xFF;
                    return findClosestNamedTextColor(r, g, b);
                } catch (NumberFormatException ignored) {}
            }
        }

        // 2. Handle &#RRGGBB or §#RRGGBB format
        if (clean.startsWith("&#") || clean.startsWith("§#")) {
            String hex = clean.substring(2);
            if (hex.length() >= 6) {
                hex = hex.substring(0, 6);
                try {
                    int rgb = Integer.parseInt(hex, 16);
                    int r = (rgb >> 16) & 0xFF;
                    int g = (rgb >> 8) & 0xFF;
                    int b = rgb & 0xFF;
                    return findClosestNamedTextColor(r, g, b);
                } catch (NumberFormatException ignored) {}
            }
        }

        // 3. Handle #RRGGBB format
        if (clean.startsWith("#") || clean.startsWith("<#")) {
            String hex = clean.startsWith("<#") ? clean.substring(2).replace(">", "") : clean.substring(1);
            if (hex.length() >= 6) {
                hex = hex.substring(0, 6);
                try {
                    int rgb = Integer.parseInt(hex, 16);
                    int r = (rgb >> 16) & 0xFF;
                    int g = (rgb >> 8) & 0xFF;
                    int b = rgb & 0xFF;
                    return findClosestNamedTextColor(r, g, b);
                } catch (NumberFormatException ignored) {}
            }
        }

        // 4. Handle standard legacy color codes (&c, &2, etc.)
        if ((clean.startsWith("&") || clean.startsWith("§")) && clean.length() >= 2) {
            char code = Character.toLowerCase(clean.charAt(1));
            NamedTextColor legacy = switch (code) {
                case '0' -> NamedTextColor.BLACK;
                case '1' -> NamedTextColor.DARK_BLUE;
                case '2' -> NamedTextColor.DARK_GREEN;
                case '3' -> NamedTextColor.DARK_AQUA;
                case '4' -> NamedTextColor.DARK_RED;
                case '5' -> NamedTextColor.DARK_PURPLE;
                case '6' -> NamedTextColor.GOLD;
                case '7' -> NamedTextColor.GRAY;
                case '8' -> NamedTextColor.DARK_GRAY;
                case '9' -> NamedTextColor.BLUE;
                case 'a' -> NamedTextColor.GREEN;
                case 'b' -> NamedTextColor.AQUA;
                case 'c' -> NamedTextColor.RED;
                case 'd' -> NamedTextColor.LIGHT_PURPLE;
                case 'e' -> NamedTextColor.YELLOW;
                case 'f' -> NamedTextColor.WHITE;
                default -> null;
            };
            if (legacy != null) return legacy;
        }

        return namedTextColor(clean);
    }

    public static NamedTextColor findClosestNamedTextColor(int r, int g, int b) {
        NamedTextColor bestMatch = NamedTextColor.WHITE;
        double minDistance = Double.MAX_VALUE;

        NamedTextColor[] colors = new NamedTextColor[] {
            NamedTextColor.BLACK, NamedTextColor.DARK_BLUE, NamedTextColor.DARK_GREEN, NamedTextColor.DARK_AQUA,
            NamedTextColor.DARK_RED, NamedTextColor.DARK_PURPLE, NamedTextColor.GOLD, NamedTextColor.GRAY,
            NamedTextColor.DARK_GRAY, NamedTextColor.BLUE, NamedTextColor.GREEN, NamedTextColor.AQUA,
            NamedTextColor.RED, NamedTextColor.LIGHT_PURPLE, NamedTextColor.YELLOW, NamedTextColor.WHITE
        };

        for (NamedTextColor color : colors) {
            int cr = (color.value() >> 16) & 0xFF;
            int cg = (color.value() >> 8) & 0xFF;
            int cb = color.value() & 0xFF;

            double dist = Math.pow(r - cr, 2) + Math.pow(g - cg, 2) + Math.pow(b - cb, 2);
            if (dist < minDistance) {
                minDistance = dist;
                bestMatch = color;
            }
        }
        return bestMatch;
    }

    private static final class ShadowSupport {

        private static final Method SHADOW_COLOR_FACTORY;
        private static final Method STYLE_SETTER_SHADOW_COLOR;

        static {
            Method colorFactory = null;
            Method styleSetter = null;
            try {
                Class<?> argbLikeClass = Class.forName("net.kyori.adventure.util.ARGBLike");
                Class<?> shadowColorClass = Class.forName("net.kyori.adventure.text.format.ShadowColor");
                Class<?> styleSetterClass = Class.forName("net.kyori.adventure.text.format.StyleSetter");
                colorFactory = shadowColorClass.getMethod("shadowColor", int.class);
                styleSetter = styleSetterClass.getMethod("shadowColor", argbLikeClass);
            } catch (ReflectiveOperationException | LinkageError ignored) {
            }
            SHADOW_COLOR_FACTORY = colorFactory;
            STYLE_SETTER_SHADOW_COLOR = styleSetter;
        }

        private ShadowSupport() {
        }

        private static void apply(Style.Builder builder, int argb) {
            if (SHADOW_COLOR_FACTORY == null || STYLE_SETTER_SHADOW_COLOR == null) {
                return;
            }
            try {
                Object shadowColor = SHADOW_COLOR_FACTORY.invoke(null, argb);
                STYLE_SETTER_SHADOW_COLOR.invoke(builder, shadowColor);
            } catch (ReflectiveOperationException | LinkageError ignored) {
            }
        }
    }

    private static String translateHexColors(String text) {
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuilder buffer = new StringBuilder();

        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("\u00A7x");
            for (char c : hex.toCharArray()) {
                replacement.append('\u00A7').append(Character.toLowerCase(c));
            }
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement.toString()));
        }
        matcher.appendTail(buffer);

        return buffer.toString();
    }

    private static String legacyToMiniMessage(String text) {
        String normalized = legacyHexToMiniMessage(text);
        StringBuilder out = new StringBuilder(normalized.length());

        for (int i = 0; i < normalized.length(); i++) {
            char current = normalized.charAt(i);
            if ((current == '&' || current == '\u00A7') && i + 1 < normalized.length()) {
                String tag = legacyCodeToMiniMessage(normalized.charAt(++i));
                if (tag != null) {
                    out.append(tag);
                    continue;
                }
                out.append(current).append(normalized.charAt(i));
                continue;
            }
            out.append(current);
        }

        return out.toString();
    }

    private static String legacyHexToMiniMessage(String text) {
        String normalized = HEX_PATTERN.matcher(text).replaceAll("<reset><#$1>");
        Matcher matcher = LEGACY_HEX_PATTERN.matcher(normalized);
        StringBuilder buffer = new StringBuilder();

        while (matcher.find()) {
            String hex = matcher.group().replaceAll("[&\u00A7xX]", "");
            matcher.appendReplacement(buffer, Matcher.quoteReplacement("<reset><#" + hex + ">"));
        }
        matcher.appendTail(buffer);

        return buffer.toString();
    }

    private static String legacyCodeToMiniMessage(char code) {
        return switch (Character.toLowerCase(code)) {
            case '0' -> "<reset><black>";
            case '1' -> "<reset><dark_blue>";
            case '2' -> "<reset><dark_green>";
            case '3' -> "<reset><dark_aqua>";
            case '4' -> "<reset><dark_red>";
            case '5' -> "<reset><dark_purple>";
            case '6' -> "<reset><gold>";
            case '7' -> "<reset><gray>";
            case '8' -> "<reset><dark_gray>";
            case '9' -> "<reset><blue>";
            case 'a' -> "<reset><green>";
            case 'b' -> "<reset><aqua>";
            case 'c' -> "<reset><red>";
            case 'd' -> "<reset><light_purple>";
            case 'e' -> "<reset><yellow>";
            case 'f' -> "<reset><white>";
            case 'k' -> "<obfuscated>";
            case 'l' -> "<bold>";
            case 'm' -> "<strikethrough>";
            case 'n' -> "<underlined>";
            case 'o' -> "<italic>";
            case 'r' -> "<reset>";
            default -> null;
        };
    }

    public static String stripColors(String text) {
        if (text == null) {
            return null;
        }
        String withoutMiniMessage = MINI_TAG_PATTERN.matcher(text).replaceAll("");
        return LEGACY_HEX_PATTERN.matcher(withoutMiniMessage)
            .replaceAll("")
            .replaceAll("[\u00A7&][0-9a-fk-orA-FK-OR]", "")
            .replaceAll("&#[A-Fa-f0-9]{6}", "");
    }

    public static boolean isBlankLoreLine(String text) {
        if (text == null || text.isEmpty()) {
            return true;
        }
        String stripped = stripColors(text);
        return stripped == null || stripped.isBlank();
    }

    public static Component createLoreComponent(String text) {
        return colorizeComponent(text).decoration(TextDecoration.ITALIC, false);
    }

    public static List<Component> colorizeLore(String text) {
        if (isBlankLoreLine(text)) {
            return Collections.emptyList();
        }

        List<Component> cached = LORE_CACHE.get(text);
        if (cached != null) {
            return cached;
        }

        List<Component> lore = new ArrayList<>();
        for (String line : text.split("\\r?\\n", -1)) {
            if (isBlankLoreLine(line)) {
                continue;
            }
            lore.add(colorizeComponent(line).decoration(TextDecoration.ITALIC, false));
        }

        List<Component> result = Collections.unmodifiableList(lore);
        putBounded(LORE_CACHE, LORE_CACHE_ENTRIES, text, result);
        return result;
    }

    public static void appendLore(List<Component> lore, String text) {
        lore.addAll(colorizeLore(text));
    }

    /**
     * Converts old config prefixes such as &a[✔], &c[✘], &e[!] and &b[i]
     * to the current unified message style. This keeps already generated configs
     * compatible, so administrators do not need to delete them after an update.
     */
    public static String normalizeLegacyMessagePrefix(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        if (text.startsWith(USAGE_PREFIX)) {
            String syntax = text.substring(USAGE_PREFIX.length());
            String trimmed = syntax.stripLeading();
            if (syntax.isBlank() || trimmed.startsWith("&8•")) {
                return text;
            }
            return formatUsageText(syntax);
        }

        if (hasMessagePrefix(text)) {
            return text;
        }

        Matcher matcher = LEGACY_SUCCESS_PREFIX_PATTERN.matcher(text);
        if (matcher.find()) {
            return SUCCESS_PREFIX + text.substring(matcher.end()).trim();
        }

        matcher = LEGACY_ERROR_PREFIX_PATTERN.matcher(text);
        if (matcher.find()) {
            String body = text.substring(matcher.end()).trim();
            if (body.regionMatches(true, 0, "Poprawne użycie:", 0, "Poprawne użycie:".length())) {
                return formatUsageText(body.substring("Poprawne użycie:".length()));
            }
            return ERROR_PREFIX + body;
        }

        matcher = LEGACY_WARNING_PREFIX_PATTERN.matcher(text);
        if (matcher.find()) {
            return WARNING_PREFIX + text.substring(matcher.end()).trim();
        }

        matcher = LEGACY_INFO_PREFIX_PATTERN.matcher(text);
        if (matcher.find()) {
            return INFO_PREFIX + text.substring(matcher.end()).trim();
        }

        return text;
    }

    public static void sendTitle(Player player, String title, String subtitle) {
        sendTitle(player, title, subtitle, 5, 40, 10);
    }

    public static void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        if (player == null) {
            return;
        }
        TitleText styled = styleTitle(title, subtitle);
        player.sendTitle(
            colorize(styled.title()),
            colorize(styled.subtitle()),
            Math.max(0, fadeIn),
            Math.max(0, stay),
            Math.max(0, fadeOut)
        );
    }

    private static TitleText styleTitle(String title, String subtitle) {
        String safeTitle = title == null ? "" : title;
        String safeSubtitle = subtitle == null ? "" : subtitle;
        if (!stripColors(safeTitle).isBlank() || stripColors(safeSubtitle).isBlank()) {
            return new TitleText(safeTitle, safeSubtitle);
        }

        String plain = stripColors(safeSubtitle).trim().toLowerCase(Locale.ROOT);
        if (looksLikeSuccess(safeSubtitle, plain)) {
            return new TitleText("&#00FF3B&l✔ SUKCES", safeSubtitle);
        }
        if (looksLikeError(safeSubtitle, plain)) {
            return new TitleText("&#FF0000&l✘ BŁĄD", safeSubtitle);
        }
        if (startsWithColor(safeSubtitle, "&e", "§e", "&#FFB800") || plain.startsWith("uwaga")) {
            return new TitleText("&#FFB800&l⚠ UWAGA", safeSubtitle);
        }
        return new TitleText("&#00E0FF&lℹ INFORMACJA", safeSubtitle);
    }

    private static boolean looksLikeSuccess(String colored, String plain) {
        return startsWithColor(colored, "&a", "§a", "&#00FF3B", "&#4EFA76")
            || startsWithAny(plain, "pomyślnie", "pomyslnie", "otrzymałeś", "otrzymales", "sprzedano",
                "kupiono", "zakupiono", "ustawiono", "dodano", "zmieniono", "aktywowano",
                "ulepszono", "wylosowano", "rozdano", "naprawiono", "zabiłeś", "zabiles", "zadałeś", "zadales");
    }

    private static boolean looksLikeError(String colored, String plain) {
        return startsWithColor(colored, "&c", "§c", "&4", "§4", "&#FF0000")
            || startsWithAny(plain, "błąd", "blad", "nie możesz", "nie mozesz", "nie masz", "musisz",
                "brak", "wystąpił", "wystapil", "anulowano", "zostałeś zabity", "zostales zabity");
    }

    private static boolean startsWithAny(String value, String... prefixes) {
        for (String prefix : prefixes) {
            if (value.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean startsWithColor(String value, String... colors) {
        String trimmed = value == null ? "" : value.stripLeading();
        for (String color : colors) {
            if (trimmed.regionMatches(true, 0, color, 0, color.length())) {
                return true;
            }
        }
        return false;
    }

    private record TitleText(String title, String subtitle) {}

    public static void sendUsage(CommandSender sender, String... syntaxes) {
        if (sender == null) {
            return;
        }
        sender.sendMessage(colorizeComponent(USAGE_PREFIX));
        if (syntaxes == null) {
            return;
        }
        for (String syntax : syntaxes) {
            if (syntax == null || syntax.isBlank()) {
                continue;
            }
            sender.sendMessage(colorizeComponent(" &8• &4" + syntax.trim()));
        }
    }

    public static String formatCooldownMessage(int seconds) {
        return ERROR_PREFIX + "Musisz odczekać jeszcze &#FF0000" + seconds + "s &cprzed następnym użyciem!";
    }

    public static String formatSuccessMessage() {
        return SUCCESS_PREFIX + "Pomyślnie użyto kodu!";
    }

    public static Component errorPrefix() {
        return colorizeComponent(ERROR_PREFIX);
    }

    public static Component successPrefix() {
        return colorizeComponent(SUCCESS_PREFIX);
    }

    public static Component error(String message) {
        String value = message == null ? "" : message.trim();
        if (value.startsWith("Poprawne użycie:")) {
            return usage(value.substring("Poprawne użycie:".length()).trim());
        }
        if (hasMessagePrefix(value)) {
            return colorizeComponent(value);
        }
        return colorizeComponent(ERROR_PREFIX + value);
    }

    public static Component usage(String syntax) {
        return colorizeComponent(formatUsageText(syntax));
    }

    private static String formatUsageText(String syntax) {
        String value = syntax == null ? "" : syntax.trim();
        value = value.replaceFirst("^(?:" + LEADING_FORMAT_TOKEN + "\\s*)+", "").trim();
        if (value.isEmpty()) {
            return USAGE_PREFIX;
        }

        // Older configs sometimes join multiple syntaxes with a coloured "lub".
        // Render those variants exactly like the regular multi-line usage handler.
        value = value.replaceAll(
            "(?i)\\s+(?:(?:&#[A-Fa-f0-9]{6}|[&§][0-9A-FK-ORa-fk-or])\\s*)*lub\\s+(?:(?:&#[A-Fa-f0-9]{6}|[&§][0-9A-FK-ORa-fk-or])\\s*)*",
            "\n &8• &4"
        );
        value = value.replaceAll("\\s*\\|\\s*", "\n &8• &4");
        return USAGE_PREFIX + "\n &8• &4" + value;
    }

    public static Component success(String message) {
        String value = message == null ? "" : message.trim();
        if (hasMessagePrefix(value)) {
            return colorizeComponent(value);
        }
        return colorizeComponent(SUCCESS_PREFIX + value);
    }

    public static Component info(String message) {
        String value = message == null ? "" : message.trim();
        if (hasMessagePrefix(value)) {
            return colorizeComponent(value);
        }
        return colorizeComponent(INFO_PREFIX + value);
    }

    public static Component warning(String message) {
        String value = message == null ? "" : message.trim();
        if (hasMessagePrefix(value)) {
            return colorizeComponent(value);
        }
        return colorizeComponent(WARNING_PREFIX + value);
    }

    private static boolean hasMessagePrefix(String value) {
        return value.startsWith("&#FF0000✘ &8Ξ")
            || value.startsWith("&#00FF3B✔ &8Ξ")
            || value.startsWith("&#00E0FFℹ &8Ξ")
            || value.startsWith("&#FFB800⚠ &8Ξ");
    }

}
