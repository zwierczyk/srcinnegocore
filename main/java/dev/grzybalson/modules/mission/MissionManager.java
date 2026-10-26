package dev.grzybalson.modules.mission;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.HollyDataPaths;
import dev.grzybalson.utils.ColorUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class MissionManager {

    public static final String MISSION_ID_KEY = "holly_mission_id";

    private static final String CONFIG_RESOURCE = "addons/mission/mission.yml";

    private final HollyCore plugin;
    private final File configFile;
    private final MissionDataManager dataManager;

    private YamlConfiguration config;
    private String inventoryTitle = "&8Misje";
    private int inventoryRows = 6;
    private Map<String, MissionDefinition> missions = Map.of();

    public MissionManager(HollyCore plugin) {
        this.plugin = plugin;
        this.configFile = HollyDataPaths.getAddonFolder(plugin, "mission").toPath().resolve("mission.yml").toFile();
        this.dataManager = new MissionDataManager(plugin);
        reload();
    }

    public void reload() {
        HollyDataPaths.ensureResource(plugin, CONFIG_RESOURCE);
        this.config = YamlConfiguration.loadConfiguration(configFile);
        this.inventoryTitle = config.getString("gui.title", "&8Misje");
        this.inventoryRows = Math.max(3, Math.min(6, config.getInt("gui.rows", 6)));
        this.missions = loadMissionDefinitions();
    }

    public MissionDataManager getDataManager() {
        return dataManager;
    }

    public Map<String, MissionDefinition> getMissions() {
        return missions;
    }

    public MissionDefinition getMission(String id) {
        return missions.get(id);
    }

    public CompletableFuture<Void> loadPlayer(UUID uuid) {
        return dataManager.load(uuid).thenApply(data -> null);
    }

    public void unloadPlayer(UUID uuid) {
        dataManager.unload(uuid);
    }

    public void openGui(Player player) {
        loadPlayer(player.getUniqueId()).thenAccept(ignored -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }

            int size = plugin.getGuiFactory().getInventorySize(inventoryRows);
            MissionGuiHolder holder = new MissionGuiHolder();
            Inventory inventory = plugin.getGuiFactory().createWithDefaultTemplate(
                holder,
                size,
                inventoryTitle
            );
            holder.bind(inventory);

            MissionPlayerData data = dataManager.getCached(player.getUniqueId());
            for (MissionDefinition mission : missions.values()) {
                if (!mission.enabled()) {
                    continue;
                }
                if (mission.slot() >= 0 && mission.slot() < inventory.getSize()) {
                    inventory.setItem(mission.slot(), createMissionItem(mission, data));
                }
            }

            player.openInventory(inventory);
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.6F, 1.2F);
        }));
    }

    public void handleClick(Player player, int slot, ItemStack clicked) {
        if (clicked == null || clicked.getType().isAir() || plugin.getGuiFactory().isBackgroundItem(clicked)) {
            return;
        }

        MissionDefinition mission = findMissionBySlot(slot);
        if (mission == null) {
            return;
        }

        MissionPlayerData data = dataManager.getCached(player.getUniqueId());
        int current = data.getProgress(mission.id());
        if (current < mission.goal()) {
            player.sendMessage(ColorUtils.colorizeComponent(
                config.getString("messages.not-ready", "&#FF0000✘ &8Ξ &cNie ukończyłeś jeszcze tej misji.")
                    .replace("{current}", String.valueOf(current))
                    .replace("{goal}", String.valueOf(mission.goal()))
                    .replace("{mission}", stripColors(mission.name()))
            ));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7F, 0.8F);
            return;
        }

        int shards = resolveRewardShards(player, mission);
        if (shards <= 0) {
            player.sendMessage(ColorUtils.colorizeComponent(
                config.getString("messages.no-shard-system", "&#FF0000✘ &8Ξ &cBrak skonfigurowanej nagrody.")
            ));
            return;
        }

        if (plugin.getSzamanManager() == null) {
            player.sendMessage(ColorUtils.colorizeComponent(
                config.getString("messages.no-shard-system", "&#FF0000✘ &8Ξ &cSystem odlamków jest wyłączony.")
            ));
            return;
        }

        data.reset(mission.id());
        dataManager.save(data);
        plugin.getSzamanManager().giveShard(player, shards);

        player.sendMessage(ColorUtils.colorizeComponent(
            config.getString("messages.claimed", "&#00FF3B✔ &8Ξ &#4EFA76Odebrałeś nagrodę!")
                .replace("{mission}", stripColors(mission.name()))
                .replace("{shards}", String.valueOf(shards))
        ));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8F, 1.3F);
        openGui(player);
    }

    public void incrementProgress(UUID uuid, String missionId, int amount) {
        if (amount <= 0 || missionId == null || !missions.containsKey(missionId)) {
            return;
        }
        MissionDefinition mission = missions.get(missionId);
        if (mission == null || !mission.enabled()) {
            return;
        }

        MissionPlayerData data = dataManager.getCached(uuid);
        int current = data.getProgress(missionId);
        if (current >= mission.goal()) {
            return;
        }
        data.increment(missionId, amount);
        dataManager.markDirty(data);
    }

    public void incrementCases(UUID uuid) {
        incrementProgress(uuid, "cases", 1);
    }

    public void incrementKills(UUID uuid) {
        incrementProgress(uuid, "kills", 1);
    }

    public void incrementGeneratorBlocks(UUID uuid) {
        incrementProgress(uuid, "generator_blocks", 1);
    }

    public void incrementBossHits(UUID uuid) {
        incrementProgress(uuid, "boss_hits", 1);
    }

    private MissionDefinition findMissionBySlot(int slot) {
        for (MissionDefinition mission : missions.values()) {
            if (mission.enabled() && mission.slot() == slot) {
                return mission;
            }
        }
        return null;
    }

    private ItemStack createMissionItem(MissionDefinition mission, MissionPlayerData data) {
        ItemStack item = new ItemStack(mission.material());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        int current = data.getProgress(mission.id());
        int goal = mission.goal();
        boolean ready = current >= goal;

        meta.displayName(ColorUtils.colorizeComponent(mission.name()).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(ColorUtils.colorizeComponent(
            mission.progressLore()
                .replace("{current}", String.valueOf(Math.min(current, goal)))
                .replace("{goal}", String.valueOf(goal))
        ).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(ColorUtils.colorizeComponent(mission.rewardsHeader()).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        for (MissionRewardTier tier : mission.rewardTiers()) {
            lore.add(ColorUtils.colorizeComponent(
                mission.rewardLine()
                    .replace("{display}", tier.display())
                    .replace("{shards}", String.valueOf(tier.shards()))
            ).decoration(TextDecoration.ITALIC, false));
        }
        if (ready) {
            lore.add(Component.empty());
            lore.add(ColorUtils.colorizeComponent(mission.claimableLore()).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        meta.getPersistentDataContainer().set(
            new org.bukkit.NamespacedKey(plugin, MISSION_ID_KEY),
            org.bukkit.persistence.PersistentDataType.STRING,
            mission.id()
        );
        item.setItemMeta(meta);
        return item;
    }

    private int resolveRewardShards(Player player, MissionDefinition mission) {
        MissionRewardTier defaultTier = null;
        MissionRewardTier bestMatch = null;

        for (MissionRewardTier tier : mission.rewardTiers()) {
            if (tier.defaultTier()) {
                defaultTier = tier;
            }
            if (tier.permission() == null || tier.permission().isBlank()) {
                if (defaultTier == null) {
                    defaultTier = tier;
                }
                continue;
            }
            if (player.hasPermission(tier.permission())) {
                if (bestMatch == null || tier.shards() > bestMatch.shards()) {
                    bestMatch = tier;
                }
            }
        }

        if (bestMatch != null) {
            return bestMatch.shards();
        }
        return defaultTier == null ? 0 : defaultTier.shards();
    }

    private Map<String, MissionDefinition> loadMissionDefinitions() {
        ConfigurationSection section = config.getConfigurationSection("missions");
        if (section == null) {
            return Map.of();
        }

        Map<String, MissionDefinition> loaded = new LinkedHashMap<>();
        for (String id : section.getKeys(false)) {
            String path = "missions." + id;
            if (!config.getBoolean(path + ".enabled", true)) {
                continue;
            }

            Material material = Material.matchMaterial(config.getString(path + ".material", "PAPER"));
            if (material == null) {
                material = Material.PAPER;
            }

            List<MissionRewardTier> tiers = loadRewardTiers(path + ".rewards");
            loaded.put(id, new MissionDefinition(
                id,
                true,
                config.getInt(path + ".slot", 0),
                material,
                config.getString(path + ".name", id),
                config.getString(path + ".progress-lore", "&7{current}/{goal}"),
                config.getString(path + ".rewards-header", "&8Ξ &6&lNagrody:"),
                config.getString(path + ".reward-line", "&7{display} - &f{shards} odlamki"),
                Math.max(1, config.getInt(path + ".goal", 1)),
                config.getString(path + ".claimable-lore", "&a» Kliknij LPM aby odebrać nagrodę!"),
                tiers
            ));
        }
        return Map.copyOf(loaded);
    }

    private List<MissionRewardTier> loadRewardTiers(String path) {
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null) {
            return List.of();
        }

        List<MissionRewardTier> tiers = new ArrayList<>();
        for (String tierId : section.getKeys(false)) {
            String tierPath = path + "." + tierId;
            tiers.add(new MissionRewardTier(
                tierId,
                config.getString(tierPath + ".display", tierId),
                config.getString(tierPath + ".permission", ""),
                Math.max(0, config.getInt(tierPath + ".shards", 0)),
                config.getBoolean(tierPath + ".default", false)
            ));
        }
        tiers.sort(Comparator.comparingInt(MissionRewardTier::shards).reversed());
        return List.copyOf(tiers);
    }

    private static String stripColors(String input) {
        if (input == null) {
            return "";
        }
        return input.replaceAll("(?i)&#[0-9A-F]{6}", "")
            .replaceAll("(?i)&[0-9A-FK-OR]", "")
            .replaceAll("§#[0-9A-F]{6}", "")
            .replaceAll("§[0-9A-FK-OR]", "");
    }
}
