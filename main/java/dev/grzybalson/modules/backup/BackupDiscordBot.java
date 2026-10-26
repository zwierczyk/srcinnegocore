package dev.grzybalson.modules.backup;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.configs.BackupConfig;
import dev.grzybalson.modules.backup.zapowiedz.ZapowiedzHandler;
import dev.grzybalson.modules.backup.zapowiedz.ZapowiedzReminderService;
import dev.grzybalson.modules.backup.zapowiedz.ZapowiedzService;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.mediagallery.MediaGallery;
import net.dv8tion.jda.api.components.mediagallery.MediaGalleryItem;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BackupDiscordBot extends ListenerAdapter {

    private static final Color BRAND = new Color(0x0EFF00);
    private static final Color ERROR = new Color(0xED4245);
    private static final Color NEUTRAL = new Color(0x5865F2);
    private static final String SMALL_CAPS = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ";
    private static final String FOOTER = "-# OGHUB.PL · backup · /backup pomoc";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
        .withZone(ZoneId.systemDefault());
    private static final Pattern NICK_PATTERN = Pattern.compile(
        "(?i)(?:\\*\\*Nick\\*\\*|Nick\\s*:)\\s*\\n?\\s*`?([A-Za-z0-9_]{2,16})`?"
    );

    private final HollyCore plugin;
    private final BackupManager backupManager;
    private final Map<String, List<String[]>> recentIds = new ConcurrentHashMap<>();
    private volatile JDA jda;
    private volatile boolean stopped;
    private ZapowiedzHandler zapowiedzHandler;

    public JDA getJda() {
        return jda;
    }

    public BackupDiscordBot(HollyCore plugin, BackupManager backupManager) {
        this.plugin = plugin;
        this.backupManager = backupManager;
    }

    public void start() {
        shutdown();
        stopped = false;
        String token = token();
        if (token.isBlank()) {
            plugin.getLogger().warning("Backup Discord bot: wklej discordBotToken w addons/backup/backup.yml i zrób /backup reload.");
            return;
        }

        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                ZapowiedzService zapowiedzService = backupManager.getZapowiedzService();
                ZapowiedzReminderService reminderService = backupManager.getZapowiedzReminderService();
                if (zapowiedzService != null && zapowiedzService.isEnabled()) {
                    zapowiedzHandler = new ZapowiedzHandler(plugin, zapowiedzService, reminderService);
                }
                var builder = JDABuilder.createDefault(token)
                    .enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT);
                if (zapowiedzHandler != null) {
                    builder.addEventListeners(this, zapowiedzHandler);
                } else {
                    builder.addEventListeners(this);
                }
                JDA built = builder.build();
                if (stopped) {
                    // shutdown() poszedł, gdy build był jeszcze w locie — nie zostawiaj żywej sesji.
                    built.shutdownNow();
                    return;
                }
                jda = built;
            } catch (Exception first) {
                try {
                    var fallback = JDABuilder.createDefault(token);
                    if (zapowiedzHandler != null) {
                        fallback.addEventListeners(this, zapowiedzHandler);
                    } else {
                        fallback.addEventListeners(this);
                    }
                    JDA built = fallback.build();
                    if (stopped) {
                        built.shutdownNow();
                        return;
                    }
                    jda = built;
                    plugin.getLogger().warning("Backup Discord bot wystartował bez MESSAGE_CONTENT — auto-panel z ticketu może nie odczytać nicku.");
                } catch (Exception second) {
                    plugin.getLogger().warning("Nie udało się uruchomić bota backupów Discord: " + second.getMessage());
                    jda = null;
                }
            }
        });
    }

    public void shutdown() {
        stopped = true;
        JDA current = jda;
        jda = null;
        zapowiedzHandler = null;
        recentIds.clear();
        if (current != null) {
            current.shutdownNow();
        }
    }

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        plugin.getLogger().info("Backup Discord bot online jako " + event.getJDA().getSelfUser().getName());
        String guildId = config().getDiscordGuildId();
        List<Guild> guilds = new ArrayList<>();
        if (guildId != null && !guildId.isBlank()) {
            Guild guild = event.getJDA().getGuildById(guildId.trim());
            if (guild != null) {
                guilds.add(guild);
            } else {
                plugin.getLogger().warning("Backup Discord bot nie widzi guildId=" + guildId + ". Zaproś bota na serwer.");
            }
        }
        if (guilds.isEmpty()) {
            guilds.addAll(event.getJDA().getGuilds());
        }
        for (Guild guild : guilds) {
            var commands = new java.util.ArrayList<net.dv8tion.jda.api.interactions.commands.build.SlashCommandData>();
            commands.add(commandData());
            if (zapowiedzHandler != null) {
                commands.add(zapowiedzHandler.commandData());
            }
            guild.updateCommands().addCommands(commands).queue(
                ok -> plugin.getLogger().info("Zarejestrowano komendy Discord backup/zapowiedz na " + guild.getName()),
                err -> plugin.getLogger().warning("Nie udało się zarejestrować komend Discord: " + err.getMessage())
            );
        }
    }

    private net.dv8tion.jda.api.interactions.commands.build.SlashCommandData commandData() {
        return Commands.slash("backup", "Backupy OGHUB — podgląd i nadawanie")
            .addSubcommands(
                new SubcommandData("panel", "Interaktywny panel wszystkich backupów gracza")
                    .addOptions(nickOption(true), trybOption()),
                new SubcommandData("ostatni", "Najnowszy backup gracza razem z podglądem EQ")
                    .addOptions(nickOption(true), trybOption()),
                new SubcommandData("lista", "Lista backupów gracza bez grafiki")
                    .addOptions(nickOption(true), trybOption()),
                new SubcommandData("szukaj", "Lista backupów gracza z filtrami")
                    .addOptions(
                        nickOption(true),
                        new OptionData(OptionType.INTEGER, "dni", "Tylko backupy z ostatnich X dni", false),
                        new OptionData(OptionType.INTEGER, "min_itemow", "Minimalna liczba przedmiotów", false),
                        trybOption()
                    ),
                new SubcommandData("info", "Szczegóły jednego backupu")
                    .addOptions(idOption(), trybOption()),
                new SubcommandData("podglad", "Sam obrazek ekwipunku z backupu")
                    .addOptions(idOption(), trybOption()),
                new SubcommandData("itemy", "Tekstowa lista przedmiotów z backupu")
                    .addOptions(idOption(), trybOption()),
                new SubcommandData("eq", "Nadaj graczowi ekwipunek z backupu")
                    .addOptions(idOption(), trybOption()),
                new SubcommandData("staty", "Nadaj statystyki z backupu (ELO i death)")
                    .addOptions(idOption(), trybOption()),
                new SubcommandData("wszystko", "Nadaj naraz ekwipunek i statystyki")
                    .addOptions(idOption(), trybOption()),
                new SubcommandData("oczekujace", "Backupy czekające na odbiór w grze")
                    .addOptions(nickOption(false), trybOption()),
                new SubcommandData("usun", "Usuń oczekujący backup")
                    .addOptions(
                        new OptionData(OptionType.STRING, "id", "ID oczekującego backupu", true),
                        trybOption()
                    ),
                new SubcommandData("status", "Status modułu backupów"),
                new SubcommandData("pomoc", "Spis wszystkich komend backupów")
            );
    }

    private OptionData nickOption(boolean required) {
        return new OptionData(OptionType.STRING, "nick", required
            ? "Nick gracza w Minecraft"
            : "Filtruj po nicku", required);
    }

    private OptionData trybOption() {
        return new OptionData(OptionType.STRING, "tryb", "Tryb serwera — tab pokazuje online", false)
            .setAutoComplete(true);
    }

    private OptionData idOption() {
        return new OptionData(OptionType.STRING, "id", "ID backupu, np. HB-F188E026", true)
            .setAutoComplete(true);
    }

    @Override
    public void onChannelCreate(@NotNull ChannelCreateEvent event) {
        if (!(event.getChannel() instanceof TextChannel channel)) {
            return;
        }
        if (!isBackupTicketChannel(channel)) {
            return;
        }
        org.bukkit.Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> tryAutoPanel(channel), 80L);
    }

    @Override
    public void onCommandAutoCompleteInteraction(@NotNull CommandAutoCompleteInteractionEvent event) {
        if (!event.getName().equals("backup")) {
            return;
        }
        String focused = event.getFocusedOption().getName();
        String typed = event.getFocusedOption().getValue().toLowerCase(Locale.ROOT);
        if (focused.equals("tryb")) {
            List<Command.Choice> choices = new ArrayList<>();
            for (BackupModeHub.Mode mode : onlineModes()) {
                if (choices.size() >= 25) {
                    break;
                }
                String label = mode.label();
                if (typed.isBlank()
                    || mode.prefix().toLowerCase(Locale.ROOT).contains(typed)
                    || mode.name().toLowerCase(Locale.ROOT).contains(typed)) {
                    choices.add(new Command.Choice(clip(label, 100), clip(mode.prefix(), 100)));
                }
            }
            event.replyChoices(choices).queue(null, this::ignoreUnknown);
            return;
        }
        if (!focused.equals("id")) {
            return;
        }
        List<Command.Choice> choices = new ArrayList<>();
        for (String[] entry : recentIds.getOrDefault(event.getUser().getId(), List.of())) {
            if (choices.size() >= 25) {
                break;
            }
            if (typed.isBlank() || entry[0].toLowerCase(Locale.ROOT).contains(typed)
                || entry[1].toLowerCase(Locale.ROOT).contains(typed)) {
                choices.add(new Command.Choice(clip(entry[1], 100), entry[0]));
            }
        }
        event.replyChoices(choices).queue(null, this::ignoreUnknown);
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (!event.getName().equals("backup")) {
            return;
        }
        String subcommand = event.getSubcommandName() == null ? "pomoc" : event.getSubcommandName();
        if (subcommand.equals("pomoc")) {
            event.reply(help().build()).setEphemeral(true).queue(null, this::ignoreUnknown);
            return;
        }
        if (!isStaff(event.getMember())) {
            event.reply(notice("BRAK DOSTEPU", "» Komendy backupów są tylko dla administracji.", ERROR).build())
                .setEphemeral(true)
                .queue(null, this::ignoreUnknown);
            return;
        }

        event.deferReply(true).queue(hook -> guard(hook, () -> {
            switch (subcommand) {
                case "panel" -> handleNickCommand(hook, event, "panel");
                case "ostatni" -> handleNickCommand(hook, event, "ostatni");
                case "lista" -> handleList(hook, event, nick(event), null, 0);
                case "szukaj" -> handleList(hook, event, nick(event), integer(event, "dni"), integer(event, "min_itemow"));
                case "info" -> handleInfo(hook, event);
                case "podglad" -> handlePreview(hook, event);
                case "itemy" -> handleItems(hook, event);
                case "eq" -> handleGrant(hook, event, true, false);
                case "staty" -> handleGrant(hook, event, false, true);
                case "wszystko" -> handleGrant(hook, event, true, true);
                case "oczekujace" -> handlePending(hook, event);
                case "usun" -> handleDelete(hook, event);
                case "status" -> reply(hook, status());
                default -> reply(hook, help());
            }
        }), this::ignoreUnknown);
    }

    @Override
    public void onStringSelectInteraction(@NotNull StringSelectInteractionEvent event) {
        String[] parts = hbParts(event.getComponentId());
        if (parts.length == 0) {
            return;
        }
        if (!isStaff(event.getMember())) {
            replyDenied(event);
            return;
        }
        String action = parts[0];
        String selected = event.getValues().isEmpty() ? null : event.getValues().get(0);
        if (action.equals("mode") && parts.length >= 3) {
            String next = parts[1];
            String nick = parts[2];
            event.deferEdit().queue(hook -> guard(hook, () -> {
                // findMode() to blokujące zapytanie do Mongo — dopiero po deferEdit,
                // żeby nie zablokować wątku gatewaya przed potwierdzeniem interakcji.
                BackupModeHub.Mode mode = findMode(selected);
                if (mode == null) {
                    reply(hook, unknownMode(selected));
                    return;
                }
                if (next.equals("ostatni")) {
                    handleLatest(hook, mode, nick);
                } else {
                    reply(hook, panel(mode, nick, null));
                }
            }), this::ignoreUnknown);
            return;
        }
        if (action.equals("pick") && parts.length >= 3) {
            String nick = parts[2];
            event.deferEdit().queue(hook -> guard(hook, () -> {
                BackupModeHub.Mode mode = findMode(parts[1]);
                if (mode == null) {
                    reply(hook, unknownMode(parts[1]));
                    return;
                }
                reply(hook, panel(mode, nick, selected));
            }), this::ignoreUnknown);
            return;
        }
        if (action.equals("pick") && parts.length == 2) {
            String nick = parts[1];
            event.deferEdit().queue(hook -> guard(hook, () ->
                reply(hook, panel(localMode(), nick, selected))
            ), this::ignoreUnknown);
        }
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        String[] parts = hbParts(event.getComponentId());
        if (parts.length == 0) {
            return;
        }
        if (!isStaff(event.getMember())) {
            replyDenied(event);
            return;
        }

        String action = parts[0];
        if (action.equals("panel")) {
            event.deferEdit().queue(hook -> guard(hook, () -> {
                if (parts.length >= 3) {
                    BackupModeHub.Mode mode = findMode(parts[1]);
                    if (mode == null) {
                        reply(hook, unknownMode(parts[1]));
                        return;
                    }
                    reply(hook, panel(mode, parts[2], parts.length > 3 ? parts[3] : null));
                    return;
                }
                String argument = parts.length > 1 ? parts[1] : "";
                String[] split = argument.split("\\|", 2);
                reply(hook, panel(localMode(), split[0], split.length > 1 ? split[1] : null));
            }), this::ignoreUnknown);
            return;
        }

        boolean ephemeral = event.getMessage().isEphemeral();
        boolean eq = action.equals("eq") || action.equals("all");
        boolean stats = action.equals("stats") || action.equals("all");
        if (!eq && !stats) {
            return;
        }
        String modePrefix = parts.length >= 3 ? parts[1] : localMode().prefix();
        String deathId = parts.length >= 3 ? parts[2] : (parts.length > 1 ? parts[1] : "");
        event.deferReply(ephemeral).queue(hook -> guard(hook, () ->
            reply(hook, grant(modePrefix, deathId, eq, stats, event.getUser().getAsMention()))
        ), this::ignoreUnknown);
    }

    private void handleNickCommand(InteractionHook hook, SlashCommandInteractionEvent event, String action)
        throws Exception {
        String nick = nick(event);
        BackupModeHub.Mode mode = modeOf(event);
        if (mode == null) {
            reply(hook, string(event, "tryb").isBlank() ? pickMode(action, nick) : unknownMode(string(event, "tryb")));
            return;
        }
        if (action.equals("ostatni")) {
            handleLatest(hook, mode, nick);
            return;
        }
        remember(hook, backupManager.findDeathsByNick(nick, mode.prefix()).join(), mode);
        reply(hook, panel(mode, nick, null));
    }

    private void handleLatest(InteractionHook hook, BackupModeHub.Mode mode, String nick) throws Exception {
        List<BackupDataManager.DeathRecord> deaths = backupManager.findDeathsByNick(nick, mode.prefix()).join();
        remember(hook, deaths, mode);
        if (deaths.isEmpty()) {
            reply(hook, notice("BRAK BACKUPOW",
                "» Gracz **" + nick + "** nie ma backupów z przedmiotami na trybie **" + mode.name() + "**.", ERROR));
            return;
        }
        reply(hook, detail(deaths.get(0), "NAJNOWSZY BACKUP", mode));
    }

    private void handleList(InteractionHook hook, SlashCommandInteractionEvent event, String nick, Integer days, Integer minItems)
        throws Exception {
        BackupModeHub.Mode mode = modeOf(event);
        if (mode == null) {
            reply(hook, string(event, "tryb").isBlank() ? pickMode("panel", nick) : unknownMode(string(event, "tryb")));
            return;
        }
        List<BackupDataManager.DeathRecord> deaths = backupManager.findDeathsByNick(nick, mode.prefix()).join();
        List<BackupDataManager.DeathRecord> filtered = new ArrayList<>();
        for (BackupDataManager.DeathRecord death : deaths) {
            if (days != null && days > 0 && death.createdAt() != null
                && death.createdAt().isBefore(Instant.now().minus(Duration.ofDays(days)))) {
                continue;
            }
            if (minItems != null && minItems > 0
                && BackupItemText.countItems(death.inventory(), death.armor()) < minItems) {
                continue;
            }
            filtered.add(death);
        }
        remember(hook, filtered, mode);

        if (filtered.isEmpty()) {
            reply(hook, notice("BRAK WYNIKOW",
                "» Nic nie pasuje do filtrów dla **" + nick + "** na **" + mode.name() + "**.", ERROR));
            return;
        }

        StringBuilder body = new StringBuilder("**Gracz:** `" + nick + "` · tryb: **" + mode.name()
            + "** · znaleziono **" + filtered.size() + "**\n\n");
        int shown = Math.min(15, filtered.size());
        for (int i = 0; i < shown; i++) {
            BackupDataManager.DeathRecord death = filtered.get(i);
            body.append("» `").append(death.publicId()).append("` · ")
                .append(BackupItemText.countItems(death.inventory(), death.armor())).append(" itemów · ")
                .append(TIME.format(death.createdAt())).append(" · **")
                .append(value(death.killerName())).append("**\n");
        }
        if (filtered.size() > shown) {
            body.append("\n-# ...i jeszcze ").append(filtered.size() - shown).append(". Zawęź filtry albo użyj `/backup panel`.");
        }

        reply(hook, container(BRAND, "OGHUB.PL × LISTA", body.toString(), List.of(
            ActionRow.of(Button.primary(cid("panel", mode.prefix(), nick), "Otwórz panel"))
        )));
    }

    private void handleInfo(InteractionHook hook, SlashCommandInteractionEvent event) throws Exception {
        ResolvedDeath resolved = deathOn(event);
        if (resolved.death() == null) {
            reply(hook, resolved.unknownTryb() ? unknownMode(string(event, "tryb")) : unknownBackup(event));
            return;
        }
        remember(hook, List.of(resolved.death()), resolved.mode());
        reply(hook, detail(resolved.death(), "SZCZEGOLY BACKUPU", resolved.mode()));
    }

    private void handlePreview(InteractionHook hook, SlashCommandInteractionEvent event) throws Exception {
        ResolvedDeath resolved = deathOn(event);
        if (resolved.death() == null) {
            reply(hook, resolved.unknownTryb() ? unknownMode(string(event, "tryb")) : unknownBackup(event));
            return;
        }
        BackupDataManager.DeathRecord death = resolved.death();
        FileUpload file = image(death);
        reply(hook, message(Container.of(
            TextDisplay.of(title("PODGLAD EQ")),
            Separator.createDivider(Separator.Spacing.LARGE),
            TextDisplay.of("» Tryb: **" + resolved.mode().name() + "**\n» Gracz: **"
                + death.playerName() + "** · `" + death.publicId() + "`"),
            MediaGallery.of(MediaGalleryItem.fromFile(file)),
            TextDisplay.of(FOOTER)
        ).withAccentColor(BRAND), file));
    }

    private void handleItems(InteractionHook hook, SlashCommandInteractionEvent event) throws Exception {
        ResolvedDeath resolved = deathOn(event);
        if (resolved.death() == null) {
            reply(hook, resolved.unknownTryb() ? unknownMode(string(event, "tryb")) : unknownBackup(event));
            return;
        }
        BackupDataManager.DeathRecord death = resolved.death();
        List<BackupItemText.ItemSummary> summaries = BackupItemText.summarize(death.inventory(), death.armor());
        StringBuilder body = new StringBuilder("**" + death.playerName() + "** · `" + death.publicId() + "` · tryb **"
            + resolved.mode().name() + "** · **" + summaries.size() + "** pozycji\n\n");
        int shown = Math.min(28, summaries.size());
        for (int i = 0; i < shown; i++) {
            BackupItemText.ItemSummary summary = summaries.get(i);
            body.append("» ").append(summary.name());
            if (summary.amount() > 1) {
                body.append(" **x").append(summary.amount()).append("**");
            }
            if (!summary.tags().isEmpty()) {
                body.append(" · `").append(String.join(" ", summary.tags())).append("`");
            }
            body.append('\n');
        }
        if (summaries.size() > shown) {
            body.append("\n-# ...i jeszcze ").append(summaries.size() - shown).append(" pozycji.");
        }
        reply(hook, container(BRAND, "OGHUB.PL × ITEMY", body.toString(), grantRow(resolved.mode(), death.id())));
    }

    private void handleGrant(InteractionHook hook, SlashCommandInteractionEvent event, boolean eq, boolean stats)
        throws Exception {
        ResolvedDeath resolved = deathOn(event);
        if (resolved.death() == null) {
            reply(hook, resolved.unknownTryb() ? unknownMode(string(event, "tryb")) : unknownBackup(event));
            return;
        }
        // ObjectId zamiast publicId — publicId ma tylko 8 hex i nie jest unikalny w indeksie,
        // wiec kolizja potrafila nadac backup zupelnie innego gracza.
        reply(hook, grant(resolved.mode().prefix(), resolved.death().id(), eq, stats, event.getUser().getAsMention()));
    }

    private void handlePending(InteractionHook hook, SlashCommandInteractionEvent event) {
        String filter = event.getOption("nick") == null ? null : string(event, "nick").toLowerCase(Locale.ROOT);
        String tryb = string(event, "tryb");
        List<BackupModeHub.Mode> modes = tryb.isBlank() ? onlineModes() : List.of();
        BackupModeHub.Mode single = modeOf(event);
        if (!tryb.isBlank() && single == null) {
            reply(hook, unknownMode(tryb));
            return;
        }
        if (single != null) {
            modes = List.of(single);
        }
        StringBuilder body = new StringBuilder();
        int shown = 0;
        for (BackupModeHub.Mode mode : modes) {
            List<BackupDataManager.PendingBackup> pending = backupManager.getAllPendingBackups(mode.prefix()).join();
            for (BackupDataManager.PendingBackup backup : pending) {
                if (filter != null && !backup.playerName().toLowerCase(Locale.ROOT).contains(filter)) {
                    continue;
                }
                if (shown++ >= 15) {
                    continue;
                }
                body.append("» **").append(backup.playerName()).append("** · `").append(backup.id()).append("` · **")
                    .append(mode.name()).append("**\n")
                    .append("-# status: ").append(backup.status())
                    .append(" · statystyki: ").append(backup.statsRestored() ? "nadane" : "nie")
                    .append(" · ").append(backup.createdAt() == null ? "—" : TIME.format(backup.createdAt()))
                    .append('\n');
            }
        }
        if (body.isEmpty()) {
            reply(hook, notice("OCZEKUJACE", "» Nie ma backupów czekających na odbiór.", NEUTRAL));
            return;
        }
        reply(hook, container(NEUTRAL, "OGHUB.PL × OCZEKUJACE", body.toString(), List.of()));
    }

    private void handleDelete(InteractionHook hook, SlashCommandInteractionEvent event) {
        String id = string(event, "id");
        BackupModeHub.Mode mode = modeOf(event);
        if (mode == null && !string(event, "tryb").isBlank()) {
            reply(hook, unknownMode(string(event, "tryb")));
            return;
        }
        if (mode == null) {
            mode = localMode();
            for (BackupModeHub.Mode candidate : onlineModes()) {
                BackupDataManager.PendingBackup found = backupManager.getPendingBackupById(id, candidate.prefix()).join();
                if (found != null) {
                    mode = candidate;
                    break;
                }
            }
        }
        BackupDataManager.PendingBackup backup = backupManager.getPendingBackupById(id, mode.prefix()).join();
        if (backup == null) {
            reply(hook, notice("NIE ZNALEZIONO", "» Brak oczekującego backupu o ID `" + id + "`.", ERROR));
            return;
        }
        backupManager.deleteBackup(id, mode.prefix()).join();
        reply(hook, notice(
            "USUNIETO",
            "» Usunięto oczekujący backup `" + id + "` gracza **" + backup.playerName()
                + "** na trybie **" + mode.name() + "**.",
            BRAND
        ));
    }

    private MessageCreateBuilder grant(String modePrefix, String id, boolean eq, boolean stats, String admin) {
        BackupModeHub.Mode mode = findMode(modePrefix);
        if (mode == null) {
            return unknownMode(modePrefix);
        }
        BackupDataManager.DeathRecord death = backupManager.getDeathById(id, mode.prefix()).join();
        if (death == null) {
            return notice("NIE ZNALEZIONO", "» Nie ma backupu o ID `" + id + "` na trybie **" + mode.name() + "**.", ERROR);
        }
        String result = backupManager.grantOnMode(mode.prefix(), death.id(), eq, stats, admin).join();
        if (result == null || !result.equals("ok")) {
            return notice("BLAD NADANIA", "» " + value(result), ERROR);
        }
        StringBuilder body = new StringBuilder("**" + death.playerName() + "** · `" + death.publicId()
            + "` · tryb **" + mode.name() + "**\n\n");
        if (eq) {
            body.append("» Nadano **ekwipunek** — gracz odbiera przez `/odbierzbackup` na pustym EQ na tym trybie.\n");
        }
        if (stats) {
            body.append("» Nadano **statystyki** — ELO **").append(death.preDeathElo()).append("**, −1 death.\n");
        }
        body.append("» Nadał: ").append(admin);
        return container(BRAND, "OGHUB.PL × NADANO", body.toString(), List.of());
    }

    private MessageCreateBuilder panel(BackupModeHub.Mode mode, String nick, String selectedId) throws Exception {
        List<BackupDataManager.DeathRecord> deaths = backupManager.findDeathsByNick(nick, mode.prefix()).join();
        if (deaths.isEmpty()) {
            return notice("BRAK BACKUPOW",
                "» Gracz **" + nick + "** nie ma backupów z przedmiotami na trybie **" + mode.name() + "**.", ERROR);
        }

        BackupDataManager.DeathRecord selected = deaths.stream()
            .filter(death -> death.publicId().equalsIgnoreCase(selectedId))
            .findFirst()
            .orElse(deaths.get(0));

        StringSelectMenu.Builder select = StringSelectMenu.create(cid("pick", mode.prefix(), nick))
            .setPlaceholder("Wybierz backup z listy...")
            .setMinValues(1)
            .setMaxValues(1);
        for (int i = 0; i < Math.min(25, deaths.size()); i++) {
            BackupDataManager.DeathRecord death = deaths.get(i);
            int items = BackupItemText.countItems(death.inventory(), death.armor());
            select.addOptions(SelectOption.of(clip(death.publicId() + " · " + items + " itemów", 100), death.publicId())
                .withDescription(clip(TIME.format(death.createdAt()) + " · " + value(death.killerName()), 100))
                .withDefault(death.publicId().equals(selected.publicId())));
        }

        FileUpload file = image(selected);
        Container container = Container.of(
            TextDisplay.of(title("OGHUB.PL × BACKUP")),
            Separator.createDivider(Separator.Spacing.LARGE),
            TextDisplay.of("**Gracz:** `" + nick + "` · tryb: **" + mode.name() + "** · backupów: **"
                + deaths.size() + "**\n" + summary(selected)),
            MediaGallery.of(MediaGalleryItem.fromFile(file)),
            ActionRow.of(select.build()),
            ActionRow.of(
                Button.success(cid("eq", mode.prefix(), selected.id()), "Nadaj EQ"),
                Button.primary(cid("stats", mode.prefix(), selected.id()), "Nadaj staty"),
                Button.secondary(cid("all", mode.prefix(), selected.id()), "Nadaj wszystko"),
                Button.secondary(cid("panel", mode.prefix(), nick, selected.publicId()), "Odśwież")
            ),
            TextDisplay.of(FOOTER)
        ).withAccentColor(BRAND);
        return message(container, file);
    }

    private MessageCreateBuilder detail(BackupDataManager.DeathRecord death, String headline, BackupModeHub.Mode mode)
        throws Exception {
        FileUpload file = image(death);
        Container container = Container.of(
            TextDisplay.of(title(headline)),
            Separator.createDivider(Separator.Spacing.LARGE),
            TextDisplay.of("**Gracz:** `" + death.playerName() + "` · tryb: **" + mode.name() + "**\n" + summary(death)),
            MediaGallery.of(MediaGalleryItem.fromFile(file)),
            ActionRow.of(
                Button.success(cid("eq", mode.prefix(), death.id()), "Nadaj EQ"),
                Button.primary(cid("stats", mode.prefix(), death.id()), "Nadaj staty"),
                Button.secondary(cid("all", mode.prefix(), death.id()), "Nadaj wszystko"),
                Button.secondary(cid("panel", mode.prefix(), death.playerName(), death.publicId()), "Wszystkie backupy")
            ),
            TextDisplay.of(FOOTER)
        ).withAccentColor(BRAND);
        return message(container, file);
    }

    private String summary(BackupDataManager.DeathRecord death) {
        return "» Backup: `" + death.publicId() + "`\n"
            + "» Data: " + TIME.format(death.createdAt()) + "\n"
            + "» Zabójca: **" + value(death.killerName()) + "**\n"
            + "» Przyczyna: **" + value(death.deathCause()) + "**\n"
            + "» ELO: **" + death.preDeathElo() + "** · itemy: **"
            + BackupItemText.countItems(death.inventory(), death.armor()) + "**\n"
            + "» Lokacja: " + value(death.location());
    }

    private MessageCreateBuilder status() {
        BackupConfig config = config();
        StringBuilder body = new StringBuilder()
            .append("» Bot: **online** jako `").append(jda == null ? "-" : jda.getSelfUser().getName()).append("`\n")
            .append("» Ten proces: **").append(localMode().name()).append("** · `").append(localMode().prefix()).append("`\n")
            .append("» Role administracji: **").append(size(config.getDiscordStaffRoleIds())).append("**\n")
            .append("» Kategorie ticketów: **").append(size(config.getDiscordTicketCategoryIds())).append("**\n")
            .append("» Serwery bota: **").append(jda == null ? 0 : jda.getGuilds().size()).append("**\n\n")
            .append("**Tryby online**\n");
        List<BackupModeHub.Mode> modes = onlineModes();
        if (modes.isEmpty()) {
            body.append("» Brak zarejestrowanych trybów.");
        } else {
            for (BackupModeHub.Mode mode : modes) {
                int pending = backupManager.getAllPendingBackups(mode.prefix()).join().size();
                body.append("» **").append(mode.name()).append("** · `").append(mode.prefix())
                    .append("` · oczekujące: **").append(pending).append("**\n");
            }
        }
        return container(NEUTRAL, "OGHUB.PL × STATUS", body.toString(), List.of());
    }

    private MessageCreateBuilder help() {
        String body = "**Tryb**\n"
            + "» W `/backup` jest pole **tryb** — tab pokazuje online serwery (db-prefix).\n"
            + "» Po wyborze trybu bot czyta i nadaje backupy z tej bazy.\n\n"
            + "**Podgląd**\n"
            + "» `/backup panel nick tryb` — pełny panel z listą i grafiką EQ\n"
            + "» `/backup ostatni nick tryb` — najnowsza śmierć gracza\n"
            + "» `/backup lista nick tryb` — same ID bez grafiki\n"
            + "» `/backup szukaj nick dni min_itemow tryb` — lista z filtrami\n"
            + "» `/backup info id tryb` — szczegóły jednego backupu\n"
            + "» `/backup podglad id tryb` — sam obrazek ekwipunku\n"
            + "» `/backup itemy id tryb` — spis przedmiotów tekstem\n\n"
            + "**Nadawanie**\n"
            + "» `/backup eq id tryb` — tylko ekwipunek\n"
            + "» `/backup staty id tryb` — tylko ELO i death\n"
            + "» `/backup wszystko id tryb` — EQ i statystyki naraz\n\n"
            + "**Zarządzanie**\n"
            + "» `/backup oczekujace nick tryb` — kto czeka na odbiór\n"
            + "» `/backup usun id tryb` — usuń oczekujący backup\n"
            + "» `/backup status` — stan modułu i lista trybów\n"
            + "» `/backup pomoc` — ta lista\n\n"
            + "-# Wszystkie odpowiedzi widzisz tylko Ty. Pole `id` podpowiada ostatnio przeglądane backupy.";
        return container(NEUTRAL, "OGHUB.PL × POMOC", body, List.of());
    }

    private void tryAutoPanel(TextChannel channel) {
        if (jda == null) {
            return;
        }
        try {
            List<Message> history = channel.getHistory().retrievePast(15).complete();
            String nick = null;
            for (Message message : history) {
                nick = extractNick(messageText(message));
                if (nick != null) {
                    break;
                }
            }
            if (nick == null || nick.isBlank()) {
                channel.sendMessage(notice(
                    "OGHUB.PL × BACKUP",
                    "» Nie znalazłem nicku w tickecie. Użyj `/backup panel nick:NickGracza`.",
                    NEUTRAL
                ).build()).queue(null, this::ignoreUnknown);
                return;
            }
            List<BackupModeHub.Mode> modes = onlineModes();
            if (modes.size() > 1) {
                channel.sendMessage(pickMode("panel", nick).build()).queue(null, this::ignoreUnknown);
                return;
            }
            BackupModeHub.Mode mode = modes.isEmpty() ? localMode() : modes.get(0);
            channel.sendMessage(panel(mode, nick, null).build()).queue(null, this::ignoreUnknown);
        } catch (Exception exception) {
            plugin.getLogger().warning("Backup auto-panel: " + exception.getMessage());
        }
    }

    private FileUpload image(BackupDataManager.DeathRecord death) throws Exception {
        return FileUpload.fromData(
            BackupInventoryImageRenderer.renderPng(death),
            "backup-" + death.publicId() + ".png"
        );
    }

    private List<ActionRow> grantRow(BackupModeHub.Mode mode, String deathId) {
        return List.of(ActionRow.of(
            Button.success(cid("eq", mode.prefix(), deathId), "Nadaj EQ"),
            Button.primary(cid("stats", mode.prefix(), deathId), "Nadaj staty"),
            Button.secondary(cid("all", mode.prefix(), deathId), "Nadaj wszystko")
        ));
    }

    private MessageCreateBuilder container(Color color, String headline, String body, List<ActionRow> rows) {
        List<ContainerChildComponent> children = new ArrayList<>();
        children.add(TextDisplay.of(title(headline)));
        children.add(Separator.createDivider(Separator.Spacing.LARGE));
        children.add(TextDisplay.of(clip(body, 3800)));
        if (!rows.isEmpty()) {
            children.add(Separator.createDivider(Separator.Spacing.SMALL));
            children.addAll(rows);
        }
        children.add(TextDisplay.of(FOOTER));
        return message(Container.of(children).withAccentColor(color), null);
    }

    private MessageCreateBuilder notice(String headline, String body, Color color) {
        return container(color, headline, body, List.of());
    }

    private MessageCreateBuilder message(Container container, FileUpload file) {
        MessageCreateBuilder builder = new MessageCreateBuilder()
            .useComponentsV2()
            .setComponents(container);
        if (file != null) {
            builder.setFiles(file);
        }
        return builder;
    }

    private MessageCreateBuilder unknownBackup(SlashCommandInteractionEvent event) {
        return notice("NIE ZNALEZIONO", "» Brak backupu o ID `" + string(event, "id") + "`.", ERROR);
    }

    private ResolvedDeath deathOn(SlashCommandInteractionEvent event) {
        String id = string(event, "id");
        String tryb = string(event, "tryb");
        List<BackupModeHub.Mode> modes = onlineModes();
        BackupModeHub.Mode resolved = backupManager.modeHub().resolve(tryb, modes);
        if (resolved != null) {
            return new ResolvedDeath(resolved, backupManager.getDeathById(id, resolved.prefix()).join(), false);
        }
        if (!tryb.isBlank()) {
            return new ResolvedDeath(null, null, true);
        }
        for (BackupModeHub.Mode mode : modes) {
            BackupDataManager.DeathRecord death = backupManager.getDeathById(id, mode.prefix()).join();
            if (death != null) {
                return new ResolvedDeath(mode, death, false);
            }
        }
        return new ResolvedDeath(modes.size() == 1 ? modes.get(0) : localMode(), null, false);
    }

    private void remember(InteractionHook hook, List<BackupDataManager.DeathRecord> deaths, BackupModeHub.Mode mode) {
        List<String[]> entries = new ArrayList<>();
        for (BackupDataManager.DeathRecord death : deaths) {
            if (entries.size() >= 25) {
                break;
            }
            entries.add(new String[]{
                death.publicId(),
                death.publicId() + " · " + death.playerName() + " · " + mode.name() + " · " + TIME.format(death.createdAt()),
                mode.prefix()
            });
        }
        recentIds.put(hook.getInteraction().getUser().getId(), entries);
    }

    private void reply(InteractionHook hook, MessageCreateBuilder builder) {
        hook.editOriginal(toEdit(builder)).queue(null, this::ignoreUnknown);
    }

    private void replyDenied(net.dv8tion.jda.api.interactions.callbacks.IReplyCallback event) {
        event.reply(notice("BRAK DOSTEPU", "» To narzędzie jest tylko dla administracji.", ERROR).build())
            .setEphemeral(true)
            .queue(null, this::ignoreUnknown);
    }

    private void guard(InteractionHook hook, ThrowingRunnable action) {
        try {
            action.run();
        } catch (Exception exception) {
            plugin.getLogger().warning("Backup Discord: " + exception.getMessage());
            reply(hook, notice("BLAD", "» " + value(exception.getMessage()), ERROR));
        }
    }

    private String nick(SlashCommandInteractionEvent event) {
        return string(event, "nick");
    }

    private List<BackupModeHub.Mode> onlineModes() {
        return backupManager.modeHub().onlineModes().join();
    }

    private BackupModeHub.Mode localMode() {
        return backupManager.modeHub().local();
    }

    private BackupModeHub.Mode modeOf(SlashCommandInteractionEvent event) {
        return backupManager.modeHub().resolve(string(event, "tryb"), onlineModes());
    }

    private BackupModeHub.Mode findMode(String raw) {
        return backupManager.modeHub().resolve(raw, onlineModes());
    }

    private MessageCreateBuilder pickMode(String action, String nick) {
        List<BackupModeHub.Mode> modes = onlineModes();
        if (modes.isEmpty()) {
            return notice("BRAK TRYBOW", "» Żaden tryb nie jest teraz online w rejestrze Mongo.", ERROR);
        }
        StringSelectMenu.Builder select = StringSelectMenu.create(cid("mode", action, nick))
            .setPlaceholder("Wybierz tryb...")
            .setMinValues(1)
            .setMaxValues(1);
        int added = 0;
        for (BackupModeHub.Mode mode : modes) {
            if (added++ >= 25) {
                break;
            }
            select.addOptions(SelectOption.of(clip(mode.name(), 100), clip(mode.prefix(), 100))
                .withDescription(clip(mode.prefix(), 100)));
        }
        return container(NEUTRAL, "WYBIERZ TRYB",
            "» Ten bot obsługuje kilka trybów. Wskaż, z którego czytać backupy gracza **" + nick + "**.\n"
                + "» Możesz też od razu podać `tryb` w komendzie — tab uzupełnia listę.",
            List.of(ActionRow.of(select.build())));
    }

    private MessageCreateBuilder unknownMode(String raw) {
        StringBuilder body = new StringBuilder("» Nie znam trybu `").append(value(raw)).append("`.\n\n**Online:**\n");
        for (BackupModeHub.Mode mode : onlineModes()) {
            body.append("» **").append(mode.name()).append("** · `").append(mode.prefix()).append("`\n");
        }
        return notice("NIEZNANY TRYB", body.toString(), ERROR);
    }

    private static String cid(String... parts) {
        return "hb:" + String.join("|", parts);
    }

    private static String[] hbParts(String componentId) {
        if (componentId == null || !componentId.startsWith("hb:")) {
            return new String[0];
        }
        String raw = componentId.substring(3);
        int colon = componentId.indexOf(':', 3);
        int pipe = componentId.indexOf('|');
        boolean modern = pipe > 0 && (colon < 0 || pipe < colon);
        if (modern) {
            return raw.split("\\|", -1);
        }
        String[] split = componentId.split(":", 3);
        if (split.length < 2) {
            return new String[0];
        }
        String[] parts = new String[split.length - 1];
        System.arraycopy(split, 1, parts, 0, parts.length);
        return parts;
    }

    private record ResolvedDeath(BackupModeHub.Mode mode, BackupDataManager.DeathRecord death, boolean unknownTryb) {
    }

    private String string(SlashCommandInteractionEvent event, String name) {
        OptionMapping option = event.getOption(name);
        return option == null ? "" : option.getAsString().trim();
    }

    private Integer integer(SlashCommandInteractionEvent event, String name) {
        OptionMapping option = event.getOption(name);
        return option == null ? null : option.getAsInt();
    }

    private boolean isBackupTicketChannel(TextChannel channel) {
        String name = channel.getName() == null ? "" : channel.getName().toLowerCase(Locale.ROOT);
        if (name.startsWith("backup-") || name.startsWith("chce-backupa-")) {
            return true;
        }
        List<String> categories = config().getDiscordTicketCategoryIds();
        if (categories == null || categories.isEmpty() || channel.getParentCategoryId() == null) {
            return false;
        }
        return categories.contains(channel.getParentCategoryId());
    }

    private boolean isStaff(Member member) {
        if (member == null) {
            return false;
        }
        if (member.hasPermission(Permission.ADMINISTRATOR)) {
            return true;
        }
        List<String> roles = config().getDiscordStaffRoleIds();
        if (roles == null || roles.isEmpty()) {
            return member.hasPermission(Permission.MANAGE_SERVER);
        }
        return member.getRoles().stream().anyMatch(role -> roles.contains(role.getId()));
    }

    private String extractNick(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = NICK_PATTERN.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String messageText(Message message) {
        StringBuilder builder = new StringBuilder();
        if (message.getContentRaw() != null) {
            builder.append(message.getContentRaw()).append('\n');
        }
        for (MessageEmbed embed : message.getEmbeds()) {
            builder.append(embedToText(embed)).append('\n');
        }
        try {
            for (TextDisplay display : message.getComponentTree().findAll(TextDisplay.class)) {
                if (display.getContent() != null) {
                    builder.append(display.getContent()).append('\n');
                }
            }
        } catch (Exception ignored) {
        }
        return builder.toString();
    }

    private String embedToText(MessageEmbed embed) {
        StringBuilder builder = new StringBuilder();
        if (embed.getTitle() != null) {
            builder.append(embed.getTitle()).append('\n');
        }
        if (embed.getDescription() != null) {
            builder.append(embed.getDescription()).append('\n');
        }
        for (MessageEmbed.Field field : embed.getFields()) {
            builder.append(field.getName()).append('\n').append(field.getValue()).append('\n');
        }
        return builder.toString();
    }

    private static String title(String label) {
        String formatted = toSmallCaps(label);
        int pad = Math.max(0, 48 - formatted.codePointCount(0, formatted.length()));
        int left = pad / 2;
        return "```\n" + " ".repeat(left) + formatted + " ".repeat(pad - left) + "\n```";
    }

    private static String toSmallCaps(String text) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            i += Character.charCount(codePoint);
            char character = Character.toLowerCase((char) codePoint);
            character = switch (character) {
                case 'ą' -> 'a';
                case 'ć' -> 'c';
                case 'ę' -> 'e';
                case 'ł' -> 'l';
                case 'ń' -> 'n';
                case 'ó' -> 'o';
                case 'ś' -> 's';
                case 'ź', 'ż' -> 'z';
                default -> character;
            };
            int index = "abcdefghijklmnopqrstuvwxyz".indexOf(character);
            if (index >= 0) {
                out.append(SMALL_CAPS.charAt(index));
            } else {
                out.appendCodePoint(codePoint);
            }
        }
        return out.toString();
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String value(String raw) {
        return raw == null || raw.isBlank() ? "Brak" : raw;
    }

    private static int size(List<String> list) {
        return list == null ? 0 : list.size();
    }

    private MessageEditData toEdit(MessageCreateBuilder builder) {
        return MessageEditBuilder.fromCreateData(builder.build()).useComponentsV2().build();
    }

    private BackupConfig config() {
        return plugin.getConfigManager().getBackupConfig();
    }

    private String token() {
        String token = config().getDiscordBotToken();
        return token == null ? "" : token.trim();
    }

    private void ignoreUnknown(Throwable throwable) {
        if (throwable == null) {
            return;
        }
        String message = throwable.getMessage() == null ? "" : throwable.getMessage();
        if (message.contains("10062") || message.contains("Unknown interaction")) {
            return;
        }
        plugin.getLogger().warning("Backup Discord: " + message);
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
