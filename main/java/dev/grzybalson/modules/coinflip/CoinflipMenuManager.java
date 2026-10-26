package dev.grzybalson.modules.coinflip;

import dev.grzybalson.HollyCore;
import dev.grzybalson.anarchiagg.util.TextUtil;
import dev.grzybalson.anarchiagg.util.notice.Notice;
import dev.grzybalson.config.modules.coinflip.CoinflipConfig;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import dev.grzybalson.config.HollyDataPaths;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

public class CoinflipMenuManager implements Listener {

    private static final int MAIN_SIZE = 45;
    private static final int CLAIM_SIZE = 45;
    private static final int DUEL_SIZE = 27;
    private static final int INFO_SLOT = 4;
    private static final int CLAIM_CLOSE_SLOT = 40;
    private static final int PREVIOUS_SLOT = 28;
    private static final int NEXT_SLOT = 34;
    private static final int SORT_SLOT = 31;
    private static final int MONEY_CLAIM_SLOT = 20;
    private static final int SHARDS_CLAIM_SLOT = 24;
    private static final int EMPTY_CLAIM_SLOT = 22;
    private static final List<Integer> GAME_SLOTS = List.of(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25
    );
    private static final int DUEL_CENTER_SLOT = 13;

    private final HollyCore plugin;
    private final CoinflipConfig config;
    private final CoinflipManager manager;
    private final CoinflipDatabase database;
    private final Map<UUID, MainView> mainViews = new HashMap<>();
    private final Map<UUID, ClaimView> claimViews = new HashMap<>();
    private final Map<UUID, DuelView> duelViews = new HashMap<>();
    private final Map<String, DuelScrollSession> duelScrollSessions = new HashMap<>();
    private final Map<UUID, Long> clickCooldowns = new HashMap<>();
    private BukkitTask refreshTask;

    public CoinflipMenuManager(HollyCore plugin, CoinflipConfig config, CoinflipManager manager, CoinflipDatabase database) {
        this.plugin = plugin;
        this.config = config;
        this.manager = manager;
        this.database = database;
        startRefreshTask();
    }

    public void openMain(Player player) {
        openMain(player, 0, CoinflipFilter.ALL, true);
    }

    public void openMain(Player player, int page, CoinflipFilter filter, boolean playSound) {
        String title = color(config.getGui().getMainTitle());
        Inventory inventory = Bukkit.createInventory(null, MAIN_SIZE, title);
        MainView view = renderMainInventory(player, inventory, title, page, filter);

        claimViews.remove(player.getUniqueId());
        duelViews.remove(player.getUniqueId());
        player.openInventory(inventory);
        if (playSound) {
            playGuiSound(player);
        }
        mainViews.put(player.getUniqueId(), view);
    }

