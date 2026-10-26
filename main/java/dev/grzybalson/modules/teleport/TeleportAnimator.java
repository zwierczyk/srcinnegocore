package dev.grzybalson.modules.teleport;

import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ColorUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.awt.Color;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class TeleportAnimator implements Listener {

    private static final int RUNWAY_WIDTH = 24;
    private static final String PLANE = "✈";
    private static final long TICK_PERIOD = 1L;
    private static final int MOVE_EVERY_TICKS = 4;
    private static final float HUE_STEP = 0.02f;

    private final HollyCore plugin;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public TeleportAnimator(HollyCore plugin) {
        this.plugin = plugin;
    }

    public boolean isTeleporting(UUID uuid) {
        return sessions.containsKey(uuid);
    }

    public void completeInstant(Player player, Location destination, Options options) {
        cancel(player.getUniqueId(), false);
        finish(player, destination, options);
    }

    public void start(Player player, Location destination, Options options) {
        cancel(player.getUniqueId(), false);

        final int totalTicks = Math.max(1, options.delaySeconds) * 20;
        BukkitRunnable task = new BukkitRunnable() {
            int elapsed = 0;
            float hue = 0f;
            int planePos = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    sessions.remove(player.getUniqueId());
                    cancel();
                    return;
                }

                if (elapsed >= totalTicks) {
                    sessions.remove(player.getUniqueId());
                    cancel();
                    finish(player, destination, options);
                    return;
                }

                int remainingTicks = totalTicks - elapsed;
                double remainingSeconds = remainingTicks / 20.0;
                String timeLabel = remainingSeconds < 1.0
                    ? "<1s"
                    : ((int) Math.ceil(remainingSeconds)) + "s";

                if (elapsed > 0 && elapsed % MOVE_EVERY_TICKS == 0) {
                    planePos = Math.min(RUNWAY_WIDTH - 1, planePos + 1);
                }

                int spacesAfter = RUNWAY_WIDTH - 1 - planePos;
                String runway = "\u00A0".repeat(planePos) + PLANE + "\u00A0".repeat(Math.max(0, spacesAfter));

                Color awt = Color.getHSBColor(hue, 1.0f, 1.0f);
                Component titleComp = Component.text(runway)
                    .color(TextColor.color(awt.getRed(), awt.getGreen(), awt.getBlue()));
                Component subComp = ColorUtils.colorizeComponent(
                    options.countdownSubtitle.replace("{TIME}", timeLabel).replace("{time}", timeLabel)
                );

                player.showTitle(Title.title(
                    titleComp,
                    subComp,
                    Title.Times.times(Duration.ZERO, Duration.ofSeconds(4), Duration.ZERO)
                ));

                if (elapsed % 20 == 0) {
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.35f, 1.15f);
                }

                hue = (hue + HUE_STEP) % 1.0f;
                elapsed += (int) TICK_PERIOD;
            }
        };

        sessions.put(player.getUniqueId(), new Session(task, options));
        task.runTaskTimer(plugin, 0L, TICK_PERIOD);
    }

    public void cancel(UUID uuid, boolean notify) {
        Session session = sessions.remove(uuid);
        if (session == null) {
            return;
        }

        session.task.cancel();
        Player player = plugin.getServer().getPlayer(uuid);
        if (player == null || !player.isOnline()) {
            return;
        }

        player.clearTitle();
        if (notify) {
            player.sendMessage(ColorUtils.colorizeComponent(session.options.cancelledMessage));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
        }
    }

    public void shutdown() {
        for (UUID uuid : sessions.keySet()) {
            cancel(uuid, false);
        }
        sessions.clear();
    }

    private void finish(Player player, Location destination, Options options) {
        player.teleport(destination);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);

        player.showTitle(Title.title(
            ColorUtils.colorizeComponent(options.successTitle),
            ColorUtils.colorizeComponent(options.successSubtitle),
            Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1800), Duration.ofMillis(300))
        ));

        if (options.successChat != null && !options.successChat.isBlank()) {
            player.sendMessage(ColorUtils.colorizeComponent(options.successChat));
        }
        if (options.onComplete != null) {
            options.onComplete.accept(player);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedBlock()) {
            return;
        }

        Session session = sessions.get(event.getPlayer().getUniqueId());
        if (session == null || !session.options.cancelOnMove) {
            return;
        }

        cancel(event.getPlayer().getUniqueId(), true);
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        Session session = sessions.get(player.getUniqueId());
        if (session == null || !session.options.cancelOnDamage) {
            return;
        }

        cancel(player.getUniqueId(), true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancel(event.getPlayer().getUniqueId(), false);
    }

    public static final class Options {
        public int delaySeconds = 5;
        public boolean cancelOnMove = true;
        public boolean cancelOnDamage = false;
        public String countdownSubtitle = "&fZostaniesz przeniesiony za &#77F454{TIME}";
        public String cancelledMessage = "&c» &7Teleportacja przerwana &8(&#AAAAAAruszyłeś się&8)!";
        public String successTitle = "&#77F454☺";
        public String successSubtitle = "&aPomyślnie zostałeś przeniesiony";
        public String successChat = "";
        public Consumer<Player> onComplete;
    }

    private record Session(BukkitRunnable task, Options options) {
    }
}
