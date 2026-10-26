package dev.grzybalson.modules.moderation;

import dev.grzybalson.HollyCore;
import dev.grzybalson.managers.CheckManager;
import dev.grzybalson.utils.ColorUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ModerationManager {

    private static final Pattern DURATION_TOKEN = Pattern.compile("(\\d+)(w|tyg|week|weeks|d|dni|day|days|h|godz|hour|hours|hrs|m|min|mins|minute|minutes|s|sek|sec|secs|second|seconds)");

    private final HollyCore plugin;
    private final ModerationStorage storage;
    private final ModerationConfig config;

    private final Map<UUID, ModerationPunishment> bans = new ConcurrentHashMap<>();
    private final Map<String, ModerationPunishment> ipBans = new ConcurrentHashMap<>();
    private final Map<UUID, ModerationPunishment> mutes = new ConcurrentHashMap<>();
    private final Map<String, Set<UUID>> ipToPlayers = new ConcurrentHashMap<>();
    private final Map<UUID, ModerationStorage.AltProfile> profiles = new ConcurrentHashMap<>();
    private final Map<String, UUID> nameIndex = new ConcurrentHashMap<>();
    private final Map<UUID, List<ModerationStorage.ReportEntry>> reportLists = new ConcurrentHashMap<>();

    private final Map<UUID, Set<KillCredit>> killCredits = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> banBossBars = new ConcurrentHashMap<>();

    private volatile boolean ready;

    public ModerationManager(HollyCore plugin) {
        this.plugin = plugin;
        this.config = new ModerationConfig(plugin);
        this.storage = new ModerationStorage(plugin);
        storage.loadAll().whenComplete((result, throwable) -> {
            if (throwable != null) {
                plugin.getLogger().warning("Nie udało się wczytać danych moderacji: " + throwable.getMessage());
                ready = true;
                return;
            }
            applyLoad(result == null ? ModerationStorage.LoadResult.empty() : result);
            ready = true;
            plugin.getLogger().info("Moderacja wczytana (Mongo).");
        });
    }

    public void reloadConfig() {
        config.reload();
    }

    public ModerationConfig getConfig() {
        return config;
    }

    public void shutdown() {
        for (BossBar bossBar : banBossBars.values()) {
            bossBar.removeAll();
        }
        banBossBars.clear();
        killCredits.clear();
    }

    public void handleQuit(UUID uuid) {
        clearBossBar(uuid);
    }

    public boolean isReady() {
        return ready;
    }

    public ModerationPunishment getActiveBan(UUID uuid) {
        return getActive(bans, uuid);
    }

    public ModerationPunishment getActiveIpBan(String ip) {
        String normalized = ModerationStorage.normalizeIp(ip);
        if (normalized.isBlank()) {
            return null;
        }
        ModerationPunishment punishment = ipBans.get(normalized);
        if (punishment == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        if (!punishment.isActive(now)) {
            ipBans.remove(normalized, punishment);
            if (punishment.uuid() != null) {
                bans.remove(punishment.uuid(), punishment);
                storage.removeBan(punishment.uuid());
            }
            return null;
        }
        return punishment;
    }

    public ModerationPunishment inheritIpBan(UUID uuid, String name, String ip, ModerationPunishment source) {
        if (uuid == null || source == null) {
            return source;
        }
        if (isBanImmune(uuid)) {
            return null;
        }
        ModerationPunishment existing = getActiveBan(uuid);
        if (existing != null) {
            return existing;
        }
        String normalizedIp = ModerationStorage.normalizeIp(ip);
        if (normalizedIp.isBlank()) {
            normalizedIp = ModerationStorage.normalizeIp(source.ip());
        }
        ModerationPunishment inherited = new ModerationPunishment(
            ModerationPunishment.Type.BAN,
            uuid,
            name == null || name.isBlank() ? source.name() : name,
            normalizedIp,
            source.reason(),
            source.staff(),
            source.createdAt(),
            source.expiresAt(),
            false
        );
        putBan(inherited);
        return inherited;
    }

    public ModerationPunishment getActiveMute(UUID uuid) {
        return getActive(mutes, uuid);
    }

    public boolean isBanned(UUID uuid) {
        return getActiveBan(uuid) != null;
    }

    public boolean isMuted(UUID uuid) {
        return getActiveMute(uuid) != null;
    }

    public void recordJoin(Player player) {
        if (player == null) {
            return;
        }
        String ip = resolveIp(player);
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        indexPlayer(uuid, name);
        if (!ip.isBlank()) {
            ipToPlayers.computeIfAbsent(ip, key -> ConcurrentHashMap.newKeySet()).add(uuid);
            profiles.put(uuid, new ModerationStorage.AltProfile(uuid, name, ip, System.currentTimeMillis()));
            storage.saveAltLink(ip, uuid, name);
        } else {
            profiles.put(uuid, new ModerationStorage.AltProfile(uuid, name, "", System.currentTimeMillis()));
        }

        List<ModerationStorage.ReportEntry> reports = reportLists.get(uuid);
        if (reports != null && !reports.isEmpty()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    showBanBossBar(player, reports.get(reports.size() - 1).name());
                }
            }, 20L);
        }
    }

    public void recordKill(Player killer, Player victim) {
        if (killer == null || victim == null || killer.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }
        long now = System.currentTimeMillis();
        pruneKillCredits(now);
        killCredits.computeIfAbsent(killer.getUniqueId(), key -> ConcurrentHashMap.newKeySet())
            .add(new KillCredit(victim.getUniqueId(), victim.getName(), now));
    }

    public BanResult ban(
        CommandSender staff,
        String targetName,
        long durationMillis,
        String reason
    ) {
        return ban(staff, targetName, durationMillis, reason, false);
    }

    public BanResult ban(
        CommandSender staff,
        String targetName,
        long durationMillis,
        String reason,
        boolean silent
    ) {
        ResolvedTarget target = resolveTarget(targetName);
        if (target == null) {
            return BanResult.missing();
        }

        String ip = target.online() != null ? resolveIp(target.online()) : lookupIp(target.uuid());
        long now = System.currentTimeMillis();
        long expiresAt = durationMillis <= 0L ? 0L : now + durationMillis;
        ModerationPunishment ban = new ModerationPunishment(
            ModerationPunishment.Type.BAN,
            target.uuid(),
            target.name(),
            ip,
            appendSprawdzAdmin(reason, target.uuid(), staff),
            staff.getName(),
            now,
            expiresAt,
            false
        );

        putBan(ban);

        if (target.online() != null) {
            target.online().kick(buildBanKickMessage(ban));
        }

        notifyVictimsOfBan(target.uuid(), target.name());
        if (!silent) {
            broadcastStaff(staff, target.name(), ban, true);
        }
        return BanResult.ok(ban);
    }

    public BanResult banIp(
        CommandSender staff,
        String targetNameOrIp,
        long durationMillis,
        String reason,
        boolean silent
    ) {
        String requested = targetNameOrIp == null ? "" : targetNameOrIp.trim();
        if (requested.isBlank()) {
            return BanResult.missing();
        }

        ResolvedTarget primary = null;
        String ip;
        if (looksLikeIp(requested)) {
            ip = ModerationStorage.normalizeIp(requested);
        } else {
            primary = resolveTarget(requested);
            if (primary == null) {
                return BanResult.missing();
            }
            ip = primary.online() != null ? resolveIp(primary.online()) : lookupIp(primary.uuid());
        }

        if (ip.isBlank()) {
            return BanResult.missingIp();
        }

        if (primary != null && isBanImmune(primary.uuid())) {
            return BanResult.immune();
        }

        long now = System.currentTimeMillis();
        long expiresAt = durationMillis <= 0L ? 0L : now + durationMillis;
        String displayName = primary != null ? primary.name() : ip;
        UUID sourceUuid = primary != null
            ? primary.uuid()
            : UUID.nameUUIDFromBytes(("holly:ipban:" + ip).getBytes(StandardCharsets.UTF_8));

        ModerationPunishment ipBan = new ModerationPunishment(
            ModerationPunishment.Type.BAN,
            sourceUuid,
            displayName,
            ip,
            reason,
            staff.getName(),
            now,
            expiresAt,
            true
        );
        putBan(ipBan);

        Set<UUID> affected = new LinkedHashSet<>();
        affected.add(sourceUuid);
        Set<UUID> linked = ipToPlayers.getOrDefault(ip, Set.of());
        affected.addAll(linked);
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (ip.equals(resolveIp(online))) {
                affected.add(online.getUniqueId());
            }
        }

        UUID staffUuid = staff instanceof Player player ? player.getUniqueId() : null;
        for (UUID uuid : affected) {
            if (uuid.equals(sourceUuid) || uuid.equals(staffUuid) || isBanImmune(uuid)) {
                continue;
            }
            String name = lookupName(uuid);
            inheritIpBan(uuid, name, ip, ipBan);
        }

        for (UUID uuid : affected) {
            if (uuid.equals(staffUuid) || isBanImmune(uuid)) {
                continue;
            }
            Player online = Bukkit.getPlayer(uuid);
            if (online != null && online.isOnline()) {
                online.kick(buildBanKickMessage(ipBan));
            }
            notifyVictimsOfBan(uuid, lookupName(uuid));
        }

        if (!silent) {
            // Bez IP w broadcast — staff i tak wie, że to ban IP z akcji "zbanował IP".
            String broadcastTarget = looksLikeIp(displayName) ? "cel" : displayName;
            broadcastStaff(staff, broadcastTarget, ipBan, true);
        }
        return BanResult.ok(ipBan);
    }

    public MuteResult mute(
        CommandSender staff,
        String targetName,
        long durationMillis,
        String reason
    ) {
        ResolvedTarget target = resolveTarget(targetName);
        if (target == null) {
            return MuteResult.missing();
        }

        String ip = target.online() != null ? resolveIp(target.online()) : lookupIp(target.uuid());
        long now = System.currentTimeMillis();
        long expiresAt = durationMillis <= 0L ? 0L : now + durationMillis;
        ModerationPunishment mute = new ModerationPunishment(
            ModerationPunishment.Type.MUTE,
            target.uuid(),
            target.name(),
            ip,
            reason,
            staff.getName(),
            now,
            expiresAt
        );

        mutes.put(target.uuid(), mute);
        indexPlayer(target.uuid(), target.name());
        storage.saveMute(mute);

        if (target.online() != null) {
            if (mute.isPermanent()) {
                target.online().sendMessage(ColorUtils.colorizeComponent(
                    "&#FF0000✘ &8Ξ &cZostałeś wyciszony na stałe. &7Powód: &f" + mute.reason()
                ));
            } else {
                target.online().sendMessage(ColorUtils.colorizeComponent(
                    "&#FF0000✘ &8Ξ &cZostałeś wyciszony na &f" + formatDuration(mute.remainingMillis(now))
                        + "&c. &7Powód: &f" + mute.reason()
                ));
            }
        }

        broadcastStaff(staff, target.name(), mute, false);
        return MuteResult.ok(mute);
    }

    public boolean unban(String targetName) {
        if (looksLikeIp(targetName)) {
            return liftIpBan(ModerationStorage.normalizeIp(targetName));
        }

        ResolvedTarget target = resolveTarget(targetName);
        if (target == null) {
            ModerationPunishment byName = findBanByName(targetName);
            if (byName == null) {
                return false;
            }
            removeBanRecord(byName);
            if (byName.ipBan()) {
                liftIpBan(ModerationStorage.normalizeIp(byName.ip()));
            }
            return true;
        }

        ModerationPunishment existing = bans.get(target.uuid());
        if (existing == null) {
            return false;
        }
        removeBanRecord(existing);
        if (existing.ipBan()) {
            liftIpBan(ModerationStorage.normalizeIp(existing.ip()));
        }
        return true;
    }

    public int unbanAll() {
        int count = bans.size();
        bans.clear();
        ipBans.clear();
        storage.clearAllBans();
        return count;
    }

    public boolean unmute(String targetName) {
        ResolvedTarget target = resolveTarget(targetName);
        if (target == null) {
            ModerationPunishment byName = findMuteByName(targetName);
            if (byName == null) {
                return false;
            }
            mutes.remove(byName.uuid());
            storage.removeMute(byName.uuid());
            return true;
        }
        ModerationPunishment existing = mutes.remove(target.uuid());
        if (existing == null) {
            return false;
        }
        storage.removeMute(target.uuid());
        return true;
    }

    public boolean kick(CommandSender staff, String targetName, String reason) {
        Player online = Bukkit.getPlayerExact(targetName);
        if (online == null) {
            online = Bukkit.getPlayer(targetName);
        }
        if (online == null) {
            return false;
        }
        String safeReason = reason == null || reason.isBlank() ? "Brak powodu" : reason;
        online.kick(ColorUtils.colorizeComponent(config.apply(
            config.kickKick(),
            safeReason,
            staff.getName(),
            null,
            null,
            null,
            null,
            null
        )));
        return true;
    }

    public List<AltInfo> getAlts(String targetName) {
        ResolvedTarget target = resolveTarget(targetName);
        if (target == null) {
            return Collections.emptyList();
        }
        String ip = lookupIp(target.uuid());
        if (ip.isBlank() && target.online() != null) {
            ip = resolveIp(target.online());
        }
        if (ip.isBlank()) {
            return Collections.emptyList();
        }

        Set<UUID> linked = ipToPlayers.getOrDefault(ip, Set.of());
        List<AltInfo> result = new ArrayList<>();
        for (UUID uuid : linked) {
            if (uuid.equals(target.uuid())) {
                continue;
            }
            ModerationStorage.AltProfile profile = profiles.get(uuid);
            String name = profile != null && profile.name() != null && !profile.name().isBlank()
                ? profile.name()
                : lookupName(uuid);
            boolean online = Bukkit.getPlayer(uuid) != null;
            boolean banned = isBanned(uuid);
            result.add(new AltInfo(uuid, name, online, banned));
        }
        result.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return result;
    }

    public String lookupIpFor(UUID uuid) {
        return lookupIp(uuid);
    }

    public List<ModerationStorage.ReportEntry> getReportList(UUID victimUuid) {
        List<ModerationStorage.ReportEntry> list = reportLists.get(victimUuid);
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        return List.copyOf(list);
    }

    public void clearReportList(Player player) {
        if (player == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        reportLists.remove(uuid);
        storage.clearReportList(uuid);
        clearBossBar(uuid);
        player.sendMessage(ColorUtils.success("Wyczyszczono listę zbanowanych graczy oraz bossbar."));
    }

    public Component buildBanKickMessage(ModerationPunishment ban) {
        long now = System.currentTimeMillis();
        if (ban.isPermanent()) {
            return ColorUtils.colorizeComponent(config.apply(
                config.kickBan(),
                ban.reason(),
                ban.staff(),
                null,
                null,
                null,
                null,
                null
            ));
        }
        return ColorUtils.colorizeComponent(config.apply(
            config.kickTempban(),
            ban.reason(),
            ban.staff(),
            formatDuration(ban.remainingMillis(now)),
            null,
            null,
            null,
            null
        ));
    }

    public Component buildMuteChatMessage(ModerationPunishment mute) {
        long now = System.currentTimeMillis();
        if (mute.isPermanent()) {
            return ColorUtils.colorizeComponent(
                "&#FF0000✘ &8Ξ &cJesteś wyciszony na stałe. &7Powód: &f" + mute.reason()
            );
        }
        return ColorUtils.colorizeComponent(
            "&#FF0000✘ &8Ξ &cJesteś wyciszony. &7Pozostało: &e" + formatDuration(mute.remainingMillis(now))
                + " &8| &7Powód: &f" + mute.reason()
        );
    }

    public static long parseDurationMillis(String raw) {
        if (raw == null || raw.isBlank()) {
            return -1L;
        }
        String compact = raw.toLowerCase(Locale.ROOT).replace(" ", "");
        Matcher matcher = DURATION_TOKEN.matcher(compact);
        long total = 0L;
        int index = 0;
        boolean found = false;
        while (matcher.find()) {
            if (matcher.start() != index) {
                return -1L;
            }
            found = true;
            long amount = Long.parseLong(matcher.group(1));
            if (amount < 0L) {
                return -1L;
            }
            try {
                total = Math.addExact(total, unitMillis(matcher.group(2), amount));
            } catch (ArithmeticException exception) {
                return -1L;
            }
            index = matcher.end();
        }
        if (!found || index != compact.length() || total <= 0L) {
            return -1L;
        }
        return total;
    }

    public static boolean looksLikeDuration(String raw) {
        return parseDurationMillis(raw) > 0L;
    }

    public static String formatDuration(long millis) {
        if (millis < 0L) {
            return "na zawsze";
        }
        long total = Math.max(0L, millis / 1000L);
        long days = total / 86_400L;
        total %= 86_400L;
        long hours = total / 3_600L;
        total %= 3_600L;
        long minutes = total / 60L;
        long seconds = total % 60L;
        List<String> parts = new ArrayList<>();
        if (days > 0L) {
            parts.add(days + "d");
        }
        if (hours > 0L) {
            parts.add(hours + "h");
        }
        if (minutes > 0L) {
            parts.add(minutes + "m");
        }
        if (seconds > 0L || parts.isEmpty()) {
            parts.add(seconds + "s");
        }
        return String.join(" ", parts);
    }

    private String appendSprawdzAdmin(String reason, UUID targetUuid, CommandSender staff) {
        CheckManager checks = plugin.getCheckManager();
        if (checks == null || targetUuid == null) {
            return reason;
        }
        String adminName = checks.getCheckingAdminName(targetUuid);
        if (adminName == null || adminName.isBlank()) {
            if (staff instanceof Player admin && checks.isChecking(admin)) {
                Player checked = checks.getCheckedPlayer(admin);
                if (checked != null && targetUuid.equals(checked.getUniqueId())) {
                    adminName = admin.getName();
                }
            }
        }
        if (adminName == null || adminName.isBlank()) {
            return reason;
        }
        return CheckManager.appendAdminNick(reason, adminName);
    }

    public static String joinReason(String[] args, int from) {
        if (args == null || from >= args.length) {
            return "Brak powodu";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = from; i < args.length; i++) {
            if (isSilentFlag(args[i])) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(args[i]);
        }
        String reason = builder.toString().trim();
        return reason.isEmpty() ? "Brak powodu" : reason;
    }

    public static boolean isSilentFlag(String token) {
        return token != null && (token.equalsIgnoreCase("-s") || token.equalsIgnoreCase("--silent"));
    }

    /** Zwraca true gdy w args jest -s (zwykle na końcu). */
    public static boolean hasSilentFlag(String[] args) {
        if (args == null) {
            return false;
        }
        for (String arg : args) {
            if (isSilentFlag(arg)) {
                return true;
            }
        }
        return false;
    }

    private void notifyVictimsOfBan(UUID killerUuid, String killerName) {
        Set<KillCredit> credits = killCredits.remove(killerUuid);
        if (credits == null || credits.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (KillCredit credit : credits) {
            if (now - credit.timestamp() > config.killCreditTtlMs()) {
                continue;
            }
            addReportEntry(credit.victimUuid(), killerUuid, killerName, now);
            Player victim = Bukkit.getPlayer(credit.victimUuid());
            if (victim != null && victim.isOnline()) {
                showBanBossBar(victim, killerName);
                victim.sendMessage(ColorUtils.colorizeComponent(config.apply(
                    config.victimNotify(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    killerName
                )));
            }
        }
    }

    private void addReportEntry(UUID victimUuid, UUID killerUuid, String killerName, long bannedAt) {
        List<ModerationStorage.ReportEntry> list = reportLists.computeIfAbsent(victimUuid, key -> new ArrayList<>());
        synchronized (list) {
            list.removeIf(entry -> entry.name() != null && entry.name().equalsIgnoreCase(killerName));
            list.add(new ModerationStorage.ReportEntry(killerUuid, killerName, bannedAt));
            storage.saveReportList(victimUuid, list);
        }
    }

    private void showBanBossBar(Player player, String bannedNick) {
        clearBossBar(player.getUniqueId());
        String title = ColorUtils.colorize(config.apply(
            config.bossbarTitle(),
            null,
            null,
            null,
            null,
            null,
            null,
            bannedNick
        ));
        BossBar bossBar = Bukkit.createBossBar(title, BarColor.RED, BarStyle.SEGMENTED_10);
        bossBar.setProgress(1.0);
        bossBar.addPlayer(player);
        banBossBars.put(player.getUniqueId(), bossBar);
    }

    private void clearBossBar(UUID uuid) {
        BossBar existing = banBossBars.remove(uuid);
        if (existing != null) {
            existing.removeAll();
        }
    }

    private void pruneKillCredits(long now) {
        for (Map.Entry<UUID, Set<KillCredit>> entry : killCredits.entrySet()) {
            entry.getValue().removeIf(credit -> now - credit.timestamp() > config.killCreditTtlMs());
            if (entry.getValue().isEmpty()) {
                killCredits.remove(entry.getKey(), entry.getValue());
            }
        }
    }

    private ModerationPunishment getActive(Map<UUID, ModerationPunishment> map, UUID uuid) {
        if (uuid == null) {
            return null;
        }
        ModerationPunishment punishment = map.get(uuid);
        if (punishment == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        if (!punishment.isActive(now)) {
            map.remove(uuid, punishment);
            if (punishment.type() == ModerationPunishment.Type.BAN) {
                storage.removeBan(uuid);
                if (punishment.ipBan()) {
                    String ip = ModerationStorage.normalizeIp(punishment.ip());
                    if (!ip.isBlank()) {
                        ipBans.remove(ip, punishment);
                    }
                }
            } else {
                storage.removeMute(uuid);
            }
            return null;
        }
        return punishment;
    }

    private void applyLoad(ModerationStorage.LoadResult result) {
        bans.clear();
        ipBans.clear();
        mutes.clear();
        ipToPlayers.clear();
        profiles.clear();
        nameIndex.clear();
        reportLists.clear();

        bans.putAll(result.bans());
        long now = System.currentTimeMillis();
        for (ModerationPunishment ban : bans.values()) {
            if (ban != null && ban.ipBan() && ban.isActive(now)) {
                String ip = ModerationStorage.normalizeIp(ban.ip());
                if (!ip.isBlank()) {
                    ipBans.put(ip, ban);
                }
            }
        }
        mutes.putAll(result.mutes());
        profiles.putAll(result.profiles());
        nameIndex.putAll(result.nameIndex());
        for (Map.Entry<String, Set<UUID>> entry : result.ipToPlayers().entrySet()) {
            ipToPlayers.put(entry.getKey(), ConcurrentHashMap.newKeySet());
            ipToPlayers.get(entry.getKey()).addAll(entry.getValue());
        }
        for (Map.Entry<UUID, List<ModerationStorage.ReportEntry>> entry : result.reports().entrySet()) {
            reportLists.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
    }

    private ResolvedTarget resolveTarget(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return null;
        }
        Player online = Bukkit.getPlayerExact(rawName);
        if (online == null) {
            online = Bukkit.getPlayer(rawName);
        }
        if (online != null) {
            return new ResolvedTarget(online.getUniqueId(), online.getName(), online);
        }

        UUID indexed = nameIndex.get(rawName.toLowerCase(Locale.ROOT));
        if (indexed != null) {
            String name = lookupName(indexed);
            return new ResolvedTarget(indexed, name, null);
        }

        OfflinePlayer offline = Bukkit.getOfflinePlayer(rawName);
        if (offline.getUniqueId() != null && (offline.hasPlayedBefore() || offline.isOnline() || offline.getName() != null)) {
            String name = offline.getName() != null ? offline.getName() : rawName;
            // Paper: getOfflinePlayer zawsze zwaca UUID — pozwól banować z konsoli po nicku.
            return new ResolvedTarget(offline.getUniqueId(), name, offline.isOnline() ? offline.getPlayer() : null);
        }

        // UUID wprost z konsoli
        try {
            UUID uuid = UUID.fromString(rawName);
            OfflinePlayer byId = Bukkit.getOfflinePlayer(uuid);
            String name = byId.getName() != null ? byId.getName() : rawName;
            return new ResolvedTarget(uuid, name, byId.isOnline() ? byId.getPlayer() : null);
        } catch (IllegalArgumentException ignored) {
        }

        ModerationPunishment ban = findBanByName(rawName);
        if (ban != null) {
            return new ResolvedTarget(ban.uuid(), ban.name(), null);
        }
        ModerationPunishment mute = findMuteByName(rawName);
        if (mute != null) {
            return new ResolvedTarget(mute.uuid(), mute.name(), null);
        }
        return null;
    }

    private ModerationPunishment findBanByName(String name) {
        if (name == null) {
            return null;
        }
        for (ModerationPunishment ban : bans.values()) {
            if (ban.name() != null && ban.name().equalsIgnoreCase(name) && ban.isActive(System.currentTimeMillis())) {
                return ban;
            }
        }
        return null;
    }

    private ModerationPunishment findMuteByName(String name) {
        if (name == null) {
            return null;
        }
        for (ModerationPunishment mute : mutes.values()) {
            if (mute.name() != null && mute.name().equalsIgnoreCase(name) && mute.isActive(System.currentTimeMillis())) {
                return mute;
            }
        }
        return null;
    }

    private void indexPlayer(UUID uuid, String name) {
        if (uuid == null || name == null || name.isBlank()) {
            return;
        }
        nameIndex.put(name.toLowerCase(Locale.ROOT), uuid);
    }

    private String lookupName(UUID uuid) {
        ModerationStorage.AltProfile profile = profiles.get(uuid);
        if (profile != null && profile.name() != null && !profile.name().isBlank()) {
            return profile.name();
        }
        ModerationPunishment ban = bans.get(uuid);
        if (ban != null && ban.name() != null) {
            return ban.name();
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        return offline.getName() != null ? offline.getName() : uuid.toString();
    }

    private String lookupIp(UUID uuid) {
        ModerationStorage.AltProfile profile = profiles.get(uuid);
        if (profile != null && profile.ip() != null && !profile.ip().isBlank()) {
            return profile.ip();
        }
        ModerationPunishment ban = bans.get(uuid);
        if (ban != null && ban.ip() != null) {
            return ModerationStorage.normalizeIp(ban.ip());
        }
        return "";
    }

    private String resolveIp(Player player) {
        if (player == null || player.getAddress() == null || player.getAddress().getAddress() == null) {
            return "";
        }
        return ModerationStorage.normalizeIp(player.getAddress().getAddress().getHostAddress());
    }

    private void broadcastStaff(CommandSender staff, String targetName, ModerationPunishment punishment, boolean ban) {
        if (!config.staffBroadcastEnabled()) {
            return;
        }
        String action = ban
            ? (punishment.ipBan()
                ? (punishment.isPermanent() ? "zbanował IP" : "tymczasowo zbanował IP")
                : (punishment.isPermanent() ? "zbanował" : "tymczasowo zbanował"))
            : (punishment.isPermanent() ? "wyciszył" : "tymczasowo wyciszył");
        String timePart = punishment.isPermanent()
            ? "na zawsze"
            : "na " + formatDuration(punishment.remainingMillis(System.currentTimeMillis()));
        String template = ban ? config.staffBroadcastBan() : config.staffBroadcastMute();
        String message = config.apply(
            template,
            punishment.reason(),
            staff.getName(),
            null,
            targetName,
            action,
            timePart,
            null
        );
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission("Holly.mod.ban")
                || online.hasPermission("Holly.mod.mute")
                || online.hasPermission("Holly.mod.kick")
                || online.isOp()) {
                online.sendMessage(ColorUtils.colorizeComponent(message));
            }
        }
        Bukkit.getConsoleSender().sendMessage(ColorUtils.colorizeComponent(message));
    }

    private static long unitMillis(String unit, long amount) {
        return switch (unit.toLowerCase(Locale.ROOT)) {
            case "s", "sek", "sec", "secs", "second", "seconds" -> amount * 1000L;
            case "m", "min", "mins", "minute", "minutes" -> amount * 60_000L;
            case "h", "godz", "hour", "hours", "hrs" -> amount * 3_600_000L;
            case "d", "dni", "day", "days" -> amount * 86_400_000L;
            case "w", "tyg", "week", "weeks" -> amount * 604_800_000L;
            default -> 0L;
        };
    }

    private record KillCredit(UUID victimUuid, String victimName, long timestamp) {}

    private record ResolvedTarget(UUID uuid, String name, Player online) {}

    public record AltInfo(UUID uuid, String name, boolean online, boolean banned) {}

    public static boolean looksLikeIp(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String ip = ModerationStorage.normalizeIp(raw);
        if (ip.matches("\\d{1,3}(\\.\\d{1,3}){3}")) {
            return true;
        }
        return ip.indexOf(':') >= 0 && ip.chars().filter(ch -> ch == ':').count() >= 2;
    }

    private void putBan(ModerationPunishment ban) {
        if (ban == null || ban.uuid() == null) {
            return;
        }
        bans.put(ban.uuid(), ban);
        indexPlayer(ban.uuid(), ban.name());
        if (ban.ipBan()) {
            String ip = ModerationStorage.normalizeIp(ban.ip());
            if (!ip.isBlank()) {
                ipBans.put(ip, ban);
            }
        }
        storage.saveBan(ban);
    }

    private void removeBanRecord(ModerationPunishment ban) {
        if (ban == null || ban.uuid() == null) {
            return;
        }
        bans.remove(ban.uuid());
        if (ban.ipBan()) {
            String ip = ModerationStorage.normalizeIp(ban.ip());
            if (!ip.isBlank()) {
                ipBans.remove(ip, ban);
            }
        }
        storage.removeBan(ban.uuid());
    }

    private boolean liftIpBan(String ip) {
        String normalized = ModerationStorage.normalizeIp(ip);
        if (normalized.isBlank()) {
            return false;
        }
        boolean removed = ipBans.remove(normalized) != null;
        for (ModerationPunishment ban : List.copyOf(bans.values())) {
            if (normalized.equals(ModerationStorage.normalizeIp(ban.ip()))) {
                removeBanRecord(ban);
                removed = true;
            }
        }
        return removed;
    }

    private boolean isBanImmune(UUID uuid) {
        if (uuid == null) {
            return false;
        }
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            return online.isOp()
                || online.hasPermission("Holly.mod.bypass")
                || online.hasPermission("Holly.mod.banip")
                || online.hasPermission("Holly.mod.*");
        }
        return Bukkit.getOfflinePlayer(uuid).isOp();
    }

    public static final class BanResult {
        private final boolean success;
        private final boolean missing;
        private final boolean missingIp;
        private final boolean immune;
        private final ModerationPunishment punishment;

        private BanResult(
            boolean success,
            boolean missing,
            boolean missingIp,
            boolean immune,
            ModerationPunishment punishment
        ) {
            this.success = success;
            this.missing = missing;
            this.missingIp = missingIp;
            this.immune = immune;
            this.punishment = punishment;
        }

        static BanResult ok(ModerationPunishment punishment) {
            return new BanResult(true, false, false, false, punishment);
        }

        static BanResult missing() {
            return new BanResult(false, true, false, false, null);
        }

        static BanResult missingIp() {
            return new BanResult(false, false, true, false, null);
        }

        static BanResult immune() {
            return new BanResult(false, false, false, true, null);
        }

        public boolean success() {
            return success;
        }

        public boolean isMissing() {
            return missing;
        }

        public boolean isMissingIp() {
            return missingIp;
        }

        public boolean isImmune() {
            return immune;
        }

        public ModerationPunishment punishment() {
            return punishment;
        }
    }

    public static final class MuteResult {
        private final boolean success;
        private final boolean missing;
        private final ModerationPunishment punishment;

        private MuteResult(boolean success, boolean missing, ModerationPunishment punishment) {
            this.success = success;
            this.missing = missing;
            this.punishment = punishment;
        }

        static MuteResult ok(ModerationPunishment punishment) {
            return new MuteResult(true, false, punishment);
        }

        static MuteResult missing() {
            return new MuteResult(false, true, null);
        }

        public boolean success() {
            return success;
        }

        public boolean isMissing() {
            return missing;
        }

        public ModerationPunishment punishment() {
            return punishment;
        }
    }
}
