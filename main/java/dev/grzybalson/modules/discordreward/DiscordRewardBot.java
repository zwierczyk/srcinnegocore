package dev.grzybalson.modules.discordreward;

import dev.grzybalson.HollyCore;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.modals.Modal;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DiscordRewardBot extends ListenerAdapter {

    private final HollyCore plugin;
    private final DiscordRewardManager manager;
    private final DiscordRewardManager.BotSettings settings;
    private final Map<String, Long> selectCooldowns = new ConcurrentHashMap<>();
    private JDA jda;

    public DiscordRewardBot(HollyCore plugin, DiscordRewardManager manager, DiscordRewardManager.BotSettings settings) {
        this.plugin = plugin;
        this.manager = manager;
        this.settings = settings;
    }

    public void start() {
        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                jda = JDABuilder.createDefault(settings.token())
                    .addEventListeners(this)
                    .build();
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Nie udalo sie uruchomic bota nagrody Discord: " + exception.getMessage());
            }
        });
    }

    public void shutdown() {
        if (jda != null) {
            jda.shutdownNow();
            jda = null;
        }
        selectCooldowns.clear();
    }

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        Guild guild = event.getJDA().getGuildById(settings.guildId());
        if (guild == null) {
            plugin.getLogger().warning("Bot nagrody Discord nie widzi guildId=" + settings.guildId() + ".");
            return;
        }

        guild.upsertCommand(settings.commandName(), "Wysyla panel nagrody Discord").queue(
            command -> plugin.getLogger().info("Zarejestrowano komende Discord /" + settings.commandName() + " dla nagrody."),
            throwable -> plugin.getLogger().warning("Nie udalo sie zarejestrowac komendy Discord /" + settings.commandName() + ": " + throwable.getMessage())
        );
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (!event.getName().equalsIgnoreCase(settings.commandName())) {
            return;
        }

        DiscordRewardManager.RewardMessageConfig messageConfig = settings.rewardMessageConfig();
        if (!canSendPanel(event.getMember())) {
            safeReply(event.reply(messageConfig.noPermissionMessage()).setEphemeral(true));
            return;
        }

        StringSelectMenu selectMenu = buildSelectMenu(messageConfig);
        if (selectMenu == null) {
            safeReply(event.reply(messageConfig.rewardTypeErrorMessage()).setEphemeral(true));
            return;
        }

        safeReply(event.replyEmbeds(buildEmbed(messageConfig)).addComponents(ActionRow.of(selectMenu)));
    }

    @Override
    public void onStringSelectInteraction(@NotNull StringSelectInteractionEvent event) {
        DiscordRewardManager.RewardMessageConfig messageConfig = settings.rewardMessageConfig();
        if (!event.getComponentId().equals(messageConfig.selectMenuId())) {
            return;
        }

        String selectedValue = event.getValues().isEmpty() ? "" : event.getValues().get(0);
        // Wspólny token bota na wielu trybach: każde JDA dostaje tę samą interakcję.
        // Tylko serwer z pasującym rewardOptionValue może ją acknowledge'ować —
        // inaczej drugi proces dostaje 10062 Unknown interaction.
        if (!settings.rewardOptionValue().isBlank()
            && !selectedValue.equalsIgnoreCase(settings.rewardOptionValue())) {
            return;
        }

        long now = System.currentTimeMillis();
        long cooldownUntil = selectCooldowns.getOrDefault(event.getUser().getId(), 0L);
        if (cooldownUntil > now) {
            safeReply(event.reply(messageConfig.cooldownMessage()).setEphemeral(true));
            return;
        }

        if (settings.selectionCooldownSeconds() > 0) {
            selectCooldowns.put(event.getUser().getId(), now + settings.selectionCooldownSeconds() * 1000L);
        }

        safeReply(event.replyModal(buildModal(messageConfig)));
    }

    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event) {
        DiscordRewardManager.RewardMessageConfig messageConfig = settings.rewardMessageConfig();
        if (!event.getModalId().equals(effectiveModalId(messageConfig))) {
            return;
        }

        List<DiscordRewardManager.ModalInputConfig> inputs = messageConfig.modalInputs();
        if (inputs.isEmpty()) {
            safeReply(event.reply(messageConfig.rewardTypeErrorMessage()).setEphemeral(true));
            return;
        }

        String firstNick = getModalValue(event, inputs.get(0).id()).trim();
        String secondNick = inputs.size() > 1 ? getModalValue(event, inputs.get(1).id()).trim() : firstNick;

        if (firstNick.isBlank() || !firstNick.equalsIgnoreCase(secondNick)) {
            safeReply(event.reply(messageConfig.nickMismatchMessage()).setEphemeral(true));
            return;
        }

        event.deferReply(true).queue(
            hook -> handleClaim(hook, event.getUser().getId(), firstNick, messageConfig),
            this::logInteractionFailure
        );
    }

    private String effectiveModalId(DiscordRewardManager.RewardMessageConfig messageConfig) {
        String base = messageConfig.modalId() == null ? "reward_modal" : messageConfig.modalId();
        String option = settings.rewardOptionValue();
        if (option == null || option.isBlank()) {
            return base;
        }
        return base + "_" + option;
    }

    private void handleClaim(InteractionHook hook, String discordId, String minecraftName, DiscordRewardManager.RewardMessageConfig messageConfig) {
        manager.claimDiscordReward(discordId, minecraftName).whenComplete((outcome, throwable) -> {
            if (throwable != null) {
                plugin.getLogger().warning("Nie udalo sie nadac nagrody Discord dla " + minecraftName + ": " + throwable.getMessage());
                hook.editOriginal(messageConfig.rewardTypeErrorMessage()).queue(null, this::logInteractionFailure);
                return;
            }

            switch (outcome.state()) {
                case SUCCESS -> hook.editOriginal(messageConfig.successMessage().replace("{PLAYER}", outcome.playerName()))
                    .queue(null, this::logInteractionFailure);
                case ALREADY_CLAIMED -> hook.editOriginal(messageConfig.rewardAlreadyClaimed())
                    .queue(null, this::logInteractionFailure);
                case PLAYER_NOT_FOUND -> hook.editOriginal(messageConfig.playerNoFound())
                    .queue(null, this::logInteractionFailure);
            }
        });
    }

    private void safeReply(net.dv8tion.jda.api.requests.RestAction<?> action) {
        action.queue(null, this::logInteractionFailure);
    }

    private void logInteractionFailure(Throwable throwable) {
        if (throwable == null) {
            return;
        }
        String message = throwable.getMessage() == null ? "" : throwable.getMessage();
        // 10062 = inna instancja bota / timeout — nie spamuj ERROR na konsolę serwera.
        if (message.contains("10062") || message.contains("Unknown interaction")) {
            plugin.getLogger().fine("Discord interaction juz obsluzona przez inny proces: " + message);
            return;
        }
        plugin.getLogger().warning("Discord reward interaction failed: " + message);
    }

    private MessageEmbed buildEmbed(DiscordRewardManager.RewardMessageConfig messageConfig) {
        EmbedBuilder builder = new EmbedBuilder()
            .setTitle(messageConfig.embedTitle())
            .setColor(parseColor(messageConfig.embedColorHex()));

        if (messageConfig.embedImageUrl() != null && !messageConfig.embedImageUrl().isBlank()) {
            builder.setImage(messageConfig.embedImageUrl());
        }

        for (DiscordRewardManager.EmbedFieldConfig field : messageConfig.embedFields()) {
            if (!field.name().isBlank() || !field.value().isBlank()) {
                builder.addField(field.name(), field.value(), field.inline());
            }
        }
        return builder.build();
    }

    private StringSelectMenu buildSelectMenu(DiscordRewardManager.RewardMessageConfig messageConfig) {
        if (messageConfig.selectMenuOptions().isEmpty()) {
            return null;
        }

        StringSelectMenu.Builder builder = StringSelectMenu.create(messageConfig.selectMenuId())
            .setPlaceholder(messageConfig.selectMenuPlaceholder())
            .setMinValues(1)
            .setMaxValues(1);

        int addedOptions = 0;
        for (DiscordRewardManager.SelectOptionConfig optionConfig : messageConfig.selectMenuOptions()) {
            if (optionConfig.value().isBlank()) {
                continue;
            }

            SelectOption option = SelectOption.of(optionConfig.label(), optionConfig.value());
            if (optionConfig.description() != null && !optionConfig.description().isBlank()) {
                option = option.withDescription(optionConfig.description());
            }

            Emoji emoji = buildEmoji(optionConfig.emoji());
            if (emoji != null) {
                option = option.withEmoji(emoji);
            }
            builder.addOptions(option);
            addedOptions++;
        }

        return addedOptions == 0 ? null : builder.build();
    }

    private Modal buildModal(DiscordRewardManager.RewardMessageConfig messageConfig) {
        Modal.Builder builder = Modal.create(effectiveModalId(messageConfig), messageConfig.modalTitle());
        List<Label> labels = new ArrayList<>();
        for (DiscordRewardManager.ModalInputConfig inputConfig : messageConfig.modalInputs()) {
            TextInputStyle style = "PARAGRAPH".equals(inputConfig.normalizedStyle()) ? TextInputStyle.PARAGRAPH : TextInputStyle.SHORT;
            labels.add(Label.of(
                inputConfig.label(),
                TextInput.create(inputConfig.id(), style).setRequired(true).build()
            ));
            if (labels.size() >= 5) {
                break;
            }
        }
        builder.addComponents(labels);
        return builder.build();
    }

    private String getModalValue(ModalInteractionEvent event, String id) {
        return event.getValue(id) == null ? "" : event.getValue(id).getAsString();
    }

    private Emoji buildEmoji(DiscordRewardManager.EmojiConfig config) {
        if (config == null || config.name().isBlank()) {
            return null;
        }

        try {
            if (!config.id().isBlank()) {
                return Emoji.fromCustom(config.name(), Long.parseUnsignedLong(config.id()), config.animated());
            }
            return Emoji.fromUnicode(config.name());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private boolean canSendPanel(Member member) {
        String permissionValue = settings.panelPermission();
        if (permissionValue == null || permissionValue.isBlank()) {
            return true;
        }
        if (member == null) {
            return false;
        }

        if (permissionValue.chars().allMatch(Character::isDigit)) {
            return member.getRoles().stream().anyMatch(role -> role.getId().equals(permissionValue));
        }

        try {
            Permission permission = Permission.valueOf(permissionValue.toUpperCase(Locale.ROOT));
            return member.hasPermission(permission);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private Color parseColor(String rawColor) {
        String value = rawColor == null ? "#00FFFF" : rawColor.trim();
        if (value.startsWith("#")) {
            value = value.substring(1);
        }

        try {
            return new Color(Integer.parseInt(value, 16));
        } catch (NumberFormatException exception) {
            return Color.CYAN;
        }
    }
}
