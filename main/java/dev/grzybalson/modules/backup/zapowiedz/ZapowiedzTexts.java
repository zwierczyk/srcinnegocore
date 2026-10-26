package dev.grzybalson.modules.backup.zapowiedz;

import dev.grzybalson.config.configs.BackupConfig;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

final class ZapowiedzTexts {

    private static final DateTimeFormatter DATE_PL = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("pl-PL"));
    private static final DateTimeFormatter DAY_PL = DateTimeFormatter.ofPattern("EEEE", Locale.forLanguageTag("pl-PL"));

    private ZapowiedzTexts() {
    }

    static long startEpochSeconds(BackupConfig.Zapowiedz config) {
        ZoneId zone = ZoneId.of(config.getTimezone());
        LocalDate eventDate = LocalDate.parse(config.getEventDate());
        return eventDate.atTime(config.getRewardStartHour(), 0).atZone(zone).toEpochSecond();
    }

    static String discordCountdown(BackupConfig.Zapowiedz config) {
        return "<t:" + startEpochSeconds(config) + ":R>";
    }

    static String discordFullStart(BackupConfig.Zapowiedz config) {
        return "<t:" + startEpochSeconds(config) + ":F>";
    }

    static String startLabel(BackupConfig.Zapowiedz config) {
        LocalDate eventDate = LocalDate.parse(config.getEventDate());
        String day = capitalize(DAY_PL.format(eventDate));
        return "**" + day + ", " + DATE_PL.format(eventDate) + " o " + config.getRewardStartHour() + ":00**";
    }

    static String rewardsBlock(BackupConfig.Zapowiedz config) {
        StringBuilder rewards = new StringBuilder("**Nagroda za obecność na starcie:**\n");
        boolean any = false;
        for (BackupConfig.Zapowiedz.Reward reward : config.getRewards()) {
            if (reward.getCaseName().isBlank()) {
                continue;
            }
            any = true;
            rewards.append("» **").append(reward.getAmount()).append("×** klucz do skrzynki **")
                .append(capitalize(reward.getCaseName())).append("**\n");
        }
        if (!any) {
            rewards.append("» Szczegóły nagród w ogłoszeniu na Discordzie.\n");
        }
        return rewards.toString().trim();
    }

    static String rewardWindow(BackupConfig.Zapowiedz config) {
        return "**" + config.getRewardStartHour() + ":00 a " + config.getRewardEndHour() + ":00**";
    }

    private static String capitalize(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1).toLowerCase(Locale.ROOT);
    }
}
