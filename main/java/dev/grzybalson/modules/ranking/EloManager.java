package dev.grzybalson.modules.ranking;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.modules.clans.EloSettingsConfig;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class EloManager {

    /** Dynamiczny ranking — wartości na stałe w kodzie, bez limitów z configu. */
    private static final int ABSOLUTE_MAX_ELO = 999_999;
    private static final int MIN_GAIN = 70;
    private static final int MAX_GAIN = 300;
    private static final int TYPICAL_GAIN_MIN = 70;
    private static final int TYPICAL_GAIN_MAX = 120;
    private static final int MIN_LOSS = 40;
    private static final int MAX_LOSS = 200;

    private static final long RANKING_RESET_COOLDOWN_MS = 60L * 60L * 1000L;
    private static final int RANKING_RESET_VALUE = 1000;

    private final HollyCore plugin;
    private final Map<UUID, Integer> eloCache;
    private final Map<String, Long> killCooldowns;
    private final Map<UUID, Long> rankingResetUntil;

    public EloManager(HollyCore plugin) {
        this.plugin = plugin;
        this.eloCache = new ConcurrentHashMap<>();
        this.killCooldowns = new ConcurrentHashMap<>();
        this.rankingResetUntil = new ConcurrentHashMap<>();
    }

    private String getCooldownKey(UUID killer, UUID victim) {
        return killer.toString() + ":" + victim.toString();
    }

    public boolean isOnCooldown(UUID killer, UUID victim) {
        String key = getCooldownKey(killer, victim);
        Long lastKill = killCooldowns.get(key);
        if (lastKill == null) return false;

        int cooldownSeconds = plugin.getModuleConfigManager().getEloSettingsConfig().getKillCooldownSeconds();
        return System.currentTimeMillis() - lastKill < cooldownSeconds * 1000L;
    }

    private void setCooldown(UUID killer, UUID victim) {
        String key = getCooldownKey(killer, victim);
        killCooldowns.put(key, System.currentTimeMillis());
    }

    public int calculateEloGain(int winnerElo, int loserElo) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int base = random.nextInt(TYPICAL_GAIN_MIN, TYPICAL_GAIN_MAX + 1);

        if (loserElo > winnerElo) {
            int gap = loserElo - winnerElo;
            base += Math.min(120, gap / 20);
        } else {
            base += random.nextInt(-8, 25);
        }

        base += random.nextInt(-12, 13);

        if (winnerElo > 50_000) {
            base = (int) (base * 0.90);
        } else if (winnerElo > 10_000) {
            base = (int) (base * 0.95);
        } else if (winnerElo > 5_000) {
            base = (int) (base * 0.98);
        }

        return Math.min(MAX_GAIN, Math.max(MIN_GAIN, base));
    }

    public int calculateEloLoss(int winnerElo, int loserElo) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int base = random.nextInt(55, 111);

        if (winnerElo < loserElo) {
            int gap = loserElo - winnerElo;
            base = Math.max(MIN_LOSS, base - gap / 25);
        } else {
            base += random.nextInt(-6, 18);
        }

        base += random.nextInt(-10, 11);

        if (loserElo > 50_000) {
            base = (int) (base * 0.92);
        } else if (loserElo > 10_000) {
            base = (int) (base * 0.96);
        }

        return Math.min(MAX_LOSS, Math.max(MIN_LOSS, base));
    }

    public void processKill(Player killer, Player victim) {
        EloSettingsConfig config = plugin.getModuleConfigManager().getEloSettingsConfig();

        if (!config.isEnabled()) return;

        if (isOnCooldown(killer.getUniqueId(), victim.getUniqueId())) {
            int killerElo = getElo(killer.getUniqueId());
            int victimElo = getElo(victim.getUniqueId());
            int killerPosition = getCurrentRank(killer.getUniqueId(), killerElo);
            int victimPosition = getCurrentRank(victim.getUniqueId(), victimElo);
            sendKillNotifications(new KillContext(
                killer,
                victim,
                0,
                0,
                killerElo,
                victimElo,
                killerElo,
                victimElo,
                killerPosition,
                victimPosition,
                killerPosition,
                victimPosition
            ));
            return;
        }

        setCooldown(killer.getUniqueId(), victim.getUniqueId());

        int killerElo = getElo(killer.getUniqueId());
        int victimElo = getElo(victim.getUniqueId());
        int oldKillerPosition = getCurrentRank(killer.getUniqueId(), killerElo);
        int oldVictimPosition = getCurrentRank(victim.getUniqueId(), victimElo);

        int gain = calculateEloGain(killerElo, victimElo);
        if (plugin.getSzamanManager() != null) {
            double rankingMultiplier = plugin.getSzamanManager().getTurboRankingBonus(killer.getUniqueId());
            double perkPercent = plugin.getSzamanManager().getPerkValue(killer.getUniqueId(), dev.grzybalson.modules.szaman.PerkType.RANKING);
            if (perkPercent > 0.0D) {
                rankingMultiplier = 1.0D + (rankingMultiplier - 1.0D) + (perkPercent / 100.0D);
            }
            if (rankingMultiplier > 1.0D) {
                gain = Math.min(MAX_GAIN, Math.max(gain, (int) Math.ceil(gain * rankingMultiplier)));
            }
        }

        if (plugin.getClanManager() != null) {
            String clanTag = plugin.getClanManager().getCachedPlayerClan(killer.getUniqueId());
            if (clanTag != null) {
                var clan = plugin.getClanManager().getCachedClan(clanTag);
                if (clan != null) {
                    double clanRankingBonus = plugin.getClanManager().getAmplifier(clan, "RANKING");
                    if (clanRankingBonus > 0.0D) {
                        if (clanRankingBonus >= 1.0D) {
                            gain = Math.min(MAX_GAIN, Math.max(gain, (int) Math.ceil(gain * clanRankingBonus)));
                        } else {
                            gain = Math.min(MAX_GAIN, Math.max(gain, (int) Math.ceil(gain * (1.0D + (clanRankingBonus / 100.0D)))));
                        }
                    }
                }
            }
        }

        gain = Math.max(1, (int) Math.round(gain * 0.50D));
        int loss = Math.max(1, (int) Math.round(gain * 1.30D));
        int newKillerElo = Math.min(ABSOLUTE_MAX_ELO, killerElo + gain);
        int newVictimElo = Math.max(config.getMinElo(), victimElo - loss);
        int actualKillerChange = newKillerElo - killerElo;
        int actualVictimChange = newVictimElo - victimElo;
        int newKillerPosition = getEstimatedRank(killer.getUniqueId(), newKillerElo);
        int newVictimPosition = getEstimatedRank(victim.getUniqueId(), newVictimElo);

        setElo(killer.getUniqueId(), newKillerElo);
        setElo(victim.getUniqueId(), newVictimElo);

        if (actualKillerChange > 0 && plugin.getAchievementManager() != null) {
            plugin.getAchievementManager().incrementEarnedPoints(killer.getUniqueId(), actualKillerChange);
        }

        if (plugin.getMissionManager() != null) {
            plugin.getMissionManager().incrementKills(killer.getUniqueId());
        }

        sendKillNotifications(new KillContext(
            killer,
            victim,
            actualKillerChange,
            actualVictimChange,
            killerElo,
            victimElo,
            newKillerElo,
            newVictimElo,
            oldKillerPosition,
            oldVictimPosition,
            newKillerPosition,
            newVictimPosition
        ));
    }

    private void sendKillNotifications(KillContext context) {
        EloSettingsConfig config = plugin.getModuleConfigManager().getEloSettingsConfig();
        String broadcast = applyKillPlaceholders(config.getBroadcast(), context);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (config.isKillAnimationEnabled()) {
                if (context.attacker().isOnline()) {
                    KillTitleAnimator.playKill(plugin, config, context.attacker(),
                        resolveSubtitleParts(config.getKillSubtitleParts(), context, true));
                }
                if (context.victim().isOnline()) {
                    KillTitleAnimator.playDeath(plugin, config, context.victim(),
                        resolveSubtitleParts(config.getDeathSubtitleParts(), context, false));
                }
            } else {
                String attackerTitle = ColorUtils.colorize(applyKillPlaceholders(config.getAttackerTitle(), context));
                String attackerSubtitle = ColorUtils.colorize(applyKillPlaceholders(config.getAttackerSubtitle(), context));
                String victimTitle = ColorUtils.colorize(applyKillPlaceholders(config.getVictimTitle(), context));
                String victimSubtitle = ColorUtils.colorize(applyKillPlaceholders(config.getVictimSubtitle(), context));
                if (context.attacker().isOnline()) {
                    ColorUtils.sendTitle(context.attacker(), attackerTitle, attackerSubtitle, config.getTitleFadeIn(), config.getTitleStay(), config.getTitleFadeOut());
                }
                if (context.victim().isOnline()) {
                    ColorUtils.sendTitle(context.victim(), victimTitle, victimSubtitle, config.getTitleFadeIn(), config.getTitleStay(), config.getTitleFadeOut());
                }
            }
        }, 1L);

        if (broadcast != null && !broadcast.isBlank()) {
            Bukkit.broadcast(ColorUtils.colorizeComponent(broadcast));
        }
    }

    private java.util.List<String> resolveSubtitleParts(java.util.List<String> templates, KillContext context, boolean forKiller) {
        java.util.List<String> resolved = new java.util.ArrayList<>();
        if (templates == null || templates.isEmpty()) {
            return resolved;
        }
        String posChange = forKiller
            ? buildPosChange(context.attackerOldPosition(), context.attackerNewPosition())
            : buildPosChange(context.victimOldPosition(), context.victimNewPosition());
        String points = String.valueOf(forKiller ? context.attackerNewPoints() : context.victimNewPoints());
        String oldPos = formatRankPosition(forKiller ? context.attackerOldPosition() : context.victimOldPosition());
        String newPos = formatRankPosition(forKiller ? context.attackerNewPosition() : context.victimNewPosition());
        String gain = formatSigned(context.attackerPointChange());
        String loss = formatSigned(context.victimPointChange());
        for (String part : templates) {
            resolved.add(applyKillPlaceholders(part, context)
                .replace("{KILLER}", context.attacker().getName())
                .replace("{VICTIM}", context.victim().getName())
                .replace("{GAIN}", gain)
                .replace("{LOSS}", loss)
                .replace("{POINTS}", points)
                .replace("{OLD_POS}", oldPos)
                .replace("{NEW_POS}", newPos)
                .replace("{POS_CHANGE}", posChange));
        }
        return resolved;
    }

    private String buildPosChange(int posBefore, int posAfter) {
        EloSettingsConfig config = plugin.getModuleConfigManager().getEloSettingsConfig();
        int before = posBefore > 0 ? posBefore : Integer.MAX_VALUE;
        int after = posAfter > 0 ? posAfter : Integer.MAX_VALUE;
        int diff = before - after;
        if (diff > 0) {
            return config.getPosChangeUp().replace("{AMOUNT}", String.valueOf(diff));
        }
        if (diff < 0) {
            return config.getPosChangeDown().replace("{AMOUNT}", String.valueOf(Math.abs(diff)));
        }
        return config.getPosChangeNone();
    }

    private String formatRankPosition(int position) {
        return position > 0 ? "#" + position : "-";
    }

    private int getCurrentRank(UUID uuid, int elo) {
        if (plugin.getTopManager() == null) {
            return 0;
        }
        return plugin.getTopManager().getCachedPlayerRank("elo", uuid, elo);
    }

    private int getEstimatedRank(UUID uuid, int elo) {
        if (plugin.getTopManager() == null) {
            return 0;
        }
        return plugin.getTopManager().estimatePlayerRank("elo", uuid, elo);
    }

    private String applyKillPlaceholders(String text, KillContext context) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        Map<String, String> placeholders = buildKillPlaceholders(context);
        String result = text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result
                .replace("%" + entry.getKey() + "%", entry.getValue())
                .replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    private Map<String, String> buildKillPlaceholders(KillContext context) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        String attackerName = context.attacker().getName();
        String victimName = context.victim().getName();

        placeholders.put("attacker", attackerName);
        placeholders.put("killer", attackerName);
        placeholders.put("victim", victimName);
        placeholders.put("attacker-points-count", formatSigned(context.attackerPointChange()));
        placeholders.put("victim-points-count", formatSigned(context.victimPointChange()));
        placeholders.put("attacker-points-count-raw", String.valueOf(Math.abs(context.attackerPointChange())));
        placeholders.put("victim-points-count-raw", String.valueOf(Math.abs(context.victimPointChange())));
        placeholders.put("attacker-points", String.valueOf(context.attackerNewPoints()));
        placeholders.put("victim-points", String.valueOf(context.victimNewPoints()));
        placeholders.put("attacker-points-before", String.valueOf(context.attackerOldPoints()));
        placeholders.put("victim-points-before", String.valueOf(context.victimOldPoints()));
        placeholders.put("attacker-position", formatPosition(context.attackerNewPosition()));
        placeholders.put("victim-position", formatPosition(context.victimNewPosition()));
        placeholders.put("attacker-position-before", formatPosition(context.attackerOldPosition()));
        placeholders.put("victim-position-before", formatPosition(context.victimOldPosition()));
        placeholders.put("attacker-position-change", String.valueOf(Math.max(0, context.attackerOldPosition() - context.attackerNewPosition())));
        placeholders.put("victim-position-change", String.valueOf(Math.max(0, context.victimNewPosition() - context.victimOldPosition())));
        placeholders.put("attacker-position-delta", formatSigned(context.attackerOldPosition() - context.attackerNewPosition()));
        placeholders.put("victim-position-delta", formatSigned(context.victimOldPosition() - context.victimNewPosition()));
        placeholders.put("gain", String.valueOf(Math.max(0, context.attackerPointChange())));
        placeholders.put("lost", String.valueOf(Math.max(0, -context.victimPointChange())));
        return placeholders;
    }

    private String formatSigned(int value) {
        return value > 0 ? "+" + value : String.valueOf(value);
    }

    private String formatPosition(int position) {
        return position > 0 ? String.valueOf(position) : "-";
    }

    public int getElo(UUID uuid) {
        if (eloCache.containsKey(uuid)) {
            return eloCache.get(uuid);
        }
        return plugin.getModuleConfigManager().getEloSettingsConfig().getStartingElo();
    }

    /**
     * Boty spoofera: losowe ELO 950–1200 przy pierwszym pojawieniu, bez nadpisywania
     * już wczytanego / ustawionego rankingu dla tego UUID.
     */
    public void ensureSpoofedElo(UUID uuid) {
        if (uuid == null || eloCache.containsKey(uuid)) {
            return;
        }
        setElo(uuid, ThreadLocalRandom.current().nextInt(950, 1201));
    }

    public boolean hasCachedElo(UUID uuid) {
        return uuid != null && eloCache.containsKey(uuid);
    }

    public CompletableFuture<Integer> getEloAsync(UUID uuid) {
        if (eloCache.containsKey(uuid)) {
            return CompletableFuture.completedFuture(eloCache.get(uuid));
        }
        int startingElo = plugin.getModuleConfigManager().getEloSettingsConfig().getStartingElo();
        return plugin.getMongoManager().toFuture(
            plugin.getMongoManager().getCollection("player_elo")
                .find(new org.bson.Document("uuid", uuid.toString()))
                .first()
        ).thenApply(doc -> {
            int elo = resolveStoredElo(doc, startingElo);
            eloCache.put(uuid, elo);
            return elo;
        }).exceptionally(throwable -> startingElo);
    }

    /**
     * Pobiera pozycję gracza w rankingu ELO (1 = pierwsze miejsce)
     * @param uuid UUID gracza
     * @return pozycja w rankingu lub 0 jeśli nie znaleziono
     */
    public int getRank(UUID uuid) {
        return plugin.getTopManager().getCachedPlayerRank("elo", uuid, getElo(uuid));
    }

    public void setElo(UUID uuid, int elo) {
        eloCache.put(uuid, elo);
        if (plugin.getHollyPlaceholderExpansion() != null) {
            plugin.getHollyPlaceholderExpansion().updateElo(uuid, elo);
        }
        plugin.getTopManager().invalidatePlayer(uuid);
        saveElo(uuid, elo);
    }

    public CompletableFuture<Void> setEloAsync(UUID uuid, int elo) {
        eloCache.put(uuid, elo);
        if (plugin.getHollyPlaceholderExpansion() != null) {
            plugin.getHollyPlaceholderExpansion().updateElo(uuid, elo);
        }
        plugin.getTopManager().invalidatePlayer(uuid);
        return saveElo(uuid, elo);
    }

    public CompletableFuture<Void> resetElo(UUID uuid) {
        int startingElo = plugin.getModuleConfigManager().getEloSettingsConfig().getStartingElo();
        eloCache.put(uuid, startingElo);
        if (plugin.getHollyPlaceholderExpansion() != null) {
            plugin.getHollyPlaceholderExpansion().updateElo(uuid, startingElo);
        }
        plugin.getTopManager().invalidatePlayer(uuid);
        return saveElo(uuid, startingElo);
    }

    public int getRankingResetValue() {
        return RANKING_RESET_VALUE;
    }

    public long getRankingResetRemainingMs(UUID uuid) {
        if (uuid == null) {
            return 0L;
        }
        Long until = rankingResetUntil.get(uuid);
        if (until == null) {
            return 0L;
        }
        return Math.max(0L, until - System.currentTimeMillis());
    }

    public CompletableFuture<Void> resetRankingWithCooldown(UUID uuid) {
        setElo(uuid, RANKING_RESET_VALUE);
        long until = System.currentTimeMillis() + RANKING_RESET_COOLDOWN_MS;
        rankingResetUntil.put(uuid, until);
        CompletableFuture<Void> cooldownSave = plugin.getMongoManager().toFuture(
            plugin.getMongoManager().getCollection("ranking_reset_cooldown")
                .replaceOne(
                    new org.bson.Document("uuid", uuid.toString()),
                    new org.bson.Document("uuid", uuid.toString()).append("until", until),
                    new com.mongodb.client.model.ReplaceOptions().upsert(true)
                )
        ).thenApply(ignored -> null);
        return CompletableFuture.allOf(cooldownSave, resetCombatStats(uuid));
    }

    private CompletableFuture<Void> resetCombatStats(UUID uuid) {
        CompletableFuture<Void> achievements = plugin.getAchievementManager() != null
            ? plugin.getAchievementManager().resetStats(uuid, true, true, false, false)
            : CompletableFuture.completedFuture(null);
        return achievements.thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                try {
                    player.setStatistic(Statistic.PLAYER_KILLS, 0);
                    player.setStatistic(Statistic.DEATHS, 0);
                } catch (Throwable ignored) {
                }
                if (plugin.getTopManager() != null) {
                    plugin.getTopManager().rememberPlayerKillStatistic(player);
                }
            }
            if (plugin.getTopManager() != null) {
                plugin.getTopManager().invalidatePlayer(uuid);
                plugin.getTopManager().requestRefresh();
            }
            if (plugin.getHollyPlaceholderExpansion() != null) {
                plugin.getHollyPlaceholderExpansion().invalidatePlayer(uuid);
            }
        }));
    }

    private void loadRankingResetCooldown(UUID uuid) {
        plugin.getMongoManager().toFuture(
            plugin.getMongoManager().getCollection("ranking_reset_cooldown")
                .find(new org.bson.Document("uuid", uuid.toString()))
                .first()
        ).thenAccept(doc -> {
            if (doc == null) {
                return;
            }
            Object value = doc.get("until");
            if (!(value instanceof Number number)) {
                return;
            }
            long until = number.longValue();
            if (until > System.currentTimeMillis()) {
                rankingResetUntil.put(uuid, until);
            } else {
                rankingResetUntil.remove(uuid);
            }
        }).exceptionally(throwable -> null);
    }

    public void loadElo(UUID uuid) {
        final int startingElo = plugin.getModuleConfigManager().getEloSettingsConfig().getStartingElo();
        String playerName = null;
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            playerName = online.getName();
        }
        final String resolvedName = playerName;

        plugin.getMongoManager().toFuture(
            plugin.getMongoManager().getCollection("player_elo")
                .find(new org.bson.Document("uuid", uuid.toString()))
                .first()
        ).thenCompose(doc -> {
            int elo = resolveStoredElo(doc, startingElo);
            eloCache.put(uuid, elo);
            if (plugin.getHollyPlaceholderExpansion() != null) {
                plugin.getHollyPlaceholderExpansion().updateElo(uuid, elo);
            }

            loadRankingResetCooldown(uuid);

            org.bson.Document updated = new org.bson.Document("uuid", uuid.toString())
                .append("elo", elo)
                .append("initialized", true);
            if (resolvedName != null && !resolvedName.isBlank()) {
                updated.append("name", resolvedName);
            } else if (doc != null && doc.getString("name") != null && !doc.getString("name").isBlank()) {
                updated.append("name", doc.getString("name"));
            }

            return plugin.getMongoManager().toFuture(
                plugin.getMongoManager().getCollection("player_elo")
                    .replaceOne(
                        new org.bson.Document("uuid", uuid.toString()),
                        updated,
                        new com.mongodb.client.model.ReplaceOptions().upsert(true)
                    )
            );
        }).exceptionally(throwable -> null);
    }

    private int resolveStoredElo(org.bson.Document doc, int startingElo) {
        if (doc == null) {
            return startingElo;
        }

        int storedElo = getStoredEloValue(doc, startingElo);
        boolean initialized = Boolean.TRUE.equals(doc.getBoolean("initialized"));
        int minElo = plugin.getModuleConfigManager().getEloSettingsConfig().getMinElo();

        if (!initialized && startingElo > minElo && storedElo <= minElo) {
            return startingElo;
        }

        return storedElo;
    }

    private int getStoredEloValue(org.bson.Document doc, int fallback) {
        Object value = doc.get("elo");
        if (value instanceof Number number) {
            return number.intValue();
        }
        return fallback;
    }

    private CompletableFuture<Void> saveElo(UUID uuid, int elo) {
        String playerName = null;
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            playerName = online.getName();
        }

        org.bson.Document doc = new org.bson.Document()
            .append("uuid", uuid.toString())
            .append("elo", elo)
            .append("initialized", true);
        if (playerName != null && !playerName.isBlank()) {
            doc.append("name", playerName);
        }

        return plugin.getMongoManager().toFuture(
            plugin.getMongoManager().getCollection("player_elo")
                .replaceOne(
                    new org.bson.Document("uuid", uuid.toString()),
                    doc,
                    new com.mongodb.client.model.ReplaceOptions().upsert(true)
                )
        ).thenRun(() -> plugin.getTopManager().requestRefresh());
    }

    public void unloadElo(UUID uuid) {
        eloCache.remove(uuid);
        rankingResetUntil.remove(uuid);
        if (plugin.getHollyPlaceholderExpansion() != null) {
            plugin.getHollyPlaceholderExpansion().invalidatePlayer(uuid);
        }
    }

    public void clearAllCaches() {
        eloCache.clear();
        rankingResetUntil.clear();
    }

    private record KillContext(
        Player attacker,
        Player victim,
        int attackerPointChange,
        int victimPointChange,
        int attackerOldPoints,
        int victimOldPoints,
        int attackerNewPoints,
        int victimNewPoints,
        int attackerOldPosition,
        int victimOldPosition,
        int attackerNewPosition,
        int victimNewPosition
    ) {
    }
}
