package dev.grzybalson.managers;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.protocol.player.TextureProperty;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoRemove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerListHeaderAndFooter;
import dev.grzybalson.HollyCore;
import dev.grzybalson.config.configs.LpcChatConfig;
import dev.grzybalson.utils.BungeeSpooferPlaceholders;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.LuckPermsGroups;
import dev.grzybalson.utils.SpoofedPlayerProfile;
import dev.grzybalson.utils.SpooferBridge;
import dev.grzybalson.utils.SpooferCountHelper;
import dev.grzybalson.utils.TexturePackRankIcons;
import me.clip.placeholderapi.PlaceholderAPI;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.event.EventSubscription;
import net.luckperms.api.event.group.GroupDataRecalculateEvent;
import net.luckperms.api.event.node.NodeMutateEvent;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TablistManager implements Listener {

    private static final int MAX_ROWS = 80;
    private static final String CONFIG_RESOURCE = "addons/tablist/tablist.yml";
    private static final String PROFILE_NAME_PREFIX = "HCTAB";
    private static final long[] JOIN_IDENTITY_RETRY_TICKS = {4L, 16L, 40L};
    private static final int LIST_ORDER_PACKET_CHUNK = 64;
    private static final long SPOOFER_ONLINE_CACHE_MILLIS = 1_000L;
    private static final Pattern HOLLY_PLACEHOLDER_PATTERN = Pattern.compile("%holly_([A-Za-z0-9_]+)%");
    private static final Pattern REL_HOLLY_PLACEHOLDER_PATTERN = Pattern.compile("%rel_holly_([A-Za-z0-9_]+)%");
    private static final Pattern PLACEHOLDER_TOKEN_PATTERN = Pattern.compile("%[A-Za-z0-9_\\-]+%");
    private static final String DEFAULT_IDLE_AFK_MARKER = "&7[AFK]";
    private static final long NAMETAG_CACHE_TTL_MS = 3000L;
    private static final String NAMETAG_TEAM_PREFIX = "t_";
    private static final String SIDEBAR_TEAM_PREFIX = "holly_sb_";
    /** Teamy slotów custom TAB — alfabetycznie po {@code h999}, czyli pod rangami jak listOrder=0 na 1.21.2+. */
    private static final String SLOT_SORT_TEAM_PREFIX = "hs";
    /** Protokół 1.21.2 — pierwsze wydanie z {@code listOrder}. */
    private static final int LIST_ORDER_PROTOCOL = 768;
    private static final List<String> DEFAULT_RANK_ORDER = List.of(
        "root", "wlasciciel", "owner", "zarzad", "headadmin", "hadmin", "admin",
        "mod", "moderator", "kidmod", "jmod", "helper", "thelper", "trialhelper",
        "builder", "budowniczy", "partner", "tworca", "media", "youtuber", "yt",
        "tiktok", "minimedia", "sponsor", "patron", "elita", "champion", "boss",
        "dig", "mvip", "svip", "vip", "gracz", "player", "default"
    );
    /** Domyslne kolory nickow — przywracane gdy sekcja zniknie z tablist.yml. */
    private static final Map<String, String> DEFAULT_RANK_NAME_COLORS = Map.ofEntries(
        Map.entry("root", "&c"),
        Map.entry("wlasciciel", "&c"),
        Map.entry("owner", "&c"),
        Map.entry("headadmin", "&c"),
        Map.entry("hadmin", "&c"),
        Map.entry("admin", "&c"),
        Map.entry("mod", "&2"),
        Map.entry("moderator", "&2"),
        Map.entry("helper", "&b"),
        Map.entry("thelper", "&b"),
        Map.entry("trialhelper", "&b"),
        Map.entry("vip", "&e"),
        Map.entry("svip", "&e"),
        Map.entry("mvip", "&e"),
        Map.entry("gracz", "&b"),
        Map.entry("player", "&b"),
        Map.entry("default", "&b"),
        Map.entry("media", "&d"),
        Map.entry("tiktok", "&d"),
        Map.entry("partner", "&d"),
        Map.entry("sponsor", "&6"),
        Map.entry("patron", "&6")
    );
    private static final Pattern GOXY_SERVER_ONLINE_PATTERN = Pattern.compile("%goxy_server_([A-Za-z0-9_\\-]+)_online%");
    private static final Set<String> EVENT_DRIVEN_HEADER_FOOTER_PLACEHOLDERS = Set.of(
        "%player_name%",
        "%holly_nick%",
        "%goxy_network_online%"
    );
    private static final int DEFAULT_UNKNOWN_GROUP_WEIGHT = 999;
    private static final String DEFAULT_FALLBACK_GROUP = "default";
    private static final EnumSet<WrapperPlayServerPlayerInfoUpdate.Action> ADD_ROW_ACTIONS = EnumSet.of(
        WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER,
        WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED,
        WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LATENCY,
        WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_GAME_MODE,
        WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_DISPLAY_NAME,
        WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LIST_ORDER
    );
    private static final EnumSet<WrapperPlayServerPlayerInfoUpdate.Action> ADD_ROW_ACTIONS_LEGACY = EnumSet.of(
        WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER,
        WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED,
        WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LATENCY,
        WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_GAME_MODE,
        WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_DISPLAY_NAME
    );
    private static final EnumSet<WrapperPlayServerPlayerInfoUpdate.Action> CHANGE_ROW_ACTIONS = EnumSet.of(
        WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_DISPLAY_NAME,
        WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED
    );
    private static final EnumSet<WrapperPlayServerPlayerInfoUpdate.Action> UPDATE_DISPLAY_NAME_ACTIONS = EnumSet.of(
        WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_DISPLAY_NAME
    );
    private static final EnumSet<WrapperPlayServerPlayerInfoUpdate.Action> UPDATE_LISTED_ACTIONS = EnumSet.of(
        WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED
    );
    private static final EnumSet<WrapperPlayServerPlayerInfoUpdate.Action> UPDATE_FAKE_TAB_ACTIONS = EnumSet.of(
        WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_DISPLAY_NAME,
        WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED,
        WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LIST_ORDER
    );
    private static final EnumSet<WrapperPlayServerPlayerInfoUpdate.Action> UPDATE_FAKE_TAB_ACTIONS_LEGACY = EnumSet.of(
        WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_DISPLAY_NAME,
        WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED
    );

    private final HollyCore plugin;
    private final File configFile;
    private final Map<UUID, PlayerTablist> tablists = new ConcurrentHashMap<>();
    private final Set<UUID> dirtyPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Set<UUID>> hiddenRealPlayersByViewer = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastMoveDirtyMark = new ConcurrentHashMap<>();
    private final Set<UUID> dirtyIdentities = ConcurrentHashMap.newKeySet();
    private final Set<UUID> spooferBotsHiddenViewers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> lastIdleActivity = new ConcurrentHashMap<>();
    private final Set<UUID> idleAfkPlayers = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean updateQueued = new AtomicBoolean(false);
    private final AtomicBoolean spooferNameTagRefreshQueued = new AtomicBoolean(false);
    private volatile boolean spooferTabRefreshPending;
    private final AtomicBoolean fakeIdentitySyncQueued = new AtomicBoolean(false);
    private long nextFakeIdentitySyncMillis;
    private final Set<UUID> pendingViewerFakeTagResync = ConcurrentHashMap.newKeySet();
    private static final long FAKE_TAGS_SYNC_MIN_GAP_MS = 4000L;
    private final AtomicBoolean tabOrderRefreshQueued = new AtomicBoolean(false);
    private final AtomicBoolean listOrderFlushQueued = new AtomicBoolean(false);
    private final Map<UUID, String> lastPlayerListNames = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> lastPlayerListOrders = new ConcurrentHashMap<>();
    private final Set<UUID> pendingListOrderBroadcasts = ConcurrentHashMap.newKeySet();
    private final Set<UUID> paperListOrderSynced = ConcurrentHashMap.newKeySet();
    private final Set<UUID> knownFakeBotUuids = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Map<UUID, String>> lastNameTagSuffixes = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, String>> lastNameTagTeams = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, String>> lastFakeNameTagTeams = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, String>> lastFakeNameTagSuffixes = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, String>> lastFakeTabDisplayNames = new ConcurrentHashMap<>();
    private final Map<String, FakeTagSnapshot> fakeTagSnapshots = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> lastFakeTagVersions = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> lastFakeTabDisplayVersions = new ConcurrentHashMap<>();
    private final Map<UUID, net.kyori.adventure.text.Component> tabListDisplayCache = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, net.kyori.adventure.text.Component>> relationalTabDisplayCache = new ConcurrentHashMap<>();
    private volatile Map<String, FakeTagSnapshot> preparedFakeTags = Map.of();
    private long preparedFakeNamesSignature;
    private volatile int fakeTagVersion;
    private boolean fakeNameTagViewerDependent;
    private boolean fakeTabDisplayViewerDependent;
    private final Map<UUID, String> rankNameColorCache = new ConcurrentHashMap<>();
    private final Map<String, String> groupNameColorCache = new ConcurrentHashMap<>();
    private final Map<UUID, HeaderFooterState> lastHeaderFooters = new ConcurrentHashMap<>();
    private final Map<UUID, LuckPermsIdentityData> luckPermsIdentityCache = new ConcurrentHashMap<>();
    private final Map<UUID, PrefixSuffix> nametagCacheByTarget = new ConcurrentHashMap<>();
    private final Map<UUID, RealTagSnapshot> realTagSnapshots = new ConcurrentHashMap<>();
    private final Map<String, String> staticColorCache = new ConcurrentHashMap<>();
    private final Set<UUID> dirtyHeaderFooters = ConcurrentHashMap.newKeySet();
    private Method playerListOrderMethod;
    private Method spooferIsFakePlayerMethod;
    private Method spooferGetFakePlayerIdsMethod;
    private Method spooferGetFakePlayersMethod;
    private Method spooferGetFakePlayerCountMethod;
    private Method spooferGetFakePlayerIdsServerMethod;
    private Method spooferGetFakePlayersServerMethod;
    private Method spooferGetFakePlayerCountServerMethod;
    private Method spooferGetLocalServerNameMethod;
    private Method spooferGetFakeBotHelmetMethod;
    private Method spooferGetFakeBotChestplateMethod;
    private Method spooferGetFakeBotLeggingsMethod;
    private Method spooferGetFakeBotBootsMethod;
    private Method spooferGetFakeBotWeaponMethod;
    private Method spooferGetFakeBotUuidMethod;
    private Method spooferGetFakeBotLuckPermsGroupMethod;
    private Method spooferGetFakeBotTabPriorityMethod;
    private Method spooferGetFakeBotTabDataMapMethod;
    private final Map<String, Method> tabDataAccessorCache = new ConcurrentHashMap<>();
    private Map<String, Object> currentSpooferTabDataMap;
    private Object spooferApi;
    private boolean spooferApiChecked;
    private long nextSpooferOnlineRefreshMillis;
    private int cachedVisibleOnlineCount = -1;
    private boolean playerListOrderMethodChecked;

    private BukkitTask updateTask;
    private boolean enabled;
    private boolean listenerRegistered;
    private boolean papiEnabled;
    private boolean luckPermsEnabled;
    private LuckPerms luckPerms;
    private final List<EventSubscription<?>> luckPermsSubscriptions = new ArrayList<>();
    private EventSubscription<UserDataRecalculateEvent> luckPermsUserDataSubscription;
    private boolean customTabEnabled;
    private boolean headerFooterEnabled;
    private long updateIntervalTicks;
    private long headerFooterIntervalTicks;
    private long identityUpdateIntervalTicks;
    private long fullRowUpdateIntervalTicks;
    private long adaptiveFullRowUpdateIntervalTicks;
    private long maxAdaptiveFullRowUpdateIntervalTicks;
    private long rowRenderCacheMillis;
    private long nextIdentityFullUpdateMillis;
    private long nextFullRowUpdateMillis;
    private long nextHeaderFooterUpdateMillis;
    private boolean updateRowsOnMove;
    private boolean updateIdentityOnEveryTabTick;
    private boolean periodicIdentityRefresh;
    private boolean adaptiveRefresh;
    private boolean fillEmptyRows;
    private boolean autoShadowEnabled;
    private boolean sortByLuckPerms;
    private int unknownGroupWeight = DEFAULT_UNKNOWN_GROUP_WEIGHT;
    private String fallbackGroup = DEFAULT_FALLBACK_GROUP;
    private boolean idleAfkEnabled;
    private boolean idleAfkResetOnInteract;
    private boolean idleAfkResetOnChat;
    private boolean idleAfkResetOnCommand;
    private long idleAfkAfterMillis;
    private long nextIdleAfkCheckMillis;
    private String idleAfkMarker = DEFAULT_IDLE_AFK_MARKER;
    private int playersPerTick;
    private int identityPlayersPerTick;
    private Map<String, Integer> lpcGroupOrder = Map.of();
    private Map<String, String> rankNameColors = Map.of();
    private List<String> header = List.of();
    private List<String> footer = List.of();
    private List<String> rows = List.of();
    private List<TextureProperty> defaultTextures = List.of();
    // Animacja tablist rows
    private boolean animationEnabled;
    private long animationIntervalMillis;
    private List<String> animationPageNames = List.of();
    private Map<String, List<String>> animationPages = Map.of();
    private int currentAnimationPage;
    private long nextAnimationPageChangeMillis;
    private String tabPrefix = "";
    private String tabSuffix = "";
    private String tagPrefix = "";
    private String tagSuffix = "";
    private String playerNameFormat = "";
    private String nameTagFormat = "";
    private String autoShadowColor = "#000000C0";
    private String[] staticRenderedRows = new String[0];
    private int[] leaderboardRowIndices = new int[0];
    private int[] onlineCountRowIndices = new int[0];
    private boolean rowsStatic;
    private boolean rowsUseOnlineCount;
    private String staticRenderedHeader;
    private String staticRenderedFooter;
    private boolean headerStatic;
    private boolean footerStatic;
    private boolean headerFooterNeedsPeriodicRefresh;
    /** Header/footer ma tylko placeholdery serwerowe (bungee/goxy) — jedno renderowanie na cykl. */
    private boolean headerFooterSharedOnly;
    private boolean headerFooterUsesOnlineCount;
    private volatile String cachedSharedHeader;
    private volatile String cachedSharedFooter;
    private volatile long cachedSharedHeaderFooterAt;
    private volatile boolean sharedHeaderFooterDirty = true;
    private int lastOnlineCountForHeaderRefresh = -1;
    private boolean currentBatchHadRowChanges;
    private boolean tabOrderDirty;
    private boolean nametagUsesRelational;
    private boolean tabNameUsesRelational;
    /** Pary viewer-target / tick — przy zbyt niskim budgetcie nametagi "nie dzialaja" (minuty opoznienia). */
    private static final int NAMETAG_PAIRS_PER_TICK = 80;
    private static final int NAMETAG_PAIRS_PER_TICK_MID_POP = 56;
    private static final int NAMETAG_PAIRS_PER_TICK_HIGH_POP = 32;
    private static final int NAMETAG_PAIRS_PER_TICK_ULTRA = 20;
    private static final int FAKE_NAMETAG_BOTS_PER_TICK = 32;
    private static final int FAKE_NAMETAG_BOTS_PER_TICK_HIGH = 14;
    private static final long NAMETAG_PUMP_PERIOD_TICKS = 4L; // OPTYMALIZACJA: było 2L - zmniejsza obciążenie o 50%
    private static final long VIEWER_FAKE_TAG_RESYNC_DELAY_TICKS = 2L; // OPTYMALIZACJA: było 1L
    private static final int FAKE_TAB_DISPLAY_PACKET_CHUNK = 80;
    private static final int JOIN_NAMETAG_TARGETS_PER_TICK = 40;
    private static final int JOIN_NAMETAG_TARGETS_PER_TICK_HIGH = 20;
    private static final long ONLINE_COUNT_REFRESH_DELAY_TICKS = 40L;
    private BukkitTask nametagBatchTask;
    private BukkitTask fakeNametagBatchTask;
    private BukkitTask fakeSnapshotPrepareTask;
    private BukkitTask viewerOnlyFakeTagTask;
    private Player viewerOnlyFakeTagViewer;
    private int viewerOnlyFakeTagBotIndex;
    private String[] viewerOnlyFakeTagBotNames;
    private Map<String, FakeTagSnapshot> viewerOnlyFakeTagTags;
    private final AtomicBoolean viewerFakeTagResyncScheduled = new AtomicBoolean(false);
    private final AtomicBoolean viewerReapplyScheduled = new AtomicBoolean(false);
    private final Set<UUID> pendingViewerReapply = ConcurrentHashMap.newKeySet();
    private boolean nametagRefreshPending;
    private boolean fakeNametagRefreshPending;
    private Player[] fakeNametagViewerCache;
    private int fakeNametagViewerIndex;
    private int fakeNametagBotIndex;
    private String[] fakeNametagBotNames;
    private Map<String, FakeTagSnapshot> fakeNametagBatchTags;
    private Player[] nametagViewerCache;
    private int nametagViewerIndex;
    private int nametagTargetIndex;
    private final Deque<UUID> pendingJoinNametagWork = new ConcurrentLinkedDeque<>();
    /** Dedup tylko dla wpisow CZEKAJACYCH — kolejka rosla w nieskonczonosc przy 100+ graczach. */
    private final Set<UUID> pendingJoinNametagIds = ConcurrentHashMap.newKeySet();
    private JoinNametagWork activeJoinNametagWork;
    private BukkitTask joinNametagTask;

    private static final class JoinNametagWork {
        final UUID joinerId;
        final UUID[] online;
        int index;
        /** 0 = widzowie widzą joinera, 1 = joiner widzi resztę */
        byte phase;

        JoinNametagWork(UUID joinerId, UUID[] online) {
            this.joinerId = joinerId;
            this.online = online;
        }
    }
    private final AtomicBoolean onlineCountRefreshQueued = new AtomicBoolean(false);
    private BukkitTask onlineCountRefreshTask;
    private final Set<UUID> pendingLuckPermsLoads = ConcurrentHashMap.newKeySet();
    private String cachedHeaderText;
    private Component cachedHeaderComponent;
    private String cachedFooterText;
    private Component cachedFooterComponent;
    private final Deque<UUID> pendingListedSync = new ConcurrentLinkedDeque<>();
    private UUID activeListedSyncViewer;
    private int activeListedSyncIndex;
    private BukkitTask listedSyncTask;

    public TablistManager(HollyCore plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), CONFIG_RESOURCE);
    }

    public void start() {
        loadConfig();
        closeLuckPermsSubscription();
        if (listenerRegistered) {
            HandlerList.unregisterAll(this);
            listenerRegistered = false;
        }
        if (!enabled) {
            clearAll();
            resetIdentityAll();
            return;
        }

        registerLuckPermsSubscription();
        registerListener();
        schedule();
        markAllDirty();
        invalidateAllLuckPermsIdentities();
        queueUpdateAll();
        // Od razu prawidłowy listOrder — nie czekaj aż dirtyIdentities się opróżnią.
        Bukkit.getScheduler().runTask(plugin, this::applyLpcTabOrderAll);
        Bukkit.getScheduler().runTaskLater(plugin, this::applyLpcTabOrderAll, 20L);
        Bukkit.getScheduler().runTaskLater(plugin, this::forceNameTagCollisionRefresh, 20L);
    }

    public void forceNameTagCollisionRefresh() {
        if (!enabled) {
            return;
        }
        lastNameTagTeams.clear();
        lastNameTagSuffixes.clear();
        lastFakeNameTagTeams.clear();
        lastFakeNameTagSuffixes.clear();
        lastFakeTagVersions.clear();
        invalidateFakeTagSnapshots();
        scheduleBatchedNameTagRefresh();
        scheduleBatchedFakeNameTagRefresh();
    }

    public void reload() {
        String previousNameTag = nameTagFormat;
        String previousPlayerName = playerNameFormat;
        int previousRows = rows.size();
        boolean previousCustom = customTabEnabled;
        boolean previousEnabled = enabled;
        stopTask();
        loadConfig();
        cachedHeaderText = null;
        cachedHeaderComponent = null;
        cachedFooterText = null;
        cachedFooterComponent = null;
        closeLuckPermsSubscription();
        if (listenerRegistered) {
            HandlerList.unregisterAll(this);
            listenerRegistered = false;
        }

        if (!enabled) {
            clearAll();
            return;
        }

        registerLuckPermsSubscription();
        registerListener();
        schedule();
        boolean structureChanged = previousEnabled != enabled
            || previousCustom != customTabEnabled
            || previousRows != rows.size();

        // Bezwarunkowo czyść wszystkie cache tożsamości, kolorów, tablisty i nametagów
        luckPermsIdentityCache.clear();
        rankNameColorCache.clear();
        groupNameColorCache.clear();
        tabListDisplayCache.clear();
        relationalTabDisplayCache.clear();
        nametagCacheByTarget.clear();
        realTagSnapshots.clear();
        lastNameTagTeams.clear();
        lastNameTagSuffixes.clear();
        lastFakeNameTagTeams.clear();
        lastFakeNameTagSuffixes.clear();
        lastFakeTagVersions.clear();
        invalidateFakeTagSnapshots();
        invalidateAllRenderedRows();

        if (customTabEnabled && structureChanged) {
            recreateAll();
        } else if (!customTabEnabled && previousCustom) {
            clearAll();
        }

        markAllDirty();
        markAllIdentitiesDirty();
        if (headerFooterEnabled) {
            markAllHeaderFootersDirty();
        }

        // Natychmiastowe odświeżenie tożsamości LP dla graczy online
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (luckPerms != null) {
                try {
                    User user = luckPerms.getUserManager().getUser(player.getUniqueId());
                    if (user != null) {
                        user.getCachedData().invalidate();
                    }
                } catch (Throwable ignored) {
                }
            }
            updatePlayerTabName(player);
        }

        scheduleTabOrderRefresh();
        Bukkit.getScheduler().runTaskLater(plugin, this::queueUpdateAll, 1L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            scheduleBatchedNameTagRefresh();
            scheduleBatchedFakeNameTagRefresh();
            scheduleFakeTagsSyncWithPlayers();
        }, 2L);
    }

    public void shutdown() {
        cancelJoinNametagTask();
        cancelNametagBatch();
        cancelFakeNametagBatch();
        cancelListedSyncTask();
        stopTask();
        closeLuckPermsSubscription();
        clearAll();
        resetIdentityAll();
        if (listenerRegistered) {
            HandlerList.unregisterAll(this);
            listenerRegistered = false;
        }
        unregisterPacketEventsListener();
    }

    /** Live-reload: zatrzymaj taski bez czyszczenia TAB/nametagow graczy (mniej migotania). */
    public void softShutdown() {
        cancelNametagBatch();
        cancelJoinNametagTask();
        cancelFakeNametagBatch();
        cancelListedSyncTask();
        stopTask();
        closeLuckPermsSubscription();
        if (listenerRegistered) {
            HandlerList.unregisterAll(this);
            listenerRegistered = false;
        }
        unregisterPacketEventsListener();
    }

    /** Po fazowym reloadzie — szybkie odswiezenie tozsamosci online bez pelnego stormu pakietow. */
    public void warmupAfterLiveReload() {
        if (!enabled) {
            return;
        }
        registerLuckPermsSubscription();
        registerListener();
        schedule();
        for (Player player : Bukkit.getOnlinePlayers()) {
            applyImmediatePlayerIdentity(player);
        }
        scheduleBatchedNameTagRefresh();
        scheduleBatchedFakeNameTagRefresh();
        markAllDirty();
        queueUpdateAll();
    }

    private void applyImmediatePlayerIdentity(Player player) {
        applyJoinIdentityImmediate(player);
    }

    /**
     * Join / odswiezenie tozsamosci: LP + tab name od razu; nametagi rozlozone na ticki.
     * Nie czyscimy lastNameTagTeams — desync z klientem powoduje REMOVE z zlego teamu i kick.
     */
    private void applyJoinIdentityImmediate(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        UUID uuid = player.getUniqueId();
        if (luckPermsEnabled && !hasResolvedLuckPermsIdentity(uuid)) {
            ensureLuckPermsUserLoaded(player);
            getLuckPermsIdentityData(player);
        } else if (!realTagSnapshots.containsKey(uuid)) {
            getLuckPermsIdentityData(player);
        }

        updatePlayerTabName(player);
        queueJoinNametagWork(player);
        if (sortByLuckPerms && (hasResolvedLuckPermsIdentity(uuid) || !luckPermsEnabled)) {
            int order = resolveTabListOrder(player);
            setPlayerListOrder(player, order);
            // Tylko joiner — pełne applyLpcTabOrderAll jest O(n) i zbija MSPT przy fali joinów.
            broadcastListOrder(uuid, order);
        }
        if (customTabEnabled) {
            dirtyPlayers.add(uuid);
            if (headerFooterEnabled) {
                dirtyHeaderFooters.add(uuid);
            }
            queueUpdateAll();
        }
    }

    /** Broadcast joinera do widzow + catch-up joinera — obie fazy batchowane po JOIN_NAMETAG_TARGETS_PER_TICK. */
    private void queueJoinNametagWork(Player player) {
        if (player == null) {
            return;
        }
        if (!pendingJoinNametagIds.add(player.getUniqueId())) {
            ensureJoinNametagTask();
            return;
        }
        pendingJoinNametagWork.add(player.getUniqueId());
        ensureJoinNametagTask();
    }

    private void ensureJoinNametagTask() {
        if (joinNametagTask != null) {
            return;
        }
        joinNametagTask = new BukkitRunnable() {
            @Override
            public void run() {
                tickJoinNametagWork();
            }
        }.runTaskTimer(plugin, NAMETAG_PUMP_PERIOD_TICKS, NAMETAG_PUMP_PERIOD_TICKS);
    }

    private void tickJoinNametagWork() {
        if (!enabled) {
            cancelJoinNametagTask();
            return;
        }
        JoinNametagWork work = activeJoinNametagWork;
        if (work == null) {
            UUID next = pendingJoinNametagWork.poll();
            if (next == null) {
                cancelJoinNametagTask();
                return;
            }
            pendingJoinNametagIds.remove(next);
            List<UUID> onlineIds = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.isOnline()) {
                    onlineIds.add(p.getUniqueId());
                }
            }
            work = new JoinNametagWork(next, onlineIds.toArray(new UUID[0]));
            activeJoinNametagWork = work;
        }

        Player joiner = Bukkit.getPlayer(work.joinerId);
        if (joiner == null || !joiner.isOnline()) {
            activeJoinNametagWork = null;
            return;
        }

        UUID[] online = work.online;
        if (online.length == 0) {
            activeJoinNametagWork = null;
            return;
        }
        int queueDepth = pendingJoinNametagWork.size() + 1;
        int batch = joinNametagBatchSize(online.length, queueDepth);
        int end = Math.min(work.index + batch, online.length);

        if (work.phase == 0) {
            for (int i = work.index; i < end; i++) {
                UUID viewerId = online[i];
                if (viewerId != null && !viewerId.equals(work.joinerId)) {
                    Player viewer = Bukkit.getPlayer(viewerId);
                    if (viewer != null && viewer.isOnline()) {
                        updateNameTagForViewer(viewer, joiner);
                    }
                }
            }
            work.index = end;
            if (end >= online.length) {
                work.phase = 1;
                work.index = 0;
            }
        } else {
            for (int i = work.index; i < end; i++) {
                UUID targetId = online[i];
                if (targetId != null) {
                    Player target = Bukkit.getPlayer(targetId);
                    if (target != null && target.isOnline()) {
                        // Joiner musi widzieć WSZYSTKICH, włącznie z samym sobą
                        updateNameTagForViewer(joiner, target);
                    }
                }
            }
            work.index = end;
            if (end >= online.length) {
                activeJoinNametagWork = null;
            }
        }
    }

    private int joinNametagBatchSize(int online, int queueDepth) {
        int batch = JOIN_NAMETAG_TARGETS_PER_TICK;
        if (online > 80) {
            batch = JOIN_NAMETAG_TARGETS_PER_TICK_HIGH;
        }
        if (online > 180) {
            batch = 14;
        }
        if (queueDepth > 4 && online <= 120) {
            batch = Math.min(online, batch + 12);
        }
        return Math.max(10, batch);
    }

    private static int fakeNametagBotsPerTick(int botCount) {
        int online = Bukkit.getOnlinePlayers().size();
        if (online > 180 || botCount > 120) {
            return FAKE_NAMETAG_BOTS_PER_TICK_HIGH;
        }
        if (online > 100 || botCount > 80) {
            return 20;
        }
        return FAKE_NAMETAG_BOTS_PER_TICK;
    }

    private void cancelJoinNametagTask() {
        if (joinNametagTask != null) {
            joinNametagTask.cancel();
            joinNametagTask = null;
        }
        pendingJoinNametagWork.clear();
        pendingJoinNametagIds.clear();
        activeJoinNametagWork = null;
    }

    private net.kyori.adventure.text.Component resolveTabListDisplay(Player viewer, UUID targetId) {
        if (targetId == null) {
            return null;
        }
        if (tabNameUsesRelational && viewer != null) {
            Player target = Bukkit.getPlayer(targetId);
            if (target == null || !target.isOnline()) {
                return null;
            }
            return getRelationalTabDisplay(viewer, target);
        }
        net.kyori.adventure.text.Component cached = tabListDisplayCache.get(targetId);
        if (cached != null) {
            return cached;
        }
        Player target = Bukkit.getPlayer(targetId);
        return target != null ? cacheTabListDisplay(target) : null;
    }

    /** Tylko hit z cache — bezpieczne na wątku Netty (bez PAPI/LP). */
    private net.kyori.adventure.text.Component peekCachedTabListDisplay(Player viewer, UUID targetId) {
        if (targetId == null) {
            return null;
        }
        if (tabNameUsesRelational && viewer != null) {
            Map<UUID, net.kyori.adventure.text.Component> byTarget = relationalTabDisplayCache.get(viewer.getUniqueId());
            return byTarget != null ? byTarget.get(targetId) : null;
        }
        return tabListDisplayCache.get(targetId);
    }

    private net.kyori.adventure.text.Component getRelationalTabDisplay(Player viewer, Player target) {
        Map<UUID, net.kyori.adventure.text.Component> byTarget =
            relationalTabDisplayCache.computeIfAbsent(viewer.getUniqueId(), key -> new ConcurrentHashMap<>());
        net.kyori.adventure.text.Component cached = byTarget.get(target.getUniqueId());
        if (cached != null) {
            return cached;
        }
        net.kyori.adventure.text.Component built = buildTabListDisplayComponent(viewer, target);
        if (built != null) {
            byTarget.put(target.getUniqueId(), built);
        }
        return built;
    }

    private net.kyori.adventure.text.Component buildTabListDisplayComponent(Player target) {
        return buildTabListDisplayComponent(null, target);
    }

    private net.kyori.adventure.text.Component buildTabListDisplayComponent(Player viewer, Player target) {
        if (target == null) {
            return null;
        }
        IncognitoManager incognito = plugin.getIncognitoManager();
        if (incognito != null && incognito.isIncognito(target)) {
            return null;
        }
        String format = playerNameFormat;
        if (format == null || format.isBlank()) {
            format = "%luckperms_prefix%%name_color%%player_name%";
        }
        String nameColor = getRankNameColor(target);
        if (nameColor.isBlank()) {
            nameColor = resolveLuckPermsGroupNameColor(fallbackGroup);
        }
        String formatted = format.replace("%name_color%", nameColor).replace("%rank_name_color%", nameColor);
        formatted = viewer != null
            ? parseIdentityPlaceholders(viewer, target, formatted)
            : renderLine(target, formatted);
        return ColorUtils.colorizeComponent(formatted);
    }

    private net.kyori.adventure.text.Component cacheTabListDisplay(Player target) {
        if (target == null) {
            return null;
        }
        net.kyori.adventure.text.Component built = buildTabListDisplayComponent(target);
        if (built != null) {
            tabListDisplayCache.put(target.getUniqueId(), built);
        } else {
            tabListDisplayCache.remove(target.getUniqueId());
        }
        return built;
    }

    private void invalidateTabListDisplay(UUID uuid) {
        if (uuid != null) {
            tabListDisplayCache.remove(uuid);
            relationalTabDisplayCache.remove(uuid);
            for (Map<UUID, net.kyori.adventure.text.Component> byTarget : relationalTabDisplayCache.values()) {
                byTarget.remove(uuid);
            }
        }
    }

    /**
     * Nigdy nie blokuje glownego watku — przy masowym joinie .join() na LP zatrzymywal serwer.
     * Gdy usera brak, ladujemy async; tozsamosc dociagnie {@link #scheduleJoinIdentityRetries}.
     */
    private boolean ensureLuckPermsUserLoaded(Player player) {
        if (!luckPermsEnabled || luckPerms == null || player == null) {
            return true;
        }
        try {
            UUID uuid = player.getUniqueId();
            if (luckPerms.getUserManager().getUser(uuid) != null) {
                return true;
            }
            if (!Bukkit.isPrimaryThread()) {
                return luckPerms.getUserManager().loadUser(uuid).join() != null;
            }
            if (pendingLuckPermsLoads.add(uuid)) {
                luckPerms.getUserManager().loadUser(uuid).whenComplete((user, error) -> {
                    pendingLuckPermsLoads.remove(uuid);
                    if (user == null || !plugin.isEnabled()) {
                        return;
                    }
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        Player online = Bukkit.getPlayer(uuid);
                        if (online != null && online.isOnline()) {
                            refreshJoinedPlayerIdentity(online);
                        }
                    });
                });
            }
            return false;
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private boolean hasResolvedLuckPermsIdentity(UUID uuid) {
        if (!luckPermsEnabled) {
            return true;
        }
        LuckPermsIdentityData cached = luckPermsIdentityCache.get(uuid);
        if (cached != null && cached != LuckPermsIdentityData.EMPTY) {
            return true;
        }
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) {
            return false;
        }
        if (luckPerms != null && luckPerms.getUserManager().getUser(uuid) != null) {
            // getLuckPermsIdentityData = ten sam wynik, ale zapisuje cache (inaczej resolve leci drugi raz).
            return getLuckPermsIdentityData(player) != LuckPermsIdentityData.EMPTY;
        }
        return false;
    }

    private boolean shouldCacheRealTagSnapshot(Player target) {
        if (target == null || !luckPermsEnabled) {
            return true;
        }
        // Jak wyżej — bez cache ten sam pełny resolve LP leciał dwa razy na jedną przebudowę snapshotu.
        return getLuckPermsIdentityData(target) != LuckPermsIdentityData.EMPTY;
    }

    private boolean packetListenerRegistered;
    /** Listener zyje w PacketEvents — bez tego uchwytu przezywa disable i mnozy sie przy kazdym live reloadzie. */
    private com.github.retrooper.packetevents.event.PacketListenerCommon packetListener;

    private void registerPacketEventsListener() {
        if (!isPacketEventsReady() || packetListenerRegistered) {
            return;
        }
        packetListener = PacketEvents.getAPI().getEventManager().registerListener(new com.github.retrooper.packetevents.event.PacketListenerAbstract(com.github.retrooper.packetevents.event.PacketListenerPriority.HIGHEST) {
            @Override
            public void onPacketSend(com.github.retrooper.packetevents.event.PacketSendEvent event) {
                if (event.getPacketType() != com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Server.PLAYER_INFO_UPDATE) {
                    return;
                }
                if (!enabled) {
                    return;
                }
                WrapperPlayServerPlayerInfoUpdate wrapper = new WrapperPlayServerPlayerInfoUpdate(event);
                // Ustawiamy tylko listOrder/listed/displayName/profil — pola z innych akcji i tak nie ida na wire.
                EnumSet<WrapperPlayServerPlayerInfoUpdate.Action> actions = wrapper.getActions();
                if (actions != null
                    && !actions.contains(WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER)
                    && !actions.contains(WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED)
                    && !actions.contains(WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_DISPLAY_NAME)
                    && !actions.contains(WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LIST_ORDER)) {
                    return;
                }
                List<WrapperPlayServerPlayerInfoUpdate.PlayerInfo> entries = wrapper.getEntries();
                if (entries == null || entries.isEmpty()) {
                    return;
                }
                boolean modified = false;
                IncognitoManager incognito = plugin.getIncognitoManager();
                Player viewer = resolvePacketViewer(event);
                for (WrapperPlayServerPlayerInfoUpdate.PlayerInfo entry : entries) {
                    if (entry == null) {
                        continue;
                    }
                    UUID uuid = entry.getProfileId() != null
                        ? entry.getProfileId()
                        : (entry.getGameProfile() != null ? entry.getGameProfile().getUUID() : null);
                    if (uuid == null) {
                        continue;
                    }
                    // Netty-safe: tylko cache; miss → dirty + refresh na main (bez PAPI/LP/Bukkit.getPlayer).
                    boolean realTracked = tablists.containsKey(uuid);
                    boolean knownBot = knownFakeBotUuids.contains(uuid);
                    if (incognito != null && incognito.isIncognito(uuid)) {
                        incognito.applyToPlayerInfo(entry, viewer);
                        if (customTabEnabled) {
                            entry.setListed(false);
                        }
                        modified = true;
                        continue;
                    }
                    // Sortowanie TAB 1.21.2+: wyższy listOrder = wyżej (ROOT ≈ 999, GRACZ = 0).
                    // Klient <1.21.2 ignoruje to pole — ViaVersion je zdejmie, sortują teamy h### / hs###.
                    if (sortByLuckPerms) {
                        Integer order = lastPlayerListOrders.get(uuid);
                        if (order == null) {
                            order = toClientListOrder(knownBot ? getFakeTabPriority() : unknownGroupWeight);
                            lastPlayerListOrders.putIfAbsent(uuid, order);
                            if (realTracked) {
                                dirtyIdentities.add(uuid);
                            }
                        }
                        try {
                            entry.setListOrder(order);
                            modified = true;
                        } catch (RuntimeException | LinkageError ignored) {
                        }
                    }
                    if (customTabEnabled) {
                        // Custom tab = sloty HollyCore (HCTAB...). Prawdziwi gracze UKRYCI (listed=false); boty też ukryte.
                        if (realTracked || knownBot) {
                            entry.setListed(false);
                            net.kyori.adventure.text.Component display = peekCachedTabListDisplay(viewer, uuid);
                            if (display != null) {
                                entry.setDisplayName(display);
                            } else if (realTracked) {
                                dirtyIdentities.add(uuid);
                            }
                            modified = true;
                        }
                        continue;
                    }
                    // Bez custom TAB: boty z cache — display z cache lub dirty na main.
                    if (knownBot) {
                        entry.setListed(true);
                        net.kyori.adventure.text.Component botDisplay = tabListDisplayCache.get(uuid);
                        if (botDisplay != null) {
                            entry.setDisplayName(botDisplay);
                        }
                        modified = true;
                        continue;
                    }
                    net.kyori.adventure.text.Component display = peekCachedTabListDisplay(viewer, uuid);
                    if (display != null) {
                        entry.setDisplayName(display);
                        modified = true;
                    } else if (realTracked) {
                        dirtyIdentities.add(uuid);
                    }
                }
                // Sortowanie: listOrder (1.21.2+) = źródło prawdy; teamy h### / hs### na 1.20.
                if (modified) {
                    event.markForReEncode(true);
                }
            }
        });
        packetListenerRegistered = true;
    }

    private void unregisterPacketEventsListener() {
        if (packetListener != null) {
            try {
                PacketEvents.getAPI().getEventManager().unregisterListener(packetListener);
            } catch (RuntimeException | LinkageError ignored) {
            }
            packetListener = null;
        }
        packetListenerRegistered = false;
        enabled = false;
    }

    private Player resolvePacketViewer(com.github.retrooper.packetevents.event.PacketSendEvent event) {
        try {
            Object platformPlayer = event.getPlayer();
            if (platformPlayer instanceof Player player) {
                return player;
            }
        } catch (RuntimeException | LinkageError ignored) {
        }
        try {
            if (event.getUser() != null && event.getUser().getUUID() != null) {
                return Bukkit.getPlayer(event.getUser().getUUID());
            }
        } catch (RuntimeException | LinkageError ignored) {
        }
        return null;
    }

    private void registerListener() {
        if (!listenerRegistered) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
            listenerRegistered = true;
        }
        registerPacketEventsListener();
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
        if (!hasPendingUpdateWork()) {
            return;
        }
        if (!updateQueued.compareAndSet(false, true)) {
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                updateAll();
            } catch (RuntimeException exception) {
                updateQueued.set(false);
                throw exception;
            }
        });
    }

    private boolean hasPendingUpdateWork() {
        if (!enabled) {
            return false;
        }
        if (!dirtyPlayers.isEmpty() || !dirtyIdentities.isEmpty() || !dirtyHeaderFooters.isEmpty()) {
            return true;
        }

        long now = System.currentTimeMillis();
        if (updateIdentityOnEveryTabTick || (periodicIdentityRefresh && now >= nextIdentityFullUpdateMillis)) {
            return true;
        }
        if (idleAfkEnabled && now >= nextIdleAfkCheckMillis) {
            return true;
        }
        if (customTabEnabled && now >= nextFullRowUpdateMillis) {
            return true;
        }
        return headerFooterEnabled && shouldRefreshHeaderFooterPeriodically() && now >= nextHeaderFooterUpdateMillis;
    }

    private void updateAll() {
        if (!enabled) {
            updateQueued.set(false);
            return;
        }

        updateIdleAfkStates();
        updateIdentitiesIfNeeded();
        markRowsDirtyIfNeeded();
        markHeaderFootersDirtyIfNeeded();

        if (!isPacketEventsReady()) {
            updateQueued.set(false);
            queueUpdateAllIfWorkRemains();
            return;
        }

        Player[] headerFooterPlayers = drainDirtyHeaderFooters();
        Player[] players = drainDirtyPlayers();
        currentBatchHadRowChanges = false;
        updateHeaderFooterBatch(headerFooterPlayers, 0, players);
    }

    private void updateHeaderFooterBatch(Player[] players, int startIndex, Player[] rowPlayers) {
        if (!enabled || !isPacketEventsReady()) {
            updateQueued.set(false);
            return;
        }

        try {
            int batchSize = effectivePlayersPerTick();
            int endIndex = Math.min(players.length, startIndex + batchSize);
            for (int i = startIndex; i < endIndex; i++) {
                Player player = players[i];
                if (player != null && player.isOnline()) {
                    try {
                        sendHeaderFooter(player);
                    } catch (RuntimeException | Error playerException) {
                        plugin.getLogger().warning("Blad header/footer TAB dla " + player.getName() + ": " + playerException);
                    }
                }
            }

            if (endIndex >= players.length) {
                updateBatch(rowPlayers, 0);
                return;
            }

            Bukkit.getScheduler().runTaskLater(plugin, () -> updateHeaderFooterBatch(players, endIndex, rowPlayers), 1L);
        } catch (RuntimeException | Error exception) {
            // Bez tego jeden wyjatek zostawia updateQueued=true i TAB zamarza dla calego serwera.
            updateQueued.set(false);
            plugin.getLogger().warning("Przerwana partia header/footer TAB: " + exception);
            queueUpdateAllIfWorkRemains();
        }
    }

    private void updateBatch(Player[] players, int startIndex) {
        if (!enabled || !isPacketEventsReady()) {
            updateQueued.set(false);
            return;
        }

        try {
            int batchSize = effectivePlayersPerTick();
            int endIndex = Math.min(players.length, startIndex + batchSize);
            for (int i = startIndex; i < endIndex; i++) {
                try {
                    if (updatePlayer(players[i], false)) {
                        currentBatchHadRowChanges = true;
                    }
                } catch (RuntimeException | Error playerException) {
                    plugin.getLogger().warning("Blad aktualizacji wierszy TAB: " + playerException);
                }
            }

            if (endIndex >= players.length) {
                adjustAdaptiveRowRefresh();
                updateQueued.set(false);
                queueUpdateAllIfWorkRemains();
                return;
            }

            Bukkit.getScheduler().runTaskLater(plugin, () -> updateBatch(players, endIndex), 1L);
        } catch (RuntimeException | Error exception) {
            // Bez tego jeden wyjatek zostawia updateQueued=true i TAB zamarza dla calego serwera.
            updateQueued.set(false);
            plugin.getLogger().warning("Przerwana partia wierszy TAB: " + exception);
            queueUpdateAllIfWorkRemains();
        }
    }

    private void queueUpdateAllIfWorkRemains() {
        if (plugin.isEnabled() && hasPendingUpdateWork()) {
            Bukkit.getScheduler().runTaskLater(plugin, this::queueUpdateAll, 1L);
        }
    }

    private Player[] drainDirtyPlayers() {
        List<Player> players = new ArrayList<>(dirtyPlayers.size());
        for (UUID uuid : dirtyPlayers) {
            if (!dirtyPlayers.remove(uuid)) {
                continue;
            }
            dirtyHeaderFooters.remove(uuid);

            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                players.add(player);
            }
        }
        return players.toArray(Player[]::new);
    }

    private Player[] drainDirtyHeaderFooters() {
        List<Player> players = new ArrayList<>(dirtyHeaderFooters.size());
        for (UUID uuid : dirtyHeaderFooters) {
            if (!dirtyHeaderFooters.remove(uuid)) {
                continue;
            }

            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                players.add(player);
            }
        }
        return players.toArray(Player[]::new);
    }

    private void markAllDirty() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            dirtyPlayers.add(player.getUniqueId());
        }
    }

    private void markAllHeaderFootersDirty() {
        if (!headerFooterEnabled) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            dirtyHeaderFooters.add(player.getUniqueId());
        }
    }

    private void markAllIdentitiesDirty() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            dirtyIdentities.add(player.getUniqueId());
        }
    }

    private void updateIdleAfkStates() {
        if (!idleAfkEnabled) {
            return;
        }

        long now = System.currentTimeMillis();
        nextIdleAfkCheckMillis = now + 1_000L;
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            long lastActivity = lastIdleActivity.computeIfAbsent(uuid, ignored -> now);
            if (now - lastActivity >= idleAfkAfterMillis && idleAfkPlayers.add(uuid)) {
                markIdleStateChanged(uuid);
            }
        }
    }

    private void markPlayerActivity(Player player) {
        if (!idleAfkEnabled || player == null) {
            return;
        }

        UUID uuid = player.getUniqueId();
        lastIdleActivity.put(uuid, System.currentTimeMillis());
        if (idleAfkPlayers.remove(uuid)) {
            markIdleStateChanged(uuid);
        }
    }

    private void markIdleStateChanged(UUID uuid) {
        dirtyIdentities.add(uuid);
        invalidateRenderedRows(uuid);
        if (customTabEnabled) {
            dirtyPlayers.add(uuid);
        }
        SidebarManager sidebarManager = plugin.getSidebarManager();
        if (sidebarManager != null) {
            sidebarManager.refreshPlayer(uuid);
        }
        queueUpdateAll();
    }

    private void invalidateAllLuckPermsIdentities() {
        luckPermsIdentityCache.clear();
        rankNameColorCache.clear();
        groupNameColorCache.clear();
        tabListDisplayCache.clear();
        relationalTabDisplayCache.clear();
        nametagCacheByTarget.clear();
        realTagSnapshots.clear();
        invalidateFakeTagSnapshots();
        tabOrderDirty = true;
        markAllIdentitiesDirty();
        scheduleFakeTagsSyncWithPlayers();
    }

    private void scheduleOnlineCountRefresh() {
        if (!headerFooterEnabled && (!customTabEnabled || !rowsUseOnlineCount)) {
            return;
        }
        if (!onlineCountRefreshQueued.compareAndSet(false, true)) {
            return;
        }
        if (onlineCountRefreshTask != null) {
            onlineCountRefreshTask.cancel();
        }
        onlineCountRefreshTask = new BukkitRunnable() {
            @Override
            public void run() {
                onlineCountRefreshTask = null;
                onlineCountRefreshQueued.set(false);
                if (!enabled) {
                    return;
                }
                int visibleOnline = headerFooterUsesOnlineCount || rowsUseOnlineCount
                    ? networkOrLocalOnlineCount()
                    : getVisibleOnlineCount();
                if (visibleOnline == lastOnlineCountForHeaderRefresh) {
                    return;
                }
                lastOnlineCountForHeaderRefresh = visibleOnline;
                cachedVisibleOnlineCount = -1;
                sharedHeaderFooterDirty = true;
                cachedSharedHeader = null;
                cachedSharedFooter = null;
                // H/F: shared cache = 1× PAPI, potem O(n) pakietów. Bez przebudowy wszystkich wierszy TAB.
                if (headerFooterEnabled) {
                    markAllHeaderFootersDirty();
                }
                if (customTabEnabled && rowsUseOnlineCount && onlineCountRowIndices.length > 0) {
                    refreshRowsByIndex(onlineCountRowIndices);
                }
                queueUpdateAll();
            }
        }.runTaskLater(plugin, onlineCountRefreshDelayTicks());
    }

    private long onlineCountRefreshDelayTicks() {
        int online = Bukkit.getOnlinePlayers().size();
        if (online > 150) {
            return 60L;
        }
        if (online > 80) {
            return 50L;
        }
        return ONLINE_COUNT_REFRESH_DELAY_TICKS;
    }

    private void markHeaderFooterDirtyAfterOnlineCountChange() {
        scheduleOnlineCountRefresh();
    }

    private void updateIdentitiesIfNeeded() {
        long now = System.currentTimeMillis();
        boolean identityWave = false;
        if (updateIdentityOnEveryTabTick && dirtyIdentities.isEmpty()) {
            // Re-render dynamic placeholders, but keep LuckPerms data cached. LuckPerms changes
            // are invalidated by UserDataRecalculateEvent instead of polling every tab tick.
            markAllIdentitiesDirty();
            identityWave = true;
        }
        if (periodicIdentityRefresh && now >= nextIdentityFullUpdateMillis) {
            invalidateAllLuckPermsIdentities();
            nextIdentityFullUpdateMillis = now + identityUpdateIntervalTicks * 50L;
            identityWave = true;
        }
        updateDirtyIdentitiesNow();
        if (identityWave) {
            scheduleFakeTagsSyncThrottled();
        }
    }

    private void markRowsDirtyIfNeeded() {
        if (!customTabEnabled) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now < nextFullRowUpdateMillis) {
            return;
        }
        markAllDirty();
        nextFullRowUpdateMillis = now + adaptiveFullRowUpdateIntervalTicks * 50L;
    }

    private void adjustAdaptiveRowRefresh() {
        if (!customTabEnabled) {
            return;
        }
        if (!adaptiveRefresh) {
            adaptiveFullRowUpdateIntervalTicks = fullRowUpdateIntervalTicks;
            return;
        }

        if (currentBatchHadRowChanges) {
            adaptiveFullRowUpdateIntervalTicks = fullRowUpdateIntervalTicks;
            return;
        }

        adaptiveFullRowUpdateIntervalTicks = Math.min(
            maxAdaptiveFullRowUpdateIntervalTicks,
            Math.max(fullRowUpdateIntervalTicks, adaptiveFullRowUpdateIntervalTicks * 2L)
        );
    }

    private void markHeaderFootersDirtyIfNeeded() {
        if (!headerFooterEnabled || !shouldRefreshHeaderFooterPeriodically()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now < nextHeaderFooterUpdateMillis) {
            return;
        }
        sharedHeaderFooterDirty = true;
        cachedSharedHeader = null;
        cachedSharedFooter = null;
        markAllHeaderFootersDirty();
        nextHeaderFooterUpdateMillis = now + headerFooterIntervalTicks * 50L;
    }

    private boolean shouldRefreshHeaderFooterPeriodically() {
        return headerFooterNeedsPeriodicRefresh;
    }

    private boolean updatePlayer(Player player, boolean forceCreate) {
        if (player == null || !player.isOnline() || !isPacketEventsReady()) {
            return false;
        }

        if (!customTabEnabled) {
            clearPlayerRows(player, false);
            restoreRealPlayersInTab(player);
            if (headerFooterEnabled) {
                sendHeaderFooter(player);
            }
            return false;
        }

        PlayerTablist tablist = tablists.computeIfAbsent(player.getUniqueId(), this::createTablist);
        String[] renderedRows = getRenderedRows(player, tablist, forceCreate);

        long now = System.currentTimeMillis();

        if (forceCreate || !tablist.created) {
            sendAddRows(player, tablist, renderedRows);
            applyLegacySlotSortTeams(player, tablist);
            ensureRealPlayersListedForViewer(player);
            tablist.created = true;
            if (headerFooterEnabled) {
                sendHeaderFooter(player, tablist);
                tablist.lastHeaderFooterUpdate = now;
            }
            // Drugi full re-render tylko gdy LP/identity nie były gotowe przy create.
            if (!isJoinIdentityComplete(player)) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline() && enabled && tablists.get(player.getUniqueId()) == tablist) {
                        for (int i = 0; i < tablist.lastRows.size(); i++) {
                            tablist.lastRows.set(i, null);
                        }
                        invalidateRenderedRows(tablist);
                        sendChangedRows(player, tablist, getRenderedRows(player, tablist, true));
                        applyLegacySlotSortTeams(player, tablist);
                    }
                }, 2L);
            }
            return true;
        }

        boolean rowsChanged = sendChangedRows(player, tablist, renderedRows);

        if (headerFooterEnabled && now - tablist.lastHeaderFooterUpdate >= headerFooterIntervalTicks * 50L) {
            sendHeaderFooter(player, tablist);
            tablist.lastHeaderFooterUpdate = now;
        }
        return rowsChanged;
    }

    private PlayerTablist createTablist(UUID viewerId) {
        PlayerTablist tablist = new PlayerTablist();
        int rowCount = getConfiguredRowCount();
        for (int i = 0; i < rowCount; i++) {
            UUID uuid = createRowUuid(viewerId, i);
            String name = PROFILE_NAME_PREFIX + String.format("%011d", i);
            UserProfile profile = new UserProfile(uuid, name, defaultTextures);
            tablist.profiles.add(profile);
            tablist.profileIds.add(uuid);
            tablist.lastRows.add(null);
        }
        return tablist;
    }

    private UUID createRowUuid(UUID viewerId, int row) {
        return UUID.nameUUIDFromBytes(("holly-tablist-row-" + viewerId + "-" + row).getBytes(StandardCharsets.UTF_8));
    }

    private List<UUID> createRowUuids(UUID viewerId) {
        List<UUID> ids = new ArrayList<>(MAX_ROWS);
        for (int i = 0; i < MAX_ROWS; i++) {
            ids.add(createRowUuid(viewerId, i));
        }
        return ids;
    }

    private void sendAddRows(Player viewer, PlayerTablist tablist, String[] renderedRows) {
        int rowCount = tablist.profiles.size();
        List<WrapperPlayServerPlayerInfoUpdate.PlayerInfo> entries = new ArrayList<>(rowCount);
        for (int i = 0; i < rowCount; i++) {
            UserProfile profile = tablist.profiles.get(i);
            String text = i < renderedRows.length ? renderedRows[i] : "";
            WrapperPlayServerPlayerInfoUpdate.PlayerInfo info = new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
                profile,
                true,
                Math.max(0, viewer.getPing()),
                GameMode.SURVIVAL,
                toComponent(text),
                null
            );
            try {
                // Jak gracze default: listOrder 0 — sloty pod rangami, w sobie po nazwie HCTAB000…
                info.setListOrder(0);
            } catch (RuntimeException | LinkageError ignored) {
            }
            entries.add(info);
            tablist.lastRows.set(i, text);
        }

        sendPlayerInfoUpdate(viewer, ADD_ROW_ACTIONS, ADD_ROW_ACTIONS_LEGACY, entries);
    }

    private boolean sendChangedRows(Player viewer, PlayerTablist tablist, String[] renderedRows) {
        int rowCount = tablist.profiles.size();
        List<WrapperPlayServerPlayerInfoUpdate.PlayerInfo> updates = new ArrayList<>(Math.min(renderedRows.length, rowCount));

        for (int i = 0; i < rowCount; i++) {
            String text = i < renderedRows.length ? renderedRows[i] : "";
            if (text.equals(tablist.lastRows.get(i))) {
                continue;
            }

            UserProfile profile = tablist.profiles.get(i);
            updates.add(new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
                profile,
                true,
                0,
                GameMode.SURVIVAL,
                toComponent(text),
                null
            ));
            tablist.lastRows.set(i, text);
        }

        if (updates.isEmpty()) {
            return false;
        }

        WrapperPlayServerPlayerInfoUpdate packet = new WrapperPlayServerPlayerInfoUpdate(CHANGE_ROW_ACTIONS, updates);
        sendPacket(viewer, packet);
        return true;
    }

    private boolean sendChangedRows(Player viewer, PlayerTablist tablist, String[] renderedRows, int[] rowIndices) {
        if (rowIndices == null || rowIndices.length == 0) {
            return false;
        }

        List<WrapperPlayServerPlayerInfoUpdate.PlayerInfo> updates = new ArrayList<>(Math.min(rowIndices.length, MAX_ROWS));
        for (int i : rowIndices) {
            if (i < 0 || i >= tablist.profiles.size()) {
                continue;
            }
            String text = i < renderedRows.length && renderedRows[i] != null ? renderedRows[i] : "";
            if (text.equals(tablist.lastRows.get(i))) {
                continue;
            }

            UserProfile profile = tablist.profiles.get(i);
            updates.add(new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
                profile,
                true,
                0,
                GameMode.SURVIVAL,
                toComponent(text),
                null
            ));
            tablist.lastRows.set(i, text);
        }

        if (updates.isEmpty()) {
            return false;
        }

        sendPacket(viewer, new WrapperPlayServerPlayerInfoUpdate(CHANGE_ROW_ACTIONS, updates));
        return true;
    }

    private void hideRealPlayersFromTab(Player viewer) {
        hideRealPlayersFromTab(viewer, false);
    }

    private void hideRealPlayersFromTab(Player viewer, boolean force) {
        if (viewer == null || !viewer.isOnline()) {
            return;
        }

        Set<UUID> hiddenTargets = hiddenRealPlayersByViewer.computeIfAbsent(viewer.getUniqueId(), ignored -> ConcurrentHashMap.newKeySet());
        List<Player> targets = new ArrayList<>();
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (force || hiddenTargets.add(target.getUniqueId())) {
                targets.add(target);
            }
        }

        if (targets.isEmpty()) {
            return;
        }

        sendRealPlayersListedState(viewer, false, targets);
        for (Player target : targets) {
            hiddenTargets.add(target.getUniqueId());
        }
    }

    private void hideRealPlayerFromTab(Player viewer, Player target, boolean force) {
        if (viewer == null || target == null || !viewer.isOnline() || !target.isOnline()) {
            return;
        }

        Set<UUID> hiddenTargets = hiddenRealPlayersByViewer.computeIfAbsent(viewer.getUniqueId(), ignored -> ConcurrentHashMap.newKeySet());
        if (!force && !hiddenTargets.add(target.getUniqueId())) {
            return;
        }

        sendRealPlayersListedState(viewer, false, List.of(target));
        hiddenTargets.add(target.getUniqueId());
    }

    private void ensureRealPlayersListedForViewer(Player viewer) {
        if (viewer == null || !viewer.isOnline() || !customTabEnabled) {
            return;
        }
        // Sync O(n)×PAPI przy create zabijał tick — ten sam chunkowany pump co na joinie.
        ensureRealPlayersListedOnJoin(viewer);
    }

    /**
     * Kazdy wpis renderuje display name przez PAPI — przy 200+ online sync petla
     * zabijala tick przy masowym joinie. Rozkladamy na chunki po tickach.
     */
    private void ensureRealPlayersListedOnJoin(Player player) {
        if (!enabled || !customTabEnabled || player == null || !player.isOnline()) {
            return;
        }
        // Custom tab włączony = gracze powinni być ukryci (listed=false), nie pokazywani
        Set<UUID> hiddenTargets = hiddenRealPlayersByViewer.computeIfAbsent(player.getUniqueId(), ignored -> ConcurrentHashMap.newKeySet());
        for (Player target : Bukkit.getOnlinePlayers()) {
            hiddenTargets.add(target.getUniqueId());
        }
        pendingListedSync.add(player.getUniqueId());
        ensureListedSyncTask();
    }

    private void ensureListedSyncTask() {
        if (listedSyncTask != null) {
            return;
        }
        listedSyncTask = new BukkitRunnable() {
            @Override
            public void run() {
                tickListedSync();
            }
        }.runTaskTimer(plugin, NAMETAG_PUMP_PERIOD_TICKS, NAMETAG_PUMP_PERIOD_TICKS);
    }

    private void tickListedSync() {
        if (!enabled || !customTabEnabled) {
            cancelListedSyncTask();
            return;
        }
        if (activeListedSyncViewer == null) {
            activeListedSyncViewer = pendingListedSync.poll();
            activeListedSyncIndex = 0;
            if (activeListedSyncViewer == null) {
                cancelListedSyncTask();
                return;
            }
        }

        Player viewer = Bukkit.getPlayer(activeListedSyncViewer);
        if (viewer == null || !viewer.isOnline()) {
            activeListedSyncViewer = null;
            return;
        }

        Player[] online = Bukkit.getOnlinePlayers().toArray(new Player[0]);
        int end = Math.min(activeListedSyncIndex + FAKE_TAB_DISPLAY_PACKET_CHUNK, online.length);
        List<Player> chunk = new ArrayList<>(end - activeListedSyncIndex);
        for (int i = activeListedSyncIndex; i < end; i++) {
            if (online[i] != null && online[i].isOnline()) {
                chunk.add(online[i]);
            }
        }
        // Custom tab = gracze NIE są wyświetlani (listed=false)
        sendRealPlayersListedState(viewer, false, chunk);

        activeListedSyncIndex = end;
        if (activeListedSyncIndex >= online.length) {
            activeListedSyncViewer = null;
            activeListedSyncIndex = 0;
        }
    }

    private void cancelListedSyncTask() {
        if (listedSyncTask != null) {
            listedSyncTask.cancel();
            listedSyncTask = null;
        }
        pendingListedSync.clear();
        activeListedSyncViewer = null;
        activeListedSyncIndex = 0;
    }

    private void restoreRealPlayersInTab(Player viewer) {
        Set<UUID> hiddenTargets = hiddenRealPlayersByViewer.remove(viewer.getUniqueId());
        if (hiddenTargets == null || hiddenTargets.isEmpty()) {
            return;
        }
        sendRealPlayersListedState(viewer, true, Bukkit.getOnlinePlayers());
    }

    private static Method playerSetListedMethod;
    private static boolean playerSetListedChecked;

    private void setListedPaperNative(Player player, boolean listed) {
        if (!playerSetListedChecked) {
            playerSetListedChecked = true;
            try {
                playerSetListedMethod = Player.class.getMethod("setListed", boolean.class);
            } catch (NoSuchMethodException ignored) {
                playerSetListedMethod = null;
            }
        }
        if (playerSetListedMethod != null) {
            try {
                playerSetListedMethod.invoke(player, listed);
            } catch (Exception ignored) {
            }
        }
    }

    private void sendRealPlayersListedState(Player viewer, boolean listed, Collection<? extends Player> targets) {
        if (viewer == null || !viewer.isOnline() || !isPacketEventsReady()) {
            return;
        }

        List<WrapperPlayServerPlayerInfoUpdate.PlayerInfo> entries = new ArrayList<>();
        for (Player target : targets) {
            if (target == null || !target.isOnline()) {
                continue;
            }
            // NIE wywołuj Player#setListed — to globalny stan Paper i po hide
            // prawdziwi gracze znikają u WSZYSTKICH, zostają same boty.
            // Przy listed=false nie buduj display przez PAPI — wystarczy flaga listed.
            net.kyori.adventure.text.Component display = listed
                ? cacheTabListDisplay(target)
                : tabListDisplayCache.get(target.getUniqueId());
            entries.add(new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
                new UserProfile(target.getUniqueId(), target.getName()),
                listed,
                Math.max(0, target.getPing()),
                GameMode.SURVIVAL,
                display,
                null
            ));
        }

        if (entries.isEmpty()) {
            return;
        }

        sendPacket(viewer, new WrapperPlayServerPlayerInfoUpdate(UPDATE_LISTED_ACTIONS, entries));
    }

    private void sendHeaderFooter(Player player) {
        String renderedHeader = resolveRenderedHeader(player);
        String renderedFooter = resolveRenderedFooter(player);
        HeaderFooterState previous = lastHeaderFooters.get(player.getUniqueId());
        if (previous != null && previous.matches(renderedHeader, renderedFooter)) {
            return;
        }
        PlayerTablist tablist = tablists.get(player.getUniqueId());
        if (tablist != null) {
            tablist.lastHeader = renderedHeader;
            tablist.lastFooter = renderedFooter;
        }
        sendPacket(player, new WrapperPlayServerPlayerListHeaderAndFooter(headerComponent(renderedHeader), footerComponent(renderedFooter)));
        lastHeaderFooters.put(player.getUniqueId(), new HeaderFooterState(renderedHeader, renderedFooter));
    }

    private void sendHeaderFooter(Player player, PlayerTablist tablist) {
        String renderedHeader = resolveRenderedHeader(player);
        String renderedFooter = resolveRenderedFooter(player);
        if (renderedHeader.equals(tablist.lastHeader) && renderedFooter.equals(tablist.lastFooter)) {
            return;
        }
        tablist.lastHeader = renderedHeader;
        tablist.lastFooter = renderedFooter;
        lastHeaderFooters.put(player.getUniqueId(), new HeaderFooterState(renderedHeader, renderedFooter));
        sendPacket(player, new WrapperPlayServerPlayerListHeaderAndFooter(headerComponent(renderedHeader), footerComponent(renderedFooter)));
    }

    private String resolveRenderedHeader(Player player) {
        if (headerStatic) {
            return staticRenderedHeader;
        }
        if (headerFooterSharedOnly) {
            return resolveSharedRenderedHeaderFooter(true);
        }
        return renderMultiline(player, header);
    }

    private String resolveRenderedFooter(Player player) {
        if (footerStatic) {
            return staticRenderedFooter;
        }
        if (headerFooterSharedOnly) {
            return resolveSharedRenderedHeaderFooter(false);
        }
        return renderMultiline(player, footer);
    }

    /** Jedno parsowanie MiniMessage/PAPI na cykl — ten sam header/footer dla wszystkich widzów. */
    private String resolveSharedRenderedHeaderFooter(boolean wantHeader) {
        long now = System.currentTimeMillis();
        long ttl = headerFooterIntervalTicks * 50L;
        if (!sharedHeaderFooterDirty
            && cachedSharedHeader != null
            && cachedSharedFooter != null
            && now - cachedSharedHeaderFooterAt < ttl) {
            return wantHeader ? cachedSharedHeader : cachedSharedFooter;
        }
        Player sample = null;
        for (Player online : Bukkit.getOnlinePlayers()) {
            sample = online;
            break;
        }
        cachedSharedHeader = renderMultiline(sample, header);
        cachedSharedFooter = renderMultiline(sample, footer);
        cachedSharedHeaderFooterAt = now;
        sharedHeaderFooterDirty = false;
        cachedHeaderText = null;
        cachedHeaderComponent = null;
        cachedFooterText = null;
        cachedFooterComponent = null;
        return wantHeader ? cachedSharedHeader : cachedSharedFooter;
    }

    /**
     * Header/footer jest ten sam dla wszystkich graczy, ale wspoldzielony cache w ColutUtils
     * bywa wypierany przez lore/GUI, wiec kazdy z 200+ graczy parsowal MiniMessage od nowa.
     */
    private Component headerComponent(String text) {
        if (cachedHeaderComponent != null && text.equals(cachedHeaderText)) {
            return cachedHeaderComponent;
        }
        Component component = toComponent(text);
        cachedHeaderText = text;
        cachedHeaderComponent = component;
        return component;
    }

    private Component footerComponent(String text) {
        if (cachedFooterComponent != null && text.equals(cachedFooterText)) {
            return cachedFooterComponent;
        }
        Component component = toComponent(text);
        cachedFooterText = text;
        cachedFooterComponent = component;
        return component;
    }

    private String[] getRenderedRows(Player player, PlayerTablist tablist, boolean force) {
        if (rowsStatic) {
            return staticRenderedRows;
        }
        long now = System.currentTimeMillis();
        if (!force
            && rowRenderCacheMillis > 0L
            && tablist.renderedRows != null
            && now - tablist.renderedRowsAt <= rowRenderCacheMillis) {
            return tablist.renderedRows;
        }

        String[] rendered = renderRows(player);
        tablist.renderedRows = rendered;
        tablist.renderedRowsAt = now;
        return rendered;
    }

    private void invalidateRenderedRows(PlayerTablist tablist) {
        if (tablist != null) {
            tablist.renderedRowsAt = 0L;
            tablist.renderedRows = null;
        }
    }

    private void invalidateRenderedRows(UUID uuid) {
        invalidateRenderedRows(tablists.get(uuid));
    }

    private void invalidateAllRenderedRows() {
        for (PlayerTablist tablist : tablists.values()) {
            invalidateRenderedRows(tablist);
        }
    }

    private int getConfiguredRowCount() {
        if (fillEmptyRows) {
            return MAX_ROWS;
        }
        return Math.max(1, Math.min(rows.size(), MAX_ROWS));
    }

    private String[] renderRows(Player player) {
        if (rowsStatic) {
            return staticRenderedRows;
        }

        // Sprawdź czy trzeba zmienić stronę animacji
        List<String> activeRows = getCurrentAnimationRows();

        int limit = Math.min(activeRows.size(), MAX_ROWS);
        String[] rendered = new String[limit];
        for (int i = 0; i < limit; i++) {
            rendered[i] = renderLine(player, activeRows.get(i));
        }
        return rendered;
    }

    private String[] renderRows(Player player, int[] indices) {
        List<String> activeRows = getCurrentAnimationRows();
        int limit = Math.min(activeRows.size(), MAX_ROWS);
        String[] rendered = new String[limit];
        if (indices == null) {
            return rendered;
        }
        for (int index : indices) {
            if (index >= 0 && index < limit) {
                rendered[index] = renderLine(player, activeRows.get(index));
            }
        }
        return rendered;
    }

    /**
     * Zwraca aktualną listę wierszy - z animacji lub domyślne rows
     */
    private List<String> getCurrentAnimationRows() {
        if (!animationEnabled || animationPages.isEmpty() || animationPageNames.isEmpty()) {
            return rows;
        }

        long now = System.currentTimeMillis();
        if (now >= nextAnimationPageChangeMillis) {
            // Zmień stronę
            currentAnimationPage = (currentAnimationPage + 1) % animationPageNames.size();
            nextAnimationPageChangeMillis = now + animationIntervalMillis;

            // Oznacz wszystkich graczy jako dirty, żeby zaktualizować ich tabliste
            markAllDirty();
            queueUpdateAll();
        }

        String currentPageName = animationPageNames.get(currentAnimationPage);
        List<String> pageRows = animationPages.get(currentPageName);
        return pageRows != null ? pageRows : rows;
    }

    private String renderMultiline(Player player, List<String> lines) {
        if (lines.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                builder.append('\n');
            }
            builder.append(renderLine(player, lines.get(i)));
        }
        return builder.toString();
    }

    private String renderLine(Player player, String line) {
        if (line == null || line.isEmpty()) {
            return "";
        }
        if (!hasPlaceholder(line)) {
            return staticColorCache.computeIfAbsent(line, value -> value);
        }
        return parsePlaceholders(player, line);
    }

    private boolean hasPlaceholder(String text) {
        return text != null && text.indexOf('%') >= 0;
    }

    public void refreshAllIdentities() {
        if (!plugin.isEnabled()) {
            return;
        }
        runOnMainThread(() -> {
            invalidateAllLuckPermsIdentities();
            lastNameTagTeams.clear();
            lastNameTagSuffixes.clear();
            invalidateAllRenderedRows();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (luckPerms != null) {
                    try {
                        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
                        if (user != null) {
                            user.getCachedData().invalidate();
                        }
                    } catch (Throwable ignored) {
                    }
                }
                updatePlayerTabName(player);
            }
            markAllDirty();
            markAllIdentitiesDirty();
            scheduleBatchedNameTagRefresh();
            scheduleBatchedFakeNameTagRefresh();
            queueUpdateAll();
            scheduleFakeTagsSyncWithPlayers();
        });
    }

    public void refreshPlayerIdentity(UUID uuid) {
        if (!plugin.isEnabled() || uuid == null) {
            return;
        }
        runOnMainThread(() -> {
            luckPermsIdentityCache.remove(uuid);
            rankNameColorCache.remove(uuid);
            invalidateTabListDisplay(uuid);
            nametagCacheByTarget.remove(uuid);
            realTagSnapshots.remove(uuid);
            for (Map<UUID, String> m : lastNameTagTeams.values()) {
                m.remove(uuid);
            }
            for (Map<UUID, String> m : lastNameTagSuffixes.values()) {
                m.remove(uuid);
            }
            dirtyIdentities.add(uuid);
            invalidateRenderedRows(uuid);
            if (customTabEnabled) {
                dirtyPlayers.add(uuid);
            }
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                if (luckPerms != null) {
                    try {
                        User user = luckPerms.getUserManager().getUser(uuid);
                        if (user != null) {
                            user.getCachedData().invalidate();
                        }
                    } catch (Throwable ignored) {
                    }
                }
                updatePlayerTabName(player);
                queueJoinNametagWork(player);
            }
            queueUpdateAll();
        });
    }

    public void refreshLuckPermsIdentity(UUID uuid) {
        if (!plugin.isEnabled() || uuid == null) {
            return;
        }
        runOnMainThread(() -> {
            luckPermsIdentityCache.remove(uuid);
            rankNameColorCache.remove(uuid);
            invalidateTabListDisplay(uuid);
            lastPlayerListOrders.remove(uuid);
            pendingListOrderBroadcasts.remove(uuid);
            paperListOrderSynced.remove(uuid);
            nametagCacheByTarget.remove(uuid);
            realTagSnapshots.remove(uuid);
            for (Map<UUID, String> m : lastNameTagTeams.values()) {
                m.remove(uuid);
            }
            for (Map<UUID, String> m : lastNameTagSuffixes.values()) {
                m.remove(uuid);
            }
            tabOrderDirty = true;
            dirtyIdentities.add(uuid);
            invalidateRenderedRows(uuid);
            if (customTabEnabled) {
                dirtyPlayers.add(uuid);
            }
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                if (luckPerms != null) {
                    try {
                        User user = luckPerms.getUserManager().getUser(uuid);
                        if (user != null) {
                            user.getCachedData().invalidate();
                        }
                    } catch (Throwable ignored) {
                    }
                }
                updatePlayerTabName(player);
                queueJoinNametagWork(player);
            }
            queueUpdateAll();
        });
    }

    private void runOnMainThread(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    public void refreshLeaderboardRows() {
        if (!plugin.isEnabled()) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!enabled || !customTabEnabled) {
                return;
            }
            invalidateAllRenderedRows();
            if (leaderboardRowIndices.length == 0) {
                markAllDirty();
            } else {
                refreshRowsByIndex(leaderboardRowIndices);
            }
            adaptiveFullRowUpdateIntervalTicks = fullRowUpdateIntervalTicks;
            queueUpdateAll();
        });
    }

    private void refreshRowsByIndex(int[] rowIndices) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerTablist tablist = tablists.get(player.getUniqueId());
            if (tablist == null || !tablist.created) {
                dirtyPlayers.add(player.getUniqueId());
                continue;
            }
            invalidateRenderedRows(tablist);
            sendChangedRows(player, tablist, renderRows(player, rowIndices), rowIndices);
        }
    }

    private void updateDirtyIdentitiesNow() {
        if (!enabled) {
            resetIdentityAll();
            return;
        }

        int processed = 0;
        int batchLimit = effectiveIdentityBatchSize();
        for (UUID uuid : dirtyIdentities) {
            if (processed >= batchLimit) {
                break;
            }
            if (!dirtyIdentities.remove(uuid)) {
                continue;
            }
            processed++;
            Player target = Bukkit.getPlayer(uuid);
            if (target != null && target.isOnline()) {
                nametagCacheByTarget.remove(uuid);
                realTagSnapshots.remove(uuid);
                updatePlayerTabName(target);
                queueJoinNametagWork(target);
            }
        }

        if (tabOrderDirty && dirtyIdentities.isEmpty()) {
            scheduleTabOrderRefresh();
        }
    }

    private void updateAllIdentitiesNow() {
        if (!enabled) {
            resetIdentityAll();
            return;
        }
        invalidateAllLuckPermsIdentities();
        markAllDirty();
        updateDirtyIdentitiesNow();
    }

    private void updatePlayerTabName(Player target) {
        if (target == null || !target.isOnline()) {
            return;
        }
        if (customTabEnabled || tabNameUsesRelational) {
            target.playerListName(null);
            return;
        }
        String format = playerNameFormat;
        if (format == null || format.isBlank()) {
            format = "%luckperms_prefix%%name_color%%player_name%";
        }
        String nameColor = getRankNameColor(target);
        if (nameColor.isBlank()) {
            nameColor = resolveLuckPermsGroupNameColor(fallbackGroup);
        }
        String formatted = format.replace("%name_color%", nameColor).replace("%rank_name_color%", nameColor);
        formatted = parseIdentityPlaceholders(target, target, formatted);
        IncognitoManager incognito = plugin.getIncognitoManager();
        if (incognito != null && incognito.isIncognito(target)) {
            // Display name idzie per-widz pakietem (UKRYTY + żółty nick), nie globalnym playerListName.
            target.playerListName(null);
            return;
        }
        target.playerListName(ColorUtils.colorizeComponent(formatted));
    }

    private void scheduleBatchedNameTagRefresh() {
        if (!enabled || !plugin.isEnabled()) {
            return;
        }
        if (nametagBatchTask != null) {
            nametagRefreshPending = true;
            scheduleFakeTagsSyncWithPlayers();
            return;
        }
        nametagRefreshPending = false;
        nametagCacheByTarget.clear();
        realTagSnapshots.clear();
        nametagViewerCache = Bukkit.getOnlinePlayers().toArray(new Player[0]);
        nametagViewerIndex = 0;
        nametagTargetIndex = 0;
        if (nametagViewerCache.length == 0) {
            return;
        }
        nametagBatchTask = new BukkitRunnable() {
            @Override
            public void run() {
                tickBatchedNameTags();
            }
        }.runTaskTimer(plugin, NAMETAG_PUMP_PERIOD_TICKS, NAMETAG_PUMP_PERIOD_TICKS);
        // Ten sam update nametagów / player-name u botów (format z tablist.yml).
        scheduleFakeTagsSyncWithPlayers();
    }

    /**
     * Gdy tablista odświeża nametagi/tożsamość graczy — boty dostają ten sam przebieg.
     * Bez softRefresh (czyści cache teamów i robi full rebuild = spike MSPT).
     */
    private void scheduleFakeTagsSyncWithPlayers() {
        if (!enabled || !plugin.isEnabled()) {
            return;
        }
        if (!fakeIdentitySyncQueued.compareAndSet(false, true)) {
            return;
        }
        long now = System.currentTimeMillis();
        long waitMs = Math.max(0L, nextFakeIdentitySyncMillis - now);
        long delayTicks = Math.max(8L, (waitMs + 49L) / 50L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            fakeIdentitySyncQueued.set(false);
            if (!enabled || !plugin.isEnabled()) {
                return;
            }
            java.util.Collection<String> fakeNames = getSpooferFakeNames();
            if (fakeNames == null || fakeNames.isEmpty()) {
                return;
            }
            nextFakeIdentitySyncMillis = System.currentTimeMillis() + FAKE_TAGS_SYNC_MIN_GAP_MS;
            // Tylko bump wersji + batch — NIE czyść lastFakeNameTag* (shouldSkip działa).
            fakeTagVersion++;
            scheduleBatchedFakeNameTagRefresh();
        }, delayTicks);
    }

    /** Periodic identity wave — max raz na identity-interval (nie co tick tablisty). */
    private void scheduleFakeTagsSyncThrottled() {
        long now = System.currentTimeMillis();
        long minGap = Math.max(5_000L, identityUpdateIntervalTicks * 50L);
        if (now < nextFakeIdentitySyncMillis) {
            return;
        }
        nextFakeIdentitySyncMillis = now + minGap;
        scheduleFakeTagsSyncWithPlayers();
    }

    private void cancelNametagBatch() {
        cancelFullNametagBatchOnly();
    }

    private void cancelFullNametagBatchOnly() {
        if (nametagBatchTask != null) {
            nametagBatchTask.cancel();
            nametagBatchTask = null;
        }
        nametagViewerCache = null;
        nametagViewerIndex = 0;
        nametagTargetIndex = 0;
        nametagRefreshPending = false;
    }

    private void tickBatchedNameTags() {
        if (!enabled) {
            cancelNametagBatch();
            return;
        }
        Player[] viewers = nametagViewerCache;
        if (viewers == null || viewers.length == 0) {
            cancelNametagBatch();
            return;
        }

        int targetCount = viewers.length;
        int processed = 0;
        int online = viewers.length;
        int pairsBudget = online > 180
            ? NAMETAG_PAIRS_PER_TICK_ULTRA
            : (online > 120
                ? NAMETAG_PAIRS_PER_TICK_HIGH_POP
                : (online > 80 ? NAMETAG_PAIRS_PER_TICK_MID_POP : NAMETAG_PAIRS_PER_TICK));

        while (processed < pairsBudget && nametagViewerIndex < viewers.length) {
            Player viewer = viewers[nametagViewerIndex];
            if (viewer == null || !viewer.isOnline()) {
                // Widz wyszedl — nie przepalaj na niego calego budzetu par (i tak nic nie wysylamy).
                nametagTargetIndex = 0;
                nametagViewerIndex++;
                continue;
            }
            Player target = viewers[nametagTargetIndex];
            if (target != null && target.isOnline()) {
                updateNameTagForViewer(viewer, target);
            }

            nametagTargetIndex++;
            if (nametagTargetIndex >= targetCount) {
                nametagTargetIndex = 0;
                nametagViewerIndex++;
            }
            processed++;
        }

        if (nametagViewerIndex >= viewers.length) {
            boolean pending = nametagRefreshPending;
            cancelFullNametagBatchOnly();
            if (pending && enabled) {
                scheduleBatchedNameTagRefresh();
            }
        }
    }

    private int effectivePlayersPerTick() {
        int online = Bukkit.getOnlinePlayers().size();
        // Przy wysokim online MNIEJSZY batch = niższy MSPT spike (rozłożenie na więcej ticków).
        if (online > 350) {
            return Math.max(2, Math.min(playersPerTick, 3));
        }
        if (online > 200) {
            return Math.max(3, Math.min(playersPerTick, online / 50));
        }
        if (online > 100) {
            return Math.max(4, Math.min(playersPerTick, online / 30));
        }
        return playersPerTick;
    }

    private int effectiveIdentityBatchSize() {
        int online = Bukkit.getOnlinePlayers().size();
        if (online > 200) {
            return Math.max(3, Math.min(identityPlayersPerTick, online / 40));
        }
        if (online > 80) {
            return Math.max(4, Math.min(identityPlayersPerTick, online / 25));
        }
        return identityPlayersPerTick;
    }

    private void updateNameTagsForTarget(Player target) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            updateNameTagForViewer(viewer, target);
        }
    }

    private void updateNameTagsForViewer(Player viewer) {
        updateRealNameTagsForViewer(viewer);
        queueViewerFakeTagResync(viewer);
    }

    private void updateRealNameTagsForViewer(Player viewer) {
        for (Player target : Bukkit.getOnlinePlayers()) {
            updateNameTagForViewer(viewer, target);
        }
    }

    private void queueViewerFakeTagResync(Player viewer) {
        if (viewer == null) {
            return;
        }
        pendingViewerFakeTagResync.add(viewer.getUniqueId());
        if (!viewerFakeTagResyncScheduled.compareAndSet(false, true)) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            viewerFakeTagResyncScheduled.set(false);
            Set<UUID> ids = Set.copyOf(pendingViewerFakeTagResync);
            pendingViewerFakeTagResync.clear();
            if (ids.isEmpty()) {
                return;
            }
            // Typowy join = 1 widz → lokalny refresh, nie globalny O(online×boty).
            if (ids.size() == 1) {
                Player online = Bukkit.getPlayer(ids.iterator().next());
                if (online != null && online.isOnline()) {
                    refreshSpooferNameTagsForViewer(online);
                }
                return;
            }
            scheduleBatchedFakeNameTagRefresh();
        }, VIEWER_FAKE_TAG_RESYNC_DELAY_TICKS);
    }

    public boolean isCustomTabEnabled() {
        return customTabEnabled;
    }


    /** Nametag incognito tylko u jednego widza — po joinie / zmianie {@code Holly.cmd.incognito.view}. */
    public void refreshIncognitoForViewer(Player viewer) {
        if (viewer == null) {
            return;
        }
        IncognitoManager incognito = plugin.getIncognitoManager();
        if (incognito == null) {
            return;
        }
        UUID viewerId = viewer.getUniqueId();
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (!incognito.isIncognito(target)) {
                continue;
            }
            UUID uuid = target.getUniqueId();
            Map<UUID, String> viewerTeams = lastNameTagTeams.get(viewerId);
            if (viewerTeams != null) {
                viewerTeams.remove(uuid);
            }
            Map<UUID, String> viewerCache = lastNameTagSuffixes.get(viewerId);
            if (viewerCache != null) {
                viewerCache.remove(uuid);
            }
            updateNameTagForViewer(viewer, target);
        }
    }

    /** Po przełączeniu incognito: nowy nick/team u wszystkich, bez przebudowy całego TAB. */
    public void refreshIncognito(Player target) {
        if (target == null) {
            return;
        }
        UUID uuid = target.getUniqueId();
        nametagCacheByTarget.remove(uuid);
        realTagSnapshots.remove(uuid);
        luckPermsIdentityCache.remove(uuid);
        rankNameColorCache.remove(uuid);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            UUID viewerId = viewer.getUniqueId();
            Map<UUID, String> viewerTeams = lastNameTagTeams.get(viewerId);
            if (viewerTeams != null) {
                viewerTeams.remove(uuid);
            }
            Map<UUID, String> viewerCache = lastNameTagSuffixes.get(viewerId);
            if (viewerCache != null) {
                viewerCache.remove(uuid);
            }
        }
        updatePlayerTabName(target);
        updateNameTagsForTarget(target);
        updateNameTagsForViewer(target);
    }

    public void handleScoreboardChange(Player player) {
        if (player == null || !plugin.isEnabled()) {
            // Przy disable scheduler rzuca IllegalPluginAccessException i wywraca reszte onDisable.
            return;
        }
        runOnMainThread(() -> {
            // Pakietowe teamy nametagów nie zależą od Bukkit scoreboard —
            // wipe cache + globalny fake refresh = podwójna fala O(n·boty) przy każdym joinie.
            UUID viewerId = player.getUniqueId();
            Map<UUID, String> teams = lastNameTagTeams.get(viewerId);
            if ((teams == null || teams.isEmpty()) && !pendingJoinNametagIds.contains(viewerId)) {
                queueJoinNametagWork(player);
            }
            refreshSpooferNameTagsForViewer(player);
            player.setCollidable(false);
            if (plugin.getTopsManager() != null) {
                plugin.getTopsManager().reapplyNametags(player);
            }
        });
    }

    /** Woła HollySpoofer po dodaniu jednego bota — kolejkuj do batcha zamiast O(online) sync. */
    public void refreshSpooferBotJoined(String fakeName) {
        if (!enabled || fakeName == null || fakeName.isBlank() || !plugin.isEnabled()) {
            return;
        }
        // Pełny sync po każdym bocie przy 100+ viewerach zabija MSPT — użyj zbiorczego refresh.
        refreshSpooferNameTags();
    }

    /** Woła HollySpoofer po dodaniu / usunięciu bota — TAB display + nametagi botów. */
    public void refreshSpooferNameTags() {
        if (!enabled) {
            return;
        }
        if (!plugin.isEnabled()) {
            return;
        }
        if (!spooferNameTagRefreshQueued.compareAndSet(false, true)) {
            spooferTabRefreshPending = true;
            return;
        }
        Runnable work = () -> {
            spooferNameTagRefreshQueued.set(false);
            boolean pending = spooferTabRefreshPending;
            spooferTabRefreshPending = false;
            if (!enabled || !plugin.isEnabled()) {
                return;
            }
            softRefreshFakeTagSnapshots();
            flushNewFakeNameTagsImmediate();
            scheduleTabOrderRefresh();
            if (pending) {
                refreshSpooferNameTags();
            }
        };
        // Zawsze przez scheduler — na main threadzie work.run() kasowalo CAS i N wywolan w ticku = N pelnych przebudow.
        Bukkit.getScheduler().runTask(plugin, work);
    }

    /**
     * Teparka / izolacja — ukryj boty na TAB (Spoofer + HollyCore display).
     */
    public void setSpooferBotsHidden(Player viewer, boolean hidden) {
        if (viewer == null) {
            return;
        }
        UUID viewerId = viewer.getUniqueId();
        if (hidden) {
            spooferBotsHiddenViewers.add(viewerId);
            lastFakeTagVersions.remove(viewerId);
            lastFakeTabDisplayVersions.remove(viewerId);
            lastFakeTabDisplayNames.remove(viewerId);
            SpooferBridge.hideTabBots(viewer);
            return;
        }
        spooferBotsHiddenViewers.remove(viewerId);
        SpooferBridge.showTabBots(viewer);
    }

    public boolean areSpooferBotsHidden(Player viewer) {
        return viewer != null && spooferBotsHiddenViewers.contains(viewer.getUniqueId());
    }

    /**
     * Join gracza — tylko ten widz dostaje TAB display botów.
     * Bez przebudowy nametagów u wszystkich online.
     */
    public void refreshSpooferNameTagsForViewer(Player viewer) {
        if (!enabled || viewer == null || !viewer.isOnline() || !plugin.isEnabled()) {
            return;
        }
        if (areSpooferBotsHidden(viewer)) {
            return;
        }
        Runnable work = () -> {
            if (!enabled || !viewer.isOnline()) {
                return;
            }
            java.util.Collection<String> fakeNames = getSpooferFakeNames();
            if (fakeNames.isEmpty()) {
                return;
            }
            Map<String, FakeTagSnapshot> fakeTags = resolvePreparedFakeTags(fakeNames);
            if (fakeTags.isEmpty()) {
                return;
            }
            // Sync tylko przy małej liczbie botów — 96×Team packet = spike MSPT przy joinie.
            if (fakeTags.size() <= 12) {
                UUID viewerId = viewer.getUniqueId();
                cleanupRemovedFakeBots(viewer, fakeTags);
                Map<String, String> viewerTeams = lastFakeNameTagTeams.computeIfAbsent(
                    viewerId, key -> new ConcurrentHashMap<>());
                Map<String, String> viewerCache = lastFakeNameTagSuffixes.computeIfAbsent(
                    viewerId, key -> new ConcurrentHashMap<>());
                for (String fakeName : fakeTags.keySet()) {
                    FakeTagSnapshot tag = fakeNameTagViewerDependent
                        ? buildFakeTagSnapshotForViewer(viewer, fakeName)
                        : fakeTags.get(fakeName);
                    if (tag == null) {
                        continue;
                    }
                    if (shouldSkipFakeTeamUpdate(viewerTeams, viewerCache, fakeName, tag)) {
                        continue;
                    }
                    updateFakeNameTagForViewer(viewer, fakeName, tag, viewerTeams, viewerCache);
                }
                flushFakePlayerTabDisplayNames(viewer, fakeTags);
                lastFakeTagVersions.put(viewerId, fakeTagVersion);
                lastFakeTabDisplayVersions.put(viewerId, fakeTagVersion);
                return;
            }
            scheduleViewerFakeNameTagBatch(viewer, fakeTags);
        };
        if (Bukkit.isPrimaryThread()) {
            work.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, work);
        }
    }

    private Map<String, FakeTagSnapshot> resolvePreparedFakeTags(java.util.Collection<String> fakeNames) {
        if (fakeNames == null || fakeNames.isEmpty()) {
            return Map.of();
        }
        if (!preparedFakeTags.isEmpty()) {
            long signature = 1L;
            for (String fakeName : fakeNames) {
                signature = signature * 31L + (fakeName == null ? 0 : fakeName.hashCode());
            }
            signature = signature * 31L + fakeNames.size();
            if (signature == preparedFakeNamesSignature) {
                return preparedFakeTags;
            }
        }
        return prepareFakeTagSnapshots(fakeNames);
    }

    private void refreshFakeTagsDisplayForViewer(Player viewer, Map<String, FakeTagSnapshot> fakeTags) {
        if (viewer == null || fakeTags == null || fakeTags.isEmpty()) {
            return;
        }
        cleanupRemovedFakeBots(viewer, fakeTags);
        flushFakePlayerTabDisplayNames(viewer, fakeTags);
        UUID viewerId = viewer.getUniqueId();
        lastFakeTabDisplayVersions.put(viewerId, fakeTagVersion);
    }

    /**
     * Po dodaniu fakeslota od razu team + display — bez batcha 1 bot/tick,
     * żeby nick nie siedział na górze tablisty.
     * Teamy h… (sortowanie) idą z HollyCore; Spoofer może trzymać ht… na Bukkit scoreboard.
     */
    private void flushNewFakeNameTagsImmediate() {
        java.util.Collection<String> fakeNames = getSpooferFakeNames();
        Map<String, FakeTagSnapshot> fakeTags = prepareFakeTagSnapshots(fakeNames);
        int version = fakeTagVersion;
        int online = Bukkit.getOnlinePlayers().size();
        int botCount = fakeTags.size();

        // Przy większej populacji pełny sync na main = spike MSPT — zawsze batch.
        if (online > 80 || botCount > 40 || online * Math.max(1, botCount) > 800) {
            lastFakeTagVersions.clear();
            lastFakeTabDisplayVersions.clear();
            scheduleBatchedFakeNameTagRefresh();
            return;
        }

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer == null || !viewer.isOnline() || areSpooferBotsHidden(viewer)) {
                continue;
            }
            cleanupRemovedFakeBots(viewer, fakeTags);
            if (!fakeTags.isEmpty()) {
                Map<String, String> viewerTeams = lastFakeNameTagTeams.computeIfAbsent(
                    viewer.getUniqueId(), key -> new ConcurrentHashMap<>());
                Map<String, String> viewerCache = lastFakeNameTagSuffixes.computeIfAbsent(
                    viewer.getUniqueId(), key -> new ConcurrentHashMap<>());
                for (String fakeName : fakeTags.keySet()) {
                    FakeTagSnapshot tag = fakeNameTagViewerDependent
                        ? buildFakeTagSnapshotForViewer(viewer, fakeName)
                        : fakeTags.get(fakeName);
                    if (tag == null) {
                        continue;
                    }
                    if (shouldSkipFakeTeamUpdate(viewerTeams, viewerCache, fakeName, tag)) {
                        continue;
                    }
                    updateFakeNameTagForViewer(viewer, fakeName, tag, viewerTeams, viewerCache);
                }
            }
            flushFakePlayerTabDisplayNames(viewer, fakeTags);
            lastFakeTagVersions.put(viewer.getUniqueId(), version);
            lastFakeTabDisplayVersions.put(viewer.getUniqueId(), version);
        }
    }

    /** Od razu TAB display dla wszystkich botów u wszystkich widzów (bez batcha 1 widz/tick). */
    private void flushAllFakeTabDisplaysImmediate() {
        // Przy custom tab + dużej populacji display botów nie jest potrzebny na TAB (listed=false).
        if (customTabEnabled && Bukkit.getOnlinePlayers().size() > 80) {
            scheduleBatchedFakeNameTagRefresh();
            return;
        }
        java.util.Collection<String> fakeNames = getSpooferFakeNames();
        if (fakeNames.isEmpty()) {
            return;
        }
        Map<String, Object> tabDataMap = loadSpooferTabDataMap();
        currentSpooferTabDataMap = tabDataMap;
        Map<String, FakeTagSnapshot> fakeTags;
        try {
            fakeTags = prepareFakeTagSnapshotsWithTabData(fakeNames, tabDataMap);
        } finally {
            currentSpooferTabDataMap = null;
        }
        if (fakeTags.isEmpty()) {
            return;
        }
        int version = fakeTagVersion;
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer == null || !viewer.isOnline()) {
                continue;
            }
            flushFakePlayerTabDisplayNames(viewer, fakeTags);
            lastFakeTagVersions.put(viewer.getUniqueId(), version);
            lastFakeTabDisplayVersions.put(viewer.getUniqueId(), version);
        }
    }

    private void scheduleViewerFakeNameTagBatch(Player viewer, Map<String, FakeTagSnapshot> fakeTags) {
        if (viewer == null || !viewer.isOnline() || fakeTags == null || fakeTags.isEmpty()) {
            return;
        }
        UUID viewerId = viewer.getUniqueId();
        lastFakeTagVersions.remove(viewerId);
        lastFakeTabDisplayVersions.remove(viewerId);

        if (viewerOnlyFakeTagTask != null
            && viewerOnlyFakeTagViewer != null
            && viewerOnlyFakeTagViewer.getUniqueId().equals(viewerId)) {
            viewerOnlyFakeTagTags = fakeTags;
            viewerOnlyFakeTagBotNames = fakeTags.keySet().toArray(String[]::new);
            viewerOnlyFakeTagBotIndex = 0;
            return;
        }

        cancelViewerOnlyFakeTagBatch();
        viewerOnlyFakeTagViewer = viewer;
        viewerOnlyFakeTagTags = fakeTags;
        viewerOnlyFakeTagBotNames = fakeTags.keySet().toArray(String[]::new);
        viewerOnlyFakeTagBotIndex = 0;
        viewerOnlyFakeTagTask = new BukkitRunnable() {
            @Override
            public void run() {
                tickViewerOnlyFakeNameTags();
            }
        }.runTaskTimer(plugin, 0L, NAMETAG_PUMP_PERIOD_TICKS);
    }

    private void tickViewerOnlyFakeNameTags() {
        Player viewer = viewerOnlyFakeTagViewer;
        Map<String, FakeTagSnapshot> fakeTags = viewerOnlyFakeTagTags;
        String[] botNames = viewerOnlyFakeTagBotNames;
        if (!enabled || viewer == null || !viewer.isOnline() || areSpooferBotsHidden(viewer)) {
            cancelViewerOnlyFakeTagBatch();
            return;
        }
        if (fakeTags == null || botNames == null || botNames.length == 0) {
            cancelViewerOnlyFakeTagBatch();
            return;
        }

        UUID viewerId = viewer.getUniqueId();
        int botCount = botNames.length;
        int botsPerTick = fakeNametagBotsPerTick(botCount);
        Map<String, String> viewerTeams = lastFakeNameTagTeams.computeIfAbsent(viewerId, key -> new ConcurrentHashMap<>());
        Map<String, String> viewerCache = lastFakeNameTagSuffixes.computeIfAbsent(viewerId, key -> new ConcurrentHashMap<>());

        if (viewerOnlyFakeTagBotIndex == 0) {
            cleanupRemovedFakeBots(viewer, fakeTags);
        }

        int end = Math.min(viewerOnlyFakeTagBotIndex + botsPerTick, botNames.length);
        for (int index = viewerOnlyFakeTagBotIndex; index < end; index++) {
            String fakeName = botNames[index];
            FakeTagSnapshot tag = fakeNameTagViewerDependent
                ? buildFakeTagSnapshotForViewer(viewer, fakeName)
                : fakeTags.get(fakeName);
            if (tag == null) {
                continue;
            }
            if (shouldSkipFakeTeamUpdate(viewerTeams, viewerCache, fakeName, tag)) {
                continue;
            }
            updateFakeNameTagForViewer(viewer, fakeName, tag, viewerTeams, viewerCache);
        }
        viewerOnlyFakeTagBotIndex = end;

        if (viewerOnlyFakeTagBotIndex < botNames.length) {
            return;
        }

        flushFakePlayerTabDisplayNames(viewer, fakeTags);
        lastFakeTagVersions.put(viewerId, fakeTagVersion);
        lastFakeTabDisplayVersions.put(viewerId, fakeTagVersion);
        cancelViewerOnlyFakeTagBatch();
    }

    private void cancelViewerOnlyFakeTagBatch() {
        if (viewerOnlyFakeTagTask != null) {
            viewerOnlyFakeTagTask.cancel();
            viewerOnlyFakeTagTask = null;
        }
        viewerOnlyFakeTagViewer = null;
        viewerOnlyFakeTagBotNames = null;
        viewerOnlyFakeTagTags = null;
        viewerOnlyFakeTagBotIndex = 0;
    }

    private void scheduleBatchedFakeNameTagRefresh() {
        if (!enabled || !plugin.isEnabled()) {
            return;
        }
        if (fakeNametagBatchTask != null || fakeSnapshotPrepareTask != null) {
            fakeNametagRefreshPending = true;
            return;
        }
        fakeNametagRefreshPending = false;
        java.util.Collection<String> fakeNames = getSpooferFakeNames();
        Player[] viewers = Bukkit.getOnlinePlayers().toArray(new Player[0]);
        if (viewers.length == 0) {
            return;
        }
        Map<String, Object> tabDataMap = loadSpooferTabDataMap();
        if (fakeNames.isEmpty()) {
            startFakeNametagBatch(viewers, Map.of());
            return;
        }
        if (fakeSnapshotSafeForAsync()) {
            List<String> namesCopy = new ArrayList<>(fakeNames);
            Map<String, Object> tabDataCopy = tabDataMap != null ? new HashMap<>(tabDataMap) : Map.of();
            fakeSnapshotPrepareTask = Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                Map<String, FakeTagSnapshot> built = buildFakeTagSnapshotsAsync(namesCopy, tabDataCopy);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    fakeSnapshotPrepareTask = null;
                    if (!enabled || !plugin.isEnabled()) {
                        return;
                    }
                    boolean pending = fakeNametagRefreshPending;
                    fakeNametagRefreshPending = false;
                    Player[] freshViewers = Bukkit.getOnlinePlayers().toArray(new Player[0]);
                    if (freshViewers.length == 0) {
                        if (pending) {
                            scheduleBatchedFakeNameTagRefresh();
                        }
                        return;
                    }
                    startFakeNametagBatch(freshViewers, built);
                    if (pending) {
                        scheduleBatchedFakeNameTagRefresh();
                    }
                });
            });
            return;
        }
        currentSpooferTabDataMap = tabDataMap;
        try {
            startFakeNametagBatch(viewers, prepareFakeTagSnapshotsWithTabData(fakeNames, tabDataMap));
        } finally {
            currentSpooferTabDataMap = null;
        }
    }

    private void startFakeNametagBatch(Player[] viewers, Map<String, FakeTagSnapshot> tags) {
        fakeNametagViewerCache = viewers;
        fakeNametagViewerIndex = 0;
        fakeNametagBotIndex = 0;
        fakeNametagBatchTags = tags != null ? tags : Map.of();
        fakeNametagBotNames = fakeNametagBatchTags.isEmpty()
            ? null
            : fakeNametagBatchTags.keySet().toArray(String[]::new);
        fakeNametagBatchTask = new BukkitRunnable() {
            @Override
            public void run() {
                tickBatchedFakeNameTags();
            }
        }.runTaskTimer(plugin, 0L, NAMETAG_PUMP_PERIOD_TICKS);
    }

    /** HollySpoofer robi nametagi botów przez Bukkit scoreboard (ht…), nie pakiety h…. */
    private boolean spooferUsesBukkitNametags() {
        org.bukkit.plugin.Plugin spoofer = Bukkit.getPluginManager().getPlugin("HollySpoofer");
        if (spoofer == null) {
            return false;
        }
        return spoofer.getConfig().getBoolean("tab.hollycore-teams", true)
            && spoofer.getConfig().getBoolean("tab.use-scoreboard-team", true);
    }

    private boolean fakeSnapshotSafeForAsync() {
        if (fakeNameTagViewerDependent || fakeTabDisplayViewerDependent) {
            return false;
        }
        String combined = (nameTagFormat == null ? "" : nameTagFormat)
            + (playerNameFormat == null ? "" : playerNameFormat);
        if (combined.contains("%rel_")) {
            return false;
        }
        return !papiEnabled || !hasPlaceholder(combined);
    }

    private Map<String, FakeTagSnapshot> buildFakeTagSnapshotsAsync(List<String> fakeNames, Map<String, Object> tabDataMap) {
        currentSpooferTabDataMap = tabDataMap;
        try {
            return prepareFakeTagSnapshotsWithTabData(fakeNames, tabDataMap);
        } finally {
            currentSpooferTabDataMap = null;
        }
    }

    private void cancelFakeNametagBatch() {
        cancelViewerOnlyFakeTagBatch();
        if (fakeSnapshotPrepareTask != null) {
            fakeSnapshotPrepareTask.cancel();
            fakeSnapshotPrepareTask = null;
        }
        if (fakeNametagBatchTask != null) {
            fakeNametagBatchTask.cancel();
            fakeNametagBatchTask = null;
        }
        fakeNametagViewerCache = null;
        fakeNametagViewerIndex = 0;
        fakeNametagBotIndex = 0;
        fakeNametagBotNames = null;
        fakeNametagBatchTags = null;
        fakeNametagRefreshPending = false;
    }

    private void tickBatchedFakeNameTags() {
        if (!enabled) {
            cancelFakeNametagBatch();
            return;
        }
        Player[] viewers = fakeNametagViewerCache;
        Map<String, FakeTagSnapshot> fakeTags = fakeNametagBatchTags;
        String[] botNames = fakeNametagBotNames;
        if (viewers == null || viewers.length == 0 || fakeTags == null) {
            cancelFakeNametagBatch();
            return;
        }

        int version = fakeTagVersion;
        int botCount = botNames != null ? botNames.length : 0;
        int botsPerTick = fakeNametagBotsPerTick(botCount);

        while (fakeNametagViewerIndex < viewers.length) {
            Player viewer = viewers[fakeNametagViewerIndex];
            if (viewer == null || !viewer.isOnline() || areSpooferBotsHidden(viewer)) {
                fakeNametagViewerIndex++;
                fakeNametagBotIndex = 0;
                continue;
            }

            UUID viewerId = viewer.getUniqueId();
            if (!fakeNameTagViewerDependent
                && Objects.equals(lastFakeTagVersions.get(viewerId), version)
                && Objects.equals(lastFakeTabDisplayVersions.get(viewerId), version)) {
                fakeNametagViewerIndex++;
                fakeNametagBotIndex = 0;
                continue;
            }

            if (fakeNametagBotIndex == 0) {
                cleanupRemovedFakeBots(viewer, fakeTags);
            }

            if (botNames == null || botNames.length == 0) {
                lastFakeTagVersions.put(viewerId, version);
                lastFakeTabDisplayVersions.put(viewerId, version);
                fakeNametagViewerIndex++;
                fakeNametagBotIndex = 0;
                continue;
            }

            Map<String, String> viewerTeams = lastFakeNameTagTeams.computeIfAbsent(viewerId, key -> new ConcurrentHashMap<>());
            Map<String, String> viewerCache = lastFakeNameTagSuffixes.computeIfAbsent(viewerId, key -> new ConcurrentHashMap<>());
            int end = Math.min(fakeNametagBotIndex + botsPerTick, botNames.length);
            for (int index = fakeNametagBotIndex; index < end; index++) {
                String fakeName = botNames[index];
                FakeTagSnapshot tag = fakeNameTagViewerDependent
                    ? buildFakeTagSnapshotForViewer(viewer, fakeName)
                    : fakeTags.get(fakeName);
                if (tag == null) {
                    continue;
                }
                if (shouldSkipFakeTeamUpdate(viewerTeams, viewerCache, fakeName, tag)) {
                    continue;
                }
                updateFakeNameTagForViewer(viewer, fakeName, tag, viewerTeams, viewerCache);
            }
            fakeNametagBotIndex = end;

            if (fakeNametagBotIndex < botNames.length) {
                return;
            }

            flushFakePlayerTabDisplayNames(viewer, fakeTags);
            lastFakeTagVersions.put(viewerId, version);
            lastFakeTabDisplayVersions.put(viewerId, version);
            fakeNametagBotIndex = 0;
            fakeNametagViewerIndex++;
            return;
        }

        boolean pending = fakeNametagRefreshPending;
        cancelFakeNametagBatch();
        if (pending && enabled) {
            scheduleBatchedFakeNameTagRefresh();
        } else if (enabled && !customTabEnabled) {
            scheduleTabOrderRefresh();
        }
    }

    /**
     * HollySpoofer /spf loadtest true — symuluje koszt tablisty i nametagow jak przy prawdziwych graczach.
     */
    public void runSpooferLoadTestPass(int botCount) {
        if (!enabled || botCount <= 0 || !plugin.isEnabled()) {
            return;
        }

        java.util.Collection<String> fakeNames = getSpooferFakeNames();
        if (fakeNames.isEmpty()) {
            return;
        }

        // Jedna przebiezka — jak realny refresh nametagow, bez 32x stormu.
        invalidateFakeTagSnapshots();
        preparedFakeNamesSignature = 0L;
        scheduleBatchedFakeNameTagRefresh();

        markAllHeaderFootersDirty();
        for (Player player : Bukkit.getOnlinePlayers()) {
            dirtyIdentities.add(player.getUniqueId());
            if (customTabEnabled) {
                dirtyPlayers.add(player.getUniqueId());
            }
        }
        queueUpdateAll();
    }

    private void updateFakeNameTagsOnly() {
        Map<String, FakeTagSnapshot> fakeTags = prepareFakeTagSnapshots(getSpooferFakeNames());
        int version = fakeTagVersion;
        boolean skipUnchanged = !fakeNameTagViewerDependent;
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            UUID viewerId = viewer.getUniqueId();
            if (skipUnchanged) {
                Integer applied = lastFakeTagVersions.get(viewerId);
                if (applied != null && applied == version) {
                    continue;
                }
            }
            updateFakeNameTagsForViewer(viewer, fakeTags);
            lastFakeTagVersions.put(viewerId, version);
        }
    }

    private void updateNameTagForViewer(Player viewer, Player target) {
        if (viewer == null || target == null) {
            return;
        }

        RealTagSnapshot tag = realTagSnapshot(viewer, target);
        UUID targetId = target.getUniqueId();
        String expectedTeamName = tag.teamName();

        Map<UUID, String> viewerTeamMap = lastNameTagTeams.computeIfAbsent(viewer.getUniqueId(), key -> new ConcurrentHashMap<>());
        Map<UUID, String> viewerCache = lastNameTagSuffixes.computeIfAbsent(viewer.getUniqueId(), key -> new ConcurrentHashMap<>());
        String previousTeamName = viewerTeamMap.get(targetId);

        if (expectedTeamName.equals(previousTeamName) && tag.identity().equals(viewerCache.get(targetId))) {
            return;
        }

        // Ten sam team, inny entry (incognito nick ↔ real nick): usuń TYLKO stare entry z cache.
        // REMOVE z target.getName() gdy na teamie jest fake nick = Network Protocol Error (kick klienta).
        if (previousTeamName != null && previousTeamName.equals(expectedTeamName)) {
            String[] oldEntries = entriesFromNameTagIdentity(viewerCache.get(targetId));
            if (oldEntries != null) {
                java.util.Set<String> keep = new java.util.HashSet<>();
                if (tag.entries() != null) {
                    java.util.Collections.addAll(keep, tag.entries());
                }
                for (String old : oldEntries) {
                    if (old != null && !old.isBlank() && !keep.contains(old)) {
                        PacketNameTagSender.removeEntities(viewer, previousTeamName, old);
                    }
                }
            }
        }

        PacketNameTagSender.apply(
            viewer,
            expectedTeamName,
            previousTeamName,
            tag.prefix(),
            tag.suffix(),
            tag.color(),
            tag.entries()
        );
        viewerTeamMap.put(targetId, expectedTeamName);
        viewerCache.put(targetId, tag.identity());
    }

    /**
     * Nametag gracza jest ten sam dla każdego widza (o ile format nie ma %rel_),
     * więc gotowe komponenty i nazwę teamu liczymy raz na gracza, nie raz na parę.
     */
    private RealTagSnapshot realTagSnapshot(Player viewer, Player target) {
        UUID targetId = target.getUniqueId();
        IncognitoManager incognitoLookup = plugin.getIncognitoManager();
        boolean hiddenTarget = incognitoLookup != null && incognitoLookup.isIncognito(target);
        if (!nametagUsesRelational && !hiddenTarget) {
            RealTagSnapshot cached = realTagSnapshots.get(targetId);
            if (cached != null && (System.currentTimeMillis() - cached.createdAt() < NAMETAG_CACHE_TTL_MS)) {
                return cached;
            }
        }

        PrefixSuffix nameTag = resolveNameTag(viewer, target);
        String prefix = nameTag.prefix();
        String suffix = nameTag.suffix() + wardrobeNameTagSuffix(target, hiddenTarget);
        if (prefix.length() > 256) {
            prefix = prefix.substring(0, 256);
        }
        if (suffix.length() > 256) {
            suffix = suffix.substring(0, 256);
        }
        IncognitoManager incognito = plugin.getIncognitoManager();
        boolean hidden = incognito != null && incognito.isIncognito(target);
        String realEntry = target.getName();
        String fakeEntry = hidden ? incognito.displayName(target) : null;
        String[] entries = hidden && fakeEntry != null && !fakeEntry.isBlank()
            ? new String[]{fakeEntry}
            : new String[]{realEntry};
        RealTagSnapshot snapshot = new RealTagSnapshot(
            getNameTagTeamName(target),
            entries,
            prefix + "\n" + suffix + "\n" + String.join(",", entries) + "\nnever",
            ColorUtils.colorizeComponent(prefix),
            ColorUtils.colorizeComponent(suffix),
            plugin.getMasakraCustomItemsManager() != null && plugin.getMasakraCustomItemsManager().isMarked(target)
                ? net.kyori.adventure.text.format.NamedTextColor.RED
                : ColorUtils.parseToNamedTextColor(hidden ? "&f" : getRankNameColor(target))
        );
        if (!nametagUsesRelational && !hidden && shouldCacheRealTagSnapshot(target)) {
            realTagSnapshots.put(targetId, snapshot);
        }
        return snapshot;
    }

    private String wardrobeNameTagSuffix(Player target, boolean hidden) {
        var szafka = plugin.getMasakraSzafkaManager();
        return szafka == null || hidden ? "" : szafka.nameTagSuffix(target);
    }

    public void refreshNameTag(Player target) {
        if (!enabled || target == null || !target.isOnline()) {
            return;
        }
        UUID uuid = target.getUniqueId();
        nametagCacheByTarget.remove(uuid);
        realTagSnapshots.remove(uuid);
        updateNameTagsForTarget(target);
    }

    private record RealTagSnapshot(
        String teamName,
        String[] entries,
        String identity,
        net.kyori.adventure.text.Component prefix,
        net.kyori.adventure.text.Component suffix,
        net.kyori.adventure.text.format.NamedTextColor color,
        long createdAt
    ) {
        public RealTagSnapshot(
            String teamName,
            String[] entries,
            String identity,
            net.kyori.adventure.text.Component prefix,
            net.kyori.adventure.text.Component suffix,
            net.kyori.adventure.text.format.NamedTextColor color
        ) {
            this(teamName, entries, identity, prefix, suffix, color, System.currentTimeMillis());
        }
    }

    /** identity = prefix + \\n + suffix + \\n + entriesCsv + \\nnever */
    private static String[] entriesFromNameTagIdentity(String identity) {
        if (identity == null || identity.isBlank()) {
            return null;
        }
        String[] parts = identity.split("\n", -1);
        if (parts.length < 3) {
            return null;
        }
        String raw = parts[2];
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.split(",");
    }

    private java.util.Collection<String> getSpooferFakeNames() {
        Object api = getSpooferApi();
        if (api == null || spooferGetFakePlayersMethod == null) {
            return java.util.List.of();
        }
        try {
            Object result = spooferGetFakePlayersMethod.invoke(api);
            if (!(result instanceof java.util.Collection<?> raw)) {
                return java.util.List.of();
            }
            java.util.List<String> names = new java.util.ArrayList<>(raw.size());
            for (Object entry : raw) {
                if (entry instanceof String name && !name.isBlank()) {
                    names.add(name);
                }
            }
            return names;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return java.util.List.of();
        }
    }

    private boolean isKnownOrSpooferBot(UUID uuid, WrapperPlayServerPlayerInfoUpdate.PlayerInfo entry) {
        if (uuid == null) {
            return false;
        }
        if (knownFakeBotUuids.contains(uuid)) {
            return true;
        }
        String name = null;
        try {
            if (entry != null && entry.getGameProfile() != null) {
                name = entry.getGameProfile().getName();
            }
        } catch (RuntimeException | LinkageError ignored) {
        }
        if (name == null || name.isBlank()) {
            return false;
        }
        // Nasze sloty custom TAB nigdy nie sa botami — pomijamy kosztowny skan listy Spoofera.
        if (name.startsWith(PROFILE_NAME_PREFIX)) {
            return false;
        }
        if (!SpooferBridge.isFakeBot(name)) {
            return false;
        }
        knownFakeBotUuids.add(uuid);
        Integer order = lastPlayerListOrders.get(uuid);
        if (order == null) {
            int weight = SpooferBridge.getFakeBotTabPriority(name, getFakeTabPriority());
            lastPlayerListOrders.put(uuid, toClientListOrder(weight));
        }
        return true;
    }

    private net.kyori.adventure.text.Component resolveFakeBotTabDisplay(
        Player viewer,
        UUID uuid,
        WrapperPlayServerPlayerInfoUpdate.PlayerInfo entry
    ) {
        Map<String, FakeTagSnapshot> prepared = preparedFakeTags;
        if (prepared != null && !prepared.isEmpty()) {
            for (FakeTagSnapshot tag : prepared.values()) {
                if (tag != null && uuid.equals(tag.profileUuid()) && tag.tabDisplayComponent() != null) {
                    return tag.tabDisplayComponent();
                }
            }
        }
        String name = null;
        try {
            if (entry != null && entry.getGameProfile() != null) {
                name = entry.getGameProfile().getName();
            }
        } catch (RuntimeException | LinkageError ignored) {
        }
        if (name == null || name.isBlank()) {
            return null;
        }
        SpoofedPlayerProfile profile = buildSpoofedProfile(name, needsHollyPlaceholder(playerNameFormat));
        if (profile == null) {
            return null;
        }
        return ColorUtils.colorizeComponent(formatFakePlayerTabName(viewer, profile));
    }

    private void updateFakeNameTagsForViewer(Player viewer, Map<String, FakeTagSnapshot> fakeTags) {
        if (viewer == null) {
            return;
        }
        cleanupRemovedFakeBots(viewer, fakeTags);
        if (fakeTags.isEmpty()) {
            return;
        }
        Map<String, String> viewerTeams = lastFakeNameTagTeams.computeIfAbsent(viewer.getUniqueId(), key -> new ConcurrentHashMap<>());
        Map<String, String> viewerCache = lastFakeNameTagSuffixes.computeIfAbsent(viewer.getUniqueId(), key -> new ConcurrentHashMap<>());
        for (String fakeName : fakeTags.keySet()) {
            FakeTagSnapshot tag = fakeNameTagViewerDependent
                ? buildFakeTagSnapshotForViewer(viewer, fakeName)
                : fakeTags.get(fakeName);
            if (tag == null) {
                continue;
            }
            if (shouldSkipFakeTeamUpdate(viewerTeams, viewerCache, fakeName, tag)) {
                continue;
            }
            updateFakeNameTagForViewer(viewer, fakeName, tag, viewerTeams, viewerCache);
        }
        flushFakePlayerTabDisplayNames(viewer, fakeTags);
        UUID viewerId = viewer.getUniqueId();
        lastFakeTagVersions.put(viewerId, fakeTagVersion);
        lastFakeTabDisplayVersions.put(viewerId, fakeTagVersion);
    }

    private void cleanupRemovedFakeBots(Player viewer, Map<String, FakeTagSnapshot> fakeTags) {
        UUID viewerId = viewer.getUniqueId();
        Map<String, String> displayCache = lastFakeTabDisplayNames.get(viewerId);
        if (displayCache != null && !displayCache.isEmpty()) {
            displayCache.keySet().removeIf(tracked -> !fakeTags.containsKey(tracked));
        }

        Map<String, String> viewerTeams = lastFakeNameTagTeams.get(viewerId);
        if (viewerTeams == null || viewerTeams.isEmpty()) {
            return;
        }
        viewerTeams.keySet().removeIf(tracked -> {
            if (fakeTags.containsKey(tracked)) {
                return false;
            }
            String oldTeam = viewerTeams.get(tracked);
            if (oldTeam != null) {
                PacketNameTagSender.remove(viewer, oldTeam);
            }
            Map<String, String> suffixCache = lastFakeNameTagSuffixes.get(viewerId);
            if (suffixCache != null) {
                suffixCache.remove(tracked);
            }
            return true;
        });
    }

    private FakeTagSnapshot buildFakeTagSnapshotForViewer(Player viewer, String fakeName) {
        // buildSpoofedProfile i tak sam podnosi flagę, gdy EQ jest potrzebne — nie płać 5 reflect calli na darmo.
        SpoofedPlayerProfile profile = buildSpoofedProfile(fakeName, fakeProfileNeedsEquipment());
        if (profile == null) {
            return null;
        }
        PrefixSuffix nameTag = resolveFakeNameTag(viewer, profile);
        String prefix = nameTag.prefix();
        String suffix = nameTag.suffix();
        if (prefix.length() > 256) {
            prefix = prefix.substring(0, 256);
        }
        if (suffix.length() > 256) {
            suffix = suffix.substring(0, 256);
        }
        String teamName = buildFakeNameTagTeamName(fakeName, profile.tabPriority());
        String nameColor = profile.nameColor() != null ? profile.nameColor() : getFakeRankNameColor();
        return new FakeTagSnapshot(
            teamName,
            prefix + "\n" + suffix + "\nnever",
            ColorUtils.colorizeComponent(prefix),
            ColorUtils.colorizeComponent(suffix),
            ColorUtils.parseToNamedTextColor(nameColor),
            null,
            null,
            null,
            null
        );
    }

    private void flushFakePlayerTabDisplayNames(Player viewer, Map<String, FakeTagSnapshot> fakeTags) {
        if (viewer == null || !viewer.isOnline() || fakeTags.isEmpty() || !isPacketEventsReady()) {
            return;
        }
        if (areSpooferBotsHidden(viewer)) {
            return;
        }
        // Custom tab HollyCore = sloty; boty na TAB = leak (po czasie same boty).
        if (customTabEnabled) {
            unlinkFakeBotsFromTab(viewer, fakeTags);
            return;
        }
        if (!fakeTabDisplayViewerDependent) {
            Integer applied = lastFakeTabDisplayVersions.get(viewer.getUniqueId());
            if (applied != null && applied == fakeTagVersion) {
                return;
            }
        }

        Map<String, String> cache = lastFakeTabDisplayNames.computeIfAbsent(viewer.getUniqueId(), key -> new ConcurrentHashMap<>());
        List<WrapperPlayServerPlayerInfoUpdate.PlayerInfo> updates = new ArrayList<>(Math.min(fakeTags.size(), 64));
        boolean needEquipment = needsHollyPlaceholder(playerNameFormat);

        for (Map.Entry<String, FakeTagSnapshot> entry : fakeTags.entrySet()) {
            String fakeName = entry.getKey();
            FakeTagSnapshot tag = entry.getValue();
            String formatted;
            net.kyori.adventure.text.Component component;
            java.util.UUID profileUuid;
            String profileName;
            int listOrder = toClientListOrder(getFakeTabPriority());

            if (fakeTabDisplayViewerDependent) {
                SpoofedPlayerProfile profile = buildSpoofedProfile(fakeName, needEquipment);
                if (profile == null) {
                    continue;
                }
                formatted = formatFakePlayerTabName(viewer, profile);
                component = ColorUtils.colorizeComponent(formatted);
                profileUuid = profile.uuid();
                profileName = profile.name();
                listOrder = toClientListOrder(profile.tabPriority());
            } else {
                if (tag.tabDisplayRaw() == null || tag.profileUuid() == null || tag.profileName() == null) {
                    continue;
                }
                formatted = tag.tabDisplayRaw();
                component = tag.tabDisplayComponent();
                profileUuid = tag.profileUuid();
                profileName = tag.profileName();
                Integer cachedOrder = lastPlayerListOrders.get(profileUuid);
                if (cachedOrder != null) {
                    listOrder = cachedOrder;
                }
            }

            if (formatted.equals(cache.get(fakeName))) {
                continue;
            }
            cache.put(fakeName, formatted);
            lastPlayerListOrders.put(profileUuid, listOrder);
            try {
                WrapperPlayServerPlayerInfoUpdate.PlayerInfo info = new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
                    new UserProfile(profileUuid, profileName),
                    true,
                    0,
                    GameMode.SURVIVAL,
                    component,
                    null
                );
                info.setListOrder(listOrder);
                updates.add(info);
            } catch (RuntimeException | LinkageError ignored) {
            }
        }

        if (updates.isEmpty()) {
            if (!fakeTabDisplayViewerDependent) {
                lastFakeTabDisplayVersions.put(viewer.getUniqueId(), fakeTagVersion);
            }
            return;
        }

        try {
            for (int index = 0; index < updates.size(); index += FAKE_TAB_DISPLAY_PACKET_CHUNK) {
                int end = Math.min(index + FAKE_TAB_DISPLAY_PACKET_CHUNK, updates.size());
                sendPlayerInfoUpdate(
                    viewer,
                    UPDATE_FAKE_TAB_ACTIONS,
                    UPDATE_FAKE_TAB_ACTIONS_LEGACY,
                    updates.subList(index, end)
                );
            }
        } catch (RuntimeException | LinkageError ignored) {
        }
        if (!fakeTabDisplayViewerDependent) {
            lastFakeTabDisplayVersions.put(viewer.getUniqueId(), fakeTagVersion);
        }
    }

    /** Custom tab: wymuś listed=false na botach (HollyCore wcześniej ustawiał true i zalewał TAB). */
    private void unlinkFakeBotsFromTab(Player viewer, Map<String, FakeTagSnapshot> fakeTags) {
        if (viewer == null || fakeTags == null || fakeTags.isEmpty() || !isPacketEventsReady()) {
            return;
        }
        List<WrapperPlayServerPlayerInfoUpdate.PlayerInfo> updates = new ArrayList<>();
        for (Map.Entry<String, FakeTagSnapshot> entry : fakeTags.entrySet()) {
            String fakeName = entry.getKey();
            FakeTagSnapshot tag = entry.getValue();
            java.util.UUID uuid = tag != null ? tag.profileUuid() : null;
            String name = tag != null ? tag.profileName() : null;
            if (uuid == null) {
                uuid = SpooferBridge.getFakeBotUuid(fakeName);
            }
            if (uuid == null) {
                continue;
            }
            knownFakeBotUuids.add(uuid);
            if (name == null || name.isBlank()) {
                name = fakeName;
            }
            try {
                updates.add(new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
                    new UserProfile(uuid, name),
                    false,
                    0,
                    GameMode.SURVIVAL,
                    null,
                    null
                ));
            } catch (RuntimeException | LinkageError ignored) {
            }
        }
        if (updates.isEmpty()) {
            return;
        }
        try {
            for (int index = 0; index < updates.size(); index += FAKE_TAB_DISPLAY_PACKET_CHUNK) {
                int end = Math.min(index + FAKE_TAB_DISPLAY_PACKET_CHUNK, updates.size());
                sendPacket(viewer, new WrapperPlayServerPlayerInfoUpdate(UPDATE_LISTED_ACTIONS, updates.subList(index, end)));
            }
        } catch (RuntimeException | LinkageError ignored) {
        }
    }

    private String formatFakePlayerTabName(Player viewer, SpoofedPlayerProfile profile) {
        String format = playerNameFormat;
        if (format == null || format.isBlank()) {
            format = "%luckperms_prefix%%name_color%%player_name%";
        }
        String nameColor = profile.nameColor() != null ? profile.nameColor() : getFakeRankNameColor();
        String template = format
            .replace("%name_color%", nameColor)
            .replace("%rank_name_color%", nameColor);
        return parseIdentityPlaceholders(viewer, null, profile, template);
    }

    private static boolean shouldSkipFakeTeamUpdate(
        Map<String, String> viewerTeams,
        Map<String, String> viewerCache,
        String fakeName,
        FakeTagSnapshot tag
    ) {
        if (viewerTeams == null || viewerCache == null || tag == null) {
            return false;
        }
        return tag.teamName().equals(viewerTeams.get(fakeName))
            && tag.identity().equals(viewerCache.get(fakeName));
    }

    private void updateFakeNameTagForViewer(
        Player viewer,
        String fakeName,
        FakeTagSnapshot tag,
        Map<String, String> viewerTeamMap,
        Map<String, String> viewerCache
    ) {
        String expectedTeamName = tag.teamName();
        String previousTeamName = viewerTeamMap.get(fakeName);
        if (expectedTeamName.equals(previousTeamName) && tag.identity().equals(viewerCache.get(fakeName))) {
            return;
        }
        SpooferBridge.clearHtTeamForEntry(viewer, fakeName);
        PacketNameTagSender.apply(
            viewer,
            expectedTeamName,
            previousTeamName,
            tag.prefix(),
            tag.suffix(),
            tag.color(),
            fakeName
        );
        viewerTeamMap.put(fakeName, expectedTeamName);
        viewerCache.put(fakeName, tag.identity());
    }

    /**
     * Wygląd nametagu bota nie zależy od widza (o ile format nie ma %rel_/%bungee_/%goxy_),
     * więc reflection do API Spoofera, LuckPerms i parsowanie placeholderów robimy raz na bota,
     * a nie raz na bota razy widza.
     */
    private Map<String, FakeTagSnapshot> prepareFakeTagSnapshots(java.util.Collection<String> fakeNames) {
        Map<String, Object> tabDataMap = loadSpooferTabDataMap();
        currentSpooferTabDataMap = tabDataMap;
        try {
            return prepareFakeTagSnapshotsWithTabData(fakeNames, tabDataMap);
        } finally {
            currentSpooferTabDataMap = null;
        }
    }

    private Map<String, FakeTagSnapshot> prepareFakeTagSnapshotsWithTabData(
        java.util.Collection<String> fakeNames,
        Map<String, Object> tabDataMap
    ) {
        if (fakeNames.isEmpty()) {
            if (!fakeTagSnapshots.isEmpty()) {
                fakeTagSnapshots.clear();
                preparedFakeTags = Map.of();
                preparedFakeNamesSignature = 0L;
                fakeTagVersion++;
            }
            return Map.of();
        }

        long signature = 1L;
        for (String fakeName : fakeNames) {
            signature = signature * 31L + (fakeName == null ? 0 : fakeName.hashCode());
        }
        signature = signature * 31L + fakeNames.size();
        if (!fakeNameTagViewerDependent && signature == preparedFakeNamesSignature) {
            return preparedFakeTags;
        }

        boolean changed = false;
        Map<String, FakeTagSnapshot> active = new java.util.LinkedHashMap<>(Math.max(16, fakeNames.size() * 2));
        for (String fakeName : fakeNames) {
            if (fakeName == null || fakeName.isBlank() || active.containsKey(fakeName)) {
                continue;
            }
            FakeTagSnapshot snapshot = fakeNameTagViewerDependent ? null : fakeTagSnapshots.get(fakeName);
            if (snapshot == null) {
                snapshot = buildFakeTagSnapshot(fakeName);
                if (snapshot == null) {
                    continue;
                }
                fakeTagSnapshots.put(fakeName, snapshot);
                changed = true;
            }
            active.put(fakeName, snapshot);
        }
        if (fakeTagSnapshots.keySet().retainAll(active.keySet())) {
            changed = true;
        }
        if (changed) {
            fakeTagVersion++;
        }
        preparedFakeTags = java.util.Collections.unmodifiableMap(active);
        preparedFakeNamesSignature = signature;
        return preparedFakeTags;
    }

    private FakeTagSnapshot buildFakeTagSnapshot(String fakeName) {
        // jw. — bez armorstars w formacie EQ botów nie jest czytane, a kosztuje 5 reflect calli na bota.
        SpoofedPlayerProfile profile = buildSpoofedProfile(fakeName, fakeProfileNeedsEquipment());
        if (profile == null) {
            return null;
        }
        PrefixSuffix nameTag = resolveFakeNameTag(null, profile);
        String prefix = nameTag.prefix();
        String suffix = nameTag.suffix();
        if (prefix.length() > 256) {
            prefix = prefix.substring(0, 256);
        }
        if (suffix.length() > 256) {
            suffix = suffix.substring(0, 256);
        }
        String teamName = buildFakeNameTagTeamName(fakeName, profile.tabPriority());
        String nameColor = profile.nameColor() != null ? profile.nameColor() : getFakeRankNameColor();
        String tabDisplayRaw = null;
        net.kyori.adventure.text.Component tabDisplayComponent = null;
        if (!fakeTabDisplayViewerDependent) {
            tabDisplayRaw = formatFakePlayerTabName(null, profile);
            tabDisplayComponent = ColorUtils.colorizeComponent(tabDisplayRaw);
        }
        // Nowy bot: najpierw dół TAB, potem applyLpc ustawi właściwą rangę.
        seedBottomListOrder(profile.uuid());
        knownFakeBotUuids.add(profile.uuid());
        return new FakeTagSnapshot(
            teamName,
            prefix + "\n" + suffix + "\nnever",
            ColorUtils.colorizeComponent(prefix),
            ColorUtils.colorizeComponent(suffix),
            ColorUtils.parseToNamedTextColor(nameColor),
            profile.uuid(),
            profile.name(),
            tabDisplayRaw,
            tabDisplayComponent
        );
    }

    private boolean fakeProfileNeedsEquipment() {
        if (needsHollyPlaceholder(nameTagFormat) || needsHollyPlaceholder(playerNameFormat)) {
            return true;
        }
        return nametagNeedsFakeEquipment();
    }

    private static boolean needsHollyPlaceholder(String format) {
        if (format == null || format.isBlank()) {
            return false;
        }
        String lower = format.toLowerCase(java.util.Locale.ROOT);
        // "stars" łapie też aliasy %core_stars% / %service_stars% przepisywane dopiero przy renderze.
        return lower.contains("%holly_") || lower.contains("stars");
    }

    private boolean nametagNeedsFakeEquipment() {
        String format = nameTagFormat;
        if (format == null || format.isBlank()) {
            return false;
        }
        String lower = format.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("armorstars")
            || lower.contains("armor_stars")
            || lower.contains("%holly_stars")
            || lower.contains("eqstars");
    }

    private void softRefreshFakeTagSnapshots() {
        // Czyść snapshoty — inaczej bot zostaje bez armorstars / starego formatu po doładowaniu EQ.
        fakeTagSnapshots.clear();
        preparedFakeTags = Map.of();
        preparedFakeNamesSignature = 0L;
        // Bez tego shouldSkipFakeTeamUpdate zostawia stare puste prefixy.
        lastFakeNameTagTeams.clear();
        lastFakeNameTagSuffixes.clear();
    }

    /**
     * Clan-tag rel na botach nie wymaga per-viewer (brak klanu).
     * Reszta placeholdery relacyjnych / proxy — tak.
     */
    private static boolean isFakeFormatViewerDependent(String format) {
        if (format == null || format.isBlank()) {
            return false;
        }
        String stripped = format
            .replace("%rel_holly_clan_tag_rel%", "")
            .replace("%rel_holly_clan_rel%", "")
            .replace("%rel_holly_clan_tag%", "")
            .replace("%rel_holly_clan%", "");
        return stripped.contains("%rel_");
    }

    /** YAML multiline / CR → jedna linia; team prefix nie może mieć \\n. */
    private static String normalizeIdentityFormat(String format) {
        if (format == null || format.isEmpty()) {
            return format == null ? "" : format;
        }
        return format.replace('\r', ' ').replace('\n', ' ').replaceAll(" {2,}", " ").trim();
    }

    private void invalidateFakeTagSnapshots() {
        softRefreshFakeTagSnapshots();
        lastFakeTabDisplayNames.clear();
        lastFakeTagVersions.clear();
        lastFakeTabDisplayVersions.clear();
        fakeTagVersion++;
    }

    private record FakeTagSnapshot(
        String teamName,
        String identity,
        net.kyori.adventure.text.Component prefix,
        net.kyori.adventure.text.Component suffix,
        net.kyori.adventure.text.format.NamedTextColor color,
        java.util.UUID profileUuid,
        String profileName,
        String tabDisplayRaw,
        net.kyori.adventure.text.Component tabDisplayComponent
    ) {
    }

    private PrefixSuffix resolveFakeNameTag(Player viewer, SpoofedPlayerProfile profile) {
        if (profile == null) {
            return new PrefixSuffix("", "");
        }

        String nameColor = profile.nameColor();
        if (nameColor == null || nameColor.isBlank()) {
            nameColor = "&7";
        }
        String rawFormat = (nameTagFormat == null || nameTagFormat.isBlank())
            ? "%luckperms_prefix%%name_color%%player_name%"
            : nameTagFormat;

        String template = rawFormat;
        if (template.contains("%name_color%")) {
            template = template.replace("%name_color%", nameColor);
        } else if (template.contains("%rank_name_color%")) {
            template = template.replace("%rank_name_color%", nameColor);
        } else if (template.contains("%core_name_color%")) {
            template = template.replace("%core_name_color%", nameColor);
        } else if (template.contains("%holly_name_color%")) {
            template = template.replace("%holly_name_color%", nameColor);
        } else if (template.contains("%holly_rank_name_color%")) {
            template = template.replace("%holly_rank_name_color%", nameColor);
        }

        Player context = viewer != null && viewer.isOnline()
            ? viewer
            : (fakeNameTagViewerDependent ? anyOnlinePlayer() : null);

        NamePlaceholderSpan span = findNamePlaceholderSpan(template);
        if (span == null) {
            return new PrefixSuffix(parseIdentityPlaceholders(context, null, profile, template), "");
        }

        String prefixTemplate = template.substring(0, span.start());
        String suffixTemplate = template.substring(span.end());

        if (!rawFormat.contains("%name_color%") && !rawFormat.contains("%rank_name_color%")
            && !rawFormat.contains("%core_name_color%") && !rawFormat.contains("%holly_name_color%")
            && !rawFormat.contains("%holly_rank_name_color%")) {
            prefixTemplate = prefixTemplate + nameColor;
        }

        return new PrefixSuffix(
            parseIdentityPlaceholders(context, null, profile, prefixTemplate),
            parseIdentityPlaceholders(context, null, profile, suffixTemplate)
        );
    }

    private Player anyOnlinePlayer() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            return player;
        }
        return null;
    }

    private SpoofedPlayerProfile buildSpoofedProfile(String fakeName) {
        return buildSpoofedProfile(fakeName, true);
    }

    private SpoofedPlayerProfile buildSpoofedProfile(String fakeName, boolean includeEquipment) {
        if (fakeName == null || fakeName.isBlank()) {
            return null;
        }
        Object tabData = null;
        if (currentSpooferTabDataMap != null) {
            tabData = currentSpooferTabDataMap.get(fakeName);
            if (tabData == null) {
                for (Map.Entry<String, Object> entry : currentSpooferTabDataMap.entrySet()) {
                    if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(fakeName)) {
                        tabData = entry.getValue();
                        break;
                    }
                }
            }
        }
        if (tabData != null) {
            return buildSpoofedProfileFromTabData(fakeName, tabData, includeEquipment);
        }
        // Always rebuild with equipment when nametag/TAB needs holly placeholders (armorstars).
        if (!includeEquipment && fakeProfileNeedsEquipment()) {
            includeEquipment = true;
        }
        if (includeEquipment) {
            ensureSpooferEquipmentMethods();
        } else if (spooferGetFakeBotUuidMethod == null) {
            spooferApiChecked = false;
            getSpooferApi();
        }
        java.util.UUID uuid = invokeFakeUuid(fakeName);
        if (uuid == null) {
            uuid = java.util.UUID.nameUUIDFromBytes(
                ("holly-fake-tag-" + fakeName).getBytes(StandardCharsets.UTF_8)
            );
        }
        String group = invokeFakeGroup(fakeName);
        if (group == null || group.isBlank()) {
            group = fallbackGroup != null ? fallbackGroup : DEFAULT_FALLBACK_GROUP;
        }
        int priority = invokeFakePriority(fakeName);
        LuckPermsIdentityData lpData = loadLuckPermsIdentityForGroup(group, priority);
        String resolvedGroup = lpData != LuckPermsIdentityData.EMPTY && !lpData.primaryGroup.isBlank()
            ? lpData.primaryGroup
            : group;
        String nameColor = resolveLuckPermsNameColor(resolvedGroup);
        if (nameColor == null || nameColor.isBlank()) {
            nameColor = TexturePackRankIcons.nameColorFor(resolvedGroup);
        }
        String lpPrefix = lpData.prefix;
        if (ColorUtils.isBlankLoreLine(lpPrefix)) {
            lpPrefix = resolveGroupPrefix(resolvedGroup);
        }
        if (uuid != null && plugin.getEloManager() != null) {
            plugin.getEloManager().ensureSpoofedElo(uuid);
        }
        return new SpoofedPlayerProfile(
            uuid,
            fakeName,
            resolvedGroup,
            priority,
            lpPrefix,
            lpData.suffix,
            nameColor,
            includeEquipment ? getFakeBotItem(fakeName, spooferGetFakeBotHelmetMethod) : null,
            includeEquipment ? getFakeBotItem(fakeName, spooferGetFakeBotChestplateMethod) : null,
            includeEquipment ? getFakeBotItem(fakeName, spooferGetFakeBotLeggingsMethod) : null,
            includeEquipment ? getFakeBotItem(fakeName, spooferGetFakeBotBootsMethod) : null,
            includeEquipment ? getFakeBotItem(fakeName, spooferGetFakeBotWeaponMethod) : null
        );
    }

    private SpoofedPlayerProfile buildSpoofedProfileFromTabData(String fakeName, Object tabData, boolean includeEquipment) {
        if (!includeEquipment && fakeProfileNeedsEquipment()) {
            includeEquipment = true;
        }
        java.util.UUID uuid = extractTabDataUuid(tabData);
        if (uuid == null) {
            uuid = java.util.UUID.nameUUIDFromBytes(
                ("holly-fake-tag-" + fakeName).getBytes(StandardCharsets.UTF_8)
            );
        }
        String group = extractTabDataString(tabData, "luckPermsGroup");
        if (group == null || group.isBlank()) {
            group = fallbackGroup != null ? fallbackGroup : DEFAULT_FALLBACK_GROUP;
        }
        int priority = extractTabDataInt(tabData, "tabPriority", getFakeTabPriority());
        LuckPermsIdentityData lpData = loadLuckPermsIdentityForGroup(group, priority);
        String resolvedGroup = lpData != LuckPermsIdentityData.EMPTY && !lpData.primaryGroup.isBlank()
            ? lpData.primaryGroup
            : group;
        String nameColor = resolveLuckPermsNameColor(resolvedGroup);
        if (nameColor == null || nameColor.isBlank()) {
            nameColor = TexturePackRankIcons.nameColorFor(resolvedGroup);
        }
        String lpPrefix = lpData.prefix;
        if (ColorUtils.isBlankLoreLine(lpPrefix)) {
            lpPrefix = resolveGroupPrefix(resolvedGroup);
        }
        if (uuid != null && plugin.getEloManager() != null) {
            plugin.getEloManager().ensureSpoofedElo(uuid);
        }
        return new SpoofedPlayerProfile(
            uuid,
            fakeName,
            resolvedGroup,
            priority,
            lpPrefix,
            lpData.suffix,
            nameColor,
            includeEquipment ? extractTabDataItem(tabData, "helmet") : null,
            includeEquipment ? extractTabDataItem(tabData, "chestplate") : null,
            includeEquipment ? extractTabDataItem(tabData, "leggings") : null,
            includeEquipment ? extractTabDataItem(tabData, "boots") : null,
            includeEquipment ? extractTabDataItem(tabData, "weapon") : null
        );
    }

    private Map<String, Object> loadSpooferTabDataMap() {
        if (spooferGetFakeBotTabDataMapMethod == null) {
            getSpooferApi();
        }
        if (spooferGetFakeBotTabDataMapMethod == null) {
            return null;
        }
        Object api = getSpooferApi();
        if (api == null) {
            return null;
        }
        try {
            Object result = spooferGetFakeBotTabDataMapMethod.invoke(api);
            if (!(result instanceof Map<?, ?> raw)) {
                return null;
            }
            Map<String, Object> map = new HashMap<>(raw.size() * 2);
            for (Map.Entry<?, ?> entry : raw.entrySet()) {
                if (entry.getKey() instanceof String key) {
                    map.put(key, entry.getValue());
                }
            }
            return map;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private java.util.UUID extractTabDataUuid(Object tabData) {
        Object value = invokeTabDataAccessor(tabData, "uuid");
        return value instanceof java.util.UUID id ? id : null;
    }

    private String extractTabDataString(Object tabData, String component) {
        Object value = invokeTabDataAccessor(tabData, component);
        return value instanceof String text ? text : null;
    }

    private int extractTabDataInt(Object tabData, String component, int fallback) {
        Object value = invokeTabDataAccessor(tabData, component);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private ItemStack extractTabDataItem(Object tabData, String component) {
        Object value = invokeTabDataAccessor(tabData, component);
        return value instanceof ItemStack stack ? stack : null;
    }

    private Object invokeTabDataAccessor(Object tabData, String component) {
        if (tabData == null || component == null) {
            return null;
        }
        try {
            Method accessor = tabDataAccessorCache.get(component);
            if (accessor == null) {
                accessor = tabData.getClass().getMethod(component);
                tabDataAccessorCache.putIfAbsent(component, accessor);
                accessor = tabDataAccessorCache.get(component);
            }
            return accessor.invoke(tabData);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private java.util.UUID invokeFakeUuid(String fakeName) {
        if (spooferGetFakeBotUuidMethod == null) {
            return null;
        }
        Object api = getSpooferApi();
        if (api == null) {
            return null;
        }
        try {
            Object result = spooferGetFakeBotUuidMethod.invoke(api, fakeName);
            return result instanceof java.util.UUID id ? id : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private String invokeFakeGroup(String fakeName) {
        if (spooferGetFakeBotLuckPermsGroupMethod == null) {
            return fallbackGroup;
        }
        Object api = getSpooferApi();
        if (api == null) {
            return fallbackGroup;
        }
        try {
            Object result = spooferGetFakeBotLuckPermsGroupMethod.invoke(api, fakeName);
            return result instanceof String group && !group.isBlank() ? group : fallbackGroup;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return fallbackGroup;
        }
    }

    private int invokeFakePriority(String fakeName) {
        if (spooferGetFakeBotTabPriorityMethod == null) {
            return getFakeTabPriority();
        }
        Object api = getSpooferApi();
        if (api == null) {
            return getFakeTabPriority();
        }
        try {
            Object result = spooferGetFakeBotTabPriorityMethod.invoke(api, fakeName);
            if (result instanceof Number number) {
                return number.intValue();
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        return getFakeTabPriority();
    }

    private LuckPermsIdentityData loadLuckPermsIdentityForGroup(String groupName, int tabPriority) {
        if (!luckPermsEnabled || luckPerms == null) {
            return LuckPermsIdentityData.EMPTY;
        }
        Group group = resolveLuckPermsGroup(groupName);
        if (group == null) {
            return LuckPermsIdentityData.EMPTY;
        }
        try {
            String resolvedGroup = group.getName();
            var meta = group.getCachedData().getMetaData();
            String prefix = meta.getPrefix() != null ? meta.getPrefix() : "";
            if (ColorUtils.isBlankLoreLine(prefix)) {
                prefix = resolveGroupPrefix(resolvedGroup);
            }
            String suffix = meta.getSuffix() != null ? meta.getSuffix() : "";
            return new LuckPermsIdentityData(
                resolvedGroup,
                resolvedGroup,
                prefix,
                suffix,
                tabPriority
            );
        } catch (RuntimeException | LinkageError ignored) {
            return LuckPermsIdentityData.EMPTY;
        }
    }

    private String resolveGroupPrefix(String groupName) {
        if (groupName != null && !groupName.isBlank()) {
            String prefix = prefixOfGroup(groupName);
            if (!ColorUtils.isBlankLoreLine(prefix)) {
                return prefix;
            }
        }
        java.util.LinkedHashSet<String> candidates = new java.util.LinkedHashSet<>();
        candidates.add("gracz");
        candidates.add("default");
        if (fallbackGroup != null && !fallbackGroup.isBlank()) {
            candidates.add(fallbackGroup.toLowerCase(java.util.Locale.ROOT));
        }
        for (String candidate : candidates) {
            String prefix = prefixOfGroup(candidate);
            if (!ColorUtils.isBlankLoreLine(prefix)) {
                return prefix;
            }
        }
        return TexturePackRankIcons.prefixFor(groupName != null ? groupName : fallbackGroup);
    }

    private Group resolveLuckPermsGroup(String groupName) {
        if (!luckPermsEnabled || luckPerms == null) {
            return null;
        }
        java.util.LinkedHashSet<String> candidates = new java.util.LinkedHashSet<>();
        if (groupName != null && !groupName.isBlank()) {
            candidates.add(groupName.toLowerCase(java.util.Locale.ROOT));
        }
        candidates.add("gracz");
        candidates.add("default");
        if (fallbackGroup != null && !fallbackGroup.isBlank()) {
            candidates.add(fallbackGroup.toLowerCase(java.util.Locale.ROOT));
        }
        for (String candidate : candidates) {
            try {
                Group group = luckPerms.getGroupManager().getGroup(candidate);
                if (group != null) {
                    return group;
                }
            } catch (RuntimeException | LinkageError ignored) {
            }
        }
        return null;
    }

    private String resolveLuckPermsNameColor(String groupName) {
        return resolveLuckPermsGroupNameColor(groupName);
    }

    /** Kolor nicku: LP meta → config rank-name-colors → texturepack fallback. */
    private String resolveLuckPermsGroupNameColor(String groupName) {
        String cacheKey = groupName == null ? "" : groupName.toLowerCase(java.util.Locale.ROOT);
        String cached = groupNameColorCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        String resolved = resolveLuckPermsGroupNameColorUncached(groupName);
        groupNameColorCache.put(cacheKey, resolved == null ? "" : resolved);
        return resolved;
    }

    private String resolveLuckPermsGroupNameColorUncached(String groupName) {
        if (luckPermsEnabled && luckPerms != null) {
            Group group = resolveLuckPermsGroup(groupName);
            if (group != null) {
                try {
                    String metaColor = group.getCachedData().getMetaData().getMetaValue("name-color");
                    if (metaColor != null && !metaColor.isBlank()) {
                        return metaColor;
                    }
                } catch (RuntimeException | LinkageError ignored) {
                }
            }
        }
        if (groupName != null && !groupName.isBlank()) {
            String configured = rankNameColors.get(groupName.toLowerCase(java.util.Locale.ROOT));
            if (configured != null && !configured.isBlank()) {
                return configured;
            }
        }
        return TexturePackRankIcons.nameColorFor(groupName);
    }

    private void ensureSpooferEquipmentMethods() {
        if (spooferGetFakeBotHelmetMethod != null && spooferGetFakeBotUuidMethod != null) {
            return;
        }
        spooferApiChecked = false;
        getSpooferApi();
    }

    private org.bukkit.inventory.ItemStack getFakeBotItem(String fakeName, Method method) {
        if (method == null || fakeName == null || fakeName.isBlank()) {
            return null;
        }
        Object api = getSpooferApi();
        if (api == null) {
            return null;
        }
        try {
            Object result = method.invoke(api, fakeName);
            return result instanceof org.bukkit.inventory.ItemStack stack ? stack : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private int getFakeTabPriority() {
        Integer configured = getConfiguredLpcGroupPriority(fallbackGroup);
        return configured != null ? configured : unknownGroupWeight;
    }

    private String buildFakeNameTagTeamName(String fakeName, int priority) {
        UUID uuid = UUID.nameUUIDFromBytes(("holly-fake-tag-" + fakeName).getBytes(StandardCharsets.UTF_8));
        return compactNameTagTeamName(priority, uuid);
    }

    private String getNameTagTeamName(Player target) {
        int priority = getLuckPermsIdentityData(target).tabPriority;
        return compactNameTagTeamName(priority, target.getUniqueId());
    }

    /**
     * ViaVersion 1.8–1.12 tnie nazwę teamu do 16 znaków. Stary format
     * {@code h000 + nick} kolidował przy długich nickach i zabierał kolor.
     */
    private static String compactNameTagTeamName(int priority, UUID uuid) {
        int prio = Math.max(0, Math.min(999, priority));
        String hex = uuid == null ? "000000000000" : uuid.toString().replace("-", "");
        String name = "h" + String.format("%03d", prio) + hex;
        return name.length() > 16 ? name.substring(0, 16) : name;
    }

    private static boolean isRemovableNametagTeam(String teamName) {
        if (teamName == null || teamName.isEmpty()
            || teamName.startsWith(SIDEBAR_TEAM_PREFIX)
            || teamName.startsWith("ht")) {
            return false;
        }
        if (teamName.startsWith(NAMETAG_TEAM_PREFIX)
            || teamName.startsWith("hc_tag_")
            || teamName.startsWith(SLOT_SORT_TEAM_PREFIX)) {
            return true;
        }
        return teamName.length() > 4
            && teamName.charAt(0) == 'h'
            && Character.isDigit(teamName.charAt(1))
            && Character.isDigit(teamName.charAt(2))
            && Character.isDigit(teamName.charAt(3));
    }

    private void resetIdentityAll() {
        lastPlayerListNames.clear();
        lastPlayerListOrders.clear();
        pendingListOrderBroadcasts.clear();
        paperListOrderSynced.clear();
        lastNameTagSuffixes.clear();
        lastNameTagTeams.clear();
        lastFakeNameTagTeams.clear();
        lastFakeNameTagSuffixes.clear();
        invalidateFakeTagSnapshots();
        lastHeaderFooters.clear();
        luckPermsIdentityCache.clear();
        rankNameColorCache.clear();
        groupNameColorCache.clear();
        tabListDisplayCache.clear();
        relationalTabDisplayCache.clear();
        dirtyIdentities.clear();
        tabOrderDirty = false;
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setPlayerListName(player.getName());
            setPlayerListOrder(player, 0);
            Scoreboard scoreboard = player.getScoreboard();
            if (scoreboard == null) {
                continue;
            }
            for (Team team : new ArrayList<>(scoreboard.getTeams())) {
                if (isRemovableNametagTeam(team.getName())) {
                    team.unregister();
                }
            }
        }
    }

    private Map<String, Integer> buildLpcGroupOrder(YamlConfiguration config, String root) {
        Map<String, Integer> order = new HashMap<>();
        List<String> groups = new ArrayList<>();

        List<String> sortingTypes = config.getStringList(root + "sorting.sorting-types");
        if (sortingTypes.isEmpty()) {
            sortingTypes = config.getStringList("sorting.sorting-types");
        }
        for (String entry : sortingTypes) {
            if (entry.toUpperCase(java.util.Locale.ROOT).startsWith("GROUPS:")) {
                String rawGroups = entry.substring("GROUPS:".length());
                for (String group : rawGroups.split(",")) {
                    String cleaned = group.trim().toLowerCase(java.util.Locale.ROOT);
                    if (!cleaned.isEmpty()) {
                        groups.add(cleaned);
                    }
                }
                break;
            }
        }

        if (groups.isEmpty()) {
            List<String> configuredOrder = config.getStringList(root + "rank-order");
            if (configuredOrder.isEmpty() && !root.isEmpty()) {
                configuredOrder = config.getStringList("rank-order");
            }
            if (!configuredOrder.isEmpty()) {
                groups.addAll(configuredOrder);
            }
        }

        // rank-order: pierwsza = najwyżej w tabie. Puste wpisy nie biorą wagi.
        List<String> primaryList = !groups.isEmpty() ? groups : DEFAULT_RANK_ORDER;
        int index = 0;
        for (String groupName : primaryList) {
            if (groupName == null || groupName.isBlank()) {
                continue;
            }
            String cleaned = groupName.trim().toLowerCase(java.util.Locale.ROOT);
            if (order.putIfAbsent(cleaned, index) == null) {
                index++;
            }
        }
        LuckPermsGroups.setRankOrder(order.keySet().stream()
            .sorted(java.util.Comparator.comparingInt(order::get))
            .toList());
        return order;
    }

    private static final EnumSet<WrapperPlayServerPlayerInfoUpdate.Action> UPDATE_LIST_ORDER_ACTIONS = EnumSet.of(
        WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LIST_ORDER,
        // Sam LIST_ORDER często nie przebudowuje otwartego TABa u klienta — LISTED wymusza refresh.
        WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED
    );
    /** Przy custom TAB nie ruszamy LISTED (musi zostać false). */
    private static final EnumSet<WrapperPlayServerPlayerInfoUpdate.Action> UPDATE_LIST_ORDER_ONLY_ACTIONS = EnumSet.of(
        WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LIST_ORDER
    );

    /**
     * Sortowanie TAB 1.21.2+: wyższy listOrder = wyżej na liście.
     * rank-order index (0 = root) mapujemy na {@code 999 - waga}.
     * Join bez LP = listOrder 0 (dół), potem skok na właściwą rangę.
     */
    private void applyLpcTabOrderAll() {
        if (!sortByLuckPerms) {
            return;
        }
        List<WrapperPlayServerPlayerInfoUpdate.PlayerInfo> entries = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player == null || !player.isOnline()) {
                continue;
            }
            int order = resolveTabListOrder(player);
            Integer previous = lastPlayerListOrders.get(player.getUniqueId());
            setPlayerListOrder(player, order);
            boolean pending = pendingListOrderBroadcasts.remove(player.getUniqueId());
            if (!pending && previous != null && previous == order) {
                continue;
            }
            addListOrderEntry(entries, player.getUniqueId(), order);
        }
        // Custom TAB nie listuje botów, ale listOrder i tak ustaw —
        // jak bot wycieknie na listę, nie ląduje na górze (wysoki listOrder).
        appendChangedFakeBotListOrders(entries);
        broadcastListOrderEntries(entries);
    }

    private void appendChangedFakeBotListOrders(List<WrapperPlayServerPlayerInfoUpdate.PlayerInfo> entries) {
        Map<String, Object> tabDataMap = loadSpooferTabDataMap();
        if (tabDataMap != null && !tabDataMap.isEmpty()) {
            for (Map.Entry<String, Object> entry : tabDataMap.entrySet()) {
                String fakeName = entry.getKey();
                Object tabData = entry.getValue();
                if (fakeName == null || fakeName.isBlank() || tabData == null) {
                    continue;
                }
                try {
                    java.util.UUID uuid = extractTabDataUuid(tabData);
                    if (uuid == null) {
                        uuid = SpooferBridge.getFakeBotUuid(fakeName);
                    }
                    if (uuid == null) {
                        continue;
                    }
                    int weight = extractTabDataInt(tabData, "tabPriority", -1);
                    if (weight < 0 || weight > 999) {
                        weight = getFakeTabPriority();
                    }
                    addChangedFakeBotListOrder(entries, uuid, toClientListOrder(weight));
                } catch (RuntimeException | LinkageError ignored) {
                }
            }
            return;
        }
        for (String fakeName : getSpooferFakeNames()) {
            if (fakeName == null || fakeName.isBlank()) {
                continue;
            }
            try {
                java.util.UUID uuid = SpooferBridge.getFakeBotUuid(fakeName);
                if (uuid == null) {
                    continue;
                }
                int weight = SpooferBridge.getFakeBotTabPriority(fakeName, -1);
                if (weight < 0 || weight > 999) {
                    weight = getFakeTabPriority();
                }
                addChangedFakeBotListOrder(entries, uuid, toClientListOrder(weight));
            } catch (RuntimeException | LinkageError ignored) {
            }
        }
    }

    private void addChangedFakeBotListOrder(
        List<WrapperPlayServerPlayerInfoUpdate.PlayerInfo> entries,
        UUID uuid,
        int order
    ) {
        Integer previous = lastPlayerListOrders.get(uuid);
        if (previous != null && previous == order) {
            return;
        }
        lastPlayerListOrders.put(uuid, order);
        knownFakeBotUuids.add(uuid);
        addListOrderEntry(entries, uuid, order);
    }

    private void addListOrderEntry(
        List<WrapperPlayServerPlayerInfoUpdate.PlayerInfo> entries,
        UUID uuid,
        int order
    ) {
        try {
            WrapperPlayServerPlayerInfoUpdate.PlayerInfo info =
                new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(uuid);
            info.setListOrder(order);
            if (!customTabEnabled) {
                info.setListed(true);
            }
            entries.add(info);
        } catch (RuntimeException | LinkageError ignored) {
        }
    }

    private void broadcastListOrderEntries(List<WrapperPlayServerPlayerInfoUpdate.PlayerInfo> entries) {
        if (entries == null || entries.isEmpty() || !isPacketEventsReady()) {
            return;
        }
        try {
            Collection<? extends Player> viewers = Bukkit.getOnlinePlayers();
            EnumSet<WrapperPlayServerPlayerInfoUpdate.Action> modern =
                customTabEnabled ? UPDATE_LIST_ORDER_ONLY_ACTIONS : UPDATE_LIST_ORDER_ACTIONS;
            EnumSet<WrapperPlayServerPlayerInfoUpdate.Action> legacy =
                customTabEnabled ? EnumSet.noneOf(WrapperPlayServerPlayerInfoUpdate.Action.class) : UPDATE_LISTED_ACTIONS;
            for (int i = 0; i < entries.size(); i += LIST_ORDER_PACKET_CHUNK) {
                int end = Math.min(i + LIST_ORDER_PACKET_CHUNK, entries.size());
                List<WrapperPlayServerPlayerInfoUpdate.PlayerInfo> chunk =
                    new ArrayList<>(entries.subList(i, end));
                for (Player viewer : viewers) {
                    if (viewer != null && viewer.isOnline() && serverSupportsListOrder() && viewerSupportsListOrder(viewer)) {
                        sendPlayerInfoUpdate(viewer, modern, legacy, chunk);
                    }
                }
            }
        } catch (RuntimeException | LinkageError ignored) {
        }
    }

    /**
     * Waga z rank-order (0 = root) → listOrder klienta 1.21.2+.
     * Na 1.21+ wyższy listOrder = wyżej — odwracamy wagę (ROOT → 999, GRACZ → 0).
     * Teamy {@code h###} rosnąco (root = h000) + {@code hs###} na sloty — ten sam układ na 1.20.
     */
    private static int toClientListOrder(int rankWeight) {
        int weight = Math.max(0, Math.min(999, rankWeight));
        return 999 - weight;
    }

    private int resolveTabListOrder(Player player) {
        if (player == null) {
            return toClientListOrder(unknownGroupWeight);
        }
        // Dopóki LP nie gotowe — zawsze dół (nigdy jak właściciel).
        if (luckPermsEnabled && !hasResolvedLuckPermsIdentity(player.getUniqueId())) {
            return toClientListOrder(unknownGroupWeight);
        }
        LuckPermsIdentityData data = getLuckPermsIdentityData(player);
        int weight = data != null ? data.tabPriority : unknownGroupWeight;
        if (weight < 0 || weight > 999) {
            weight = unknownGroupWeight;
        }
        return toClientListOrder(weight);
    }

    private void seedBottomListOrder(UUID uuid) {
        if (uuid == null || !sortByLuckPerms) {
            return;
        }
        lastPlayerListOrders.putIfAbsent(uuid, toClientListOrder(unknownGroupWeight));
    }

    private void forceBottomListOrder(UUID uuid) {
        if (uuid == null || !sortByLuckPerms) {
            return;
        }
        lastPlayerListOrders.put(uuid, toClientListOrder(unknownGroupWeight));
    }

    /**
     * Kolejkuj listOrder — flush 1×/tick dla całej fali joinów (zamiast O(n) pakietów per joiner).
     */
    private void broadcastListOrder(UUID uuid, int order) {
        if (uuid == null || !isPacketEventsReady() || !sortByLuckPerms) {
            return;
        }
        lastPlayerListOrders.put(uuid, order);
        pendingListOrderBroadcasts.add(uuid);
        scheduleCoalescedListOrderFlush();
    }

    private void scheduleCoalescedListOrderFlush() {
        if (!enabled || !plugin.isEnabled()) {
            return;
        }
        if (!listOrderFlushQueued.compareAndSet(false, true)) {
            return;
        }
        long delay = Bukkit.getOnlinePlayers().size() > 100 ? 2L : 1L;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            listOrderFlushQueued.set(false);
            flushPendingListOrders();
        }, delay);
    }

    private void flushPendingListOrders() {
        if (!enabled || !isPacketEventsReady() || pendingListOrderBroadcasts.isEmpty()) {
            return;
        }
        List<WrapperPlayServerPlayerInfoUpdate.PlayerInfo> entries = new ArrayList<>();
        for (UUID uuid : Set.copyOf(pendingListOrderBroadcasts)) {
            if (!pendingListOrderBroadcasts.remove(uuid)) {
                continue;
            }
            Integer order = lastPlayerListOrders.get(uuid);
            if (order != null) {
                addListOrderEntry(entries, uuid, order);
            }
        }
        broadcastListOrderEntries(entries);
    }

    /**
     * Pełne sortowanie TAB jest O(graczy^2) — przy fali joinów robiłoby się to
     * po razie na gracza. Zbieramy zmiany i wykonujemy jeden przebieg.
     */
    private void scheduleTabOrderRefresh() {
        if (!enabled || !plugin.isEnabled()) {
            return;
        }
        if (!tabOrderRefreshQueued.compareAndSet(false, true)) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            tabOrderRefreshQueued.set(false);
            if (!enabled) {
                return;
            }
            tabOrderDirty = false;
            applyLpcTabOrderAll();
        }, tabOrderRefreshDelayTicks());
    }

    private long tabOrderRefreshDelayTicks() {
        // Dłuższy coalesce przy fali joinów — jeden przebieg zamiast N×O(online).
        int online = Bukkit.getOnlinePlayers().size();
        if (online > 180) {
            return 8L;
        }
        if (online > 80) {
            return 4L;
        }
        return 2L;
    }

    /**
     * Waga tabu = najlepsza (najniższy index) grupa z rank-order wśród wszystkich
     * grup gracza, nie tylko primary. Dzięki temu właściciel + odebrane VIP
     * zostaje na górze, a puste prefixy grup nie przebijają wyższej rangi.
     */
    private int resolveGroupPriority(String groupName) {
        if (groupName == null || groupName.isBlank()) {
            return unknownGroupWeight;
        }
        Integer configured = getConfiguredLpcGroupPriority(groupName);
        if (configured != null) {
            return configured;
        }
        if (luckPerms != null) {
            try {
                Group group = luckPerms.getGroupManager().getGroup(groupName);
                if (group != null && group.getWeight().isPresent()) {
                    int weight = group.getWeight().getAsInt();
                    return Math.max(10, Math.min(899, 900 - weight));
                }
            } catch (Throwable ignored) {
            }
        }
        int fallbackIndex = LuckPermsGroups.rankIndex(groupName);
        if (fallbackIndex < Integer.MAX_VALUE - 1) {
            return Math.min(unknownGroupWeight - 1, fallbackIndex + 100);
        }
        return unknownGroupWeight;
    }

    private EffectiveRank resolveEffectiveRank(User user) {
        Set<String> groups = LuckPermsGroups.collect(luckPerms, user);
        String primary = user.getPrimaryGroup() != null && !user.getPrimaryGroup().isBlank()
            ? user.getPrimaryGroup()
            : fallbackGroup;
        if (groups.isEmpty()) {
            groups = Set.of(primary.toLowerCase(java.util.Locale.ROOT));
        }

        int bestPriority = unknownGroupWeight;
        String sortGroup = primary;
        String prefix = "";
        int prefixPriority = unknownGroupWeight;

        for (String group : groups) {
            int priority = resolveGroupPriority(group);
            if (priority < bestPriority) {
                bestPriority = priority;
                sortGroup = group;
            }
            String groupPrefix = prefixOfGroup(group);
            if (ColorUtils.isBlankLoreLine(groupPrefix)) {
                continue;
            }
            if (priority < prefixPriority) {
                prefixPriority = priority;
                prefix = groupPrefix;
            }
        }

        String metaPrefix = null;
        try {
            metaPrefix = user.getCachedData().getMetaData().getPrefix();
        } catch (Throwable ignored) {
        }

        if (ColorUtils.isBlankLoreLine(prefix)) {
            prefix = metaPrefix != null ? metaPrefix : "";
        }
        if (ColorUtils.isBlankLoreLine(prefix)) {
            prefix = LuckPermsGroups.bestPrefix(luckPerms, user);
        }
        if (ColorUtils.isBlankLoreLine(prefix)) {
            prefix = resolveGroupPrefix(sortGroup);
        }
        if (ColorUtils.isBlankLoreLine(prefix)) {
            prefix = TexturePackRankIcons.prefixFor(sortGroup);
        }

        String suffix = "";
        try {
            String metaSuffix = user.getCachedData().getMetaData().getSuffix();
            if (metaSuffix != null) {
                suffix = metaSuffix;
            }
        } catch (Throwable ignored) {
        }

        return new EffectiveRank(primary, sortGroup, prefix == null ? "" : prefix, suffix, bestPriority);
    }

    private String prefixOfGroup(String groupName) {
        if (luckPerms == null || groupName == null || groupName.isBlank()) {
            return "";
        }
        try {
            Group group = luckPerms.getGroupManager().getGroup(groupName);
            if (group == null) {
                return "";
            }
            String prefix = group.getCachedData().getMetaData().getPrefix();
            return prefix == null ? "" : prefix;
        } catch (RuntimeException | LinkageError ignored) {
            return "";
        }
    }

    public void dumpDebugInfo(org.bukkit.command.CommandSender sender) {
        sender.sendMessage(dev.grzybalson.utils.ColorUtils.colorizeLegacy("&e=== TABLIST DEBUG INFO ==="));
        sender.sendMessage(dev.grzybalson.utils.ColorUtils.colorizeLegacy("&6Loaded rank-order: &f" + lpcGroupOrder));
        sender.sendMessage(dev.grzybalson.utils.ColorUtils.colorizeLegacy("&6Unknown weight: &f" + unknownGroupWeight + " &8| &6Fallback: &f" + fallbackGroup));
        for (Player p : Bukkit.getOnlinePlayers()) {
            LuckPermsIdentityData data = getLuckPermsIdentityData(p);
            String teamName = getNameTagTeamName(p);
            sender.sendMessage(dev.grzybalson.utils.ColorUtils.colorizeLegacy(
                "&7- &f" + p.getName()
                    + " &8| &ePrimary: &f" + data.primaryGroup
                    + " &8| &eSort: &f" + data.sortGroup
                    + " &8| &aWeight: &f" + data.tabPriority
                    + " &8| &dListOrder: &f" + resolveTabListOrder(p)
                    + " &8| &bTeam: &f" + teamName
            ));
        }
    }

    private Integer getConfiguredLpcGroupPriority(String groupName) {
        if (groupName == null || groupName.isBlank()) {
            return null;
        }
        return lpcGroupOrder.get(groupName.toLowerCase(java.util.Locale.ROOT));
    }

    private LuckPermsIdentityData getLuckPermsIdentityData(Player player) {
        LuckPermsIdentityData cached = luckPermsIdentityCache.get(player.getUniqueId());
        if (cached != null) {
            return cached;
        }
        LuckPermsIdentityData loaded = loadLuckPermsIdentityData(player);
        // Puste LP (user jeszcze nie załadowany) NIE cachujemy — inaczej 2% graczy
        // zostaje bez koloru nicku aż do reloga.
        if (loaded != LuckPermsIdentityData.EMPTY) {
            luckPermsIdentityCache.put(player.getUniqueId(), loaded);
        }
        return loaded;
    }

    private LuckPermsIdentityData loadLuckPermsIdentityData(Player player) {
        try {
            if (!luckPermsEnabled || luckPerms == null) {
                return LuckPermsIdentityData.EMPTY;
            }
            User user = luckPerms.getUserManager().getUser(player.getUniqueId());
            if (user == null) {
                return LuckPermsIdentityData.EMPTY;
            }
            String group = user.getPrimaryGroup();
            EffectiveRank rank = resolveEffectiveRank(user);
            return new LuckPermsIdentityData(
                rank.primaryGroup.isBlank() ? (group != null ? group : "") : rank.primaryGroup,
                rank.sortGroup,
                rank.prefix,
                rank.suffix,
                rank.tabPriority
            );
        } catch (RuntimeException | LinkageError exception) {
            return LuckPermsIdentityData.EMPTY;
        }
    }

    /** Kazde wywolanie synchronizuje Paper przy zmianie albo gdy jeszcze nie bylo syncu po joine. */
    private void setPlayerListOrder(Player player, int order) {
        if (player == null) {
            return;
        }
        int value = Math.max(0, Math.min(999, order));
        UUID uuid = player.getUniqueId();
        Integer previous = lastPlayerListOrders.get(uuid);
        boolean changed = previous == null || previous != value;
        boolean needsPaperSync = changed || !paperListOrderSynced.contains(uuid);
        lastPlayerListOrders.put(uuid, value);
        if (changed) {
            pendingListOrderBroadcasts.add(uuid);
        }
        if (!needsPaperSync) {
            return;
        }
        try {
            Method method = getPlayerListOrderMethod();
            if (method != null) {
                // Pierwszy sync po AsyncPreLogin seedzie — bez tego Paper zostawia zły default.
                method.invoke(player, value);
                paperListOrderSynced.add(uuid);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Starsze API bez setPlayerListOrder — pakiety listOrder i tak idą z cache.
        }
    }

    private Method getPlayerListOrderMethod() {
        if (playerListOrderMethodChecked) {
            return playerListOrderMethod;
        }
        playerListOrderMethodChecked = true;
        try {
            playerListOrderMethod = Player.class.getMethod("setPlayerListOrder", int.class);
        } catch (ReflectiveOperationException ignored) {
            playerListOrderMethod = null;
        }
        return playerListOrderMethod;
    }

    public String getRankNameColor(Player player) {
        if (player == null || !luckPermsEnabled || luckPerms == null) {
            return "";
        }
        String cached = rankNameColorCache.get(player.getUniqueId());
        if (cached != null) {
            return cached;
        }
        String resolved = computeRankNameColor(player);
        rankNameColorCache.put(player.getUniqueId(), resolved == null ? "" : resolved);
        return resolved;
    }

    private String computeRankNameColor(Player player) {
        try {
            User user = luckPerms.getUserManager().getUser(player.getUniqueId());
            if (user != null) {
                String userColor = user.getCachedData().getMetaData().getMetaValue("name-color");
                if (userColor != null && !userColor.isBlank()) {
                    return userColor;
                }
                EffectiveRank rank = resolveEffectiveRank(user);
                String groupColor = resolveLuckPermsGroupNameColor(rank.sortGroup);
                if (!groupColor.isBlank()) {
                    return groupColor;
                }
            }
        } catch (RuntimeException | LinkageError ignored) {
        }
        LuckPermsIdentityData lpData = getLuckPermsIdentityData(player);
        if (lpData != null && lpData.sortGroup != null && !lpData.sortGroup.isBlank()) {
            return resolveLuckPermsGroupNameColor(lpData.sortGroup);
        }
        return resolveLuckPermsGroupNameColor(fallbackGroup);
    }

    /** Kolor nicku dla grupy LP — LP meta → config rank-name-colors → texturepack. */
    public String getRankNameColorForGroup(String groupName) {
        return resolveLuckPermsGroupNameColor(groupName);
    }

    public String resolveSpoofedChatPrefix(String fakeName, String groupName) {
        SpoofedPlayerProfile profile = fakeName != null ? buildSpoofedProfile(fakeName, false) : null;
        if (profile != null && !ColorUtils.isBlankLoreLine(profile.luckPermsPrefix())) {
            return profile.luckPermsPrefix();
        }
        String group = profile != null && profile.primaryGroup() != null && !profile.primaryGroup().isBlank()
            ? profile.primaryGroup()
            : (groupName == null || groupName.isBlank() ? "default" : groupName);
        String prefix = resolveGroupPrefix(group);
        if (!ColorUtils.isBlankLoreLine(prefix)) {
            return prefix;
        }
        return TexturePackRankIcons.prefixFor(group);
    }

    public String resolveSpoofedChatNameColor(String fakeName, String groupName) {
        SpoofedPlayerProfile profile = fakeName != null ? buildSpoofedProfile(fakeName, false) : null;
        if (profile != null && profile.nameColor() != null && !profile.nameColor().isBlank()) {
            return profile.nameColor();
        }
        String group = profile != null && profile.primaryGroup() != null && !profile.primaryGroup().isBlank()
            ? profile.primaryGroup()
            : (groupName == null || groupName.isBlank() ? "default" : groupName);
        String color = getRankNameColorForGroup(group);
        if (color != null && !color.isBlank()) {
            return color;
        }
        return TexturePackRankIcons.nameColorFor(group);
    }

    /**
     * Podmienia %holly_*% (w tym armorstars) dla wiadomości chatu botów spoofera.
     */
    public String resolveSpoofedChatPlaceholders(String fakeName, java.util.UUID uuid, String groupName, String format) {
        if (format == null || format.isEmpty()) {
            return "";
        }
        SpoofedPlayerProfile profile = buildSpoofedProfile(fakeName, true);
        if (profile == null) {
            String group = (groupName == null || groupName.isBlank()) ? "default" : groupName;
            String nameColor = getRankNameColorForGroup(group);
            LuckPermsIdentityData lpData = loadLuckPermsIdentityForGroup(group, 0);
            profile = new SpoofedPlayerProfile(
                uuid != null ? uuid : java.util.UUID.nameUUIDFromBytes(("spoof:" + fakeName).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                fakeName != null ? fakeName : "Bot",
                group,
                0,
                lpData.prefix,
                lpData.suffix,
                nameColor,
                null,
                null,
                null,
                null,
                null
            );
        }

        String parsed = format;
        if (plugin.getHollyPlaceholderExpansion() != null && parsed.contains("%holly_")) {
            parsed = parseSpoofedHollyPlaceholders(profile, parsed);
        }
        if (hasPlaceholder(parsed)) {
            parsed = PLACEHOLDER_TOKEN_PATTERN.matcher(parsed).replaceAll("");
        }
        return parsed;
    }

    private String getFakeRankNameColor() {
        String group = fallbackGroup == null ? "default" : fallbackGroup.toLowerCase(java.util.Locale.ROOT);
        return getRankNameColorForGroup(group);
    }

    private String parseIdentityPlaceholders(Player viewer, Player target, String text) {
        return parseIdentityPlaceholders(viewer, target, null, text);
    }

    private String parseIdentityPlaceholders(Player viewer, Player target, SpoofedPlayerProfile spoofed, String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        String displayName = target != null ? target.getName() : spoofed.name();
        if (target != null) {
            IncognitoManager incognito = plugin.getIncognitoManager();
            if (incognito != null && incognito.isIncognito(target)) {
                displayName = incognito.displayName(target);
            }
        }
        String nameColor = target != null ? getRankNameColor(target) : (spoofed != null ? spoofed.nameColor() : "&7");
        if (nameColor == null || nameColor.isBlank()) {
            nameColor = "&7";
        }
        String parsed = normalizeCorePlaceholders(target, text);

        if (parsed.contains("%name_color%") || parsed.contains("%rank_name_color%")
            || parsed.contains("%core_name_color%") || parsed.contains("%holly_name_color%")
            || parsed.contains("%holly_rank_name_color%")) {
            parsed = parsed.replace("%name_color%", nameColor)
                .replace("%rank_name_color%", nameColor)
                .replace("%core_name_color%", nameColor)
                .replace("%holly_name_color%", nameColor)
                .replace("%holly_rank_name_color%", nameColor);
        }

        if (parsed.contains("%player_name%")) {
            String nameReplacement = text.contains("%name_color%") || text.contains("%rank_name_color%")
                || text.contains("%core_name_color%") || text.contains("%holly_name_color%")
                || text.contains("%holly_rank_name_color%")
                ? displayName
                : nameColor + displayName;
            parsed = parsed.replace("%player_name%", nameReplacement);
        }
        if (parsed.contains("%holly_nick%") || parsed.contains("%holly_name%")
            || parsed.contains("%core_nick%") || parsed.contains("%core_name%") || parsed.contains("%core_player_name%")) {
            String nickReplacement = text.contains("%name_color%") || text.contains("%rank_name_color%")
                || text.contains("%core_name_color%") || text.contains("%holly_name_color%")
                || text.contains("%holly_rank_name_color%")
                ? displayName
                : nameColor + displayName;
            parsed = parsed.replace("%holly_nick%", nickReplacement)
                .replace("%holly_name%", nickReplacement)
                .replace("%core_nick%", nickReplacement)
                .replace("%core_name%", nickReplacement)
                .replace("%core_player_name%", nickReplacement);
        }

        if (parsed.contains("%luckperms_")) {
            LuckPermsIdentityData lpData = target != null
                ? getLuckPermsIdentityData(target)
                : new LuckPermsIdentityData(
                    spoofed.primaryGroup(),
                    spoofed.primaryGroup(),
                    spoofed.luckPermsPrefix(),
                    spoofed.luckPermsSuffix(),
                    spoofed.tabPriority()
                );
            if (parsed.contains("%luckperms_prefix%")) {
                parsed = parsed.replace("%luckperms_prefix%", lpData.prefix);
            }
            if (parsed.contains("%luckperms_suffix%")) {
                parsed = parsed.replace("%luckperms_suffix%", lpData.suffix);
            }
            if (parsed.contains("%luckperms_primary_group_name%")) {
                parsed = parsed.replace("%luckperms_primary_group_name%", lpData.primaryGroup);
            }
        }

        if (parsed.contains("%goxy_")) {
            parsed = applyGoxyOnlinePlaceholders(target != null ? target : viewer, parsed);
        }

        if (target != null) {
            parsed = parseLocalWalletPlaceholders(target, parsed);
        }

        if (plugin.getHollyPlaceholderExpansion() != null) {
            if (target != null) {
                if (parsed.contains("%rel_holly_")) {
                    parsed = parseRelationalHollyPlaceholders(viewer, target, parsed);
                }
                if (parsed.contains("%holly_")) {
                    parsed = parseHollyPlaceholders(target, parsed);
                }
            } else if (spoofed != null) {
                // Ten sam pipeline co u gracza: clan rel + holly_* (armorstars itd.).
                if (parsed.contains("%rel_holly_")) {
                    parsed = parseRelationalHollyPlaceholdersForSpoofed(viewer, spoofed, parsed);
                }
                if (parsed.contains("%holly_")) {
                    parsed = parseSpoofedHollyPlaceholders(spoofed, parsed);
                }
            }
        }

        if (papiEnabled && hasPlaceholder(parsed)) {
            parsed = BungeeSpooferPlaceholders.apply(target != null ? target : viewer, parsed);
            if (target != null) {
                if (parsed.contains("%rel_")) {
                    parsed = PlaceholderAPI.setRelationalPlaceholders(viewer, target, parsed);
                }
                if (hasPlaceholder(parsed)) {
                    parsed = PlaceholderAPI.setPlaceholders(target, parsed);
                }
            }
            // Boty: NIE wołaj PAPI na OfflinePlayer(fake UUID) — kasuje / nie resolvuje
            // luckperms/holly i zostawia surowe tokeny albo puste nametagi.
        }
        return hasPlaceholder(parsed) ? PLACEHOLDER_TOKEN_PATTERN.matcher(parsed).replaceAll("") : parsed;
    }

    private String parseSpoofedHollyPlaceholders(SpoofedPlayerProfile spoofed, String text) {
        java.util.regex.Matcher matcher = HOLLY_PLACEHOLDER_PATTERN.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String value = plugin.getHollyPlaceholderExpansion().onRequestForSpoofedProfile(spoofed, matcher.group(1));
            matcher.appendReplacement(buffer, java.util.regex.Matcher.quoteReplacement(value != null ? value : ""));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String parseRelationalHollyPlaceholders(Player viewer, Player target, String text) {
        Matcher matcher = REL_HOLLY_PLACEHOLDER_PATTERN.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String value = plugin.getHollyPlaceholderExpansion().onPlaceholderRequest(viewer, target, matcher.group(1));
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(value != null ? value : matcher.group()));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String parseRelationalHollyPlaceholdersForSpoofed(
        Player viewer,
        SpoofedPlayerProfile spoofed,
        String text
    ) {
        if (spoofed == null || text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }
        Matcher matcher = REL_HOLLY_PLACEHOLDER_PATTERN.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String value = plugin.getHollyPlaceholderExpansion()
                .onPlaceholderRequestForSpoofed(viewer, spoofed, matcher.group(1));
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(value != null ? value : ""));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String parsePlaceholders(Player player, String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        text = normalizeCorePlaceholders(player, text);
        String parsed = text
            .replace("%player_name%", player.getName())
            .replace("%holly_nick%", player.getName());
        parsed = applyGoxyOnlinePlaceholders(player, parsed);

        if (parsed.contains("%name_color%") || parsed.contains("%rank_name_color%")) {
            String nc = getRankNameColor(player);
            if (nc.isBlank()) {
                nc = resolveLuckPermsGroupNameColor(fallbackGroup);
            }
            parsed = parsed.replace("%name_color%", nc).replace("%rank_name_color%", nc);
        }

        if (parsed.contains("%luckperms_")) {
            LuckPermsIdentityData lpData = getLuckPermsIdentityData(player);
            if (parsed.contains("%luckperms_prefix%")) {
                parsed = parsed.replace("%luckperms_prefix%", lpData.prefix);
            }
            if (parsed.contains("%luckperms_suffix%")) {
                parsed = parsed.replace("%luckperms_suffix%", lpData.suffix);
            }
            if (parsed.contains("%luckperms_primary_group_name%")) {
                parsed = parsed.replace("%luckperms_primary_group_name%", lpData.primaryGroup);
            }
            if (parsed.contains("%luckperms_group%")) {
                parsed = parsed.replace("%luckperms_group%", lpData.sortGroup.isBlank() ? lpData.primaryGroup : lpData.sortGroup);
            }
            if (parsed.contains("%luckperms_highest_group_by_weight%")) {
                parsed = parsed.replace("%luckperms_highest_group_by_weight%", lpData.sortGroup.isBlank() ? lpData.primaryGroup : lpData.sortGroup);
            }
        }

        if (plugin.getHollyPlaceholderExpansion() != null && parsed.contains("%holly_")) {
            parsed = parseCommonHollyPlaceholders(player, parsed);
            parsed = parseHollyPlaceholders(player, parsed);
        }

        parsed = parseLocalWalletPlaceholders(player, parsed);

        if (papiEnabled && hasPlaceholder(parsed)) {
            parsed = BungeeSpooferPlaceholders.apply(player, parsed);
            parsed = PlaceholderAPI.setPlaceholders(player, parsed);
        }
        return parsed;
    }

    private String applyGoxyOnlinePlaceholders(org.bukkit.OfflinePlayer player, String parsed) {
        if (parsed == null || !parsed.contains("%goxy_")) {
            return parsed;
        }
        if (parsed.contains("%goxy_network_online%")) {
            parsed = parsed.replace("%goxy_network_online%", BungeeSpooferPlaceholders.resolveCount(player, "all"));
        }
        Matcher matcher = GOXY_SERVER_ONLINE_PATTERN.matcher(parsed);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String count = BungeeSpooferPlaceholders.resolveCount(player, matcher.group(1));
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(count));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private int getVisibleOnlineCount() {
        long now = System.currentTimeMillis();
        if (cachedVisibleOnlineCount >= 0 && now < nextSpooferOnlineRefreshMillis) {
            return cachedVisibleOnlineCount;
        }

        int realPlayers = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!isSpoofedPlayer(player)) {
                realPlayers++;
            }
        }

        int visibleOnline = realPlayers + getLocalSpoofedPlayerCount();
        cachedVisibleOnlineCount = visibleOnline;
        nextSpooferOnlineRefreshMillis = now + SPOOFER_ONLINE_CACHE_MILLIS;
        return visibleOnline;
    }

    private int networkOrLocalOnlineCount() {
        int network = SpooferCountHelper.getNetworkOnlineCount();
        if (network >= 0) {
            return network;
        }
        return getVisibleOnlineCount();
    }

    private boolean isSpoofedPlayer(Player player) {
        Object api = getSpooferApi();
        if (api == null || spooferIsFakePlayerMethod == null) {
            return false;
        }

        try {
            Object result = spooferIsFakePlayerMethod.invoke(api, player);
            return result instanceof Boolean fake && fake;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    private int getLocalSpoofedPlayerCount() {
        Object api = getSpooferApi();
        if (api == null) {
            return 0;
        }

        try {
            String server = null;
            if (spooferGetLocalServerNameMethod != null) {
                Object serverName = spooferGetLocalServerNameMethod.invoke(api);
                if (serverName instanceof String value && !value.isBlank()) {
                    server = value;
                }
            }

            Object result = invokeSpooferCount(api, server);
            if (result instanceof Number number) {
                return Math.max(0, number.intValue());
            }
            if (result instanceof Collection<?> fakePlayers) {
                return fakePlayers.size();
            }
            if (result instanceof Map<?, ?> fakePlayers) {
                return fakePlayers.size();
            }
            return 0;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return 0;
        }
    }

    private Object invokeSpooferCount(Object api, String server) throws ReflectiveOperationException {
        if (server != null) {
            if (spooferGetFakePlayerCountServerMethod != null) return spooferGetFakePlayerCountServerMethod.invoke(api, server);
            if (spooferGetFakePlayerIdsServerMethod != null) return spooferGetFakePlayerIdsServerMethod.invoke(api, server);
            if (spooferGetFakePlayersServerMethod != null) return spooferGetFakePlayersServerMethod.invoke(api, server);
        }
        if (spooferGetFakePlayerCountMethod != null) return spooferGetFakePlayerCountMethod.invoke(api);
        if (spooferGetFakePlayerIdsMethod != null) return spooferGetFakePlayerIdsMethod.invoke(api);
        if (spooferGetFakePlayersMethod != null) return spooferGetFakePlayersMethod.invoke(api);
        return null;
    }

    private Object getSpooferApi() {
        if (spooferApiChecked) {
            return spooferApi;
        }

        spooferApiChecked = true;
        try {
            Object api = null;
            Class<?> apiType = null;
            for (String className : List.of(
                "com.halos.spoofer.api.spigot.SpigotSpooferAPI",
                "com.halos.spoofer.api.SpooferAPI",
                "pl.halos.spoofer.api.spigot.SpigotSpooferAPI",
                "pl.halos.spoofer.api.SpooferAPI"
            )) {
                try {
                    apiType = Class.forName(className);
                    Method getMethod = findNoArgMethod(apiType, "get", "getInstance", "api");
                    if (getMethod != null) {
                        api = getMethod.invoke(null);
                    }
                    if (api != null) {
                        break;
                    }
                } catch (ReflectiveOperationException | LinkageError ignored) {
                    api = null;
                    apiType = null;
                }
            }

            if (api == null) {
                org.bukkit.plugin.Plugin pluginInstance = Bukkit.getPluginManager().getPlugin("HollySpoofer");
                if (pluginInstance == null) pluginInstance = Bukkit.getPluginManager().getPlugin("HalosPlayerSpoofer");
                if (pluginInstance == null) pluginInstance = Bukkit.getPluginManager().getPlugin("PlayerSpoofer");
                api = pluginInstance;
                apiType = api == null ? null : api.getClass();
            }
            if (api == null || apiType == null) {
                return null;
            }

            spooferIsFakePlayerMethod = findMethod(apiType, "isFakePlayer", Player.class);
            spooferGetFakePlayerCountMethod = findNoArgMethod(apiType, "getFakePlayerCount", "getFakePlayersCount", "getOnlineFakePlayersCount");
            spooferGetFakePlayerIdsMethod = findNoArgMethod(apiType, "getFakePlayerIds", "getFakePlayersIds");
            spooferGetFakePlayersMethod = findNoArgMethod(apiType, "getFakePlayers", "getOnlineFakePlayers");
            spooferGetFakePlayerCountServerMethod = findMethod(apiType, "getFakePlayerCount", String.class);
            if (spooferGetFakePlayerCountServerMethod == null) spooferGetFakePlayerCountServerMethod = findMethod(apiType, "getFakePlayersCount", String.class);
            spooferGetFakePlayerIdsServerMethod = findMethod(apiType, "getFakePlayerIds", String.class);
            spooferGetFakePlayersServerMethod = findMethod(apiType, "getFakePlayers", String.class);
            spooferGetLocalServerNameMethod = findNoArgMethod(apiType, "getLocalServerName", "getServerName");
            spooferGetFakeBotHelmetMethod = findMethod(apiType, "getFakeBotHelmet", String.class);
            spooferGetFakeBotChestplateMethod = findMethod(apiType, "getFakeBotChestplate", String.class);
            spooferGetFakeBotLeggingsMethod = findMethod(apiType, "getFakeBotLeggings", String.class);
            spooferGetFakeBotBootsMethod = findMethod(apiType, "getFakeBotBoots", String.class);
            spooferGetFakeBotWeaponMethod = findMethod(apiType, "getFakeBotWeapon", String.class);
            spooferGetFakeBotUuidMethod = findMethod(apiType, "getFakeBotUuid", String.class);
            spooferGetFakeBotLuckPermsGroupMethod = findMethod(apiType, "getFakeBotLuckPermsGroup", String.class);
            spooferGetFakeBotTabPriorityMethod = findMethod(apiType, "getFakeBotTabPriority", String.class);
            spooferGetFakeBotTabDataMapMethod = findNoArgMethod(apiType, "getFakeBotTabDataMap");
            spooferApi = api;
            return spooferApi;
        } catch (LinkageError | RuntimeException ignored) {
            spooferApi = null;
            return null;
        }
    }

    private Method findNoArgMethod(Class<?> type, String... names) {
        for (String name : names) {
            Method method = findMethod(type, name);
            if (method != null) return method;
        }
        return null;
    }

    private Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        try {
            return type.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }


    private String parseHollyPlaceholders(Player player, String text) {
        Matcher matcher = HOLLY_PLACEHOLDER_PATTERN.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String value = plugin.getHollyPlaceholderExpansion().onRequest(player, matcher.group(1));
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(value != null ? value : matcher.group()));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String normalizeCorePlaceholders(Player player, String text) {
        if (text == null || text.isEmpty() || text.indexOf('%') < 0) {
            return text == null ? "" : text;
        }

        String parsed = text
            .replace("%core_vpln%", "%portfel_money%")
            .replace("%core_format%", getIdleAfkMarker(player))
            .replace("%core_afk%", getIdleAfkMarker(player))
            .replace("%core_prefix%", "");
        parsed = parsed.replace("%core_", "%holly_");
        parsed = parsed.replace("%hollyservice_", "%holly_");
        parsed = parsed.replace("%service_", "%holly_");
        return parsed;
    }

    public String getIdleAfkMarker(Player player) {
        if (!idleAfkEnabled || player == null) {
            return "";
        }
        return idleAfkPlayers.contains(player.getUniqueId()) ? idleAfkMarker : "";
    }

    /** {token, "%holly_token%"} — kolejnosc jak dawniej; bez tego kazdy wiersz alokowal 25 stringow. */
    private static final String[][] COMMON_HOLLY_TOKENS = {
        {"joininfo", "%holly_joininfo%"},
        {"join_info", "%holly_join_info%"},
        {"joininfo_raw", "%holly_joininfo_raw%"},
        {"join_info_raw", "%holly_join_info_raw%"},
        {"joinnumber", "%holly_joinnumber%"},
        {"join_number", "%holly_join_number%"},
        {"joininfo_total", "%holly_joininfo_total%"},
        {"join_total", "%holly_join_total%"},
        {"money", "%holly_money%"},
        {"balance", "%holly_balance%"},
        {"points", "%holly_points%"},
        {"ranking", "%holly_ranking%"},
        {"position", "%holly_position%"},
        {"rank", "%holly_rank%"},
        {"kills", "%holly_kills%"},
        {"deaths", "%holly_deaths%"},
        {"level", "%holly_level%"},
        {"clan", "%holly_clan%"},
        {"clan_points", "%holly_clan_points%"},
        {"clan_position", "%holly_clan_position%"},
        {"clan_kills", "%holly_clan_kills%"},
        {"clan_deaths", "%holly_clan_deaths%"},
        {"clan_members", "%holly_clan_members%"},
        {"clan_online", "%holly_clan_online%"},
        {"sector", "%holly_sector%"}
    };

    private String parseCommonHollyPlaceholders(Player player, String text) {
        if (player == null || text == null || text.isEmpty() || plugin.getHollyPlaceholderExpansion() == null) {
            return text;
        }

        String parsed = text;
        for (String[] token : COMMON_HOLLY_TOKENS) {
            parsed = replaceHollyToken(player, parsed, token[0], token[1]);
        }
        return parsed;
    }

    private String parseLocalWalletPlaceholders(Player player, String text) {
        if (text == null || text.isEmpty() || !text.contains("%portfel_")) {
            return text;
        }
        if (player == null || plugin.getWalletManager() == null) {
            return text.replace("%portfel_money%", "0.00").replace("%portfel_balance%", "0.00");
        }
        String value = String.format(java.util.Locale.US, "%.2f", plugin.getWalletManager().getCachedBalance(player.getUniqueId()));
        return text.replace("%portfel_money%", value).replace("%portfel_balance%", value);
    }

    private String replaceHollyToken(Player player, String text, String token, String placeholder) {
        if (!text.contains(placeholder)) {
            return text;
        }
        String value = plugin.getHollyPlaceholderExpansion().onRequest(player, token);
        return text.replace(placeholder, value != null ? value : "");
    }

    private Component toComponent(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        if (autoShadowEnabled) {
            return ColorUtils.colorizeComponentWithShadow(text, autoShadowColor);
        }
        return ColorUtils.colorizeComponent(text);
    }

    private void recreateAll() {
        clearAll();
        for (Player player : Bukkit.getOnlinePlayers()) {
            updatePlayer(player, true);
        }
    }

    private void clearAll() {
        if (isPacketEventsReady()) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                clearPlayerRows(player, true);
                restoreRealPlayersInTab(player);
            }
        }
        tablists.clear();
        dirtyPlayers.clear();
        dirtyHeaderFooters.clear();
        hiddenRealPlayersByViewer.clear();
        lastHeaderFooters.clear();
        lastIdleActivity.clear();
        idleAfkPlayers.clear();
    }

    private void clearPlayerRows(Player player) {
        clearPlayerRows(player, true);
    }

    private void clearPlayerRows(Player player, boolean force) {
        if (player == null || !player.isOnline() || !isPacketEventsReady()) {
            return;
        }

        PlayerTablist tablist = tablists.remove(player.getUniqueId());
        if (tablist == null && !force) {
            return;
        }

        List<UUID> ids = tablist != null ? tablist.profileIds : createRowUuids(player.getUniqueId());
        lastPlayerListOrders.keySet().removeAll(ids);

        clearLegacySlotSortTeams(player, tablist != null ? tablist.profiles.size() : getConfiguredRowCount());

        // Force jest używany przy reload/shutdown, bo klient może dalej trzymać stare fake wpisy
        // po przeładowaniu pluginu. W zwykłym ticku nie wysyłamy pustych remove packetów.
        sendPacket(player, new WrapperPlayServerPlayerInfoRemove(ids));
    }

    private void loadConfig() {
        ensureConfigFile();

        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        String root = config.isConfigurationSection("tab") ? "tab." : "";
        addMissingToggleDefaults(config, root);
        rankNameColors = loadRankNameColors(config, root);

        enabled = getBoolean(config, true, root + "enabled", "enabled");
        papiEnabled = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
        luckPerms = resolveLuckPerms();
        luckPermsEnabled = luckPerms != null;
        luckPermsIdentityCache.clear();
        rankNameColorCache.clear();
        groupNameColorCache.clear();
        tabListDisplayCache.clear();
        relationalTabDisplayCache.clear();
        lastHeaderFooters.clear();
        sharedHeaderFooterDirty = true;
        cachedSharedHeader = null;
        cachedSharedFooter = null;
        lastOnlineCountForHeaderRefresh = -1;
        customTabEnabled = getBoolean(config, true, root + "custom-tab-enabled", root + "customTabEnabled", "customTabEnabled", "custom-tab-enabled");
        headerFooterEnabled = getBoolean(config, true, root + "header-footer-enabled", root + "customTabHeaderFooterEnabled", "customTabHeaderFooterEnabled", "header-footer-enabled");
        updateIntervalTicks = Math.max(60L, getSecondsAsTicks(config, 100L, // OPTYMALIZACJA: było 60L -> 100L (5s zamiast 3s)
            root + "update-seconds", "update-seconds", root + "updateInterval", root + "update-interval-ticks", "updateInterval", "update-interval-ticks"));
        headerFooterIntervalTicks = Math.max(60L, getLong(config, 120L, root + "headerFooterInterval", root + "header-footer-interval-ticks", "headerFooterInterval", "header-footer-interval-ticks")); // OPTYMALIZACJA: było 100L -> 120L
        identityUpdateIntervalTicks = Math.max(40L, getLong(config, 200L, root + "identityUpdateInterval", root + "identity-update-interval-ticks", "identityUpdateInterval", "identity-update-interval-ticks"));
        fullRowUpdateIntervalTicks = Math.max(40L, getLong(config, 200L, root + "fullRowUpdateInterval", root + "full-row-update-interval-ticks", "fullRowUpdateInterval", "full-row-update-interval-ticks"));
        maxAdaptiveFullRowUpdateIntervalTicks = Math.max(
            fullRowUpdateIntervalTicks,
            getLong(config, 600L, root + "adaptive-max-ticks", root + "maxAdaptiveFullRowUpdateInterval", root + "max-adaptive-full-row-update-interval-ticks",
                "adaptive-max-ticks", "maxAdaptiveFullRowUpdateInterval", "max-adaptive-full-row-update-interval-ticks")
        );
        rowRenderCacheMillis = Math.max(0L, getLong(config, 1000L, root + "rowRenderCacheMillis", root + "row-render-cache-millis", "rowRenderCacheMillis", "row-render-cache-millis"));
        adaptiveFullRowUpdateIntervalTicks = fullRowUpdateIntervalTicks;
        adaptiveRefresh = getBoolean(config, true, root + "adaptive-refresh", "adaptive-refresh");
        updateRowsOnMove = getBoolean(config, false, root + "updateRowsOnMove", root + "update-rows-on-move", "updateRowsOnMove", "update-rows-on-move");
        updateIdentityOnEveryTabTick = getBoolean(config, false, root + "updateIdentityOnEveryTabTick", root + "update-identity-on-every-tab-tick", "updateIdentityOnEveryTabTick", "update-identity-on-every-tab-tick");
        periodicIdentityRefresh = getBoolean(config, true, root + "periodicIdentityRefresh", root + "periodic-identity-refresh", "periodicIdentityRefresh", "periodic-identity-refresh");
        fillEmptyRows = getBoolean(config, false, root + "fill-empty-rows", "fill-empty-rows");
        autoShadowEnabled = getBoolean(config, true, root + "auto-shadow.enabled", "auto-shadow.enabled");
        autoShadowColor = getString(config, "#000000C0", root + "auto-shadow.color", "auto-shadow.color");
        sortByLuckPerms = getBoolean(config, true, root + "sort-by-luck-perms", "sort-by-luck-perms");
        unknownGroupWeight = Math.max(0, Math.min(999, (int) getLong(config, DEFAULT_UNKNOWN_GROUP_WEIGHT,
            root + "sorting.unknown-group-weight", "sorting.unknown-group-weight")));
        fallbackGroup = getString(config, DEFAULT_FALLBACK_GROUP,
            root + "sorting.fallback-group", "sorting.fallback-group").trim().toLowerCase(java.util.Locale.ROOT);
        if (fallbackGroup.isBlank()) {
            fallbackGroup = DEFAULT_FALLBACK_GROUP;
        }
        playersPerTick = Math.max(1, Math.min(64, (int) getLong(config, 10L, root + "update-batch-size", "update-batch-size", root + "players-per-tick", "players-per-tick")));
        identityPlayersPerTick = Math.max(1, Math.min(32, (int) getLong(config, Math.min(12, playersPerTick),
            root + "name-tag-update-batch-size", "name-tag-update-batch-size")));
        lpcGroupOrder = buildLpcGroupOrder(config, root);
        header = List.copyOf(config.getStringList(root + "header").isEmpty() && !root.isEmpty() ? config.getStringList("header") : config.getStringList(root + "header"));
        footer = List.copyOf(config.getStringList(root + "footer").isEmpty() && !root.isEmpty() ? config.getStringList("footer") : config.getStringList(root + "footer"));
        List<String> configuredRows = config.getStringList(root + "rows");
        if (configuredRows.isEmpty() && !root.isEmpty()) {
            configuredRows = config.getStringList("rows");
        }
        rows = List.copyOf(configuredRows.subList(0, Math.min(configuredRows.size(), MAX_ROWS)));

        // Wczytaj konfigurację animacji
        animationEnabled = getBoolean(config, false, root + "animation.enabled", "animation.enabled");
        animationIntervalMillis = Math.max(1000L, getSecondsAsTicks(config, 10L,
            root + "animation.interval-seconds", "animation.interval-seconds") * 50L);
        animationPageNames = List.copyOf(config.getStringList(root + "animation.pages"));

        if (animationEnabled && !animationPageNames.isEmpty()) {
            Map<String, List<String>> pages = new HashMap<>();
            for (String pageName : animationPageNames) {
                List<String> pageRows = config.getStringList(root + pageName);
                if (pageRows.isEmpty() && !root.isEmpty()) {
                    pageRows = config.getStringList(pageName);
                }
                if (!pageRows.isEmpty()) {
                    pages.put(pageName, List.copyOf(pageRows.subList(0, Math.min(pageRows.size(), MAX_ROWS))));
                }
            }
            animationPages = Map.copyOf(pages);
            currentAnimationPage = 0;
            nextAnimationPageChangeMillis = System.currentTimeMillis() + animationIntervalMillis;
        } else {
            animationPages = Map.of();
            currentAnimationPage = 0;
        }

        tabPrefix = getString(config, "", root + "tabprefix", "tabprefix");
        tabSuffix = getString(config, "", root + "tabsuffix", "tabsuffix");
        tagPrefix = getString(config, "", root + "tagprefix", "tagprefix");
        tagSuffix = getString(config, "", root + "tagsuffix", "tagsuffix");
        playerNameFormat = normalizeIdentityFormat(getString(config, "", root + "player-name", "player-name"));
        nameTagFormat = normalizeIdentityFormat(getString(config, "", root + "name-tag", "name-tag"));

        defaultTextures = loadDefaultTextures(config, root);
        loadIdleAfkConfig(config);
        prepareStaticTemplates();
        if (plugin.getHollyPlaceholderExpansion() != null) {
            plugin.getHollyPlaceholderExpansion().clearTabTopCache();
        }
        long now = System.currentTimeMillis();
        nextIdentityFullUpdateMillis = now + identityUpdateIntervalTicks * 50L;
        nextFullRowUpdateMillis = now + fullRowUpdateIntervalTicks * 50L;
        nextHeaderFooterUpdateMillis = now + headerFooterIntervalTicks * 50L;
        nextIdleAfkCheckMillis = now + 1_000L;
    }

    private void registerLuckPermsSubscription() {
        closeLuckPermsSubscription();
        if (!enabled || luckPerms == null) {
            return;
        }
        try {
            luckPermsSubscriptions.add(luckPerms.getEventBus().subscribe(
                plugin,
                UserDataRecalculateEvent.class,
                event -> refreshLuckPermsIdentity(event.getUser().getUniqueId())
            ));
            luckPermsSubscriptions.add(luckPerms.getEventBus().subscribe(
                plugin,
                NodeMutateEvent.class,
                event -> {
                    if (event.isUser() && event.getTarget() instanceof User user) {
                        refreshLuckPermsIdentity(user.getUniqueId());
                    } else if (event.isGroup()) {
                        refreshAllIdentities();
                    }
                }
            ));
            luckPermsSubscriptions.add(luckPerms.getEventBus().subscribe(
                plugin,
                GroupDataRecalculateEvent.class,
                event -> refreshAllIdentities()
            ));
        } catch (Throwable ignored) {
        }
    }

    private void closeLuckPermsSubscription() {
        for (EventSubscription<?> sub : luckPermsSubscriptions) {
            try {
                sub.close();
            } catch (Throwable ignored) {
            }
        }
        luckPermsSubscriptions.clear();
        if (luckPermsUserDataSubscription != null) {
            try {
                luckPermsUserDataSubscription.close();
            } catch (Throwable ignored) {
            }
            luckPermsUserDataSubscription = null;
        }
    }

    private PrefixSuffix resolveNameTag(Player viewer, Player target) {
        IncognitoManager incognito = plugin.getIncognitoManager();
        boolean hidden = incognito != null && incognito.isIncognito(target);
        if (!nametagUsesRelational && !hidden) {
            PrefixSuffix cached = nametagCacheByTarget.get(target.getUniqueId());
            if (cached != null && (System.currentTimeMillis() - cached.createdAt() < NAMETAG_CACHE_TTL_MS)) {
                return applyIncognitoNameTag(viewer, target, cached);
            }
        }

        String nameColor = getRankNameColor(target);

        String rawFormat = (nameTagFormat == null || nameTagFormat.isBlank())
            ? "%luckperms_prefix%%name_color%%player_name%"
            : nameTagFormat;

        String template = rawFormat;
        if (template.contains("%name_color%")) {
            template = template.replace("%name_color%", nameColor);
        } else if (template.contains("%rank_name_color%")) {
            template = template.replace("%rank_name_color%", nameColor);
        } else if (template.contains("%core_name_color%")) {
            template = template.replace("%core_name_color%", nameColor);
        } else if (template.contains("%holly_name_color%")) {
            template = template.replace("%holly_name_color%", nameColor);
        } else if (template.contains("%holly_rank_name_color%")) {
            template = template.replace("%holly_rank_name_color%", nameColor);
        }

        NamePlaceholderSpan span = findNamePlaceholderSpan(template);
        if (span == null) {
            return applyIncognitoNameTag(viewer, target, cacheNameTag(target, hidden,
                new PrefixSuffix(parseIdentityPlaceholders(viewer, target, template), "")));
        }

        String prefixTemplate = template.substring(0, span.start());
        String suffixTemplate = template.substring(span.end());

        if (!rawFormat.contains("%name_color%") && !rawFormat.contains("%rank_name_color%")
            && !rawFormat.contains("%core_name_color%") && !rawFormat.contains("%holly_name_color%")
            && !rawFormat.contains("%holly_rank_name_color%")) {
            prefixTemplate = prefixTemplate + nameColor;
        }

        return applyIncognitoNameTag(viewer, target, cacheNameTag(target, hidden, new PrefixSuffix(
            parseIdentityPlaceholders(viewer, target, prefixTemplate),
            parseIdentityPlaceholders(viewer, target, suffixTemplate)
        )));
    }

    private PrefixSuffix applyIncognitoNameTag(Player viewer, Player target, PrefixSuffix nameTag) {
        IncognitoManager incognito = plugin.getIncognitoManager();
        if (incognito == null || !incognito.isIncognito(target) || nameTag == null) {
            return nameTag;
        }
        return new PrefixSuffix("", incognito.nametagSuffix(viewer, target));
    }

    private PrefixSuffix cacheNameTag(Player target, boolean hidden, PrefixSuffix nameTag) {
        if (!nametagUsesRelational && !hidden && target != null && shouldCacheRealTagSnapshot(target)) {
            nametagCacheByTarget.put(target.getUniqueId(), nameTag);
        }
        return nameTag;
    }

    private NamePlaceholderSpan findNamePlaceholderSpan(String template) {
        NamePlaceholderSpan best = null;
        for (String placeholder : List.of("%player_name%", "%holly_nick%", "%holly_name%", "%core_nick%", "%core_name%", "%core_player_name%")) {
            int index = template.indexOf(placeholder);
            if (index < 0) {
                continue;
            }
            if (best == null || index < best.start()) {
                best = new NamePlaceholderSpan(index, index + placeholder.length());
            }
        }
        return best;
    }

    private LuckPerms resolveLuckPerms() {
        if (!Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            return null;
        }
        try {
            return LuckPermsProvider.get();
        } catch (RuntimeException | LinkageError exception) {
            return null;
        }
    }

    private void prepareStaticTemplates() {
        staticColorCache.clear();
        rowsStatic = rows.stream().noneMatch(this::hasPlaceholder);
        rowsUseOnlineCount = usesOnlineCountPlaceholder(rows);
        leaderboardRowIndices = findLeaderboardRowIndices();
        onlineCountRowIndices = findOnlineCountRowIndices();
        headerStatic = header.stream().noneMatch(this::hasPlaceholder);
        footerStatic = footer.stream().noneMatch(this::hasPlaceholder);
        headerFooterUsesOnlineCount = usesOnlineCountPlaceholder(header) || usesOnlineCountPlaceholder(footer);
        headerFooterNeedsPeriodicRefresh = needsPeriodicHeaderFooterRefresh(header)
            || needsPeriodicHeaderFooterRefresh(footer);
        headerFooterSharedOnly = !headerStatic
            && !footerStatic
            && !hasPlayerSpecificHeaderFooterPlaceholders(header)
            && !hasPlayerSpecificHeaderFooterPlaceholders(footer);
        nametagUsesRelational = nameTagFormat != null && nameTagFormat.contains("%rel_");
        tabNameUsesRelational = playerNameFormat != null && playerNameFormat.contains("%rel_");
        relationalTabDisplayCache.clear();
        // %rel_holly_clan_tag_rel% u botów ≈ zawsze pusto — nie rób O(boty×widzowie).
        // Inne %rel_ / %bungee_ / %goxy_ nadal per widz.
        fakeNameTagViewerDependent = isFakeFormatViewerDependent(nameTagFormat);
        fakeTabDisplayViewerDependent = isFakeFormatViewerDependent(playerNameFormat);
        nametagCacheByTarget.clear();
        realTagSnapshots.clear();
        invalidateFakeTagSnapshots();

        if (rowsStatic) {
            staticRenderedRows = new String[Math.min(rows.size(), MAX_ROWS)];
            for (int i = 0; i < staticRenderedRows.length; i++) {
                staticRenderedRows[i] = renderLine(null, rows.get(i));
            }
        } else {
            staticRenderedRows = new String[0];
        }

        staticRenderedHeader = headerStatic ? renderMultiline(null, header) : null;
        staticRenderedFooter = footerStatic ? renderMultiline(null, footer) : null;
    }

    private int[] findLeaderboardRowIndices() {
        List<Integer> indices = new ArrayList<>();
        int limit = Math.min(rows.size(), MAX_ROWS);
        for (int i = 0; i < limit; i++) {
            String row = rows.get(i);
            if (row != null && (row.contains("%holly_tabtop") || row.contains("%holly_top_")
                || row.contains("%core_tabtop") || row.contains("%core_top_"))) {
                indices.add(i);
            }
        }
        int[] result = new int[indices.size()];
        for (int i = 0; i < indices.size(); i++) {
            result[i] = indices.get(i);
        }
        return result;
    }

    private int[] findOnlineCountRowIndices() {
        List<Integer> indices = new ArrayList<>();
        int limit = Math.min(rows.size(), MAX_ROWS);
        for (int i = 0; i < limit; i++) {
            String row = rows.get(i);
            if (row != null && usesOnlineCountPlaceholder(List.of(row))) {
                indices.add(i);
            }
        }
        int[] result = new int[indices.size()];
        for (int i = 0; i < indices.size(); i++) {
            result[i] = indices.get(i);
        }
        return result;
    }

    private boolean needsPeriodicHeaderFooterRefresh(List<String> lines) {
        for (String line : lines) {
            Matcher matcher = PLACEHOLDER_TOKEN_PATTERN.matcher(line == null ? "" : line);
            while (matcher.find()) {
                if (!EVENT_DRIVEN_HEADER_FOOTER_PLACEHOLDERS.contains(matcher.group())) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasPlayerSpecificHeaderFooterPlaceholders(List<String> lines) {
        for (String line : lines) {
            Matcher matcher = PLACEHOLDER_TOKEN_PATTERN.matcher(line == null ? "" : line);
            while (matcher.find()) {
                String token = matcher.group();
                if (EVENT_DRIVEN_HEADER_FOOTER_PLACEHOLDERS.contains(token)) {
                    return true;
                }
                if (token.startsWith("%player_") || token.contains("player_ping")) {
                    return true;
                }
                if (token.startsWith("%core_") || token.startsWith("%holly_")
                    || token.startsWith("%hollyservice_") || token.startsWith("%service_")
                    || token.startsWith("%portfel_")) {
                    return true;
                }
                if (token.startsWith("%rel_")) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean usesOnlineCountPlaceholder(List<String> lines) {
        for (String line : lines) {
            if (line != null && (line.contains("%goxy_network_online%")
                || line.contains("%goxy_server_")
                || line.contains("%bungee_all%")
                || line.contains("%bungee_total%")
                || line.contains("%bungee_online%")
                || line.contains("%bungee_"))) {
                return true;
            }
        }
        return false;
    }

    private Map<String, String> loadRankNameColors(YamlConfiguration config, String root) {
        ConfigurationSection section = config.getConfigurationSection(root + "rank-name-colors");
        if (section == null) {
            section = config.getConfigurationSection("rank-name-colors");
        }
        if (section == null) {
            return Map.of();
        }
        Map<String, String> colors = new java.util.LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            String value = section.getString(key);
            if (value != null && !value.isBlank()) {
                colors.put(key.toLowerCase(java.util.Locale.ROOT), value);
            }
        }
        return colors.isEmpty() ? Map.of() : Map.copyOf(colors);
    }

    private void addMissingToggleDefaults(YamlConfiguration config, String root) {
        boolean changed = false;

        for (String removedPath : List.of("sort-by-lpc", "sortByLpcRank", "lpc-tab-sorting")) {
            if (config.contains(removedPath)) {
                config.set(removedPath, null);
                changed = true;
            }
        }

        // rank-name-colors NIGDY nie kasować — to źródło kolorów nicków w TAB/nametag/chat.
        String colorsPrefix = (root != null && !root.isBlank()) ? root + "rank-name-colors" : "rank-name-colors";
        if (!config.isConfigurationSection(colorsPrefix) && !config.isConfigurationSection("rank-name-colors")) {
            for (Map.Entry<String, String> entry : DEFAULT_RANK_NAME_COLORS.entrySet()) {
                config.set(colorsPrefix + "." + entry.getKey(), entry.getValue());
            }
            changed = true;
        }

        String rankOrderPrefix = (root != null && !root.isBlank()) ? root + "rank-order" : "rank-order";
        if (!config.contains(rankOrderPrefix) && !config.contains("rank-order")) {
            config.set(rankOrderPrefix, DEFAULT_RANK_ORDER);
            changed = true;
        }

        if (changed) {
            try {
                config.save(configFile);
            } catch (IOException exception) {
                plugin.getLogger().warning("Nie udalo sie zapisac domyslnych ustawien tablisty: " + exception.getMessage());
            }
        }
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

    private long getSecondsAsTicks(YamlConfiguration config, long defTicks, String secondsPath, String legacySecondsPath, String... tickPaths) {
        for (String path : List.of(secondsPath, legacySecondsPath)) {
            if (config.contains(path)) {
                return config.getLong(path, defTicks / 20L) * 20L;
            }
        }
        return getLong(config, defTicks, tickPaths);
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

    private List<TextureProperty> loadDefaultTextures(YamlConfiguration config, String root) {
        String value = firstString(config,
            root + "custom-tab-skin.value",
            root + "default-skin.texture-value",
            root + "default-skin.value",
            "custom-tab-skin.value",
            "default-skin.texture-value",
            "default-skin.value"
        );
        String signature = firstString(config,
            root + "custom-tab-skin.signature",
            root + "default-skin.texture-signature",
            root + "default-skin.signature",
            "custom-tab-skin.signature",
            "default-skin.texture-signature",
            "default-skin.signature"
        );
        if (value == null || value.isBlank()) {
            return Collections.emptyList();
        }
        return List.of(new TextureProperty("textures", value, signature == null || signature.isBlank() ? null : signature));
    }

    private void loadIdleAfkConfig(YamlConfiguration config) {
        // Znacznik AFK przy nicku wylaczony na stale — nie doklejamy nic do nicku.
        idleAfkEnabled = false;
        idleAfkPlayers.clear();
        lastIdleActivity.clear();
        idleAfkAfterMillis = Math.max(1L, getLong(config, 120L, "idle-afk.after-seconds")) * 1000L;
        idleAfkResetOnInteract = getBoolean(config, true, "idle-afk.reset-on-interact");
        idleAfkResetOnChat = getBoolean(config, true, "idle-afk.reset-on-chat");
        idleAfkResetOnCommand = getBoolean(config, true, "idle-afk.reset-on-command");
        idleAfkMarker = "";
    }

    private String firstString(YamlConfiguration config, String... paths) {
        for (String path : paths) {
            String value = config.getString(path);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
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

    private boolean isPacketEventsReady() {
        return PacketEvents.getAPI() != null && PacketEvents.getAPI().isLoaded();
    }

    private void sendPacket(Player player, PacketWrapper<?> packet) {
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
    }

    /**
     * {@code listOrder} istnieje od protokołu 768 (1.21.2). Na 1.20 / 1.21.0–1.21.1
     * akcja UPDATE_LIST_ORDER psuje PLAYER_INFO_UPDATE albo ViaVersion zrzuca cały pakiet.
     */
    private boolean viewerSupportsListOrder(Player player) {
        if (player == null || !isPacketEventsReady()) {
            return false;
        }
        try {
            com.github.retrooper.packetevents.protocol.player.User user =
                PacketEvents.getAPI().getPlayerManager().getUser(player);
            if (user != null && user.getClientVersion() != null) {
                return clientSupportsListOrder(user.getClientVersion());
            }
        } catch (RuntimeException | LinkageError ignored) {
        }
        // Nieznany klient (ViaVersion jeszcze nie gotowy) — teamy hs###, nie listOrder.
        return false;
    }

    private boolean serverSupportsListOrder() {
        if (!isPacketEventsReady()) {
            return false;
        }
        try {
            return PacketEvents.getAPI().getServerManager().getVersion().getProtocolVersion() >= LIST_ORDER_PROTOCOL;
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private static boolean clientSupportsListOrder(ClientVersion version) {
        if (version == null) {
            return false;
        }
        try {
            return version.getProtocolVersion() >= LIST_ORDER_PROTOCOL;
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private void sendPlayerInfoUpdate(
        Player viewer,
        EnumSet<WrapperPlayServerPlayerInfoUpdate.Action> modernActions,
        EnumSet<WrapperPlayServerPlayerInfoUpdate.Action> legacyActions,
        List<WrapperPlayServerPlayerInfoUpdate.PlayerInfo> entries
    ) {
        if (viewer == null || !viewer.isOnline() || entries == null || entries.isEmpty() || !isPacketEventsReady()) {
            return;
        }
        EnumSet<WrapperPlayServerPlayerInfoUpdate.Action> actions =
            serverSupportsListOrder() && viewerSupportsListOrder(viewer) ? modernActions : legacyActions;
        if (actions == null || actions.isEmpty()) {
            return;
        }
        sendPacket(viewer, new WrapperPlayServerPlayerInfoUpdate(actions, entries));
    }

    /**
     * 1.20 sortuje TAB po nazwie teamu, nie po listOrder. Bez teamu sloty HCTAB
     * lądują na górze (pusta nazwa teamu) i zasłaniają rangi. {@code hs000+} jest
     * alfabetycznie po {@code h999}, czyli pod staffem — jak listOrder=0 na 1.21.2+.
     */
    private void applyLegacySlotSortTeams(Player viewer, PlayerTablist tablist) {
        if (viewer == null || tablist == null || !PacketNameTagSender.ready()) {
            return;
        }
        if (serverSupportsListOrder() && viewerSupportsListOrder(viewer)) {
            return;
        }
        int rowCount = tablist.profiles.size();
        for (int i = 0; i < rowCount; i++) {
            UserProfile profile = tablist.profiles.get(i);
            if (profile == null || profile.getName() == null || profile.getName().isBlank()) {
                continue;
            }
            PacketNameTagSender.apply(
                viewer,
                slotSortTeamName(i),
                null,
                Component.empty(),
                Component.empty(),
                NamedTextColor.WHITE,
                profile.getName()
            );
        }
    }

    private void clearLegacySlotSortTeams(Player viewer, int rowCount) {
        if (viewer == null || !PacketNameTagSender.ready()) {
            return;
        }
        int limit = Math.max(0, Math.min(rowCount, MAX_ROWS));
        for (int i = 0; i < limit; i++) {
            PacketNameTagSender.remove(viewer, slotSortTeamName(i));
        }
    }

    private static String slotSortTeamName(int index) {
        return SLOT_SORT_TEAM_PREFIX + String.format("%03d", Math.max(0, Math.min(999, index)));
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onAsyncPreLogin(AsyncPlayerPreLoginEvent event) {
        if (!enabled) {
            return;
        }
        // Odrzucone połączenia (ban/whitelist/auth) nie mogą zostawiać wpisów w mapach ani ładować LP.
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        // Seed na dół listy zanim klient dostanie właściwą rangę.
        forceBottomListOrder(event.getUniqueId());
        if (!luckPermsEnabled || luckPerms == null) {
            return;
        }
        try {
            luckPerms.getUserManager().loadUser(event.getUniqueId()).join();
        } catch (RuntimeException | LinkageError ignored) {
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerLogin(PlayerLoginEvent event) {
        if (!enabled || event.getResult() != PlayerLoginEvent.Result.ALLOWED) {
            return;
        }
        Player player = event.getPlayer();
        setPlayerListOrder(player, toClientListOrder(unknownGroupWeight));
    }

    /** Login odrzucony po naszym seedzie — sprzątamy bookkeeping UUID-a, który nigdy nie wejdzie na serwer. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerLoginMonitor(PlayerLoginEvent event) {
        if (event.getResult() == PlayerLoginEvent.Result.ALLOWED) {
            return;
        }
        UUID uuid = event.getPlayer().getUniqueId();
        lastPlayerListOrders.remove(uuid);
        pendingListOrderBroadcasts.remove(uuid);
        paperListOrderSynced.remove(uuid);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!enabled) {
            return;
        }
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        lastIdleActivity.put(uuid, System.currentTimeMillis());

        // Paper listOrder lokalnie od razu; broadcast O(n) dopiero +1t (nie na ticku joinu).
        int bottom = toClientListOrder(unknownGroupWeight);
        setPlayerListOrder(player, bottom);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || !enabled) {
                return;
            }
            applyJoinIdentityImmediate(player);
            // Identity kolejkowała listOrder — jeden flush na falę joinów.
            scheduleCoalescedListOrderFlush();
            if (!isJoinIdentityComplete(player)) {
                scheduleJoinIdentityRetries(player);
            }
            if (customTabEnabled) {
                ensureRealPlayersListedOnJoin(player);
            }
        }, 1L);

        scheduleOnlineCountRefresh();
    }

    private boolean isJoinIdentityComplete(Player player) {
        if (player == null) {
            return true;
        }
        UUID uuid = player.getUniqueId();
        if (!luckPermsEnabled) {
            return realTagSnapshots.containsKey(uuid);
        }
        return hasResolvedLuckPermsIdentity(uuid) && realTagSnapshots.containsKey(uuid);
    }

    private void scheduleJoinIdentityRetries(Player player) {
        for (long delayTicks : JOIN_IDENTITY_RETRY_TICKS) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> refreshJoinedPlayerIdentity(player), delayTicks);
        }
    }

    private void refreshJoinedPlayerIdentity(Player player) {
        if (!enabled || player == null || !player.isOnline()) {
            return;
        }
        if (isJoinIdentityComplete(player)) {
            return;
        }
        UUID uuid = player.getUniqueId();
        luckPermsIdentityCache.remove(uuid);
        nametagCacheByTarget.remove(uuid);
        realTagSnapshots.remove(uuid);
        tabOrderDirty = true;
        ensureLuckPermsUserLoaded(player);
        getLuckPermsIdentityData(player);
        updatePlayerTabName(player);
        queueJoinNametagWork(player);
        if (sortByLuckPerms && hasResolvedLuckPermsIdentity(uuid)) {
            int order = resolveTabListOrder(player);
            setPlayerListOrder(player, order);
            broadcastListOrder(uuid, order);
        }
    }

    private void scheduleReapplyViewerNameTags(Player player) {
        if (player == null) {
            return;
        }
        pendingViewerReapply.add(player.getUniqueId());
        if (!viewerReapplyScheduled.compareAndSet(false, true)) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            viewerReapplyScheduled.set(false);
            Set<UUID> ids = Set.copyOf(pendingViewerReapply);
            pendingViewerReapply.clear();
            for (UUID id : ids) {
                Player online = Bukkit.getPlayer(id);
                if (online != null && online.isOnline()) {
                    reapplyViewerNameTags(online);
                }
            }
        }, 5L);
    }

    private void reapplyViewerNameTags(Player player) {
        if (!enabled || player == null || !player.isOnline()) {
            return;
        }
        UUID uuid = player.getUniqueId();
        lastNameTagTeams.remove(uuid);
        lastNameTagSuffixes.remove(uuid);
        lastFakeNameTagTeams.remove(uuid);
        lastFakeNameTagSuffixes.remove(uuid);
        scheduleBatchedNameTagRefresh();
        queueViewerFakeTagResync(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        scheduleReapplyViewerNameTags(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        scheduleReapplyViewerNameTags(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null || event.getFrom().getWorld() == event.getTo().getWorld()) {
            return;
        }
        scheduleReapplyViewerNameTags(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!enabled || event.getTo() == null) {
            return;
        }
        boolean changedBlock = hasChangedBlock(event.getFrom(), event.getTo());
        if (changedBlock) {
            markPlayerActivity(event.getPlayer());
        }
        if (updateRowsOnMove && changedBlock) {
            UUID uuid = event.getPlayer().getUniqueId();
            long now = System.currentTimeMillis();
            Long last = lastMoveDirtyMark.get(uuid);
                if (last == null || now - last >= 1000L) {
                lastMoveDirtyMark.put(uuid, now);
                dirtyPlayers.add(uuid);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (enabled && idleAfkResetOnInteract) {
            markPlayerActivity(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (!enabled || !idleAfkResetOnChat) {
            return;
        }
        Player player = event.getPlayer();
        lastIdleActivity.put(player.getUniqueId(), System.currentTimeMillis());
        if (idleAfkPlayers.remove(player.getUniqueId())) {
            runOnMainThread(() -> markIdleStateChanged(player.getUniqueId()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (enabled && idleAfkResetOnCommand) {
            markPlayerActivity(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (plugin.getIncognitoManager() != null) {
            plugin.getIncognitoManager().handleQuit(event.getPlayer());
        }
        dirtyPlayers.remove(uuid);
        dirtyHeaderFooters.remove(uuid);
        lastMoveDirtyMark.remove(uuid);
        hiddenRealPlayersByViewer.remove(uuid);
        for (Set<UUID> hiddenTargets : hiddenRealPlayersByViewer.values()) {
            hiddenTargets.remove(uuid);
        }
        dirtyIdentities.remove(uuid);
        lastIdleActivity.remove(uuid);
        idleAfkPlayers.remove(uuid);
        PlayerTablist removedTablist = tablists.remove(uuid);
        if (removedTablist != null) {
            // Sloty HCTAB maja UUID per widz — bez tego mapa rosnie o ~71 wpisow na kazdy unikalny join.
            lastPlayerListOrders.keySet().removeAll(removedTablist.profileIds);
        }
        lastPlayerListNames.remove(uuid);
        lastPlayerListOrders.remove(uuid);
        pendingListOrderBroadcasts.remove(uuid);
        paperListOrderSynced.remove(uuid);
        lastHeaderFooters.remove(uuid);
        luckPermsIdentityCache.remove(uuid);
        rankNameColorCache.remove(uuid);
        invalidateTabListDisplay(uuid);
        nametagCacheByTarget.remove(uuid);
        realTagSnapshots.remove(uuid);
        lastNameTagSuffixes.remove(uuid);
        lastNameTagTeams.remove(uuid);
        lastFakeNameTagTeams.remove(uuid);
        lastFakeNameTagSuffixes.remove(uuid);
        lastFakeTabDisplayNames.remove(uuid);
        lastFakeTagVersions.remove(uuid);
        lastFakeTabDisplayVersions.remove(uuid);
        spooferBotsHiddenViewers.remove(uuid);
        for (Map<UUID, String> cache : lastNameTagSuffixes.values()) {
            cache.remove(uuid);
        }
        for (Map<UUID, String> cache : lastNameTagTeams.values()) {
            cache.remove(uuid);
        }
        tabOrderDirty = true;
        pendingJoinNametagWork.remove(uuid);
        pendingJoinNametagIds.remove(uuid);
        pendingListedSync.remove(uuid);
        if (activeListedSyncViewer != null && activeListedSyncViewer.equals(uuid)) {
            activeListedSyncViewer = null;
            activeListedSyncIndex = 0;
        }
        if (activeJoinNametagWork != null && activeJoinNametagWork.joinerId.equals(uuid)) {
            activeJoinNametagWork = null;
        }
        if (fakeNametagViewerCache != null) {
            for (int i = 0; i < fakeNametagViewerCache.length; i++) {
                if (fakeNametagViewerCache[i] != null && fakeNametagViewerCache[i].getUniqueId().equals(uuid)) {
                    fakeNametagViewerCache[i] = null;
                }
            }
        }
        if (nametagViewerCache != null) {
            for (int i = 0; i < nametagViewerCache.length; i++) {
                if (nametagViewerCache[i] != null && nametagViewerCache[i].getUniqueId().equals(uuid)) {
                    nametagViewerCache[i] = null;
                }
            }
        }
        markHeaderFooterDirtyAfterOnlineCountChange();
        queueUpdateAll();
    }

    private boolean hasChangedBlock(Location from, Location to) {
        return from.getWorld() != to.getWorld()
            || from.getBlockX() != to.getBlockX()
            || from.getBlockY() != to.getBlockY()
            || from.getBlockZ() != to.getBlockZ();
    }

    private static final class LuckPermsIdentityData {
        private static final LuckPermsIdentityData EMPTY = new LuckPermsIdentityData("", "", "", "", DEFAULT_UNKNOWN_GROUP_WEIGHT);

        private final String primaryGroup;
        private final String sortGroup;
        private final String prefix;
        private final String suffix;
        private final int tabPriority;

        private LuckPermsIdentityData(String primaryGroup, String sortGroup, String prefix, String suffix, int tabPriority) {
            this.primaryGroup = primaryGroup;
            this.sortGroup = sortGroup;
            this.prefix = prefix;
            this.suffix = suffix;
            this.tabPriority = tabPriority;
        }
    }

    private record EffectiveRank(String primaryGroup, String sortGroup, String prefix, String suffix, int tabPriority) {
    }

    private record HeaderFooterState(String header, String footer) {
        private boolean matches(String header, String footer) {
            return this.header.equals(header) && this.footer.equals(footer);
        }
    }

    private record PrefixSuffix(String prefix, String suffix, long createdAt) {
        public PrefixSuffix(String prefix, String suffix) {
            this(prefix, suffix, System.currentTimeMillis());
        }
    }

    private record NamePlaceholderSpan(int start, int end) {
    }

    private static final class PlayerTablist {
        private final List<UserProfile> profiles = new ArrayList<>(MAX_ROWS);
        private final List<UUID> profileIds = new ArrayList<>(MAX_ROWS);
        private final List<String> lastRows = new ArrayList<>(MAX_ROWS);
        private boolean created;
        private long lastHeaderFooterUpdate;
        private String lastHeader = null;
        private String lastFooter = null;
        private String[] renderedRows;
        private long renderedRowsAt;
    }
}
