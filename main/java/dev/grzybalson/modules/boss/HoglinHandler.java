package dev.grzybalson.modules.boss;

import dev.grzybalson.managers.SidebarManager;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Hoglin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class HoglinHandler implements Listener {

    private final BossManager bossManager;
    private final Map<UUID, BossDamage> bossDamages = new HashMap<>();

    private UUID activeBossId;
    private Location bossLocation;
    private int bossHealth;
    private int maxBossHealth;
    private String displayName;
    private long lastTransform;
    private long lastBoardUpdate;

    public HoglinHandler(BossManager bossManager) {
        this.bossManager = bossManager;
    }

    public void prepareSpawn(int maxHealth, String name) {
        this.maxBossHealth = Math.max(1, maxHealth);
        this.bossHealth = this.maxBossHealth;
        this.displayName = name;
        this.bossDamages.clear();
        this.activeBossId = null;
        this.bossLocation = null;
        this.lastTransform = System.currentTimeMillis();
        this.lastBoardUpdate = 0L;
    }

    public void trackSpawned(Hoglin hoglin) {
        this.activeBossId = hoglin.getUniqueId();
        this.bossLocation = hoglin.getLocation().clone();
        updateName(hoglin);
        updateBoard(true);
    }

    public boolean isActiveBoss(UUID uuid) {
        return activeBossId != null && activeBossId.equals(uuid);
    }

    @EventHandler
    public void onTransform(EntityTransformEvent event) {
        if (isActiveBoss(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        bossDamages.remove(event.getPlayer().getUniqueId());
        SidebarManager sidebar = bossManager.getPlugin().getSidebarManager();
        if (sidebar != null) {
            sidebar.clearCustomBoard(event.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity().getType() != EntityType.HOGLIN) {
            return;
        }
        if (!(event.getEntity() instanceof Hoglin hoglin)) {
            return;
        }
        if (!isActiveBoss(hoglin.getUniqueId())) {
            return;
        }
        if (!(event.getDamager() instanceof Player damager)) {
            event.setDamage(0.0D);
            return;
        }

        event.setDamage(0.0D);
        if (hoglin.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
            hoglin.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(1000.0D);
        }
        hoglin.setHealth(Math.min(hoglin.getMaxHealth(), 1000.0D));
        hoglin.setTarget(damager);
        bossLocation = hoglin.getLocation().clone();

        bossHealth--;
        BossDamage damage = bossDamages.computeIfAbsent(damager.getUniqueId(), BossDamage::new);
        damage.addHit();
        if (bossManager.getPlugin().getMissionManager() != null) {
            bossManager.getPlugin().getMissionManager().incrementBossHits(damager.getUniqueId());
        }
        updateName(hoglin);

        if (bossHealth > 0 && bossHealth % 10 == 0) {
            applyRandomAbility(hoglin);
        }

        if (lastTransform + 6500L < System.currentTimeMillis()) {
            lastTransform = System.currentTimeMillis();
            if (hoglin.isAdult()) {
                hoglin.setBaby();
            } else {
                hoglin.setAdult();
            }
        }

        if (bossHealth <= 0) {
            handleDefeat(hoglin);
            return;
        }

        updateBoard(false);
    }

    private void applyRandomAbility(Hoglin hoglin) {
        int roll = ThreadLocalRandom.current().nextInt(1, 4);
        switch (roll) {
            case 1 -> {
                hoglin.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 4));
                hoglin.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 200, 4));
            }
            case 2 -> applyNearby(hoglin, PotionEffectType.SLOW, 100, 2);
            case 3 -> applyNearby(hoglin, PotionEffectType.LEVITATION, 60, 2);
            default -> {
            }
        }
    }

    private void applyNearby(Hoglin hoglin, PotionEffectType type, int duration, int amplifier) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.getWorld().equals(hoglin.getWorld())) {
                continue;
            }
            if (online.getLocation().distanceSquared(hoglin.getLocation()) <= 25.0D) {
                online.addPotionEffect(new PotionEffect(type, duration, amplifier));
            }
        }
    }

    private void handleDefeat(Hoglin hoglin) {
        List<BossDamage> ranking = sortedDamages();
        Player first = ranking.isEmpty() ? null : Bukkit.getPlayer(ranking.get(0).getUniqueId());

        if (first != null) {
            List<Player> rankingPlayers = new ArrayList<>();
            for (BossDamage entry : ranking) {
                Player player = Bukkit.getPlayer(entry.getUniqueId());
                if (player != null && player.isOnline()) {
                    rankingPlayers.add(player);
                }
            }
            BossRewards rewards = bossManager.getConfig().getHoglinRewardPack();
            String fallbackBroadcast = rewards.broadcast() == null || rewards.broadcast().isBlank()
                ? "&aBoss Hoglin został pokonany! Top1: &2{PLAYER}"
                : null;
            BossRewardGiver.grantTop(rankingPlayers, hoglin.getLocation(), rewards, displayName);
            if (fallbackBroadcast != null) {
                Bukkit.broadcast(ColorUtils.colorizeComponent(
                    fallbackBroadcast.replace("{PLAYER}", first.getName())
                ));
            }
        } else {
            BossRewardGiver.grantTop(List.of(), hoglin.getLocation(), bossManager.getConfig().getHoglinRewardPack(), displayName);
            Bukkit.broadcast(ColorUtils.colorizeComponent("&aBoss Hoglin został pokonany!"));
        }

        activeBossId = null;
        bossLocation = null;
        bossDamages.clear();
        hoglin.remove();

        SidebarManager sidebar = bossManager.getPlugin().getSidebarManager();
        if (sidebar != null) {
            sidebar.clearAllCustomBoards();
        }
    }

    private void updateBoard(boolean force) {
        SidebarManager sidebar = bossManager.getPlugin().getSidebarManager();
        if (sidebar == null || activeBossId == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (!force && now - lastBoardUpdate < 250L) {
            return;
        }
        lastBoardUpdate = now;

        Location loc = bossLocation;
        String prettyLocation = loc == null
            ? "&7?"
            : String.format("x: %d y: %d z: %d", loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        String bossName = displayName == null ? "&cHoglin" : displayName;
        List<BossDamage> ranking = sortedDamages();
        BossConfig config = bossManager.getConfig();
        String emptyRank = config.getSidebarEmptyRank();
        String rankFormat = config.getSidebarRankFormat();
        String top1 = formatRankEntry(ranking, 0, emptyRank, rankFormat);
        String top2 = formatRankEntry(ranking, 1, emptyRank, rankFormat);
        String top3 = formatRankEntry(ranking, 2, emptyRank, rankFormat);

        for (Player online : Bukkit.getOnlinePlayers()) {
            int playerDamage = 0;
            BossDamage own = bossDamages.get(online.getUniqueId());
            if (own != null) {
                playerDamage = own.getDamage();
            }

            List<String> lines = new ArrayList<>(config.getSidebarLines().size());
            for (String template : config.getSidebarLines()) {
                lines.add(applyBoardPlaceholders(
                    template, bossName, prettyLocation, playerDamage,
                    getPlayerRank(online.getUniqueId(), ranking),
                    online.getName(), top1, top2, top3
                ));
            }
            sidebar.setCustomBoard(
                online,
                applyBoardPlaceholders(
                    config.getSidebarTitle(), bossName, prettyLocation, playerDamage,
                    getPlayerRank(online.getUniqueId(), ranking),
                    online.getName(), top1, top2, top3
                ),
                lines
            );
        }
    }

    private List<BossDamage> sortedDamages() {
        List<BossDamage> ranking = new ArrayList<>(bossDamages.values());
        ranking.sort(Comparator.comparingInt(BossDamage::getDamage).reversed());
        return ranking;
    }

    private String formatRankEntry(List<BossDamage> ranking, int index, String emptyRank, String rankFormat) {
        if (ranking.size() <= index) {
            return emptyRank == null ? "Nikt" : emptyRank;
        }
        BossDamage entry = ranking.get(index);
        Player player = Bukkit.getPlayer(entry.getUniqueId());
        if (player == null) {
            return emptyRank == null ? "Nikt" : emptyRank;
        }
        String format = rankFormat == null || rankFormat.isBlank()
            ? "{PLAYER} &8- &#C80B0B❤{DAMAGE}"
            : rankFormat;
        return format
            .replace("{PLAYER}", player.getName())
            .replace("{DAMAGE}", String.valueOf(entry.getDamage()));
    }

    private String applyBoardPlaceholders(
        String template,
        String bossName,
        String prettyLocation,
        int playerDamage,
        int position,
        String playerName,
        String top1,
        String top2,
        String top3
    ) {
        if (template == null || template.isEmpty()) {
            return "";
        }
        return template
            .replace("{TOP1}", top1)
            .replace("{TOP2}", top2)
            .replace("{TOP3}", top3)
            .replace("{BOSS}", bossName)
            .replace("{NAME}", bossName)
            .replace("{HEALTH}", String.valueOf(Math.max(0, bossHealth)))
            .replace("{MAX_HEALTH}", String.valueOf(maxBossHealth))
            .replace("{LOCATION}", prettyLocation)
            .replace("{DAMAGE}", String.valueOf(playerDamage))
            .replace("{POSITION}", String.valueOf(position))
            .replace("{PLAYER}", playerName);
    }

    private int getPlayerRank(UUID uuid, List<BossDamage> ranking) {
        for (int i = 0; i < ranking.size(); i++) {
            if (ranking.get(i).getUniqueId().equals(uuid)) {
                return i + 1;
            }
        }
        return ranking.isEmpty() ? 0 : ranking.size() + 1;
    }

    private void updateName(Hoglin hoglin) {
        String formatted = bossManager.getConfig().getHoglinNametag()
            .replace("{NAME}", displayName == null ? "&cHoglin" : displayName)
            .replace("{HEALTH}", String.valueOf(Math.max(0, bossHealth)))
            .replace("{MAX_HEALTH}", String.valueOf(maxBossHealth));
        hoglin.customName(ColorUtils.colorizeComponent(formatted));
        hoglin.setCustomNameVisible(true);
    }
}
