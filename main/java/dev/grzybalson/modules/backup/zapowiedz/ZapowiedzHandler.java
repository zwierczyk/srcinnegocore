package dev.grzybalson.modules.backup.zapowiedz;

import dev.grzybalson.HollyCore;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.modals.Modal;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.util.List;

public final class ZapowiedzHandler extends ListenerAdapter {

    private static final Color ERROR = new Color(0xED4245);

    private final HollyCore plugin;
    private final ZapowiedzService service;
    private final ZapowiedzReminderService reminderService;

    public ZapowiedzHandler(HollyCore plugin, ZapowiedzService service, ZapowiedzReminderService reminderService) {
        this.plugin = plugin;
        this.service = service;
        this.reminderService = reminderService;
    }

    public SlashCommandData commandData() {
        return Commands.slash("zapowiedz", "Zapowiedź startu SkyPvP")
            .addSubcommands(
                new SubcommandData("panel", "Wysyła panel zapisów na kanał")
                    .addOption(OptionType.CHANNEL, "kanal", "Kanał, na który wysłać panel", true),
                new SubcommandData("wyslij", "Wysyła przypomnienia PV (menu + embed statusu)")
            );
    }

    public void registerCommands(@NotNull ReadyEvent event) {
        // Komendy rejestrowane wspólnie z /backup w BackupDiscordBot.onReady().
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (!event.getName().equalsIgnoreCase("zapowiedz")) {
            return;
        }
        if (!service.isEnabled()) {
            event.replyEmbeds(errorEmbed("Moduł zapowiedzi jest wyłączony.")).setEphemeral(true).queue();
            return;
        }
        if (!isStaff(event.getMember())) {
            event.replyEmbeds(errorEmbed("Tylko administracja może używać tej komendy.")).setEphemeral(true).queue();
            return;
        }
        if (!service.isMongoReady()) {
            event.replyEmbeds(errorEmbed("Baza Mongo zapowiedzi jest niedostępna.")).setEphemeral(true).queue();
            return;
        }

        String subcommand = event.getSubcommandName();
        if (subcommand == null) {
            event.replyEmbeds(errorEmbed("Użyj `/zapowiedz panel` lub `/zapowiedz wyslij`.")).setEphemeral(true).queue();
            return;
        }

        if (subcommand.equalsIgnoreCase("panel")) {
            handlePanel(event);
            return;
        }
        if (subcommand.equalsIgnoreCase("wyslij")) {
            handleWyslij(event);
        }
    }

    private void handlePanel(SlashCommandInteractionEvent event) {
        var channelOption = event.getOption("kanal");
        if (channelOption == null || channelOption.getAsChannel().asTextChannel() == null) {
            event.replyEmbeds(errorEmbed("Podaj prawidłowy kanał tekstowy.")).setEphemeral(true).queue();
            return;
        }
        var channel = channelOption.getAsChannel().asTextChannel();
        event.deferReply(true).queue(hook -> {
            service.postAnnouncement(event.getJDA(), channel, event.getUser().getId());
            hook.editOriginalEmbeds(successEmbed(
                "Wysłano zapowiedź startu SkyPvP na " + channel.getAsMention() + ".\n"
                    + "Gracze mogą klikać przycisk i wpisywać nick Minecraft."
            )).queue();
        });
    }

    private void handleWyslij(SlashCommandInteractionEvent event) {
        if (reminderService == null) {
            event.replyEmbeds(errorEmbed("Serwis przypomnień nie jest jeszcze gotowy.")).setEphemeral(true).queue();
            return;
        }
        if (reminderService.hasActiveDispatch()) {
            event.replyEmbeds(errorEmbed("Wysyłka PV już trwa — poczekaj na zakończenie.")).setEphemeral(true).queue();
            return;
        }

        StringSelectMenu.Builder menu = StringSelectMenu.create(ZapowiedzReminderService.DISPATCH_SELECT_ID)
            .setPlaceholder("Wybierz przypomnienie do wysłania...")
            .setMinValues(1)
            .setMaxValues(1);

        for (ZapowiedzReminderService.ReminderSlot slot : reminderService.allSlots()) {
            menu.addOptions(SelectOption.of(clip(slot.label(), 100), slot.key())
                .withDescription(clip("PV · start trybu SkyPvP", 100)));
        }

        event.replyEmbeds(successEmbed(
            "Wybierz przypomnienie z menu poniżej.\n"
                + "Embed ze statusem trafi na kanał <#"
                + plugin.getConfigManager().getBackupConfig().getZapowiedz().getReminderStatusChannelId()
                + "> — będzie pokazywał ile osób zostało powiadomionych."
        ))
            .addComponents(ActionRow.of(menu.build()))
            .setEphemeral(true)
            .queue();
    }

