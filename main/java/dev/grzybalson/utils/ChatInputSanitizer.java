package dev.grzybalson.utils;

import org.bukkit.entity.Player;

import java.util.regex.Pattern;

public final class ChatInputSanitizer {

    private static final String MINI_MESSAGE_TAG_NAMES =
        "#[0-9a-f]{6}|click|hover|click_event|hover_event|run_command|suggest_command|open_url|copy_to_clipboard|"
            + "change_page|insertion|font|selector|score|nbt|key|keybind|lang|translate|translatable|newline|br|"
            + "gradient|rainbow|transition|pride|color|colour|shadow|reset|bold|b|italic|i|underlined|u|"
            + "strikethrough|st|obfuscated|obf|black|dark_blue|dark_green|dark_aqua|dark_red|dark_purple|gold|"
            + "gray|grey|dark_gray|dark_grey|blue|green|aqua|red|light_purple|yellow|white";

    private static final Pattern MINI_MESSAGE_TAG_PATTERN = Pattern.compile(
        "(?i)<\\s*/?\\s*!?\\s*(?:" + MINI_MESSAGE_TAG_NAMES + ")(?:\\s*:[^>\\r\\n]*)?\\s*/?\\s*>"
    );
    private static final Pattern LEGACY_FORMAT_PATTERN = Pattern.compile(
        "(?i)(?:[&\\x{00A7}][0-9a-fk-orx])|(?:[&\\x{00A7}]x(?:[&\\x{00A7}][0-9a-f]){6})|(?:&#[0-9a-f]{6})"
    );
    private static final String BLOCKED_MESSAGE = "Nie mozesz uzywac formatowania w wiadomosciach.";

    private ChatInputSanitizer() {
    }

    public static boolean containsBlockedFormatting(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }
        return MINI_MESSAGE_TAG_PATTERN.matcher(input).find()
            || LEGACY_FORMAT_PATTERN.matcher(input).find();
    }

    public static boolean rejectIfBlocked(Player player, String input) {
        if (!containsBlockedFormatting(input)) {
            return false;
        }
        player.sendMessage(ColorUtils.error(BLOCKED_MESSAGE));
        return true;
    }
}
