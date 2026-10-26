package dev.grzybalson.modules.achievement;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import dev.grzybalson.HollyCore;
import dev.grzybalson.config.modules.achievement.AchievementConfig;
import dev.grzybalson.gui.AchievementGUI;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.NBTUtils;
import dev.grzybalson.utils.TimeUtil;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import org.bukkit.Bukkit;
import org.bukkit.Sound;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

public class AchievementListener implements Listener {

    private final HollyCore plugin;
    private Set<Material> allowedBlocks;

    public AchievementListener(HollyCore plugin) {
        this.plugin = plugin;
        reloadAllowedBlocks();
    }

    public void reloadAllowedBlocks() {
        allowedBlocks = new HashSet<>();
        AchievementConfig config = plugin.getModuleConfigManager().getAchievementConfig();
        for (String matName : config.getAllowedBlocks()) {
            Material mat = Material.getMaterial(matName);
            if (mat != null) {
                allowedBlocks.add(mat);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getAchievementManager().syncPlayerStatistics(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getAchievementManager().unloadPlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerKill(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player killer = victim.getKiller();

        plugin.getAchievementManager().loadPlayer(victim.getUniqueId())
            .thenCompose(data -> {
                plugin.getAchievementManager().updatePlayerName(victim.getUniqueId(), victim.getName());
                plugin.getAchievementManager().incrementDeaths(victim.getUniqueId());
                return plugin.getAchievementManager().savePlayer(victim.getUniqueId());
            });

        if (killer != null && !killer.equals(victim)) {
            if (plugin.getTopManager() != null) {
                plugin.getTopManager().rememberPlayerKillStatistic(killer);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    plugin.getTopManager().rememberPlayerKillStatistic(killer);
                    plugin.getTopManager().requestRefresh();
                }, 1L);
            }

            plugin.getAchievementManager().loadPlayer(killer.getUniqueId())
                .thenCompose(data -> {
                    plugin.getAchievementManager().updatePlayerName(killer.getUniqueId(), killer.getName());
                    plugin.getAchievementManager().incrementKills(killer.getUniqueId());
                    return plugin.getAchievementManager().savePlayer(killer.getUniqueId());
                })
                .thenRun(() -> {
                    if (plugin.getTopManager() != null) {
                        plugin.getTopManager().invalidatePlayer(killer.getUniqueId());
                        plugin.getTopManager().requestRefresh();
                    }
                });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Material blockType = event.getBlock().getType();

        if (isGeneratorMining(event.getBlock().getLocation())) {
            plugin.getAchievementManager().incrementBlocksMined(player.getUniqueId());
            return;
        }

        if (!allowedBlocks.contains(blockType)) return;

        // Check if in excluded region
        if (isInExcludedRegion(event.getBlock().getLocation())) return;

        plugin.getAchievementManager().incrementBlocksMined(player.getUniqueId());
    }

    private boolean isGeneratorMining(org.bukkit.Location location) {
        if (location == null) {
            return false;
        }
        if (plugin.getGeneratorManager() != null && plugin.getGeneratorManager().findGeneratorAt(location) != null) {
            return true;
        }
        return plugin.getZoneBoxManager() != null && plugin.getZoneBoxManager().isInGeneratorVolume(location);
    }

    private boolean isInExcludedRegion(Location location) {
        try {
            AchievementConfig config = plugin.getModuleConfigManager().getAchievementConfig();
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionQuery query = container.createQuery();
            ApplicableRegionSet regions = query.getApplicableRegions(BukkitAdapter.adapt(location));

            for (String excludedRegion : config.getExcludedRegions()) {
                if (regions.getRegions().stream()
                    .anyMatch(r -> r.getId().equalsIgnoreCase(excludedRegion))) {
                    return true;
                }
            }
        } catch (Exception e) {
            // WorldGuard not available or error
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof AchievementGUI gui)) return;

        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType().isAir()) return;
        if (plugin.getGuiFactory().isBackgroundItem(clickedItem)) return;

        if (gui.getViewType() == AchievementGUI.ViewType.MAIN) {
            // Check if clicked a category
            String category = NBTUtils.getString(clickedItem, "achievement_category");
            if (category != null) {
                try {
                    AchievementType type = AchievementType.valueOf(category);
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        new AchievementGUI(plugin, player, type).open();
                    });
                } catch (IllegalArgumentException ignored) {}
            }
        } else if (gui.getViewType() == AchievementGUI.ViewType.CATEGORY) {
            // Check for back button
            String action = NBTUtils.getString(clickedItem, "achievement_action");
            if ("back".equals(action)) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    new AchievementGUI(plugin, player).open();
                });
                return;
            }

            // Check for achievement claim
            String typeStr = NBTUtils.getString(clickedItem, "achievement_type");
            Integer tier = NBTUtils.getInt(clickedItem, "achievement_tier");
            if (typeStr != null && tier != null) {
                try {
                    AchievementType type = AchievementType.valueOf(typeStr);
                    AchievementManager manager = plugin.getAchievementManager();
                    AchievementConfig config = plugin.getModuleConfigManager().getAchievementConfig();

                    player.closeInventory();

                    long enableDate = config.getEnableDateMillis();
                    if (TimeUtil.isEnableDateActive(enableDate)) {
                        long remaining = enableDate - System.currentTimeMillis();
                        sendTitle(player, "&c", "&cOsiągnięcia zostaną włączone za&8: &4" + TimeUtil.convert(remaining));
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                        return;
                    }

                    if (manager.isClaimed(player.getUniqueId(), type, tier)) {
                        sendTitle(player, "&f", "&cOdebrałeś już tą nagrodę!");
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    } else if (!manager.canClaimTier(player.getUniqueId(), type, tier)) {
                        sendTitle(player, "&f", "&cPierw odbierz poprzednie nagrody!");
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    } else if (!manager.isCompleted(player.getUniqueId(), type, tier)) {
                        sendTitle(player, "&f", "&cNie ukończyłeś jeszcze tego osiągnięcia!");
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    } else {
                        String rewardKey = type.name() + "_" + tier;
                        String rewardData = config.getReward(rewardKey);
                        if (rewardData == null || rewardData.isEmpty()) {
                            player.sendMessage(ColorUtils.colorizeComponent(config.getNoRewardsSet()));
                            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                        } else if (manager.claimAchievement(player, type, tier)) {
                            ItemStack[] rewards = manager.getRewardItems(type, tier);
                            if (rewards != null) {
                                for (ItemStack reward : rewards) {
                                    if (reward != null && !reward.getType().isAir()) {
                                        player.sendMessage(ColorUtils.colorizeComponent(
                                            "&a+" + reward.getAmount() + " " + getRewardName(reward)));
                                    }
                                }
                            }
                            sendTitle(player, "&f", "&aOdebrałeś osiągnięcie!");
                            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                        }
                    }
                } catch (IllegalArgumentException ignored) {}
            }
        }
    }

    private void sendTitle(Player player, String subtitle, String title) {
        player.showTitle(Title.title(
            ColorUtils.colorizeComponent(title),
            ColorUtils.colorizeComponent(subtitle),
            Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(2000), Duration.ofMillis(500))
        ));
    }

    private String getRewardName(ItemStack reward) {
        if (reward.hasItemMeta() && reward.getItemMeta().hasDisplayName()) {
            return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacyAmpersand().serialize(reward.getItemMeta().displayName());
        }
        return reward.getType().name();
    }
}
