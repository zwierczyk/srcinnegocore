package dev.grzybalson.managers;

import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.BungeeSpooferPlaceholders;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.MoneyAmountUtil;
import fr.mrmicky.fastboard.FastBoard;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class SidebarManager implements Listener {

    private static final int MAX_LINES = 15;
    private static final int MAX_TITLE_LENGTH = 128;
    private static final int MAX_TEAM_TEXT_LENGTH = 64;
    private static final String CONFIG_RESOURCE = "addons/tablist/tablist.yml";
    private static final String OBJECTIVE_NAME = "holly_sidebar";
    private static final String TEAM_PREFIX = "holly_sb_";
    private static final String PLACEHOLDER_BATCH_DELIMITER = "\u0001HOLLY_SIDEBAR_SPLIT\u0001";
    private static final int RENDERED_TEXT_CACHE_LIMIT = 4096;
    private static final String[] ENTRIES = buildEntries();

    private final HollyCore plugin;
    private final File configFile;
    private final Map<UUID, PlayerSidebar> sidebars = new ConcurrentHashMap<>();
    private final Set<UUID> dirtyPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> lastMoveDirtyMark = new ConcurrentHashMap<>();
    private final Map<String, RenderedText> renderedTextCache = new ConcurrentHashMap<>();
    private final Map<UUID, CustomBoardState> customBoards = new ConcurrentHashMap<>();
    private final AtomicBoolean updateQueued = new AtomicBoolean(false);
    private final AtomicBoolean fastBoardWarningLogged = new AtomicBoolean(false);
    private final AtomicBoolean boardDeleteWarningLogged = new AtomicBoolean(false);
    private int periodicRefreshCursor;

    private BukkitTask updateTask;
    private boolean enabled;
    private boolean papiEnabled;
    private volatile boolean fastBoardAvailable = true;
    private long updateIntervalTicks;
    private long moveUpdateIntervalMillis;
    private long joinDelayTicks;
    private int playersPerTick;
    private boolean periodicRefresh;
    private boolean updateOnMove;
    private boolean autoShadowEnabled;
    private String sidebarTitle;
    private String autoShadowColor = "#000000C0";
    private boolean showClanSection = true;
    private List<String> noClanLines = List.of();
    private List<String> clanLines = List.of();
    private boolean titleStatic;
    private boolean noClanLinesStatic;
    private boolean clanLinesStatic;
    private boolean usesComponentOnlyFormatting;
    private RenderedText renderedStaticTitle = RenderedText.empty();
    private List<RenderedText> renderedStaticNoClanLines = List.of();
    private List<RenderedText> renderedStaticClanLines = List.of();

    public SidebarManager(HollyCore plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), CONFIG_RESOURCE);
    }

    public void start() {
        loadConfig();
        if (!enabled) {
            clearAll();
            return;
        }

        schedule();
        markAllDirty();
        queueUpdateAll();
    }

    public void reload() {
        stopTask();
        updateQueued.set(false);
        loadConfig();

        if (!enabled) {
            clearAll();
            return;
        }

        schedule();
        markAllDirty();
        queueUpdateAll();
    }

    public void shutdown() {
        stopTask();
        clearAll();
    }

    /** HollySpoofer /spf loadtest true — odswieza scoreboard jak przy pelnym online. */
    public void runLoadTestPass(int botCount) {
        if (!enabled || botCount <= 0 || !plugin.isEnabled()) {
            return;
        }
        // Realistycznie: odśwież tylko online viewerów (bot nie ma scoreboardu).
        // Koszt skaluje się z botami przez częstsze dirty mark (jakby więcej graczy
        // wymuszało częstszy refresh), ale bez 48x updatePlayer na 1 osobę.
        markAllDirty();
        queueUpdateAll();
    }

    private void schedule() {
        updateTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
            plugin,
            this::queueUpdateAll,
            updateIntervalTicks,
            updateIntervalTicks
        );
    }

    private void queueUpdateAll() {
        if (!plugin.isEnabled()) {
            return;
        }
        if (!periodicRefresh && dirtyPlayers.isEmpty()) {
            return;
        }
        if (!updateQueued.compareAndSet(false, true)) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                if (periodicRefresh) {
                    markPeriodicDirtyBatch();
                }
                if (dirtyPlayers.isEmpty()) {
                    updateQueued.set(false);
                    return;
                }
                updateAll();
            } catch (RuntimeException exception) {
                updateQueued.set(false);
                throw exception;
            }
        });
    }

    /**
     * Co cykl timera oznaczamy wszystkich (lub dużą falę) jako dirty —
     * inaczej przy małym batchu pełna rotacja trwała 20–40s.
     */
    private void markPeriodicDirtyBatch() {
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (online.isEmpty()) {
            return;
        }
        // Do ~120 online: wszyscy co cykl (~update-seconds). Wyżej: fala tak, by domknąć w 1–2 cyklach.
        if (online.size() <= 120) {
            for (Player player : online) {
                dirtyPlayers.add(player.getUniqueId());
            }
            periodicRefreshCursor = 0;
            return;
        }
        int batch = Math.max(24, online.size() / 2);
        for (int i = 0; i < batch; i++) {
            int index = (periodicRefreshCursor + i) % online.size();
            dirtyPlayers.add(online.get(index).getUniqueId());
        }
        periodicRefreshCursor = (periodicRefreshCursor + batch) % online.size();
    }

    private void updateAll() {
        if (!enabled) {
            updateQueued.set(false);
            return;
        }

        Player[] players = drainDirtyPlayers();
        updateBatch(players, 0);
    }

    private void updateBatch(Player[] players, int startIndex) {
        if (!enabled) {
            updateQueued.set(false);
            return;
        }

        int endIndex = Math.min(players.length, startIndex + effectivePlayersPerTick());
        for (int i = startIndex; i < endIndex; i++) {
            try {
                updatePlayer(players[i]);
            } catch (Throwable throwable) {
                plugin.getLogger().warning("Blad sidebaru dla " + players[i].getName() + ": " + describeThrowable(throwable));
            }
        }

        if (endIndex >= players.length) {
            updateQueued.set(false);
            if (!dirtyPlayers.isEmpty()) {
                queueUpdateAll();
            }
            return;
        }

        // Rozłóż PAPI na całe okno update-seconds — nie dumpuj 40 graczy w 4 tickach = spike co 3s.
        long delay = sidebarBatchDelayTicks(players.length);
        // Bez tego wyjatek w kontynuacji zostawia updateQueued=true i sidebary zamarzaja do reloadu.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                updateBatch(players, endIndex);
            } catch (Throwable throwable) {
                updateQueued.set(false);
                plugin.getLogger().warning("Partia sidebaru przerwana: " + describeThrowable(throwable));
            }
        }, delay);
    }

    private long sidebarBatchDelayTicks(int batchSize) {
        if (batchSize <= 0) {
            return 1L;
        }
        // Cel: rozłożyć całą falę na ~80% interwału (np. 3s → ~2.4s).
        long window = Math.max(20L, (long) (updateIntervalTicks * 0.8));
        int perTick = Math.max(1, effectivePlayersPerTick());
        int steps = Math.max(1, (batchSize + perTick - 1) / perTick);
        return Math.max(1L, Math.min(5L, window / steps));
    }

    private Player[] drainDirtyPlayers() {
        List<Player> players = new ArrayList<>(dirtyPlayers.size());
        for (UUID uuid : dirtyPlayers.toArray(new UUID[0])) {
            if (!dirtyPlayers.remove(uuid)) {
                continue;
            }

            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                players.add(player);
            }
        }
        return players.toArray(new Player[0]);
    }

    /**
     * Małe batche + delay między nimi = brak spike’a co update-seconds.
     * Pełna rotacja nadal mieści się w oknie interwału.
     */
    private int effectivePlayersPerTick() {
        int online = Bukkit.getOnlinePlayers().size();
        long window = Math.max(20L, (long) (updateIntervalTicks * 0.8));
        // Ile graczy/tick, żeby skończyć w oknie przy delay 1–2t.
        int spread = (int) Math.ceil(online / Math.max(4.0, window / 2.0));
        int capped = Math.max(2, Math.min(playersPerTick, Math.max(2, spread)));
        if (online > 200) {
            return Math.min(capped, 8);
        }
        if (online > 80) {
            return Math.min(capped, 6);
        }
        return Math.min(capped, 5);
    }

    private void markAllDirty() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            dirtyPlayers.add(player.getUniqueId());
        }
    }

    public void refreshPlayer(UUID uuid) {
        if (uuid != null) {
            dirtyPlayers.add(uuid);
            queueUpdateAll();
        }
    }

    /**
     * Hypestar-style: podczas bossa podmienia scoreboard na custom board.
     * Odświeżaj okresowo (enableCustomBoard ma TTL ~10s).
     */
    public void setCustomBoard(Player player, List<String> lines) {
        setCustomBoard(player, null, lines);
    }

    public void setCustomBoard(Player player, String title, List<String> lines) {
        if (player == null || lines == null) {
            return;
        }
        customBoards.put(player.getUniqueId(), new CustomBoardState(
            title,
            List.copyOf(lines),
            System.currentTimeMillis() + 10_000L
        ));
        dirtyPlayers.add(player.getUniqueId());
        queueUpdateAll();
    }

    public void clearCustomBoard(Player player) {
        if (player == null) {
            return;
        }
        if (customBoards.remove(player.getUniqueId()) != null) {
            dirtyPlayers.add(player.getUniqueId());
            queueUpdateAll();
        }
    }

    public void clearAllCustomBoards() {
        if (customBoards.isEmpty()) {
            return;
        }
        customBoards.clear();
        markAllDirty();
        queueUpdateAll();
    }

    public boolean hasCustomBoard(UUID uuid) {
        CustomBoardState state = customBoards.get(uuid);
        return state != null && state.expiresAt() > System.currentTimeMillis();
    }

    private void updatePlayer(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        CustomBoardState custom = customBoards.get(player.getUniqueId());
        if (custom != null && custom.expiresAt() <= System.currentTimeMillis()) {
            customBoards.remove(player.getUniqueId(), custom);
            custom = null;
        }

        List<String> template;
        boolean linesStatic;
        List<RenderedText> staticLines;
        if (custom != null) {
            template = custom.lines();
            linesStatic = false;
            staticLines = List.of();
        } else {
            if (!showClanSection) {
                // Gdy show-clan-section: false, użyj linii bez sekcji klanu (noClanLines)
                template = noClanLines;
                linesStatic = noClanLinesStatic;
                staticLines = renderedStaticNoClanLines;
            } else {
                // Gdy show-clan-section: true, sprawdź czy gracz ma klan
                boolean playerHasClan = hasClan(player);
                template = playerHasClan ? clanLines : noClanLines;
                linesStatic = playerHasClan ? clanLinesStatic : noClanLinesStatic;
                staticLines = playerHasClan ? renderedStaticClanLines : renderedStaticNoClanLines;
            }
        }

        RenderedSidebar rendered = renderSidebar(player, template, linesStatic, staticLines);
        RenderedText title = rendered.title();
        if (custom != null && custom.title() != null && !custom.title().isBlank()) {
            title = renderParsedText(parsePlaceholders(player, custom.title()));
        }
        List<RenderedText> lines = rendered.lines();
        boolean needsComponentBoard = autoShadowEnabled || usesComponentOnlyFormatting || rendered.needsComponentBoard();
        if (!needsComponentBoard && hasComponentOnlyFormatting(title.raw())) {
            needsComponentBoard = true;
        }
        if (!needsComponentBoard && (containsPrivateUseGlyph(title.raw()) || lines.stream().anyMatch(line -> containsPrivateUseGlyph(line.raw())))) {
            needsComponentBoard = true;
        }

        PlayerSidebar sidebar = sidebars.get(player.getUniqueId());
        if (sidebar == null) {
            sidebar = createSidebar(player, needsComponentBoard);
            if (sidebar != null) {
                sidebars.put(player.getUniqueId(), sidebar);
            }
        } else if (needsComponentBoard && sidebar.board.isFastBoard()) {
            sidebar.board.delete();
            BoardAdapter componentBoard = createBukkitBoard(player);
            if (componentBoard == null) {
                return;
            }
            sidebar.board = componentBoard;
            sidebar.lastTitle = RenderedText.empty();
            sidebar.lastLines = List.of();
        }
        if (sidebar == null) {
            return;
        }

        try {
            applySidebarUpdate(sidebar, title, lines);
        } catch (Throwable throwable) {
            handleBoardUpdateFailure(player, sidebar, title, lines, throwable);
        }
    }

    private PlayerSidebar createSidebar(Player player, boolean needsComponentBoard) {
        BoardAdapter board = needsComponentBoard ? null : createFastBoard(player);
        if (board == null) {
            board = createBukkitBoard(player);
        }
        return board == null ? null : new PlayerSidebar(board);
    }

    private BoardAdapter createFastBoard(Player player) {
        if (!fastBoardAvailable) {
            return null;
        }

        try {
            return new FastBoardAdapter(new FastBoard(player));
        } catch (Throwable throwable) {
            fastBoardAvailable = false;
            logFastBoardFallback(throwable);
            return null;
        }
    }

    private BoardAdapter createBukkitBoard(Player player) {
        if (Bukkit.getScoreboardManager() == null) {
            return null;
        }
        return new BukkitBoardAdapter(player);
    }

    private void applySidebarUpdate(PlayerSidebar sidebar, RenderedText title, List<RenderedText> lines) {
        if (!title.equals(sidebar.lastTitle)) {
            sidebar.board.updateTitle(title);
            sidebar.lastTitle = title;
        }

        if (!lines.equals(sidebar.lastLines)) {
            sidebar.board.updateLines(lines);
            sidebar.lastLines = lines;
        }
    }

    private void handleBoardUpdateFailure(Player player, PlayerSidebar sidebar, RenderedText title, List<RenderedText> lines, Throwable throwable) {
        if (!sidebar.board.isFastBoard()) {
            plugin.getLogger().warning("Nie udalo sie zaktualizowac sidebaru dla " + player.getName() + ": " + describeThrowable(throwable));
            return;
        }

        fastBoardAvailable = false;
        logFastBoardFallback(throwable);
        sidebar.board.delete();

        BoardAdapter fallback = createBukkitBoard(player);
        if (fallback == null) {
            return;
        }

        sidebar.board = fallback;
        sidebar.lastTitle = RenderedText.empty();
        sidebar.lastLines = List.of();
        applySidebarUpdate(sidebar, title, lines);
    }

    private void logFastBoardFallback(Throwable throwable) {
        if (fastBoardWarningLogged.compareAndSet(false, true)) {
            plugin.getLogger().warning("FastBoard nie jest kompatybilny z tym silnikiem serwera, uzywam fallbacku Bukkit scoreboard. Powod: " + describeThrowable(throwable));
        }
    }

    /** Jednorazowy log — przy shutdown z 200 graczami nie chcemy 200 stacktrace'ow. */
    private void logBoardDeleteFailure(Throwable throwable) {
        if (boardDeleteWarningLogged.compareAndSet(false, true)) {
            try {
                plugin.getLogger().warning("Nie udalo sie usunac sidebaru gracza: " + describeThrowable(throwable));
            } catch (Throwable ignored) {
            }
        }
    }

    private String describeThrowable(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null) {
            root = root.getCause();
        }

        String message = root.getMessage();
        return root.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
    }

    private List<RenderedText> renderLines(Player player, List<String> template) {
        int size = Math.min(template.size(), MAX_LINES);
        List<RenderedText> lines = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            lines.add(renderText(player, template.get(i)));
        }
        return List.copyOf(lines);
    }

    private RenderedText renderText(Player player, String text) {
        String raw = parsePlaceholders(player, text);
        return renderParsedText(raw);
    }

    private RenderedText renderParsedText(String raw) {
        String safeRaw = raw == null ? "" : raw;
        RenderedText cached = renderedTextCache.get(safeRaw);
        if (cached != null) {
            return cached;
        }

        RenderedText rendered = new RenderedText(
            safeRaw,
            null,
            autoShadowEnabled ? ColorUtils.colorizeComponentWithShadow(safeRaw, autoShadowColor) : null
        );
        if (renderedTextCache.size() >= RENDERED_TEXT_CACHE_LIMIT) {
            renderedTextCache.clear();
        }
        RenderedText previous = renderedTextCache.putIfAbsent(safeRaw, rendered);
        return previous != null ? previous : rendered;
    }

    private RenderedSidebar renderSidebar(Player player, List<String> template, boolean linesStatic, List<RenderedText> staticLines) {
        if (titleStatic && linesStatic) {
            return new RenderedSidebar(renderedStaticTitle, staticLines, false);
        }

        int lineCount = Math.min(template.size(), MAX_LINES);
        List<String> texts = new ArrayList<>((titleStatic ? 0 : 1) + (linesStatic ? 0 : lineCount));
        if (!titleStatic) {
            texts.add(sidebarTitle);
        }
        if (!linesStatic) {
            for (int i = 0; i < lineCount; i++) {
                texts.add(template.get(i));
            }
        }

        List<String> parsed = parsePlaceholdersBatch(player, texts);
        int index = 0;
        RenderedText title = titleStatic ? renderedStaticTitle : renderParsedText(parsed.get(index++));
        boolean needsComponentBoard = !usesComponentOnlyFormatting && hasComponentOnlyFormatting(title.raw());
        List<RenderedText> lines;
        if (linesStatic) {
            lines = staticLines;
        } else {
            List<RenderedText> renderedLines = new ArrayList<>(lineCount);
            for (int i = 0; i < lineCount; i++) {
                RenderedText line = renderParsedText(parsed.get(index++));
                if (!needsComponentBoard && !usesComponentOnlyFormatting && hasComponentOnlyFormatting(line.raw())) {
                    needsComponentBoard = true;
                }
                renderedLines.add(line);
            }
            lines = List.copyOf(renderedLines);
        }
        return new RenderedSidebar(title, lines, needsComponentBoard);
    }

    private boolean hasClan(Player player) {
        if (plugin.getHollyPlaceholderExpansion() != null) {
            String value = plugin.getHollyPlaceholderExpansion().onRequest(player, "has_clan");
            return Boolean.parseBoolean(value);
        }

        if (!papiEnabled) {
            return false;
        }

        String value = PlaceholderAPI.setPlaceholders(player, "%holly_has_clan%");
        return Boolean.parseBoolean(value);
    }

    private String parsePlaceholders(Player player, String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        text = normalizeCorePlaceholders(player, text);
        if (player == null || !hasPlaceholder(text)) {
            return text;
        }

        String parsed = text.replace("%player_name%", player.getName());
        if (papiEnabled) {
            parsed = BungeeSpooferPlaceholders.apply(player, parsed);
            parsed = PlaceholderAPI.setPlaceholders(player, parsed);
        }
        return parsed;
    }

    private List<String> parsePlaceholdersBatch(Player player, List<String> texts) {
        if (texts.isEmpty()) {
            return List.of();
        }

        boolean hasAnyPlaceholder = false;
        List<String> localResult = new ArrayList<>(texts.size());
        if (player != null) {
            boolean localOnly = true;
            for (String text : texts) {
                if (text == null || text.isEmpty()) {
                    localResult.add("");
                    continue;
                }
                text = normalizeCorePlaceholders(player, text);
                if (!hasPlaceholder(text)) {
                    localResult.add(text);
                    continue;
                }

                hasAnyPlaceholder = true;
                PlaceholderParseResult parsed = parseLocalPlaceholders(player, text);
                if (!parsed.resolvedAll()) {
                    localOnly = false;
                    break;
                }
                localResult.add(parsed.value());
            }
            if (localOnly) {
                return List.copyOf(localResult);
            }
        } else {
            for (String text : texts) {
                text = normalizeCorePlaceholders(null, text);
                if (hasPlaceholder(text)) {
                    hasAnyPlaceholder = true;
                }
                localResult.add(text == null ? "" : text);
            }
        }

        if (player == null || !hasAnyPlaceholder) {
            return List.copyOf(localResult);
        }

        String playerName = player.getName();
        StringBuilder joined = new StringBuilder(texts.size() * 32);
        for (int i = 0; i < texts.size(); i++) {
            if (i > 0) {
                joined.append(PLACEHOLDER_BATCH_DELIMITER);
            }
            String text = normalizeCorePlaceholders(player, texts.get(i));
            joined.append(text == null ? "" : text.replace("%player_name%", playerName));
        }

        String parsed = joined.toString();
        if (papiEnabled) {
            parsed = BungeeSpooferPlaceholders.apply(player, parsed);
            parsed = PlaceholderAPI.setPlaceholders(player, parsed);
        }

        return splitPlaceholderBatch(parsed, texts.size());
    }

    private PlaceholderParseResult parseLocalPlaceholders(Player player, String text) {
        StringBuilder result = null;
        int cursor = 0;
        while (true) {
            int start = text.indexOf('%', cursor);
            if (start < 0) {
                if (result == null) {
                    return new PlaceholderParseResult(text, true);
                }
                result.append(text, cursor, text.length());
                return new PlaceholderParseResult(result.toString(), true);
            }

            int end = text.indexOf('%', start + 1);
            if (end < 0) {
                return new PlaceholderParseResult(text, false);
            }

            String placeholder = text.substring(start + 1, end);
            String replacement = resolveLocalPlaceholder(player, placeholder);
            if (replacement == null) {
                return new PlaceholderParseResult(text, false);
            }

            if (result == null) {
                result = new StringBuilder(text.length() + 16);
            }
            result.append(text, cursor, start).append(replacement);
            cursor = end + 1;
        }
    }

    private String resolveLocalPlaceholder(Player player, String placeholder) {
        if (placeholder.equals("player_name")) {
            return player.getName();
        }
        if (placeholder.equalsIgnoreCase("core_vpln")) {
            if (plugin.getWalletManager() != null) {
                return String.format(java.util.Locale.US, "%.2f", plugin.getWalletManager().getCachedBalance(player.getUniqueId()));
            }
            return "0.00";
        }
        if (placeholder.equalsIgnoreCase("core_format") || placeholder.equalsIgnoreCase("core_afk")) {
            return plugin.getTablistManager() != null ? plugin.getTablistManager().getIdleAfkMarker(player) : "";
        }
        if (placeholder.equalsIgnoreCase("core_prefix")) {
            return "";
        }
        if (placeholder.startsWith("core_")) {
            if (plugin.getHollyPlaceholderExpansion() == null) {
                return null;
            }
            String key = placeholder.substring(5);
            if (key.equalsIgnoreCase("vpln")) {
                return null;
            }
            String value = plugin.getHollyPlaceholderExpansion().onRequest(player, key);
            return value == null ? "" : value;
        }
        if (placeholder.startsWith("holly_")) {
            if (plugin.getHollyPlaceholderExpansion() == null) {
                return null;
            }
            String value = plugin.getHollyPlaceholderExpansion().onRequest(player, placeholder.substring(6));
            return value == null ? "" : value;
        }
        if (placeholder.startsWith("hollyservice_")) {
            if (plugin.getHollyPlaceholderExpansion() == null) {
                return null;
            }
            String value = plugin.getHollyPlaceholderExpansion().onRequest(player, placeholder.substring(13));
            return value == null ? "" : value;
        }
        if (placeholder.startsWith("portfel_")) {
            String key = placeholder.substring(8);
            if ((key.equals("money") || key.equals("balance")) && plugin.getWalletManager() != null) {
                return MoneyAmountUtil.formatCompact(plugin.getWalletManager().getCachedBalance(player.getUniqueId()));
            }
            return "0.00";
        }
        return null;
    }

    private String normalizeCorePlaceholders(Player player, String text) {
        if (text == null || text.isEmpty() || text.indexOf('%') < 0) {
            return text == null ? "" : text;
        }

        String parsed = text
            .replace("%core_vpln%", "%portfel_money%")
            .replace("%core_format%", plugin.getTablistManager() != null ? plugin.getTablistManager().getIdleAfkMarker(player) : "")
            .replace("%core_afk%", plugin.getTablistManager() != null ? plugin.getTablistManager().getIdleAfkMarker(player) : "")
            .replace("%core_prefix%", "");
        parsed = parsed.replace("%core_", "%holly_");
        parsed = parsed.replace("%hollyservice_", "%holly_");
        parsed = parsed.replace("%service_", "%holly_");
        return parsed;
    }

    private List<String> splitPlaceholderBatch(String parsed, int expectedParts) {
        List<String> result = new ArrayList<>(expectedParts);
        int cursor = 0;
        for (int i = 1; i < expectedParts; i++) {
            int next = parsed.indexOf(PLACEHOLDER_BATCH_DELIMITER, cursor);
            if (next < 0) {
                result.add(parsed.substring(cursor));
                while (result.size() < expectedParts) {
                    result.add("");
                }
                return List.copyOf(result);
            }
            result.add(parsed.substring(cursor, next));
            cursor = next + PLACEHOLDER_BATCH_DELIMITER.length();
        }
        result.add(cursor <= parsed.length() ? parsed.substring(cursor) : "");
        return List.copyOf(result);
    }


    private static String compactLegacyColors(String text) {
        if (text == null || text.isEmpty() || text.indexOf(ChatColor.COLOR_CHAR) < 0) {
            return text == null ? "" : text;
        }

        StringBuilder result = new StringBuilder(text.length());
        String activeColor = "";
        boolean[] activeFormats = new boolean[128];

        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if (current != ChatColor.COLOR_CHAR || i + 1 >= text.length()) {
                result.append(current);
                continue;
            }

            char code = Character.toLowerCase(text.charAt(i + 1));
            if (code == 'x' && i + 13 < text.length() && isHexColorSequence(text, i)) {
                String color = text.substring(i, i + 14).toLowerCase();
                if (!color.equals(activeColor)) {
                    result.append(text, i, i + 14);
                    activeColor = color;
                    activeFormats = new boolean[128];
                }
                i += 13;
                continue;
            }

            if (isLegacyColor(code)) {
                String color = "§" + code;
                if (!color.equals(activeColor)) {
                    result.append(ChatColor.COLOR_CHAR).append(code);
                    activeColor = color;
                    activeFormats = new boolean[128];
                }
                i++;
                continue;
            }

            if (code == 'r') {
                if (!activeColor.isEmpty() || hasActiveFormat(activeFormats)) {
                    result.append(ChatColor.COLOR_CHAR).append(code);
                    activeColor = "";
                    activeFormats = new boolean[128];
                }
                i++;
                continue;
            }

            if (isFormatCode(code)) {
                if (!activeFormats[code]) {
                    result.append(ChatColor.COLOR_CHAR).append(code);
                    activeFormats[code] = true;
                }
                i++;
                continue;
            }

            result.append(ChatColor.COLOR_CHAR).append(text.charAt(i + 1));
            i++;
        }
        return result.toString();
    }

    private static boolean isLegacyColor(char code) {
        return (code >= '0' && code <= '9') || (code >= 'a' && code <= 'f');
    }

    private static boolean isFormatCode(char code) {
        return code >= 0 && code < 128 && (code == 'k' || code == 'l' || code == 'm' || code == 'n' || code == 'o');
    }

    private static boolean hasActiveFormat(boolean[] activeFormats) {
        return activeFormats['k'] || activeFormats['l'] || activeFormats['m'] || activeFormats['n'] || activeFormats['o'];
    }

    private String toBukkitObjectiveTitle(String title) {
        if (title == null || title.isEmpty()) {
            return "";
        }
        if (title.length() <= MAX_TITLE_LENGTH) {
            return title;
        }

        int safeEnd = findSafeSplitIndex(title, MAX_TITLE_LENGTH);
        String trimmed = title.substring(0, safeEnd);

        if (trimmed.length() <= MAX_TITLE_LENGTH) {
            return trimmed;
        }
        return trimmed.substring(0, Math.min(trimmed.length(), MAX_TITLE_LENGTH));
    }

    private void setTeamText(Team team, RenderedText text) {
        RenderedText safeText = text == null ? RenderedText.empty() : text;
        team.prefix(safeText.component());
        // Suffix ustawiamy raz przy rejestracji teamu — tu tylko dublowal pakiet TEAMS na kazda zmieniona linie.
    }

    private int findSafeSplitIndex(String text, int maxLength) {
        int end = Math.min(maxLength, text.length());
        if (end >= text.length()) {
            return end;
        }
        if (end > 0 && text.charAt(end - 1) == ChatColor.COLOR_CHAR) {
            return end - 1;
        }

        int colorIndex = text.lastIndexOf(ChatColor.COLOR_CHAR, end - 1);
        if (colorIndex >= 0 && colorIndex + 1 >= end) {
            return colorIndex;
        }
        if (colorIndex >= 0 && isHexColorSequence(text, colorIndex)) {
            int hexEnd = colorIndex + 14;
            if (end > colorIndex && end < hexEnd) {
                return colorIndex;
            }
        }

        return end;
    }

    private static boolean isHexColorSequence(String text, int index) {
        if (index + 13 >= text.length() || Character.toLowerCase(text.charAt(index + 1)) != 'x') {
            return false;
        }

        for (int i = index + 2; i <= index + 12; i += 2) {
            if (text.charAt(i) != ChatColor.COLOR_CHAR || !isHexDigit(text.charAt(i + 1))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isHexDigit(char value) {
        return (value >= '0' && value <= '9')
            || (value >= 'a' && value <= 'f')
            || (value >= 'A' && value <= 'F');
    }

    private void loadConfig() {
        ensureConfigFile();

        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        String root = config.isConfigurationSection("scoreboard") ? "scoreboard." : "";

        enabled = getBoolean(config, true, root + "enabled", "enabled");
        papiEnabled = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
        // Domyślnie 3s — placeholdery (hajs, kille, punkty) mają być świeże, nie „co 20–40s”.
        updateIntervalTicks = Math.max(20L, getSecondsAsTicks(config, 60L, root + "update-seconds", "update-seconds",
            root + "update-interval-ticks", "update-interval-ticks"));
        moveUpdateIntervalMillis = Math.max(1000L, getLong(config, 40L, root + "move-update-interval-ticks", "move-update-interval-ticks") * 50L);
        joinDelayTicks = Math.max(1L, getLong(config, 5L, root + "join-delay-ticks", "join-delay-ticks"));
        playersPerTick = Math.max(1, Math.min(64, (int) getLong(config, 24L,
            root + "update-batch-size", "update-batch-size", root + "players-per-tick", "players-per-tick")));
        periodicRefresh = getBoolean(config, true, root + "periodic-refresh", "periodic-refresh");
        updateOnMove = getBoolean(config, false, root + "update-on-move", "update-on-move");
        autoShadowEnabled = getBoolean(config, true, root + "auto-shadow.enabled", "scoreboard.auto-shadow.enabled",
            "tab.auto-shadow.enabled", "auto-shadow.enabled");
        autoShadowColor = getString(config, "#000000C0", root + "auto-shadow.color", "scoreboard.auto-shadow.color",
            "tab.auto-shadow.color", "auto-shadow.color");
        showClanSection = getBoolean(config, true, root + "show-clan-section", "show-clan-section");
        sidebarTitle = getString(config, "&8x &b&lUNIMC.PL &8x", root + "title", root + "sidebarTitle", "sidebarTitle");

        if (config.isList(root + "lines")) {
            List<String> baseLines = config.getStringList(root + "lines");
            noClanLines = readLines(expandClanSection(baseLines, config.getStringList(root + "no-clan-lines")));
            clanLines = readLines(expandClanSection(baseLines, config.getStringList(root + "in-clan-lines")));
        } else {
            noClanLines = readLines(config.getStringList("scoreboardLines"));
            clanLines = readLines(config.getStringList("scoreboardGuildLines"));
        }
        renderedTextCache.clear();
        prepareStaticTemplates();
    }

    private List<String> expandClanSection(List<String> baseLines, List<String> sectionLines) {
        List<String> result = new ArrayList<>();
        boolean replaced = false;
        for (String line : baseLines) {
            if (line != null && line.contains("{clan_section}")) {
                replaced = true;
                for (String sectionLine : sectionLines) {
                    result.add(normalizeConfigPlaceholders(sectionLine));
                }
                continue;
            }
            result.add(normalizeConfigPlaceholders(line));
        }
        if (!replaced) {
            for (String sectionLine : sectionLines) {
                result.add(normalizeConfigPlaceholders(sectionLine));
            }
        }
        return result;
    }

    private String normalizeConfigPlaceholders(String line) {
        if (line == null || line.isEmpty()) {
            return "";
        }
        return collapseDuplicateShadowTags(line)
            .replace("{player_points_position}", "%core_position%")
            .replace("{clan_name}", "%core_clan_name%")
            .replace("{clan_points}", "%core_clan_points%")
            .replace("{clan_points_position}", "%core_clan_position%")
            .replace("{BOSS_NEXT}", "%holly_boss_next%")
            .replace("{BOSS_TIME}", "%holly_boss_time%")
            .replace("{BOSS_NAME}", "%holly_boss_name%");
    }

    /**
     * Zagnieżdżone {@code <shadow>} bez zamknięcia zjadają emotki PUA w MiniMessage.
     * Zostawiamy jeden otwierający tag wokół całej linii.
     */
    private String collapseDuplicateShadowTags(String line) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?i)<shadow:[^>]*>").matcher(line);
        if (!matcher.find()) {
            return line;
        }
        String open = matcher.group();
        String body = line.replaceAll("(?i)</?shadow(?::[^>]*)?>", "");
        return open + body + "</shadow>";
    }

    private void prepareStaticTemplates() {
        titleStatic = !hasPlaceholder(sidebarTitle);
        noClanLinesStatic = noClanLines.stream().noneMatch(this::hasPlaceholder);
        clanLinesStatic = clanLines.stream().noneMatch(this::hasPlaceholder);
        usesComponentOnlyFormatting = autoShadowEnabled
            || hasComponentOnlyFormatting(sidebarTitle)
            || noClanLines.stream().anyMatch(this::hasComponentOnlyFormatting)
            || clanLines.stream().anyMatch(this::hasComponentOnlyFormatting);
        renderedStaticTitle = titleStatic ? renderText(null, sidebarTitle) : RenderedText.empty();
        renderedStaticNoClanLines = noClanLinesStatic ? renderStaticLines(noClanLines) : List.of();
        renderedStaticClanLines = clanLinesStatic ? renderStaticLines(clanLines) : List.of();
    }

    private List<RenderedText> renderStaticLines(List<String> lines) {
        List<RenderedText> rendered = new ArrayList<>(Math.min(lines.size(), MAX_LINES));
        for (int i = 0; i < Math.min(lines.size(), MAX_LINES); i++) {
            rendered.add(renderText(null, lines.get(i)));
        }
        return List.copyOf(rendered);
    }

    private boolean hasPlaceholder(String text) {
        return text != null && text.indexOf('%') >= 0;
    }

    private boolean hasComponentOnlyFormatting(String text) {
        if (text == null) {
            return false;
        }
        return containsIgnoreCase(text, "<shadow:") || containsIgnoreCase(text, "<!shadow");
    }

    private boolean containsPrivateUseGlyph(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            char value = text.charAt(i);
            if (value >= '\uE000' && value <= '\uF8FF') {
                return true;
            }
        }
        return false;
    }

    private boolean containsIgnoreCase(String text, String pattern) {
        int max = text.length() - pattern.length();
        for (int i = 0; i <= max; i++) {
            if (text.regionMatches(true, i, pattern, 0, pattern.length())) {
                return true;
            }
        }
        return false;
    }

    private List<String> readLines(List<String> lines) {
        if (lines.size() <= MAX_LINES) {
            return List.copyOf(lines);
        }
        return List.copyOf(lines.subList(0, MAX_LINES));
    }

    private boolean getBoolean(YamlConfiguration config, boolean def, String... paths) {
        for (String path : paths) {
            if (config.contains(path)) {
                return config.getBoolean(path, def);
            }
        }
        return def;
    }

    private long getLong(YamlConfiguration config, long def, String... paths) {
        for (String path : paths) {
            if (config.contains(path)) {
                return config.getLong(path, def);
            }
        }
        return def;
    }

    private long getSecondsAsTicks(YamlConfiguration config, long defTicks, String secondsPath, String legacySecondsPath, String ticksPath, String legacyTicksPath) {
        for (String path : List.of(secondsPath, legacySecondsPath)) {
            if (config.contains(path)) {
                return config.getLong(path, defTicks / 20L) * 20L;
            }
        }
        return getLong(config, defTicks, ticksPath, legacyTicksPath);
    }

    private String getString(YamlConfiguration config, String def, String... paths) {
        for (String path : paths) {
            String value = config.getString(path);
            if (value != null) {
                return value;
            }
        }
        return def;
    }

    private void ensureConfigFile() {
        if (configFile.exists()) {
            return;
        }
        plugin.saveResource(CONFIG_RESOURCE, false);
    }

    private void stopTask() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
    }

    private void clearAll() {
        for (PlayerSidebar sidebar : sidebars.values()) {
            // Jeden blad kasowania nie moze przerwac petli ani wyleciec z shutdown() — dalej jest zapis danych graczy.
            try {
                if (!sidebar.board.isDeleted()) {
                    sidebar.board.delete();
                }
            } catch (Throwable throwable) {
                logBoardDeleteFailure(throwable);
            }
        }
        sidebars.clear();
        dirtyPlayers.clear();
        lastMoveDirtyMark.clear();
        customBoards.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!enabled) {
            return;
        }
        Player player = event.getPlayer();
        dirtyPlayers.add(player.getUniqueId());
        // Szybki pierwszy render dla joinera — nie czekaj na pełną falę periodyczną.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!enabled || !player.isOnline()) {
                return;
            }
            dirtyPlayers.add(player.getUniqueId());
            queueUpdateAll();
        }, joinDelayTicks);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!enabled || !updateOnMove || event.getTo() == null) {
            return;
        }
        if (hasChangedBlock(event.getFrom(), event.getTo())) {
            UUID uuid = event.getPlayer().getUniqueId();
            long now = System.currentTimeMillis();
            Long last = lastMoveDirtyMark.get(uuid);
            if (last == null || now - last >= moveUpdateIntervalMillis) {
                lastMoveDirtyMark.put(uuid, now);
                dirtyPlayers.add(uuid);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        dirtyPlayers.remove(uuid);
        lastMoveDirtyMark.remove(uuid);
        customBoards.remove(uuid);

        PlayerSidebar sidebar = sidebars.remove(uuid);
        if (sidebar != null && !sidebar.board.isDeleted()) {
            try {
                sidebar.board.delete();
            } catch (Throwable throwable) {
                logBoardDeleteFailure(throwable);
            }
        }
    }

    private boolean hasChangedBlock(Location from, Location to) {
        return from.getWorld() != to.getWorld()
            || from.getBlockX() != to.getBlockX()
            || from.getBlockY() != to.getBlockY()
            || from.getBlockZ() != to.getBlockZ();
    }

    private static String[] buildEntries() {
        ChatColor[] colors = ChatColor.values();
        String[] entries = new String[MAX_LINES];
        for (int i = 0; i < MAX_LINES; i++) {
            entries[i] = colors[i].toString() + ChatColor.RESET;
        }
        return entries;
    }

    private interface BoardAdapter {
        void updateTitle(RenderedText title);

        void updateLines(List<RenderedText> lines);

        void delete();

        boolean isDeleted();

        boolean isFastBoard();
    }

    private static final class FastBoardAdapter implements BoardAdapter {
        private final FastBoard board;

        private FastBoardAdapter(FastBoard board) {
            this.board = board;
        }

        @Override
        public void updateTitle(RenderedText title) {
            board.updateTitle(title.legacy());
        }

        @Override
        public void updateLines(List<RenderedText> lines) {
            List<String> legacyLines = new ArrayList<>(lines.size());
            for (RenderedText line : lines) {
                legacyLines.add(line.legacy());
            }
            board.updateLines(legacyLines);
        }

        @Override
        public void delete() {
            if (!board.isDeleted()) {
                board.delete();
            }
        }

        @Override
        public boolean isDeleted() {
            return board.isDeleted();
        }

        @Override
        public boolean isFastBoard() {
            return true;
        }
    }

    private final class BukkitBoardAdapter implements BoardAdapter {
        private final Player player;
        private final Scoreboard scoreboard;
        private final Objective objective;
        private final Team[] teams;
        private final RenderedText[] lastLines = new RenderedText[MAX_LINES];
        private final int[] lastScores = new int[MAX_LINES];
        private boolean deleted;

        private BukkitBoardAdapter(Player player) {
            this.player = player;
            this.scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
            this.objective = scoreboard.registerNewObjective(
                OBJECTIVE_NAME,
                "dummy",
                toBukkitObjectiveTitle(ColorUtils.colorizeLegacy(sidebarTitle))
            );
            this.objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            this.teams = new Team[MAX_LINES];

            for (int i = 0; i < MAX_LINES; i++) {
                Team team = scoreboard.registerNewTeam(TEAM_PREFIX + i);
                team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
                team.suffix(Component.empty());
                team.addEntry(ENTRIES[i]);
                teams[i] = team;
                lastScores[i] = Integer.MIN_VALUE;
            }
        }

        @Override
        public void updateTitle(RenderedText title) {
            objective.displayName(title.component());
            if (!player.getScoreboard().equals(scoreboard)) {
                player.setScoreboard(scoreboard);
                if (plugin.getTablistManager() != null) {
                    plugin.getTablistManager().handleScoreboardChange(player);
                }
            }
        }

        @Override
        public void updateLines(List<RenderedText> lines) {
            int size = Math.min(lines.size(), MAX_LINES);

            for (int i = 0; i < MAX_LINES; i++) {
                String entry = ENTRIES[i];

                if (i >= size) {
                    if (lastLines[i] != null) {
                        scoreboard.resetScores(entry);
                        setTeamText(teams[i], RenderedText.empty());
                        lastLines[i] = null;
                        lastScores[i] = Integer.MIN_VALUE;
                    }
                    continue;
                }

                RenderedText line = lines.get(i);
                if (!line.equals(lastLines[i])) {
                    setTeamText(teams[i], line);
                    lastLines[i] = line;
                }

                int score = size - i;
                if (lastScores[i] != score) {
                    objective.getScore(entry).setScore(score);
                    lastScores[i] = score;
                }
            }

            if (!player.getScoreboard().equals(scoreboard)) {
                player.setScoreboard(scoreboard);
                if (plugin.getTablistManager() != null) {
                    plugin.getTablistManager().handleScoreboardChange(player);
                }
            }
        }

        @Override
        public void delete() {
            deleted = true;
            if (Bukkit.getScoreboardManager() != null && player.isOnline() && player.getScoreboard().equals(scoreboard)) {
                player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                if (plugin.getTablistManager() != null) {
                    plugin.getTablistManager().handleScoreboardChange(player);
                }
            }
        }

        @Override
        public boolean isDeleted() {
            return deleted;
        }

        @Override
        public boolean isFastBoard() {
            return false;
        }
    }

    private static final class PlayerSidebar {
        private BoardAdapter board;
        private List<RenderedText> lastLines = List.of();
        private RenderedText lastTitle = RenderedText.empty();

        private PlayerSidebar(BoardAdapter board) {
            this.board = board;
        }
    }

    private static final class RenderedText {
        private final String raw;
        /** Liczony leniwie — czyta go tylko FastBoard, a przy component-only configu nie powstaje wcale. */
        private String legacy;
        private Component component;

        private RenderedText(String raw, String legacy, Component component) {
            this.raw = raw == null ? "" : raw;
            this.legacy = legacy;
            this.component = component;
        }

        private String raw() {
            return raw;
        }

        private String legacy() {
            String value = legacy;
            if (value == null) {
                value = compactLegacyColors(ColorUtils.colorizeLegacy(raw));
                legacy = value;
            }
            return value;
        }

        private Component component() {
            Component value = component;
            if (value == null) {
                value = ColorUtils.colorizeComponent(raw);
                component = value;
            }
            return value;
        }

        private static RenderedText empty() {
            return new RenderedText("", "", Component.empty());
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof RenderedText other)) {
                return false;
            }
            // legacy i component sa deterministyczna funkcja raw — porownanie samego raw daje ten sam wynik.
            return raw.equals(other.raw);
        }

        @Override
        public int hashCode() {
            return raw.hashCode();
        }
    }

    private record PlaceholderParseResult(String value, boolean resolvedAll) {
    }

    private record RenderedSidebar(RenderedText title, List<RenderedText> lines, boolean needsComponentBoard) {
    }

    private record CustomBoardState(String title, List<String> lines, long expiresAt) {
    }
}
