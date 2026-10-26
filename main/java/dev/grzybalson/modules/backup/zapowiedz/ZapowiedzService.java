package dev.grzybalson.modules.backup.zapowiedz;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.configs.BackupConfig;
import dev.grzybalson.modules.cases.CaseManager;
import dev.grzybalson.utils.ColorUtils;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.mediagallery.MediaGallery;
import net.dv8tion.jda.api.components.mediagallery.MediaGalleryItem;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.awt.Color;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

public final class ZapowiedzService {

    private static final Color BRAND = new Color(0x0EFF00);
    private static final String FOOTER = "-# OGHUB.PL · zapowiedz startu · SkyPvP";
    private static final String SMALL_CAPS = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ";
    private static final Pattern NICK_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,16}$");
    private static final DateTimeFormatter DATE_PL = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("pl-PL"));

    private final HollyCore plugin;
    private final ZapowiedzMongoManager mongo;
    private final ZapowiedzRepository repository;

    public ZapowiedzService(HollyCore plugin, ZapowiedzMongoManager mongo, ZapowiedzRepository repository) {
        this.plugin = plugin;
        this.mongo = mongo;
        this.repository = repository;
    }

    public boolean isEnabled() {
        return plugin.getConfigManager().getBackupConfig().getZapowiedz().isEnabled();
    }

    public boolean connectMongo() {
        if (!isEnabled()) {
            return false;
        }
        boolean ok = mongo.connect();
        if (ok) {
            repository.ensureIndexes();
        }
        return ok;
    }

    public void disconnectMongo() {
        mongo.disconnect();
    }

    public boolean isMongoReady() {
        return mongo.isAvailable();
    }

    public boolean isRewardServer() {
        BackupConfig.Zapowiedz config = plugin.getConfigManager().getBackupConfig().getZapowiedz();
        String required = config.getRewardDbPrefix();
        if (required == null || required.isBlank()) {
            return true;
        }
        String current = plugin.getConfigManager().getDatabaseConfig().getDatabaseName();
        return required.equalsIgnoreCase(current);
    }

    public ZapowiedzRepository getRepository() {
        return repository;
    }

    public static String buttonId(String messageId) {
        return "zp:join:" + messageId;
    }

    public static String modalId(String messageId) {
        return "zp:nick:" + messageId;
    }

    public static String messageIdFromComponent(String componentId) {
        if (componentId == null) {
            return null;
        }
        if (componentId.startsWith("zp:join:")) {
            return componentId.substring("zp:join:".length());
        }
        if (componentId.startsWith("zp:nick:")) {
            return componentId.substring("zp:nick:".length());
        }
        return null;
    }

    public MessageCreateBuilder buildAnnouncementPanel(long participantCount, String messageId) {
        BackupConfig.Zapowiedz config = plugin.getConfigManager().getBackupConfig().getZapowiedz();
        String body = buildBody(config, participantCount);

        List<ContainerChildComponent> children = new ArrayList<>();
        children.add(TextDisplay.of(title("ZAPOWIEDZ STARTU")));
        children.add(Separator.createDivider(Separator.Spacing.LARGE));
        children.add(TextDisplay.of(body));
        children.add(Separator.createDivider(Separator.Spacing.SMALL));
        children.add(ActionRow.of(buildJoinButton(participantCount, messageId)));
        if (config.getBannerUrl() != null && !config.getBannerUrl().isBlank()) {
            children.add(Separator.createDivider(Separator.Spacing.SMALL));
            children.add(MediaGallery.of(MediaGalleryItem.fromUrl(config.getBannerUrl())));
        }
        children.add(TextDisplay.of(FOOTER));

        return new MessageCreateBuilder()
            .useComponentsV2()
            .setComponents(Container.of(children).withAccentColor(BRAND));
    }

    public MessageEditBuilder buildAnnouncementEdit(long participantCount, String messageId) {
        MessageCreateBuilder create = buildAnnouncementPanel(participantCount, messageId);
        return MessageEditBuilder.fromCreateData(create.build());
    }

    private Button buildJoinButton(long participantCount, String messageId) {
        String label = participantCount <= 0
            ? "Zapisz się · bądź pierwszy!"
            : "Zapisz się · " + participantCount + " os. bierze udział";
        if (label.length() > 80) {
            label = "Zapisz się · " + participantCount + " osób";
        }
        return Button.success(buttonId(messageId), label);
    }

    private String buildBody(BackupConfig.Zapowiedz config, long participantCount) {
        ZoneId zone = ZoneId.of(config.getTimezone());
        LocalDate date = LocalDate.parse(config.getEventDate());
        String dateText = DATE_PL.format(date);

        StringBuilder rewards = new StringBuilder();
        for (BackupConfig.Zapowiedz.Reward reward : config.getRewards()) {
            if (reward.getCaseName().isBlank()) {
                continue;
            }
            rewards.append("» **").append(reward.getAmount()).append("×** klucz do skrzynki **")
                .append(capitalize(reward.getCaseName())).append("**\n");
        }
        if (rewards.isEmpty()) {
            rewards.append("» Nagrody zostaną ogłoszone wkrótce.\n");
        }

        return ""
            + "**Start trybu SkyPvP:** " + ZapowiedzTexts.startLabel(config) + "\n"
            + "**Odliczanie:** " + ZapowiedzTexts.discordCountdown(config) + "\n"
            + "**Dokładnie:** " + ZapowiedzTexts.discordFullStart(config) + "\n"
            + "**Okno na nagrodę:** " + dateText + ", **" + config.getRewardStartHour() + ":00 – "
            + config.getRewardEndHour() + ":00**\n"
            + "*(Musisz wejść na serwer w tym przedziale czasowym)*\n\n"
            + "**Co dostaniesz za obecność na starcie:**\n"
            + rewards + "\n"
            + "**⚠ Ważne — deklaracja udziału**\n"
            + "Klikając przycisk poniżej **deklarujesz, że na 100% będziesz na starcie**.\n"
            + "To nie jest luźne „może przyjdę” — jeśli się nie pojawisz, **nagroda przepada** i nie będzie drugiej szansy.\n\n"
            + "Zapisz swój nick Minecraft i bądź online między **" + config.getRewardStartHour() + ":00 a "
            + config.getRewardEndHour() + ":00** w dniu startu!\n\n"
            + "-# Aktualnie zapisanych: **" + participantCount + "**";
    }

    public void postAnnouncement(JDA jda, TextChannel channel, String createdByDiscordId) {
        MessageCreateBuilder initial = buildAnnouncementPanel(0, "temp");
        channel.sendMessage(initial.build()).queue(message -> {
            String messageId = message.getId();
            repository.saveAnnouncement(messageId, channel.getId(), channel.getGuild().getId(), createdByDiscordId)
                .thenCompose(v -> repository.countParticipants(messageId))
                .whenComplete((count, error) -> {
                    if (error != null) {
                        plugin.getLogger().warning("[Zapowiedz] Zapis ogłoszenia: " + error.getMessage());
                        return;
                    }
                    message.editMessage(buildAnnouncementEdit(count == null ? 0L : count, messageId).build()).queue();
                });
        }, error -> plugin.getLogger().warning("[Zapowiedz] Wysyłka panelu: " + error.getMessage()));
    }

    public CompletableFuture<String> registerParticipant(String messageId, String discordUserId, String rawNick) {
        if (!mongo.isAvailable()) {
            return CompletableFuture.completedFuture("Baza zapisów jest chwilowo niedostępna. Spróbuj za chwilę.");
        }
        String nick = rawNick == null ? "" : rawNick.trim();
        if (!NICK_PATTERN.matcher(nick).matches()) {
            return CompletableFuture.completedFuture("Nick musi mieć 3–16 znaków (litery, cyfry, _).");
        }

        return repository.isDiscordRegistered(messageId, discordUserId).thenCompose(already -> {
            if (already) {
                return CompletableFuture.completedFuture("Jesteś już zapisany na tę zapowiedź.");
            }
            return repository.isNickTaken(messageId, nick.toLowerCase(Locale.ROOT)).thenCompose(taken -> {
                if (taken) {
                    return CompletableFuture.completedFuture("Ten nick Minecraft jest już zapisany przez kogoś innego.");
                }
                return repository.register(messageId, discordUserId, nick).thenCompose(v ->
                    repository.countParticipants(messageId).thenApply(count -> {
                        refreshAnnouncementMessage(messageId, count);
                        return "Zapisano! Nick **" + nick + "** jest zarejestrowany. Pamiętaj — musisz być online "
                            + formatEventWindow() + ", inaczej nagroda przepada.";
                    })
                );
            });
        });
    }

    private void refreshAnnouncementMessage(String messageId, long count) {
        repository.getAnnouncement(messageId).thenAccept(doc -> {
            if (doc == null) {
                return;
            }
            String channelId = doc.getString("channelId");
            JDA jda = plugin.getBackupManager() != null ? plugin.getBackupManager().getDiscordJda() : null;
            if (jda == null || channelId == null) {
                return;
            }
            TextChannel channel = jda.getTextChannelById(channelId);
            if (channel == null) {
                return;
            }
            channel.editMessageById(messageId, buildAnnouncementEdit(count, messageId).build()).queue(null, err ->
                plugin.getLogger().warning("[Zapowiedz] Nie udało się odświeżyć panelu: " + err.getMessage())
            );
        });
    }

    public void tryRewardPlayer(Player player) {
        if (!isEnabled() || !mongo.isAvailable() || !isWithinRewardWindow() || !isRewardServer()) {
            return;
        }

        String nickLower = player.getName().toLowerCase(Locale.ROOT);
        repository.findPendingRewards(nickLower).thenAccept(docs -> {
            if (docs == null || docs.isEmpty()) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> grantRewards(player, docs));
        });
    }

    private void grantRewards(Player player, List<org.bson.Document> docs) {
        if (!player.isOnline() || !isWithinRewardWindow()) {
            return;
        }

        BackupConfig.Zapowiedz config = plugin.getConfigManager().getBackupConfig().getZapowiedz();
        CaseManager caseManager = plugin.getCaseManager();

        for (org.bson.Document doc : docs) {
            String registrationId = doc.getObjectId("_id").toHexString();
            repository.markRewarded(registrationId).thenAccept(marked -> {
                if (!marked || !player.isOnline()) {
                    return;
                }
                Bukkit.getScheduler().runTask(plugin, () -> {
                    for (BackupConfig.Zapowiedz.Reward reward : config.getRewards()) {
                        giveCaseReward(player, caseManager, reward);
                    }
                    player.sendMessage(ColorUtils.colorizeComponent(
                        "&#00FF3B✔ &8Ξ &#4EFA76Dziękujemy, że dotrzymałeś zapowiedzi! Otrzymałeś klucze do skrzynek startowych."
                    ));
                    plugin.getLogger().info("[Zapowiedz] Nagrodzono gracza " + player.getName() + " za zapowiedź startu.");
                });
            });
        }
    }

    private void giveCaseReward(Player player, CaseManager caseManager, BackupConfig.Zapowiedz.Reward reward) {
        if (reward.getCaseName().isBlank()) {
            return;
        }
        if (caseManager != null && plugin.isAddonEnabled("cases")) {
            var definition = caseManager.getCase(reward.getCaseName());
            if (definition != null && caseManager.hasConfiguredKey(definition)) {
                caseManager.giveKey(player, definition, reward.getAmount());
                return;
            }
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
            "case give " + player.getName() + " " + reward.getCaseName() + " " + reward.getAmount());
    }

    public boolean isWithinRewardWindow() {
        BackupConfig.Zapowiedz config = plugin.getConfigManager().getBackupConfig().getZapowiedz();
        ZoneId zone = ZoneId.of(config.getTimezone());
        LocalDate eventDate = LocalDate.parse(config.getEventDate());
        LocalDateTime start = eventDate.atTime(config.getRewardStartHour(), 0);
        // atTime() przyjmuje tylko 0-23, a config dopuszcza 24 ("do północy") — liczymy od startu dnia.
        LocalDateTime end = eventDate.atStartOfDay().plusHours(config.getRewardEndHour());
        LocalDateTime now = LocalDateTime.now(zone);
        return !now.isBefore(start) && now.isBefore(end);
    }

    private String formatEventWindow() {
        BackupConfig.Zapowiedz config = plugin.getConfigManager().getBackupConfig().getZapowiedz();
        LocalDate date = LocalDate.parse(config.getEventDate());
        return DATE_PL.format(date) + " między " + config.getRewardStartHour() + ":00 a " + config.getRewardEndHour() + ":00";
    }

    private static String capitalize(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1).toLowerCase(Locale.ROOT);
    }

    private static String title(String label) {
        String formatted = toSmallCaps(label);
        int pad = Math.max(0, 48 - formatted.codePointCount(0, formatted.length()));
        return "```\n" + " ".repeat(Math.min(24, pad / 2)) + formatted + "\n```";
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
}