    @Override
    public void onStringSelectInteraction(@NotNull StringSelectInteractionEvent event) {
        if (!ZapowiedzReminderService.DISPATCH_SELECT_ID.equals(event.getComponentId())) {
            return;
        }
        if (reminderService == null) {
            event.replyEmbeds(errorEmbed("Serwis przypomnień nie jest jeszcze gotowy.")).setEphemeral(true).queue();
            return;
        }
        if (!isStaff(event.getMember())) {
            event.replyEmbeds(errorEmbed("Tylko administracja może wysyłać przypomnienia.")).setEphemeral(true).queue();
            return;
        }
        if (reminderService.hasActiveDispatch()) {
            event.replyEmbeds(errorEmbed("Wysyłka PV już trwa — poczekaj na zakończenie.")).setEphemeral(true).queue();
            return;
        }

        String reminderKey = event.getValues().isEmpty() ? null : event.getValues().get(0);
        if (reminderKey == null) {
            event.replyEmbeds(errorEmbed("Nie wybrano przypomnienia.")).setEphemeral(true).queue();
            return;
        }

        event.deferReply(true).queue(hook -> {
            reminderService.startManualDispatch(event.getJDA(), reminderKey, event.getUser().getId());
            hook.editOriginalEmbeds(successEmbed(
                "Uruchomiono wysyłkę PV.\n"
                    + "Status na kanale <#"
                    + plugin.getConfigManager().getBackupConfig().getZapowiedz().getReminderStatusChannelId()
                    + "> — **1 osoba / "
                    + ZapowiedzReminderService.DISPATCH_INTERVAL_SECONDS
                    + " s**."
            )).queue();
        });
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        String messageId = ZapowiedzService.messageIdFromComponent(event.getComponentId());
        if (messageId == null) {
            return;
        }
        if (!service.isEnabled() || !service.isMongoReady()) {
            event.replyEmbeds(errorEmbed("Zapisy są chwilowo niedostępne.")).setEphemeral(true).queue();
            return;
        }

        Modal modal = Modal.create(ZapowiedzService.modalId(messageId), "Zapis na start SkyPvP")
            .addComponents(Label.of(
                "Twój nick Minecraft",
                TextInput.create("minecraft_nick", TextInputStyle.SHORT)
                    .setPlaceholder("np. Steve")
                    .setMinLength(3)
                    .setMaxLength(16)
                    .setRequired(true)
                    .build()
            ))
            .build();
        event.replyModal(modal).queue();
    }

    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event) {
        String messageId = ZapowiedzService.messageIdFromComponent(event.getModalId());
        if (messageId == null) {
            return;
        }
        String nick = event.getValue("minecraft_nick") == null ? "" : event.getValue("minecraft_nick").getAsString();
        event.deferReply(true).queue(hook -> service.registerParticipant(messageId, event.getUser().getId(), nick)
            .whenComplete((message, error) -> {
                if (error != null) {
                    hook.editOriginalEmbeds(errorEmbed("Błąd zapisu: " + error.getMessage())).queue();
                    return;
                }
                if (message.startsWith("Zapisano")) {
                    hook.editOriginalEmbeds(successEmbed(message)).queue();
                } else {
                    hook.editOriginalEmbeds(errorEmbed(message)).queue();
                }
            })
        );
    }

    private boolean isStaff(Member member) {
        if (member == null) {
            return false;
        }
        if (member.hasPermission(Permission.ADMINISTRATOR)) {
            return true;
        }
        List<String> roles = plugin.getConfigManager().getBackupConfig().getDiscordStaffRoleIds();
        if (roles == null || roles.isEmpty()) {
            return member.hasPermission(Permission.MANAGE_SERVER);
        }
        return member.getRoles().stream().anyMatch(role -> roles.contains(role.getId()));
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static net.dv8tion.jda.api.entities.MessageEmbed errorEmbed(String text) {
        return new EmbedBuilder()
            .setColor(ERROR)
            .setDescription("» " + text)
            .build();
    }

    private static net.dv8tion.jda.api.entities.MessageEmbed successEmbed(String text) {
        return new EmbedBuilder()
            .setColor(new Color(0x0EFF00))
            .setDescription("» " + text)
            .build();
    }
}
