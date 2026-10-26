package dev.grzybalson.modules.discordreward;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.HollyDataPaths;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.ConfigItemStackReader;
import dev.grzybalson.utils.HeadUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class DiscordRewardManager {

    private static final String RESOURCE_PATH = "addons/discordreward/discordreward.yml";

    private final HollyCore plugin;
    private final File configFile;

    private YamlConfiguration config;
    private DiscordRewardClaimDataManager dataManager;
    private DiscordRewardBot bot;
    private MenuSettings menuSettings = MenuSettings.defaults();
    private BotSettings botSettings = BotSettings.disabled();
    private String collectionName = "";

    public DiscordRewardManager(HollyCore plugin) {
        this.plugin = plugin;
        this.configFile = new File(HollyDataPaths.getAddonFolder(plugin, "discordreward"), "discordreward.yml");
        reload();
    }

    public void reload() {
        HollyDataPaths.ensureResource(plugin, RESOURCE_PATH);
        config = YamlConfiguration.loadConfiguration(configFile);
        menuSettings = loadMenuSettings();
        botSettings = loadBotSettings();
        reloadDataManager();
        restartBot();
    }

    public void shutdown() {
        if (bot != null) {
            bot.shutdown();
            bot = null;
        }
    }

    public boolean isEnabled() {
        return menuSettings.enabled();
    }

    public CompletableFuture<Boolean> hasMinecraftClaim(UUID minecraftUuid, String minecraftName) {
        DiscordRewardClaimDataManager currentDataManager = dataManager;
        if (currentDataManager == null) {
            return CompletableFuture.completedFuture(false);
        }
        return currentDataManager.hasMinecraftClaim(minecraftUuid, minecraftName);
    }

    public BotSettings getBotSettings() {
        return botSettings;
    }

    public void openRewardMenu(Player player) {
        reloadMenuConfig();
        if (!menuSettings.enabled()) {
            player.sendMessage(ColorUtils.colorizeComponent(config.getString("messages.menuDisabled", "&cNagroda Discord jest wylaczona.")));
            return;
        }

        if (dataManager == null) {
            openRewardMenu(player, false);
            return;
        }

        dataManager.hasMinecraftClaim(player.getUniqueId(), player.getName()).whenComplete((claimed, throwable) -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                if (throwable != null) {
                    plugin.getLogger().warning("Nie udalo sie sprawdzic statusu nagrody Discord dla " + player.getName() + ": " + throwable.getMessage());
                    openRewardMenu(player, false);
                    return;
                }
                openRewardMenu(player, Boolean.TRUE.equals(claimed));
            });
        });
    }

    public void handleMenuClick(Player player, int rawSlot) {
        if (rawSlot != menuSettings.slot()) {
            return;
        }

        for (String line : config.getStringList("messages.minecraftClick")) {
            if (!line.isBlank()) {
                player.sendMessage(ColorUtils.colorizeComponent(line.replace("{LINK}", menuSettings.link())));
            }
        }

        if (!menuSettings.clickable() || menuSettings.link().isBlank()) {
            return;
        }

        Component component = ColorUtils.colorizeComponent(config.getString("messages.linkMessage", "&bKliknij tutaj, aby wejsc na Discord: &f{LINK}")
            .replace("{LINK}", menuSettings.link()))
            .clickEvent(ClickEvent.openUrl(menuSettings.link()));
        player.sendMessage(component);
    }

    public CompletableFuture<ClaimOutcome> claimDiscordReward(String discordId, String minecraftName) {
        CompletableFuture<ClaimOutcome> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayerExact(minecraftName);
            if (player == null) {
                future.complete(ClaimOutcome.playerNotFound());
                return;
            }

            DiscordRewardClaimDataManager currentDataManager = dataManager;
            if (currentDataManager == null) {
                future.completeExceptionally(new IllegalStateException("Data manager nagrody Discord nie jest gotowy."));
                return;
            }

            UUID playerUuid = player.getUniqueId();
            String playerName = player.getName();
            currentDataManager.claim(discordId, playerUuid, playerName, menuSettings.rewardType()).whenComplete((result, throwable) -> {
                if (throwable != null) {
                    future.completeExceptionally(throwable);
                    return;
                }

                if (result == DiscordRewardClaimDataManager.ClaimResult.ALREADY_CLAIMED) {
                    future.complete(ClaimOutcome.alreadyClaimed());
                    return;
                }

                Bukkit.getScheduler().runTask(plugin, () -> {
                    dispatchRewardCommands(playerName, playerUuid, discordId);
                    future.complete(ClaimOutcome.success(playerName));
                });
            });
        });
        return future;
    }

    private void openRewardMenu(Player player, boolean claimed) {
        DiscordRewardMenuHolder holder = new DiscordRewardMenuHolder();
        Inventory inventory = Bukkit.createInventory(holder, menuSettings.size(), ColorUtils.colorize(menuSettings.title()));
        holder.setInventory(inventory);
        fillInventory(inventory);
        inventory.setItem(menuSettings.slot(), buildRewardItem(claimed));
        player.openInventory(inventory);
    }

    private void fillInventory(Inventory inventory) {
        if (menuSettings.templateEnabled()) {
            plugin.getGuiFactory().applyDefaultTemplate(inventory);
            return;
        }

        if (!menuSettings.fillerEnabled()) {
            return;
        }

        Material material = Material.matchMaterial(menuSettings.fillerMaterial());
        if (material == null || material.isAir()) {
            material = Material.GRAY_STAINED_GLASS_PANE;
        }

        ItemStack filler = new ItemStack(material);
        ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            meta.displayName(ColorUtils.colorizeComponent(menuSettings.fillerName()));
            filler.setItemMeta(meta);
        }

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (slot != menuSettings.slot()) {
                inventory.setItem(slot, filler.clone());
            }
        }
    }

    private ItemStack buildRewardItem(boolean claimed) {
        ItemStack item = ConfigItemStackReader.getItem(config, "reward.itemStack");
        if (item == null) {
            item = new ItemStack(Material.PLAYER_HEAD);
        }

        if (item.getType() == Material.PLAYER_HEAD && !menuSettings.head().isBlank()) {
            item = HeadUtils.setHeadTextureLikeShop(item, menuSettings.head());
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        String displayName = config.getString(
            "reward.itemStack.displayName",
            config.getString("reward.displayName", menuSettings.displayName())
        );
        meta.displayName(ColorUtils.colorizeComponent(replacePlaceholders(displayName, claimed)));

        List<String> rawLore = config.getStringList("reward.itemStack.lore");
        if (!rawLore.isEmpty()) {
            List<Component> lore = new ArrayList<>(rawLore.size());
            for (String line : rawLore) {
                String formatted = replacePlaceholders(line, claimed);
                if (ColorUtils.isBlankLoreLine(formatted)) {
                    continue;
                }
                lore.add(ColorUtils.colorizeComponent(formatted));
            }
            if (!lore.isEmpty()) {
                meta.lore(lore);
            }
        }

        item.setItemMeta(meta);
        return item;
    }

    private String replacePlaceholders(String text, boolean claimed) {
        String status = claimed ? menuSettings.statusClaimed() : menuSettings.statusAvailable();
        return (text == null ? "" : text)
            .replace("{STATUS}", status)
            .replace("{LINK}", menuSettings.link())
            .replace("{REWARD}", menuSettings.rewardType());
    }

    private void dispatchRewardCommands(String playerName, UUID playerUuid, String discordId) {
        for (String rawCommand : menuSettings.commands()) {
            if (rawCommand == null || rawCommand.isBlank()) {
                continue;
            }

            String command = rawCommand
                .replace("{PLAYER}", playerName)
                .replace("{player}", playerName)
                .replace("{UUID}", playerUuid.toString())
                .replace("{PLAYER_UUID}", playerUuid.toString())
                .replace("{DISCORD_ID}", discordId)
                .replace("{discordId}", discordId)
                .trim();

            if (command.startsWith("/")) {
                command = command.substring(1);
            }
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        }
    }

    private void reloadMenuConfig() {
        config = YamlConfiguration.loadConfiguration(configFile);
        menuSettings = loadMenuSettings();
    }

    private void reloadDataManager() {
        String nextCollectionName = config.getString("mongo.collection", "discord_rewards");
        if (nextCollectionName == null || nextCollectionName.isBlank()) {
            nextCollectionName = "discord_rewards";
        }

        if (dataManager == null || !nextCollectionName.equals(collectionName)) {
            collectionName = nextCollectionName;
            dataManager = new DiscordRewardClaimDataManager(plugin, collectionName);
        }
    }

    private void restartBot() {
        if (bot != null) {
            bot.shutdown();
            bot = null;
        }

        if (!botSettings.readyToStart()) {
            if (botSettings.enabled()) {
                plugin.getLogger().info("Bot nagrody Discord nie zostal uruchomiony: uzupelnij token i guildId w addons/discordreward/discordreward.yml.");
            }
            return;
        }

        bot = new DiscordRewardBot(plugin, this, botSettings);
        bot.start();
    }

    private MenuSettings loadMenuSettings() {
        int rows = Math.max(1, config.getInt("gui.rows", Math.max(1, config.getInt("gui.size", 45) / 9)));
        int size = plugin.getGuiFactory().getInventorySize(rows);
        int slot = Math.max(0, Math.min(size - 1, config.getInt("reward.slot", 22)));
        return new MenuSettings(
            config.getBoolean("enabled", true),
            size,
            config.getString("gui.title", "&8Nagroda Discord"),
            slot,
            config.getString("reward.displayName", "&bDiscord"),
            config.getString("reward.reward", "DISCORD"),
            config.getString("reward.link", ""),
            config.getBoolean("reward.clickable", false),
            config.getString("reward.head", ""),
            config.getStringList("reward.commands"),
            config.getString("status.available", "&aDostępna"),
            config.getString("status.claimed", "&cOdebrana"),
            config.getBoolean("gui.template", true),
            config.getBoolean("gui.filler.enabled", true),
            config.getString("gui.filler.material", "LIGHT_BLUE_STAINED_GLASS_PANE"),
            config.getString("gui.filler.name", " ")
        );
    }

    private BotSettings loadBotSettings() {
        RewardMessageConfig rewardMessageConfig = loadRewardMessageConfig("bot.rewardMessageConfig");
        String rewardOptionValue = config.getString("bot.rewardOptionValue", "");
        if ((rewardOptionValue == null || rewardOptionValue.isBlank()) && !rewardMessageConfig.selectMenuOptions().isEmpty()) {
            rewardOptionValue = rewardMessageConfig.selectMenuOptions().get(0).value();
        }

        int cooldownSeconds = config.getInt("bot.selectionCooldownSeconds", config.getInt("reward.verificationTime", 60));
        return new BotSettings(
            config.getBoolean("bot.enabled", true),
            config.getString("bot.token", ""),
            config.getString("bot.guildId", ""),
            config.getString("bot.commandName", "nagroda"),
            config.getString("bot.panelPermission", "MANAGE_SERVER"),
            rewardOptionValue == null ? "" : rewardOptionValue,
            Math.max(0, cooldownSeconds),
            rewardMessageConfig
        );
    }

    private RewardMessageConfig loadRewardMessageConfig(String path) {
        List<EmbedFieldConfig> fields = new ArrayList<>();
        for (Map<?, ?> map : config.getMapList(path + ".embedFields")) {
            fields.add(new EmbedFieldConfig(
                stringValue(map, "name", ""),
                stringValue(map, "value", ""),
                booleanValue(map, "inline", false)
            ));
        }

        List<SelectOptionConfig> options = new ArrayList<>();
        for (Map<?, ?> map : config.getMapList(path + ".selectMenuOptions")) {
            options.add(new SelectOptionConfig(
                stringValue(map, "label", "Opcja"),
                stringValue(map, "value", ""),
                stringValue(map, "description", ""),
                emojiValue(map)
            ));
        }

        List<ModalInputConfig> inputs = new ArrayList<>();
        for (Map<?, ?> map : config.getMapList(path + ".modalInputs")) {
            inputs.add(new ModalInputConfig(
                stringValue(map, "id", "nick" + (inputs.size() + 1)),
                stringValue(map, "label", "Podaj nick"),
                stringValue(map, "style", "SHORT")
            ));
        }

        if (inputs.isEmpty()) {
            inputs.add(new ModalInputConfig("nick1", "Podaj swoj nick", "SHORT"));
            inputs.add(new ModalInputConfig("nick2", "Potwierdz swoj nick", "SHORT"));
        }

        return new RewardMessageConfig(
            config.getString(path + ".embedTitle", "Nagrody"),
            config.getString(path + ".embedColorHex", "#00FFFF"),
            config.getString(path + ".embedImageUrl", ""),
            fields,
            config.getString(path + ".selectMenuId", "reward_options"),
            config.getString(path + ".selectMenuPlaceholder", "Wybierz serwer!"),
            options,
            config.getString(path + ".modalId", "reward_modal"),
            config.getString(path + ".modalTitle", "Nagroda"),
            inputs,
            config.getString(path + ".cooldownMessage", "Musisz chwilę poczekać przed ponownym wyborem nagrody."),
            config.getString(path + ".rewardTypeErrorMessage", "Problem z nazwą nagrody..."),
            config.getString(path + ".nickMismatchMessage", "Podane nicki nie są takie same!"),
            config.getString(path + ".rewardAlreadyClaimed", "Odebrales juz nagrode..."),
            config.getString(path + ".playerNoFound", "Nie znajdujesz sie na serwerze!"),
            config.getString(path + ".successMessage", "Nagroda zostala nadana. Wpisz /odbierz na serwerze."),
            config.getString(path + ".noPermissionMessage", "&#FF0000✘ &8Ξ &cNie posiadasz dostępu do &#FF0000/nagroda reload &8(&4Holly.cmd.nagroda.reload&8)")
        );
    }

    private EmojiConfig emojiValue(Map<?, ?> map) {
        Object rawEmoji = map.get("emoji");
        if (!(rawEmoji instanceof Map<?, ?> emojiMap)) {
            return null;
        }
        return new EmojiConfig(
            stringValue(emojiMap, "name", ""),
            stringValue(emojiMap, "id", ""),
            booleanValue(emojiMap, "animated", false)
        );
    }

    private String stringValue(Map<?, ?> map, String key, String fallback) {
        Object value = map.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private boolean booleanValue(Map<?, ?> map, String key, boolean fallback) {
        Object value = map.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private int normalizeInventorySize(int rawSize) {
        int size = Math.max(9, Math.min(54, rawSize));
        return ((size + 8) / 9) * 9;
    }

    public record ClaimOutcome(ClaimState state, String playerName) {
        static ClaimOutcome success(String playerName) {
            return new ClaimOutcome(ClaimState.SUCCESS, playerName);
        }

        static ClaimOutcome alreadyClaimed() {
            return new ClaimOutcome(ClaimState.ALREADY_CLAIMED, "");
        }

        static ClaimOutcome playerNotFound() {
            return new ClaimOutcome(ClaimState.PLAYER_NOT_FOUND, "");
        }
    }

    public enum ClaimState {
        SUCCESS,
        ALREADY_CLAIMED,
        PLAYER_NOT_FOUND
    }

    public record MenuSettings(
        boolean enabled,
        int size,
        String title,
        int slot,
        String displayName,
        String rewardType,
        String link,
        boolean clickable,
        String head,
        List<String> commands,
        String statusAvailable,
        String statusClaimed,
        boolean templateEnabled,
        boolean fillerEnabled,
        String fillerMaterial,
        String fillerName
    ) {
        static MenuSettings defaults() {
            return new MenuSettings(true, 45, "&8Nagroda Discord", 22, "&bDiscord", "DISCORD", "", false, "",
                Collections.emptyList(), "&aDostepna", "&cOdebrana", true, true, "LIGHT_BLUE_STAINED_GLASS_PANE", " ");
        }
    }

    public record BotSettings(
        boolean enabled,
        String token,
        String guildId,
        String commandName,
        String panelPermission,
        String rewardOptionValue,
        int selectionCooldownSeconds,
        RewardMessageConfig rewardMessageConfig
    ) {
        static BotSettings disabled() {
            return new BotSettings(false, "", "", "nagroda", "MANAGE_SERVER", "", 60, RewardMessageConfig.defaults());
        }

        boolean readyToStart() {
            return enabled && token != null && !token.isBlank() && guildId != null && !guildId.isBlank();
        }
    }

    public record RewardMessageConfig(
        String embedTitle,
        String embedColorHex,
        String embedImageUrl,
        List<EmbedFieldConfig> embedFields,
        String selectMenuId,
        String selectMenuPlaceholder,
        List<SelectOptionConfig> selectMenuOptions,
        String modalId,
        String modalTitle,
        List<ModalInputConfig> modalInputs,
        String cooldownMessage,
        String rewardTypeErrorMessage,
        String nickMismatchMessage,
        String rewardAlreadyClaimed,
        String playerNoFound,
        String successMessage,
        String noPermissionMessage
    ) {
        static RewardMessageConfig defaults() {
            return new RewardMessageConfig("Nagrody", "#00FFFF", "", Collections.emptyList(), "reward_options", "Wybierz serwer!",
                Collections.emptyList(), "reward_modal", "Nagroda", Collections.emptyList(), "Musisz chwilę poczekać przed ponownym wyborem nagrody.",
                "Problem z nazwą nagrody...", "Podane nicki nie są takie same!", "Odebrales juz nagrode...", "Nie znajdujesz sie na serwerze!",
                "Nagroda zostala nadana. Wpisz /odbierz na serwerze.", "&#FF0000✘ &8Ξ &cNie posiadasz dostępu do &#FF0000/nagroda reload &8(&4Holly.cmd.nagroda.reload&8)");
        }
    }

    public record EmbedFieldConfig(String name, String value, boolean inline) {
    }

    public record SelectOptionConfig(String label, String value, String description, EmojiConfig emoji) {
    }

    public record EmojiConfig(String name, String id, boolean animated) {
    }

    public record ModalInputConfig(String id, String label, String style) {
        public String normalizedStyle() {
            return style == null ? "SHORT" : style.toUpperCase(Locale.ROOT);
        }
    }
}
