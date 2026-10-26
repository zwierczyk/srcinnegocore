package dev.grzybalson.managers;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.TextureProperty;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoRemove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ChatLinkifier;
import dev.grzybalson.utils.ColorUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.security.SecureRandom;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Model Hypestar: nick {@code UKRYTY......}, stały slim skin.
 * Pakiet {@code PLAYER_INFO_UPDATE} z {@code ADD_PLAYER} dostaje nowy nick i tekstury,
 * a encja jest odświeżana {@code hidePlayer}/{@code showPlayer}.
 */
public final class IncognitoManager implements Listener {

    public static final String USE_PERMISSION = "Holly.cmd.incognito";
    public static final String VIEW_PERMISSION = "Holly.cmd.incognito.view";
    public static final long COOLDOWN_MS = 10_000L;

    private static final String NAME_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final EnumSet<WrapperPlayServerPlayerInfoUpdate.Action> PROFILE_ACTIONS = EnumSet.of(
        WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER,
        WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED,
        WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_DISPLAY_NAME,
        WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_GAME_MODE,
        WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LATENCY
    );

    private static final String SKIN_VALUE =
        "ewogICJ0aW1lc3RhbXAiIDogMTYzMDc0MTEwMzg1MywKICAicHJvZmlsZUlkIiA6ICJiNWRkZTVmODJlYjM0OTkzYmMwN2Q0MGFiNWY2ODYyMyIsCiAgInByb2ZpbGVOYW1lIiA6ICJsdXhlbWFuIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzMxZDBmNWI1ZWI0MDY1ZWU0NWVkNjlkZDIxNjA2NDNhNzAzM2QyOGQ1OWIzMTE1ZTBlOGQ0ZGRiMWMzMjNmOSIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9CiAgfQp9";
    private static final String SKIN_SIGNATURE =
        "Pp3i0mOYXiKKYzzsz++DES9j/Py2kbJ5Gf7HVq5DvmPUT3fm34zHPOUUbQEpFfXeAofxAl9JLwDG4hemvSUsIlasRgbeZHrFMzka4/C9SV1dQ7c65vSbj7XhY2unihKj4Il2+WtO12+OzoRJobto8KWkef8/FWw2r8xmzglEWa0dvwqWwIyKouyMG4tF/Nbed2MMmp/M9ezFggskXLM7YGrHx6j5iIHz9aWJtn2x2L6zWgZYTEirK8XUlaC2rEB0YJ0spXfUozcVSbpLJR7WlyCCY/H/8GHwOI9RbYe4nYv1qceTnMXFGUkk0vxyEvy1RTXhysus1M/E6hpMirnHc/kPIOsuKCFwOGzEJlRSPa6TpnwF1Xmwbmity6fOvCxTtycDQIAJc9Ce1Ngxg7sZpPqTJ9F4UPhr3VOS5TbieROibym7N9a3cv4q4xiRfBPhMinG/OUHfkPIvFtmUi2fIw4VrXMO2aYM+oo7oMmXM0SByvFHDEhpsOGvz2c2IXiUO2zB2ZG0RIsH4ynQ3e2P5B4lqesgF1Gaag3OKhxAZiEpTkqE+Ta4lgx0SSLmhAVqZR66iWsfp3xhOIp7wG902UhzsK/NZUYOdiXpx89RhO+IodBpBFan7R2zCfIWU7+gwDiL0VDHExUTNE5ccyNJebzfVZYU58jnBe9oLsNoUp4=";

    private final HollyCore plugin;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cooldownUntil = new ConcurrentHashMap<>();
    private boolean packetListenerRegistered;

