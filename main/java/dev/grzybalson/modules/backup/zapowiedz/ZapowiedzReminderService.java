package dev.grzybalson.modules.backup.zapowiedz;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.configs.BackupConfig;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;

import java.awt.Color;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class ZapowiedzReminderService {

    public static final String DISPATCH_SELECT_ID = "zp:dispatch:select";
    public static final int DISPATCH_INTERVAL_SECONDS = 10;

    private static final Color BRAND = new Color(0x0EFF00);
    private static final String SMALL_CAPS = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ";
    private static final long DISPATCH_WINDOW_MINUTES = 180L;

    private final HollyCore plugin;
    private final ZapowiedzService service;
    private final ZapowiedzRepository repository;
    private final Queue<PendingReminder> queue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean fillingQueue = new AtomicBoolean(false);
    private final AtomicInteger inFlight = new AtomicInteger();
    private volatile DispatchSession activeSession;

    public ZapowiedzReminderService(HollyCore plugin, ZapowiedzService service, ZapowiedzRepository repository) {
        this.plugin = plugin;
        this.service = service;
        this.repository = repository;
    }

    public List<ReminderSlot> allSlots() {
        return buildSlots(plugin.getConfigManager().getBackupConfig().getZapowiedz());
    }

    public Optional<ReminderSlot> findSlot(String key) {
        return allSlots().stream().filter(slot -> slot.key().equals(key)).findFirst();
    }

    public boolean hasActiveDispatch() {
        return activeSession != null && !activeSession.finished();
    }

    public void startManualDispatch(JDA jda, String reminderKey, String startedByUserId) {
        if (jda == null || !service.isMongoReady()) {
            return;
        }
        Optional<ReminderSlot> slotOpt = findSlot(reminderKey);
        if (slotOpt.isEmpty()) {
            return;
        }
        if (hasActiveDispatch() || !queue.isEmpty()) {
            // Kolejkę napełnia też automatyczny tick (bez activeSession) — bez tego warunku
            // ręczna wysyłka dokładała duplikaty i ludzie dostawali to samo PV dwa razy.
            plugin.getLogger().warning("[Zapowiedz] Wysyłka już trwa — poczekaj na zakończenie.");
            return;
        }

        ReminderSlot slot = slotOpt.get();
        String statusChannelId = plugin.getConfigManager().getBackupConfig().getZapowiedz().getReminderStatusChannelId();
        TextChannel statusChannel = jda.getTextChannelById(statusChannelId);
        if (statusChannel == null) {
            plugin.getLogger().warning("[Zapowiedz] Brak kanału statusu: " + statusChannelId);
            return;
        }

        repository.findUnsentForReminder(slot.key()).whenComplete((unsent, error) -> {
            if (error != null) {
                plugin.getLogger().warning("[Zapowiedz] Nie udało się wczytać listy PV: " + error.getMessage());
                return;
            }
            if (unsent == null || unsent.isEmpty()) {
                statusChannel.sendMessageEmbeds(buildStatusEmbed(slot, 0, 0, true, startedByUserId)).queue();
                return;
            }

            DispatchSession session = new DispatchSession(
                slot,
                statusChannelId,
                unsent.size(),
                new AtomicInteger(0),
                startedByUserId
            );
            activeSession = session;

            statusChannel.sendMessageEmbeds(buildStatusEmbed(slot, 0, unsent.size(), false, startedByUserId)).queue(message -> {
                session.statusMessageId = message.getId();
                for (ZapowiedzRepository.RegistrationEntry entry : unsent) {
                    queue.offer(new PendingReminder(slot.key(), entry.discordUserId(), entry.minecraftNick(), slot.message(entry.minecraftNick())));
                }
                plugin.getLogger().info("[Zapowiedz] Ręczna wysyłka " + slot.key() + ": " + unsent.size()
                    + " osób (1 PV / " + DISPATCH_INTERVAL_SECONDS + "s).");
                tick(jda);
            }, sendError -> {
                activeSession = null;
                plugin.getLogger().warning("[Zapowiedz] Nie wysłano panelu statusu: " + sendError.getMessage());
            });
        });
    }

    public void tick(JDA jda) {
        if (jda == null || !service.isEnabled() || !service.isMongoReady()) {
            return;
        }

        PendingReminder next = queue.poll();
        if (next != null) {
            inFlight.incrementAndGet();
            dispatchOne(jda, next);
            return;
        }

        BackupConfig.Zapowiedz config = plugin.getConfigManager().getBackupConfig().getZapowiedz();
        if (!config.isRemindersEnabled()) {
            return;
        }

        ZoneId zone = ZoneId.of(config.getTimezone());
        LocalDateTime now = LocalDateTime.now(zone);
        for (ReminderSlot slot : buildSlots(config)) {
            if (!slot.isWithinDispatchWindow(now, zone)) {
                continue;
            }
            tryFillQueue(slot);
            break;
        }
    }

    private void tryFillQueue(ReminderSlot slot) {
        // inFlight: markReminderSent leci dopiero po dostarczeniu PV, więc bez tego licznika
        // ostatnia osoba z kolejki trafiała do niej drugi raz przy kolejnym ticku.
        if (!queue.isEmpty() || inFlight.get() > 0 || hasActiveDispatch() || !fillingQueue.compareAndSet(false, true)) {
            return;
        }
        repository.findUnsentForReminder(slot.key()).whenComplete((unsent, error) -> {
            fillingQueue.set(false);
            if (error != null) {
                plugin.getLogger().warning("[Zapowiedz] Nie udało się wczytać listy przypomnień: " + error.getMessage());
                return;
            }
            if (unsent == null || unsent.isEmpty()) {
                return;
            }
            for (ZapowiedzRepository.RegistrationEntry entry : unsent) {
                queue.offer(new PendingReminder(slot.key(), entry.discordUserId(), entry.minecraftNick(), slot.message(entry.minecraftNick())));
            }
            int interval = DISPATCH_INTERVAL_SECONDS;
            plugin.getLogger().info("[Zapowiedz] Kolejka przypomnień " + slot.key() + ": " + unsent.size()
                + " osób (1 PV / " + interval + "s).");
        });
    }

    private void dispatchOne(JDA jda, PendingReminder reminder) {
        Runnable afterSend = () -> {
            inFlight.decrementAndGet();
            updateSessionProgress(jda, reminder.reminderKey());
            if (queue.isEmpty()) {
                finishSession(jda);
            }
        };

        User user = jda.getUserById(reminder.discordUserId());
        if (user == null) {
            repository.markReminderSent(reminder.reminderKey(), reminder.discordUserId()).whenComplete((ignored, error) -> {
                if (error != null) {
                    plugin.getLogger().warning("[Zapowiedz] Oznaczenie przypomnienia: " + error.getMessage());
                }
                afterSend.run();
            });
            return;
        }

        user.openPrivateChannel().queue(channel -> channel.sendMessage(buildDm(reminder.body()).build()).queue(
            success -> repository.markReminderSent(reminder.reminderKey(), reminder.discordUserId()).whenComplete((ignored, error) -> {
                if (error != null) {
                    plugin.getLogger().warning("[Zapowiedz] Zapis wysłanego przypomnienia: " + error.getMessage());
                }
                afterSend.run();
            }),
            error -> {
                plugin.getLogger().warning("[Zapowiedz] Nie wysłano PV do " + reminder.discordUserId() + ": " + error.getMessage());
                repository.markReminderSent(reminder.reminderKey(), reminder.discordUserId()).whenComplete((ignored, markError) -> afterSend.run());
            }
        ), error -> {
            plugin.getLogger().warning("[Zapowiedz] Brak PV do " + reminder.discordUserId() + ": " + error.getMessage());
            repository.markReminderSent(reminder.reminderKey(), reminder.discordUserId()).whenComplete((ignored, markError) -> afterSend.run());
        });
    }

    private void updateSessionProgress(JDA jda, String reminderKey) {
        DispatchSession session = activeSession;
        if (session == null || !session.slot.key().equals(reminderKey) || session.statusMessageId == null) {
            return;
        }
        int notified = session.notifiedCount.incrementAndGet();
        TextChannel channel = jda.getTextChannelById(session.statusChannelId);
        if (channel == null) {
            return;
        }
        channel.editMessageEmbedsById(session.statusMessageId,
            buildStatusEmbed(session.slot, notified, session.totalCount, false, session.startedByUserId)
        ).queue(null, error -> plugin.getLogger().warning("[Zapowiedz] Odświeżenie panelu statusu: " + error.getMessage()));
    }

    private void finishSession(JDA jda) {
        DispatchSession session = activeSession;
        if (session == null || session.finished()) {
            return;
        }
        session.finished = true;
        activeSession = null;

        TextChannel channel = jda.getTextChannelById(session.statusChannelId);
        if (channel == null || session.statusMessageId == null) {
            return;
        }
        int notified = session.notifiedCount.get();
        channel.editMessageEmbedsById(session.statusMessageId,
            buildStatusEmbed(session.slot, notified, session.totalCount, true, session.startedByUserId)
        ).queue(null, error -> plugin.getLogger().warning("[Zapowiedz] Finalizacja panelu statusu: " + error.getMessage()));
    }

    private MessageEmbed buildStatusEmbed(ReminderSlot slot, int notified, int total, boolean done, String startedByUserId) {
        int remaining = Math.max(0, total - notified);
        String statusLine = done
            ? "**Zakończono wysyłkę PV.**"
            : total <= 0
                ? "**Brak osób do powiadomienia** — wszyscy już dostali tę wiadomość."
                : "**Trwa wysyłka PV…**";

        String description = ""
            + statusLine + "\n\n"
            + "**Przypomnienie:** " + slot.label() + "\n"
            + "**Powiadomiono:** **" + notified + "** / **" + total + "**\n"
            + "**Pozostało:** **" + remaining + "**\n"
            + "**Tempo:** 1 osoba / **" + DISPATCH_INTERVAL_SECONDS + " s**\n\n"
            + "Uruchomione przez <@" + startedByUserId + "> · tryb **SkyPvP**";

        return new EmbedBuilder()
            .setTitle("Wysyłka przypomnień SkyPvP")
            .setDescription(description)
            .setColor(done ? BRAND : new Color(0xFFB800))
            .setFooter("OGHUB.PL · zapowiedz · panel statusu")
            .build();
    }

    private MessageCreateBuilder buildDm(String body) {
        return new MessageCreateBuilder()
            .useComponentsV2()
            .setComponents(Container.of(
                TextDisplay.of(title("ZAPOWIEDZ SKYPVP")),
                Separator.createDivider(Separator.Spacing.LARGE),
                TextDisplay.of(body),
                TextDisplay.of("-# OGHUB.PL · zapowiedz · tryb SkyPvP")
            ).withAccentColor(BRAND));
    }

    static List<ReminderSlot> buildSlots(BackupConfig.Zapowiedz config) {
        LocalDate eventDate = LocalDate.parse(config.getEventDate());
        LocalDate dayBefore = eventDate.minusDays(1);
        LocalDate todayReminder = LocalDate.of(2026, 8, 22);
        int startHour = config.getRewardStartHour();
        String start = ZapowiedzTexts.startLabel(config);
        String countdown = ZapowiedzTexts.discordCountdown(config);

        List<ReminderSlot> slots = new ArrayList<>();
        slots.add(new ReminderSlot(
            todayReminder + "_reminder_wednesday_start",
            "22.08 — przypomnienie (start w środę 18:00)",
            todayReminder,
            15,
            15,
            nick -> ""
                + "Cześć! Przypominamy o zapisie na **start trybu SkyPvP** na OGHUB.PL.\n\n"
                + "**Tryb startuje:** " + start + "\n"
                + "**Odliczanie do startu:** " + countdown + "\n"
                + "**Dokładnie:** " + ZapowiedzTexts.discordFullStart(config) + "\n\n"
                + ZapowiedzTexts.rewardsBlock(config) + "\n\n"
                + "Twój zapisany nick: **`" + nick + "`**\n"
                + "Bądź online między " + ZapowiedzTexts.rewardWindow(config) + " w dniu startu, aby odebrać nagrodę."
        ));
        slots.add(new ReminderSlot(
            dayBefore + "_18_day_before",
            "25.08 o 18:00 — dzień przed startem",
            dayBefore,
            18,
            0,
            nick -> ""
                + "Cześć! **Jutro startuje tryb SkyPvP** na OGHUB.PL!\n\n"
                + "**Start:** " + start + "\n"
                + "**Odliczanie:** " + countdown + "\n\n"
                + ZapowiedzTexts.rewardsBlock(config) + "\n\n"
                + "Jesteś zapisany/a na nick **`" + nick + "`**.\n"
                + "Musisz być online **jutro między " + ZapowiedzTexts.rewardWindow(config)
                + "**, inaczej nagroda przepada."
        ));
        slots.add(new ReminderSlot(
            eventDate + "_15_start",
            "26.08 o 15:00 — 3h do startu SkyPvP",
            eventDate,
            15,
            0,
            nick -> ""
                + "Cześć! **Dziś startuje tryb SkyPvP** na OGHUB.PL!\n\n"
                + "**Start:** " + start + "\n"
                + "**Odliczanie:** " + countdown + "\n\n"
                + ZapowiedzTexts.rewardsBlock(config) + "\n\n"
                + "Twój zapisany nick: **`" + nick + "`**.\n"
                + "Do startu zostało ok. **3 godziny** — bądź online między "
                + ZapowiedzTexts.rewardWindow(config) + "."
        ));
        slots.add(new ReminderSlot(
            eventDate + "_17_start",
            "26.08 o 17:00 — 1h do startu SkyPvP",
            eventDate,
            17,
            0,
            nick -> ""
                + "Cześć! **Za godzinę startuje tryb SkyPvP** na OGHUB.PL!\n\n"
                + "**Start:** " + start + "\n"
                + "**Odliczanie:** " + countdown + "\n\n"
                + ZapowiedzTexts.rewardsBlock(config) + "\n\n"
                + "Twój zapisany nick: **`" + nick + "`**.\n"
                + "Wejdź na serwer i bądź online między " + ZapowiedzTexts.rewardWindow(config) + "."
        ));
        slots.add(new ReminderSlot(
            eventDate + "_18_start",
            "26.08 o 18:00 — START SkyPvP",
            eventDate,
            startHour,
            0,
            nick -> ""
                + "Cześć! **Właśnie startuje tryb SkyPvP** na OGHUB.PL!\n\n"
                + "**Start:** " + start + "\n\n"
                + ZapowiedzTexts.rewardsBlock(config) + "\n\n"
                + "Twój zapisany nick: **`" + nick + "`**.\n"
                + "Wejdź teraz na serwer — nagrodę odbierzesz będąc online między "
                + ZapowiedzTexts.rewardWindow(config) + "."
        ));
        return slots;
    }

    private static String title(String label) {
        return "```\n" + toSmallCaps(label) + "\n```";
    }

    private static String toSmallCaps(String input) {
        StringBuilder builder = new StringBuilder();
        for (char ch : input.toUpperCase(Locale.ROOT).toCharArray()) {
            if (ch >= 'A' && ch <= 'Z') {
                builder.append(SMALL_CAPS.charAt(ch - 'A'));
            } else {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private record PendingReminder(String reminderKey, String discordUserId, String minecraftNick, String body) {
    }

    public record ReminderSlot(
        String key,
        String label,
        LocalDate date,
        int hour,
        int minute,
        MessageBuilder messageBuilder
    ) {
        public interface MessageBuilder {
            String build(String nick);
        }

        public String message(String nick) {
            return messageBuilder.build(nick);
        }

        public boolean isWithinDispatchWindow(LocalDateTime now, ZoneId zone) {
            LocalDateTime start = date.atTime(hour, minute);
            LocalDateTime end = start.plusMinutes(DISPATCH_WINDOW_MINUTES);
            return !now.isBefore(start) && now.isBefore(end);
        }
    }

    private static final class DispatchSession {
        private final ReminderSlot slot;
        private final String statusChannelId;
        private final int totalCount;
        private final AtomicInteger notifiedCount;
        private final String startedByUserId;
        private volatile String statusMessageId;
        private volatile boolean finished;

        private DispatchSession(
            ReminderSlot slot,
            String statusChannelId,
            int totalCount,
            AtomicInteger notifiedCount,
            String startedByUserId
        ) {
            this.slot = slot;
            this.statusChannelId = statusChannelId;
            this.totalCount = totalCount;
            this.notifiedCount = notifiedCount;
            this.startedByUserId = startedByUserId;
        }

        private boolean finished() {
            return finished;
        }
    }
}
