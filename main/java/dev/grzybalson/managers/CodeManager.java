package dev.grzybalson.managers;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.configs.CodeConfig;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.CommandPlaceholderUtil;
import dev.grzybalson.utils.ItemSerializer;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CodeManager {

    private final HollyCore plugin;
    private final Map<String, Set<UUID>> usedCodes = new ConcurrentHashMap<>();
    private final Map<String, BossBar> activeBossBars = new ConcurrentHashMap<>();
    private BukkitTask bossBarTask;

    public CodeManager(HollyCore plugin) {
        this.plugin = plugin;
        loadUsedCodes();
        startBossBarTask();
    }

    private void loadUsedCodes() {
        plugin.getMongoManager().toListFuture(
            plugin.getMongoManager().getCollection("code_usage").find()
        ).thenAccept(docs -> {
            for (org.bson.Document doc : docs) {
                String code = doc.getString("code");
                List<String> uuids = doc.getList("used_by", String.class);
                if (code != null && uuids != null) {
                    Set<UUID> uuidSet = ConcurrentHashMap.newKeySet();
                    for (String uuid : uuids) {
                        uuidSet.add(UUID.fromString(uuid));
                    }
                    usedCodes.put(code.toLowerCase(), uuidSet);
                }
            }
        });
    }

    private void saveUsedCode(String code, UUID uuid) {
        usedCodes.computeIfAbsent(code.toLowerCase(), k -> ConcurrentHashMap.newKeySet()).add(uuid);

        List<String> uuids = new ArrayList<>();
        for (UUID u : usedCodes.get(code.toLowerCase())) {
            uuids.add(u.toString());
        }

        org.bson.Document doc = new org.bson.Document()
            .append("code", code.toLowerCase())
            .append("used_by", uuids);

        plugin.getMongoManager().toFuture(
            plugin.getMongoManager().getCollection("code_usage")
                .replaceOne(
                    new org.bson.Document("code", code.toLowerCase()),
                    doc,
                    new com.mongodb.client.model.ReplaceOptions().upsert(true)
                )
        );
    }

    public boolean hasUsedCode(UUID uuid, String code) {
        Set<UUID> used = usedCodes.get(code.toLowerCase());
        return used != null && used.contains(uuid);
    }

    public void resetCodeUsageForAll(String codeName) {
        usedCodes.remove(codeName.toLowerCase());

        plugin.getMongoManager().toFuture(
            plugin.getMongoManager().getCollection("code_usage")
                .deleteOne(new org.bson.Document("code", codeName.toLowerCase()))
        );
    }

    public void resetCodeUsageForPlayer(String codeName, UUID playerUuid) {
        Set<UUID> used = usedCodes.get(codeName.toLowerCase());
        if (used != null) {
            used.remove(playerUuid);

            List<String> uuids = new ArrayList<>();
            for (UUID u : used) {
                uuids.add(u.toString());
            }

            org.bson.Document doc = new org.bson.Document()
                .append("code", codeName.toLowerCase())
                .append("used_by", uuids);

            plugin.getMongoManager().toFuture(
                plugin.getMongoManager().getCollection("code_usage")
                    .replaceOne(
                        new org.bson.Document("code", codeName.toLowerCase()),
                        doc,
                        new com.mongodb.client.model.ReplaceOptions().upsert(true)
                    )
            );
        }
    }

    public boolean redeemCode(Player player, String codeName) {
        CodeConfig config = plugin.getConfigManager().getCodeConfig();
        CodeConfig.CodeEntry code = config.getCode(codeName);

        if (code == null) {
            player.sendMessage(ColorUtils.colorizeComponent(config.getCodeNotFound()));
            return false;
        }

        if (!code.isActive()) {
            player.sendMessage(ColorUtils.colorizeComponent(config.getCodeNotActive()));
            return false;
        }

        if (hasUsedCode(player.getUniqueId(), codeName)) {
            player.sendMessage(ColorUtils.colorizeComponent(config.getCodeAlreadyUsed()));
            return false;
        }

        int requiredMinutes = code.getRequiredPlaytimeMinutes();
        if (requiredMinutes > 0) {
            long playtimeTicks = player.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE);
            int playtimeMinutes = (int) (playtimeTicks / 20 / 60);

            if (playtimeMinutes < requiredMinutes) {
                int remaining = requiredMinutes - playtimeMinutes;
                String message = config.getCodeTimeRequired().replace("{time}", remaining + " minut");
                player.sendMessage(ColorUtils.colorizeComponent(message));
                return false;
            }
        }

        for (String itemData : code.getItems()) {
            ItemStack item = ItemSerializer.deserializeItem(itemData);
            if (item != null) {
                HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item.clone());
                for (ItemStack left : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), left);
                }
            }
        }

        for (String command : code.getCommands()) {
            String finalCommand = CommandPlaceholderUtil.normalizeConsoleCommand(command, player);
            if (!finalCommand.isBlank()) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand);
            }
        }

        saveUsedCode(codeName, player.getUniqueId());

        if (config.isCodeRedeemMessageEnabled()) {
            player.sendMessage(ColorUtils.colorizeComponent(
                config.getCodeRedeemed().replace("{code}", codeName)
            ));
        }

        boolean broadcast = config.isCodeBroadcastEnabled() && (code == null || code.isBroadcastEnabled());
        if (broadcast) {
            for (String line : config.getCodeBroadcast()) {
                if (line == null || line.isBlank()) {
                    continue;
                }
                String broadcastLine = line
                    .replace("{player}", player.getName())
                    .replace("{code}", codeName);
                Component message = ColorUtils.colorizeComponent(broadcastLine);
                for (Player online : Bukkit.getOnlinePlayers()) {
                    online.sendMessage(message);
                }
            }
        }

        return true;
    }

    public void startCode(String codeName) {
        CodeConfig config = plugin.getConfigManager().getCodeConfig();
        CodeConfig.CodeEntry code = config.getCode(codeName);
        if (code == null) return;

        code.setActive(true);
        config.save();

        createBossBar(codeName);
    }

    public void stopCode(String codeName) {
        CodeConfig config = plugin.getConfigManager().getCodeConfig();
        CodeConfig.CodeEntry code = config.getCode(codeName);
        if (code == null) return;

        code.setActive(false);
        config.save();

        removeBossBar(codeName);
    }

    private void createBossBar(String codeName) {
        String normalizedCode = codeName.toLowerCase(Locale.ROOT);
        if (activeBossBars.containsKey(normalizedCode)) {
            removeBossBar(codeName);
        }

        CodeConfig config = plugin.getConfigManager().getCodeConfig();
        CodeConfig.CodeEntry code = config.getCode(codeName);

        if (!config.isBossbarEnabled()) return;
        if (code != null && !code.isBossbarEnabled()) return;

        String colorName = code != null && code.getBossbarColor() != null ? code.getBossbarColor() : config.getBossbarColor();
        String styleName = code != null && code.getBossbarStyle() != null ? code.getBossbarStyle() : config.getBossbarStyle();
        String formatRaw = code != null && code.getBossbarFormat() != null ? code.getBossbarFormat() : config.getBossbarFormat();
        double progressRaw = code != null && code.getBossbarProgress() != null ? code.getBossbarProgress() : config.getBossbarProgress();

        BossBar.Color color;
        try {
            color = BossBar.Color.valueOf(colorName.toUpperCase());
        } catch (IllegalArgumentException e) {
            color = BossBar.Color.YELLOW;
        }

        BossBar.Overlay style;
        try {
            style = BossBar.Overlay.valueOf(styleName.toUpperCase());
        } catch (IllegalArgumentException e) {
            style = BossBar.Overlay.PROGRESS;
        }

        String format = formatRaw.replace("{code}", codeName);
        float progress = (float) Math.max(0.0, Math.min(1.0, progressRaw));

        BossBar bossBar = BossBar.bossBar(
            ColorUtils.colorizeComponent(format),
            progress,
            color,
            style
        );

        activeBossBars.put(normalizedCode, bossBar);

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!hasUsedCode(player.getUniqueId(), codeName)) {
                player.showBossBar(bossBar);
            }
        }
    }

    private void removeBossBar(String codeName) {
        BossBar bossBar = activeBossBars.remove(codeName.toLowerCase());
        if (bossBar != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.hideBossBar(bossBar);
            }
        }
    }

    public void hideBossBarForPlayer(Player player, String codeName) {
        BossBar bossBar = activeBossBars.get(codeName.toLowerCase());
        if (bossBar != null) {
            player.hideBossBar(bossBar);
        }
    }

    public void showBossBarsToPlayer(Player player) {
        for (Map.Entry<String, BossBar> entry : activeBossBars.entrySet()) {
            if (!hasUsedCode(player.getUniqueId(), entry.getKey())) {
                player.showBossBar(entry.getValue());
            }
        }
    }

    private void startBossBarTask() {
        bossBarTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            CodeConfig config = plugin.getConfigManager().getCodeConfig();
            for (Map.Entry<String, CodeConfig.CodeEntry> entry : config.getCodes().entrySet()) {
                if (entry.getValue().isActive() && !activeBossBars.containsKey(entry.getKey().toLowerCase(Locale.ROOT))) {
                    createBossBar(entry.getKey());
                }
            }
        }, 100L, 100L);
    }

    public void reload() {
        shutdown();
        loadUsedCodes();
        startBossBarTask();

        CodeConfig config = plugin.getConfigManager().getCodeConfig();
        for (Map.Entry<String, CodeConfig.CodeEntry> entry : config.getCodes().entrySet()) {
            if (entry.getValue().isActive()) {
                createBossBar(entry.getKey());
            }
        }
    }

    public void shutdown() {
        if (bossBarTask != null) {
            bossBarTask.cancel();
        }
        for (BossBar bossBar : activeBossBars.values()) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.hideBossBar(bossBar);
            }
        }
        activeBossBars.clear();
    }

    public void handlePlayerJoin(Player player) {
        showBossBarsToPlayer(player);
    }

    public void handlePlayerQuit(Player player) {
        for (BossBar bossBar : activeBossBars.values()) {
            player.hideBossBar(bossBar);
        }
    }
}
