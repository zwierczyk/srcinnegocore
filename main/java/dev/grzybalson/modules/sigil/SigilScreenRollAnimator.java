package dev.grzybalson.modules.sigil;

import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ColorUtils;
import net.kyori.adventure.title.Title;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntFunction;

/**
 * Animacja losowania na title — kilka nazw naraz przewija się od prawej do lewej (jak case).
 */
public final class SigilScreenRollAnimator {

    /** 1:1 z CaseManager.DEFAULT_ANIM_DELAYS */
    public static final int[] DEFAULT_DELAYS = {
        1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
        2, 2, 2, 2, 2,
        3, 3, 3, 3,
        4, 4, 5,
        6, 8, 10,
        13, 16,
        20,
        5,
        30
    };

    private static final Random RANDOM = new Random();
    /** Ile napisów widać jednocześnie na title (okno przewijania). */
    public static final int VISIBLE_NAMES = 3;

    private SigilScreenRollAnimator() {
    }

    public static void run(
        HollyCore plugin,
        Player player,
        int[] delays,
        IntFunction<Frame> frameFactory,
        Runnable onFinish,
        AtomicBoolean cancelled
    ) {
        if (player == null || !player.isOnline()) {
            if (onFinish != null) {
                onFinish.run();
            }
            return;
        }

        player.closeInventory();

        int[] safeDelays = (delays == null || delays.length == 0) ? DEFAULT_DELAYS.clone() : delays.clone();
        scheduleStep(plugin, player, safeDelays, 0, frameFactory, onFinish, cancelled);
    }

    private static void scheduleStep(
        HollyCore plugin,
        Player player,
        int[] delays,
        int step,
        IntFunction<Frame> frameFactory,
        Runnable onFinish,
        AtomicBoolean cancelled
    ) {
        if (cancelled != null && cancelled.get()) {
            return;
        }
        if (!player.isOnline()) {
            if (onFinish != null) {
                onFinish.run();
            }
            return;
        }

        if (step >= delays.length) {
            if (cancelled == null || !cancelled.get()) {
                if (onFinish != null) {
                    onFinish.run();
                }
            }
            return;
        }

        Frame frame = frameFactory.apply(step);
        if (frame != null) {
            player.showTitle(Title.title(
                ColorUtils.colorizeComponent(frame.title()),
                ColorUtils.colorizeComponent(frame.subtitle()),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(1100), Duration.ZERO)
            ));
            playTickSounds(player, step, delays.length, frame.finalStep());
        }

        int delay = Math.max(1, delays[step]);
        plugin.getServer().getScheduler().runTaskLater(plugin, () ->
            scheduleStep(plugin, player, delays, step + 1, frameFactory, onFinish, cancelled), delay);
    }

    /**
     * Buduje pasek nazw: przy każdym kroku okno {@code visible} elementów przesuwa się w lewo
     * (kolejne napisy wjeżdżają z prawej). Zwycięzca ląduje na środku w ostatniej klatce.
     */
    public static <T> List<T> buildScrollStrip(List<T> pool, T winner, int steps, int visible, java.util.function.Supplier<T> randomPick) {
        int safeVisible = Math.max(1, visible);
        int centerOffset = safeVisible / 2;
        int len = steps + safeVisible;
        List<T> strip = new ArrayList<>(len);
        for (int i = 0; i < len; i++) {
            if (pool != null && !pool.isEmpty() && randomPick != null) {
                strip.add(randomPick.get());
            } else if (pool != null && !pool.isEmpty()) {
                strip.add(pool.get(RANDOM.nextInt(pool.size())));
            } else {
                strip.add(winner);
            }
        }
        int winnerIdx = Math.min(len - 1, (steps - 1) + centerOffset);
        strip.set(winnerIdx, winner);
        return strip;
    }

    /** Render okna: kilka pozycji z paska, środkowa wyróżniona. */
    public static String renderScrollWindow(List<String> strip, int step, int visible, boolean highlightCenter) {
        int safeVisible = Math.max(1, visible);
        int center = safeVisible / 2;
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < safeVisible; i++) {
            if (i > 0) {
                builder.append(" &8· ");
            }
            int idx = Math.min(step + i, strip.size() - 1);
            String part = strip.get(Math.max(0, idx));
            if (highlightCenter && i == center) {
                builder.append("&f» ").append(part).append(" &f«");
            } else {
                builder.append("&7").append(part);
            }
        }
        return builder.toString();
    }

    public static String powerColor(double power, double minPower, double maxPower) {
        double span = Math.max(0.001D, maxPower - minPower);
        double t = Math.max(0.0D, Math.min(1.0D, (power - minPower) / span));
        if (t < 0.25D) {
            return "&#FF4D4D";
        }
        if (t < 0.45D) {
            return "&#FF8A3D";
        }
        if (t < 0.65D) {
            return "&#FFD93D";
        }
        if (t < 0.85D) {
            return "&#6CFF6C";
        }
        return "&#3DFFF0";
    }

    public static String coloredPower(double power, double minPower, double maxPower) {
        return powerColor(power, minPower, maxPower) + "&l" + SigilType.formatValue(power) + "%";
    }

    private static void playTickSounds(Player player, int step, int total, boolean finalStep) {
        float progress = total <= 1 ? 1.0F : (float) step / (total - 1);
        float pitch = 1.55F - progress * 0.85F;

        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.45F, pitch);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.18F, 0.7F + progress * 0.9F);

        if (step % 3 == 0) {
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.35F, 1.2F + RANDOM.nextFloat() * 0.4F);
        }
        if (step % 5 == 0) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.22F, 1.4F - progress * 0.5F);
        }
        if (finalStep) {
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7F, 1.15F);
            player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8F, 1.35F);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.55F, 1.4F);
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.6F, 1.1F);
        }
    }

    public static void playFinishFanfare(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.85F, 1.05F);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.55F, 1.6F);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7F, 0.8F);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9F, 0.9F);
    }

    public record Frame(String title, String subtitle, boolean finalStep) {
        public Frame(String title, String subtitle) {
            this(title, subtitle, false);
        }
    }
}