    public IncognitoManager(HollyCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        registerPacketListener();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /** TAB + nametag incognito dla jednego widza (join / zmiana permisji). */
    public void refreshForViewer(Player viewer) {
        if (viewer == null || !viewer.isOnline() || sessions.isEmpty()) {
            return;
        }
        boolean listed = plugin.getTablistManager() == null || !plugin.getTablistManager().isCustomTabEnabled();
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.getUniqueId().equals(viewer.getUniqueId()) || !isIncognito(target)) {
                continue;
            }
            sendProfile(viewer, target, listed || isIncognito(target));
        }
        if (plugin.getTablistManager() != null) {
            plugin.getTablistManager().refreshIncognitoForViewer(viewer);
        }
    }

    public boolean isIncognito(UUID uuid) {
        return uuid != null && sessions.containsKey(uuid);
    }

    public boolean isIncognito(Player player) {
        return player != null && isIncognito(player.getUniqueId());
    }

    public String getIncognitoName(UUID uuid) {
        Session session = sessions.get(uuid);
        return session != null ? session.fakeName() : null;
    }

    public String displayName(Player player) {
        if (player == null) {
            return "";
        }
        Session session = sessions.get(player.getUniqueId());
        return session != null ? session.fakeName() : player.getName();
    }

    public String realName(UUID uuid) {
        Session session = sessions.get(uuid);
        if (session != null) {
            return session.realName();
        }
        Player player = Bukkit.getPlayer(uuid);
        return player != null ? player.getName() : null;
    }

    public boolean canSeeRealName(Player viewer, Player target) {
        if (viewer == null || target == null) {
            return false;
        }
        if (viewer.getUniqueId().equals(target.getUniqueId())) {
            return true;
        }
        return hasViewPermission(viewer);
    }

    /**
     * LuckPerms API jest źródłem prawdy (działa też z wątku pakietów).
     * {@code Player#hasPermission} z Netty potrafi spaść do {@code default: op},
     * więc helper bez OP z {@code Holly.cmd.incognito.view} nic nie widział.
     */
    private static boolean hasViewPermission(Player viewer) {
        try {
            var lp = LuckPermsProvider.get();
            var adapter = lp.getPlayerAdapter(Player.class);
            if (adapter != null) {
                if (adapter.getPermissionData(viewer).checkPermission(VIEW_PERMISSION).asBoolean()) {
                    return true;
                }
            } else {
                var user = lp.getUserManager().getUser(viewer.getUniqueId());
                if (user != null && user.getCachedData().getPermissionData().checkPermission(VIEW_PERMISSION).asBoolean()) {
                    return true;
                }
            }
        } catch (RuntimeException | LinkageError ignored) {
        }
        try {
            return viewer.hasPermission(VIEW_PERMISSION);
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    public void broadcastDisguisedChat(Player speaker, String rawMessage) {
        if (speaker == null || rawMessage == null) {
            return;
        }
        String fake = displayName(speaker);
        String real = realName(speaker.getUniqueId());
        Component publicMessage = ChatLinkifier.apply(
            ColorUtils.colorizeWithPlainPlaceholder("&f" + fake + "&7: &f{MESSAGE}", "{MESSAGE}", rawMessage)
        );
        Component staffMessage = real == null || real.isBlank()
            ? publicMessage
            : ChatLinkifier.apply(
                ColorUtils.colorizeWithPlainPlaceholder("&f" + fake + " &e" + real + "&7: &f{MESSAGE}", "{MESSAGE}", rawMessage)
            );
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            IgnoreManager ignore = plugin.getIgnoreManager();
            if (ignore != null && ignore.shouldHideChat(viewer, speaker)) {
                continue;
            }
            viewer.sendMessage(canSeeRealName(viewer, speaker) ? staffMessage : publicMessage);
        }
        Bukkit.getConsoleSender().sendMessage(staffMessage);
    }

    /**
     * TAB: {@code UKRYTYXXXX} i obok żółty prawdziwy nick, jak w Hypestarze.
     */
    public Component tabDisplayName(Player viewer, Player target) {
        if (target == null || !isIncognito(target)) {
            return null;
        }
        String fake = displayName(target);
        Component name = Component.text(fake);
        if (!canSeeRealName(viewer, target)) {
            return name;
        }
        String real = realName(target.getUniqueId());
        if (real == null || real.isBlank()) {
            return name;
        }
        return name.append(Component.text(" " + real, NamedTextColor.YELLOW));
    }

    public String nametagSuffix(Player viewer, Player target) {
        if (!isIncognito(target) || !canSeeRealName(viewer, target)) {
            return "";
        }
        String real = realName(target.getUniqueId());
        return real == null || real.isBlank() ? "" : " &e" + real + " ";
    }

    public long remainingCooldownMs(Player player) {
        Long until = cooldownUntil.get(player.getUniqueId());
        if (until == null) {
            return 0L;
        }
        return Math.max(0L, until - System.currentTimeMillis());
    }

    public boolean toggle(Player player) {
        UUID uuid = player.getUniqueId();
        if (sessions.containsKey(uuid)) {
            disable(player);
            cooldownUntil.put(uuid, System.currentTimeMillis() + COOLDOWN_MS);
            return false;
        }
        sessions.put(uuid, new Session(player.getName(), "UKRYTY" + randomSuffix(6)));
        cooldownUntil.put(uuid, System.currentTimeMillis() + COOLDOWN_MS);
        return true;
    }

    /** Wymuszone wyłączenie (quit / admin) — czyści sesję i przywraca widoczność. */
    public void disable(Player player) {
        if (player == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        if (sessions.remove(uuid) == null) {
            cooldownUntil.remove(uuid);
            return;
        }
        cooldownUntil.remove(uuid);
        if (player.isOnline()) {
            refreshVisibility(player);
            if (plugin.getTablistManager() != null) {
                try {
                    plugin.getTablistManager().refreshIncognito(player);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    public void handleQuit(Player player) {
        if (player == null) {
            return;
        }
        // Zawsze zdejmij incognito przy DC — inaczej /klan info i cache widzą UKRYTY*.
        sessions.remove(player.getUniqueId());
        cooldownUntil.remove(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player viewer = event.getPlayer();
        if (sessions.isEmpty()) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> refreshForViewer(viewer), 2L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        handleQuit(event.getPlayer());
    }

    public List<TextureProperty> skinProperties() {
        return List.of(new TextureProperty("textures", SKIN_VALUE, SKIN_SIGNATURE));
    }

    public void applyToPlayerInfo(WrapperPlayServerPlayerInfoUpdate.PlayerInfo entry, Player viewer) {
        if (entry == null) {
            return;
        }
        UUID uuid = entry.getProfileId() != null
            ? entry.getProfileId()
            : (entry.getGameProfile() != null ? entry.getGameProfile().getUUID() : null);
        if (uuid == null || !isIncognito(uuid)) {
            return;
        }
        String fakeName = getIncognitoName(uuid);
        if (fakeName == null || fakeName.isBlank()) {
            return;
        }
        UserProfile current = entry.getGameProfile();
        UUID profileUuid = current != null && current.getUUID() != null ? current.getUUID() : uuid;
        UserProfile rewritten = new UserProfile(profileUuid, fakeName);
        rewritten.setTextureProperties(skinProperties());
        entry.setGameProfile(rewritten);

        Player target = Bukkit.getPlayer(uuid);
        if (target != null) {
            Component display = tabDisplayName(viewer, target);
            if (display != null) {
                entry.setDisplayName(display);
            }
        }
    }

    /**
     * Jak Hypestar: hide/show wymusza ADD_PLAYER u widzów, a listener podmienia nick i skin.
     * Siebie odświeżamy pakietem — nie da się schować przed samym sobą.
     */
    public void refreshVisibility(Player target) {
        if (target == null || !target.isOnline()) {
            return;
        }
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> refreshVisibility(target));
            return;
        }
        VanishManager vanish = plugin.getVanishManager();
        boolean vanished = vanish != null && vanish.isVanished(target);
        // Custom TAB = sloty HollyCore; prawdziwi gracze zawsze listed=false (także incognito).
        boolean listed = plugin.getTablistManager() == null
            || !plugin.getTablistManager().isCustomTabEnabled();

        sendProfile(target, target, listed);

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.getUniqueId().equals(target.getUniqueId())) {
                continue;
            }
            if (vanished && vanish != null && !vanish.canSeeVanished(viewer)) {
                continue;
            }
            try {
                viewer.hidePlayer(plugin, target);
                viewer.showPlayer(plugin, target);
            } catch (RuntimeException | LinkageError ignored) {
                sendProfile(viewer, target, listed);
            }
        }
    }

    private void registerPacketListener() {
        if (packetListenerRegistered || PacketEvents.getAPI() == null || !PacketEvents.getAPI().isLoaded()) {
            return;
        }
        PacketEvents.getAPI().getEventManager().registerListener(
            new PacketListenerAbstract(PacketListenerPriority.HIGHEST) {
                @Override
                public void onPacketSend(PacketSendEvent event) {
                    if (event.getPacketType() != PacketType.Play.Server.PLAYER_INFO_UPDATE) {
                        return;
                    }
                    if (sessions.isEmpty()) {
                        return;
                    }
                    WrapperPlayServerPlayerInfoUpdate wrapper = new WrapperPlayServerPlayerInfoUpdate(event);
                    EnumSet<WrapperPlayServerPlayerInfoUpdate.Action> actions = wrapper.getActions();
                    if (actions != null
                        && !actions.contains(WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER)
                        && !actions.contains(WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_DISPLAY_NAME)) {
                        return;
                    }
                    List<WrapperPlayServerPlayerInfoUpdate.PlayerInfo> entries = wrapper.getEntries();
                    if (entries == null || entries.isEmpty()) {
                        return;
                    }
                    Player viewer = resolveViewer(event);
                    boolean modified = false;
                    for (WrapperPlayServerPlayerInfoUpdate.PlayerInfo entry : entries) {
                        UUID uuid = entry == null ? null : (entry.getProfileId() != null
                            ? entry.getProfileId()
                            : (entry.getGameProfile() != null ? entry.getGameProfile().getUUID() : null));
                        if (uuid == null || !isIncognito(uuid)) {
                            continue;
                        }
                        applyToPlayerInfo(entry, viewer);
                        modified = true;
                    }
                    if (modified) {
                        event.markForReEncode(true);
                    }
                }
            }
        );
        packetListenerRegistered = true;
    }

    private static Player resolveViewer(PacketSendEvent event) {
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

    private void sendProfile(Player viewer, Player target, boolean listed) {
        UUID uuid = target.getUniqueId();
        String name = isIncognito(target) ? displayName(target) : target.getName();
        UserProfile profile = new UserProfile(uuid, name);
        if (isIncognito(target)) {
            profile.setTextureProperties(skinProperties());
        } else {
            try {
                var user = PacketEvents.getAPI().getPlayerManager().getUser(target);
                if (user != null && user.getProfile() != null && user.getProfile().getTextureProperties() != null) {
                    profile.setTextureProperties(user.getProfile().getTextureProperties());
                }
            } catch (RuntimeException | LinkageError ignored) {
            }
        }

        Component display = isIncognito(target)
            ? tabDisplayName(viewer, target)
            : target.playerListName();

        WrapperPlayServerPlayerInfoUpdate.PlayerInfo info = new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
            profile,
            listed,
            Math.max(0, target.getPing()),
            toProtocolGameMode(target.getGameMode()),
            display,
            null
        );
        try {
            PacketEvents.getAPI().getPlayerManager().sendPacket(
                viewer,
                new WrapperPlayServerPlayerInfoRemove(List.of(uuid))
            );
            PacketEvents.getAPI().getPlayerManager().sendPacket(
                viewer,
                new WrapperPlayServerPlayerInfoUpdate(PROFILE_ACTIONS, info)
            );
        } catch (RuntimeException | LinkageError ignored) {
        }
    }

    private static GameMode toProtocolGameMode(org.bukkit.GameMode gameMode) {
        if (gameMode == org.bukkit.GameMode.CREATIVE) {
            return GameMode.CREATIVE;
        }
        if (gameMode == org.bukkit.GameMode.ADVENTURE) {
            return GameMode.ADVENTURE;
        }
        if (gameMode == org.bukkit.GameMode.SPECTATOR) {
            return GameMode.SPECTATOR;
        }
        return GameMode.SURVIVAL;
    }

    private static String randomSuffix(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(NAME_CHARS.charAt(RANDOM.nextInt(NAME_CHARS.length())));
        }
        return builder.toString();
    }

    private record Session(String realName, String fakeName) {
    }
}
