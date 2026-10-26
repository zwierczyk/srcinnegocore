package dev.grzybalson.modules.dropkeys;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.HollyDataPaths;
import dev.grzybalson.modules.bossbar.ScheduledDropService;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class DropKeysManager implements Listener {

    private static final String CONFIG_RESOURCE = "addons/dropkeys/dropkeys.yml";

    private final HollyCore plugin;
    private final File configFile;
    private YamlConfiguration config;
    private boolean enabled = true;
    private Source mining = Source.empty();
    private Source kills = Source.empty();
    private int samePlayerCooldownSeconds = 3600;
    private final Cache<String, Long> killRewardCooldowns = Caffeine.newBuilder()
        .expireAfterWrite(1, TimeUnit.HOURS)
        .maximumSize(5000)
        .build();

    public DropKeysManager(HollyCore plugin) {
        this.plugin = plugin;
        this.configFile = HollyDataPaths.getAddonFolder(plugin, "dropkeys").toPath().resolve("dropkeys.yml").toFile();
        reload();
    }

    public void reload() {
        HollyDataPaths.ensureResource(plugin, CONFIG_RESOURCE);
        this.config = YamlConfiguration.loadConfiguration(configFile);
        this.enabled = config.getBoolean("enabled", true);
        this.mining = loadSource("mining", true);
        this.kills = loadSource("kills", false);
        ConfigurationSection killsSection = config.getConfigurationSection("kills");
        this.samePlayerCooldownSeconds = killsSection != null
            ? Math.max(0, killsSection.getInt("same-player-cooldown-seconds", 3600))
            : 3600;
        this.killRewardCooldowns.invalidateAll();
    }

    private Source loadSource(String path, boolean titleMode) {
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null) {
            return Source.empty();
        }
        boolean sourceEnabled = section.getBoolean("enabled", true);
        List<Reward> rewards = new ArrayList<>();
        List<?> rawRewards = section.getList("rewards");
        if (rawRewards != null && !rawRewards.isEmpty()) {
            for (Object entry : rawRewards) {
                Reward reward = parseReward(entry, titleMode);
                if (reward != null) {
                    rewards.add(reward);
                }
            }
        } else {
            Reward reward = parseReward(section, titleMode);
            if (reward != null) {
                rewards.add(reward);
            }
        }
        return new Source(sourceEnabled, rewards);
    }

    private Reward parseReward(Object source, boolean titleMode) {
        if (source instanceof ConfigurationSection section) {
            return parseRewardSection(section, titleMode);
        }
        if (source instanceof java.util.Map<?, ?> map) {
            YamlConfiguration wrapper = new YamlConfiguration();
            ConfigurationSection section = wrapper.createSection("reward");
            for (java.util.Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    section.set(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return parseRewardSection(section, titleMode);
        }
        return null;
    }

    private Reward parseRewardSection(ConfigurationSection section, boolean titleMode) {
        if (section == null) {
            return null;
        }
        String command = section.getString("command", "");
        if (command == null || command.isBlank()) {
            return null;
        }
        return new Reward(
            Math.max(0.0D, section.getDouble("chance", 0.0D)),
            command,
            section.getString("title", "&#7AD4FF&lKLUCZ"),
            section.getString("subtitle", "&#7AD4FFWykopałeś klucz"),
            section.getString("chat", "&#FF59E8❖ &#7AD7FF{PLAYER} &8〢 &fzdobył klucz z zabójstwa"),
            parseSound(section.getString("sound"), titleMode ? Sound.BLOCK_AMETHYST_BLOCK_CHIME : Sound.ENTITY_PLAYER_LEVELUP),
            parseSound(section.getString("extra-sound"), titleMode ? null : Sound.BLOCK_NOTE_BLOCK_CHIME),
            (float) section.getDouble("sound-volume", 0.9D),
            (float) section.getDouble("sound-pitch", 1.3D),
            (float) section.getDouble("extra-sound-volume", 0.7D),
            (float) section.getDouble("extra-sound-pitch", 1.8D)
        );
    }

    private static Sound parseSound(String raw, Sound fallback) {
        if (raw != null && raw.equalsIgnoreCase("none")) {
            return null;
        }
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Sound.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGeneratorBreak(BlockBreakEvent event) {
        if (!enabled || !mining.enabled() || mining.rewards().isEmpty()) {
            return;
        }
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        if (!isGeneratorMine(event.getBlock().getLocation())) {
            return;
        }
        tryRewards(player, mining, true, false, event.getBlock().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKill(PlayerDeathEvent event) {
        if (!enabled || !kills.enabled() || kills.rewards().isEmpty()) {
            return;
        }
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)) {
            return;
        }
        if (killer.getGameMode() == GameMode.CREATIVE || killer.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        if (isSamePlayerKillOnCooldown(killer.getUniqueId(), victim.getUniqueId())) {
            return;
        }
        markSamePlayerKill(killer.getUniqueId(), victim.getUniqueId());
        tryRewards(killer, kills, false, true, null);
    }

    private boolean isSamePlayerKillOnCooldown(UUID killer, UUID victim) {
        if (samePlayerCooldownSeconds <= 0) {
            return false;
        }
        if (plugin.getEloManager() != null && plugin.getEloManager().isOnCooldown(killer, victim)) {
            return true;
        }
        String key = killer + ":" + victim;
        Long lastReward = killRewardCooldowns.getIfPresent(key);
        if (lastReward == null) {
            return false;
        }
        return System.currentTimeMillis() - lastReward < samePlayerCooldownSeconds * 1000L;
    }

    private void markSamePlayerKill(UUID killer, UUID victim) {
        if (samePlayerCooldownSeconds <= 0) {
            return;
        }
        killRewardCooldowns.put(killer + ":" + victim, System.currentTimeMillis());
    }

    private boolean isGeneratorMine(Location location) {
        if (plugin.getZoneBoxManager() != null && plugin.getZoneBoxManager().isInGeneratorVolume(location)) {
            return true;
        }
        return plugin.getGeneratorManager() != null && plugin.getGeneratorManager().findGeneratorAt(location) != null;
    }

    private void tryRewards(Player player, Source source, boolean showTitle, boolean broadcastKill, Location location) {
        double chanceMultiplier = location == null ? 1.0D : ScheduledDropService.getMultiplier(plugin, location);
        chanceMultiplier *= getKostiumDropKeysMultiplier(player);
        for (Reward reward : source.rewards()) {
            double chance = Math.max(0.0D, Math.min(100.0D, reward.chance() * chanceMultiplier));
            if (chance <= 0.0D) {
                continue;
            }
            if (ThreadLocalRandom.current().nextDouble() * 100.0D > chance) {
                continue;
            }
            grant(player, reward, showTitle, broadcastKill);
        }
    }

    private double getKostiumDropKeysMultiplier(Player player) {
        try {
            if (plugin.getOgHubCustomItemsManager() != null
                && plugin.getOgHubCustomItemsManager().isInBlockedRegion(player.getLocation())) {
                return 1.0D;
            }
            return dev.grzybalson.anarchiagg.szafa.BoostHandler.getDropKeysMultiplier(player.getUniqueId());
        } catch (Throwable ignored) {
            return 1.0D;
        }
    }

    private void grant(Player player, Reward reward, boolean showTitle, boolean broadcastKill) {
        String command = reward.command()
            .replace("{PLAYER}", player.getName())
            .replace("{player}", player.getName())
            .replace("{UUID}", player.getUniqueId().toString());
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);

        if (showTitle) {
            ColorUtils.sendTitle(player, reward.title(), reward.subtitle(), 5, 35, 8);
            playSounds(player, reward, false);
            return;
        }
        if (broadcastKill && reward.chat() != null && !reward.chat().isBlank()) {
            String chat = reward.chat()
                .replace("{PLAYER}", player.getName())
                .replace("{player}", player.getName());
            Bukkit.broadcast(ColorUtils.colorizeComponent(chat));
        }
        playSounds(player, reward, true);
    }

    private void playSounds(Player player, Reward reward, boolean nearby) {
        Location location = player.getLocation();
        if (reward.sound() != null) {
            if (nearby) {
                player.getWorld().playSound(location, reward.sound(), reward.volume(), reward.pitch());
            } else {
                player.playSound(location, reward.sound(), reward.volume(), reward.pitch());
            }
        }
        if (reward.extraSound() != null) {
            if (nearby) {
                player.getWorld().playSound(location, reward.extraSound(), reward.extraVolume(), reward.extraPitch());
            } else {
                player.playSound(location, reward.extraSound(), reward.extraVolume(), reward.extraPitch());
            }
        }
    }

    private record Source(boolean enabled, List<Reward> rewards) {
        private static Source empty() {
            return new Source(false, List.of());
        }
    }

    private record Reward(
        double chance,
        String command,
        String title,
        String subtitle,
        String chat,
        Sound sound,
        Sound extraSound,
        float volume,
        float pitch,
        float extraVolume,
        float extraPitch
    ) {
    }
}