    public void openClaim(Player player) {
        database.getRewards(player.getName()).whenComplete((rewards, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (error != null) {
                Notice.sendP(player, config.getMessages().getType(), config.getMessages().getClaimError());
                TextUtil.sendConsole("&eNie mozna wczytac nagrod coinflip: " + error.getMessage());
                return;
            }
            openClaim(player, rewards == null ? new CoinflipRewards(0.0, 0.0) : rewards);
        }));
    }

    public void openDuel(Player viewer, Player other, GameSession session) {
        String title = color(config.getGui().getDuelTitle()
                .replace("%creator%", session.getCreatorName())
                .replace("%opponent%", session.getOpponentName() == null ? other.getName() : session.getOpponentName()));
        Inventory inventory = Bukkit.createInventory(null, DUEL_SIZE, title);
        fillDuelFrame(inventory);
        viewer.openInventory(inventory);
        playGuiSound(viewer);
        duelViews.put(viewer.getUniqueId(), new DuelView(title, session.getId(), false));
    }

    public void startDuelScrollAnimation(Player creator, Player opponent, GameSession session, Player winner, Runnable onComplete, Runnable onInterrupt) {
        openDuel(creator, opponent, session);
        openDuel(opponent, creator, session);

        ItemStack creatorHead = duelPlayerHead(creator, true);
        ItemStack opponentHead = duelPlayerHead(opponent, false);
        ItemStack winnerHead = winner.getUniqueId().equals(creator.getUniqueId()) ? creatorHead : opponentHead;

        int[] delays = config.getSettings().resolveAnimationDelayTicks();
        int steps = delays.length;
        int winnerIdx = steps - 1 + 4;
        int len = steps + 9;
        ItemStack[] strip = new ItemStack[len];
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < len; i++) {
            if (i == winnerIdx) {
                strip[i] = winnerHead.clone();
            } else {
                strip[i] = (random.nextBoolean() ? creatorHead : opponentHead).clone();
            }
        }

        AtomicBoolean resolved = new AtomicBoolean(false);
        Runnable finishSession = () -> {
            if (!resolved.compareAndSet(false, true)) {
                return;
            }
            duelScrollSessions.remove(session.getId());
            markDuelViewAnimating(creator, false);
            markDuelViewAnimating(opponent, false);
            ItemStack highlightedWinner = highlightWinnerHead(winnerHead.clone());
            showDuelWinner(creator, highlightedWinner);
            showDuelWinner(opponent, highlightedWinner);
            onComplete.run();
        };

        DuelScrollSession scrollSession = new DuelScrollSession(
                creator.getUniqueId(),
                opponent.getUniqueId(),
                onInterrupt,
                resolved
        );
        duelScrollSessions.put(session.getId(), scrollSession);
        markDuelViewAnimating(creator, true);
        markDuelViewAnimating(opponent, true);
        scheduleDuelScrollStep(session.getId(), creator, opponent, strip, delays, 0, finishSession);
    }

    public void interruptDuelScroll(String sessionId) {
        DuelScrollSession scrollSession = duelScrollSessions.remove(sessionId);
        if (scrollSession == null) {
            return;
        }
        if (!scrollSession.resolved().compareAndSet(false, true)) {
            return;
        }
        Runnable onInterrupt = scrollSession.onInterrupt();
        if (onInterrupt != null) {
            onInterrupt.run();
        }
    }

    private void scheduleDuelScrollStep(
            String sessionId,
            Player creator,
            Player opponent,
            ItemStack[] strip,
            int[] delays,
            int step,
            Runnable onFinish
    ) {
        if (duelScrollSessions.get(sessionId) == null) {
            return;
        }
        if (step >= delays.length) {
            onFinish.run();
            return;
        }
        applyDuelScrollStep(creator, strip, step);
        applyDuelScrollStep(opponent, strip, step);
        int delay = Math.max(1, delays[step]);
        Bukkit.getScheduler().runTaskLater(plugin, () -> scheduleDuelScrollStep(
                sessionId,
                creator,
                opponent,
                strip,
                delays,
                step + 1,
                onFinish
        ), delay);
    }

    private void applyDuelScrollStep(Player player, ItemStack[] strip, int step) {
        if (player == null || !player.isOnline()) {
            return;
        }
        DuelView view = duelViews.get(player.getUniqueId());
        if (view == null || !view.animating() || !isSameInventory(player, view.title())) {
            return;
        }
        Inventory inventory = player.getOpenInventory().getTopInventory();
        int startSlot = 9;
        for (int i = 0; i < 9; i++) {
            int stripIdx = step + i;
            ItemStack item = stripIdx < strip.length && strip[stripIdx] != null
                    ? strip[stripIdx].clone()
                    : new ItemStack(Material.AIR);
            inventory.setItem(startSlot + i, item);
        }
        float pitch = 1.5f - ((float) step / Math.max(1, strip.length)) * 0.7f;
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.4f, pitch);
    }

    private void showDuelWinner(Player player, ItemStack winnerHead) {
        if (!player.isOnline()) {
            return;
        }
        DuelView view = duelViews.get(player.getUniqueId());
        if (view == null || !isSameInventory(player, view.title())) {
            return;
        }
        player.getOpenInventory().getTopInventory().setItem(DUEL_CENTER_SLOT, winnerHead);
    }

    private ItemStack duelPlayerHead(Player player, boolean positiveColor) {
        String colorCode = positiveColor ? "&a" : "&c";
        return playerHead(player.getName(), colorCode + player.getName(), List.of());
    }

    private ItemStack highlightWinnerHead(ItemStack head) {
        ItemMeta meta = head.getItemMeta();
        if (meta != null) {
            meta.addEnchant(Enchantment.LUCK, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            head.setItemMeta(meta);
        }
        return head;
    }

    private void markDuelViewAnimating(Player player, boolean animating) {
        if (player == null) {
            return;
        }
        DuelView view = duelViews.get(player.getUniqueId());
        if (view == null) {
            return;
        }
        duelViews.put(player.getUniqueId(), new DuelView(view.title(), view.sessionId(), animating));
    }

    public void refreshMainViews() {
        for (Map.Entry<UUID, MainView> entry : new ArrayList<>(mainViews.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey());
            MainView view = entry.getValue();
            if (!isSameInventory(player, view.title)) {
                mainViews.remove(entry.getKey());
                continue;
            }
            Inventory inventory = player.getOpenInventory().getTopInventory();
            mainViews.put(entry.getKey(), renderMainInventory(player, inventory, view.title, view.page, view.filter));
        }
    }

    public void shutdown() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        mainViews.clear();
        claimViews.clear();
        duelViews.clear();
        duelScrollSessions.clear();
        clickCooldowns.clear();
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        MainView mainView = mainViews.get(player.getUniqueId());
        ClaimView claimView = claimViews.get(player.getUniqueId());
        DuelView duelView = duelViews.get(player.getUniqueId());

        if (mainView != null && !event.getView().getTitle().equals(mainView.title)) {
            mainViews.remove(player.getUniqueId());
            mainView = null;
        }
        if (claimView != null && !event.getView().getTitle().equals(claimView.title)) {
            claimViews.remove(player.getUniqueId());
            claimView = null;
        }
        if (duelView != null && !event.getView().getTitle().equals(duelView.title())) {
            duelViews.remove(player.getUniqueId());
            duelView = null;
        }
        if (mainView == null && claimView == null && duelView == null) {
            return;
        }

        event.setCancelled(true);
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }
        if (duelView != null || isClickTooFast(player)) {
            return;
        }

        if (mainView != null) {
            handleMainClick(player, event.getSlot(), mainView);
            return;
        }

        handleClaimClick(player, event.getSlot(), claimView);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (mainViews.containsKey(player.getUniqueId()) || claimViews.containsKey(player.getUniqueId()) || duelViews.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        mainViews.remove(player.getUniqueId());
        claimViews.remove(player.getUniqueId());
        duelViews.remove(player.getUniqueId());
        clickCooldowns.remove(player.getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        mainViews.remove(player.getUniqueId());
        claimViews.remove(player.getUniqueId());
        duelViews.remove(player.getUniqueId());
        clickCooldowns.remove(player.getUniqueId());
    }

    private MainView renderMainInventory(Player viewer, Inventory inventory, String title, int page, CoinflipFilter filter) {
        List<GameSession> allGames = manager.getGames(filter);
        int pageSize = GAME_SLOTS.size();
        int maxPage = Math.max(0, (allGames.size() - 1) / pageSize);
        int currentPage = Math.max(0, Math.min(page, maxPage));
        int from = currentPage * pageSize;
        int to = Math.min(allGames.size(), from + pageSize);
        List<GameSession> pageGames = from >= to ? List.of() : allGames.subList(from, to);
        Map<Integer, String> gamesBySlot = new HashMap<>();

        inventory.clear();
        fillFrame(inventory, true, filter);
        for (int index = 0; index < pageGames.size(); index++) {
            GameSession game = pageGames.get(index);
            int slot = GAME_SLOTS.get(index);
            inventory.setItem(slot, gameItem(viewer, game));
            gamesBySlot.put(slot, game.getId());
        }
        return new MainView(title, currentPage, maxPage, filter, gamesBySlot);
    }

    private void openClaim(Player player, CoinflipRewards rewards) {
        String title = color(config.getGui().getClaimTitle());
        Inventory inventory = Bukkit.createInventory(null, CLAIM_SIZE, title);
        fillFrame(inventory, false, CoinflipFilter.ALL);

        Map<Integer, CoinflipCurrency> currenciesBySlot = new HashMap<>();
        if (rewards.money() > 0.0) {
            inventory.setItem(MONEY_CLAIM_SLOT, item(config.getGui().getMoneyClaimItem(), Map.of(
                    "%amount%", manager.formatAmount(CoinflipCurrency.MONEY, rewards.money())
            )));
            currenciesBySlot.put(MONEY_CLAIM_SLOT, CoinflipCurrency.MONEY);
        }
        if (rewards.shards() > 0.0) {
            inventory.setItem(SHARDS_CLAIM_SLOT, item(config.getGui().getShardsClaimItem(), Map.of(
                    "%amount%", manager.formatAmount(CoinflipCurrency.SHARDS, rewards.shards())
            )));
            currenciesBySlot.put(SHARDS_CLAIM_SLOT, CoinflipCurrency.SHARDS);
        }
        if (currenciesBySlot.isEmpty()) {
            inventory.setItem(EMPTY_CLAIM_SLOT, item(config.getGui().getEmptyClaimItem(), Map.of()));
        }

        mainViews.remove(player.getUniqueId());
        duelViews.remove(player.getUniqueId());
        player.openInventory(inventory);
        playGuiSound(player);
        claimViews.put(player.getUniqueId(), new ClaimView(title, currenciesBySlot));
    }

    private void handleMainClick(Player player, int slot, MainView view) {
        if (slot == PREVIOUS_SLOT && view.page > 0) {
            openMain(player, view.page - 1, view.filter, true);
            return;
        }
        if (slot == NEXT_SLOT && view.page < view.maxPage) {
            openMain(player, view.page + 1, view.filter, true);
            return;
        }
        if (slot == SORT_SLOT) {
            openMain(player, 0, view.filter.next(), true);
            return;
        }
        if (slot == CLAIM_CLOSE_SLOT) {
            player.closeInventory();
            return;
        }

        String gameId = view.gamesBySlot.get(slot);
        if (gameId != null) {
            if (manager.isOwnOpenGame(player, gameId)) {
                manager.cancelGame(player, gameId);
                return;
            }
            manager.joinGame(player, gameId);
        }
    }

    private void handleClaimClick(Player player, int slot, ClaimView view) {
        if (slot == CLAIM_CLOSE_SLOT) {
            player.closeInventory();
            return;
        }

        CoinflipCurrency currency = view.currenciesBySlot.get(slot);
        if (currency != null) {
            manager.claim(player, currency);
        }
    }

    private void fillFrame(Inventory inventory, boolean main, CoinflipFilter filter) {
        if (!applyGuiTemplate(inventory)) {
            ItemStack orange = glass(Material.ORANGE_STAINED_GLASS_PANE);
            ItemStack yellow = glass(Material.YELLOW_STAINED_GLASS_PANE);
            ItemStack white = glass(Material.WHITE_STAINED_GLASS_PANE);

            for (int slot : List.of(0, 8, 36, 44)) inventory.setItem(slot, orange.clone());
            for (int slot : List.of(1, 7, 9, 17, 27, 35, 37, 43)) inventory.setItem(slot, yellow.clone());
            for (int slot : List.of(2, 3, 4, 5, 6, 18, 26, 38, 39, 41, 42)) inventory.setItem(slot, white.clone());
        }

        if (main) {
            inventory.setItem(INFO_SLOT, item(config.getGui().getInfoItem(), Map.of()));
            inventory.setItem(PREVIOUS_SLOT, item(config.getGui().getPreviousPageItem(), Map.of()));
            inventory.setItem(NEXT_SLOT, item(config.getGui().getNextPageItem(), Map.of()));
            inventory.setItem(SORT_SLOT, sortItem(filter));
        }
        inventory.setItem(CLAIM_CLOSE_SLOT, item(config.getGui().getCloseItem(), Map.of()));
    }

    private void fillDuelFrame(Inventory inventory) {
        if (!applyGuiTemplate(inventory)) {
            ItemStack orange = glass(Material.ORANGE_STAINED_GLASS_PANE);
            ItemStack yellow = glass(Material.YELLOW_STAINED_GLASS_PANE);
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                if (slot < 9 || slot >= 18 || slot == 9 || slot == 17) {
                    inventory.setItem(slot, slot % 2 == 0 ? orange.clone() : yellow.clone());
                }
            }
        }
    }

    private boolean applyGuiTemplate(Inventory inv) {
        if (inv == null) return false;
        HollyDataPaths.ensureResource(plugin, "guis/coinflip.yml");
        HollyDataPaths.ensureResource(plugin, "guis/template.yml");

        File guiFile = new File(plugin.getDataFolder(), "guis/coinflip.yml");
        YamlConfiguration guiConfig = null;

        if (guiFile.exists() && guiFile.length() > 0) {
            guiConfig = YamlConfiguration.loadConfiguration(guiFile);
        } else {
            InputStream stream = plugin.getResource("guis/coinflip.yml");
            if (stream != null) {
                guiConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
            }
        }

        ConfigurationSection section = null;
        if (guiConfig != null) {
            int size = inv.getSize();
            section = guiConfig.getConfigurationSection("template" + size);
            if (section == null) {
                section = guiConfig.getConfigurationSection("template");
            }
            if (section == null) {
                section = guiConfig.getConfigurationSection("template" + (size >= 54 ? "54" : (size >= 45 ? "45" : "27")));
            }
            if (section == null && hasMaterialKeys(guiConfig)) {
                section = guiConfig;
            }
        }

        if (section == null || section.getKeys(false).isEmpty()) {
            File templateFile = new File(plugin.getDataFolder(), "guis/template.yml");
            if (templateFile.exists() && templateFile.length() > 0) {
                guiConfig = YamlConfiguration.loadConfiguration(templateFile);
            } else {
                InputStream stream = plugin.getResource("guis/template.yml");
                if (stream != null) {
                    guiConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
                }
            }
            if (guiConfig != null) {
                int size = inv.getSize();
                section = guiConfig.getConfigurationSection("template" + size);
                if (section == null) {
                    section = guiConfig.getConfigurationSection(size >= 54 ? "template54" : (size >= 45 ? "template45" : "template27"));
                }
            }
        }

        if (section == null) return false;

        boolean applied = false;
        for (String materialName : section.getKeys(false)) {
            Material material = Material.matchMaterial(materialName);
            if (material == null || !material.isItem()) continue;

            ItemStack decoration = plugin.getGuiFactory() != null
                    ? plugin.getGuiFactory().createBackgroundItem(material)
                    : new ItemStack(material);

            for (int slot : section.getIntegerList(materialName)) {
                if (slot >= 0 && slot < inv.getSize()) {
                    inv.setItem(slot, decoration.clone());
                    applied = true;
                }
            }
        }
        return applied;
    }

    private boolean hasMaterialKeys(ConfigurationSection section) {
        if (section == null) return false;
        for (String key : section.getKeys(false)) {
            Material m = Material.matchMaterial(key);
            if (m != null && m.isItem()) {
                return true;
            }
        }
        return false;
    }

    private ItemStack sortItem(CoinflipFilter filter) {
        CoinflipConfig.Gui gui = config.getGui();
        return item(gui.getSortItem(), Map.of(
                "%all_line%", sortLine(filter, CoinflipFilter.ALL, gui.getAllSortName()),
                "%shards_line%", sortLine(filter, CoinflipFilter.SHARDS, gui.getShardsSortName()),
                "%money_line%", sortLine(filter, CoinflipFilter.MONEY, gui.getMoneySortName())
        ));
    }

    private String sortLine(CoinflipFilter current, CoinflipFilter option, String name) {
        String template = current == option ? config.getGui().getSelectedSortLine() : config.getGui().getUnselectedSortLine();
        return template.replace("%name%", name);
    }

    private ItemStack gameItem(Player viewer, GameSession game) {
        String name = manager.currencyName(game.getCurrency());
        String formattedAmt = manager.formatAmount(game.getCurrency(), game.getAmount());
        String symbol = game.getCurrency() == CoinflipCurrency.MONEY ? "$" : "odłamków";
        String amtWithSymbol = game.getCurrency() == CoinflipCurrency.MONEY ? (formattedAmt + "$") : (formattedAmt + " odłamków");

        Map<String, String> placeholders = Map.of(
                "%creator%", game.getCreatorName(),
                "%currency%", name,
                "%currency_name%", name,
                "%currency_symbol%", symbol,
                "%amount%", formattedAmt,
                "%amount_formatted%", amtWithSymbol
        );
        CoinflipConfig.GuiItem item = game.getCreatorUuid().equals(viewer.getUniqueId())
                ? config.getGui().getOwnGameHead()
                : config.getGui().getGameHead();
        return playerHead(game.getCreatorUuid(), game.getCreatorName(),
                replace(item.getName(), placeholders),
                replace(item.getLore(), placeholders));
    }

    private ItemStack item(CoinflipConfig.GuiItem settings, Map<String, String> placeholders) {
        Material material = settings.getMaterial() == null ? Material.STONE : settings.getMaterial();
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.setDisplayName(color(replace(settings.getName(), placeholders)));
        meta.setLore(TextUtil.fixColor(replace(settings.getLore() == null ? List.of() : settings.getLore(), placeholders)));
        if (settings.getCustomModelData() > 0) {
            meta.setCustomModelData(settings.getCustomModelData());
        }
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack playerHead(String owner, String name, List<String> lore) {
        return playerHead(null, owner, name, lore);
    }

    private ItemStack playerHead(UUID ownerUuid, String owner, String name, List<String> lore) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta == null) {
            return item;
        }
        OfflinePlayer offlinePlayer = ownerUuid == null ? Bukkit.getOfflinePlayer(owner) : Bukkit.getOfflinePlayer(ownerUuid);
        meta.setOwningPlayer(offlinePlayer);
        meta.setDisplayName(color(name));
        meta.setLore(TextUtil.fixColor(lore == null ? List.of() : lore));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack glass(Material material) {
        return named(material, " ", List.of());
    }

    private ItemStack named(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.setDisplayName(color(name));
        meta.setLore(TextUtil.fixColor(lore));
        item.setItemMeta(meta);
        return item;
    }

    private boolean isSameInventory(Player player, String title) {
        return player != null
                && player.isOnline()
                && player.getOpenInventory() != null
                && title.equals(player.getOpenInventory().getTitle());
    }

    private boolean isClickTooFast(Player player) {
        long now = System.currentTimeMillis();
        long allowedAt = clickCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (allowedAt > now) {
            return true;
        }
        clickCooldowns.put(player.getUniqueId(), now + 150L);
        return false;
    }

    private void playGuiSound(Player player) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
    }

    private void startRefreshTask() {
        int seconds = config.getSettings().getGuiRefreshSeconds();
        if (seconds <= 0) {
            return;
        }

        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshMainViews, Math.max(20L, seconds * 20L), Math.max(20L, seconds * 20L));
    }

    private String replace(String text, Map<String, String> placeholders) {
        String output = text == null ? "" : text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            output = output.replace(entry.getKey(), entry.getValue());
        }
        return output;
    }

    private List<String> replace(List<String> lines, Map<String, String> placeholders) {
        List<String> output = new ArrayList<>();
        for (String line : lines) {
            output.add(replace(line, placeholders));
        }
        return output;
    }

    private String color(String text) {
        return TextUtil.fixColor(text);
    }

    private record MainView(String title, int page, int maxPage, CoinflipFilter filter, Map<Integer, String> gamesBySlot) {
    }

    private record ClaimView(String title, Map<Integer, CoinflipCurrency> currenciesBySlot) {
    }

    private record DuelView(String title, String sessionId, boolean animating) {
    }

    private record DuelScrollSession(UUID creatorUuid, UUID opponentUuid, Runnable onInterrupt, AtomicBoolean resolved) {
    }
}
