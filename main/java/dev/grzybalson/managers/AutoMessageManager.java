package dev.grzybalson.managers;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.configs.AutoMessageConfig;
import dev.grzybalson.utils.ColorUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class AutoMessageManager {

    private final HollyCore plugin;
    private BukkitTask task;
    private BukkitTask delayedTask;
    private int currentIndex;
    private final Random random;
    private List<PreparedMessage> preparedMessages = List.of();
    private boolean randomOrder;

    public AutoMessageManager(HollyCore plugin) {
        this.plugin = plugin;
        this.currentIndex = 1;
        this.random = new Random();
    }

    public void start() {
        AutoMessageConfig config = plugin.getConfigManager().getAutoMessageConfig();

        if (!config.isEnabled()) {
            return;
        }

        prepareMessages(config);
        if (preparedMessages.isEmpty()) {
            return;
        }

        int interval = config.getInterval() * 20;

        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            sendScheduledMessage();
        }, interval, interval);

        plugin.getLogger().info("AutoMessage started with interval: " + config.getInterval() + "s");
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (delayedTask != null) {
            delayedTask.cancel();
            delayedTask = null;
        }
    }

    public void reload() {
        stop();
        start();
    }

    private void broadcastMessage(PreparedMessage message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            for (Component line : message.lines()) {
                player.sendMessage(line);
            }
        }
    }

    private void sendScheduledMessage() {
        if (shouldWaitForClearLag()) {
            scheduleDelayedMessage();
            return;
        }

        if (preparedMessages.isEmpty()) {
            return;
        }

        PreparedMessage message;
        if (randomOrder) {
            message = preparedMessages.get(random.nextInt(preparedMessages.size()));
        } else {
            if (currentIndex >= preparedMessages.size()) {
                currentIndex = 0;
            }
            message = preparedMessages.get(currentIndex);
            currentIndex++;
        }

        broadcastMessage(message);
    }

    private boolean shouldWaitForClearLag() {
        ClearLagManager clearLagManager = plugin.getClearLagManager();
        return clearLagManager != null && clearLagManager.isAutoMessagePaused();
    }

    private void scheduleDelayedMessage() {
        if (delayedTask != null) {
            return;
        }

        delayedTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            delayedTask = null;
            sendScheduledMessage();
        }, 40L);
    }

    private boolean isRewardReminderMessage(List<String> lines) {
        for (String line : lines) {
            String normalized = ColorUtils.stripColors(line);
            if (normalized != null && normalized.toLowerCase().contains("/nagroda")) {
                return true;
            }
        }
        return false;
    }

    public void sendTestMessage(int index) {
        Map<Integer, List<String>> messages = plugin.getConfigManager().getAutoMessageConfig().getMessages();
        if (messages.containsKey(index)) {
            broadcastMessage(prepareMessage(messages.get(index)));
        }
    }

    private void prepareMessages(AutoMessageConfig config) {
        randomOrder = config.isRandomOrder();
        List<Integer> keys = new ArrayList<>(config.getMessages().keySet());
        keys.sort(Integer::compareTo);
        List<PreparedMessage> prepared = new ArrayList<>(keys.size());
        for (Integer key : keys) {
            List<String> lines = config.getMessages().get(key);
            if (lines != null && !lines.isEmpty()) {
                prepared.add(prepareMessage(lines));
            }
        }
        preparedMessages = List.copyOf(prepared);
        currentIndex = 0;
    }

    private PreparedMessage prepareMessage(List<String> lines) {
        List<Component> components = new ArrayList<>(lines.size());
        for (String line : lines) {
            components.add(ColorUtils.colorizeComponent(line));
        }
        return new PreparedMessage(List.copyOf(components), isRewardReminderMessage(lines));
    }

    private record PreparedMessage(List<Component> lines, boolean rewardReminder) {
    }
}
